package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [Index(value = ["dateKey"]), Index(value = ["startTimeMs"])],
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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
    val travelModeName: String,
    val routePointsJson: String,
)
