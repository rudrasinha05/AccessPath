package com.accesspath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accesspath.R
import com.accesspath.data.PendingSurvey
import com.accesspath.data.ReviewStatus
import com.accesspath.domain.Survey
import com.accesspath.domain.isPlace

/** A small drawing of a mapped area: its path and the places on it, for an admin to check. */
@Composable
fun SurveyPreview(survey: Survey, modifier: Modifier = Modifier) {
    val nodes = survey.nodes
    val byId = nodes.associateBy { it.id }
    Box(modifier.fillMaxWidth().height(160.dp).background(Color(0xFFEFF3F6), RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            if (nodes.isEmpty()) return@Canvas
            val minX = nodes.minOf { it.x }; val maxX = nodes.maxOf { it.x }
            val minY = nodes.minOf { it.y }; val maxY = nodes.maxOf { it.y }
            val span = maxOf(maxX - minX, maxY - minY, 5.0)
            val scale = (minOf(size.width, size.height) / span).toFloat()
            val cx = ((minX + maxX) / 2).toFloat(); val cy = ((minY + maxY) / 2).toFloat()
            fun p(x: Double, y: Double) = Offset(size.width / 2 + (x.toFloat() - cx) * scale, size.height / 2 + (y.toFloat() - cy) * scale)
            survey.edges.forEach { e ->
                val a = byId[e.from] ?: return@forEach; val b = byId[e.to] ?: return@forEach
                drawLine(Color(0xFF1A73E8), p(a.x, a.y), p(b.x, b.y), strokeWidth = 8f, cap = StrokeCap.Round)
            }
            nodes.filter { it.isPlace }.forEach { n ->
                drawCircle(Color.White, 12f, p(n.x, n.y)); drawCircle(placeTint(n.type), 9f, p(n.x, n.y))
            }
        }
    }
}

/** Admin: areas waiting for review, each with a preview and Approve / Reject. */
@Composable
fun ReviewQueueCard(queue: List<PendingSurvey>, canEdit: Boolean, onApprove: (String) -> Unit, onReject: (String) -> Unit) {
    SectionCard(stringResource(R.string.review_title)) {
        Column(Modifier.testTag("reviewQueue"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoText(stringResource(R.string.review_intro))
            if (queue.isEmpty()) InfoText(stringResource(R.string.review_none))
            queue.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    InfoText(listOf(stringResource(R.string.draft_places, p.placeCount), formatTime(p.createdAt)).joinToString(" · "))
                    SurveyPreview(p.survey)
                    val places = p.survey.nodes.filter { it.isPlace }.joinToString(", ") { it.name }
                    if (places.isNotBlank()) Text(places, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = canEdit, onClick = { onApprove(p.id) }, modifier = Modifier.testTag("approve_${p.id}")) {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.approve))
                        }
                        OutlinedButton(enabled = canEdit, onClick = { onReject(p.id) }) { Text(stringResource(R.string.reject)) }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Contribute tab: areas this phone mapped and what the admin decided. */
@Composable
fun MyMappedAreas(list: List<PendingSurvey>) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("myAreas")) {
        SectionTitle(stringResource(R.string.my_areas))
        list.forEach { p ->
            val (label, color, icon) = when (p.status) {
                ReviewStatus.PENDING -> Triple(stringResource(if (p.uploaded) R.string.status_waiting_review else R.string.status_not_sent), Color(0xFFE37400), Icons.Filled.HourglassTop)
                ReviewStatus.APPROVED -> Triple(stringResource(R.string.status_on_map), Color(0xFF188038), Icons.Filled.CheckCircle)
                ReviewStatus.REJECTED -> Triple(stringResource(R.string.status_rejected), Color(0xFFD93025), Icons.Filled.Cancel)
            }
            ListItem(
                headlineContent = { Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(listOfNotNull(label, stringResource(R.string.draft_places, p.placeCount), p.reason.takeIf { it.isNotBlank() }).joinToString(" · "), color = color)
                },
                leadingContent = { Icon(icon, null, tint = color) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}
