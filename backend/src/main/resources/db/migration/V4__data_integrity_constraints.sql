-- uq_friendship_pair (V1) only rejects an exact (requester_id, addressee_id) tuple twice — it
-- does nothing to stop (A,B) and (B,A) coexisting. Two users adding each other at the same
-- moment can both pass the app-level "does a friendship already exist" check before either
-- commits, creating both rows. FriendshipRepository.findBetween then returns 2 rows forever
-- after for that pair, throwing NonUniqueResultException on every future call — including from
-- PhotoService.upload, permanently breaking photo sending between those two users. This indexes
-- the unordered pair instead, so either insert order is caught by the same constraint.
create unique index uq_friendships_unordered_pair
    on friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

-- A Play Billing purchase token was only ever checked against Google, never against whether it
-- was already claimed by a different account — letting one real purchase be shared/replayed to
-- grant unlimited free subscriptions. NULL is excluded since a user with no purchase yet has no
-- token to collide on.
create unique index uq_subscriptions_purchase_token
    on subscriptions (play_purchase_token)
    where play_purchase_token is not null;

-- Cheap insurance against a future bug/raw-SQL migration silently writing an invalid status
-- string that JPA's enum mapping would only catch at read time.
alter table friendships add constraint chk_friendship_status
    check (status in ('PENDING', 'ACCEPTED', 'DECLINED'));

alter table subscriptions add constraint chk_subscription_status
    check (status in ('NONE', 'ACTIVE', 'EXPIRED', 'CANCELLED'));

alter table subscriptions add constraint chk_subscription_plan
    check (plan is null or plan in ('MONTHLY', 'YEARLY'));

-- FriendService.searchUsers matches lower(username)/lower(display_name) with a leading-wildcard
-- LIKE ('%query%'), which idx_users_username (a plain B-tree, added in V2) can never use — every
-- search keystroke does a sequential scan of the whole users table, getting linearly worse as
-- the user base grows. Trigram indexes support arbitrary substring matches; indexed on the same
-- lower(...) expression the query actually filters on, since a plain-column trigram index can't
-- be used for a query wrapped in lower().
create extension if not exists pg_trgm;

create index idx_users_username_trgm on users using gin (lower(username) gin_trgm_ops);
create index idx_users_display_name_trgm on users using gin (lower(display_name) gin_trgm_ops);
