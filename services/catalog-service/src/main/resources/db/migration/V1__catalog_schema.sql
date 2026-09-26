-- Accent-insensitive search (specification-repository renders ignoreCase as unaccent(upper(x))).
-- unaccent is a trusted extension since PostgreSQL 13, so the database owner can create it.
create extension if not exists unaccent;

create table seller (
    id           varchar(64)  primary key,
    display_name varchar(120) not null,
    email        varchar(160) not null,
    city         varchar(80)  not null
);

create table category (
    id   uuid         primary key,
    slug varchar(80)  not null unique,
    name varchar(120) not null
);

create table product (
    id             uuid          primary key,
    sku            varchar(40)   not null unique,
    slug           varchar(220)  not null unique,
    name           varchar(160)  not null,
    description    varchar(4000) not null,
    price_amount   numeric(12,2) not null check (price_amount > 0),
    price_currency varchar(3)    not null,
    status         varchar(20)   not null check (status in ('DRAFT', 'ACTIVE', 'DISCONTINUED')),
    seller_id      varchar(64)   not null references seller (id),
    created_at     timestamptz   not null,
    published_at   timestamptz,
    updated_at     timestamptz   not null,
    version        bigint        not null default 0
);

create index product_status_idx       on product (status);
create index product_seller_idx       on product (seller_id);
create index product_published_at_idx on product (published_at);
create index product_price_idx        on product (price_amount);

create table product_category (
    product_id  uuid not null references product (id) on delete cascade,
    category_id uuid not null references category (id),
    primary key (product_id, category_id)
);

create index product_category_category_idx on product_category (category_id);

create table product_tag (
    product_id uuid        not null references product (id) on delete cascade,
    tag        varchar(30) not null,
    primary key (product_id, tag)
);

create index product_tag_tag_idx on product_tag (tag);
