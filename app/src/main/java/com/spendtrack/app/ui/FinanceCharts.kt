package com.spendtrack.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.animation.SpendTrackMotion
import com.spendtrack.app.ui.animation.ChartAnimationState
import java.time.LocalDate
import java.time.YearMonth
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
            Text("Monthly spending by category", style = MaterialTheme.typography.titleLarge)
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
    val labelFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val monthName = selectedMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + selectedMonth.year
    val subLabel = when {
        isCurrentMonth -> "Last 7 Days"
        isFutureMonth -> "Future Month"
        else -> "Final 7 Days of $monthName"
    }
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
            Text("Expenses in the last 7 days", style = MaterialTheme.typography.titleLarge)
            Text(subLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isFutureMonth) {
                Text("No transactions for $monthName", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (values.all { it == 0L }) {
                Text(if (isCurrentMonth) "No transactions in the last 7 days" else "No transactions in the final 7 days of $monthName", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                    if (size.width <= 0f || size.height <= 0f || values.isEmpty()) return@Canvas
                    val gap = 10.dp.toPx()
                    val barWidth = (size.width - gap * (values.size - 1)) / values.size
                    if (barWidth <= 0f) return@Canvas
                    val baseline = size.height - 24.dp.toPx()
                    val chartTop = 48.dp.toPx()
                    val labelAlpha = ((barProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = onSurface.toArgb()
                        textSize = 10.5.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        alpha = (labelAlpha * 255).toInt()
                    }
                    val dayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = axisLabel.toArgb()
                        textSize = 11.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    values.forEachIndexed { index, value ->
                        val targetBarHeight = if (value > 0L) {
                            ((baseline - chartTop) * value.toFloat() / maxValue.toFloat()).coerceAtLeast(6.dp.toPx())
                        } else 0f
                        val barHeight = targetBarHeight * barProgress
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

                            if (pointerBottom > pointerTop && labelAlpha > 0f) {
                                drawLine(
                                    color = (if (index == values.lastIndex) primary else axisLabel).copy(alpha = labelAlpha),
                                    start = androidx.compose.ui.geometry.Offset(barCenterX, pointerBottom),
                                    end = androidx.compose.ui.geometry.Offset(barCenterX, pointerTop),
                                    strokeWidth = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }

                            if (labelAlpha > 0f) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    chartBarMoney(value),
                                    barCenterX,
                                    labelBaseline,
                                    valuePaint,
                                )
                            }
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
                if (dates.isNotEmpty()) {
                    Text(
                        "${dates.first().format(labelFormatter)} – ${dates.last().format(labelFormatter)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
            Text("Income vs expense", style = MaterialTheme.typography.titleLarge)
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
