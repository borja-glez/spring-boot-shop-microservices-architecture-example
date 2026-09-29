# Arquitectura

Mercado es un marketplace dividido en seis servicios de negocio y un gateway, cada uno una aplicación Spring Boot independiente con su propia base de datos PostgreSQL. El navegador habla con un único origen (nginx sirviendo la tienda Angular), nginx reenvía `/api/**` al gateway y el gateway enruta cada prefijo de ruta a su servicio. Los servicios nunca comparten tablas: cooperan mediante comandos petición/respuesta sobre RabbitMQ (la saga de checkout) y mediante eventos de integración en Kafka, publicados desde un outbox transaccional. Dentro de cada servicio el código sigue las mismas cuatro capas, verificadas por ArchUnit, y un módulo de plataforma compartido da a todos los servicios el mismo formato de error, los mismos correlation ids, la misma resolución del usuario actual y los mismos valores operativos por defecto.

<p align="center"><img src="../assets/diagrams/architecture.svg" alt="Arquitectura de Mercado: navegador, frontend, gateway, seis servicios con sus propias bases de datos PostgreSQL, RabbitMQ para los comandos de la saga y las consultas remotas, Kafka para los eventos de integración y un pipeline de OpenTelemetry hacia Grafana" width="100%"></p>

## Servicios

| Servicio | Puerto | Responsabilidad | Base de datos | Mensajería de entrada | Mensajería de salida | Spring Boot |
|---|---|---|---|---|---|---|
| `gateway-service` | 8080 | Punto de entrada público único; enruta `/api/<service>/**` | ninguna | HTTP desde nginx | HTTP hacia los servicios | 4 (Jackson 3) |
| `catalog-service` | 8081 | Los vendedores publican, cambian el precio y descatalogan productos; búsqueda pública y facetas; fichas de producto con stock en vivo | `catalog` | Kafka: sus propios eventos de catálogo (se registran en el log) | Kafka: `ProductPublished`, `ProductPriceChanged`, `ProductDiscontinued`; consultas RabbitMQ: `GetStockLevels` | 4 (Jackson 3) |
| `orders-service` | 8082 | Pedidos con event sourcing, orquestación de la saga de checkout, copia local del catálogo, modelo de lectura de pedidos, explorador del event store | `orders` | Kafka: eventos de catálogo, eventos de pedidos | Kafka: `OrderPlaced`, `OrderConfirmed`, `OrderRejected`, `OrderCancelled`; peticiones RabbitMQ: `ReserveStock`, `ReleaseStock`, `AuthorizePayment`, `RefundPayment`; consultas RabbitMQ: `GetStockLevels`, `GetOrderNotices` | 4 (Jackson 3) |
| `inventory-service` | 8083 | Stock por producto, reservas de todo o nada, backoffice de stock | `inventory` | RabbitMQ: `ReserveStock`, `ReleaseStock`, `GetStockLevels`; Kafka: `ProductPublished` | Kafka: `StockReserved`, `StockReleased`, `StockAdjusted` | 4 (Jackson 3) |
| `payments-service` | 8084 | Pagos con tarjeta con event sourcing y un límite de tarjeta de demostración, backoffice de pagos | `payments` | RabbitMQ: `AuthorizePayment`, `RefundPayment` | Kafka: `PaymentAuthorized`, `PaymentDeclined`, `PaymentRefunded` | 4 (Jackson 3) |
| `notifications-service` | 8085 | Avisos al cliente, enviados en directo mediante server-sent events | `notifications` | Kafka: eventos de pedidos, `PaymentRefunded`; RabbitMQ: `GetOrderNotices` | SSE hacia el navegador | **3.5 (Jackson 2)** |
| `reporting-service` | 8086 | Informes de ventas, productos, rechazos y clientes a partir de sus propias proyecciones; reconstrucción desde Kafka | `reporting` | Kafka: eventos de pedidos | ninguna | 4 (Jackson 3) |

`notifications-service` se ejecuta a propósito sobre Spring Boot 3.5 con Jackson 2, mientras que el resto de servicios usa Spring Boot 4 con Jackson 3. Lee los eventos que escriben los servicios de Boot 4 y responde la consulta `GetOrderNotices` que orders le envía por RabbitMQ, lo que demuestra que los contratos de mensajes y las librerías interoperan entre ambas generaciones del framework. Por eso no depende de `service-support`, `es-kit` ni `test-support` (todos compilados contra Boot 4), y `platform/contracts` no incluye ningún BOM de Spring Boot.

