package com.spendtrack.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import com.spendtrack.app.widget.SpendTrackQuickActionWidget
import com.spendtrack.app.widget.SpendTrackWidget
import com.spendtrack.app.widget.WidgetDataProvider
import com.spendtrack.app.widget.WidgetSnapshot
import com.spendtrack.app.widget.WidgetTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataProviderTest {

    @Test
    fun testFormatMoney() {
        assertEquals("₹0", WidgetDataProvider.formatMoney(0L))
        assertEquals("₹500", WidgetDataProvider.formatMoney(50000L))
        assertEquals("₹1,250", WidgetDataProvider.formatMoney(125000L))
        assertEquals("₹10,000", WidgetDataProvider.formatMoney(1000000L))
    }

    @Test
    fun testFormatCompactMoney() {
        assertEquals("₹450", WidgetDataProvider.formatCompactMoney(45000L))
        assertEquals("₹15.5K", WidgetDataProvider.formatCompactMoney(1550000L))
        assertEquals("₹1.5L", WidgetDataProvider.formatCompactMoney(15000000L))
        assertEquals("₹2.5Cr", WidgetDataProvider.formatCompactMoney(2500000000L))
    }

    @Test
    fun testWidgetSnapshotDefaults() {
        val snapshot = WidgetSnapshot(
            todaySpentPaise = 45000L,
            monthSpentPaise = 1500000L,
            netWorthPaise = 50000000L,
            topBudgetName = "Groceries",
            topBudgetPercentFormatted = "45%",
            topBudgetProgress = 0.45f
        )
        assertEquals(45000L, snapshot.todaySpentPaise)
        assertEquals(1500000L, snapshot.monthSpentPaise)
        assertEquals(50000000L, snapshot.netWorthPaise)
        assertEquals("Groceries", snapshot.topBudgetName)
        assertEquals("45%", snapshot.topBudgetPercentFormatted)
        assertEquals(0.45f, snapshot.topBudgetProgress, 0.001f)
    }

    @Test
    fun testWidgetBreakpoints() {
        val widget = SpendTrackWidget()
        assertTrue(widget.sizeMode is SizeMode.Responsive)
        val responsive = widget.sizeMode as SizeMode.Responsive
        val sizes = responsive.sizes
        assertTrue(sizes.contains(DpSize(100.dp, 100.dp)))
        assertTrue(sizes.contains(DpSize(220.dp, 100.dp)))
        assertTrue(sizes.contains(DpSize(220.dp, 220.dp)))
    }

    @Test
    fun testQuickActionWidgetSizeMode() {
        val widget = SpendTrackQuickActionWidget()
        assertTrue(widget.sizeMode is SizeMode.Exact)
    }

    @Test
    fun testWidgetTransactionModel() {
        val tx = WidgetTransaction(
            id = 1L,
            description = "Coffee",
            categoryName = "Food",
            amountPaise = 15000L,
            isExpense = true,
            dateText = "Today"
        )
        assertEquals(1L, tx.id)
        assertEquals("Coffee", tx.description)
        assertEquals("Food", tx.categoryName)
        assertEquals(15000L, tx.amountPaise)
        assertTrue(tx.isExpense)
        assertEquals("Today", tx.dateText)
    }
}
