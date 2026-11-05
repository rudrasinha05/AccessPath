package com.accesspath.ui.mapping

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.accesspath.R
import com.accesspath.domain.GpsTrailRecorder
import com.accesspath.domain.PathSimplifier
import com.accesspath.domain.SignReader
import com.accesspath.domain.SignSuggestion
import com.accesspath.domain.LatLng
import com.accesspath.domain.SurveyPlace
import com.accesspath.domain.TrailPoint
import com.accesspath.ui.formatNumber
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.delay

private enum class ArState { CHECKING, NEEDS_PERMISSION, DENIED, NEEDS_INSTALL, INSTALLING, UNSUPPORTED, READY }

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Full-screen camera mapping. Records the walked path with ARCore and lets the surveyor name places.
 * [onFinish] receives the trail and named places; nothing is saved unless the person taps Finish.
 */
@Composable
fun MappingScreen(onClose: () -> Unit, onFinish: (List<TrailPoint>, List<SurveyPlace>, LatLng?, Double?, String) -> Unit) {
    // AI walk (camera reads signs + blue dot) is the main way; walking without camera and ARCore tracking are options.
    var method by rememberSaveable { mutableStateOf<String?>(null) }
    // The walk waits here while the person names the area.
    var finished by remember { mutableStateOf<(String) -> Unit>({}) }
    var naming by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    when (method) {
        null -> MethodChooser(onClose, onAi = { method = "ai" }, onCamera = { method = "camera" }, onGps = { method = "gps" })
        "ai", "gps" -> GpsRecording(onClose, aiCamera = method == "ai", onCameraDenied = { method = "gps" }) { t, p, start ->
            finished = { name -> onFinish(t, p, start, 0.0, name) }; naming = true
        }
        else -> CameraMapping(onClose, onUseGps = { method = "gps" }) { t, p -> finished = { name -> onFinish(t, p, null, null, name) }; naming = true }
    }
    if (naming) NameAreaDialog(onDismiss = { naming = false }) { name -> naming = false; finished(name) }
}

/** Last step of mapping: name the area, then it is sent for an admin to verify. */
@Composable
private fun NameAreaDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.name_area_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.name_area_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("areaName"))
                Text(stringResource(R.string.name_area_review), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSend(name.trim()) }, modifier = Modifier.testTag("sendForReview")) { Text(stringResource(R.string.send_for_review)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CameraMapping(onClose: () -> Unit, onUseGps: () -> Unit, onFinish: (List<TrailPoint>, List<SurveyPlace>) -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.activity() }
    var arState by remember { mutableStateOf(ArState.CHECKING) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var recheck by remember { mutableIntStateOf(0) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        arState = if (granted) ArState.CHECKING else ArState.DENIED
    }

    // Permission first, then make sure Google Play Services for AR is present.
    fun check() {
        if (!DeviceSensors.hasCameraPermission(context)) { arState = if (asked) ArState.DENIED else ArState.NEEDS_PERMISSION; return }
        val act = activity ?: run { arState = ArState.UNSUPPORTED; return }
        // Ask Android first; only open the Play Store when the person chooses to install ARCore.
        val availability = runCatching { ArCoreApk.getInstance().checkAvailability(context) }.getOrNull()
        if (availability == null || availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) { arState = ArState.UNSUPPORTED; return }
        if (availability.isTransient) { recheck++; return } // Android is still finding out; ask again shortly
        if (!installRequested && availability != ArCoreApk.Availability.SUPPORTED_INSTALLED && !availability.isTransient) { arState = ArState.NEEDS_INSTALL; return }
        arState = try {
            when (ArCoreApk.getInstance().requestInstall(act, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALLED -> ArState.READY
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; ArState.INSTALLING }
                else -> ArState.UNSUPPORTED
            }
        } catch (e: Exception) {
            Log.w("MappingScreen", "ARCore unavailable", e); ArState.UNSUPPORTED
        }
    }
    LaunchedEffect(recheck) { if (recheck > 0) { delay(300); check() } }
    // Runs on open and again after the camera permission answer (which sets CHECKING).
    LaunchedEffect(arState) { if (arState == ArState.CHECKING) check() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && arState == ArState.INSTALLING) check() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (arState) {
            ArState.READY -> ArRecording(onClose, onFinish) { arState = ArState.UNSUPPORTED }
            else -> Column(
                Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.mapping_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val msg = when (arState) {
                    ArState.NEEDS_PERMISSION -> R.string.mapping_camera_needed
                    ArState.DENIED -> R.string.mapping_camera_denied
                    ArState.INSTALLING -> R.string.mapping_installing
                    ArState.NEEDS_INSTALL -> R.string.mapping_needs_arcore
                    ArState.UNSUPPORTED -> R.string.mapping_unsupported
                    else -> R.string.mapping_checking
                }
                Text(stringResource(msg), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                if (arState == ArState.NEEDS_PERMISSION) {
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
                }
                if (arState == ArState.CHECKING || arState == ArState.INSTALLING) CircularProgressIndicator(color = Color.White)
                if (arState == ArState.NEEDS_INSTALL) {
                    Button(onClick = { installRequested = false; arState = ArState.INSTALLING; activity?.let { a ->
                        arState = try {
                            if (ArCoreApk.getInstance().requestInstall(a, true) == ArCoreApk.InstallStatus.INSTALLED) ArState.READY
                            else { installRequested = true; ArState.INSTALLING }
                        } catch (e: Exception) { ArState.UNSUPPORTED }
                    } }) { Text(stringResource(R.string.mapping_install_arcore)) }
                    Spacer(Modifier.height(8.dp))
                }
                // Location mapping is always one tap away when the camera can't be used.
                if (arState == ArState.UNSUPPORTED || arState == ArState.DENIED || arState == ArState.NEEDS_INSTALL || arState == ArState.INSTALLING) {
                    OutlinedButton(onClick = onUseGps, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(stringResource(R.string.mapping_use_gps)) }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.close), color = Color.White) }
            }
        }
    }
}

