package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DepositSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: DepositSettings)

    @Query("SELECT * FROM deposit_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): DepositSettings?
}