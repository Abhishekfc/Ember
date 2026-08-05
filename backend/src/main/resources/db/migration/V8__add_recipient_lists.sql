-- Personal, named recipient shortcuts saved from the camera's recipient picker (the "+" badge) —
-- e.g. "Family". Always read and written as one whole unit (create a list, delete a list), never
-- a partial-member operation, so friend_ids is a single JSON-array column rather than a separate
-- join table. Synced through the backend (not just on-device storage) specifically so a list
-- created on one phone shows up after logging into the same account on another.
create table recipient_lists (
    id          uuid primary key default gen_random_uuid(),
    owner_id    uuid not null references users (id) on delete cascade,
    name        varchar(60) not null,
    friend_ids  text not null, -- JSON array of friend user IDs, e.g. ["...", "..."]
    created_at  timestamptz not null default now()
);

create index idx_recipient_lists_owner on recipient_lists (owner_id);
