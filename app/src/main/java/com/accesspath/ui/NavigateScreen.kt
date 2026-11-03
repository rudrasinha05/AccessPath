package com.accesspath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.domain.*
import com.accesspath.ui.theme.LocalMapColors

@Composable
fun NavigateScreen(ui: ScreenState, vm: AccessViewModel) {
    val state = ui.snapshot ?: return
    val context = LocalContext.current
    val speaker = rememberSpeaker()
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "from" | "to"
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var tappedPlace by rememberSaveable { mutableStateOf<String?>(null) }

    val found = ui.route as? RouteResult.Found
    val steps = remember(found, state.map) { found?.let { RouteSteps.build(it, state.map) }.orEmpty() }
    val exclusions = remember(state) { Routing.exclusions(state) }
    val current = state.node(state.current)
    val destination = state.node(state.destination)

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("navigate")
    ) {
        item { ScreenTitle(stringResource(R.string.where_to)) }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EndPoint(stringResource(R.string.from_label), current?.let { state.placeLabel(context, it) } ?: "—") { picking = "from" }
                        EndPoint(stringResource(R.string.to_label), destination?.let { state.placeLabel(context, it) } ?: "—") { picking = "to" }
                    }
                    IconButton(onClick = vm::swapEnds) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.swap)) }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(state.profile.mobility.label()), fontWeight = FontWeight.SemiBold)
                        profileSummary(state.profile)?.let { InfoText(it) }
                    }
                    TextButton(onClick = { editProfile = true }) { Text(stringResource(R.string.change)) }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.nearest_title), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NearbyKind.entries.forEach { kind ->
                        AssistChip(onClick = { vm.goToNearest(kind) }, label = { Text(stringResource(kind.label())) })
                    }
                }
            }
        }

        item {
            RouteSummary(ui, state, found, steps.size, speaker, onReadAloud = {
                val locale = context.resources.configuration.locales[0]
                val lines = steps.map { it.text(context) }
                if (!speaker.speak(lines, locale)) vm.message(UiMessage.Text(R.string.tts_unavailable))
            }, onOpenSettings = { editProfile = true })
        }

        item {
            SectionCard(stringResource(R.string.map_title)) {
                val routeFloors = found?.nodes?.map { it.floorId }?.toSet().orEmpty()
                val routeColor = LocalMapColors.current.route
                val routeDot: @Composable () -> Unit = { Box(Modifier.size(8.dp).background(routeColor, CircleShape)) }
                val multiBuilding = state.map.buildings.size > 1
                Row(Modifier.fillMaxWidth().horizontalScrollable(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.map.floors.sortedWith(compareBy({ it.buildingId }, { it.ordinal })).forEach { f ->
                        val building = state.map.buildings.firstOrNull { it.id == f.buildingId }?.name
                        FilterChip(
                            selected = f.id == state.selectedFloor,
                            onClick = { vm.selectFloor(f.id) },
                            label = { Text(if (multiBuilding && building != null) "$building · ${f.label}" else f.label) },
                            leadingIcon = if (f.id in routeFloors) routeDot else null
                        )
                    }
                }
                val floorName = state.floor(state.selectedFloor)?.label ?: ""
                FloorPlan(
                    state = state, floorId = state.selectedFloor, route = found, exclusions = exclusions,
                    description = stringResource(R.string.map_a11y, floorName),
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(MaterialTheme.shapes.medium)
                ) { tappedPlace = it.id }
                Legend()
                InfoText(stringResource(R.string.map_hint))
            }
        }

        if (found != null && steps.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.steps_title)) }
            itemsIndexed(steps, key = { i, s -> "$i-${s.to.id}" }) { index, step ->
                StepRow(index, step, isNext = index == 0, onConfirm = { vm.setCurrent(step.to.id) })
            }
        }
    }

    when (picking) {
        "from" -> PlacePickerDialog(state, stringResource(R.string.pick_from_title), { picking = null }) { picking = null; vm.setCurrent(it.id) }
        "to" -> PlacePickerDialog(state, stringResource(R.string.pick_to_title), { picking = null }) { picking = null; vm.setDestination(it.id) }
    }
    if (editProfile) ProfileDialog(state.profile, { editProfile = false }) { editProfile = false; vm.setProfile(it) }
    tappedPlace?.let(state::node)?.let { node ->
        AlertDialog(
            onDismissRequest = { tappedPlace = null },
            title = { Text(node.name) },
            text = { InfoText(state.placeLabel(context, node)) },
            confirmButton = {
                Button(onClick = { tappedPlace = null; vm.setDestination(node.id) }) { Text(stringResource(R.string.go_here)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { tappedPlace = null }) { Text(stringResource(R.string.close)) }
                    TextButton(onClick = { tappedPlace = null; vm.setCurrent(node.id) }) { Text(stringResource(R.string.im_here)) }
                }
            }
        )
    }
}

