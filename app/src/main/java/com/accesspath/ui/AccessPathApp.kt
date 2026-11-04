package com.accesspath.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesspath.R
import com.accesspath.domain.Geo
import com.accesspath.domain.LatLng
import com.accesspath.ui.mapping.DeviceSensors
import com.accesspath.ui.mapping.MappingScreen
import com.accesspath.ui.mapping.UnmappedAreaCheck
import com.accesspath.ui.theme.AccessPathTheme
import kotlinx.coroutines.launch

private enum class Tab(val label: Int, val icon: ImageVector) {
    EXPLORE(R.string.tab_explore, Icons.Filled.Explore),
    CONTRIBUTE(R.string.tab_contribute, Icons.Filled.AddLocationAlt),
    YOU(R.string.tab_you, Icons.Filled.AccountCircle)
}

@Composable
fun AccessPathApp(vm: AccessViewModel) {
    AccessPathTheme {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableStateOf(Tab.EXPLORE) }
        var managing by rememberSaveable { mutableStateOf(false) }
        // "lat,lng" of where camera mapping started; null when not mapping.
        var mappingAt by rememberSaveable { mutableStateOf<String?>(null) }
        var heading by remember { mutableDoubleStateOf(0.0) }
        val scope = rememberCoroutineScope()

        fun startMapping(at: LatLng) { mappingAt = "${at.lat},${at.lng}" }
        fun startMappingHere() = scope.launch {
            val fix = DeviceSensors.currentLocation(context, 8_000)?.takeIf { it.hasAccuracy() && it.accuracy <= 60f }
            val s = ui.snapshot
            val at = fix?.let { LatLng(it.latitude, it.longitude) }
                ?: s?.let { st -> st.node(st.current)?.let { Geo.position(st.map, it) } }
                ?: s?.map?.geo?.let { LatLng(it.lat, it.lng) }
            if (at != null) startMapping(at) else vm.message(UiMessage.Text(R.string.mapping_no_position))
        }
        val speaker = rememberSpeaker()

        LaunchedEffect(ui.message) {
            ui.message?.let {
                vm.message(null)
                snackbar.showSnackbar(it.text(context), withDismissAction = true)
            }
        }
        // Keep the watched places (nearby alerts) in step with the campus data. Cheap when nothing changed.
        LaunchedEffect(ui.snapshot) {
            val snap = ui.snapshot ?: return@LaunchedEffect
            kotlinx.coroutines.delay(1_000)
            val fences = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.accesspath.domain.FencePlanner.plan(snap) }
            runCatching { com.accesspath.geofence.Geofencing.update(context, fences) }
        }
        LaunchedEffect(mappingAt) { if (mappingAt != null) heading = DeviceSensors.heading(context) ?: 0.0 }

        mappingAt?.let { at ->
            val (lat, lng) = at.split(",").map { it.toDouble() }
            MappingScreen(onClose = { mappingAt = null }) { trail, places ->
                mappingAt = null
                vm.saveSurvey(trail, places, LatLng(lat, lng), heading)
            }
            return@AccessPathTheme
        }

        BackHandler(enabled = tab != Tab.EXPLORE || managing) { if (managing) managing = false else tab = Tab.EXPLORE }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t; managing = false },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        ) { padding ->
            // The map runs under the status bar like Google Maps; other tabs keep clear of it.
            val inner = if (tab == Tab.EXPLORE) Modifier.padding(bottom = padding.calculateBottomPadding())
                else Modifier.padding(padding).statusBarsPadding()
            Box(Modifier.fillMaxSize().then(inner)) {
                when {
                    ui.loadError -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Banner(stringResource(R.string.error_generic), warning = true)
                    }
                    ui.snapshot == null -> Column(
                        Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.loading))
                    }
                    else -> when (tab) {
                        Tab.EXPLORE -> ExploreScreen(ui, vm, speaker, onMapArea = { at -> if (at != null) startMapping(at) else startMappingHere() })
                        Tab.CONTRIBUTE -> ReportsScreen(ui, vm)
                        Tab.YOU -> if (managing) ManageScreen(ui, vm, onBack = { managing = false })
                            else SettingsScreen(ui, vm, onManage = { managing = true }, onMapNewArea = { startMappingHere() })
                    }
                }
            }
        }
        UnmappedAreaCheck(ui.snapshot, vm.areaChecked, vm::markAreaChecked, onStartMapping = ::startMapping)
    }
}
