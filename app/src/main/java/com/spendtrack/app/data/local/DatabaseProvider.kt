package com.spendtrack.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile private var instance: SpendTrackDatabase? = null

    fun get(context: Context): SpendTrackDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SpendTrackDatabase::class.java,
                "spendtrack.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `card_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardAccountId` INTEGER NOT NULL, `sourceAccountId` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL)")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN sharedWith TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE owed ADD COLUMN sourceTransactionId INTEGER")
        }
    }
}
