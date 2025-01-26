package com.example.app

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Perform the necessary schema changes
        database.execSQL("ALTER TABLE subjects ADD COLUMN maxStudents INTEGER NOT NULL DEFAULT 30")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE subjects ADD COLUMN dayOfWeek TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE subjects ADD COLUMN time TEXT NOT NULL DEFAULT ''")
    }
}