package com.example.location

import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.TripRepository
import com.example.domain.CompletedTripRecord
import com.example.domain.LiveTrackerState
import com.example.domain.LocationSample
import com.example.domain.TripTrackerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shared application-scoped coordinator synchronizing the foreground location service,
 * the TripTrackerEngine state machine, and Room trip persistence.
 */
class TrackerCoordinator(
    private val tripRepository: TripRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val engine = TripTrackerEngine()

    private val _liveState = MutableStateFlow(LiveTrackerState())
    val liveState: StateFlow<LiveTrackerState> = _liveState.asStateFlow()

    init {
        scope.launch {
            preferencesRepository.preferences.collectLatest { prefs ->
                synchronized(lock) {
                    engine.setDwellThresholdMinutes(prefs.dwellThresholdMinutes)
                    _liveState.value = engine.snapshot(System.currentTimeMillis())
                }
            }
        }
    }

    fun onServiceStarted() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            engine.startTracking()
            _liveState.value = engine.snapshot(now)
        }
    }

    fun onServiceStopped() {
        val now = System.currentTimeMillis()
        val completed: CompletedTripRecord?
        synchronized(lock) {
            completed = engine.stopTracking(now)
            _liveState.value = engine.snapshot(now)
        }
        completed?.let { persistCompletedTrip(it) }
    }

    fun onLocationUpdate(sample: LocationSample) {
        val completed: CompletedTripRecord?
        synchronized(lock) {
            completed = engine.processLocation(sample)
            _liveState.value = engine.snapshot(sample.timestampMs)
        }
        completed?.let { persistCompletedTrip(it) }
    }

    fun onPeriodicTick(nowMs: Long = System.currentTimeMillis()) {
        val completed: CompletedTripRecord?
        synchronized(lock) {
            completed = engine.onTick(nowMs)
            _liveState.value = engine.snapshot(nowMs)
        }
        completed?.let { persistCompletedTrip(it) }
    }

    fun finishCurrentLegNow() {
        val now = System.currentTimeMillis()
        val completed: CompletedTripRecord?
        synchronized(lock) {
            completed = engine.finishCurrentTripNow(now)
            _liveState.value = engine.snapshot(now)
        }
        completed?.let { persistCompletedTrip(it) }
    }

    private fun persistCompletedTrip(record: CompletedTripRecord) {
        scope.launch(Dispatchers.IO) {
            tripRepository.saveCompletedTrip(record)
        }
    }
}
