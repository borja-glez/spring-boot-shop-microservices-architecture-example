-- One row per order, rebuilt from the order events. The status only moves forward, whatever
-- order the events arrive in. placed_day is stored because reports group by it and the query
-- DSL groups by columns, not by expressions.
create table report_order (
    order_id         uuid          primary key,
    customer_id      varchar(64),
    status           varchar(20)   not null,
    total            numeric(12,2),
    currency         varchar(3),
    line_count       int           not null default 0,
    placed_at        timestamptz,
    placed_day       date,
    decided_at       timestamptz,
    rejection_reason varchar(40),
    row_version      bigint        not null default 0
);

create index report_order_status_day_idx on report_order (status, placed_day);

create table report_line (
    id         uuid          primary key,
    order_id   uuid          not null references report_order (order_id) on delete cascade,
    product_id uuid          not null,
    sku        varchar(40)   not null,
    name       varchar(160)  not null,
    quantity   int           not null,
    revenue    numeric(12,2) not null
);

create index report_line_order_idx on report_line (order_id);
create index report_line_sku_idx on report_line (sku);
