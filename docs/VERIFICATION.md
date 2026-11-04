# Verification record

Automated checks run in GitHub Actions on every push (`.github/workflows/android.yml`). A test that exists is not evidence on its own; check the latest workflow run for its result.

## Automated

| Area | Evidence |
|---|---|
| Map validation, invalid map rejection | `RoutingTest` (unit) |
| Walking / avoid-stairs / wheelchair routes, width, slope, unknown metadata | `RoutingTest` |
| Extra distance vs. stairs route, maximum-distance warning | `RoutingTest` |
| Lift A failure → Lift B, all lifts down → "closed right now" | `RoutingTest` |
| Closed corridor → alternative, reports close both directions and whole lift | `RoutingTest` |
| Pending / confirmed / rejected / fixed report rules and transitions | `RoutingTest` |
| "Not connected" vs "closed now" vs "settings too strict" explanations | `RoutingTest` |
| Nearest washroom / working lift / exit | `RoutingTest` |
| Directions merge lift and stair segments | `RoutingTest` |
| Saved data from the previous app version still loads | `RoutingTest` |
| 1,000 places / 5,000 paths warm routing p95 < 500 ms | `RoutingTest` |
| Atomic review + audit, idempotent submit, stale update refused, parallel writes | `RepositoryTest` (device) |
| Confirm position, close/reopen path, import/reset rules, unreadable data recovery, persistence across reopen | `RepositoryTest` (device) |
| App opens with no permission prompt; settings dialog; report form; Manage and Settings tabs | `AppSmokeTest` (device, API 29 and 35) |
| Map position of the sample campus, geo conversion | `RoutingTest` |
| Mapped vs unmapped area check; camera survey → draft paths with unknown accessibility; wheelchair routes skip drafts | `RoutingTest` |
| Draft area add / idempotent / delete | `RepositoryTest` (device) |
| Map opens, search, place card, directions → navigation → exit, Contribute, You → Campus management | `AppSmokeTest` (device) |
| Lint (incl. missing translations), debug build, R8 release build | CI `build` job |

## Still to check by hand on a real phone

- Install the release APK; use the app in airplane mode.
- TalkBack through every tab, 200 % font size, dark mode.
- Hindi: switch language in Settings; check text fits on small screens.
- Read aloud with English and Hindi voices installed and without them.
- Scan a QR code of `accesspath://node/lobby0` with the camera app.
- Camera mapping on an ARCore phone: walk a corridor, add places, finish, check the draft appears on the map in the right place. CI emulators have no ARCore, so this is not tested automatically.
- Unmapped-area prompt outdoors away from the campus (and no prompt inside it).
- Import a surveyed map and walk a route on the real campus.

The sample campus is illustrative. Passing these checks does not certify any real building as accessible.
