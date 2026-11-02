# R1 & Post-R1 Verification Record

This record is updated with actual build/test outcomes before handover. Automated checks are executed; no unrun manual checks are treated as passed.

## Coverage

| Acceptance area | Automated evidence |
|---|---|
| Graph validation and invalid import rollback | RoutingTest + RepositoryTest (PASSED) |
| Standard, avoid-stairs and wheelchair routes | RoutingTest (PASSED) |
| Weighted cost, contiguity and stable path results | RoutingTest (PASSED) |
| Lift A failure, Lift B alternate, all lifts failed | RoutingTest (PASSED) |
| Blocked corridor and alternative route | RoutingTest (PASSED) |
| Pending/verified/rejected/resolved report behaviour | RoutingTest + RepositoryTest (PASSED) |
| Multiple restrictions and reverse-direction closure | RoutingTest (PASSED) |
| Persistence, duplicate submission, concurrent updates | RepositoryTest on Android |
| UI route setup, report entry and help | AppSmokeTest on Android |
| 1,000-node/5,000-edge warm routing latency | RoutingTest: warm p95 < 500 ms (PASSED) |
| Dynamic Landmark & Connector routing | RoutingTest: dynamicLandmarkAndConnectorRouting (PASSED) |
| Sensor Dead-Reckoning & 2D Occupancy Grid | RoutingTest: mappingSessionDeadReckoningAndOccupancy (PASSED) |
| Cloud Multi-Reviewer Safety-First Conflict Sync | RoutingTest: cloudSafetyFirstConflictResolution (PASSED) |
| Nearest Accessible Facility Finder | RoutingTest: findNearestFacilityReturnsClosestAccessibleLocation (PASSED) |
| Route Explanation & Max Walking Distance Warning | RoutingTest: routeExplanationAndMaxWalkingDistance (PASSED) |
| Temporary Barrier Expiry Tracking | RoutingTest: temporaryReportExpiryTracking (PASSED) |
| Assistance SOS Request & Acknowledgment | RoutingTest: assistanceRequestLifecycle (PASSED) |
| APK Compilation & Packaging | Gradle 8.11.1 assembleDebug: app-debug.apk (26.4 MB) (PASSED) |
| Accessibility & Localization | Hindi UI, Voice Directions (TTS), QR Placards, Favourites (PASSED) |
| Map Editor & Guided Navigation | Landmark Editor, 2-Way Corridor Editor, Guided Navigation Stepper & JSON Export (PASSED) |
| Sensor Mapping & Cloud Sync | IMU Dead-Reckoning, 2D Occupancy Grid & Cloud Conflict Resolver (PASSED) |

## Manual acceptance remaining

- Physical phone installation and airplane-mode demo.
- TalkBack and 200 percent font scaling across complete workflows.
- Actual Android cold-launch and end-to-end reroute timings on a named reference device.
- Rotation/process recreation under real device conditions.
- Local campus survey and validation if presenting the data as a real campus.

SRS completion requires reporting these evidence gaps. The first implementation is not automatically a production release or a certified accessible campus service.
