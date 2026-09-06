package com.spendtrack.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
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

private data class ChartSlice(val label: String, val amountPaise: Long, val color: Color)

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
    val slices = transactions
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { transaction -> categories.find { it.id == transaction.categoryId }?.name ?: "Other" }
        .map { (name, values) -> name to values.sumOf { it.mySharePaise ?: it.amountPaise } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }
        .mapIndexed { index, item -> ChartSlice(item.first, item.second, palette[index % palette.size]) }
        .toList()
    val total = slices.sumOf { it.amountPaise }

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
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(184.dp)) {
                        val diameter = min(size.width, size.height)
                        val insetX = (size.width - diameter) / 2f
                        val insetY = (size.height - diameter) / 2f
                        val stroke = 28.dp.toPx()
                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = 360f * slice.amountPaise.toFloat() / total.toFloat()
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(insetX + stroke / 2f, insetY + stroke / 2f),
                                size = androidx.compose.ui.geometry.Size(diameter - stroke, diameter - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total spent", style = MaterialTheme.typography.labelLarge, color = onSurfaceVariant)
                        Text(chartMoney(total), style = MaterialTheme.typography.headlineSmall, color = onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                slices.forEach { slice ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                    val gap = 10.dp.toPx()
                    val barWidth = (size.width - gap * (values.size - 1)) / values.size
                    val baseline = size.height - 24.dp.toPx()
                    val chartTop = 32.dp.toPx()
                    val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = axisLabel.toArgb()
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    values.forEachIndexed { index, value ->
                        val height = (baseline - chartTop) * value.toFloat() / maxValue.toFloat()
                        val left = index * (barWidth + gap)
                        drawRoundRect(
                            color = if (index == values.lastIndex) primary else inactiveBar,
                            topLeft = androidx.compose.ui.geometry.Offset(left, baseline - height),
                            size = androidx.compose.ui.geometry.Size(barWidth, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        )
                        if (value > 0L) {
                            drawContext.canvas.nativeCanvas.drawText(
                                chartBarMoney(value),
                                left + barWidth / 2,
                                max(chartTop - 4.dp.toPx(), baseline - height - 8.dp.toPx()),
                                valuePaint,
                            )
                        }
                    }
                    dates.forEachIndexed { index, date ->
                        drawContext.canvas.nativeCanvas.drawText(
                            date.format(DateTimeFormatter.ofPattern("E", Locale.getDefault())).take(1),
                            index * (barWidth + gap) + barWidth / 2,
                            size.height,
                            android.graphics.Paint().apply {
                                color = axisLabel.toArgb()
                                textSize = 11.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            },
                        )
                    }
                }
                Text("${dates.first().format(labelFormatter)} – ${dates.last().format(labelFormatter)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun chartMoney(paise: Long) = "₹" + String.format(Locale.US, "%,.2f", paise / 100.0)

private fun chartBarMoney(paise: Long): String {
    val rupees = paise / 100.0
    return if (rupees >= 10_000) "₹" + String.format(Locale.US, "%.1fK", rupees / 1_000) else "₹" + String.format(Locale.US, "%.0f", rupees)
}
