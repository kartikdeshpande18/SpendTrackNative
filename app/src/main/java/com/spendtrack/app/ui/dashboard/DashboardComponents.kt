package com.spendtrack.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendtrack.app.ui.animation.SpendTrackMotion

@Composable
fun TopBudgetCategoryCard(
    item: TopBudgetItem,
    modifier: Modifier = Modifier,
) {
    val isOverBudget = item.spentPaise > item.budgetPaise
    val percentageColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val progressColor = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val animatedProgress by animateFloatAsState(
        targetValue = item.visualProgress,
        animationSpec = tween(
            durationMillis = SpendTrackMotion.DurationNormal,
            easing = SpendTrackMotion.DecelEasing
        ),
        label = "TopBudgetProgress_${item.categoryId}"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.utilizationPercentageFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    color = percentageColor
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Text(
                text = "${TopBudgetsCalculator.formatCompactMoney(item.spentPaise)} / ${TopBudgetsCalculator.formatCompactMoney(item.budgetPaise)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TopBudgetCategoriesEmptyCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No budgets set yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Set category budgets in Settings to track utilization here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
