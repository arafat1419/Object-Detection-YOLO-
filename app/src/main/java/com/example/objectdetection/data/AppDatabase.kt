package com.example.objectdetection.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ResearchData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun researchDataDao(): ResearchDataDao
}