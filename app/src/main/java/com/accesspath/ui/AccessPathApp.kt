@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.accesspath.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.accesspath.domain.*
import com.accesspath.data.CampusRepository
import java.text.DateFormat
import java.util.Date
import java.util.UUID

// Premium Accessible Color Palette (Deep Oceanic Teal, Electric Mint, Indigo & Clean Slate)
private val BrandTeal = Color(0xFF0F766E)       // Primary Teal
private val BrandMint = Color(0xFF14B8A6)       // Mint Accent
private val BrandTealContainer = Color(0xFFCCFBF1) // Soft mint wash
private val BrandIndigo = Color(0xFF4338CA)     // Secondary Indigo
private val BrandIndigoContainer = Color(0xFFE0E7FF)
private val SlateBgLight = Color(0xFFF8FAFC)    // Ultra clean Slate-50 background
private val SlateSurfaceLight = Color(0xFFFFFFFF)
private val SlateBorderLight = Color(0xFFE2E8F0) // Subtle border
private val SlateBgDark = Color(0xFF090D16)     // Deep obsidian background
private val SlateSurfaceDark = Color(0xFF131B2E)// Elevated navy surface
private val SlateBorderDark = Color(0xFF1E293B)  // Dark mode border

private fun label(value: String) = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun time(value: Long?) = if (value == null || value == 0L) "Not field-verified" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
internal fun number(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

@Composable
fun AccessPathApp(vm: AccessViewModel = viewModel()) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF2DD4BF),
            onPrimary = Color(0xFF042F2E),
            primaryContainer = Color(0xFF134E4A),
            onPrimaryContainer = Color(0xFFCCFBF1),
            secondary = Color(0xFF818CF8),
            background = SlateBgDark,
            surface = SlateSurfaceDark,
            surfaceVariant = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
    } else {
        lightColorScheme(
            primary = BrandTeal,
            onPrimary = Color.White,
            primaryContainer = BrandTealContainer,
            onPrimaryContainer = Color(0xFF115E59),
            secondary = BrandIndigo,
            background = SlateBgLight,
            surface = SlateSurfaceLight,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF64748B)
        )
    }

    val context = LocalContext.current
    var lang by rememberSaveable { mutableStateOf("en") }
    val voice = remember { VoiceNavigator(context) }
    DisposableEffect(voice, lang) {
        voice.setLanguage(lang)
        onDispose { voice.stop() }
    }

    MaterialTheme(colorScheme = colors) {
        val ui by vm.ui.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var reviewer by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.message(null) } }
        BackHandler(tab != 0) { tab = 0 }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (dark) SlateBorderDark else SlateBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("AccessPath", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                "PRO",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        AppStrings.t("tagline", lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            FilterChip(
                                selected = lang == "hi",
                                onClick = { lang = if (lang == "hi") "en" else "hi" },
                                label = { Text(if (lang == "hi") "English" else "हिन्दी", fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.padding(end = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            TextButton(onClick = { tab = 4 }) {
                                Text(AppStrings.t("help", lang), fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (dark) SlateBorderDark else SlateBorderLight)
                ) {
                    NavigationBar(containerColor = Color.Transparent) {
                        val tabs = listOf(
                            AppStrings.t("nav_tab", lang),
                            AppStrings.t("reports_tab", lang),
                            AppStrings.t("map_tab", lang),
                            AppStrings.t("manage_tab", lang),
                            AppStrings.t("settings_tab", lang)
                        )
                        val tabIcons = listOf(
                            Icons.Default.LocationOn,
                            Icons.Default.Warning,
                            Icons.Default.Place,
                            Icons.Default.Build,
                            Icons.Default.Settings
                        )
                        tabs.forEachIndexed { index, title ->
                            val isCenterMap = index == 2
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = {
                                    if (isCenterMap) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (tab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                            shadowElevation = if (tab == 2) 4.dp else 1.dp,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = tabIcons[index],
                                                    contentDescription = title,
                                                    tint = if (tab == 2) Color.White else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            imageVector = tabIcons[index],
                                            contentDescription = title,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        title,
                                        fontWeight = if (tab == index) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isCenterMap && tab == index) MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                }
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            val state = ui.snapshot
            if (state == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(AppStrings.t("preparing", lang), fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                if (tab == 2) {
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        MapScreen(
                            state = state,
                            vm = vm,
                            ui = ui,
                            lang = lang,
                            onNavigateTo = { targetNode ->
                                vm.action { vm.repository.select(state.profile, state.current, targetNode.id, state.revision) }
                                tab = 0
                            }
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (ui.busy || ui.routing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        when (tab) {
                            0 -> NavigationScreen(ui, vm, lang, voice, onOpenMap = { tab = 2 })
                            1 -> ReportsScreen(state, vm, reviewer, ui.busy, lang)
                            3 -> ManagementScreen(state, vm, reviewer, { reviewer = it }, ui.busy, lang)
                            4 -> SettingsScreen(state, vm, ui.busy, lang) { lang = it }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                eyebrow.uppercase(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Panel(
    title: String? = null,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) SlateBorderDark else SlateBorderLight
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun Notice(text: String, isWarning: Boolean = false) {
    val bgColor = if (isWarning) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val textColor = if (isWarning) Color(0xFF991B1B) else MaterialTheme.colorScheme.onPrimaryContainer
    val borderColor = if (isWarning) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (isWarning) "⚠️" else "ℹ️", fontSize = 16.sp)
            Text(text, style = MaterialTheme.typography.bodySmall, color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun NavigationScreen(ui: ScreenState, vm: AccessViewModel, lang: String, voice: VoiceNavigator, onOpenMap: () -> Unit = {}) {
    val s = ui.snapshot!!
    var setup by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf(false) }
    var qrScanner by rememberSaveable { mutableStateOf(false) }
    var assistanceDialog by rememberSaveable { mutableStateOf(false) }
    var showGeofenceDialog by rememberSaveable { mutableStateOf(false) }
    var showWorldwideDialog by rememberSaveable { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<Node?>(null) }
    var mapSearchQuery by rememberSaveable { mutableStateOf("") }

    // Active geofence detection
    val currentPosNode = s.map.nodes.find { it.id == s.current }
    val activeGf = if (currentPosNode != null) {
        s.geofences.find { gf ->
            gf.active && gf.floorId == currentPosNode.floorId &&
            gf.contains(currentPosNode.x, currentPosNode.y)
        }
    } else null

    // Guided navigation states
    var isGuiding by rememberSaveable { mutableStateOf(false) }
    var guidedStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val visitedNodes = remember { mutableStateListOf<String>() }

    val route = ui.route as? RouteResult.Found

    Heading(AppStrings.t("nav_eyebrow", lang), AppStrings.t("nav_title", lang), AppStrings.t("nav_detail", lang))
    Notice("${s.map.provenance} • ${s.map.name} • Offline, last-known facility status")

    // --- Hero Origin & Destination Card ---
    Panel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (s.profile.mobility == Mobility.WHEELCHAIR) "♿" else if (s.profile.mobility == Mobility.AVOID_STAIRS) "🚶" else "🏃",
                        fontSize = 14.sp
                    )
                    Text(
                        AppStrings.labelMobility(s.profile.mobility, lang),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text("GLBITM Campus · Verified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Origin & Destination Visual Connector Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Origin
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Column {
                        Text(AppStrings.t("from", lang).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.map.nodes.find { it.id == s.current }?.name.orEmpty(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Box(Modifier.padding(start = 4.dp).height(12.dp).width(2.dp).background(Color(0xFF94A3B8)))
                // Destination
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                    Column {
                        Text(AppStrings.t("to", lang).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.map.nodes.find { it.id == s.destination }?.name.orEmpty(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // Action Buttons Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { setup = true },
                enabled = !ui.busy,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.t("plan_route", lang), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { qrScanner = true },
                enabled = !ui.busy,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("📷 " + AppStrings.t("qr_scan", lang), fontWeight = FontWeight.SemiBold)
            }
        }

        // Quick Destinations Favourites
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(AppStrings.t("quick_destinations", lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            val popularTargets = listOf("classroom", "washroom", "gate", "garden", "lobby0").filter { id -> s.map.nodes.any { it.id == id } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                popularTargets.forEach { targetId ->
                    val node = s.map.nodes.first { it.id == targetId }
                    val isSelected = s.destination == targetId
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (s.destination != targetId) {
                                isGuiding = false
                                guidedStepIndex = 0
                                vm.action { vm.repository.select(s.profile, s.current, targetId, s.revision) }
                            }
                        },
                        label = { Text("📍 " + node.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // 1-Tap Nearest Facilities Cards
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(AppStrings.t("nearest_facilities", lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("WASHROOM", AppStrings.t("nearest_washroom", lang), Color(0xFF0284C7)),
                    Triple("ELEVATOR", AppStrings.t("nearest_lift", lang), Color(0xFF7C3AED)),
                    Triple("EXIT", AppStrings.t("nearest_exit", lang), Color(0xFF059669))
                ).forEach { (category, title, accentColor) ->
                    Surface(
                        onClick = {
                            isGuiding = false
                            guidedStepIndex = 0
                            vm.findNearest(category)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // SOS Button
        Surface(
            onClick = { assistanceDialog = true },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFDC2626),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(AppStrings.t("request_assistance", lang), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            "Clear width ≥ ${String.format(java.util.Locale.US, "%.2f", s.profile.minWidth)} m • Max slope ≤ ${number(s.profile.maxSlope)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // --- Guided Navigation Active Stepper Banner ---
    if (isGuiding && route != null && route.edges.isNotEmpty()) {
        val safeIndex = guidedStepIndex.coerceIn(0, route.edges.size - 1)
        val currentEdge = route.edges[safeIndex]
        val currentNode = route.nodes[safeIndex]
        val nextNode = route.nodes[safeIndex + 1]
        val nextFloor = s.map.floors.first { it.id == nextNode.floorId }
        val remainingDist = route.edges.drop(safeIndex).sumOf { it.length }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "STEP ${safeIndex + 1} OF ${route.edges.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "${number(remainingDist)} m remaining",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${AppStrings.labelSegment(currentEdge.type, lang)}: ${currentNode.name} ➔ ${nextNode.name}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${number(currentEdge.length)} m · Floor: ${nextFloor.label}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (safeIndex > 0) {
                        OutlinedButton(
                            onClick = { guidedStepIndex = safeIndex - 1 },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                        ) {
                            Text(AppStrings.t("prev_step", lang), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (!visitedNodes.contains(currentNode.id)) visitedNodes.add(currentNode.id)
                            if (!visitedNodes.contains(nextNode.id)) visitedNodes.add(nextNode.id)
                            vm.action { vm.repository.position(nextNode.id) }
                            if (safeIndex < route.edges.size - 1) {
                                guidedStepIndex = 0 // resetting to 0 because new route recalculates from nextNode.id
                                val nextStep = route.edges[safeIndex + 1]
                                val stepTarget = route.nodes[safeIndex + 2]
                                val announcement = if (lang == "hi") "पहुंच गए ${nextNode.name}। अगला कदम: ${AppStrings.labelSegment(nextStep.type, "hi")} से ${stepTarget.name}।"
                                else "Reached ${nextNode.name}. Next step: ${label(nextStep.type.name)} to ${stepTarget.name}."
                                voice.speak(announcement)
                            } else {
                                isGuiding = false
                                guidedStepIndex = 0
                                val completion = if (lang == "hi") "बधाई हो! आप अपनी मंज़िल ${nextNode.name} पर पहुंच चुके हैं।"
                                else "You have arrived at your destination: ${nextNode.name}!"
                                voice.speak(completion)
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (safeIndex < route.edges.size - 1) AppStrings.t("next_step", lang) else "Finish Journey 🎉", fontWeight = FontWeight.ExtraBold)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { isGuiding = false; guidedStepIndex = 0 }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))) {
                        Text(AppStrings.t("stop_guided_nav", lang))
                    }
                }
            }
        }
    }

    // --- Interactive Campus Map Panel ---
    Panel(
        title = AppStrings.t("campus_overview", lang),
        badge = when (s.mapStyle) {
            MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> "🗺️ Default"
            MapStyle.SATELLITE -> "🛰️ Satellite"
            MapStyle.TERRAIN -> "⛰️ Terrain"
            MapStyle.HYBRID, MapStyle.SATELLITE_HYBRID -> "🌐 Hybrid"
            MapStyle.ACCESSIBILITY_HEATMAP -> "🌡️ Heatmap"
            MapStyle.NIGHT_CYBER -> "⚡ Cyber OLED"
        }
    ) {
        // Active Geofence Alert Banner
        if (activeGf != null) {
            Surface(
                color = when (activeGf.type) {
                    GeofenceType.SAFE_ZONE -> Color(0xFF10B981).copy(alpha = 0.15f)
                    GeofenceType.HAZARD -> Color(0xFFEF4444).copy(alpha = 0.15f)
                    GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                    GeofenceType.QUIET_ZONE -> Color(0xFF6366F1).copy(alpha = 0.15f)
                    GeofenceType.RESTRICTED -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, when (activeGf.type) {
                    GeofenceType.SAFE_ZONE -> Color(0xFF10B981)
                    GeofenceType.HAZARD -> Color(0xFFEF4444)
                    GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF3B82F6)
                    GeofenceType.QUIET_ZONE -> Color(0xFF6366F1)
                    GeofenceType.RESTRICTED -> Color(0xFFF59E0B)
                }),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🛡️", fontSize = 16.sp)
                    Column {
                        Text("Active Geofence: ${activeGf.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        if (activeGf.alertMessage.isNotBlank()) Text(activeGf.alertMessage, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Search Bar for Map Navigation
        OutlinedTextField(
            value = mapSearchQuery,
            onValueChange = { mapSearchQuery = it },
            label = { Text("🔍 Search campus rooms, labs, washrooms, lifts...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Instant Autocomplete Search Results Dropdown
        if (mapSearchQuery.isNotBlank()) {
            val matching = s.map.nodes.filter {
                it.name.contains(mapSearchQuery, ignoreCase = true) ||
                it.id.contains(mapSearchQuery, ignoreCase = true) ||
                it.type.contains(mapSearchQuery, ignoreCase = true)
            }.take(5)

            if (matching.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(6.dp)) {
                        matching.forEach { node ->
                            TextButton(
                                onClick = {
                                    selectedNode = node
                                    mapSearchQuery = ""
                                    if (s.selectedFloor != node.floorId) {
                                        vm.action { vm.repository.floor(node.floorId) }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("📍 ${node.name}", fontWeight = FontWeight.Bold)
                                    Text("Floor ${node.floorId}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Worldwide Campus, Full Map & Geofence Quick Floating Bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                onClick = onOpenMap,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1.2f).height(40.dp)
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("🗺️ Fullscreen Map", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            Surface(
                onClick = { showWorldwideDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("🌍 Campuses", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Surface(
                onClick = { showGeofenceDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(0.9f).height(40.dp)
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("🛡️ (${s.geofences.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Floor Switcher Chips
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.map.floors.forEach { f ->
                val isSelected = s.selectedFloor == f.id
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.action { vm.repository.floor(f.id) } },
                    label = { Text("${s.map.buildings.find { it.id == f.buildingId }?.name} · ${f.label}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    shape = RoundedCornerShape(10.dp),
                    enabled = !ui.busy
                )
            }
        }

        // Interactive Map Canvas Engine
        CampusCanvas(
            s = s,
            route = route,
            guidedStepIndex = if (isGuiding) guidedStepIndex else null,
            visitedNodes = visitedNodes.toSet(),
            selectedNode = selectedNode,
            onNodeSelected = { selectedNode = it },
            onStyleChange = { vm.setMapStyle(it) }
        )

        // Interactive Landmark Details Bottom Sheet / Card
        if (selectedNode != null) {
            LandmarkDetailsCard(
                node = selectedNode!!,
                currentPos = currentPosNode,
                lang = lang,
                onNavigate = { targetNode ->
                    isGuiding = false
                    guidedStepIndex = 0
                    vm.action { vm.repository.select(s.profile, s.current, targetNode.id, s.revision) }
                    selectedNode = null
                },
                onClose = { selectedNode = null }
            )
        }

        Text(
            "Tap any room or landmark on the map to inspect accessibility details and navigate directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // --- Route Result Display ---
    when (val r = ui.route) {
        is RouteResult.Found -> Panel(
            title = if (r.edges.isEmpty()) AppStrings.t("reached_destination", lang) else AppStrings.t("accessible_journey", lang),
            badge = if (r.edges.isEmpty()) "ARRIVED" else "${number(r.distance)} m"
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${number(r.distance)} m", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(AppStrings.t("distance", lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.height(40.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(number(r.score), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(AppStrings.t("score", lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Route Explanation Info Box
            if (r.explanation.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(AppStrings.t("route_explanation", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(r.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Walking Distance Limit Alert
            if (r.exceedsMaxWalkingDistance) {
                Notice(
                    if (lang == "hi") "चेतावनी: यह मार्ग आपकी पसंदीदा अधिकतम चलने की दूरी (${s.profile.maxWalkingDistance?.let { number(it) }} मी.) से अधिक है!"
                    else "Notice: This route exceeds your preferred maximum walking distance limit of ${s.profile.maxWalkingDistance?.let { number(it) }} m!",
                    isWarning = true
                )
            }

            // Primary Start Guidance & Set Position Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.edges.isNotEmpty()) {
                    Button(
                        onClick = {
                            isGuiding = true
                            guidedStepIndex = 0
                            visitedNodes.clear()
                            visitedNodes.add(r.nodes.first().id)
                            val startSpeech = if (lang == "hi") "मार्गदर्शन शुरू। पहला कदम: ${AppStrings.labelSegment(r.edges.first().type, "hi")} से ${r.nodes[1].name}।"
                            else "Starting guidance. Step 1: ${label(r.edges.first().type.name)} to ${r.nodes[1].name}."
                            voice.speak(startSpeech)
                        },
                        enabled = !ui.busy,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(AppStrings.t("start_guided_nav", lang), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = { position = true },
                    enabled = !ui.busy,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(AppStrings.t("set_current", lang), fontWeight = FontWeight.SemiBold)
                }
            }

            // Audio Speech Controls
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val destinationName = s.map.nodes.find { it.id == s.destination }?.name.orEmpty()
                        val stepsSpeech = if (lang == "hi") {
                            val text = StringBuilder("गंतव्य ${destinationName} के लिए मार्ग। कुल दूरी ${number(r.distance)} मीटर। ")
                            r.edges.forEachIndexed { i, e ->
                                val toNode = r.nodes[i + 1]
                                text.append("कदम ${i + 1}: ${AppStrings.labelSegment(e.type, "hi")} से ${toNode.name}। ")
                            }
                            text.toString()
                        } else {
                            val text = StringBuilder("Accessible route to ${destinationName}. Distance: ${number(r.distance)} meters. ")
                            r.edges.forEachIndexed { i, e ->
                                val toNode = r.nodes[i + 1]
                                text.append("Step ${i + 1}: ${label(e.type.name)} to ${toNode.name}, ${number(e.length)} meters. ")
                            }
                            text.toString()
                        }
                        voice.speak(stepsSpeech)
                    },
                    enabled = !ui.busy,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AppStrings.t("speak_directions", lang))
                }
                OutlinedButton(onClick = { voice.stop() }, shape = RoundedCornerShape(10.dp)) {
                    Text(AppStrings.t("stop_speech", lang))
                }
            }

            // Turn-by-Turn Timeline
            r.edges.forEachIndexed { i, e ->
                val to = r.nodes[i + 1]
                val floor = s.map.floors.first { it.id == to.floorId }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text("${AppStrings.labelSegment(e.type, lang)} ➔ ${to.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("${number(e.length)} m · ${floor.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                vm.action { vm.repository.position(to.id) }
                                voice.speak(if (lang == "hi") "आप ${to.name} पर पहुंच चुके हैं।" else "Reached ${to.name}.")
                            },
                            enabled = !ui.busy
                        ) {
                            Text("Reached", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text("Map ${r.mapVersion} • State revision ${r.revision}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is RouteResult.Unavailable -> Panel(AppStrings.t("no_route", lang)) {
            Notice(AppStrings.t("no_route_sub", lang), isWarning = true)
            r.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { position = true }, enabled = !ui.busy, shape = RoundedCornerShape(12.dp)) {
                Text(AppStrings.t("set_current", lang))
            }
        }
        null -> Text("Calculating route…")
    }

    if (setup) RouteSetup(s, lang, onDismiss = { setup = false }) { profile, start, end ->
        isGuiding = false
        guidedStepIndex = 0
        vm.action("Route preferences saved") { vm.repository.select(profile, start, end, s.revision) }
        setup = false
    }
    if (position) PickNode(AppStrings.t("set_current", lang), s.map.nodes, onDismiss = { position = false }) { node ->
        vm.action { vm.repository.position(node.id) }
        position = false
    }
    if (qrScanner) QRScanDialog(s, lang, onDismiss = { qrScanner = false }) { node ->
        isGuiding = false
        guidedStepIndex = 0
        vm.action("Location updated via QR placard: ${node.name}") { vm.repository.position(node.id) }
        voice.speak(if (lang == "hi") "QR कोड स्कैन हुआ: वर्तमान स्थान ${node.name}।" else "QR scanned: current position set to ${node.name}.")
        qrScanner = false
    }
    if (assistanceDialog) AssistanceRequestDialog(s, lang, onDismiss = { assistanceDialog = false }) { urgency, note ->
        vm.requestAssistance(urgency, note)
        assistanceDialog = false
    }
    if (showGeofenceDialog) GeofenceManagerDialog(
        s = s,
        onDismiss = { showGeofenceDialog = false },
        onAddGeofence = { vm.addGeofence(it) },
        onDeleteGeofence = { vm.deleteGeofence(it) }
    )
    if (showWorldwideDialog) WorldwideCampusExplorerDialog(
        s = s,
        onDismiss = { showWorldwideDialog = false },
        onSelectCampus = { campusId ->
            vm.switchCampus(campusId)
            showWorldwideDialog = false
        },
        onPublishCurrent = { vm.publishCampusToWorld() }
    )
}

@Composable
private fun QRScanDialog(s: AppSnapshot, lang: String, onDismiss: () -> Unit, onSelect: (Node) -> Unit) {
    var customCode by rememberSaveable { mutableStateOf("") }
    val placards = listOf(
        "gate" to "Main Entrance Gate [QR Placard #01]",
        "lobby0" to "Main Building Lobby (Ground Floor) [QR Placard #02]",
        "liftA0" to "Elevator Bank A [QR Placard #03]",
        "liftA1" to "First Floor Corridor [QR Placard #04]",
        "classroom" to "Main Lecture Hall [QR Placard #05]",
        "washroom" to "Ground Floor Accessible Washroom [QR Placard #06]"
    ).filter { pair -> s.map.nodes.any { it.id == pair.first } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "hi") "कैंपस QR कोड स्कैन" else "Scan Campus QR Placard", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (lang == "hi") "कैंपस के QR कोड को स्कैन करें या नीचे दिए गए प्लेकार्ड का चयन करें:"
                    else "Scan physical QR code placard at your campus location:"
                )
                placards.forEach { (nodeId, description) ->
                    val node = s.map.nodes.first { it.id == nodeId }
                    OutlinedButton(
                        onClick = { onSelect(node) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("📍 ${node.name}", fontWeight = FontWeight.Bold)
                            Text(description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = customCode,
                    onValueChange = { customCode = it },
                    label = { Text(if (lang == "hi") "QR कोड या नोड ID दर्ज करें" else "Enter QR payload / Node ID") },
                    placeholder = { Text("e.g. gate, classroom, accesspath://node/gate") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = customCode.trim().removePrefix("accesspath://node/").lowercase()
                    val match = s.map.nodes.find { it.id.equals(cleaned, ignoreCase = true) || it.name.contains(cleaned, ignoreCase = true) }
                    if (match != null) onSelect(match)
                },
                enabled = customCode.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (lang == "hi") "पुष्टि करें" else "Confirm QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PickNode(title: String, nodes: List<Node>, onDismiss: () -> Unit, onPick: (Node) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("Search locations") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    nodes.filter { it.name.contains(query, true) }.forEach { n ->
                        TextButton(onClick = { onPick(n) }, modifier = Modifier.fillMaxWidth()) {
                            Text("📍 ${n.name} · Floor ${n.floorId}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RouteSetup(s: AppSnapshot, lang: String, onDismiss: () -> Unit, onSave: (Profile, String, String) -> Unit) {
    var mobility by rememberSaveable { mutableStateOf(s.profile.mobility) }
    var width by rememberSaveable { mutableStateOf(s.profile.minWidth.toString()) }
    var slope by rememberSaveable { mutableStateOf(s.profile.maxSlope.toString()) }
    var maxWalking by rememberSaveable { mutableStateOf(s.profile.maxWalkingDistance?.toString().orEmpty()) }
    var start by rememberSaveable { mutableStateOf(s.current) }
    var end by rememberSaveable { mutableStateOf(s.destination) }
    var picking by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.t("plan_route", lang), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select mobility persona:", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mobility.entries.forEach { m ->
                        FilterChip(
                            selected = mobility == m,
                            onClick = { mobility = m },
                            label = { Text(AppStrings.labelMobility(m, lang)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("${AppStrings.t("from", lang)}: ${s.map.nodes.find { it.id == start }?.name}")
                }
                OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("${AppStrings.t("to", lang)}: ${s.map.nodes.find { it.id == end }?.name}")
                }
                TextButton(onClick = { val old = start; start = end; end = old }) {
                    Text("🔄 " + AppStrings.t("swap_start_end", lang).ifBlank { "Swap start & destination" })
                }
                OutlinedTextField(
                    width,
                    { width = it },
                    label = { Text("Minimum clear width in metres") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    slope,
                    { slope = it },
                    label = { Text("Maximum ramp slope in percent") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    maxWalking,
                    { maxWalking = it },
                    label = { Text(if (lang == "hi") "अधिकतम चलने की सीमा (मीटर, वैकल्पिक)" else "Maximum walking distance in metres (optional)") },
                    placeholder = { Text("e.g. 100") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mw = if (maxWalking.isBlank()) null else maxWalking.toDoubleOrNull()
                    val p = Profile(mobility, width.toDoubleOrNull() ?: Double.NaN, slope.toDoubleOrNull() ?: Double.NaN, maxWalkingDistance = mw)
                    try {
                        p.validate()
                        onSave(p, start, end)
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Find Accessible Route")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking != 0) PickNode(if (picking == 1) "Choose starting point" else "Choose destination", s.map.nodes, { picking = 0 }) {
        if (picking == 1) start = it.id else end = it.id
        picking = 0
    }
}

@Composable
private fun ReportsScreen(s: AppSnapshot, vm: AccessViewModel, reviewer: Boolean, busy: Boolean, lang: String) {
    var create by rememberSaveable { mutableStateOf(false) }
    var review by remember { mutableStateOf<BarrierReport?>(null) }
    var filter by rememberSaveable { mutableStateOf("All") }

    Heading(AppStrings.t("reports_eyebrow", lang), AppStrings.t("reports_title", lang), AppStrings.t("reports_detail", lang))

    Button(
        onClick = { create = true },
        enabled = !busy && s.map.edges.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(AppStrings.t("report_barrier", lang), fontWeight = FontWeight.Bold)
    }

    Notice("Pending reports create precautionary closures until reviewed. Reviewer controls are enabled from Manage.")

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("All") + ReportStatus.entries.map { it.name }).forEach { v ->
            val displayLabel = if (v == "All") AppStrings.t("all", lang) else AppStrings.labelReportStatus(ReportStatus.valueOf(v), lang)
            FilterChip(
                selected = filter == v,
                onClick = { filter = v },
                label = { Text(displayLabel) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }

    val reports = s.reports.filter { filter == "All" || it.status.name == filter }.reversed()
    if (reports.isEmpty()) Panel(AppStrings.t("no_reports", lang)) {
        Text("No barrier reports matching this filter.")
    }

    reports.forEach { r ->
        val statusColor = when (r.status) {
            ReportStatus.VERIFIED -> Color(0xFF10B981)
            ReportStatus.PENDING -> Color(0xFFF59E0B)
            ReportStatus.REJECTED -> Color(0xFFEF4444)
            ReportStatus.RESOLVED -> Color(0xFF64748B)
        }
        Panel(r.category) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        AppStrings.labelReportStatus(r.status, lang),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("Expires in ${r.expiryHours}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(edgeLabel(s, r.edgeId), fontWeight = FontWeight.Bold)
            Text(r.description, style = MaterialTheme.typography.bodyMedium)
            if (r.photoUri != null) Text("📷 Photo reference: ${r.photoUri}", style = MaterialTheme.typography.bodySmall)
            if (r.hasVoiceNote) Text("🎙️ Voice memo recorded", style = MaterialTheme.typography.bodySmall)
            Text("Reported ${time(r.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.reason.isNotBlank()) Text("Decision: ${r.reason} · ${time(r.reviewedAt)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (reviewer && r.status in setOf(ReportStatus.PENDING, ReportStatus.VERIFIED)) {
                OutlinedButton(onClick = { review = r }, enabled = !busy, shape = RoundedCornerShape(10.dp)) {
                    Text(AppStrings.t("review_report", lang))
                }
            }
        }
    }

    if (create) ReportDialog(s, { create = false }) { id, edge, category, text, photo, voiceNote, expiry ->
        vm.submitReportWithMedia(id, edge, category, text, photo, voiceNote, expiry)
        create = false
    }
    review?.let { r ->
        DecisionDialog(r, { review = null }) { status, reason ->
            vm.action("Review saved") { vm.repository.review(r.id, status, reason, s.revision) }
            review = null
        }
    }
}

private fun edgeLabel(s: AppSnapshot, id: String): String {
    val e = s.map.edges.find { it.id == id } ?: return id
    return "${s.map.nodes.find { it.id == e.from }?.name} ➔ ${s.map.nodes.find { it.id == e.to }?.name} (${label(e.type.name)})"
}

@Composable
private fun ReportDialog(
    s: AppSnapshot,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Boolean, Int) -> Unit
) {
    val submission = rememberSaveable { UUID.randomUUID().toString() }
    var edge by rememberSaveable { mutableStateOf(s.map.edges.firstOrNull()?.id.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(CampusRepository.categories.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf("") }
    var hasVoiceNote by rememberSaveable { mutableStateOf(false) }
    var expiryHoursStr by rememberSaveable { mutableStateOf("24") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { photoUri = it.toString() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report an Accessibility Barrier", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(edgeLabel(s, edge))
                }
                ChoiceMenu("Issue type", category, CampusRepository.categories) { category = it }
                OutlinedTextField(
                    description,
                    { if (it.length <= 1000) description = it },
                    label = { Text("Describe the barrier") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("${description.length}/1000 · minimum 5 characters") }
                )
                OutlinedTextField(
                    photoUri,
                    { photoUri = it },
                    label = { Text("Photo file reference (optional)") },
                    placeholder = { Text("e.g. broken_lift_ground.jpg") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedButton(
                    onClick = { photoPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoUri.isBlank()) "Pick Photo (Gallery / Camera)" else "Photo Attached: " + photoUri.takeLast(25))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasVoiceNote) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (hasVoiceNote) Color(0xFF10B981) else Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = hasVoiceNote, onCheckedChange = { hasVoiceNote = it })
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasVoiceNote) "🎙 Voice memo attached (12s)" else "Attach voice memo recording", fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedTextField(
                    expiryHoursStr,
                    { expiryHoursStr = it },
                    label = { Text("Temporary barrier expiry (hours, default 24)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (s.reports.any { it.edgeId == edge && it.status in setOf(ReportStatus.PENDING, ReportStatus.VERIFIED) }) {
                    Notice("This segment already has an active report. Submit only if this is a distinct observation.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val exp = expiryHoursStr.toIntOrNull() ?: 24
                    val photo = if (photoUri.isBlank()) null else photoUri.trim()
                    onSave(submission, edge, category, description, photo, hasVoiceNote, exp)
                },
                enabled = description.trim().length in 5..1000,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Submit Barrier Report")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) EdgePicker(s, { picking = false }) { edge = it; picking = false }
}

@Composable
private fun ChoiceMenu(title: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(12.dp)) {
            Text("$title: ${label(value)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { v ->
                DropdownMenuItem(text = { Text(label(v)) }, onClick = { onPick(v); open = false })
            }
        }
    }
}

@Composable
private fun EdgePicker(s: AppSnapshot, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a route segment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search segments") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    s.map.edges.filter { edgeLabel(s, it.id).contains(query, true) }.forEach { e ->
                        TextButton(onClick = { onPick(e.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(edgeLabel(s, e.id), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DecisionDialog(report: BarrierReport, onDismiss: () -> Unit, onSave: (ReportStatus, String) -> Unit) {
    val choices = if (report.status == ReportStatus.PENDING) listOf(ReportStatus.VERIFIED, ReportStatus.REJECTED) else listOf(ReportStatus.RESOLVED)
    var status by remember { mutableStateOf(choices.first()) }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Barrier Report", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(report.description, fontWeight = FontWeight.Medium)
                ChoiceMenu("Decision", status.name, choices.map { it.name }) { status = ReportStatus.valueOf(it) }
                OutlinedTextField(
                    reason,
                    { if (it.length <= 1000) reason = it },
                    label = { Text("Reason for decision") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(status, reason) }, enabled = reason.trim().length >= 5, shape = RoundedCornerShape(10.dp)) {
                Text("Save Decision")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManagementScreen(s: AppSnapshot, vm: AccessViewModel, reviewer: Boolean, onRole: (Boolean) -> Unit, busy: Boolean, lang: String) {
    var facility by remember { mutableStateOf<Facility?>(null) }
    var edgePicker by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var addLandmarkDialog by remember { mutableStateOf(false) }
    var addConnectorDialog by remember { mutableStateOf(false) }
    var editLandmarkDialog by remember { mutableStateOf(false) }
    var deleteLandmarkDialog by remember { mutableStateOf(false) }

    Heading(AppStrings.t("manage_eyebrow", lang), AppStrings.t("manage_title", lang), AppStrings.t("manage_detail", lang))

    Panel(AppStrings.t("demo_role", lang)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable reviewer administration mode", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(checked = reviewer, onCheckedChange = onRole)
        }
        Text("This switch enables facility controls, report reviews, and map modifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (!reviewer) {
        Notice("Enable local reviewer mode above to update facilities, verify reports, edit map landmarks, and simulate failures.")
        return
    }

    // Map Editor Section
    Panel(AppStrings.t("map_editor", lang)) {
        Text("Add or modify accessible landmarks and corridors with full accessibility constraint validation.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addLandmarkDialog = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text(AppStrings.t("add_landmark", lang), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { addConnectorDialog = true }, enabled = !busy && s.map.nodes.size >= 2, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text(AppStrings.t("add_connector", lang), fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editLandmarkDialog = true }, enabled = !busy && s.map.nodes.isNotEmpty(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text(AppStrings.t("edit_landmark", lang))
            }
            OutlinedButton(onClick = { deleteLandmarkDialog = true }, enabled = !busy && s.map.nodes.size > 2, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text(AppStrings.t("delete_landmark", lang))
            }
        }
    }

    // Active Assistance Requests
    AssistanceRequestsPanel(
        requests = s.assistanceRequests,
        busy = busy,
        onAcknowledge = { vm.acknowledgeAssistance(it) }
    )

    // Sensor Mapping & 2D Occupancy Grid
    SensorMappingCard(vm, s, lang, busy)

    // Cloud Multi-Reviewer Synchronization
    CloudSyncCard(vm, s, lang, busy)

    // Facility Controls
    s.map.facilities.forEach { f ->
        Panel(f.name) {
            val isAvail = f.availability == Availability.AVAILABLE
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAvail) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
            ) {
                Text(
                    AppStrings.labelAvailability(f.availability, lang),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isAvail) Color(0xFF10B981) else Color(0xFFEF4444),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(f.reason, style = MaterialTheme.typography.bodyMedium)
            Text("${f.source} · ${time(f.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { facility = f }, enabled = !busy, shape = RoundedCornerShape(10.dp)) {
                Text("Update ${f.name} Status")
            }
        }
    }

    // Failure Simulation
    Panel("Failure Simulation Suite") {
        Text("Demonstrate automatic rerouting when unexpected barriers arise on campus.")
        Button(onClick = { vm.action("All lifts unavailable") { vm.repository.allLifts(false) } }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(AppStrings.t("simulate_lifts_down", lang), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { vm.action("Lifts restored") { vm.repository.allLifts(true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(AppStrings.t("restore_lifts", lang), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { edgePicker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(AppStrings.t("block_path", lang), fontWeight = FontWeight.Bold)
        }
        s.blockedEdges.sorted().forEach { id ->
            TextButton(onClick = { vm.action("Path restored") { vm.repository.block(id, false) } }, enabled = !busy) {
                Text("Restore: ${edgeLabel(s, id)}")
            }
        }
        if (s.map.provenance == "Illustrative demo") {
            TextButton(onClick = { reset = true }, enabled = !busy) {
                Text(AppStrings.t("reset_demo", lang))
            }
        }
    }

    // Audit History Panel
    Panel("Reviewer Audit Log") {
        if (s.audit.isEmpty()) Text("No reviewer actions logged yet.")
        s.audit.takeLast(25).reversed().forEach { e ->
            Text("${e.action} · ${e.before} ➔ ${e.after}", fontWeight = FontWeight.Bold)
            Text("${e.reason}\n${e.actor} · ${time(e.time)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
        }
    }

    facility?.let { f ->
        FacilityDialog(f, { facility = null }) { status, reason ->
            vm.action("Facility status updated") { vm.repository.facility(f.id, status, reason, s.revision) }
            facility = null
        }
    }
    if (edgePicker) EdgePicker(s, { edgePicker = false }) {
        vm.action("Path blocked in both directions") { vm.repository.block(it, true) }
        edgePicker = false
    }
    if (reset) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text("Reset demo dataset?") },
        text = { Text("This removes demo reports, restores facilities, and resets route preferences.") },
        confirmButton = {
            TextButton(onClick = { vm.action("Demo reset") { vm.repository.resetDemo() }; reset = false }) {
                Text("Reset Demo")
            }
        },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } }
    )

    if (addLandmarkDialog) AddLandmarkDialog(s, onDismiss = { addLandmarkDialog = false }) { node ->
        vm.addLandmark(node)
        addLandmarkDialog = false
    }
    if (addConnectorDialog) AddConnectorDialog(s, onDismiss = { addConnectorDialog = false }) { edge, twoWay ->
        vm.addConnector(edge, twoWay)
        addConnectorDialog = false
    }
    if (editLandmarkDialog) EditLandmarkDialog(s, onDismiss = { editLandmarkDialog = false }) { id, name, x, y ->
        vm.editLandmark(id, name, x, y)
        editLandmarkDialog = false
    }
    if (deleteLandmarkDialog) DeleteLandmarkDialog(s, onDismiss = { deleteLandmarkDialog = false }) { id ->
        vm.deleteLandmark(id)
        deleteLandmarkDialog = false
    }
}

@Composable
private fun AddLandmarkDialog(s: AppSnapshot, onDismiss: () -> Unit, onSave: (Node) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var floorId by rememberSaveable { mutableStateOf(s.map.floors.firstOrNull()?.id.orEmpty()) }
    var xStr by rememberSaveable { mutableStateOf("50.0") }
    var yStr by rememberSaveable { mutableStateOf("50.0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Accessible Landmark", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label = { Text("Node ID (unique, e.g. lab_204)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Landmark Name (e.g. Physics Lab)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                ChoiceMenu("Floor", floorId, s.map.floors.map { it.id }) { floorId = it }
                OutlinedTextField(xStr, { xStr = it }, label = { Text("X coordinate (0 - 1000)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(yStr, { yStr = it }, label = { Text("Y coordinate (0 - 1000)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = xStr.toDoubleOrNull()
                    val y = yStr.toDoubleOrNull()
                    if (id.isBlank() || name.isBlank() || x == null || y == null) {
                        error = "Please fill all fields with valid numbers."
                        return@Button
                    }
                    onSave(Node(id, floorId, name, x, y, "LANDMARK"))
                },
                enabled = id.isNotBlank() && name.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Landmark")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddConnectorDialog(s: AppSnapshot, onDismiss: () -> Unit, onSave: (Edge, Boolean) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf(s.map.nodes.firstOrNull()?.id.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(s.map.nodes.getOrNull(1)?.id.orEmpty()) }
    var segmentType by rememberSaveable { mutableStateOf(Segment.CORRIDOR) }
    var lengthStr by rememberSaveable { mutableStateOf("10.0") }
    var widthStr by rememberSaveable { mutableStateOf("1.5") }
    var slopeStr by rememberSaveable { mutableStateOf("0.0") }
    var stepFree by rememberSaveable { mutableStateOf(true) }
    var wheelchairSuitable by rememberSaveable { mutableStateOf(true) }
    var twoWay by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Accessible Corridor / Path", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label = { Text("Connector ID (e.g. corridor_204)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                ChoiceMenu("From", from, s.map.nodes.map { it.id }) { from = it }
                ChoiceMenu("To", to, s.map.nodes.map { it.id }) { to = it }
                ChoiceMenu("Type", segmentType.name, Segment.entries.map { it.name }) { segmentType = Segment.valueOf(it) }
                OutlinedTextField(lengthStr, { lengthStr = it }, label = { Text("Length in metres") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(widthStr, { widthStr = it }, label = { Text("Clear width in metres") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(slopeStr, { slopeStr = it }, label = { Text("Ramp slope in percent") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = stepFree, onCheckedChange = { stepFree = it })
                    Text("Step-free passage")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wheelchairSuitable, onCheckedChange = { wheelchairSuitable = it })
                    Text("Wheelchair suitable")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = twoWay, onCheckedChange = { twoWay = it })
                    Text("Walkable in both directions")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val len = lengthStr.toDoubleOrNull()
                    val w = widthStr.toDoubleOrNull()
                    val slope = slopeStr.toDoubleOrNull()
                    if (id.isBlank() || from == to || len == null || len <= 0) {
                        error = "Check endpoints and enter positive length."
                        return@Button
                    }
                    val edge = Edge(
                        id = id,
                        from = from,
                        to = to,
                        type = segmentType,
                        length = len,
                        width = w,
                        slope = slope,
                        stepFree = stepFree,
                        wheelchairSuitable = wheelchairSuitable
                    )
                    onSave(edge, twoWay)
                },
                enabled = id.isNotBlank() && from != to,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Corridor")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FacilityDialog(f: Facility, onDismiss: () -> Unit, onSave: (Availability, String) -> Unit) {
    var status by remember { mutableStateOf(f.availability) }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu("Status", status.name, Availability.entries.map { it.name }) { status = Availability.valueOf(it) }
                OutlinedTextField(
                    reason,
                    { if (it.length <= 1000) reason = it },
                    label = { Text("Reason or observation") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(status, reason) }, enabled = reason.trim().length >= 5, shape = RoundedCornerShape(10.dp)) {
                Text("Update Facility Status")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsScreen(s: AppSnapshot, vm: AccessViewModel, busy: Boolean, lang: String, onLangChange: (String) -> Unit) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importMap(it) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportMap(it) } }

    Heading(AppStrings.t("settings_eyebrow", lang), AppStrings.t("settings_title", lang), AppStrings.t("settings_detail", lang))

    Panel(AppStrings.t("language", lang)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = lang == "en",
                onClick = { onLangChange("en") },
                label = { Text("English (US)", fontWeight = if (lang == "en") FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(10.dp)
            )
            FilterChip(
                selected = lang == "hi",
                onClick = { onLangChange("hi") },
                label = { Text("हिन्दी (Hindi)", fontWeight = if (lang == "hi") FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }

    Panel("Campus Topology & Data") {
        Text("${s.map.name}\nVersion ${s.map.version} • Schema ${s.map.schemaVersion}\n${s.map.nodes.size} landmarks · ${s.map.edges.size} directed path segments", fontWeight = FontWeight.Medium)
        Text("${s.map.provenance}\nVerified: ${time(s.map.verifiedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(AppStrings.t("import_map", lang), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { exporter.launch("${s.map.name.lowercase().replace(' ', '-')}-v${s.map.version}.json") },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(AppStrings.t("export_map", lang), fontWeight = FontWeight.Bold)
            }
        }
        Text("Imported maps are strictly verified for connectivity and accessibility rules before commit.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Panel("How to Use AccessPath") {
        Text("""1. Open Navigate and tap 'Plan a route' or pick Quick Destinations.
2. Tap 'Start guided navigation' for turn-by-turn guidance with visual breadcrumbs.
3. Tap 'Speak directions' for audio guidance in English or Hindi.
4. Scan physical QR placards on walls to reset location.
5. In Manage, enable Reviewer controls to add landmarks, corridors, or simulate lift failures.
6. In Settings, export surveyed campus topologies to JSON.""")
    }

    Panel("Privacy & Safety Guardrails") {
        Text("Your maps, reports, profiles, and audit records are strictly stored locally on this device. No remote telemetry, cameras, microphones, or tracking without consent.")
        Text("Routes rely on verified topological measurements and last-known facility conditions. Never enter a visible hazard or flooded path.", style = MaterialTheme.typography.bodySmall)
    }

    Panel("Build & System Information") {
        Text("""AccessPath 1.0.0-PRO
Modern Material 3 · Jetpack Compose · Room Database
Features: Guided Navigation & Breadcrumb Trail, Bilingual UI, Voice Guidance (TTS), QR Start Points, Map Editor, 2D Occupancy Grid, Cloud Sync""")
        Text("Source: github.com/rudrasinha05/AccessPath", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
