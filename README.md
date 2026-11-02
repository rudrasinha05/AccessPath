# AccessPath Android

Accessible campus navigation with profile-aware routes, facility failures, barrier reports and an in-app review workflow. This is the R1 faculty hackathon implementation; automatic camera/sensor mapping belongs to R2.

## Open in Android Studio

1. Clone `https://github.com/rudrasinha05/AccessPath.git` or pull the latest `main`.
2. Open the **repository root**, not the `app` folder.
3. Use Android Studio Ladybug 2024.2.1 or newer with support for AGP 8.7.3. Set **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** to **JDK 17**.
4. Install **Android SDK Platform 35**, **Build Tools 35.0.0**, and **Platform Tools** in SDK Manager. Let Gradle sync finish.
5. Select the `app` run configuration and an Android 10/API 29 or newer emulator/phone, then Run.

The app needs **no API key, cloud account, Google Maps service or runtime permission**. Internet is needed to download build dependencies; the installed app works offline. `local.properties` is machine-specific and Android Studio creates it locally.

On Windows:

```powershell
git pull origin main
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS/Linux:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also publishes the debug APK and verification reports as workflow artifacts. It is a demo/debug build, not a Play Store signed release.

## Demonstrate all six email requirements

1. **Graph and map:** Navigate shows a 31-node, 72-directed-edge campus with two buildings and three floors in the main building. Pan/zoom the map and select floors.
2. **Weighted costs:** Routes use distance × segment multiplier plus a lift waiting penalty. Ramps cost 1.5×, stairs 2×, other segments 1×; a lift connector adds 20 equivalent metres. The score is **not an ETA**.
3. **Accessibility:** Plan Main gate → Classroom 204 with Standard mobility, then Wheelchair. Standard uses the short stair route; Wheelchair excludes stairs and checks metadata. The default wheelchair route uses Lift A.
4. **Rerouting:** Manage → enable Local demo reviewer → Update Lift A → Unavailable, give a reason. Navigate automatically changes to Lift B. Recalculation starts at the **last confirmed node**, not inferred GPS position.
5. **Reports and verification:** Reports → Report a barrier → choose a segment → give a description. Pending reports conservatively restrict the segment in both directions. In reviewer mode, verify/reject the report and later resolve verified barriers. Every decision is audited. Resolving one report does not clear other restrictions.
6. **Simulated failures:** Manage → Simulate all lifts unavailable → Navigate displays no accessible route. Restore lifts. Block a corridor to inspect an alternate path. Reset demo requires confirmation.

Try Library, Accessible washroom, the disconnected Closed annex, and Unverified studio (unknown wheelchair metadata). Confirm intermediate locations using the route steps. Force-close/reopen: profiles, reports and status persist. Repeat in airplane mode.

## Scope and truthful limitations

- The bundled map is **illustrative**, not a surveyed GLBITM campus. Replace it with validated local data before presenting actual campus coverage.
- Local role switching demonstrates verification; it is **not secure authentication**. There is no shared backend or cross-device synchronization.
- No live indoor positioning, autonomous wheelchair control, camera capture or automatic SLAM mapping is claimed in R1.
- Only known, profile-compatible segments are eligible. The app never relaxes wheelchair requirements to show stairs as a fallback.
- Unknown areas/measurements are not certified accessible. User width/slope preferences and supplied map data need appropriate real-world verification.
- Status is local and timestamped, not a live building-management feed.

## Project layout

| Path | Purpose |
|---|---|
| `app/src/main/java/com/accesspath/domain` | Pure Kotlin model, validation, Dijkstra, cost and report rules |
| `app/src/main/java/com/accesspath/data` | Room persistence, atomic mutations, optimistic revision checks |
| `app/src/main/java/com/accesspath/ui` | Compose map, route steps, reporting, review and settings |
| `app/src/main/assets/maps/demo-campus.json` | Replaceable documented demo topology |
| `app/src/test` | Routing, validation, state-rule and graph-performance unit tests |
| `app/src/androidTest` | Room persistence/concurrency and Compose smoke tests |
| `docs` | SRS, data contract, acceptance coverage and verification status |

## Build versions

AGP **8.7.3**, Gradle **8.9**, JDK **17**, Kotlin **1.9.24**, Compose compiler **1.5.14**, Compose BOM **2024.10.01**, Room **2.6.1**. Minimum SDK **29**, compile/target SDK **35**. These are pinned for this reproducible demo, not a statement of latest versions or Play publication compliance.

If sync fails, check the JDK and installed SDK before changing versions. Do not upgrade just AGP, Kotlin or the Compose compiler independently. The complete Gradle wrapper is committed. Proxy/network failures during dependency download require the local network/proxy configuration, not changes to app logic.

## Test and review

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

The second command needs a running emulator or connected phone. CI targets API 29 and API 35. Consult [`docs/VERIFICATION.md`](docs/VERIFICATION.md) for actual results and outstanding manual checks; test existence alone does not mean it has passed.

The initial SRS is [`docs/AccessPath_Android_SRS_v1.docx`](docs/AccessPath_Android_SRS_v1.docx). Implementation details and any deliberate simplifications are recorded in [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md). Future work stays in [`docs/BACKLOG.md`](docs/BACKLOG.md).
