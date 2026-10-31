package com.spendtrack.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtrack.app.data.local.RecurrenceFrequency
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics

@Composable
fun RecurrenceFrequencySelector(
    selectedFrequency: RecurrenceFrequency,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSpendTrackHaptics()
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        RecurrenceFrequency.entries.forEach { freq ->
            FilterChip(
                selected = selectedFrequency == freq,
                onClick = {
                    haptics.selection()
                    onFrequencySelected(freq)
                },
                label = {
                    Text(
                        text = freq.displayName,
                        fontSize = if (freq == RecurrenceFrequency.YEARLY) 14.sp else 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
