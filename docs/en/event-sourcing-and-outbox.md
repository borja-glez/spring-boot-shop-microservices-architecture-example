# Event sourcing and the transactional outbox

Every service that publishes events keeps them in one table, `event_store`, provided by [`platform/es-kit`](../../platform/es-kit). For orders and payments that table is the source of truth: an order or a payment is its stream of events, and its state is rebuilt by replaying them. For the catalog and the inventory, which store their state in ordinary tables, the same table is only the outbox. In both cases writing an event and "sending" it are the same insert, in the same transaction as the business change, so an event is published if and only if the change commits. A relay publishes pending rows to Kafka in global order once the broker acknowledges them, delivery is at least once, and every consumer is idempotent and tolerant to events arriving out of order across types.

<p align="center"><img src="../assets/diagrams/outbox.svg" alt="Transactional outbox: the business transaction writes the change and its events, the relay publishes pending rows to Kafka, idempotent consumers apply each event once" width="100%"></p>

## The event store table

Created by es-kit's migration [`V1000__event_store.sql`](../../platform/es-kit/src/main/resources/db/eskit/V1000__event_store.sql) in the database of each service that includes es-kit:

| Column | Type | Purpose |
|---|---|---|
| `global_position` | `bigserial`, primary key | Global order of the store; the relay publishes following it. |
| `event_id` | `uuid`, unique | Id of the spring-boot-cqrs message. It survives the trip through Kafka, and consumers deduplicate with it. |
| `stream_type`, `stream_id` | `varchar` | The stream the event belongs to (`order` + order id, `payment` + order id, `product` + product id, `reservation` + order id, `stock` + product id). |
| `version` | `bigint`, nullable | Position in an event-sourced stream, from 1. `null` for outbox rows of state-based aggregates. |
| `event_type` | `varchar(200)` | Wire name from `@CqrsMessage`, for example `shop.orders.1.event.order.order-placed`. [`EventTypeRegistry`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventTypeRegistry.java) maps it to the class. |
| `payload` | `jsonb` | The serialized event. |
| `metadata` | `jsonb` | The spring-boot-cqrs `MessageContext` of the request (correlation id and related entries) plus the W3C trace headers captured by `TraceCarrier`. |
| `occurred_at` | `timestamptz` | When the event was stored. |
| `published_at` | `timestamptz`, nullable | When the relay handed the event to the broker; `null` while pending. |
| `publish_attempts`, `last_error` | `int`, `varchar(1000)` | Failed publication attempts and the last error. |

Indexes:

- `event_store_stream_version_uq`: unique `(stream_type, stream_id, version)` where `version is not null`. It is the optimistic concurrency guard of event-sourced streams.
- `event_store_pending_idx`: `(global_position)` where `published_at is null`, which keeps the relay's query and the backlog metrics cheap whatever the size of the store.
- `event_store_stream_idx` and `event_store_type_idx` for loading streams and exploring by type.

The same migration creates `processed_message (consumer, message_id, processed_at)`, the idempotency markers of consumers.

## Two ways to use the same table

| | Event-sourced | State-based with outbox |
|---|---|---|
| Services | orders ([`Order`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/Order.java)), payments ([`Payment`](../../services/payments-service/src/main/java/com/borjaglez/shop/payments/domain/Payment.java)) | catalog (`Product`), inventory (`StockItem`, `Reservation`) |
| Source of truth | the stream in `event_store` | the entity tables |
| API | `AggregateStore.load` / `save` -> `EventStore.append` | `EventStore.record` |
| `version` | 1, 2, 3... per stream | `null` |
| Concurrency | expected version checked on append | JPA `@Version` on the entities |

### Event-sourced aggregates

An aggregate extends [`EventSourcedAggregate`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventSourcedAggregate.java). Command methods validate and call `apply(event)`; `when(event)` is the only place that mutates state; loading replays the stored events through the same `when`. The state after a command and after a reload is therefore identical by construction.

```java
public void confirm(UUID paymentId) {
  if (status == OrderStatus.CONFIRMED) {
    return;
  }
  requireInCheckout("confirm");
  apply(new OrderConfirmed(orderId, paymentId));
}

@Override
protected void when(Event event) {
  switch (event) {
    case OrderPlaced placed -> { /* id, customer, lines, total, status = PLACED */ }
    case OrderConfirmed confirmed -> {
      paymentId = confirmed.getPaymentId();
      status = OrderStatus.CONFIRMED;
    }
    case OrderRejected rejected -> status = OrderStatus.REJECTED;
    case OrderCancelled cancelled -> status = OrderStatus.CANCELLED;
    default -> throw new IllegalArgumentException("Unexpected event " + event.getClass());
  }
}
```

