package com.example.studypilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StudyTask::class, User::class], version = 2, exportSchema = false)
abstract class StudyPilotDatabase : RoomDatabase() {
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: StudyPilotDatabase? = null

        fun getDatabase(context: Context): StudyPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyPilotDatabase::class.java,
                    "studypilot_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
