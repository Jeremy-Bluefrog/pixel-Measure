package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.MeasureRecord

@Database(entities = [MeasureRecord::class], version = 3, exportSchema = false)
abstract class MeasureDatabase : RoomDatabase() {
    abstract fun measureDao(): MeasureDao

    companion object {
        @Volatile
        private var INSTANCE: MeasureDatabase? = null

        fun getDatabase(context: Context): MeasureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeasureDatabase::class.java,
                    "measure_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
