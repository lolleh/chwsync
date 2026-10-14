# REST API reference

Base URL: `<context>/ws/chwsync/v1` (for example `http://localhost:8080/openmrs/ws/chwsync/v1`).

All bodies are JSON (`Content-Type: application/json`). All timestamps are ISO-8601 strings; all
dates are `yyyy-MM-dd` strings. Record cross-references are **uuids**, never integer ids.

## Authentication

Everything except `/manifest`, `/auth/login` and `/reference/concepts`'s parameter validation requires
a bearer token:

```
Authorization: Bearer <token>
```

The `Bearer ` prefix is optional. A missing, malformed, expired or revoked token gives `401`; a valid
token whose user lacks the required privilege gives `403`.

## Errors

Every error is:

```json
{ "code": "forbidden", "message": "This account is not allowed to sync CHW data." }
```

| Status | When |
| --- | --- |
| `400` | Malformed body, unknown record type, missing required query parameter. |
| `401` | No token, or the token does not resolve. |
| `403` | The token's user is known but not permitted. |
| `404` | The record or device does not exist. |
| `500` | Unexpected server error. |

---

## `GET /manifest` — no auth

A small, unauthenticated description so a client can check compatibility before logging in.

```json
{
  "module": "chwsync",
  "apiVersion": "v1",
  "namespace": "/ws/chwsync/v1",
  "entityTypes": ["patient", "visit", "encounter", "household", "appointment", "referral"],
  "serverTime": "2026-10-14T09:15:00Z"
}
```

---

## Auth

### `POST /auth/login`

Verifies a username and password, registers the device, and issues a token, all in one call.

```json
{
  "username": "chwsync.amelia",
  "password": "secret",
  "deviceUuid": "9f1c... (optional; the server assigns one if omitted)",
  "deviceName": "Amelia's phone",
  "platform": "android",
  "appVersion": "1.0.0"
}
```

**200**

```json
{
  "token": "0Xy... (opaque, shown once and never again)",
  "deviceUuid": "9f1c...",
  "userUuid": "a34d...",
  "username": "chwsync.amelia",
  "displayName": "Amelia N.",
  "privileges": ["Sync CHW Data"],
  "expiresAt": "2026-11-13T09:15:00Z",
  "serverTime": "2026-10-14T09:15:00Z"
}
```

`401` for a wrong username or password (the same message either way), `403` when the account is valid
but lacks `Sync CHW Data`.

### `GET /auth/me`

The current token's user and privileges.

```json
{
  "userUuid": "a34d...",
  "username": "chwsync.amelia",
  "displayName": "Amelia N.",
  "privileges": ["Sync CHW Data"],
  "serverTime": "2026-10-14T09:16:00Z"
}
```

### `POST /auth/logout`

Revokes the presented token. Idempotent. Returns `204 No Content`.

---

## Sync

### `GET /sync/pull`

| Query | Meaning |
| --- | --- |
| `since` | The cursor from the previous pull. Omit for a first sync. |
| `types` | Comma-separated subset of record types. Omit for all. |
| `limit` | Page size. Clamped to `chwsync.syncMaxPageSize`. |

```json
{
  "cursor": "2026-10-14T09:15:00Z",
  "hasMore": false,
  "changes": [
    {
      "revision": 412,
      "entityType": "patient",
      "uuid": "a34d...",
      "operation": "UPDATE",
      "version": 1760433300,
      "deleted": false,
      "changedAt": "2026-10-14T09:14:58Z",
      "data": { "uuid": "a34d...", "version": 1760433300, "names": [ ... ] }
    }
  ]
}
```

Pass the returned `cursor` verbatim on the next call. A `deleted: true` change has no `data`: remove
the record if you have it, and do nothing if you do not.

### `POST /sync/push`

```json
{
  "deviceUuid": "9f1c...",
  "changes": [
    {
      "clientChangeId": "b7e2... (unique per change, stable across retries)",
      "entityType": "patient",
      "uuid": "a34d...",
      "operation": "UPDATE",
      "baseVersion": 1760433300,
      "payload": { "uuid": "a34d...", "gender": "F", "names": [ ... ] }
    }
  ]
}
```

`baseVersion` is the `version` you last saw for the record, or `null`/omitted for a create.

**200**

```json
{
  "cursor": "2026-10-14T09:15:01Z",
  "applied": 1,
  "conflicts": 0,
  "rejected": 0,
  "results": [
    {
      "clientChangeId": "b7e2...",
      "entityType": "patient",
      "uuid": "a34d...",
      "status": "APPLIED",
      "serverVersion": 1760433301,
      "message": null,
      "data": null
    }
  ]
}
```