[`AggregateStore`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/AggregateStore.java) loads and saves one aggregate type. Each service declares one bean per stream type:

```java
@Bean
AggregateStore<Order> orderStore(EventStore eventStore) {
  return new AggregateStore<>(eventStore, Order.STREAM_TYPE, Order::new);
}
```

A command handler loads, decides and saves; the new events are appended with the version the handler read:

```java
Order order = orders.load(command.getOrderId().toString())
    .filter(o -> o.belongsTo(command.getCustomerId()))
    .orElseThrow(() -> new NotFoundException("order-not-found", "Unknown order " + command.getOrderId()));
order.cancel(command.getCustomerId(), command.getReason());
orders.save(order);
```

### State-based aggregates

The catalog saves products as normal JPA entities and records the events the aggregate collected in the same transaction:

```java
private void publish(Product product) {
  var events = product.pullEvents();
  if (!events.isEmpty()) {
    eventStore.record("product", product.getId().toString(), events);
  }
}
```

Inventory does the same for `StockReserved`, `StockReleased` (stream `reservation`) and `StockAdjusted` (stream `stock`). Keeping both styles on the same infrastructure makes the difference visible: the relay, the explorer and the consumers do not care which one wrote a row.

## Optimistic concurrency

[`EventStore.append`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EventStore.java) compares the stream's current version with the version the caller loaded. If they differ, or if a concurrent transaction inserts the same version between the check and the insert (the unique index rejects it), it throws [`ConcurrencyConflictException`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/ConcurrencyConflictException.java). It is a `ConflictException`, so the API answers 409 `concurrent-modification` and the client can reload and retry. State-based entities get the same answer from their JPA `@Version` through `DataAccessProblemMapper`.

## The outbox relay

<p align="center"><img src="../assets/diagrams/cqrs-event-sourcing.svg" alt="Write side and read side of orders: event-sourced aggregate, event store, relay to Kafka, projection and queries" width="100%"></p>

A service turns the relay on by declaring an [`OutboxDestination`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxDestination.java) bean; catalog, orders, inventory and payments declare `KafkaOutboxDestination`. Reporting includes es-kit only for its idempotent consumers and declares none.

[`OutboxRelay`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/OutboxRelay.java) runs on its own thread (`OutboxRelayScheduler`, a `SmartLifecycle` started after the context is ready and stopped before the database goes away), every `shop.outbox.relay.interval` (500 ms by default):

```sql
select * from event_store where published_at is null
order by global_position limit :batch for update skip locked
```

1. Each batch (`shop.outbox.relay.batch-size`, 100 by default) runs in one transaction and locks its rows with `FOR UPDATE SKIP LOCKED`, so several replicas of a service relay in parallel without publishing the same row twice.
2. Rows are published in `global_position` order. Each one is deserialized, its correlation id is restored into the `MessageContext`, its trace is resumed (`TraceCarrier`), and it is handed to the destination.
3. `KafkaOutboxDestination` publishes through spring-boot-cqrs's `KafkaMessagePublisher` and returns only once Kafka acknowledged the record. A broker failure surfaces as an exception.
4. A published row gets `published_at`. On the first failure the batch stops: the row's `publish_attempts` is incremented and `last_error` records the reason, the rows before it are committed as published, and the failed row is retried on the next run. With one relaying instance a later event never overtakes an earlier one.
5. The loop continues with the next batch until a batch is smaller than the batch size or fails.

A row that can never be published (for example an event type that no longer exists) blocks the rows behind it by design; `publish_attempts` and `last_error` make it visible in the event store explorer, and `shop.outbox.oldest.age.seconds` raises the "Events are not reaching Kafka" alert. See [Observability](observability.md).

