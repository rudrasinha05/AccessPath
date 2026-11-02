package com.accesspath.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accesspath.domain.AppSnapshot
import com.accesspath.domain.AssistanceRequest
import com.accesspath.domain.Node

@Composable
fun AssistanceRequestDialog(
    s: AppSnapshot,
    lang: String,
    onDismiss: () -> Unit,
    onSubmit: (urgency: String, note: String) -> Unit
) {
    var urgency by rememberSaveable { mutableStateOf("STANDARD") }
    var note by rememberSaveable { mutableStateOf("") }
    val currentNodeName = s.map.nodes.find { it.id == s.current }?.name ?: s.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "hi") "🆘 सहायता व केयरगिवर अनुरोध" else "🆘 Request Assistance & Caregiver") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (lang == "hi") "स्थान: $currentNodeName\nकैंपस हेल्पडेस्क और उपलब्ध केयरगिवर को तुरंत अलर्ट भेजा जाएगा।"
                    else "Location: $currentNodeName\nCampus helpdesk and designated caregivers will be notified immediately."
                )

                Text(if (lang == "hi") "आपातकाल स्तर (Urgency):" else "Urgency Level:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("STANDARD", "HIGH", "CRITICAL").forEach { level ->
                        FilterChip(
                            selected = urgency == level,
                            onClick = { urgency = level },
                            label = { Text(level) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (lang == "hi") "विवरण (वैकल्पिक)" else "Details / Physical needs (optional)") },
                    placeholder = { Text("e.g. Wheelchair battery low, door heavy, need escort") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(urgency, note) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text(if (lang == "hi") "अनुरोध भेजें (Dispatch SOS)" else "Dispatch Help Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditLandmarkDialog(
    s: AppSnapshot,
    onDismiss: () -> Unit,
    onSave: (nodeId: String, newName: String, newX: Double, newY: Double) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf(s.map.nodes.firstOrNull()?.id.orEmpty()) }
    val node = s.map.nodes.find { it.id == selectedId }
    var name by remember(selectedId) { mutableStateOf(node?.name.orEmpty()) }
    var xStr by remember(selectedId) { mutableStateOf(node?.x?.toString().orEmpty()) }
    var yStr by remember(selectedId) { mutableStateOf(node?.y?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Existing Landmark") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select landmark to update:")
                s.map.nodes.take(15).forEach { n ->
                    FilterChip(
                        selected = selectedId == n.id,
                        onClick = { selectedId = n.id },
                        label = { Text("${n.name} (${n.id})") }
                    )
                }

                OutlinedTextField(name, { name = it }, label = { Text("Landmark Name") }, singleLine = true)
                OutlinedTextField(xStr, { xStr = it }, label = { Text("X coordinate") }, singleLine = true)
                OutlinedTextField(yStr, { yStr = it }, label = { Text("Y coordinate") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = xStr.toDoubleOrNull() ?: 50.0
                    val y = yStr.toDoubleOrNull() ?: 50.0
                    onSave(selectedId, name, x, y)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteLandmarkDialog(
    s: AppSnapshot,
    onDismiss: () -> Unit,
    onDelete: (nodeId: String) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf("") }
    val eligibleNodes = s.map.nodes.filter { it.id != s.current && it.id != s.destination }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Landmark / Room") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select an obsolete landmark to delete (incident corridors will also be pruned):")
                eligibleNodes.forEach { n ->
                    FilterChip(
                        selected = selectedId == n.id,
                        onClick = { selectedId = n.id },
                        label = { Text("${n.name} (${n.id})") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(selectedId) },
                enabled = selectedId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Delete Landmark")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AssistanceRequestsPanel(
    requests: List<AssistanceRequest>,
    busy: Boolean,
    onAcknowledge: (id: String) -> Unit
) {
    if (requests.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Active Assistance Requests (${requests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            requests.reversed().forEach { req ->
                Surface(
                    color = when (req.urgency) {
                        "CRITICAL" -> Color(0xFFFEE2E2)
                        "HIGH" -> Color(0xFFFEF3C7)
                        else -> Color(0xFFE0F2FE)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📍 ${req.nodeName}", fontWeight = FontWeight.Bold)
                            Text("Urgency: ${req.urgency}", fontWeight = FontWeight.Bold, color = if (req.urgency == "CRITICAL") Color(0xFF991B1B) else Color(0xFF92400E))
                        }
                        if (req.note.isNotBlank()) Text("Note: ${req.note}", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Status: ${req.status}", style = MaterialTheme.typography.labelSmall)
                            if (req.status == "OPEN") {
                                OutlinedButton(onClick = { onAcknowledge(req.id) }, enabled = !busy) {
                                    Text("Acknowledge")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
