# Backlog

Ordered roughly by value for a real deployment.

1. **Surveyed campus data.** Survey the real campus (widths, slopes, step-free status, lift suitability) and import it. The sample map must not be presented as a real campus.
2. **Shared backend.** A server so reports, lift status and closures are shared across phones, with per-entity storage and conflict handling.
3. **Real reviewer accounts.** Sign-in for estates/facility staff with roles, replacing the local reviewer switch.
4. **Toolchain upgrade for Play Store.** Move to a newer Android Gradle Plugin, Kotlin 2.x with the Compose compiler plugin, and the target SDK that Google Play requires at publication time. Test on the matrix in CI before release.
5. **Device accessibility pass.** TalkBack walk-through of every workflow, 200 % font size, switch access, colour contrast check on real screens.
6. **Location QR placards.** Generate printable QR sheets from the map (`accesspath://node/<id>`).
7. **In-app map editing for surveyors**, if JSON import proves too hard for the estates team.
8. **Indoor positioning** (e.g. BLE beacons) as an optional aid, always with manual confirmation as a fallback.

Out of scope: wheelchair motor control, automatic accessibility certification, AI-generated accessibility labels for unseen spaces.
