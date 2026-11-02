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

    val currentPosNode = state.map.nodes.find { it.id == state.current }

    // Filter nodes by category and search query
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
        // 1. Core Map Canvas Engine (Spans entire background with pinch/pan/zoom)
        CampusCanvas(
            s = state,
            route = ui.route as? RouteResult.Found,
            guidedStepIndex = null,
            visitedNodes = emptySet(),
            selectedNode = selectedNode,
            onNodeSelected = { selectedNode = it },
            onStyleChange = { vm.setMapStyle(it) }
        )

        // 2. Top Floating Controls: Search Bar, Category Chips & Floor Switcher
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Input Surface
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    if (lang == "hi") "कमरा, लैब, लिफ्ट या रैंप खोजें..." else "Search rooms, labs, elevators, ramps...",
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
                    }

                    // Autocomplete Results Dropdown
                    AnimatedVisibility(visible = searchQuery.isNotBlank() && matchingNodes.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
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

            // Quick Category Filters & Floor Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Floor Chips
                state.map.floors.forEach { f ->
                    val isFloorActive = state.selectedFloor == f.id
                    FilterChip(
                        selected = isFloorActive,
                        onClick = { vm.action { vm.repository.floor(f.id) } },
                        label = {
                            Text(
                                "${state.map.buildings.find { it.id == f.buildingId }?.name ?: "Building"} · ${f.label}",
                                fontWeight = if (isFloorActive) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Category Quick Filters
                listOf(
                    "ALL" to "🌐 All",
                    "ROOMS" to "🚪 Rooms & Labs",
                    "LIFTS" to "🛗 Lifts",
                    "RAMPS" to "♿ Ramps",
                    "EXITS" to "🚪 Exits"
                ).forEach { (catId, catLabel) ->
                    val isCatActive = selectedCategory == catId
                    FilterChip(
                        selected = isCatActive,
                        onClick = { selectedCategory = if (isCatActive) "ALL" else catId },
                        label = { Text(catLabel, fontWeight = if (isCatActive) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // 3. Floating HUD Control Panel (Top-Right: Layers, Worldwide, Geofences)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 135.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Layer Switcher Floating Button
            Surface(
                onClick = { showLayerMenu = !showLayerMenu },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when (state.mapStyle) {
                            MapStyle.BLUEPRINT -> "📐"
                            MapStyle.SATELLITE_HYBRID -> "🛰️"
                            MapStyle.ACCESSIBILITY_HEATMAP -> "🌡️"
                            MapStyle.NIGHT_CYBER -> "⚡"
                        },
                        fontSize = 20.sp
                    )
                }
            }

            // Worldwide Campuses Explorer Button
            Surface(
                onClick = { showWorldwideDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌍", fontSize = 20.sp)
                }
            }

            // Geofences Manager Button with live count badge
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛡️", fontSize = 20.sp)
                    }
                }
            }
        }

        // Layer Style Quick Switcher Drawer / Bubble
        AnimatedVisibility(
            visible = showLayerMenu,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 190.dp, end = 68.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Map Layer Views",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    listOf(
                        MapStyle.BLUEPRINT to ("📐 Blueprint CAD" to "Architectural layout"),
                        MapStyle.SATELLITE_HYBRID to ("🛰️ Satellite & Terrain" to "Outdoor & paths"),
                        MapStyle.ACCESSIBILITY_HEATMAP to ("🌡️ Accessibility Heatmap" to "Slope gradient safety"),
                        MapStyle.NIGHT_CYBER to ("⚡ Cyber OLED" to "High-contrast dark mode")
                    ).forEach { (style, meta) ->
                        val (title, desc) = meta
                        val isSelected = state.mapStyle == style
                        Surface(
                            onClick = {
                                vm.setMapStyle(style)
                                showLayerMenu = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(title, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 4. Active Route Floating Status Pill (If a route is planned)
        val activeRoute = ui.route as? RouteResult.Found
        if (activeRoute != null && activeRoute.edges.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (selectedNode != null) 260.dp else 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Text(
                        "Active Route: ${number(activeRoute.distance)}m",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // 5. Landmark Details Slide-Up Card (When node is selected)
        if (selectedNode != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
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
