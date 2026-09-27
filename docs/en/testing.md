# Testing

Mercado is tested in layers, each cheaper and faster than the next: plain unit tests of the domain model, ArchUnit rules that keep the layers honest, `@WebMvcTest` slices that check the HTTP contract of each controller, integration tests that run a service against real PostgreSQL, Kafka and RabbitMQ in Testcontainers, a contract test for the shared messages and a cross-generation test between Spring Boot 4 and 3.5. The frontend has its own checks. On top of that, a separate suite of system tests drives a deployed platform through the gateway, including stress scenarios with injected faults, and ends every scenario by checking the same cross-service invariants: every order is confirmed, rejected or cancelled consistently in every service.

## The pyramid

| Layer | Tooling | What it covers | Runs in |
|---|---|---|---|
| Domain unit tests | JUnit 5, AssertJ | Aggregates, value objects and state machines, with no Spring context | `./gradlew build` |
| Architecture | ArchUnit | Layer dependencies and constructor injection in every service; message contract conventions | `./gradlew build` |
| Web slices | `@WebMvcTest`, MockMvc, mocked buses | HTTP to command/query translation, `@FilterableQuery` parsing, validation, problem details | `./gradlew build` |
| Integration | `@SpringBootTest`, Testcontainers (PostgreSQL 17, Kafka, RabbitMQ) | Real buses, real SQL, real brokers, one service at a time | `./gradlew build` (needs Docker) |
| Cross-generation | Testcontainers 1.x on Boot 3.5 | Events written by Jackson 3 read by the Boot 3.5 / Jackson 2 service | `./gradlew build` |
| Frontend | Vitest, ESLint, Prettier, Angular build | Stores, API clients, filter serialization, pages | `npm run check` |
| System | JUnit 5 tagged `system`, plain HTTP through the gateway | Customer journeys, chaos and stress against a running platform | `./gradlew :system-tests:systemTest` |

## Domain unit tests

Examples: [`OrderTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/OrderTest.java), [`CheckoutSagaTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/CheckoutSagaTest.java) (every transition of the saga, including the ones that must be refused), `ProductTest` and `MoneyTest` (catalog), `StockItemTest` (inventory), `PaymentTest` (payments), `NotificationTest` (notifications), and in the platform `EventSourcedAggregateTest`, `EventTypeRegistryTest`, `QueryPlansTest`, `ChaosTest` and `MicrometerTraceCarrierTest`.

## Architecture tests

Each service has an `ArchitectureTest` that applies the shared rules from [`ShopArchitectureRules`](../../platform/test-support/src/main/java/com/borjaglez/shop/testsupport/architecture/ShopArchitectureRules.java):

```java
class ArchitectureTest {

  @ArchTest
  static final ArchRule layers = ShopArchitectureRules.layersOf("com.borjaglez.shop.orders");

  @ArchTest static final ArchRule constructorInjection = ShopArchitectureRules.noFieldInjection();
}
```

[`ContractsConventionsTest`](../../platform/contracts/src/test/java/com/borjaglez/shop/contracts/ContractsConventionsTest.java) checks every message in `platform/contracts`: public, annotated with `@CqrsMessage`, instantiable by deserializers (no-argument constructor) and free of Spring, Jackson (2 or 3) and JPA types.

## Web slices

`ProductControllerTest`, `OrderControllerTest`, `InventoryControllerTest`, `PaymentControllerTest`, `ReportingControllerTest` and `NotificationControllerTest` run the controller alone with mocked `CommandBus` and `QueryBus`, checking which message is dispatched for each request and how the answer is rendered. `service-support` registers its web auto-configuration for the `@WebMvcTest` slice, so these tests see the real correlation filter, `@CurrentUser` resolution and problem details; specification-repository does the same with `HttpFilterAutoConfiguration`, so `@FilterableQuery` parameters, including composed annotations such as `@ProductFilter`, resolve and reject disallowed fields as in the running service. `service-support` itself tests them with `ProblemDetailsExceptionHandlerTest`, `CorrelationIdFilterTest`, `CurrentUserArgumentResolverTest`, `ChaosControllerTest`, `ChaosDisabledTest` and `ShopDefaultsTest`.

## Integration tests

Tests named `*IT` start real infrastructure with Testcontainers and need a running Docker daemon. [`platform/test-support`](../../platform/test-support/src/main/java/com/borjaglez/shop/testsupport) provides one configuration per dependency, pinned to the same versions as Compose and Kubernetes and wired through `@ServiceConnection`:

| Configuration | Image |
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

Spring's test context cache keeps one set of containers per distinct configuration, so test classes that share a configuration share the containers.

