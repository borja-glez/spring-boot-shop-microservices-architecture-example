-- Who placed each order, and what it cost, as learnt from OrderPlaced.
create table order_owner (
    order_id    uuid          primary key,
    customer_id varchar(64)   not null,
    total       numeric(12,2) not null,
    currency    varchar(3)    not null
);

-- One notice per event: the event id is the key, so a redelivered event adds nothing. A notice
-- whose order is not known yet (its OrderPlaced has not arrived) waits with no customer.
create table notification (
    event_id    varchar(64)  primary key,
    order_id    uuid         not null,
    customer_id varchar(64),
    kind        varchar(30)  not null,
    detail      varchar(200),
    amount      numeric(12,2),
    currency    varchar(3),
    title       varchar(120),
    body        varchar(300),
    occurred_at timestamptz  not null,
    read_at     timestamptz
);

create index notification_customer_idx on notification (customer_id, occurred_at desc);
create index notification_pending_idx on notification (order_id) where customer_id is null;
