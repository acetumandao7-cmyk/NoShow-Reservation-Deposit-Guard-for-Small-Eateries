package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RolePermissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permissions: List<RolePermission>)

    @Query(
        "SELECT * FROM role_permissions " +
        "WHERE role = :role " +
        "ORDER BY permission ASC"
    )
    suspend fun getPermissionsForRole(role: String): List<RolePermission>

    @Query(
        "UPDATE role_permissions " +
        "SET enabled = :enabled " +
        "WHERE role = :role AND permission = :permission"
    )
    suspend fun setPermission(
        role: String,
        permission: String,
        enabled: Boolean
    )
}