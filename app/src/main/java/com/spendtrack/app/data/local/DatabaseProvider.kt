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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
                .also { instance = it }
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
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN destinationAccountId INTEGER")
        }
    }
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `fixed_deposits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `linkedAccountId` INTEGER NOT NULL, `principalPaise` INTEGER NOT NULL, `interestRateBps` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, `maturityDateEpochDay` INTEGER NOT NULL, `maturityAmountPaise` INTEGER NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL)")
        }
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE fixed_deposits ADD COLUMN compoundingFrequency TEXT NOT NULL DEFAULT 'QUARTERLY'")
            db.execSQL("ALTER TABLE fixed_deposits ADD COLUMN isExisting INTEGER NOT NULL DEFAULT 0")
        }
    }
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `description` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `type` TEXT NOT NULL, `frequency` TEXT NOT NULL, `startDateEpochDay` INTEGER NOT NULL, `endDateEpochDay` INTEGER, `nextOccurrenceEpochDay` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `isShared` INTEGER NOT NULL, `mySharePaise` INTEGER, `sharedWith` TEXT NOT NULL, `destinationAccountId` INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_occurrences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scheduleId` INTEGER NOT NULL, `occurrenceEpochDay` INTEGER NOT NULL, `transactionId` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_scheduleId_occurrenceEpochDay` ON `recurring_occurrences` (`scheduleId`, `occurrenceEpochDay`)")
        }
    }
    internal val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN minimumBalancePaise INTEGER NOT NULL DEFAULT 0")
        }
    }
    internal val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `import_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filename` TEXT NOT NULL, `fileHash` TEXT NOT NULL, `format` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `importTimestampEpochMs` INTEGER NOT NULL, `transactionsImported` INTEGER NOT NULL, `duplicatesSkipped` INTEGER NOT NULL)")
        }
    }
    internal val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isExcludedFromReports` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalExpenseId` INTEGER")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `splitDetails` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateEpochDay` ON `transactions` (`dateEpochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_destinationAccountId` ON `transactions` (`destinationAccountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_originalExpenseId` ON `transactions` (`originalExpenseId`)")

            db.execSQL("ALTER TABLE `owed` ADD COLUMN `originalAmountPaise` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `owed` ADD COLUMN `repaidAmountPaise` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `owed` SET `originalAmountPaise` = `amountPaise` WHERE `originalAmountPaise` = 0")

            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `interestRateBps` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `interestCalculationMethod` TEXT NOT NULL DEFAULT 'DAILY'")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `interestCreditFrequency` TEXT NOT NULL DEFAULT 'QUARTERLY'")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `lastInterestPostingEpochDay` INTEGER")

            db.execSQL("ALTER TABLE `categories` ADD COLUMN `colorHex` TEXT")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `iconName` TEXT")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `isDisabled` INTEGER NOT NULL DEFAULT 0")
        }
    }
    internal val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isLoanRepayment` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `transactions` SET `isLoanRepayment` = 1 WHERE `description` LIKE 'Loan Repayment:%'")
        }
    }
}
