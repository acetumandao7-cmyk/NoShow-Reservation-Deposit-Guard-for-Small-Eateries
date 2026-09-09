package com.example.noshow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        User::class,
        Reservation::class,
        Deposit::class,
        RolePermission::class,
        DepositSettings::class,
        ReservationSettings::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun reservationDao(): ReservationDao

    abstract fun depositDao(): DepositDao

    abstract fun rolePermissionDao(): RolePermissionDao

    abstract fun depositSettingsDao(): DepositSettingsDao

    abstract fun reservationSettingsDao(): ReservationSettingsDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reservations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        reservationDate TEXT NOT NULL,
                        reservationTime TEXT NOT NULL,
                        partySize INTEGER NOT NULL,
                        details TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deposits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reservationId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        status TEXT NOT NULL,
                        recordedAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS role_permissions (
                        role TEXT NOT NULL,
                        permission TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        PRIMARY KEY(role, permission)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deposit_settings (
                        id INTEGER NOT NULL,
                        depositRequired INTEGER NOT NULL,
                        defaultDepositAmount REAL NOT NULL,
                        minimumDeposit REAL NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO deposit_settings
                    (id, depositRequired, defaultDepositAmount, minimumDeposit, updatedAt)
                    VALUES (1, 0, 500.0, 100.0, '')
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reservation_settings (
                        id INTEGER NOT NULL,
                        maxPartySize INTEGER NOT NULL,
                        openingTime TEXT NOT NULL,
                        closingTime TEXT NOT NULL,
                        minimumAdvanceMinutes INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO reservation_settings
                    (id, maxPartySize, openingTime, closingTime, minimumAdvanceMinutes, updatedAt)
                    VALUES (1, 50, '10:00', '22:00', 30, '')
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "noshow_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}