<h1 align="center">Mercado · Spring Boot microservices reference</h1>

<p align="center">
  A complete marketplace built with Spring Boot microservices: CQRS, event sourcing, a transactional
  outbox, an orchestrated saga, Kafka, RabbitMQ, PostgreSQL, OpenTelemetry, Kubernetes and GraalVM.
  <br>
  The reference example of <a href="https://github.com/borja-glez/spring-boot-cqrs"><b>spring-boot-cqrs</b></a>
  and <a href="https://github.com/borja-glez/spring-boot-specification-repository"><b>spring-boot-specification-repository</b></a>.
</p>

<p align="center">
  <a href="README.es.md">Leer en español</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#observability">Observability</a> ·
  <a href="#documentation">Documentation</a>
</p>

<p align="center">
  <a href="https://github.com/borja-glez/spring-boot-shop-microservices-architecture-example/actions/workflows/ci.yml"><img src="https://github.com/borja-glez/spring-boot-shop-microservices-architecture-example/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0%20%7C%203.5-6db33f" alt="Spring Boot 4.0 and 3.5">
  <img src="https://img.shields.io/badge/Kafka-4.3-231f20" alt="Kafka">
  <img src="https://img.shields.io/badge/RabbitMQ-4.3-ff6600" alt="RabbitMQ">
  <img src="https://img.shields.io/badge/PostgreSQL-17-336791" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/OpenTelemetry-traces%20%C2%B7%20metrics%20%C2%B7%20logs-7b61ff" alt="OpenTelemetry">
  <img src="https://img.shields.io/badge/GraalVM-native-f29111" alt="GraalVM native">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License"></a>
</p>

<p align="center">
  <img src="docs/assets/diagrams/architecture.svg" alt="Architecture of Mercado: browser, frontend, gateway, six services with their own PostgreSQL databases, RabbitMQ for saga commands, Kafka for integration events and an OpenTelemetry pipeline into Grafana" width="100%">
</p>

## The two libraries

Mercado exists to show, in a system with real moving parts, how two open-source libraries fit into a
Spring Boot microservices architecture. Every command, query and event of the platform travels through
the first one; every database read goes through the second one.

<table>
<tr>
<td width="50%" valign="top">

### [spring-boot-cqrs](https://github.com/borja-glez/spring-boot-cqrs)

Command, query and event buses for Spring Boot 3 and 4, with a middleware pipeline and pluggable
transports.

- `CommandBus`, `QueryBus` and `EventBus` with annotation-driven handlers.
- Middleware for validation, context propagation (correlation ids) and Micrometer observations.
- **RabbitMQ** request/reply for commands and **Kafka** for events, as drop-in transports.
- Actuator endpoint, GraalVM native hints, Boot 3 and Boot 4 starters.