@Composable
private fun ArRecording(onClose: () -> Unit, onFinish: (List<TrailPoint>, List<SurveyPlace>) -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val recorder = remember { TrailRecorder() }
    val session = remember {
        runCatching {
            Session(context).also { s ->
                s.configure(Config(s).apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                })
            }
        }.onFailure { Log.w("MappingScreen", "Could not start AR session", it) }.getOrNull()
    }
    if (session == null) { LaunchedEffect(Unit) { onFailed() }; return }

    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(ArCameraRenderer(session, { ContextCompat.getDisplayOrDefault(context).rotation }, recorder))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    DisposableEffect(lifecycle) {
        fun resume() = runCatching { session.resume(); glView.onResume() }.onFailure { Log.w("MappingScreen", "AR resume failed", it); onFailed() }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> { glView.onPause(); session.pause() }
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            glView.onPause(); session.pause(); session.close()
        }
    }

    var tracking by remember { mutableStateOf(false) }
    var distance by remember { mutableDoubleStateOf(0.0) }
    var points by remember { mutableIntStateOf(0) }
    var places by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var tooShort by remember { mutableStateOf(false) }
    LaunchedEffect(recorder) {
        while (true) {
            val (t, p, d) = recorder.snapshot()
            tracking = recorder.tracking; distance = d; points = t.size; places = p.size
            delay(300)
        }
    }

    AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (points > 1) confirmDiscard = true else onClose() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.mapping_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(when { paused -> R.string.mapping_status_paused; tracking -> R.string.mapping_status_tracking; else -> R.string.mapping_status_limited }),
                        style = MaterialTheme.typography.bodySmall, color = if (tracking || paused) Color.White.copy(alpha = 0.85f) else Color(0xFFFDD663)
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.mapping_stats, stringResource(R.string.metres, formatNumber(distance)), points, places), style = MaterialTheme.typography.titleMedium)
                if (tooShort) Text(stringResource(R.string.mapping_too_short), color = Color(0xFFFDD663), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { addingPlace = true }, enabled = points > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_place))
                    }
                    OutlinedButton(onClick = { paused = !paused; recorder.paused = paused }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = stringResource(if (paused) R.string.resume else R.string.pause))
                    }
                }
                Button(onClick = {
                    val (t, p, _) = recorder.snapshot()
                    if (t.size < 2) tooShort = true
                    else onFinish(t, p.map { (i, nt) -> SurveyPlace(i, nt.first, nt.second) })
                }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.finish))
                }
            }
        }
    }

    if (addingPlace) AddPlaceDialog(onDismiss = { addingPlace = false }) { name, type -> addingPlace = false; recorder.addPlace(name, type) }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_title)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun AddPlaceDialog(onDismiss: () -> Unit, initialName: String = "", initialType: String = "ROOM", onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    val types = listOf("ROOM" to R.string.type_room, "WASHROOM" to R.string.type_washroom, "ELEVATOR" to R.string.type_lift,
        "ENTRANCE" to R.string.type_entrance, "EXIT" to R.string.type_exit, "RAMP" to R.string.type_ramp,
        "STAIRS" to R.string.type_stairs, "POINT" to R.string.type_other)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_place)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.place_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.place_type), style = MaterialTheme.typography.labelLarge)
                FlowRowChips(types.map { it.first to stringResource(it.second) }, type) { type = it }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), type) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) -> FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label) }) }
    }
}

