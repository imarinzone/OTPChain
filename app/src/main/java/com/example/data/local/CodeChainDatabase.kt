package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SyncedOtpEntity::class], version = 1, exportSchema = false)
abstract class CodeChainDatabase : RoomDatabase() {
    abstract fun syncedOtpDao(): SyncedOtpDao

    companion object {
        @Volatile
        private var INSTANCE: CodeChainDatabase? = null

        fun getInstance(context: Context): CodeChainDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CodeChainDatabase::class.java,
                    "codechain_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