## Una base de datos por servicio

Una única instancia de PostgreSQL aloja una base de datos y un rol propietario por servicio, creados por [`01-databases.sh`](../../deploy/k8s/base/infra/postgres-init/01-databases.sh), el mismo script para Docker Compose y Kubernetes. El script revoca el acceso público a cada base de datos, de modo que un servicio no puede leer los datos de otro ni siquiera por accidente. Cuando un servicio necesita datos que pertenecen a otro, mantiene su propia copia, alimentada por eventos:

| Copia | Propietario de los datos | Mantenida por |
|---|---|---|
| `catalog_product` (orders) | catalog | [`CatalogProductProjector`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/projection/CatalogProductProjector.java) |
| filas de `stock_item` (inventory) | catalog | [`CatalogProductsProjector`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/projection/CatalogProductsProjector.java) |
| `report_order`, `report_line` (reporting) | orders | [`ReportProjector`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/projection/ReportProjector.java) |
| `order_owner`, `notification` (notifications) | orders, payments | [`NotificationProjector`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/NotificationProjector.java) |

Cada esquema se gestiona con Flyway y `ddl-auto: validate`; consulta [Event sourcing y outbox](event-sourcing-and-outbox.md#organización-de-flyway) para ver cómo las migraciones del servicio y de la plataforma comparten un único historial.

## Gateway

El gateway está construido sobre Spring Cloud Gateway Server MVC. Las rutas son configuración, no código: `shop.gateway.routes[n].{id,path,uri}` en [`application.yaml`](../../services/gateway-service/src/main/resources/application.yaml), validadas al arrancar por [`GatewayRoutesProperties`](../../services/gateway-service/src/main/java/com/borjaglez/shop/gateway/GatewayRoutesProperties.java). [`GatewayRoutesConfiguration`](../../services/gateway-service/src/main/java/com/borjaglez/shop/gateway/GatewayRoutesConfiguration.java) convierte cada entrada en una ruta y añade tres comportamientos:

- **Las trazas empiezan en la plataforma.** [`ExternalTraceHeadersFilter`](../../services/gateway-service/src/main/java/com/borjaglez/shop/gateway/ExternalTraceHeadersFilter.java) se ejecuta antes de que la petición se observe y oculta los `traceparent`, `tracestate` y `baggage` enviados por el cliente. Un cliente no puede unirse a la traza de otro ni desactivar el muestreo de sus propios pedidos.
- **Un único correlation id de extremo a extremo.** El `CorrelationIdFilter` compartido acepta o genera el id, y la ruta lo reenvía como `X-Correlation-Id`, de modo que el gateway y el servicio registran el mismo valor. Se elimina la copia de la cabecera de respuesta del servicio para evitar un duplicado.
- **Los servicios inalcanzables devuelven 503.** Un fallo de conexión se convierte en un problema RFC 9457 `service-unavailable` en lugar de un error interno.

## Frontend

La tienda es una aplicación Angular 22 ([`frontend/`](../../frontend)) servida por un nginx sin privilegios. nginx sirve el bundle estático y hace de proxy de `/api/` hacia `${GATEWAY_URL}` ([`default.conf.template`](../../frontend/nginx/default.conf.template)), así que el navegador solo habla con un origen y no necesita CORS. El buffering del proxy está desactivado y el timeout de lectura es de una hora, para que los server-sent events fluyan de inmediato. Durante el desarrollo, `ng serve` hace lo mismo con [`proxy.conf.json`](../../frontend/proxy.conf.json).

Un interceptor HTTP ([`shop-headers.interceptor.ts`](../../frontend/src/app/core/api/shop-headers.interceptor.ts)) añade el usuario de demostración (`X-Shop-User`) y un `X-Correlation-Id` nuevo a cada llamada a la API.

## Capas dentro de un servicio

Todos los servicios usan el paquete `com.borjaglez.shop.<service>` con cuatro capas:

```
com.borjaglez.shop.<service>
├── domain/          aggregates, value objects, business rules, repository interfaces
├── application/     commands, queries and their handlers, projections, saga
├── infrastructure/  Spring configuration, adapters (RabbitMQ, Kafka, SQL)
└── api/             REST controllers and DTOs
```

[`ShopArchitectureRules`](../../platform/test-support/src/main/java/com/borjaglez/shop/testsupport/architecture/ShopArchitectureRules.java) codifica las reglas y cada servicio las comprueba en su propio `ArchitectureTest`:

- `domain` no depende de ninguna otra capa.
- `application` solo la usan `api` e `infrastructure`.
- `api` e `infrastructure` son hojas: nada depende de ellas.
- Las dependencias se inyectan por constructor, nunca con campos `@Autowired`.

Los controladores no contienen lógica: traducen HTTP a un comando o a una consulta y lo envían a través de los buses de spring-boot-cqrs. Los repositorios viven en `domain` y extienden `SpecificationRepository`; toda lectura pasa por el DSL de specification-repository o por un `QueryPlan`. Consulta [Las librerías en la práctica](libraries.md).

## Módulos de plataforma

| Módulo | Contenido |
|---|---|
| [`platform/contracts`](../../platform/contracts) | Mensajes intercambiados entre servicios (eventos `@CqrsMessage`, comandos y sus records de respuesta). Solo depende de `spring-boot-cqrs-core`; sin tipos de Spring ni de Jackson, comprobado por `ContractsConventionsTest`. |
| [`platform/es-kit`](../../platform/es-kit) | Event store que hace también de outbox transaccional, clase base de agregado con event sourcing, relay del outbox, destino Kafka, consumidores idempotentes, métricas del outbox, hints para imagen nativa. |
| [`platform/service-support`](../../platform/service-support) | Errores RFC 9457, correlation ids, `@CurrentUser`, `PageResponse`, `QueryPlans`, `TraceCarrier`, exportación OTLP, registro y endpoint de caos, valores por defecto compartidos. Autoconfigurado. |
| [`platform/test-support`](../../platform/test-support) | Configuraciones de Testcontainers (PostgreSQL 17, Kafka, RabbitMQ) y las reglas de ArchUnit. |

Las convenciones de build viven en [`build-logic`](../../build-logic/src/main/kotlin): `shop.boot-service-conventions` (servicios Boot 4, build nativo opcional), `shop.boot3-service-conventions` (el servicio Boot 3.5), `shop.library-conventions`, `shop.java-base-conventions` y `shop.hibernate-enhancement-conventions`.

## Estilos de comunicación

| Estilo | Se usa para | Por qué |
|---|---|---|
| HTTP, síncrono | Todas las llamadas desde la UI: consultas y comandos del usuario (realizar, cancelar, publicar, ajustar stock) | El usuario espera una respuesta; el gateway ofrece un único origen y un único formato de error. |
| Petición/respuesta sobre RabbitMQ | Comandos de la saga de orders a inventory y payments | Van dirigidos a un servicio, necesitan respuesta (reservado o insuficiente, autorizado o denegado) y los consumidores en competencia reparten la carga. |
| Consultas petición/respuesta sobre RabbitMQ | Lecturas de datos que son de otro servicio: `GetStockLevels` (catalog y orders preguntan a inventory), `GetOrderNotices` (orders pregunta a notifications) | El dato cambia demasiado rápido para una copia alimentada por eventos, o se lee demasiado poco para que merezca copiarlo; la página se degrada si el dueño no responde. |
| Kafka, a través del outbox | Eventos de integración (`Product*`, `Order*`, `Stock*`, `Payment*`) | Cualquier servicio puede suscribirse; el log se puede reproducir (reporting se reconstruye desde el offset 0); la publicación está ligada a la transacción de negocio. |

### Copiarlo o preguntarlo

Un servicio que necesita datos de otro puede guardar una copia alimentada por eventos o preguntar al dueño cuando los necesita. Mercado hace las dos cosas, y la elección depende del dato:

| Dato | Lo necesita | Estilo | Por qué |
|---|---|---|---|
| Nombre, precio y si el producto está a la venta | orders (precios), inventory (stock inicial) | Copia desde eventos de Kafka (`CatalogProduct`) | Cambia poco; hacer un pedido no debe depender de que el catálogo esté arriba. |
| Stock libre | catalog (ficha de producto), orders (presupuesto del carrito) | Preguntar a inventory por RabbitMQ (`GetStockLevels`) | Cambia con cada checkout; una copia iría retrasada justo cuando el stock escasea. |
| Avisos de un pedido | orders (página del pedido) | Preguntar a notifications por RabbitMQ (`GetOrderNotices`) | Se lee una vez por visita; copiar cada aviso en orders duplicaría un servicio entero. |

Una consulta remota tiene su propio timeout corto (`shop.remote-queries.reply-timeout`, 1 s) y nunca hace fallar la página: sin respuesta, la ficha muestra el stock como desconocido, el presupuesto del carrito comprueba solo los precios y la página del pedido indica que los avisos no están disponibles. Solo los contratos de consulta, anotados con `@CqrsMessage`, se enlazan a las colas del broker; el resto de consultas siguen siendo locales. Consulta [Librerías](libraries.md#peticiónrespuesta-sobre-rabbitmq-para-consultas).

Kafka transporta solo eventos (`cqrs.kafka.commands.enabled=false`, `cqrs.kafka.queries.enabled=false`). Ningún handler publica directamente en un broker: los eventos se escriben en la tabla `event_store` del servicio en la misma transacción que el cambio y se retransmiten después. Consulta [Event sourcing y outbox](event-sourcing-and-outbox.md) y [Saga de checkout](checkout-saga.md).

## Modelo de errores

Todos los servicios Boot 4 responden a los errores con problem details RFC 9457 (`application/problem+json`), generados por [`ProblemDetailsExceptionHandler`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/ProblemDetailsExceptionHandler.java):

```json
{
  "type": "https://shop.borjaglez.com/problems/duplicate-sku",
  "title": "Conflict",
  "status": 409,
  "detail": "Another product already uses SKU CAF-001",
  "instance": "/api/catalog/products",
  "code": "duplicate-sku",
  "correlationId": "3f0c9a52-5d1e-4a57-9b43-2f0d5c6b7e11"
}
```

- `code` es un identificador estable en kebab-case en el que los clientes pueden confiar; `type` se deriva de él. `correlationId` permite al usuario citar la petición al informar de un problema. Los fallos de validación añaden un array `errors` de `{field, message}`.
- El código de dominio lanza subtipos de [`DomainException`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/error/DomainException.java), que llevan el código y no dependen de HTTP: `NotFoundException` (404), `ConflictException` (409) y `BusinessRuleViolationException` (422).
- El handler recorre la cadena de causas (de la más externa a la más interna, hasta diez niveles) y consulta una cadena de beans [`ProblemMapper`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/problem/ProblemMapper.java), de modo que los envoltorios añadidos por el bus de comandos o por Spring Data nunca ocultan el motivo real:

| Orden | Mapper | Traduce |
|---|---|---|
| 0 | `DomainProblemMapper` | subtipos de `DomainException` a 404 / 409 / 422 |
| 10 | `UserHeaderProblemMapper` | `X-Shop-User` ausente a 401 `missing-user`, mal formado a 400 `invalid-user` |
| 20 | `DataAccessProblemMapper` | bloqueo optimista a 409 `concurrent-modification`, violaciones de integridad a 409 `data-integrity-violation` |
| 30 | `ValidationProblemMapper` | fallos de Bean Validation lanzados por el bus de comandos a 400 `validation-failed` |
| 40 | `SpecificationQueryProblemMapper` | campos no permitidos, valores no convertibles y filtros rechazados a 400 `invalid-filter` |
| 50 | `SpecificationHttpProblemMapper` | sintaxis mal formada de `filter` / `orFilter` / `sort` a 400 `invalid-filter` |
| 100 | solo en el gateway | fallos de conexión a 503 `service-unavailable` |

Todo lo que ningún mapper reclama se convierte en un 500 `internal-error` cuyo cuerpo nunca contiene el mensaje de la excepción. Las `IllegalArgumentException` o `IllegalStateException` genéricas no se traducen a 4xx de forma intencionada: un error de programación debe parecerlo. Para dar soporte a una nueva familia de excepciones basta con declarar otro bean `ProblemMapper`.

`notifications-service` genera el mismo formato, `code` incluido, con su propio [`ProblemHandler`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/api/ProblemHandler.java).

## Correlation ids

[`CorrelationIdFilter`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/CorrelationIdFilter.java) asigna un correlation id a cada petición. Acepta el `X-Correlation-Id` entrante cuando es seguro (de 1 a 64 caracteres de `[A-Za-z0-9._-]`) y, si no, genera un UUID. El id:

- se devuelve en la cabecera de respuesta;
- se guarda como atributo de la petición, de donde lo leen los problem details;
- se coloca en el MDC de logging como `correlationId`, se imprime en cada línea de log y se exporta con cada registro de log OTLP;
- se copia en el `MessageContext` de spring-boot-cqrs mediante `MessageContextCorrelationScope`, de modo que cada comando y evento despachado al atender la petición lo lleva a través de RabbitMQ y Kafka. El event store también lo guarda en los metadatos de cada evento, y el relay lo restaura al publicar.

Los correlation ids identifican una petición de negocio; los trace ids identifican una traza técnica. Ambos aparecen en el patrón de log `[correlationId,traceId]`; consulta [Observabilidad](observability.md).

## El usuario de demostración

El ejemplo no tiene autenticación. La tienda permite al visitante elegir un cliente o vendedor de demostración y envía su id en la cabecera `X-Shop-User`. Los controladores lo reciben con [`@CurrentUser`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/CurrentUser.java):

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
CreatedResponse place(@CurrentUser String customer, @Valid @RequestBody PlaceOrderRequest body) {
```

La propiedad se sigue comprobando en el servidor: los clientes solo ven sus propios pedidos, y un vendedor que toca el producto de otro vendedor recibe un 404, para no revelar que el producto existe. `notifications-service` lee la misma cabecera, y su stream SSE recibe el usuario como parámetro de consulta porque el `EventSource` del navegador no puede enviar cabeceras.

En producción la cabecera se sustituiría por una identidad real: el gateway y los servicios funcionarían como resource servers OAuth2 que validan JWT emitidos por un proveedor OpenID Connect, `@CurrentUser` resolvería el subject a partir del contexto de seguridad, y los endpoints exclusivos de operadores (explorador del event store, backoffice de pagos y de stock, informes, reconstrucción, caos) exigirían un rol de operador.

## Valores por defecto compartidos

[`ShopDefaultsEnvironmentPostProcessor`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/autoconfigure/ShopDefaultsEnvironmentPostProcessor.java) registra los ajustes que comparten todos los servicios Boot 4, con la precedencia más baja, de modo que cualquier `application.yaml`, perfil o variable de entorno puede sobrescribirlos:

| Ajuste | Valor | Propósito |
|---|---|---|
| `spring.data.web.pageable.max-page-size` / `default-page-size` | 100 / 20 | Ninguna página sin límite |
| `spring.jpa.open-in-view` | `false` | Sin carga perezosa durante el renderizado de la vista |
| `spring.datasource.hikari.data-source-properties.socketTimeout` | 30 (s) | Una consulta sobre una conexión muerta se abandona en lugar de bloquear el ejecutor de la saga o el relay |
| `spring.datasource.hikari.data-source-properties.tcpKeepAlive` | `true` | Se sondean las conexiones inactivas |
| `spring.threads.virtual.enabled` | `true` | Las peticiones y los listeners se ejecutan en hilos virtuales |
| `server.shutdown` / `spring.lifecycle.timeout-per-shutdown-phase` | `graceful` / 20s | Las peticiones en curso terminan durante el apagado |
| `management.endpoints.web.exposure.include` | `health,info,prometheus,metrics,cqrs` | Superficie de Actuator, incluido el endpoint de spring-boot-cqrs |
| `management.endpoint.health.probes.enabled`, `show-details` | `true`, `always` | Grupos de liveness y readiness para Kubernetes |
| `management.info.env.enabled` | `true` | La `info.service.description` de cada servicio aparece en `/actuator/info` |
| `cqrs.aot.message-packages` | `com.borjaglez.shop.contracts` | Hints de imagen nativa para cada contrato |
| `management.tracing.sampling.probability` | `1.0` | Trazas completas en la demo |
| `spring.rabbitmq.*.observation-enabled`, `spring.kafka.*.observation-enabled` | `true` | Propagación de trazas a través de ambos brokers |
| `logging.pattern.correlation` | `[%X{correlationId:-},%X{traceId:-}] ` | Correlation id y trace id en cada línea |

Cada servicio fija además un `connection-timeout` de Hikari de 5 segundos y añade `db` a su grupo de readiness, para que Kubernetes deje de enrutar tráfico a una instancia que ha perdido su base de datos.

## Relacionado

- [Las librerías en la práctica](libraries.md)
- [Event sourcing y outbox](event-sourcing-and-outbox.md)
- [Saga de checkout](checkout-saga.md)
- [Modelos de lectura](read-models.md)
- [Consultas](querying.md)
- [Observabilidad](observability.md)
- [Resiliencia y escalado](resilience-and-scaling.md)
- [Despliegue](deployment.md)
- [Testing](testing.md)
