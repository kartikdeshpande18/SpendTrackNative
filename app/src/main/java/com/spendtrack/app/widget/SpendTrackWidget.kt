package com.spendtrack.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spendtrack.app.MainActivity
import com.spendtrack.app.R
import com.spendtrack.app.data.local.TransactionType

class SpendTrackWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(100.dp, 100.dp)
        val MEDIUM = DpSize(220.dp, 100.dp)
        val LARGE = DpSize(220.dp, 220.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataProvider.loadSnapshot(context)
        provideContent {
            GlanceTheme {
                SpendTrackWidgetContent(snapshot)
            }
        }
    }
}

@Composable
fun SpendTrackWidgetContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val context = LocalContext.current
    val mainIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(mainIntent))
    ) {
        when {
            size.height >= 200.dp && size.width >= 200.dp -> {
                LargeDashboardWidget(snapshot, context)
            }
            size.width >= 190.dp -> {
                MediumBannerWidget(snapshot, context)
            }
            else -> {
                SmallCardWidget(snapshot, context)
            }
        }
    }
}

@Composable
private fun SmallCardWidget(snapshot: WidgetSnapshot, context: Context) {
    val addIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_transaction")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        // Header Row: SpendTrack Rupee Icon + TODAY label
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .cornerRadius(12.dp)
                    .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_spendtrack),
                    contentDescription = "SpendTrack",
                    modifier = GlanceModifier.size(16.dp)
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "TODAY",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                )
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // Big Metric: Today's Spend
        Text(
            text = WidgetDataProvider.formatMoney(snapshot.todaySpentPaise, snapshot.isLocked),
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            )
        )

        Spacer(GlanceModifier.height(2.dp))

        // Month total subtitle
        Text(
            text = if (snapshot.isLocked) "SpendTrack Locked" else "Month: ${WidgetDataProvider.formatCompactMoney(snapshot.monthSpentPaise)}",
            style = TextStyle(
                fontSize = 11.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )

        Spacer(GlanceModifier.defaultWeight())

        // Bottom Row: Net Worth + Quick Add Floating Pill Button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "Net: ${WidgetDataProvider.formatCompactMoney(snapshot.netWorthPaise, snapshot.isLocked)}",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(addIntent))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Add",
                        modifier = GlanceModifier.size(12.dp)
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "Add",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MediumBannerWidget(snapshot: WidgetSnapshot, context: Context) {
    val addIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_transaction")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val budgetIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "budgets")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Left Asymmetrical Container: Month Spend & Net Worth
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(18.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(20.dp)
                        .cornerRadius(10.dp)
                        .background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_spendtrack),
                        contentDescription = null,
                        modifier = GlanceModifier.size(13.dp)
                    )
                }
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "This Month",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = WidgetDataProvider.formatMoney(snapshot.monthSpentPaise, snapshot.isLocked),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = if (snapshot.isLocked) "SpendTrack Locked" else "Today: ${WidgetDataProvider.formatCompactMoney(snapshot.todaySpentPaise)} · Net: ${WidgetDataProvider.formatCompactMoney(snapshot.netWorthPaise)}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(GlanceModifier.width(8.dp))

        // Right Container: Top 3 Budgets
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(18.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(actionStartActivity(budgetIntent))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Top Budgets",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            if (snapshot.topBudgets.isNotEmpty()) {
                snapshot.topBudgets.take(3).forEachIndexed { index, budget ->
                    if (index > 0) {
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = budget.categoryName,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.onPrimaryContainer
                            )
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = budget.percentFormatted,
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary
                            )
                        )
                    }
                    Spacer(GlanceModifier.height(2.dp))
                    WidgetProgressBar(
                        progress = budget.progress,
                        modifier = GlanceModifier.fillMaxWidth().height(4.dp)
                    )
                }
            } else if (snapshot.topBudgetName.isNotBlank()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = snapshot.topBudgetName,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = snapshot.topBudgetPercentFormatted,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                WidgetProgressBar(
                    progress = snapshot.topBudgetProgress,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp)
                )
            } else {
                Text(
                    text = "No budgets set",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun LargeDashboardWidget(snapshot: WidgetSnapshot, context: Context) {
    val addExpenseIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_expense")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val addIncomeIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_income")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val budgetIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "budgets")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // 1. Header Bar: Logo + Month Name + Quick Action Pill
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_spendtrack),
                    contentDescription = "SpendTrack",
                    modifier = GlanceModifier.size(18.dp)
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text(
                    text = "SpendTrack",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                Text(
                    text = snapshot.monthName,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            // Floating Quick-Add Button
            Box(
                modifier = GlanceModifier
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(addExpenseIntent))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Add",
                        modifier = GlanceModifier.size(12.dp)
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "+ Add",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        // 2. Metric Pills Row: Today, Month, Net Worth
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Today Pill
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = WidgetDataProvider.formatCompactMoney(snapshot.todaySpentPaise, snapshot.isLocked),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                )
            }

            Spacer(GlanceModifier.width(6.dp))

            // Month Spend Pill
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Spending",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onPrimaryContainer)
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = WidgetDataProvider.formatCompactMoney(snapshot.monthSpentPaise, snapshot.isLocked),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
                )
            }

            Spacer(GlanceModifier.width(6.dp))

            // Net Worth Pill
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Net Worth",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSecondaryContainer)
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = WidgetDataProvider.formatCompactMoney(snapshot.netWorthPaise, snapshot.isLocked),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSecondaryContainer)
                )
            }
        }

        // 3. Top Budget Status
        if (snapshot.topBudgetName.isNotBlank()) {
            Spacer(GlanceModifier.height(8.dp))
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(14.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionStartActivity(budgetIntent))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "Budget: ${snapshot.topBudgetName}",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = "${WidgetDataProvider.formatCompactMoney(snapshot.topBudgetSpentPaise)} / ${WidgetDataProvider.formatCompactMoney(snapshot.topBudgetTotalPaise)} (${snapshot.topBudgetPercentFormatted})",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                WidgetProgressBar(
                    progress = snapshot.topBudgetProgress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp)
                )
            }
        }

        // 4. Recent Transactions Section
        if (snapshot.recentTransactions.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = "RECENT TRANSACTIONS",
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(GlanceModifier.height(4.dp))

            snapshot.recentTransactions.take(2).forEach { tx ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    val isTransfer = tx.transactionType == TransactionType.TRANSFER
                    val iconRes = when {
                        isTransfer -> R.drawable.ic_widget_transfer
                        tx.isExpense -> R.drawable.ic_widget_expense
                        else -> R.drawable.ic_widget_income
                    }
                    val iconBg = when {
                        isTransfer -> GlanceTheme.colors.secondaryContainer
                        tx.isExpense -> GlanceTheme.colors.surfaceVariant
                        else -> GlanceTheme.colors.primaryContainer
                    }
                    val prefix = when {
                        isTransfer -> ""
                        tx.isExpense -> "-"
                        else -> "+"
                    }
                    val amountColor = when {
                        isTransfer -> GlanceTheme.colors.onSurface
                        tx.isExpense -> GlanceTheme.colors.onSurface
                        else -> GlanceTheme.colors.primary
                    }

                    Box(
                        modifier = GlanceModifier
                            .size(18.dp)
                            .cornerRadius(9.dp)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(10.dp)
                        )
                    }
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = tx.description,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = prefix + WidgetDataProvider.formatMoney(tx.amountPaise),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetProgressBar(progress: Float, modifier: GlanceModifier) {
    val clampedProgress = progress.coerceIn(0.01f, 1f)
    Row(
        modifier = modifier
            .cornerRadius(3.dp)
            .background(GlanceTheme.colors.surfaceVariant),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(3.dp)
                .background(if (progress > 1f) GlanceTheme.colors.error else GlanceTheme.colors.primary)
        ) {}
        if (clampedProgress < 0.99f) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
            ) {}
        }
    }
}
