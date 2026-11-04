package com.accesspath.ui

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.domain.*
import java.util.UUID

@Composable
fun ReportsScreen(ui: ScreenState, vm: AccessViewModel, onMapArea: () -> Unit = {}) {
    val state = ui.snapshot ?: return
    val context = LocalContext.current
    val app = context.applicationContext as com.accesspath.AccessPathApplication
    val scope = rememberCoroutineScope()
    var outdoorAt by remember { mutableStateOf<com.accesspath.domain.LatLng?>(null) }
    var outdoorTick by remember { mutableIntStateOf(0) }
    fun locateForReport() = scope.launch {
        val fix = com.accesspath.ui.mapping.DeviceSensors.currentLocation(context, 10_000)
        if (fix == null) vm.message(UiMessage.Text(R.string.location_off)) else outdoorAt = com.accesspath.domain.LatLng(fix.latitude, fix.longitude)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (com.accesspath.ui.mapping.DeviceSensors.hasLocationPermission(context)) locateForReport()
    }
    var reporting by rememberSaveable { mutableStateOf(false) }
    var decision by rememberSaveable { mutableStateOf<Pair<String, ReportStatus>?>(null) }
    val (active, closed) = remember(state.reports) { state.reports.sortedByDescending { it.createdAt }.partition { it.isActive } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("reports")
    ) {
        item { ScreenTitle(stringResource(R.string.tab_contribute)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ContributionsCard(onReportOutdoor = {
                    if (com.accesspath.ui.mapping.DeviceSensors.hasLocationPermission(context)) locateForReport()
                    else locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }, onMapArea = onMapArea)
                key(outdoorTick) { MyOutdoorReports(outdoorTick) { outdoorTick++ } }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Accessible, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.contribute_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.reports_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            Button(onClick = { reporting = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.report_button))
            }
        }
        if (!ui.reviewer && active.isNotEmpty()) item { InfoText(stringResource(R.string.reviewer_hint)) }
        item { SectionTitle(stringResource(R.string.reports_title)) }
        if (state.reports.isEmpty()) item { InfoText(stringResource(R.string.reports_empty)) }
        if (active.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reports_active, active.size)) }
            items(active, key = { it.id }) { r -> ReportCard(state, r, ui.reviewer && !ui.busy) { status -> decision = r.id to status } }
        }
        if (closed.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reports_closed, closed.size)) }
            items(closed, key = { it.id }) { r -> ReportCard(state, r, false) {} }
        }
    }

    outdoorAt?.let { at ->
        ReportBarrierDialog(signedIn = app.supabase.session != null, onDismiss = { outdoorAt = null }) { kind, note ->
            outdoorAt = null
            scope.launch {
                val shared = app.barriers.report(at, kind, note)
                vm.message(UiMessage.Text(if (shared) R.string.barrier_reported else R.string.barrier_reported_local))
                outdoorTick++
            }
        }
    }
    if (reporting) {
        ReportDialog(state, (ui.route as? RouteResult.Found)?.edges.orEmpty(), onDismiss = { reporting = false }) { id, edge, category, text ->
            reporting = false
            vm.submitReport(id, edge, category, text)
        }
    }
    decision?.let { (id, status) ->
        val title = stringResource(when (status) {
            ReportStatus.VERIFIED -> R.string.decision_verify
            ReportStatus.REJECTED -> R.string.decision_reject
            else -> R.string.decision_resolve
        })
        val report = state.reports.firstOrNull { it.id == id }
        ReasonDialog(title, title, onDismiss = { decision = null }, extra = {
            report?.let { InfoText(state.segmentLabel(context, it.edgeId) + "\n" + it.description) }
        }) { reason ->
            decision = null
            vm.review(id, status, reason, state.revision)
        }
    }
}

@Composable
private fun ReportCard(state: AppSnapshot, report: BarrierReport, canReview: Boolean, onDecide: (ReportStatus) -> Unit) {
    val context = LocalContext.current
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(stringResource(report.status.label()), emphasis = report.isActive)
            StatusPill(stringResource(report.category.label()), emphasis = false)
        }
        Text(state.segmentLabel(context, report.edgeId), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(report.description, style = MaterialTheme.typography.bodyMedium)
        InfoText(stringResource(R.string.reported_at, formatTime(report.createdAt)))
        if (report.reason.isNotBlank()) InfoText(stringResource(R.string.reviewer_note, report.reason))
        if (canReview) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollable()) {
                when (report.status) {
                    ReportStatus.PENDING -> {
                        Button(onClick = { onDecide(ReportStatus.VERIFIED) }) { Text(stringResource(R.string.action_verify)) }
                        OutlinedButton(onClick = { onDecide(ReportStatus.REJECTED) }) { Text(stringResource(R.string.action_reject)) }
                    }
                    ReportStatus.VERIFIED -> Button(onClick = { onDecide(ReportStatus.RESOLVED) }) { Text(stringResource(R.string.action_resolve)) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun ReportDialog(
    state: AppSnapshot, routeEdges: List<Edge>, onDismiss: () -> Unit, preselect: String? = null,
    onSubmit: (id: String, edgeId: String, category: BarrierCategory, description: String) -> Unit
) {
    val context = LocalContext.current
    // Generated once per dialog so a double tap on Submit cannot create two reports.
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var category by rememberSaveable { mutableStateOf(BarrierCategory.BLOCKED_PATH) }
    var edgeId by rememberSaveable { mutableStateOf(preselect ?: routeEdges.firstOrNull()?.id) }
    var text by rememberSaveable { mutableStateOf("") }
    var pickSegment by rememberSaveable { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }
    val textOk = text.trim().length in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.category_label), style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { categoryOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(category.label())) }
                    DropdownMenu(categoryOpen, onDismissRequest = { categoryOpen = false }) {
                        BarrierCategory.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(stringResource(c.label())) }, onClick = { category = c; categoryOpen = false })
                        }
                    }
                }
                Text(stringResource(R.string.segment_label), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickSegment = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(edgeId?.let { state.segmentLabel(context, it) } ?: stringResource(R.string.segment_choose))
                }
                OutlinedTextField(
                    text, { if (it.length <= ReportRules.MAX_TEXT) text = it },
                    label = { Text(stringResource(R.string.description_label)) },
                    placeholder = { Text(stringResource(R.string.description_hint)) },
                    isError = text.isNotEmpty() && !textOk,
                    supportingText = { if (text.isNotEmpty() && !textOk) Text(stringResource(R.string.text_length_error)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(enabled = textOk && edgeId != null, onClick = { edgeId?.let { onSubmit(id, it, category, text.trim()) } }) {
                Text(stringResource(R.string.submit))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    if (pickSegment) SegmentPickerDialog(state, routeEdges, onDismiss = { pickSegment = false }) { edgeId = it.id; pickSegment = false }
}
