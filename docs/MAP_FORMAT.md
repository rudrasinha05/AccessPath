# Campus map format

Use `app/src/main/assets/maps/demo-campus.json` as a complete example. Import a map in **Settings → Map data → Import map**. Schema version `1`. Maximum file size 10 MB; at most 10,000 places and 50,000 directed paths. Fields the app does not know are ignored; invalid values are rejected and the stored map stays unchanged.

| Field | Contract |
|---|---|
| `version`, `name`, `provenance` | Required non-empty strings. Say whether the data is illustrative or surveyed. The value `Illustrative demo` marks the resettable sample map. |
| `verifiedAt` | Optional UTC epoch milliseconds of the field survey |
| `buildings` | Unique `id`, `name` |
| `floors` | Unique `id`, existing `buildingId`, `label` (shown to people, e.g. "Second floor"), integer `ordinal` |
| `nodes` (places) | Unique `id`, existing `floorId`, `name`, `x`, `y`, optional `type` |
| `edges` (paths) | Unique `id`, existing distinct `from`/`to`, `type`, non-negative `length` |
| `facilities` | Unique `id`, `name`, `availability`, `reason`, `updatedAt`, `source` |

Coordinates and lengths are metres in a floor-local frame (0–10,000). Paths are **directed**: add a reverse path for two-way travel. Path types: `CORRIDOR`, `OUTDOOR`, `DOOR`, `RAMP`, `STAIRS`, `ELEVATOR`. Paths between floors of one building must be stairs, ramps or lifts. Paths between buildings must be `OUTDOOR` and join floors with the same ordinal.

Place `type` values with meaning: `ENTRANCE`/`EXIT` (nearest exit), `WASHROOM` (nearest washroom), `ELEVATOR` (lift landing), `LANDMARK` (corridor point; label shown only when zoomed in), `ROOM`.

**Wheelchair metadata:** `width` in metres, `slope` in percent grade, `stepFree` boolean, `wheelchairSuitable` boolean on lifts. Leave unknown values out (or `null`); never use 0 to mean unknown. Wheelchair routes need known width and step-free status; slope is checked on ramps and suitability on lifts. Lift paths must reference a `facilityId`.

`availability`: `AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`. `restricted: true` excludes a path for everyone. Optional `multiplier` and `penalty` override the routing cost; only distance is shown to people.

A disconnected map is accepted with a warning. Active reports must point at path IDs that still exist in the new map; resolve them first if paths were renamed.

## Sample map IDs

Places: `gate`, `classroom`, `library`, `washroom`, `unknown` (missing wheelchair data), `isolated` (not connected). Facilities: `liftA`, `liftB`. Paths: `hall2`/`hall2_r` (direct second-floor corridor), `south2` (alternative), `liftA_0`, `liftA_1` (Lift A).
