package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.local.TripDao
import com.example.data.local.TripEntity
import com.example.domain.CompletedTripRecord
import com.example.domain.GeoMath
import com.example.domain.RoutePoint
import com.example.domain.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.coroutines.resume

data class Trip(
    val id: Long,
    val dateKey: String,
    val originLat: Double,
    val originLng: Double,
    val originLabel: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationLabel: String,
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

class TripRepository(
    private val context: Context,
    private val tripDao: TripDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAllTrips(): Flow<List<Trip>> =
        tripDao.observeAllTrips().map { list -> list.map { it.toDomain() } }

    fun observeTripsForDate(dateKey: String): Flow<List<Trip>> =
        tripDao.observeTripsForDate(dateKey).map { list -> list.map { it.toDomain() } }

    fun observeTripById(tripId: Long): Flow<Trip?> =
        tripDao.observeTripById(tripId).map { it?.toDomain() }

    suspend fun saveCompletedTrip(record: CompletedTripRecord): Long {
        val originName = record.originLabel
            ?: resolvePlaceLabel(record.originLat, record.originLng)
        val destinationName = record.destinationLabel
            ?: resolvePlaceLabel(record.destinationLat, record.destinationLng)

        val entity = TripEntity(
            dateKey = record.dateKey,
            originLat = record.originLat,
            originLng = record.originLng,
            originLabel = originName,
            destinationLat = record.destinationLat,
            destinationLng = record.destinationLng,
            destinationLabel = destinationName,
            startTimeMs = record.startTimeMs,
            arrivalTimeMs = record.arrivalTimeMs,
            confirmedAtMs = record.confirmedAtMs,
            durationMs = record.durationMs,
            movingDurationMs = record.movingDurationMs,
            distanceMeters = record.distanceMeters,
            peakSpeedMps = record.peakSpeedMps,
            avgSpeedMps = record.avgSpeedMps,
            travelModeName = record.travelMode.name,
            routePointsJson = json.encodeToString(record.routePoints),
        )
        return tripDao.insertTrip(entity)
    }

    suspend fun updateTripLabels(tripId: Long, originLabel: String, destinationLabel: String) {
        tripDao.updateTripLabels(
            tripId = tripId,
            originLabel = originLabel.trim().ifEmpty { "Start Point" },
            destinationLabel = destinationLabel.trim().ifEmpty { "Destination" },
        )
    }

    suspend fun deleteTrip(tripId: Long) {
        tripDao.deleteTripById(tripId)
    }

    suspend fun clearAllTrips() {
        tripDao.deleteAllTrips()
    }

    suspend fun resolvePlaceLabel(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        val fallback = GeoMath.formatCoordFallback(lat, lng)
        if (!Geocoder.isPresent()) return@withContext fallback

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(2500L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<Address?> { cont ->
                    runCatching {
                        geocoder.getFromLocation(lat, lng, 1) { addresses ->
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
        formatAddressLabel(address) ?: fallback
    }

    private fun formatAddressLabel(address: Address?): String? {
        if (address == null) return null
        val feature = address.featureName?.takeIf { it.isNotBlank() && it != address.subThoroughfare }
        val street = listOfNotNull(
            address.subThoroughfare?.takeIf { it.isNotBlank() },
            address.thoroughfare?.takeIf { it.isNotBlank() },
        ).joinToString(" ").takeIf { it.isNotBlank() }

        val locality = address.locality ?: address.subAdminArea
        return when {
            street != null && locality != null -> "$street, $locality"
            street != null -> street
            feature != null && locality != null -> "$feature, $locality"
            locality != null -> locality
            else -> null
        }
    }

    private fun TripEntity.toDomain(): Trip {
        val points = runCatching {
            json.decodeFromString<List<RoutePoint>>(routePointsJson)
        }.getOrDefault(emptyList())
        val mode = runCatching { TravelMode.valueOf(travelModeName) }.getOrDefault(TravelMode.TRAVEL)
        return Trip(
            id = id,
            dateKey = dateKey,
            originLat = originLat,
            originLng = originLng,
            originLabel = originLabel,
            destinationLat = destinationLat,
            destinationLng = destinationLng,
            destinationLabel = destinationLabel,
            startTimeMs = startTimeMs,
            arrivalTimeMs = arrivalTimeMs,
            confirmedAtMs = confirmedAtMs,
            durationMs = durationMs,
            movingDurationMs = movingDurationMs,
            distanceMeters = distanceMeters,
            peakSpeedMps = peakSpeedMps,
            avgSpeedMps = avgSpeedMps,
            travelMode = mode,
            routePoints = points,
        )
    }
}
