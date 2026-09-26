-- Stock per catalog product. Items appear when the catalog publishes a product.
create table stock_item (
    product_id  uuid         primary key,
    sku         varchar(40)  not null,
    name        varchar(160) not null,
    on_hand     int          not null check (on_hand >= 0),
    reserved    int          not null default 0 check (reserved >= 0 and reserved <= on_hand),
    updated_at  timestamptz  not null,
    row_version bigint       not null default 0
);

create index stock_item_sku_idx on stock_item (sku);

-- Stock held for an order by the checkout saga. A released reservation stays as a tombstone, so
-- a late duplicate of the reserve command cannot hold the stock again.
create table reservation (
    order_id    uuid         primary key,
    status      varchar(20)  not null,
    reserved_at timestamptz,
    released_at timestamptz,
    row_version bigint       not null default 0
);

create index reservation_status_idx on reservation (status, reserved_at desc);

create table reservation_line (
    order_id   uuid        not null references reservation (order_id) on delete cascade,
    line_no    int         not null,
    product_id uuid        not null,
    sku        varchar(40) not null,
    quantity   int         not null check (quantity > 0),
    primary key (order_id, line_no)
);

create index reservation_line_product_idx on reservation_line (product_id);