| Area | Tests |
|---|---|
| es-kit | `EventStoreIT`, `AggregateStoreIT` (optimistic concurrency), `OutboxRelayIT` (global order, stop on failure and retry, correlation id and trace of each event, chaos faults, metrics), `IdempotentConsumerIT` |
| catalog | `CatalogApiIT` (HTTP to PostgreSQL end to end), `CatalogCommandsIT`, `CatalogQueriesIT`, `ProductRepositoryIT` and `ProductQueryIT` (DSL semantics on PostgreSQL), `CatalogEventsOverKafkaIT` |
| orders | `OrdersApiIT`, `OrderCommandsIT`, `OrderQueriesIT`, `OrderViewProjectorIT`, `CatalogProductProjectorIT` (out-of-order catalog events), `OrdersOverKafkaIT`, `CqrsActuatorIT` |
| checkout saga | `CheckoutSagaIT`, `CheckoutOverRabbitIT`, `DueCheckoutsIT`, `CheckoutTracingIT`; see [Checkout saga](checkout-saga.md#tests) |
| inventory | `InventoryCommandsIT`, `InventoryMessagingIT` (products from Kafka, commands over RabbitMQ) |
| payments | `PaymentCommandsIT`, `PaymentsOverRabbitIT` |
| reporting | `ReportsIT`, `RebuildIT` (clear, replay from offset 0 with a real Kafka, same numbers) |
| notifications | `NotificationsIT`, `CqrsActuatorIT` (Boot 3 starter), `CrossGenerationIT` |
| gateway | `GatewayRoutingIT` (routing, user header, correlation id, 404 and 503 problems); `ExternalTraceHeadersFilterTest` for the trace headers |

The saga tests replace inventory and payments with [`FakeCheckout`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/FakeCheckout.java), which answers per order as each test scripts it, and drive the saga one step at a time with [`CheckoutDriver`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutDriver.java) while the scheduled runner is off (`shop.checkout.enabled=false`).

### notifications-service

The Boot 3.5 service cannot use `test-support`, which is built against Boot 4. Its [`TestContainers`](../../services/notifications-service/src/test/java/com/borjaglez/shop/notifications/TestContainers.java) configuration uses Testcontainers 1.x, managed by the Boot 3.5 BOM, with the same PostgreSQL and Kafka images.

### Cross-generation

[`CrossGenerationIT`](../../services/notifications-service/src/test/java/com/borjaglez/shop/notifications/application/CrossGenerationIT.java) publishes, on a real Kafka, event payloads exactly as the Boot 4 services write them with Jackson 3 (nanosecond ISO instants, numeric amounts, records for order lines, taken from a real orders event store), with the headers spring-boot-cqrs adds, and checks that the Boot 3.5 / Jackson 2 service turns them into notices. The only difference from production is who wrote the bytes.

## Frontend

```bash
cd frontend
npm ci
npm run check      # prettier --check, eslint, vitest, production build
```

Vitest specs cover the stores (cart, user, notifications), the API clients, the filter serializer, the checkout steps and history timeline, the catalog search, and the product, Filter Lab and Chaos pages.

## System tests

[`system-tests`](../../system-tests/src/test/java/com/borjaglez/shop/system) holds JUnit tests tagged `system` that call a running platform, Compose or Kubernetes, through the gateway, the way the shop does: [`ShopClient`](../../system-tests/src/test/java/com/borjaglez/shop/system/ShopClient.java) sends `X-Shop-User` and reads plain JSON, sharing no classes with the services. The regular build skips them.

```bash
./gradlew :system-tests:systemTest -Pshop.baseUrl=http://localhost:8080
```

Every test creates its own products (for seller `seller-ana`, with their own stock) and customers, so tests never share stock, turns off every chaos fault when done, and ends with [`Checkouts.assertConsistent`](../../system-tests/src/test/java/com/borjaglez/shop/system/Checkouts.java), the invariants of a settled order across services:

| Saga mode | Order view | Payment | Reservation |
|---|---|---|---|
| `CHECKOUT` | `CONFIRMED` | exactly one, `AUTHORIZED` | `RESERVED` |
| `REJECTING` | `REJECTED` | none, `DECLINED` or `REFUNDED` | none or `RELEASED` |
| `CANCELLING` | `CANCELLED` | `REFUNDED` | `RELEASED` |

A saga must settle within four minutes (retries and compensations included) and read models must follow within 60 seconds. `assertStock` checks that the warehouse never promises more than it has and holds exactly what confirmed orders took.

| Test | Scenarios |
|---|---|
| [`CheckoutJourneyTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/CheckoutJourneyTest.java) | A paid order is confirmed and holds its stock; an order above the card limit is rejected and releases its stock; an order for more than the stock is rejected without charging; a cancelled order is refunded and gives its stock back; an order cannot be cancelled while its checkout runs; a customer cannot see someone else's order |
| [`ChaosJourneyTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ChaosJourneyTest.java) | Every card declined; payments failing and coming back; slow payments that time out without anybody paying twice; relay paused while sagas finish and reports catch up later; duplicated events counted once. Needs `SHOP_CHAOS_ENABLED=true` |
| [`ScarceStockStressTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ScarceStockStressTest.java) | Many customers compete for the last units: only as many orders as units go through; cancelling every confirmed order gives all the stock back |
| [`ChaosStormStressTest`](../../system-tests/src/test/java/com/borjaglez/shop/system/ChaosStormStressTest.java) | A burst of orders under duplicated events, slow reservations, intermittent payment failures and a paused relay; afterwards every order is consistent and reports count each confirmed order exactly once |

A change that touches the saga, the outbox or concurrency should come with a scenario here.

## Commands

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, Spotless check, unit, ArchUnit, slice and integration tests (Docker required) |
| `./gradlew test` | Tests only |
| `./gradlew :services:orders-service:test --tests "*CheckoutSagaIT"` | One test class |
| `./gradlew :services:catalog-service:test --tests "*ProductControllerTest.searchTurnsHttpFiltersIntoAQueryPlan"` | One test method |
| `./gradlew :platform:es-kit:test` | Tests of one module |
| `./gradlew spotlessApply` | Format Java code (Google Java Format) |
| `./gradlew :system-tests:systemTest -Pshop.baseUrl=http://localhost:8080` | System and stress tests against a running platform |
| `cd frontend && npm run check` | Prettier, ESLint, Vitest and production build |

## Related

- [Architecture](architecture.md#layers-inside-a-service)
- [Checkout saga](checkout-saga.md#tests)
- [Read models](read-models.md)
- [Resilience and scaling](resilience-and-scaling.md#stress-tests)
- [Deployment](deployment.md#continuous-integration)
