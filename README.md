# AccessPath

Offline, step-free campus navigation for Android. AccessPath finds routes that respect a person's mobility needs, explains why when no route exists, and lets people report barriers that close a path until a reviewer checks it.

No account, no internet and no location permission are needed. Everything stays on the phone.

## What it does

| Tab | What you can do |
|---|---|
| **Navigate** | Choose where you are and where you want to go. Pick a mobility setting (walking, avoid stairs, wheelchair with minimum width and maximum ramp slope, optional maximum distance). See the route on a zoomable floor plan and as numbered text directions. Confirm each point as you reach it; the route recalculates from there. Jump to the nearest reachable washroom, working lift or exit. Have directions read aloud. |
| **Reports** | Report a barrier on any path (blocked, lift not working, damaged ramp, too narrow, step, other). A new report closes that path in both directions for everyone, as a precaution, until a reviewer confirms or rejects it. Confirmed barriers stay closed until marked fixed. |
| **Manage** | Reviewer mode: confirm, reject or resolve reports, set lift status (working / out of service / unknown) with a reason, temporarily close or reopen paths, and see the activity log. Demo controls for the sample campus: all lifts down, restore, reset. |
| **Settings** | Language (phone default, English, हिन्दी). Map details, import and export of map JSON, location-link explanation, and the app's limits. |

**Location links:** every place has a link such as `accesspath://node/lobby0`. Print it as a QR code at that spot; scanning it with the phone camera opens AccessPath and confirms the person's position.

### Routing rules

- Only paths that match the mobility setting are used. AccessPath never relaxes the setting to show a fallback.
- Unknown width, slope, step-free status or lift suitability counts as **not accessible** for wheelchair routes.
- A lift with status *unknown* or *out of service* is not used by anyone.
- Pending and confirmed reports close both directions of a path; a lift report closes that lift on every floor. Several closures on one path are independent: resolving one does not reopen the others.
- Cost = length × multiplier (ramp 1.5, stairs 2, otherwise 1) + 20 for each lift ride. The cost picks the route; only real distance is shown to people.
- When there is no route, the app says why: the places aren't connected in the map, something is closed right now (and what), or the mobility setting excludes every path.

## Build and run

Requirements: Android Studio Ladybug (2024.2.1) or newer, **JDK 17**, Android SDK Platform 35 and Build Tools 35.0.0. Open the **repository root** (not the `app` folder) and run the `app` configuration on Android 10 (API 29) or newer.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug     # unit tests, lint, debug APK
./gradlew connectedDebugAndroidTest                      # needs an emulator or phone
./gradlew assembleRelease                                # minified release APK
```

On Windows use `.\gradlew.bat` with the same tasks.

### Signed release

The release build is minified with R8. It is signed only when these environment variables are set (never commit a keystore):

| Variable | Meaning |
|---|---|
| `ACCESSPATH_KEYSTORE` | Path to the `.jks` keystore |
| `ACCESSPATH_KEYSTORE_PASSWORD` | Keystore password |
| `ACCESSPATH_KEY_ALIAS` | Key alias |
| `ACCESSPATH_KEY_PASSWORD` | Key password |
| `ACCESSPATH_VERSION_CODE` | Optional; increase for every store upload |

Without them, Gradle produces `app-release-unsigned.apk`.

### Continuous integration

GitHub Actions runs unit tests, lint, debug and release builds on every push, plus instrumentation tests on API 29 and API 35 emulators. APKs, the R8 mapping file and test reports are uploaded as workflow artifacts.

## Project layout

| Path | Purpose |
|---|---|
| `app/src/main/java/com/accesspath/domain` | Plain Kotlin: map model and validation, routing, route steps, report rules |
| `app/src/main/java/com/accesspath/data` | Room storage: one versioned state row, transactional compare-and-set updates |
| `app/src/main/java/com/accesspath/ui` | Jetpack Compose screens, floor plan, view model, text-to-speech |
| `app/src/main/res/values*/strings.xml` | English and Hindi text |
| `app/src/main/assets/maps/demo-campus.json` | Illustrative sample campus |
| `app/src/test`, `app/src/androidTest` | Unit tests; storage and UI tests on a device |
| `docs/` | Map format, implementation notes, verification record, backlog |

## Honest limits

- The bundled campus is **illustrative**, not a survey of a real campus. Import surveyed data ([map format](docs/MAP_FORMAT.md)) before relying on it.
- Reports and lift statuses live on one phone. There is no shared server, so two phones do not see each other's reports.
- Reviewer mode is a local role, not a secure login.
- Position is what the person confirms (by tapping or scanning a location link). There is no live indoor positioning.
- Target SDK is 35. Google Play's yearly target-API requirement may need a newer SDK and toolchain before store publication; see [docs/BACKLOG.md](docs/BACKLOG.md).

See [docs/VERIFICATION.md](docs/VERIFICATION.md) for what is tested automatically and what still needs checking on a real phone.
