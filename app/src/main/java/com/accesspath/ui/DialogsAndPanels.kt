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
import androidx.compose.ui.unit.sp
import com.accesspath.domain.*

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

@Composable
fun LandmarkDetailsCard(
    node: Node,
    currentPos: Node?,
    lang: String,
    onNavigate: (Node) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text("📍", modifier = Modifier.padding(6.dp))
                    }
                    Column {
                        Text(node.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Floor ${node.floorId} • Type: ${node.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) {
                    Text("✕", fontWeight = FontWeight.Bold)
                }
            }

            // Accessible attributes checklist
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                ) {
                    Text("✓ Step-free access", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF047857), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.12f)
                ) {
                    Text("✓ Door ≥ 1.2m", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF1D4ED8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onNavigate(node) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "hi") "🚀 यहां का रास्ता बनाएं" else "🚀 Navigate Here", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun GeofenceManagerDialog(
    s: AppSnapshot,
    onDismiss: () -> Unit,
    onAddGeofence: (GeofenceZone) -> Unit,
    onDeleteGeofence: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var radiusStr by rememberSaveable { mutableStateOf("30.0") }
    var type by rememberSaveable { mutableStateOf(GeofenceType.SAFE_ZONE) }
    var alertMsg by rememberSaveable { mutableStateOf("") }
    var isCreating by rememberSaveable { mutableStateOf(false) }

    val currentNode = s.map.nodes.find { it.id == s.current }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛡️ Campus Geofencing Manager", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Define virtual perimeters on campus to trigger automated safety alerts and load floor plans.")

                // Current Active Geofences List
                if (s.geofences.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No active geofences on this campus yet. Create one below.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("Active Campus Geofences (${s.geofences.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    s.geofences.forEach { gf ->
                        val badgeColor = when (gf.type) {
                            GeofenceType.SAFE_ZONE -> Color(0xFF10B981)
                            GeofenceType.HAZARD -> Color(0xFFEF4444)
                            GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF3B82F6)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = badgeColor.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(gf.name, fontWeight = FontWeight.Bold)
                                    Text("Radius: ${gf.radius} m • Floor: ${gf.floorId} • Type: ${gf.type.name}", style = MaterialTheme.typography.labelSmall)
                                    if (gf.alertMessage.isNotBlank()) Text(gf.alertMessage, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onDeleteGeofence(gf.id) }) {
                                    Text("Delete", color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (!isCreating) {
                    Button(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ Create Geofence Around Current Location")
                    }
                } else {
                    Text("New Geofence Details:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Zone Name (e.g. Academic Block A Perimeter)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        radiusStr,
                        { radiusStr = it },
                        label = { Text("Radius in metres (e.g. 25)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GeofenceType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        alertMsg,
                        { alertMsg = it },
                        label = { Text("Alert Message on Entry (optional)") },
                        placeholder = { Text("e.g. You have entered Academic Block A. Ramps on your left.") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val r = radiusStr.toDoubleOrNull() ?: 25.0
                                val curX = currentNode?.x ?: 50.0
                                val curY = currentNode?.y ?: 50.0
                                val zone = GeofenceZone(
                                    id = "gf_" + System.currentTimeMillis(),
                                    name = name.ifBlank { "Campus Zone" },
                                    floorId = s.selectedFloor,
                                    centerX = curX,
                                    centerY = curY,
                                    radius = r,
                                    type = type,
                                    alertMessage = alertMsg.ifBlank { "Entered ${name.ifBlank { "safe zone" }}" }
                                )
                                onAddGeofence(zone)
                                isCreating = false
                                name = ""
                            },
                            enabled = name.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Zone")
                        }
                        OutlinedButton(onClick = { isCreating = false }, shape = RoundedCornerShape(10.dp)) {
                            Text("Cancel")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun WorldwideCampusExplorerDialog(
    s: AppSnapshot,
    onDismiss: () -> Unit,
    onSelectCampus: (String) -> Unit,
    onPublishCurrent: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val catalog = com.accesspath.data.CampusRepository.globalCampusCatalog
    val filtered = catalog.filter {
        it.name.contains(query, ignoreCase = true) ||
        it.city.contains(query, ignoreCase = true) ||
        it.country.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🌍 Worldwide Campus Directory", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Browse, download, and switch between accessible indoor topologies from universities and hubs globally.")

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search campuses worldwide (e.g. Delhi, Stanford, AIIMS)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                filtered.forEach { item ->
                    val isActive = s.activeCampusId == item.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("ACTIVE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text("📍 ${item.city}, ${item.country} • ${item.buildingCount} Buildings Mapped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.description, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Contributor: ${item.contributor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!isActive) {
                                    Button(
                                        onClick = { onSelectCampus(item.id) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Load Map")
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Crowdsourced Community Publishing Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F766E).copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🤝 Community Cloud Mapping", fontWeight = FontWeight.Bold)
                        Text("Have you surveyed or edited this campus? Publish it to the Global Directory so all students and visitors can navigate with it.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = onPublishCurrent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌐 Publish My Map to Worldwide Directory")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
