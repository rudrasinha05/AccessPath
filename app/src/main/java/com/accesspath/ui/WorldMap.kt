package com.accesspath.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.accesspath.BuildConfig
import com.accesspath.domain.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.hypot

/** Where the map camera should go. A new [nonce] re-runs the move even if the target is unchanged. */
data class CameraRequest(val nonce: Int, val center: LatLng? = null, val zoom: Double = 19.5, val bounds: List<LatLng> = emptyList())

/** Everything the campus overlay draws, prepared outside the draw loop. */
private data class OverlayData(
    val buildings: List<Pair<String, List<LatLng>>>,
    val segments: List<Triple<LatLng, LatLng, Int>>, // style: 0 normal, 1 unsuitable, 2 closed
    val route: List<List<LatLng>>,
    val places: List<Pair<Node, LatLng>>,
    val current: LatLng?,
    val destination: LatLng?,
    val currentId: String,
    val destinationId: String,
    val selectedId: String?,
    val routeNodeIds: Set<String>
)

private class CampusOverlay(context: Context, private val onPlaceTap: (Node) -> Unit, private val onEmptyTap: () -> Unit) : Overlay() {
    var data: OverlayData? = null
    var colors: WorldMapColors = WorldMapColors.Light

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val p1 = Point()
    private val p2 = Point()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dash = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12f); textAlign = Paint.Align.CENTER }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12f); textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeWidth = dp(3f) }
    private val path = Path()
    private val rect = RectF()

    private fun px(mapView: MapView, l: LatLng, out: Point): Point = mapView.projection.toPixels(GeoPoint(l.lat, l.lng), out)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val d = data ?: return
        val zoom = mapView.zoomLevelDouble

        // Building footprints: soft rounded boxes around each building's places on this floor.
        d.buildings.forEach { (name, pts) ->
            if (pts.isEmpty()) return@forEach
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            pts.forEach { px(mapView, it, p1); minX = minOf(minX, p1.x.toFloat()); minY = minOf(minY, p1.y.toFloat()); maxX = maxOf(maxX, p1.x.toFloat()); maxY = maxOf(maxY, p1.y.toFloat()) }
            val pad = dp(18f)
            rect.set(minX - pad, minY - pad, maxX + pad, maxY + pad)
            fill.color = colors.building; canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
            stroke.color = colors.buildingEdge; stroke.strokeWidth = dp(1.5f); stroke.pathEffect = null
            canvas.drawRoundRect(rect, dp(10f), dp(10f), stroke)
            if (zoom >= 17.5) drawLabel(canvas, name, rect.centerX(), rect.top + dp(14f), colors.buildingLabel, bold = true)
        }

        // Paths: casing then fill, like a road on Google Maps.
        d.segments.forEach { (a, b, style) ->
            px(mapView, a, p1); px(mapView, b, p2)
            when (style) {
                2 -> { stroke.color = colors.closed; stroke.strokeWidth = dp(4f); stroke.pathEffect = dash }
                1 -> { stroke.color = colors.unsuitable; stroke.strokeWidth = dp(2.5f); stroke.pathEffect = dash }
                else -> {
                    stroke.pathEffect = null; stroke.color = colors.pathCasing; stroke.strokeWidth = dp(7f)
                    canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), stroke)
                    stroke.color = colors.path; stroke.strokeWidth = dp(4.5f)
                }
            }
            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), stroke)
        }
        stroke.pathEffect = null

        // Route: white casing + blue line.
        d.route.forEach { line ->
            if (line.size < 2) return@forEach
            path.reset()
            line.forEachIndexed { i, l -> px(mapView, l, p1); if (i == 0) path.moveTo(p1.x.toFloat(), p1.y.toFloat()) else path.lineTo(p1.x.toFloat(), p1.y.toFloat()) }
            stroke.color = colors.routeCasing; stroke.strokeWidth = dp(11f); canvas.drawPath(path, stroke)
            stroke.color = colors.route; stroke.strokeWidth = dp(7f); canvas.drawPath(path, stroke)
        }

        // Places.
        d.places.forEach { (n, l) ->
            if (n.id == d.currentId || n.id == d.destinationId) return@forEach
            px(mapView, l, p1)
            val important = !n.type.equals("LANDMARK", true)
            val onRoute = n.id in d.routeNodeIds
            if (!important && !onRoute && zoom < 19.0) return@forEach
            val r = if (important) dp(7f) else dp(4f)
            fill.color = colors.white; canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), r + dp(2f), fill)
            fill.color = if (onRoute) colors.route else colors.placeColor(n.type); canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), r, fill)
            if (n.id == d.selectedId) { stroke.color = colors.route; stroke.strokeWidth = dp(3f); canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), r + dp(5f), stroke) }
            if ((important && zoom >= 18.5) || zoom >= 20.0 || n.id == d.selectedId) {
                drawLabel(canvas, n.name, p1.x.toFloat(), p1.y + r + dp(14f), colors.label, bold = n.id == d.selectedId)
            }
        }

        // Destination pin.
        d.destination?.let { l ->
            px(mapView, l, p1)
            val x = p1.x.toFloat(); val y = p1.y.toFloat()
            path.reset()
            path.moveTo(x, y)
            path.cubicTo(x - dp(4f), y - dp(10f), x - dp(12f), y - dp(14f), x - dp(12f), y - dp(24f))
            path.arcTo(RectF(x - dp(12f), y - dp(36f), x + dp(12f), y - dp(12f)), 180f, 180f)
            path.cubicTo(x + dp(12f), y - dp(14f), x + dp(4f), y - dp(10f), x, y)
            fill.color = colors.destination; canvas.drawPath(path, fill)
            fill.color = colors.destinationDot; canvas.drawCircle(x, y - dp(24f), dp(4.5f), fill)
            drawLabel(canvas, d.places.firstOrNull { it.first.id == d.destinationId }?.first?.name ?: "", x, y + dp(14f), colors.label, bold = true)
        }

        // You: blue dot with halo.
        d.current?.let { l ->
            px(mapView, l, p1)
            fill.color = colors.youHalo; canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), dp(20f), fill)
            fill.color = colors.white; canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), dp(10f), fill)
            fill.color = colors.you; canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), dp(7.5f), fill)
        }
    }

    private fun drawLabel(canvas: Canvas, s: String, x: Float, y: Float, color: Int, bold: Boolean) {
        if (s.isBlank()) return
        val label = if (s.length > 26) s.take(25) + "…" else s
        text.isFakeBoldText = bold; halo.isFakeBoldText = bold
        halo.color = colors.white; canvas.drawText(label, x, y, halo)
        text.color = color; canvas.drawText(label, x, y, text)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val d = data ?: return false
        val hit = d.places.minByOrNull { (_, l) -> px(mapView, l, p1); hypot(p1.x - e.x, p1.y - e.y) }
        if (hit != null) {
            px(mapView, hit.second, p1)
            if (hypot(p1.x - e.x, p1.y - e.y) <= dp(28f)) { onPlaceTap(hit.first); return true }
        }
        onEmptyTap()
        return false
    }
}

