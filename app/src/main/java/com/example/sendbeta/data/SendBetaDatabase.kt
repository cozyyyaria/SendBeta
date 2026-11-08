package com.example.sendbeta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Gym::class, Route::class, Attempt::class], version = 1, exportSchema = false)
abstract class SendBetaDatabase : RoomDatabase() {

    abstract fun gymDao(): GymDao
    abstract fun routeDao(): RouteDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        @Volatile
        private var INSTANCE: SendBetaDatabase? = null

        /** Single database instance for the whole app. */
        fun get(context: Context): SendBetaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SendBetaDatabase::class.java,
                    "sendbeta.db"
                )
                    .addCallback(object : Callback() {
                        // Runs once, the first time the database file is created
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            SeedData.populate(db)
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
