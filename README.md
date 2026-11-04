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

This is a backend that tells the iOS app which splash videos it may play. The set of videos can change without an app release. An event video, such as Christmas, replaces the normal rotation for a time window. It is Java 21, Spring Boot, PostgreSQL and Cloudflare R2 (S3-compatible file storage). It is backend only. I did not build a Swift client, because the brief says the backend alone is complete.

## How I read the brief

- The splash plays at app launch, possibly before the network is ready. So the app must never wait on this call.
- A CDN (content delivery network, a service that keeps copies of files close to users) fronts the API and the video storage. R2 sits on Cloudflare's network. For production I would use a custom domain. The `r2.dev` address is for development only.
- "Carousel" and "random rotation" mean the app picks one video at random from a pool on each launch. A design-controlled order would need an ordering field. I list that as future work.
- An event replaces the normal rotation for its window. I took "a new splash video for that duration" literally.
- "About 6" videos is data, not code. The pool can be any size.
- The brief only says the set must change without an app release. The admin API is my assumption about how.
- There is one backend instance.
- Event windows are in UTC.

## The design in one page

The flow:

1. The app calls `GET /api/v1/splashes`.
2. The backend works out which tier applies (a live event, or the normal rotation), applies pins, and returns the pool.
3. The app picks one video at random and plays it.

The backend returns a pool, not a single pick. A pool is the same for every user. So the app and a CDN can cache it, and the server needs no randomness and no idea who is asking.

The rules for building the pool are short:

- Only videos with `active = true` count.
- If at least one active event is live now, only live events are returned. They replace the standard videos.
- Otherwise the active standard videos are returned.
- A start time counts as live. An end time does not (start inclusive, end exclusive).
- If any video in the chosen tier is pinned, only the pinned ones are returned. A pin narrows the pool inside its tier.
- A pinned standard video never beats a live event.
- An event without both start and end times is never eligible.
- An empty pool is valid. The app then plays the video bundled in the app.

An example with the seed data. Normally the six standard videos come back. If I give Christmas a window around now, only Christmas comes back. If two events are live at once, both come back and the app picks between them.

**What the app is expected to do.** I cannot check any of this from the backend. I expect the app to use a short timeout, play from its local cache, fall back to the bundled default when the call fails, pick at random, and check a downloaded file's checksum before keeping it.

## Speed

The splash is the first thing a user sees, so speed was my baseline. I did four things.

**The active rows are cached in memory.** I use Caffeine, a Java cache library. On a cache hit, a request runs no database query. The cache lasts `splash.cache.ttl`, which is 5 minutes (TTL means time to live: how long an entry stays before it is reloaded). Any PATCH clears it straight away. The cache holds the raw active rows, and the tier is worked out on each request. So an event starts and ends at its exact second even while the cache is warm. I watched that happen on a live run.

**`Cache-Control` lets the app or a CDN reuse its copy without asking me.** The app sends this header:

```
Cache-Control: max-age=300, public
```

For 300 seconds the app can reuse what it has. It does not call me at all. The value comes from `splash.http.max-age`.

**The ETag lets the app ask "has this changed?"** After those 300 seconds, the app sends the ETag it holds in an `If-None-Match` header. If nothing changed, I reply `304 Not Modified` with no body. An ETag is a fingerprint of a response. Mine is a SHA-256 hash of every item the app sees: id, URL, checksum and size. The URL is part of it. So if the CDN domain changes, the ETag changes too, and nobody is stuck holding a dead URL.

The value here is modest. The payload is small: with the six seed videos the body is 1,482 bytes, and a `304` is 164 bytes including headers. It matters most behind a CDN, which can check with the ETag instead of fetching again.

**The cost: a change can be late.** A kill switch or an event boundary can take up to `max-age`, which is 300 seconds, to reach apps that hold a fresh copy.

My targets were a cache hit in a few milliseconds and a miss with one query in tens of milliseconds. I measured on my laptop against local Postgres, with no network in between. A cache hit had a median of 0.43 ms (95th percentile 1.03 ms) over 500 requests. A miss had a median of 1.93 ms (95th percentile 3.24 ms) over 60 requests. A real network adds to both. I have not measured behind a CDN or under load.

**Why no Redis.** Redis would add a network hop to save a query that costs almost nothing. The cache here is a few rows.

## Decisions, and what I left out

First the choices that had a real alternative. Then what I did not build.

