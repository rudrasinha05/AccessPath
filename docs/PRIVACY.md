# AccessPath privacy notes

Plain summary of what the app uses, why, and where it goes. Use it as the base of the public privacy policy and the Google Play data-safety form.

| Data | When | Why | Where it goes |
|---|---|---|---|
| Precise location | Only after the person allows it: unmapped-area check at start, "show my location", outdoor directions and navigation | To know whether the area is mapped, to show the blue dot and to plan and follow outdoor routes | Stays on the phone. Start and end points of an outdoor route are sent to the routing service (OSRM by FOSSGIS, or openrouteservice.org for wheelchair routes) to get the route. |
| Background location | Only if the person turns on Nearby alerts and chooses "Allow all the time" | Android geofences: alerts when entering a campus area, a closed path or a broken lift | Checked by Android/Google Play services on the phone. AccessPath does not store or upload it. |
| Camera | Only during camera mapping, after permission | ARCore motion tracking to record the walked path | No photos or video are saved. The recorded path and place names become a draft map. |
| Draft maps (walked path, place names) | When saved; shared when signed in | So every phone gets the new area | Supabase project of the campus, readable by app users. |
| Barrier reports (path, category, description) | When the person reports | So reviewers can check and other people avoid the path | Supabase, linked to the account that sent it. |
| Account (email, Google name/ID token) | Only if the person signs in | Reviewer and admin roles; sharing reports and drafts | Supabase Auth. |
| Search text | When searching world places | Geocoding | MapTiler. |
| Map tiles | While the map is shown | Background map | MapTiler (or OpenStreetMap if no key). |

Not collected: contacts, photos, advertising ID, analytics, crash reports (none are built in yet).

Deleting data: signing out removes the session from the phone; uninstalling removes all local data. Deleting a server account needs an admin (Supabase dashboard) until a self-service delete is added.
