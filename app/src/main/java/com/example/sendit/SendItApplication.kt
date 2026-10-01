package com.example.sendit

import android.app.Application
import androidx.room.Room
import com.example.sendit.data.AppDatabase
import com.example.sendit.data.ClimbRepository

// Shares one database and repository between the worker and screens.
class SendItApplication : Application() {
    val repository by lazy { // lazy means this is only created when first accessed
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "sendit.db").build()
        ClimbRepository(database)
    }
}
