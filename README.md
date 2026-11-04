# AccessPath

Campus navigation for Android, for everyone: walking, avoiding stairs, or using a wheelchair. New installs route for walking; each person can switch to avoid-stairs or wheelchair rules. AccessPath explains why when no route exists, and lets people report barriers that close a path until a reviewer checks it.

It looks and works like Google Maps: a real-world map (OpenStreetMap) with the campus drawn on top, a search bar, a bottom panel for places and directions, and turn-by-turn steps. Where the campus isn't mapped yet, it can record a draft map with the phone camera (ARCore).

The app ships with **no campus data**. Every place comes from real surveys: camera mapping on the phone, an imported map file, or a map an admin publishes. Campus routing works offline; the world map, search and outdoor directions need internet.

## What it does

| Tab | What you can do |
|---|---|
| **Explore** | Full-screen map with the campus on top: building outlines, paths, places, floor picker, compass (tap to turn north up), zoom buttons, your position (blue dot) and destination (red pin). **Long-press anywhere** to drop a pin with options: Directions, View campus, Map this area, zoom in/out. Search finds campus places first, then real-world places. Tap Washroom / Lift / Exit for the nearest reachable one. Campus **Directions** use walking / avoid-stairs / wheelchair rules; **Start** gives turn-by-turn steps with an "I'm at …" button. **Outdoor directions** to a pin offer Walk, Wheelchair, Bike, Car and Bus, then **Start** follows live GPS and re-routes when you go off the line. Read-aloud is available. |
| **Contribute** | Report a barrier on any path. A new report closes that path in both directions for everyone, as a precaution, until a reviewer confirms or rejects it. |
| **You** | Mobility settings, **map a new area with the camera**, the unmapped-area check, **Nearby alerts (geofencing)**, language (phone default, English, हिन्दी), map import/export, and **Campus management** (reviewer mode, lift status, temporary closures, camera drafts, QR placards, activity log). |

**Camera mapping:** when the app opens it asks once (a popup) whether it may check the location for unmapped areas. If allowed, and the phone has a good GPS fix (40 m or better) outside every mapped area, the camera mapping screen opens straight away; inside mapped areas it stays off. It can be turned off in Settings. Mapping uses ARCore to record the walked path and lets the surveyor name places (room, washroom, lift, entrance, stairs). The result is saved as a **draft** area: width, slope and steps can't be measured from a walk, so wheelchair routes skip draft paths until a reviewer checks them. Reviewers can delete drafts. When signed in, drafts are shared through the `surveys` table so every phone gets them.

**What the camera records:** the walked path (from ARCore motion tracking) and the places the surveyor names. It does not recognise objects or store photos or video.

**Outdoor directions:**

| Mode | Source | Notes |
|---|---|---|
| Walk, Bike, Car | Public OSRM servers run by FOSSGIS on OpenStreetMap data | No key. Fair-use service, fine for demos and light use; run your own OSRM for production. |
| Wheelchair | openrouteservice.org wheelchair profile | Needs a free key: `ORS_KEY=…` in `local.properties` and the GitHub secret `ORS_KEY`. Without it no wheelchair route is shown, because a walking route isn't checked for steps or kerbs. |
| Bus | none | There is no free public-transport routing data, so the app opens Google Maps transit directions instead. |

World search uses MapTiler Geocoding with the same `MAPTILER_KEY` as the map tiles.

**Nearby alerts (geofencing):** opt-in from You → Settings. Android (Google Play services) watches up to 100 circles and wakes the app when the phone enters one:

| Watched place | Alert | Who gets it |
|---|---|---|
| Each mapped campus area | "You're at …" | Everyone who turned alerts on |
| Each camera draft area | "You're at …" plus "paths not checked yet" | Everyone |
| A lift that is out of service | "Lift out of service nearby" | Only avoid-stairs and wheelchair settings |
| A closed path (closure or active report) | "Path closed nearby" | Everyone |

