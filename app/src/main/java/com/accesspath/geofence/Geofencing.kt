package com.accesspath.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.accesspath.MainActivity
import com.accesspath.R
import com.accesspath.domain.Fence
import com.accesspath.domain.FenceKind
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * Nearby alerts with Android geofences. Android (Google Play services) watches the circles; AccessPath is woken only
 * when the phone enters one, and posts a notification. Nothing is tracked or uploaded by the app.
 */
object Geofencing {
    private const val TAG = "Geofencing"
    private const val PREFS = "accesspath_geofence"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FENCES = "fences"
    private const val KEY_ACTIVE = "active"
    private const val CHANNEL = "nearby_alerts"
    /** The same place alerts at most once in this period. */
    private const val REPEAT_AFTER_MS = 6 * 60 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }

    enum class Status { OFF, NEEDS_LOCATION, NEEDS_BACKGROUND, NEEDS_NOTIFICATIONS, UNAVAILABLE, NOTHING_TO_WATCH, ON }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
    fun activeCount(c: Context) = prefs(c).getInt(KEY_ACTIVE, 0)

    fun playServicesAvailable(c: Context) =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS

    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    fun hasFineLocation(c: Context) = granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasBackgroundLocation(c: Context) = granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun hasNotifications(c: Context) = Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS)

    fun status(c: Context): Status = when {
        !isEnabled(c) -> Status.OFF
        !playServicesAvailable(c) -> Status.UNAVAILABLE
        !hasFineLocation(c) -> Status.NEEDS_LOCATION
        !hasBackgroundLocation(c) -> Status.NEEDS_BACKGROUND
        !hasNotifications(c) -> Status.NEEDS_NOTIFICATIONS
        activeCount(c) == 0 -> Status.NOTHING_TO_WATCH
        else -> Status.ON
    }

    private fun pendingIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, GeofenceReceiver::class.java),
        // Geofencing adds the event to the intent, so it must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    )

    /** Turns alerts on or off. Turning off removes every geofence right away. */
    suspend fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) clear(c) else register(c, savedFences(c), force = true)
    }

    /** Called whenever the campus data changes; only talks to Android when the watched places actually changed. */
    suspend fun update(c: Context, fences: List<Fence>) {
        val text = json.encodeToString(ListSerializer(Fence.serializer()), fences)
        val changed = prefs(c).getString(KEY_FENCES, null) != text
        if (changed) prefs(c).edit().putString(KEY_FENCES, text).apply()
        if (isEnabled(c) && (changed || activeCount(c) != fences.size)) register(c, fences, force = false)
    }

    fun savedFences(c: Context): List<Fence> = prefs(c).getString(KEY_FENCES, null)
        ?.let { runCatching { json.decodeFromString(ListSerializer(Fence.serializer()), it) }.getOrNull() }.orEmpty()

    @SuppressLint("MissingPermission")
    suspend fun register(c: Context, fences: List<Fence>, force: Boolean) {
        if (!isEnabled(c) || !playServicesAvailable(c) || !hasFineLocation(c) || !hasBackgroundLocation(c)) {
            prefs(c).edit().putInt(KEY_ACTIVE, 0).apply(); return
        }
        val client = LocationServices.getGeofencingClient(c)
        runCatching { client.removeGeofences(pendingIntent(c)).await() }
        if (fences.isEmpty()) { prefs(c).edit().putInt(KEY_ACTIVE, 0).apply(); return }
        val request = GeofencingRequest.Builder()
            // Arriving while already inside also counts, so someone opening alerts on campus gets the welcome.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences.map { f ->
                Geofence.Builder().setRequestId(f.id).setCircularRegion(f.lat, f.lng, f.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setNotificationResponsiveness(60_000)
                    .build()
            }).build()
        val ok = runCatching { client.addGeofences(request, pendingIntent(c)).await() }
            .onFailure { Log.w(TAG, "Could not add geofences (force=$force)", it) }.isSuccess
        prefs(c).edit().putInt(KEY_ACTIVE, if (ok) fences.size else 0).apply()
    }

    private suspend fun clear(c: Context) {
        runCatching { LocationServices.getGeofencingClient(c).removeGeofences(pendingIntent(c)).await() }
        prefs(c).edit().putInt(KEY_ACTIVE, 0).apply()
    }

    /** Posts the alert for a fence the phone just entered, unless the same place alerted recently. */
    @SuppressLint("MissingPermission") // checked by hasNotifications()
    fun alert(c: Context, fence: Fence, now: Long = System.currentTimeMillis()) {
        val key = "last:${fence.id}"
        if (now - prefs(c).getLong(key, 0) < REPEAT_AFTER_MS) return
        if (!hasNotifications(c)) return
        prefs(c).edit().putLong(key, now).apply()
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.geo_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val (title, body) = when (fence.kind) {
            FenceKind.AREA -> c.getString(R.string.geo_area_title, fence.name) to c.getString(R.string.geo_area_body)
            FenceKind.DRAFT_AREA -> c.getString(R.string.geo_area_title, fence.name) to c.getString(R.string.geo_draft_body)
            FenceKind.LIFT_DOWN -> c.getString(R.string.geo_lift_title) to c.getString(R.string.geo_lift_body, fence.name)
            FenceKind.PATH_CLOSED -> c.getString(R.string.geo_closed_title) to c.getString(R.string.geo_closed_body, fence.name)
        }
        val open = PendingIntent.getActivity(c, fence.id.hashCode(),
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_accesspath)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
        runCatching { nm.notify(fence.id.hashCode(), n) }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        addOnCanceledListener { cont.cancel() }
    }

    internal fun errorText(code: Int) = GeofenceStatusCodes.getStatusCodeString(code)
}

/** Woken by Android when the phone enters a watched area. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) { Log.w("Geofencing", "Geofence error: ${Geofencing.errorText(event.errorCode)}"); return }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val fences = Geofencing.savedFences(context).associateBy { it.id }
        event.triggeringGeofences.orEmpty().mapNotNull { fences[it.requestId] }
            // One alert per entry: the most useful one first (closures and lifts before a welcome).
            .sortedBy { when (it.kind) { FenceKind.PATH_CLOSED -> 0; FenceKind.LIFT_DOWN -> 1; FenceKind.DRAFT_AREA -> 2; FenceKind.AREA -> 3 } }
            .forEach { Geofencing.alert(context, it) }
    }
}

/** Android drops geofences on restart and app update; this puts them back. */
class GeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Geofencing.isEnabled(context)) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try { Geofencing.register(context, Geofencing.savedFences(context), force = true) } finally { pending.finish() }
        }
    }
}
