package com.accesspath

import android.app.Application
import androidx.room.Room
import com.accesspath.data.AccessDatabase
import com.accesspath.data.CampusRepository

class AccessPathApplication : Application() {
    val supabase by lazy { com.accesspath.data.remote.SupabaseClient(this, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) }

    val localPlaces by lazy { com.accesspath.data.LocalPlaces(this) }
    val contributions by lazy { com.accesspath.data.Contributions(this) }
    val barriers by lazy { com.accesspath.data.OutdoorBarriers(this, supabase, contributions) }

    val repository by lazy {
        val db = Room.databaseBuilder(this, AccessDatabase::class.java, "accesspath.db").build()
        CampusRepository(db, seed = { assets.open("maps/campus.json").bufferedReader().use { it.readText() } })
    }
}
