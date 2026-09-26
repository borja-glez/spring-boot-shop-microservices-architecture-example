-- es-kit: event store that doubles as transactional outbox.
-- Rows with a version belong to event-sourced streams; rows without one are outbox messages of
-- state-based aggregates. published_at stays null until the relay hands the event to the broker.
create table event_store (
    global_position  bigserial     primary key,
    event_id         uuid          not null unique,
    stream_type      varchar(60)   not null,
    stream_id        varchar(80)   not null,
    version          bigint,
    event_type       varchar(200)  not null,
    payload          jsonb         not null,
    metadata         jsonb         not null,
    occurred_at      timestamptz   not null,
    published_at     timestamptz,
    publish_attempts int           not null default 0,
    last_error       varchar(1000)
);

-- Optimistic concurrency: two writers cannot append the same version of a stream.
create unique index event_store_stream_version_uq
    on event_store (stream_type, stream_id, version) where version is not null;

create index event_store_stream_idx on event_store (stream_type, stream_id);
create index event_store_pending_idx on event_store (global_position) where published_at is null;
create index event_store_type_idx on event_store (event_type);

-- Idempotent consumers: a message is applied once per consumer.
create table processed_message (
    consumer     varchar(100) not null,
    message_id   varchar(80)  not null,
    processed_at timestamptz  not null,
    primary key (consumer, message_id)
);
