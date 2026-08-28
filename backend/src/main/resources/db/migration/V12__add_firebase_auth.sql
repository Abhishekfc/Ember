-- Firebase now owns the credential (password or Google account) and confirms the person actually
-- controls the email address they signed up with — something this app never checked before,
-- letting anyone register with an email they don't own since nothing was ever sent to it. This
-- column is how a `users` row is found for an incoming, already-verified Firebase identity.
--
-- Nullable, and password_hash is made nullable alongside it, because existing accounts keep their
-- current password_hash until the one-time import moves them into Firebase (at which point this
-- gets set); a brand new account created after this migration goes the other way — firebase_uid
-- is set from the moment the row is created and password_hash is never populated at all, since
-- this backend no longer handles passwords itself.
alter table users add column firebase_uid varchar(128);
alter table users alter column password_hash drop not null;

-- Partial index — only real values need to be unique, and only real values ever get looked up.
create unique index uq_users_firebase_uid on users (firebase_uid) where firebase_uid is not null;
