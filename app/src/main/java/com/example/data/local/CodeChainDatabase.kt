package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SyncedOtpEntity::class], version = 2, exportSchema = false)
abstract class CodeChainDatabase : RoomDatabase() {
    abstract fun syncedOtpDao(): SyncedOtpDao

    companion object {
        @Volatile
        private var INSTANCE: CodeChainDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE synced_otps ADD COLUMN chainId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): CodeChainDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CodeChainDatabase::class.java,
                    "codechain_local.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
