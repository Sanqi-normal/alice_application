package com.example.alice

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<Schedule>
}