data class WorldMapColors(
    val route: Int, val routeCasing: Int, val path: Int, val pathCasing: Int, val closed: Int, val unsuitable: Int,
    val building: Int, val buildingEdge: Int, val buildingLabel: Int, val label: Int, val white: Int,
    val you: Int, val youHalo: Int, val destination: Int, val destinationDot: Int,
    val washroom: Int, val lift: Int, val entrance: Int, val room: Int, val landmark: Int
) {
    fun placeColor(type: String) = when (type.uppercase()) {
        "WASHROOM" -> washroom
        "ELEVATOR" -> lift
        "ENTRANCE", "EXIT" -> entrance
        "LANDMARK" -> landmark
        else -> room
    }

    companion object {
        private fun c(hex: Long) = Color(hex).toArgb()
        val Light = WorldMapColors(
            route = c(0xFF1A73E8), routeCasing = c(0xFFFFFFFF), path = c(0xFFFFFFFF), pathCasing = c(0xFFC4CCD4),
            closed = c(0xFFD93025), unsuitable = c(0xFF9AA0A6), building = c(0xE6F1EFEA), buildingEdge = c(0xFFD2CEC4),
            buildingLabel = c(0xFF5F6368), label = c(0xFF202124), white = c(0xFFFFFFFF),
            you = c(0xFF1A73E8), youHalo = c(0x331A73E8), destination = c(0xFFEA4335), destinationDot = c(0xFFB31412),
            washroom = c(0xFF00897B), lift = c(0xFF7B1FA2), entrance = c(0xFF188038), room = c(0xFFE37400), landmark = c(0xFF80868B)
        )
    }
}

