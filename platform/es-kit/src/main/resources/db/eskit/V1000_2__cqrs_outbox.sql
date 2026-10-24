-- Outbox of spring-boot-cqrs (com/borjaglez/cqrs/jdbc/schema-outbox-postgresql.sql): the
-- integration events of the domain events stored by DomainEventStore, written in the same
-- transaction and published to Kafka by the library's relay.
create table cqrs_outbox (
    id            bigserial     primary key,
    event_id      varchar(64)   not null unique,
    event_name    varchar(255)  not null,
    event_class   varchar(512)  not null,
    payload       bytea         not null,
    context       bytea,
    created_at    timestamp     not null,
    published_at  timestamp,
    failed_at     timestamp,
    attempts      int           not null default 0,
    read_failures int           not null default 0,
    last_error    varchar(2000)
);

-- The relay's queue: rows waiting to be published that were not set aside.
create index cqrs_outbox_pending on cqrs_outbox (id) where published_at is null and failed_at is null;
-- The cleanup of rows published longer than the retention ago.
create index cqrs_outbox_published on cqrs_outbox (published_at) where published_at is not null;
-- The backlog metrics: pending and set-aside rows, and the age of the oldest.
create index cqrs_outbox_unpublished on cqrs_outbox (created_at) where published_at is null;
