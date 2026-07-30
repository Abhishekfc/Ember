-- Blocking is its own concept, not another Friendship status — unfriending currently hard-deletes
-- the friendship row entirely (see FriendService.removeFriend), so there's no existing row to
-- "mark blocked" for two people who were never friends in the first place (a stranger found via
-- search, for instance). One row per (blocker, blocked) direction — blocking is not assumed to be
-- mutual just because one side did it.
CREATE TABLE blocked_users (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_blocked_users_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_blocked_users_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_blocked_users_blocker ON blocked_users(blocker_id);
-- Backs the "is there a block between these two, either direction" check used to enforce mutual
-- invisibility in search/friend requests (see BlockedUserRepository.existsBetween).
CREATE INDEX idx_blocked_users_blocked ON blocked_users(blocked_id);

-- Purely a moderation record — reporting never automatically actions the reported account, it's
-- logged for a human to review later, so this has no status/resolution column yet.
CREATE TABLE user_reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_reports_not_self CHECK (reporter_id <> reported_user_id)
);

CREATE INDEX idx_user_reports_reported ON user_reports(reported_user_id);
