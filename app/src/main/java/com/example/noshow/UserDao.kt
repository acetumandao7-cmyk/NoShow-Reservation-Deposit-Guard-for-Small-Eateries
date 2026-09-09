package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    suspend fun login(email: String, password: String): User?

    @Query("UPDATE users SET fullName = :fullName, email = :email WHERE id = :userId")
    suspend fun updateUserProfile(userId: Int, fullName: String, email: String)

    @Query("SELECT * FROM users ORDER BY id DESC")
    suspend fun getAllUsers(): List<User>

    @Query("UPDATE users SET role = :role WHERE id = :userId")
    suspend fun updateUserRole(userId: Int, role: String)
}
