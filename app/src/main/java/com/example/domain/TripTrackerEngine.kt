package com.example.domain

/**
 * Pure Kotlin state machine that accumulates daily car/bike distance and segments
 * point-to-point journeys wherever the traveler stops for longer than [dwellThresholdMs]
 * (default: 15 minutes).
 */
class TripTrackerEngine(
    dwellThresholdMs: Long = LiveTrackerState.DEFAULT_DWELL_THRESHOLD_MS,
    private val dwellRadiusMeters: Double = DEFAULT_DWELL_RADIUS_METERS,
    private val maxAcceptedAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
    private val stationaryDetectionGraceMs: Long = DEFAULT_STATIONARY_GRACE_MS,
    private val onTripCompleted: (CompletedTripRecord) -> Unit = {},
) {
    private var dwellThresholdMs: Long = dwellThresholdMs
    private var isTrackingActive: Boolean = false
    private var phase: TrackerPhase = TrackerPhase.IDLE

    // Anchor while IDLE or AT_DESTINATION
    private var anchorLat: Double? = null
    private var anchorLng: Double? = null
    private var anchorTimestampMs: Long? = null

    // Active trip state (MOVING or DWELLING)
    private var tripOriginLat: Double? = null
    private var tripOriginLng: Double? = null
    private var tripStartMs: Long? = null
    private var lastMovingLat: Double? = null
    private var lastMovingLng: Double? = null
    private var lastMovingTimeMs: Long? = null
    private var distanceMeters: Double = 0.0
    private var movingDurationMs: Long = 0L
    private var peakSpeedMps: Double = 0.0
    private var currentSpeedMps: Double = 0.0
    private val routePoints = mutableListOf<RoutePoint>()

    // Candidate dwell zone while MOVING (detects stopping even if speed sensor is noisy)
    private var candidateDwellLat: Double? = null
    private var candidateDwellLng: Double? = null
    private var candidateDwellStartMs: Long? = null

    // Confirmed brief-stop dwell timer while DWELLING
    private var dwellAnchorLat: Double? = null
    private var dwellAnchorLng: Double? = null
    private var dwellStartMs: Long? = null
    private var lastFixTimeMs: Long? = null

    fun setDwellThresholdMinutes(minutes: Int) {
        dwellThresholdMs = (minutes.coerceIn(1, 60)) * 60L * 1000L
    }

    fun startTracking() {
        isTrackingActive = true
    }

    fun stopTracking(nowMs: Long): CompletedTripRecord? {
        val finished = if (phase == TrackerPhase.MOVING || phase == TrackerPhase.DWELLING) {
            if (distanceMeters >= MIN_SAVEABLE_TRIP_METERS) {
                finalizeCurrentTrip(
                    arrivalMs = dwellStartMs ?: lastMovingTimeMs ?: nowMs,
                    confirmedMs = nowMs,
                    destLat = dwellAnchorLat ?: lastMovingLat ?: tripOriginLat ?: 0.0,
                    destLng = dwellAnchorLng ?: lastMovingLng ?: tripOriginLng ?: 0.0,
                )
            } else {
                null
            }
        } else {
            null
        }
        resetAll()
        isTrackingActive = false
        return finished
    }

    fun finishCurrentTripNow(nowMs: Long): CompletedTripRecord? {
        if (!isTrackingActive) return null
        if (phase != TrackerPhase.MOVING && phase != TrackerPhase.DWELLING) return null
        if (distanceMeters < MIN_SAVEABLE_TRIP_METERS) return null

        val destLat = dwellAnchorLat ?: lastMovingLat ?: tripOriginLat ?: return null
        val destLng = dwellAnchorLng ?: lastMovingLng ?: tripOriginLng ?: return null
        val arrivalMs = dwellStartMs ?: lastMovingTimeMs ?: nowMs
        val record = finalizeCurrentTrip(
            arrivalMs = arrivalMs,
            confirmedMs = nowMs,
            destLat = destLat,
            destLng = destLng,
        )
        transitionToDestination(destLat, destLng, nowMs)
        return record
    }

    fun processLocation(sample: LocationSample): CompletedTripRecord? {
        if (!isTrackingActive) return null
        if (sample.accuracyMeters > maxAcceptedAccuracyMeters) return null
        lastFixTimeMs?.let { prev ->
            if (sample.timestampMs < prev) return null
        }
        lastFixTimeMs = sample.timestampMs

        return when (phase) {
            TrackerPhase.IDLE, TrackerPhase.AT_DESTINATION -> {
                handleIdleOrDestinationFix(sample)
                null
            }

            TrackerPhase.MOVING -> {
                handleMovingFix(sample)
                null
            }

            TrackerPhase.DWELLING -> {
                handleDwellingFix(sample)
            }
        }
    }

    fun onTick(nowMs: Long): CompletedTripRecord? {
        if (!isTrackingActive) return null
        if (phase != TrackerPhase.DWELLING) return null
        val dStart = dwellStartMs ?: return null
        if (nowMs - dStart >= dwellThresholdMs) {
            val destLat = dwellAnchorLat ?: lastMovingLat ?: tripOriginLat ?: return null
            val destLng = dwellAnchorLng ?: lastMovingLng ?: tripOriginLng ?: return null
            val record = finalizeCurrentTrip(
                arrivalMs = dStart,
                confirmedMs = nowMs,
                destLat = destLat,
                destLng = destLng,
            )
            transitionToDestination(destLat, destLng, nowMs)
            return record
        }
        return null
    }

    fun snapshot(nowMs: Long): LiveTrackerState {
        val dElapsed = if (phase == TrackerPhase.DWELLING && dwellStartMs != null) {
            (nowMs - dwellStartMs!!).coerceAtLeast(0L)
        } else {
            0L
        }
        val activeAvgSpeed = if (movingDurationMs > 0L) {
            distanceMeters / (movingDurationMs / 1000.0)
        } else {
            0.0
        }
        return LiveTrackerState(
            isTrackingActive = isTrackingActive,
            phase = phase,
            activeOriginLat = tripOriginLat ?: anchorLat,
            activeOriginLng = tripOriginLng ?: anchorLng,
            activeTripStartMs = tripStartMs,
            activeDistanceMeters = distanceMeters,
            activeMovingDurationMs = movingDurationMs,
            dwellStartMs = dwellStartMs,
            dwellElapsedMs = dElapsed,
            dwellThresholdMs = dwellThresholdMs,
            currentSpeedMps = currentSpeedMps,
            peakSpeedMps = peakSpeedMps,
            inferredMode = inferTravelMode(peakSpeedMps, activeAvgSpeed),
            activeRoutePoints = downsampleRoute(routePoints),
            lastFixTimeMs = lastFixTimeMs,
        )
    }

    private fun handleIdleOrDestinationFix(sample: LocationSample) {
        val aLat = anchorLat
        val aLng = anchorLng
        if (aLat == null || aLng == null) {
            anchorLat = sample.latitude
            anchorLng = sample.longitude
            anchorTimestampMs = sample.timestampMs
            currentSpeedMps = 0.0
            return
        }

        val distFromAnchor = GeoMath.distanceMeters(aLat, aLng, sample.latitude, sample.longitude)
        val reportedSpeed = sample.speedMps?.toDouble() ?: 0.0
        val prevTime = anchorTimestampMs ?: sample.timestampMs
        val dtSec = ((sample.timestampMs - prevTime).coerceAtLeast(1L)) / 1000.0
        val impliedSpeed = distFromAnchor / dtSec

        // Reject single-point unrealistic GPS teleport spikes (> 65 m/s)
        if (distFromAnchor > 300.0 && impliedSpeed > MAX_REALISTIC_SPEED_MPS) {
            return
        }

        val isMovingOut = distFromAnchor > dwellRadiusMeters &&
            (reportedSpeed >= MIN_ACTIVE_SPEED_MPS || impliedSpeed >= MIN_ACTIVE_SPEED_MPS || distFromAnchor > dwellRadiusMeters * 1.4)

        if (isMovingOut) {
            phase = TrackerPhase.MOVING
            tripOriginLat = aLat
            tripOriginLng = aLng
            tripStartMs = anchorTimestampMs ?: sample.timestampMs
            distanceMeters = distFromAnchor
            movingDurationMs = (sample.timestampMs - (anchorTimestampMs ?: sample.timestampMs)).coerceIn(0L, 120_000L)
            val effectiveSpeed = maxOf(reportedSpeed, impliedSpeed).coerceAtMost(MAX_REALISTIC_SPEED_MPS)
            currentSpeedMps = effectiveSpeed
            peakSpeedMps = effectiveSpeed
            lastMovingLat = sample.latitude
            lastMovingLng = sample.longitude
            lastMovingTimeMs = sample.timestampMs
            routePoints.clear()
            routePoints.add(RoutePoint(aLat, aLng, tripStartMs!!))
            routePoints.add(RoutePoint(sample.latitude, sample.longitude, sample.timestampMs))
            candidateDwellLat = sample.latitude
            candidateDwellLng = sample.longitude
            candidateDwellStartMs = sample.timestampMs
        } else {
            // Still resting near anchor — refresh latest timestamp so departure timestamp stays crisp
            anchorTimestampMs = sample.timestampMs
            currentSpeedMps = 0.0
        }
    }

    private fun handleMovingFix(sample: LocationSample) {
        val prevLat = lastMovingLat ?: sample.latitude
        val prevLng = lastMovingLng ?: sample.longitude
        val prevTime = lastMovingTimeMs ?: sample.timestampMs

        val deltaMeters = GeoMath.distanceMeters(prevLat, prevLng, sample.latitude, sample.longitude)
        val dtMs = (sample.timestampMs - prevTime).coerceAtLeast(1L)
        val dtSec = dtMs / 1000.0
        val impliedSpeed = deltaMeters / dtSec
        val reportedSpeed = sample.speedMps?.toDouble()

        if (deltaMeters > 200.0 && impliedSpeed > MAX_REALISTIC_SPEED_MPS) {
            // Ignore obvious GPS jump
            return
        }

        val effectiveSpeed = (reportedSpeed ?: impliedSpeed).coerceIn(0.0, MAX_REALISTIC_SPEED_MPS)
        currentSpeedMps = effectiveSpeed

        if (deltaMeters >= MIN_STEP_DISTANCE_METERS) {
            distanceMeters += deltaMeters
            movingDurationMs += dtMs.coerceAtMost(MAX_STEP_TIME_MS)
            if (effectiveSpeed > peakSpeedMps) {
                peakSpeedMps = effectiveSpeed
            }
            lastMovingLat = sample.latitude
            lastMovingLng = sample.longitude
            lastMovingTimeMs = sample.timestampMs
            appendRoutePoint(sample.latitude, sample.longitude, sample.timestampMs)
        } else {
            lastMovingTimeMs = sample.timestampMs
        }

        // Check if we're lingering inside a candidate stationary zone
        val candLat = candidateDwellLat
        val candLng = candidateDwellLng
        val candStart = candidateDwellStartMs
        if (candLat == null || candLng == null || candStart == null) {
            candidateDwellLat = sample.latitude
            candidateDwellLng = sample.longitude
            candidateDwellStartMs = sample.timestampMs
            return
        }

        val distFromCandidate = GeoMath.distanceMeters(candLat, candLng, sample.latitude, sample.longitude)
        val isSlow = (reportedSpeed == null || reportedSpeed < STATIONARY_SPEED_MPS) &&
            distFromCandidate <= dwellRadiusMeters

        if (isSlow) {
            if (sample.timestampMs - candStart >= stationaryDetectionGraceMs) {
                // Transition to DWELLING; arrival at candidate stop was candStart
                phase = TrackerPhase.DWELLING
                dwellAnchorLat = candLat
                dwellAnchorLng = candLng
                dwellStartMs = candStart
                currentSpeedMps = 0.0
            }
        } else {
            // Moved outside candidate stop bubble -> reset candidate anchor
            candidateDwellLat = sample.latitude
            candidateDwellLng = sample.longitude
            candidateDwellStartMs = sample.timestampMs
        }
    }

    private fun handleDwellingFix(sample: LocationSample): CompletedTripRecord? {
        val dLat = dwellAnchorLat ?: sample.latitude
        val dLng = dwellAnchorLng ?: sample.longitude
        val dStart = dwellStartMs ?: sample.timestampMs

        val distFromDwell = GeoMath.distanceMeters(dLat, dLng, sample.latitude, sample.longitude)
        val reportedSpeed = sample.speedMps?.toDouble() ?: 0.0

        // First check if dwell duration already exceeded threshold before this fix
        if (sample.timestampMs - dStart >= dwellThresholdMs && distFromDwell <= dwellRadiusMeters) {
            val record = finalizeCurrentTrip(
                arrivalMs = dStart,
                confirmedMs = sample.timestampMs,
                destLat = dLat,
                destLng = dLng,
            )
            transitionToDestination(dLat, dLng, sample.timestampMs)
            return record
        }

        if (distFromDwell > dwellRadiusMeters &&
            (reportedSpeed >= MIN_ACTIVE_SPEED_MPS || distFromDwell > dwellRadiusMeters * 1.25)
        ) {
            // Resumed trip before 15 minutes elapsed!
            val prevLat = lastMovingLat ?: dLat
            val prevLng = lastMovingLng ?: dLng
            val resumedDelta = GeoMath.distanceMeters(prevLat, prevLng, sample.latitude, sample.longitude)
            val dtMs = (sample.timestampMs - (lastMovingTimeMs ?: dStart)).coerceAtLeast(1L)
            val impliedSpeed = resumedDelta / (dtMs / 1000.0)
            if (!(resumedDelta > 300.0 && impliedSpeed > MAX_REALISTIC_SPEED_MPS)) {
                distanceMeters += resumedDelta
                val effectiveSpeed = maxOf(reportedSpeed, impliedSpeed).coerceAtMost(MAX_REALISTIC_SPEED_MPS)
                currentSpeedMps = effectiveSpeed
                if (effectiveSpeed > peakSpeedMps) peakSpeedMps = effectiveSpeed
                lastMovingLat = sample.latitude
                lastMovingLng = sample.longitude
                lastMovingTimeMs = sample.timestampMs
                appendRoutePoint(sample.latitude, sample.longitude, sample.timestampMs)
            }
            phase = TrackerPhase.MOVING
            dwellAnchorLat = null
            dwellAnchorLng = null
            dwellStartMs = null
            candidateDwellLat = sample.latitude
            candidateDwellLng = sample.longitude
            candidateDwellStartMs = sample.timestampMs
        } else {
            currentSpeedMps = 0.0
        }
        return null
    }

    private fun finalizeCurrentTrip(
        arrivalMs: Long,
        confirmedMs: Long,
        destLat: Double,
        destLng: Double,
    ): CompletedTripRecord {
        val oLat = tripOriginLat ?: destLat
        val oLng = tripOriginLng ?: destLng
        val sTime = tripStartMs ?: arrivalMs
        val duration = (arrivalMs - sTime).coerceAtLeast(1000L)
        val effectiveMovingMs = movingDurationMs.coerceIn(1000L, duration)
        val avgSpeed = distanceMeters / (effectiveMovingMs / 1000.0)
        val mode = inferTravelMode(peakSpeedMps, avgSpeed)

        val finalPoints = routePoints.toMutableList()
        if (finalPoints.isEmpty() ||
            GeoMath.distanceMeters(finalPoints.last().latitude, finalPoints.last().longitude, destLat, destLng) > 10.0
        ) {
            finalPoints.add(RoutePoint(destLat, destLng, arrivalMs))
        }

        val record = CompletedTripRecord(
            dateKey = GeoMath.localDateKey(sTime),
            originLat = oLat,
            originLng = oLng,
            originLabel = null,
            destinationLat = destLat,
            destinationLng = destLng,
            destinationLabel = null,
            startTimeMs = sTime,
            arrivalTimeMs = arrivalMs,
            confirmedAtMs = confirmedMs,
            durationMs = duration,
            movingDurationMs = effectiveMovingMs,
            distanceMeters = distanceMeters,
            peakSpeedMps = peakSpeedMps,
            avgSpeedMps = avgSpeed,
            travelMode = mode,
            routePoints = downsampleRoute(finalPoints),
        )
        onTripCompleted(record)
        return record
    }

    private fun transitionToDestination(destLat: Double, destLng: Double, nowMs: Long) {
        phase = TrackerPhase.AT_DESTINATION
        anchorLat = destLat
        anchorLng = destLng
        anchorTimestampMs = nowMs
        clearTripBuffers()
    }

    private fun resetAll() {
        phase = TrackerPhase.IDLE
        anchorLat = null
        anchorLng = null
        anchorTimestampMs = null
        lastFixTimeMs = null
        clearTripBuffers()
    }

    private fun clearTripBuffers() {
        tripOriginLat = null
        tripOriginLng = null
        tripStartMs = null
        lastMovingLat = null
        lastMovingLng = null
        lastMovingTimeMs = null
        distanceMeters = 0.0
        movingDurationMs = 0L
        peakSpeedMps = 0.0
        currentSpeedMps = 0.0
        routePoints.clear()
        candidateDwellLat = null
        candidateDwellLng = null
        candidateDwellStartMs = null
        dwellAnchorLat = null
        dwellAnchorLng = null
        dwellStartMs = null
    }

    private fun appendRoutePoint(lat: Double, lng: Double, timestampMs: Long) {
        val last = routePoints.lastOrNull()
        if (last == null || GeoMath.distanceMeters(last.latitude, last.longitude, lat, lng) >= ROUTE_POINT_MIN_SPACING_METERS) {
            routePoints.add(RoutePoint(lat, lng, timestampMs))
        }
    }

    private fun downsampleRoute(points: List<RoutePoint>, maxPoints: Int = 80): List<RoutePoint> {
        if (points.size <= maxPoints) return points.toList()
        val step = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
        val result = ArrayList<RoutePoint>(maxPoints)
        for (i in 0 until maxPoints) {
            val idx = (i * step).toInt().coerceIn(0, points.lastIndex)
            result.add(points[idx])
        }
        return result
    }

    companion object {
        const val DEFAULT_DWELL_RADIUS_METERS = 65.0
        const val DEFAULT_MAX_ACCURACY_METERS = 45.0f
        const val DEFAULT_STATIONARY_GRACE_MS = 45_000L
        const val MIN_STEP_DISTANCE_METERS = 4.0
        const val MIN_ACTIVE_SPEED_MPS = 0.9
        const val STATIONARY_SPEED_MPS = 0.85
        const val MAX_REALISTIC_SPEED_MPS = 65.0
        const val MAX_STEP_TIME_MS = 60_000L
        const val MIN_SAVEABLE_TRIP_METERS = 50.0
        const val ROUTE_POINT_MIN_SPACING_METERS = 25.0

        fun inferTravelMode(peakSpeedMps: Double, avgSpeedMps: Double): TravelMode {
            if (peakSpeedMps <= 0.1 && avgSpeedMps <= 0.1) return TravelMode.TRAVEL
            // Typical bike: peak < 10.5 m/s (~23.5 mph) and avg < 7.5 m/s (~16.8 mph)
            return if (peakSpeedMps < 10.5 && avgSpeedMps < 7.5) {
                TravelMode.BIKE
            } else {
                TravelMode.CAR
            }
        }
    }
}