| Decision | What I chose | What I dropped | Why |
|---|---|---|---|
| Pool or single pick | Return a pool | Pick one on the server | A pool is the same for everyone, so it caches. The server needs no randomness or user identity. |
| Cache | In-process Caffeine | Redis | A network hop to save a cheap query. The data is a few rows. |
| Upload path | Through the backend | Presigned URLs | A splash is a few MB. Fewer moving parts, and the server hashes the bytes it receives. |
| Hiding a video | An `active` flag | A DELETE endpoint | A flag is reversible and leaves no orphaned files in storage. |
| Admin access | One static key | Per-admin login and an audit trail | The brief does not stress security. This is a small internal tool. |
| Schema | Hibernate `ddl-auto=update` and a seeder | Flyway migrations | One table. This is a demo convenience. Production would use migrations. |
| Event window checks | Guards in code | A database CHECK constraint | Hibernate applies a CHECK reliably only when it creates the table. |
| Events | They replace the rotation | They join it | I read "for that duration" literally. |
| Overlapping events | Both are returned, the app picks | A priority column | The simplest rule. An admin can switch one off. |
| Stored location | The object key, with the URL built from `storage.public-url` | The full URL | A new bucket or CDN domain is one setting and moves no data. |
| Content hash | My own SHA-256 | R2's ETag | R2's ETag is not a content hash for multipart uploads. |
| ETag type | A strong ETag over the response content | A weak ETag, or a version counter | It changes whenever the response changes, URL included, and needs no stored state. |
| PATCH semantics | `null` and "left out" both mean no change. Unknown fields and an empty body are rejected. | Silent acceptance | A typo like `pined` would succeed and change nothing. |
| After upload | `active = false` | Switching it on automatically | An upload cannot reach the app by accident. |
| Time | UTC instants | Local time zones | A window has one exact meaning. |
| Admin key check | Before the upload body is read | After parsing the body | A wrong key with a large file is refused cheaply. |

**Things I deliberately did not build.**

- **Impression logging.** The brief never asks for measurement, and it needs a high-volume table. I wrote it up under future work.
- **Regional or per-user targeting.** It would split the cache and put user identity into the call.
- **Timezone-local events.** "Christmas Day" is exact for one time zone only.
- **Per-admin identity.** I explain this in its own section below.
- **A DELETE endpoint.** Switching a video off does the job and leaves no orphans.
- **"Never the same splash twice in a row".** It needs identity and a write on every app open.
- **Carousel order or weights.** I read the brief as random rotation.
- **Video duration metadata.** It needs a tool like ffprobe. I store checksum and size only.
- **Rate limiting.** I see it as a job for a gateway or CDN.
- **Cache invalidation across several instances.** The cache lives in one process.
- **The Swift client.** It is optional in the brief.

## Why I store a checksum

This is deliberate. When a video is uploaded, the server hashes the bytes once with SHA-256 and stores the result next to the size. The app is expected to hash what it downloaded and keep the file only if the two match. So a dropped or cut-off download never becomes a broken splash. I do not lean on R2's own ETag, because for multipart uploads it is not a hash of the content.

## Why one static admin key

This is deliberate too. The admin endpoints are guarded by a static key in the `X-Admin-Key` header. The key is read from the `ADMIN_API_KEY` environment variable. I left out per-admin identity, login and an audit trail. The brief does not emphasise security, and this is a small internal tool.

The cost is plain. There is no record of who changed what, and there is one shared key to rotate.

Inside that choice I did these things properly:

- The key is compared in constant time (`MessageDigest.isEqual`), so response timing does not leak how much of a guess matched.
- The app refuses to start if the key is blank.
- The key is never logged. I searched the logs of my test runs for it and found no match.
- The key is checked before an upload body is read. A wrong key with an 11 MB file gets a `401` in milliseconds, not a `413`. I ran that on the live app.

## Project structure

```
strollsplash/
  pom.xml, mvnw               Maven build and wrapper
  docker-compose.yml          Postgres 16 for local runs
  .env.example                every environment variable, dummy values
  assets/
    splashes/                 the eight demo videos and manifest.json (object key, SHA-256, size)
    extra/new-year-event.mp4  a ninth video for trying the upload endpoint
  src/main/java/dev/slethware/strollsplash/
    controller/               SplashController (public), AdminSplashController (admin)
    service/splash/           resolver, cache, validator, seeder, the two services
    service/storage/          StorageService and its S3/R2 implementation
    repository/               SplashVideoRepository
    entity/                   SplashVideo
    dto/                      request and response shapes
    enums/                    SplashKind (STANDARD or EVENT)
    exception/                custom exceptions and one global handler
    interceptor/              AdminKeyInterceptor
    config/                   properties, storage client, web config
    utility/                  ChecksumUtil, ApiResponseUtil
  src/main/resources/
    application.properties
    seed/splashes.json        the eight starting rows
  src/test/java/...           the tests, none of which need a database
```

