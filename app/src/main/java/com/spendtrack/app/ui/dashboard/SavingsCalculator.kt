package com.spendtrack.app.ui.dashboard

import com.spendtrack.app.data.local.AccountEntity
import kotlin.math.max

object SavingsCalculator {

    /**
     * Calculates Available (Spendable) Savings across all non-credit-card savings/bank accounts.
     *
     * In the SpendTrack model, non-credit-card accounts represent savings/bank accounts.
     * Formula for each account:
     *   available = max(account.balancePaise - account.minimumBalancePaise, 0L)
     *
     * Critical Financial Invariant:
     * - Minimum balance represents bank requirement to avoid penalty, but the funds
     *   still belong to the user.
     * - Net Worth and actual account balance MUST NOT subtract minimum balance.
     * - Only Available Savings uses minimum balance.
     */
    fun calculateAvailableSavingsPaise(accounts: List<AccountEntity>): Long {
        return accounts
            .filterNot { it.isCreditCard }
            .sumOf { max(it.balancePaise - it.minimumBalancePaise, 0L) }
    }
}