/** Real-world map (OpenStreetMap) with the campus drawn on top for one floor level. */
@Composable
fun WorldMap(
    state: AppSnapshot,
    route: RouteResult.Found?,
    exclusions: Map<String, ExclusionReason>,
    selectedPlaceId: String?,
    camera: CameraRequest,
    centerOffsetPx: Int,
    description: String,
    modifier: Modifier = Modifier,
    onPlaceTap: (Node) -> Unit,
    onEmptyTap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestPlaceTap by rememberUpdatedState(onPlaceTap)
    val latestEmptyTap by rememberUpdatedState(onEmptyTap)

    val overlay = remember { CampusOverlay(context, { latestPlaceTap(it) }, { latestEmptyTap() }) }
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = "AccessPath/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 15.0
            maxZoomLevel = 21.5
            isTilesScaledToDpi = true
            isVerticalMapRepetitionEnabled = false
            isHorizontalMapRepetitionEnabled = false
            overlays.add(overlay)
            controller.setZoom(18.5)
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onDetach() }
    }

    // Prepare drawing data for the selected floor level (all buildings at the same level).
    val data = remember(state.map, state.selectedFloor, state.current, state.destination, route, exclusions, selectedPlaceId) {
        val map = state.map
        val floors = map.floors.associateBy { it.id }
        val selected = floors[state.selectedFloor]
        val level = selected?.ordinal ?: 0
        val visibleFloors = map.floors.filter { f ->
            if (f.buildingId == selected?.buildingId) f.id == selected.id else f.ordinal == level
        }.map { it.id }.toSet()
        val pos = map.nodes.mapNotNull { n -> Geo.position(map, n)?.let { n.id to it } }.toMap()
        val visible = map.nodes.filter { it.floorId in visibleFloors && it.id in pos }
        val visibleIds = visible.map { it.id }.toSet()
        val segments = map.edges.filter { it.from in visibleIds && it.to in visibleIds }
            .groupBy { listOf(it.from, it.to).sorted() }.values.map { pair ->
                val reasons = pair.mapNotNull { exclusions[it.id] }
                val style = when { reasons.any { it.isClosure } -> 2; reasons.isNotEmpty() -> 1; else -> 0 }
                Triple(pos.getValue(pair[0].from), pos.getValue(pair[0].to), style)
            }
        // Route split into runs that stay on visible floors.
        val routeLines = mutableListOf<MutableList<LatLng>>()
        route?.let { r ->
            var current: MutableList<LatLng>? = null
            r.edges.forEach { e ->
                if (e.from in visibleIds && e.to in visibleIds) {
                    val a = pos.getValue(e.from); val b = pos.getValue(e.to)
                    if (current == null || current!!.last() != a) { current = mutableListOf(a); routeLines += current!! }
                    current!!.add(b)
                } else current = null
            }
        }
        val buildings = map.buildings.mapNotNull { b ->
            val pts = visible.filter { floors[it.floorId]?.buildingId == b.id }.map { pos.getValue(it.id) }
            if (pts.isEmpty()) null else b.name to pts
        }
        OverlayData(
            buildings = buildings, segments = segments, route = routeLines,
            places = visible.map { it to pos.getValue(it.id) },
            current = state.current.takeIf { it in visibleIds }?.let(pos::get),
            destination = state.destination.takeIf { it in visibleIds && it != state.current }?.let(pos::get),
            currentId = state.current, destinationId = state.destination, selectedId = selectedPlaceId,
            routeNodeIds = route?.nodes?.map { it.id }?.toSet().orEmpty()
        )
    }

    LaunchedEffect(camera.nonce) {
        val pts = camera.bounds
        if (pts.size >= 2) {
            val box = BoundingBox.fromGeoPoints(pts.map { GeoPoint(it.lat, it.lng) })
            // Padding keeps the route clear of the search bar and the bottom sheet.
            mapView.post { mapView.zoomToBoundingBox(box.increaseByScale(1.6f), true, 64, 20.5, 600L) }
        } else camera.center?.let { c ->
            mapView.controller.animateTo(GeoPoint(c.lat, c.lng), camera.zoom, 600L)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.semantics { contentDescription = description },
        update = { view ->
            view.setMapCenterOffset(0, centerOffsetPx)
            overlay.data = data
            view.invalidate()
        }
    )
}
