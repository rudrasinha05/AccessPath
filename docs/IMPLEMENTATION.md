# R1 implementation notes

## State and persistence

The Android app uses a single Room `campus_state` aggregate containing the versioned map, profile/endpoints, facility status, reports, simulated closures and audit events. Every mutation executes in a Room transaction with a compare-and-set revision. Report decisions and their effects cannot be partially committed. No destructive migration fallback is enabled. Map imports are validated before commit and incompatible report references reject replacement.

This deliberately simplifies the SRS logical entity model into one persisted aggregate for the offline, single-device R1. **There are no separate SQL tables/foreign keys per graph entity**; reference integrity is enforced by `MapValidator` and repository operations. This is a documented physical storage deviation, not a claim of implementing the SRS's normalized SQL schema. The data/domain interfaces permit later normalization or remote synchronization without changing route eligibility rules.

## Route lifecycle

Room emits immutable state snapshots. `collectLatest` cancels obsolete work; calculation runs on `Dispatchers.Default`; results publish only while their revision is current. Changing profile, endpoint, current position, facility state or a report refreshes the route. Current position is manually confirmed and persisted.

The domain layer uses adjacency lists and a priority queue. Ties are reproducible through sorted edges and a stable node ID queue order. All costs are nonnegative. Width and slope apply to wheelchair profiles. Avoid-stairs excludes stairs but is not a substitute for a wheelchair profile. Missing required wheelchair metadata excludes an edge.

Pending and verified reports close both directions of a physical segment. Elevator reports affect every connector sharing that facility ID. Independent active reports and simulated closures are aggregated; no single resolution can clear the others. A facility marked Unknown is excluded, conservatively, for all profiles.

## Security and operational boundary

The local reviewer switch is visibly disclosed as a demo role. It does not protect records from another person using the same device. There are no network APIs or API keys. Map import uses the Android document picker and a 10 MB limit. Logs do not contain report text. Android app storage clearing removes local data; demo reset only applies to the bundled illustrative map.

## Scope discipline

Implemented first: all six functional topics in the supplied email. Deferred: sensor mapping, multi-user identity, cloud APIs and actual campus survey. The email's optional computer vision/deep learning depth is not claimed. The app exposes in-process repository APIs; it has no unnecessary HTTP service.

An actual mapped-campus deployment still requires a surveyed dataset. The supplied illustrative topology demonstrates the required behaviour but must not be described as verified GLBITM geometry. Physical device accessibility checks and timing targets remain separate evidence gates until run.
