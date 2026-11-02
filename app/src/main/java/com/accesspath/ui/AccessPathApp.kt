@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.accesspath.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.accesspath.domain.*
import com.accesspath.data.CampusRepository
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private val Pine = Color(0xFF125B50)
private val Mist = Color(0xFFF4F7F6)
private val Lime = Color(0xFFD6F59B)
private fun label(value: String) = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun time(value: Long?) = if(value == null || value == 0L) "Not field-verified" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
internal fun number(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

@Composable fun AccessPathApp(vm: AccessViewModel = viewModel()) {
    val dark = isSystemInDarkTheme()
    val colors = if(dark) darkColorScheme(primary=Lime, secondary=Lime, background=Color(0xFF101C19), surface=Color(0xFF172822))
        else lightColorScheme(primary=Pine, secondary=Pine, background=Mist, surface=Color.White, primaryContainer=Lime, onPrimaryContainer=Color(0xFF14332B))
    
    val context = LocalContext.current
    var lang by rememberSaveable { mutableStateOf("en") }
    val voice = remember { VoiceNavigator(context) }
    DisposableEffect(voice, lang) {
        voice.setLanguage(lang)
        onDispose { voice.stop() }
    }

    MaterialTheme(colorScheme=colors) {
        val ui by vm.ui.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var reviewer by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.message(null) } }
        BackHandler(tab != 0) { tab=0 }
        Scaffold(
            containerColor=MaterialTheme.colorScheme.background,
            topBar={
                TopAppBar(
                    title={
                        Column {
                            Text("AccessPath", fontWeight=FontWeight.Bold)
                            Text(AppStrings.t("tagline", lang), style=MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions={
                        FilterChip(
                            selected=lang == "hi",
                            onClick={ lang = if (lang == "hi") "en" else "hi" },
                            label={ Text(if (lang == "hi") "EN" else "हिन्दी") },
                            modifier=Modifier.padding(end=4.dp)
                        )
                        TextButton(onClick={ tab=3 }) { Text(AppStrings.t("help", lang)) }
                    },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)
                )
            },
            bottomBar={
                NavigationBar {
                    val tabs = listOf(
                        AppStrings.t("nav_tab", lang),
                        AppStrings.t("reports_tab", lang),
                        AppStrings.t("manage_tab", lang),
                        AppStrings.t("settings_tab", lang)
                    )
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected=tab==index,
                            onClick={tab=index},
                            icon={ Text(listOf("↗","!","☷","⋯")[index], style=MaterialTheme.typography.titleLarge) },
                            label={Text(title)}
                        )
                    }
                }
            },
            snackbarHost={ SnackbarHost(snackbar) }
        ) { padding ->
            val state=ui.snapshot
            if(state==null) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(AppStrings.t("preparing", lang))
                }
            } else Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(ui.busy || ui.routing) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(tab) {
                    0 -> NavigationScreen(ui, vm, lang, voice)
                    1 -> ReportsScreen(state, vm, reviewer, ui.busy, lang)
                    2 -> ManagementScreen(state, vm, reviewer, {reviewer=it}, ui.busy, lang)
                    3 -> SettingsScreen(state, vm, ui.busy, lang) { lang = it }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable private fun Heading(eyebrow: String, title: String, detail: String) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
        Text(title, style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
        Text(detail, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Panel(title: String?=null, content: @Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(title!=null) Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            content()
        }
    }
}

@Composable private fun Notice(text: String) {
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(12.dp)) {
        Text(text, Modifier.padding(12.dp).fillMaxWidth(),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun NavigationScreen(ui: ScreenState, vm: AccessViewModel, lang: String, voice: VoiceNavigator) {
    val s = ui.snapshot!!
    var setup by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf(false) }
    var qrScanner by rememberSaveable { mutableStateOf(false) }
    var assistanceDialog by rememberSaveable { mutableStateOf(false) }

    // Guided navigation states
    var isGuiding by rememberSaveable { mutableStateOf(false) }
    var guidedStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val visitedNodes = remember { mutableStateListOf<String>() }

    val route = ui.route as? RouteResult.Found

    Heading(AppStrings.t("nav_eyebrow", lang), AppStrings.t("nav_title", lang), AppStrings.t("nav_detail", lang))
    Notice("${s.map.provenance} • ${s.map.name} • Offline, last-known facility status")
    
    Panel {
        Text(AppStrings.labelMobility(s.profile.mobility, lang), color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
        Text("${AppStrings.t("from", lang)}  ${s.map.nodes.find{it.id==s.current}?.name.orEmpty()}", style=MaterialTheme.typography.bodyLarge)
        Text("${AppStrings.t("to", lang)}       ${s.map.nodes.find{it.id==s.destination}?.name.orEmpty()}", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={setup=true}, enabled=!ui.busy, modifier=Modifier.weight(1f)) {
                Text(AppStrings.t("plan_route", lang))
            }
            OutlinedButton(onClick={qrScanner=true}, enabled=!ui.busy) {
                Text(AppStrings.t("qr_scan", lang))
            }
        }

        // Quick Destinations (Favourites)
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(AppStrings.t("quick_destinations", lang), style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.Bold)
            val popularTargets = listOf("classroom", "washroom", "gate", "garden", "lobby0").filter { id -> s.map.nodes.any { it.id == id } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                popularTargets.forEach { targetId ->
                    val node = s.map.nodes.first { it.id == targetId }
                    AssistChip(
                        onClick = {
                            if (s.destination != targetId) {
                                isGuiding = false
                                guidedStepIndex = 0
                                vm.action { vm.repository.select(s.profile, s.current, targetId, s.revision) }
                            }
                        },
                        label = { Text(node.name) }
                    )
                }
            }
        }

        // Nearest Facilities (1-Tap)
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(AppStrings.t("nearest_facilities", lang), style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(
                    "WASHROOM" to AppStrings.t("nearest_washroom", lang),
                    "ELEVATOR" to AppStrings.t("nearest_lift", lang),
                    "EXIT" to AppStrings.t("nearest_exit", lang)
                ).forEach { (category, title) ->
                    FilledTonalButton(
                        onClick = {
                            isGuiding = false
                            guidedStepIndex = 0
                            vm.findNearest(category)
                        },
                        enabled = !ui.busy
                    ) {
                        Text(title, style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
            Button(
                onClick = { assistanceDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                enabled = !ui.busy
            ) {
                Text(AppStrings.t("request_assistance", lang))
            }
        }

        Text("Minimum width ${String.format(java.util.Locale.US,"%.2f",s.profile.minWidth)} m • Maximum ramp slope ${number(s.profile.maxSlope)}%", style=MaterialTheme.typography.bodySmall)
    }

    // Guided Mode Banner & Interactive Stepper
    if (isGuiding && route != null && route.edges.isNotEmpty()) {
        val safeIndex = guidedStepIndex.coerceIn(0, route.edges.size - 1)
        val currentEdge = route.edges[safeIndex]
        val currentNode = route.nodes[safeIndex]
        val nextNode = route.nodes[safeIndex + 1]
        val nextFloor = s.map.floors.first { it.id == nextNode.floorId }
        val remainingDist = route.edges.drop(safeIndex).sumOf { it.length }

        Panel("🧭 Guided Navigation Active") {
            Text("Step ${safeIndex + 1} of ${route.edges.size} · Remaining: ${number(remainingDist)} m", fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            Text(
                "${AppStrings.labelSegment(currentEdge.type, lang)}: ${currentNode.name} → ${nextNode.name}",
                style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold
            )
            Text("${number(currentEdge.length)} m · Floor: ${nextFloor.label}", style=MaterialTheme.typography.bodyMedium)

            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (safeIndex > 0) {
                    OutlinedButton(onClick={
                        guidedStepIndex = safeIndex - 1
                    }, modifier=Modifier.weight(1f)) {
                        Text(AppStrings.t("prev_step", lang))
                    }
                }
                Button(onClick={
                    if (!visitedNodes.contains(currentNode.id)) visitedNodes.add(currentNode.id)
                    if (!visitedNodes.contains(nextNode.id)) visitedNodes.add(nextNode.id)
                    vm.action { vm.repository.position(nextNode.id) }
                    
                    if (safeIndex < route.edges.size - 1) {
                        guidedStepIndex = safeIndex + 1
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
                }, modifier=Modifier.weight(1f)) {
                    Text(if (safeIndex < route.edges.size - 1) AppStrings.t("next_step", lang) else "Finish Journey")
                }
            }
            TextButton(onClick={ isGuiding = false; guidedStepIndex = 0 }) {
                Text(AppStrings.t("stop_guided_nav", lang))
            }
        }
    }

    Panel {
        Text(AppStrings.t("campus_overview", lang), style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            s.map.floors.forEach { f -> FilterChip(selected=s.selectedFloor==f.id, onClick={vm.action {vm.repository.floor(f.id)}},
                label={Text("${s.map.buildings.find{it.id==f.buildingId}?.name} · ${f.label}")}, enabled=!ui.busy) }
        }
        CampusCanvas(
            s = s,
            route = route,
            guidedStepIndex = if (isGuiding) guidedStepIndex else null,
            visitedNodes = visitedNodes.toSet()
        )
        Text(AppStrings.t("map_legend", lang), style=MaterialTheme.typography.bodySmall)
    }

    when(val r = ui.route) {
        is RouteResult.Found -> Panel(if(r.edges.isEmpty()) AppStrings.t("reached_destination", lang) else AppStrings.t("accessible_journey", lang)) {
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("${number(r.distance)} m", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    Text(AppStrings.t("distance", lang), style=MaterialTheme.typography.labelMedium)
                }
                Column {
                    Text(number(r.score), style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
                    Text(AppStrings.t("score", lang), style=MaterialTheme.typography.labelMedium)
                }
            }
            Text(if(s.profile.mobility==Mobility.STANDARD) "Lowest score among currently open paths." else "Stairs excluded. Longer routes may offer suitable step-free access.")
            Text("${AppStrings.t("last_confirmed", lang)} ${r.nodes.first().name}", fontWeight=FontWeight.Medium)

            if (r.explanation.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(AppStrings.t("route_explanation", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(r.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (r.exceedsMaxWalkingDistance) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (lang == "hi") "⚠️ यह मार्ग आपकी अधिकतम चलने की सीमा (${s.profile.maxWalkingDistance?.let { number(it) }} मी.) से अधिक है!"
                            else "⚠️ This route exceeds your maximum walking distance limit of ${s.profile.maxWalkingDistance?.let { number(it) }} m!",
                            color = Color(0xFF991B1B),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (r.edges.isNotEmpty()) {
                    Button(onClick={
                        isGuiding = true
                        guidedStepIndex = 0
                        visitedNodes.clear()
                        visitedNodes.add(r.nodes.first().id)
                        val startSpeech = if (lang == "hi") "मार्गदर्शन शुरू। पहला कदम: ${AppStrings.labelSegment(r.edges.first().type, "hi")} से ${r.nodes[1].name}।"
                            else "Starting guidance. Step 1: ${label(r.edges.first().type.name)} to ${r.nodes[1].name}."
                        voice.speak(startSpeech)
                    }, enabled=!ui.busy, modifier=Modifier.weight(1f)) {
                        Text(AppStrings.t("start_guided_nav", lang))
                    }
                }
                OutlinedButton(onClick={position=true}, enabled=!ui.busy) {
                    Text(AppStrings.t("set_current", lang))
                }
            }

            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={
                    val stepsSpeech = if (lang == "hi") {
                        val destinationName = s.map.nodes.find { it.id == s.destination }?.name.orEmpty()
                        val text = StringBuilder("गंतव्य ${destinationName} के लिए मार्ग। कुल दूरी ${number(r.distance)} मीटर। ")
                        r.edges.forEachIndexed { i, e ->
                            val toNode = r.nodes[i+1]
                            text.append("कदम ${i+1}: ${AppStrings.labelSegment(e.type, "hi")} से ${toNode.name}। ")
                        }
                        text.toString()
                    } else {
                        val destinationName = s.map.nodes.find { it.id == s.destination }?.name.orEmpty()
                        val text = StringBuilder("Accessible route to ${destinationName}. Distance: ${number(r.distance)} meters. ")
                        r.edges.forEachIndexed { i, e ->
                            val toNode = r.nodes[i+1]
                            text.append("Step ${i+1}: ${label(e.type.name)} to ${toNode.name}, ${number(e.length)} meters. ")
                        }
                        text.toString()
                    }
                    voice.speak(stepsSpeech)
                }, enabled=!ui.busy) {
                    Text(AppStrings.t("speak_directions", lang))
                }
                OutlinedButton(onClick={ voice.stop() }) {
                    Text(AppStrings.t("stop_speech", lang))
                }
            }

            r.edges.forEachIndexed { i, e ->
                val to = r.nodes[i+1]; val floor = s.map.floors.first { it.id == to.floorId }
                HorizontalDivider()
                Text("${i+1}. ${AppStrings.labelSegment(e.type, lang)} → ${to.name}", fontWeight=FontWeight.Bold)
                Text("${number(e.length)} m · ${floor.label}", style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={
                    vm.action { vm.repository.position(to.id) }
                    voice.speak(if (lang == "hi") "आप ${to.name} पर पहुंच चुके हैं।" else "Reached ${to.name}.")
                }, enabled=!ui.busy) {
                    Text("${AppStrings.t("i_reached", lang)} ${to.name}")
                }
            }
            Text("Map ${r.mapVersion} • State revision ${r.revision}", style=MaterialTheme.typography.labelSmall)
        }
        is RouteResult.Unavailable -> Panel(AppStrings.t("no_route", lang)) {
            Text(AppStrings.t("no_route_sub", lang))
            r.reasons.forEach { Text("• $it", style=MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick={position=true}, enabled=!ui.busy) {
                Text(AppStrings.t("set_current", lang))
            }
        }
        null -> Text("Calculating route…")
    }

    if(setup) RouteSetup(s, lang, onDismiss={setup=false}) { profile, start, end ->
        isGuiding = false
        guidedStepIndex = 0
        vm.action("Route preferences saved") { vm.repository.select(profile, start, end, s.revision) }; setup=false
    }
    if(position) PickNode(AppStrings.t("set_current", lang), s.map.nodes, onDismiss={position=false}) { node ->
        vm.action { vm.repository.position(node.id) }; position=false
    }
    if(qrScanner) QRScanDialog(s, lang, onDismiss={qrScanner=false}) { node ->
        isGuiding = false
        guidedStepIndex = 0
        vm.action("Location updated via QR placard: ${node.name}") { vm.repository.position(node.id) }
        voice.speak(if (lang == "hi") "QR कोड स्कैन हुआ: वर्तमान स्थान ${node.name}।" else "QR scanned: current position set to ${node.name}.")
        qrScanner=false
    }
    if(assistanceDialog) AssistanceRequestDialog(s, lang, onDismiss={assistanceDialog=false}) { urgency, note ->
        vm.requestAssistance(urgency, note)
        assistanceDialog = false
    }
}

@Composable private fun QRScanDialog(s: AppSnapshot, lang: String, onDismiss: () -> Unit, onSelect: (Node) -> Unit) {
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
        title = { Text(if (lang == "hi") "कैंपस QR कोड स्कैन" else "Scan Campus QR Placard") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if (lang == "hi") "कैंपस के QR कोड को स्कैन करें या नीचे दिए गए प्लेकार्ड का चयन करें:" else "Scan physical QR code placard at your campus location:")
                
                placards.forEach { (nodeId, description) ->
                    val node = s.map.nodes.first { it.id == nodeId }
                    OutlinedButton(
                        onClick = { onSelect(node) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment=Alignment.Start) {
                            Text("📍 ${node.name}", fontWeight=FontWeight.Bold)
                            Text(description, style=MaterialTheme.typography.bodySmall)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = customCode.trim().removePrefix("accesspath://node/").lowercase()
                    val match = s.map.nodes.find { it.id.equals(cleaned, ignoreCase=true) || it.name.contains(cleaned, ignoreCase=true) }
                    if (match != null) onSelect(match)
                },
                enabled = customCode.isNotBlank()
            ) {
                Text(if (lang == "hi") "पुष्टि करें" else "Confirm QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable private fun PickNode(title:String, nodes:List<Node>, onDismiss:()->Unit, onPick:(Node)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss, title={Text(title)}, text={
        Column { OutlinedTextField(query, {query=it}, label={Text("Search locations")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
            Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
                nodes.filter{it.name.contains(query,true)}.forEach{n -> TextButton(onClick={onPick(n)}, modifier=Modifier.fillMaxWidth()) {Text("${n.name} · ${n.floorId}")} }
            }
        }
    }, confirmButton={}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun RouteSetup(s:AppSnapshot, lang:String, onDismiss:()->Unit, onSave:(Profile,String,String)->Unit) {
    var mobility by rememberSaveable {mutableStateOf(s.profile.mobility)}
    var width by rememberSaveable {mutableStateOf(s.profile.minWidth.toString())}
    var slope by rememberSaveable {mutableStateOf(s.profile.maxSlope.toString())}
    var maxWalking by rememberSaveable {mutableStateOf(s.profile.maxWalkingDistance?.toString().orEmpty())}
    var start by rememberSaveable {mutableStateOf(s.current)}; var end by rememberSaveable {mutableStateOf(s.destination)}
    var picking by rememberSaveable {mutableIntStateOf(0)}; var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss, title={Text(AppStrings.t("plan_route", lang))}, text={
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Mobility.entries.forEach{m -> FilterChip(selected=mobility==m, onClick={mobility=m}, label={Text(AppStrings.labelMobility(m, lang))})}
            OutlinedButton(onClick={picking=1}, modifier=Modifier.fillMaxWidth()){Text("${AppStrings.t("from", lang)}: ${s.map.nodes.find{it.id==start}?.name}")}
            OutlinedButton(onClick={picking=2}, modifier=Modifier.fillMaxWidth()){Text("${AppStrings.t("to", lang)}: ${s.map.nodes.find{it.id==end}?.name}")}
            TextButton(onClick={val old=start; start=end; end=old}){Text(AppStrings.t("swap_start_end", lang).ifBlank { "Swap start and destination" })}
            OutlinedTextField(width, {width=it}, label={Text("Minimum clear width in metres")}, singleLine=true)
            OutlinedTextField(slope, {slope=it}, label={Text("Maximum ramp slope in percent")}, singleLine=true)
            OutlinedTextField(
                maxWalking,
                {maxWalking=it},
                label={Text(if (lang == "hi") "अधिकतम चलने की सीमा (मीटर, वैकल्पिक)" else "Maximum walking distance in metres (optional)")},
                placeholder={Text("e.g. 100")},
                singleLine=true
            )
            Text("Width, slope and max distance constrain routes. These are your preferences, not a safety certification.", style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it, color=MaterialTheme.colorScheme.error)}
        }
    }, confirmButton={Button(onClick={
        val mw = if (maxWalking.isBlank()) null else maxWalking.toDoubleOrNull()
        val p=Profile(mobility, width.toDoubleOrNull()?:Double.NaN, slope.toDoubleOrNull()?:Double.NaN, maxWalkingDistance=mw)
        try {p.validate();onSave(p,start,end)}catch(e:IllegalArgumentException){error=e.message}
    }){Text("Find route")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(picking!=0) PickNode(if(picking==1) "Choose starting point" else "Choose destination", s.map.nodes, {picking=0}) {
        if(picking==1) start=it.id else end=it.id; picking=0
    }
}

@Composable private fun ReportsScreen(s:AppSnapshot, vm:AccessViewModel, reviewer:Boolean, busy:Boolean, lang:String) {
    var create by rememberSaveable {mutableStateOf(false)}
    var review by remember {mutableStateOf<BarrierReport?>(null)}
    var filter by rememberSaveable {mutableStateOf("All")}
    Heading(AppStrings.t("reports_eyebrow", lang), AppStrings.t("reports_title", lang), AppStrings.t("reports_detail", lang))
    Button(onClick={create=true}, enabled=!busy && s.map.edges.isNotEmpty(), modifier=Modifier.fillMaxWidth()){Text(AppStrings.t("report_barrier", lang))}
    Notice("Pending reports create precautionary closures until reviewed. Reviewer controls are enabled from Manage.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        (listOf("All")+ReportStatus.entries.map{it.name}).forEach{v ->
            val displayLabel = if (v == "All") AppStrings.t("all", lang) else AppStrings.labelReportStatus(ReportStatus.valueOf(v), lang)
            FilterChip(selected=filter==v, onClick={filter=v}, label={Text(displayLabel)})
        }
    }
    val reports=s.reports.filter{filter=="All" || it.status.name==filter}.reversed()
    if(reports.isEmpty()) Panel(AppStrings.t("no_reports", lang)) {Text("New reports will appear here with their review status.")}
    reports.forEach { r -> Panel(r.category) {
        Text(AppStrings.labelReportStatus(r.status, lang), fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
        Text(edgeLabel(s, r.edgeId)); Text(r.description)
        if (r.photoUri != null) Text("📷 Photo reference: ${r.photoUri}", style=MaterialTheme.typography.bodySmall)
        if (r.hasVoiceNote) Text("🎙️ Voice memo recorded", style=MaterialTheme.typography.bodySmall)
        Text("Reported ${time(r.createdAt)} · Auto-expires in ${r.expiryHours}h", style=MaterialTheme.typography.bodySmall)
        if(r.reason.isNotBlank()) Text("Decision: ${r.reason} · ${time(r.reviewedAt)}", style=MaterialTheme.typography.bodySmall)
        if(reviewer && r.status in setOf(ReportStatus.PENDING, ReportStatus.VERIFIED)) OutlinedButton(onClick={review=r}, enabled=!busy){Text(AppStrings.t("review_report", lang))}
    } }
    if(create) ReportDialog(s, {create=false}){id,edge,category,text,photo,voiceNote,expiry ->
        vm.submitReportWithMedia(id,edge,category,text,photo,voiceNote,expiry)
        create=false
    }
    review?.let{r -> DecisionDialog(r, {review=null}){status,reason -> vm.action("Review saved") {vm.repository.review(r.id,status,reason,s.revision)};review=null}}
}

private fun edgeLabel(s:AppSnapshot, id:String):String {
    val e=s.map.edges.find{it.id==id}?:return id
    return "${s.map.nodes.find{it.id==e.from}?.name} → ${s.map.nodes.find{it.id==e.to}?.name} (${label(e.type.name)})"
}

@Composable private fun ReportDialog(
    s:AppSnapshot,
    onDismiss:()->Unit,
    onSave:(String,String,String,String,String?,Boolean,Int)->Unit
) {
    val submission=rememberSaveable{UUID.randomUUID().toString()}
    var edge by rememberSaveable{mutableStateOf(s.map.edges.firstOrNull()?.id.orEmpty())};var category by rememberSaveable{mutableStateOf(CampusRepository.categories.first())}
    var description by rememberSaveable{mutableStateOf("")}; var picking by remember{mutableStateOf(false)}
    var photoUri by rememberSaveable{mutableStateOf("")}
    var hasVoiceNote by rememberSaveable{mutableStateOf(false)}
    var expiryHoursStr by rememberSaveable{mutableStateOf("24")}

    AlertDialog(onDismissRequest=onDismiss, title={Text("Report a barrier")}, text={Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={picking=true}){Text(edgeLabel(s,edge))}
        ChoiceMenu("Issue type", category, CampusRepository.categories){category=it}
        OutlinedTextField(description, {if(it.length<=1000)description=it}, label={Text("Describe the barrier")}, minLines=3, supportingText={Text("${description.length}/1000 · minimum 5 characters")})
        OutlinedTextField(photoUri, {photoUri=it}, label={Text("Photo file / reference (optional)")}, placeholder={Text("e.g. broken_lift_ground.jpg")}, singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=hasVoiceNote, onCheckedChange={hasVoiceNote=it})
            Text("Attach voice memo recording")
        }
        OutlinedTextField(expiryHoursStr, {expiryHoursStr=it}, label={Text("Temporary barrier expiry (hours, default 24)")}, singleLine=true)
        if(s.reports.any{it.edgeId==edge && it.status in setOf(ReportStatus.PENDING,ReportStatus.VERIFIED)}) Notice("This segment already has an active report. Submit only if this is a distinct observation.")
    }}, confirmButton={Button(onClick={
        val exp = expiryHoursStr.toIntOrNull() ?: 24
        val photo = if (photoUri.isBlank()) null else photoUri.trim()
        onSave(submission,edge,category,description,photo,hasVoiceNote,exp)
    }, enabled=description.trim().length in 5..1000){Text("Submit report")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(picking) EdgePicker(s, {picking=false}){edge=it;picking=false}
}

@Composable private fun ChoiceMenu(title:String, value:String, options:List<String>, onPick:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box {OutlinedButton(onClick={open=true}){Text("$title: ${label(value)}")}
        DropdownMenu(expanded=open, onDismissRequest={open=false}){options.forEach{v->DropdownMenuItem(text={Text(label(v))}, onClick={onPick(v);open=false})}}
    }
}

@Composable private fun EdgePicker(s:AppSnapshot, onDismiss:()->Unit, onPick:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss, title={Text("Choose a route segment")}, text={Column{
        OutlinedTextField(query, {query=it}, label={Text("Search segments")}, singleLine=true)
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
            s.map.edges.filter{edgeLabel(s,it.id).contains(query,true)}.forEach{e->TextButton(onClick={onPick(e.id)}){Text(edgeLabel(s,e.id))}}
        }
    }}, confirmButton={}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun DecisionDialog(report:BarrierReport, onDismiss:()->Unit, onSave:(ReportStatus,String)->Unit) {
    val choices=if(report.status==ReportStatus.PENDING) listOf(ReportStatus.VERIFIED,ReportStatus.REJECTED) else listOf(ReportStatus.RESOLVED)
    var status by remember{mutableStateOf(choices.first())};var reason by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss, title={Text("Review barrier")}, text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(report.description)
        ChoiceMenu("Decision", status.name, choices.map{it.name}){status=ReportStatus.valueOf(it)}
        OutlinedTextField(reason, {if(it.length<=1000)reason=it}, label={Text("Reason for decision")}, minLines=2)
        Text("Local demo reviewer · Decisions are recorded in the audit history.", style=MaterialTheme.typography.bodySmall)
    }}, confirmButton={Button(onClick={onSave(status,reason)}, enabled=reason.trim().length>=5){Text("Save decision")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun ManagementScreen(s:AppSnapshot, vm:AccessViewModel, reviewer:Boolean, onRole:(Boolean)->Unit, busy:Boolean, lang:String) {
    var facility by remember{mutableStateOf<Facility?>(null)};var edgePicker by remember{mutableStateOf(false)};var reset by remember{mutableStateOf(false)}
    var addLandmarkDialog by remember{mutableStateOf(false)}; var addConnectorDialog by remember{mutableStateOf(false)}
    var editLandmarkDialog by remember{mutableStateOf(false)}; var deleteLandmarkDialog by remember{mutableStateOf(false)}

    Heading(AppStrings.t("manage_eyebrow", lang), AppStrings.t("manage_title", lang), AppStrings.t("manage_detail", lang))
    Panel(AppStrings.t("demo_role", lang)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Enable reviewer controls", Modifier.weight(1f));Switch(checked=reviewer, onCheckedChange=onRole)}
        Text("This switch demonstrates a review workflow. It is not secure authentication or shared administration.", style=MaterialTheme.typography.bodySmall)
    }
    if(!reviewer) {Notice("Enable local demo reviewer mode to update facilities, verify reports, edit map data, and simulate failures.");return}

    // Map Editor Section
    Panel(AppStrings.t("map_editor", lang)) {
        Text("Add new accessible landmarks and corridors to the live graph with full constraints validation.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={addLandmarkDialog=true}, enabled=!busy, modifier=Modifier.weight(1f)) {
                Text(AppStrings.t("add_landmark", lang))
            }
            OutlinedButton(onClick={addConnectorDialog=true}, enabled=!busy && s.map.nodes.size >= 2, modifier=Modifier.weight(1f)) {
                Text(AppStrings.t("add_connector", lang))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={editLandmarkDialog=true}, enabled=!busy && s.map.nodes.isNotEmpty(), modifier=Modifier.weight(1f)) {
                Text(AppStrings.t("edit_landmark", lang))
            }
            OutlinedButton(onClick={deleteLandmarkDialog=true}, enabled=!busy && s.map.nodes.size > 2, modifier=Modifier.weight(1f)) {
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

    s.map.facilities.forEach{f->Panel(f.name){
        Text(AppStrings.labelAvailability(f.availability, lang), fontWeight=FontWeight.Bold, color=if(f.availability==Availability.AVAILABLE)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(f.reason);Text("${f.source} · ${time(f.updatedAt)}", style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={facility=f}, enabled=!busy){Text("Update ${f.name}")}
    }}
    Panel("Failure simulation") {
        Text("Routes recalculate automatically from the last confirmed location. Switch to Navigate to inspect the result.")
        Button(onClick={vm.action("All lifts unavailable") {vm.repository.allLifts(false)}}, enabled=!busy, modifier=Modifier.fillMaxWidth()){Text(AppStrings.t("simulate_lifts_down", lang))}
        OutlinedButton(onClick={vm.action("Lifts restored") {vm.repository.allLifts(true)}}, enabled=!busy, modifier=Modifier.fillMaxWidth()){Text(AppStrings.t("restore_lifts", lang))}
        OutlinedButton(onClick={edgePicker=true}, enabled=!busy, modifier=Modifier.fillMaxWidth()){Text(AppStrings.t("block_path", lang))}
        s.blockedEdges.sorted().forEach{id->TextButton(onClick={vm.action("Path restored") {vm.repository.block(id,false)}}, enabled=!busy){Text("Restore: ${edgeLabel(s,id)}")}}
        if(s.map.provenance=="Illustrative demo") TextButton(onClick={reset=true}, enabled=!busy){Text(AppStrings.t("reset_demo", lang))}
    }
    Panel("Audit history") {
        if(s.audit.isEmpty()) Text("No reviewer actions yet.")
        s.audit.takeLast(30).reversed().forEach{e->Text("${e.action} · ${e.before} → ${e.after}", fontWeight=FontWeight.Medium);Text("${e.reason}\n${e.actor} · ${time(e.time)}", style=MaterialTheme.typography.bodySmall);HorizontalDivider()}
    }
    facility?.let{f->FacilityDialog(f, {facility=null}){status,reason->vm.action("Facility status updated") {vm.repository.facility(f.id,status,reason,s.revision)};facility=null}}
    if(edgePicker) EdgePicker(s, {edgePicker=false}){vm.action("Path blocked in both directions") {vm.repository.block(it,true)};edgePicker=false}
    if(reset) AlertDialog(onDismissRequest={reset=false}, title={Text("Reset demo data?")}, text={Text("This removes demo reports and audit history, restores facilities and resets your route preferences. Imported maps are not reset.")},
        confirmButton={TextButton(onClick={vm.action("Demo reset") {vm.repository.resetDemo()};reset=false}){Text("Reset demo")}}, dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})

    if(addLandmarkDialog) AddLandmarkDialog(s, onDismiss={addLandmarkDialog=false}) { node ->
        vm.addLandmark(node)
        addLandmarkDialog = false
    }

    if(addConnectorDialog) AddConnectorDialog(s, onDismiss={addConnectorDialog=false}) { edge, twoWay ->
        vm.addConnector(edge, twoWay)
        addConnectorDialog = false
    }

    if(editLandmarkDialog) EditLandmarkDialog(s, onDismiss={editLandmarkDialog=false}) { id, name, x, y ->
        vm.editLandmark(id, name, x, y)
        editLandmarkDialog = false
    }

    if(deleteLandmarkDialog) DeleteLandmarkDialog(s, onDismiss={deleteLandmarkDialog=false}) { id ->
        vm.deleteLandmark(id)
        deleteLandmarkDialog = false
    }
}

@Composable private fun AddLandmarkDialog(s: AppSnapshot, onDismiss: () -> Unit, onSave: (Node) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var floorId by rememberSaveable { mutableStateOf(s.map.floors.firstOrNull()?.id.orEmpty()) }
    var xStr by rememberSaveable { mutableStateOf("50.0") }
    var yStr by rememberSaveable { mutableStateOf("50.0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Accessible Landmark") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label={ Text("Node ID (unique, e.g. room205)") }, singleLine=true)
                OutlinedTextField(name, { name = it }, label={ Text("Landmark Name (e.g. Physics Lab 205)") }, singleLine=true)
                ChoiceMenu("Floor", floorId, s.map.floors.map { it.id }) { floorId = it }
                OutlinedTextField(xStr, { xStr = it }, label={ Text("X coordinate (0 - 1000)") }, singleLine=true)
                OutlinedTextField(yStr, { yStr = it }, label={ Text("Y coordinate (0 - 1000)") }, singleLine=true)
                error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
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
                enabled = id.isNotBlank() && name.isNotBlank()
            ) {
                Text("Save Landmark")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable private fun AddConnectorDialog(s: AppSnapshot, onDismiss: () -> Unit, onSave: (Edge, Boolean) -> Unit) {
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
        title = { Text("Add Path / Corridor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label={ Text("Connector ID (e.g. corridor_205)") }, singleLine=true)
                ChoiceMenu("From", from, s.map.nodes.map { it.id }) { from = it }
                ChoiceMenu("To", to, s.map.nodes.map { it.id }) { to = it }
                ChoiceMenu("Type", segmentType.name, Segment.entries.map { it.name }) { segmentType = Segment.valueOf(it) }
                OutlinedTextField(lengthStr, { lengthStr = it }, label={ Text("Length in metres") }, singleLine=true)
                OutlinedTextField(widthStr, { widthStr = it }, label={ Text("Clear width in metres") }, singleLine=true)
                OutlinedTextField(slopeStr, { slopeStr = it }, label={ Text("Ramp slope in percent") }, singleLine=true)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=stepFree, onCheckedChange={ stepFree = it })
                    Text("Step-free access")
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=wheelchairSuitable, onCheckedChange={ wheelchairSuitable = it })
                    Text("Wheelchair suitable")
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=twoWay, onCheckedChange={ twoWay = it })
                    Text("Two-way (walkable in both directions)")
                }
                error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
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
                enabled = id.isNotBlank() && from != to
            ) {
                Text("Save Connector")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable private fun FacilityDialog(f:Facility, onDismiss:()->Unit, onSave:(Availability,String)->Unit) {
    var status by remember{mutableStateOf(f.availability)};var reason by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss, title={Text(f.name)}, text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        ChoiceMenu("Status", status.name, Availability.entries.map{it.name}){status=Availability.valueOf(it)}
        OutlinedTextField(reason, {if(it.length<=1000)reason=it}, label={Text("Reason or observation")}, minLines=2)
    }}, confirmButton={Button(onClick={onSave(status,reason)}, enabled=reason.trim().length>=5){Text("Update status")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun SettingsScreen(s:AppSnapshot, vm:AccessViewModel, busy:Boolean, lang:String, onLangChange:(String)->Unit) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.importMap(it)}}
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{vm.exportMap(it)}}

    Heading(AppStrings.t("settings_eyebrow", lang), AppStrings.t("settings_title", lang), AppStrings.t("settings_detail", lang))
    
    Panel(AppStrings.t("language", lang)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected=lang == "en",
                onClick={ onLangChange("en") },
                label={ Text("English (US)") }
            )
            FilterChip(
                selected=lang == "hi",
                onClick={ onLangChange("hi") },
                label={ Text("हिन्दी (Hindi)") }
            )
        }
    }

    Panel("Map and data") {
        Text("${s.map.name}\nVersion ${s.map.version} • Schema ${s.map.schemaVersion}\n${s.map.nodes.size} nodes · ${s.map.edges.size} directed segments")
        Text("${s.map.provenance}\nVerified: ${time(s.map.verifiedAt)}", style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))}, enabled=!busy, modifier=Modifier.weight(1f)){
                Text(AppStrings.t("import_map", lang))
            }
            Button(onClick={exporter.launch("${s.map.name.lowercase().replace(' ','-')}-v${s.map.version}.json")}, enabled=!busy, modifier=Modifier.weight(1f)){
                Text(AppStrings.t("export_map", lang))
            }
        }
        Text("Imports and live additions are validated against connectivity and accessibility rules before commit.", style=MaterialTheme.typography.bodySmall)
    }
    Panel("How to use AccessPath") {
        Text("1. Open Navigate and choose Plan a route or tap Quick Destinations.\n2. Tap 'Start guided navigation' for step-by-step turn guidance with breadcrumb visual tracking.\n3. Read the route by floor and follow the landmark steps.\n4. Confirm locations as you reach them or scan QR placards.\n5. Use 'Speak directions' for audio assistance in English or Hindi.\n6. In Manage, enable demo reviewer mode to add landmarks/paths, verify reports and simulate failures.\n7. In Settings, export surveyed maps to JSON.")
    }
    Panel("Privacy and limitations") {
        Text("Your map, reports, profile and audit history are stored on this device. This build uses no camera, microphone, GPS, account or remote analytics. It does not track your live location.")
        Text("The bundled campus is illustrative, not a surveyed GLBITM map. Routes depend on the supplied measurements and last-known conditions. Do not follow a route through a visible hazard.")
        Text("Automatic 2D mapping, live positioning and shared cloud reports are the next phase. This build does not control a wheelchair.")
        Text("To remove all local app data, use Android Settings → Apps → AccessPath → Storage → Clear storage. This deletes imported maps, reports and preferences.", style=MaterialTheme.typography.bodySmall)
    }
    Panel("Build information") {
        Text("AccessPath 1.0.0-demo\nNative Android · Kotlin · Compose · Room\nDijkstra routing with hard accessibility constraints\nFeatures: Guided Navigation & Breadcrumb Trail, Hindi UI, Voice Directions (TTS), QR Start Points, Map Editor, JSON Export")
        Text("Source: github.com/rudrasinha05/AccessPath", style=MaterialTheme.typography.bodySmall)
    }
}
