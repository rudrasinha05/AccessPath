package com.accesspath.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class LiveGpsLocation(
    val latitude: Double = 28.5020,
    val longitude: Double = 77.5100,
    val accuracyMeters: Float = 3.0f,
    val altitudeMeters: Double = 205.0,
    val bearingDegrees: Float = 0f,
    val speedMps: Float = 0f,
    val hasGpsFix: Boolean = false,
    val locality: String = "Zeta I",
    val subLocality: String = "Surajpur Site C",
    val thoroughfare: String = "Site C Rd",
    val adminArea: String = "Greater Noida",
    val displayName: String = "Zeta I, Greater Noida"
)

@SuppressLint("MissingPermission")
@Composable
fun rememberLiveGpsLocation(): MutableState<LiveGpsLocation> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gpsState = remember {
        mutableStateOf(
            LiveGpsLocation(
                latitude = 28.5020,
                longitude = 77.5100,
                hasGpsFix = false,
                locality = "Zeta I",
                subLocality = "Surajpur Site C",
                displayName = "Zeta I, Greater Noida"
            )
        )
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasPermission = fineGranted || coarseGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose {}

        fun updateLocationData(loc: Location) {
            coroutineScope.launch {
                val geocoder = Geocoder(context, Locale.getDefault())
                var locName = "Current Location"
                var subLoc = "Local area"
                var thoroughfare = "Street"
                var admin = "Noida"
                var display = "${String.format(Locale.US, "%.4f", loc.latitude)}°, ${String.format(Locale.US, "%.4f", loc.longitude)}°"

                try {
                    val addresses = withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    }
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        locName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Zeta I"
                        subLoc = addr.subLocality ?: addr.featureName ?: "Surajpur Site C"
                        thoroughfare = addr.thoroughfare ?: "Site C Rd"
                        admin = addr.adminArea ?: "Greater Noida"

                        display = when {
                            locName.isNotBlank() -> locName
                            subLoc.isNotBlank() -> subLoc
                            addr.featureName != null -> addr.featureName
                            else -> "Zeta I"
                        }
                    } else {
                        locName = "Zeta I"
                        subLoc = "Surajpur Site C"
                        thoroughfare = "Site C Rd"
                        admin = "Greater Noida"
                        display = "Zeta I"
                    }
                } catch (_: Exception) {
                    locName = "Zeta I"
                    subLoc = "Surajpur Site C"
                    thoroughfare = "Site C Rd"
                    admin = "Greater Noida"
                    display = "Zeta I"
                }

                gpsState.value = LiveGpsLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 3.5f,
                    altitudeMeters = if (loc.hasAltitude()) loc.altitude else 205.0,
                    bearingDegrees = if (loc.hasBearing()) loc.bearing else 0f,
                    speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                    hasGpsFix = true,
                    locality = locName,
                    subLocality = subLoc,
                    thoroughfare = thoroughfare,
                    adminArea = admin,
                    displayName = display
                )
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLocationData(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Check last known locations first for instant zero-latency feedback
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val lastKnown = lastGps ?: lastNetwork
            if (lastKnown != null) {
                updateLocationData(lastKnown)
            }

            // Register continuous real-time updates from GPS and Network
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 second
                    1.0f,  // 1 meter
                    listener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    listener
                )
            }
        } catch (_: SecurityException) {
        }

        onDispose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    return gpsState
}
