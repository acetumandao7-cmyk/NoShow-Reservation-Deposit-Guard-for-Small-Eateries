package com.example.noshow.data

import androidx.room.Entity

@Entity(tableName = "deposit_settings")
data class DepositSettings(
    @androidx.room.PrimaryKey
    val id: Int = 1,
    val depositRequired: Boolean = false,
    val defaultDepositAmount: Double = 500.0,
    val minimumDeposit: Double = 100.0,
    val updatedAt: String = ""
)