package com.accesspath.data

import android.content.Context

/** What the person has added to AccessPath from this phone, for the points and level on the Contribute tab. */
class Contributions(context: Context) {
    enum class Kind(val points: Int) { OUTDOOR_BARRIER(10), CAMPUS_REPORT(10), MAPPED_AREA(50) }

    private val prefs = context.getSharedPreferences("accesspath_contributions", Context.MODE_PRIVATE)

    fun add(kind: Kind) = prefs.edit().putInt(kind.name, count(kind) + 1).apply()
    fun count(kind: Kind) = prefs.getInt(kind.name, 0)
    fun points() = Kind.entries.sumOf { count(it) * it.points }

    companion object {
        /** Points needed to reach each level; level 1 starts at 0. */
        val LEVELS = listOf(0, 50, 150, 400, 1000, 2500)
        fun level(points: Int) = LEVELS.indexOfLast { points >= it } + 1
        /** Points needed for the next level, or null at the top level. */
        fun nextLevelAt(points: Int) = LEVELS.firstOrNull { it > points }
    }
}
