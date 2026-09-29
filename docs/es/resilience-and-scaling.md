# Resiliencia y escalado

Mercado está construido para cumplir sus promesas cuando fallan partes de él: nunca se cobra un pedido sin confirmarlo, nunca se retiene stock para un pedido rechazado y ningún evento se pierde ni se cuenta dos veces. Cada modo de fallo tiene un mecanismo concreto que lo absorbe (reintentos con backoff, comandos idempotentes, compensaciones que nunca se rinden, el outbox transaccional, consumidores idempotentes), y un panel de caos permite a cualquiera provocar esos fallos a propósito y ver cómo se recupera la plataforma. Los mismos mecanismos hacen que la ruta de las peticiones se pueda ejecutar con seguridad en varias réplicas: HTTP no tiene estado, el relay del outbox y el ejecutor de la saga se reparten el trabajo con bloqueos de fila, y Kafka y RabbitMQ distribuyen los mensajes entre instancias. Los servicios también se compilan como imágenes nativas GraalVM que arrancan en bastante menos de un segundo.

## Modos de fallo

| Fallo | Qué ocurre | Qué lo absorbe |
|---|---|---|
| Payments caído o lento | `AuthorizePayment` agota su timeout a los 5 s o falla | La saga reintenta con backoff (de 1 s hasta 30 s). La autorización es idempotente por pedido, así que un reintento nunca cobra dos veces. Tras 8 intentos (unos dos minutos), el pedido se rechaza con `payment-unavailable` y la saga reembolsa o anula el pago, de modo que una autorización tardía no puede cobrar al cliente. |
| Inventory caído | `ReserveStock` falla | Los mismos reintentos; tras 8 intentos el pedido se rechaza con `inventory-unavailable`. La liberación posterior deja una lápida, así que una reserva tardía no retiene nada. |
| Una compensación sigue fallando | `RefundPayment` o `ReleaseStock` fallan | Las compensaciones nunca se rinden. Tras 20 intentos la saga pasa a `STUCK` (alerta "Checkout compensations are stuck") y sigue reintentando cada 30 s hasta que lo consigue. |
| RabbitMQ no disponible | Los comandos de la saga no se pueden enviar | Toda excepción de un paso remoto es un fallo técnico: la saga lo reintenta como si fuera un timeout. |
| Inventory lento o caído, visto desde una página | `GetStockLevels` no recibe respuesta en 1 s | La ficha de producto muestra el stock como desconocido y sigue vendiendo el producto; el presupuesto del carrito comprueba solo los precios (`stockChecked: false`) y deja seguir con el pedido, porque la saga reserva el stock de todos modos. |
| Notifications caído | `GetOrderNotices` no recibe respuesta en 1 s | La página del pedido indica que los avisos no están disponibles; todo lo demás viene de orders. |
| Kafka caído (`relay.paused`) | El relay no puede publicar | Los eventos se quedan en `event_store` con `published_at` a null. La saga continúa, ya que usa RabbitMQ. Los modelos de lectura, los informes y los avisos se congelan y se ponen al día en orden cuando vuelve el broker. Alerta "Events are not reaching Kafka". |
| Entrega duplicada (`relay.duplicate`) | Cada evento se publica dos veces | Los consumidores idempotentes guardan una marca por `(consumer, event id)`; notifications usa el id del evento como clave de los avisos. Los duplicados solo aparecen en `shop.consumer.events{outcome="duplicate"}`. |
| Tarjetas denegadas (`payments.decline-all`) | Se deniega cada pago nuevo | Una respuesta de negocio, no un fallo: el pedido se rechaza con `card-limit-exceeded` y se libera el stock. |
| Eventos desordenados | Kafka solo ordena por tipo de evento | Las proyecciones guardan marcas de tiempo de vigencia y estados monótonos; consulta [Event sourcing y outbox](event-sourcing-and-outbox.md#orden). |
| Un producto que inventory aún no conoce | `ReserveStock` de un producto cuyo `ProductPublished` no ha llegado | Se trata como un fallo técnico, de modo que la saga reintenta en lugar de rechazar el pedido por falta de stock. |
| Contención sobre stock escaso | Muchas reservas actualizan las mismas filas de stock | [`StockTransactions`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/command/StockTransactions.java) reintenta, dentro de inventory, una reserva que ha perdido una carrera de bloqueo optimista o un deadlock (hasta 15 veces, con una pausa breve aleatoria), y `hibernate.order_updates` hace que cada transacción actualice las filas de stock en orden de clave, así que las reservas cruzadas rara vez provocan deadlocks. |
| Conexión a la base de datos perdida | Una consulta se queda colgada en una conexión muerta | `socketTimeout` de JDBC de 30 s y TCP keep-alive; la readiness probe incluye la base de datos, así que Kubernetes deja de enrutar tráfico a la instancia. |
| orders-service se reinicia a mitad de un checkout | Sagas en curso | La saga se persiste junto con el pedido; el ejecutor reanuda cada saga en su paso. Una saga reclamada por una instancia muerta vuelve cuando vence su lease. |
| Una instancia de un servicio muere a mitad del relay | Filas publicadas pero sin marcar | La transacción se revierte y las filas se publican de nuevo; los consumidores son idempotentes. |

## Panel de caos

Todos los fallos empiezan desactivados. Los componentes registran sus fallos en el registro [`Chaos`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/Chaos.java) al arrancar y los comprueban en cada llamada; mientras un fallo está desactivado, la comprobación cuesta una lectura volatile. Cada fallo es un interruptor (0 o 1) o un retraso en milisegundos (de 0 a 60 000).

| Clave del fallo | Tipo | Servicios | Lo registra | Efecto |
|---|---|---|---|---|
| `payments.decline-all` | interruptor | payments | `PaymentCommandHandler` | El límite de la tarjeta baja a cero: todo pago nuevo se deniega con `card-limit-exceeded` |
| `AuthorizePayment.fail` / `AuthorizePayment.delay-ms` | interruptor / retraso | payments | `MessageChaosMiddleware` | Las autorizaciones fallan técnicamente o se ralentizan (por encima del timeout de respuesta de 5 s, la saga ve un timeout) |
| `RefundPayment.fail` / `RefundPayment.delay-ms` | interruptor / retraso | payments | `MessageChaosMiddleware` | Los reembolsos fallan o son lentos: las compensaciones reintentan y acaban atascándose |
| `ReserveStock.fail` / `ReserveStock.delay-ms` | interruptor / retraso | inventory | `MessageChaosMiddleware` | Las reservas fallan o son lentas |
| `ReleaseStock.fail` / `ReleaseStock.delay-ms` | interruptor / retraso | inventory | `MessageChaosMiddleware` | Las liberaciones fallan o son lentas |
| `GetStockLevels.fail` / `GetStockLevels.delay-ms` | interruptor / retraso | inventory | `MessageChaosMiddleware` | Las lecturas de stock fallan o son lentas (por encima de 1 s la ficha y el carrito muestran el stock como desconocido) |
| `relay.paused` | interruptor | catalog, orders, inventory, payments | `OutboxRelay` | El relay se detiene, como si Kafka estuviera caído |
| `relay.duplicate` | interruptor | catalog, orders, inventory, payments | `OutboxRelay` | Cada evento se publica dos veces |

[`MessageChaosMiddleware`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/MessageChaosMiddleware.java) es un middleware de spring-boot-cqrs que registra un fallo `.delay-ms` y otro `.fail` por cada nombre simple de clase listado en `shop.chaos.messages` (`ReserveStock, ReleaseStock, GetStockLevels` en inventory, `AuthorizePayment, RefundPayment` en payments). Se sitúa en los buses locales, así que también afecta a los comandos y consultas que llegan por RabbitMQ: quien llama ve un servicio lento o un error remoto, exactamente igual que en una caída real. Un fallo inyectado es una [`ChaosException`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/ChaosException.java), que deliberadamente no se traduce a un 4xx.

El endpoint, servido por [`ChaosController`](../../platform/service-support/src/main/java/com/borjaglez/shop/support/chaos/ChaosController.java), existe en los servicios que fijan `shop.chaos.service` (catalog, orders, inventory, payments), en `/api/<service>/chaos`:

| Petición | Efecto |
|---|---|
| `GET /api/payments/chaos` | Lista los fallos con clave, descripción, tipo y valor |
| `PUT /api/payments/chaos/AuthorizePayment.fail` con `{"value": 1}` | Activa un fallo (0 lo desactiva; los retrasos se indican en milisegundos) |
| `DELETE /api/payments/chaos` | Desactiva todos los fallos del servicio |

El endpoint solo responde cuando el servicio arrancó con `SHOP_CHAOS_ENABLED=true` (`shop.chaos.enabled`); en caso contrario, toda petición recibe 404 `chaos-disabled` y no se puede activar ningún fallo. El flag se lee al arrancar en lugar de usarse como condición de un bean, porque una imagen nativa fija sus condiciones en tiempo de build. Docker Compose y los overlays locales de Kubernetes lo activan, ya que son despliegues de demostración; el endpoint no tiene autenticación, como el resto de la API de la demo, y permanecería desactivado en cualquier otro entorno.

La página Chaos de la tienda (`/chaos`) muestra los fallos de cada servicio con una explicación del efecto esperado y un botón para desactivarlo todo:

![Panel de caos](../assets/screenshots/shop-chaos.png)

## Timeouts

| Timeout | Valor | Dónde |
|---|---|---|
| Respuesta de RabbitMQ, comandos de la saga | 5 s (`CHECKOUT_REPLY_TIMEOUT`) | `spring.rabbitmq.template.reply-timeout` en orders; una respuesta que no llega es un fallo reintentable del paso |
| Respuesta de RabbitMQ, consultas a otros servicios | 1 s (`REMOTE_QUERY_TIMEOUT`) | `shop.remote-queries.reply-timeout` en catalog y orders, sobre un template propio; una respuesta que no llega deja vacía esa parte de la página |
| Socket JDBC | 30 s | propiedad `socketTimeout` del data source, desde `ShopDefaultsEnvironmentPostProcessor` |
| Obtención de conexión de Hikari | 5 s | `spring.datasource.hikari.connection-timeout` en todos los servicios |
| Lease de la saga | 30 s | `shop.checkout.lease`; más largo que cualquier paso |
| Fase de apagado | 20 s | `spring.lifecycle.timeout-per-shutdown-phase` |
| Conexión SSE | 30 min, heartbeat cada 20 s | `SseNotificationHub`; `proxy_read_timeout 1h` de nginx |

## Apagado ordenado y probes

- `server.shutdown=graceful` deja terminar las peticiones en curso; los schedulers del relay y de la saga son beans `SmartLifecycle` que se detienen antes de que desaparezcan la base de datos y los brokers (el scheduler de la saga espera hasta 10 s a un paso que está esperando una respuesta).
- Los Deployments fijan `terminationGracePeriodSeconds: 40` y un `preStop` con un sleep de 5 s, para que el controlador de endpoints deje de enrutar tráfico antes de que la aplicación empiece a apagarse.
- **Startup probe** sobre `/actuator/health/liveness` cada 5 s, hasta 36 fallos (3 minutos) para las imágenes JVM y 12 para las nativas.
- **Readiness probe** sobre `/actuator/health/readiness`, que incluye la base de datos (`readinessState,db`): una instancia sin su base de datos no recibe tráfico.
- **Liveness probe** sobre `/actuator/health/liveness` cada 20 s: solo indica que el proceso funciona, así que una caída de la base de datos nunca reinicia pods.
- El relay y el ejecutor de la saga capturan todo, `Error` incluidos, y siguen adelante: un scheduled executor descarta en silencio una tarea que lanza una excepción, lo que detendría el bucle mientras el servicio seguiría pareciendo sano.

## Escalado horizontal

El componente de Kustomize [`deploy/k8s/components/scaling`](../../deploy/k8s/components/scaling) escala la ruta de las peticiones; el overlay [`deploy/k8s/overlays/scaled`](../../deploy/k8s/overlays/scaled/kustomization.yaml) lo combina con las configuraciones local y de observabilidad:

```bash
kubectl apply -k deploy/k8s/overlays/scaled
```

| Pieza | Ajuste |
|---|---|
| Réplicas | 2 de `gateway-service`, `catalog-service`, `orders-service`, `inventory-service`, `payments-service` |
| Rolling updates | `maxSurge: 1`, `maxUnavailable: 0`: la capacidad nunca baja durante un despliegue |
| PodDisruptionBudgets | `minAvailable: 1` para cada uno: los drenados de nodos y las actualizaciones desalojan una réplica cada vez |
| HorizontalPodAutoscalers | De 2 a 3 réplicas al 70% de CPU media, con estabilización del escalado hacia abajo de 5 minutos; necesitan un metrics server (en Docker Desktop, instala `metrics-server` con `--kubelet-insecure-tls`) |
| Topology spread | `maxSkew: 1` sobre `kubernetes.io/hostname`, `ScheduleAnyway` |

### Por qué cada pieza se puede ejecutar dos veces con seguridad

| Aspecto | Mecanismo |
|---|---|
| Peticiones HTTP | Sin estado: no hay sesión; el usuario y el correlation id viajan en cabeceras. |
| Relay del outbox | Los lotes se bloquean con `FOR UPDATE SKIP LOCKED`: las réplicas se reparten el backlog sin publicar dos veces una fila. Con varias réplicas haciendo de relay, los lotes pueden intercalarse, algo que los consumidores ya toleran. |
| Ejecutor de la saga de checkout | Las sagas pendientes se reclaman con `FOR UPDATE SKIP LOCKED` y se conceden con un lease; cada saga se ejecuta en una réplica a la vez, y otra se hace cargo cuando vence el lease. Ejecutar un paso dos veces no causa daño (comprobación de versión más comandos idempotentes). |
| Consumidores de Kafka | Las réplicas de un servicio comparten su consumer group, así que cada evento llega a una réplica; los consumidores idempotentes absorben las nuevas entregas durante los rebalanceos. |
| Comandos de RabbitMQ | Consumidores en competencia sobre una cola por tipo de comando; los handlers son idempotentes por pedido. |
| Concurrencia optimista | Los streams de eventos, las proyecciones, las sagas y las filas de stock están versionados: los escritores concurrentes nunca se sobrescriben. |

### Lo que se queda con una sola réplica

- `notifications-service` mantiene las conexiones SSE en memoria: un aviso llega a las pestañas del navegador conectadas a la réplica que procesó el evento. Con varias réplicas haría falta un fan-out compartido (por ejemplo un topic de difusión o Redis pub/sub).
- `reporting-service` pausa su único consumidor para una reconstrucción, lo que da por hecho que ninguna otra instancia consume con su grupo.
- Los fallos del caos viven en la memoria de cada instancia, así que un interruptor activado a través del gateway solo llegaría a una réplica. Por eso el overlay `scaled` arranca los servicios con `SHOP_CHAOS_ENABLED=false`; para el panel de caos usa Compose o un overlay de una sola réplica.
- PostgreSQL, RabbitMQ y Kafka se ejecutan en la demo como StatefulSets de un solo nodo; en producción se usarían brokers y bases de datos gestionados o en clúster.

### Presupuesto de conexiones a la base de datos

Cada servicio tiene un pool de Hikari de 10 (`DB_POOL_SIZE`). PostgreSQL se ejecuta con `max_connections=250`, fijado tanto en Compose como en Kubernetes. Los servicios escalados en su máximo de tres réplicas, más un pod adicional durante un rolling update, más los servicios de una sola réplica, se mantienen dentro de ese límite.

## Imágenes nativas GraalVM

```bash
./gradlew buildImages -Pnative                          # shop/<service>:0.1.0-SNAPSHOT-native
kubectl apply -k deploy/k8s/overlays/native             # or overlays/native-observability
SHOP_IMAGE_SUFFIX=-native docker compose -f deploy/compose/compose.yaml up -d
```

Con `-Pnative`, `shop.boot-service-conventions` aplica el plugin GraalVM Native Build Tools y `bootBuildImage` compila dentro de Paketo, así que no hace falta GraalVM en local. El código tiene como objetivo Java 25; el build nativo usa GraalVM para JDK 25 (`BP_JVM_VERSION=25`). Las imágenes se construyen de una en una (el build service `ImageBuilds` de `build-logic`), porque varios builds nativos simultáneos agotan Docker Desktop. `notifications-service`, el servicio Spring Boot 3.5, no tiene build nativo y también se ejecuta sobre la JVM en el overlay nativo.

Medido en Docker Desktop (16 CPU, 32 GB) con todos los servicios arrancando a la vez en Compose:

| | JVM (Paketo, Java 25) | Nativa (GraalVM 25) |
|---|---|---|
| Arranque de la aplicación ("Started ... in") | 6-18 s | 0,11-0,63 s |
| Memoria del contenedor tras una ejecución de extremo a extremo | 244-400 MiB | 73-132 MiB |
| Build de la imagen | unos 25 s por servicio | 140-190 s por servicio |

El overlay nativo reduce los requests y limits a 128/256 MiB y acorta en consecuencia la startup probe.

Qué hace que los servicios estén preparados para imagen nativa:

- **Hints junto al código que los necesita.** [`EsKitRuntimeHints`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EsKitRuntimeHints.java) registra las migraciones de Flyway de es-kit (`db/eskit/**`), los arrays de ids que Hibernate crea por reflexión cuando carga por lotes con `default_batch_fetch_size`, y las clases de excepción de Flyway que se usan para informar de los fallos de conexión. spring-boot-cqrs registra hints para todos los contratos mediante `cqrs.aot.message-packages`.
- **Un índice de eventos generado en tiempo de build.** Una imagen nativa no puede escanear el classpath, así que [`EventTypeIndexAotProcessor`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventTypeIndexAotProcessor.java) escanea los paquetes de eventos durante el procesamiento AOT, escribe las clases en `META-INF/shop/event-types.idx` y registra sus hints de binding; `EventTypeRegistry` lee el índice en tiempo de ejecución y, en la JVM, recurre al escaneo si no lo encuentra.
- **Interruptores en tiempo de ejecución en lugar de `@Conditional`.** En una imagen nativa las condiciones se evalúan en tiempo de build, así que todo lo que deba poder activarse en tiempo de ejecución es una comprobación en tiempo de ejecución: los exportadores OTLP ([Observabilidad](observability.md#activar-la-exportación)) y el endpoint de caos.
- **Enhancement de bytecode.** `shop.hibernate-enhancement-conventions` mejora las entidades JPA en tiempo de build, porque una imagen nativa no puede generar proxies de Hibernate en tiempo de ejecución; los builds JVM también se mejoran, para que ambos se comporten igual.
- **Sin refresh scope en el gateway.** Las rutas son configuración estática y `spring.cloud.refresh.enabled=false`, ya que refresh scope no está soportado en imágenes nativas.
- **Bucles que registran los `Error` y siguen adelante.** En una imagen nativa, un hint que falta aflora como un `Error`. `OutboxRelayScheduler`, `CheckoutSagaScheduler` y `CheckoutSagaRunner` capturan y registran en el log tanto los `Error` como las excepciones, de modo que un bucle en segundo plano nunca puede detenerse en silencio.
- **Volcados de hilos.** Las imágenes nativas se construyen con `--enable-monitoring=threaddump`: `docker kill --signal=QUIT <container>` imprime los hilos en el log, igual que en la JVM.

## Tests de estrés

Los tests de sistema incluyen dos escenarios de estrés contra una plataforma en ejecución (consulta [Testing](testing.md#tests-de-sistema)):

- `ScarceStockStressTest`: muchos clientes quieren las últimas unidades a la vez. Se confirman exactamente tantos pedidos como unidades hay, el resto se rechazan por falta de stock, nadie paga por un pedido rechazado y el stock nunca baja de cero; cancelar todos los pedidos confirmados devuelve todo el stock.
- `ChaosStormStressTest`: una ráfaga de pedidos mientras golpean a la vez eventos duplicados, reservas lentas, fallos intermitentes de pago y un relay en pausa. Una vez retirados los fallos, todos los pedidos terminan consistentes en todos los servicios y los informes cuentan cada pedido confirmado exactamente una vez.

## Relacionado

- [Saga de checkout](checkout-saga.md)
- [Event sourcing y outbox](event-sourcing-and-outbox.md)
- [Observabilidad](observability.md)
- [Despliegue](deployment.md)
- [Testing](testing.md)
