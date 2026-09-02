-- Backs EmailVerificationExpiryService's own sweep query (findByEmailVerificationRequiredTrue
-- AndCreatedAtBefore), which previously had no index at all and did a full scan of the entire
-- users table on every tick. A partial index — only rows still pending verification, which is
-- normally zero or a small handful at any moment, since a row leaves this set within seconds of
-- either verifying or expiring — keeps that scan effectively free no matter how large the users
-- table grows overall, rather than the cost of every tick scaling with total signups forever.
create index idx_users_pending_email_verification
    on users (created_at)
    where email_verification_required = true;
