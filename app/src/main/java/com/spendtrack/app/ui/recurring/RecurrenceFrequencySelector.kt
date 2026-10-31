package com.spendtrack.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.RecurrenceFrequency
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun RecurrenceFrequencySelector(
    selectedFrequency: RecurrenceFrequency,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSpendTrackHaptics()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
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
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
