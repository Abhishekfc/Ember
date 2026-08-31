-- Whether this specific account must have a verified email before it can use anything beyond
-- reading its own profile (enforced in FirebaseAuthenticationFilter). Only new sign-ups are held
-- to this — nobody who already had an account was ever asked to verify anything, so every row
-- that exists at the moment this migration runs is explicitly grandfathered in as exempt. The
-- default of true is what every row created from this point on picks up, matching User.kt's own
-- default for a freshly-inserted row.
alter table users add column email_verification_required boolean not null default true;
update users set email_verification_required = false;
