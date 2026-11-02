package com.accesspath.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CampusCanvas(
    s: AppSnapshot,
    route: RouteResult.Found?,
    guidedStepIndex: Int? = null,
    visitedNodes: Set<String> = emptySet(),
    selectedNode: Node? = null,
    onNodeSelected: (Node?) -> Unit = {},
    onStyleChange: (MapStyle) -> Unit = {}
) {
    var zoom by remember(s.selectedFloor) { mutableFloatStateOf(1.2f) }
    var pan by remember(s.selectedFloor) { mutableStateOf(Offset.Zero) }
    var showLayersMenu by remember { mutableStateOf(false) }

    val floorNodes = remember(s.map, s.selectedFloor) { s.map.nodes.filter { it.floorId == s.selectedFloor } }
    val nodes = s.map.nodes.associateBy { it.id }
    val excluded = remember(s) { Routing.exclusions(s) }
    val active = route?.edges?.map { it.id }?.toSet().orEmpty()
    val currentStepEdge = if (guidedStepIndex != null && route != null && guidedStepIndex in route.edges.indices) {
        route.edges[guidedStepIndex].id
    } else null

    // Smooth camera center animation when selectedNode changes
    LaunchedEffect(selectedNode) {
        if (selectedNode != null && selectedNode.floorId == s.selectedFloor) {
            val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
            val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
            val targetX = 35f + selectedNode.x.toFloat() / maxX * (700f - 70f)
            val targetY = 45f + selectedNode.y.toFloat() / maxY * (350f - 85f)
            pan = Offset(350f - targetX, 175f - targetY)
            zoom = 2.0f
        }
    }

    // Infinite pulse animation for current location beacon & geofence zones
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Heading beacon animation
    val headingAngle by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headingAngle"
    )

    val labelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 23f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }

    val currentFloor = s.map.floors.find { it.id == s.selectedFloor }
    val floorLabel = currentFloor?.label ?: s.selectedFloor

    // Style-dependent visual tokens
    val canvasBgColor = when (s.mapStyle) {
        MapStyle.BLUEPRINT -> Color(0xFF0F172A)          // Architectural Navy Blueprint
        MapStyle.SATELLITE_HYBRID -> Color(0xFF132018)    // Deep Satellite Organic Ground
        MapStyle.ACCESSIBILITY_HEATMAP -> Color(0xFF18181B) // Neutral Dark Heatmap Canvas
        MapStyle.NIGHT_CYBER -> Color(0xFF000000)        // Pure OLED Cyber Void
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(canvasBgColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Interactive campus map floor $floorLabel with ${floorNodes.size} landmarks."
                }
                .pointerInput(s.selectedFloor, zoom, pan) {
                    detectTapGestures { tapOffset ->
                        val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
                        val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
                        fun point(n: Node) = Offset(
                            35f + n.x.toFloat() / maxX * (size.width - 70f),
                            45f + n.y.toFloat() / maxY * (size.height - 85f)
                        )

                        val center = Offset(size.width / 2f, size.height / 2f)
                        val clicked = floorNodes.find { n ->
                            val pt = point(n)
                            val screenX = (pt.x - center.x) * zoom + center.x + pan.x
                            val screenY = (pt.y - center.y) * zoom + center.y + pan.y
                            val dist = (tapOffset - Offset(screenX, screenY)).getDistance()
                            dist < 80f // 80px touch target tolerance
                        }
                        onNodeSelected(clicked)
                    }
                }
                .pointerInput(s.selectedFloor) {
                    detectTransformGestures { _, delta, scale, _ ->
                        zoom = (zoom * scale).coerceIn(0.7f, 6.0f)
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

            // 1. Map Layer Background Grid & Terrain Textures
            when (s.mapStyle) {
                MapStyle.BLUEPRINT -> {
                    val subGridColor = Color(0xFF1E293B)
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
                }
                MapStyle.SATELLITE_HYBRID -> {
                    // Satellite terrain simulated texture
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E3A2B), Color(0xFF111E16)),
                            center = center,
                            radius = size.width.coerceAtLeast(size.height)
                        )
                    )
                    // Campus outer grounds border
                    drawRoundRect(
                        color = Color(0xFF2E5339).copy(alpha = 0.4f),
                        topLeft = Offset(10f, 10f),
                        size = Size(size.width - 20f, size.height - 20f),
                        cornerRadius = CornerRadius(20f, 20f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
                    )
                }
                MapStyle.ACCESSIBILITY_HEATMAP -> {
                    // Heatmap ambient wash
                    drawRect(Color(0xFF18181B))
                }
                MapStyle.NIGHT_CYBER -> {
                    // Pure OLED with neon grid points
                    val cols = 12
                    val rows = 8
                    for (i in 0..cols) {
                        for (j in 0..rows) {
                            drawCircle(Color(0xFF06B6D4).copy(alpha = 0.25f), 1.5f, Offset(i * size.width / cols, j * size.height / rows))
                        }
                    }
                }
            }

            withTransform({
                translate(pan.x, pan.y)
                scale(zoom, zoom, center)
            }) {
                // 2. Active Geofence Zones Overlay
                s.geofences.filter { it.floorId == s.selectedFloor && it.active }.forEach { gf ->
                    val centerPt = Offset(
                        35f + gf.centerX.toFloat() / maxX * (size.width - 70f),
                        45f + gf.centerY.toFloat() / maxY * (size.height - 85f)
                    )
                    val radiusPx = (gf.radius.toFloat() / 100f) * (size.width - 70f) * 0.4f

                    val gfColor = when (gf.type) {
                        GeofenceType.SAFE_ZONE -> Color(0xFF10B981)
                        GeofenceType.HAZARD -> Color(0xFFEF4444)
                        GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF3B82F6)
                    }

                    // Translucent pulsing geofence fill
                    drawCircle(gfColor.copy(alpha = 0.12f), radius = radiusPx + pulseRadius * 0.3f, center = centerPt)
                    // Border perimeter
                    drawCircle(
                        color = gfColor.copy(alpha = 0.75f),
                        radius = radiusPx,
                        center = centerPt,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )
                    // Geofence center marker
                    drawCircle(gfColor, radius = 5f, center = centerPt)
                }

                // 3. Corridors & Paths (Edges)
                val relevantEdges = s.map.edges.filter {
                    nodes[it.from]?.floorId == s.selectedFloor && nodes[it.to]?.floorId == s.selectedFloor
                }

                // Inactive edges
                relevantEdges.filter { it.id !in active && it.id != currentStepEdge && it.id !in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))

                    val edgeColor = when (s.mapStyle) {
                        MapStyle.ACCESSIBILITY_HEATMAP -> {
                            val slope = e.slope ?: 0.0
                            when {
                                slope <= 2.0 -> Color(0xFF10B981) // Flat / Green
                                slope <= 5.0 -> Color(0xFFEAB308) // Moderate slope / Yellow
                                slope <= 8.0 -> Color(0xFFF97316) // Max wheelchair ramp / Orange
                                else -> Color(0xFFEF4444)         // Steep / Red
                            }
                        }
                        MapStyle.NIGHT_CYBER -> Color(0xFF0E7490) // Cyan cyber line
                        MapStyle.SATELLITE_HYBRID -> Color(0xFF475569) // Paved outdoor walkway
                        MapStyle.BLUEPRINT -> Color(0xFF334155) // Slate architectural wall
                    }

                    drawLine(
                        color = edgeColor,
                        start = fromPt,
                        end = toPt,
                        strokeWidth = if (s.mapStyle == MapStyle.ACCESSIBILITY_HEATMAP) 8f else 6f,
                        cap = StrokeCap.Round
                    )
                }

                // Excluded / Blocked paths
                relevantEdges.filter { it.id in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 7f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }

                // Active route multi-layered luminous glow
                relevantEdges.filter { it.id in active && it.id != currentStepEdge }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))

                    val glowColor = if (s.mapStyle == MapStyle.NIGHT_CYBER) Color(0xFF06B6D4) else Color(0xFF0D9488)
                    val coreColor = if (s.mapStyle == MapStyle.NIGHT_CYBER) Color(0xFF22D3EE) else Color(0xFF14B8A6)

                    drawLine(
                        color = glowColor.copy(alpha = 0.35f),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 20f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = coreColor,
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 9f,
                        cap = StrokeCap.Round
                    )
                }

                // Current guided step (Bright glowing amber beam)
                if (currentStepEdge != null) {
                    relevantEdges.filter { it.id == currentStepEdge }.forEach { e ->
                        val fromPt = point(nodes.getValue(e.from))
                        val toPt = point(nodes.getValue(e.to))
                        drawLine(
                            color = Color(0xFFF59E0B).copy(alpha = 0.45f),
                            start = fromPt,
                            end = toPt,
                            strokeWidth = 26f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFFFBBF24),
                            start = fromPt,
                            end = toPt,
                            strokeWidth = 13f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 4. Landmarks and Nodes
                floorNodes.forEach { n ->
                    val pt = point(n)
                    val isCurrent = n.id == s.current
                    val isDestination = n.id == s.destination
                    val isVisited = n.id in visitedNodes
                    val isSelected = n.id == selectedNode?.id
                    val isElevator = n.id.startsWith("lift", true) || n.name.contains("lift", true)
                    val isWashroom = n.id.contains("wash", true) || n.name.contains("washroom", true)

                    // Directional Heading Cone on Current Location
                    if (isCurrent) {
                        val coneLength = 32f
                        val angleRad = Math.toRadians((headingAngle + 90).toDouble())
                        val leftRad = angleRad - 0.4
                        val rightRad = angleRad + 0.4

                        val tip = Offset(pt.x + (coneLength * cos(angleRad)).toFloat(), pt.y - (coneLength * sin(angleRad)).toFloat())
                        val left = Offset(pt.x + (16f * cos(leftRad)).toFloat(), pt.y - (16f * sin(leftRad)).toFloat())
                        val right = Offset(pt.x + (16f * cos(rightRad)).toFloat(), pt.y - (16f * sin(rightRad)).toFloat())

                        val conePath = Path().apply {
                            moveTo(pt.x, pt.y)
                            lineTo(left.x, left.y)
                            lineTo(tip.x, tip.y)
                            lineTo(right.x, right.y)
                            close()
                        }
                        drawPath(conePath, brush = Brush.radialGradient(colors = listOf(Color(0xFF2DD4BF).copy(alpha = 0.5f), Color.Transparent), center = pt, radius = 40f))

                        // Pulsing location beacon
                        drawCircle(color = Color(0xFF2DD4BF).copy(alpha = pulseAlpha), radius = pulseRadius, center = pt)
                    }

                    // Destination beacon rings
                    if (isDestination) {
                        drawCircle(color = Color(0xFF6366F1).copy(alpha = 0.35f), radius = 20f, center = pt)
                    }

                    // Selection highlight ring
                    if (isSelected) {
                        drawCircle(color = Color(0xFFF59E0B), radius = 17f, center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f))
                    }

                    // Node base circle & border
                    val nodeBorder = when {
                        isCurrent -> Color(0xFF5EEAD4)
                        isDestination -> Color(0xFFA5B4FC)
                        isSelected -> Color(0xFFFDE047)
                        isVisited -> Color(0xFF86EFAC)
                        isElevator -> Color(0xFFFDE047)
                        isWashroom -> Color(0xFF93C5FD)
                        else -> Color(0xFF64748B)
                    }

                    val nodeFill = when {
                        isCurrent -> Color(0xFF0F766E)
                        isDestination -> Color(0xFF4338CA)
                        isSelected -> Color(0xFFB45309)
                        isVisited -> Color(0xFF15803D)
                        isElevator -> Color(0xFF854D0E)
                        isWashroom -> Color(0xFF1E40AF)
                        else -> Color(0xFF1E293B)
                    }

                    drawCircle(nodeBorder, radius = if (isCurrent || isDestination || isSelected) 13f else 8f, center = pt)
                    drawCircle(nodeFill, radius = if (isCurrent || isDestination || isSelected) 10f else 6f, center = pt)

                    // Label badge with pill background
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

                    drawRoundRect(
                        color = Color(0xFF090D16).copy(alpha = if (isSelected) 0.95f else 0.85f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155).copy(alpha = 0.7f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(if (isSelected) 2f else 1.2f)
                    )

                    labelTextPaint.color = if (isSelected) android.graphics.Color.rgb(253, 224, 71)
                    else if (isCurrent || isDestination) android.graphics.Color.WHITE
                    else android.graphics.Color.rgb(226, 232, 240)

                    drawContext.canvas.nativeCanvas.drawText(
                        cleanText,
                        pillLeft + pillPaddingH,
                        pillTop + pillHeight - pillPaddingV,
                        labelTextPaint
                    )
                }
            }
        }

        // --- Floating Map HUD & Controls ---

        // Top-Left: Active Floor Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF090D16).copy(alpha = 0.88f),
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

        // Top-Right: Layers Switcher & Center Camera
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Layers Menu Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090D16).copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Box {
                    IconButton(
                        onClick = { showLayersMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("🗺️", fontSize = 16.sp)
                    }
                    DropdownMenu(
                        expanded = showLayersMenu,
                        onDismissRequest = { showLayersMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🏛️ Blueprint Architecture") },
                            onClick = { onStyleChange(MapStyle.BLUEPRINT); showLayersMenu = false },
                            leadingIcon = { if (s.mapStyle == MapStyle.BLUEPRINT) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text("🛰️ Satellite & Outdoor Terrain") },
                            onClick = { onStyleChange(MapStyle.SATELLITE_HYBRID); showLayersMenu = false },
                            leadingIcon = { if (s.mapStyle == MapStyle.SATELLITE_HYBRID) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text("🌡️ Accessibility Slope Heatmap") },
                            onClick = { onStyleChange(MapStyle.ACCESSIBILITY_HEATMAP); showLayersMenu = false },
                            leadingIcon = { if (s.mapStyle == MapStyle.ACCESSIBILITY_HEATMAP) Text("✓") }
                        )
                        DropdownMenuItem(
                            text = { Text("⚡ Night Cyber OLED Mode") },
                            onClick = { onStyleChange(MapStyle.NIGHT_CYBER); showLayersMenu = false },
                            leadingIcon = { if (s.mapStyle == MapStyle.NIGHT_CYBER) Text("✓") }
                        )
                    }
                }
            }

            // Center Camera Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090D16).copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                IconButton(
                    onClick = {
                        zoom = 1.2f
                        pan = Offset.Zero
                        onNodeSelected(null)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Center map",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                    onClick = { zoom = (zoom * 1.35f).coerceAtMost(6.0f) },
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
                    onClick = { zoom = (zoom / 1.35f).coerceAtLeast(0.7f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom-Left: Style & Layer Tag Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF090D16).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    when (s.mapStyle) {
                        MapStyle.BLUEPRINT -> "📐 Blueprint"
                        MapStyle.SATELLITE_HYBRID -> "🛰️ Satellite"
                        MapStyle.ACCESSIBILITY_HEATMAP -> "🌡️ Slope Heatmap"
                        MapStyle.NIGHT_CYBER -> "⚡ Cyber OLED"
                    },
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
