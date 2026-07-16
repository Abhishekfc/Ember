-- gen_random_uuid() has been a built-in core function since PostgreSQL 13; no extension required.

create table users (
    id                 uuid primary key default gen_random_uuid(),
    email              varchar(255) not null unique,
    password_hash      varchar(255) not null,
    display_name       varchar(100) not null,
    created_at         timestamptz not null default now()
);

create table friendships (
    id                 uuid primary key default gen_random_uuid(),
    requester_id       uuid not null references users (id) on delete cascade,
    addressee_id       uuid not null references users (id) on delete cascade,
    status             varchar(20) not null default 'PENDING', -- PENDING, ACCEPTED, DECLINED
    requester_pinned   boolean not null default false,          -- requester has pinned addressee as default recipient
    addressee_pinned   boolean not null default false,          -- addressee has pinned requester as default recipient
    created_at         timestamptz not null default now(),
    responded_at       timestamptz,
    constraint uq_friendship_pair unique (requester_id, addressee_id),
    constraint chk_friendship_not_self check (requester_id <> addressee_id)
);

create index idx_friendships_requester on friendships (requester_id);
create index idx_friendships_addressee on friendships (addressee_id);

create table photos (
    id                 uuid primary key default gen_random_uuid(),
    sender_id          uuid not null references users (id) on delete cascade,
    storage_key        varchar(512) not null,
    content_type       varchar(100) not null,
    created_at         timestamptz not null default now()
);

create index idx_photos_sender_created on photos (sender_id, created_at desc);

create table photo_recipients (
    id                 uuid primary key default gen_random_uuid(),
    photo_id           uuid not null references photos (id) on delete cascade,
    recipient_id       uuid not null references users (id) on delete cascade,
    delivered_at       timestamptz,
    viewed_at          timestamptz,
    constraint uq_photo_recipient unique (photo_id, recipient_id)
);

create index idx_photo_recipients_recipient_photo on photo_recipients (recipient_id, photo_id);

create table subscriptions (
    id                       uuid primary key default gen_random_uuid(),
    user_id                  uuid not null unique references users (id) on delete cascade,
    status                   varchar(20) not null default 'NONE', -- NONE, ACTIVE, EXPIRED, CANCELLED
    plan                     varchar(20),                          -- MONTHLY, YEARLY
    play_purchase_token      varchar(512),
    play_product_id          varchar(255),
    expires_at               timestamptz,
    updated_at               timestamptz not null default now()
);

create table device_tokens (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null references users (id) on delete cascade,
    fcm_token          varchar(512) not null unique,
    created_at         timestamptz not null default now()
);

create index idx_device_tokens_user on device_tokens (user_id);
