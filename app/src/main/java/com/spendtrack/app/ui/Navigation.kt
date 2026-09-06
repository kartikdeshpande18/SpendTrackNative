package com.spendtrack.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : TopLevelDestination("home", "Home", Icons.Outlined.Home)
    data object Spending : TopLevelDestination("spending", "Spending", Icons.Outlined.Payments)
    data object LoansOwed : TopLevelDestination("loans_owed", "Loans & Owed", Icons.Outlined.People)
    data object Settings : TopLevelDestination("settings", "Settings", Icons.Outlined.Settings)
}

val topLevelDestinations = listOf(TopLevelDestination.Home, TopLevelDestination.Spending, TopLevelDestination.LoansOwed, TopLevelDestination.Settings)
