# Las librerías en la práctica

Mercado es la aplicación de referencia de dos librerías open source de Borja González, ambas publicadas en Maven Central en la versión 0.4.0: [spring-boot-cqrs](https://github.com/borja-glez/spring-boot-cqrs) (`com.borjaglez.cqrs`) y [spring-boot-specification-repository](https://github.com/borja-glez/spring-boot-specification-repository) (`com.borjaglez.specrepository`). spring-boot-cqrs da a cada servicio sus buses de comandos, consultas y eventos, los ejecuta en local o sobre RabbitMQ y Kafka, y añade validación, propagación de contexto y observabilidad alrededor de cada handler. specification-repository es la única forma en que los servicios leen sus bases de datos: un DSL de consultas fluido sobre repositorios Spring Data JPA, más una sintaxis de filtros HTTP que convierte los parámetros de la petición en planes de consulta con lista blanca. Esta página muestra cómo se configuran y se usan ambas, con el código real que hay detrás de cada pieza.

<p align="center"><img src="../assets/diagrams/libraries.svg" alt="Cómo spring-boot-cqrs y spring-boot-specification-repository atienden una petición" width="100%"></p>

## Dependencias

Las versiones vienen de [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) (`cqrs = "0.4.0"`, `specrepo = "0.4.0"`).

| Artefacto | Lo usa | Propósito |
|---|---|---|
| `spring-boot-cqrs-core` | `platform/contracts` (API), `platform/es-kit` | `Command`, `Query`, `Event`, `@CqrsMessage`, `MessageContext`, `MessageSerializer` |
| `spring-boot-cqrs-boot4-starter` | todos los servicios Boot 4 | Buses, descubrimiento de handlers, middleware, endpoint de Actuator, hints AOT |
| `spring-boot-cqrs-boot3-starter` | `notifications-service` | Lo mismo para Spring Boot 3.5 / Jackson 2 |
| `spring-boot-cqrs-rabbitmq` | catalog, orders, inventory, payments, notifications | `RabbitMqCommandBus` para los comandos petición/respuesta de la saga, `RabbitMqQueryBus` para las consultas a otros servicios |
| `spring-boot-cqrs-kafka` | todos los servicios salvo el gateway | Topic de eventos, contenedor de listeners, `KafkaMessagePublisher` usado por el outbox |
| `specification-repository-boot4-starter` | servicios Boot 4, `es-kit` | `SpecificationRepository`, DSL, planes de consulta |
| `specification-repository-boot3-starter` | `notifications-service` | Lo mismo para Spring Boot 3.5 |
| `specification-repository-http` | todos los servicios con un endpoint filtrable | `@FilterableQuery`, parser de filtros HTTP |

Para compilar contra copias locales de las librerías (por ejemplo, para probar cambios aún no publicados), consulta [Despliegue](deployment.md#compilar-contra-copias-locales-de-las-librerías).

## spring-boot-cqrs

### Contratos de mensajes

Los mensajes que cruzan una frontera de proceso viven en [`platform/contracts`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts). Extienden `Event` o `Command` de la librería, declaran su nombre en el cable con `@CqrsMessage`, mantienen un constructor sin argumentos para los deserializadores y no usan tipos de Spring ni de Jackson, porque los leen por igual servicios Boot 4 / Jackson 3 y Boot 3.5 / Jackson 2:

```java
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event {

  private UUID orderId;
  private String customerId;
  private List<OrderLine> lines;
  private BigDecimal total;
  private String currency;
}
```

Con `cqrs.naming.prefix: shop`, el nombre en el cable de este evento es `shop.orders.1.event.order.order-placed` (el `1` es la `version` por defecto de `@CqrsMessage`). El event store guarda ese nombre, nunca el nombre de la clase Java, así que las clases pueden moverse sin reescribir el historial. Las respuestas a los comandos de la saga son records simples como [`StockReservation`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts/inventory/StockReservation.java) y [`PaymentAuthorization`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts/payments/PaymentAuthorization.java). [`ContractsConventionsTest`](../../platform/contracts/src/test/java/com/borjaglez/shop/contracts/ContractsConventionsTest.java) comprueba las cuatro reglas con ArchUnit.

Los comandos y consultas que nunca salen de su servicio (por ejemplo `CreateProductCommand` o `SearchProductsQuery`) se quedan en el paquete `application` del servicio y no llevan `@CqrsMessage`.

### Los controladores despachan, los handlers deciden

Los controladores solo traducen HTTP a mensajes. Inyectan `CommandBus` y `QueryBus` y usan `dispatchAndReceive` (comando con resultado), `dispatchAndWait` (comando sin resultado) y `ask` (consulta):

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
CreatedResponse create(
    @CurrentUser String seller, @Valid @RequestBody CreateProductRequest body) {
  UUID id =
      commands.dispatchAndReceive(
          new CreateProductCommand(
              seller, body.sku(), body.name(), body.description(),
              body.price(), body.currency(), body.categories(), body.tags()));
  return new CreatedResponse(id);
}

@PostMapping("/{id}/publish")
@ResponseStatus(HttpStatus.NO_CONTENT)
void publish(@CurrentUser String seller, @PathVariable UUID id) {
  commands.dispatchAndWait(new PublishProductCommand(id, seller));
}
```

(de [`ProductController`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/api/ProductController.java))

Los handlers son clases simples en `application`, anotadas con `@CommandHandler` o `@QueryHandler`, con un método `@HandleCommand` o `@HandleQuery` por mensaje. La transacción del método es la unidad de trabajo:

```java
@CommandHandler
public class CatalogCommandHandler {

  @HandleCommand
  @Transactional
  public void changePrice(ChangeProductPriceCommand command) {
    Product product = ownedProduct(command.getProductId(), command.getSellerId());
    product.changePrice(new Money(command.getPrice(), command.getCurrency()), clock);
    publish(product);   // records the events in the outbox, same transaction
  }
}
```

| Servicio | Handlers de comandos | Handlers de consultas |
|---|---|---|
| catalog | [`CatalogCommandHandler`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/application/command/CatalogCommandHandler.java) | [`CatalogQueryHandler`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/application/query/CatalogQueryHandler.java) |
| orders | [`OrderCommandHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/command/OrderCommandHandler.java) | [`OrderQueryHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/query/OrderQueryHandler.java) |
| inventory | [`InventoryCommandHandler`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/command/InventoryCommandHandler.java) | [`InventoryQueryHandler`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/query/InventoryQueryHandler.java) |
| payments | [`PaymentCommandHandler`](../../services/payments-service/src/main/java/com/borjaglez/shop/payments/application/command/PaymentCommandHandler.java) | [`PaymentQueryHandler`](../../services/payments-service/src/main/java/com/borjaglez/shop/payments/application/query/PaymentQueryHandler.java) |
| notifications | [`NotificationHandler`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/NotificationHandler.java) (ambos) | |
| reporting | | [`ReportsHandler`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/query/ReportsHandler.java) |

Los mismos métodos `@HandleCommand` de inventory y payments responden a los comandos que llegan por RabbitMQ: la librería recibe el mensaje, lo despacha en el bus local y devuelve como respuesta el valor de retorno del método.

### Event handlers como proyectores

Los eventos consumidos desde Kafka se despachan a clases `@EventHandler` con un método `@HandleEvent` por tipo de evento. En Mercado son proyectores que actualizan un modelo de lectura local, siempre a través del [`IdempotentConsumer`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/IdempotentConsumer.java) de es-kit:

```java
@EventHandler
public class OrderViewProjector {

  static final String CONSUMER = "orders.order-view";

  @HandleEvent
  public void on(OrderConfirmed event) {
    apply(event, event.getOrderId(), view -> view.confirmed(event.getPaymentId(), at(event)));
  }

  private void apply(Event event, UUID orderId, Consumer<OrderView> change) {
    idempotent.once(CONSUMER, event, () -> {
      OrderView view = views.query()
          .where("orderId", Operators.EQUALS, orderId)
          .findOne()
          .orElseGet(() -> OrderView.unknown(orderId));
      change.accept(view);
      views.save(view);
    });
  }
}
```

(resumido de [`OrderViewProjector`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/projection/OrderViewProjector.java))

Otros proyectores: `CatalogProductProjector` (orders), `CatalogProductsProjector` (inventory), `ReportProjector` (reporting) y `NotificationProjector` (notifications, sobre Boot 3.5). Consulta [Modelos de lectura](read-models.md).

### Middleware

Todo mensaje despachado en un bus local, incluidos los comandos recibidos por RabbitMQ y los eventos recibidos de Kafka, pasa por una cadena de middleware. Los starters registran el middleware de la librería cuando sus requisitos están presentes, y `service-support` añade uno propio:

| Middleware | Origen | Efecto |
|---|---|---|
| `CommandValidationInterceptor` | spring-boot-cqrs (cuando existe un `Validator`) | Valida los comandos con Bean Validation antes de que se ejecute el handler; una violación se convierte en `ConstraintViolationException`, que `ValidationProblemMapper` devuelve como 400 `validation-failed`. `CreateProductCommand` se apoya en ello (`@NotBlank`, `@Size`, `@Positive`...). |
| `ContextPropagationMiddleware` | spring-boot-cqrs | Transporta el `MessageContext` (correlation id y entradas relacionadas) desde quien despacha hasta el handler y a través de los brokers. `service-support` lo inicializa con el correlation id de la petición. |
| `TracingMiddleware` | spring-boot-cqrs (cuando existe un `ObservationRegistry`) | Envuelve cada tratamiento en la observación `cqrs.bus.handle` (span y timer), etiquetada con `cqrs.message.kind` y `cqrs.message.type`. |
| `MicrometerBusObservability` | spring-boot-cqrs (cuando existe un `MeterRegistry`) | Mide cada despacho como `cqrs.bus.dispatch`, etiquetado con `cqrs.type`, `cqrs.message` y `cqrs.outcome`. |
| [`MessageChaosMiddleware`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/MessageChaosMiddleware.java) | service-support (demo) | Retrasa o hace fallar los tipos de mensaje listados en `shop.chaos.messages`, para que quien llama vea un servicio lento o que falla. Consulta [Resiliencia y escalado](resilience-and-scaling.md#panel-de-caos). |

Un middleware propio no es más que un bean `BusMiddleware`:

```java
@Override
public Object process(Object message, MiddlewareChain chain) throws Exception {
  String type = message.getClass().getSimpleName();
  Faults faults = byType.get(type);
  if (faults != null) {
    long millis = faults.delay().delay().toMillis();
    if (millis > 0) {
      Thread.sleep(millis);
    }
    if (faults.fail().active()) {
      throw new ChaosException("Chaos: " + type + " failed on purpose");
    }
  }
  return chain.proceed(message);
}
```

### Petición/respuesta sobre RabbitMQ para la saga

La saga de checkout llega a inventory y payments a través de dos puertos, `InventoryGateway` y `PaymentsGateway`. Su implementación RabbitMQ, [`RabbitCheckoutGateways`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/RabbitCheckoutGateways.java), es una capa fina sobre `RabbitMqCommandBus.dispatchAndReceive`:

```java
@Override
public StockReservation reserve(UUID orderId, List<ReservationLine> lines) {
  return reply(commands.dispatchAndReceive(new ReserveStock(orderId, lines)), "ReserveStock");
}
```

Una respuesta que no llega (`RemoteReplyTimeoutException`, tras `spring.rabbitmq.template.reply-timeout`, 5 segundos por defecto) o un fallo en el handler remoto (`RemoteHandlerException`) llega a la saga como una excepción, que la saga reintenta. Un rechazo de negocio nunca es una excepción: vuelve como un valor (`StockReservation.reserved() == false`, `PaymentAuthorization.authorized() == false`). Consulta [Saga de checkout](checkout-saga.md).

Los servicios que reciben objetos por RabbitMQ restringen la deserialización a los paquetes de contratos y a tipos Java simples. Spring AMQP compara nombres de paquete completos, así que se lista cada subpaquete:

```yaml
cqrs:
  rabbitmq:
    prefix: shop
    trusted-packages:
      - com.borjaglez.shop.contracts.inventory
      - com.borjaglez.shop.contracts.payments
      - com.borjaglez.shop.contracts.orders
      - java.util
      - java.lang
```

### Petición/respuesta sobre RabbitMQ para consultas

Tres páginas leen datos que son de otro servicio, mediante consultas enviadas por RabbitMQ con `RabbitMqQueryBus`:

| Consulta (contrato) | La hace | La responde | Se ve en |
|---|---|---|---|
| `GetStockLevels` → `StockLevels` | catalog, orders | inventory | la ficha de producto, el carrito |
| `GetOrderNotices` → `OrderNotices` | orders | notifications (Boot 3.5, Jackson 2) | la página del pedido |

Los contratos viven en `platform/contracts` como los comandos, anotados con `@CqrsMessage`. Esa anotación es lo que los expone: con la exposición por defecto de la librería (`cqrs.rabbitmq.expose=annotated`), un servicio solo enlaza a su cola los mensajes anotados, así que sus propias consultas (`SearchStockQuery`, `MyNotificationsQuery`...) se quedan en el bus local aunque el bus de consultas de RabbitMQ esté activo. En el lado que responde, una consulta remota es un método `@HandleQuery` normal:

```java
@HandleQuery
@Transactional(readOnly = true)
public StockLevels levels(GetStockLevels query) {
  return new StockLevels(
      stock.query().where("productId", Operators.IN, query.getProductIds()).findAll().stream()
          .map(item -> new StockLevel(item.getProductId(), item.available()))
          .toList());
}
```

En el lado que pregunta, la consulta pasa por un puerto de la capa de aplicación (`StockLevelsGateway`, `OrderNoticesGateway`), implementado en `infrastructure` sobre RabbitMQ ([`RabbitStockLevelsGateway`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/infrastructure/messaging/RabbitStockLevelsGateway.java) en catalog, [`RabbitRemoteQueries`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/messaging/RabbitRemoteQueries.java) en orders). Los controladores siguen usando el `QueryBus` local; el handler local compone la respuesta:

- La ficha de producto se lee en una transacción de solo lectura corta y el stock se pregunta después, así que ninguna conexión a la base de datos espera al broker.
- Un timeout, un fallo remoto o un broker inalcanzable devuelven un `Optional` vacío: la página muestra el stock como desconocido en lugar de fallar.

El timeout de respuesta de un `RabbitTemplate` de Spring es un único ajuste para todas las peticiones que pasan por él, y orders necesita dos: la saga espera hasta 5 segundos por un comando, y un comprador no debería esperar más que un momento por una página. Por eso los servicios que preguntan montan un segundo `RabbitMqQueryBus` sobre un template propio, configurado por el `RabbitTemplateConfigurer` de Boot igual que el de por defecto, cambiando solo el timeout ([`RemoteQueriesConfiguration`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/messaging/RemoteQueriesConfiguration.java)):

```java
RabbitTemplate template = new RabbitTemplate();
configurer.configure(template, connectionFactory);
template.setReplyTimeout(replyTimeout.toMillis()); // shop.remote-queries.reply-timeout, 1 s
RabbitMqQueryBus remoteQueries =
    new RabbitMqQueryBus(
        new RabbitMqPublisher(template, contextHeaderPrefix),
        rabbitNaming,
        messageNaming,
        properties.getQueries().getExchange(),
        middlewares.getIfAvailable(Collections::emptyList));
```

Catalog y notifications solo participan en consultas, así que desactivan los buses de comandos y eventos de RabbitMQ (`cqrs.rabbitmq.commands.enabled=false`, `cqrs.rabbitmq.events.enabled=false`).

`GetOrderNotices` cruza las dos generaciones de Jackson en los dos sentidos: Jackson 3 escribe la consulta y lee la respuesta, Jackson 2 hace lo contrario. El conversor JSON que Spring AMQP monta por su cuenta escribe los valores de fecha y hora de Java como números con Jackson 2 (`1790676930.123456000`) y como texto ISO-8601 con Jackson 3, así que notifications declara su `cqrsMessageConverter` con el `ObjectMapper` de Spring Boot, que también escribe ISO-8601 ([`MessagingConfiguration`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/infrastructure/MessagingConfiguration.java)). `OrderNoticesOverRabbitIT` envía la consulta con los bytes que escribe Jackson 3 y comprueba el JSON en bruto de la respuesta.

### Kafka solo para eventos

Cada servicio que publica o consume eventos declara el módulo de Kafka y desactiva el transporte Kafka para comandos y consultas:

```yaml
cqrs:
  naming:
    prefix: shop
  kafka:
    prefix: shop
    # Kafka carries integration events only; the checkout saga's commands go over RabbitMQ.
    commands:
      enabled: false
    queries:
      enabled: false
```

Todos los eventos comparten un único topic, `shop.events`. Los handlers nunca publican en él directamente: registran los eventos en el outbox, y el relay los publica mediante el `KafkaMessagePublisher` de la librería, envuelto en el [`KafkaOutboxDestination`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/kafka/KafkaOutboxDestination.java) de es-kit, que solo retorna cuando el broker ha confirmado el registro. Cada servicio consume el topic con su propio consumer group a través del contenedor de listeners de la librería (bean `cqrsKafkaEventListenerContainer`), que `reporting-service` detiene, rebobina y vuelve a arrancar para reconstruir sus informes. Los eventos usan como clave el nombre del mensaje, de modo que el orden está garantizado por tipo de evento; las proyecciones están escritas para tolerar la entrega desordenada entre tipos. Consulta [Event sourcing y outbox](event-sourcing-and-outbox.md#orden).

### Endpoint de Actuator

`management.endpoints.web.exposure.include` contiene `cqrs` en todos los servicios. `GET /actuator/cqrs` describe los buses del servicio en ejecución: número de handlers por tipo, cada handler con su tipo de mensaje y la cadena de middleware (indicando qué entradas son middleware de observabilidad). `GET /actuator/cqrs/handlers/{kind}` lista un tipo (`command`, `query`, `event`), y `/actuator/info` incluye un resumen `cqrs`. `CqrsActuatorIT`, en orders (Boot 4) y en notifications (Boot 3.5), comprueba el endpoint en un servicio real.

### Imágenes nativas

Los contratos que un servicio solo envía o registra, sin llegar a tratarlos, siguen necesitando hints de reflexión en una imagen nativa. `ShopDefaultsEnvironmentPostProcessor` fija `cqrs.aot.message-packages=com.borjaglez.shop.contracts`, de modo que el starter registra hints para todos los contratos en tiempo de build. Consulta [Resiliencia y escalado](resilience-and-scaling.md#imágenes-nativas-graalvm).

### Nombres de observaciones

| Nombre | Tipo | Etiquetas |
|---|---|---|
| `cqrs.bus.handle` | observación: span más timer (`cqrs_bus_handle_milliseconds_*` en Prometheus) | `cqrs.message.kind`, `cqrs.message.type`, `error` |
| `cqrs.bus.dispatch` | timer | `cqrs.type`, `cqrs.message`, `cqrs.outcome` |

El dashboard "Shop · Checkout saga & messaging" muestra los mensajes tratados, los fallos de handlers y el p95 del tiempo de tratamiento a partir de `cqrs.bus.handle`. Consulta [Observabilidad](observability.md).

## spring-boot-specification-repository

### Repositorios

Todos los repositorios extienden `SpecificationRepository` y no declaran métodos: no hay consultas derivadas ni anotaciones `@Query` en ninguna parte del código.

```java
/**
 * Products. Every read goes through the specification-repository DSL ({@link #query()}) or a {@code
 * QueryPlan}; there are deliberately no derived or {@code @Query} methods.
 */
public interface ProductRepository extends SpecificationRepository<Product, UUID> {}
```

Esto incluye la plataforma: `StoredEventRepository` y `ProcessedMessageRepository` de es-kit leen el event store y las marcas de idempotencia de la misma forma. El único SQL escrito a mano está donde el DSL no puede expresar el bloqueo de filas: el lote `FOR UPDATE SKIP LOCKED` del relay del outbox y la sentencia de reclamación del ejecutor de la saga.

### DSL fluido

Las lecturas en el servidor usan `repository.query()`: condiciones con `where`, joins y fetches, ordenación y una operación terminal (`findOne`, `findAll`, `findSlice`, `count`).

```java
@HandleQuery
@Transactional(readOnly = true)
public ProductDetail product(GetProductQuery query) {
  return products
      .query()
      .where("slug", Operators.EQUALS, query.getSlug())
      .where("status", Operators.NOT_EQUALS, ProductStatus.DRAFT)
      .leftFetch("seller")
      .findOne()
      .map(ProductViews::detail)
      .orElseThrow(() -> new NotFoundException(
          "product-not-found", "No product is published as " + query.getSlug()));
}
```

Otros ejemplos: cargar un stream en orden de versión ([`EventStore.load`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventStore.java)), comprobar la propiedad (`ownedProduct` en `CatalogCommandHandler`), contar las filas pendientes del outbox ([`OutboxMetrics`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxMetrics.java)) y leer la más antigua con `findSlice(PageRequest.of(0, 1))`.

### Filtros HTTP con `@FilterableQuery`

Los endpoints de listado aceptan la sintaxis HTTP del módulo `-http`. `@FilterableQuery` convierte `filter`, `orFilter` y `sort` en un `QueryPlan` de la entidad indicada, restringido a los campos declarados, y rechaza cualquier otro campo antes de que se ejecute el handler:

```java
@GetMapping
PageResponse<OrderSummary> myOrders(
    @CurrentUser String customer,
    @FilterableQuery(
            value = OrderView.class,
            filterableFields = {"status", "total", "currency", "placedAt", "lines.sku"},
            sortableFields = {"placedAt", "total", "status"})
        QueryPlan<OrderView> plan,
    Pageable pageable) {
  Page<OrderSummary> page = queries.ask(new ListMyOrdersQuery(customer, plan, pageable));
  return PageResponse.of(page);
}
```

| Parámetro | Sintaxis | Significado |
|---|---|---|
| `filter` (repetible) | `field:operator:value` | Todos los `filter` deben cumplirse (AND) |
| `orFilter` (repetible) | `a:op:v;b:op:v` | Debe cumplirse al menos una condición de cada grupo |
| `sort` (repetible) | `field,asc` o `field,desc` | Orden; solo `sortableFields` |
| `page`, `size` | enteros | Paginación; `size` tiene un máximo de 100 |

Operadores: `eq`, `neq`, `contains`, `notcontains`, `startswith`, `endswith`, `gt`, `gte`, `lt`, `lte`, `between`, `in`, `notin`, `isnull`, `isnotnull`, `isempty`, `isnotempty`. Los operadores de lista (`between`, `in`, `notin`) separan los valores con `|`; los cuatro últimos no llevan valor.

El controlador pasa el `Pageable` tal cual: Spring construye su ordenación a partir del mismo parámetro `sort`, y el repositorio también la comprueba contra la lista blanca del plan.

Los endpoints que exponen la misma entidad comparten una única declaración mediante una anotación compuesta, ya que `@FilterableQuery` funciona como meta-anotación. La búsqueda del catálogo y sus facetas usan [`@ProductFilter`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/api/ProductFilter.java), que además declara los campos de texto que se comparan sin distinguir mayúsculas ni acentos (`caseInsensitiveFields`), y permite que la búsqueda añada sus campos ordenables mediante un atributo `@AliasFor`:

```java
@FilterableQuery(
    value = Product.class,
    filterableFields = {"name", "description", "sku", "price.amount", /* ... */ "publishedAt"},
    caseInsensitiveFields = {"name", "description"})
@interface ProductFilter {

  @AliasFor(annotation = FilterableQuery.class)
  String[] sortableFields() default {};
}

PageResponse<ProductCard> search(
    @ProductFilter(sortableFields = {"name", "sku", "price.amount", "publishedAt"})
        QueryPlan<Product> plan,
    Pageable pageable) { ... }

CatalogFacets facets(@ProductFilter QueryPlan<Product> plan) { ... }
```

Los cuatro informes de pedidos comparten [`@OrderReportFilter`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/api/OrderReportFilter.java) de la misma forma. Listas blancas por endpoint:

| Endpoint | Filtrables | Ordenables |
|---|---|---|
| `GET /api/catalog/products`, `/facets` (`@ProductFilter`) | `name`, `description`, `sku`, `price.amount`, `status`, `categories.slug`, `tags`, `seller.id`, `seller.city`, `publishedAt` | búsqueda: `name`, `sku`, `price.amount`, `publishedAt`; facetas: ninguno |
| `GET /api/orders` | `status`, `total`, `currency`, `placedAt`, `lines.sku` | `placedAt`, `total`, `status` |
| `GET /api/orders/events` | `eventType`, `streamType`, `streamId`, `version`, `occurredAt`, `publishedAt`, `publishAttempts` | `globalPosition`, `occurredAt` |
| `GET /api/inventory/stock` | `sku`, `name`, `onHand`, `reserved`, `updatedAt` | los mismos |
| `GET /api/inventory/reservations` | `orderId`, `status`, `reservedAt`, `releasedAt`, `lines.sku` | `reservedAt`, `releasedAt` |
| `GET /api/payments` | `orderId`, `customerId`, `status`, `reason`, `amount`, `currency`, `updatedAt` | `amount`, `createdAt`, `updatedAt` |
| `GET /api/reporting/summary`, `/sales-by-day`, `/rejections`, `/customers` (`@OrderReportFilter`) | `placedAt`, `placedDay`, `currency` | ninguno |
| `GET /api/reporting/top-products` | `order.placedAt`, `order.placedDay`, `sku`, `name` | ninguno |

Cualquier otro campo, un parámetro mal formado, un operador desconocido o un valor que no se puede convertir al tipo del campo se responde con 400 `invalid-filter` ([`SpecificationQueryProblemMapper`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/problem/SpecificationQueryProblemMapper.java) traduce `DisallowedFieldException` e `InvalidFilterException`). Consulta [Consultas](querying.md).

### Condiciones del servidor sobre un plan del cliente

Un plan del cliente a menudo necesita condiciones que el cliente no debe controlar: el propietario de los datos, el estado de lo que es público, las columnas que lee una proyección. Los handlers derivan el plan del cliente con `repository.query(plan)`, que conserva los filtros, la ordenación y la lista blanca del cliente y les añade lo siguiente:

| En la consulta derivada | Efecto | Se usa en |
|---|---|---|
| `where(...)` | una **condición del servidor**: se combina con AND con los filtros del cliente, no se comprueba contra la lista blanca (puede usar un campo por el que el cliente no puede filtrar) y un `orFilter` del cliente nunca la amplía | "Mis pedidos" (`customerId = user`), catálogo (`status = ACTIVE`), todos los informes |
| `sortedByDefault(sort)` | una ordenación que solo se usa cuando el cliente no ha pedido ninguna; debe ser uno de los campos ordenables | pedidos (los más recientes primero), event store, inventario, pagos |
| `leftFetch(paths...)` | fetch joins | la búsqueda del catálogo hace fetch de `seller` |
| `select(fields...).selectInto(type)` | lee las columnas indicadas directamente en un record, sin entidades gestionadas | listado de pagos |
| `groupBy`, `select`, agregados, `having` y después `findRows()` / `findRow()` | una consulta agrupada sobre las mismas filas | facetas y categorías del catálogo, todos los informes |

"Mis pedidos":

```java
return views
    .query(query.getPlan())
    .where("customerId", Operators.EQUALS, query.getCustomerId())
    .sortedByDefault(NEWEST_ORDERS)
    .findAll(query.getPageable())
    .map(OrderViews::summary);
```

`?filter=customerId:eq:otra-persona` sigue siendo un 400, porque `customerId` no está en la lista blanca, y `?orFilter=status:eq:PLACED;status:eq:CONFIRMED` solo encuentra pedidos del cliente actual.

El backoffice de pagos lee las filas directamente en su record de respuesta:

```java
return views
    .query(query.getPlan())
    .sortedByDefault(NEWEST_FIRST)
    .select(SUMMARY_FIELDS)
    .selectInto(PaymentSummary.class)
    .findAll(query.getPageable());
```

Las facetas disyuntivas necesitan lo contrario de una condición del servidor: cada faceta prescinde del propio filtro del cliente sobre su campo. La consulta derivada no puede quitar filtros del cliente, así que lo hace [`QueryPlans.without`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/query/QueryPlans.java), en `service-support`, que conserva todo lo demás del plan (consulta [Consultas](querying.md#facetas)).

### Facetas e informes: agrupación y agregados

Una consulta con selecciones o agregados devuelve filas, no entidades: `findRows()` las lee como `GroupedRow`s y `findRow()` lee la primera. El cliente decide qué filas (sus filtros); el servidor decide qué se agrupa y qué se suma: la agrupación, las selecciones, los agregados y `having` siempre se añaden en el código.

```java
return placed(query.getPlan(), ReportStatus.CONFIRMED)   // orders.query(plan) + condiciones del servidor
    .groupBy("placedDay", "currency")
    .select("placedDay", "currency")
    .countAs("orders", "orderId")
    .countDistinctAs("customers", "customerId")
    .sumAs("revenue", "total")
    .findRows()
    .stream()...
```

(de [`ReportsHandler.salesByDay`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/query/ReportsHandler.java))

La lista blanca también cubre los campos de `having`. El informe de productos más vendidos conserva los productos que han vendido al menos `minUnits` con `having(SUM, "quantity", ...)`, y `quantity` no es un filtro del cliente, así que `topProducts` comprueba el plan del cliente contra su lista blanca (`allowedFieldsPolicy().validate(plan)`) y después deja que la consulta agrupada use cualquier campo.

Las facetas del catálogo usan el mismo mecanismo con `countDistinctAs` por categoría, vendedor y etiqueta, y `minAs`/`maxAs` con `findRow()` para el rango de precios. Consulta [Consultas](querying.md#facetas) y [Modelos de lectura](read-models.md#reporting-service).

### Toda lectura pasa por ella

| Lectura | Cómo |
|---|---|
| Búsqueda pública del catálogo, facetas, categorías | `query(plan)...findAll(pageable)`, `findRows()`, `findRow()` |
| Detalle de producto, comprobaciones de propiedad, SKU duplicado | `query()...findOne()` / `count()` |
| Cargar un agregado con event sourcing | `EventStore.load`: `query().where(...).sort(Sort.by("version")).findAll()` |
| Comprobación de concurrencia optimista al añadir | `query()...count()` de las filas versionadas del stream |
| Marcas de idempotencia | `IdempotentConsumer`: `query()...count()` |
| Métricas del outbox | `count()` de las filas pendientes, `findSlice` para la más antigua |
| Modelos de lectura y proyecciones | `query()...findOne()` en cada proyector |
| Búsqueda y métricas de la saga | `CheckoutSagaRepository.query()` |
| Informes | `query(plan)...findRows()` con `HAVING` |
| Listado de pagos | `query(plan)...selectInto(...).findAll(pageable)` |

## Relacionado

- [Arquitectura](architecture.md)
- [Consultas](querying.md)
- [Event sourcing y outbox](event-sourcing-and-outbox.md)
- [Saga de checkout](checkout-saga.md)
- [Modelos de lectura](read-models.md)
- [Observabilidad](observability.md)
- [spring-boot-cqrs en GitHub](https://github.com/borja-glez/spring-boot-cqrs)
- [spring-boot-specification-repository en GitHub](https://github.com/borja-glez/spring-boot-specification-repository)
