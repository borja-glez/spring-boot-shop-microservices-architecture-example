# Deployment

Mercado runs the same container images in three ways: infrastructure in Docker with the services started from Gradle or an IDE for development, the whole platform in Docker Compose, or the whole platform in Kubernetes with Kustomize overlays (Docker Desktop's cluster is enough). Service images are built with Cloud Native Buildpacks (Paketo), as JVM or GraalVM native images, without Dockerfiles; the frontend has a small multi-stage Dockerfile. Every setting a deployment needs is an environment variable with a local default, and observability, fault injection and horizontal scaling are optional layers added by a Compose file or a Kustomize component.

<p align="center"><img src="../assets/diagrams/deployment.svg" alt="Docker Compose and Kubernetes deployment options" width="100%"></p>

## Prerequisites

| Tool | Needed for |
|---|---|
| JDK 25 | Building and running the services (the Gradle toolchain requires it). Always use the wrapper, `./gradlew`. |
| Docker (Docker Desktop on Windows and macOS) | Infrastructure, images, Testcontainers-based tests |
| Node 24 | Only for frontend development (`ng serve`, `npm run check`); the frontend image builds with its own Node |
| Kubernetes (optional) | Docker Desktop's built-in cluster, `kubectl` with Kustomize support |

The two libraries come from Maven Central; no other checkout is needed.

## Docker Compose

Five files in [`deploy/compose`](../../deploy/compose):

| File | Contents |
|---|---|
| [`compose.infra.yaml`](../../deploy/compose/compose.infra.yaml) | PostgreSQL 17 (`max_connections=250`, one database per service from the shared init script), RabbitMQ 4.3 with management UI, Kafka 4.3 in KRaft mode (3 partitions, retention `-1`), Kafka UI |
| [`compose.yaml`](../../deploy/compose/compose.yaml) | Includes the infrastructure and adds the seven services and the frontend, with health-based `depends_on`, a 768 MiB memory limit per service and `SHOP_CHAOS_ENABLED=true` |
| [`compose.observability.yaml`](../../deploy/compose/compose.observability.yaml) | Adds `grafana/otel-lgtm` with the shop dashboards and alert rules, and the OTLP variables of every service |
| [`compose.lgtm.yaml`](../../deploy/compose/compose.lgtm.yaml) | `grafana/otel-lgtm` alone, with Grafana on 3000 and OTLP over HTTP on 4318; included by the observability and development files |
| [`compose.dev.yaml`](../../deploy/compose/compose.dev.yaml) | Infrastructure plus `grafana/otel-lgtm`, for running the services and the frontend outside Docker |

```bash
./gradlew buildImages                                  # JVM images shop/<service>:0.1.0-SNAPSHOT
docker compose -f deploy/compose/compose.yaml up -d --build
# with traces, metrics, logs, dashboards and alerts:
docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.observability.yaml up -d
# with native images:
./gradlew buildImages -Pnative
SHOP_IMAGE_SUFFIX=-native docker compose -f deploy/compose/compose.yaml up -d
```

`--build` builds the frontend image from [`frontend/Dockerfile`](../../frontend/Dockerfile). `SHOP_VERSION` and `SHOP_IMAGE_SUFFIX` select the image tag; `notifications-service` always uses its JVM image.

### URLs

| URL | What |
|---|---|
| http://localhost:4200 | The shop (nginx + Angular) |
| http://localhost:8080 | Gateway, for example http://localhost:8080/api/catalog/products |
| http://localhost:8081 ... 8086 | catalog, orders, inventory, payments, notifications and reporting directly (actuator included) |
| http://localhost:15672 | RabbitMQ management (`shop` / `shop-dev`) |
| http://localhost:8090 | Kafka UI |
| http://localhost:3000 | Grafana (with `compose.observability.yaml` or `compose.dev.yaml`) |
| http://localhost:4318 | OTLP over HTTP (with `compose.observability.yaml` or `compose.dev.yaml`) |
| `localhost:5432`, `localhost:5672`, `localhost:9094` | PostgreSQL, AMQP and Kafka for local development |

## Local development

Run the infrastructure and observability in Docker and the services from Gradle or an IDE, where they can be debugged. Every service defaults to `localhost` for PostgreSQL (5432), RabbitMQ (5672) and Kafka (9094, the broker's external listener). The init script creates each database role with the password `shop-dev`, so pass it:

```bash
docker compose -f deploy/compose/compose.dev.yaml up -d  # or compose.infra.yaml, without Grafana
DB_PASSWORD=shop-dev ./gradlew :services:catalog-service:bootRun
DB_PASSWORD=shop-dev ./gradlew :services:orders-service:bootRun
DB_PASSWORD=shop-dev ./gradlew :services:inventory-service:bootRun
DB_PASSWORD=shop-dev ./gradlew :services:payments-service:bootRun
DB_PASSWORD=shop-dev ./gradlew :services:notifications-service:bootRun
DB_PASSWORD=shop-dev ./gradlew :services:reporting-service:bootRun
./gradlew :services:gateway-service:bootRun
cd frontend && npm ci && npm start                     # http://localhost:4200
```

`ng serve` proxies `/api` to the gateway on port 8080 ([`proxy.conf.json`](../../frontend/proxy.conf.json)). To try the chaos panel locally, start the services with `SHOP_CHAOS_ENABLED=true`.

To send traces, metrics and logs to Grafana (http://localhost:3000), add these variables to each run configuration:

| Services | Variables |
|---|---|
| catalog, orders, inventory, payments, reporting, gateway | `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` |
| notifications (Spring Boot 3.5) | `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces`, `MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://localhost:4318/v1/metrics`, `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true`, `MANAGEMENT_OTLP_LOGGING_ENDPOINT=http://localhost:4318/v1/logs` |

Without them the services run the same and export nothing.

## Kubernetes with Kustomize

Manifests live in [`deploy/k8s`](../../deploy/k8s), all in the namespace `shop`:

```
deploy/k8s/
├── base/
│   ├── infra/        postgres, rabbitmq, kafka (StatefulSets with PVCs), kafka-ui, postgres-init ConfigMap
│   ├── apps/         one Deployment + Service per service, and the frontend
│   └── kustomization.yaml   namespace, labels, shop-credentials secret generator
├── components/
│   ├── observability/   otel-lgtm, Grafana provisioning (dashboards, alerts), OTLP env vars
│   └── scaling/         2 replicas, rolling-update strategy, PDBs, HPAs, topology spread
└── overlays/
    ├── local/                  base + local image tags, LoadBalancer for gateway and frontend,
    │                           imagePullPolicy IfNotPresent, SHOP_CHAOS_ENABLED=true
    ├── native/                 local + native image tags, smaller resources, shorter startup probe
    ├── observability/          local + observability component
    ├── native-observability/   native + observability component
    └── scaled/                 local + observability + scaling components
```

```bash
./gradlew buildImages                                   # or -Pnative for the native overlays
docker build -t shop/frontend:0.1.0-SNAPSHOT frontend
kubectl apply -k deploy/k8s/overlays/local              # or native, observability, native-observability, scaled
kubectl -n shop rollout status deploy/catalog-service deploy/orders-service deploy/inventory-service \
  deploy/payments-service deploy/notifications-service deploy/reporting-service deploy/gateway-service
```

Docker Desktop publishes the `LoadBalancer` services on localhost: the shop at http://localhost:4200, the gateway at http://localhost:8080 and, with the observability component, Grafana at http://localhost:3000. Images are named `shop/<service>:0.1.0-SNAPSHOT` (JVM) and `shop/<service>:0.1.0-SNAPSHOT-native` (native), taken from the local Docker daemon.

Every service Deployment runs as non-root with a `RuntimeDefault` seccomp profile, drops all capabilities, and has startup, readiness (with database) and liveness probes, a `preStop` sleep and a 40 s termination grace period. See [Resilience and scaling](resilience-and-scaling.md#graceful-shutdown-and-probes).

**Secrets.** The base generates `shop-credentials` (`postgres-password`, `db-password`, `rabbitmq-password`) with development values. A real environment replaces that generator in its own overlay or uses an external secret operator; production secrets are never committed.

CI renders every overlay with `kubectl kustomize` and validates the Compose files with `docker compose config`.

## Images

| Image | Built by | Notes |
|---|---|---|
| `shop/<service>:0.1.0-SNAPSHOT` | `./gradlew buildImages` (`bootBuildImage` of every service, one at a time) | Paketo buildpacks, Java 25, non-root |
| `shop/<service>:0.1.0-SNAPSHOT-native` | `./gradlew buildImages -Pnative` | GraalVM 25 inside Paketo; not for `notifications-service` |
| `shop/frontend:0.1.0-SNAPSHOT` | `docker build -t shop/frontend:0.1.0-SNAPSHOT frontend` or `docker compose ... --build` | Node 24 build stage, `nginx-unprivileged` on port 8080 |

Requests run on virtual threads, so manifests and Compose set `BPL_JVM_THREAD_COUNT=50`: the Paketo memory calculator then reserves stack space for 50 platform threads instead of its default 250, leaving more heap within the 768 MiB limit.

## Configuration

Every service reads its settings from environment variables with local defaults in its `application.yaml`.

| Variable | Default | Used by |
|---|---|---|
| `DB_HOST`, `DB_PORT` | `localhost`, `5432` | services with a database |
| `DB_NAME`, `DB_USER` | the service name (`catalog`, `orders`...) | services with a database |
| `DB_PASSWORD` | the service name (`shop-dev` in Compose and Kubernetes) | services with a database |
| `DB_POOL_SIZE` | `10` | services with a database |
| `KAFKA_BOOTSTRAP` | `localhost:9094` | every service except the gateway |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD` | `localhost`, `5672`, `shop`, `shop-dev` | orders, inventory, payments |
| `SERVER_PORT` | 8080 ... 8086 | every service |
| `CATALOG_URI`, `ORDERS_URI`, `INVENTORY_URI`, `PAYMENTS_URI`, `NOTIFICATIONS_URI`, `REPORTING_URI` | `http://localhost:808x` | gateway routes |
| `GATEWAY_URL` | `http://gateway-service:8080` | frontend (nginx) |
| `SHOP_CHAOS_ENABLED` | `false` (`true` in Compose and the local overlays) | catalog, orders, inventory, payments |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | unset (no export) | Boot 4 services |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT`, `MANAGEMENT_OTLP_METRICS_EXPORT_URL`, `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED`, `MANAGEMENT_OTLP_LOGGING_ENDPOINT` | unset | notifications (Boot 3.5) |
| `CHECKOUT_REPLY_TIMEOUT` | `5s` | orders |
| `REMOTE_QUERY_TIMEOUT` | `1s` | catalog, orders |
| `CHECKOUT_MAX_ATTEMPTS`, `CHECKOUT_COMPENSATION_MAX_ATTEMPTS` | `8`, `20` | orders |
| `PAYMENTS_CARD_LIMIT` | `300` | payments |
| `INVENTORY_INITIAL_STOCK` | `25` | inventory |
| `BPL_JVM_THREAD_COUNT` | `50` in Compose and Kubernetes | Paketo JVM images |

Compose-level variables: `SHOP_VERSION`, `SHOP_IMAGE_SUFFIX`, `SHOP_DB_PASSWORD`, `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`.

## PostgreSQL initialisation

One script, [`deploy/k8s/base/infra/postgres-init/01-databases.sh`](../../deploy/k8s/base/infra/postgres-init/01-databases.sh), creates one database and one owner role per entry of `SHOP_DATABASES` (`catalog orders inventory payments notifications reporting`) and revokes public access to each. Kubernetes mounts it from the `postgres-init` ConfigMap and Compose mounts the same file into `/docker-entrypoint-initdb.d`, so there is a single copy. It runs once, when the data volume is empty; each service then creates its own schema with Flyway.

## Building against local checkouts of the libraries

To try unreleased library changes, the build can use local checkouts of spring-boot-cqrs and spring-boot-specification-repository instead of the Maven Central artifacts, through a Gradle composite build:

```bash
./gradlew build -Pshop.localLibs=true
./gradlew build -Pshop.localLibs=true -Pshop.cqrsPath=../spring-boot-cqrs -Pshop.specrepoPath=../spring-boot-specification-repository
```

The defaults of `shop.cqrsPath` and `shop.specrepoPath` (in [`gradle.properties`](../../gradle.properties)) expect both repositories cloned next to this one. `shop.localLibs=true` can also be set in `gradle.properties`.

## Continuous integration

[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) runs on every push to `main` and on pull requests:

| Job | Steps |
|---|---|
| Backend | JDK 25 (Temurin), `./gradlew build`: compilation, Spotless, unit, ArchUnit and Testcontainers integration tests |
| Frontend | Node 24, `npm ci`, `npm run check`: Prettier, ESLint, Vitest and a production build |
| Kubernetes manifests | `kubectl kustomize` of every overlay; `docker compose config` of `compose.yaml` alone and with `compose.observability.yaml`, and of `compose.dev.yaml` |

The system tests need a deployed platform and are run separately; see [Testing](testing.md#system-tests).

## Related

- [Architecture](architecture.md)
- [Observability](observability.md#running-it)
- [Resilience and scaling](resilience-and-scaling.md)
- [Testing](testing.md)
