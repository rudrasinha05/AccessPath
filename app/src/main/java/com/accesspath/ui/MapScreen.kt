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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesspath.domain.*
import java.util.UUID

private val BrandTeal = Color(0xFF0F766E)
private val BrandMint = Color(0xFF14B8A6)
private val BrandTealContainer = Color(0xFFCCFBF1)
private val BrandIndigo = Color(0xFF4338CA)
private val SlateBorderLight = Color(0xFFE2E8F0)

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

    // Dialog & Sheet States
    var showProfileDrawer by rememberSaveable { mutableStateOf(false) }
    var showRoutePlannerSheet by rememberSaveable { mutableStateOf(false) }
    var showReportsSheet by rememberSaveable { mutableStateOf(false) }
    var showReportBarrierForm by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showArrivalDialog by rememberSaveable { mutableStateOf(false) }
    var showUnmappedPrompt by rememberSaveable { mutableStateOf(false) }
    var showARScannerHUD by rememberSaveable { mutableStateOf(false) }
    var showLayerMenu by rememberSaveable { mutableStateOf(false) }
    var showWorldwideDialog by rememberSaveable { mutableStateOf(false) }
    var showGeofenceDialog by rememberSaveable { mutableStateOf(false) }

    // Google Maps View States
    var is3DMode by rememberSaveable { mutableStateOf(false) }
    var bearing by rememberSaveable { mutableFloatStateOf(0f) }

    // Freehand Fencing States
    var isFreehandDrawing by rememberSaveable { mutableStateOf(false) }
    val freehandPoints = remember { mutableStateListOf<GeofencePoint>() }
    var showFreehandSaveDialog by rememberSaveable { mutableStateOf(false) }

    // Navigation & Voice guidance states
    var isNavigating by rememberSaveable { mutableStateOf(false) }
    var navStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var isVoiceMuted by rememberSaveable { mutableStateOf(false) }
    var activeRole by rememberSaveable { mutableStateOf("VISITOR") }
    var selectedMobility by rememberSaveable { mutableStateOf(state.profile.mobility) }

    // Barrier form fields
    var barrierDesc by rememberSaveable { mutableStateOf("") }
    var barrierCategory by rememberSaveable { mutableStateOf("Blocked path") }

    val currentPosNode = state.map.nodes.find { it.id == state.current }
    val route = ui.route as? RouteResult.Found

    // Autocomplete Filter
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
        // --- 1. Core Map Canvas Engine (Movable, Pannable, Zoomable, Rotatable) ---
        CampusCanvas(
            s = state,
            route = route,
            guidedStepIndex = if (isNavigating) navStepIndex else null,
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
            onBearingChange = { bearing = it },
            onUnmappedCorridorTapped = {
                showUnmappedPrompt = true
            }
        )

        // --- 2. Top Google Maps Search Bar & Autocomplete ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Google Maps G / Compass icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
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

                    // Profile Avatar Pill (R) - Opens isolated role & demo simulation drawer
                    Surface(
                        onClick = { showProfileDrawer = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("R", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
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
                                            "Select",
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

        // --- 3. Vertical Floor Selector (Left Side) ---
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
                        color = if (isActive) BrandTeal else Color.Transparent,
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

        // --- 4. Floating Tools Stack (Right Side) ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Layer switcher
            Surface(
                onClick = { showLayerMenu = !showLayerMenu },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🥞", fontSize = 20.sp)
                }
            }

            // Compass
            Surface(
                onClick = { bearing = 0f },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🧭", fontSize = 22.sp, modifier = Modifier.rotate(-bearing))
                }
            }

            // 3D Perspective Toggle
            Surface(
                onClick = { is3DMode = !is3DMode },
                shape = CircleShape,
                color = if (is3DMode) BrandTeal else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (is3DMode) "2D" else "3D",
                        fontWeight = FontWeight.Black,
                        color = if (is3DMode) Color.White else BrandTeal,
                        fontSize = 14.sp
                    )
                }
            }

            // Freehand Geofence FAB
            Surface(
                onClick = {
                    isFreehandDrawing = !isFreehandDrawing
                    if (isFreehandDrawing) freehandPoints.clear()
                },
                shape = CircleShape,
                color = if (isFreehandDrawing) Color(0xFFEF4444) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isFreehandDrawing) "❌" else "✏️", fontSize = 20.sp)
                }
            }

            // Recenter to current location
            Surface(
                onClick = {
                    val cur = state.map.nodes.find { it.id == state.current }
                    if (cur != null) selectedNode = cur
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎯", fontSize = 20.sp)
                }
            }
        }

        // --- 5. Contextual Material 3 Bottom Sheet (Peek ~72dp or Half ~280dp) ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, SlateBorderLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                if (isNavigating) {
                    // Turn-by-Turn Navigation Header Banner
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BrandTeal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("♿", fontSize = 20.sp)
                                    Text("In 40m, turn left into Entrance Ramp", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                }
                                IconButton(onClick = { isVoiceMuted = !isVoiceMuted }) {
                                    Text(if (isVoiceMuted) "🔇" else "🔊", fontSize = 18.sp)
                                }
                            }
                            Text("Distance remaining: 180 m • 2 mins roll", color = Color(0xFFCCFBF1), style = MaterialTheme.typography.bodySmall)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        navStepIndex++
                                        if (navStepIndex >= (route?.edges?.size ?: 1)) {
                                            showArrivalDialog = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandTeal),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Next Step ▶", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showReportsSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                                ) {
                                    Text("Report Barrier ⚠️", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { showArrivalDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6))
                                ) {
                                    Text("Arrive 🏁", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (selectedNode != null) {
                    // Half-expanded ~280dp Adaptive Sheet for Selected Destination
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🏢", fontSize = 20.sp)
                                    }
                                }
                                Column {
                                    Text(selectedNode!!.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    Text("Floor ${selectedNode!!.floorId} • ${selectedNode!!.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { selectedNode = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        // Metrics & Accessibility Badges
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f)
                            ) {
                                Text("✓ 100% Step-free", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF047857), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandTeal.copy(alpha = 0.12f)
                            ) {
                                Text("✓ Slope 3.2°", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = BrandTeal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF3B82F6).copy(alpha = 0.12f)
                            ) {
                                Text("✓ Lift Operational", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF1D4ED8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Distance: 340 m • 4 mins roll (wheelchair) • 5 mins walk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onNavigateTo(selectedNode!!)
                                    isNavigating = true
                                    navStepIndex = 0
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                            ) {
                                Text("Start Accessible Route ▶", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.action { vm.repository.floor("a0") }
                                },
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Explore Interior 🏢", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    // Peek State (~72dp) with Quick Action Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showRoutePlannerSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                        ) {
                            Text("Plan a route", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showReportsSheet = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Barrier Reports", fontWeight = FontWeight.Bold)
                        }

                        AssistChip(
                            onClick = {
                                val bharti = state.map.nodes.find { it.id == "entry" } ?: state.map.nodes.firstOrNull()
                                selectedNode = bharti
                            },
                            label = { Text("Bharti School", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(14.dp)
                        )

                        AssistChip(
                            onClick = {
                                val lib = state.map.nodes.find { it.name.contains("Lobby", ignoreCase = true) } ?: state.map.nodes.firstOrNull()
                                selectedNode = lib
                            },
                            label = { Text("Central Library", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(14.dp)
                        )

                        AssistChip(
                            onClick = {
                                val ramp = state.map.nodes.find { it.type == "RAMP" } ?: state.map.nodes.firstOrNull()
                                selectedNode = ramp
                            },
                            label = { Text("Accessible Ramp", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(14.dp)
                        )

                        AssistChip(
                            onClick = { showARScannerHUD = true },
                            label = { Text("AR Survey 📹", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        }
    }

    // --- 6. Natural Two-Tier Arrival Dialog ---
    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏢", fontSize = 24.sp)
                    Text("Reached Building Entrance", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Text(
                    "You have arrived at Bharti School Main Entrance. Outdoor guidance is complete. Would you like to explore the indoor floor plan, accessible elevators, and rooms?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.action { vm.repository.floor("a0") }
                        isNavigating = false
                        showArrivalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Explore Building Interior 🏢", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isNavigating = false
                    showArrivalDialog = false
                }) {
                    Text("Done / Dismiss")
                }
            }
        )
    }

    // --- 7. Polite Opt-In Prompt for Unmapped Corridor ---
    if (showUnmappedPrompt) {
        AlertDialog(
            onDismissRequest = { showUnmappedPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📹", fontSize = 22.sp)
                    Text("Unmapped Corridor Detected", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Text(
                    "This corridor has not been mapped yet. Would you like to map it using camera spatial sensing and sensor dead-reckoning to help future visitors navigate safely?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnmappedPrompt = false
                        showARScannerHUD = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Start Mapping 📹", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmappedPrompt = false }) {
                    Text("Not now")
                }
            }
        )
    }

    // --- 8. Fullscreen Camera AR Scanning HUD Modal ---
    if (showARScannerHUD) {
        val session by vm.sensorMapper.session.collectAsStateWithLifecycle()

        Dialog(onDismissRequest = { showARScannerHUD = false }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF090D16),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AR Camera Spatial Scanner HUD", fontWeight = FontWeight.ExtraBold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("Phone IMU & Sensor Dead-Reckoning Engine", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { showARScannerHUD = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // Status and Confidence
                    Surface(
                        color = Color(0xFF131B2E),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status: ${session.status.name}", fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
                            Text("Precision: ${(session.currentConfidence * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${session.trail.size} steps", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Text("${number(session.distanceTraversed)} m", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Camera Viewfinder & 2D Occupancy Grid Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val grid = session.grid
                            val cellW = size.width / grid.gridSize
                            val cellH = size.height / grid.gridSize

                            for (y in 0 until grid.gridSize) {
                                for (x in 0 until grid.gridSize) {
                                    val cell = grid.getCell(x, y)
                                    val color = when (cell) {
                                        OccupancyCell.FREE -> Color(0xFF10B981)
                                        OccupancyCell.OBSTACLE -> Color(0xFFEF4444)
                                        OccupancyCell.LANDMARK -> Color(0xFF3B82F6)
                                        OccupancyCell.UNKNOWN -> Color(0xFF334155).copy(alpha = 0.5f)
                                    }
                                    drawRect(color, Offset(x * cellW, y * cellH), Size(cellW, cellH))
                                }
                            }

                            // Viewfinder Reticle
                            drawCircle(Color(0xFF2DD4BF).copy(alpha = 0.6f), radius = 60f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                            drawLine(Color(0xFF2DD4BF), Offset(center.x - 70f, center.y), Offset(center.x + 70f, center.y), strokeWidth = 2f)
                            drawLine(Color(0xFF2DD4BF), Offset(center.x, center.y - 70f), Offset(center.x, center.y + 70f), strokeWidth = 2f)
                        }
                        Text("3D AR Floor Reticle Active", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    // Drift Recovery Warning
                    if (session.trackingLost) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⚠️ Tracking Drift Detected: Realign phone with floor grid or use manual step fallback.",
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Control Actions
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    vm.sensorMapper.startSession(state.selectedFloor, MappingConsent(true, System.currentTimeMillis(), "Android Sensor"))
                                    vm.sensorMapper.recordStep(0.8)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Step (+1) 🚶", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.sensorMapper.recordObstacle(1.2)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text("Obstacle 🛑", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                vm.convertMappingSession(session)
                                vm.sensorMapper.stopSession()
                                showARScannerHUD = false
                                vm.message("[Stage 1: Local Draft] Survey saved with unverified sensor estimations.")
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Survey Draft (Unverified) ✅", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- 9. Route Planning Dialog / Sheet ---
    if (showRoutePlannerSheet) {
        AlertDialog(
            onDismissRequest = { showRoutePlannerSheet = false },
            title = {
                Text("Plan a route", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Select mobility persona:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMobility == Mobility.STANDARD,
                            onClick = { selectedMobility = Mobility.STANDARD },
                            label = { Text("Standard", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedMobility == Mobility.AVOID_STAIRS,
                            onClick = { selectedMobility = Mobility.AVOID_STAIRS },
                            label = { Text("Avoid stairs", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedMobility == Mobility.WHEELCHAIR,
                            onClick = { selectedMobility = Mobility.WHEELCHAIR },
                            label = { Text("Wheelchair", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Origin: Main Gate (Ground Floor)", fontWeight = FontWeight.Bold)
                            Text("Destination: Reception / Bharti School", fontWeight = FontWeight.Bold, color = BrandTeal)
                            Text("Preferences: Step-free corridors, maximum slope 8.0%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedProfile = state.profile.copy(mobility = selectedMobility)
                        val originNode = state.map.nodes.find { it.id == "gate" } ?: state.map.nodes.first()
                        val destNode = state.map.nodes.find { it.id == "entry" } ?: state.map.nodes.last()
                        vm.action { vm.repository.select(updatedProfile, originNode.id, destNode.id, state.revision) }
                        showRoutePlannerSheet = false
                        isNavigating = true
                        navStepIndex = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Find Accessible Route", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoutePlannerSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- 10. Reports List Dialog ---
    if (showReportsSheet) {
        AlertDialog(
            onDismissRequest = { showReportsSheet = false },
            title = {
                Text("Reports", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showReportsSheet = false
                            showReportBarrierForm = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Report a barrier", fontWeight = FontWeight.Bold)
                    }

                    Text("Active Campus Reports (${state.reports.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)

                    if (state.reports.isEmpty()) {
                        Text("No active barrier reports. Campus is fully accessible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.reports.forEach { rep ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(rep.category, fontWeight = FontWeight.Bold)
                                        Text(rep.status.name, fontWeight = FontWeight.Bold, color = if (rep.status == ReportStatus.VERIFIED) Color(0xFF047857) else Color(0xFFD97706))
                                    }
                                    Text(rep.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportsSheet = false }) {
                    Text("Close")
                }
            }
        )
    }

    // --- 11. Report a Barrier Form Dialog ---
    if (showReportBarrierForm) {
        AlertDialog(
            onDismissRequest = { showReportBarrierForm = false },
            title = {
                Text("Report a barrier", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Help other visitors by flagging barriers, broken lifts, or construction debris.", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = barrierDesc,
                        onValueChange = { barrierDesc = it },
                        label = { Text("Describe the barrier") },
                        placeholder = { Text("e.g., Construction materials blocking corridor") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category: $barrierCategory", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (barrierDesc.isNotBlank()) {
                            vm.action { vm.repository.submit(UUID.randomUUID().toString(), "hall0", barrierCategory, barrierDesc) }
                            showReportBarrierForm = false
                            barrierDesc = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportBarrierForm = false
                    barrierDesc = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- 12. Help & Documentation Dialog ---
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text("Help & AccessPath Guide", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Simple by design.", fontWeight = FontWeight.ExtraBold, color = BrandTeal, style = MaterialTheme.typography.titleMedium)
                    Text("AccessPath is designed to give you effortless, familiar map navigation while ensuring strictly verified accessibility data for wheelchairs and mobility challenges.")
                    HorizontalDivider()
                    Text("• Step-Free Routing: Avoids stairs and steep inclines automatically for wheelchair users.")
                    Text("• Floor Elevator Pills: Switch between campus floors using the elevator selector.")
                    Text("• AR Spatial Scanner: Map unmapped corridors with confidence scoring and sensor dead-reckoning.")
                    Text("• Offline First: Complete route calculation and maps work 100% without internet.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // --- 13. Profile & Role Governance Drawer (Avatar R) ---
    if (showProfileDrawer) {
        AlertDialog(
            onDismissRequest = { showProfileDrawer = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = BrandTeal, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("R", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        }
                    }
                    Column {
                        Text("Rudra Sinha", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text("Role: $activeRole", style = MaterialTheme.typography.labelSmall, color = BrandTeal, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Role Switcher (भूमिका चुनें):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = activeRole == "VISITOR",
                            onClick = { activeRole = "VISITOR" },
                            label = { Text("Visitor", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = activeRole == "REVIEWER",
                            onClick = { activeRole = "REVIEWER" },
                            label = { Text("Reviewer", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = activeRole == "ADMIN",
                            onClick = { activeRole = "ADMIN" },
                            label = { Text("Admin", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Segregated Demo Simulation Mode Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚡", fontSize = 18.sp)
                                Text("Demo Simulation Mode", fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                            }
                            Text(
                                "⚠️ SIMULATION SANDBOX — Controls here simulate campus emergencies (e.g. lift breakdown, corridor closure) for demonstration only. They are isolated from live visitor data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF991B1B)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        vm.action {
                                            val currentLift = state.map.facilities.find { it.id == "liftA" }
                                            val newAvail = if (currentLift?.availability == Availability.AVAILABLE) Availability.UNAVAILABLE else Availability.AVAILABLE
                                            vm.repository.facility("liftA", newAvail, "Simulated breakdown", state.revision)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Toggle Lift A", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        vm.action { vm.repository.resetDemo() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Text("Reset Demo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Help & Documentation
                    Button(
                        onClick = {
                            showProfileDrawer = false
                            showHelpDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Help", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDrawer = false }) {
                    Text("Close")
                }
            }
        )
    }

    // --- 14. Google Maps Layers & Styles Dialog ---
    if (showLayerMenu) {
        AlertDialog(
            onDismissRequest = { showLayerMenu = false },
            title = { Text("🗺️ Google Maps Views & Layers", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        MapStyle.DEFAULT_VECTOR to ("🗺️ Default" to "Clean Google vector map with roads & buildings"),
                        MapStyle.SATELLITE to ("🛰️ Satellite" to "Photorealistic aerial satellite photography"),
                        MapStyle.TERRAIN to ("⛰️ Terrain" to "Topographic contour lines and elevation relief"),
                        MapStyle.HYBRID to ("🌐 Hybrid" to "Satellite imagery with street network and labels"),
                        MapStyle.ACCESSIBILITY_HEATMAP to ("🌡️ Slope Heatmap" to "Wheelchair slope safety gradient")
                    ).forEach { (style, meta) ->
                        val (title, desc) = meta
                        val isSelected = state.mapStyle == style
                        Surface(
                            onClick = {
                                vm.setMapStyle(style)
                                showLayerMenu = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandTeal.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.5.dp, BrandTeal) else BorderStroke(0.5.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(title, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold, color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayerMenu = false }) { Text("Close") }
            }
        )
    }

    // Save Freehand Geofence Dialog
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

    // Geofence Manager Dialog
    if (showGeofenceDialog) {
        GeofenceManagerDialog(
            s = state,
            onDismiss = { showGeofenceDialog = false },
            onAddGeofence = { zone -> vm.addGeofence(zone) },
            onDeleteGeofence = { id -> vm.deleteGeofence(id) }
        )
    }
}
