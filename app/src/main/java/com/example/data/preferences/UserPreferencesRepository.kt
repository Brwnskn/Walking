package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.waymarkDataStore: DataStore<Preferences> by preferencesDataStore(name = "waymark_prefs")

data class UserPreferences(
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val dwellThresholdMinutes: Int = DEFAULT_DWELL_MINUTES,
) {
    companion object {
        const val DEFAULT_DWELL_MINUTES = 15
    }
}

class UserPreferencesRepository(private val context: Context) {
    private val keyUnit = stringPreferencesKey("distance_unit")
    private val keyDwellMinutes = intPreferencesKey("dwell_threshold_minutes")

    val preferences: Flow<UserPreferences> = context.waymarkDataStore.data.map { prefs ->
        val unitName = prefs[keyUnit] ?: DistanceUnit.MILES.name
        val unit = runCatching { DistanceUnit.valueOf(unitName) }.getOrDefault(DistanceUnit.MILES)
        val dwell = (prefs[keyDwellMinutes] ?: UserPreferences.DEFAULT_DWELL_MINUTES).coerceIn(5, 45)
        UserPreferences(distanceUnit = unit, dwellThresholdMinutes = dwell)
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.waymarkDataStore.edit { prefs ->
            prefs[keyUnit] = unit.name
        }
    }

    suspend fun setDwellThresholdMinutes(minutes: Int) {
        context.waymarkDataStore.edit { prefs ->
            prefs[keyDwellMinutes] = minutes.coerceIn(5, 45)
        }
    }
}
