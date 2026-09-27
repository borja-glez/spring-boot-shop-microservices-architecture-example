# The libraries in practice

Mercado is the reference application of two open-source libraries by Borja González, both published on Maven Central in version 0.4.0: [spring-boot-cqrs](https://github.com/borja-glez/spring-boot-cqrs) (`com.borjaglez.cqrs`) and [spring-boot-specification-repository](https://github.com/borja-glez/spring-boot-specification-repository) (`com.borjaglez.specrepository`). spring-boot-cqrs gives every service its command, query and event buses, runs them locally or over RabbitMQ and Kafka, and adds validation, context propagation and observability around every handler. specification-repository is the only way the services read their databases: a fluent query DSL on Spring Data JPA repositories, plus an HTTP filter syntax that turns request parameters into whitelisted query plans. This page shows how both are wired and used, with the real code behind each piece.

<p align="center"><img src="../assets/diagrams/libraries.svg" alt="How spring-boot-cqrs and spring-boot-specification-repository serve one request" width="100%"></p>

## Dependencies

Versions come from [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) (`cqrs = "0.4.0"`, `specrepo = "0.4.0"`).

| Artifact | Used by | Purpose |
|---|---|---|
| `spring-boot-cqrs-core` | `platform/contracts` (API), `platform/es-kit` | `Command`, `Query`, `Event`, `@CqrsMessage`, `MessageContext`, `MessageSerializer` |
| `spring-boot-cqrs-boot4-starter` | every Boot 4 service | Buses, handler discovery, middleware, actuator endpoint, AOT hints |
| `spring-boot-cqrs-boot3-starter` | `notifications-service` | The same for Spring Boot 3.5 / Jackson 2 |
| `spring-boot-cqrs-rabbitmq` | orders, inventory, payments | `RabbitMqCommandBus` for the saga's request/reply commands |
| `spring-boot-cqrs-kafka` | every service except the gateway | Event topic, listener container, `KafkaMessagePublisher` used by the outbox |
| `specification-repository-boot4-starter` | Boot 4 services, `es-kit` | `SpecificationRepository`, DSL, query plans |
| `specification-repository-boot3-starter` | `notifications-service` | The same for Spring Boot 3.5 |
| `specification-repository-http` | every service with a filterable endpoint | `@FilterableQuery`, HTTP filter parser |

To build against local checkouts of the libraries instead (for example to try unreleased library changes), see [Deployment](deployment.md#building-against-local-checkouts-of-the-libraries).

## spring-boot-cqrs

### Message contracts

Messages that cross a process boundary live in [`platform/contracts`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts). They extend the library's `Event` or `Command`, declare their wire name with `@CqrsMessage`, keep a no-argument constructor for deserializers and use no Spring or Jackson types, because they are read by Boot 4 / Jackson 3 and Boot 3.5 / Jackson 2 services alike:

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

With `cqrs.naming.prefix: shop` the wire name of this event is `shop.orders.1.event.order.order-placed` (the `1` is the default `version` of `@CqrsMessage`). The event store keeps that name, never the Java class name, so classes can move without rewriting history. Replies to saga commands are plain records such as [`StockReservation`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts/inventory/StockReservation.java) and [`PaymentAuthorization`](../../platform/contracts/src/main/java/com/borjaglez/shop/contracts/payments/PaymentAuthorization.java). [`ContractsConventionsTest`](../../platform/contracts/src/test/java/com/borjaglez/shop/contracts/ContractsConventionsTest.java) checks all four rules with ArchUnit.

Commands and queries that never leave their service (for example `CreateProductCommand` or `SearchProductsQuery`) stay in the service's `application` package and carry no `@CqrsMessage`.

### Controllers dispatch, handlers decide

Controllers only translate HTTP into messages. They inject `CommandBus` and `QueryBus` and use `dispatchAndReceive` (command with a result), `dispatchAndWait` (command without one) and `ask` (query):

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

(from [`ProductController`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/api/ProductController.java))

Handlers are plain classes in `application`, annotated `@CommandHandler` or `@QueryHandler`, with one `@HandleCommand` or `@HandleQuery` method per message. The method's transaction is the unit of work:

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

| Service | Command handlers | Query handlers |
|---|---|---|
| catalog | [`CatalogCommandHandler`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/application/command/CatalogCommandHandler.java) | [`CatalogQueryHandler`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/application/query/CatalogQueryHandler.java) |
| orders | [`OrderCommandHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/command/OrderCommandHandler.java) | [`OrderQueryHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/query/OrderQueryHandler.java) |
| inventory | [`InventoryCommandHandler`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/command/InventoryCommandHandler.java) | [`InventoryQueryHandler`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/query/InventoryQueryHandler.java) |
| payments | [`PaymentCommandHandler`](../../services/payments-service/src/main/java/com/borjaglez/shop/payments/application/command/PaymentCommandHandler.java) | [`PaymentQueryHandler`](../../services/payments-service/src/main/java/com/borjaglez/shop/payments/application/query/PaymentQueryHandler.java) |
| notifications | [`NotificationHandler`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/NotificationHandler.java) (both) | |
| reporting | | [`ReportsHandler`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/query/ReportsHandler.java) |

The same `@HandleCommand` methods of inventory and payments answer the commands that arrive over RabbitMQ: the library receives the message, dispatches it on the local bus and sends the method's return value back as the reply.

### Event handlers as projectors

Events consumed from Kafka are dispatched to `@EventHandler` classes with one `@HandleEvent` method per event type. In Mercado they are projectors that update a local read model, always through es-kit's [`IdempotentConsumer`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/IdempotentConsumer.java):

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

(abridged from [`OrderViewProjector`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/projection/OrderViewProjector.java))

Other projectors: `CatalogProductProjector` (orders), `CatalogProductsProjector` (inventory), `ReportProjector` (reporting) and `NotificationProjector` (notifications, on Boot 3.5). See [Read models](read-models.md).

### Middleware

Every message dispatched on a local bus, including commands received over RabbitMQ and events received from Kafka, passes through a middleware chain. The starters register the library's middleware when their prerequisites are present, and `service-support` adds one of its own:

| Middleware | Source | Effect |
|---|---|---|
| `CommandValidationInterceptor` | spring-boot-cqrs (when a `Validator` exists) | Validates commands with Bean Validation before the handler runs; a violation becomes `ConstraintViolationException`, rendered as 400 `validation-failed` by `ValidationProblemMapper`. `CreateProductCommand` relies on it (`@NotBlank`, `@Size`, `@Positive`...). |
| `ContextPropagationMiddleware` | spring-boot-cqrs | Carries the `MessageContext` (correlation id and related entries) from the dispatcher to the handler and across brokers. `service-support` seeds it with the request's correlation id. |
| `TracingMiddleware` | spring-boot-cqrs (when an `ObservationRegistry` exists) | Wraps each handling in the observation `cqrs.bus.handle` (span and timer), tagged `cqrs.message.kind` and `cqrs.message.type`. |
| `MicrometerBusObservability` | spring-boot-cqrs (when a `MeterRegistry` exists) | Times each dispatch as `cqrs.bus.dispatch`, tagged `cqrs.type`, `cqrs.message` and `cqrs.outcome`. |
| [`MessageChaosMiddleware`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/MessageChaosMiddleware.java) | service-support (demo) | Delays or fails the message types listed in `shop.chaos.messages`, so the caller sees a slow or failing service. See [Resilience and scaling](resilience-and-scaling.md#chaos-panel). |

A custom middleware is just a `BusMiddleware` bean:

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

### RabbitMQ request/reply for the saga

The checkout saga reaches inventory and payments through two ports, `InventoryGateway` and `PaymentsGateway`. Their RabbitMQ implementation, [`RabbitCheckoutGateways`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/RabbitCheckoutGateways.java), is a thin layer over `RabbitMqCommandBus.dispatchAndReceive`:

```java
@Override
public StockReservation reserve(UUID orderId, List<ReservationLine> lines) {
  return reply(commands.dispatchAndReceive(new ReserveStock(orderId, lines)), "ReserveStock");
}
```

A missing reply (`RemoteReplyTimeoutException`, after `spring.rabbitmq.template.reply-timeout`, 5 seconds by default) or a failure in the remote handler (`RemoteHandlerException`) reaches the saga as an exception, which it retries. A business refusal is never an exception: it comes back as a value (`StockReservation.reserved() == false`, `PaymentAuthorization.authorized() == false`). See [Checkout saga](checkout-saga.md).

Services that receive objects over RabbitMQ restrict deserialization to the contract packages and plain Java types. Spring AMQP compares whole package names, so each subpackage is listed:

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

### Kafka for events only

Every service that publishes or consumes events declares the Kafka module and turns off Kafka transport for commands and queries:

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

All events share one topic, `shop.events`. Handlers never publish to it directly: they record events in the outbox, and the relay publishes them through the library's `KafkaMessagePublisher`, wrapped in es-kit's [`KafkaOutboxDestination`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/kafka/KafkaOutboxDestination.java), which returns only once the broker acknowledged the record. Each service consumes the topic with its own consumer group through the library's listener container (bean `cqrsKafkaEventListenerContainer`), which `reporting-service` stops, rewinds and restarts to rebuild its reports. Events are keyed by message name, so ordering is guaranteed per event type; projections are written to tolerate out-of-order delivery across types. See [Event sourcing and outbox](event-sourcing-and-outbox.md#ordering).

### Actuator endpoint

`management.endpoints.web.exposure.include` contains `cqrs` in every service. `GET /actuator/cqrs` describes the buses of the running service: handler counts per kind, every handler with its message type, and the middleware chain (including which entries are observability middleware). `GET /actuator/cqrs/handlers/{kind}` lists one kind (`command`, `query`, `event`), and `/actuator/info` includes a `cqrs` summary. `CqrsActuatorIT` in orders (Boot 4) and in notifications (Boot 3.5) checks the endpoint in a real service.

### Native images

Contracts a service only sends or records, never handles, still need reflection hints in a native image. `ShopDefaultsEnvironmentPostProcessor` sets `cqrs.aot.message-packages=com.borjaglez.shop.contracts`, so the starter registers hints for every contract at build time. See [Resilience and scaling](resilience-and-scaling.md#graalvm-native-images).

### Observation names

| Name | Kind | Tags |
|---|---|---|
| `cqrs.bus.handle` | observation: span plus timer (`cqrs_bus_handle_milliseconds_*` in Prometheus) | `cqrs.message.kind`, `cqrs.message.type`, `error` |
| `cqrs.bus.dispatch` | timer | `cqrs.type`, `cqrs.message`, `cqrs.outcome` |

The "Shop · Checkout saga & messaging" dashboard charts handled messages, handler failures and p95 handling time from `cqrs.bus.handle`. See [Observability](observability.md).

## spring-boot-specification-repository

### Repositories

Every repository extends `SpecificationRepository` and declares no methods: there are no derived queries and no `@Query` annotations anywhere in the code base.

```java
/**
 * Products. Every read goes through the specification-repository DSL ({@link #query()}) or a {@code
 * QueryPlan}; there are deliberately no derived or {@code @Query} methods.
 */
public interface ProductRepository extends SpecificationRepository<Product, UUID> {}
```

This includes the platform: es-kit's `StoredEventRepository` and `ProcessedMessageRepository` read the event store and the idempotency markers the same way. The only SQL written by hand is where the DSL cannot express row locking: the outbox relay's `FOR UPDATE SKIP LOCKED` batch and the saga runner's claim statement.

### Fluent DSL

Server-side reads use `repository.query()`: conditions with `where`, joins and fetches, sorting, and a terminal operation (`findOne`, `findAll`, `findSlice`, `count`).

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

Other examples: loading a stream in version order ([`EventStore.load`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventStore.java)), checking ownership (`ownedProduct` in `CatalogCommandHandler`), counting pending outbox rows ([`OutboxMetrics`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxMetrics.java)) and reading the oldest one with `findSlice(PageRequest.of(0, 1))`.

### HTTP filters with `@FilterableQuery`

List endpoints accept the HTTP syntax of the `-http` module. `@FilterableQuery` parses `filter`, `orFilter` and `sort` into a `QueryPlan` of the given entity, restricted to the declared fields, and rejects any other field before the handler runs:

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

| Parameter | Syntax | Meaning |
|---|---|---|
| `filter` (repeatable) | `field:operator:value` | Every `filter` must match (AND) |
| `orFilter` (repeatable) | `a:op:v;b:op:v` | At least one condition of each group must match |
| `sort` (repeatable) | `field,asc` or `field,desc` | Order; only `sortableFields` |
| `page`, `size` | integers | Paging; `size` is capped at 100 |

Operators: `eq`, `neq`, `contains`, `notcontains`, `startswith`, `endswith`, `gt`, `gte`, `lt`, `lte`, `between`, `in`, `notin`, `isnull`, `isnotnull`, `isempty`, `isnotempty`. List operators (`between`, `in`, `notin`) separate values with `|`; the last four take no value.

The controller passes the `Pageable` as it is: Spring builds its sort from the same `sort` parameter, and the repository checks it against the plan's whitelist too.

Endpoints that expose the same entity share one declaration through a composed annotation, since `@FilterableQuery` works as a meta-annotation. The catalog search and its facets both use [`@ProductFilter`](../../services/catalog-service/src/main/java/com/borjaglez/shop/catalog/api/ProductFilter.java), which also declares the text fields matched ignoring case and accents (`caseInsensitiveFields`), and lets the search add its sortable fields through an `@AliasFor` attribute:

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

The four order reports share [`@OrderReportFilter`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/api/OrderReportFilter.java) the same way. Whitelists per endpoint:

| Endpoint | Filterable | Sortable |
|---|---|---|
| `GET /api/catalog/products`, `/facets` (`@ProductFilter`) | `name`, `description`, `sku`, `price.amount`, `status`, `categories.slug`, `tags`, `seller.id`, `seller.city`, `publishedAt` | search: `name`, `sku`, `price.amount`, `publishedAt`; facets: none |
| `GET /api/orders` | `status`, `total`, `currency`, `placedAt`, `lines.sku` | `placedAt`, `total`, `status` |
| `GET /api/orders/events` | `eventType`, `streamType`, `streamId`, `version`, `occurredAt`, `publishedAt`, `publishAttempts` | `globalPosition`, `occurredAt` |
| `GET /api/inventory/stock` | `sku`, `name`, `onHand`, `reserved`, `updatedAt` | same |
| `GET /api/inventory/reservations` | `orderId`, `status`, `reservedAt`, `releasedAt`, `lines.sku` | `reservedAt`, `releasedAt` |
| `GET /api/payments` | `orderId`, `customerId`, `status`, `reason`, `amount`, `currency`, `updatedAt` | `amount`, `createdAt`, `updatedAt` |
| `GET /api/reporting/summary`, `/sales-by-day`, `/rejections`, `/customers` (`@OrderReportFilter`) | `placedAt`, `placedDay`, `currency` | none |
| `GET /api/reporting/top-products` | `order.placedAt`, `order.placedDay`, `sku`, `name` | none |

Any other field, a malformed parameter, an unknown operator or a value that cannot be converted to the field's type is answered with 400 `invalid-filter` ([`SpecificationQueryProblemMapper`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/web/problem/SpecificationQueryProblemMapper.java) maps `DisallowedFieldException` and `InvalidFilterException`). See [Querying](querying.md).

### Server conditions on a client plan

A client plan often needs conditions the client must not control: the owner of the data, the status of what is public, the columns a projection reads. Handlers derive the client plan with `repository.query(plan)`, which keeps the client's filters, sort and whitelist and adds to them:

| On the derived query | Effect | Used in |
|---|---|---|
| `where(...)` | a **server condition**: ANDed with the client's filters, not checked against the whitelist (it may use a field the client cannot filter by) and never widened by a client `orFilter` | "My orders" (`customerId = user`), catalog (`status = ACTIVE`), every report |
| `sortedByDefault(sort)` | a sort used only when the client asked for none; it must be one of the sortable fields | orders (newest first), event store, inventory, payments |
| `leftFetch(paths...)` | fetch joins | catalog search fetches `seller` |
| `select(fields...).selectInto(type)` | reads the given columns straight into a record, without managed entities | payments list |
| `groupBy`, `select`, aggregates, `having`, then `findRows()` / `findRow()` | a grouped query over the same rows | catalog facets and categories, all reports |

"My orders":

```java
return views
    .query(query.getPlan())
    .where("customerId", Operators.EQUALS, query.getCustomerId())
    .sortedByDefault(NEWEST_ORDERS)
    .findAll(query.getPageable())
    .map(OrderViews::summary);
```

`?filter=customerId:eq:someone-else` is still a 400, since `customerId` is not in the whitelist, and `?orFilter=status:eq:PLACED;status:eq:CONFIRMED` only matches the current customer's orders.

The payments backoffice reads rows straight into its response record:

```java
return views
    .query(query.getPlan())
    .sortedByDefault(NEWEST_FIRST)
    .select(SUMMARY_FIELDS)
    .selectInto(PaymentSummary.class)
    .findAll(query.getPageable());
```

Disjunctive facets need the opposite of a server condition: each facet drops the client's own filter on its field. The derived query cannot remove client filters, so [`QueryPlans.without`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/query/QueryPlans.java) in `service-support` does it, keeping everything else of the plan (see [Querying](querying.md#facets)).

### Facets and reports: grouping and aggregates

A query with selections or aggregates returns rows, not entities: `findRows()` reads them as `GroupedRow`s, `findRow()` reads the first one. The client decides which rows (its filters), the server decides what is grouped and summed: grouping, selections, aggregates and `having` are always added in code.

```java
return placed(query.getPlan(), ReportStatus.CONFIRMED)   // orders.query(plan) + server conditions
    .groupBy("placedDay", "currency")
    .select("placedDay", "currency")
    .countAs("orders", "orderId")
    .countDistinctAs("customers", "customerId")
    .sumAs("revenue", "total")
    .findRows()
    .stream()...
```

(from [`ReportsHandler.salesByDay`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/query/ReportsHandler.java))

The whitelist also covers the fields of `having`. The top products report keeps the products that sold at least `minUnits` with `having(SUM, "quantity", ...)`, and `quantity` is not a client filter, so `topProducts` checks the client plan against its whitelist (`allowedFieldsPolicy().validate(plan)`) and then lets the grouped query use every field.

Catalog facets use the same mechanism with `countDistinctAs` per category, seller and tag, and `minAs`/`maxAs` with `findRow()` for the price range. See [Querying](querying.md#facets) and [Read models](read-models.md#reporting-service).

### Every read goes through it

| Read | How |
|---|---|
| Public catalog search, facets, categories | `query(plan)...findAll(pageable)`, `findRows()`, `findRow()` |
| Product detail, ownership checks, duplicate SKU | `query()...findOne()` / `count()` |
| Loading an event-sourced aggregate | `EventStore.load`: `query().where(...).sort(Sort.by("version")).findAll()` |
| Optimistic concurrency check on append | `query()...count()` of the stream's versioned rows |
| Idempotency markers | `IdempotentConsumer`: `query()...count()` |
| Outbox metrics | `count()` of pending rows, `findSlice` for the oldest |
| Read models and projections | `query()...findOne()` in every projector |
| Saga lookup and metrics | `CheckoutSagaRepository.query()` |
| Reports | `query(plan)...findRows()` with `HAVING` |
| Payments list | `query(plan)...selectInto(...).findAll(pageable)` |

## Related

- [Architecture](architecture.md)
- [Querying](querying.md)
- [Event sourcing and outbox](event-sourcing-and-outbox.md)
- [Checkout saga](checkout-saga.md)
- [Read models](read-models.md)
- [Observability](observability.md)
- [spring-boot-cqrs on GitHub](https://github.com/borja-glez/spring-boot-cqrs)
- [spring-boot-specification-repository on GitHub](https://github.com/borja-glez/spring-boot-specification-repository)