A public read goes controller, service, cache, resolver, and comes back out as a pool. An empty cache loads the active rows from the repository first. An admin write goes through the key interceptor, then the controller, then the admin service. The service validates, uploads the file if there is one, saves the row, and (for a PATCH) clears the cache.

## Data model

There is one table, `splash_video`. Hibernate creates it.

| Column | Type | Meaning |
|---|---|---|
| `id` | uuid | Primary key, generated. |
| `title` | varchar, not null | An admin label, at most 120 characters (checked in code). |
| `kind` | varchar, not null | `STANDARD` or `EVENT`. |
| `object_key` | varchar, not null | The file's place in the bucket, such as `splashes/seed-01-original.mp4`. |
| `checksum` | varchar(64), not null | SHA-256 of the file, as lowercase hex. |
| `size_bytes` | bigint, not null | File size in bytes. |
| `active` | boolean, not null | Off hides the video (the kill switch). |
| `pinned` | boolean, not null | Narrows the pool inside the tier. |
| `starts_at` | timestamptz | Event start. Empty for a standard video. |
| `ends_at` | timestamptz | Event end. Empty for a standard video. |
| `created_at` | timestamptz, not null | Set on insert. |
| `updated_at` | timestamptz, not null | Set on every update. |

The database stores the object key. The public URL is `storage.public-url` plus the key. So pointing at another bucket, or at a new CDN domain, is one setting and moves no data.

## API

There are four endpoints. Every admin endpoint needs the `X-Admin-Key` header.

| Method and path | Auth | Purpose | Success |
|---|---|---|---|
| `GET /api/v1/splashes` | none | The pool the app may play | 200, or 304 when unchanged |
| `GET /api/v1/admin/splashes` | admin key | Every video, active or not, newest first | 200 |
| `POST /api/v1/admin/splashes` | admin key | Upload an mp4 (a multipart request: a file plus text fields) | 201 |
| `PATCH /api/v1/admin/splashes/{id}` | admin key | Partial update of one video | 200 |

Swagger UI documents every endpoint at `http://localhost:8080/docs`. Its Authorize button takes the admin key and sends it as `X-Admin-Key`. I checked that in a browser. The raw OpenAPI file is at `/api-docs`.

