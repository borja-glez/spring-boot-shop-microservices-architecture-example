# Testing

Mercado se prueba por capas, cada una más barata y rápida que la siguiente: tests unitarios simples del modelo de dominio, reglas de ArchUnit que mantienen las capas en orden, slices `@WebMvcTest` que comprueban el contrato HTTP de cada controlador, tests de integración que ejecutan un servicio contra PostgreSQL, Kafka y RabbitMQ reales en Testcontainers, un test de contrato para los mensajes compartidos y un test entre generaciones de Spring Boot 4 y 3.5. El frontend tiene sus propias comprobaciones. Por encima de todo ello, una suite separada de tests de sistema maneja una plataforma desplegada a través del gateway, incluidos escenarios de estrés con fallos inyectados, y termina cada escenario comprobando las mismas invariantes entre servicios: cada pedido queda confirmado, rechazado o cancelado de forma consistente en todos los servicios.

## La pirámide

| Capa | Herramientas | Qué cubre | Se ejecuta en |
|---|---|---|---|
| Tests unitarios de dominio | JUnit 5, AssertJ | Agregados, value objects y máquinas de estados, sin contexto de Spring | `./gradlew build` |
| Arquitectura | ArchUnit | Dependencias entre capas e inyección por constructor en todos los servicios; convenciones de los contratos de mensajes | `./gradlew build` |
| Slices web | `@WebMvcTest`, MockMvc, buses simulados | Traducción de HTTP a comandos/consultas, parseo de `@FilterableQuery`, validación, problem details | `./gradlew build` |
| Integración | `@SpringBootTest`, Testcontainers (PostgreSQL 17, Kafka, RabbitMQ) | Buses reales, SQL real, brokers reales, un servicio cada vez | `./gradlew build` (necesita Docker) |
| Entre generaciones | Testcontainers 1.x sobre Boot 3.5 | Eventos escritos por Jackson 3 leídos por el servicio Boot 3.5 / Jackson 2 | `./gradlew build` |
| Frontend | Vitest, ESLint, Prettier, build de Angular | Stores, clientes de la API, serialización de filtros, páginas | `npm run check` |
| Sistema | JUnit 5 con la etiqueta `system`, HTTP simple a través del gateway | Recorridos de cliente, caos y estrés contra una plataforma en ejecución | `./gradlew :system-tests:systemTest` |

## Tests unitarios de dominio

Ejemplos: [`OrderTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/OrderTest.java), [`CheckoutSagaTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/CheckoutSagaTest.java) (todas las transiciones de la saga, incluidas las que deben rechazarse), `ProductTest` y `MoneyTest` (catalog), `StockItemTest` (inventory), `PaymentTest` (payments), `NotificationTest` (notifications) y, en la plataforma, `EventSourcedAggregateTest`, `EventTypeRegistryTest`, `QueryPlansTest`, `ChaosTest` y `MicrometerTraceCarrierTest`.

## Tests de arquitectura

Cada servicio tiene un `ArchitectureTest` que aplica las reglas compartidas de [`ShopArchitectureRules`](../../platform/test-support/src/main/java/com/borjaglez/shop/testsupport/architecture/ShopArchitectureRules.java):

```java
class ArchitectureTest {

  @ArchTest
  static final ArchRule layers = ShopArchitectureRules.layersOf("com.borjaglez.shop.orders");

  @ArchTest static final ArchRule constructorInjection = ShopArchitectureRules.noFieldInjection();
}
```

[`ContractsConventionsTest`](../../platform/contracts/src/test/java/com/borjaglez/shop/contracts/ContractsConventionsTest.java) comprueba cada mensaje de `platform/contracts`: público, anotado con `@CqrsMessage`, instanciable por los deserializadores (constructor sin argumentos) y libre de tipos de Spring, Jackson (2 o 3) y JPA.

## Slices web

`ProductControllerTest`, `OrderControllerTest`, `InventoryControllerTest`, `PaymentControllerTest`, `ReportingControllerTest` y `NotificationControllerTest` ejecutan el controlador aislado con `CommandBus` y `QueryBus` simulados, y comprueban qué mensaje se despacha para cada petición y cómo se genera la respuesta. `service-support` registra su autoconfiguración web para el slice `@WebMvcTest`, así que estos tests ven el filtro de correlación, la resolución de `@CurrentUser` y los problem details reales; specification-repository hace lo mismo con `HttpFilterAutoConfiguration`, de modo que los parámetros `@FilterableQuery`, incluidas anotaciones compuestas como `@ProductFilter`, se resuelven y rechazan los campos no permitidos igual que en el servicio en ejecución. El propio `service-support` los prueba con `ProblemDetailsExceptionHandlerTest`, `CorrelationIdFilterTest`, `CurrentUserArgumentResolverTest`, `ChaosControllerTest`, `ChaosDisabledTest` y `ShopDefaultsTest`.

## Tests de integración

