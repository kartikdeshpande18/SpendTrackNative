package com.spendtrack.app.ui.recurring

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spendtrack.app.data.local.RecurrenceFrequency
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecurrenceFrequencySelector(
    selectedFrequency: RecurrenceFrequency,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSpendTrackHaptics()
    val entries = RecurrenceFrequency.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        entries.forEachIndexed { index, freq ->
            SegmentedButton(
                selected = selectedFrequency == freq,
                onClick = {
                    haptics.selection()
                    onFrequencySelected(freq)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                icon = {},
                label = {
                    Text(
                        text = freq.displayName,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
