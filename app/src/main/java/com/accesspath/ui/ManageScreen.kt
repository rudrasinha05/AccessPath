package com.accesspath.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.domain.*

@Composable
fun ManageScreen(ui: ScreenState, vm: AccessViewModel, onBack: () -> Unit) {
    val state = ui.snapshot ?: return
    val context = LocalContext.current
    val canEdit = ui.reviewer && !ui.busy
    var facilityId by rememberSaveable { mutableStateOf<String?>(null) }
    var closing by rememberSaveable { mutableStateOf(false) }
    var closeEdge by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenEdge by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // One entry per physical segment (both directions are closed together).
    val closedSegments = remember(state.blockedEdges, state.map) {
        state.map.edges.filter { it.id in state.blockedEdges }.distinctBy { listOf(it.from, it.to).sorted() }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("manage")
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                ScreenTitle(stringResource(R.string.manage_title))
            }
        }
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().toggleable(ui.reviewer, role = Role.Switch, onValueChange = vm::setReviewer),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reviewer_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(ui.reviewer, onCheckedChange = null)
                }
                InfoText(stringResource(R.string.reviewer_disclosure))
            }
        }
        if (!ui.reviewer) item { Banner(stringResource(R.string.reviewer_required)) }

        item {
            SectionCard(stringResource(R.string.facilities_title)) {
                state.map.facilities.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold)
                            StatusPill(stringResource(f.availability.label()), emphasis = f.availability != Availability.AVAILABLE)
                            val updated = if (f.updatedAt > 0) stringResource(R.string.facility_updated, formatTime(f.updatedAt)) else stringResource(R.string.facility_never_updated)
                            InfoText(updated + if (f.reason.isNotBlank()) " · ${f.reason}" else "")
                        }
                        TextButton(enabled = canEdit, onClick = { facilityId = f.id }) { Text(stringResource(R.string.update)) }
                    }
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.closures_title)) {
                InfoText(stringResource(R.string.closures_intro))
                if (closedSegments.isEmpty()) InfoText(stringResource(R.string.closures_none))
                closedSegments.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.segmentLabel(context, e.id), Modifier.weight(1f))
                        TextButton(enabled = canEdit, onClick = { reopenEdge = e.id }) { Text(stringResource(R.string.reopen)) }
                    }
                }
                OutlinedButton(enabled = canEdit, onClick = { closing = true }) { Text(stringResource(R.string.close_path)) }
            }
        }

        if (state.map.isDemo) item {
            SectionCard(stringResource(R.string.demo_title)) {
                InfoText(stringResource(R.string.demo_intro))
                OutlinedButton(enabled = canEdit, onClick = { vm.simulateAllLifts(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lifts_down)) }
                OutlinedButton(enabled = canEdit, onClick = { vm.simulateAllLifts(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lifts_up)) }
                Button(
                    enabled = canEdit, onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text(stringResource(R.string.reset_demo)) }
            }
        }

        item { SectionTitle(stringResource(R.string.audit_title)) }
        val events = state.audit.asReversed().take(50)
        if (events.isEmpty()) item { InfoText(stringResource(R.string.audit_empty)) }
        items(events, key = { it.id }) { e ->
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.audit_entry, e.action, e.before.ifBlank { "—" }, e.after), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                InfoText(listOf(formatTime(e.time), e.target, e.reason).filter { it.isNotBlank() }.joinToString(" · "))
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }

    facilityId?.let { id -> state.map.facilities.firstOrNull { it.id == id } }?.let { f ->
        var status by rememberSaveable(f.id) { mutableStateOf(f.availability) }
        ReasonDialog(
            title = stringResource(R.string.facility_dialog_title, f.name), confirmLabel = stringResource(R.string.save),
            onDismiss = { facilityId = null },
            extra = {
                Column(Modifier.selectableGroup()) {
                    Availability.entries.forEach { a ->
                        Row(Modifier.fillMaxWidth().selectable(a == status, role = Role.RadioButton) { status = a }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(a == status, onClick = null)
                            Text(stringResource(a.label()), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        ) { reason -> facilityId = null; vm.setFacility(f.id, status, reason, state.revision) }
    }
    if (closing) {
        SegmentPickerDialog(state, (ui.route as? RouteResult.Found)?.edges.orEmpty(), filter = { it.id !in state.blockedEdges },
            onDismiss = { closing = false }) { closing = false; closeEdge = it.id }
    }
    closeEdge?.let { id ->
        ReasonDialog(stringResource(R.string.close_path), stringResource(R.string.close_path), onDismiss = { closeEdge = null },
            extra = { InfoText(state.segmentLabel(context, id)) }) { reason -> closeEdge = null; vm.setPathClosed(id, true, reason) }
    }
    reopenEdge?.let { id ->
        ReasonDialog(stringResource(R.string.reopen_title), stringResource(R.string.reopen), onDismiss = { reopenEdge = null },
            extra = { InfoText(state.segmentLabel(context, id)) }) { reason -> reopenEdge = null; vm.setPathClosed(id, false, reason) }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_body)) },
            confirmButton = { Button(onClick = { confirmReset = false; vm.resetDemo() }) { Text(stringResource(R.string.reset)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
