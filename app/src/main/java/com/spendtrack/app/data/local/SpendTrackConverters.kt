package com.spendtrack.app.data.local

import androidx.room.TypeConverter

class SpendTrackConverters {
    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
    @TypeConverter fun owedDirectionToString(value: OwedDirection): String = value.name
    @TypeConverter fun stringToOwedDirection(value: String): OwedDirection = OwedDirection.valueOf(value)
}
