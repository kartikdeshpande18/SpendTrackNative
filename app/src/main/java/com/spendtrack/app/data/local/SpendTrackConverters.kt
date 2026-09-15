package com.spendtrack.app.data.local

import androidx.room.TypeConverter

class SpendTrackConverters {
    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)
    @TypeConverter fun owedDirectionToString(value: OwedDirection): String = value.name
    @TypeConverter fun stringToOwedDirection(value: String): OwedDirection =
        runCatching { OwedDirection.valueOf(value) }.getOrDefault(OwedDirection.OWED_TO_ME)
    @TypeConverter fun fixedDepositStatusToString(value: FixedDepositStatus): String = value.name
    @TypeConverter fun stringToFixedDepositStatus(value: String): FixedDepositStatus =
        runCatching { FixedDepositStatus.valueOf(value) }.getOrDefault(FixedDepositStatus.ACTIVE)
    @TypeConverter fun compoundingFrequencyToString(value: CompoundingFrequency): String = value.name
    @TypeConverter fun stringToCompoundingFrequency(value: String): CompoundingFrequency =
        runCatching { CompoundingFrequency.valueOf(value) }.getOrDefault(CompoundingFrequency.QUARTERLY)
    @TypeConverter fun recurrenceFrequencyToString(value: RecurrenceFrequency): String = value.name
    @TypeConverter fun stringToRecurrenceFrequency(value: String): RecurrenceFrequency =
        runCatching { RecurrenceFrequency.valueOf(value) }.getOrDefault(RecurrenceFrequency.MONTHLY)
}
