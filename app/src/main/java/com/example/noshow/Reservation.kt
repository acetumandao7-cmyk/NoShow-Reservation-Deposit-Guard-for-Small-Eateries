package com.example.noshow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations")
data class Reservation(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userId: Int,

    val reservationDate: String,

    val reservationTime: String,

    val partySize: Int,

    val details: String,

    val status: String = "Pending"
)