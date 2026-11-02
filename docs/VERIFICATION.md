# R1 verification record

This record is updated with actual build/test outcomes before handover. Automated checks are being executed; no unrun manual checks are treated as passed.

## Coverage

| Acceptance area | Automated evidence |
|---|---|
| Graph validation and invalid import rollback | RoutingTest + RepositoryTest |
| Standard, avoid-stairs and wheelchair routes | RoutingTest |
| Weighted cost, contiguity and stable path results | RoutingTest |
| Lift A failure, Lift B alternate, all lifts failed | RoutingTest |
| Blocked corridor and alternative route | RoutingTest |
| Pending/verified/rejected/resolved report behaviour | RoutingTest + RepositoryTest |
| Multiple restrictions and reverse-direction closure | RoutingTest |
| Persistence, duplicate submission, concurrent updates | RepositoryTest on Android |
| UI route setup, report entry and help | AppSmokeTest on Android |
| 1,000-node/5,000-edge warm routing latency | RoutingTest; this JVM result is not an Android UI timing result |

## Manual acceptance remaining

- Physical phone installation and airplane-mode demo.
- TalkBack and 200 percent font scaling across complete workflows.
- Actual Android cold-launch and end-to-end reroute timings on a named reference device.
- Rotation/process recreation under real device conditions.
- Local campus survey and validation if presenting the data as a real campus.

SRS completion requires reporting these evidence gaps. The first implementation is not automatically a production release or a certified accessible campus service.