In Mercado: controllers only dispatch commands and queries, the checkout saga talks to inventory and
payments over RabbitMQ, and integration events flow over Kafka.

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-boot4-starter:0.3.1")
implementation("com.borjaglez.cqrs:spring-boot-cqrs-rabbitmq:0.3.1")
implementation("com.borjaglez.cqrs:spring-boot-cqrs-kafka:0.3.1")
```

</td>
<td width="50%" valign="top">

### [spring-boot-specification-repository](https://github.com/borja-glez/spring-boot-specification-repository)

A fluent, type-safe query DSL for Spring Data JPA, with an HTTP filter syntax protected by field
whitelists.

- `repository.query().where(...).leftFetch(...).findOne()` instead of derived or `@Query` methods.
- `?filter=price.amount:between:5|20&orFilter=...&sort=...` parsed into an immutable `QueryPlan`.
- Per-endpoint whitelists of filterable and sortable fields.
- Grouping and aggregates (`COUNT DISTINCT`, `HAVING`) for facets and reports.

In Mercado: the catalog search, facets, "my orders", the event store explorer, the backoffice and
every report are specification-repository queries composed with server-side conditions.

```kotlin
implementation("com.borjaglez.specrepository:specification-repository-boot4-starter:0.3.1")
implementation("com.borjaglez.specrepository:specification-repository-http:0.3.1")
```

</td>
</tr>
</table>

<p align="center">
  <img src="docs/assets/diagrams/libraries.svg" alt="How spring-boot-cqrs and spring-boot-specification-repository serve one request" width="100%">
</p>

Read [how the libraries are integrated](docs/en/libraries.md) for the code behind every box.

## What this example shows

| Area | Practice | Where to look |
|---|---|---|
| **Service design** | Database per service, hexagonal layers enforced with ArchUnit, RFC 9457 errors with stable codes | [Architecture](docs/en/architecture.md) |
| **CQRS** | Controllers dispatch commands and queries; separate write models and read models | [Libraries](docs/en/libraries.md) |
| **Event sourcing** | Orders and payments stored as event streams with optimistic concurrency; full history per order | [Event sourcing and outbox](docs/en/event-sourcing-and-outbox.md) |
| **Reliable messaging** | Transactional outbox (the event store itself), `SKIP LOCKED` relay, at-least-once delivery, idempotent consumers | [Event sourcing and outbox](docs/en/event-sourcing-and-outbox.md) |
| **Distributed transactions** | Orchestrated, persisted checkout saga over RabbitMQ with retries, backoff and compensations | [Checkout saga](docs/en/checkout-saga.md) |
| **Read models** | Projections tolerant to out-of-order events, reports rebuilt by replaying Kafka from offset 0, live notifications over SSE | [Read models](docs/en/read-models.md) |
| **Querying** | HTTP filters, whitelists, server-side conditions, facets and aggregated reports | [Querying](docs/en/querying.md) |
| **Observability** | One trace per order across HTTP, RabbitMQ, Kafka, outbox and saga; RED and business metrics; logs linked to traces; dashboards and alerts | [Observability](docs/en/observability.md) |
| **Resilience** | Timeouts, idempotency, compensations that never give up, a chaos panel to break things on purpose | [Resilience and scaling](docs/en/resilience-and-scaling.md) |
| **Scalability** | Stateless services, saga runners and outbox relays that share work across replicas, PDBs and HPAs | [Resilience and scaling](docs/en/resilience-and-scaling.md) |
| **Delivery** | Docker Compose, Kubernetes with Kustomize overlays, Paketo images, GraalVM native images | [Deployment](docs/en/deployment.md) |
| **Interoperability** | A Spring Boot 3.5 / Jackson 2 service consuming events written by Spring Boot 4 / Jackson 3 services | [Read models](docs/en/read-models.md) |
| **Testing** | Unit, ArchUnit, Testcontainers integration tests, cross-generation tests, system and stress tests | [Testing](docs/en/testing.md) |

## Quick start

**Requirements:** JDK 21 and Docker (Docker Desktop with about 8 GB of memory). Nothing else: the
frontend is built inside Docker and the libraries come from Maven Central.

```bash
git clone https://github.com/borja-glez/spring-boot-shop-microservices-architecture-example.git
cd spring-boot-shop-microservices-architecture-example

./gradlew buildImages          # builds the seven service images with Paketo buildpacks
docker compose -f deploy/compose/compose.yaml \
               -f deploy/compose/compose.observability.yaml up -d --build
