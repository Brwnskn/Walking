package com.example.domain

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TravelMode(val label: String) {
    BIKE("Bike"),
    CAR("Car"),
    TRAVEL("Travel"),
}

enum class DistanceUnit(val shortLabel: String, val speedLabel: String) {
    MILES("mi", "mph"),
    KILOMETERS("km", "km/h"),
}

enum class TrackerPhase {
    IDLE,
    MOVING,
    DWELLING,
    AT_DESTINATION,
}

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
)

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val speedMps: Float?,
    val accuracyMeters: Float,
)

data class CompletedTripRecord(
    val dateKey: String,
    val originLat: Double,
    val originLng: Double,
    val originLabel: String?,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationLabel: String?,
    val startTimeMs: Long,
    val arrivalTimeMs: Long,
    val confirmedAtMs: Long,
    val durationMs: Long,
    val movingDurationMs: Long,
    val distanceMeters: Double,
    val peakSpeedMps: Double,
    val avgSpeedMps: Double,
    val travelMode: TravelMode,
    val routePoints: List<RoutePoint>,
)

data class LiveTrackerState(
    val isTrackingActive: Boolean = false,
    val phase: TrackerPhase = TrackerPhase.IDLE,
    val activeOriginLat: Double? = null,
    val activeOriginLng: Double? = null,
    val activeTripStartMs: Long? = null,
    val activeDistanceMeters: Double = 0.0,
    val activeMovingDurationMs: Long = 0L,
    val dwellStartMs: Long? = null,
    val dwellElapsedMs: Long = 0L,
    val dwellThresholdMs: Long = DEFAULT_DWELL_THRESHOLD_MS,
    val currentSpeedMps: Double = 0.0,
    val peakSpeedMps: Double = 0.0,
    val inferredMode: TravelMode = TravelMode.TRAVEL,
    val activeRoutePoints: List<RoutePoint> = emptyList(),
    val lastFixTimeMs: Long? = null,
) {
    val dwellRemainingMs: Long
        get() = (dwellThresholdMs - dwellElapsedMs).coerceAtLeast(0L)

    val dwellProgress: Float
        get() = if (dwellThresholdMs <= 0L) 1f
        else (dwellElapsedMs.toFloat() / dwellThresholdMs.toFloat()).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_DWELL_THRESHOLD_MS = 15L * 60L * 1000L
    }
}

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_KM = 1000.0

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2.0).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun formatDistanceValue(meters: Double, unit: DistanceUnit): String {
        val converted = when (unit) {
            DistanceUnit.MILES -> meters / METERS_PER_MILE
            DistanceUnit.KILOMETERS -> meters / METERS_PER_KM
        }
        return if (converted >= 100.0) {
            String.format(Locale.US, "%.0f", converted)
        } else {
            String.format(Locale.US, "%.1f", converted)
        }
    }

    fun formatDistance(meters: Double, unit: DistanceUnit): String =
        "${formatDistanceValue(meters, unit)} ${unit.shortLabel}"

    fun formatSpeed(speedMps: Double, unit: DistanceUnit): String {
        val value = when (unit) {
            DistanceUnit.MILES -> speedMps * 2.23693629
            DistanceUnit.KILOMETERS -> speedMps * 3.6
        }
        return String.format(Locale.US, "%.0f %s", value.coerceAtLeast(0.0), unit.speedLabel)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0L -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.US, "%ds", seconds)
        }
    }

    fun formatCountdown(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatClockTime(timestampMs: Long): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    fun localDateKey(timestampMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(timestampMs))
    }

    fun formatDayLabel(dateKey: String, todayKey: String, yesterdayKey: String): String {
        return when (dateKey) {
            todayKey -> "Today"
            yesterdayKey -> "Yesterday"
            else -> runCatching {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val parsed = parser.parse(dateKey)
                if (parsed != null) {
                    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(parsed)
                } else {
                    dateKey
                }
            }.getOrDefault(dateKey)
        }
    }

    fun formatShortWeekday(dateKey: String): String {
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = parser.parse(dateKey)
            if (parsed != null) {
                SimpleDateFormat("EEE", Locale.getDefault()).format(parsed)
            } else {
                dateKey.takeLast(2)
            }
        }.getOrDefault(dateKey.takeLast(2))
    }

    fun formatCoordFallback(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.4f° %s, %.4f° %s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lng),
            lngDir,
        )
    }
}
