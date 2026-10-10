-- One row per rewarded ad Google has confirmed a user watched to restore a broken streak.
--
-- A row is written only by Google's own server-to-server callback (AdRewardController), after its
-- signature has been checked, never by the app, so the app cannot claim a reward it didn't earn.
-- Restoring a streak without Emigo Gold then spends one row (consumed_at), which is what stops a
-- single ad from restoring more than one streak.
create table ad_rewards (
    -- Google's own id for this watched ad. Primary key so a retried callback can never create a
    -- second reward for the same ad.
    transaction_id varchar(128) primary key,
    user_id        uuid not null references users (id) on delete cascade,
    friendship_id  uuid not null references friendships (id) on delete cascade,
    created_at     timestamptz not null default now(),
    -- Null until the reward is spent.
    consumed_at    timestamptz
);

-- The lookup made when a restore is requested: this user's unspent rewards for this friendship.
create index idx_ad_rewards_unspent on ad_rewards (user_id, friendship_id) where consumed_at is null;

-- Backs the daily cleanup of old rows.
create index idx_ad_rewards_created_at on ad_rewards (created_at);
