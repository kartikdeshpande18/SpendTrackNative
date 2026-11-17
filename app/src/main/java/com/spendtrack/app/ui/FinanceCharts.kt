package com.spendtrack.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.animation.ChartAnimationState
import com.spendtrack.app.ui.animation.SpendTrackMotion
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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

private data class DonutData(
    val slices: List<ChartSlice>,
    val total: Long,
)

private data class DailyBarData(
    val dates: List<LocalDate>,
    val values: List<Long>,
    val maxValue: Long,
    val isCurrentMonth: Boolean,
    val isFutureMonth: Boolean,
)

private data class IncomeExpenseMetrics(
    val expensePaise: Long,
    val incomePaise: Long,
    val total: Long,
)

@Composable
private fun chartPalette() = with(MaterialTheme.colorScheme) {
    listOf(
        primary,
        secondary,
        tertiary,
        error,
        primaryContainer,
        secondaryContainer,
        tertiaryContainer,
        inversePrimary,
        onPrimaryContainer,
        onSecondaryContainer,
        onTertiaryContainer,
        surfaceTint,
    )
}

@Composable
fun CategorySpendingDonut(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
) {
    val palette = chartPalette()
    val donutData = remember(transactions, categories, palette) {
        val categoryMap = categories.associateBy { it.id }
        val expenseTotals = HashMap<String, Long>()
        for (t in transactions) {
            if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations) {
                val name = categoryMap[t.categoryId]?.name ?: "Other"
                val amount = t.mySharePaise ?: t.amountPaise
                expenseTotals[name] = (expenseTotals[name] ?: 0L) + amount
            }
        }
        val expenseItems = expenseTotals.filter { it.value > 0L }.toList().sortedByDescending { it.second }
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
        DonutData(slices = slices, total = total)
    }
    val slices = donutData.slices
    val total = donutData.total

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Monthly spending by category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (slices.isEmpty()) {
                Text("No expense data for this selection", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val shouldAnimate = remember { ChartAnimationState.shouldAnimate("CategorySpendingDonut") }
                val animatable = remember { Animatable(if (shouldAnimate) 0f else 1f) }
                LaunchedEffect(total, slices.size) {
                    if (shouldAnimate && animatable.value < 1f) {
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SpendTrackMotion.DurationChart,
                                easing = SpendTrackMotion.DecelEasing,
                            ),
                        )
                    }
                }
                val sweepProgress = animatable.value

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
                            val sweep = 360f * slice.amountPaise.toFloat() / total.toFloat() * sweepProgress
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
                        Text(chartMoney(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
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
fun DailyTransactionBarChart(
    transactions: List<TransactionEntity>,
    selectedMonth: YearMonth = YearMonth.now(),
    modifier: Modifier = Modifier
) {
    val chartData = remember(transactions, selectedMonth) {
        val currentMonth = YearMonth.now()
        val today = LocalDate.now()
        val isCurrentMonth = selectedMonth == currentMonth
        val isFutureMonth = selectedMonth > currentMonth

        val dates = when {
            isCurrentMonth -> (6 downTo 0).map { today.minusDays(it.toLong()) }
            isFutureMonth -> emptyList()
            else -> {
                val lastDay = selectedMonth.atEndOfMonth()
                (6 downTo 0).map { lastDay.minusDays(it.toLong()) }
            }
        }
        val epochDayToAmount = HashMap<Long, Long>()
        val dateSet = dates.map { it.toEpochDay() }.toSet()
        for (t in transactions) {
            if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations && t.dateEpochDay in dateSet) {
                epochDayToAmount[t.dateEpochDay] = (epochDayToAmount[t.dateEpochDay] ?: 0L) + (t.mySharePaise ?: t.amountPaise)
            }
        }
        val values = dates.map { date -> epochDayToAmount[date.toEpochDay()] ?: 0L }
        val maxValue = max(values.maxOrNull() ?: 0L, 1L)
        DailyBarData(dates, values, maxValue, isCurrentMonth, isFutureMonth)
    }

    val dates = chartData.dates
    val values = chartData.values
    val maxValue = chartData.maxValue
    val isCurrentMonth = chartData.isCurrentMonth
    val isFutureMonth = chartData.isFutureMonth
    val labelFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val monthName = remember(selectedMonth) {
        selectedMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + selectedMonth.year
    }

    val totalSpend = values.sum()
    val dailyAvg = if (values.isNotEmpty()) totalSpend / values.size else 0L

    var selectedIndex by remember(values) { mutableStateOf<Int?>(null) }
    val haptics = rememberSpendTrackHaptics()

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row (Single Line)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCurrentMonth) "Trailing 7 Days Spending" else monthName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (dates.isNotEmpty()) {
                    Text(
                        text = "${dates.first().format(labelFormatter)} – ${dates.last().format(labelFormatter)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Big Metric Display (Google Health style)
            if (!isFutureMonth && values.any { it > 0L }) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayAmount = if (selectedIndex != null && selectedIndex!! in values.indices) {
                        values[selectedIndex!!]
                    } else {
                        totalSpend
                    }
                    val displaySubtitle = if (selectedIndex != null && selectedIndex!! in dates.indices) {
                        dates[selectedIndex!!].format(labelFormatter)
                    } else {
                        "daily avg ${chartMoney(dailyAvg)}"
                    }

                    Text(
                        text = chartMoney(displayAmount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isFutureMonth) {
                Text("No transactions for $monthName", color = onSurfaceVariant)
            } else if (values.all { it == 0L }) {
                Text(
                    if (isCurrentMonth) "No transactions in the last 7 days" else "No transactions in the final 7 days of $monthName",
                    color = onSurfaceVariant
                )
            } else {
                val shouldAnimate = remember { ChartAnimationState.shouldAnimate("DailyTransactionBarChart") }
                val animatable = remember { Animatable(if (shouldAnimate) 0f else 1f) }
                val chartKey = remember(transactions) { values.joinToString(",") }
                LaunchedEffect(chartKey) {
                    if (shouldAnimate && animatable.value < 1f) {
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SpendTrackMotion.DurationChart,
                                easing = SpendTrackMotion.DecelEasing,
                            ),
                        )
                    }
                }
                val barProgress = animatable.value

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(values) {
                            detectTapGestures { offset ->
                                val yAxisWidth = 36.dp.toPx()
                                val chartWidth = size.width - yAxisWidth
                                val gap = 10.dp.toPx()
                                val barWidth = (chartWidth - gap * (values.size - 1)) / values.size
                                val clickedIndex = (offset.x / (barWidth + gap)).toInt().coerceIn(0, values.lastIndex)
                                haptics.selection()
                                selectedIndex = if (selectedIndex == clickedIndex) null else clickedIndex
                            }
                        }
                ) {
                    if (size.width <= 0f || size.height <= 0f || values.isEmpty()) return@Canvas

                    val yAxisWidth = 36.dp.toPx()
                    val chartWidth = size.width - yAxisWidth
                    val gap = 10.dp.toPx()
                    val barWidth = (chartWidth - gap * (values.size - 1)) / values.size
                    if (barWidth <= 0f) return@Canvas

                    val baseline = size.height - 24.dp.toPx()
                    val chartTop = 18.dp.toPx()
                    val chartHeight = baseline - chartTop

                    val yAxisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val dayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurfaceVariant.toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val dayActivePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = primary.toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // 1. Horizontal guidelines & right-aligned Y-axis labels
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    val yLevels = listOf(
                        Pair(chartTop, maxValue),
                        Pair(chartTop + chartHeight / 2f, maxValue / 2),
                        Pair(baseline, 0L)
                    )
                    yLevels.forEach { (y, value) ->
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.35f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            chartBarMoney(value),
                            size.width,
                            y + 3.5.dp.toPx(),
                            yAxisPaint
                        )
                    }

                    // 2. Average Benchmark Line (Google Health reference style)
                    if (dailyAvg > 0L) {
                        val avgY = baseline - (chartHeight * (dailyAvg.toFloat() / maxValue.toFloat())) * barProgress
                        drawLine(
                            color = secondary.copy(alpha = 0.75f),
                            start = androidx.compose.ui.geometry.Offset(0f, avgY),
                            end = androidx.compose.ui.geometry.Offset(chartWidth, avgY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // 3. Capsule / Pill Bars
                    val pillRadius = barWidth / 2f
                    values.forEachIndexed { index, value ->
                        val left = index * (barWidth + gap)
                        val isSelected = selectedIndex == index
                        val isToday = index == values.lastIndex

                        val barColor = when {
                            selectedIndex != null -> if (isSelected) primary else primary.copy(alpha = 0.28f)
                            isToday -> primary
                            else -> primary.copy(alpha = 0.38f)
                        }

                        if (value > 0L) {
                            val targetBarHeight = (chartHeight * value.toFloat() / maxValue.toFloat()).coerceAtLeast(barWidth)
                            val barHeight = targetBarHeight * barProgress
                            val barTop = baseline - barHeight

                            // Draw full capsule bar (top & bottom rounded)
                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(left, barTop),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillRadius, pillRadius)
                            )
                        } else {
                            // Empty day placeholder pill cap
                            drawRoundRect(
                                color = surfaceContainerHighest.copy(alpha = 0.6f),
                                topLeft = androidx.compose.ui.geometry.Offset(left, baseline - 4.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(barWidth, 4.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillRadius, pillRadius)
                            )
                        }

                        // X-axis day of week label
                        val dayLabel = dates[index].dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                        val isHighlighted = isSelected || (selectedIndex == null && isToday)
                        drawContext.canvas.nativeCanvas.drawText(
                            dayLabel,
                            left + pillRadius,
                            size.height - 2.dp.toPx(),
                            if (isHighlighted) dayActivePaint else dayPaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeVsExpenseChart(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(transactions) {
        var exp = 0L
        var inc = 0L
        for (t in transactions) {
            if (t.isExcludedFromCalculations) continue
            if (t.type == TransactionType.EXPENSE) {
                exp += (t.mySharePaise ?: t.amountPaise)
            } else if (t.type == TransactionType.INCOME) {
                inc += t.amountPaise
            }
        }
        IncomeExpenseMetrics(exp, inc, exp + inc)
    }
    val expensePaise = metrics.expensePaise
    val incomePaise = metrics.incomePaise
    val total = metrics.total

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Income vs expense",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (total == 0L) {
                Text("No income or expense data for this selection", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val shouldAnimate = remember { ChartAnimationState.shouldAnimate("IncomeVsExpenseChart") }
                val animatable = remember { Animatable(if (shouldAnimate) 0f else 1f) }
                LaunchedEffect(total, incomePaise, expensePaise) {
                    if (shouldAnimate && animatable.value < 1f) {
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SpendTrackMotion.DurationChart,
                                easing = SpendTrackMotion.DecelEasing,
                            ),
                        )
                    }
                }
                val sweepProgress = animatable.value

                val incomeColor = MaterialTheme.colorScheme.tertiary
                val expenseColor = MaterialTheme.colorScheme.primary
                val onPrimary = MaterialTheme.colorScheme.onPrimary
                val onError = MaterialTheme.colorScheme.onError
                val onSurface = MaterialTheme.colorScheme.onSurface

                val incomePct = (incomePaise.toDouble() / total.toDouble() * 100.0).toFloat()
                val expensePct = (expensePaise.toDouble() / total.toDouble() * 100.0).toFloat()

                val incomePctFormatted = when {
                    incomePaise == 0L -> ""
                    expensePaise == 0L || incomePct >= 99.95f -> "100%"
                    incomePct == incomePct.toInt().toFloat() -> "${incomePct.toInt()}%"
                    else -> String.format(Locale.US, "%.1f%%", incomePct)
                }
                val expensePctFormatted = when {
                    expensePaise == 0L -> ""
                    incomePaise == 0L || expensePct >= 99.95f -> "100%"
                    expensePct == expensePct.toInt().toFloat() -> "${expensePct.toInt()}%"
                    else -> String.format(Locale.US, "%.1f%%", expensePct)
                }

                val textMeasurer = rememberTextMeasurer()

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        val fullIncomeSweep = 360f * (incomePaise.toFloat() / total.toFloat())
                        val fullExpenseSweep = 360f * (expensePaise.toFloat() / total.toFloat())

                        val labelAlpha = if (sweepProgress >= 0.3f) ((sweepProgress - 0.3f) / 0.7f).coerceIn(0f, 1f) else 0f

                        // Pre-measure percentage labels to calculate safe dynamic chart radius
                        val incLayout = if (incomePaise > 0L && incomePctFormatted.isNotEmpty()) {
                            textMeasurer.measure(
                                text = incomePctFormatted,
                                style = TextStyle(
                                    color = incomeColor.copy(alpha = labelAlpha),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            )
                        } else null

                        val expLayout = if (expensePaise > 0L && expensePctFormatted.isNotEmpty()) {
                            textMeasurer.measure(
                                text = expensePctFormatted,
                                style = TextStyle(
                                    color = expenseColor.copy(alpha = labelAlpha),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            )
                        } else null

                        val maxLabelW = maxOf(incLayout?.size?.width ?: 0, expLayout?.size?.width ?: 0).toFloat()
                        val maxLabelH = maxOf(incLayout?.size?.height ?: 0, expLayout?.size?.height ?: 0).toFloat()
                        val labelGap = 8.dp.toPx()
                        val maxExplosionPx = 10.dp.toPx()
                        val cardSafetyMargin = 6.dp.toPx()

                        // Dynamically calculate maximum usable donut radius based on available canvas dimensions
                        val maxSafeRx = centerX - maxLabelW - labelGap - maxExplosionPx - cardSafetyMargin
                        val maxSafeRy = centerY - maxLabelH - labelGap - maxExplosionPx - cardSafetyMargin
                        val outerRadius = minOf(maxSafeRx, maxSafeRy).coerceAtLeast(64.dp.toPx())
                        val strokeWidth = (outerRadius * 0.26f).coerceIn(22.dp.toPx(), 28.dp.toPx())
                        val arcRadius = outerRadius - strokeWidth / 2f

                        // 1. Draw Income slice (starts at -90 deg, top, in standard position)
                        if (incomePaise > 0L) {
                            val sweep = fullIncomeSweep * sweepProgress
                            drawArc(
                                color = incomeColor,
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(centerX - arcRadius, centerY - arcRadius),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                            )
                        }

                        // 2. Draw Expense slice (visually exploded outward along its midpoint angle)
                        val expStartAngle = if (incomePaise > 0L) -90f + fullIncomeSweep else -90f
                        val expMidAngleDeg = expStartAngle + (fullExpenseSweep / 2f)
                        val expMidAngleRad = Math.toRadians(expMidAngleDeg.toDouble())
                        val cosExp = Math.cos(expMidAngleRad).toFloat()
                        val sinExp = Math.sin(expMidAngleRad).toFloat()

                        val explosionDist = if (incomePaise > 0L && expensePaise > 0L) maxExplosionPx * sweepProgress else 0f
                        val expCenterX = centerX + explosionDist * cosExp
                        val expCenterY = centerY + explosionDist * sinExp

                        if (expensePaise > 0L) {
                            val sweep = fullExpenseSweep * sweepProgress
                            drawArc(
                                color = expenseColor,
                                startAngle = expStartAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(expCenterX - arcRadius, expCenterY - arcRadius),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                            )
                        }

                        // 3. Draw percentage labels outside donut ring
                        if (labelAlpha > 0f) {
                            incLayout?.let { layout ->
                                val incMidAngleDeg = -90f + (fullIncomeSweep / 2f)
                                val incMidAngleRad = Math.toRadians(incMidAngleDeg.toDouble())
                                val cosA = Math.cos(incMidAngleRad).toFloat()
                                val sinA = Math.sin(incMidAngleRad).toFloat()

                                val halfW = layout.size.width / 2f
                                val halfH = layout.size.height / 2f
                                val absCos = Math.abs(cosA).coerceAtLeast(0.001f)
                                val absSin = Math.abs(sinA).coerceAtLeast(0.001f)
                                val innerRadialExtent = minOf(halfW / absCos, halfH / absSin)
                                val incLabelDist = outerRadius + labelGap + innerRadialExtent
                                val incLblX = centerX + incLabelDist * cosA
                                val incLblY = centerY + incLabelDist * sinA

                                drawText(
                                    textLayoutResult = layout,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        incLblX - halfW,
                                        incLblY - halfH
                                    )
                                )
                            }

                            expLayout?.let { layout ->
                                val halfW = layout.size.width / 2f
                                val halfH = layout.size.height / 2f
                                val absCos = Math.abs(cosExp).coerceAtLeast(0.001f)
                                val absSin = Math.abs(sinExp).coerceAtLeast(0.001f)
                                val innerRadialExtent = minOf(halfW / absCos, halfH / absSin)
                                val expLabelDist = outerRadius + labelGap + innerRadialExtent
                                // Moves outward with the exploded slice center
                                val expLblX = expCenterX + expLabelDist * cosExp
                                val expLblY = expCenterY + expLabelDist * sinExp

                                drawText(
                                    textLayoutResult = layout,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        expLblX - halfW,
                                        expLblY - halfH
                                    )
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = chartMoney(incomePaise),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(incomeColor))
                    Spacer(Modifier.width(10.dp))
                    Text("Income", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(chartMoney(incomePaise), style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(expenseColor))
                    Spacer(Modifier.width(10.dp))
                    Text("Expenses", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(chartMoney(expensePaise), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

enum class SpendingTrendPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingTrendChart(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier
) {
    var period by remember { mutableStateOf(SpendingTrendPeriod.WEEK) }
    var offset by remember(period) { mutableIntStateOf(0) }
    var selectedIndex by remember(period, offset) { mutableStateOf<Int?>(null) }
    val haptics = rememberSpendTrackHaptics()

    data class TrendBucket(
        val label: String,
        val fullLabel: String,
        val amountPaise: Long,
        val isAnchor: Boolean
    )

    data class TrendChartData(
        val buckets: List<TrendBucket>,
        val headerTitle: String,
        val totalSpend: Long,
        val averageSpend: Long,
        val peakSpend: Long,
        val activeCount: Int,
        val maxValue: Long
    )

    val chartData = remember(transactions, period, offset) {
        val today = LocalDate.now()
        when (period) {
            SpendingTrendPeriod.WEEK -> {
                val anchor = today.plusWeeks(offset.toLong())
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dates = (0..6).map { monday.plusDays(it.toLong()) }
                val dateMap = HashMap<Long, Long>()
                val dateSet = dates.map { it.toEpochDay() }.toSet()
                for (t in transactions) {
                    if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations && t.dateEpochDay in dateSet) {
                        val amt = t.mySharePaise ?: t.amountPaise
                        dateMap[t.dateEpochDay] = (dateMap[t.dateEpochDay] ?: 0L) + amt
                    }
                }
                val buckets = dates.map { d ->
                    val amt = dateMap[d.toEpochDay()] ?: 0L
                    val dayLabel = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                    val full = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                    TrendBucket(dayLabel, full, amt, isAnchor = d == today)
                }
                val total = buckets.sumOf { it.amountPaise }
                val avg = if (buckets.isNotEmpty()) total / buckets.size else 0L
                val peak = buckets.maxOfOrNull { it.amountPaise } ?: 0L
                val active = buckets.count { it.amountPaise > 0L }
                val startFmt = dates.first().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                val endFmt = dates.last().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                val header = "$startFmt – $endFmt"
                TrendChartData(buckets, header, total, avg, peak, active, max(peak, 1L))
            }
            SpendingTrendPeriod.MONTH -> {
                val currentYm = YearMonth.now()
                val targetYm = currentYm.plusMonths(offset.toLong())
                val daysCount = targetYm.lengthOfMonth()
                val dates = (1..daysCount).map { targetYm.atDay(it) }
                val dateMap = HashMap<Long, Long>()
                val dateSet = dates.map { it.toEpochDay() }.toSet()
                for (t in transactions) {
                    if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations && t.dateEpochDay in dateSet) {
                        val amt = t.mySharePaise ?: t.amountPaise
                        dateMap[t.dateEpochDay] = (dateMap[t.dateEpochDay] ?: 0L) + amt
                    }
                }
                val buckets = dates.map { d ->
                    val amt = dateMap[d.toEpochDay()] ?: 0L
                    val dayNum = d.dayOfMonth.toString()
                    val full = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                    TrendBucket(dayNum, full, amt, isAnchor = d == today)
                }
                val total = buckets.sumOf { it.amountPaise }
                val avg = if (buckets.isNotEmpty()) total / buckets.size else 0L
                val peak = buckets.maxOfOrNull { it.amountPaise } ?: 0L
                val active = buckets.count { it.amountPaise > 0L }
                val header = targetYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                TrendChartData(buckets, header, total, avg, peak, active, max(peak, 1L))
            }
            SpendingTrendPeriod.YEAR -> {
                val currentYear = java.time.Year.now()
                val targetYear = currentYear.plusYears(offset.toLong())
                val months = (1..12).map { YearMonth.of(targetYear.value, it) }
                val monthMap = HashMap<YearMonth, Long>()
                for (t in transactions) {
                    if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations) {
                        val ym = YearMonth.from(LocalDate.ofEpochDay(t.dateEpochDay))
                        if (ym.year == targetYear.value) {
                            val amt = t.mySharePaise ?: t.amountPaise
                            monthMap[ym] = (monthMap[ym] ?: 0L) + amt
                        }
                    }
                }
                val currentYm = YearMonth.now()
                val buckets = months.map { ym ->
                    val amt = monthMap[ym] ?: 0L
                    val monthInit = ym.month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                    val full = ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                    TrendBucket(monthInit, full, amt, isAnchor = ym == currentYm)
                }
                val total = buckets.sumOf { it.amountPaise }
                val avg = if (buckets.isNotEmpty()) total / buckets.size else 0L
                val peak = buckets.maxOfOrNull { it.amountPaise } ?: 0L
                val active = buckets.count { it.amountPaise > 0L }
                val header = targetYear.value.toString()
                TrendChartData(buckets, header, total, avg, peak, active, max(peak, 1L))
            }
        }
    }

    val buckets = chartData.buckets
    val headerTitle = chartData.headerTitle
    val totalSpend = chartData.totalSpend
    val averageSpend = chartData.averageSpend
    val peakSpend = chartData.peakSpend
    val activeCount = chartData.activeCount
    val maxValue = chartData.maxValue

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row (Single Line Date Range with < > Navigation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { haptics.click(); offset-- },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (offset < 0) { haptics.click(); offset++ } },
                        enabled = offset < 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = if (offset < 0) onSurfaceVariant else onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Big Metric Display (Google Health style)
            val selectedBucket = selectedIndex?.let { if (it in buckets.indices) buckets[it] else null }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val displayAmount = selectedBucket?.amountPaise ?: totalSpend
                val displaySubtitle = if (selectedBucket != null) {
                    selectedBucket.fullLabel
                } else {
                    "avg ${chartMoney(averageSpend)}/${if (period == SpendingTrendPeriod.YEAR) "mo" else "day"}"
                }
                Text(
                    text = chartMoney(displayAmount),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Interactive Bar Chart Canvas
            if (buckets.isEmpty()) {
                Text("No data available", color = onSurfaceVariant)
            } else {
                val shouldAnimate = remember { ChartAnimationState.shouldAnimate("SpendingTrendChart") }
                val animatable = remember { Animatable(if (shouldAnimate) 0f else 1f) }
                val chartKey = remember(period, offset, transactions) { "$period-$offset-${buckets.sumOf { it.amountPaise }}" }
                LaunchedEffect(chartKey) {
                    if (shouldAnimate && animatable.value < 1f) {
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SpendTrackMotion.DurationChart,
                                easing = SpendTrackMotion.DecelEasing,
                            ),
                        )
                    }
                }
                val barProgress = animatable.value

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(period, offset, buckets) {
                            detectTapGestures { tapOffset ->
                                val yAxisWidth = 36.dp.toPx()
                                val chartWidth = size.width - yAxisWidth
                                val count = buckets.size
                                val gap = if (count > 20) 2.dp.toPx() else 8.dp.toPx()
                                val barWidth = (chartWidth - gap * (count - 1)) / count
                                val clicked = (tapOffset.x / (barWidth + gap)).toInt().coerceIn(0, count - 1)
                                haptics.selection()
                                selectedIndex = if (selectedIndex == clicked) null else clicked
                            }
                        }
                ) {
                    if (size.width <= 0f || size.height <= 0f || buckets.isEmpty()) return@Canvas

                    val yAxisWidth = 36.dp.toPx()
                    val chartWidth = size.width - yAxisWidth
                    val count = buckets.size
                    val gap = if (count > 20) 2.dp.toPx() else 8.dp.toPx()
                    val barWidth = (chartWidth - gap * (count - 1)) / count
                    if (barWidth <= 0f) return@Canvas

                    val baseline = size.height - 24.dp.toPx()
                    val chartTop = 18.dp.toPx()
                    val chartHeight = baseline - chartTop

                    val yAxisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurfaceVariant.copy(alpha = 0.7f).toArgb()
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurfaceVariant.toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val labelActivePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = primary.toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // 1. Horizontal dashed guidelines & right-aligned Y-axis labels
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    val yLevels = listOf(
                        Pair(chartTop, maxValue),
                        Pair(chartTop + chartHeight / 2f, maxValue / 2),
                        Pair(baseline, 0L)
                    )
                    yLevels.forEach { (y, value) ->
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.35f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            chartBarMoney(value),
                            size.width,
                            y + 3.5.dp.toPx(),
                            yAxisPaint
                        )
                    }

                    // 2. Average Benchmark Line
                    if (averageSpend > 0L) {
                        val avgY = baseline - (chartHeight * (averageSpend.toFloat() / maxValue.toFloat())) * barProgress
                        drawLine(
                            color = secondary.copy(alpha = 0.75f),
                            start = androidx.compose.ui.geometry.Offset(0f, avgY),
                            end = androidx.compose.ui.geometry.Offset(chartWidth, avgY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // 3. Capsule Bars
                    val pillRadius = (barWidth / 2f).coerceAtMost(8.dp.toPx())
                    buckets.forEachIndexed { index, bucket ->
                        val left = index * (barWidth + gap)
                        val isSelected = selectedIndex == index
                        val barColor = when {
                            selectedIndex != null -> if (isSelected) primary else primary.copy(alpha = 0.28f)
                            bucket.isAnchor -> primary
                            else -> primary.copy(alpha = 0.38f)
                        }

                        if (bucket.amountPaise > 0L) {
                            val targetH = (chartHeight * bucket.amountPaise.toFloat() / maxValue.toFloat()).coerceAtLeast(barWidth)
                            val barH = targetH * barProgress
                            val barTop = baseline - barH

                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(left, barTop),
                                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillRadius, pillRadius)
                            )
                        } else {
                            drawRoundRect(
                                color = surfaceContainerHighest.copy(alpha = 0.6f),
                                topLeft = androidx.compose.ui.geometry.Offset(left, baseline - 3.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(barWidth, 3.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillRadius, pillRadius)
                            )
                        }

                        // X-axis label logic
                        val shouldDrawLabel = when (period) {
                            SpendingTrendPeriod.WEEK -> true
                            SpendingTrendPeriod.MONTH -> {
                                val day = index + 1
                                day == 1 || day == 8 || day == 15 || day == 22 || day == count
                            }
                            SpendingTrendPeriod.YEAR -> true
                        }
                        if (shouldDrawLabel) {
                            val isHighlighted = isSelected || (selectedIndex == null && bucket.isAnchor)
                            drawContext.canvas.nativeCanvas.drawText(
                                bucket.label,
                                left + barWidth / 2f,
                                size.height - 2.dp.toPx(),
                                if (isHighlighted) labelActivePaint else labelPaint
                            )
                        }
                    }
                }
            }

            // Period Segmented Selector (Week / Month / Year switcher buttons at the bottom)
            val periods = SpendingTrendPeriod.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = {
                            haptics.selection()
                            period = p
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        icon = {},
                        label = {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
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
