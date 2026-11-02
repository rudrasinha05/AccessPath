package com.accesspath.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accesspath.domain.*

@Composable
fun CampusCanvas(
    s: AppSnapshot,
    route: RouteResult.Found?,
    guidedStepIndex: Int? = null,
    visitedNodes: Set<String> = emptySet()
) {
    var zoom by remember(s.selectedFloor) { mutableFloatStateOf(1.15f) }
    var pan by remember(s.selectedFloor) { mutableStateOf(Offset.Zero) }

    val floorNodes = remember(s.map, s.selectedFloor) { s.map.nodes.filter { it.floorId == s.selectedFloor } }
    val nodes = s.map.nodes.associateBy { it.id }
    val excluded = remember(s) { Routing.exclusions(s) }
    val active = route?.edges?.map { it.id }?.toSet().orEmpty()
    val currentStepEdge = if (guidedStepIndex != null && route != null && guidedStepIndex in route.edges.indices) {
        route.edges[guidedStepIndex].id
    } else null

    // Infinite pulse animation for current location beacon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val labelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(15, 23, 42) // Slate-900
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }

    val currentFloor = s.map.floors.find { it.id == s.selectedFloor }
    val floorLabel = currentFloor?.label ?: s.selectedFloor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0F172A)) // Deep architectural slate canvas
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Interactive campus map floor $floorLabel with ${floorNodes.size} landmarks."
                }
                .pointerInput(s.selectedFloor) {
                    detectTransformGestures { _, delta, scale, _ ->
                        zoom = (zoom * scale).coerceIn(0.8f, 5.0f)
                        pan += delta
                    }
                }
        ) {
            val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
            val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
            fun point(n: Node) = Offset(
                35f + n.x.toFloat() / maxX * (size.width - 70f),
                45f + n.y.toFloat() / maxY * (size.height - 85f)
            )

            // 1. Subtle Architectural Blueprint Grid
            val subGridColor = Color(0xFF151F32)
            val cols = 14
            val rows = 10
            for (i in 0..cols) {
                val x = i * size.width / cols
                drawLine(subGridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            for (j in 0..rows) {
                val y = j * size.height / rows
                drawLine(subGridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }

            withTransform({
                translate(pan.x, pan.y)
                scale(zoom, zoom, center)
            }) {
                // 2. Corridors & Paths (Edges)
                val relevantEdges = s.map.edges.filter {
                    nodes[it.from]?.floorId == s.selectedFloor && nodes[it.to]?.floorId == s.selectedFloor
                }

                // Draw background inactive edges first
                relevantEdges.filter { it.id !in active && it.id != currentStepEdge && it.id !in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    drawLine(
                        color = Color(0xFF334155), // Slate-700
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw excluded / blocked paths
                relevantEdges.filter { it.id in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    drawLine(
                        color = Color(0xFFEF4444), // Crimson danger
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 7f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }

                // Draw active route (Multi-layered luminous glow)
                relevantEdges.filter { it.id in active && it.id != currentStepEdge }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    // Glow halo
                    drawLine(
                        color = Color(0xFF0D9488).copy(alpha = 0.35f),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 18f,
                        cap = StrokeCap.Round
                    )
                    // Core route line
                    drawLine(
                        color = Color(0xFF14B8A6), // Vibrant Teal
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 9f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw active guided step (Bright glowing amber/orange beam)
                if (currentStepEdge != null) {
                    relevantEdges.filter { it.id == currentStepEdge }.forEach { e ->
                        val fromPt = point(nodes.getValue(e.from))
                        val toPt = point(nodes.getValue(e.to))
                        // Pulse glow
                        drawLine(
                            color = Color(0xFFF59E0B).copy(alpha = 0.45f),
                            start = fromPt,
                            end = toPt,
                            strokeWidth = 26f,
                            cap = StrokeCap.Round
                        )
                        // Core beam
                        drawLine(
                            color = Color(0xFFFBBF24), // Vibrant Amber Core
                            start = fromPt,
                            end = toPt,
                            strokeWidth = 13f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. Landmarks and Nodes
                floorNodes.forEach { n ->
                    val pt = point(n)
                    val isCurrent = n.id == s.current
                    val isDestination = n.id == s.destination
                    val isVisited = n.id in visitedNodes
                    val isElevator = n.id.startsWith("lift", true) || n.name.contains("lift", true)

                    // Render animated pulse on current position
                    if (isCurrent) {
                        drawCircle(
                            color = Color(0xFF2DD4BF).copy(alpha = pulseAlpha),
                            radius = pulseRadius,
                            center = pt
                        )
                    }

                    // Render Destination glowing beacon rings
                    if (isDestination) {
                        drawCircle(
                            color = Color(0xFF6366F1).copy(alpha = 0.35f),
                            radius = 18f,
                            center = pt
                        )
                    }

                    // Node base circle & border
                    val nodeBorder = when {
                        isCurrent -> Color(0xFF5EEAD4)
                        isDestination -> Color(0xFFA5B4FC)
                        isVisited -> Color(0xFF86EFAC)
                        isElevator -> Color(0xFFFDE047)
                        else -> Color(0xFF64748B)
                    }

                    val nodeFill = when {
                        isCurrent -> Color(0xFF0F766E)
                        isDestination -> Color(0xFF4338CA)
                        isVisited -> Color(0xFF15803D)
                        isElevator -> Color(0xFF854D0E)
                        else -> Color(0xFF1E293B)
                    }

                    drawCircle(nodeBorder, radius = if (isCurrent || isDestination) 12f else 8f, center = pt)
                    drawCircle(nodeFill, radius = if (isCurrent || isDestination) 9f else 6f, center = pt)

                    // Label badge with background pill for perfect readability
                    val cleanText = n.name.take(20)
                    val bounds = Rect()
                    labelTextPaint.getTextBounds(cleanText, 0, cleanText.length, bounds)
                    val textWidth = bounds.width().toFloat()
                    val textHeight = bounds.height().toFloat()

                    val pillPaddingH = 10f
                    val pillPaddingV = 6f
                    val pillLeft = pt.x + 14f
                    val pillTop = pt.y - textHeight - pillPaddingV - 4f
                    val pillWidth = textWidth + pillPaddingH * 2
                    val pillHeight = textHeight + pillPaddingV * 2

                    // Draw pill background
                    drawRoundRect(
                        color = Color(0xFF090D16).copy(alpha = 0.85f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFF334155).copy(alpha = 0.7f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f)
                    )

                    // Draw text inside pill
                    labelTextPaint.color = if (isCurrent || isDestination) android.graphics.Color.WHITE else android.graphics.Color.rgb(226, 232, 240)
                    drawContext.canvas.nativeCanvas.drawText(
                        cleanText,
                        pillLeft + pillPaddingH,
                        pillTop + pillHeight - pillPaddingV,
                        labelTextPaint
                    )
                }
            }
        }

        // --- Floating Map Overlays & Controls ---

        // Top-Left: Active Floor Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF090D16).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2DD4BF)))
                Text(
                    text = floorLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top-Right: Quick Map Reset & Center
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF090D16).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            IconButton(
                onClick = { zoom = 1.15f; pan = Offset.Zero },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset view",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom-Right: Floating Zoom In / Out Column
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF090D16).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column {
                IconButton(
                    onClick = { zoom = (zoom * 1.3f).coerceAtMost(5.0f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.width(36.dp))
                IconButton(
                    onClick = { zoom = (zoom / 1.3f).coerceAtLeast(0.8f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom-Left: Map Legend Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF090D16).copy(alpha = 0.82f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2DD4BF)))
                    Text("Route", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Text("Step", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Text("Blocked", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                }
            }
        }
    }
}
