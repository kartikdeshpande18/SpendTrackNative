package com.spendtrack.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private data class ChartSlice(
    val label: String,
    val amountPaise: Long,
    val color: Color,
    val percentage: Float,
    val percentageFormatted: String,
)

@Composable
fun CategorySpendingDonut(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val expenseItems = transactions
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { transaction -> categories.find { it.id == transaction.categoryId }?.name ?: "Other" }
        .map { (name, values) -> name to values.sumOf { it.mySharePaise ?: it.amountPaise } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }
        .toList()
    val total = expenseItems.sumOf { it.second }

    val slices = expenseItems.mapIndexed { index, item ->
        val pct = if (total > 0L) (item.second.toDouble() / total.toDouble() * 100.0).toFloat() else 0f
        val pctFormatted = when {
            pct >= 99.95f -> "100%"
            pct == pct.toInt().toFloat() -> "${pct.toInt()}%"
            else -> String.format(Locale.US, "%.1f%%", pct)
        }
        ChartSlice(
            label = item.first,
            amountPaise = item.second,
            color = palette[index % palette.size],
            percentage = pct,
            percentageFormatted = pctFormatted,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly Spending by Category", style = MaterialTheme.typography.titleLarge)
            if (slices.isEmpty()) {
                Text("No expense data for this selection", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val onSurface = MaterialTheme.colorScheme.onSurface
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val outerRadius = 92.dp.toPx()
                        val strokeWidth = 28.dp.toPx()
                        val arcRadius = outerRadius - strokeWidth / 2f

                        // 1. Draw Donut Slices
                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = 360f * slice.amountPaise.toFloat() / total.toFloat()
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(centerX - arcRadius, centerY - arcRadius),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                            )
                            startAngle += sweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total spent", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
                        Text(chartMoney(total), style = MaterialTheme.typography.titleMedium, color = onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                slices.forEach { slice ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                        Spacer(Modifier.width(10.dp))
                        Text(slice.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(chartMoney(slice.amountPaise), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTransactionBarChart(transactions: List<TransactionEntity>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = dates.map { date ->
        transactions.filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == date }
            .sumOf { it.mySharePaise ?: it.amountPaise }
    }
    val maxValue = max(values.maxOrNull() ?: 0L, 1L)
    val labelFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val primary = MaterialTheme.colorScheme.primary
    val inactiveBar = MaterialTheme.colorScheme.secondaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val axisLabel = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Expenses in the Last 7 Days", style = MaterialTheme.typography.titleLarge)
            Text("Last 7 days", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (values.all { it == 0L }) {
                Text("No transactions in the last 7 days", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                    val gap = 10.dp.toPx()
                    val barWidth = (size.width - gap * (values.size - 1)) / values.size
                    val baseline = size.height - 24.dp.toPx()
                    val chartTop = 48.dp.toPx()
                    val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurface.toArgb()
                        textSize = 10.5.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val dayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = axisLabel.toArgb()
                        textSize = 11.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    values.forEachIndexed { index, value ->
                        val barHeight = if (value > 0L) {
                            ((baseline - chartTop) * value.toFloat() / maxValue.toFloat()).coerceAtLeast(6.dp.toPx())
                        } else 0f
                        val left = index * (barWidth + gap)
                        val barCenterX = left + barWidth / 2f
                        val barColor = if (index == values.lastIndex) primary else inactiveBar

                        if (barHeight > 0f) {
                            val barTop = baseline - barHeight
                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(left, barTop),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            )

                            // Position the label just above the top of the bar with a vertical indicator line
                            val labelBaseline = (barTop - 13.dp.toPx()).coerceAtLeast(18.dp.toPx())
                            val pointerBottom = barTop - 2.dp.toPx()
                            val pointerTop = (labelBaseline + 3.dp.toPx()).coerceAtMost(pointerBottom - 2.dp.toPx())

                            if (pointerBottom > pointerTop) {
                                drawLine(
                                    color = if (index == values.lastIndex) primary else axisLabel,
                                    start = androidx.compose.ui.geometry.Offset(barCenterX, pointerBottom),
                                    end = androidx.compose.ui.geometry.Offset(barCenterX, pointerTop),
                                    strokeWidth = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                chartBarMoney(value),
                                barCenterX,
                                labelBaseline,
                                valuePaint,
                            )
                        }
                    }

                    dates.forEachIndexed { index, date ->
                        drawContext.canvas.nativeCanvas.drawText(
                            date.format(DateTimeFormatter.ofPattern("E", Locale.getDefault())).take(1),
                            index * (barWidth + gap) + barWidth / 2f,
                            size.height,
                            dayPaint,
                        )
                    }
                }
                Text("${dates.first().format(labelFormatter)} – ${dates.last().format(labelFormatter)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun chartMoney(paise: Long) = "₹" + String.format(Locale.US, "%,.2f", paise / 100.0)

private fun chartBarMoney(paise: Long): String {
    val rupees = paise / 100.0
    return when {
        rupees >= 100_000 -> "₹" + String.format(Locale.US, "%.1fL", rupees / 100_000)
        rupees >= 10_000 -> "₹" + String.format(Locale.US, "%.1fK", rupees / 1_000)
        rupees % 1.0 == 0.0 -> "₹" + String.format(Locale.US, "%.0f", rupees)
        else -> "₹" + String.format(Locale.US, "%.2f", rupees)
    }
}
