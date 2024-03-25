package com.example.objectdetection.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ResearchDataDao {
    @Insert
    suspend fun insert(user: ResearchDataDao)

    @Query("SELECT * FROM research_data")
    suspend fun getResearchData(): List<ResearchDataDao>

    @Query("DELETE FROM research_data")
    suspend fun clearResearchData()
}