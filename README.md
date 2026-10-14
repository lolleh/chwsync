# CHW — Community Health Worker sync module for OpenMRS

An OpenMRS module that lets community health workers run their rounds from a phone or tablet with no
network, and reconcile with the facility whenever a connection appears. It ships three pieces that
share one sync contract:

- a **server module** (`api` + `omod`) that exposes an offline-first sync REST API and an automatic
  change feed built from OpenMRS core events;
- a **Progressive Web App** (`omod/src/main/webapp/resources/owa`) served by the module, dependency
  free and fully offline;
- a **native Android app** (`android/`) in Kotlin and Jetpack Compose.

All three let a CHW register and edit patients, run household and visit tracking, capture vitals and
observations, schedule appointments and reminders, make referrals, and see their own reports.

---

## Contents

- [How sync works](#how-sync-works)
- [Authentication](#authentication)
- [Prerequisites](#prerequisites)
- [Building](#building)
- [Installing](#installing)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [The PWA](#the-pwa)
- [The Android app](#the-android-app)
- [Testing](#testing)
- [Project layout](#project-layout)
- [Design notes and limitations](#design-notes-and-limitations)

---

## How sync works

The device's local store is the source of truth for its own user interface, so the app never blocks
on the network. A background sync reconciles it with the server.

- **Pull** returns every record that changed strictly after the client's opaque cursor, page by page.
  Each change carries the record's full payload and a `version` (the last-changed instant, truncated
  to the second). The server deliberately re-reads a short window before the cursor, so a record
  written by a transaction that was still committing during the last pull is not lost. Duplicate
  changes at that boundary are expected and harmless: clients key their stores on `uuid`.
- **Push** sends a batch of the device's changes. Each is answered individually, so one conflict does
  not fail the batch. Every change carries a `clientChangeId`; re-sending a batch returns the original
  outcome (`REPLAYED`) rather than applying it twice, which is what makes a retry after a dropped
  connection safe.
- **Conflicts** are detected with optimistic concurrency. A change whose `baseVersion` matches the
  server's current version is applied; a change built on a stale version is answered with `CONFLICT`
  and the server's current record, so the client can reconcile without a second round trip.
- The automatic feed is fed by **OpenMRS core events**: the module subscribes to patient, person,
  encounter, obs and visit events (via the required `event` module) and appends them to the sync
  change log. Module-owned records (households, appointments, referrals) are appended by the service
  itself.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## Authentication

A CHW types their OpenMRS username and password **once**, on login. The server verifies them against
core, registers the device, and issues an opaque bearer token. Only the SHA-256 hash of the token is
stored, so a database dump does not hand out live sessions. Every subsequent call presents
`Authorization: Bearer <token>`.

The token is resolved back to a user by a custom `AuthenticationScheme`
(`ChwSyncTokenAuthenticationScheme`), so a sync request runs with exactly the privileges that user has
everywhere else in OpenMRS. A device can never do more than its owner; taking access away from a lost
handset is a single revocation, not a password change. Tokens are long lived by default (30 days)
because a CHW may be offline for weeks — revocation, not expiry, is the real control.

## Prerequisites

- OpenMRS platform **2.8.9** (the module requires platform 2.5.0 or later).
- The **[Event](https://github.com/openmrs/openmrs-module-event) module 4.0.0+** installed and started.
  It is a hard requirement (`config.xml`); without it the change feed is empty.
- Java **11 or 17** and Maven to build. Java 25 fails — the build targets `release 11`.

## Building

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # 11 or 17; not 25
mvn clean package
```

The artifact is produced at `omod/target/chwsync-1.0.0-SNAPSHOT.omod`.

The `api` module compiles against the event module's API, which is not published to a Maven
repository; it is vendored at `lib/event-api-4.0.0.jar` and referenced with `system` scope. At runtime
the classes come from the installed event module.

## Installing

1. Copy `omod/target/chwsync-1.0.0-SNAPSHOT.omod` into your OpenMRS modules directory
   (`~/.OpenMRS/modules` or `<appdata>/modules`).
2. Ensure `event-4.0.0.omod` (or newer) is present too.
3. Restart OpenMRS. The module runs its Liquibase changesets on first start.

The PWA is then available at:

```
<context>/moduleResources/chwsync/owa/index.html
```

e.g. `http://localhost:8080/openmrs/moduleResources/chwsync/owa/index.html`.

## Configuration

### Privileges (assign via roles)

| Privilege | Grants |
| --- | --- |
| `Sync CHW Data` | Read and write through the sync feed. The only privilege a CHW account needs. |
| `Manage CHW Devices` | Device registration/revocation and the cross-CHW device list. |

A CHW must hold `Sync CHW Data`; login will fail with a 403 otherwise.

### Global properties

| Property | Default | Meaning |
| --- | --- | --- |
| `chwsync.syncPageSize` | `200` | Changes returned by a pull when the client does not ask. |
| `chwsync.syncMaxPageSize` | `1000` | Hard ceiling on a single pull. |
| `chwsync.syncMaxPushBatch` | `200` | Hard ceiling on changes in one push. |
| `chwsync.tokenTtlHours` | `720` | Device token lifetime, in hours. |
| `chwsync.identifierType` | *(empty)* | Identifier type for a CHW-assigned patient number; empty lets core assign. |

## REST API

Base path `<context>/ws/chwsync/v1`. Errors are `{ "code": ..., "message": ... }` with
`400`/`401`/`403`/`404`/`500`.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/manifest` | none | API version and the record types this server carries. |
| POST | `/auth/login` | none | Verify credentials, register the device, return a bearer token. |
| GET | `/auth/me` | bearer | The current token's user and privileges. |
| POST | `/auth/logout` | bearer | Revoke the presented token. |
| GET | `/sync/pull?since=&types=&limit=` | bearer | A page of changes and the next cursor. |
| POST | `/sync/push` | bearer | Apply a batch of client changes; per-item results. |
| GET | `/sync/record/{type}/{uuid}` | bearer | One record in wire form, for conflict resolution. |
| GET | `/sync/limits` | bearer | Server page and batch limits. |
| GET | `/reference` | bearer | Locations, identifier/visit/encounter types, the caller's provider. |
| GET | `/reference/concepts?q=` | bearer | Concept search, for coded observations. |
| GET | `/devices` | manage | Every registered device and its user. |
| POST | `/devices/{uuid}/deactivate` | manage | Deactivate a device and revoke its tokens. |
| POST | `/devices/{uuid}/activate` | manage | Return a device to service. |

Record types: `patient`, `visit`, `encounter`, `household`, `appointment`, `referral`.
Change operations: `CREATE`, `UPDATE`, `DELETE`.
Push statuses: `APPLIED`, `CONFLICT`, `REJECTED`, `REPLAYED`.

Full request/response and payload shapes: [`docs/API.md`](docs/API.md).

## The PWA

A dependency-free Progressive Web App served from the module at
`<context>/moduleResources/chwsync/owa/`. It stores the register in IndexedDB, registers a service worker
that caches the app shell, and works with no network. Open it on the phone, sign in, and it is
installable to the home screen.

## The Android app

A native Kotlin / Jetpack Compose client under `android/`, using Room for the local store, Retrofit
for the wire, DataStore for the token, and WorkManager for background sync. See
[`android/README.md`](android/README.md). It builds with `./gradlew :app:assembleDebug` (Android SDK
34 and JDK 17).

## Testing

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -o -pl api test
```

The suite covers the pure parts of the contract: date/version handling, the wire enumerations, the
patient and encounter codecs, and token verification (hashing, revocation, expiry).

## Project layout

```
chwsync/
  api/     Services, DAOs, models, codecs, the sync engine, the event listener, the auth scheme
  omod/    REST controllers, module/web Spring contexts, config.xml, Liquibase, the PWA
  android/ Native Compose client (source only)
  lib/     vendored event-api jar (compile-time sight of the event module's API)
  docs/    Architecture and API reference
```

## Design notes and limitations

- **No Hibernate mappings.** The module's own tables are accessed with native SQL, so the schema in
  `api/src/main/resources/liquibase.xml` is the single source of truth.
- **Observations travel inside their encounter.** An obs has no meaning without the encounter and
  patient it belongs to, so the two move together. Complex obs (images, documents) are intentionally
  not carried.
- **Voids, not deletes.** Clinical rows are voided; a `DELETE` on the wire means "no longer active".
- **Per-user sync only.** The API is scoped to the authenticated user; there is no cross-CHW patient
  filter beyond OpenMRS privileges. Restricting a CHW to their own patients is a deployment concern
  (location-based access), not enforced by this module yet.
