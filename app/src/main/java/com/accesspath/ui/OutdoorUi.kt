package com.accesspath.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.data.remote.OutdoorRoute
import com.accesspath.data.remote.OutdoorStep
import com.accesspath.data.remote.TravelMode
import com.accesspath.domain.LatLng
import java.util.Locale

private val Blue = Color(0xFF1A73E8)

fun TravelMode.label(): Int = when (this) {
    TravelMode.WALK -> R.string.mode_walk; TravelMode.WHEELCHAIR -> R.string.mode_wheelchair
    TravelMode.BIKE -> R.string.mode_bike; TravelMode.CAR -> R.string.mode_car; TravelMode.BUS -> R.string.mode_bus
}

fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk; TravelMode.WHEELCHAIR -> Icons.AutoMirrored.Filled.Accessible
    TravelMode.BIKE -> Icons.Filled.PedalBike; TravelMode.CAR -> Icons.Filled.DirectionsCar; TravelMode.BUS -> Icons.Filled.DirectionsBus
}

fun formatLatLng(l: LatLng) = String.format(Locale.US, "%.5f, %.5f", l.lat, l.lng)

fun duration(context: Context, sec: Double): String {
    val min = Math.round(sec / 60.0).toInt().coerceAtLeast(1)
    return if (min < 60) context.getString(R.string.dur_min, min) else context.getString(R.string.dur_hours, min / 60, min % 60)
}

fun OutdoorStep.text(context: Context): String {
    instruction?.let { return it }
    val dir = when (modifier) {
        "left" -> R.string.dir_left; "right" -> R.string.dir_right
        "slight left" -> R.string.dir_slight_left; "slight right" -> R.string.dir_slight_right
        "sharp left" -> R.string.dir_sharp_left; "sharp right" -> R.string.dir_sharp_right
        "uturn" -> R.string.dir_uturn; else -> R.string.dir_straight
    }.let(context::getString)
    fun named(withName: Int, plain: Int, vararg pre: String) =
        if (road.isNotBlank()) context.getString(withName, *pre, road) else context.getString(plain, *pre)
    return when (type) {
        "depart" -> named(R.string.out_depart, R.string.out_depart_plain)
        "arrive" -> context.getString(R.string.out_arrive)
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> named(R.string.out_roundabout, R.string.out_roundabout_plain)
        "turn", "end of road", "fork", "on ramp", "off ramp", "merge" ->
            if (modifier == null || modifier == "straight") named(R.string.out_continue, R.string.out_continue_plain) else named(R.string.out_turn, R.string.out_turn_plain, dir)
        else -> named(R.string.out_continue, R.string.out_continue_plain)
    }
}

fun outStepIcon(s: OutdoorStep): ImageVector = when {
    s.type == "arrive" -> Icons.Filled.Place
    s.type.startsWith("roundabout") || s.type.contains("rotary") -> Icons.Filled.Autorenew
    s.modifier?.contains("left") == true -> Icons.Filled.TurnLeft
    s.modifier?.contains("right") == true -> Icons.Filled.TurnRight
    s.modifier == "uturn" -> Icons.Filled.UTurnLeft
    else -> Icons.Filled.ArrowUpward
}

/** Google-Maps-style compass: the needle points to north; tap turns the map back to north-up. */
@Composable
fun CompassButton(orientation: Float, onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("compass")) {
        Icon(Icons.Filled.Navigation, contentDescription = stringResource(R.string.compass), tint = Color(0xFFEA4335),
            modifier = Modifier.rotate(-orientation))
    }
}

@Composable
fun ZoomButtons(onIn: () -> Unit, onOut: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column {
            IconButton(onClick = onIn) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zoom_in)) }
            HorizontalDivider(Modifier.width(32.dp).align(Alignment.CenterHorizontally))
            IconButton(onClick = onOut) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.zoom_out)) }
        }
    }
}

/** Options for a long-pressed spot: the "popup" for a dropped pin. */
@Composable
fun PinSheet(
    pin: LatLng, title: String?, nearPlace: String?, hasCampus: Boolean,
    onDirections: () -> Unit, onViewCampus: () -> Unit, onMapArea: () -> Unit,
    onZoomIn: () -> Unit, onZoomOut: () -> Unit, onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("pinSheet")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title ?: stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(formatLatLng(pin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(nearPlace?.let { stringResource(R.string.near_place, it) } ?: stringResource(R.string.not_mapped_here),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDirections, colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) {
                Icon(Icons.Filled.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.directions))
            }
            if (hasCampus) OutlinedButton(onClick = onViewCampus) {
                Icon(Icons.Filled.Apartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_campus))
            }
            OutlinedButton(onClick = onMapArea) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.map_this_area))
            }
            OutlinedIconButton(onClick = onZoomIn) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zoom_in)) }
            OutlinedIconButton(onClick = onZoomOut) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.zoom_out)) }
        }
    }
}