```

The first run downloads the base images and takes a few minutes. Then open:

| URL | What |
|---|---|
| <http://localhost:4200> | **The shop**: catalog, cart, orders with the live saga, notifications, backoffice, filter lab, chaos panel |
| <http://localhost:3000> | **Grafana**: *Dashboards > Shop*, traces in Tempo, logs in Loki, alert rules |
| <http://localhost:8080/api/catalog/products> | The public API, through the gateway |
| <http://localhost:15672> | RabbitMQ management (`shop` / `shop-dev`) |
| <http://localhost:8090> | Kafka UI: the `shop.events` topic |

Stop everything with `docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.observability.yaml down`
(add `-v` to delete the data). Kubernetes, native images and local development are covered in
[Deployment](docs/en/deployment.md).

<p align="center">
  <img src="docs/assets/demo-checkout.gif" alt="Placing an order: the checkout saga reserves stock, authorizes the payment and confirms the order while notifications arrive live" width="100%">
</p>

## A guided tour

Pick a user in the header (customers such as `cliente-lucia` place orders, sellers such as `seller-ana` own products) and:

1. **Buy something.** Add products to the cart and check out. The order page follows the saga step by
   step (reserve stock, authorize payment, confirm) and shows the order's event stream, each event
   marked as published or still in the outbox. The bell receives the notification over SSE.
2. **Get rejected.** Orders above 300 EUR exceed the demo card limit: the saga releases the stock and
   rejects the order with the reason. Lower a product's stock in *Stock* and order more than what is
   left to see an out-of-stock rejection.
3. **Cancel.** Cancel a confirmed order: the saga refunds the payment and releases the stock (*Payments*
   shows it as refunded).
4. **Break things.** In *Chaos*, take payments down or make them slow, pause the outbox relay ("Kafka
   down") or duplicate every event. Checkouts retry and recover; reports and notifications freeze and
   catch up in order; duplicates are ignored.
5. **Rebuild a read model.** In *Reports*, "Rebuild from Kafka" empties the reporting projections and
   replays the topic from the first event.
6. **Learn the query syntax.** The *Filter lab* sends ready-made catalog queries, shows the results
   and the RFC 9457 problems returned for invalid filters.
7. **Follow the order in Grafana.** Open *Shop · Checkout saga & messaging*, then jump from a log line to
   its trace: one order is one trace, from the browser request to the last Kafka consumer.

| Catalog with facets | Order page: saga and event stream |
|---|---|
| ![Catalog](docs/assets/screenshots/shop-catalog.png) | ![Order page](docs/assets/screenshots/shop-order-saga.png) |
| **Chaos panel** | **Reports rebuilt from Kafka** |
| ![Chaos panel](docs/assets/screenshots/shop-chaos.png) | ![Reports](docs/assets/screenshots/shop-reports.png) |

## Architecture

| Service | Port | Owns | Talks through | Stack |
|---|---|---|---|---|
| `gateway-service` | 8080 | routing, correlation ids, trace entry point | HTTP | Boot 4, Spring Cloud Gateway |
| `catalog-service` | 8081 | products, sellers, categories, facets | publishes product events (outbox → Kafka) | Boot 4 |
| `orders-service` | 8082 | event-sourced orders, checkout saga, order read model | RabbitMQ commands to inventory and payments; order events to Kafka; consumes product and order events | Boot 4 |
| `inventory-service` | 8083 | stock and all-or-nothing reservations | answers `ReserveStock`/`ReleaseStock`; stock events to Kafka | Boot 4 |
| `payments-service` | 8084 | event-sourced payments, refunds, card limit | answers `AuthorizePayment`/`RefundPayment`; payment events to Kafka | Boot 4 |
| `notifications-service` | 8085 | notices per customer, live SSE stream | consumes order and payment events | **Boot 3.5**, Jackson 2 |
| `reporting-service` | 8086 | sales, products, rejections and customer reports | consumes order events; rebuilds by replaying Kafka | Boot 4 |
| `frontend` | 4200 | Angular 22 SPA served by nginx | proxies `/api` to the gateway | Angular, nginx |

Three communication styles, each where it fits:

- **HTTP** from the browser, through the gateway, for queries and user commands.
- **RabbitMQ request/reply** for the saga's commands: point-to-point, with an answer, idempotent per order.
- **Kafka** for integration events: published through the outbox, replayable, consumed by idempotent
  projections.

<table>
<tr>
<td width="50%"><img src="docs/assets/diagrams/outbox.svg" alt="Transactional outbox and idempotent consumers"></td>
<td width="50%"><img src="docs/assets/diagrams/checkout-saga.svg" alt="Checkout saga state machine with compensations"></td>
</tr>
<tr>
<td align="center"><a href="docs/en/event-sourcing-and-outbox.md">Transactional outbox and idempotent consumers</a></td>
<td align="center"><a href="docs/en/checkout-saga.md">Orchestrated checkout saga</a></td>
</tr>
</table>

<p align="center">
  <img src="docs/assets/diagrams/cqrs-event-sourcing.svg" alt="Write side and read side of orders: event-sourced aggregate, event store, projection and queries" width="100%">
</p>

## Observability

Set one variable, `OTEL_EXPORTER_OTLP_ENDPOINT`, and every service exports **traces, metrics and logs**
over OTLP to the Grafana LGTM stack (the Compose observability file and the Kubernetes overlays set it
for you). The trace context is carried by hand where work is deferred, through the outbox and the saga,
so **one order is one trace** across HTTP, RabbitMQ, Kafka and background threads, and every log line
links to it.

<p align="center">
  <img src="docs/assets/diagrams/observability.svg" alt="Telemetry pipeline from the services through the OpenTelemetry Collector into Tempo, Prometheus, Loki and Grafana" width="100%">
</p>

Beyond the standard HTTP, JVM, database and broker metrics, the platform publishes the signals that
matter for this architecture: outbox backlog and age, store-to-Kafka delay, events applied and
duplicates ignored per consumer, read-model delay (the eventual-consistency window), Kafka consumer lag,
checkout outcomes, rejection reasons, duration and stuck sagas. Two dashboards and five alert rules are
provisioned.

| Services overview | Checkout saga & messaging |
|---|---|
| ![Services overview dashboard](docs/assets/screenshots/grafana-overview.png) | ![Checkout and messaging dashboard](docs/assets/screenshots/grafana-messaging.png) |
| **One order, one trace** | **Logs linked to traces** |
| ![Distributed trace of one order](docs/assets/screenshots/grafana-trace.png) | ![Logs in Loki](docs/assets/screenshots/grafana-logs.png) |

Details, metric names and how to explore them: [Observability](docs/en/observability.md).

## Run it your way

| Goal | Command |
|---|---|
| Everything in Docker, with observability | `docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.observability.yaml up -d --build` |
| Only the infrastructure, services from the IDE | `docker compose -f deploy/compose/compose.infra.yaml up -d`, then `./gradlew :services:<name>:bootRun` |
| Kubernetes (Docker Desktop) | `./gradlew buildImages`, `docker build -t shop/frontend:0.1.0-SNAPSHOT frontend`, then `kubectl apply -k deploy/k8s/overlays/local` |
| Kubernetes, replicated and observable | `kubectl apply -k deploy/k8s/overlays/scaled` |
| GraalVM native images | `./gradlew buildImages -Pnative`, then `SHOP_IMAGE_SUFFIX=-native docker compose ...` or `overlays/native` |

<p align="center">
  <img src="docs/assets/diagrams/deployment.svg" alt="Docker Compose and Kubernetes deployment options" width="100%">
</p>

## Project structure

```
build-logic/                 Gradle convention plugins (Java, libraries, Boot 4 and Boot 3 services)
platform/
  contracts/                 messages exchanged between services (@CqrsMessage)
  es-kit/                    event store that doubles as outbox, relay, idempotent consumers, metrics
  service-support/           RFC 9457 errors, correlation, current user, QueryPlans, OTLP export, chaos
  test-support/              Testcontainers for PostgreSQL, Kafka and RabbitMQ, ArchUnit rules
