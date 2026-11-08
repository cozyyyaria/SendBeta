package com.example.sendbeta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Gym::class, Route::class, Attempt::class], version = 3, exportSchema = false)
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
                        // Runs every time the database opens. If there are no gyms, the database is new
                        // (first install, or rebuilt after a schema change), so fill in the starter data.
                        // Gyms can't be deleted in the app, so this never re-adds data the user removed.
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            val empty = db.query("SELECT COUNT(*) FROM gyms").use { c ->
                                c.moveToFirst() && c.getInt(0) == 0
                            }
                            if (empty) SeedData.populate(db)
                        }
                    })
                    // Prototype: rebuild (and re-seed) the database when the schema changes
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
