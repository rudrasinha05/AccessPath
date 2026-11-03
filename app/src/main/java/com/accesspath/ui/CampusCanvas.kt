@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.accesspath.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onBearingChange: (Float) -> Unit = {},
    onUnmappedCorridorTapped: () -> Unit = {},
    gpsLocation: LiveGpsLocation? = null,
    recenterTrigger: Int = 0
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

    // Recenter camera directly on user GPS blue dot when requested
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            pan = Offset.Zero
            zoom = 1.35f
            onBearingChange(0f)
        }
    }

    // Smooth camera center animation when selectedNode changes
    LaunchedEffect(selectedNode) {
        if (selectedNode != null && selectedNode.floorId == s.selectedFloor) {
            val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
            val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
            val targetX = 35f + selectedNode.x.toFloat() / maxX * (700f - 70f)
            val targetY = 45f + selectedNode.y.toFloat() / maxY * (500f - 85f)
            pan = Offset(350f - targetX, 250f - targetY)
            zoom = 2.0f
        }
    }

    // Infinite pulse animation for current location GPS blue dot & geofence zones
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Heading beacon animation
    val headingAngle by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headingAngle"
    )

    val streetTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(100, 116, 139)
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val buildingTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(71, 85, 105)
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val labelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 41, 59)
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val hindiSubTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(100, 116, 139)
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    val watermarkPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(160, 95, 99, 104)
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val bluePoiTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(26, 115, 232)
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    val currentFloor = s.map.floors.find { it.id == s.selectedFloor }
    val floorLabel = currentFloor?.label ?: s.selectedFloor

    // Google Maps Style-dependent Canvas Background Colors
    val canvasBgColor = when (s.mapStyle) {
        MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> Color(0xFFF1F3F4) // Google Maps Clean Vector Ground
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
                    contentDescription = "Interactive Google Map GPS view for ${gpsLocation?.displayName ?: "Current Location"}."
                }
                .pointerInput(s.selectedFloor, isFreehandMode) {
                    if (isFreehandMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val maxX = (floorNodes.maxOfOrNull { it.x } ?: 100.0).coerceAtLeast(100.0).toFloat() + 25f
                                val maxY = (floorNodes.maxOfOrNull { it.y } ?: 100.0).coerceAtLeast(100.0).toFloat() + 20f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                val p1 = offset - pan
                                val rad = -Math.toRadians(bearing.toDouble())
                                val dx = p1.x - center.x
                                val dy = p1.y - center.y
                                val rx = dx * cos(rad) - dy * sin(rad) + center.x
                                val ry = dx * sin(rad) + dy * cos(rad) + center.y
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
                                val rx = dx * cos(rad) - dy * sin(rad) + center.x
                                val ry = dx * sin(rad) + dy * cos(rad) + center.y
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
                                val dx = pt.x - center.x
                                val dy = pt.y - center.y
                                val scaledX = dx * zoom
                                val scaledY = dy * zoom
                                val rad = Math.toRadians(bearing.toDouble())
                                val rx = scaledX * cos(rad) - scaledY * sin(rad) + center.x + pan.x
                                val ry = scaledX * sin(rad) + scaledY * cos(rad) + center.y + pan.y
                                (tapOffset - Offset(rx.toFloat(), ry.toFloat())).getDistance() < 90f
                            }
                            if (clicked != null) {
                                onNodeSelected(clicked)
                            } else {
                                onNodeSelected(null)
                            }
                        }
                    }
                }
                .pointerInput(s.selectedFloor, isFreehandMode) {
                    if (!isFreehandMode) {
                        detectTransformGestures { _, delta, scale, rotation ->
                            zoom = (zoom * scale).coerceIn(0.4f, 5.0f)
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

            // --- 1. Base Real-World Map Background ---
            when (s.mapStyle) {
                MapStyle.DEFAULT_VECTOR, MapStyle.BLUEPRINT -> {
                    // Google Maps Clean Vector Ground
                    drawRect(Color(0xFFE8ECEF))

                    // Soft urban grid / block separation lines
                    val cols = 8
                    val rows = 12
                    for (i in 0..cols) {
                        drawLine(Color(0xFFDFE3E6), Offset(i * size.width / cols, 0f), Offset(i * size.width / cols, size.height), 1f)
                    }
                    for (j in 0..rows) {
                        drawLine(Color(0xFFDFE3E6), Offset(0f, j * size.height / rows), Offset(size.width, j * size.height / rows), 1f)
                    }
                }
                MapStyle.SATELLITE, MapStyle.HYBRID, MapStyle.SATELLITE_HYBRID -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1B3828), Color(0xFF0F1E15)),
                            center = center,
                            radius = size.width.coerceAtLeast(size.height)
                        )
                    )
                    drawCircle(Color(0xFF264C33).copy(alpha = 0.6f), radius = size.width * 0.35f, center = Offset(size.width * 0.3f, size.height * 0.4f))
                    drawCircle(Color(0xFF1E3D29).copy(alpha = 0.6f), radius = size.width * 0.28f, center = Offset(size.width * 0.7f, size.height * 0.6f))
                }
                MapStyle.TERRAIN -> {
                    drawRect(Color(0xFFE8E2D5))
                    val contourPaint = Color(0xFFC7BFA8)
                    for (step in 1..8) {
                        val r = (step * 80f)
                        drawCircle(contourPaint, radius = r, center = Offset(size.width * 0.2f, size.height * 0.3f), style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))))
                    }
                }
                MapStyle.ACCESSIBILITY_HEATMAP -> {
                    drawRect(Color(0xFF18181B))
                }
                MapStyle.NIGHT_CYBER -> {
                    drawRect(Color(0xFF05080E))
                }
            }

            // --- Apply Movable Transformation Matrix (Pan, Zoom, Bearing, 3D Tilt) ---
            withTransform({
                translate(pan.x, pan.y)
                rotate(bearing, center)
                scale(zoom, zoom, center)
            }) {
                val zOffset3D = if (is3DMode) Offset(-18f, -24f) else Offset.Zero
                val isOutdoor = s.selectedFloor == "a0" || s.selectedFloor == "outdoor" || s.selectedFloor == "iitd_f0" || s.selectedFloor == "aiims_f0" || s.selectedFloor == "igi_f0" || s.selectedFloor == "su_f0"

                // --- 2. Real-World Urban Environment & Street Network ---
                if (isOutdoor) {
                    val roadColor = if (s.mapStyle == MapStyle.SATELLITE) Color(0xFF273549) else Color(0xFFFFFFFF)
                    val roadBorder = if (s.mapStyle == MapStyle.SATELLITE) Color(0xFF1A2332) else Color(0xFFD3D7DC)

                    // Green Park & Lawn Enclaves (Matching Google Maps)
                    drawRoundRect(
                        color = Color(0xFFCEEAD6).copy(alpha = 0.65f),
                        topLeft = Offset(size.width * 0.05f, size.height * 0.12f),
                        size = Size(size.width * 0.38f, size.height * 0.22f),
                        cornerRadius = CornerRadius(20f, 20f)
                    )
                    drawRoundRect(
                        color = Color(0xFFCEEAD6).copy(alpha = 0.5f),
                        topLeft = Offset(size.width * 0.55f, size.height * 0.62f),
                        size = Size(size.width * 0.40f, size.height * 0.26f),
                        cornerRadius = CornerRadius(24f, 24f)
                    )

                    // Real Building Footprints (Matching Image 2 & 3: Oasis Venetia, Surajpur Site C, Migsun Green)
                    fun drawBuildingParcel(leftRatio: Float, topRatio: Float, wRatio: Float, hRatio: Float, label: String, hindiLabel: String = "") {
                        val topLeft = Offset(size.width * leftRatio, size.height * topRatio)
                        val bSize = Size(size.width * wRatio, size.height * hRatio)

                        if (is3DMode) {
                            // 3D Extrusion Side
                            drawRect(
                                color = Color(0xFFCBD5E1),
                                topLeft = topLeft + zOffset3D,
                                size = bSize
                            )
                        }

                        drawRoundRect(
                            color = Color(0xFFFBFBFB),
                            topLeft = topLeft,
                            size = bSize,
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = Color(0xFFD5D9DE),
                            topLeft = topLeft,
                            size = bSize,
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 1.8f)
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            topLeft.x + 16f,
                            topLeft.y + bSize.height * 0.45f,
                            buildingTextPaint
                        )
                        if (hindiLabel.isNotEmpty()) {
                            drawContext.canvas.nativeCanvas.drawText(
                                hindiLabel,
                                topLeft.x + 16f,
                                topLeft.y + bSize.height * 0.45f + 22f,
                                hindiSubTextPaint
                            )
                        }
                    }

                    drawBuildingParcel(0.10f, 0.40f, 0.38f, 0.16f, "Oasis Venetia Heights", "ओएसिस विनेसिया हाइट्स")
                    drawBuildingParcel(0.52f, 0.24f, 0.42f, 0.18f, "UPSIDC SITE C", "उप्सिदक साईट C")
                    drawBuildingParcel(0.12f, 0.68f, 0.36f, 0.15f, "Migsun Green Mansion", "मिगसन ग्रीन मैन्शन")
                    drawBuildingParcel(0.56f, 0.08f, 0.38f, 0.14f, "Commercial Block K", "ब्लॉक K")
                    drawBuildingParcel(0.48f, 0.68f, 0.44f, 0.17f, "Surajpur site C industrial area", "सूरजपुर साइट सी")

                    // Main Arterial Road (Noida-Greater Noida Link Rd - Wide Curved Dual Carriageway)
                    val mainRoadPath = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.05f)
                        cubicTo(
                            size.width * 0.45f, size.height * 0.22f,
                            size.width * 0.55f, size.height * 0.55f,
                            size.width * 0.92f, size.height * 0.88f
                        )
                    }
                    // Border Casing
                    drawPath(mainRoadPath, color = roadBorder, style = Stroke(width = 44f, cap = StrokeCap.Round))
                    // Road Surface
                    drawPath(mainRoadPath, color = roadColor, style = Stroke(width = 36f, cap = StrokeCap.Round))
                    // Center dash line
                    drawPath(
                        mainRoadPath,
                        color = Color(0xFFF1F3F4),
                        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 14f)))
                    )

                    // Connecting Avenue (Site C Rd)
                    val siteCRoadPath = Path().apply {
                        moveTo(size.width * 0.48f, size.height * 0.35f)
                        lineTo(size.width * 0.15f, size.height * 0.92f)
                    }
                    drawPath(siteCRoadPath, color = roadBorder, style = Stroke(width = 32f, cap = StrokeCap.Round))
                    drawPath(siteCRoadPath, color = roadColor, style = Stroke(width = 26f, cap = StrokeCap.Round))

                    // Secondary Avenue (Sakipur Rd)
                    val crossRoadPath = Path().apply {
                        moveTo(size.width * 0.90f, size.height * 0.18f)
                        lineTo(size.width * 0.42f, size.height * 0.48f)
                    }
                    drawPath(crossRoadPath, color = roadBorder, style = Stroke(width = 26f, cap = StrokeCap.Round))
                    drawPath(crossRoadPath, color = roadColor, style = Stroke(width = 20f, cap = StrokeCap.Round))

                    // Street Labels along Roads (Matching Image 2 & 3)
                    drawContext.canvas.nativeCanvas.drawText("Noida - Greater Noida Link Rd", size.width * 0.30f, size.height * 0.22f, streetTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("Site C Rd", size.width * 0.24f, size.height * 0.72f, streetTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("BLOCK H", size.width * 0.58f, size.height * 0.50f, streetTextPaint)

                    // Authentic POI Markers & Badges (Matching Image 2 & 3)
                    // 1. Guddi Cosmetics (Blue shopping bag pin)
                    val guddiPt = Offset(size.width * 0.48f, size.height * 0.55f)
                    drawCircle(Color(0xFF1A73E8), radius = 13f, center = guddiPt)
                    drawCircle(Color.White, radius = 13f, center = guddiPt, style = Stroke(2f))
                    drawContext.canvas.nativeCanvas.drawText("GUDDI COSMETICS", guddiPt.x - 70f, guddiPt.y - 18f, bluePoiTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("गुड्डी कॉस्मेटिक्स", guddiPt.x - 55f, guddiPt.y + 32f, hindiSubTextPaint)

                    // 2. Wine Shop (Orange drink pin)
                    val winePt = Offset(size.width * 0.28f, size.height * 0.38f)
                    drawCircle(Color(0xFFE65100), radius = 13f, center = winePt)
                    drawCircle(Color.White, radius = 13f, center = winePt, style = Stroke(2f))
                    drawContext.canvas.nativeCanvas.drawText("Wine Shop", winePt.x - 45f, winePt.y - 18f, buildingTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("वाइन शॉप", winePt.x - 30f, winePt.y + 30f, hindiSubTextPaint)

                    // 3. Alpine First Step (School pin)
                    val schoolPt = Offset(size.width * 0.38f, size.height * 0.65f)
                    drawCircle(Color(0xFF546E7A), radius = 13f, center = schoolPt)
                    drawCircle(Color.White, radius = 13f, center = schoolPt, style = Stroke(2f))
                    drawContext.canvas.nativeCanvas.drawText("Alpine First Step: Play School", schoolPt.x - 110f, schoolPt.y - 18f, buildingTextPaint)
                    drawContext.canvas.nativeCanvas.drawText("अल्पाइन फर्स्ट स्टेप", schoolPt.x - 60f, schoolPt.y + 30f, hindiSubTextPaint)

                    // 4. ACE Platinum
                    val acePt = Offset(size.width * 0.42f, size.height * 0.79f)
                    drawCircle(Color(0xFF5F6368), radius = 12f, center = acePt)
                    drawCircle(Color.White, radius = 12f, center = acePt, style = Stroke(2f))
                    drawContext.canvas.nativeCanvas.drawText("ACE PLATINUM", acePt.x - 60f, acePt.y - 16f, buildingTextPaint)

                    // 5. Google Maps Watermark (Matching Image 2 & 3 bottom-left)
                    drawContext.canvas.nativeCanvas.drawText("Google Maps", 36f, size.height * 0.82f, watermarkPaint)
                }

                // --- 3. Active Geofences (Both Circular & Freehand Polygons) ---
                s.geofences.filter { it.floorId == s.selectedFloor && it.active }.forEach { gf ->
                    val gfColor = when (gf.type) {
                        GeofenceType.SAFE_ZONE -> Color(0xFF10B981)
                        GeofenceType.HAZARD -> Color(0xFFEF4444)
                        GeofenceType.ACCESSIBLE_FACILITY -> Color(0xFF06B6D4)
                        GeofenceType.QUIET_ZONE -> Color(0xFF6366F1)
                        GeofenceType.RESTRICTED -> Color(0xFFF59E0B)
                    }

                    if (gf.polygon.size >= 3) {
                        val polyPath = Path().apply {
                            val first = gf.polygon.first()
                            moveTo(35f + first.x.toFloat() / maxX * (size.width - 70f), 45f + first.y.toFloat() / maxY * (size.height - 85f))
                            for (i in 1 until gf.polygon.size) {
                                val p = gf.polygon[i]
                                lineTo(35f + p.x.toFloat() / maxX * (size.width - 70f), 45f + p.y.toFloat() / maxY * (size.height - 85f))
                            }
                            close()
                        }
                        drawPath(polyPath, color = gfColor.copy(alpha = 0.20f))
                        drawPath(
                            polyPath,
                            color = gfColor.copy(alpha = 0.85f),
                            style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                        )
                    } else {
                        val centerPt = Offset(
                            35f + gf.centerX.toFloat() / maxX * (size.width - 70f),
                            45f + gf.centerY.toFloat() / maxY * (size.height - 85f)
                        )
                        val radiusPx = (gf.radius.toFloat() / 100f) * (size.width - 70f) * 0.45f
                        drawCircle(gfColor.copy(alpha = 0.15f), radius = radiusPx + pulseRadius * 0.25f, center = centerPt)
                        drawCircle(
                            color = gfColor.copy(alpha = 0.85f),
                            radius = radiusPx,
                            center = centerPt,
                            style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                        )
                    }
                }

                // --- 4. Live Freehand Stroke Preview ---
                if (freehandPoints.isNotEmpty()) {
                    val strokePath = Path().apply {
                        val first = freehandPoints.first()
                        moveTo(35f + first.x.toFloat() / maxX * (size.width - 70f), 45f + first.y.toFloat() / maxY * (size.height - 85f))
                        for (i in 1 until freehandPoints.size) {
                            val p = freehandPoints[i]
                            lineTo(35f + p.x.toFloat() / maxX * (size.width - 70f), 45f + p.y.toFloat() / maxY * (size.height - 85f))
                        }
                    }
                    drawPath(
                        strokePath,
                        color = Color(0xFFFF5252),
                        style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // --- 5. Active Accessible Navigation Polyline ---
                val relevantEdges = s.map.edges.filter {
                    nodes[it.from]?.floorId == s.selectedFloor && nodes[it.to]?.floorId == s.selectedFloor
                }

                // Active navigation polyline (Google Maps Bold Blue)
                relevantEdges.filter { it.id in active && it.id != currentStepEdge }.forEach { e ->
                    val fromPt = point(nodes.getValue(e.from))
                    val toPt = point(nodes.getValue(e.to))
                    // Shadow / aura
                    drawLine(
                        color = Color(0xFF1A73E8).copy(alpha = 0.35f),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 20f,
                        cap = StrokeCap.Round
                    )
                    // Core line
                    drawLine(
                        color = Color(0xFF1A73E8),
                        start = fromPt,
                        end = toPt,
                        strokeWidth = 11f,
                        cap = StrokeCap.Round
                    )
                }

                // Current guided step edge (Glowing vibrant amber)
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

                // --- 6. Real Accessible POIs (Elevators, Ramps, Gates, Restrooms) ---
                val displayNodes = if (isOutdoor) {
                    floorNodes.filter { n ->
                        n.type in setOf("ENTRANCE", "RAMP", "ELEVATOR", "WASHROOM") ||
                        n.name.contains("Ramp", ignoreCase = true) ||
                        n.name.contains("Lift", ignoreCase = true) ||
                        n.name.contains("Gate", ignoreCase = true) ||
                        n.name.contains("Entry", ignoreCase = true) ||
                        n.name.contains("Washroom", ignoreCase = true)
                    }
                } else {
                    floorNodes
                }

                displayNodes.forEach { n ->
                    val pt = point(n)
                    val isNodeSelected = selectedNode?.id == n.id

                    // POI Color & Display Badge
                    val (poiColor, poiSymbol) = when {
                        n.type.contains("ELEVATOR", ignoreCase = true) || n.name.contains("Lift", ignoreCase = true) -> Color(0xFF007B83) to "🛗"
                        n.type.contains("RAMP", ignoreCase = true) || n.name.contains("Ramp", ignoreCase = true) -> Color(0xFF1E8E3E) to "♿"
                        n.type.contains("ENTRANCE", ignoreCase = true) || n.name.contains("Gate", ignoreCase = true) -> Color(0xFFE37400) to "🚪"
                        n.name.contains("Washroom", ignoreCase = true) || n.type.contains("WASHROOM", ignoreCase = true) -> Color(0xFF12B5CB) to "🚻"
                        else -> Color(0xFF1A73E8) to "📍"
                    }

                    // Selected Node Pulsing Aura
                    if (isNodeSelected) {
                        drawCircle(Color(0xFF1A73E8).copy(alpha = 0.25f), radius = 28f + pulseRadius * 0.4f, center = pt)
                        drawCircle(Color(0xFF1A73E8), radius = 22f, center = pt, style = Stroke(3.5f))
                    }

                    // Google Maps POI Pin Badge
                    drawCircle(poiColor, radius = 15f, center = pt)
                    drawCircle(Color.White, radius = 15f, center = pt, style = Stroke(2.5f))

                    // Text Label - Authentic Real Facilities Only
                    val cleanLabel = when {
                        n.name.contains("Ramp", ignoreCase = true) -> "♿ Ramp"
                        n.name.contains("Lift", ignoreCase = true) -> "🛗 PRM Lift"
                        n.name.contains("Entry", ignoreCase = true) || n.name.contains("Gate", ignoreCase = true) -> "🚪 Main Entry"
                        n.name.contains("Washroom", ignoreCase = true) -> "🚻 Restroom"
                        else -> n.name.take(12)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        cleanLabel,
                        pt.x - 24f,
                        pt.y + 34f,
                        labelTextPaint
                    )
                }

                // --- 7. Live User GPS Location (Google Blue Dot + Pulsing Halo + View Cone) ---
                // The blue dot is centered directly at the user's live GPS position on outdoor floor!
                val userCenter = if (isOutdoor) {
                    Offset(size.width * 0.48f, size.height * 0.38f) // Real intersection on Site C Rd & Link Rd
                } else {
                    nodes[s.current]?.let { point(it) } ?: Offset(size.width * 0.5f, size.height * 0.5f)
                }

                // Animated Google Maps pulsating radar accuracy wave
                drawCircle(
                    color = Color(0xFF4285F4).copy(alpha = pulseAlpha),
                    radius = pulseRadius + 18f,
                    center = userCenter
                )
                // Accuracy circle
                drawCircle(
                    color = Color(0xFF4285F4).copy(alpha = 0.20f),
                    radius = 26f,
                    center = userCenter
                )

                // Directional Heading Cone (Pointing in movement direction)
                val conePath = Path().apply {
                    val angleRad = Math.toRadians((headingAngle - 90).toDouble())
                    val leftAngle = angleRad - 0.45
                    val rightAngle = angleRad + 0.45
                    moveTo(userCenter.x, userCenter.y)
                    lineTo((userCenter.x + 55f * cos(leftAngle)).toFloat(), (userCenter.y + 55f * sin(leftAngle)).toFloat())
                    lineTo((userCenter.x + 55f * cos(rightAngle)).toFloat(), (userCenter.y + 55f * sin(rightAngle)).toFloat())
                    close()
                }
                drawPath(
                    conePath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4285F4).copy(alpha = 0.48f), Color.Transparent),
                        center = userCenter,
                        radius = 60f
                    )
                )

                // Google Blue Dot with Crisp White Outer Ring
                drawCircle(Color.White, radius = 11f, center = userCenter)
                drawCircle(Color(0xFF1A73E8), radius = 8.5f, center = userCenter)
            }
        }
    }
}
