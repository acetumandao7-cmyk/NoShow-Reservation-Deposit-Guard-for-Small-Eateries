package com.example.noshow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReservationDao {

    @Insert
    suspend fun insertReservation(reservation: Reservation)

    @Query(
        "SELECT * FROM reservations " +
        "WHERE userId = :userId " +
        "ORDER BY id DESC"
    )
    suspend fun getReservationsByUser(userId: Int): List<Reservation>

    @Query(
        "SELECT * FROM reservations " +
        "WHERE id = :reservationId " +
        "LIMIT 1"
    )
    suspend fun getReservationById(reservationId: Int): Reservation?

    @Query(
        "SELECT * FROM reservations " +
        "WHERE userId = :userId " +
        "AND reservationDate = :reservationDate " +
        "AND reservationTime = :reservationTime " +
        "AND status IN ('Pending', 'Confirmed') " +
        "LIMIT 1"
    )
    suspend fun getActiveReservation(
        userId: Int,
        reservationDate: String,
        reservationTime: String
    ): Reservation?

    @Query(
        "SELECT * FROM reservations " +
        "ORDER BY id DESC"
    )
    suspend fun getAllReservations(): List<Reservation>

    @Query(
        "UPDATE reservations " +
        "SET status = :status " +
        "WHERE id = :reservationId"
    )
    suspend fun updateReservationStatus(
        reservationId: Int,
        status: String
    )
}