services/
  gateway-service/           single public entry point
  catalog-service/           products, facets and the catalog search
  orders-service/            event-sourced orders, checkout saga, order read model
  inventory-service/         stock and reservations
  payments-service/          event-sourced payments
  notifications-service/     live notices (Spring Boot 3.5, Jackson 2, SSE)
  reporting-service/         reports from its own projections, rebuildable from Kafka
frontend/                    Angular 22 SPA
deploy/compose/              Docker Compose: infrastructure, platform, observability
deploy/k8s/                  Kustomize: base, overlays (local, native, observability, scaled), components
system-tests/                journeys and stress tests against a running platform
docs/                        documentation in English (docs/en) and Spanish (docs/es), diagrams, screenshots
```

## Build and test

```bash
./gradlew build                     # compile, Spotless, unit, ArchUnit and Testcontainers tests (needs Docker)
./gradlew :services:orders-service:test --tests "*CheckoutSagaIT"
./gradlew :system-tests:systemTest -Pshop.baseUrl=http://localhost:8080   # against a running platform
cd frontend && npm ci && npm run check                                     # Prettier, ESLint, Vitest, build
```

See [Testing](docs/en/testing.md) for the strategy behind each layer.

## Documentation

| Guide | Contents |
|---|---|
| [Architecture](docs/en/architecture.md) | Services, layers, communication styles, errors, correlation, shared defaults |
| [The libraries in practice](docs/en/libraries.md) | How spring-boot-cqrs and spring-boot-specification-repository are wired and used |
| [Event sourcing and outbox](docs/en/event-sourcing-and-outbox.md) | Event store, aggregates, relay, delivery guarantees, ordering |
| [Checkout saga](docs/en/checkout-saga.md) | Orchestration, compensations, retries, idempotency, scaling the runner |
| [Read models](docs/en/read-models.md) | Projections, reports rebuilt from Kafka, live notifications |
| [Querying](docs/en/querying.md) | HTTP filter syntax, whitelists, server-side conditions, facets |
| [Observability](docs/en/observability.md) | Traces, metrics, logs, dashboards, alerts |
| [Resilience and scaling](docs/en/resilience-and-scaling.md) | Failure modes, chaos panel, replicas, native images |
| [Deployment](docs/en/deployment.md) | Compose, Kubernetes, configuration, local development |
| [Testing](docs/en/testing.md) | Test pyramid, Testcontainers, system and stress tests |

## Beyond the demo

Mercado keeps a few things deliberately simple so the architecture stays in focus. A production system
would add authentication (an OAuth2/OIDC resource server instead of the `X-Shop-User` header), secrets
from a vault or an external secrets operator, replicated brokers and databases, tail-based trace
sampling, alert routing and TLS everywhere. Each guide lists what changes for production.

## License

[Apache License 2.0](LICENSE).
