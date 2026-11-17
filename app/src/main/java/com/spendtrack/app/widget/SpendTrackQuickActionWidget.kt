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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spendtrack.app.MainActivity
import com.spendtrack.app.R

class SpendTrackQuickActionWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickActionWidgetContent()
            }
        }
    }
}

@Composable
fun QuickActionWidgetContent() {
    val context = LocalContext.current

    val expenseIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_expense")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val incomeIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_income")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val transferIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("extra_action", "add_transfer")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // App icon badge
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .cornerRadius(18.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(actionStartActivity(expenseIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_spendtrack),
                contentDescription = "SpendTrack",
                modifier = GlanceModifier.size(20.dp)
            )
        }

        Spacer(GlanceModifier.width(6.dp))

        // + Expense Pill Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.primary)
                .clickable(actionStartActivity(expenseIntent))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "Add Expense",
                    modifier = GlanceModifier.size(13.dp)
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "Expense",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onPrimary
                    )
                )
            }
        }

        Spacer(GlanceModifier.width(6.dp))

        // + Income Pill Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionStartActivity(incomeIntent))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_income),
                    contentDescription = "Add Income",
                    modifier = GlanceModifier.size(13.dp)
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "Income",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSecondaryContainer
                    )
                )
            }
        }

        Spacer(GlanceModifier.width(6.dp))

        // + Transfer Pill Button
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.tertiaryContainer)
                .clickable(actionStartActivity(transferIntent))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_transfer),
                    contentDescription = "Add Transfer",
                    modifier = GlanceModifier.size(13.dp)
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "Transfer",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onTertiaryContainer
                    )
                )
            }
        }
    }
}
