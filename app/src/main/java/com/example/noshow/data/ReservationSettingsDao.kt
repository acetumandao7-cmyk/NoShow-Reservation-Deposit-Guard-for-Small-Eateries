package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReservationSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ReservationSettings)

    @Query("SELECT * FROM reservation_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): ReservationSettings?
}