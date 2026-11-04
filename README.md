# Stroll splash backend

This file explains my thinking, my assumptions and my choices. The code is the proof.

## Contents

1. [What this is](#what-this-is)
2. [How I read the brief](#how-i-read-the-brief)
3. [The design in one page](#the-design-in-one-page)
4. [Speed](#speed)
5. [Decisions, and what I left out](#decisions-and-what-i-left-out)
6. [Why I store a checksum](#why-i-store-a-checksum)
7. [Why one static admin key](#why-one-static-admin-key)
8. [Project structure](#project-structure)
9. [Data model](#data-model)
10. [API](#api)
11. [Running it](#running-it)
12. [How it was checked](#how-it-was-checked)
13. [Future work](#future-work)
14. [Limits I accept](#limits-i-accept)
15. [Questions for the Stroll team](#questions-for-the-stroll-team)

## What this is

A backend that tells the iOS app which splash videos it may play. The list can change without an app release. An event video, such as Christmas, replaces the normal rotation for a time window. It is Java 21, Spring Boot, PostgreSQL and Cloudflare R2 (S3-compatible file storage). It is backend only. I did not build a Swift client, because the brief says the backend alone is complete.

## How I read the brief

- The splash plays at app launch, possibly before the network is ready. So the app must never wait on this call.
- A CDN (content delivery network, a service that keeps copies of files close to users) fronts the API and the video storage. For production I would use a custom domain, not the `r2.dev` address (the free test address R2 gives a bucket).
- "Random rotation" means the app picks one video at random from a pool on each launch. A set order would need an ordering field.
- An event replaces the normal rotation for its window. I took "a new splash video for that duration" literally.
- "About 6" videos is data, not code. The pool can be any size.
- The brief only says the set must change without an app release. The admin API is my assumption about how.
- There is one backend instance, and event windows are in UTC.

## The design in one page

1. The app calls `GET /api/v1/splashes`.
2. The backend works out which tier applies (a live event, or the normal rotation), applies pins (an admin can pin a video to say "play this one"), and returns the pool.
3. The app picks one video at random and plays it.

![Architecture: the iOS app asks the backend for the pool and downloads videos from R2, while an admin writes through a key check to PostgreSQL and R2](images/architecture.png)

Left: the app reads. Right: an admin writes. The backend never serves video files. The app downloads them from R2.

The backend returns a pool, not a single pick. A pool is the same for every user, so the app and a CDN can cache it. The server needs no randomness and no idea who is asking.

The rules for the pool:

- Only videos with `active = true` count.
- If an active event is live now, only live events are returned. They replace the standard videos.
- An event is live from its start time up to, but not including, its end time. An event without both times is never eligible.
- If any video in the chosen tier is pinned, only the pinned ones are returned. A pinned standard video never beats a live event.
- An empty pool is valid. The app then plays the video bundled in the app.

With the seed data, the six standard videos come back. Give Christmas a window around now, and only Christmas comes back.

**What the app is expected to do.** I did not build or test the app side. I expect it to use a short timeout, play from its local cache, fall back to the bundled default, pick at random, and check a downloaded file's checksum before keeping it.

## Speed

The splash is the first thing a user sees, so speed was my baseline.

**Cache in memory.** The active rows are cached with Caffeine, a Java cache library. On a hit, a request runs no database query. An entry lasts 5 minutes (`splash.cache.ttl`; TTL means time to live). Every PATCH clears the cache at once. An upload does not, because a new video is inactive and the cache holds only active ones.

**Events stay exact.** The tier is worked out on each request, so an event starts and ends at its exact second even with a warm cache. I watched that on a live run.

**`Cache-Control`** lets the app or a CDN reuse its copy without asking me:

```
Cache-Control: max-age=300, public
```

For 300 seconds the app does not call me at all. The value is `splash.http.max-age`.

**The ETag** is a fingerprint of the response. After the 300 seconds, the app sends it back in an `If-None-Match` header and asks "has this changed?" If not, I reply `304 Not Modified` with no body. Mine is a SHA-256 hash (a fixed-size fingerprint of some data) of every item the app sees, URL included. So if the CDN domain changes, the ETag changes too, and nobody holds a dead URL.

The saving is modest, because the payload is small: 1,482 bytes for the six seed videos, and 164 bytes for a `304` with headers. It matters most behind a CDN.

**The cost.** A kill switch (turning a video off) or an event boundary can take up to 300 seconds to reach apps that hold a fresh copy.

**Numbers.** My targets were a few milliseconds for a cache hit and tens of milliseconds for a miss. On my laptop, against local Postgres with no network, a hit had a median of 0.49 ms (95th percentile 1.15 ms) over 500 requests. A miss had a median of 1.82 ms (95th percentile 3.44 ms) over 60. A real network adds to both. I have not measured behind a CDN or under load.

**Why no Redis.** It would add a network hop to save a query that costs almost nothing.

## Decisions, and what I left out

| Decision | What I chose | What I dropped | Why |
|---|---|---|---|
| Pool or pick | Return a pool | Pick on the server | A pool is the same for everyone, so it caches. |
| Cache | In-process Caffeine | Redis | A network hop to save a cheap query. |
| Upload path | Through the backend | Presigned URLs (links that let the app upload straight to storage) | A splash is a few MB, and the server hashes what it receives. |
| Hiding a video | An `active` flag | A DELETE endpoint | Reversible, with no orphaned files. |
| Admin access | One static key | Per-admin login, audit trail | Small internal tool. See below. |
| Schema | `ddl-auto=update` and a seeder (it loads the eight starting rows) | Flyway migrations | One table. Production would use migrations. |
| Event window checks | Guards in code | A database CHECK | Hibernate applies a CHECK reliably only when it creates the table. |
| Events | They replace the rotation | They join it | I read "for that duration" literally. |
| Overlapping events | Both are returned | A priority column | The simplest rule. An admin can switch one off. |
| Stored location | The object key | The full URL | A new bucket or CDN domain is one setting and moves no data. |
| Content hash | My own SHA-256 | R2's ETag | See below. |
| ETag type | Strong, over the response | Weak, or a version counter | It changes whenever the response does. No state to keep. |
| PATCH | `null` or "left out" means no change. Unknown fields and an empty body are rejected. | Silent acceptance | A typo like `pined` would otherwise change nothing without a word. |
| After upload | `active = false` | Switching on automatically | An upload cannot reach the app by accident. |
| Time | UTC instants | Local time zones | A window has one exact meaning. |
| Admin key check | Before the upload body is read | After parsing it | A wrong key with a big file is refused cheaply. |
| Upload abuse | One app-wide throttle, 10 uploads a minute, counted after the key check | Per-IP limits, a rate-limit library | One shared key and one instance, so one counter is enough. |

**Deliberately not built:** impression logging (see future work), regional or per-user targeting (it splits the cache), timezone-local events, per-admin identity, DELETE, replacing a file (upload a new video and switch the old one off), "never the same splash twice in a row" (it needs identity), carousel order or weights, video duration metadata, rate limiting beyond the upload throttle (a gateway or CDN job), cache sharing across instances, and the Swift client.

## Why I store a checksum

This is deliberate. At upload, the server hashes the file once with SHA-256 and stores the result next to the size. The app is expected to hash what it downloaded and keep the file only if the two match. So a dropped or cut-off download never becomes a broken splash. I do not use R2's ETag for this, because what it means depends on how a file was uploaded. For my eight files it equals the file's MD5 (an older kind of hash), which I checked. I want one hash that I control and that means the same thing for every file.

## Why one static admin key

This is deliberate too. The admin endpoints are guarded by a static key in the `X-Admin-Key` header, read from `ADMIN_API_KEY`. I left out per-admin login and an audit trail. The brief does not stress security, and this is a small internal tool. The cost is plain: no record of who changed what, and one shared key to rotate.

Inside that choice:

- The key is compared in constant time, so timing does not leak how much of a guess matched.
- The app refuses to start if the key is blank.
- The key is never logged. I searched my test logs for it and found no match.
- The key is checked before an upload body is read. A wrong key with an 11 MB file gets a `401` in milliseconds, not a `413`. I ran that live.
- Uploads are throttled to 10 a minute across the whole app. The count starts only after the key check, so a stranger cannot use it up. Over the limit, the answer is `429` and the body is not read. I ran that live: 40 uploads at once gave 10 through and 30 refused.

## Project structure

```
strollsplash/
  pom.xml, mvnw               Maven build and wrapper
  docker-compose.yml          Postgres 16 for local runs
  .env.example                every environment variable, with dummy values
  images/architecture.png     the picture in this README
  video-assets/
    splashes/                 the eight demo videos and manifest.json (object key, SHA-256, size)
    extra/new-year-event.mp4  a ninth video for trying the upload
  src/main/java/dev/slethware/strollsplash/
    controller/               the public and the admin controller
    service/splash/           resolver, cache, validator, seeder, the two services
    service/storage/          the S3/R2 upload and URL building
    repository/ entity/ dto/ enums/ exception/ interceptor/ config/ utility/
  src/main/resources/         application.properties, seed/splashes.json (the eight starting rows)
  src/test/java/...           the tests, none of which need a database
```

A public read goes controller, service, cache, resolver, and comes back as a pool. An admin write goes through the key check, then the controller, then the admin service, which validates, uploads the file if there is one, saves the row, and (for a PATCH) clears the cache.

## Data model

One table, `splash_video`. Hibernate creates it.

| Column | Type | Meaning |
|---|---|---|
| `id` | uuid | Primary key, generated. |
| `title` | varchar | An admin label, at most 120 characters (checked in code). |
| `kind` | varchar | `STANDARD` or `EVENT`. |
| `object_key` | varchar | The file's place in the bucket, such as `splashes/seed-01-original.mp4`. |
| `checksum` | varchar(64) | SHA-256 of the file, lowercase hex. |
| `size_bytes` | bigint | File size in bytes. |
| `active` | boolean | Off hides the video (the kill switch). |
| `pinned` | boolean | Narrows the pool inside the tier. |
| `starts_at`, `ends_at` | timestamptz | The event window. Empty for a standard video. |
| `created_at`, `updated_at` | timestamptz | Set automatically. |

The public URL is `storage.public-url` plus `object_key`, so pointing at another bucket or CDN domain is one setting.

## API

| Method and path | Auth | Purpose | Success |
|---|---|---|---|
| `GET /api/v1/splashes` | none | The pool the app may play | 200, or 304 when unchanged |
| `GET /api/v1/admin/splashes` | admin key | Every video, newest first, in pages (`page` from 0, `size` 1 to 100, default 20). The total is in the `X-Total-Count` header. | 200 |
| `POST /api/v1/admin/splashes` | admin key | Upload an mp4 (a multipart request: a file plus text fields). At most 10 a minute across the app. | 201 |
| `PATCH /api/v1/admin/splashes/{id}` | admin key | Partial update of one video | 200 |

Swagger UI documents every endpoint at `http://localhost:8080/docs`. Its Authorize button takes the admin key and sends it as `X-Admin-Key`. I checked that in a browser.

The examples assume the app is running and `.env` is loaded (see [Running it](#running-it)). I ran each one. I use `jq` only to pick values out of JSON.

**The public pool, then the same call with `If-None-Match`:**

```bash
curl -i http://localhost:8080/api/v1/splashes
ETAG=$(curl -s -D - -o /dev/null http://localhost:8080/api/v1/splashes | grep -i '^etag:' | cut -d' ' -f2 | tr -d '\r')
curl -i -H "If-None-Match: $ETAG" http://localhost:8080/api/v1/splashes
```

The first returns `200` with the `Cache-Control` and `ETag` headers. The second returns `304` with no body.

**List the first three videos:**

```bash
curl -s -i -H "X-Admin-Key: $ADMIN_API_KEY" "http://localhost:8080/api/v1/admin/splashes?page=0&size=3"
```

**Pin a video.** The pool then holds only that video. Send `{"pinned": false}` to undo it.

```bash
ID=$(curl -s -H "X-Admin-Key: $ADMIN_API_KEY" "http://localhost:8080/api/v1/admin/splashes?size=100" | jq -r '.data[] | select(.title == "Original") | .id')
curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d '{"pinned": true}' http://localhost:8080/api/v1/admin/splashes/$ID
```

**Give an event a window.** This makes Christmas live from one hour ago to one hour from now.

```bash
XMAS=$(curl -s -H "X-Admin-Key: $ADMIN_API_KEY" "http://localhost:8080/api/v1/admin/splashes?size=100" | jq -r '.data[] | select(.title == "Christmas 2026") | .id')
FROM=$(python3 -c "import datetime as d; print((d.datetime.now(d.timezone.utc) - d.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
TO=$(python3 -c "import datetime as d; print((d.datetime.now(d.timezone.utc) + d.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d "{\"startsAt\": \"$FROM\", \"endsAt\": \"$TO\"}" http://localhost:8080/api/v1/admin/splashes/$XMAS
```

**Upload a video.** The new row is inactive until you switch it on with a PATCH. Do not set the `Content-Type` header yourself. curl adds it, with the boundary the server needs.

```bash
curl -s -X POST -H "X-Admin-Key: $ADMIN_API_KEY" -F "title=New Year 2027" -F "kind=EVENT" -F "startsAt=2026-12-31T00:00:00Z" -F "endsAt=2027-01-02T00:00:00Z" -F "file=@video-assets/extra/new-year-event.mp4;type=video/mp4" http://localhost:8080/api/v1/admin/splashes
```

This needs storage credentials that can write, and returns `201`. The check looks at the file's label (`video/mp4`) and its first bytes, so a renamed text file is refused.

**Errors** all have the same three fields:

```bash
curl -i -H "X-Admin-Key: wrong" http://localhost:8080/api/v1/admin/splashes
```

```json
{"message":"Invalid or missing admin key","error":"Unauthorized","statusCode":401}
```

- `400`: a validation message (several are joined with "; "), an unknown field such as `Unknown field 'pined'`, an unreadable body, a bad value such as `Invalid value 'BOGUS' for kind. Allowed values: STANDARD, EVENT`, a bad `page` or `size`, or `Only video/mp4 files are accepted`.
- `401`: wrong or missing key. `404`: unknown video or route. `405`: wrong method.
- `413`: `File exceeds the maximum upload size`.
- `429`: `Too many uploads, try again in a minute`.
- `500`: `Failed to upload video to storage`, or `An unexpected error occurred`.

## Running it

You need JDK 21 and Docker. Docker runs only Postgres.

```bash
cp .env.example .env
docker compose up -d
set -a; source .env; set +a
./mvnw spring-boot:run
```

Fill `.env` with your storage values first (the options below). The app does not read `.env` by itself. The variables must be in the environment of the shell that starts it, which is what `set -a; source .env; set +a` does. Then open `http://localhost:8080/docs`.

Postgres reads `DB_USERNAME` and `DB_PASSWORD` only when it first creates its volume. If you change them later, run `docker compose down -v` and start again. Logs use event names like `splash.update.done`. Set `LOG_LEVEL_APP=DEBUG` for more.

The seeded rows point at real video files, so storage needs a little setup. Pick one of three ways.

**Option one: the `.env` I send you.** Put it in the repo root as `.env`, skip the `cp` line, and run the rest. It holds a token scoped to my one bucket, so uploads work. I will rotate the token after the review. Do not commit the file.

**Option two: your own bucket.** Any S3-compatible store works. Upload the eight `.mp4` files in `video-assets/splashes/`, keeping the object keys listed in `manifest.json`, and make the bucket publicly readable. In the `.env` from the `cp` line, set the storage values to yours. For R2 or similar, set `STORAGE_ENDPOINT` to the S3 API address on the bucket page. Leave it empty for AWS S3. Everything then works, uploads included.

**Option three: no storage credentials, read only.** In the `.env` from the `cp` line, set `STORAGE_PUBLIC_URL` to the public address I send you, and put any text, such as `unused`, in the other three storage values. The app starts and the videos play from my bucket. Uploads fail with a `500`. I checked this live.

**All the settings.** The app will not start if a required value is blank:

| Variable | What it is | Example or default | Required |
|---|---|---|---|
| `DB_URL` | JDBC address of Postgres | `jdbc:postgresql://localhost:5432/strollsplash` | yes |
| `DB_USERNAME` | Postgres user | `strollsplash` | yes |
| `DB_PASSWORD` | Postgres password | `change-me` | yes |
| `ADMIN_API_KEY` | The key admins send as `X-Admin-Key` | `change-me` | yes |
| `STORAGE_ENDPOINT` | S3-compatible endpoint. Empty means AWS S3. | empty | no |
| `STORAGE_REGION` | Storage region | `us-east-1` | no |
| `STORAGE_ACCESS_KEY_ID` | Storage access key id | your value | yes |
| `STORAGE_SECRET_ACCESS_KEY` | Storage secret | your value | yes |
| `STORAGE_BUCKET_NAME` | Bucket name | `my-bucket` | yes |
| `STORAGE_PUBLIC_URL` | Public base address of the bucket | `https://pub-example.r2.dev` | yes |
| `SPLASH_UPLOAD_MAX_SIZE` | Largest allowed upload | `10MB` | no |
| `SPLASH_UPLOAD_MAX_PER_MINUTE` | Uploads allowed per minute, whole app (1 or more) | `10` | no |
| `PORT` | HTTP port | `8080` | no |
| `LOG_LEVEL_APP` | Log level for this app | `INFO` | no |

**First run.** The seeder creates eight rows: six standard videos, Halloween 2026 (30 October to 1 November) and Christmas 2026 (24 to 27 December). When I wrote this, on 4 November 2026, Halloween was over and Christmas was ahead, so the first `GET` returns the six standard videos. Inside an event window you see that event instead. The seeder does nothing when the table already has rows.

**Try the event flow.** If you ran the pin example, send `{"pinned": false}` first. Call `GET /api/v1/splashes`: six videos. Give Christmas a window with the PATCH above, then call `GET` again: only `seed-08-christmas.mp4` comes back. Switch it off with `{"active": false}` and the six return. To restore its seeded window, send `{"active": true, "startsAt": "2026-12-24T00:00:00Z", "endsAt": "2026-12-27T00:00:00Z"}`.

I made the demo videos from the splash screen recording in the brief, with colour grades, overlays and a cleaned-up status bar. They stand in for the design team's real assets.

## How it was checked

There are 195 tests. None need a database, Docker or network. They cover the tier rules and their time boundaries, every validation message, paging, the upload check, the PATCH rules, the key check, the error format, the cache, the seeder and the storage settings.

I also ran the real app against real Postgres and a real R2 bucket, twice, on a fresh database each time: a scripted pass of about 130 checks. It covered the public response and the `304`, admin auth, events and pins (including a window opening and closing by itself with a warm cache), every validation message, an upload that lands in the bucket with the same SHA-256, a wrong key with an 11 MB file giving a quick `401`, the seeder skipping a non-empty table after a restart, and a storage failure giving a clean `500`. After that I added the logs, the page parameters, the byte check and the upload throttle. I checked those with their own tests and live runs, not by repeating the whole pass.

I have not checked the app behind a CDN, under load, with several instances, or on a phone.

## Future work

- **Impression logging.** The app sends a batched `POST` when a splash plays. I would store per-video daily counters and add a report endpoint for the design team. Costs: a write path with no login, a high-volume table, and work in the app.
- Carousel order and weights, which need an ordering field.
- Regions and timezone-local events, which split the cache.
- Presigned uploads, once files get large.
- Per-admin login with an audit trail.
- Migrations, in place of `ddl-auto=update`.
- Cache sharing across instances, and video duration metadata.

## Limits I accept

- If two admins edit the same video at once, the last write wins. There is no version column.
- If the database save fails after a file has uploaded, an orphan file stays in the bucket. It is harmless. The API has no delete, so removing it means using the storage console.
- The cache lives in one process. With several instances, a write only clears the instance that handled it. The others catch up when the TTL ends.
- A change can take up to 300 seconds to reach apps that hold a fresh copy.
- The upload check looks at the label and the first bytes. It does not prove the file plays. The endpoint is protected by the admin key.
- The upload is read fully into memory. The size cap (`SPLASH_UPLOAD_MAX_SIZE`, 10MB by default) keeps it small.
- The `r2.dev` address is for development and is rate limited. Cloudflare answers `429 Too Many Requests` when it throttles. Production wants a custom domain.
- Event windows are UTC.
- The upload throttle is one counter for the whole app, in one process. A real burst of more than 10 uploads a minute must wait.
- Nothing limits other requests, and the key check has no lockout. Use a long random `ADMIN_API_KEY`.
- The admin list returns pages of 20 by default, so a caller that wants more must pass `size` or page through. No client depends on it yet.

## Questions for the Stroll team

- Is the "Stroll" wordmark baked into the splash video, or does the app draw it on top? It is in the pixels of the recording I was given.
- Should an event replace the normal rotation, or join it?
- Are events the same for every user, or specific to a region?
- Does the app cache splash videos on disk today, and does it check them?
