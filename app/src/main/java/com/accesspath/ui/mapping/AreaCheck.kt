package com.accesspath.ui.mapping

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.accesspath.R
import com.accesspath.domain.AppSnapshot
import com.accesspath.domain.Coverage
import com.accesspath.domain.LatLng
import com.accesspath.ui.mapping.DeviceSensors.toLatLng

/** Remembers whether the person wants the "unmapped area" check when the app opens. */
object AreaCheckPrefs {
    private const val FILE = "accesspath_prefs"
    private const val KEY = "area_check"
    enum class Choice { UNSET, ON, OFF }

    fun get(context: Context): Choice = runCatching {
        Choice.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, Choice.UNSET.name)!!)
    }.getOrDefault(Choice.UNSET)

    fun set(context: Context, choice: Choice) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
}

/**
 * When the app opens: asks once whether it may check the location. If the person is somewhere the
 * map does not cover (and the GPS fix is good enough to be sure), offers camera mapping. Inside mapped
 * areas it stays silent.
 */
@Composable
fun UnmappedAreaCheck(state: AppSnapshot?, alreadyChecked: Boolean, onChecked: () -> Unit, onStartMapping: (LatLng) -> Unit) {
    val context = LocalContext.current
    var choice by remember { mutableStateOf(AreaCheckPrefs.get(context)) }
    var unmappedAt by remember { mutableStateOf<LatLng?>(null) }
    var granted by remember { mutableStateOf(DeviceSensors.hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        choice = if (granted) AreaCheckPrefs.Choice.ON else AreaCheckPrefs.Choice.OFF
        AreaCheckPrefs.set(context, choice)
    }

    if (choice == AreaCheckPrefs.Choice.UNSET && state != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.area_check_title)) },
            text = { Text(stringResource(R.string.area_check_body)) },
            confirmButton = {
                Button(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Text(stringResource(R.string.allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { choice = AreaCheckPrefs.Choice.OFF; AreaCheckPrefs.set(context, choice) }) { Text(stringResource(R.string.not_now)) }
            }
        )
    }

    LaunchedEffect(state != null, choice, granted) {
        if (state == null || alreadyChecked || choice != AreaCheckPrefs.Choice.ON || !granted) return@LaunchedEffect
        onChecked()
        val fix = DeviceSensors.currentLocation(context) ?: return@LaunchedEffect
        if (!fix.hasAccuracy() || fix.accuracy > DeviceSensors.MAX_ACCURACY_M) return@LaunchedEffect
        val here = fix.toLatLng()
        if (!Coverage.isMapped(state.map, here)) unmappedAt = here
    }

    unmappedAt?.let { here ->
        AlertDialog(
            onDismissRequest = { unmappedAt = null },
            title = { Text(stringResource(R.string.unmapped_title)) },
            text = { Text(stringResource(R.string.unmapped_body)) },
            confirmButton = { Button(onClick = { unmappedAt = null; onStartMapping(here) }) { Text(stringResource(R.string.start_mapping)) } },
            dismissButton = { TextButton(onClick = { unmappedAt = null }) { Text(stringResource(R.string.not_now)) } }
        )
    }
}
