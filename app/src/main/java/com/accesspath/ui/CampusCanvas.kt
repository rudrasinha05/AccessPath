@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.accesspath.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onStyleChange: (MapStyle) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
    isFreehandMode: Boolean = false,
    freehandPoints: List<GeofencePoint> = emptyList(),
    onFreehandPointAdded: (GeofencePoint) -> Unit = {},
    onFreehandDrawingFinished: () -> Unit = {},
    is3DMode: Boolean = false,
    bearing: Float = 0f,
    onBearingChange: (Float) -> Unit = {}
) {
    var zoom by remember(s.selectedFloor) { mutableFloatStateOf(1.25f) }
    var pan by remember(s.selectedFloor) { mutableStateOf(Offset.Zero) }

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
            val targetY = 45f + selectedNode.y.toFloat() / maxY * (500f - 85f)
            pan = Offset(350f - targetX, 250f - targetY)
            zoom = 2.2f
        }
    }

    // Infinite pulse animation for current location beacon & geofence zones
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 36f,
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
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headingAngle"
    )

    val labelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
    }

    val currentFloor = s.map.floors.find { it.id == s.selectedFloor }
    val floorLabel = currentFloor?.label ?: s.selectedFloor

    // Google Maps Style-dependent Canvas Background Colors
    val canvasBgColor = when (s.mapStyle) {
        MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> Color(0xFFF8F9FA) // Google Maps Clean Vector Ground
        MapStyle.SATELLITE, MapStyle.HYBRID, MapStyle.SATELLITE_HYBRID -> Color(0xFF132018) // Google Satellite Earth
        MapStyle.TERRAIN -> Color(0xFFE8E2D5) // Google Maps Topographic Parchment
        MapStyle.ACCESSIBILITY_HEATMAP -> Color(0xFF1E293B) // Dark Heatmap Canvas
        MapStyle.NIGHT_CYBER -> Color(0xFF090D16) // OLED Cyber Night
    }

    Box(
        modifier = modifier
            .background(canvasBgColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Interactive Google Map campus view for floor $floorLabel."
                }
                .pointerInput(s.selectedFloor, isFreehandMode) {
                    if (isFreehandMode) {
                        // Freehand touch gesture detector
                        detectDragGestures(
                            onDragStart = { offset ->
                                val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
                                val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                val p1 = offset - pan
                                val rad = -Math.toRadians(bearing.toDouble())
                                val dx = p1.x - center.x
                                val dy = p1.y - center.y
                                val rx = dx * Math.cos(rad) - dy * Math.sin(rad) + center.x
                                val ry = dx * Math.sin(rad) + dy * Math.cos(rad) + center.y
                                val unscaledX = (rx - center.x) / zoom + center.x
                                val unscaledY = (ry - center.y) / zoom + center.y

                                val mapX = ((unscaledX - 35f) / (size.width - 70f) * maxX).toDouble()
                                val mapY = ((unscaledY - 45f) / (size.height - 85f) * maxY).toDouble()
                                onFreehandPointAdded(GeofencePoint(mapX, mapY))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
                                val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                val p1 = change.position - pan
                                val rad = -Math.toRadians(bearing.toDouble())
                                val dx = p1.x - center.x
                                val dy = p1.y - center.y
                                val rx = dx * Math.cos(rad) - dy * Math.sin(rad) + center.x
                                val ry = dx * Math.sin(rad) + dy * Math.cos(rad) + center.y
                                val unscaledX = (rx - center.x) / zoom + center.x
                                val unscaledY = (ry - center.y) / zoom + center.y

                                val mapX = ((unscaledX - 35f) / (size.width - 70f) * maxX).toDouble()
                                val mapY = ((unscaledY - 45f) / (size.height - 85f) * maxY).toDouble()
                                onFreehandPointAdded(GeofencePoint(mapX, mapY))
                            },
                            onDragEnd = {
                                onFreehandDrawingFinished()
                            }
                        )
                    }
                }
                .pointerInput(s.selectedFloor, zoom, pan, bearing, isFreehandMode) {
                    if (!isFreehandMode) {
                        detectTapGestures { tapOffset ->
                            val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
                            val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
                            val center = Offset(size.width / 2f, size.height / 2f)

                            val clicked = floorNodes.find { n ->
                                val pt = Offset(
                                    35f + n.x.toFloat() / maxX * (size.width - 70f),
                                    45f + n.y.toFloat() / maxY * (size.height - 85f)
                                )
                                // Apply transform forward
                                val dx = pt.x - center.x
                                val dy = pt.y - center.y
                                val scaledX = dx * zoom
                                val scaledY = dy * zoom
                                val rad = Math.toRadians(bearing.toDouble())
                                val rx = scaledX * Math.cos(rad) - scaledY * Math.sin(rad) + center.x + pan.x
                                val ry = scaledX * Math.sin(rad) + scaledY * Math.cos(rad) + center.y + pan.y
                                (tapOffset - Offset(rx.toFloat(), ry.toFloat())).getDistance() < 90f
                            }
                            onNodeSelected(clicked)
                        }
                    }
                }
                .pointerInput(s.selectedFloor, isFreehandMode) {
                    if (!isFreehandMode) {
                        detectTransformGestures { _, delta, scale, rotation ->
                            zoom = (zoom * scale).coerceIn(0.35f, 6.0f)
                            pan += delta
                            if (Math.abs(rotation) > 0.5f) {
                                onBearingChange((bearing + rotation) % 360f)
                            }
                        }
                    }
                }
        ) {
            val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
            val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
            fun point(n: Node) = Offset(
                35f + n.x.toFloat() / maxX * (size.width - 70f),
                45f + n.y.toFloat() / maxY * (size.height - 85f)
            )

            // --- 1. Background Textures for Google Maps Views ---
            when (s.mapStyle) {
                MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> {
                    // Google Maps Clean Vector: Light green park/lawn areas
                    drawRoundRect(
                        color = Color(0xFFCEEAD6).copy(alpha = 0.5f),
                        topLeft = Offset(20f, 20f),
                        size = Size(size.width - 40f, size.height - 40f),
                        cornerRadius = CornerRadius(24f, 24f)
                    )
                    // Google Maps soft grid / block lines
                    val cols = 10
                    val rows = 8
                    for (i in 0..cols) {
                        drawLine(Color(0xFFE8EAED), Offset(i * size.width / cols, 0f), Offset(i * size.width / cols, size.height), 1.5f)
                    }
                    for (j in 0..rows) {
                        drawLine(Color(0xFFE8EAED), Offset(0f, j * size.height / rows), Offset(size.width, j * size.height / rows), 1.5f)
                    }
                }
                MapStyle.SATELLITE, MapStyle.HYBRID, MapStyle.SATELLITE_HYBRID -> {
                    // High-res Satellite Photorealistic aerial imagery texture
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1B3828), Color(0xFF0F1E15)),
                            center = center,
                            radius = size.width.coerceAtLeast(size.height)
                        )
                    )
                    // Outdoor lawns & tree textures
                    drawCircle(Color(0xFF264C33).copy(alpha = 0.6f), radius = size.width * 0.35f, center = Offset(size.width * 0.3f, size.height * 0.4f))
                    drawCircle(Color(0xFF1E3D29).copy(alpha = 0.6f), radius = size.width * 0.28f, center = Offset(size.width * 0.7f, size.height * 0.6f))
                }
                MapStyle.TERRAIN -> {
                    // Google Maps Topographic Elevation Contour Lines
                    drawRect(Color(0xFFE8E2D5))
                    // Subtle topographic contour waves
                    val contourPaint = Color(0xFFC7BFA8)
                    for (step in 1..8) {
                        val r = (step * 80f)
                        drawCircle(contourPaint, radius = r, center = Offset(size.width * 0.2f, size.height * 0.3f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))))
                        drawCircle(contourPaint, radius = r * 1.2f, center = Offset(size.width * 0.8f, size.height * 0.7f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))))
                    }
                }
                MapStyle.ACCESSIBILITY_HEATMAP -> {
                    drawRect(Color(0xFF18181B))
                }
                MapStyle.NIGHT_CYBER -> {
                    drawRect(Color(0xFF05080E))
                    val cols = 12
                    val rows = 8
                    for (i in 0..cols) {
                        for (j in 0..rows) {
                            drawCircle(Color(0xFF06B6D4).copy(alpha = 0.25f), 1.5f, Offset(i * size.width / cols, j * size.height / rows))
                        }
                    }
                }
            }

            // --- Apply Movable Transformation Matrix (Pan, Zoom, Bearing, 3D Tilt) ---
            withTransform({
                translate(pan.x, pan.y)
                rotate(bearing, center)
                scale(zoom, zoom, center)
            }) {
                val zOffset3D = if (is3DMode) Offset(-16f, -22f) else Offset.Zero

                // --- 2. Active Geofences (Both Circular & Freehand Polygons) ---
                s.geofences.filter { it.floorId == s.selectedFloor && it.active }.forEach { gf ->
                    val gfColor = when (gf.type) {
                        GeofenceType.SAFE_ZONE -> Color(0xFF10B981)
                        GeofenceType.HAZARD -> Color(0xFFEF4444)
                        GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF06B6D4)
                        GeofenceType.QUIET_ZONE -> Color(0xFF6366F1)
                        GeofenceType.RESTRICTED -> Color(0xFFF59E0B)
                    }

                    if (gf.polygon.size >= 3) {
                        // Render FREEHAND CLOSED POLYGON
                        val polyPath = Path().apply {
                            val first = gf.polygon.first()
                            moveTo(35f + first.x.toFloat() / maxX * (size.width - 70f), 45f + first.y.toFloat() / maxY * (size.height - 85f))
                            for (i in 1 until gf.polygon.size) {
                                val p = gf.polygon[i]
                                lineTo(35f + p.x.toFloat() / maxX * (size.width - 70f), 45f + p.y.toFloat() / maxY * (size.height - 85f))
                            }
                            close()
                        }

                        // Fill freehand polygon with translucent tint
                        drawPath(polyPath, color = gfColor.copy(alpha = 0.22f))
                        // Dashed stroke perimeter
                        drawPath(
                            polyPath,
                            color = gfColor.copy(alpha = 0.85f),
                            style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                        )
                        // Centroid marker & label
                        val centroidX = 35f + gf.centerX.toFloat() / maxX * (size.width - 70f)
                        val centroidY = 45f + gf.centerY.toFloat() / maxY * (size.height - 85f)
                        drawCircle(gfColor, radius = 7f, center = Offset(centroidX, centroidY))
                    } else {
                        // Render Circular Geofence
                        val centerPt = Offset(
                            35f + gf.centerX.toFloat() / maxX * (size.width - 70f),
                            45f + gf.centerY.toFloat() / maxY * (size.height - 85f)
                        )
                        val radiusPx = (gf.radius.toFloat() / 100f) * (size.width - 70f) * 0.45f
                        drawCircle(gfColor.copy(alpha = 0.15f), radius = radiusPx + pulseRadius * 0.3f, center = centerPt)
                        drawCircle(
                            color = gfColor.copy(alpha = 0.8f),
                            radius = radiusPx,
                            center = centerPt,
                            style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                        )
                        drawCircle(gfColor, radius = 6f, center = centerPt)
                    }
                }

                // --- 3. Live Freehand Stroke Preview while user is drawing ---
                if (freehandPoints.isNotEmpty()) {
                    val strokePath = Path().apply {
                        val first = freehandPoints.first()
                        moveTo(35f + first.x.toFloat() / maxX * (size.width - 70f), 45f + first.y.toFloat() / maxY * (size.height - 85f))
                        for (i in 1 until freehandPoints.size) {
                            val p = freehandPoints[i]
                            lineTo(35f + p.x.toFloat() / maxX * (size.width - 70f), 45f + p.y.toFloat() / maxY * (size.height - 85f))
                        }
                    }
                    // Glowing stroke
                    drawPath(
                        strokePath,
                        color = Color(0xFFFF5252),
                        style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Closing preview dashed line
                    if (freehandPoints.size >= 3) {
                        val first = freehandPoints.first()
                        val last = freehandPoints.last()
                        drawLine(
                            color = Color(0xFFFF5252).copy(alpha = 0.65f),
                            start = Offset(35f + last.x.toFloat() / maxX * (size.width - 70f), 45f + last.y.toFloat() / maxY * (size.height - 85f)),
                            end = Offset(35f + first.x.toFloat() / maxX * (size.width - 70f), 45f + first.y.toFloat() / maxY * (size.height - 85f)),
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                        )
                    }
                }

                // --- 4. Corridors & Road Networks (Edges) ---
                val relevantEdges = s.map.edges.filter {
                    nodes[it.from]?.floorId == s.selectedFloor && nodes[it.to]?.floorId == s.selectedFloor
                }

                // Regular / Inactive corridors
                relevantEdges.filter { it.id !in active && it.id != currentStepEdge && it.id !in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))

                    val (roadColor, roadWidth) = when (s.mapStyle) {
                        MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> Color(0xFFFFFFFF) to 12f // Google Maps wide white avenues
                        MapStyle.SATELLITE, MapStyle.SATELLITE_HYBRID -> Color(0xFF334155) to 10f // Asphalt walkways
                        MapStyle.TERRAIN -> Color(0xFF81C784) to 8f // Nature trails
                        MapStyle.HYBRID -> Color(0xFFFFF59D) to 12f // Yellow arterial roads
                        MapStyle.ACCESSIBILITY_HEATMAP -> {
                            val slope = e.slope ?: 0.0
                            when {
                                slope <= 2.0 -> Color(0xFF10B981) // Green
                                slope <= 5.0 -> Color(0xFFEAB308) // Yellow
                                slope <= 8.0 -> Color(0xFFF97316) // Orange
                                else -> Color(0xFFEF4444)         // Red
                            } to 10f
                        }
                        MapStyle.NIGHT_CYBER -> Color(0xFF0E7490) to 8f
                    }

                    // Road casing / border (Google Maps styling)
                    if (s.mapStyle == MapStyle.DEFAULT_VECTOR || s.mapStyle == MapStyle.BLUEPRINT) {
                        drawLine(
                            color = Color(0xFFDADCE0),
                            start = fromPt,
                            end = toPt,
                            strokeWidth = roadWidth + 4f,
                            cap = StrokeCap.Round
                        )
                    }

                    drawLine(
                        color = roadColor,
                        start = fromPt,
                        end = toPt,
                        strokeWidth = roadWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Blocked / restricted edges
                relevantEdges.filter { it.id in excluded }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                        cap = StrokeCap.Round
                    )
                }

                // Active navigation route (Google Maps Navigation Polyline)
                relevantEdges.filter { it.id in active && it.id != currentStepEdge }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    // Route shadow/glow
                    drawLine(
                        color = Color(0xFF1A73E8).copy(alpha = 0.35f),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 18f,
                        cap = StrokeCap.Round
                    )
                    // Google Maps bold blue route
                    drawLine(
                        color = Color(0xFF1A73E8),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }

                // Current guided step edge (Glowing vibrant mint/orange)
                if (currentStepEdge != null) {
                    relevantEdges.find { it.id == currentStepEdge }?.let { e ->
                        val fromPt = point(nodes.getValue(e.from))
                        val toPt = point(nodes.getValue(e.to))
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = fromPt,
                            end = toPt,
                            strokeWidth = 14f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // --- 5. Building Footprints & Rooms (With Optional 3D Extrusion) ---
                floorNodes.forEach { n ->
                    val pt = point(n)

                    // 3D Extrusion Effect
                    if (is3DMode) {
                        // Extruded side facet (shadowed)
                        val sidePath = Path().apply {
                            moveTo(pt.x + 22f, pt.y - 15f)
                            lineTo(pt.x + 22f + zOffset3D.x, pt.y - 15f + zOffset3D.y)
                            lineTo(pt.x + 22f + zOffset3D.x, pt.y + 15f + zOffset3D.y)
                            lineTo(pt.x + 22f, pt.y + 15f)
                            close()
                        }
                        drawPath(sidePath, color = Color(0xFF94A3B8).copy(alpha = 0.7f))

                        // Extruded top facet (roof)
                        drawRoundRect(
                            color = Color(0xFFCBD5E1),
                            topLeft = pt + zOffset3D - Offset(22f, 15f),
                            size = Size(44f, 30f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }

                    // Base building / landmark footprint
                    val isNodeSelected = selectedNode?.id == n.id
                    val isStart = s.current == n.id
                    val isDest = s.destination == n.id

                    // Landmark POI Pin Shape & Color
                    val (poiColor, poiIcon) = when {
                        n.type.contains("ELEVATOR", ignoreCase = true) || n.name.contains("Lift", ignoreCase = true) -> Color(0xFF007B83) to "🛗"
                        n.type.contains("RAMP", ignoreCase = true) || n.name.contains("Ramp", ignoreCase = true) -> Color(0xFF1E8E3E) to "♿"
                        n.type.contains("STAIRS", ignoreCase = true) || n.name.contains("Stairs", ignoreCase = true) -> Color(0xFF64748B) to "🪜"
                        n.type.contains("ENTRANCE", ignoreCase = true) || n.name.contains("Gate", ignoreCase = true) -> Color(0xFFE37400) to "🚪"
                        n.name.contains("Washroom", ignoreCase = true) || n.type.contains("WASHROOM", ignoreCase = true) -> Color(0xFF12B5CB) to "🚻"
                        else -> Color(0xFF1A73E8) to "📍" // Google Blue
                    }

                    // Draw Room Card Footprint (Google Maps Style)
                    if (s.mapStyle == MapStyle.DEFAULT_VECTOR || s.mapStyle == MapStyle.BLUEPRINT) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = pt - Offset(24f, 16f),
                            size = Size(48f, 32f),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                        drawRoundRect(
                            color = Color(0xFFDADCE0),
                            topLeft = pt - Offset(24f, 16f),
                            size = Size(48f, 32f),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(1.5f)
                        )
                    }

                    // Selected Node Pulsing Aura
                    if (isNodeSelected) {
                        drawCircle(Color(0xFF1A73E8).copy(alpha = 0.25f), radius = 28f + pulseRadius * 0.4f, center = pt)
                        drawCircle(Color(0xFF1A73E8), radius = 22f, center = pt, style = Stroke(3.5f))
                    }

                    // Google Maps Circular POI Pin Badge
                    drawCircle(poiColor, radius = 14f, center = pt)
                    drawCircle(Color.White, radius = 14f, center = pt, style = Stroke(2.5f))

                    // Text Label (Room name below pin)
                    drawContext.canvas.nativeCanvas.drawText(
                        n.name.take(12),
                        pt.x - 25f,
                        pt.y + 32f,
                        labelTextPaint
                    )
                }

                // --- 6. Live User Location Beacon (Google Maps Blue Dot + Pulsing Halo) ---
                nodes[s.current]?.let { cur ->
                    if (cur.floorId == s.selectedFloor) {
                        val curPt = point(cur)

                        // Google Maps blue pulsating accuracy radar aura
                        drawCircle(Color(0xFF4285F4).copy(alpha = pulseAlpha), radius = pulseRadius + 18f, center = curPt)
                        drawCircle(Color(0xFF4285F4).copy(alpha = 0.22f), radius = 24f, center = curPt)

                        // Directional heading cone
                        val conePath = Path().apply {
                            val angleRad = Math.toRadians((headingAngle - 90).toDouble())
                            val leftAngle = angleRad - 0.45
                            val rightAngle = angleRad + 0.45
                            moveTo(curPt.x, curPt.y)
                            lineTo((curPt.x + 45f * Math.cos(leftAngle)).toFloat(), (curPt.y + 45f * Math.sin(leftAngle)).toFloat())
                            lineTo((curPt.x + 45f * Math.cos(rightAngle)).toFloat(), (curPt.y + 45f * Math.sin(rightAngle)).toFloat())
                            close()
                        }
                        drawPath(conePath, brush = Brush.radialGradient(listOf(Color(0xFF4285F4).copy(alpha = 0.45f), Color.Transparent), center = curPt, radius = 50f))

                        // Google Blue Dot with crisp white halo
                        drawCircle(Color.White, radius = 10f, center = curPt)
                        drawCircle(Color(0xFF1A73E8), radius = 8f, center = curPt)
                    }
                }
            }
        }
    }
}
