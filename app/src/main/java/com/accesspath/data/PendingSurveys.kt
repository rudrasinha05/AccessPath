package com.accesspath.data

import android.content.Context
import com.accesspath.domain.MapJson
import com.accesspath.domain.Survey
import com.accesspath.domain.isPlace
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Where a mapped area is in review. Only APPROVED areas are added to the map that people see. */
enum class ReviewStatus { PENDING, APPROVED, REJECTED }

/** A mapped area waiting for an admin, made on this phone or downloaded for a reviewer. */
@Serializable
data class PendingSurvey(
    val survey: Survey,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val reason: String = "",
    /** False while only this phone has it (not signed in, or offline). */
    val uploaded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** True for areas this phone recorded; false for ones a reviewer downloaded to check. */
    val mine: Boolean = true
) {
    val id get() = survey.building.id
    val name get() = survey.building.name
    val placeCount get() = survey.nodes.count { it.isPlace }
}

/** Mapped areas made on this phone, with their review status. Nothing here is shown on the map until approved. */
class PendingSurveys(context: Context) {
    private val prefs = context.getSharedPreferences("accesspath_pending_surveys", Context.MODE_PRIVATE)
    private val list = ListSerializer(PendingSurvey.serializer())

    fun all(): List<PendingSurvey> = prefs.getString(KEY, null)
        ?.let { runCatching { MapJson.decodeFromString(list, it) }.getOrNull() }.orEmpty()

    private fun save(v: List<PendingSurvey>) = prefs.edit().putString(KEY, MapJson.encodeToString(list, v)).apply()

    fun add(p: PendingSurvey) = save(listOf(p) + all().filterNot { it.id == p.id })
    fun update(id: String, change: (PendingSurvey) -> PendingSurvey) = save(all().map { if (it.id == id) change(it) else it })
    fun remove(id: String) = save(all().filterNot { it.id == id })

    private companion object { const val KEY = "surveys" }
}