@Composable
private fun EndPoint(label: String, value: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onChange) { Text(stringResource(R.string.change)) }
    }
}

@Composable
private fun profileSummary(p: Profile): String? {
    val parts = mutableListOf<String>()
    if (p.mobility == Mobility.WHEELCHAIR) parts += stringResource(R.string.profile_summary_wheelchair, formatNumber(p.minWidth), formatNumber(p.maxSlope))
    p.maxWalkingDistance?.let { parts += stringResource(R.string.profile_summary_limit, formatNumber(it)) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun RouteSummary(
    ui: ScreenState, state: AppSnapshot, found: RouteResult.Found?, stepCount: Int,
    speaker: Speaker, onReadAloud: () -> Unit, onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    SectionCard(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            ui.routing && ui.route == null -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                InfoText(stringResource(R.string.routing))
            }
            found != null && found.edges.isEmpty() -> {
                SectionTitle(stringResource(R.string.arrived_title))
                InfoText(stringResource(R.string.arrived_body, state.node(state.current)?.name ?: ""))
            }
            found != null -> {
                Text(stringResource(R.string.route_summary, metres(found.distance), stepCount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val tags = buildList {
                    when {
                        state.profile.mobility == Mobility.WHEELCHAIR -> add(stringResource(R.string.route_step_free))
                        !found.usesStairs -> add(stringResource(R.string.route_no_stairs))
                        else -> add(stringResource(R.string.route_uses_stairs))
                    }
                    if (found.usesLift) add(stringResource(R.string.route_uses_lift))
                    if (found.usesRamp) add(stringResource(R.string.route_uses_ramp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollable()) { tags.forEach { StatusPill(it, false) } }
                found.extraDistanceVsStandard?.takeIf { it >= 1.0 }?.let { InfoText(stringResource(R.string.route_extra, metres(it))) }
                if (found.exceedsMaxWalkingDistance) {
                    Banner(stringResource(R.string.route_over_limit, metres(state.profile.maxWalkingDistance ?: 0.0)), warning = true)
                }
                if (speaker.speaking) {
                    OutlinedButton(onClick = speaker::stop) { Text(stringResource(R.string.stop_reading)) }
                } else {
                    OutlinedButton(onClick = onReadAloud) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.read_aloud))
                    }
                }
            }
            ui.route is RouteResult.Unavailable -> {
                val r = ui.route
                when (r.cause) {
                    NoRouteCause.INVALID_ENDPOINTS -> InfoText(stringResource(R.string.no_route_endpoints))
                    NoRouteCause.INVALID_PROFILE -> {
                        Banner(stringResource(R.string.no_route_profile_invalid), warning = true)
                        Button(onClick = onOpenSettings) { Text(stringResource(R.string.review_settings)) }
                    }
                    NoRouteCause.NOT_CONNECTED -> {
                        SectionTitle(stringResource(R.string.no_route_not_connected_title))
                        InfoText(stringResource(R.string.no_route_not_connected))
                    }
                    NoRouteCause.CLOSED_NOW -> {
                        SectionTitle(stringResource(R.string.no_route_title))
                        InfoText(stringResource(R.string.no_route_closed))
                        r.closures.mapNotNull { it.closureLabel() }.distinct().forEach { Text("• " + context.getString(it)) }
                    }
                    NoRouteCause.PROFILE_TOO_STRICT -> {
                        SectionTitle(stringResource(R.string.no_route_profile_title))
                        InfoText(stringResource(R.string.no_route_profile))
                        Button(onClick = onOpenSettings) { Text(stringResource(R.string.review_settings)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, step: RouteStep, isNext: Boolean, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val container = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "${index + 1}", Modifier.size(28.dp).wrapContentSize(Alignment.Center),
                        color = if (isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (isNext) Text(stringResource(R.string.step_next_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(step.text(context), style = MaterialTheme.typography.bodyLarge)
                    InfoText(metres(step.distance))
                }
            }
            if (isNext) {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.confirm_reached, step.to.name))
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val c = LocalMapColors.current
    Row(Modifier.horizontalScrollable(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(c.you, stringResource(R.string.legend_you), dot = true)
        LegendItem(c.destination, stringResource(R.string.legend_destination), dot = true)
        LegendItem(c.route, stringResource(R.string.legend_route))
        LegendItem(c.closed, stringResource(R.string.legend_closed))
        LegendItem(c.unsuitable, stringResource(R.string.legend_unsuitable))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(if (dot) Modifier.size(12.dp).background(color, CircleShape) else Modifier.size(width = 18.dp, height = 4.dp).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
