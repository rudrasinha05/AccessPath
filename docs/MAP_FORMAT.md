# Campus map contract

Use `app/src/main/assets/maps/demo-campus.json` as a complete example. Import JSON through Settings. Supported schema version is `1`; unknown fields and invalid values are rejected. Maximum file size: 10 MB; maximum 10,000 nodes and 50,000 directed edges.

| Field | Contract |
|---|---|
| `version`, `name`, `provenance` | Required nonempty strings; say whether illustrative or surveyed |
| `buildings` | Unique `id`, `name` |
| `floors` | Unique `id`, valid `buildingId`, `label`, integer `ordinal` |
| `nodes` | Unique `id`, valid `floorId`, `name`, `x`, `y`, optional `type` |
| `edges` | Unique `id`, existing distinct `from`/`to`, `type`, nonnegative `length` |
| `facilities` | Unique `id`, `name`, `availability`, `reason`, `updatedAt`, `source` |

Coordinates and lengths are metres; coordinates must be finite and within 0–10,000 in a floor-local coordinate frame. An edge is **directed**; add a separate reverse edge for bidirectional travel. Allowed types: CORRIDOR, OUTDOOR, DOOR, RAMP, STAIRS, ELEVATOR. Edges crossing floors within one building must be stairs, ramps or elevators. Cross-building links must be OUTDOOR with matching floor ordinals.

Wheelchair metadata: `width` in metres, `slope` in percent grade, `stepFree` boolean and `wheelchairSuitable` boolean on elevators. Use null/omit for unknown values, not zero. Wheelchair routes require known width and step-free status; ramp slope and lift suitability are checked on their respective segment types. Elevator edges must reference a valid `facilityId`.

Status values: AVAILABLE, UNAVAILABLE, UNKNOWN. Timestamps are UTC epoch milliseconds. `verifiedAt` is optional. `restricted: true` excludes an edge. Optional `multiplier` and `penalty` override the nonnegative score coefficients; the app displays score separately from distance. Elevator connector length is explicit and is not inferred from 2D coordinates.

For a new building, supply its own floors and nodes, then inter-building edges where appropriate. Disconnected graphs produce a warning and are supported. Existing report edge IDs must remain present when importing a replacement. For now, fully different maps should be evaluated in a fresh app data installation; no silent report deletion or remapping occurs.

## Known demo IDs

`gate`, `classroom`, `library`, `washroom`, `unknown`, `isolated`: useful destination nodes. `liftA`/`liftB`: facilities. `hall2`/`hall2_r`: direct second-floor corridor. `south2`: alternative second-floor corridor. `liftA_0` and `liftA_1`: Lift A floor connectors.
