@file:OptIn(ExperimentalMaterial3Api::class)

package com.accesspath.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.accesspath.R
import com.accesspath.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Mode { EXPLORE, PLACE, DIRECTIONS, NAVIGATE }

private val GoogleBlue = Color(0xFF1A73E8)

fun placeIcon(type: String): ImageVector = when (type.uppercase()) {
    "WASHROOM" -> Icons.Filled.Wc
    "ELEVATOR" -> Icons.Filled.Elevator
    "ENTRANCE", "EXIT" -> Icons.Filled.MeetingRoom
    "STAIRS" -> Icons.Filled.Stairs
    "RAMP" -> Icons.AutoMirrored.Filled.Accessible
    "LANDMARK" -> Icons.Filled.Place
    else -> Icons.Filled.Room
}

fun placeTint(type: String): Color = when (type.uppercase()) {
    "WASHROOM" -> Color(0xFF00897B)
    "ELEVATOR" -> Color(0xFF7B1FA2)
    "ENTRANCE", "EXIT" -> Color(0xFF188038)
    "LANDMARK" -> Color(0xFF80868B)
    else -> Color(0xFFE37400)
}

fun stepIcon(type: Segment): ImageVector = when (type) {
    Segment.CORRIDOR -> Icons.Filled.ArrowUpward
    Segment.OUTDOOR -> Icons.Filled.Park
    Segment.DOOR -> Icons.Filled.MeetingRoom
    Segment.RAMP -> Icons.AutoMirrored.Filled.Accessible
    Segment.STAIRS -> Icons.Filled.Stairs
    Segment.ELEVATOR -> Icons.Filled.Elevator
}

