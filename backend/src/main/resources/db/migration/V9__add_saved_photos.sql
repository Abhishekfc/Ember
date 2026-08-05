-- A photo only stays in storage forever once it's explicitly saved (the camera's bookmark
-- button) — everything else is temporary, cleaned up once it's no longer visible in anyone's
-- feed (see the scheduled cleanup job in PhotoCleanupService). null means "not saved".
alter table photos add column saved_at timestamptz;

-- Every photo already sent up to this migration was uploaded under the old "everything is
-- permanent" model — grandfathered in as saved so nothing already in anyone's Memories
-- disappears, and so the (new) cleanup job never considers pre-existing photos for deletion.
update photos set saved_at = created_at where saved_at is null;

-- Backs both the Memories query (saved_at is not null) and the cleanup job (saved_at is null).
create index idx_photos_saved_at on photos (saved_at);
