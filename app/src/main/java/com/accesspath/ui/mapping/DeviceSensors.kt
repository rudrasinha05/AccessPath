package com.accesspath.ui.mapping

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.accesspath.domain.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Small helpers for one-off location and compass readings. Nothing is tracked in the background. */
object DeviceSensors {
    /** GPS accuracy needed before the app decides whether an area is mapped; worse fixes are ignored. */
    const val MAX_ACCURACY_M = 40f

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** One current position, or null when there is no permission, no provider or no fix within [timeoutMs]. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context, timeoutMs: Long = 15_000): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }
        }
    }

    fun Location.toLatLng() = LatLng(latitude, longitude)

    /** The freshest position any provider already has (instant; may be a few minutes old), or null. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** True when location is turned on in the phone's settings. */
    fun locationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Live compass direction the top of the phone points to (degrees clockwise from north), for compass mode. */
    fun headingUpdates(context: Context): kotlinx.coroutines.flow.Flow<Float> = kotlinx.coroutines.flow.callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val r = FloatArray(9); val o = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(r, event.values)
                SensorManager.getOrientation(r, o)
                trySend(((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0).toFloat())
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }

    /**
     * Live positions while collected (outdoor navigation and walk mapping); stops when collection stops.
     * Listens to every enabled provider (GPS, network, fused) and also checks the latest known fix every 1.5 s,
     * because some phones deliver updates late or batch them.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(context: Context): kotlinx.coroutines.flow.Flow<Location> = kotlinx.coroutines.flow.callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !hasLocationPermission(context)) { close(); return@callbackFlow }
        var lastTime = 0L
        fun offer(l: Location?) {
            if (l == null || l.time <= lastTime) return
            lastTime = l.time
            trySend(l)
        }
        val listener = androidx.core.location.LocationListenerCompat { offer(it) }
        val request = androidx.core.location.LocationRequestCompat.Builder(1_000L).setMinUpdateDistanceMeters(0f) // callers filter steps themselves
            .setQuality(androidx.core.location.LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
        fun providers() = runCatching { lm.getProviders(true) }.getOrDefault(emptyList()).filter { it != LocationManager.PASSIVE_PROVIDER }
        providers().forEach { p ->
            runCatching { LocationManagerCompat.requestLocationUpdates(lm, p, request, ContextCompat.getMainExecutor(context), listener) }
        }
        val poll = launch {
            while (true) {
                providers().mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let(::offer)
                kotlinx.coroutines.delay(1_500)
            }
        }
        awaitClose { poll.cancel(); LocationManagerCompat.removeUpdates(lm, listener) }
    }

    /** Compass direction the phone's back camera faces, degrees clockwise from north; null without a sensor. */
    suspend fun heading(context: Context, timeoutMs: Long = 2_000): Double? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val r = FloatArray(9); val o = FloatArray(3)
                        SensorManager.getRotationMatrixFromVector(r, event.values)
                        // Remap so the azimuth follows the back camera when the phone is held upright.
                        val remapped = FloatArray(9)
                        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                        SensorManager.getOrientation(remapped, o)
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0))
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }
}
