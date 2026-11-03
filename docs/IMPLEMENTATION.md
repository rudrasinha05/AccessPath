# Implementation notes

## State and storage

All app state (map, profile, position, reports, closures, audit log) is one `AppSnapshot`, saved as JSON in a single Room row with a revision number. Every change runs in a Room transaction that compares and sets the revision, so a report decision and its audit entry are always saved together. Reviewer decisions pass the revision the reviewer was looking at; if the data changed in the meantime the decision is refused with a "check the latest data" message.

The audit log keeps the latest 500 events. Saved data from earlier app versions loads: unknown fields are ignored and old free-text report categories are mapped to the new categories. If saved data cannot be read at all, the app restores the bundled map and records that in the activity log instead of crashing.

This single-row design suits an offline, single-phone app. A multi-user deployment needs a server with per-entity tables and real authentication; the domain layer has no Android dependencies so it can move to a backend unchanged.

## Routing

`Routing.calculate` runs Dijkstra with adjacency lists and a priority queue; ties break by node ID so results are stable. Exclusions are computed first, each with a reason. Temporary closures (reports, closed paths, lifts out of service or unknown) are separated from profile limits (stairs, width, slope, lift suitability, restricted). When no route exists, two more searches decide the explanation: with closures ignored (→ "closed right now") and with everything allowed (→ "not connected" vs "settings too strict").

For non-walking profiles the app also computes the walking route to show how much longer the step-free route is. `Routing.nearest` runs one search from the current position and picks the closest washroom, working lift or exit.

`RouteSteps.build` turns a route into instructions, merging consecutive lift or stair segments ("Take Lift A to Second floor").

## Screens

- **Navigate**: route card, nearest-place chips, floor plan (Compose `Canvas` with pinch zoom, pan and tap), numbered directions. The first step has an "I'm at …" button that confirms the position. The floor plan has a content description; the text directions are the accessible equivalent.
- **Reports**: report form with category, path picker (paths on the current route listed first) and description; report list with reviewer actions.
- **Manage**: reviewer switch (disclosed as a local role), facilities, temporary closures, demo controls (sample map only), activity log.
- **Settings**: mobility settings, language via `AppCompatDelegate.setApplicationLocales`, map info, import/export through the system file picker, limits.

All text is in `strings.xml` (English) and `values-hi/strings.xml` (Hindi). Errors from the data layer carry an `ErrorCode`, which the UI turns into a translated message.

## Security and privacy

- No permissions are requested. No network code. No analytics.
- Backup and device transfer are disabled so local reports are not copied off the phone.
- Map import is limited to 10 MB and fully validated before anything is saved.
- Logs never contain report text.

## Removed from the earlier version

The previous UI imitated Google Maps and showed hardcoded data (a user name and level, weather, air quality, photo-view counts, fixed "turn left in 40 m" directions, fixed slope and lift badges). It also simulated GPS, AR scanning, cloud sync with a fixed token, assistance requests that went nowhere, and listed real institutions (IIT Delhi, AIIMS, Delhi Airport, Stanford) with invented maps and contributors. All of that was removed because it presented made-up information as real. The working features underneath (routing, reports, review, facilities, import/export) were kept and made reachable.
