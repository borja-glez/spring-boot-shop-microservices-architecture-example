# Saga de checkout

Realizar un pedido afecta a tres servicios que no comparten base de datos: orders registra el pedido, inventory retiene el stock y payments cobra el dinero. Mercado los coordina con una saga orquestada: un process manager persistido en `orders-service` que envía comandos idempotentes a inventory y payments mediante petición/respuesta sobre RabbitMQ, registra cada respuesta y deshace lo hecho cuando un paso no puede completarse. Los rechazos de negocio (sin stock, tarjeta denegada) son respuestas, no errores; los fallos técnicos (timeouts, un servicio caído) se reintentan con backoff exponencial; las compensaciones nunca se rinden. Puede haber cualquier número de réplicas de `orders-service` ejecutando la saga en paralelo, y cada saga la reclama una sola de ellas a la vez.

<p align="center"><img src="../assets/diagrams/checkout-saga.svg" alt="Máquina de estados de la saga de checkout con sus pasos hacia delante, compensaciones y estados finales" width="100%"></p>

## Roles

| Servicio | Rol | Estado |
|---|---|---|
| `orders-service` | Orquestador. Decide el siguiente paso, llama a los otros servicios, registra los resultados y escribe `OrderConfirmed` / `OrderRejected`. | `checkout_saga` y `checkout_saga_step` en la base de datos `orders`; el stream de eventos del pedido |
| `inventory-service` | Reserva y libera stock, todo o nada, de forma idempotente por pedido. | `stock_item`, `reservation`, `reservation_line`; eventos en su outbox |
| `payments-service` | Autoriza, deniega y reembolsa pagos, de forma idempotente por pedido. Deniega los importes por encima del límite de la tarjeta (300, `shop.payments.card-limit`). | el stream `payment` en su event store; `payment_view` para el backoffice |

Los comandos viajan por RabbitMQ (`RabbitMqCommandBus.dispatchAndReceive`); los eventos que registra cada servicio (`OrderConfirmed`, `StockReserved`, `PaymentAuthorized`...) salen a través del outbox hacia Kafka para los modelos de lectura.

## Pasos y modos

Una saga tiene un **modo**, aquello hacia lo que trabaja, y un **paso** actual. [`CheckoutStep`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/CheckoutStep.java) enumera los pasos; cuatro son remotos y dos son locales.

| Paso | Tipo | Llamada | Si tiene éxito pasa a |
|---|---|---|---|
| `RESERVE_STOCK` | remoto, hacia delante | `ReserveStock` -> `StockReservation` | `AUTHORIZE_PAYMENT` (reservado) o `RELEASE_STOCK` en `REJECTING` (insuficiente) |
| `AUTHORIZE_PAYMENT` | remoto, hacia delante | `AuthorizePayment` -> `PaymentAuthorization` | `CONFIRM_ORDER` (autorizado) o `RELEASE_STOCK` en `REJECTING` (denegado) |
| `CONFIRM_ORDER` | local | añade `OrderConfirmed` | `DONE` |
| `REFUND_PAYMENT` | remoto, compensación | `RefundPayment` -> `PaymentRefund` | `RELEASE_STOCK` |
| `RELEASE_STOCK` | remoto, compensación | `ReleaseStock` -> `StockRelease` | `REJECT_ORDER` (rechazando) o `DONE` (cancelando) |
| `REJECT_ORDER` | local | añade `OrderRejected` | `DONE` |

| Modo | Recorrido | Estado final del pedido |
|---|---|---|
| `CHECKOUT` | `RESERVE_STOCK` -> `AUTHORIZE_PAYMENT` -> `CONFIRM_ORDER` | `CONFIRMED` |
| `REJECTING` | [`REFUND_PAYMENT` ->] `RELEASE_STOCK` -> `REJECT_ORDER` | `REJECTED`, con un motivo |
| `CANCELLING` | `REFUND_PAYMENT` -> `RELEASE_STOCK` | `CANCELLED` (el evento se escribe cuando el cliente lo pide) |

El estado de la saga es `RUNNING`, `COMPLETED` o `STUCK`. Los motivos de rechazo son códigos estables:

| Motivo | Cuándo |
|---|---|
| `out-of-stock` | Inventory respondió que falta un producto; el detalle indica las unidades solicitadas y disponibles por SKU |
| `card-limit-exceeded` | Payments denegó el importe (por encima de 300, o cualquier importe mientras el fallo `payments.decline-all` está activo) |
| `inventory-unavailable` | `RESERVE_STOCK` falló técnicamente en todos los intentos |
| `payment-unavailable` | `AUTHORIZE_PAYMENT` falló técnicamente en todos los intentos; la saga reembolsa o anula antes de liberar |

