# Resilience and scaling

Mercado is built to keep its promises when parts of it fail: an order is never charged without being confirmed, stock is never held for a rejected order, and no event is lost or counted twice. Each failure mode has a specific mechanism that absorbs it (retries with backoff, idempotent commands, compensations that never give up, the transactional outbox, idempotent consumers), and a chaos panel lets anyone trigger those failures on purpose and watch the platform recover. The same mechanisms make the request path safe to run on several replicas: HTTP is stateless, the outbox relay and the saga runner split their work with row locks, and Kafka and RabbitMQ distribute messages among instances. The services also build as GraalVM native images that start in well under a second.

## Failure modes

| Failure | What happens | What absorbs it |
|---|---|---|
| Payments down or slow | `AuthorizePayment` times out after 5 s or fails | The saga retries with backoff (1 s up to 30 s). Authorization is idempotent per order, so a retry never charges twice. After 8 attempts (about two minutes) the order is rejected with `payment-unavailable`, and the saga refunds or voids the payment, so a late authorization cannot charge the customer. |
| Inventory down | `ReserveStock` fails | Same retries; after 8 attempts the order is rejected with `inventory-unavailable`. The release that follows leaves a tombstone, so a late reservation holds nothing. |
| A compensation keeps failing | `RefundPayment` or `ReleaseStock` fails | Compensations never give up. After 20 attempts the saga is `STUCK` (alert "Checkout compensations are stuck") and keeps retrying every 30 s until it succeeds. |
| RabbitMQ unavailable | Saga commands cannot be sent | Every exception of a remote step is a technical failure: the saga retries it like a timeout. |
| Kafka down (`relay.paused`) | The relay cannot publish | Events stay in `event_store` with `published_at` null. The saga continues, since it uses RabbitMQ. Read models, reports and notices freeze and catch up in order when the broker is back. Alert "Events are not reaching Kafka". |
| Duplicate delivery (`relay.duplicate`) | Every event is published twice | Idempotent consumers keep one marker per `(consumer, event id)`; notifications key notices on the event id. Duplicates show up only in `shop.consumer.events{outcome="duplicate"}`. |
| Cards declined (`payments.decline-all`) | Every new payment is declined | A business answer, not a failure: the order is rejected with `card-limit-exceeded` and the stock released. |
| Events out of order | Kafka orders per event type only | Projections keep as-of timestamps and monotonic statuses; see [Event sourcing and outbox](event-sourcing-and-outbox.md#ordering). |
| A product not yet known to inventory | `ReserveStock` for a product whose `ProductPublished` has not arrived | Treated as a technical failure, so the saga retries instead of rejecting the order as out of stock. |
| Contention on scarce stock | Many reservations update the same stock rows | [`StockTransactions`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/command/StockTransactions.java) retries a reservation that lost an optimistic-lock or deadlock race (up to 15 times, with a short random pause) inside inventory, and `hibernate.order_updates` makes every transaction update stock rows in key order, so crossing reservations rarely deadlock. |
| Database connection lost | A query hangs on a dead connection | JDBC `socketTimeout` of 30 s and TCP keep-alive; the readiness probe includes the database, so Kubernetes stops routing traffic to the instance. |
| orders-service restarts mid-checkout | Sagas in flight | The saga is persisted with the order; the runner resumes every saga at its step. A saga claimed by a dead instance returns when its lease expires. |
| A service instance dies mid-relay | Rows published but not marked | The transaction rolls back and the rows are published again; consumers are idempotent. |

## Chaos panel

Every fault starts off. Components register their faults in the [`Chaos`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/Chaos.java) registry at startup and check them on every call; while a fault is off the check costs one volatile read. Each fault is a switch (0 or 1) or a delay in milliseconds (0 to 60 000).

| Fault key | Kind | Services | Registered by | Effect |
|---|---|---|---|---|
| `payments.decline-all` | switch | payments | `PaymentCommandHandler` | The card limit drops to zero: every new payment is declined with `card-limit-exceeded` |
| `AuthorizePayment.fail` / `AuthorizePayment.delay-ms` | switch / delay | payments | `MessageChaosMiddleware` | Authorizations fail technically, or are slowed down (above the 5 s reply timeout the saga sees a timeout) |
| `RefundPayment.fail` / `RefundPayment.delay-ms` | switch / delay | payments | `MessageChaosMiddleware` | Refunds fail or are slow: compensations retry and eventually get stuck |
| `ReserveStock.fail` / `ReserveStock.delay-ms` | switch / delay | inventory | `MessageChaosMiddleware` | Reservations fail or are slow |
| `ReleaseStock.fail` / `ReleaseStock.delay-ms` | switch / delay | inventory | `MessageChaosMiddleware` | Releases fail or are slow |
| `relay.paused` | switch | catalog, orders, inventory, payments | `OutboxRelay` | The relay stops, as if Kafka were down |
| `relay.duplicate` | switch | catalog, orders, inventory, payments | `OutboxRelay` | Every event is published twice |

[`MessageChaosMiddleware`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/MessageChaosMiddleware.java) is a spring-boot-cqrs middleware registering a `.delay-ms` and a `.fail` fault for each simple class name listed in `shop.chaos.messages` (`ReserveStock, ReleaseStock` in inventory, `AuthorizePayment, RefundPayment` in payments). It sits on the local buses, so it also affects the commands that arrive over RabbitMQ: the caller sees a slow service or a remote error, exactly as in a real outage. An injected failure is a [`ChaosException`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/ChaosException.java), deliberately not mapped to a 4xx.

The endpoint, served by [`ChaosController`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/ChaosController.java), exists in the services that set `shop.chaos.service` (catalog, orders, inventory, payments) at `/api/<service>/chaos`:

| Request | Effect |
|---|---|
| `GET /api/payments/chaos` | Lists the faults with key, description, kind and value |
| `PUT /api/payments/chaos/AuthorizePayment.fail` with `{"value": 1}` | Turns a fault on (0 turns it off; delays take milliseconds) |
| `DELETE /api/payments/chaos` | Turns every fault of the service off |

The endpoint answers only when the service started with `SHOP_CHAOS_ENABLED=true` (`shop.chaos.enabled`); otherwise every request gets 404 `chaos-disabled` and no fault can be turned on. The flag is read at startup rather than used as a bean condition, because a native image fixes its conditions at build time. Docker Compose and the local Kubernetes overlays enable it, since they are demo deployments; the endpoint is unauthenticated like the rest of the demo API and would stay disabled anywhere else.

The Chaos page of the shop (`/chaos`) shows every service's faults with an explanation of the expected effect and a button to turn everything off:

![Chaos panel](../assets/screenshots/shop-chaos.png)

## Timeouts

| Timeout | Value | Where |
|---|---|---|
| RabbitMQ reply | 5 s (`CHECKOUT_REPLY_TIMEOUT`) | `spring.rabbitmq.template.reply-timeout` in orders; a missing reply is a retryable failure of the step |
| JDBC socket | 30 s | `socketTimeout` data-source property, from `ShopDefaultsEnvironmentPostProcessor` |
| Hikari connection acquisition | 5 s | `spring.datasource.hikari.connection-timeout` in every service |
| Saga lease | 30 s | `shop.checkout.lease`; longer than any step |
| Shutdown phase | 20 s | `spring.lifecycle.timeout-per-shutdown-phase` |
| SSE connection | 30 min, heartbeat every 20 s | `SseNotificationHub`; nginx `proxy_read_timeout 1h` |

## Graceful shutdown and probes

- `server.shutdown=graceful` lets in-flight requests finish; the relay and saga schedulers are `SmartLifecycle` beans that stop before the database and brokers go away (the saga scheduler waits up to 10 s for a step that is waiting for a reply).
- Deployments set `terminationGracePeriodSeconds: 40` and a `preStop` sleep of 5 s, so the endpoints controller stops routing traffic before the application starts shutting down.
- **Startup probe** on `/actuator/health/liveness` every 5 s, up to 36 failures (3 minutes) for JVM images, 12 for native ones.
- **Readiness probe** on `/actuator/health/readiness`, which includes the database (`readinessState,db`): an instance without its database receives no traffic.
- **Liveness probe** on `/actuator/health/liveness` every 20 s: it only says the process works, so a database outage never restarts pods.
- The relay and the saga runner catch everything, `Error`s included, and keep going: a scheduled executor silently drops a task that throws, which would stop the loop while the service still looked healthy.

## Horizontal scaling

The Kustomize component [`deploy/k8s/components/scaling`](../../deploy/k8s/components/scaling) scales the request path; the overlay [`deploy/k8s/overlays/scaled`](../../deploy/k8s/overlays/scaled/kustomization.yaml) combines it with the local and observability setups:

```bash
kubectl apply -k deploy/k8s/overlays/scaled
```

| Piece | Setting |
|---|---|
| Replicas | 2 of `gateway-service`, `catalog-service`, `orders-service`, `inventory-service`, `payments-service` |
| Rolling updates | `maxSurge: 1`, `maxUnavailable: 0`: capacity never drops during a rollout |
| PodDisruptionBudgets | `minAvailable: 1` for each: node drains and upgrades evict one replica at a time |
| HorizontalPodAutoscalers | 2 to 3 replicas at 70% average CPU, scale-down stabilization of 5 minutes; they need a metrics server (on Docker Desktop, install `metrics-server` with `--kubelet-insecure-tls`) |
| Topology spread | `maxSkew: 1` across `kubernetes.io/hostname`, `ScheduleAnyway` |

### Why each piece is safe to run twice

| Concern | Mechanism |
|---|---|
| HTTP requests | Stateless: no session; the user and the correlation id travel in headers. |
| Outbox relay | Batches are locked with `FOR UPDATE SKIP LOCKED`: replicas share the backlog without publishing a row twice. With several relaying replicas batches may interleave, which consumers already tolerate. |
| Checkout saga runner | Due sagas are claimed with `FOR UPDATE SKIP LOCKED` and leased; each saga runs on one replica at a time, and another takes it over when the lease expires. A step run twice is harmless (version check plus idempotent commands). |
| Kafka consumers | Replicas of a service share its consumer group, so each event reaches one replica; idempotent consumers absorb redeliveries during rebalances. |
| RabbitMQ commands | Competing consumers on one queue per command type; handlers are idempotent per order. |
| Optimistic concurrency | Event streams, projections, sagas and stock rows are versioned: concurrent writers never overwrite each other. |

### What stays single-replica

- `notifications-service` holds SSE connections in memory: a notice reaches the browser tabs connected to the replica that processed the event. Several replicas would need a shared fan-out (for example a broadcast topic or Redis pub/sub).
- `reporting-service` pauses its single consumer for a rebuild, which assumes no other instance consumes with its group.
- The chaos faults live in the memory of each instance, so a switch flipped through the gateway would reach only one replica. The `scaled` overlay therefore starts the services with `SHOP_CHAOS_ENABLED=false`; use Compose or a single-replica overlay for the chaos panel.
- PostgreSQL, RabbitMQ and Kafka run as single-node StatefulSets in the demo; production would use managed or clustered brokers and databases.

### Database connection budget

Each service has a Hikari pool of 10 (`DB_POOL_SIZE`). PostgreSQL runs with `max_connections=250`, set in both Compose and Kubernetes. The scaled services at their maximum of three replicas, plus one surge pod during a rolling update, plus the single-replica services, stay within that limit.

## GraalVM native images

```bash
./gradlew buildImages -Pnative                          # shop/<service>:0.1.0-SNAPSHOT-native
kubectl apply -k deploy/k8s/overlays/native             # or overlays/native-observability
SHOP_IMAGE_SUFFIX=-native docker compose -f deploy/compose/compose.yaml up -d
```

With `-Pnative`, `shop.boot-service-conventions` applies the GraalVM Native Build Tools plugin and `bootBuildImage` compiles inside Paketo, so no local GraalVM is needed. The code targets Java 21; the native build uses GraalVM 25 (`BP_JVM_VERSION=25`), which Paketo requires for Spring Boot 4. Images are built one at a time (the `ImageBuilds` build service in `build-logic`), because several concurrent native builds exhaust Docker Desktop. `notifications-service`, the Spring Boot 3.5 service, has no native build and runs on the JVM in the native overlay too.

Measured on Docker Desktop (16 CPUs, 32 GB) with all services starting at once in Compose:

| | JVM (Paketo, Java 21) | Native (GraalVM 25) |
|---|---|---|
| Application start ("Started ... in") | 6-18 s | 0.11-0.63 s |
| Container memory after an end-to-end run | 244-400 MiB | 73-132 MiB |
| Image build | about 25 s per service | 140-190 s per service |

The native overlay lowers requests and limits to 128/256 MiB and shortens the startup probe accordingly.

What makes the services native-ready:

- **Hints next to the code that needs them.** [`EsKitRuntimeHints`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EsKitRuntimeHints.java) registers es-kit's Flyway migrations (`db/eskit/**`), the id arrays Hibernate creates reflectively when it batch-loads with `default_batch_fetch_size`, and the Flyway exception classes used to report connection failures. spring-boot-cqrs registers hints for every contract through `cqrs.aot.message-packages`.
- **A build-time event index.** A native image cannot scan the classpath, so [`EventTypeIndexAotProcessor`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventTypeIndexAotProcessor.java) scans the event packages during AOT processing, writes the classes to `META-INF/shop/event-types.idx` and registers their binding hints; `EventTypeRegistry` reads the index at runtime and falls back to scanning on the JVM.
- **Runtime switches instead of `@Conditional`.** Conditions are evaluated at build time in a native image, so anything that must be switchable at runtime is a runtime check: the OTLP exporters ([Observability](observability.md#turning-export-on)) and the chaos endpoint.
- **Bytecode enhancement.** `shop.hibernate-enhancement-conventions` enhances the JPA entities at build time, because a native image cannot generate Hibernate proxies at runtime; JVM builds are enhanced too, so both behave alike.
- **No refresh scope in the gateway.** Routes are static configuration and `spring.cloud.refresh.enabled=false`, since refresh scope is not supported in native images.
- **Loops that log `Error`s and keep going.** In a native image a missing hint surfaces as an `Error`. `OutboxRelayScheduler`, `CheckoutSagaScheduler` and `CheckoutSagaRunner` catch and log `Error`s as well as exceptions, so a background loop can never stop silently.
- **Thread dumps.** Native images are built with `--enable-monitoring=threaddump`: `docker kill --signal=QUIT <container>` prints the threads to the log, as on the JVM.

## Stress tests

The system tests include two stress scenarios against a running platform (see [Testing](testing.md#system-tests)):

- `ScarceStockStressTest`: many customers want the last units at once. Exactly as many orders as there are units are confirmed, the rest are rejected as out of stock, nobody pays for a rejected order and stock never goes below zero; cancelling every confirmed order gives all the stock back.
- `ChaosStormStressTest`: a burst of orders while duplicated events, slow reservations, intermittent payment failures and a paused relay hit at once. Once the faults are gone every order ends consistent in every service and the reports count each confirmed order exactly once.

## Related

- [Checkout saga](checkout-saga.md)
- [Event sourcing and outbox](event-sourcing-and-outbox.md)
- [Observability](observability.md)
- [Deployment](deployment.md)
- [Testing](testing.md)
