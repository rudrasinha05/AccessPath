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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import kotlinx.coroutines.async
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.accesspath.data.remote.GeoResult
import com.accesspath.data.remote.Places
import com.accesspath.data.remote.Poi
import com.accesspath.data.remote.PoiCategory
import com.accesspath.data.remote.Weather
import com.accesspath.data.remote.Outdoor
import com.accesspath.data.remote.TravelMode
import com.accesspath.data.remote.metresTo
import com.accesspath.data.remote.metresToLine
import com.accesspath.ui.mapping.DeviceSensors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode { EXPLORE, PLACE, DIRECTIONS, NAVIGATE, PIN, OUT_DIRECTIONS, OUT_NAVIGATE, POI }

/** Shown when nothing is mapped and there is no GPS fix yet: all of India. */
private val DefaultView = LatLng(22.0, 79.0)

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
fun ExploreScreen(ui: ScreenState, vm: AccessViewModel, speaker: Speaker, onMapArea: (LatLng?) -> Unit, pendingPlace: com.accesspath.data.SavedPlace? = null, onPendingDone: () -> Unit = {}, onMapCenter: (LatLng) -> Unit = {}, onOpenYou: () -> Unit = {}) {
    val state = ui.snapshot ?: return
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(Mode.EXPLORE) }
    var placeId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var editProfile by rememberSaveable { mutableStateOf(false) }
    // Report a barrier from the map: the path to preselect, and the paths to list first.
    var reportEdge by rememberSaveable { mutableStateOf<String?>(null) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    var lastRoute by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    var camera by remember {
        val start = state.node(state.current)?.let { Geo.position(state.map, it) }
        mutableStateOf(CameraRequest(0, center = start ?: DefaultView, zoom = if (start != null) 18.8 else 4.5))
    }
    // Dropped pin ("lat,lng") from a long press or a world search result, and its name when it came from search.
    var pinText by rememberSaveable { mutableStateOf<String?>(null) }
    var pinTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val pin = pinText?.split(",")?.let { LatLng(it[0].toDouble(), it[1].toDouble()) }
    var orientation by remember { mutableFloatStateOf(0f) }
    var travelMode by rememberSaveable { mutableStateOf(TravelMode.WALK) }
    var outdoor by remember { mutableStateOf<OutdoorState>(OutdoorState.Idle) }
    var gps by remember { mutableStateOf<LatLng?>(null) }
    var outStep by remember { mutableIntStateOf(0) }
    var outStatus by remember { mutableStateOf<Int?>(null) }
    var routeRequest by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val hasCampus = state.map.nodes.isNotEmpty()
    val outdoorMode = mode == Mode.OUT_DIRECTIONS || mode == Mode.OUT_NAVIGATE
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { routeRequest++ }
    // Home screen: real places by category, map type, weather and the area's name.
    val local = (context.applicationContext as com.accesspath.AccessPathApplication).localPlaces
    var category by rememberSaveable { mutableStateOf<PoiCategory?>(null) }
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var poisLoading by remember { mutableStateOf(false) }
    var poisFailed by remember { mutableStateOf(false) }
    var poiReload by remember { mutableIntStateOf(0) }
    var selectedPoi by remember { mutableStateOf<Poi?>(null) }
    var mapStyle by rememberSaveable { mutableStateOf(MapStyle.DEFAULT) }
    var layers by rememberSaveable { mutableStateOf(false) }
    var mapCenter by remember { mutableStateOf<LatLng?>(null) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var area by remember { mutableStateOf<String?>(null) }
    var autoStart by remember { mutableStateOf(false) }
    var directionsFrom by remember { mutableStateOf(Mode.PIN) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchForDirections by rememberSaveable { mutableStateOf(false) }
    var savedTick by remember { mutableIntStateOf(0) }
    // Directions and navigation: real travel time per mode, reported outdoor barriers, voice and speed.
    val app = context.applicationContext as com.accesspath.AccessPathApplication
    var modeTimes by remember { mutableStateOf<Map<TravelMode, Double>>(emptyMap()) }
    var barriers by remember { mutableStateOf<List<com.accesspath.data.OutdoorBarrier>>(emptyList()) }
    var barrierTick by remember { mutableIntStateOf(0) }
    var reportAt by remember { mutableStateOf<LatLng?>(null) }
    var reportAtPin by remember { mutableStateOf(false) }
    var barrierInfo by remember { mutableStateOf<com.accesspath.data.OutdoorBarrier?>(null) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var speedKmh by remember { mutableStateOf<Int?>(null) }
    var along by remember { mutableStateOf<List<Poi>?>(null) }
    var alongLoading by remember { mutableStateOf(false) }
    var sos by rememberSaveable { mutableStateOf(false) }
    fun openSos() {
        sos = true
        // Get a fresh position for the message when location is allowed.
        if (DeviceSensors.hasLocationPermission(context)) scope.launch {
            DeviceSensors.currentLocation(context, 8_000)?.let { gps = LatLng(it.latitude, it.longitude) }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun say(text: String) { if (!muted) speaker.speak(listOf(text), context.resources.configuration.locales[0]) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { searchQuery = it; searchForDirections = false; searching = true }
    }
    fun startVoice() {
        try {
            voice.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.search_here)))
        } catch (e: android.content.ActivityNotFoundException) { vm.message(UiMessage.Text(R.string.voice_unavailable)) }
    }
    fun share(text: String) = runCatching {
        context.startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null))
    }
    fun mapLink(l: LatLng) = "https://www.openstreetmap.org/?mlat=${l.lat}&mlon=${l.lng}#map=18/${l.lat}/${l.lng}"
    fun openPoi(p: Poi) {
        selectedPoi = p; placeId = null; mode = Mode.POI
        camera = CameraRequest(++nonce, center = p.at, zoom = 17.5)
        local.addRecent(com.accesspath.data.SavedPlace(p.name, p.address, p.lat, p.lng, poi = p))
    }
    fun directionsTo(at: LatLng, title: String?, start: Boolean = false) {
        directionsFrom = if (mode == Mode.POI) Mode.POI else Mode.PIN
        pinText = "${at.lat},${at.lng}"; pinTitle = title; placeId = null
        outdoor = OutdoorState.Idle; autoStart = start; mode = Mode.OUT_DIRECTIONS
    }
    fun openSaved(sp: com.accesspath.data.SavedPlace) {
        local.addRecent(sp.copy(time = System.currentTimeMillis()))
        val p = sp.poi
        if (p != null) openPoi(p) else {
            pinText = "${sp.lat},${sp.lng}"; pinTitle = sp.name; placeId = null; mode = Mode.PIN
            camera = CameraRequest(++nonce, center = LatLng(sp.lat, sp.lng), zoom = 17.0)
        }
    }

    LaunchedEffect(pendingPlace) { pendingPlace?.let { openSaved(it); onPendingDone() } }

    val found = ui.route as? RouteResult.Found
    val steps = remember(found, state.map) { found?.let { RouteSteps.build(it, state.map) }.orEmpty() }
    val exclusions = remember(state) { Routing.exclusions(state) }
    val hasGeo = remember(state.map) { state.map.nodes.isEmpty() || Geo.hasGeo(state.map) }
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
    fun dropPin(at: LatLng, title: String? = null) {
        pinText = "${at.lat},${at.lng}"; pinTitle = title; placeId = null; mode = Mode.PIN
    }
    fun showCampus() {
        val pts = state.map.nodes.mapNotNull { Geo.position(state.map, it) }
        when {
            pts.size >= 2 -> camera = CameraRequest(++nonce, bounds = pts)
            pts.size == 1 -> camera = CameraRequest(++nonce, center = pts[0], zoom = 19.0)
            else -> vm.message(UiMessage.Text(R.string.no_campus_title))
        }
    }
    fun showMe() {
        val node = current
        if (node != null && Geo.position(state.map, node) != null && !outdoorMode) {
            if (node.floorId != state.selectedFloor) vm.selectFloor(node.floorId); moveTo(node); return
        }
        if (!DeviceSensors.hasLocationPermission(context)) {
            locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        scope.launch {
            val fix = DeviceSensors.currentLocation(context, 10_000)
            if (fix == null) vm.message(UiMessage.Text(R.string.location_off))
            else { val l = LatLng(fix.latitude, fix.longitude); gps = l; camera = CameraRequest(++nonce, center = l, zoom = 17.5) }
        }
    }
    // Nearest mapped place to the pin, within 60 m.
    val nearPin = remember(pin, state.map) {
        pin?.let { p -> state.map.nodes.mapNotNull { n -> Geo.position(state.map, n)?.let { n to it.metresTo(p) } }.filter { it.second <= 60.0 }.minByOrNull { it.second }?.first }
    }

    // Outdoor directions from the phone's location to the pin, for the chosen travel mode.
    LaunchedEffect(mode, travelMode, pinText, routeRequest) {
        if (mode != Mode.OUT_DIRECTIONS || pin == null) return@LaunchedEffect
        if (!Outdoor.supports(travelMode)) { outdoor = OutdoorState.Unsupported; return@LaunchedEffect }
        if (!DeviceSensors.hasLocationPermission(context)) { outdoor = OutdoorState.NeedLocation; return@LaunchedEffect }
        outdoor = OutdoorState.Loading
        val from = DeviceSensors.currentLocation(context, 10_000)?.let { LatLng(it.latitude, it.longitude) } ?: gps
        if (from == null) { outdoor = OutdoorState.Failed; vm.message(UiMessage.Text(R.string.location_off)); return@LaunchedEffect }
        gps = from
        // Reported barriers around the trip decide which of the route choices to use.
        val mid = LatLng((from.lat + pin.lat) / 2, (from.lng + pin.lng) / 2)
        val known = runCatching { app.barriers.near(mid, (from.metresTo(pin) / 2 + 1500).toInt().coerceAtMost(30_000)) }.getOrElse { app.barriers.local() }
        barriers = (known + barriers).distinctBy { it.id }
        outdoor = try { chooseRoute(Outdoor.routes(travelMode, from, pin), barriers) } catch (e: AppError) { OutdoorState.Failed }
        if (autoStart && outdoor is OutdoorState.Ready) { autoStart = false; mode = Mode.OUT_NAVIGATE }
        (outdoor as? OutdoorState.Ready)?.route?.line?.takeIf { it.size >= 2 }?.let { camera = CameraRequest(++nonce, bounds = it + pin) }
    }
    // Real travel time of every mode for the mode tabs, fetched together.
    LaunchedEffect(pinText, mode == Mode.OUT_DIRECTIONS, gps != null) {
        if (mode != Mode.OUT_DIRECTIONS || pin == null) return@LaunchedEffect
        val from = gps ?: return@LaunchedEffect
        val to = pin
        modeTimes = kotlinx.coroutines.coroutineScope {
            listOf(TravelMode.CAR, TravelMode.BIKE, TravelMode.WALK, TravelMode.WHEELCHAIR).filter(Outdoor::supports)
                .map { m -> async { runCatching { m to Outdoor.route(m, from, to).durationSec }.getOrNull() } }
                .mapNotNull { it.await() }.toMap()
        }
    }
    // Outdoor navigation: follow live GPS, move to the next instruction, speak it, warn about reported
    // barriers ahead, and re-route (around barriers) when off the line.
    LaunchedEffect(mode) {
        if (mode != Mode.OUT_NAVIGATE) return@LaunchedEffect
        outStep = 0; outStatus = null; along = null
        var lastReroute = 0L
        var spokenStep = -1; var spokenNear = -1
        val warned = mutableSetOf<String>()
        (outdoor as? OutdoorState.Ready)?.route?.steps?.firstOrNull()?.let { say(it.text(context)); spokenStep = 0 }
        DeviceSensors.locationUpdates(context).collect { loc ->
            val here = LatLng(loc.latitude, loc.longitude)
            gps = here
            speedKmh = if (loc.hasSpeed()) Math.round(loc.speed * 3.6f) else null
            camera = CameraRequest(++nonce, center = here, zoom = 18.0)
            val dest = pin ?: return@collect
            val ready = outdoor as? OutdoorState.Ready ?: return@collect
            val r = ready.route
            if (here.metresTo(dest) < 20.0) {
                if (outStatus != R.string.out_arrived) say(context.getString(R.string.out_arrived))
                outStatus = R.string.out_arrived; return@collect
            }
            outStatus = null
            while (outStep + 1 < r.steps.size && here.metresTo(r.steps[outStep + 1].location) < 20.0) outStep++
            // Voice: the coming turn once early, and again just before it.
            r.steps.getOrNull(outStep + 1)?.let { next ->
                val d = here.metresTo(next.location)
                if (spokenStep != outStep + 1 && d < 400) { spokenStep = outStep + 1; say(context.getString(R.string.in_distance, distanceText(context, d), next.text(context))) }
                else if (spokenNear != outStep + 1 && d < 40) { spokenNear = outStep + 1; say(next.text(context)) }
            }
            // Reported barriers on the route within 150 m.
            ready.barriersOnRoute.firstOrNull { it.id !in warned && here.metresTo(it.at) < 150 }?.let { b ->
                warned += b.id
                val text = context.getString(R.string.barrier_ahead, context.getString(b.kind.label()))
                vm.message(UiMessage.Text(R.string.barrier_ahead, listOf(context.getString(b.kind.label())))); say(text)
            }
            val now = System.currentTimeMillis()
            if (here.metresToLine(r.line) > 50.0 && now - lastReroute > 15_000) {
                lastReroute = now; outStatus = R.string.out_rerouting
                runCatching { chooseRoute(Outdoor.routes(travelMode, here, dest), barriers) }.onSuccess { outdoor = it; outStep = 0; outStatus = null }
            }
        }
    }
    // The ongoing notification ("120 m · Turn left …", Exit navigation) while navigating.
    val navReady = outdoor as? OutdoorState.Ready
    val navNext = navReady?.route?.steps?.let { it.getOrNull(outStep + 1) ?: it.getOrNull(outStep) }
    val navRemaining = gps?.let { g -> pin?.let { g.metresTo(it) } }?.let { straight ->
        navReady?.route?.let { r -> r.steps.drop(outStep + 2).sumOf { it.distance } + (navNext?.let { gps!!.metresTo(it.location) } ?: 0.0) }?.coerceAtLeast(straight) ?: straight
    }
    val navRemainingSec = navReady?.route?.let { r -> navRemaining?.let { if (r.distance > 0) r.durationSec * (it / r.distance).coerceAtMost(1.5) else null } }
    DisposableEffect(mode == Mode.OUT_NAVIGATE) {
        if (mode == Mode.OUT_NAVIGATE) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                runCatching { notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            com.accesspath.nav.NavSession.start(context, com.accesspath.nav.NavInfo("", context.getString(R.string.out_waiting_gps), pinTitle.orEmpty()))
        }
        onDispose { if (mode == Mode.OUT_NAVIGATE) com.accesspath.nav.NavSession.stop(context) }
    }
    LaunchedEffect(navNext, navRemaining?.let { Math.round(it / 10) }, outStatus) {
        if (mode != Mode.OUT_NAVIGATE) return@LaunchedEffect
        val g = gps ?: return@LaunchedEffect
        com.accesspath.nav.NavSession.update(com.accesspath.nav.NavInfo(
            navNext?.let { distanceText(context, g.metresTo(it.location)) } ?: "",
            outStatus?.let(context::getString) ?: navNext?.text(context).orEmpty(),
            navRemainingSec?.let { context.getString(R.string.nav_eta, duration(context, it), arrivalTime(it)) } ?: pinTitle.orEmpty()))
    }
    LaunchedEffect(Unit) { com.accesspath.nav.NavSession.exitRequests.collect { speaker.stop(); if (mode == Mode.OUT_NAVIGATE) mode = Mode.OUT_DIRECTIONS } }
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
    // While navigating, tell the person when the route changes for a reason other than their own progress.
    LaunchedEffect(found?.edges, mode) {
        val now = found?.let { state.current to it.edges.map(Edge::id) }
        val before = lastRoute
        lastRoute = now
        if (mode == Mode.NAVIGATE && before != null && now != null && before.first == now.first && before.second != now.second) {
            vm.message(UiMessage.Text(R.string.route_updated))
        }
        if (mode == Mode.NAVIGATE && found == null && ui.route is RouteResult.Unavailable && before != null) {
            vm.message(UiMessage.Text(R.string.route_lost))
        }
    }
    // While navigating, follow the confirmed position.
    LaunchedEffect(state.current, mode) { if (mode == Mode.NAVIGATE) moveTo(current, 20.3) }

    // Nearby places of the chosen category, around the middle of the map.
    LaunchedEffect(category, poiReload) {
        pois = emptyList(); poisFailed = false
        val c = category ?: return@LaunchedEffect
        val center = mapCenter ?: gps ?: return@LaunchedEffect
        poisLoading = true
        try { pois = Places.nearby(c, center, if (c == PoiCategory.ACCESSIBLE || c == PoiCategory.TOILETS) 2500 else 1500) }
        catch (e: AppError) { poisFailed = true }
        finally { poisLoading = false }
    }
    // Weather, air quality and the area's name, refreshed when the map moves about 2 km.
    val areaAt = gps ?: mapCenter
    val areaKey = areaAt?.let { "${Math.round(it.lat * 50)}/${Math.round(it.lng * 50)}" }
    LaunchedEffect(areaKey) {
        val at = areaAt ?: return@LaunchedEffect
        delay(700)
        runCatching { Places.weather(at) }.getOrNull()?.let { weather = it }
        runCatching { Places.areaName(at) }.getOrNull()?.let { area = it }
    }

    // Reported outdoor barriers around the visible area.
    LaunchedEffect(areaKey, barrierTick) {
        val at = areaAt ?: return@LaunchedEffect
        runCatching { app.barriers.near(at) }.getOrNull()?.let { near -> barriers = (near + barriers.filter { it.at.metresTo(at) > 5_000 }).distinctBy { it.id } }
    }

    BackHandler(enabled = mode != Mode.EXPLORE || category != null) {
        if (mode == Mode.EXPLORE) { category = null; return@BackHandler }
        mode = when (mode) {
            Mode.NAVIGATE -> Mode.DIRECTIONS; Mode.DIRECTIONS -> if (placeId != null) Mode.PLACE else Mode.EXPLORE
            Mode.OUT_NAVIGATE -> Mode.OUT_DIRECTIONS; Mode.OUT_DIRECTIONS -> if (directionsFrom == Mode.POI && selectedPoi != null) Mode.POI else Mode.PIN
            else -> Mode.EXPLORE
        }
        if (mode == Mode.EXPLORE) { placeId = null; pinText = null; selectedPoi = null }
    }

    // Small phones (about 640 dp tall): a lower sheet and fewer buttons so nothing covers the map controls.
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700
    val peek: Dp = when (mode) {
        Mode.NAVIGATE -> 176.dp; Mode.OUT_NAVIGATE -> 104.dp
        Mode.EXPLORE -> if (short) (if (category != null) 220.dp else 200.dp) else if (category != null) 260.dp else 240.dp
        Mode.PIN -> 200.dp; Mode.OUT_DIRECTIONS -> 280.dp; Mode.POI -> 290.dp; else -> 236.dp
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerDp = with(density) { headerPx.toDp() }
    // Keep the camera target in the visible part of the map, between the top overlay and the sheet.
    val centerOffsetPx = with(density) { ((headerPx - peek.roundToPx()) / 2f).toInt() }
    // First camera move once the layout (and so the visible map area) is known.
    LaunchedEffect(headerPx > 0) {
        if (headerPx <= 0 || mode != Mode.EXPLORE) return@LaunchedEffect
        val target = placeId?.let(state::node) ?: current
        if (target != null && Geo.position(state.map, target) != null) { moveTo(target, 19.0); return@LaunchedEffect }
        // Nothing mapped yet: start from the phone's position when location is already allowed.
        if (DeviceSensors.hasLocationPermission(context)) DeviceSensors.currentLocation(context, 8_000)?.let {
            val l = LatLng(it.latitude, it.longitude); gps = l; camera = CameraRequest(++nonce, center = l, zoom = 17.0)
        }
    }
    // When the first campus data arrives (a camera survey, an import or a published map), show it.
    var hadCampus by remember { mutableStateOf(hasCampus) }
    LaunchedEffect(hasCampus) {
        if (hasCampus && !hadCampus && headerPx > 0 && mode == Mode.EXPLORE) moveTo(current, 19.0)
        hadCampus = hasCampus
    }
    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = peek,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 640.dp).padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("sheet")) {
                when (mode) {
                    Mode.EXPLORE -> Column {
                        AreaHeader(area, weather)
                        val c = category
                        when {
                            c != null -> PoiList(c, pois, poisLoading, poisFailed, gps ?: mapCenter, onRetry = { poiReload++ }, onPick = ::openPoi)
                            !hasCampus -> EmptyCampusCard(onMapArea = { onMapArea(null) })
                            else -> ExploreSheet(state, onChangePosition = { picking = "from" }, onNearest = { kind -> placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }, onPlace = ::select)
                        }
                    }
                    Mode.POI -> selectedPoi?.let { p ->
                        val sp = com.accesspath.data.SavedPlace(p.name, p.address, p.lat, p.lng, poi = p)
                        val saved = remember(savedTick, p.id) { local.isSaved(sp) }
                        PoiSheet(p, gps ?: mapCenter, saved,
                            onDirections = { directionsTo(p.at, p.name) },
                            onStart = { directionsTo(p.at, p.name, start = true) },
                            onShare = { share(listOf(p.name, p.address, mapLink(p.at)).filter { it.isNotBlank() }.joinToString("\n")) },
                            onSave = { val now = local.toggleSaved(sp); savedTick++; vm.message(UiMessage.Text(if (now) R.string.place_saved else R.string.place_unsaved)) },
                            onCall = { ph -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$ph"))) } },
                            onWebsite = { w -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(if (w.startsWith("http")) w else "https://$w"))) } },
                            onClose = { selectedPoi = null; mode = Mode.EXPLORE })
                    } ?: LaunchedEffect(Unit) { mode = Mode.EXPLORE }
                    Mode.PLACE -> if (place != null) {
                        PlaceSheet(state, place, onClose = { placeId = null; mode = Mode.EXPLORE },
                            onReport = { reportEdge = state.map.edges.firstOrNull { it.from == place.id || it.to == place.id }?.id; reporting = true },
                            onDirections = { startDirections(place.id) }, onImHere = { vm.setCurrent(place.id); placeId = null; mode = Mode.EXPLORE })
                    } else LaunchedEffect(Unit) { mode = Mode.EXPLORE }
                    Mode.DIRECTIONS -> DirectionsSheet(ui, state, found, steps, speaker,
                        onStart = { mode = Mode.NAVIGATE; moveTo(current, 20.3) },
                        onRead = { read(context, speaker, steps, vm) },
                        onSettings = { editProfile = true })
                    Mode.NAVIGATE -> NavigateSheet(state, found, ui.route as? RouteResult.Unavailable, steps, speaker,
                        onSettings = { editProfile = true },
                        onReport = { reportEdge = found?.edges?.firstOrNull()?.id; reporting = true },
                        onConfirm = { steps.firstOrNull()?.let { vm.setCurrent(it.to.id) } },
                        onRead = { read(context, speaker, steps.take(1), vm) },
                        onExit = { speaker.stop(); mode = Mode.EXPLORE; placeId = null })
                    Mode.PIN -> if (pin != null) PinSheet(pin, pinTitle, nearPin?.name, hasCampus,
                        onDirections = { directionsFrom = Mode.PIN; outdoor = OutdoorState.Idle; mode = Mode.OUT_DIRECTIONS },
                        onViewCampus = ::showCampus, onMapArea = { onMapArea(pin) },
                        onReport = { reportAtPin = true; reportAt = pin },
                        onZoomIn = { camera = CameraRequest(++nonce, zoomStep = 1) },
                        onZoomOut = { camera = CameraRequest(++nonce, zoomStep = -1) },
                        onClose = { pinText = null; mode = Mode.EXPLORE }) else LaunchedEffect(Unit) { mode = Mode.EXPLORE }
                    Mode.OUT_DIRECTIONS -> if (pin != null) {
                        val destName = pinTitle ?: nearPin?.name ?: formatLatLng(pin)
                        val sp = com.accesspath.data.SavedPlace(destName, selectedPoi?.address ?: formatLatLng(pin), pin.lat, pin.lng, poi = selectedPoi?.takeIf { it.at == pin })
                        val saved = remember(savedTick, pinText) { local.isSaved(sp) }
                        OutdoorDirectionsSheet(outdoor, travelMode, destName, saved,
                            onAllowLocation = { locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            onRetry = { routeRequest++ },
                            onStart = { mode = Mode.OUT_NAVIGATE },
                            onOpenTransit = { runCatching { context.startActivity(Outdoor.transitIntent(pin)) } },
                            onPickRoute = { i -> (outdoor as? OutdoorState.Ready)?.let { outdoor = it.copy(index = i) } },
                            onShare = {
                                val r = (outdoor as? OutdoorState.Ready)?.route
                                share(if (r != null) context.getString(R.string.directions_message, destName, duration(context, r.durationSec), distanceText(context, r.distance), mapLink(pin)) else mapLink(pin))
                            },
                            onSave = { val now = local.toggleSaved(sp); savedTick++; vm.message(UiMessage.Text(if (now) R.string.place_saved else R.string.place_unsaved)) })
                    } else LaunchedEffect(Unit) { mode = Mode.EXPLORE }
                    Mode.OUT_NAVIGATE -> {
                        val r = (outdoor as? OutdoorState.Ready)?.route
                        OutdoorNavSheet(navRemaining, navRemainingSec, r, outStep, muted, mapStyle == MapStyle.SATELLITE, along, alongLoading, gps,
                            onExit = { speaker.stop(); mode = Mode.OUT_DIRECTIONS },
                            onReport = { reportAtPin = false; reportAt = gps ?: mapCenter },
                            onShareProgress = {
                                val g = gps
                                if (g != null && navRemainingSec != null) share(context.getString(R.string.progress_message, pinTitle ?: formatLatLng(pin ?: g),
                                    arrivalTime(navRemainingSec), duration(context, navRemainingSec), mapLink(g)))
                                else g?.let { share(context.getString(R.string.share_my_location, mapLink(it))) }
                            },
                            onAlong = { c ->
                                val line = r?.line.orEmpty(); val g = gps ?: line.firstOrNull()
                                if (g != null) scope.launch {
                                    alongLoading = true; along = null
                                    // Search the rest of the route: around you and every 2 km along the line (up to 5 spots),
                                    // one request at a time as the OpenStreetMap search service asks.
                                    val spots = mutableListOf(g)
                                    line.forEach { p -> if (spots.size < 5 && p.metresTo(spots.last()) >= 2000) spots += p }
                                    pin?.let { if (spots.size < 6 && it.metresTo(spots.last()) > 800) spots += it }
                                    val found = mutableListOf<Poi>()
                                    spots.forEachIndexed { i, p ->
                                        if (i > 0) delay(1_100)
                                        found += runCatching { Places.nearby(c, p, 1200) }.getOrDefault(emptyList())
                                    }
                                    along = found.distinctBy { it.id }.filter { line.size < 2 || it.at.metresToLine(line) <= 400 }.sortedBy { g.metresTo(it.at) }
                                    alongLoading = false
                                }
                            },
                            onGoAlong = { p -> along = null; speaker.stop(); directionsTo(p.at, p.name, start = true) },
                            onSatellite = { mapStyle = if (it) MapStyle.SATELLITE else MapStyle.DEFAULT },
                            onMute = { muted = it; if (it) speaker.stop() },
                            onSettings = { editProfile = true },
                            expanded = sheetState.bottomSheetState.currentValue == SheetValue.Expanded,
                            onToggleMenu = { scope.launch { if (sheetState.bottomSheetState.currentValue == SheetValue.Expanded) sheetState.bottomSheetState.partialExpand() else sheetState.bottomSheetState.expand() } })
                    }
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
                    pin = pin.takeIf { mode == Mode.PIN || outdoorMode },
                    outdoorRoute = if (outdoorMode) (outdoor as? OutdoorState.Ready)?.route?.line.orEmpty() else emptyList(),
                    altRoutes = (outdoor as? OutdoorState.Ready)?.takeIf { mode == Mode.OUT_DIRECTIONS }?.let { o -> o.routes.filterIndexed { i, _ -> i != o.index }.map { it.line } }.orEmpty(),
                    onAltTap = { i -> (outdoor as? OutdoorState.Ready)?.let { o -> outdoor = o.copy(index = o.routes.indices.filter { it != o.index }[i]) } },
                    barriers = barriers,
                    onBarrierTap = { barrierInfo = it },
                    onLongPress = { if (mode != Mode.NAVIGATE && mode != Mode.OUT_NAVIGATE) dropPin(it) },
                    onOrientation = { orientation = it },
                    onCenter = { mapCenter = it; onMapCenter(it) },
                    myLocation = gps.takeIf { outdoorMode || !hasCampus },
                    mapStyle = mapStyle,
                    pois = when (mode) { Mode.EXPLORE, Mode.POI -> (pois + listOfNotNull(selectedPoi)).distinctBy { it.id }; Mode.OUT_NAVIGATE -> along.orEmpty(); else -> emptyList() },
                    selectedPoi = selectedPoi?.id.takeIf { mode == Mode.POI },
                    onPoiTap = { if (mode == Mode.OUT_NAVIGATE) { along = null; directionsTo(it.at, it.name, start = true) } else if (mode != Mode.NAVIGATE) openPoi(it) },
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
                    Mode.OUT_NAVIGATE -> {
                        val r = (outdoor as? OutdoorState.Ready)?.route
                        val step = r?.steps?.getOrNull(outStep + 1) ?: r?.steps?.getOrNull(outStep)
                        OutdoorNavBanner(step, step?.let { s -> gps?.metresTo(s.location) }, r?.steps?.getOrNull(outStep + 2),
                            outStatus?.let { stringResource(it) } ?: if (gps == null) stringResource(R.string.out_waiting_gps) else null)
                    }
                    Mode.OUT_DIRECTIONS -> Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { mode = if (directionsFrom == Mode.POI && selectedPoi != null) Mode.POI else Mode.PIN }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EndpointBox(Icons.Filled.MyLocation, GoogleBlue, stringResource(R.string.out_from_here)) {}
                                    EndpointBox(Icons.Filled.Place, Color(0xFFEA4335), pinTitle ?: nearPin?.name ?: pin?.let(::formatLatLng).orEmpty()) { mode = Mode.PIN }
                                }
                            }
                            Box(Modifier.padding(start = 8.dp, top = 6.dp)) { TravelModeChips(travelMode, modeTimes) { travelMode = it } }
                        }
                    }
                    Mode.DIRECTIONS -> DirectionsHeader(state,
                        onBack = { mode = if (placeId != null) Mode.PLACE else Mode.EXPLORE },
                        onFrom = { picking = "from" }, onTo = { picking = "to" }, onSwap = vm::swapEnds,
                        onMobility = { vm.setProfile(state.profile.copy(mobility = it)) }, onOptions = { editProfile = true })
                    else -> {
                        HomeSearchBar(ui.account?.email?.firstOrNull()?.uppercase(),
                            onSearch = { searchQuery = ""; searchForDirections = false; searching = true },
                            onVoice = ::startVoice, onCamera = { onMapArea(null) }, onProfile = onOpenYou)
                        Spacer(Modifier.height(10.dp))
                        CategoryChips(state.profile.mobility, category, onMobility = { editProfile = true },
                            onCategory = { category = it; if (mode == Mode.POI || mode == Mode.PIN) { selectedPoi = null; pinText = null; mode = Mode.EXPLORE } })
                        // On small screens the sheet's "Nearest accessible" buttons do the same job.
                        if (hasCampus && !short) Spacer(Modifier.height(8.dp))
                        if (hasCampus && !short) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NearbyKind.entries.forEach { kind ->
                                val icon = when (kind) { NearbyKind.WASHROOM -> Icons.Filled.Wc; NearbyKind.LIFT -> Icons.Filled.Elevator; NearbyKind.EXIT -> Icons.Filled.MeetingRoom }
                                MapChip(stringResource(kind.label()), icon) { placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }
                            }
                        }
                    }
                }
            }

            // Right-side controls: floor picker under the top overlay, "show my position" above the sheet.
            // Floor picker on the left so it never collides with the right-hand controls on short screens.
            if (!outdoorMode) Box(Modifier.align(Alignment.TopStart).padding(top = headerDp + 4.dp, start = 12.dp)) {
                FloorPicker(state) { vm.selectFloor(it) }
            }
            // One column above the sheet, like Google Maps. Zoom buttons are left out while the tall directions
            // header is shown (pinch zoom still works) so the controls never cover it on short screens.
            // Top right: map type and compass.
            val browsing = mode == Mode.EXPLORE || mode == Mode.POI || mode == Mode.PIN || mode == Mode.PLACE
            if (hasGeo && browsing) Column(Modifier.align(Alignment.TopEnd).padding(top = headerDp + 4.dp, end = 12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MapRoundButton(Icons.Filled.Layers, stringResource(R.string.map_type), tint = BrandTeal, container = BrandTealSoft, modifier = Modifier.testTag("layers")) { layers = true }
                // Compass only while the map is turned, like other map apps.
                if (Math.abs(orientation % 360f) > 1f) CompassButton(orientation) { camera = CameraRequest(++nonce, northUp = true) }
                // Zoom buttons only where there is room for them; pinch and the pin menu always zoom too.
                if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 720)
                    ZoomButtons(onIn = { camera = CameraRequest(++nonce, zoomStep = 1) }, onOut = { camera = CameraRequest(++nonce, zoomStep = -1) })
            }
            // Bottom right, above the sheet: zoom, share, my location and directions.
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = peek + if (short) 8.dp else 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 10.dp)) {
                if (hasGeo && mode == Mode.OUT_NAVIGATE) {
                    CompassButton(orientation) { camera = CameraRequest(++nonce, northUp = true) }
                    MapRoundButton(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(R.string.voice_guidance), modifier = Modifier.testTag("muteNav")) { muted = !muted; if (muted) speaker.stop() }
                    MapRoundButton(Icons.Filled.AddAlert, stringResource(R.string.add_report), tint = Color.White, container = Color(0xFFE37400), modifier = Modifier.testTag("reportNav")) {
                        reportAtPin = false; reportAt = gps ?: mapCenter
                    }
                }
                // Share only where there is room; small screens keep the map clear (the place card has Share too).
                val tall = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 720
                if (browsing && tall) MapRoundButton(Icons.Filled.Share, stringResource(R.string.share)) {
                    (gps ?: mapCenter)?.let { share(context.getString(R.string.share_my_location, mapLink(it))) }
                }
                MapRoundButton(Icons.Filled.MyLocation, stringResource(R.string.recenter), tint = BrandBlue, onClick = ::showMe)
                if (mode == Mode.EXPLORE) MapRoundButton(Icons.Filled.Directions, stringResource(R.string.directions), tint = Color.White, container = BrandTeal, modifier = Modifier.testTag("directionsFab")) {
                    searchQuery = ""; searchForDirections = true; searching = true
                }
            }
            if (mode != Mode.OUT_NAVIGATE && mode != Mode.NAVIGATE) Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = peek + 30.dp)) { SosButton(::openSos) }
            if (mode == Mode.OUT_NAVIGATE) speedKmh?.let { k ->
                Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = peek + 28.dp)) { SpeedBubble(k) }
            }
            if (hasGeo) {
                val online = rememberIsOnline()
                Text(
                    if (online) mapAttribution() else stringResource(R.string.map_offline),
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF3C4043),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = peek + 6.dp)
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }

    if (searching) {
        var recents by remember { mutableStateOf(local.recents()) }
        var home by remember { mutableStateOf(local.home()) }
        var work by remember { mutableStateOf(local.work()) }
        fun go(sp: com.accesspath.data.SavedPlace) {
            searching = false
            if (searchForDirections) { local.addRecent(sp.copy(time = System.currentTimeMillis())); directionsTo(LatLng(sp.lat, sp.lng), sp.name) } else openSaved(sp)
        }
        fun setHere(isHome: Boolean) {
            val at = gps ?: mapCenter ?: return
            val sp = com.accesspath.data.SavedPlace(context.getString(if (isHome) R.string.home else R.string.work), formatLatLng(at), at.lat, at.lng)
            if (isHome) { local.setHome(sp); home = sp } else { local.setWork(sp); work = sp }
            vm.message(UiMessage.Text(if (isHome) R.string.home_set else R.string.work_set))
        }
        SearchDialog(state, near = gps ?: mapCenter ?: current?.let { Geo.position(state.map, it) }, initialQuery = searchQuery,
            title = if (searchForDirections) R.string.choose_destination else R.string.search_here,
            recents = recents, home = home, work = work,
            onHome = { home?.let(::go) ?: setHere(true) }, onWork = { work?.let(::go) ?: setHere(false) },
            onRecent = ::go, onClearRecents = { local.clearRecents(); recents = emptyList() },
            onCategory = { c -> searching = false; category = c; mode = Mode.EXPLORE },
            onGeo = { r -> go(com.accesspath.data.SavedPlace(r.name, r.detail, r.at.lat, r.at.lng)) },
            onDismiss = { searching = false }, onNearest = { kind -> searching = false; placeId = null; vm.goToNearest(kind); mode = Mode.DIRECTIONS }) { node -> searching = false; select(node) }
    }
    if (sos) SosSheet(gps?.let(::mapLink), hasCampus, onDismiss = { sos = false },
        onNearestHospital = {
            val at = gps ?: mapCenter
            sos = false
            if (at == null) vm.message(UiMessage.Text(R.string.location_off)) else scope.launch {
                val all = runCatching { Places.nearby(PoiCategory.HOSPITALS, at, 5_000) }.getOrDefault(emptyList())
                val h = all.firstOrNull { it.tags["type"] == "hospital" || it.tags["amenity"] == "hospital" } ?: all.firstOrNull()
                if (h == null) vm.message(UiMessage.Text(R.string.places_failed)) else { selectedPoi = h; mode = Mode.POI; directionsTo(h.at, h.name) }
            }
        },
        onNearestExit = { sos = false; placeId = null; vm.goToNearest(NearbyKind.EXIT); mode = Mode.DIRECTIONS })
    reportAt?.let { at ->
        ReportBarrierDialog(signedIn = app.supabase.session != null, atPin = reportAtPin, onDismiss = { reportAt = null }) { kind, note ->
            reportAt = null
            scope.launch {
                val shared = app.barriers.report(at, kind, note)
                vm.message(UiMessage.Text(if (shared) R.string.barrier_reported else R.string.barrier_reported_local))
                barriers = (app.barriers.local() + barriers).distinctBy { it.id }
                // Pick again with the new report, so the route goes around it when another way exists.
                (outdoor as? OutdoorState.Ready)?.let { o -> outdoor = chooseRoute(o.routes, barriers) }
                barrierTick++
            }
        }
    }
    barrierInfo?.let { b ->
        BarrierInfoDialog(b, gps, onDismiss = { barrierInfo = null }) {
            barrierInfo = null
            scope.launch {
                app.barriers.clear(b)
                barriers = barriers.filterNot { it.id == b.id }
                (outdoor as? OutdoorState.Ready)?.let { o -> outdoor = chooseRoute(o.routes, barriers) }
                vm.message(UiMessage.Text(R.string.barrier_removed))
            }
        }
    }
    if (layers) LayersSheet(mapStyle, category, onStyle = { mapStyle = it }, onCategory = { category = it; layers = false; mode = Mode.EXPLORE }, onDismiss = { layers = false })
    when (picking) {
        "from" -> PlacePickerDialog(state, stringResource(R.string.pick_from_title), { picking = null }) { picking = null; vm.setCurrent(it.id); moveTo(it) }
        "to" -> PlacePickerDialog(state, stringResource(R.string.pick_to_title), { picking = null }) { picking = null; startDirections(it.id) }
    }
    if (reporting) {
        ReportDialog(state, found?.edges.orEmpty(), onDismiss = { reporting = false }, preselect = reportEdge) { id, edge, category, text ->
            reporting = false
            vm.submitReport(id, edge, category, text)
        }
    }
    if (editProfile) ProfileDialog(state.profile, { editProfile = false }) { editProfile = false; vm.setProfile(it) }
}

