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
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.ui.res.pluralStringResource
import com.accesspath.data.BarrierKind
import com.accesspath.data.OutdoorBarrier
import com.accesspath.data.onLine
import com.accesspath.data.remote.metresTo
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
    onDirections: () -> Unit, onViewCampus: () -> Unit, onMapArea: () -> Unit, onReport: () -> Unit,
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
            OutlinedButton(onClick = onReport, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD93025))) {
                Icon(Icons.Filled.AddAlert, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.report_barrier_here))
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
    /** Route choices (best first) with the reported barriers on each; [index] is the one in use. */
    data class Ready(val routes: List<OutdoorRoute>, val index: Int = 0, val blocked: List<List<OutdoorBarrier>> = routes.map { emptyList() }) : OutdoorState {
        val route get() = routes[index]
        val barriersOnRoute get() = blocked.getOrElse(index) { emptyList() }
    }
}

/**
 * Picks the route to use: the quickest one with no reported barrier on it; when every route has one,
 * the one with the fewest. This is how AccessPath turns people's reports into a different path.
 */
fun chooseRoute(routes: List<OutdoorRoute>, barriers: List<OutdoorBarrier>): OutdoorState.Ready {
    val blocked = routes.map { barriers.onLine(it.line) }
    val best = routes.indices.minWithOrNull(compareBy<Int>({ blocked[it].size }, { routes[it].durationSec })) ?: 0
    return OutdoorState.Ready(routes, best, blocked)
}

/** "350 m", "1.2 km": rounded like other map apps. */
fun distanceText(context: Context, m: Double): String = when {
    m < 100 -> context.getString(R.string.metres, Math.round(m).toString())
    m < 1000 -> context.getString(R.string.metres, (Math.round(m / 10) * 10).toString())
    else -> context.getString(R.string.km, String.format(Locale.getDefault(), "%.1f", m / 1000))
}
@Composable fun dist(m: Double) = distanceText(LocalContext.current, m)

fun arrivalTime(sec: Double): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(System.currentTimeMillis() + (sec * 1000).toLong()))

@StringRes fun BarrierKind.label() = when (this) {
    BarrierKind.CONSTRUCTION -> R.string.barrier_construction; BarrierKind.ROAD_BLOCKED -> R.string.barrier_blocked
    BarrierKind.FLOODING -> R.string.barrier_flooding; BarrierKind.NO_RAMP -> R.string.barrier_no_ramp
    BarrierKind.BROKEN_FOOTPATH -> R.string.barrier_footpath; BarrierKind.OTHER -> R.string.barrier_other
}
fun BarrierKind.icon(): ImageVector = when (this) {
    BarrierKind.CONSTRUCTION -> Icons.Filled.Construction; BarrierKind.ROAD_BLOCKED -> Icons.Filled.Block
    BarrierKind.FLOODING -> Icons.Filled.Water; BarrierKind.NO_RAMP -> Icons.Filled.Stairs
    BarrierKind.BROKEN_FOOTPATH -> Icons.Filled.Warning; BarrierKind.OTHER -> Icons.Filled.ReportProblem
}

/** Travel modes with the real travel time of each, like the mode tabs of a map app. */
@Composable
fun TravelModeChips(selected: TravelMode, times: Map<TravelMode, Double> = emptyMap(), onPick: (TravelMode) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TravelMode.entries.forEach { m ->
            FilterChip(selected = m == selected, onClick = { onPick(m) },
                label = { Text(times[m]?.let { duration(context, it) } ?: stringResource(m.label())) },
                leadingIcon = { Icon(m.icon(), contentDescription = stringResource(m.label()), modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandTealSoft, selectedLabelColor = BrandTeal, selectedLeadingIconColor = BrandTeal),
                modifier = Modifier.testTag("mode_${m.name.lowercase()}"))
        }
    }
}

