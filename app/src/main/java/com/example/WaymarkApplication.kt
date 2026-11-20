package com.example

import android.app.Application
import com.example.data.local.WaymarkDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.TripRepository
import com.example.location.TrackerCoordinator

class WaymarkApplication : Application() {
    val database: WaymarkDatabase by lazy { WaymarkDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val tripRepository: TripRepository by lazy { TripRepository(this, database.tripDao()) }
    val trackerCoordinator: TrackerCoordinator by lazy {
        TrackerCoordinator(tripRepository, preferencesRepository)
    }
}