Los tests con nombre `*IT` arrancan infraestructura real con Testcontainers y necesitan un daemon de Docker en ejecución. [`platform/test-support`](../../platform/test-support/src/main/java/com/borjaglez/shop/testsupport) proporciona una configuración por dependencia, fijada en las mismas versiones que Compose y Kubernetes y conectada mediante `@ServiceConnection`:

| Configuración | Imagen |
|---|---|
| `PostgresTestConfiguration` | `postgres:17-alpine` |
| `KafkaTestConfiguration` | `apache/kafka:4.3.1` |
| `RabbitTestConfiguration` | `rabbitmq:4.3-management-alpine` |

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.checkout.enabled=false")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
class DueCheckoutsIT { ... }
```

La caché de contextos de test de Spring mantiene un conjunto de contenedores por cada configuración distinta, así que las clases de test que comparten configuración comparten los contenedores.

| Área | Tests |
|---|---|
| es-kit | `EventStoreIT`, `AggregateStoreIT` (concurrencia optimista), `OutboxRelayIT` (orden global, parada ante un fallo y reintento, correlation id y traza de cada evento, fallos de caos, métricas), `IdempotentConsumersIT` (un evento entregado de nuevo se aplica una vez, un evento fallido no deja marca y se aplica en la nueva entrega, cuentas de aplicados y duplicados, retraso) |
| catalog | `CatalogApiIT` (de HTTP a PostgreSQL de extremo a extremo), `CatalogCommandsIT`, `CatalogQueriesIT`, `ProductRepositoryIT` y `ProductQueryIT` (semántica del DSL sobre PostgreSQL), `CatalogEventsOverKafkaIT`, `StockLevelsOverRabbitIT` (stock consultado por RabbitMQ, timeouts y fallos remotos) |
| orders | `OrdersApiIT`, `OrderCommandsIT`, `OrderQueriesIT`, `OrderViewProjectorIT` y `CatalogProductProjectorIT` (eventos de catálogo desordenados, nuevas entregas despachadas por `EventHandlerRegistry` como hace el consumidor de Kafka), `OrdersOverKafkaIT`, `RemoteQueriesOverRabbitIT` (stock y avisos consultados por RabbitMQ con el timeout corto), `CqrsActuatorIT` |
| saga de checkout | `CheckoutSagaIT`, `CheckoutOverRabbitIT`, `DueCheckoutsIT`, `CheckoutTracingIT`; consulta [Saga de checkout](checkout-saga.md#tests) |
| inventory | `InventoryCommandsIT`, `InventoryMessagingIT` (productos desde Kafka, comandos y `GetStockLevels` por RabbitMQ) |
| payments | `PaymentCommandsIT`, `PaymentsOverRabbitIT` |
| reporting | `ReportsIT` (nuevas entregas por `EventHandlerRegistry`), `RebuildIT` (vaciado, reproducción desde el offset 0 con un Kafka real, mismas cifras) |
| notifications | `NotificationsIT`, `CqrsActuatorIT` (starter de Boot 3), `CrossGenerationIT`, `OrderNoticesOverRabbitIT` (una consulta escrita por Jackson 3, respondida por Jackson 2 y leída por su nombre lógico sea cual sea el nombre de clase que le dio el emisor) |
| gateway | `GatewayRoutingIT` (enrutado, cabecera de usuario, correlation id, problemas 404 y 503); `ExternalTraceHeadersFilterTest` para las cabeceras de traza |

Los tests de la saga sustituyen inventory y payments por [`FakeCheckout`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/FakeCheckout.java), que responde por pedido según lo guioniza cada test, y hacen avanzar la saga paso a paso con [`CheckoutDriver`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutDriver.java) mientras el ejecutor programado está desactivado (`shop.checkout.enabled=false`). Del mismo modo, [`FakeStockLevels`](../../services/catalog-service/src/test/java/com/borjaglez/shop/catalog/FakeStockLevels.java) y [`FakeRemoteReads`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/FakeRemoteReads.java) sustituyen a los servicios que catalog y orders consultan por RabbitMQ, respondiendo por producto y por pedido.

### notifications-service

El servicio Boot 3.5 no puede usar `test-support`, que está compilado contra Boot 4. Su configuración [`TestContainers`](../../services/notifications-service/src/test/java/com/borjaglez/shop/notifications/TestContainers.java) usa Testcontainers 1.x, gestionado por el BOM de Boot 3.5, con las mismas imágenes de PostgreSQL y Kafka.

### Entre generaciones

[`CrossGenerationIT`](../../services/notifications-service/src/test/java/com/borjaglez/shop/notifications/application/CrossGenerationIT.java) publica, en un Kafka real, payloads de eventos exactamente como los escriben los servicios Boot 4 con Jackson 3 (instantes ISO con nanosegundos, importes numéricos, records para las líneas de pedido, tomados de un event store real de orders), con las cabeceras que añade spring-boot-cqrs, y comprueba que el servicio Boot 3.5 / Jackson 2 los convierte en avisos. La única diferencia con producción es quién escribió los bytes.

## Frontend

```bash
cd frontend
npm ci
npm run check      # prettier --check, eslint, vitest, production build
```

Las specs de Vitest cubren los stores (carrito, usuario, notificaciones), los clientes de la API, el serializador de filtros, los pasos del checkout y la línea de tiempo del historial, la búsqueda del catálogo y las páginas de producto, Filter Lab y Chaos.

## Tests de sistema

[`system-tests`](../../system-tests/src/test/java/com/borjaglez/shop/system) contiene tests JUnit con la etiqueta `system` que llaman a una plataforma en ejecución, en Compose o en Kubernetes, a través del gateway, igual que hace la tienda: [`ShopClient`](../../system-tests/src/test/java/com/borjaglez/shop/system/ShopClient.java) envía `X-Shop-User` y lee JSON simple, sin compartir clases con los servicios. El build normal los omite.

```bash
./gradlew :system-tests:systemTest -Pshop.baseUrl=http://localhost:8080
```

Cada test crea sus propios productos (para el vendedor `seller-ana`, con su propio stock) y clientes, de modo que los tests nunca comparten stock, desactiva todos los fallos de caos al terminar y acaba con [`Checkouts.assertConsistent`](../../system-tests/src/test/java/com/borjaglez/shop/system/Checkouts.java), las invariantes de un pedido cerrado en todos los servicios:

| Modo de la saga | Vista del pedido | Pago | Reserva |
|---|---|---|---|
| `CHECKOUT` | `CONFIRMED` | exactamente uno, `AUTHORIZED` | `RESERVED` |
| `REJECTING` | `REJECTED` | ninguno, `DECLINED` o `REFUNDED` | ninguna o `RELEASED` |
| `CANCELLING` | `CANCELLED` | `REFUNDED` | `RELEASED` |

Una saga debe cerrarse en cuatro minutos (reintentos y compensaciones incluidos) y los modelos de lectura deben ponerse al día en 60 segundos. `assertStock` comprueba que el almacén nunca promete más de lo que tiene y que retiene exactamente lo que se llevaron los pedidos confirmados.

| Test | Escenarios |
|---|---|
| [`CheckoutJourneyTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/CheckoutJourneyTest.java) | Un pedido pagado se confirma y retiene su stock; un pedido por encima del límite de la tarjeta se rechaza y libera su stock; un pedido de más unidades de las que hay en stock se rechaza sin cobrar; un pedido cancelado se reembolsa y devuelve su stock; no se puede cancelar un pedido mientras su checkout está en curso; un cliente no puede ver el pedido de otro |
| [`ChaosJourneyTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ChaosJourneyTest.java) | Todas las tarjetas denegadas; payments fallando y volviendo; pagos lentos que agotan el timeout sin que nadie pague dos veces; relay en pausa mientras las sagas terminan y los informes se ponen al día más tarde; eventos duplicados contados una sola vez. Necesita `SHOP_CHAOS_ENABLED=true` |
| [`ScarceStockStressTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ScarceStockStressTest.java) | Muchos clientes compiten por las últimas unidades: solo salen adelante tantos pedidos como unidades hay; cancelar todos los pedidos confirmados devuelve todo el stock |
| [`ChaosStormStressTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ChaosStormStressTest.java) | Una ráfaga de pedidos bajo eventos duplicados, reservas lentas, fallos intermitentes de pago y un relay en pausa; después, todos los pedidos son consistentes y los informes cuentan cada pedido confirmado exactamente una vez |

Un cambio que afecte a la saga, al outbox o a la concurrencia debería venir acompañado de un escenario aquí.

## Comandos

| Comando | Qué hace |
|---|---|
| `./gradlew build` | Compila, comprueba Spotless y ejecuta los tests unitarios, de ArchUnit, de slices y de integración (requiere Docker) |
| `./gradlew test` | Solo los tests |
| `./gradlew :services:orders-service:test --tests "*CheckoutSagaIT"` | Una clase de test |
| `./gradlew :services:catalog-service:test --tests "*ProductControllerTest.searchTurnsHttpFiltersIntoAQueryPlan"` | Un método de test |
| `./gradlew :platform:es-kit:test` | Los tests de un módulo |
| `./gradlew spotlessApply` | Formatea el código Java (Google Java Format) |
| `./gradlew :system-tests:systemTest -Pshop.baseUrl=http://localhost:8080` | Tests de sistema y de estrés contra una plataforma en ejecución |
| `cd frontend && npm run check` | Prettier, ESLint, Vitest y build de producción |

## Relacionado

- [Arquitectura](architecture.md#capas-dentro-de-un-servicio)
- [Saga de checkout](checkout-saga.md#tests)
- [Modelos de lectura](read-models.md)
- [Resiliencia y escalado](resilience-and-scaling.md#tests-de-estrés)
- [Despliegue](deployment.md#integración-continua)
