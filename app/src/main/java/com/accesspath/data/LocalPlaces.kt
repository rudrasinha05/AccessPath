package com.accesspath.data

import android.content.Context
import com.accesspath.data.remote.Poi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A place the person searched, opened or saved. Stored on the phone only. */
@Serializable
data class SavedPlace(val name: String, val detail: String, val lat: Double, val lng: Double, val time: Long = System.currentTimeMillis(), val poi: Poi? = null)

/** Recent places and saved places (with Home and Work), kept in the app's private storage. */
class LocalPlaces(context: Context) {
    private val prefs = context.getSharedPreferences("accesspath_places", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(SavedPlace.serializer())

    private fun read(key: String): List<SavedPlace> = prefs.getString(key, null)
        ?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()
    private fun write(key: String, v: List<SavedPlace>) = prefs.edit().putString(key, json.encodeToString(list, v)).apply()
    private fun same(a: SavedPlace, b: SavedPlace) = a.name == b.name && Math.abs(a.lat - b.lat) < 1e-5 && Math.abs(a.lng - b.lng) < 1e-5

    fun recents(): List<SavedPlace> = read(RECENT)
    fun addRecent(p: SavedPlace) = write(RECENT, (listOf(p) + recents().filterNot { same(it, p) }).take(MAX_RECENT))
    fun removeRecent(p: SavedPlace) = write(RECENT, recents().filterNot { same(it, p) })
    fun clearRecents() = write(RECENT, emptyList())

    fun saved(): List<SavedPlace> = read(SAVED)
    fun isSaved(p: SavedPlace) = saved().any { same(it, p) }
    /** Saves or unsaves; returns true when the place is now saved. */
    fun toggleSaved(p: SavedPlace): Boolean {
        val now = saved()
        return if (now.any { same(it, p) }) { write(SAVED, now.filterNot { same(it, p) }); false } else { write(SAVED, listOf(p) + now); true }
    }

    fun home(): SavedPlace? = read(HOME).firstOrNull()
    fun work(): SavedPlace? = read(WORK).firstOrNull()
    fun setHome(p: SavedPlace?) = write(HOME, listOfNotNull(p))
    fun setWork(p: SavedPlace?) = write(WORK, listOfNotNull(p))

    companion object {
        private const val RECENT = "recent"; private const val SAVED = "saved"; private const val HOME = "home"; private const val WORK = "work"
        const val MAX_RECENT = 30
    }
}
