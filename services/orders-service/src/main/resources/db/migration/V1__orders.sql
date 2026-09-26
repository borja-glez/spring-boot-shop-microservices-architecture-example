-- Local copy of the catalog, fed by catalog events from Kafka. Each group of attributes keeps
-- the time of the event that last set it, so events that arrive out of order cannot undo newer
-- ones (Kafka orders events per message type, not per product).
create table catalog_product (
    product_id         uuid          primary key,
    sku                varchar(40),
    name               varchar(160),
    price              numeric(12,2),
    currency           varchar(3),
    available          boolean       not null default false,
    details_as_of      timestamptz,
    price_as_of        timestamptz,
    availability_as_of timestamptz,
    -- Optimistic locking: two consumers applying events to the same row must not overwrite each
    -- other's changes. The loser fails, Kafka redelivers and it applies on fresh data.
    row_version        bigint        not null default 0
);

-- Read model of orders, built from the order events received from Kafka.
create table order_view (
    order_id         uuid          primary key,
    customer_id      varchar(64),
    status           varchar(20)   not null,
    total            numeric(12,2),
    currency         varchar(3),
    line_count       int           not null default 0,
    cancel_reason    varchar(200),
    -- Why a checkout failed and which payment it used.
    payment_id       uuid,
    rejection_reason varchar(40),
    rejection_detail varchar(500),
    placed_at        timestamptz,
    updated_at       timestamptz   not null,
    row_version      bigint        not null default 0
);

create index order_view_customer_idx on order_view (customer_id, placed_at desc);
create index order_view_status_idx on order_view (status);

create table order_view_line (
    order_id   uuid          not null references order_view (order_id) on delete cascade,
    line_no    int           not null,
    product_id uuid          not null,
    sku        varchar(40)   not null,
    name       varchar(160)  not null,
    quantity   int           not null,
    unit_price numeric(12,2) not null,
    primary key (order_id, line_no)
);

create index order_view_line_product_idx on order_view_line (product_id);

-- Process manager of the checkout saga: one row per order, advanced by the saga runner.
create table checkout_saga (
    order_id         uuid         primary key,
    customer_id      varchar(64)  not null,
    state            varchar(20)  not null,
    mode             varchar(20)  not null,
    step             varchar(30)  not null,
    attempts         int          not null default 0,
    next_attempt_at  timestamptz,
    last_error       varchar(500),
    payment_id       uuid,
    rejection_reason varchar(40),
    rejection_detail varchar(500),
    -- W3C trace context of the request that started (or cancelled) the checkout: every step the
    -- runner takes later continues that trace, so one order is one trace.
    trace_parent     varchar(55),
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    row_version      bigint       not null default 0
);

-- The runner looks for unfinished sagas whose next attempt is due.
create index checkout_saga_due_idx on checkout_saga (next_attempt_at)
    where state in ('RUNNING', 'STUCK');

create table checkout_saga_step (
    order_id uuid         not null references checkout_saga (order_id) on delete cascade,
    seq      int          not null,
    step     varchar(30)  not null,
    outcome  varchar(20)  not null,
    detail   varchar(500),
    at       timestamptz  not null,
    primary key (order_id, seq)
);
