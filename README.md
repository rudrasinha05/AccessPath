# AccessPath

Offline, step-free campus navigation for Android. AccessPath finds routes that respect a person's mobility needs, explains why when no route exists, and lets people report barriers that close a path until a reviewer checks it.

It looks and works like Google Maps: a real-world map (OpenStreetMap) with the campus drawn on top, a search bar, a bottom panel for places and directions, and turn-by-turn steps. Where the campus isn't mapped yet, it can record a draft map with the phone camera (ARCore).

No account is needed and all data stays on the phone. Internet is only used for the world-map background; routing works offline.

## What it does

| Tab | What you can do |
|---|---|
| **Explore** | Full-screen map with the campus on top: building outlines, paths, places, floor picker, your confirmed position (blue dot) and destination (red pin). Search places, or tap Washroom / Lift / Exit for the nearest reachable one. A place card shows how far it is with your mobility setting. **Directions** shows the route in blue with walking / avoid-stairs / wheelchair modes; **Start** gives turn-by-turn steps with an "I'm at …" button that confirms each point. Read-aloud is available. |
| **Contribute** | Report a barrier on any path. A new report closes that path in both directions for everyone, as a precaution, until a reviewer confirms or rejects it. |
| **You** | Mobility settings, **map a new area with the camera**, the unmapped-area check, language (phone default, English, हिन्दी), map import/export, and **Campus management** (reviewer mode, lift status, temporary closures, camera drafts, demo controls, activity log). |

**Camera mapping:** when the app opens it asks once whether it may check the location. With a good GPS fix (40 m or better) outside every mapped area, it offers to map the area with the camera; inside mapped areas it never asks. Mapping uses ARCore to record the walked path and lets the surveyor name places (room, washroom, lift, entrance, stairs). The result is saved as a **draft** area: width, slope and steps can't be measured from a walk, so wheelchair routes skip draft paths until a reviewer checks them. Reviewers can delete drafts.

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
- Position is what the person confirms (by tapping or scanning a location link). GPS is used only once at app start to decide whether the area is mapped; there is no live indoor positioning.
- Camera mapping needs an ARCore-supported phone. The draft's real-world placement uses the starting GPS fix and compass, so it can be off by several metres; drafts are not accessibility-verified.
- The sample campus is placed approximately in Knowledge Park III, Greater Noida, for the demo. Its layout is illustrative.
- Target SDK is 35. Google Play's yearly target-API requirement may need a newer SDK and toolchain before store publication; see [docs/BACKLOG.md](docs/BACKLOG.md).

See [docs/VERIFICATION.md](docs/VERIFICATION.md) for what is tested automatically and what still needs checking on a real phone.
