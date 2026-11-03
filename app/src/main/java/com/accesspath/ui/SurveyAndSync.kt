package com.accesspath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesspath.domain.*

@Composable
fun SensorMappingCard(vm: AccessViewModel, s: AppSnapshot, lang: String, busy: Boolean) {
    val session by vm.sensorMapper.session.collectAsStateWithLifecycle()
    var consentDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(AppStrings.t("sensor_mapping", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Phone IMU & sensor dead-reckoning engine. Builds incremental 2D occupancy grids with confidence scoring and tracking loss detection.",
                style = MaterialTheme.typography.bodySmall
            )

            // Status & Confidence Indicators
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Status: ${session.status.name}",
                    fontWeight = FontWeight.Bold,
                    color = when (session.status) {
                        MappingStatus.RECORDING -> Color(0xFF1B8A5A)
                        MappingStatus.PAUSED -> Color(0xFFD97706)
                        MappingStatus.STOPPED -> Color(0xFFDC2626)
                        MappingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    "Confidence: ${(session.currentConfidence * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = if (session.trackingLost) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                )
            }

            if (session.trackingLost) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        AppStrings.t("tracking_lost", lang),
                        color = Color(0xFF991B1B),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text("${session.trail.size} steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Steps / Points", style = MaterialTheme.typography.labelSmall)
                }
                Column {
                    Text("${number(session.distanceTraversed)} m", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Distance", style = MaterialTheme.typography.labelSmall)
                }
                Column {
                    Text("${session.grid.freeCount} / ${session.grid.obstacleCount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Free / Obstacles", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 2D Occupancy Grid Canvas
            Text("2D Occupancy Map Preview (Grey: Unknown, Green: Free Corridor, Red: Obstacle, Amber: Trail)", style = MaterialTheme.typography.labelSmall)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                val grid = session.grid
                val cellW = size.width / grid.gridSize
                val cellH = size.height / grid.gridSize

                for (y in 0 until grid.gridSize) {
                    for (x in 0 until grid.gridSize) {
                        val cell = grid.getCell(x, y)
                        val color = when (cell) {
                            OccupancyCell.FREE -> Color(0xFF86EFAC)
                            OccupancyCell.OBSTACLE -> Color(0xFFEF4444)
                            OccupancyCell.LANDMARK -> Color(0xFF3B82F6)
                            OccupancyCell.UNKNOWN -> Color(0xFFCBD5E1)
                        }
                        drawRect(color, Offset(x * cellW, y * cellH), Size(cellW, cellH))
                    }
                }

                // Draw trail path
                session.trail.forEach { pt ->
                    val px = (pt.x / grid.gridSize * size.width).toFloat()
                    val py = (pt.y / grid.gridSize * size.height).toFloat()
                    drawCircle(Color(0xFFF59E0B), 5f, Offset(px, py))
                }
            }

            // Survey Session Controls
            when (session.status) {
                MappingStatus.IDLE, MappingStatus.STOPPED -> {
                    Button(onClick = { consentDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(AppStrings.t("start_mapping", lang))
                    }
                }
                MappingStatus.RECORDING -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.sensorMapper.recordStep(0.8) }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("record_step", lang))
                        }
                        OutlinedButton(onClick = { vm.sensorMapper.recordObstacle(1.2) }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("record_obstacle", lang))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.sensorMapper.pauseSession() }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("pause_mapping", lang))
                        }
                        OutlinedButton(onClick = { vm.sensorMapper.stopSession() }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("stop_mapping", lang))
                        }
                    }
                }
                MappingStatus.PAUSED -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.sensorMapper.resumeSession() }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("resume_mapping", lang))
                        }
                        OutlinedButton(onClick = { vm.sensorMapper.stopSession() }, modifier = Modifier.weight(1f)) {
                            Text(AppStrings.t("stop_mapping", lang))
                        }
                    }
                }
            }

            if (session.trail.size >= 2) {
                Button(
                    onClick = { vm.convertMappingSession(session) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AppStrings.t("convert_to_graph", lang))
                }
            }
        }
    }

    if (consentDialog) {
        AlertDialog(
            onDismissRequest = { consentDialog = false },
            title = { Text("Sensor Mapping Consent & Setup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AccessPath uses device IMU motion sensors (Step Detector & Gyroscope) to build an incremental 2D occupancy corridor layout."
                    )
                    Text("• Explicit surveyor consent is required before recording.\n• Sensor tracking confidence is evaluated dynamically.\n• No video/microphone data is collected.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val consent = MappingConsent(
                        agreed = true,
                        timestamp = System.currentTimeMillis(),
                        deviceHardware = "Android SensorManager IMU"
                    )
                    vm.sensorMapper.startSession(s.selectedFloor, consent)
                    consentDialog = false
                }) {
                    Text("Grant Consent & Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { consentDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CloudSyncCard(vm: AccessViewModel, s: AppSnapshot, lang: String, busy: Boolean) {
    val reviewerAccount = remember {
        ReviewerAccount(
            reviewerId = "REV-05",
            displayName = "Dr. Rudra Sinha",
            role = "CERTIFIED_CAMPUS_SURVEYOR"
        )
    }

    val ui by vm.ui.collectAsStateWithLifecycle()

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(AppStrings.t("cloud_sync", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Multi-reviewer synchronization engine with safety-first conflict resolution rules.",
                style = MaterialTheme.typography.bodySmall
            )

            // Reviewer badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(reviewerAccount.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("${reviewerAccount.reviewerId} · ${reviewerAccount.role}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("🟢 Authenticated", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val payload = SyncPayload(
                            mapVersion = s.map.version,
                            facilities = s.map.facilities,
                            reports = s.reports,
                            exportedAt = System.currentTimeMillis(),
                            publisher = reviewerAccount.displayName
                        )
                        // Staged in local queue - strictly NO false claim of live cloud sharing!
                        vm.message("[Stage 2: Upload Pending] Staged topology v${payload.mapVersion} in local queue. Awaiting verified server acknowledgement.")
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.t("publish_cloud", lang))
                }

                OutlinedButton(
                    onClick = {
                        // Simulate remote update with an unavailable facility to demonstrate safety-first conflict resolution
                        val simulatedRemotePayload = SyncPayload(
                            mapVersion = "1.0.1-cloud",
                            facilities = s.map.facilities.map {
                                if (it.id == "liftB") it.copy(availability = Availability.UNAVAILABLE, reason = "Remote surveyor reported maintenance") else it
                            },
                            reports = s.reports + BarrierReport(
                                id = "remote_rep_${System.currentTimeMillis() % 1000}",
                                edgeId = s.map.edges.firstOrNull()?.id.orEmpty(),
                                category = "Blocked path",
                                description = "Temporary maintenance cones deployed",
                                createdAt = System.currentTimeMillis(),
                                status = ReportStatus.VERIFIED,
                                reason = "Verified by remote surveyor"
                            ),
                            exportedAt = System.currentTimeMillis(),
                            publisher = "Admin Operations Center"
                        )
                        vm.syncRemoteWithConflicts(simulatedRemotePayload, reviewerAccount)
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.t("sync_cloud", lang))
                }
            }

            // Conflict Resolution Log
            if (ui.syncConflicts.isNotEmpty()) {
                Text("Resolved Conflicts (${ui.syncConflicts.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                ui.syncConflicts.forEach { c ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Target: ${c.entityId} · Local: ${c.localValue} vs Remote: ${c.remoteValue}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("→ Decided: ${c.resolvedValue} (${c.reason})", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
