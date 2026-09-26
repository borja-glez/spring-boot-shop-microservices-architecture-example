-- Read model of payments, updated in the same transaction as the payment's events.
create table payment_view (
    order_id     uuid          primary key,
    payment_id   uuid          not null,
    customer_id  varchar(64),
    amount       numeric(12,2) not null,
    currency     varchar(3),
    status       varchar(20)   not null,
    reason       varchar(60),
    created_at   timestamptz   not null,
    updated_at   timestamptz   not null
);

create index payment_view_status_idx on payment_view (status, updated_at desc);
create index payment_view_customer_idx on payment_view (customer_id, updated_at desc);