private fun read(context: android.content.Context, speaker: Speaker, steps: List<RouteStep>, vm: AccessViewModel) {
    val locale = context.resources.configuration.locales[0]
    if (!speaker.speak(steps.map { it.text(context) }, locale)) vm.message(UiMessage.Text(R.string.tts_unavailable))
}

// ---- Top overlays ----------------------------------------------------------------------------

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
private fun PlaceSheet(state: AppSnapshot, node: Node, onClose: () -> Unit, onReport: () -> Unit, onDirections: () -> Unit, onImHere: () -> Unit) {
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
        val closures = remember(state, node.id) {
            val ex = Routing.exclusions(state)
            state.map.edges.filter { it.from == node.id || it.to == node.id }.mapNotNull { ex[it.id] }.filter { it.isClosure }.mapNotNull { it.closureLabel() }.distinct()
        }
        closures.forEach { Banner(stringResource(it), warning = true) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick = onDirections, colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue, contentColor = Color.White)) {
                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.directions))
            }
            OutlinedButton(onClick = onImHere) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.im_here))
            }
            OutlinedButton(onClick = onReport) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.report_short))
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
private fun NavigateSheet(state: AppSnapshot, found: RouteResult.Found?, problem: RouteResult.Unavailable?, steps: List<RouteStep>, speaker: Speaker, onSettings: () -> Unit, onReport: () -> Unit, onConfirm: () -> Unit, onRead: () -> Unit, onExit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (problem != null) {
            RouteProblem(problem, onSettings)
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.exit_navigation)) }
            return@Column
        }
        if (found == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        if (steps.isEmpty()) {
            Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            InfoText(stringResource(R.string.arrived_body, state.node(state.current)?.name ?: ""))
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.done)) }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.remaining, metres(found.distance), steps.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onReport) { Icon(Icons.Filled.Flag, contentDescription = stringResource(R.string.report_title)) }
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
private fun SearchDialog(
    state: AppSnapshot, near: LatLng?, initialQuery: String, title: Int,
    recents: List<com.accesspath.data.SavedPlace>, home: com.accesspath.data.SavedPlace?, work: com.accesspath.data.SavedPlace?,
    onHome: () -> Unit, onWork: () -> Unit, onRecent: (com.accesspath.data.SavedPlace) -> Unit, onClearRecents: () -> Unit,
    onCategory: (PoiCategory) -> Unit,
    onGeo: (GeoResult) -> Unit, onDismiss: () -> Unit, onNearest: (NearbyKind) -> Unit, onPick: (Node) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var geo by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var geoError by remember { mutableStateOf<Int?>(null) }
    // Real-world places (MapTiler), searched shortly after typing stops.
    LaunchedEffect(query) {
        geo = emptyList(); geoError = null
        if (query.isBlank()) return@LaunchedEffect
        if (!Outdoor.canSearch) { geoError = R.string.search_needs_key; return@LaunchedEffect }
        delay(400)
        try { geo = Outdoor.search(query, near) } catch (e: AppError) { geoError = R.string.search_failed }
    }
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
                        placeholder = { Text(stringResource(title)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) } },
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (query.isBlank()) {
                        // Home and Work, like the shortcuts in other map apps; "Set location" uses where you are now.
                        item {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 8.dp)) {
                                ShortcutCell(Icons.Filled.Home, stringResource(R.string.home), home?.detail ?: stringResource(R.string.set_location), Modifier.weight(1f), onHome)
                                VerticalDivider()
                                ShortcutCell(Icons.Filled.Work, stringResource(R.string.work), work?.detail ?: stringResource(R.string.set_location), Modifier.weight(1f), onWork)
                            }
                            HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Inside a mapped campus the nearest washroom, lift and exit come first.
                                if (state.map.nodes.isNotEmpty()) NearbyKind.entries.forEach { kind ->
                                    AssistChip(onClick = { onNearest(kind) }, label = { Text(stringResource(kind.label())) })
                                }
                                listOf(PoiCategory.ACCESSIBLE, PoiCategory.TOILETS, PoiCategory.HOSPITALS, PoiCategory.RESTAURANTS, PoiCategory.ATMS, PoiCategory.PETROL).forEach { c ->
                                    AssistChip(onClick = { onCategory(c) }, label = { Text(stringResource(c.label())) }, leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                        if (recents.isNotEmpty()) item {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = onClearRecents) { Text(stringResource(R.string.clear_recents)) }
                            }
                        }
                        items(recents.size) { i ->
                            val r = recents[i]
                            ListItem(
                                headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { if (r.detail.isNotBlank()) Text(r.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.History, null) } },
                                modifier = Modifier.clickable(role = Role.Button) { onRecent(r) }.testTag("recent")
                            )
                        }
                    }
                    if (query.isNotBlank() && results.isNotEmpty()) item { SearchHeader(stringResource(R.string.campus_results)) }
                    items(results, key = { it.id }) { node ->
                        ListItem(
                            headlineContent = { Text(node.name) },
                            supportingContent = { Text(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · ")) },
                            leadingContent = { PlaceBadge(node.type, 36.dp) },
                            modifier = Modifier.clickable(role = Role.Button) { onPick(node) }
                        )
                    }
                    if (query.isNotBlank()) {
                        if (geo.isNotEmpty()) item { SearchHeader(stringResource(R.string.world_results)) }
                        items(geo.size) { i ->
                            val g = geo[i]
                            ListItem(
                                headlineContent = { Text(g.name) },
                                supportingContent = { if (g.detail.isNotBlank()) Text(g.detail, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { PlaceBadge("LANDMARK", 36.dp) },
                                modifier = Modifier.clickable(role = Role.Button) { onGeo(g) }.testTag("geoResult")
                            )
                        }
                        geoError?.let { item { Box(Modifier.padding(16.dp)) { InfoText(stringResource(it)) } } }
                        if (results.isEmpty() && geo.isEmpty() && geoError == null) item { Box(Modifier.padding(16.dp)) { InfoText(stringResource(R.string.no_places)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCell(icon: ImageVector, title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(BrandTealSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = BrandTeal) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchHeader(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))

/** Whether the phone has a working internet connection, updated as it changes. Only the map background needs it. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(android.net.ConnectivityManager::class.java) }
    fun now(): Boolean = cm?.getNetworkCapabilities(cm.activeNetwork)
        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    var online by remember { mutableStateOf(now()) }
    DisposableEffect(cm) {
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { online = now() }
            override fun onLost(network: android.net.Network) { online = now() }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { online = now() }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    return online
}