[`CheckoutSaga`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/CheckoutSaga.java) es una máquina de estados pura: decide, nunca llama a nada. Cada intento de cada paso se añade a su log (`checkout_saga_step`) con un resultado `SUCCEEDED`, `DECLINED`, `RETRYING` o `GAVE_UP`, que es lo que muestran la página del pedido y `GET /api/orders/{id}/checkout`.

## Reglas que la hacen fiable

**La saga se escribe junto con el pedido.** `PlaceOrderCommand` añade `OrderPlaced` e inserta la fila de la saga en la misma transacción ([`OrderCommandHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/command/OrderCommandHandler.java)): no hay pedido sin saga ni saga sin pedido. La saga guarda también el `traceparent` W3C de la petición (columna `trace_parent`), para que todos los pasos posteriores pertenezcan a la misma traza. Tras un reinicio, el ejecutor reanuda cada saga en el paso en el que estaba.

**Un "no" de negocio es un valor de respuesta.** "Sin stock" y "tarjeta denegada" vuelven como `StockReservation(false, shortages)` y `PaymentAuthorization(false, paymentId, reason)`. Las excepciones se reservan para los fallos técnicos, que la saga reintenta. Los puertos lo dicen de forma explícita:

```java
/**
 * The inventory as the checkout saga needs it. Any exception is a technical failure that the saga
 * retries; a shortage comes back as a {@link StockReservation}.
 */
public interface InventoryGateway {
  StockReservation reserve(UUID orderId, List<ReservationLine> lines);
  StockRelease release(UUID orderId);
}
```

**Todo comando remoto es idempotente, con el id de pedido como clave.** Tras un timeout, la saga no puede saber si el otro servicio ejecutó el comando, así que lo envía de nuevo:

- Un `ReserveStock` para un pedido que ya tiene una reserva responde con el resultado de esa reserva en lugar de reservar dos veces.
- Un `AuthorizePayment` para un pedido que ya tiene un pago devuelve la respuesta de ese pago.
- `ReleaseStock` y `RefundPayment` no hacen nada cuando no hay nada que deshacer.

**Deshacer deja una lápida.** Una reserva liberada se conserva con estado `RELEASED`; un `ReleaseStock` que llega antes que cualquier reserva guarda una ya liberada. Un `ReserveStock` tardío, el reintento de un intento que parecía perdido, la encuentra entonces y no reserva nada. Del mismo modo, un `RefundPayment` para un pedido sin pago registra un pago anulado (`PaymentDeclined` con motivo `voided`), y un `AuthorizePayment` tardío no cobra nada. Por eso un checkout rechazado siempre libera el stock, incluso tras una respuesta de "sin stock": puede que un intento anterior que agotó su timeout siga en la cola.

**Un producto desconocido es un reintento, no una falta de stock.** Justo después de publicarse un producto, puede que inventory todavía no haya recibido su `ProductPublished`. En ese caso `ReserveStock` falla en lugar de responder "sin stock", y la saga reintenta hasta que inventory se pone al día.

**Las llamadas remotas se ejecutan fuera de transacciones.** Una llamada puede tardar hasta el timeout de respuesta, así que [`CheckoutSagaProcessor`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutSagaProcessor.java) llama sin una transacción abierta y registra el resultado después en una transacción corta. Antes de registrarlo, recarga la saga y comprueba que el paso y la columna `@Version` (`row_version`) no han cambiado; si otro ejecutor la ha movido, el resultado se descarta. Como los comandos remotos son idempotentes, haber enviado uno dos veces no causa ningún daño. Los pasos locales (confirmar, rechazar) añaden el evento del pedido y mueven la saga en una única transacción.

**Los reintentos usan backoff exponencial.** Un fallo técnico programa el siguiente intento tras `initialBackoff * 2^(attempts-1)`, con un máximo de `maxBackoff`: 1 s, 2 s, 4 s, 8 s, 16 s y después 30 s. Un paso hacia delante (`RESERVE_STOCK`, `AUTHORIZE_PAYMENT`) se rinde tras 8 intentos, unos dos minutos contando los timeouts de respuesta, lo suficiente para aguantar un reinicio progresivo; el pedido se rechaza entonces con `inventory-unavailable` o `payment-unavailable` y la saga compensa.

**Las compensaciones nunca se rinden.** Un reembolso o una liberación abandonados dejarían stock retenido o a un cliente cobrado. Tras 20 intentos fallidos la saga se marca como `STUCK`, lo que notifica la alerta "Checkout compensations are stuck" (compensaciones de checkout atascadas), y sigue reintentando cada 30 segundos; termina por sí sola en cuanto el otro servicio vuelve a responder. Los pasos locales siguen la misma regla.

**Cancelar forma parte de la saga.** Un cliente puede cancelar un pedido confirmado: `CancelOrderCommand` añade `OrderCancelled` y cambia la saga completada a `CANCELLING` en una sola transacción, con la traza de la petición de cancelación. Cancelar mientras el checkout sigue en curso responde 409 `checkout-in-progress`; cancelar un pedido rechazado responde 409 `order-rejected`; cancelar dos veces no cambia nada.

## Configuración

[`CheckoutProperties`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutProperties.java), prefijo `shop.checkout`:

| Propiedad | Por defecto | Significado |
|---|---|---|
| `enabled` | `true` | Si el ejecutor programado hace avanzar las sagas; los tests lo desactivan y conducen la saga paso a paso |
| `poll-interval` | `500ms` | Pausa entre dos búsquedas de sagas pendientes |
| `batch-size` | `20` | Sagas reclamadas por búsqueda |
| `max-attempts` | `8` (`CHECKOUT_MAX_ATTEMPTS`) | Intentos de un paso hacia delante antes de rechazar el checkout |
| `compensation-max-attempts` | `20` (`CHECKOUT_COMPENSATION_MAX_ATTEMPTS`) | Intentos de cualquier otro paso antes de marcar la saga como atascada |
| `initial-backoff` / `max-backoff` | `1s` / `30s` | Esperas entre reintentos |
| `lease` | `30s` | Cuánto tiempo pertenece una saga reclamada al ejecutor que la reclamó |

El timeout de respuesta de RabbitMQ es `spring.rabbitmq.template.reply-timeout`, `5s` por defecto (`CHECKOUT_REPLY_TIMEOUT`).

## Ejecutar la saga en varias réplicas

[`CheckoutSagaScheduler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/CheckoutSagaScheduler.java) ejecuta [`CheckoutSagaRunner`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutSagaRunner.java) en su propio hilo en cada intervalo de sondeo. Cada ejecución reclama primero sus sagas a través del puerto [`DueCheckouts`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/DueCheckouts.java), implementado por [`PostgresDueCheckouts`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/PostgresDueCheckouts.java) con una única sentencia:

```sql
update checkout_saga set next_attempt_at = :leasedUntil
where order_id in (
    select order_id from checkout_saga
    where state in ('RUNNING', 'STUCK') and next_attempt_at <= :now
    order by next_attempt_at
    limit :max
    for update skip locked)
returning order_id
```

- `FOR UPDATE SKIP LOCKED` permite que ejecutores concurrentes tomen conjuntos disjuntos de sagas sin esperarse entre sí, y el update se confirma de inmediato.
- La reclamación es un **lease** (una concesión temporal): el siguiente intento de la saga se mueve al final del lease (`shop.checkout.lease`, 30 s), de modo que ningún otro ejecutor la recoge mientras tanto. El paso que se ejecuta a continuación fija el siguiente intento real. Si la instancia muere a mitad de un paso, el lease vence y otra instancia se hace cargo de la saga.
- El lease solo evita trabajo duplicado. La corrección nunca depende de él: ejecutar un paso dos veces es seguro porque la saga comprueba su versión antes de registrar un resultado y todo comando remoto es idempotente.
- El ejecutor hace avanzar en paralelo las sagas de un lote sobre hilos virtuales, de modo que un servicio lento o inalcanzable no retiene los checkouts que no dependen de él. Los errores de una saga concreta se registran en el log y el ejecutor continúa; el scheduler captura también los `Error`, así que el bucle nunca puede detenerse en silencio.
- Un índice parcial, `checkout_saga_due_idx on (next_attempt_at) where state in ('RUNNING','STUCK')`, mantiene barata la reclamación.

## Métricas

[`CheckoutMetrics`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutMetrics.java) captura lo que cada paso registró dentro de su transacción y lo publica tras el commit, de modo que un paso revertido nunca se contabiliza:

| Métrica | Tipo | Etiquetas | Significado |
|---|---|---|---|
| `shop.checkout.steps` | counter | `step`, `result` = `succeeded` / `declined` / `retrying` / `gave-up` | Intentos de cada paso |
| `shop.checkout.completed` | counter | `outcome` = `confirmed` / `rejected` / `cancelled`, `reason` | Sagas terminadas; `reason` es el motivo de rechazo o `none` |
| `shop.checkout.duration` | timer | `outcome` | Desde que se realiza el pedido hasta que se confirma o se rechaza |
| `shop.checkout.sagas` | gauge | `state` = `running` / `stuck` | Sagas que aún tienen trabajo pendiente, leídas de la base de datos |

Alimentan el dashboard "Shop · Checkout saga & messaging" y dos reglas de alerta; consulta [Observabilidad](observability.md).

## Probar cada recorrido desde la tienda

Con la plataforma en marcha (consulta [Despliegue](deployment.md)), elige un cliente en la cabecera de la tienda:

| Recorrido | Cómo |
|---|---|
| Confirmado | Añade productos al carrito y finaliza la compra. La página del pedido sigue la saga paso a paso hasta que el pedido se confirma. |
| Tarjeta denegada | Finaliza la compra de un pedido de más de 300. Se rechaza con `card-limit-exceeded`, y la liberación del stock aparece en la línea de tiempo. |
| Sin stock | En la página Stock (`/backoffice/stock`) reduce las unidades de un producto y después pide más de las que quedan. |
| Cancelado | Cancela un pedido confirmado desde su página. La saga reembolsa el pago (la página Payments lo muestra como reembolsado) y libera el stock. |
| Payments caído | Detén payments durante un checkout (`docker compose -f deploy/compose/compose.yaml stop payments-service`). La saga reintenta, se rinde con `payment-unavailable` al cabo de unos dos minutos y completa el reembolso y la liberación cuando payments vuelve. |
| Fallos transitorios | En la página Chaos (`/chaos`) activa `AuthorizePayment.fail`, realiza un pedido y desactívalo: la saga reintenta y después confirma. Con `relay.paused` la saga sigue terminando (usa RabbitMQ), mientras que la lista de pedidos, los informes y los avisos esperan hasta que el relay se reanuda. |

![Página del pedido con la línea de tiempo de la saga](../assets/screenshots/shop-order-saga.png)

## Tests

| Test | Alcance |
|---|---|
| [`CheckoutSagaTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/CheckoutSagaTest.java) | La máquina de estados: cada transición, reintentos, compensaciones atascadas, cancelación, incluidos los pasos que no están permitidos |
| [`CheckoutSagaIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutSagaIT.java) | Camino feliz, sin stock, tarjeta denegada, reintentos, payments caído, compensación atascada, cancelación, contra PostgreSQL. [`FakeCheckout`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/FakeCheckout.java) guioniza inventory y payments por pedido; [`CheckoutDriver`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutDriver.java) hace avanzar la saga paso a paso con `shop.checkout.enabled=false` |
| [`CheckoutOverRabbitIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutOverRabbitIT.java) | Los gateways RabbitMQ reales: respuestas, errores remotos y timeouts de respuesta |
| [`DueCheckoutsIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/DueCheckoutsIT.java) | Los ejecutores concurrentes nunca reclaman la misma saga; una saga reclamada solo vuelve cuando vence su lease; los checkouts terminados se cuentan y se cronometran |
| [`CheckoutTracingIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutTracingIT.java) | Todos los pasos y todos los eventos pertenecen a la traza que realizó el pedido; una cancelación continúa la traza de la petición de cancelación; un error en un paso se registra en el log y el ejecutor continúa |
| `InventoryMessagingIT`, `PaymentsOverRabbitIT` | Los lados de inventory y payments de los comandos RabbitMQ |
| `CheckoutJourneyTest`, `ChaosJourneyTest`, `ScarceStockStressTest`, `ChaosStormStressTest` | Tests de sistema contra una plataforma en ejecución, cada uno terminando con las invariantes entre servicios de `Checkouts.assertConsistent`; consulta [Testing](testing.md#tests-de-sistema) |

## Relacionado

- [Arquitectura](architecture.md)
- [Las librerías en la práctica](libraries.md#peticiónrespuesta-sobre-rabbitmq-para-la-saga)
- [Event sourcing y outbox](event-sourcing-and-outbox.md)
- [Resiliencia y escalado](resilience-and-scaling.md)
- [Observabilidad](observability.md)
- [Testing](testing.md)
