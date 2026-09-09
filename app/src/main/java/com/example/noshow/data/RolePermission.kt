package com.example.noshow.data

import androidx.room.Entity

@Entity(
    tableName = "role_permissions",
    primaryKeys = ["role", "permission"]
)
data class RolePermission(
    val role: String,
    val permission: String,
    val enabled: Boolean
)