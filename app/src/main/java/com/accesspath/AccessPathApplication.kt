package com.accesspath

import android.app.Application
import androidx.room.Room
import com.accesspath.data.AccessDatabase
import com.accesspath.data.CampusRepository

class AccessPathApplication : Application() {
    val repository by lazy {
        val db = Room.databaseBuilder(this, AccessDatabase::class.java, "accesspath.db").build()
        CampusRepository(db, seed = { assets.open("maps/demo-campus.json").bufferedReader().use { it.readText() } })
    }
}
