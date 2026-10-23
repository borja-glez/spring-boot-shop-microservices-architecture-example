-- Hands the publications of the seed products (V1001) to the outbox of spring-boot-cqrs
-- (cqrs_outbox, V1000_2), whose relay sends them to Kafka like any other integration event: one
-- outbox row per pending publication, under the same event id, carrying the JSON stored in
-- event_store, which is already the ProductPublished the message serializer reads. Each
-- event_store row is then marked as published, as DomainEventStore does with the events it hands
-- over, so every seed product is announced exactly once. Publications already sent are left alone.
insert into cqrs_outbox (event_id, event_name, event_class, payload, context, created_at)
select e.event_id::text,
       e.event_type,
       'com.borjaglez.shop.contracts.catalog.ProductPublished',
       convert_to(e.payload::text, 'UTF8'),
       null,
       now()
from event_store e
where e.stream_type = 'product'
  and e.event_type = 'shop.catalog.1.event.product.product-published'
  and e.metadata ->> 'correlationId' = 'catalog-seed'
  and e.published_at is null
order by e.global_position;

update event_store
set published_at = now()
where stream_type = 'product'
  and event_type = 'shop.catalog.1.event.product.product-published'
  and metadata ->> 'correlationId' = 'catalog-seed'
  and published_at is null;
