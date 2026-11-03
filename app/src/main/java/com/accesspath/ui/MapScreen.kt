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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesspath.domain.*
import java.util.UUID

private val BrandTeal = Color(0xFF0F766E)
private val BrandTealLight = Color(0xFF14B8A6)
private val BrandTealContainer = Color(0xFFCCFBF1)
private val BrandIndigo = Color(0xFF4338CA)
private val SlateBorderLight = Color(0xFFE2E8F0)
private val GoogleBlue = Color(0xFF1A73E8)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC04)
private val GoogleGreen = Color(0xFF34A853)

@Composable
fun MapScreen(
    state: AppSnapshot,
    vm: AccessViewModel,
    ui: ScreenState,
    lang: String,
    onLanguageToggle: () -> Unit = {},
    onNavigateTo: (Node) -> Unit
) {
    // 3 Google Maps Bottom Navigation Tabs (0: Explore, 1: You, 2: Contribute)
    var bottomNavTab by rememberSaveable { mutableIntStateOf(0) }

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
    var showFreehandSaveDialog by rememberSaveable { mutableStateOf(false) }

    // Map gesture and layer modes
    var is3DMode by rememberSaveable { mutableStateOf(false) }
    var isFreehandDrawing by rememberSaveable { mutableStateOf(false) }
    val freehandPoints = remember { mutableStateListOf<GeofencePoint>() }

    // Navigation and route simulation state
    var isNavigating by rememberSaveable { mutableStateOf(false) }
    var navStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var isVoiceMuted by rememberSaveable { mutableStateOf(false) }

    // Mobility persona selection
    var selectedMobility by rememberSaveable { mutableStateOf(state.profile.mobility) }

    // Role state
    var activeRole by rememberSaveable { mutableStateOf("VISITOR") }

    // Barrier form inputs
    var barrierDesc by rememberSaveable { mutableStateOf("") }
    var barrierCategory by rememberSaveable { mutableStateOf("Blocked path") }

    // Star rating for recent route in Contribute tab
    var ratingStars by rememberSaveable { mutableIntStateOf(0) }

    val route = remember(state, ui.route) {
        (ui.route as? RouteResult.Found) ?: (Routing.calculate(state) as? RouteResult.Found)
    }

    val matchingNodes = remember(searchQuery, selectedCategory, state.map.nodes, state.selectedFloor) {
        if (searchQuery.isBlank() && selectedCategory == "ALL") {
            emptyList()
        } else {
            state.map.nodes.filter { node ->
                val matchesCategory = when (selectedCategory) {
                    "ALL" -> true
                    "ELEVATOR" -> node.type.contains("ELEVATOR", ignoreCase = true) || node.name.contains("Lift", ignoreCase = true)
                    "RAMP" -> node.type.contains("RAMP", ignoreCase = true) || node.name.contains("Ramp", ignoreCase = true)
                    "ENTRANCE" -> node.type.contains("ENTRANCE", ignoreCase = true) || node.name.contains("Gate", ignoreCase = true)
                    "WASHROOM" -> node.type.contains("WASHROOM", ignoreCase = true) || node.name.contains("Washroom", ignoreCase = true)
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

    Scaffold(
        bottomBar = {
            // Google Maps 3-Tab Bottom Navigation Bar (Explore, You, Contribute)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, SlateBorderLight),
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 0: Explore
                    val isExplore = bottomNavTab == 0
                    Surface(
                        onClick = { bottomNavTab = 0 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isExplore) BrandTealContainer else Color.Transparent,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = "Explore",
                                tint = if (isExplore) BrandTeal else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                            if (isExplore) {
                                Text("Explore", fontWeight = FontWeight.ExtraBold, color = BrandTeal, fontSize = 13.sp)
                            }
                        }
                    }

                    // Tab 1: You (Saved & Profile & Governance)
                    val isYou = bottomNavTab == 1
                    Surface(
                        onClick = { bottomNavTab = 1 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isYou) BrandTealContainer else Color.Transparent,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Box {
                                Text("🔖", fontSize = 18.sp)
                                // Red notification badge dot
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .align(Alignment.TopEnd)
                                )
                            }
                            if (isYou) {
                                Text("You", fontWeight = FontWeight.ExtraBold, color = BrandTeal, fontSize = 13.sp)
                            }
                        }
                    }

                    // Tab 2: Contribute (Matching Image 4 & 5)
                    val isContribute = bottomNavTab == 2
                    Surface(
                        onClick = { bottomNavTab = 2 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isContribute) BrandTealContainer else Color.Transparent,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.AddCircle,
                                contentDescription = "Contribute",
                                tint = if (isContribute) BrandTeal else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                            if (isContribute) {
                                Text("Contribute", fontWeight = FontWeight.ExtraBold, color = BrandTeal, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (bottomNavTab) {
                0 -> {
                    // ==========================================
                    // TAB 0: EXPLORE (Pure Full-Screen Google Maps)
                    // ==========================================
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Core Map Canvas Engine
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
                            onUnmappedCorridorTapped = {
                                showUnmappedPrompt = true
                            }
                        )

                        // 2. Top Google Maps Search Bar & Category Chips (Matching Image 1, 2, 3)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Floating White Search Bar Pill
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                tonalElevation = 4.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 4-Color Google Maps Pin Logo
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        drawCircle(GoogleBlue, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.35f))
                                        drawCircle(Color.White, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.35f))
                                        drawCircle(GoogleRed, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.35f))
                                    }

                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text(
                                                if (lang == "hi") "यहाँ खोजें / कमरा, लिफ्ट खोजें" else "Search here",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF64748B),
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
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Voice Mic Emoji Button
                                    Text("🎙️", fontSize = 16.sp, modifier = Modifier.clickable { /* mic */ }.padding(horizontal = 4.dp))

                                    // Camera / Lens Emoji Button
                                    Text("📷", fontSize = 16.sp, modifier = Modifier.clickable { showARScannerHUD = true }.padding(horizontal = 4.dp))

                                    // Language Switcher Chip
                                    Surface(
                                        onClick = onLanguageToggle,
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Text(if (lang == "hi") "EN" else "हिन्दी", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandTeal)
                                        }
                                    }

                                    // Profile Avatar Pill (R) with Blue Status Ring
                                    Surface(
                                        onClick = { showProfileDrawer = true },
                                        shape = CircleShape,
                                        color = BrandTeal,
                                        border = BorderStroke(2.dp, GoogleBlue),
                                        modifier = Modifier.size(34.dp),
                                        shadowElevation = 2.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("R", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }

                            // Sub-Search Category Chips Row (Matching Image 1, 2, 3)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ✨ Ask Maps (Blue Sparkle Pill)
                                Surface(
                                    onClick = { showHelpDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFE8F0FE),
                                    border = BorderStroke(1.dp, Color(0xFFD2E3FC))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("✨", fontSize = 13.sp)
                                        Text("Ask Maps", fontWeight = FontWeight.Bold, color = GoogleBlue, fontSize = 13.sp)
                                    }
                                }

                                // 🚻 Restrooms
                                Surface(
                                    onClick = { selectedCategory = if (selectedCategory == "WASHROOM") "ALL" else "WASHROOM" },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selectedCategory == "WASHROOM") BrandTealContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (selectedCategory == "WASHROOM") BrandTeal else SlateBorderLight),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("🚻", fontSize = 13.sp)
                                        Text("Restrooms", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                                // 🛗 Elevators
                                Surface(
                                    onClick = { selectedCategory = if (selectedCategory == "ELEVATOR") "ALL" else "ELEVATOR" },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selectedCategory == "ELEVATOR") BrandTealContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (selectedCategory == "ELEVATOR") BrandTeal else SlateBorderLight),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("🛗", fontSize = 13.sp)
                                        Text("Elevators", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // 🚪 Ramps
                                Surface(
                                    onClick = { selectedCategory = if (selectedCategory == "RAMP") "ALL" else "RAMP" },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selectedCategory == "RAMP") BrandTealContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (selectedCategory == "RAMP") BrandTeal else SlateBorderLight),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("🚪", fontSize = 13.sp)
                                        Text("Ramps", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // 📹 AR Survey
                                Surface(
                                    onClick = { showARScannerHUD = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, SlateBorderLight),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("📹", fontSize = 13.sp)
                                        Text("AR Survey", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // Autocomplete Results Dropdown
                            AnimatedVisibility(visible = searchQuery.isNotBlank() && matchingNodes.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 10.dp,
                                    border = BorderStroke(1.dp, SlateBorderLight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        matchingNodes.forEach { node ->
                                            Surface(
                                                onClick = {
                                                    selectedNode = node
                                                    if (state.selectedFloor != node.floorId) {
                                                        vm.action { vm.repository.floor(node.floorId) }
                                                    }
                                                    searchQuery = ""
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color.Transparent
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        when {
                                                            node.type.contains("ELEVATOR", ignoreCase = true) -> "🛗"
                                                            node.type.contains("RAMP", ignoreCase = true) -> "🚪"
                                                            node.type.contains("STAIRS", ignoreCase = true) -> "🪜"
                                                            node.type.contains("ENTRANCE", ignoreCase = true) -> "🏢"
                                                            else -> "📍"
                                                        },
                                                        fontSize = 18.sp
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(node.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        Text("Floor ${node.floorId} • ${node.type.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = BrandTealContainer
                                                    ) {
                                                        Text("Step-free", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = BrandTeal, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Vertical Indoor Floor Selector (Left Side)
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                state.map.floors.reversed().forEach { f ->
                                    val isActive = state.selectedFloor == f.id
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isActive) BrandTeal else Color.Transparent,
                                        modifier = Modifier.size(38.dp),
                                        onClick = { vm.action { vm.repository.floor(f.id) } }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                f.label.take(2).uppercase(),
                                                fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                                                color = if (isActive) Color.White else Color(0xFF334155),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Floating Action Controls Stack on Right (Matching Image 1, 2, 3)
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Layer Switcher Button (Pancake Icon)
                            Surface(
                                onClick = { showLayerMenu = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🥞", fontSize = 20.sp)
                                }
                            }

                            // Compass Needle Button
                            Surface(
                                onClick = { /* Reset bearing */ },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(26.dp)) {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val northPath = Path().apply {
                                            moveTo(cx, cy - 12f)
                                            lineTo(cx - 5f, cy)
                                            lineTo(cx + 5f, cy)
                                            close()
                                        }
                                        drawPath(northPath, GoogleRed)
                                        val southPath = Path().apply {
                                            moveTo(cx, cy + 12f)
                                            lineTo(cx - 5f, cy)
                                            lineTo(cx + 5f, cy)
                                            close()
                                        }
                                        drawPath(southPath, Color(0xFFBDC1C6))
                                    }
                                }
                            }

                            // 3D Perspective Toggle
                            Surface(
                                onClick = { is3DMode = !is3DMode },
                                shape = CircleShape,
                                color = if (is3DMode) BrandTeal else MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
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
                                color = if (isFreehandDrawing) Color(0xFFEF4444) else MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (isFreehandDrawing) "❌" else "✏️", fontSize = 18.sp)
                                }
                            }

                            // Location Recenter Button (White circle with blue bullseye)
                            Surface(
                                onClick = {
                                    val cur = state.map.nodes.find { it.id == state.current }
                                    if (cur != null) selectedNode = cur
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, SlateBorderLight),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        drawCircle(GoogleBlue, radius = 4f, center = Offset(cx, cy))
                                        drawCircle(GoogleBlue, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                                        drawLine(GoogleBlue, Offset(cx - 12f, cy), Offset(cx - 9f, cy), strokeWidth = 2.5f)
                                        drawLine(GoogleBlue, Offset(cx + 9f, cy), Offset(cx + 12f, cy), strokeWidth = 2.5f)
                                        drawLine(GoogleBlue, Offset(cx, cy - 12f), Offset(cx, cy - 9f), strokeWidth = 2.5f)
                                        drawLine(GoogleBlue, Offset(cx, cy + 9f), Offset(cx, cy + 12f), strokeWidth = 2.5f)
                                    }
                                }
                            }

                            // Floating Direction FAB (Teal rounded square with right turn arrow - Matching Image 2 & 3)
                            Surface(
                                onClick = { showRoutePlannerSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = BrandTeal,
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⮡", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 5. Contextual Material 3 Bottom Sheet (Matching Image 2 & 3)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 16.dp,
                            border = BorderStroke(1.dp, SlateBorderLight)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Drag handle pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFCBD5E1))
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
                                            }
                                        }
                                    }
                                } else if (selectedNode != null) {
                                    // Half-Expanded Sheet (~280dp) for Selected Destination
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = BrandTealContainer,
                                                    modifier = Modifier.size(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("🏢", fontSize = 22.sp)
                                                    }
                                                }
                                                Column {
                                                    Text(selectedNode!!.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                                    Text("Floor ${selectedNode!!.floorId} • ${selectedNode!!.type}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                                }
                                            }
                                            IconButton(onClick = { selectedNode = null }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                                            }
                                        }

                                        // Verified Accessibility Metrics Badges
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFECFDF5)
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
                                                color = Color(0xFFEFF6FF)
                                            ) {
                                                Text("✓ Lift Operational", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF1D4ED8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text("Distance: 340 m • 4 mins roll (wheelchair) • 5 mins walk", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))

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
                                                modifier = Modifier.weight(1f).height(44.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                                            ) {
                                                Text("Start Accessible Route ▶", fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    vm.action { vm.repository.floor("a0") }
                                                },
                                                modifier = Modifier.height(44.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Explore Interior 🏢", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else {
                                    // Header row in Peek state (Matching Image 2 & 3: Local vibe / Campus name + Weather)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Bharti School Campus", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("☀️ 28°", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFECFDF5)
                                            ) {
                                                Text("🟢 100% Step-free", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Quick Action Chips Row - Always visible on Explore
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
                                        Text("Reports", fontWeight = FontWeight.Bold)
                                    }

                                    AssistChip(
                                        onClick = { showHelpDialog = true },
                                        label = { Text("Help", fontWeight = FontWeight.Bold) },
                                        shape = RoundedCornerShape(14.dp)
                                    )

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
                1 -> {
                    // ==========================================
                    // TAB 1: YOU (Profile, Saved Places, Isolated Demo Simulation)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // User Profile Header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = BrandTeal, modifier = Modifier.size(54.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("R", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 24.sp)
                                }
                            }
                            Column {
                                Text("Rudra Sinha", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                Text("Level 3 Campus Navigator • $activeRole", style = MaterialTheme.typography.bodySmall, color = BrandTeal, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Role Switcher
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Active Role (भूमिका):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("VISITOR", "REVIEWER", "ADMIN").forEach { role ->
                                        FilterChip(
                                            selected = activeRole == role,
                                            onClick = { activeRole = role },
                                            label = { Text(role, fontWeight = FontWeight.Bold) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Isolated DEMO SIMULATION MODE Sandbox
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("⚡", fontSize = 18.sp)
                                    Text("Demo Simulation Mode", fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                                }
                                Text(
                                    "⚠️ SIMULATION SANDBOX — Controls here simulate campus emergencies (e.g. lift breakdown, corridor closure) for demonstration only. They are isolated from live visitor data.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7F1D1D)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val lift = state.map.facilities.find { it.name.contains("Lift", ignoreCase = true) }
                                            if (lift != null) {
                                                val nextAvail = if (lift.availability == Availability.AVAILABLE) Availability.UNAVAILABLE else Availability.AVAILABLE
                                                vm.action { vm.repository.facility(lift.id, nextAvail, "Demo simulation toggle", state.revision) }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Toggle Lift A")
                                    }
                                    Button(
                                        onClick = { vm.action { vm.repository.resetDemo() } },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Reset Demo")
                                    }
                                }
                            }
                        }

                        // Saved Campus Places
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Saved Places & Frequent Routes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                listOf(
                                    "Bharti School Main Entrance" to "Floor outdoor • Step-free entry",
                                    "Central Library East Wing" to "Floor a0 • Lift B access",
                                    "Accessible Ramp North" to "Floor a1 • 4.1° slope gradient"
                                ).forEach { (title, subtitle) ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("🔖", fontSize = 16.sp)
                                        Column {
                                            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }

                        // Offline Map Status
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Offline Campus Map Database", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Topology Version: v${state.map.version} (Verified offline available)", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("100% of campus routing, offline indoor positioning & step-free pathfinding work without network connectivity.", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
                2 -> {
                    // ==========================================
                    // TAB 2: CONTRIBUTE (Exact match to User Reference Images 4 & 5)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Contribute", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)

                        // 1. Rate this place / Recent Route Card (Image 4 & 5)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Rate your recent accessible route", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    (1..5).forEach { star ->
                                        IconButton(onClick = { ratingStars = star }) {
                                            Text(if (star <= ratingStars) "★" else "☆", fontSize = 28.sp, color = if (star <= ratingStars) GoogleYellow else Color(0xFF94A3B8))
                                        }
                                    }
                                    TextButton(onClick = { ratingStars = 0 }) {
                                        Text("Skip", fontWeight = FontWeight.Bold, color = BrandTeal)
                                    }
                                }
                            }
                        }

                        // 2. All-Time Views Card (Image 4)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("All-time accessibility views", style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
                                        Text("6,054", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                                    }
                                    TextButton(onClick = { showARScannerHUD = true }) {
                                        Text("+ Add survey", fontWeight = FontWeight.Bold, color = BrandTeal)
                                    }
                                }

                                // Views Sparkline Chart
                                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(GoogleYellow, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 3f)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("13 Oct", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text("20", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text("27", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text("3 Nov", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }
                        }

                        // 3. Level Progress Card (Image 4)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { 0.75f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = BrandTeal
                                )
                                Text("93 points away from Level 4 Contributor", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                Text("Add 9 reviews to reach Level 4", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { showReportBarrierForm = true }) {
                                    Text("Add a barrier report", fontWeight = FontWeight.Bold, color = BrandTeal)
                                }
                            }
                        }

                        // 4. Quick Action Cards (Report Barrier & AR Survey)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showReportBarrierForm = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                            ) {
                                Text("Report Barrier ⚠️", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showARScannerHUD = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Start AR Survey 📹", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // GOOGLE MAPS LAYER SWITCHER BOTTOM SHEET (Image 1 1:1 Match)
    // ==========================================
    if (showLayerMenu) {
        ModalBottomSheet(
            onDismissRequest = { showLayerMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Map type title and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Map type", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showLayerMenu = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // 3 Map Types (Default, Satellite, Terrain) with signature teal border when selected
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Default (Vector)
                    val isDefault = state.mapStyle == MapStyle.DEFAULT_VECTOR || state.mapStyle == MapStyle.BLUEPRINT
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            vm.setMapStyle(MapStyle.DEFAULT_VECTOR)
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFCEEAD6),
                            border = if (isDefault) BorderStroke(2.5.dp, BrandTeal) else BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(Color(0xFF81C995), radius = 22f, center = Offset(size.width * 0.4f, size.height * 0.4f))
                                    drawLine(Color.White, Offset(0f, size.height * 0.6f), Offset(size.width, size.height * 0.4f), strokeWidth = 6f)
                                }
                            }
                        }
                        Text("Default", fontWeight = if (isDefault) FontWeight.ExtraBold else FontWeight.Medium, color = if (isDefault) BrandTeal else Color(0xFF374151), fontSize = 12.sp)
                    }

                    // 2. Satellite
                    val isSatellite = state.mapStyle == MapStyle.SATELLITE || state.mapStyle == MapStyle.HYBRID
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            vm.setMapStyle(MapStyle.SATELLITE)
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E3A2F),
                            border = if (isSatellite) BorderStroke(2.5.dp, BrandTeal) else BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawLine(Color(0xFF9E9E9E), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 5f)
                                }
                            }
                        }
                        Text("Satellite", fontWeight = if (isSatellite) FontWeight.ExtraBold else FontWeight.Medium, color = if (isSatellite) BrandTeal else Color(0xFF374151), fontSize = 12.sp)
                    }

                    // 3. Terrain
                    val isTerrain = state.mapStyle == MapStyle.TERRAIN
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable {
                            vm.setMapStyle(MapStyle.TERRAIN)
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEFEBE9),
                            border = if (isTerrain) BorderStroke(2.5.dp, BrandTeal) else BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(Color(0xFFBCAAA4), radius = 24f, center = Offset(size.width * 0.5f, size.height * 0.5f), style = Stroke(width = 3f))
                                }
                            }
                        }
                        Text("Terrain", fontWeight = if (isTerrain) FontWeight.ExtraBold else FontWeight.Medium, color = if (isTerrain) BrandTeal else Color(0xFF374151), fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = SlateBorderLight, modifier = Modifier.padding(vertical = 4.dp))

                // Map details Section (Matching Image 1)
                Text("Map details", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. Step-Free (Public transport style)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFE0F2FE),
                            border = BorderStroke(2.dp, BrandTeal),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("♿", fontSize = 22.sp)
                            }
                        }
                        Text("Step-free", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandTeal)
                    }

                    // 2. Traffic (Barriers style)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚦", fontSize = 22.sp)
                            }
                        }
                        Text("Barriers", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // 3. 3D Raised Buildings
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { is3DMode = !is3DMode }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (is3DMode) BrandTealContainer else Color(0xFFF1F5F9),
                            border = if (is3DMode) BorderStroke(2.dp, BrandTeal) else BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏢", fontSize = 22.sp)
                            }
                        }
                        Text("3D Mode", fontSize = 11.sp, fontWeight = if (is3DMode) FontWeight.Bold else FontWeight.Medium)
                    }

                    // 4. AR Street View
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            showLayerMenu = false
                            showARScannerHUD = true
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFEF9C3),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📹", fontSize = 22.sp)
                            }
                        }
                        Text("Live AR", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // 5. Slope Heatmap
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            vm.setMapStyle(MapStyle.ACCESSIBILITY_HEATMAP)
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, SlateBorderLight),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌱", fontSize = 22.sp)
                            }
                        }
                        Text("Slope AQI", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // DIALOGS & MODALS
    // ==========================================

    // Natural Two-Tier Arrival Dialog
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

    // Polite Opt-In Prompt for Unmapped Corridor
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

    // Fullscreen Camera AR Scanning HUD Modal
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

                    Surface(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF2DD4BF))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
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

                                drawCircle(Color(0xFF2DD4BF).copy(alpha = 0.6f), radius = 60f, center = center, style = Stroke(width = 2.5f))
                                drawLine(Color(0xFF2DD4BF), Offset(center.x - 70f, center.y), Offset(center.x + 70f, center.y), strokeWidth = 2f)
                                drawLine(Color(0xFF2DD4BF), Offset(center.x, center.y - 70f), Offset(center.x, center.y + 70f), strokeWidth = 2f)
                            }
                            Text("3D AR Floor Reticle Active", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }

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

    // Route Planning Dialog
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

    // Reports Dialog
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

    // Report a Barrier Form Dialog
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

    // Help & Documentation Dialog
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

    // Profile & Role Governance Drawer (Avatar R)
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

                    // Demo Simulation Mode (Isolated Sandbox)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚡", fontSize = 16.sp)
                                Text("Demo Simulation Mode", fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                            }
                            Text(
                                "⚠️ SIMULATION SANDBOX — Controls here simulate campus emergencies (e.g. lift breakdown, corridor closure) for demonstration only. They are isolated from live visitor data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7F1D1D)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val lift = state.map.facilities.find { it.name.contains("Lift", ignoreCase = true) }
                                        if (lift != null) {
                                            val nextAvail = if (lift.availability == Availability.AVAILABLE) Availability.UNAVAILABLE else Availability.AVAILABLE
                                            vm.action { vm.repository.facility(lift.id, nextAvail, "Demo simulation toggle", state.revision) }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Toggle Lift A")
                                }
                                Button(
                                    onClick = { vm.action { vm.repository.resetDemo() } },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Text("Reset Demo")
                                }
                            }
                        }
                    }

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
}
