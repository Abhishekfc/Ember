-- Backs the Activity tab's nav-dock badge dot. This needs to survive a reinstall (a fresh local
-- install shouldn't make already-seen activity look new again), so it's a real per-user column
-- rather than on-device storage — null means "never viewed the Activity tab", which correctly
-- treats any existing activity as new for a brand new account.
ALTER TABLE users ADD COLUMN activity_last_seen_at TIMESTAMPTZ NULL;
