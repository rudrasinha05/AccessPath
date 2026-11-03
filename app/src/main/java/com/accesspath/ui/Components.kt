package com.accesspath.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.domain.*

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) SectionTitle(title)
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
}

@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Banner(text: String, warning: Boolean = false) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun StatusPill(text: String, emphasis: Boolean) {
    Surface(
        color = if (emphasis) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (emphasis) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
}

/** Searchable list of places, grouped by building and floor in the row text. */
@Composable
fun PlacePickerDialog(state: AppSnapshot, title: String, onDismiss: () -> Unit, onPick: (Node) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val floors = state.map.floors.associateBy { it.id }
    val places = remember(state.map, query) {
        state.map.nodes
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ floors[it.floorId]?.buildingId }, { floors[it.floorId]?.ordinal }, { it.name }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_places)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (places.isEmpty()) InfoText(stringResource(R.string.no_places))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(places, key = { it.id }) { node ->
                        ListItem(
                            headlineContent = { Text(node.name) },
                            supportingContent = { Text(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · ")) },
                            modifier = Modifier.clickable(role = Role.Button) { onPick(node) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** List of physical segments (one per pair of places). Segments on [routeEdges] are listed first. */
@Composable
fun SegmentPickerDialog(state: AppSnapshot, routeEdges: List<Edge>, filter: (Edge) -> Boolean = { true }, onDismiss: () -> Unit, onPick: (Edge) -> Unit) {
    val context = LocalContext.current
    val onRoute = remember(routeEdges) { routeEdges.map { listOf(it.from, it.to).sorted() }.toSet() }
    val segments = remember(state.map) {
        state.map.edges.filter(filter).distinctBy { listOf(it.from, it.to).sorted() }
            .sortedBy { state.segmentLabel(context, it.id) }
    }
    val (first, rest) = segments.partition { listOf(it.from, it.to).sorted() in onRoute }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.segment_choose)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                if (first.isNotEmpty()) {
                    item { Text(stringResource(R.string.segment_on_route), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(first, key = { "r" + it.id }) { SegmentRow(state, it, onPick) }
                    item { Text(stringResource(R.string.segment_all), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                items(rest, key = { it.id }) { SegmentRow(state, it, onPick) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SegmentRow(state: AppSnapshot, edge: Edge, onPick: (Edge) -> Unit) {
    val context = LocalContext.current
    val floor = state.node(edge.from)?.let { state.floor(it.floorId)?.label }
    ListItem(
        headlineContent = { Text(state.segmentLabel(context, edge.id)) },
        supportingContent = floor?.let { { Text(it) } },
        modifier = Modifier.clickable(role = Role.Button) { onPick(edge) }
    )
}

/** Asks for a 5–1000 character reason before a reviewer decision is saved. */
@Composable
fun ReasonDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    val valid = reason.trim().length in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                extra()
                OutlinedTextField(
                    reason, { if (it.length <= ReportRules.MAX_TEXT) reason = it },
                    label = { Text(stringResource(R.string.reason_label)) },
                    supportingText = { if (reason.isNotEmpty() && !valid) Text(stringResource(R.string.text_length_error)) },
                    isError = reason.isNotEmpty() && !valid,
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(reason.trim()) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ProfileDialog(profile: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var mobility by rememberSaveable { mutableStateOf(profile.mobility) }
    var width by rememberSaveable { mutableStateOf(formatPlain(profile.minWidth)) }
    var slope by rememberSaveable { mutableStateOf(formatPlain(profile.maxSlope)) }
    var limit by rememberSaveable { mutableStateOf(profile.maxWalkingDistance?.let(::formatPlain).orEmpty()) }
    val widthValue = width.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..5.0 }
    val slopeValue = slope.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    val limitValue = limit.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..20000.0 }
    val limitOk = limit.isBlank() || limitValue != null
    val wheelchair = mobility == Mobility.WHEELCHAIR
    val valid = (!wheelchair || (widthValue != null && slopeValue != null)) && limitOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoText(stringResource(R.string.profile_intro))
                Column(Modifier.selectableGroup()) {
                    Mobility.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(option == mobility, role = Role.RadioButton) { mobility = option }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(option == mobility, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(stringResource(option.label()), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(option.description()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (wheelchair) {
                    NumberField(width, { width = it }, stringResource(R.string.profile_min_width), widthValue == null, stringResource(R.string.value_range_error, "0.1", "5"))
                    NumberField(slope, { slope = it }, stringResource(R.string.profile_max_slope), slopeValue == null, stringResource(R.string.value_range_error, "0", "100"))
                }
                NumberField(limit, { limit = it }, stringResource(R.string.profile_max_distance), !limitOk, stringResource(R.string.value_range_error, "1", "20000"))
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(profile.copy(
                    mobility = mobility,
                    minWidth = widthValue ?: profile.minWidth,
                    maxSlope = slopeValue ?: profile.maxSlope,
                    maxWalkingDistance = limitValue
                ))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, error: Boolean, errorText: String) {
    OutlinedTextField(
        value, { if (it.length <= 8) onChange(it) }, label = { Text(label) }, singleLine = true,
        isError = error, supportingText = { if (error) Text(errorText) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatPlain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Lets a row of chips scroll sideways instead of being cut off on narrow screens or with large text. */
@Composable
fun Modifier.horizontalScrollable(): Modifier = this.horizontalScroll(rememberScrollState())
