alter table users add column username varchar(30);

-- Backfill existing rows with a generated handle derived from their display name, deduplicated
-- with a short suffix from their id so the unique constraint below can never collide.
update users
set username = lower(regexp_replace(display_name, '[^a-zA-Z0-9]+', '', 'g')) || substr(md5(id::text), 1, 4)
where username is null;

alter table users alter column username set not null;
alter table users add constraint uq_users_username unique (username);
create index idx_users_username on users (username);