These examples assume the app is running and you have loaded `.env` (see [Running it](#running-it)). I ran each one against the running app. I use `jq` only to pick values out of JSON. You can copy ids by hand instead.

**The public pool.**

```bash
curl -i http://localhost:8080/api/v1/splashes
```

It returns `200` with the `Cache-Control` and `ETag` headers. The body has `message`, `statusCode`, `isSuccessful` and `data`, a list of `id`, `url`, `checksum` and `sizeBytes`.

**The same call with `If-None-Match`.**

```bash
ETAG=$(curl -s -D - -o /dev/null http://localhost:8080/api/v1/splashes | grep -i '^etag:' | cut -d' ' -f2 | tr -d '\r')
curl -i -H "If-None-Match: $ETAG" http://localhost:8080/api/v1/splashes
```

It returns `304` with no body and both headers.

**List every video.**

```bash
curl -s -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/api/v1/admin/splashes | jq -c '.data[] | {title, kind, active, pinned}'
```

**Pin a video.** After this, the pool holds only that video.

```bash
ID=$(curl -s -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/api/v1/admin/splashes | jq -r '.data[] | select(.title == "Original") | .id')
curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d '{"pinned": true}' http://localhost:8080/api/v1/admin/splashes/$ID
```

To undo it, send `{"pinned": false}` to the same URL.

**Give an event a window.** This makes Christmas live from one hour ago to one hour from now.

```bash
XMAS=$(curl -s -H "X-Admin-Key: $ADMIN_API_KEY" http://localhost:8080/api/v1/admin/splashes | jq -r '.data[] | select(.title == "Christmas 2026") | .id')
FROM=$(python3 -c "import datetime as d; print((d.datetime.now(d.timezone.utc) - d.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
TO=$(python3 -c "import datetime as d; print((d.datetime.now(d.timezone.utc) + d.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d "{\"startsAt\": \"$FROM\", \"endsAt\": \"$TO\"}" http://localhost:8080/api/v1/admin/splashes/$XMAS
```

**Upload a video.** The file part must be sent as `video/mp4`. The new row is inactive until you switch it on with a PATCH.

```bash
curl -s -X POST -H "X-Admin-Key: $ADMIN_API_KEY" -F "title=New Year 2027" -F "kind=EVENT" -F "startsAt=2026-12-31T00:00:00Z" -F "endsAt=2027-01-02T00:00:00Z" -F "file=@assets/extra/new-year-event.mp4;type=video/mp4" http://localhost:8080/api/v1/admin/splashes
```

It returns `201` with `active` and `pinned` both false. Do not set the `Content-Type` header yourself here. curl adds it, with the boundary the server needs.

**Errors.** Every failure has the same three fields. A wrong key looks like this:

```bash
curl -i -H "X-Admin-Key: wrong" http://localhost:8080/api/v1/admin/splashes
```

```json
{"message":"Invalid or missing admin key","error":"Unauthorized","statusCode":401}
```

These are the cases and their statuses:

- `400`: a validation message (several are joined with "; "), an unknown field such as `Unknown field 'pined'`, an unreadable JSON body, a bad value such as `Invalid value 'BOGUS' for kind. Allowed values: STANDARD, EVENT`, an empty PATCH (`At least one field must be provided`), or a request that is not multipart.
- `401`: `Invalid or missing admin key`.
- `404`: `Splash video not found`, or a route that does not exist.
- `405`: the wrong method for a route.
- `413`: `File exceeds the maximum upload size`.
- `500`: `Failed to upload video to storage`, or `An unexpected error occurred`.

## Running it

You need JDK 21 and Docker. Docker runs only Postgres. The app runs on your machine.

1. Copy the example file and fill it in. Use obvious values for the database, and the storage values from one of the two options below.

   ```bash
   cp .env.example .env
   ```

2. Start Postgres.

   ```bash
   docker compose up -d
   ```

3. Load the environment into your shell and start the app. The app does not read `.env` by itself. The variables must be in the environment of the shell that starts it.

   ```bash
   set -a; source .env; set +a
   ./mvnw spring-boot:run
   ```

4. Open `http://localhost:8080/docs`.

One trap: Postgres only reads `DB_USERNAME` and `DB_PASSWORD` the first time it creates its volume. If you change them later, run `docker compose down -v` and start again.

The app will not start if a required value is blank. These are all the environment variables:

| Variable | What it is | Example or default | Required |
|---|---|---|---|
| `DB_URL` | JDBC address of Postgres | `jdbc:postgresql://localhost:5432/strollsplash` | yes |
| `DB_USERNAME` | Postgres user | `strollsplash` | yes |
| `DB_PASSWORD` | Postgres password | `change-me` | yes |
| `ADMIN_API_KEY` | The key admins send as `X-Admin-Key` | `change-me` | yes |
| `STORAGE_ENDPOINT` | S3-compatible endpoint. Leave empty for AWS S3. | empty, or `https://<account-id>.r2.cloudflarestorage.com` | no |
| `STORAGE_REGION` | Storage region | `us-east-1` | no |
| `STORAGE_ACCESS_KEY_ID` | Storage access key id | (your value) | yes |
| `STORAGE_SECRET_ACCESS_KEY` | Storage secret | (your value) | yes |
| `STORAGE_BUCKET_NAME` | Bucket name | `my-bucket` | yes |
| `STORAGE_PUBLIC_URL` | Public base address of the bucket | `https://pub-example.r2.dev` | yes |
| `SPLASH_UPLOAD_MAX_SIZE` | Largest allowed upload | `10MB` | no |
| `PORT` | HTTP port | `8080` | no |

The seeded rows point at real files, so there are two ways to set up storage.

**Option one: the bucket details I send you privately.** Put them in `.env`. The key I share is scoped to one bucket, and I will rotate it after the review. Do not put any key in the repo.

**Option two: your own bucket.** Any S3-compatible store works. Leave `STORAGE_ENDPOINT` empty for AWS S3, or set it for R2 or a similar service. Upload the eight files in `assets/splashes/`, using the object keys in `assets/splashes/manifest.json`. Make the bucket publicly readable, and set `STORAGE_PUBLIC_URL` to its public address. The app needs all the storage values to start. Uploads through the API also need a key that can write.

**What you will see on first run.** The seeder creates eight rows: six standard videos, Halloween 2026 (30 October to 1 November) and Christmas 2026 (24 to 27 December). When I wrote this, on 4 November 2026, Halloween was over and Christmas was ahead. So the first `GET /api/v1/splashes` returns the six standard videos. If you run it inside an event window, you will see that event instead. That is the feature working. The seeder does nothing when the table already has rows.

**Try the event flow.**

1. Call `GET /api/v1/splashes` and see six videos.
2. Give Christmas a window around now, with the PATCH from the API section. Call `GET` again: only `seed-08-christmas.mp4` comes back.
3. Switch it off, and the six come back.

   ```bash
   curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d '{"active": false}' http://localhost:8080/api/v1/admin/splashes/$XMAS
   ```

4. To put Christmas back as it was seeded:

   ```bash
   curl -s -X PATCH -H "X-Admin-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" -d '{"active": true, "startsAt": "2026-12-24T00:00:00Z", "endsAt": "2026-12-27T00:00:00Z"}' http://localhost:8080/api/v1/admin/splashes/$XMAS
   ```

I made the demo videos from the splash screen recording in the brief, with colour grades, overlays and a cleaned-up status bar. They stand in for the design team's real assets.

## How it was checked

There are 165 tests. None of them need a database, Docker or network. They cover the tier rules and their time boundaries, every validation message, the PATCH merge rules, the admin key check, the error format, the cache, the seeder and the storage settings.

I also ran the real app against real Postgres and a real R2 bucket, twice, on a fresh database each time. That was a scripted pass of about 130 checks. It covered:

- the public response, its headers and the `304`
- admin auth, with a missing, empty and wrong key
- pool resolution with events and pins, including an event window opening and closing by itself while the cache was warm
- every validation message on PATCH and upload
- an upload that lands in the bucket, with the same SHA-256 and size when I download it
- a wrong key with an 11 MB file giving a quick `401`, and the right key giving a `413`
- the seeder skipping a database that already has rows after a restart
- a storage failure giving a clean `500` and no row

Tests cannot prove the order of the key check and the upload parsing. That only shows on a live request, so I ran it. I have not checked the app behind a CDN, under load, with several instances, or on a phone.

## Future work

- **Impression logging.** The app would send a batched `POST` when a splash plays. I would store per-video daily counters and add a report endpoint for the design team. The costs: a write path on an endpoint with no login, a high-volume table, and work in the app.
- **Carousel order and weights.** This needs an ordering field.
- **Regions and timezone-local events.** These split the cache by region.
- **Presigned uploads,** once files get large.
- **Per-admin login with an audit trail.**
- **Migrations,** in place of `ddl-auto=update`.
- **Cache invalidation across several instances.**
- **Video duration metadata,** read at upload.
- **Checking the file bytes on upload.** See the limits below.

## Limits I accept

- If two admins edit the same video at once, the last write wins. There is no version column.
- If the database save fails after a file has uploaded, an orphan file stays in the bucket. It is harmless and costs a few MB. The API has no delete, so removing it means using the storage console.
- The cache lives in one process. With several instances, a write only clears the instance that handled it. The others catch up when the TTL ends.
- A change can take up to `max-age` (300 seconds) to reach apps that hold a fresh copy.
- The upload type check reads the declared `Content-Type` of the file part. It does not look at the bytes. I tried it: text sent as `video/mp4` is accepted. The endpoint is protected by the admin key, so I judged this acceptable for now.
- The upload is read fully into memory. The size cap (`SPLASH_UPLOAD_MAX_SIZE`, 10MB by default) keeps it small.
- The `r2.dev` address is for development and is rate limited. Production wants a custom domain.
- Event windows are UTC.
- A video's file cannot be replaced. To swap a file, upload a new video and switch the old one off.
- The admin list has no pagination. It returns every row.

## Questions for the Stroll team

These are the questions I would ask in a real kickoff.

- Is the "Stroll" wordmark baked into the splash video, or does the app draw it on top? It is in the pixels of the recording I was given.
- Should an event replace the normal rotation, or join it?
- Are events the same for every user, or specific to a region?
- Does the app cache splash videos on disk today, and does it check them?
