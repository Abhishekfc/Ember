-- Streak itself is never stored (StreakCalculator always recomputes it live from photo exchange
-- history) — this table exists purely so a scheduled job can tell WHEN a streak transitions from
-- active to broken, which requires comparing against a previously-known value, and so a broken
-- streak can be "restored" for a limited window afterward.
create table friendship_streak_states (
    friendship_id uuid primary key references friendships(id) on delete cascade,
    last_known_streak integer not null default 0,
    broken_at timestamptz,
    -- Restoring is only offered for this long after a break — enforced server-side, not just
    -- hidden client-side, since restoring is an Ember Gold-gated action.
    restore_deadline timestamptz,
    -- The single calendar day StreakCalculator should treat as a mutual exchange day even though
    -- it wasn't one, once a restore succeeds. Set once per break event and never cleared — the
    -- restoration itself doesn't expire, only the window to request it does.
    restored_through_date date,
    updated_at timestamptz not null default now()
);

-- Backs the scheduled job's own query for "which breaks still have a live restore window",
-- separate from the per-friendship primary-key lookup every other access path uses.
create index idx_friendship_streak_states_restore_deadline on friendship_streak_states (restore_deadline);
