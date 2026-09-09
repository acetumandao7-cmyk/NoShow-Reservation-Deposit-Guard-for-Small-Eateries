package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DepositDao {

    @Insert
    suspend fun insertDeposit(deposit: Deposit)

    @Query(
        "SELECT * FROM deposits " +
        "WHERE reservationId = :reservationId " +
        "LIMIT 1"
    )
    suspend fun getDepositByReservationId(
        reservationId: Int
    ): Deposit?

    @Query("SELECT * FROM deposits ORDER BY id DESC")
    suspend fun getAllDeposits(): List<Deposit>

    @Query(
        "UPDATE deposits " +
        "SET amount = :amount, status = :status, recordedAt = :recordedAt " +
        "WHERE id = :depositId"
    )
    suspend fun updateDeposit(
        depositId: Int,
        amount: Double,
        status: String,
        recordedAt: String
    )
}