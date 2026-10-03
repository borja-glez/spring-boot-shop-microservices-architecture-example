# Checkout saga

Placing an order touches three services that share no database: orders records the order, inventory holds the stock and payments takes the money. Mercado coordinates them with an orchestrated saga: a persisted process manager in `orders-service` that sends idempotent commands to inventory and payments over RabbitMQ request/reply, records each answer, and undoes what was done when a step cannot succeed. Business refusals (no stock, card declined) are answers, not errors; technical failures (timeouts, a service down) are retried with exponential backoff; compensations never give up. Any number of `orders-service` replicas can run the saga side by side, each saga claimed by one of them at a time.

<p align="center"><img src="../assets/diagrams/checkout-saga.svg" alt="Checkout saga state machine with its forward steps, compensations and final states" width="100%"></p>

## Roles

| Service | Role | State |
|---|---|---|
| `orders-service` | Orchestrator. Decides the next step, calls the other services, records results, writes `OrderConfirmed` / `OrderRejected`. | `checkout_saga` and `checkout_saga_step` in the `orders` database; the order's event stream |
| `inventory-service` | Reserves and releases stock, all or nothing, idempotently per order. | `stock_item`, `reservation`, `reservation_line`; events in its outbox |
| `payments-service` | Authorizes, declines and refunds payments, idempotently per order. Declines amounts above the card limit (300, `shop.payments.card-limit`). | the `payment` stream in its event store; `payment_view` for the backoffice |

Commands travel over RabbitMQ (`RabbitMqCommandBus.dispatchAndReceive`); the events each service records (`OrderConfirmed`, `StockReserved`, `PaymentAuthorized`...) leave through the outbox to Kafka for the read models.

## Steps and modes

A saga has a **mode**, what it is working towards, and a current **step**. [`CheckoutStep`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/CheckoutStep.java) lists the steps; four are remote and two are local.

| Step | Kind | Call | Success moves to |
|---|---|---|---|
| `RESERVE_STOCK` | remote, forward | `ReserveStock` -> `StockReservation` | `AUTHORIZE_PAYMENT` (reserved) or `RELEASE_STOCK` in `REJECTING` (short) |
| `AUTHORIZE_PAYMENT` | remote, forward | `AuthorizePayment` -> `PaymentAuthorization` | `CONFIRM_ORDER` (authorized) or `RELEASE_STOCK` in `REJECTING` (declined) |
| `CONFIRM_ORDER` | local | appends `OrderConfirmed` | `DONE` |
| `REFUND_PAYMENT` | remote, compensation | `RefundPayment` -> `PaymentRefund` | `RELEASE_STOCK` |
| `RELEASE_STOCK` | remote, compensation | `ReleaseStock` -> `StockRelease` | `REJECT_ORDER` (rejecting) or `DONE` (cancelling) |
| `REJECT_ORDER` | local | appends `OrderRejected` | `DONE` |

| Mode | Path | Final order status |
|---|---|---|
| `CHECKOUT` | `RESERVE_STOCK` -> `AUTHORIZE_PAYMENT` -> `CONFIRM_ORDER` | `CONFIRMED` |
| `REJECTING` | [`REFUND_PAYMENT` ->] `RELEASE_STOCK` -> `REJECT_ORDER` | `REJECTED`, with a reason |
| `CANCELLING` | `REFUND_PAYMENT` -> `RELEASE_STOCK` | `CANCELLED` (the event is written when the customer asks) |

The saga's state is `RUNNING`, `COMPLETED` or `STUCK`. Rejection reasons are stable codes:

| Reason | When |
|---|---|
| `out-of-stock` | Inventory answered that a product is short; the detail lists requested and available units per SKU |
| `card-limit-exceeded` | Payments declined the amount (above 300, or any amount while the `payments.decline-all` fault is on) |
| `inventory-unavailable` | `RESERVE_STOCK` failed technically on every attempt |
| `payment-unavailable` | `AUTHORIZE_PAYMENT` failed technically on every attempt; the saga refunds or voids before releasing |

[`CheckoutSaga`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/CheckoutSaga.java) is a pure state machine: it decides, it never calls anything. Each attempt of each step is appended to its log (`checkout_saga_step`) with an outcome of `SUCCEEDED`, `DECLINED`, `RETRYING` or `GAVE_UP`, which is what the order page and `GET /api/orders/{id}/checkout` show.

## Rules that make it reliable

