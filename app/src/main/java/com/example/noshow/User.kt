package com.example.noshow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fullName: String,

    val email: String,

    val password: String,

    val role: String = "Customer"
)