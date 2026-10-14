# CHW Android app

The native client for the CHW sync module. Kotlin, Jetpack Compose, and an offline-first local store.
It speaks the same `<server>/ws/chwsync/v1` contract as the server module and the PWA, so it can be used
interchangeably with either.

## What it does

- Sign in once with an OpenMRS username and password; the server returns a bearer token, which is
  kept in DataStore. The password is never stored.
- Register and edit patients, with names, addresses, identifiers and attributes.
- Open visits, capture encounters and vitals/observations, schedule appointments, make referrals, and
  manage households and their members.
- Work entirely offline against Room. Changes queue in an outbox.
- Sync in the background with WorkManager, and on demand from the Sync screen. Conflicts are surfaced
  in the UI rather than silently resolved.

## Modules and libraries

| Concern | Choice |
| --- | --- |
| UI | Jetpack Compose (Material 3), Navigation Compose |
| Local store | Room (KSP), with `Outbox` and `Conflict` tables beside the record tables |
| Wire | Retrofit + OkHttp, kotlinx.serialization |
| Session | DataStore Preferences |
| Background sync | WorkManager |
| Min / target SDK | 26 / 34 |

## Layout

```
app/src/main/java/org/openmrs/chwsync/android/
  ChwSyncApplication.kt        Application; builds the AppContainer
  MainActivity.kt          Single activity hosting the Compose nav graph
  di/                      AppContainer: hand-wired dependencies
  data/
    local/                 Room entities, DAOs, database, type converters
    model/                 Domain entities and record <-> payload mappers
    remote/                Retrofit API, wire DTOs, error type, client factory
    repo/                  AuthRepository, RecordRepository, ReferenceRepository
    Serialization.kt       Shared Json configuration
  sync/                    SyncEngine (pull/push/conflict) and SyncWorker
  ui/                      Screens, view models, nav graph, theme, form components
  util/                    Uuid, DateTime, NetworkMonitor
```

## Building

The app targets Android SDK 34 and Java 17.

```bash
cd android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # if not already set
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

`./gradlew :app:assembleDebug` has been run against Android SDK 34 and produces a working debug APK.

## Configuration

The server base URL is entered on the login screen and stored with the session, so one build can
point at any deployment. No server URL is baked into the APK.

## Sync behaviour

`SyncEngine` mirrors the server's rules: pull page by page following the cursor, push the outbox with
a stable `clientChangeId` per change, and store `CONFLICT` results in the `Conflict` table for the UI
to present. Retries are safe because a replayed `clientChangeId` is recognised by the server.
