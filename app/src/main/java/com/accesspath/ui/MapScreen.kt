@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.accesspath.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accesspath.domain.*

@Composable
fun MapScreen(
    state: AppSnapshot,
    vm: AccessViewModel,
    ui: ScreenState,
    lang: String,
    onNavigateTo: (Node) -> Unit
) {
    var selectedNode by remember { mutableStateOf<Node?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    var showWorldwideDialog by rememberSaveable { mutableStateOf(false) }
    var showGeofenceDialog by rememberSaveable { mutableStateOf(false) }
    var showLayerMenu by rememberSaveable { mutableStateOf(false) }

    // Google Maps View states
    var is3DMode by rememberSaveable { mutableStateOf(false) }
    var bearing by rememberSaveable { mutableFloatStateOf(0f) }

    // Freehand Fencing states
    var isFreehandDrawing by rememberSaveable { mutableStateOf(false) }
    val freehandPoints = remember { mutableStateListOf<GeofencePoint>() }
    var showFreehandSaveDialog by rememberSaveable { mutableStateOf(false) }

    val currentPosNode = state.map.nodes.find { it.id == state.current }

    // Real-time Search Autocomplete Filter
    val matchingNodes = remember(searchQuery, selectedCategory, state.map.nodes, state.selectedFloor) {
        if (searchQuery.isBlank() && selectedCategory == "ALL") {
            emptyList()
        } else {
            state.map.nodes.filter { node ->
                val matchesCategory = when (selectedCategory) {
                    "ROOMS" -> node.type.contains("ROOM", ignoreCase = true) || node.name.contains("Room", ignoreCase = true) || node.name.contains("Class", ignoreCase = true) || node.name.contains("Lab", ignoreCase = true)
                    "LIFTS" -> node.type.contains("ELEVATOR", ignoreCase = true) || node.name.contains("Lift", ignoreCase = true)
                    "RAMPS" -> node.type.contains("RAMP", ignoreCase = true) || node.name.contains("Ramp", ignoreCase = true)
                    "EXITS" -> node.type.contains("ENTRANCE", ignoreCase = true) || node.name.contains("Exit", ignoreCase = true) || node.name.contains("Gate", ignoreCase = true)
                    else -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    node.name.contains(searchQuery, ignoreCase = true) ||
                    node.id.contains(searchQuery, ignoreCase = true) ||
                    node.type.contains(searchQuery, ignoreCase = true)
                }
                matchesCategory && matchesSearch
            }.take(8)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. Core Google Maps Interactive Engine (Full-screen, Pannable, Zoomable, Rotatable) ---
        CampusCanvas(
            s = state,
            route = ui.route as? RouteResult.Found,
            guidedStepIndex = null,
            visitedNodes = emptySet(),
            selectedNode = selectedNode,
            onNodeSelected = { selectedNode = it },
            onStyleChange = { vm.setMapStyle(it) },
            modifier = Modifier.fillMaxSize(),
            isFreehandMode = isFreehandDrawing,
            freehandPoints = freehandPoints.toList(),
            onFreehandPointAdded = { pt -> freehandPoints.add(pt) },
            onFreehandDrawingFinished = {
                if (freehandPoints.size >= 3) {
                    showFreehandSaveDialog = true
                }
            },
            is3DMode = is3DMode,
            bearing = bearing,
            onBearingChange = { bearing = it }
        )

        // --- 2. Top Google Maps Search Bar & Autocomplete ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Google Maps Floating Search Bar Pill
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Google Maps G / Search Icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("G", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    }

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (lang == "hi") "कमरा, लैब, लिफ्ट या रैंप खोजें..." else "Search here / Find rooms & lifts",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Mic Icon
                    IconButton(onClick = { /* Voice search trigger */ }, modifier = Modifier.size(32.dp)) {
                        Text("🎙️", fontSize = 16.sp)
                    }

                    // Avatar Circle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("♿", fontSize = 14.sp)
                        }
                    }
                }
            }

            // Autocomplete Results Dropdown
            AnimatedVisibility(visible = searchQuery.isNotBlank() && matchingNodes.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        matchingNodes.forEach { node ->
                            Surface(
                                onClick = {
                                    selectedNode = node
                                    searchQuery = ""
                                    if (state.selectedFloor != node.floorId) {
                                        vm.action { vm.repository.floor(node.floorId) }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    when {
                                                        node.type.contains("ELEVATOR", ignoreCase = true) -> "🛗"
                                                        node.type.contains("RAMP", ignoreCase = true) -> "♿"
                                                        node.type.contains("STAIRS", ignoreCase = true) -> "🪜"
                                                        node.type.contains("ENTRANCE", ignoreCase = true) || node.name.contains("Gate", ignoreCase = true) -> "🚪"
                                                        node.type.contains("WASHROOM", ignoreCase = true) || node.name.contains("Washroom", ignoreCase = true) -> "🚻"
                                                        else -> "📍"
                                                    },
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(node.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Floor ${node.floorId} • ${node.type.lowercase().replace('_', ' ')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "Inspect",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Google Maps Vertical Indoor Floor / Level Picker (Left Side) ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.map.floors.reversed().forEach { f ->
                    val isActive = state.selectedFloor == f.id
                    Surface(
                        onClick = { vm.action { vm.repository.floor(f.id) } },
                        shape = CircleShape,
                        color = if (isActive) Color(0xFF1A73E8) else Color.Transparent,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                f.label.take(2).uppercase(),
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Google Maps Floating Tools HUD (Top-Right / Right Side) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Google Maps Layer Button (Iconic Stacked Layers FAB)
            Surface(
                onClick = { showLayerMenu = !showLayerMenu },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🥞", fontSize = 20.sp) // Google Layers icon representation
                }
            }

            // Google Maps Compass Button (rotates with bearing, tap to reset north)
            Surface(
                onClick = { bearing = 0f },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "🧭",
                        fontSize = 22.sp,
                        modifier = Modifier.rotate(-bearing)
                    )
                }
            }

            // 3D / 2D Perspective View Toggle Button
            Surface(
                onClick = { is3DMode = !is3DMode },
                shape = CircleShape,
                color = if (is3DMode) Color(0xFF1A73E8) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (is3DMode) "2D" else "3D",
                        fontWeight = FontWeight.Black,
                        color = if (is3DMode) Color.White else Color(0xFF1A73E8),
                        fontSize = 14.sp
                    )
                }
            }

            // Freehand Fencing FAB (Activates Draw Mode)
            Surface(
                onClick = {
                    isFreehandDrawing = !isFreehandDrawing
                    if (isFreehandDrawing) {
                        freehandPoints.clear()
                    }
                },
                shape = CircleShape,
                color = if (isFreehandDrawing) Color(0xFFEF4444) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isFreehandDrawing) "❌" else "✏️",
                        fontSize = 20.sp
                    )
                }
            }

            // Worldwide Campuses Directory FAB
            Surface(
                onClick = { showWorldwideDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌍", fontSize = 20.sp)
                }
            }

            // Geofence Zones List FAB with Count Badge
            BadgedBox(
                badge = {
                    if (state.geofences.isNotEmpty()) {
                        Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                            Text("${state.geofences.size}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                Surface(
                    onClick = { showGeofenceDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛡️", fontSize = 20.sp)
                    }
                }
            }
        }

        // --- 5. Google Maps Bottom-Right Navigation Actions (Recenter & Zoom) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = if (selectedNode != null) 250.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Google Maps Recenter FAB
            Surface(
                onClick = {
                    val cur = state.map.nodes.find { it.id == state.current }
                    if (cur != null) {
                        selectedNode = cur
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎯", fontSize = 24.sp)
                }
            }
        }

        // --- 6. Freehand Fencing Drawing Mode HUD (When Active) ---
        if (isFreehandDrawing) {
            // Top guidance banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎨", fontSize = 18.sp)
                    Text(
                        "Freehand Draw Mode: Trace any boundary on the screen. (${freehandPoints.size} pts)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Bottom Drawing Actions Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (freehandPoints.isNotEmpty()) freehandPoints.removeLast() },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("↩️ Undo")
                    }
                    OutlinedButton(
                        onClick = { freehandPoints.clear() },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("🗑️ Clear")
                    }
                    Button(
                        onClick = {
                            if (freehandPoints.size >= 3) {
                                showFreehandSaveDialog = true
                            }
                        },
                        enabled = freehandPoints.size >= 3,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("✅ Complete Fence (${freehandPoints.size})")
                    }
                }
            }
        }

        // --- 7. Google Maps Official Layers & Views Dialog / Drawer ---
        if (showLayerMenu) {
            AlertDialog(
                onDismissRequest = { showLayerMenu = false },
                title = { Text("🗺️ Google Maps Views & Layers", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Map Type (नक्शे का प्रकार):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        listOf(
                            MapStyle.DEFAULT_VECTOR to ("🗺️ Default (डिफ़ॉल्ट)" to "Clean Google vector map with roads & buildings"),
                            MapStyle.SATELLITE to ("🛰️ Satellite (सैटेलाइट)" to "Photorealistic aerial satellite photography"),
                            MapStyle.TERRAIN to ("⛰️ Terrain (टेरेन)" to "Topographic contour lines and elevation relief"),
                            MapStyle.HYBRID to ("🌐 Hybrid (हाइब्रिड)" to "Satellite imagery with street network and labels"),
                            MapStyle.ACCESSIBILITY_HEATMAP to ("🌡️ Slope Heatmap" to "Wheelchair slope safety gradient (Green/Orange/Red)"),
                            MapStyle.NIGHT_CYBER to ("⚡ Night OLED" to "High-contrast dark OLED mode")
                        ).forEach { (style, meta) ->
                            val (title, desc) = meta
                            val isSelected = state.mapStyle == style
                            Surface(
                                onClick = {
                                    vm.setMapStyle(style)
                                    showLayerMenu = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF1A73E8).copy(alpha = 0.15f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF1A73E8)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(title, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold, color = if (isSelected) Color(0xFF1A73E8) else MaterialTheme.colorScheme.onSurface)
                                    Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("Map Details (नक्शे के विवरण):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🏢 3D Buildings & Tilt", fontWeight = FontWeight.SemiBold)
                            Switch(checked = is3DMode, onCheckedChange = { is3DMode = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLayerMenu = false }) { Text("Close") }
                }
            )
        }

        // --- 8. Landmark Details Card (Google Maps Place Sheet) ---
        if (selectedNode != null && !isFreehandDrawing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                LandmarkDetailsCard(
                    node = selectedNode!!,
                    currentPos = currentPosNode,
                    lang = lang,
                    onNavigate = { targetNode ->
                        onNavigateTo(targetNode)
                        selectedNode = null
                    },
                    onClose = { selectedNode = null }
                )
            }
        }
    }

    // --- 9. Save Freehand Geofence Dialog ---
    if (showFreehandSaveDialog) {
        FreehandGeofenceSaveDialog(
            points = freehandPoints.toList(),
            currentFloor = state.selectedFloor,
            onSave = { zone ->
                vm.addGeofence(zone)
                showFreehandSaveDialog = false
                isFreehandDrawing = false
                freehandPoints.clear()
            },
            onDismiss = {
                showFreehandSaveDialog = false
            }
        )
    }

    // Worldwide Campuses Explorer Dialog
    if (showWorldwideDialog) {
        WorldwideCampusExplorerDialog(
            s = state,
            onDismiss = { showWorldwideDialog = false },
            onSelectCampus = { campusId ->
                vm.switchCampus(campusId)
                showWorldwideDialog = false
            },
            onPublishCurrent = {
                vm.publishCampusToWorld()
            }
        )
    }

    // Geofencing Safety Perimeters Manager Dialog
    if (showGeofenceDialog) {
        GeofenceManagerDialog(
            s = state,
            onDismiss = { showGeofenceDialog = false },
            onAddGeofence = { zone -> vm.addGeofence(zone) },
            onDeleteGeofence = { id -> vm.deleteGeofence(id) }
        )
    }
}