@Composable
private fun MethodChooser(onClose: () -> Unit, onAi: () -> Unit, onCamera: () -> Unit, onGps: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0F2238)).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.mapping_choose_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        MethodCard(Icons.Filled.AutoAwesome, stringResource(R.string.mapping_method_ai), stringResource(R.string.mapping_method_ai_desc), onAi, recommended = true)
        MethodCard(Icons.Filled.MyLocation, stringResource(R.string.mapping_method_gps), stringResource(R.string.mapping_method_gps_desc), onGps)
        MethodCard(Icons.Filled.CameraAlt, stringResource(R.string.mapping_method_camera), stringResource(R.string.mapping_method_camera_desc), onCamera)
        Text(stringResource(R.string.mapping_review_note), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close), color = Color.White) }
    }
}

@Composable
private fun MethodCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit, recommended: Boolean = false) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = if (recommended) 0.18f else 0.10f), contentColor = Color.White,
        border = if (recommended) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8AB4F8)) else null, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color(0xFF8AB4F8))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (recommended) Text(stringResource(R.string.recommended), color = Color(0xFF8AB4F8), style = MaterialTheme.typography.labelMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

/**
 * Mapping by walking with location: every good fix of the blue dot becomes a point of the path. Works on any phone;
 * indoors location is weaker than the camera, so weak fixes are skipped and the accuracy is shown.
 */
@Composable
private fun GpsRecording(onClose: () -> Unit, aiCamera: Boolean = false, onCameraDenied: () -> Unit = {}, onFinish: (List<TrailPoint>, List<SurveyPlace>, LatLng) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { GpsTrailRecorder() }
    // AI: camera permission, what the camera read, and the place it suggests.
    var cameraOk by remember { mutableStateOf(DeviceSensors.hasCameraPermission(context)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> cameraOk = ok; if (!ok) onCameraDenied() }
    LaunchedEffect(aiCamera) { if (aiCamera && !cameraOk) cameraLauncher.launch(Manifest.permission.CAMERA) }
    var suggestion by remember { mutableStateOf<SignSuggestion?>(null) }
    val seen = remember { mutableMapOf<SignSuggestion, Int>() }
    val handled = remember { mutableSetOf<SignSuggestion>() }
    var editing by remember { mutableStateOf<SignSuggestion?>(null) }
    fun onSignText(lines: List<String>) {
        SignReader.read(lines).forEach { s ->
            if (s in handled) return@forEach
            val n = (seen[s] ?: 0) + 1; seen[s] = n
            // Seen in two readings in a row before it is offered, so one blurry frame does not count.
            if (n >= 2 && suggestion == null) suggestion = s
        }
    }
    var granted by remember { mutableStateOf(DeviceSensors.hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> granted = r.values.any { it } }
    var accuracy by remember { mutableStateOf<Double?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var tooShort by remember { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (!granted) { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return@LaunchedEffect }
        DeviceSensors.locationUpdates(context).collect { loc ->
            val acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 99.0
            accuracy = acc
            if (recorder.add(LatLng(loc.latitude, loc.longitude), acc)) version++
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F2238)).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (recorder.size > 1) confirmDiscard = true else onClose() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White) }
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(if (aiCamera) R.string.mapping_method_ai else R.string.mapping_method_gps), color = Color.White, fontWeight = FontWeight.Bold)
                val acc = accuracy
                Text(
                    when {
                        !granted -> stringResource(R.string.out_need_location)
                        paused -> stringResource(R.string.mapping_status_paused)
                        acc == null -> stringResource(R.string.gps_status_waiting)
                        acc > GpsTrailRecorder.MAX_ACCURACY_M -> stringResource(R.string.gps_status_poor, formatNumber(acc))
                        else -> stringResource(R.string.gps_status_accuracy, formatNumber(acc))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (acc != null && acc > GpsTrailRecorder.MAX_ACCURACY_M) Color(0xFFFDD663) else Color.White.copy(alpha = 0.85f)
                )
            }
        }
        if (aiCamera && cameraOk) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).testTag("aiCamera")) {
            SignCamera(Modifier.fillMaxSize(), onText = ::onSignText)
            Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = Color(0xFF8AB4F8)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_reading_signs), style = MaterialTheme.typography.labelMedium)
                }
            }
            suggestion?.let { s ->
                Surface(color = Color.White, contentColor = Color(0xFF0F2238), shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().testTag("aiSuggestion")) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ai_found), style = MaterialTheme.typography.labelMedium, color = Color(0xFF1A73E8))
                            Text(s.name, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { handled += s; suggestion = null }) { Text(stringResource(R.string.not_now)) }
                        Button(enabled = recorder.size > 0, onClick = { editing = s; suggestion = null }) { Text(stringResource(R.string.add)) }
                    }
                }
            }
        }
        // The walked path, drawn to fit: a straightened blue line with the blue dot at the newest point.
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))) {
            val raw = remember(version) { recorder.trail() }
            val placeIdxRaw = remember(version) { recorder.places.map { it.trailIndex }.toSet() }
            // Show the walk as it will be saved: only the bends and the places.
            val trail = remember(version) { if (raw.size > 2) PathSimplifier.keep(raw, placeIdxRaw + raw.lastIndex).map { raw[it] } else raw }
            val placeIdx = remember(version) {
                val kept = if (raw.size > 2) PathSimplifier.keep(raw, placeIdxRaw + raw.lastIndex) else raw.indices.toList()
                placeIdxRaw.mapNotNull { kept.indexOf(it).takeIf { k -> k >= 0 } }.toSet()
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                if (trail.isEmpty()) return@Canvas
                val xs = trail.map { it.x }; val ys = trail.map { -it.z }
                val span = maxOf(xs.max() - xs.min(), ys.max() - ys.min(), 10.0)
                val scale = (minOf(size.width, size.height) / span).toFloat()
                val cx = ((xs.max() + xs.min()) / 2).toFloat(); val cy = ((ys.max() + ys.min()) / 2).toFloat()
                fun pt(i: Int) = androidx.compose.ui.geometry.Offset(size.width / 2 + (xs[i].toFloat() - cx) * scale, size.height / 2 - (ys[i].toFloat() - cy) * scale)
                for (i in 1 until trail.size) drawLine(Color(0xFF8AB4F8), pt(i - 1), pt(i), strokeWidth = 10f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                placeIdx.forEach { drawCircle(Color(0xFFFFB070), 14f, pt(it)) }
                drawCircle(Color.White, 20f, pt(trail.lastIndex)); drawCircle(Color(0xFF1A73E8), 14f, pt(trail.lastIndex))
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.08f), contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val v = version // re-read the counts when a point is kept
                Text(stringResource(R.string.mapping_stats, stringResource(R.string.metres, formatNumber(recorder.distance + v * 0.0)), recorder.size, recorder.places.size), style = MaterialTheme.typography.titleMedium)
                if (tooShort) Text(stringResource(R.string.mapping_too_short), color = Color(0xFFFDD663), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { addingPlace = true }, enabled = recorder.size > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_place))
                    }
                    OutlinedButton(onClick = { paused = !paused; recorder.paused = paused }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = stringResource(if (paused) R.string.resume else R.string.pause))
                    }
                }
                Button(onClick = {
                    val start = recorder.start
                    if (recorder.size < 2 || start == null) tooShort = true else onFinish(recorder.trail(), recorder.places, start)
                }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.finish))
                }
            }
        }
    }
    if (addingPlace) AddPlaceDialog(onDismiss = { addingPlace = false }) { name, type -> addingPlace = false; recorder.addPlace(name, type); version++ }
    // An AI suggestion opens the same form, filled in, so the surveyor can correct the name before adding.
    editing?.let { s ->
        AddPlaceDialog(onDismiss = { editing = null; handled += s }, initialName = s.name, initialType = s.type) { name, type ->
            editing = null; handled += s; recorder.addPlace(name, type); version++
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_title)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } }
    )
}