The relay also registers two demo faults, `relay.paused` (stops publishing, as if Kafka were down) and `relay.duplicate` (publishes every event twice). See [Resilience and scaling](resilience-and-scaling.md#chaos-panel).

| Property | Default | Meaning |
|---|---|---|
| `shop.outbox.relay.enabled` | `true` | Whether the scheduled relay runs |
| `shop.outbox.relay.interval` | `500ms` | Pause between runs |
| `shop.outbox.relay.batch-size` | `100` | Rows locked and published per transaction |
| `shop.event-store.event-packages` | `com.borjaglez.shop.contracts` | Where `@CqrsMessage` events are looked up |

## At-least-once delivery and idempotent consumers

If the relay publishes a row and the process dies before the transaction commits, the row is still pending and will be published again. Kafka may also redeliver to a consumer group during a rebalance. Delivery is therefore at least once, and every consumer applies each event at most once through [`IdempotentConsumer`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/IdempotentConsumer.java):

```java
@Transactional
public boolean once(String consumer, String messageId, Runnable effect) {
  boolean seen = processed.query()
      .where("consumer", Operators.EQUALS, consumer)
      .where("messageId", Operators.EQUALS, messageId)
      .count() > 0;
  if (seen) {
    meters.counter("shop.consumer.events", "consumer", consumer, "outcome", "duplicate").increment();
    return false;
  }
  processed.save(new ProcessedMessage(consumer, messageId, OffsetDateTime.now(clock)));
  effect.run();
  meters.counter("shop.consumer.events", "consumer", consumer, "outcome", "applied").increment();
  return true;
}
```

The marker and the effect commit in the same transaction: if the effect fails, neither is stored and the redelivery tries again; if it succeeds, redeliveries are skipped. The `(consumer, message_id)` primary key also stops two concurrent deliveries of the same event. The overload that takes the `Event` also records `shop.consumer.lag`, the time between the event and its projection.

Consumer names: `orders.catalog-products`, `orders.order-view`, `inventory.catalog-products`, `reporting.orders`. `notifications-service` (Boot 3.5, without es-kit) reaches the same guarantee by keying each notice on the event id.

## Ordering

spring-boot-cqrs publishes every event on the shared topic `shop.events`, keyed by its message name. Kafka keeps order within a partition, so ordering is guaranteed per event type, not per aggregate: an `OrderCancelled` can reach a consumer before the `OrderPlaced` it cancels, and a `ProductPriceChanged` before the `ProductPublished` of the same product. The projections are written to converge whatever the delivery order:

- **As-of timestamps.** [`CatalogProduct`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/CatalogProduct.java) keeps, for each group of attributes (details, price, availability), the time of the event that set it (`details_as_of`, `price_as_of`, `availability_as_of`) and ignores older events. A late `ProductPublished` neither undoes a newer price nor puts a discontinued product back on sale.
- **Monotonic status.** [`OrderView`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/domain/OrderView.java) and `ReportOrder` create the row with whichever event arrives first, and the status only moves forward. A late `OrderPlaced` fills in the details of an order already known to be cancelled without reopening it. Until then the row has no customer, so no customer lists it.
- **Optimistic locking on projections.** Projection rows carry a `row_version`; two consumers updating the same row cannot overwrite each other. The loser fails, Kafka redelivers and the event applies on fresh data.
- **Waiting for the owner.** A notice whose order is not known yet is stored without a customer and addressed once `OrderPlaced` arrives. See [Read models](read-models.md#notifications-service).

## Reading the store

| Endpoint | Source | Consistency |
|---|---|---|
| `GET /api/orders`, `GET /api/orders/{id}` | `order_view`, filtered with specification-repository | Eventual: updated from Kafka a moment after the command |
| `GET /api/orders/{id}/history` | the order's stream, replayed one event at a time to show the state after each version, with `publishedAt` of each event | Immediate |
| `GET /api/orders/events` | the whole event store of orders, with HTTP filters on `eventType`, `streamType`, `streamId`, `version`, `occurredAt`, `publishedAt`, `publishAttempts`; newest first by default | Immediate |
| `GET /api/payments/{orderId}/history` | the payment's stream | Immediate |

The order page of the shop shows both sides at once: the saga and the read model, and the history from the event store with each event marked as published or still pending in the outbox. The Event store page (`/event-store`) is the explorer, useful to watch the relay at work or to find a row with `publish_attempts > 0`:

![Event store explorer](../assets/screenshots/shop-event-store.png)

## Flyway layout

Each service includes two Flyway locations and one schema history:

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/eskit
    out-of-order: true
```

- Service migrations live in `db/migration` and are numbered `V1`, `V2`...
- es-kit's live in `db/eskit` and start at `V1000`, and any later es-kit migration continues that series, so both sequences never collide.
- Because a new service migration is numbered below es-kit's and must still be applied, services set `out-of-order: true`.
- A service migration that writes es-kit tables is numbered `V1001` and up, so it runs after `V1000` on a fresh database. The catalog's [`V1001__publish_seed_products.sql`](../../services/catalog-service/src/main/resources/db/migration/V1001__publish_seed_products.sql) records a `ProductPublished` for every seed product, and the relay sends them to Kafka like any other event, so orders, inventory and reporting build their own copies of the seed catalog.
- Applied migrations are never edited: Flyway validates their checksums.

Native images only contain the resources registered at build time; [`EsKitRuntimeHints`](../../platform/es-kit/src/main/java/com/borjaglez/shop/eskit/EsKitRuntimeHints.java) registers `db/eskit/**` next to Spring Boot's own `db/migration`.

## Related

- [Architecture](architecture.md)
- [The libraries in practice](libraries.md)
- [Checkout saga](checkout-saga.md)
- [Read models](read-models.md)
- [Observability](observability.md)
- [Resilience and scaling](resilience-and-scaling.md)
