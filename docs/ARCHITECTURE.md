# Architecture

This module exists to let a community health worker work from a handset with no dependable network.
That single constraint shapes everything below: the device's local store is authoritative for its own
screen, the server is authoritative for the shared record, and the two reconcile through a change
feed that is safe to interrupt and retry at any point.

## The moving parts

| Layer | Where | Responsibility |
| --- | --- | --- |
| REST web layer | `omod/.../web/controller` | HTTP, bearer-token auth, wire DTOs, error mapping. |
| Services | `api/.../api/service` | Business rules: pull, push, conflict detection, devices, tokens. |
| DAOs | `api/.../api/dao` | Native SQL against the module's tables and reads of core tables. |
| Codecs | `api/.../api/sync/codec` | Turn core domain objects into wire maps and back. |
| Event listener | `api/.../api/event` | Listen to OpenMRS core events and append to the change log. |
| Auth scheme | `api/.../api/security` | Resolve a bearer token back to a core `User`. |

The module deliberately carries no Hibernate mappings. Its tables are small and its access patterns
are simple, so the schema in `liquibase.xml` and the SQL in the DAOs are the whole story, and there is
no second place for the model to drift.

## The change log

Everything a device pulls comes from one append-only table, `chwsync_change`:

```
change_id     bigint pk, auto-increment   -- the revision, monotonic
entity_type   varchar                     -- patient, visit, encounter, household, appointment, referral
entity_uuid   char(38)                    -- the record this is a change to
operation     varchar                     -- CREATE, UPDATE, DELETE
changed_at    datetime                    -- when it happened
changed_by    int                         -- the user who caused it
device_uuid   char(38)                    -- the device, when the change came from one
```

The `change_id` is the ordering and the revision a device acknowledges. `changed_at` is what a cursor
is compared against. The distinction matters: two changes can share an instant, so the cursor is a
timestamp read back to the client, and the server re-reads a short window before it on the next pull
(see below).

Rows are produced two ways:

- **Core events.** `ChwSyncChangeEventListener` subscribes to patient, person, encounter, obs and visit
  events through the required `event` module. When core changes a record, the listener appends a
  change. This is how edits made anywhere in OpenMRS — the legacy UI, the REST API, another module —
  reach the field devices.
- **Module writes.** Records the module owns (households, appointments, referrals) are appended by
  `ChwSyncServiceImpl` as it writes them, because no core event exists for them.

## Pull

```
GET /ws/chwsync/v1/sync/pull?since=<cursor>&types=<csv>&limit=<n>
```

1. The cursor is parsed back to an instant (`since`, absent means "from the beginning").
2. The DAO reads changes with `changed_at` strictly greater than the cursor, up to `limit + 1`, in
   `change_id` order.
3. The extra row decides `hasMore`.
4. Repeats of the same record are collapsed to the latest change, so a record edited five times
   between two pulls travels once. The record is then serialized fresh from the database, not from a
   snapshot in the log — the log says *that* something changed, the codec says *what it is now*.
5. The response's cursor is the instant the read ran (`now`), not the timestamp of the last row.

Step 5 with a strict `>` comparison would normally lose a change that committed while the response
was being built. To avoid that the server reads from `now - 5s` on the next call, trading a small,
bounded number of duplicate rows for never dropping one. Duplicates are free to the client, which
upserts by `uuid`; a lost change is not.

## Push

```
POST /ws/chwsync/v1/sync/push
{ "deviceUuid": "...", "changes": [ { "clientChangeId": "...", "entityType": "...", "uuid": "...",
  "operation": "...", "baseVersion": <n|null>, "payload": { ... } } ] }
```

Each item is evaluated on its own and answered on its own, so one bad change does not fail the batch:

| `baseVersion` | Entity state given `uuid` | Result |
| --- | --- | --- |
| `null` | absent | `APPLIED` — created |
| `null` | present | `CONFLICT` — someone already created this uuid |
| set | absent | `REJECTED` — edited something the server does not have |
| set | present, differs from current version | `CONFLICT` — carried `data`, the server's copy |
| set | present, matches current version | `APPLIED` |

A `DELETE` is idempotent: deleting an already-gone record is `APPLIED`, because the intended end
state is what holds. When a change is applied the row's `version` is set to the change instant
truncated to the second, which is what a later device compares its `baseVersion` against.

Every item carries a `clientChangeId`, and the outcome of each is recorded in `chwsync_client_change`. A
retry of a batch the server already saw returns the recorded outcome as `REPLAYED` instead of doing
the work twice. This is what makes "send, connection dies, send again" safe — the retry cannot
duplicate a patient.

## Where records come from

Pulled records are assembled by codecs that read straight from core services and flatten a core
object into the map the clients expect:

- **patient** — person demographics, names, addresses, identifiers.
- **visit** — visit type, location, start/stop, and the encounters a device should see.
- **encounter** — encounter type, provider, location, and its obs (coded and primitive).
- **household / appointment / referral** — module-owned tables.

Observations travel inside the encounter that contains them. An observation has no meaning detached
from its encounter and patient, so there is no top-level `obs` type; complex observations (images,
documents) are not carried at all.

## Authentication

A CHW authenticates with a username and password exactly once, at `POST /auth/login`. That call:

1. authenticates against core (`Context.authenticate`),
2. registers the device and issues a token in the same transaction,
3. returns the token, which the client stores and sends as `Authorization: Bearer <token>`.

Tokens are 32 random bytes, base64url. Only the SHA-256 hash is stored, so the database never holds a
value that can be replayed. `ChwSyncTokenAuthenticationScheme` resolves an incoming token to a `User` by
hashing it, looking up the row, and rejecting anything absent, expired, or revoked. `BaseSyncController`
then runs the request in that user's context, so core authorization and privileges apply unchanged.

Because the token belongs to a real user, deactivating a device — which revokes every token tied to
it — is all it takes to cut off a lost handset. Expiry is a backstop, not the mechanism: the default
lifetime is 30 days precisely because a CHW may be out of range for a long time, and forcing a
password re-entry in the field would be worse than a long-lived token that can be revoked centrally.

## The three clients

- The **PWA** and the **Android app** implement the same contract independently. Neither is generated
  from the other; the contract in `docs/API.md` is the interface.
- Both keep an outbox and apply the same optimistic-concurrency rule locally that the server applies
  remotely, so conflicts are rare and, when they happen, visible.
- Dates on the wire are always ISO-8601 strings, never numeric timestamps, so a client never has to
  know how the server's JSON serializer is configured.

## Deliberate limits

- Sync is scoped to the authenticated user and their privileges; there is no additional per-CHW
  patient partitioning yet. A deployment that needs "each CHW sees only their own patients" would add
  a location-based filter in the codecs and DAOs.
- There is no server-push or long-poll channel. Devices poll when they have a connection.
- The module does not carry complex obs, attachments, or the full concept dictionary.