@Composable
fun OutdoorDirectionsSheet(
    state: OutdoorState, mode: TravelMode, destinationName: String, saved: Boolean,
    onAllowLocation: () -> Unit, onRetry: () -> Unit, onStart: () -> Unit, onOpenTransit: () -> Unit,
    onPickRoute: (Int) -> Unit, onShare: () -> Unit, onSave: () -> Unit
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
                    Button(onClick = onOpenTransit) { Icon(Icons.Filled.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_transit)) }
                } else Banner(stringResource(R.string.out_wheelchair_none), warning = true)
            }
            is OutdoorState.Ready -> {
                val r = state.route
                val fastest = state.routes.minOf { it.durationSec }
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(duration(context, r.durationSec), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = if (state.barriersOnRoute.isEmpty()) Color(0xFF188038) else Color(0xFFE37400), modifier = Modifier.testTag("routeDuration"))
                        Spacer(Modifier.width(10.dp))
                        Text("(${dist(r.distance)})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    val avoided = state.blocked.withIndex().filter { it.index != state.index }.sumOf { it.value.size }
                    when {
                        state.barriersOnRoute.isNotEmpty() -> Banner(pluralStringResource(R.plurals.barriers_on_route, state.barriersOnRoute.size, state.barriersOnRoute.size,
                            stringResource(state.barriersOnRoute.first().kind.label())), warning = true)
                        avoided > 0 && r.durationSec > fastest -> Text(pluralStringResource(R.plurals.barriers_avoided, avoided, avoided), color = Color(0xFF188038), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        r.durationSec <= fastest -> InfoText(stringResource(R.string.fastest_route))
                        else -> InfoText(stringResource(R.string.to_place, destinationName))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = BrandTeal, contentColor = Color.White), modifier = Modifier.height(44.dp).testTag("startNav")) {
                            Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start))
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.height(44.dp)) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share)) }
                        OutlinedButton(onClick = onSave, modifier = Modifier.height(44.dp)) {
                            Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (saved) R.string.saved else R.string.save_place))
                        }
                    }
                    if (state.routes.size > 1) {
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                        Text(stringResource(R.string.route_options), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                if (state.routes.size > 1) items(state.routes.size) { i ->
                    val alt = state.routes[i]; val sel = i == state.index; val bad = state.blocked.getOrElse(i) { emptyList() }
                    Surface(onClick = { onPickRoute(i) }, shape = RoundedCornerShape(14.dp),
                        color = if (sel) BrandTealSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().testTag("routeOption$i")) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (sel) Icons.Filled.CheckCircle else Icons.Filled.Route, null, tint = if (sel) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${duration(context, alt.durationSec)} · ${dist(alt.distance)}", fontWeight = FontWeight.SemiBold)
                                val via = alt.steps.filter { it.road.isNotBlank() }.maxByOrNull { it.distance }?.road
                                Text(listOfNotNull(via?.let { stringResource(R.string.via_road, it) },
                                    if (bad.isNotEmpty()) pluralStringResource(R.plurals.barrier_count, bad.size, bad.size) else stringResource(R.string.no_barriers_reported)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = if (bad.isNotEmpty()) Color(0xFFD93025) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val diff = Math.round((alt.durationSec - fastest) / 60.0)
                            if (diff > 0) Text("+" + context.getString(R.string.dur_min, diff.toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                }
                items(r.steps.size) { i -> StepRow(r.steps[i]) }
                item {
                    Text(stringResource(if (r.mode == TravelMode.WHEELCHAIR) R.string.route_credit_ors else R.string.route_credit_osrm),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StepRow(s: OutdoorStep) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(outStepIcon(s), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(s.text(context), style = MaterialTheme.typography.bodyLarge)
            if (s.distance > 0) Text(dist(s.distance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val NavTeal = Color(0xFF0B5F66)
private val NavTealDark = Color(0xFF074349)

/** Navigation banner: the next turn in big type, and the turn after it in a small tab underneath. */
@Composable
fun OutdoorNavBanner(step: OutdoorStep?, toStep: Double?, next: OutdoorStep?, status: String?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("navBanner")) {
        Surface(color = NavTeal, contentColor = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(step?.let(::outStepIcon) ?: Icons.Filled.GpsFixed, null, Modifier.size(44.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    if (status == null && toStep != null) Text(dist(toStep), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(status ?: step?.text(context).orEmpty(), style = if (status == null && toStep != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (next != null && status == null) Surface(color = NavTealDark, contentColor = Color.White, shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
            modifier = Modifier.padding(start = 14.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.then), style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(8.dp))
                Icon(outStepIcon(next), contentDescription = next.text(context), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Live speed from GPS, bottom left during navigation. */
@Composable
fun SpeedBubble(kmh: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.size(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(kmh.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.kmh), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Bottom of the navigation screen: exit, time left, distance and arrival time. Swiping it up shows the menu:
 * add a report, share trip progress, search along the route, the step list, satellite view, voice and settings.
 */
@Composable
fun OutdoorNavSheet(
    remainingM: Double?, remainingSec: Double?, route: OutdoorRoute?, currentStep: Int,
    muted: Boolean, satellite: Boolean, alongResults: List<com.accesspath.data.remote.Poi>?, alongLoading: Boolean, here: LatLng?,
    onExit: () -> Unit, onReport: () -> Unit, onShareProgress: () -> Unit, onAlong: (com.accesspath.data.remote.PoiCategory) -> Unit,
    onGoAlong: (com.accesspath.data.remote.Poi) -> Unit, onSatellite: (Boolean) -> Unit, onMute: (Boolean) -> Unit, onSettings: () -> Unit
) {
    val context = LocalContext.current
    var showSteps by remember { mutableStateOf(false) }
    Column(Modifier.testTag("outdoorNav")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onExit, modifier = Modifier.size(48.dp).testTag("exitNav")) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exit_navigation)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(remainingSec?.let { duration(context, it) } ?: stringResource(R.string.out_waiting_gps),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF188038))
                if (remainingM != null && remainingSec != null)
                    Text("${dist(remainingM)} · ${arrivalTime(remainingSec)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        NavMenuRow(Icons.Filled.AddAlert, stringResource(R.string.add_report), onReport)
        NavMenuRow(Icons.Filled.Share, stringResource(R.string.share_progress), onShareProgress)
        Text(stringResource(R.string.search_along_route), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(com.accesspath.data.remote.PoiCategory.PETROL, com.accesspath.data.remote.PoiCategory.RESTAURANTS, com.accesspath.data.remote.PoiCategory.TOILETS,
                com.accesspath.data.remote.PoiCategory.HOSPITALS, com.accesspath.data.remote.PoiCategory.ATMS).forEach { c ->
                AssistChip(onClick = { onAlong(c) }, label = { Text(stringResource(c.label())) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) })
            }
        }
        when {
            alongLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            alongResults != null && alongResults.isEmpty() -> InfoText(stringResource(R.string.none_along_route))
            alongResults != null -> alongResults.take(5).forEach { p ->
                ListItem(headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { here?.let { Text(dist(it.metresTo(p.at))) } },
                    trailingContent = { TextButton(onClick = { onGoAlong(p) }) { Text(stringResource(R.string.go_here)) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        NavMenuRow(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.steps_title)) { showSteps = !showSteps }
        if (showSteps) route?.steps?.drop(currentStep)?.forEach { StepRow(it) }
        NavSwitchRow(Icons.Filled.Satellite, stringResource(R.string.show_satellite), satellite, onSatellite)
        NavSwitchRow(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.voice_guidance), !muted) { onMute(!it) }
        NavMenuRow(Icons.Filled.Settings, stringResource(R.string.settings_title), onSettings)
    }
}

@Composable
private fun NavMenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(20.dp)); Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NavSwitchRow(icon: ImageVector, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "What's blocking the way?" Report form for a spot outdoors, like a traffic report. */
@Composable
fun ReportBarrierDialog(signedIn: Boolean, atPin: Boolean = false, onDismiss: () -> Unit, onSend: (BarrierKind, String) -> Unit) {
    var kind by remember { mutableStateOf<BarrierKind?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AddAlert, null, tint = Color(0xFFD93025)) },
        title = { Text(stringResource(R.string.report_what)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("barrierForm")) {
                InfoText(stringResource(if (atPin) R.string.report_here_pin_hint else R.string.report_here_hint))
                BarrierKind.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.label()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(k.icon(), null, Modifier.size(18.dp)) }, modifier = Modifier.weight(1f).testTag("barrier_${k.name.lowercase()}"))
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(300) }, label = { Text(stringResource(R.string.report_note_optional)) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                if (!signedIn) Text(stringResource(R.string.report_local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { kind?.let { onSend(it, note) } }, enabled = kind != null, modifier = Modifier.testTag("sendBarrier")) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Details of a reported barrier tapped on the map. */
@Composable
fun BarrierInfoDialog(b: OutdoorBarrier, here: LatLng?, onDismiss: () -> Unit, onCleared: () -> Unit) {
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(b.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(b.kind.icon(), null, tint = Color(0xFFD93025)) },
        title = { Text(stringResource(b.kind.label())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(stringResource(R.string.reported_ago, ago), here?.let { dist(it.metresTo(b.at)) }).joinToString(" · "))
                if (b.note.isNotBlank()) Text("\u201C${b.note}\u201D", style = MaterialTheme.typography.bodyMedium)
                InfoText(stringResource(R.string.barrier_routes_avoid))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onCleared) { Text(stringResource(R.string.barrier_cleared)) } })
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