@Composable
fun ExploreScreen(ui: ScreenState, vm: AccessViewModel, speaker: Speaker) {
    val state = ui.snapshot ?: return
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(Mode.EXPLORE) }
    var placeId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var nonce by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf(CameraRequest(0, center = state.node(state.current)?.let { Geo.position(state.map, it) }, zoom = 18.8)) }

    val found = ui.route as? RouteResult.Found
    val steps = remember(found, state.map) { found?.let { RouteSteps.build(it, state.map) }.orEmpty() }
    val exclusions = remember(state) { Routing.exclusions(state) }
    val hasGeo = remember(state.map) { Geo.hasGeo(state.map) }
    val current = state.node(state.current)
    val place = placeId?.let(state::node)

    fun moveTo(node: Node?, zoom: Double = 20.0) {
        val p = node?.let { Geo.position(state.map, it) } ?: return
        camera = CameraRequest(++nonce, center = p, zoom = zoom)
    }
    fun select(node: Node) {
        placeId = node.id; mode = Mode.PLACE
        if (node.floorId != state.selectedFloor) vm.selectFloor(node.floorId)
        moveTo(node)
    }
    fun startDirections(to: String) {
        vm.setDestination(to); mode = Mode.DIRECTIONS
        current?.let { if (it.floorId != state.selectedFloor) vm.selectFloor(it.floorId) }
    }

    // Fit the route whenever it changes while directions are shown.
    LaunchedEffect(found?.revision, found?.edges, mode) {
        if (mode == Mode.DIRECTIONS && found != null && found.edges.isNotEmpty()) {
            val floor = current?.floorId
            val pts = found.nodes.filter { it.floorId == floor }.mapNotNull { Geo.position(state.map, it) }
            if (pts.size >= 2) camera = CameraRequest(++nonce, bounds = pts) else moveTo(current)
        }
    }
    // While navigating, follow the confirmed position.
    LaunchedEffect(state.current, mode) { if (mode == Mode.NAVIGATE) moveTo(current, 20.3) }

    BackHandler(enabled = mode != Mode.EXPLORE) {
        mode = when (mode) { Mode.NAVIGATE -> Mode.DIRECTIONS; Mode.DIRECTIONS -> if (placeId != null) Mode.PLACE else Mode.EXPLORE; else -> Mode.EXPLORE }
        if (mode == Mode.EXPLORE) placeId = null
    }

    val peek: Dp = when (mode) { Mode.NAVIGATE -> 176.dp; Mode.EXPLORE -> 220.dp; else -> 236.dp }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerDp = with(density) { headerPx.toDp() }
    // Keep the camera target in the visible part of the map, between the top overlay and the sheet.
    val centerOffsetPx = with(density) { ((headerPx - peek.roundToPx()) / 2f).toInt() }
    // First camera move once the layout (and so the visible map area) is known.
    LaunchedEffect(headerPx > 0) { if (headerPx > 0 && mode == Mode.EXPLORE) moveTo(placeId?.let(state::node) ?: current, 19.0) }
    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = peek,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 640.dp).padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("sheet")) {
                when (mode) {
                    Mode.EXPLORE -> ExploreSheet(state, onChangePosition = { picking = "from" }, onNearest = { kind -> placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }, onPlace = ::select)
                    Mode.PLACE -> if (place != null) {
                        PlaceSheet(state, place, onClose = { placeId = null; mode = Mode.EXPLORE },
                            onDirections = { startDirections(place.id) }, onImHere = { vm.setCurrent(place.id); placeId = null; mode = Mode.EXPLORE })
                    } else LaunchedEffect(Unit) { mode = Mode.EXPLORE }
                    Mode.DIRECTIONS -> DirectionsSheet(ui, state, found, steps, speaker,
                        onStart = { mode = Mode.NAVIGATE; moveTo(current, 20.3) },
                        onRead = { read(context, speaker, steps, vm) },
                        onSettings = { editProfile = true })
                    Mode.NAVIGATE -> NavigateSheet(state, found, steps, speaker,
                        onConfirm = { steps.firstOrNull()?.let { vm.setCurrent(it.to.id) } },
                        onRead = { read(context, speaker, steps.take(1), vm) },
                        onExit = { speaker.stop(); mode = Mode.EXPLORE; placeId = null })
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            val floorLabel = state.floor(state.selectedFloor)?.label.orEmpty()
            if (hasGeo) {
                WorldMap(
                    state = state, route = found, exclusions = exclusions, selectedPlaceId = placeId, camera = camera,
                    centerOffsetPx = centerOffsetPx,
                    description = stringResource(R.string.world_map_a11y, floorLabel),
                    modifier = Modifier.fillMaxSize().testTag("worldMap"),
                    onPlaceTap = { if (mode != Mode.NAVIGATE) select(it) },
                    onEmptyTap = {}
                )
            } else {
                FloorPlan(
                    state = state, floorId = state.selectedFloor, route = found, exclusions = exclusions,
                    description = stringResource(R.string.map_a11y, floorLabel),
                    modifier = Modifier.fillMaxSize().padding(bottom = peek)
                ) { if (mode != Mode.NAVIGATE) select(it) }
            }

            // Top overlay
            Column(Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }.statusBarsPadding().padding(12.dp)) {
                when (mode) {
                    Mode.NAVIGATE -> NavBanner(steps)
                    Mode.DIRECTIONS -> DirectionsHeader(state,
                        onBack = { mode = if (placeId != null) Mode.PLACE else Mode.EXPLORE },
                        onFrom = { picking = "from" }, onTo = { picking = "to" }, onSwap = vm::swapEnds,
                        onMobility = { vm.setProfile(state.profile.copy(mobility = it)) }, onOptions = { editProfile = true })
                    else -> {
                        SearchPill(onClick = { searching = true }, profile = state.profile, onProfile = { editProfile = true })
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NearbyKind.entries.forEach { kind ->
                                val icon = when (kind) { NearbyKind.WASHROOM -> Icons.Filled.Wc; NearbyKind.LIFT -> Icons.Filled.Elevator; NearbyKind.EXIT -> Icons.Filled.MeetingRoom }
                                MapChip(stringResource(kind.label()), icon) { placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }
                            }
                        }
                    }
                }
            }

            // Right-side controls: floor picker under the top overlay, "show my position" above the sheet.
            Box(Modifier.align(Alignment.TopEnd).padding(top = headerDp + 4.dp, end = 12.dp)) {
                FloorPicker(state) { vm.selectFloor(it) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = peek + 16.dp)) {
                SmallFloatingActionButton(
                    onClick = { current?.let { if (it.floorId != state.selectedFloor) vm.selectFloor(it.floorId); moveTo(it) } },
                    containerColor = MaterialTheme.colorScheme.surface, contentColor = GoogleBlue
                ) { Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.recenter)) }
            }
            if (hasGeo) {
                Text(
                    stringResource(R.string.map_attribution),
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF3C4043),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = peek + 6.dp)
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }

    if (searching) SearchDialog(state, onDismiss = { searching = false }, onNearest = { kind -> searching = false; placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }) { node -> searching = false; select(node) }
    when (picking) {
        "from" -> PlacePickerDialog(state, stringResource(R.string.pick_from_title), { picking = null }) { picking = null; vm.setCurrent(it.id); moveTo(it) }
        "to" -> PlacePickerDialog(state, stringResource(R.string.pick_to_title), { picking = null }) { picking = null; startDirections(it.id) }
    }
    if (editProfile) ProfileDialog(state.profile, { editProfile = false }) { editProfile = false; vm.setProfile(it) }
}

