# Observabilidad

Todos los servicios de Mercado generan trazas, métricas y logs mediante OpenTelemetry, y una única variable de entorno, `OTEL_EXPORTER_OTLP_ENDPOINT`, envía las tres señales a un colector OTLP. El stack de la demo es `grafana/otel-lgtm`: un OpenTelemetry Collector que alimenta Tempo (trazas), Prometheus (métricas) y Loki (logs), con Grafana por encima y dos dashboards y cinco reglas de alerta aprovisionados desde el repositorio. Un pedido es una traza, desde la petición HTTP en el gateway, pasando por los pasos de la saga sobre RabbitMQ y los eventos retransmitidos a Kafka, hasta la última proyección. Las métricas estándar describen HTTP, la JVM, los pools de conexiones y los brokers; las métricas propias describen lo que está haciendo la arquitectura: el backlog del outbox, la ventana de consistencia eventual de cada modelo de lectura y el resultado y la duración de cada checkout. Los logs llevan el correlation id y el trace id, y Grafana salta entre una línea de log y su traza.

<p align="center"><img src="../assets/diagrams/observability.svg" alt="Pipeline de telemetría desde los servicios, a través del OpenTelemetry Collector, hacia Tempo, Prometheus, Loki y Grafana" width="100%"></p>

## Activar la exportación

### Servicios Boot 4

