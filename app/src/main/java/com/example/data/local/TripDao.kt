package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTimeMs DESC")
    fun observeAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE dateKey = :dateKey ORDER BY startTimeMs DESC")
    fun observeTripsForDate(dateKey: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTripById(tripId: Long): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Query("UPDATE trips SET originLabel = :originLabel, destinationLabel = :destinationLabel WHERE id = :tripId")
    suspend fun updateTripLabels(tripId: Long, originLabel: String, destinationLabel: String)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()
}
