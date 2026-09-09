package com.example.noshow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deposits")
data class Deposit(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val reservationId: Int,
    val amount: Double,
    val status: String = "Pending",
    val recordedAt: String
)