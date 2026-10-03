# Read models

Most of what the shop displays is not read from the service that owns the data but from a read model built from its events: the order list comes from `order_view`, orders are priced from a local copy of the catalog, the inventory learns about products from `ProductPublished`, the reports are grouped queries over reporting's own tables, and the notices are rows written by a Spring Boot 3.5 service from events written by Spring Boot 4 services. Every projection applies each event once, converges whatever the order in which events of different types arrive, and can lag the write side by the time the outbox and Kafka take, which the platform measures. This page describes each read model, what feeds it and what it guarantees.

## Overview

| Read model | Service | Fed by | Consumer | Served at |
|---|---|---|---|---|
| `order_view`, `order_view_line` | orders | `OrderPlaced`, `OrderConfirmed`, `OrderRejected`, `OrderCancelled` | `orders.order-view` | `GET /api/orders`, `GET /api/orders/{id}` |
| `catalog_product` | orders | `ProductPublished`, `ProductPriceChanged`, `ProductDiscontinued` | `orders.catalog-products` | used to price new orders |
| `stock_item` (creation) | inventory | `ProductPublished` | `inventory.catalog-products` | `GET /api/inventory/stock` |
| `report_order`, `report_line` | reporting | order events | `reporting.orders` | `GET /api/reporting/*` |
| `order_owner`, `notification` | notifications | order events, `PaymentRefunded` | keyed by event id | `GET /api/notifications`, SSE stream |
| `payment_view` | payments | written by the command handler in the same transaction as the stream | n/a | `GET /api/payments` |

## Orders: `order_view` and the catalog copy

[`OrderViewProjector`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/projection/OrderViewProjector.java) consumes the orders service's own events from Kafka and maintains `order_view`, one row per order with its lines, status, payment and rejection details. "My orders" and the order detail read it with specification-repository and HTTP filters; "My orders" always adds `customerId = <current user>` as a server condition of the client plan (`views.query(plan).where("customerId", ...)`), which the client can neither filter by nor widen with an `orFilter`. A new order appears in the list a moment after it is placed; the order page therefore shows the saga (read directly from `checkout_saga`) and the event store history, which are current, next to the read model.

[`CatalogProductProjector`](../../services/orders-service/src/main/java/com/borjaglez/shop/orders/application/projection/CatalogProductProjector.java) keeps `catalog_product`: SKU, name, price, currency and availability, enough to price an order without asking the catalog. `PlaceOrderCommand` prices every line from this table and rejects products that are not orderable (422 `product-unavailable`); the prices are then frozen in `OrderPlaced`.