private fun read(context: android.content.Context, speaker: Speaker, steps: List<RouteStep>, vm: AccessViewModel) {
    val locale = context.resources.configuration.locales[0]
    if (!speaker.speak(steps.map { it.text(context) }, locale)) vm.message(UiMessage.Text(R.string.tts_unavailable))
}

// ---- Top overlays ----------------------------------------------------------------------------

@Composable
private fun SearchPill(onClick: () -> Unit, profile: Profile, onProfile: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.search_campus), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onProfile) {
                Box(Modifier.size(36.dp).background(GoogleBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(mobilityIcon(profile.mobility), contentDescription = stringResource(R.string.profile_title), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

fun mobilityIcon(m: Mobility): ImageVector = when (m) {
    Mobility.STANDARD -> Icons.AutoMirrored.Filled.DirectionsWalk
    Mobility.AVOID_STAIRS -> Icons.Filled.Elevator
    Mobility.WHEELCHAIR -> Icons.AutoMirrored.Filled.Accessible
}

@Composable
private fun MapChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, onClick = onClick) {
        Row(Modifier.height(36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DirectionsHeader(
    state: AppSnapshot, onBack: () -> Unit, onFrom: () -> Unit, onTo: () -> Unit, onSwap: () -> Unit,
    onMobility: (Mobility) -> Unit, onOptions: () -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EndpointBox(Icons.Filled.MyLocation, GoogleBlue, stringResource(R.string.your_location, state.node(state.current)?.name ?: "—"), onFrom)
                    EndpointBox(Icons.Filled.Place, Color(0xFFEA4335), state.node(state.destination)?.name ?: stringResource(R.string.choose_destination), onTo)
                }
                IconButton(onClick = onSwap) { Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.swap)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Mobility.entries.forEach { m ->
                    FilterChip(
                        selected = state.profile.mobility == m, onClick = { onMobility(m) },
                        label = { Text(stringResource(m.label())) },
                        leadingIcon = { Icon(mobilityIcon(m), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                IconButton(onClick = onOptions) { Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.profile_title)) }
            }
        }
    }
}

@Composable
private fun EndpointBox(icon: ImageVector, tint: Color, text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NavBanner(steps: List<RouteStep>) {
    val context = LocalContext.current
    val step = steps.firstOrNull() ?: return
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Surface(color = Color(0xFF0B6E4F), contentColor = Color.White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (steps.size > 1) 0.dp else 20.dp, bottomEnd = if (steps.size > 1) 0.dp else 20.dp), shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(stepIcon(step.type), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(metres(step.distance), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                    Text(step.text(context), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        steps.getOrNull(1)?.let { next ->
            Surface(color = Color(0xFF0A5C42), contentColor = Color.White, shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(stepIcon(next.type), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.then_step, next.text(context)), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FloorPicker(state: AppSnapshot, onPick: (String) -> Unit) {
    val selected = state.floor(state.selectedFloor) ?: return
    val floors = state.map.floors.filter { it.buildingId == selected.buildingId }.sortedByDescending { it.ordinal }
    if (floors.size < 2) return
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(Modifier.padding(4.dp)) {
            floors.forEach { f ->
                val isSel = f.id == selected.id
                val label = if (f.ordinal == 0) "G" else f.ordinal.toString()
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (isSel) GoogleBlue.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(role = Role.Button) { onPick(f.id) }
                        .semantics { contentDescription = f.label },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) GoogleBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ---- Bottom sheets ---------------------------------------------------------------------------

@Composable
private fun ExploreSheet(state: AppSnapshot, onChangePosition: () -> Unit, onNearest: (NearbyKind) -> Unit, onPlace: (Node) -> Unit) {
    val context = LocalContext.current
    val current = state.node(state.current)
    val places = remember(state.map) {
        val floors = state.map.floors.associateBy { it.id }
        state.map.nodes.filter { !it.type.equals("LANDMARK", true) }
            .sortedWith(compareBy({ floors[it.floorId]?.buildingId }, { floors[it.floorId]?.ordinal }, { it.name }))
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = GoogleBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.you_are_at_place, current?.let { state.placeLabel(context, it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onChangePosition) { Text(stringResource(R.string.change)) }
            }
        }
        item {
            Text(stringResource(R.string.nearest_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NearbyKind.entries.forEach { kind ->
                    val (icon, tint) = when (kind) {
                        NearbyKind.WASHROOM -> Icons.Filled.Wc to Color(0xFF00897B)
                        NearbyKind.LIFT -> Icons.Filled.Elevator to Color(0xFF7B1FA2)
                        NearbyKind.EXIT -> Icons.Filled.MeetingRoom to Color(0xFF188038)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { onNearest(kind) }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = tint)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(kind.label()), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (state.map.isDemo) item {
            Text(stringResource(R.string.sample_campus_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        item { Text(stringResource(R.string.places_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
        items(places, key = { it.id }) { node ->
            ListItem(
                headlineContent = { Text(node.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · ")) },
                leadingContent = { PlaceBadge(node.type) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPlace(node) }
            )
        }
    }
}

@Composable
fun PlaceBadge(type: String, size: Dp = 40.dp) {
    val tint = placeTint(type)
    Box(Modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(placeIcon(type), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun PlaceSheet(state: AppSnapshot, node: Node, onClose: () -> Unit, onDirections: () -> Unit, onImHere: () -> Unit) {
    val context = LocalContext.current
    val reach by produceState<RouteResult?>(null, state, node.id) {
        value = withContext(Dispatchers.Default) { Routing.calculate(state, state.current, node.id) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(mobilityIcon(state.profile.mobility), contentDescription = null, tint = GoogleBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val line = when (val r = reach) {
                null -> stringResource(R.string.routing)
                is RouteResult.Found -> if (r.edges.isEmpty()) stringResource(R.string.reach_here) else stringResource(R.string.reach_ok, metres(r.distance))
                is RouteResult.Unavailable -> stringResource(R.string.reach_none)
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = if (reach is RouteResult.Unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick = onDirections, colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue, contentColor = Color.White)) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.directions))
            }
            OutlinedButton(onClick = onImHere) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.im_here))
            }
        }
    }
}

@Composable
private fun DirectionsSheet(
    ui: ScreenState, state: AppSnapshot, found: RouteResult.Found?, steps: List<RouteStep>, speaker: Speaker,
    onStart: () -> Unit, onRead: () -> Unit, onSettings: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            ui.routing && ui.route == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)); InfoText(stringResource(R.string.routing)) }
            found != null && found.edges.isEmpty() -> item {
                Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoText(stringResource(R.string.arrived_body, state.node(state.current)?.name ?: ""))
            }
            found != null -> {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(metres(found.distance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF188038))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.steps_count, steps.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    val tags = buildList {
                        when {
                            state.profile.mobility == Mobility.WHEELCHAIR -> add(stringResource(R.string.route_step_free))
                            !found.usesStairs -> add(stringResource(R.string.route_no_stairs))
                            else -> add(stringResource(R.string.route_uses_stairs))
                        }
                        if (found.usesLift) add(stringResource(R.string.route_uses_lift))
                        if (found.usesRamp) add(stringResource(R.string.route_uses_ramp))
                    }
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    found.extraDistanceVsStandard?.takeIf { it >= 1.0 }?.let { InfoText(stringResource(R.string.route_extra, metres(it))) }
                    if (found.exceedsMaxWalkingDistance) {
                        Spacer(Modifier.height(6.dp))
                        Banner(stringResource(R.string.route_over_limit, metres(state.profile.maxWalkingDistance ?: 0.0)), warning = true)
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue, contentColor = Color.White), modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start))
                        }
                        OutlinedButton(onClick = if (speaker.speaking) speaker::stop else onRead, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (speaker.speaking) R.string.stop_reading else R.string.read_aloud))
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp))
                    Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                }
                items(steps.size) { i ->
                    val step = steps[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(stepIcon(step.type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.text(context), style = MaterialTheme.typography.bodyLarge)
                            Text(metres(step.distance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (i < steps.lastIndex) HorizontalDivider(Modifier.padding(start = 40.dp))
                }
            }
            ui.route is RouteResult.Unavailable -> item { RouteProblem(ui.route, onSettings) }
        }
    }
}

@Composable
fun RouteProblem(r: RouteResult.Unavailable, onSettings: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (r.cause) {
            NoRouteCause.INVALID_ENDPOINTS -> InfoText(stringResource(R.string.no_route_endpoints))
            NoRouteCause.INVALID_PROFILE -> {
                Banner(stringResource(R.string.no_route_profile_invalid), warning = true)
                Button(onClick = onSettings) { Text(stringResource(R.string.review_settings)) }
            }
            NoRouteCause.NOT_CONNECTED -> {
                Text(stringResource(R.string.no_route_not_connected_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InfoText(stringResource(R.string.no_route_not_connected))
            }
            NoRouteCause.CLOSED_NOW -> {
                Text(stringResource(R.string.no_route_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InfoText(stringResource(R.string.no_route_closed))
                r.closures.mapNotNull { it.closureLabel() }.distinct().forEach { Text("• " + context.getString(it)) }
            }
            NoRouteCause.PROFILE_TOO_STRICT -> {
                Text(stringResource(R.string.no_route_profile_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InfoText(stringResource(R.string.no_route_profile))
                Button(onClick = onSettings) { Text(stringResource(R.string.review_settings)) }
            }
        }
    }
}

@Composable
private fun NavigateSheet(state: AppSnapshot, found: RouteResult.Found?, steps: List<RouteStep>, speaker: Speaker, onConfirm: () -> Unit, onRead: () -> Unit, onExit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (found == null || steps.isEmpty()) {
            Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            InfoText(stringResource(R.string.arrived_body, state.node(state.current)?.name ?: ""))
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.done)) }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.remaining, metres(found.distance), steps.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = if (speaker.speaking) speaker::stop else onRead) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(if (speaker.speaking) R.string.stop_reading else R.string.read_aloud))
            }
            OutlinedButton(onClick = onExit, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD93025))) { Text(stringResource(R.string.exit_navigation)) }
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue, contentColor = Color.White)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.confirm_reached, steps.first().to.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Search ----------------------------------------------------------------------------------

@Composable
private fun SearchDialog(state: AppSnapshot, onDismiss: () -> Unit, onNearest: (NearbyKind) -> Unit, onPick: (Node) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(state.map, query) {
        val floors = state.map.floors.associateBy { it.id }
        state.map.nodes
            .filter { if (query.isBlank()) !it.type.equals("LANDMARK", true) else it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ floors[it.floorId]?.buildingId }, { floors[it.floorId]?.ordinal }, { it.name }))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    TextField(
                        value = query, onValueChange = { query = it.take(80) },
                        placeholder = { Text(stringResource(R.string.search_campus)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) } },
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (query.isBlank()) item {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NearbyKind.entries.forEach { kind ->
                                AssistChip(onClick = { onNearest(kind) }, label = { Text(stringResource(kind.label())) })
                            }
                        }
                    }
                    if (results.isEmpty()) item { InfoText(stringResource(R.string.no_places)) }
                    items(results, key = { it.id }) { node ->
                        ListItem(
                            headlineContent = { Text(node.name) },
                            supportingContent = { Text(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · ")) },
                            leadingContent = { PlaceBadge(node.type, 36.dp) },
                            modifier = Modifier.clickable(role = Role.Button) { onPick(node) }
                        )
                    }
                }
            }
        }
    }
}
