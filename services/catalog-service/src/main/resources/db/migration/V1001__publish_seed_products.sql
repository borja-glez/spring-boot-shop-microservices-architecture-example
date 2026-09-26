-- Publishes the seed products (V2) as integration events: records a ProductPublished for each
-- product on sale in the outbox (the es-kit event store, V1000), and the relay sends them to Kafka
-- like any other event, so orders, inventory and reporting build their own copies of the catalog.
-- Numbered after V1000 because it writes to event_store. The payload mirrors the JSON the message
-- serializer writes for ProductPublished, and its eventId matches the event_id column.
insert into event_store (event_id, stream_type, stream_id, version, event_type, payload, metadata, occurred_at)
select s.event_id,
       'product',
       s.id::text,
       null,
       'shop.catalog.1.event.product.product-published',
       jsonb_build_object(
           'eventId',    s.event_id::text,
           'occurredOn', to_char(s.published_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
           'productId',  s.id::text,
           'sku',        s.sku,
           'name',       s.name,
           'price',      s.price_amount,
           'currency',   s.price_currency,
           'sellerId',   s.seller_id),
       '{"correlationId":"catalog-seed"}'::jsonb,
       s.published_at
from (select gen_random_uuid() as event_id, p.*
      from product p
      where p.status = 'ACTIVE'
      order by p.published_at) s;