Both projections tolerate out-of-order delivery across event types. `catalog_product` keeps an as-of timestamp per attribute group and ignores older events; `order_view` is created by whichever event arrives first and its status only moves forward. See [Event sourcing and outbox](event-sourcing-and-outbox.md#ordering).

## Inventory: stock from product events

[`CatalogProductsProjector`](../../services/inventory-service/src/main/java/com/borjaglez/shop/inventory/application/projection/CatalogProductsProjector.java) starts keeping stock for every product the catalog publishes, with `shop.inventory.initial-stock` units (25 by default, `INVENTORY_INITIAL_STOCK`). Republishing a product leaves its stock untouched. From then on the stock is inventory's own state: reservations and releases from the saga, and manual counts from the Stock page (`PUT /api/inventory/stock/{productId}`, recorded as `StockAdjusted` with the user who counted). Until the `ProductPublished` of a new product arrives, `ReserveStock` fails and the saga retries, rather than rejecting the order as out of stock.

## Reporting service

`reporting-service` projects the order events into its own two tables with [`ReportProjector`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/projection/ReportProjector.java), idempotently (`@Idempotent(name = "reporting.orders")`) and with a status that only moves forward:

- `report_order`: one row per order with customer, status, total, currency, line count, rejection reason, `placed_at` and `placed_day`. The day is stored as a column because reports group by it and the query DSL groups by columns, not expressions.
- `report_line`: one row per order line with SKU, name, quantity and revenue, linked to its order.

### Reports

All reports are grouped queries of specification-repository ([`ReportsHandler`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/query/ReportsHandler.java)). Amounts are only added up within one currency, so every money report also groups by currency.

| Endpoint | Rows | Groups by | Computes |
|---|---|---|---|
| `GET /api/reporting/summary` | placed orders | `status` | orders (`COUNT`) |
| `GET /api/reporting/sales-by-day` | confirmed orders | `placedDay`, `currency` | orders, distinct customers (`COUNT_DISTINCT`), revenue (`SUM`) |
| `GET /api/reporting/top-products?minUnits=N` | lines of confirmed orders | `sku`, `name`, `order.currency` | units (`SUM`), orders (`COUNT_DISTINCT`), revenue; `HAVING SUM(quantity) >= minUnits` |
| `GET /api/reporting/rejections` | rejected orders | `rejectionReason` | orders |
| `GET /api/reporting/customers` | confirmed orders | `customerId`, `currency` | orders, amount spent |

**The client filters, the server groups.** The client may narrow the rows with the usual HTTP filters, on `placedAt`, `placedDay` and `currency` for order reports and on `order.placedAt`, `order.placedDay`, `sku` and `name` for the product report. What is grouped, counted and summed is decided by the server and is never a parameter. Each report derives the client plan with `repository.query(plan)`: its filters keep their whitelist, `where` adds server conditions such as `status = CONFIRMED`, and `groupBy`, `select`, the aggregates and `having` add the server's grouping before `findRows()` reads the rows. The `having` of the product report is on `SUM(quantity)`, a field the client may not filter by: like the server conditions it is server input, so the whitelist of the plan stays restrictive. The four order reports share their whitelist through the composed annotation `@OrderReportFilter`.

![Reports page](../assets/screenshots/shop-reports.png)

### Rebuilding from Kafka

`POST /api/reporting/rebuild` throws the projections away and builds them again from the first event still in Kafka; `GET /api/reporting/rebuild` returns the status of the last rebuild. Only one rebuild runs at a time (another request gets 409 `rebuild-in-progress`). [`RebuildService`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/rebuild/RebuildService.java) works through the [`EventReplay`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/application/rebuild/EventReplay.java) port, implemented by [`KafkaEventReplay`](../../services/reporting-service/src/main/java/com/borjaglez/shop/reporting/infrastructure/KafkaEventReplay.java) on the listener container spring-boot-cqrs creates for the events topic (`cqrsKafkaEventListenerContainer`):

1. **Pause.** Stop the container, so no event is applied to half-cleared tables.
2. **Rewind.** With the Kafka `AdminClient`, move the consumer group to the earliest offset of every partition of the topic. The broker refuses while the group still has members, which leave a moment after the stop, so the call is retried.
3. **Clear.** In one transaction, delete `report_line`, `report_order` and the idempotency markers (`JdbcIdempotencyStore.deleteProcessedBefore(now)` on `cqrs_processed_message`). Without clearing the markers the replayed events would be skipped as duplicates.
4. **Resume.** Start the container again, whatever happened in steps 2 and 3.

The order is deliberate. If the rewind fails, the tables are untouched and consumption continues where it was. If the clear fails after the rewind, the markers are still there and the replayed events are skipped as duplicates, so nothing is counted twice either. Events published during the rebuild wait in Kafka and are read afterwards. [`RebuildIT`](../../services/reporting-service/src/test/java/com/borjaglez/shop/reporting/application/RebuildIT.java) checks against a real Kafka that a rebuild gives the same numbers and that an order published right after the rewind is not lost. The Reports page has a button that triggers it.

## Notifications service

`notifications-service` turns order and payment events into notices for the customer and pushes them to the browser as they are stored. It runs on **Spring Boot 3.5 with Jackson 2**, while the events it reads are written by Spring Boot 4 services with Jackson 3: it is the proof that the contracts and both libraries interoperate across framework generations. [`CrossGenerationIT`](../../services/notifications-service/src/test/java/com/borjaglez/shop/notifications/application/CrossGenerationIT.java) publishes payloads exactly as the Boot 4 services write them (taken from a real event store) and checks this service reads them.

| Event | Notice |
|---|---|
| `OrderPlaced` | none: stores who owns the order and its total (`order_owner`) |
| `OrderConfirmed` | `ORDER_CONFIRMED` |
| `OrderRejected` | `ORDER_REJECTED`, with the reason |
| `OrderCancelled` | `ORDER_CANCELLED` |
| `PaymentRefunded` | `PAYMENT_REFUNDED`, with the amount |

- **One notice per event.** [`NotificationProjector`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/NotificationProjector.java) is `@Idempotent` (`notifications.notices`, with the JDBC store of `spring-boot-cqrs-jdbc`), and the primary key of `notification` is the event id: a redelivered event adds nothing.
- **Notices that arrive before their order.** Kafka orders events per type, so an `OrderConfirmed` can be processed before its `OrderPlaced`. The notice is stored without a customer and addressed when `OrderPlaced` arrives ([`NotificationProjector`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/NotificationProjector.java)). When both are processed at the same moment on different partitions, neither transaction sees the other's insert; [`PendingNotificationsSweeper`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/application/PendingNotificationsSweeper.java) closes that gap every `shop.notifications.sweep-interval` (10 s), looking only at notices from the last day, 500 at a time.
- **Live over SSE.** `GET /api/notifications/stream?user=<id>` keeps a server-sent events connection open. [`SseNotificationHub`](../../services/notifications-service/src/main/java/com/borjaglez/shop/notifications/api/SseNotificationHub.java) pushes each notice after its transaction commits, from a virtual thread so that a slow browser never blocks the Kafka consumer, sends a heartbeat comment every 20 seconds so proxies do not close idle connections, and ends each connection after 30 minutes (the browser reconnects). `shop.notifications.sse.connections` reports the open connections.
- **REST side.** `GET /api/notifications` (optionally `unread=true`, paged, size capped at 100), `GET /api/notifications/unread-count` and `POST /api/notifications/{id}/read`, all through its own command and query buses.
- **Self-contained.** It does not use `service-support`, `es-kit` or `test-support`, which are built against Boot 4. It has its own RFC 9457 handler with the same `code` property, its own Flyway migration for `cqrs_processed_message`, observability configuration and Testcontainers 1.x setup.

## Consistency window

The time between a business transaction and its effect on a read model is measured end to end: `shop.outbox.delay` (transaction to broker acknowledgement) and `shop.consumer.lag` per consumer (event time to projection). Kafka's own `kafka.consumer.fetch.manager.records.lag.max` shows how many records a consumer is behind. The "Read models are falling behind" alert fires when a consumer stays more than 1000 records behind for five minutes. See [Observability](observability.md#metrics).

## Limits of the demo

- **No access control.** The example has no authentication, so anyone can open another user's stream (`?user=`), read every customer's spending in the reports or start a rebuild. In a real system the stream would be bound to the session and reports and rebuilds would be operator-only; see [Architecture](architecture.md#the-demo-user).
- **Kafka retention.** A rebuild can only replay what Kafka still keeps. Compose and Kubernetes run the broker with `KAFKA_LOG_RETENTION_MS=-1` (keep forever). With a finite retention, older orders would drop out of rebuilt reports; a production setup would rebuild from a durable archive or a compacted snapshot.
- **Single replica.** SSE connections live in the instance that opened them, and a rebuild assumes nobody else consumes with reporting's group. Both services therefore run one replica; see [Resilience and scaling](resilience-and-scaling.md#what-stays-single-replica).

## Related

- [Event sourcing and outbox](event-sourcing-and-outbox.md)
- [The libraries in practice](libraries.md)
- [Querying](querying.md)
- [Checkout saga](checkout-saga.md)
- [Observability](observability.md)
- [Testing](testing.md)
