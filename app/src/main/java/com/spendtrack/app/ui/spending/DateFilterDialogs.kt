package com.spendtrack.app.ui.spending

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    initialStartEpochDay: Long? = null,
    initialEndEpochDay: Long? = null,
    onDismiss: () -> Unit,
    onApply: (startEpochDay: Long, endEpochDay: Long) -> Unit,
) {
    val haptics = rememberSpendTrackHaptics()
    val today = LocalDate.now()
    var startDay by remember { mutableLongStateOf(initialStartEpochDay ?: today.minusDays(7).toEpochDay()) }
    var endDay by remember { mutableLongStateOf(initialEndEpochDay ?: today.toEpochDay()) }
    var pickingForStart by remember { mutableStateOf(false) }
    var pickingForEnd by remember { mutableStateOf(false) }

    val isValid = startDay <= endDay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Date Range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Select inclusive start and end dates:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { haptics.click(); pickingForStart = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Start Date")
                        Text(LocalDate.ofEpochDay(startDay).toString())
                    }
                }
                OutlinedButton(
                    onClick = { haptics.click(); pickingForEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("End Date")
                        Text(LocalDate.ofEpochDay(endDay).toString())
                    }
                }
                if (!isValid) {
                    Text(
                        "End date cannot be before start date.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        haptics.confirm()
                        onApply(startDay, endDay)
                    }
                },
                enabled = isValid
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("Cancel")
            }
        }
    )

    if (pickingForStart) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(startDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingForStart = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        startDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickingForStart = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingForStart = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingForEnd) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(endDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingForEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        endDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickingForEnd = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingForEnd = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun MonthSelectorDialog(
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
) {
    val haptics = rememberSpendTrackHaptics()
    var displayYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }
    val currentMonth = YearMonth.now()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Year Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select Month",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { haptics.selection(); displayYear-- },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year")
                        }
                        Text(
                            "$displayYear",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { haptics.selection(); displayYear++ },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                        }
                    }
                }

                // Month Grid (4 rows of 3 months)
                val months = (1..12).map { YearMonth.of(displayYear, it) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.chunked(3).forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEach { ym ->
                                val isSelected = ym == selectedMonth
                                val isCurrent = ym == currentMonth
                                val shortName = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

                                Surface(
                                    onClick = {
                                        haptics.confirm()
                                        onSelectMonth(ym)
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        isCurrent -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    border = if (isCurrent && !isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = shortName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isCurrent -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom actions: This Month & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptics.confirm()
                            onSelectMonth(currentMonth)
                        }
                    ) {
                        Text("This Month")
                    }
                    TextButton(onClick = { haptics.click(); onDismiss() }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

