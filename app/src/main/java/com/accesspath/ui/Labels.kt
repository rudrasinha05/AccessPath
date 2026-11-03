package com.accesspath.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.accesspath.R
import com.accesspath.domain.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Formats metres with at most one decimal, in the current locale. */
fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }.format(value)

fun formatTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))

@Composable fun metres(value: Double) = stringResource(R.string.metres, formatNumber(value))

@StringRes fun Mobility.label() = when (this) {
    Mobility.STANDARD -> R.string.mobility_standard
    Mobility.AVOID_STAIRS -> R.string.mobility_avoid_stairs
    Mobility.WHEELCHAIR -> R.string.mobility_wheelchair
}

@StringRes fun Mobility.description() = when (this) {
    Mobility.STANDARD -> R.string.mobility_standard_desc
    Mobility.AVOID_STAIRS -> R.string.mobility_avoid_stairs_desc
    Mobility.WHEELCHAIR -> R.string.mobility_wheelchair_desc
}

@StringRes fun Availability.label() = when (this) {
    Availability.AVAILABLE -> R.string.availability_available
    Availability.UNAVAILABLE -> R.string.availability_unavailable
    Availability.UNKNOWN -> R.string.availability_unknown
}

@StringRes fun ReportStatus.label() = when (this) {
    ReportStatus.PENDING -> R.string.status_pending
    ReportStatus.VERIFIED -> R.string.status_verified
    ReportStatus.REJECTED -> R.string.status_rejected
    ReportStatus.RESOLVED -> R.string.status_resolved
}

@StringRes fun BarrierCategory.label() = when (this) {
    BarrierCategory.BLOCKED_PATH -> R.string.cat_blocked
    BarrierCategory.LIFT_NOT_WORKING -> R.string.cat_lift
    BarrierCategory.DAMAGED_RAMP -> R.string.cat_ramp
    BarrierCategory.TOO_NARROW -> R.string.cat_narrow
    BarrierCategory.STEP_OR_THRESHOLD -> R.string.cat_step
    BarrierCategory.OTHER -> R.string.cat_other
}

@StringRes fun NearbyKind.label() = when (this) {
    NearbyKind.WASHROOM -> R.string.nearby_washroom
    NearbyKind.LIFT -> R.string.nearby_lift
    NearbyKind.EXIT -> R.string.nearby_exit
}

@StringRes fun ExclusionReason.closureLabel(): Int? = when (this) {
    ExclusionReason.BLOCKED_BY_SIMULATION -> R.string.closure_blocked
    ExclusionReason.PENDING_REPORT -> R.string.closure_pending
    ExclusionReason.VERIFIED_REPORT -> R.string.closure_verified
    ExclusionReason.FACILITY_UNAVAILABLE -> R.string.closure_facility_down
    ExclusionReason.FACILITY_STATUS_UNKNOWN -> R.string.closure_facility_unknown
    else -> null
}

/** Plain-language instruction for one step, used on screen and for read-aloud. */
fun RouteStep.text(context: Context): String {
    val place = to.name
    return when (type) {
        Segment.CORRIDOR -> context.getString(R.string.step_corridor, place)
        Segment.OUTDOOR -> context.getString(R.string.step_outdoor, place)
        Segment.DOOR -> context.getString(R.string.step_door, place)
        Segment.RAMP -> context.getString(R.string.step_ramp, place)
        Segment.STAIRS -> context.getString(R.string.step_stairs, toFloorLabel ?: place)
        Segment.ELEVATOR -> context.getString(R.string.step_lift, facilityName ?: context.getString(R.string.the_lift), toFloorLabel ?: place)
    }
}

fun AppSnapshot.segmentLabel(context: Context, edgeId: String): String {
    val e = edge(edgeId) ?: return edgeId
    return context.getString(R.string.segment_between, node(e.from)?.name ?: e.from, node(e.to)?.name ?: e.to)
}

fun AppSnapshot.placeLabel(context: Context, node: Node): String {
    val floor = floor(node.floorId)
    val building = floor?.let { f -> map.buildings.firstOrNull { it.id == f.buildingId } }
    val where = listOfNotNull(building?.name, floor?.label).joinToString(", ")
    return if (where.isEmpty()) node.name else context.getString(R.string.place_floor, node.name, where)
}

fun UiMessage.text(context: Context): String = when (this) {
    is UiMessage.Text -> context.getString(id, *args.toTypedArray())
    is UiMessage.Failure -> when (code) {
        ErrorCode.NOT_LOADED -> context.getString(R.string.error_not_loaded)
        ErrorCode.STALE_DATA, ErrorCode.CONCURRENT_UPDATE -> context.getString(R.string.error_stale)
        ErrorCode.INVALID_PROFILE -> context.getString(R.string.error_invalid_profile)
        ErrorCode.UNKNOWN_LOCATION -> context.getString(R.string.error_unknown_location)
        ErrorCode.UNKNOWN_SEGMENT -> context.getString(R.string.error_unknown_segment)
        ErrorCode.DESCRIPTION_LENGTH -> context.getString(R.string.error_description)
        ErrorCode.REASON_LENGTH -> context.getString(R.string.error_reason)
        ErrorCode.REPORT_NOT_FOUND -> context.getString(R.string.error_report_not_found)
        ErrorCode.INVALID_TRANSITION -> context.getString(R.string.error_transition)
        ErrorCode.FACILITY_NOT_FOUND -> context.getString(R.string.error_facility_not_found)
        ErrorCode.RESET_ONLY_DEMO -> context.getString(R.string.error_demo_only)
        ErrorCode.MAP_TOO_LARGE -> context.getString(R.string.error_map_too_large)
        ErrorCode.MAP_INVALID -> context.getString(R.string.error_map_invalid, detail ?: "")
        ErrorCode.IMPORT_ORPHANS_REPORTS -> context.getString(R.string.error_import_orphans)
        null -> context.getString(R.string.error_generic)
    }
}