The same place alerts at most once every 6 hours. Circles are at least 100 m (Android's recommendation), so alerts mean "you're near", not room-level precision, and can arrive a minute or two late. Alerts need location **Allow all the time** and notifications; the app shows a plain disclosure first. Geofences are re-added after a restart or app update, and refreshed whenever campus data changes. The app itself never stores or uploads the location.

**Location links:** every place has a link such as `accesspath://node/lobby0`. Print it as a QR code at that spot; scanning it with the phone camera opens AccessPath and confirms the person's position.

### Routing rules

- Only paths that match the mobility setting are used. AccessPath never relaxes the setting to show a fallback.
- Unknown width, slope, step-free status or lift suitability counts as **not accessible** for wheelchair routes.
- A lift with status *unknown* or *out of service* is not used by anyone.
- Pending and confirmed reports close both directions of a path; a lift report closes that lift on every floor. Several closures on one path are independent: resolving one does not reopen the others.
- Cost = length × multiplier (ramp 1.5, stairs 2, otherwise 1) + 20 for each lift ride. The cost picks the route; only real distance is shown to people.
- When there is no route, the app says why: the places aren't connected in the map, something is closed right now (and what), or the mobility setting excludes every path.

## Campus server (Supabase) and sign-in

Shared data lives in Supabase so every phone sees the same reports, lift status, closures and map.

- **Set up once:** run `supabase/schema.sql` in the Supabase dashboard (SQL Editor). It creates the tables (including `surveys` for camera drafts), row level security, the review rules and the activity log. It is safe to re-run.
- **Sign-in:** Google (Credential Manager) or email/password, from the **You** tab. Navigation works without signing in; sharing a report needs an account.
- **Roles:** `student` (default), `reviewer`, `admin`, stored in `public.profiles` and enforced by the database. Make the first admin after they sign in once:
  `update public.profiles set role = 'admin' where email = 'you@example.com';` Admins can then promote reviewers the same way.
- **Offline first:** reports are saved on the phone and sent when online; the phone syncs on start, after sign-in, every minute and on "Sync now". Reviewer decisions, lift status and closures are written to the server first.
- **Publishing a map:** an admin taps *Publish to everyone* in Campus management; phones pick up the newer map version on their next sync.
- **Config:** the Supabase URL, publishable key and Google web client ID are public values set in `app/build.gradle.kts` (override in `local.properties`). Never put the `service_role` key in the app. The MapTiler key goes in `local.properties` as `MAPTILER_KEY=…` and in the GitHub secret `MAPTILER_KEY`; without it the app falls back to OpenStreetMap tiles.

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
| `app/src/main/assets/maps/campus.json` | Empty starting campus (no sample data) |
| `app/src/test/resources/demo-campus.json` | Test-only fixture campus used by unit and UI tests |
| `app/src/test`, `app/src/androidTest` | Unit tests; storage and UI tests on a device |
| `docs/` | Map format, implementation notes, verification record, backlog |

## Privacy

See [docs/PRIVACY.md](docs/PRIVACY.md) for what is collected, where it goes and why (also the basis for the Play Store data-safety form).

## Honest limits

- There is no bundled campus. Until someone maps the area (camera, import or a published map), only the world map, search and outdoor directions work.
- Outdoor routes come from OpenStreetMap; their quality depends on how well the area is mapped there. Outdoor wheelchair routes need `ORS_KEY`. Bus routing isn't available.
- Without a configured server the app falls back to a local reviewer switch for demos; that is not a secure login.
- Indoors, position is what the person confirms (by tapping or scanning a location link); there is no live indoor positioning. GPS is used at app start to decide whether the area is mapped, for "show my location", and continuously only while outdoor navigation is running.
- Camera mapping needs an ARCore-supported phone. The draft's real-world placement uses the starting GPS fix and compass, so it can be off by several metres; drafts are not accessibility-verified.
- Nearby alerts need Google Play services. Google Play reviews apps that use background location; the declaration form must be filled in before publishing.
- Target SDK is 35. Google Play's yearly target-API requirement may need a newer SDK and toolchain before store publication; see [docs/BACKLOG.md](docs/BACKLOG.md).

See [docs/VERIFICATION.md](docs/VERIFICATION.md) for what is tested automatically and what still needs checking on a real phone.
