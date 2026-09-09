package com.example.noshow.data

import androidx.room.Entity

@Entity(tableName = "reservation_settings")
data class ReservationSettings(
    @androidx.room.PrimaryKey
    val id: Int = 1,
    val maxPartySize: Int = 50,
    val openingTime: String = "10:00",
    val closingTime: String = "22:00",
    val minimumAdvanceMinutes: Int = 30,
    val updatedAt: String = ""
)