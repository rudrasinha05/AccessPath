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
    MaterialTheme(colorScheme=colors) {
        val ui by vm.ui.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var reviewer by rememberSaveable { mutableStateOf(false) }
        val snackbar=remember { SnackbarHostState() }
        LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.message(null) } }
        BackHandler(tab != 0) { tab=0 }
        Scaffold(
            containerColor=MaterialTheme.colorScheme.background,
            topBar={ TopAppBar(title={ Column { Text("AccessPath", fontWeight=FontWeight.Bold); Text("A clearer way through campus", style=MaterialTheme.typography.labelSmall) } },
                actions={ TextButton(onClick={ tab=3 }) { Text("Help") } }, colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)) },
            bottomBar={ NavigationBar {
                listOf("Navigate", "Reports", "Manage", "Settings").forEachIndexed { index, title ->
                    NavigationBarItem(selected=tab==index, onClick={tab=index}, icon={ Text(listOf("↗","!","☷","⋯")[index], style=MaterialTheme.typography.titleLarge) }, label={Text(title)})
                }
            } }, snackbarHost={ SnackbarHost(snackbar) }
        ) { padding ->
            val state=ui.snapshot
            if(state==null) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Preparing your campus map") }
            } else Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(ui.busy || ui.routing) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(tab) {
                    0 -> NavigationScreen(ui, vm)
                    1 -> ReportsScreen(state, vm, reviewer, ui.busy)
                    2 -> ManagementScreen(state, vm, reviewer, {reviewer=it}, ui.busy)
                    3 -> SettingsScreen(state, vm, ui.busy)
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

@Composable private fun NavigationScreen(ui: ScreenState, vm: AccessViewModel) {
    val s=ui.snapshot!!
    var setup by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf(false) }
    Heading("Your campus companion","Find your way.","Routes that respect your mobility needs.")
    Notice("${s.map.provenance} • ${s.map.name} • Offline, last-known facility status")
    Panel {
        Text(label(s.profile.mobility.name),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        Text("From  ${s.map.nodes.find{it.id==s.current}?.name.orEmpty()}",style=MaterialTheme.typography.bodyLarge)
        Text("To       ${s.map.nodes.find{it.id==s.destination}?.name.orEmpty()}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Button(onClick={setup=true},enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text("Plan a route") }
        Text("Minimum width ${String.format(java.util.Locale.US,"%.2f",s.profile.minWidth)} m • Maximum ramp slope ${number(s.profile.maxSlope)}%",style=MaterialTheme.typography.bodySmall)
    }
    Panel {
        Text("Campus overview",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            s.map.floors.forEach { f -> FilterChip(selected=s.selectedFloor==f.id,onClick={vm.action {vm.repository.floor(f.id)}},
                label={Text("${s.map.buildings.find{it.id==f.buildingId}?.name} · ${f.label}")},enabled=!ui.busy) }
        }
        CampusCanvas(s,ui.route as? RouteResult.Found)
        Text("Green: route · Red dashed: restricted · Grey: other paths\nPinch to zoom, drag to pan. Directions below provide a text alternative.",style=MaterialTheme.typography.bodySmall)
    }
    when(val route=ui.route) {
        is RouteResult.Found -> Panel(if(route.edges.isEmpty()) "You are already at the destination" else "Your accessible journey") {
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column { Text("${number(route.distance)} m",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text("Route distance",style=MaterialTheme.typography.labelMedium) }
                Column { Text(number(route.score),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text("Route score, not minutes",style=MaterialTheme.typography.labelMedium) }
            }
            Text(if(s.profile.mobility==Mobility.STANDARD) "Lowest score among currently open paths." else "Stairs excluded. Longer routes may offer suitable step-free access.")
            Text("Last confirmed position: ${route.nodes.first().name}",fontWeight=FontWeight.Medium)
            OutlinedButton(onClick={position=true},enabled=!ui.busy) { Text("Set current location") }
            route.edges.forEachIndexed { i,e ->
                val to=route.nodes[i+1]; val floor=s.map.floors.first{it.id==to.floorId}
                HorizontalDivider()
                Text("${i+1}. ${label(e.type.name)} to ${to.name}",fontWeight=FontWeight.Bold)
                Text("${number(e.length)} m · ${floor.label}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={vm.action {vm.repository.position(to.id)}},enabled=!ui.busy) { Text("I have reached ${to.name}") }
            }
            Text("Map ${route.mapVersion} • State revision ${route.revision}",style=MaterialTheme.typography.labelSmall)
        }
        is RouteResult.Unavailable -> Panel("No accessible route available") {
            Text("Your requirements have not been relaxed. Confirm your location, choose another destination or review facility status.")
            route.reasons.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick={position=true},enabled=!ui.busy) { Text("Set current location") }
        }
        null -> Text("Calculating route…")
    }
    if(setup) RouteSetup(s,onDismiss={setup=false}) { profile,start,end ->
        vm.action("Route preferences saved") { vm.repository.select(profile,start,end,s.revision) }; setup=false
    }
    if(position) PickNode("Confirm your current location",s.map.nodes,onDismiss={position=false}) { node ->
        vm.action {vm.repository.position(node.id)}; position=false
    }
}

@Composable private fun PickNode(title:String,nodes:List<Node>,onDismiss:()->Unit,onPick:(Node)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={
        Column { OutlinedTextField(query,{query=it},label={Text("Search locations")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
                nodes.filter{it.name.contains(query,true)}.forEach{n -> TextButton(onClick={onPick(n)},modifier=Modifier.fillMaxWidth()) {Text("${n.name} · ${n.floorId}")} }
            }
        }
    },confirmButton={},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun RouteSetup(s:AppSnapshot,onDismiss:()->Unit,onSave:(Profile,String,String)->Unit) {
    var mobility by rememberSaveable {mutableStateOf(s.profile.mobility)}
    var width by rememberSaveable {mutableStateOf(s.profile.minWidth.toString())}
    var slope by rememberSaveable {mutableStateOf(s.profile.maxSlope.toString())}
    var start by rememberSaveable {mutableStateOf(s.current)}; var end by rememberSaveable {mutableStateOf(s.destination)}
    var picking by rememberSaveable {mutableIntStateOf(0)}; var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Plan your route")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Mobility.entries.forEach{m -> FilterChip(selected=mobility==m,onClick={mobility=m},label={Text(label(m.name))})}
            OutlinedButton(onClick={picking=1},modifier=Modifier.fillMaxWidth()){Text("From: ${s.map.nodes.find{it.id==start}?.name}")}
            OutlinedButton(onClick={picking=2},modifier=Modifier.fillMaxWidth()){Text("To: ${s.map.nodes.find{it.id==end}?.name}")}
            TextButton(onClick={val old=start; start=end; end=old}){Text("Swap start and destination")}
            OutlinedTextField(width,{width=it},label={Text("Minimum clear width in metres")},singleLine=true)
            OutlinedTextField(slope,{slope=it},label={Text("Maximum ramp slope in percent")},singleLine=true)
            Text("Width and slope constrain wheelchair routes. These are your preferences, not a safety certification.",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(onClick={
        val p=Profile(mobility,width.toDoubleOrNull()?:Double.NaN,slope.toDoubleOrNull()?:Double.NaN)
        try {p.validate();onSave(p,start,end)}catch(e:IllegalArgumentException){error=e.message}
    }){Text("Find route")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(picking!=0) PickNode(if(picking==1) "Choose starting point" else "Choose destination",s.map.nodes,{picking=0}) {
        if(picking==1) start=it.id else end=it.id; picking=0
    }
}

@Composable private fun ReportsScreen(s:AppSnapshot,vm:AccessViewModel,reviewer:Boolean,busy:Boolean) {
    var create by rememberSaveable {mutableStateOf(false)}
    var review by remember {mutableStateOf<BarrierReport?>(null)}
    var filter by rememberSaveable {mutableStateOf("All")}
    Heading("Community care","Keep paths open.","Report barriers and follow their verification status.")
    Button(onClick={create=true},enabled=!busy && s.map.edges.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Report a barrier")}
    Notice("Pending reports create precautionary closures until reviewed. Reviewer controls are enabled from Manage.")
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        (listOf("All")+ReportStatus.entries.map{it.name}).forEach{v -> FilterChip(selected=filter==v,onClick={filter=v},label={Text(label(v))})}
    }
    val reports=s.reports.filter{filter=="All" || it.status.name==filter}.reversed()
    if(reports.isEmpty()) Panel("No reports here") {Text("New reports will appear here with their review status.")}
    reports.forEach { r -> Panel(r.category) {
        Text(label(r.status.name),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Text(edgeLabel(s,r.edgeId));Text(r.description)
        Text("Reported ${time(r.createdAt)}",style=MaterialTheme.typography.bodySmall)
        if(r.reason.isNotBlank()) Text("Decision: ${r.reason} · ${time(r.reviewedAt)}",style=MaterialTheme.typography.bodySmall)
        if(reviewer && r.status in setOf(ReportStatus.PENDING,ReportStatus.VERIFIED)) OutlinedButton(onClick={review=r},enabled=!busy){Text("Review this report")}
    } }
    if(create) ReportDialog(s,{create=false}){id,edge,category,text -> vm.action("Barrier report saved") {vm.repository.submit(id,edge,category,text)};create=false}
    review?.let{r -> DecisionDialog(r,{review=null}){status,reason -> vm.action("Review saved") {vm.repository.review(r.id,status,reason,s.revision)};review=null}}
}
private fun edgeLabel(s:AppSnapshot,id:String):String {
    val e=s.map.edges.find{it.id==id}?:return id
    return "${s.map.nodes.find{it.id==e.from}?.name} → ${s.map.nodes.find{it.id==e.to}?.name} (${label(e.type.name)})"
}
@Composable private fun ReportDialog(s:AppSnapshot,onDismiss:()->Unit,onSave:(String,String,String,String)->Unit) {
    val submission=rememberSaveable{UUID.randomUUID().toString()}
    var edge by rememberSaveable{mutableStateOf(s.map.edges.firstOrNull()?.id.orEmpty())};var category by rememberSaveable{mutableStateOf(CampusRepository.categories.first())}
    var description by rememberSaveable{mutableStateOf("")}; var picking by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Report a barrier")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={picking=true}){Text(edgeLabel(s,edge))}
        ChoiceMenu("Issue type",category,CampusRepository.categories){category=it}
        OutlinedTextField(description,{if(it.length<=1000)description=it},label={Text("Describe the barrier")},minLines=3,supportingText={Text("${description.length}/1000 · minimum 5 characters")})
        if(s.reports.any{it.edgeId==edge && it.status in setOf(ReportStatus.PENDING,ReportStatus.VERIFIED)}) Notice("This segment already has an active report. Submit only if this is a distinct observation.")
    }},confirmButton={Button(onClick={onSave(submission,edge,category,description)},enabled=description.trim().length in 5..1000){Text("Submit report")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(picking) EdgePicker(s,{picking=false}){edge=it;picking=false}
}
@Composable private fun ChoiceMenu(title:String,value:String,options:List<String>,onPick:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box {OutlinedButton(onClick={open=true}){Text("$title: ${label(value)}")}
        DropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEach{v->DropdownMenuItem(text={Text(label(v))},onClick={onPick(v);open=false})}}
    }
}
@Composable private fun EdgePicker(s:AppSnapshot,onDismiss:()->Unit,onPick:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Choose a route segment")},text={Column{
        OutlinedTextField(query,{query=it},label={Text("Search segments")},singleLine=true)
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
            s.map.edges.filter{edgeLabel(s,it.id).contains(query,true)}.forEach{e->TextButton(onClick={onPick(e.id)}){Text(edgeLabel(s,e.id))}}
        }
    }},confirmButton={},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun DecisionDialog(report:BarrierReport,onDismiss:()->Unit,onSave:(ReportStatus,String)->Unit) {
    val choices=if(report.status==ReportStatus.PENDING) listOf(ReportStatus.VERIFIED,ReportStatus.REJECTED) else listOf(ReportStatus.RESOLVED)
    var status by remember{mutableStateOf(choices.first())};var reason by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Review barrier")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(report.description)
        ChoiceMenu("Decision",status.name,choices.map{it.name}){status=ReportStatus.valueOf(it)}
        OutlinedTextField(reason,{if(it.length<=1000)reason=it},label={Text("Reason for decision")},minLines=2)
        Text("Local demo reviewer · Decisions are recorded in the audit history.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={Button(onClick={onSave(status,reason)},enabled=reason.trim().length>=5){Text("Save decision")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun ManagementScreen(s:AppSnapshot,vm:AccessViewModel,reviewer:Boolean,onRole:(Boolean)->Unit,busy:Boolean) {
    var facility by remember{mutableStateOf<Facility?>(null)};var edgePicker by remember{mutableStateOf(false)};var reset by remember{mutableStateOf(false)}
    Heading("Campus operations","Stay one step ahead.","Review facility conditions and demonstrate changing routes.")
    Panel("Local demo role") {
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Enable reviewer controls",Modifier.weight(1f));Switch(checked=reviewer,onCheckedChange=onRole)}
        Text("This switch demonstrates a review workflow. It is not secure authentication or shared administration.",style=MaterialTheme.typography.bodySmall)
    }
    if(!reviewer) {Notice("Enable local demo reviewer mode to update facilities, verify reports and simulate failures.");return}
    s.map.facilities.forEach{f->Panel(f.name){
        Text(label(f.availability.name),fontWeight=FontWeight.Bold,color=if(f.availability==Availability.AVAILABLE)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(f.reason);Text("${f.source} · ${time(f.updatedAt)}",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={facility=f},enabled=!busy){Text("Update ${f.name}")}
    }}
    Panel("Failure simulation") {
        Text("Routes recalculate automatically from the last confirmed location. Switch to Navigate to inspect the result.")
        Button(onClick={vm.action("All lifts unavailable") {vm.repository.allLifts(false)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Simulate all lifts unavailable")}
        OutlinedButton(onClick={vm.action("Lifts restored") {vm.repository.allLifts(true)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Restore all lifts")}
        OutlinedButton(onClick={edgePicker=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Block a path")}
        s.blockedEdges.sorted().forEach{id->TextButton(onClick={vm.action("Path restored") {vm.repository.block(id,false)}},enabled=!busy){Text("Restore: ${edgeLabel(s,id)}")}}
        if(s.map.provenance=="Illustrative demo") TextButton(onClick={reset=true},enabled=!busy){Text("Reset demo dataset")}
    }
    Panel("Audit history") {
        if(s.audit.isEmpty()) Text("No reviewer actions yet.")
        s.audit.takeLast(30).reversed().forEach{e->Text("${e.action} · ${e.before} → ${e.after}",fontWeight=FontWeight.Medium);Text("${e.reason}\n${e.actor} · ${time(e.time)}",style=MaterialTheme.typography.bodySmall);HorizontalDivider()}
    }
    facility?.let{f->FacilityDialog(f,{facility=null}){status,reason->vm.action("Facility status updated") {vm.repository.facility(f.id,status,reason,s.revision)};facility=null}}
    if(edgePicker) EdgePicker(s,{edgePicker=false}){vm.action("Path blocked in both directions") {vm.repository.block(it,true)};edgePicker=false}
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Reset demo data?")},text={Text("This removes demo reports and audit history, restores facilities and resets your route preferences. Imported maps are not reset.")},
        confirmButton={TextButton(onClick={vm.action("Demo reset") {vm.repository.resetDemo()};reset=false}){Text("Reset demo")}},dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})
}
@Composable private fun FacilityDialog(f:Facility,onDismiss:()->Unit,onSave:(Availability,String)->Unit) {
    var status by remember{mutableStateOf(f.availability)};var reason by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(f.name)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        ChoiceMenu("Status",status.name,Availability.entries.map{it.name}){status=Availability.valueOf(it)}
        OutlinedTextField(reason,{if(it.length<=1000)reason=it},label={Text("Reason or observation")},minLines=2)
    }},confirmButton={Button(onClick={onSave(status,reason)},enabled=reason.trim().length>=5){Text("Update status")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun SettingsScreen(s:AppSnapshot,vm:AccessViewModel,busy:Boolean) {
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.importMap(it)}}
    Heading("About this build","Simple by design.","Offline navigation. Clear limitations. No account needed.")
    Panel("Map and data") {
        Text("${s.map.name}\nVersion ${s.map.version} • Schema ${s.map.schemaVersion}\n${s.map.nodes.size} nodes · ${s.map.edges.size} directed segments")
        Text("${s.map.provenance}\nVerified: ${time(s.map.verifiedAt)}",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy){Text("Import campus JSON map")}
        Text("Imports are validated before replacement. Reports remain attached to their segment IDs; incompatible replacements are rejected.",style=MaterialTheme.typography.bodySmall)
    }
    Panel("How to use AccessPath") {
        Text("1. Open Navigate and choose Plan a route.\n2. Select your mobility profile, start and destination.\n3. Read the route by floor and follow the landmark steps.\n4. Confirm locations as you reach them.\n5. Report barriers from Reports.\n6. In Manage, enable demo reviewer mode to verify reports and simulate failures.")
    }
    Panel("Privacy and limitations") {
        Text("Your map, reports, profile and audit history are stored on this device. This build uses no camera, microphone, GPS, account or remote analytics. It does not track your live location.")
        Text("The bundled campus is illustrative, not a surveyed GLBITM map. Routes depend on the supplied measurements and last-known conditions. Do not follow a route through a visible hazard.")
        Text("Automatic 2D mapping, live positioning and shared cloud reports are the next phase. This build does not control a wheelchair.")
        Text("To remove all local app data, use Android Settings → Apps → AccessPath → Storage → Clear storage. This deletes imported maps, reports and preferences.",style=MaterialTheme.typography.bodySmall)
    }
    Panel("Build information") {Text("AccessPath 1.0.0-demo\nNative Android · Kotlin · Compose · Room\nDijkstra routing with hard accessibility constraints");Text("Source: github.com/rudrasinha05/AccessPath",style=MaterialTheme.typography.bodySmall)}
}