Los servicios Boot 4 dependen de `spring-boot-starter-opentelemetry` (trazas mediante el bridge de Micrometer, métricas OTLP) y del appender de Logback de OpenTelemetry, ambos añadidos por `shop.boot-service-conventions`. No se exporta nada hasta que se define la variable estándar:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318
```

`service-support` convierte esa única variable en todo lo demás, y la lee cuando arranca la aplicación, no cuando se compila:

| Señal | Componente | Qué hace |
|---|---|---|
| Trazas | [`ShopOtlpAutoConfiguration`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/autoconfigure/ShopOtlpAutoConfiguration.java) | Define siempre un `SpanExporter`: un exportador OTLP/HTTP hacia `<endpoint>/v1/traces` cuando la variable está definida y, si no, un composite que no hace nada. |
| Logs | `ShopOtlpAutoConfiguration.Logs` | Define un `LogRecordExporter` hacia `<endpoint>/v1/logs` (que no hace nada en caso contrario) y un [`OtlpLogAppenderInstaller`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/autoconfigure/OtlpLogAppenderInstaller.java). |
| Logs | `OtlpLogAppenderInstaller` | Una vez que existe el `OpenTelemetry` de la aplicación, añade el appender de OpenTelemetry al logger raíz de Logback y captura la clave del MDC `correlationId` como atributo del log. La salida por consola no cambia. |
| Métricas | [`OtlpExportEnvironmentPostProcessor`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/autoconfigure/OtlpExportEnvironmentPostProcessor.java) | Fija `management.otlp.metrics.export.url=<endpoint>/v1/metrics` y un step de 10 s. Sin la variable desactiva el meter registry OTLP, para que un servicio nunca reintente `localhost:4318` en segundo plano. |

Los exportadores se crean en tiempo de ejecución, en lugar de mediante las propiedades OTLP con `@Conditional` del propio Spring Boot, por un motivo: una imagen nativa evalúa las condiciones en tiempo de build, así que una imagen compilada sin la variable nunca podría exportar, diga lo que diga su entorno después. Con exportadores en tiempo de ejecución, la misma imagen, JVM o nativa, exporta exactamente cuando la variable está presente. No debe definirse además la propiedad `management.opentelemetry.tracing.export.otlp.endpoint` de Boot, o cada span y cada registro de log se exportarían dos veces. Durante el procesamiento AOT, el post-processor deja activado el meter registry OTLP para que se compile en las imágenes nativas; la URL, resuelta en tiempo de ejecución, decide si envía datos.

### notifications-service (Boot 3.5)

Spring Boot 3.5 no tiene starter de OpenTelemetry, así que `shop.boot3-service-conventions` añade el bridge OTel de Micrometer, el exportador OTLP, el meter registry OTLP y el appender de Logback, y el servicio usa las propiedades propias de Boot 3:

```yaml
MANAGEMENT_OTLP_TRACING_ENDPOINT: http://lgtm:4318/v1/traces
MANAGEMENT_OTLP_METRICS_EXPORT_URL: http://lgtm:4318/v1/metrics
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED: "true"
MANAGEMENT_OTLP_LOGGING_ENDPOINT: http://lgtm:4318/v1/logs
```

Su [`ObservabilityConfiguration`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/infrastructure/ObservabilityConfiguration.java) instala el appender de logs OTLP cuando `management.otlp.logging.endpoint` está definida, y vincula las métricas de consumidor de Kafka a los consumidores que crea spring-boot-cqrs, la misma telemetría que los servicios Boot 4 obtienen de los módulos de plataforma. Su `application.yaml` muestrea todas las peticiones y activa la observación en el listener y el template de Kafka.

El fichero de Compose de observabilidad y el componente de Kubernetes definen exactamente estas variables: `OTEL_EXPORTER_OTLP_ENDPOINT` para las seis aplicaciones Boot 4 y las cuatro variables `MANAGEMENT_OTLP_*` para notifications.

## Trazas

### Propagación

- **Muestreo.** `management.tracing.sampling.probability=1.0`: se traza cada petición, porque la demo quiere trazas completas.
- **HTTP.** Las cabeceras W3C `traceparent` se propagan desde el gateway hasta los servicios.
- **RabbitMQ y Kafka.** `ShopDefaultsEnvironmentPostProcessor` activa la observación en los templates y contenedores de listeners de RabbitMQ y Kafka (`spring.rabbitmq.listener.simple.observation-enabled`, `spring.rabbitmq.template.observation-enabled`, `spring.kafka.listener.observation-enabled`, `spring.kafka.template.observation-enabled`), y los contenedores de listeners y templates que construye spring-boot-cqrs respetan los mismos ajustes de observación. Por tanto, las cabeceras de traza viajan con cada comando, respuesta y evento.
- **Buses.** spring-boot-cqrs envuelve cada tratamiento en un span `cqrs.bus.handle` etiquetado con el tipo y la clase de mensaje.
- **Trabajo diferido.** Dos tareas se ejecutan más tarde, en otro hilo: el relay del outbox publica un evento segundos después de la petición que lo registró, y el ejecutor de la saga realiza cada paso del checkout mucho después de que se realizara el pedido. [`TraceCarrier`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/tracing/TraceCarrier.java) captura la traza actual como cabeceras W3C y la reanuda más tarde en un span nuevo. El event store guarda las cabeceras en el `metadata` de cada evento, y el relay publica dentro de un span llamado `outbox publish <event type>` que la continúa. La saga guarda el `traceparent` en su columna `trace_parent`, y cada paso se ejecuta dentro de un span llamado `checkout <STEP>`; una cancelación inicia el reembolso en la traza de la petición de cancelación.
- **Las trazas empiezan en la plataforma.** El [`ExternalTraceHeadersFilter`](../../services/gateway-service/src/main/java/com/borjaglez/shop/gateway/ExternalTraceHeadersFilter.java) del gateway descarta los `traceparent`, `tracestate` y `baggage` enviados por los clientes, de modo que nadie puede unirse a la traza de otro ni desactivar el muestreo de sus pedidos.
- **Los health checks no se trazan.** Un `ObservationPredicate` en [`ShopTracingAutoConfiguration`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/autoconfigure/ShopTracingAutoConfiguration.java) omite las peticiones al servidor bajo `/actuator`, para que las probes de Kubernetes no inunden Tempo.

### Un pedido, una traza

Los spans de un único pedido, tal como los muestra Tempo (resumidos):

```
gateway-service: http post /api/orders/**
  orders-service: http post /api/orders
    orders-service: cqrs.bus.handle                                   (PlaceOrderCommand)
      orders-service: outbox publish ...order-placed                  (relay thread, moments later)
        orders-service: shop.events send -> <every consumer>: shop.events process -> cqrs.bus.handle
      orders-service: checkout RESERVE_STOCK                          (saga runner thread)
        orders-service: RabbitMQ send (ReserveStock)
          inventory-service: RabbitMQ receive -> cqrs.bus.handle
            inventory-service: outbox publish ...stock-reserved -> Kafka -> consumers
      orders-service: checkout AUTHORIZE_PAYMENT -> payments-service ... -> Kafka
      orders-service: checkout CONFIRM_ORDER
        orders-service: outbox publish ...order-confirmed -> Kafka -> reporting-service, notifications-service (Boot 3.5)
```

![Traza de un pedido en Tempo](../assets/screenshots/grafana-trace.png)

`CheckoutTracingIT` comprueba esta continuidad en el servicio de pedidos: todos los pasos y todos los eventos registrados por la saga pertenecen a la traza que realizó el pedido.

## Logs

- **Consola.** Todos los servicios Boot 4 imprimen `[correlationId,traceId]` en cada línea (`logging.pattern.correlation`), de modo que una línea de log se puede relacionar tanto con la petición de negocio como con su traza incluso sin Grafana.
- **OTLP.** Con la exportación activada, cada línea de log se envía también como registro de log OTLP a Loki. El registro lleva el `trace_id` y el `span_id` del span que lo escribió, la severidad (`severity_text`), los atributos del recurso (`service_name`) y, en los servicios Boot 4, el atributo `correlationId` tomado del MDC.
- **Enlazados.** Grafana enlaza Loki y Tempo en ambos sentidos: abre una línea de log para saltar a su traza, o abre un span para ver sus logs. El panel "Latest warnings and errors" (últimos avisos y errores) del dashboard general lista las líneas WARN y ERROR recientes con ese enlace.

![Logs en Loki](../assets/screenshots/grafana-logs.png)

## Métricas

Las métricas se envían por OTLP cada 10 segundos; todos los servicios exponen además `/actuator/prometheus` y `/actuator/metrics`. En Prometheus, los puntos de los nombres de los meters se sustituyen por guiones bajos, los timers se convierten en histogramas con un sufijo de unidad (`_milliseconds_bucket`, `_milliseconds_count`, `_milliseconds_sum`), los counters reciben `_total` y los nombres de las etiquetas se convierten del mismo modo (`event.type` pasa a ser `event_type`). Cada serie lleva la etiqueta `service_name` de su servicio. Los valores por defecto comunes activan histogramas de percentiles (`management.metrics.distribution.percentiles-histogram.*`) para `http.server.requests`, `cqrs.bus`, `spring.rabbit` y todos los timers `shop.*`: sin buckets, el registro OTLP solo envía un contador y una suma, y no se podría calcular ningún p95. Las conexiones de larga duración de server-sent events quedan fuera de los paneles y alertas de latencia.

### Métricas estándar

| Métrica | Origen | Nombre en Prometheus |
|---|---|---|
| Peticiones al servidor HTTP | Spring Boot | `http_server_requests_milliseconds_*` |
| Memoria, CPU, hilos y GC de la JVM | Spring Boot / Micrometer | `jvm_memory_used_bytes`, `process_cpu_usage`, ... |
| Pools de conexiones | HikariCP | `hikaricp_connections_active`, `hikaricp_connections_pending`, ... |
| Templates y listeners de Kafka | observaciones de Spring Kafka | `spring_kafka_template_milliseconds_*`, `spring_kafka_listener_milliseconds_*` |
| Templates y listeners de RabbitMQ | observaciones de Spring AMQP | `spring_rabbit_template_milliseconds_*`, `spring_rabbit_listener_milliseconds_*` |
| Cliente de RabbitMQ | Spring Boot | `rabbitmq_published_total`, `rabbitmq_consumed_total`, ... |
| Tratamiento en el bus | `cqrs.bus.handle` de spring-boot-cqrs | `cqrs_bus_handle_milliseconds_*` (etiquetas `cqrs_message_kind`, `cqrs_message_type`, `error`) |
| Despacho en el bus | `cqrs.bus.dispatch` de spring-boot-cqrs | `cqrs_bus_dispatch_milliseconds_*` (etiquetas `cqrs_type`, `cqrs_message`, `cqrs_outcome`) |
| Métricas del cliente de Kafka | binders de Kafka de Micrometer en cada factoría de Kafka | `kafka_consumer_fetch_manager_records_lag_max`, ... |

Spring Boot solo instrumenta las factorías de Kafka que crea él mismo. [`KafkaClientMetricsPostProcessor`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/kafka/KafkaClientMetricsPostProcessor.java) (es-kit) vincula los listeners de consumidor y productor de Micrometer a cada `DefaultKafkaConsumerFactory` y `DefaultKafkaProducerFactory` del servicio, incluidas las que crea spring-boot-cqrs para sus buses; notifications hace lo mismo con sus consumidores. De ahí sale `kafka.consumer.fetch.manager.records.lag.max`, cuántos registros lleva de retraso respecto al log un consumidor y los modelos de lectura que alimenta.

### Métricas propias

| Meter | Nombre en Prometheus | Tipo | Etiquetas | Significado |
|---|---|---|---|---|
| `shop.outbox.pending` | `shop_outbox_pending` | gauge | | Eventos guardados pero aún no publicados |
| `shop.outbox.oldest.age.seconds` | `shop_outbox_oldest_age_seconds` | gauge | | Cuánto lleva esperando el evento no publicado más antiguo; crece mientras Kafka o el relay están caídos y baja a 0 en cuanto se publica el backlog |
| `shop.outbox.published` | `shop_outbox_published_total` | counter | `event.type` | Eventos publicados por el relay |
| `shop.outbox.failures` | `shop_outbox_failures_total` | counter | `event.type` | Intentos de publicación fallidos |
| `shop.outbox.delay` | `shop_outbox_delay_milliseconds_*` | timer | | Desde la transacción de negocio hasta la confirmación del broker |
| `shop.consumer.events` | `shop_consumer_events_total` | counter | `consumer`, `outcome` = `applied` / `duplicate` | Eventos tratados por cada consumidor idempotente |
| `shop.consumer.lag` | `shop_consumer_lag_milliseconds_*` | timer | `consumer` | Desde el momento del evento hasta su proyección: la ventana de consistencia eventual de ese modelo de lectura |
| `shop.checkout.steps` | `shop_checkout_steps_total` | counter | `step`, `result` = `succeeded` / `declined` / `retrying` / `gave-up` | Intentos de cada paso de la saga |
| `shop.checkout.completed` | `shop_checkout_completed_total` | counter | `outcome` = `confirmed` / `rejected` / `cancelled`, `reason` | Sagas terminadas y su motivo de rechazo (`none` en otro caso) |
| `shop.checkout.duration` | `shop_checkout_duration_milliseconds_*` | timer | `outcome` | Desde que se realiza el pedido hasta que se confirma o se rechaza |
| `shop.checkout.sagas` | `shop_checkout_sagas` | gauge | `state` = `running` / `stuck` | Sagas que aún tienen trabajo pendiente |
| `shop.notifications.sse.connections` | `shop_notifications_sse_connections` | gauge | | Conexiones SSE abiertas de notifications |

Orígenes: [`OutboxMetrics`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxMetrics.java) y [`OutboxRelay`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxRelay.java) (outbox), [`IdempotentConsumer`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/IdempotentConsumer.java) (consumidores), [`CheckoutMetrics`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutMetrics.java) (checkout), [`SseNotificationHub`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/api/SseNotificationHub.java) (SSE). Los gauges leen la base de datos cuando se recogen (las filas pendientes tienen su propio índice parcial) y devuelven `NaN` en lugar de hacer fallar la recogida si la lectura falla. Los counters y timers del relay y de la saga se registran después del commit de su transacción, así que el trabajo revertido nunca se contabiliza.

## Dashboards

Se aprovisionan dos dashboards en la carpeta **Shop** de Grafana desde [`deploy/k8s/components/observability/grafana/dashboards`](../../deploy/k8s/components/observability/grafana/dashboards), mediante el provider [`dashboards.yaml`](../../deploy/k8s/components/observability/grafana/dashboards.yaml). Compose monta los mismos ficheros.

### Shop · Services overview (`shop-overview`)

| Fila | Paneles |
|---|---|
| Summary | Gateway requests/s, gateway 5xx %, gateway p95 latency, confirmed orders in the last hour, outbox backlog, Kafka consumer lag |
| HTTP: rate, errors, duration | Requests/s, 5xx/s y latencia p95 por servicio; endpoints más lentos; respuestas por código de estado (RED por servicio) |
| Saturation | Memoria en uso, uso de CPU, conexiones de base de datos activas y pendientes por servicio |
| Logs | Número de WARN y ERROR por servicio desde Loki; últimos avisos y errores, cada línea enlazada a su traza |

![Dashboard Services overview](../assets/screenshots/grafana-overview.png)

### Shop · Checkout saga & messaging (`shop-messaging`)

| Fila | Paneles |
|---|---|
| Summary | Checkouts por minuto, tasa de confirmación, tiempo de checkout p95, sagas en curso, sagas atascadas, rechazos técnicos en la última hora |
| Checkout saga | Resultados por minuto, motivos de rechazo, duración del checkout (p50, p95), resultados de pasos por segundo, sagas por estado |
| CQRS buses (spring-boot-cqrs) | Mensajes tratados/s por tipo y clase, fallos de handlers/s, tiempo de tratamiento p95 |
| Transactional outbox | Eventos pendientes, antigüedad del evento pendiente más antiguo, retraso del store a Kafka (p95), eventos publicados/s por tipo, fallos de publicación/s |
| Kafka consumers and read models | Consumer lag en registros, retraso de los modelos de lectura (p95 de `shop.consumer.lag`), eventos aplicados/s, duplicados ignorados/s |
| RabbitMQ (saga commands) | Tiempo de petición/respuesta (p95), mensajes publicados y consumidos por segundo |
| Service graph | Quién llama a quién por HTTP, RabbitMQ y Kafka, construido por Tempo a partir de los spans |

Ambos dashboards tienen una variable `service` para centrarse en uno o varios servicios.

![Dashboard Checkout saga & messaging](../assets/screenshots/grafana-messaging.png)

## Reglas de alerta

Se aprovisionan cinco reglas desde [`shop-alerts.yaml`](../../deploy/k8s/components/observability/grafana/alerting/shop-alerts.yaml) en la carpeta Shop (Alerting > Alert rules). Cada una vigila un modo de fallo que la arquitectura está diseñada para absorber, así que salta cuando la red de seguridad lleva demasiado tiempo en uso, no con cada fallo transitorio.

| Regla | Condición | Severidad | La red de seguridad que se está usando |
|---|---|---|---|
| Server errors above 5% | proporción de 5xx de las peticiones que no son de Actuator, por servicio, > 5% durante 2 min | critical | Los reintentos y los problem details ocultan los fallos aislados; una tasa de 5xx sostenida es una caída real. Los 4xx son respuestas de negocio y no se cuentan. |
| Events are not reaching Kafka | `shop_outbox_oldest_age_seconds` > 60 durante 1 min | warning | El outbox mantiene cada evento a salvo en la base de datos, pero los modelos de lectura, los informes y los avisos dejan de avanzar hasta que vuelven Kafka o el relay. |
| Checkout compensations are stuck | `shop_checkout_sagas{state="stuck"}` > 0 durante 1 min | critical | Un reembolso o una liberación de stock ha fallado 20 veces; la saga sigue reintentando cada 30 s, pero mientras tanto se retiene dinero o stock. |
| Orders rejected because a service did not answer | cualquier `shop_checkout_completed_total{outcome="rejected",reason=~".*-unavailable"}` en 10 min | warning | La saga se rindió con inventory o payments tras unos dos minutos de reintentos; el cliente no hizo nada mal. |
| Read models are falling behind | `kafka_consumer_fetch_manager_records_lag_max` > 1000 durante 5 min | warning | Los consumidores idempotentes absorben las nuevas entregas y el retraso, pero las proyecciones están desactualizadas; busca handlers que fallan o escala horizontalmente. |

Las reglas tienen anotaciones que indican dónde mirar a continuación (dashboard, logs, la página del pedido). No hay ningún punto de contacto configurado: en la demo, las alertas activas se ven en Grafana.

## Cómo ejecutarlo

Docker Compose:

```bash
./gradlew buildImages
docker compose -f deploy/compose/compose.yaml -f deploy/compose/compose.observability.yaml up -d
```

Kubernetes (Docker Desktop), con imágenes JVM o nativas, o con dos réplicas en la ruta de las peticiones:

```bash
kubectl apply -k deploy/k8s/overlays/observability          # JVM images
kubectl apply -k deploy/k8s/overlays/native-observability   # GraalVM native images
kubectl apply -k deploy/k8s/overlays/scaled                 # 2 replicas + autoscaling + observability
```

Grafana está en http://localhost:3000, con acceso anónimo de administrador en la demo. Consulta [Despliegue](deployment.md).

## Explorar

- **Dashboards.** Dashboards > Shop. Realiza unos cuantos pedidos, activa fallos en la página Chaos y observa cómo reaccionan los paneles del outbox, de la saga y de los consumidores.
- **Trazas.** Explore > Tempo, busca por `service.name` (el `spring.application.name` de cada servicio) o con TraceQL, por ejemplo todos los pasos de checkout del servicio de pedidos:

  ```
  {resource.service.name="orders-service" && name=~"checkout.*"}
  ```

- **Logs.** Explore > Loki, por ejemplo los avisos del servicio de pedidos:

  ```
  {service_name="orders-service"} | severity_text="WARN"
  ```

  Abre una línea y sigue el enlace a su traza. El prefijo `[correlationId,traceId]` de la consola, el `correlationId` de cada problem detail y el atributo `correlationId` en Loki permiten seguir una petición de soporte desde el mensaje de error del usuario hasta la traza.
- **Service graph.** La última fila del dashboard de mensajería, o Explore > Tempo > Service graph: el gateway, los seis servicios y sus conexiones HTTP, RabbitMQ y Kafka.

## Paso a producción

La demo prioriza verlo todo frente al coste. Una instalación de producción cambiaría:

- **Muestreo.** Tail-based sampling en el colector (conservar todas las trazas con un error o un checkout lento y una fracción del resto) en lugar de un head sampling del 100%.
- **Backends.** Un despliegue separado del OpenTelemetry Collector y Tempo, Mimir (o Prometheus) y Loki dedicados y persistentes con políticas de retención, en lugar del pod todo en uno `otel-lgtm`.
- **Enrutado de alertas.** Puntos de contacto y políticas de notificación (avisos a la guardia para las reglas críticas, chat para las de tipo warning), además de runbooks enlazados desde las anotaciones.
- **SLOs.** Objetivos de nivel de servicio sobre la duración del checkout y la tasa de confirmación, con alertas de burn rate, construidos sobre `shop.checkout.duration` y `shop.checkout.completed`.
- **Exemplars.** Exemplars en los histogramas de latencia para saltar desde un bucket lento directamente a una traza representativa.
- **Profiling continuo.** Un profiler (por ejemplo Grafana Pyroscope) para explicar los puntos calientes de CPU y de asignación de memoria que hay detrás de la latencia.

## Relacionado

- [Arquitectura](architecture.md#correlation-ids)
- [Event sourcing y outbox](event-sourcing-and-outbox.md)
- [Saga de checkout](checkout-saga.md#métricas)
- [Modelos de lectura](read-models.md#ventana-de-consistencia)
- [Resiliencia y escalado](resilience-and-scaling.md)
- [Despliegue](deployment.md)