Statuses: `APPLIED`, `CONFLICT` (with the server's `data`), `REJECTED`, `REPLAYED`. See
[Architecture](ARCHITECTURE.md) for the decision table. The reported `serverVersion` is the record's
new `version`; store it so the next edit uses the right `baseVersion`.

### `GET /sync/record/{type}/{uuid}`

Reads one record in the same wire form as a pull change's `data`, for settling a conflict. `404` if it
does not exist.

### `GET /sync/limits`

```json
{ "pageSize": 200, "maxPushBatch": 200, "serverTime": "2026-10-14T09:15:00Z" }
```

---

## Reference data

### `GET /reference`

The small set a form needs to build a valid record. Fetch once and cache it.

```json
{
  "locations": [ { "uuid": "...", "name": "Kasese HC IV" } ],
  "identifierTypes": [ { "uuid": "...", "name": "OpenMRS ID", "format": null, "required": true } ],
  "visitTypes": [ { "uuid": "...", "name": "Facility Visit" } ],
  "encounterTypes": [ { "uuid": "...", "name": "Vitals" } ],
  "provider": { "uuid": "...", "name": "Amelia N." }
}
```

`provider` is the current user's own provider record, or `null` if they have none.

### `GET /reference/concepts?q=`

Concept search for coded observations. `q` is required and must be at least two characters (`400`
otherwise). Returns at most 25 non-retired matches.

```json
[ { "uuid": "...", "name": "Blood pressure", "datatype": "NM", "conceptClass": "Finding" } ]
```

---

## Device administration (`Manage CHW Devices`)

### `GET /devices`

```json
[
  {
    "uuid": "9f1c...",
    "userId": 7,
    "username": "chwsync.amelia",
    "name": "Amelia's phone",
    "platform": "android",
    "appVersion": "1.0.0",
    "active": true,
    "dateCreated": "2026-09-30T08:00:00Z",
    "lastSeen": "2026-10-14T09:15:00Z"
  }
]
```

### `POST /devices/{uuid}/deactivate`

Deactivates the device and revokes every token bound to it. `204`. This is what cutting off a lost
handset means.

### `POST /devices/{uuid}/activate`

Returns a device to service. Previously revoked tokens stay revoked, so the user logs in again to get
a new one. `204`.

---

## Record payloads

These are the shapes inside a change's `data` (pull) and a push item's `payload`. Every record has
`uuid`, `version` (server-assigned; omit on push) and `voided`.

### patient

Folded: names, addresses, identifiers and attributes travel with the patient.

```json
{
  "uuid": "a34d...",
  "voided": false,
  "gender": "F",
  "birthdate": "1994-03-02",
  "birthdateEstimated": false,
  "dead": false,
  "deathDate": null,
  "causeOfDeath": null,
  "causeOfDeathNonCoded": null,
  "names": [
    { "uuid": "...", "givenName": "Amelia", "middleName": null, "familyName": "Nsubuga",
      "familyName2": null, "prefix": null, "familyNamePrefix": null,
      "familyNameSuffix": null, "preferred": true }
  ],
  "addresses": [
    { "uuid": "...", "address1": null, "address2": null, "cityVillage": "Kasese",
      "countyDistrict": null, "stateProvince": null, "country": null,
      "postalCode": null, "latitude": null, "longitude": null, "preferred": true }
  ],
  "identifiers": [
    { "uuid": "...", "identifier": "CHW-0001", "identifierType": "<uuid>",
      "location": "<uuid>", "preferred": true }
  ],
  "attributes": [
    { "uuid": "...", "attributeType": "<uuid>", "value": "..." }
  ]
}
```

On push, a nested item with a `uuid` is matched and updated; without one it is created. A nested item
present on the server but absent from the payload is **voided**, not deleted.

### visit

```json
{
  "uuid": "...",
  "voided": false,
  "patient": "<uuid>",
  "visitType": "<uuid>",
  "location": "<uuid>",
  "indication": "<uuid|null>",
  "startDatetime": "2026-10-14T09:00:00Z",
  "stopDatetime": null
}
```

### encounter

Observations are nested inside the encounter; there is no separate obs record type. Complex obs are
not carried.

```json
{
  "uuid": "...",
  "voided": false,
  "patient": "<uuid>",
  "encounterType": "<uuid>",
  "location": "<uuid>",
  "form": "<uuid|null>",
  "visit": "<uuid|null>",
  "encounterDatetime": "2026-10-14T09:05:00Z",
  "providers": [ { "provider": "<uuid>", "role": "<uuid>" } ],
  "obs": [
    {
      "uuid": "...",
      "concept": "<uuid>",
      "obsDatetime": "2026-10-14T09:05:00Z",
      "person": "<uuid|null>",
      "location": "<uuid|null>",
      "comment": null,
      "accessionNumber": null,
      "valueNumeric": 120,
      "valueText": null,
      "valueCoded": null,
      "valueDatetime": null,
      "valueBoolean": null,
      "valueDrug": null,
      "voided": false,
      "groupMembers": []
    }
  ]
}
```

### household

```json
{
  "uuid": "...",
  "voided": false,
  "name": "Nsubuga household",
  "village": "Kasese",
  "location": "<uuid|null>",
  "headPatient": "<uuid|null>",
  "members": [
    { "patient": "<uuid>", "relationship": "Spouse", "headOfHousehold": false }
  ]
}
```

### appointment

```json
{
  "uuid": "...",
  "voided": false,
  "patient": "<uuid>",
  "provider": "<uuid|null>",
  "location": "<uuid|null>",
  "appointmentDate": "2026-11-01T10:00:00Z",
  "reason": "Antenatal follow-up",
  "status": "SCHEDULED",
  "notes": null
}
```

### referral

```json
{
  "uuid": "...",
  "voided": false,
  "patient": "<uuid>",
  "referralDate": "2026-10-14T09:20:00Z",
  "facility": "Kasese Regional Referral",
  "reason": "Suspected pre-eclampsia",
  "urgency": "URGENT",
  "status": "PENDING",
  "notes": null
}
```
