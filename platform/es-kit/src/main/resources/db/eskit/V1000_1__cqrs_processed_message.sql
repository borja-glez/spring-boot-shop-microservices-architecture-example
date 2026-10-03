-- Idempotent consumers: the @Idempotent handlers of spring-boot-cqrs mark each event once per
-- handler in its JDBC store (com/borjaglez/cqrs/jdbc/schema-idempotency.sql). The markers of the
-- previous table move over, so no event is applied twice.
create table cqrs_processed_message (
    handler_id   varchar(255) not null,
    message_id   varchar(64)  not null,
    -- Local time of the writer; only the cleanup of old markers reads it.
    processed_at timestamp    not null,
    primary key (handler_id, message_id)
);
create index cqrs_processed_message_at on cqrs_processed_message (processed_at);

insert into cqrs_processed_message (handler_id, message_id, processed_at)
select consumer, message_id, processed_at::timestamp
from processed_message
where length(message_id) <= 64;

drop table processed_message;
