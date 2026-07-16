# Ember Backend

Spring Boot (Kotlin) REST API for Ember. Covers auth, the Postgres schema,
friends, photo upload → Cloudflare R2 → FCM push, and Play Billing subscription verification.

## Tech stack

- Kotlin + Spring Boot 3.5 (Gradle Kotlin DSL, Java 21 toolchain)
- PostgreSQL, schema managed by Flyway (`src/main/resources/db/migration`)
- Spring Security with a stateless JWT filter (no sessions, no `UserDetailsService`)
- Cloudflare R2 via the AWS S3 Java SDK v2 (R2 is S3-compatible)
- Firebase Admin SDK for FCM push
- Google Play Developer API (`androidpublisher`) for subscription receipt verification

## Prerequisites

- Java 21
- A running PostgreSQL instance
- No local Gradle install needed — use the wrapper (`./gradlew`)

## Local setup

1. Create a database (using whatever Postgres login you already have — the default superuser
   role, usually called `postgres`, is fine; you don't need a dedicated role for local dev):

   ```sql
   CREATE DATABASE ember;
   ```

2. Configure the app — either edit `src/main/resources/application.yml` directly, or (recommended,
   so you never commit real credentials) leave the `${VAR:default}` placeholders in place and
   export environment variables instead:

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/ember
   export DB_USERNAME=ember
   export DB_PASSWORD=ember
   ```

3. Run it:

   ```bash
   ./gradlew bootRun
   ```

   On first run, Flyway creates the full schema (`users`, `friendships`, `photos`,
   `photo_recipients`, `subscriptions`, `device_tokens`) automatically. Server listens on `:8080`.

Third-party integrations (R2, FCM, Play Billing) are **optional at boot** — the app starts fine
without them configured, and each fails gracefully at the moment it's actually used instead of
crashing the whole app on startup:

- No R2 config → `POST /photos` returns `503` instead of a stack trace.
- No FCM config → photo upload still succeeds; push is silently skipped (logged as a warning).
- No Play Billing config → `POST /subscription/verify` returns `400` (fails closed — it will never
  silently grant a subscription it couldn't actually verify).

## Configuration reference

All values are environment-variable driven with dev-friendly defaults baked into
`application.yml`. Nothing here needs to change for local dev except the datasource.

| Env var | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres connection | `jdbc:postgresql://localhost:5432/ember` / `postgres` / `postgres` |
| `JWT_SECRET` | HS256 signing key, **must be ≥32 bytes** | dev-only placeholder — override in prod |
| `JWT_ACCESS_TTL_MINUTES` | Access token lifetime | `60` |
| `R2_ENDPOINT` | Cloudflare R2 (or any S3-compatible) endpoint URL | empty (disables uploads) |
| `R2_BUCKET` | Bucket name | `ember-photos` |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | R2 API token | empty |
| `R2_PUBLIC_BASE_URL` | Public base URL photos are served from (r2.dev subdomain or custom domain — bucket must allow public read) | empty |
| `FCM_ENABLED` | Toggle push | `true` |
| `FCM_CREDENTIALS_PATH` | Path to Firebase service-account JSON | empty |
| `PLAY_BILLING_ENABLED` | Toggle Play Billing verification | `true` |
| `PLAY_PACKAGE_NAME` | Android app package name | `com.ember.app` |
| `PLAY_SERVICE_ACCOUNT_CREDENTIALS_PATH` | Path to Google service-account JSON with Play Console API access | empty |

## Testing locally without real R2/Firebase/Play credentials

To exercise the full photo upload path (not just the "not configured" 503) without a live
Cloudflare account, point the R2 config at any local S3-compatible server, e.g.
[MinIO](https://min.io):

```bash
./minio server /tmp/minio-data --address ":9500" --console-address ":9501"
# then, with mc configured against it:
mc mb local/ember-photos && mc anonymous set download local/ember-photos

export R2_ENDPOINT=http://localhost:9500
export R2_BUCKET=ember-photos
export R2_ACCESS_KEY_ID=<minio-root-user>
export R2_SECRET_ACCESS_KEY=<minio-root-password>
export R2_PUBLIC_BASE_URL=http://localhost:9500/ember-photos
```

Leave `FCM_ENABLED=false` and `PLAY_BILLING_ENABLED=false` if you don't have real credentials —
push sends and receipt verification are the only things that need them.

## API reference

All endpoints except `/auth/*` require `Authorization: Bearer <token>`. Unauthenticated requests
get `401`; validation/business-rule errors get `400`; missing resources get `404`.

### Auth

```bash
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123","displayName":"Alice"}'
# -> { "token": "...", "userId": "...", "email": "...", "displayName": "..." }

curl -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
```

### Friends

```bash
TOKEN=<alice's token>

# Send a request by email
curl -X POST localhost:8080/friends/request -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -d '{"email":"bob@example.com"}'

# See who's requested to add you
curl localhost:8080/friends/pending -H "Authorization: Bearer $TOKEN"

# Accept (as bob)
curl -X POST localhost:8080/friends/accept -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" -d '{"friendshipId":"<id from request response>"}'

# List friends — includes pin state (both directions) and computed streak
curl localhost:8080/friends -H "Authorization: Bearer $TOKEN"

# Pin/unpin a friend as your default recipient
curl -X POST   localhost:8080/friends/<friendshipId>/pin -H "Authorization: Bearer $TOKEN"
curl -X DELETE localhost:8080/friends/<friendshipId>/pin -H "Authorization: Bearer $TOKEN"
```

### Devices (needed before push notifications can reach a user)

```bash
curl -X POST localhost:8080/devices/register -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -d '{"fcmToken":"<token from the Android FCM SDK>"}'
```

### Photos

```bash
# Upload — recipients must already be accepted friends
curl -X POST localhost:8080/photos -H "Authorization: Bearer $TOKEN" \
  -F "file=@photo.jpg;type=image/jpeg" \
  -F "recipientIds=<bob's user id>" -F "recipientIds=<carol's user id>"
# -> { "photoId", "url", "createdAt", "recipientIds" } and triggers an FCM push to recipients

# Feed — latest photo per friend, with streak
curl localhost:8080/photos/feed -H "Authorization: Bearer $TOKEN"
```

### Subscription

```bash
curl localhost:8080/subscription/status -H "Authorization: Bearer $TOKEN"

curl -X POST localhost:8080/subscription/verify -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"purchaseToken":"<token from Play Billing>","productId":"ember_gold_yearly"}'
```

`productId` is matched against `*year*` (case-insensitive) to infer `YEARLY` vs `MONTHLY` for
storage — use product IDs like `ember_gold_monthly` / `ember_gold_yearly` in the Play Console.

## What's verified end-to-end

Register → login → friend request → accept → pin → device token registration → photo upload
(against a local S3-compatible server) → feed → streak computation → subscription status/verify
fail-closed behavior — all exercised manually via curl against a live Postgres instance during
development. FCM push and Play Billing verification are implemented against the real SDKs but
need real credentials from you to test the actual send/verify calls.

## Notes

- Streaks count consecutive UTC days with at least one exchanged photo (either direction),
  Snapchat-style — today is allowed to still be "pending" without breaking the streak.
- `photo_recipients` is a join table so a single photo can go to multiple recipients (the
  Camera screen's multi-select send).
- The bucket backing `R2_PUBLIC_BASE_URL` needs public read access (r2.dev subdomain or a custom
  domain) — the app stores/returns plain URLs, not presigned ones, to keep v1 simple. Move to
  presigned GET URLs later if photos need to be private.