@Composable
fun TravelModeChips(selected: TravelMode, onPick: (TravelMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelMode.entries.forEach { m ->
            FilterChip(selected = m == selected, onClick = { onPick(m) }, label = { Text(stringResource(m.label())) },
                leadingIcon = { Icon(m.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("mode_${m.name.lowercase()}"))
        }
    }
}

sealed interface OutdoorState {
    data object Idle : OutdoorState
    data object NeedLocation : OutdoorState
    data object Loading : OutdoorState
    data object Unsupported : OutdoorState
    data object Failed : OutdoorState
    data class Ready(val route: OutdoorRoute) : OutdoorState
}

@Composable
fun OutdoorDirectionsSheet(
    state: OutdoorState, mode: TravelMode, destination: LatLng,
    onAllowLocation: () -> Unit, onRetry: () -> Unit, onStart: () -> Unit, onOpenTransit: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("outdoorSheet")) {
        when (state) {
            OutdoorState.Idle, OutdoorState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)); InfoText(stringResource(R.string.routing)) }
            OutdoorState.NeedLocation -> item {
                InfoText(stringResource(R.string.out_need_location))
                Button(onClick = onAllowLocation) { Text(stringResource(R.string.allow_location)) }
            }
            OutdoorState.Failed -> item {
                Banner(stringResource(R.string.out_route_failed), warning = true)
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            OutdoorState.Unsupported -> item {
                if (mode == TravelMode.BUS) {
                    InfoText(stringResource(R.string.out_bus_none))
                    Button(onClick = onOpenTransit) { Icon(Icons.Filled.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_google_maps)) }
                } else Banner(stringResource(R.string.out_wheelchair_none), warning = true)
            }
            is OutdoorState.Ready -> {
                val r = state.route
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(duration(context, r.durationSec), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF188038))
                        Spacer(Modifier.width(10.dp))
                        Text("(${metres(r.distance)})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    InfoText(stringResource(R.string.out_from_here) + " → " + formatLatLng(destination))
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White), modifier = Modifier.padding(top = 8.dp).height(48.dp)) {
                        Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start))
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                }
                items(r.steps.size) { i ->
                    val s = r.steps[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(outStepIcon(s), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.text(context), style = MaterialTheme.typography.bodyLarge)
                            if (s.distance > 0) Text(metres(s.distance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Text(stringResource(if (r.mode == TravelMode.WHEELCHAIR) R.string.route_credit_ors else R.string.route_credit_osrm),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Big green instruction banner during outdoor navigation. */
@Composable
fun OutdoorNavBanner(step: OutdoorStep?, toStep: Double?, next: OutdoorStep?, status: String?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Surface(color = Color(0xFF0B6E4F), contentColor = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(step?.let(::outStepIcon) ?: Icons.Filled.GpsFixed, null, Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    toStep?.let { Text(metres(it), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f)) }
                    Text(status ?: step?.text(context).orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    next?.let { Text(stringResource(R.string.then_step, it.text(context)), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
fun OutdoorNavSheet(remaining: Double?, mode: TravelMode, speaking: Boolean, onRead: () -> Unit, onStopReading: () -> Unit, onExit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("outdoorNav")) {
        Box(Modifier.size(40.dp).background(Blue.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(mode.icon(), null, tint = Blue)
        }
        Spacer(Modifier.width(12.dp))
        Text(remaining?.let { metres(it) } ?: stringResource(R.string.out_waiting_gps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = if (speaking) onStopReading else onRead) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(if (speaking) R.string.stop_reading else R.string.read_aloud))
        }
        OutlinedButton(onClick = onExit, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD93025))) { Text(stringResource(R.string.exit_navigation)) }
    }
}

@Composable
fun EmptyCampusCard(onMapArea: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("emptyCampus")) {
        Text(stringResource(R.string.no_campus_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        InfoText(stringResource(R.string.no_campus_body))
        Button(onClick = onMapArea, colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.map_this_area))
        }
    }
}
