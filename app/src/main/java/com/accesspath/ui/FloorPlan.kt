package com.accesspath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accesspath.domain.*
import com.accesspath.ui.theme.LocalMapColors
import kotlin.math.max
import kotlin.math.min

/** Maps floor coordinates (metres) to screen pixels, including the person's zoom and pan. */
private class Projection(
    nodes: List<Node>, private val size: IntSize, private val padding: Float,
    private val scale: Float, private val pan: Offset
) {
    private val minX = nodes.minOfOrNull { it.x } ?: 0.0
    private val minY = nodes.minOfOrNull { it.y } ?: 0.0
    private val spanX = max((nodes.maxOfOrNull { it.x } ?: 1.0) - minX, 1.0)
    private val spanY = max((nodes.maxOfOrNull { it.y } ?: 1.0) - minY, 1.0)
    private val fit = min((size.width - 2 * padding) / spanX, (size.height - 2 * padding) / spanY).toFloat().coerceAtLeast(0.01f)
    private val offsetX = (size.width - spanX.toFloat() * fit) / 2f
    private val offsetY = (size.height - spanY.toFloat() * fit) / 2f
    private val center = Offset(size.width / 2f, size.height / 2f)

    fun point(n: Node): Offset {
        val base = Offset(offsetX + ((n.x - minX) * fit).toFloat(), offsetY + ((n.y - minY) * fit).toFloat())
        return (base - center) * scale + center + pan
    }
}

@Composable
fun FloorPlan(
    state: AppSnapshot,
    floorId: String,
    route: RouteResult.Found?,
    exclusions: Map<String, ExclusionReason>,
    description: String,
    modifier: Modifier = Modifier,
    onPlaceTap: (Node) -> Unit
) {
    val colors = LocalMapColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nodes = remember(state.map, floorId) { state.map.nodes.filter { it.floorId == floorId } }
    val ids = remember(nodes) { nodes.map { it.id }.toSet() }
    // One line per physical segment; a segment is "closed" or "unsuitable" if either direction is.
    val segments = remember(state.map, ids) {
        state.map.edges.filter { it.from in ids && it.to in ids }
            .groupBy { listOf(it.from, it.to).sorted() }.values.toList()
    }
    var scale by remember(floorId) { mutableFloatStateOf(1f) }
    var pan by remember(floorId) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(density) { 36.dp.toPx() }
    val tapRadius = with(density) { 28.dp.toPx() }
    val labelStyle = TextStyle(color = colors.label, fontSize = 12.sp)
    val strongLabel = labelStyle.copy(fontWeight = FontWeight.Bold)

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .semantics { contentDescription = description }
            .pointerInput(floorId) {
                // Two fingers zoom; one finger pans only when zoomed in, so the page can still scroll past the map.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            pan = if (scale == 1f) Offset.Zero else pan + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(nodes, scale, pan, size) {
                detectTapGestures { tap ->
                    val p = Projection(nodes, size, padding, scale, pan)
                    nodes.minByOrNull { (p.point(it) - tap).getDistance() }
                        ?.takeIf { (p.point(it) - tap).getDistance() <= tapRadius }
                        ?.let(onPlaceTap)
                }
            }
    ) {
        drawRect(colors.background)
        if (nodes.isEmpty()) return@Canvas
        val p = Projection(nodes, IntSize(this.size.width.toInt(), this.size.height.toInt()), padding, scale, pan)
        val byId = nodes.associateBy { it.id }
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

        segments.forEach { pair ->
            val e = pair.first()
            val a = p.point(byId.getValue(e.from)); val b = p.point(byId.getValue(e.to))
            val reasons = pair.mapNotNull { exclusions[it.id] }
            when {
                reasons.any { it.isClosure } -> drawLine(colors.closed, a, b, 4.dp.toPx(), StrokeCap.Round, dash)
                reasons.isNotEmpty() -> drawLine(colors.unsuitable, a, b, 2.dp.toPx(), StrokeCap.Round, dash)
                else -> drawLine(colors.path, a, b, 3.dp.toPx(), StrokeCap.Round)
            }
        }
        // Route on top so it stays visible over other paths.
        route?.edges?.filter { it.from in ids && it.to in ids }?.forEach { e ->
            drawLine(colors.route, p.point(byId.getValue(e.from)), p.point(byId.getValue(e.to)), 7.dp.toPx(), StrokeCap.Round)
        }

        val routeNodes = route?.nodes?.map { it.id }?.toSet().orEmpty()
        nodes.forEach { n ->
            val c = p.point(n)
            when (n.id) {
                state.current -> {
                    drawCircle(colors.background, 11.dp.toPx(), c)
                    drawCircle(colors.you, 9.dp.toPx(), c)
                    drawCircle(colors.background, 3.dp.toPx(), c)
                }
                state.destination -> {
                    drawCircle(colors.background, 11.dp.toPx(), c)
                    drawCircle(colors.destination, 9.dp.toPx(), c)
                    drawCircle(colors.background, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
                else -> if (n.isPlace) {
                    val r = if (n.id in routeNodes) 5.dp.toPx() else 4.dp.toPx()
                    drawCircle(if (n.id in routeNodes) colors.route else colors.place, r, c)
                }
            }
        }

        // Labels: always for important places; for plain corridor points only when zoomed in.
        nodes.forEach { n ->
            val important = n.id == state.current || n.id == state.destination || n.isPlace
            if (!important) return@forEach
            val c = p.point(n)
            drawLabel(
                measurer.measure(n.name, if (n.id == state.current || n.id == state.destination) strongLabel else labelStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = 120.dp.roundToPx())),
                c, 12.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawLabel(layout: androidx.compose.ui.text.TextLayoutResult, at: Offset, gap: Float) {
    val x = (at.x - layout.size.width / 2f).coerceIn(2f, max(2f, size.width - layout.size.width - 2f))
    val below = at.y + gap
    val y = if (below + layout.size.height > size.height) at.y - gap - layout.size.height else below
    drawText(layout, topLeft = Offset(x, y))
}