**The saga is written with the order.** `PlaceOrderCommand` appends `OrderPlaced` and inserts the saga row in the same transaction ([`OrderCommandHandler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/command/OrderCommandHandler.java)): there is no order without a saga and no saga without an order. The saga also stores the W3C `traceparent` of the request (`trace_parent` column) so every later step belongs to the same trace. After a restart the runner resumes every saga at the step where it was.

**A business "no" is a reply value.** "Out of stock" and "card declined" come back as `StockReservation(false, shortages)` and `PaymentAuthorization(false, paymentId, reason)`. Exceptions are reserved for technical failures, which the saga retries. The ports say it explicitly:

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

**Every remote command is idempotent, keyed by order id.** After a timeout the saga cannot know whether the other service executed the command, so it sends it again:

- `ReserveStock` for an order that already has a reservation answers with that reservation's outcome instead of reserving twice.
- `AuthorizePayment` for an order that already has a payment returns that payment's answer.
- `ReleaseStock` and `RefundPayment` do nothing when there is nothing to undo.

**Undoing leaves a tombstone.** A released reservation is kept with status `RELEASED`; a `ReleaseStock` that arrives before any reservation stores a released one. A late `ReserveStock`, the retry of an attempt that seemed lost, then finds it and reserves nothing. Likewise a `RefundPayment` for an order without a payment records a voided payment (`PaymentDeclined` with reason `voided`), and a late `AuthorizePayment` charges nothing. That is why a rejected checkout always releases the stock, even after an "out of stock" answer: an earlier timed-out attempt may still be queued.

**An unknown product is a retry, not a shortage.** Right after a product is published, the inventory may not have received its `ProductPublished` yet. `ReserveStock` then fails instead of answering "out of stock", and the saga retries until the inventory has caught up.

**Remote calls run outside transactions.** A call can take up to the reply timeout, so [`CheckoutSagaProcessor`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutSagaProcessor.java) calls without an open transaction and records the result afterwards in a short one. Before recording, it reloads the saga and checks that the step and the `@Version` column (`row_version`) are unchanged; if another runner moved it, the result is dropped. Since remote commands are idempotent, having sent one twice is harmless. Local steps (confirm, reject) append the order's event and move the saga in a single transaction.

**Retries back off exponentially.** A technical failure schedules the next attempt after `initialBackoff * 2^(attempts-1)`, capped at `maxBackoff`: 1 s, 2 s, 4 s, 8 s, 16 s, then 30 s. A forward step (`RESERVE_STOCK`, `AUTHORIZE_PAYMENT`) gives up after 8 attempts, roughly two minutes including reply timeouts, which is enough to ride out a rolling restart; the order is then rejected with `inventory-unavailable` or `payment-unavailable` and the saga compensates.

**Compensations never give up.** A refund or a release that is abandoned would leave stock held or a customer charged. After 20 failed attempts the saga is marked `STUCK`, which the "Checkout compensations are stuck" alert reports, and keeps retrying every 30 seconds; it finishes on its own as soon as the other service answers again. Local steps follow the same rule.

**Cancelling is part of the saga.** A customer can cancel a confirmed order: `CancelOrderCommand` appends `OrderCancelled` and switches the completed saga to `CANCELLING` in one transaction, with the trace of the cancel request. Cancelling while the checkout is still running answers 409 `checkout-in-progress`; cancelling a rejected order answers 409 `order-rejected`; cancelling twice changes nothing.

## Configuration

[`CheckoutProperties`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutProperties.java), prefix `shop.checkout`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Whether the scheduled runner advances sagas; tests turn it off and drive the saga step by step |
| `poll-interval` | `500ms` | Pause between two looks for due sagas |
| `batch-size` | `20` | Sagas claimed per look |
| `max-attempts` | `8` (`CHECKOUT_MAX_ATTEMPTS`) | Attempts of a forward step before the checkout is rejected |
| `compensation-max-attempts` | `20` (`CHECKOUT_COMPENSATION_MAX_ATTEMPTS`) | Attempts of any other step before the saga is marked stuck |
| `initial-backoff` / `max-backoff` | `1s` / `30s` | Retry waits |
| `lease` | `30s` | How long a claimed saga belongs to the runner that claimed it |

The RabbitMQ reply timeout is `cqrs.rabbitmq.commands.reply-timeout`, `5s` by default (`CHECKOUT_REPLY_TIMEOUT`).

## Running the saga on several replicas

[`CheckoutSagaScheduler`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/CheckoutSagaScheduler.java) runs [`CheckoutSagaRunner`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutSagaRunner.java) on its own thread every poll interval. Each run first claims its sagas through the [`DueCheckouts`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/DueCheckouts.java) port, implemented by [`PostgresDueCheckouts`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/infrastructure/checkout/PostgresDueCheckouts.java) with one statement:

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

- `FOR UPDATE SKIP LOCKED` lets concurrent runners take disjoint sets of sagas without waiting for each other, and the update commits at once.
- The claim is a **lease**: the saga's next attempt moves to the end of the lease (`shop.checkout.lease`, 30 s), so no other runner picks it up meanwhile. The step it then runs sets the real next attempt. If the instance dies mid-step, the lease runs out and another instance takes the saga over.
- The lease only avoids duplicated work. Correctness never depends on it: a step run twice is safe because the saga checks its version before recording a result and every remote command is idempotent.
- The runner advances the sagas of a batch in parallel on virtual threads, so a slow or unreachable service does not hold back checkouts that do not depend on it. Errors of a single saga are logged and the runner carries on; the scheduler also catches `Error`s, so the loop can never stop silently.
- A partial index, `checkout_saga_due_idx on (next_attempt_at) where state in ('RUNNING','STUCK')`, keeps the claim cheap.

## Metrics

[`CheckoutMetrics`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/checkout/CheckoutMetrics.java) captures what each step recorded inside its transaction and publishes it after the commit, so a rolled-back step is never counted:

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `shop.checkout.steps` | counter | `step`, `result` = `succeeded` / `declined` / `retrying` / `gave-up` | Attempts of each step |
| `shop.checkout.completed` | counter | `outcome` = `confirmed` / `rejected` / `cancelled`, `reason` | Finished sagas; `reason` is the rejection reason or `none` |
| `shop.checkout.duration` | timer | `outcome` | From order placed to confirmed or rejected |
| `shop.checkout.sagas` | gauge | `state` = `running` / `stuck` | Sagas that still have work to do, read from the database |

They feed the "Shop · Checkout saga & messaging" dashboard and two alert rules; see [Observability](observability.md).

## Trying every path from the shop

With the platform running (see [Deployment](deployment.md)), pick a customer in the header of the shop:

| Path | How |
|---|---|
| Confirmed | Add products to the cart and check out. The order page follows the saga step by step until the order is confirmed. |
| Card declined | Check out an order above 300. It is rejected with `card-limit-exceeded`, and the release of the stock appears in the timeline. |
| Out of stock | On the Stock page (`/backoffice/stock`) lower the units of a product, then order more than remain. |
| Cancelled | Cancel a confirmed order from its page. The saga refunds the payment (the Payments page shows it as refunded) and releases the stock. |
| Payments down | Stop payments during a checkout (`docker compose -f deploy/compose/compose.yaml stop payments-service`). The saga retries, gives up with `payment-unavailable` after about two minutes, and completes the refund and the release once payments is back. |
| Transient failures | On the Chaos page (`/chaos`) turn on `AuthorizePayment.fail`, place an order, and turn it off: the saga retries and then confirms. With `relay.paused` the saga still finishes (it uses RabbitMQ), while the order list, reports and notices wait until the relay resumes. |

![Order page with the saga timeline](../assets/screenshots/shop-order-saga.png)

## Tests

| Test | Scope |
|---|---|
| [`CheckoutSagaTest`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/domain/CheckoutSagaTest.java) | The state machine: every transition, retries, stuck compensations, cancellation, including steps that are not allowed |
| [`CheckoutSagaIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutSagaIT.java) | Happy path, out of stock, declined card, retries, payments down, stuck compensation, cancellation, against PostgreSQL. [`FakeCheckout`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/FakeCheckout.java) scripts inventory and payments per order; [`CheckoutDriver`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutDriver.java) advances the saga step by step with `shop.checkout.enabled=false` |
| [`CheckoutOverRabbitIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutOverRabbitIT.java) | The real RabbitMQ gateways: replies, remote errors and reply timeouts |
| [`DueCheckoutsIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/DueCheckoutsIT.java) | Concurrent runners never claim the same saga; a claimed saga comes back only when its lease runs out; finished checkouts are counted and timed |
| [`CheckoutTracingIT`](../../services/orders-service/src/test/java/com/borjaglez/shop/orders/checkout/CheckoutTracingIT.java) | Every step and every event belong to the trace that placed the order; a cancellation continues the cancel request's trace; an error in a step is logged and the runner carries on |
| `InventoryMessagingIT`, `PaymentsOverRabbitIT` | The inventory and payments sides of the RabbitMQ commands |
| `CheckoutJourneyTest`, `ChaosJourneyTest`, `ScarceStockStressTest`, `ChaosStormStressTest` | System tests against a running platform, each ending with the cross-service invariants of `Checkouts.assertConsistent`; see [Testing](testing.md#system-tests) |

## Related

- [Architecture](architecture.md)
- [The libraries in practice](libraries.md#rabbitmq-requestreply-for-the-saga)
- [Event sourcing and outbox](event-sourcing-and-outbox.md)
- [Resilience and scaling](resilience-and-scaling.md)
- [Observability](observability.md)
- [Testing](testing.md)
