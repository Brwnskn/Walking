package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WaymarkApplication
import com.example.data.preferences.UserPreferences
import com.example.data.repository.Trip
import com.example.domain.DistanceUnit
import com.example.domain.GeoMath
import com.example.domain.LiveTrackerState
import com.example.domain.TrackerPhase
import com.example.location.LocationTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DailyTravelSummary(
    val dateKey: String,
    val label: String,
    val shortWeekday: String,
    val totalDistanceMeters: Double,
    val totalDurationMs: Long,
    val tripCount: Int,
    val trips: List<Trip>,
)

data class WaymarkUiState(
    val todayDateKey: String = "",
    val todayCompletedTrips: List<Trip> = emptyList(),
    val todayTotalDistanceMeters: Double = 0.0,
    val todayTotalDurationMs: Long = 0L,
    val liveState: LiveTrackerState = LiveTrackerState(),
    val preferences: UserPreferences = UserPreferences(),
    val dailySummaries: List<DailyTravelSummary> = emptyList(),
    val weeklyChartDays: List<DailyTravelSummary> = emptyList(),
    val selectedHistoryDateKey: String? = null,
    val nowMs: Long = System.currentTimeMillis(),
)

class WaymarkViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WaymarkApplication
    private val tripRepository = app.tripRepository
    private val preferencesRepository = app.preferencesRepository
    private val coordinator = app.trackerCoordinator

    private val selectedHistoryDate = MutableStateFlow<String?>(null)

    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<WaymarkUiState> = combine(
        tripRepository.observeAllTrips(),
        preferencesRepository.preferences,
        coordinator.liveState,
        selectedHistoryDate,
        tickerFlow,
    ) { allTrips, prefs, liveState, selectedDate, nowMs ->
        val todayKey = GeoMath.localDateKey(nowMs)
        val yesterdayKey = GeoMath.localDateKey(nowMs - 24L * 60L * 60L * 1000L)

        val todayTrips = allTrips.filter { it.dateKey == todayKey }
        val activeTodayDistance = if (
            liveState.isTrackingActive &&
            (liveState.phase == TrackerPhase.MOVING || liveState.phase == TrackerPhase.DWELLING) &&
            (liveState.activeTripStartMs == null || GeoMath.localDateKey(liveState.activeTripStartMs) == todayKey)
        ) {
            liveState.activeDistanceMeters
        } else {
            0.0
        }

        val activeTodayDuration = if (
            liveState.isTrackingActive &&
            (liveState.phase == TrackerPhase.MOVING || liveState.phase == TrackerPhase.DWELLING) &&
            liveState.activeTripStartMs != null &&
            GeoMath.localDateKey(liveState.activeTripStartMs) == todayKey
        ) {
            val endMark = if (liveState.phase == TrackerPhase.DWELLING && liveState.dwellStartMs != null) {
                liveState.dwellStartMs
            } else {
                nowMs
            }
            (endMark - liveState.activeTripStartMs).coerceAtLeast(0L)
        } else {
            0L
        }

        val todayTotalDistance = todayTrips.sumOf { it.distanceMeters } + activeTodayDistance
        val todayTotalDuration = todayTrips.sumOf { it.durationMs } + activeTodayDuration

        val grouped = allTrips.groupBy { it.dateKey }
        val allDateKeys = (grouped.keys + todayKey).distinct().sortedDescending()
        val summaries = allDateKeys.map { key ->
            val dayTrips = grouped[key].orEmpty()
            val extraDist = if (key == todayKey) activeTodayDistance else 0.0
            val extraDur = if (key == todayKey) activeTodayDuration else 0L
            DailyTravelSummary(
                dateKey = key,
                label = GeoMath.formatDayLabel(key, todayKey, yesterdayKey),
                shortWeekday = GeoMath.formatShortWeekday(key),
                totalDistanceMeters = dayTrips.sumOf { it.distanceMeters } + extraDist,
                totalDurationMs = dayTrips.sumOf { it.durationMs } + extraDur,
                tripCount = dayTrips.size,
                trips = dayTrips,
            )
        }.filter { it.tripCount > 0 || it.totalDistanceMeters > 0.0 }

        val last7Keys = buildLast7DateKeys(nowMs)
        val summaryMap = summaries.associateBy { it.dateKey }
        val chartDays = last7Keys.map { key ->
            summaryMap[key] ?: DailyTravelSummary(
                dateKey = key,
                label = GeoMath.formatDayLabel(key, todayKey, yesterdayKey),
                shortWeekday = GeoMath.formatShortWeekday(key),
                totalDistanceMeters = 0.0,
                totalDurationMs = 0L,
                tripCount = 0,
                trips = emptyList(),
            )
        }

        WaymarkUiState(
            todayDateKey = todayKey,
            todayCompletedTrips = todayTrips,
            todayTotalDistanceMeters = todayTotalDistance,
            todayTotalDurationMs = todayTotalDuration,
            liveState = liveState,
            preferences = prefs,
            dailySummaries = summaries,
            weeklyChartDays = chartDays,
            selectedHistoryDateKey = selectedDate,
            nowMs = nowMs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = WaymarkUiState(),
    )

    fun observeTrip(tripId: Long): Flow<Trip?> = tripRepository.observeTripById(tripId)

    fun startTracking(context: Context) {
        LocationTrackingService.start(context)
    }

    fun stopTracking(context: Context) {
        LocationTrackingService.stop(context)
    }

    fun finishCurrentLegNow() {
        coordinator.finishCurrentLegNow()
    }

    fun selectHistoryDate(dateKey: String?) {
        selectedHistoryDate.value = if (selectedHistoryDate.value == dateKey) null else dateKey
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            preferencesRepository.setDistanceUnit(unit)
        }
    }

    fun setDwellThresholdMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setDwellThresholdMinutes(minutes)
        }
    }

    fun updateTripLabels(tripId: Long, originLabel: String, destinationLabel: String) {
        viewModelScope.launch {
            tripRepository.updateTripLabels(tripId, originLabel, destinationLabel)
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            tripRepository.clearAllTrips()
        }
    }

    private fun buildLast7DateKeys(nowMs: Long): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val keys = ArrayList<String>(7)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            keys.add(GeoMath.localDateKey(cal.timeInMillis))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return keys
    }
}
