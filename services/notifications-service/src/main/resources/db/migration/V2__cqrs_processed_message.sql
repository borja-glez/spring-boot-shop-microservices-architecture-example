-- The @Idempotent handlers mark each event once per handler (spring-boot-cqrs-jdbc,
-- com/borjaglez/cqrs/jdbc/schema-idempotency.sql).
create table cqrs_processed_message (
    handler_id   varchar(255) not null,
    message_id   varchar(64)  not null,
    processed_at timestamp    not null,
    primary key (handler_id, message_id)
);
create index cqrs_processed_message_at on cqrs_processed_message (processed_at);
