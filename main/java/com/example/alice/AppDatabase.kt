package com.example.alice

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Schedule::class], version = 2) // 版本从 1 升到 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 eventTime 列，默认为 NULL
                database.execSQL("ALTER TABLE schedules ADD COLUMN eventTime INTEGER DEFAULT NULL")
            }
        }
    }
}