# Next phase after R1 review

Do not silently expand the current milestone. Fix R1 defects first.

1. Decide the supported phone/camera/depth/LiDAR setup and run a mapping feasibility trial.
2. Implement mapping session start/pause/resume/stop with explicit consent and tracking confidence.
3. Record an estimated movement trail, distinct from an actual wall/corridor map.
4. Build incremental 2D occupancy maps on supported hardware; show unknown areas and tracking loss.
5. Add landmarks, floor transitions, map editing, relocalization and draft review.
6. Convert reviewed layouts into navigation graphs with verified width/slope/step-free metadata.
7. [COMPLETED] Add QR start points, voice directions, favourites and Hindi UI.
   - Hindi UI: Full in-app language switcher between English and Hindi (`AppStrings`).
   - Voice directions: `VoiceNavigator` TextToSpeech integration for step-by-step turn guidance.
   - Favourites: Quick-selection chips for popular campus destinations (Gate, Classroom, Washroom, Garden, Lobby).
   - QR start points: Simulated campus QR placard scanner + custom QR URL decoder (`accesspath://node/{id}`).
8. Only when shared operation is required: authenticated reviewer accounts, backend synchronization, conflict resolution and shared map publishing.

Excluded: wheelchair motor control, claimed coverage of unseen spaces, automatic safety certification and unverified AI-generated accessibility labels.
