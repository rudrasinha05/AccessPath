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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.accesspath.domain.SurveyPlace
import com.accesspath.domain.TrailPoint
import com.accesspath.ui.formatNumber
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.delay

private enum class ArState { CHECKING, NEEDS_PERMISSION, DENIED, INSTALLING, UNSUPPORTED, READY }

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
fun MappingScreen(onClose: () -> Unit, onFinish: (List<TrailPoint>, List<SurveyPlace>) -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.activity() }
    var arState by remember { mutableStateOf(ArState.CHECKING) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        arState = if (granted) ArState.CHECKING else ArState.DENIED
    }

    // Permission first, then make sure Google Play Services for AR is present.
    fun check() {
        if (!DeviceSensors.hasCameraPermission(context)) { arState = if (asked) ArState.DENIED else ArState.NEEDS_PERMISSION; return }
        val act = activity ?: run { arState = ArState.UNSUPPORTED; return }
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
    LaunchedEffect(Unit) { check() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && arState == ArState.INSTALLING) check() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler(onBack = onClose)

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
                    ArState.UNSUPPORTED -> R.string.mapping_unsupported
                    else -> R.string.loading
                }
                Text(stringResource(msg), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                if (arState == ArState.NEEDS_PERMISSION) {
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
                }
                if (arState == ArState.CHECKING || arState == ArState.INSTALLING) CircularProgressIndicator(color = Color.White)
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
private fun AddPlaceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("ROOM") }
    val types = listOf("ROOM" to R.string.type_room, "WASHROOM" to R.string.type_washroom, "ELEVATOR" to R.string.type_lift,
        "ENTRANCE" to R.string.type_entrance, "STAIRS" to R.string.type_stairs, "LANDMARK" to R.string.type_other)
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
