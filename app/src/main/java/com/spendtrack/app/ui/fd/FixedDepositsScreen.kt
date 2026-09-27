package com.spendtrack.app.ui.fd

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CompoundingFrequency
import com.spendtrack.app.data.local.FixedDepositCalculator
import com.spendtrack.app.data.local.FixedDepositEntity
import com.spendtrack.app.data.local.FixedDepositStatus
import com.spendtrack.app.ui.formatIndianMoney
import com.spendtrack.app.ui.formatEpochDate
import com.spendtrack.app.ui.parsePaise
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun FixedDepositsScreen(
    fixedDeposits: List<FixedDepositEntity>,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onAdd: (
        name: String,
        linkedAccountId: Long,
        principalPaise: Long,
        interestRateBps: Int,
        startDateEpochDay: Long,
        maturityDateEpochDay: Long,
        maturityAmountPaise: Long,
        description: String,
        compoundingFrequency: CompoundingFrequency,
        isExisting: Boolean
    ) -> Unit,
    onUpdate: (FixedDepositEntity) -> Unit,
    onDelete: (FixedDepositEntity) -> Unit,
    onMature: (Long) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var isFormVisible by remember { mutableStateOf(false) }
    var editingFd by remember { mutableStateOf<FixedDepositEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Matured

    val eligibleAccounts = remember(accounts) { accounts.filterNot { it.isCreditCard } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    if (isFormVisible) {
        FixedDepositFormScreen(
            accounts = eligibleAccounts,
            initial = editingFd,
            onBack = {
                isFormVisible = false
                editingFd = null
            },
            onSave = { fd ->
                if (editingFd == null) {
                    onAdd(
                        fd.name,
                        fd.linkedAccountId,
                        fd.principalPaise,
                        fd.interestRateBps,
                        fd.startDateEpochDay,
                        fd.maturityDateEpochDay,
                        fd.maturityAmountPaise,
                        fd.description,
                        fd.compoundingFrequency,
                        fd.isExisting
                    )
                } else {
                    onUpdate(fd)
                }
                isFormVisible = false
                editingFd = null
            },
            onDelete = { fd ->
                onDelete(fd)
                isFormVisible = false
                editingFd = null
            },
            onMature = { id ->
                onMature(id)
                isFormVisible = false
                editingFd = null
            }
        )
    } else {
        val (activeDeposits, maturedDeposits) = remember(fixedDeposits) {
            val active = ArrayList<FixedDepositEntity>()
            val matured = ArrayList<FixedDepositEntity>()
            for (fd in fixedDeposits) {
                if (fd.status == FixedDepositStatus.ACTIVE) active.add(fd)
                else if (fd.status == FixedDepositStatus.MATURED) matured.add(fd)
            }
            active to matured
        }
        val activePrincipalPaise = remember(activeDeposits) { activeDeposits.sumOf { it.principalPaise } }
        val activeMaturityPaise = remember(activeDeposits) { activeDeposits.sumOf { it.maturityAmountPaise } }
        val displayedDeposits = if (selectedTab == 0) activeDeposits else maturedDeposits
        val todayEpochDay = LocalDate.now().toEpochDay()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { haptics.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Fixed Deposits", style = MaterialTheme.typography.displaySmall)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.extraExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Principal", style = MaterialTheme.typography.titleMedium)
                        Text(money(activePrincipalPaise), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${activeDeposits.size} Active · ${maturedDeposits.size} Matured · Maturity Value: ${formatMaturityAmount(activeMaturityPaise)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        haptics.click()
                        editingFd = null
                        isFormVisible = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Fixed Deposit")
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { haptics.selection(); selectedTab = 0 },
                        label = { Text("Active (${activeDeposits.size})") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { haptics.selection(); selectedTab = 1 },
                        label = { Text("Matured (${maturedDeposits.size})") }
                    )
                }
            }

            if (displayedDeposits.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraExtraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text(
                                if (selectedTab == 0) "No active fixed deposits" else "No matured fixed deposits",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Tap 'Add Fixed Deposit' above to track a deposit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(displayedDeposits, key = { it.id }) { fd ->
                val accountName = accountMap[fd.linkedAccountId]?.name ?: "Account"
                val isDue = fd.status == FixedDepositStatus.ACTIVE && todayEpochDay >= fd.maturityDateEpochDay

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(fd.name, style = MaterialTheme.typography.titleLarge)
                                Text("Linked Account : $accountName", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (fd.isExisting) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Existing") }
                                    )
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(fd.status.name) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (fd.status == FixedDepositStatus.ACTIVE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Principal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(fd.principalPaise), style = MaterialTheme.typography.titleMedium)
                            }
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Interest Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f%%", fd.interestRateBps / 100.0), style = MaterialTheme.typography.titleMedium)
                                Text(fd.compoundingFrequency.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                                Text("Maturity Amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                                Text(formatMaturityAmount(fd.maturityAmountPaise), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${formatDate(fd.startDateEpochDay)} → ${formatDate(fd.maturityDateEpochDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (fd.description.isNotBlank()) {
                            Text(fd.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDue) {
                                Button(
                                    onClick = {
                                        haptics.confirm()
                                        onMature(fd.id)
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Mature Now")
                                }
                            }
                            IconButton(onClick = {
                                haptics.click()
                                editingFd = fd
                                isFormVisible = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit FD")
                            }
                            IconButton(onClick = {
                                haptics.reject()
                                onDelete(fd)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete FD", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FixedDepositFormScreen(
    accounts: List<AccountEntity>,
    initial: FixedDepositEntity? = null,
    onBack: () -> Unit,
    onSave: (FixedDepositEntity) -> Unit,
    onDelete: ((FixedDepositEntity) -> Unit)? = null,
    onMature: ((Long) -> Unit)? = null
) {
    val haptics = rememberSpendTrackHaptics()
    val isEditing = initial != null
    val isMatured = initial?.status == FixedDepositStatus.MATURED

    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var accountIndex by remember(initial?.id, accounts) {
        mutableIntStateOf(accounts.indexOfFirst { it.id == initial?.linkedAccountId }.coerceAtLeast(0))
    }
    var principal by remember(initial?.id) {
        mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.principalPaise / 100.0) }.orEmpty())
    }
    var interestRate by remember(initial?.id) {
        mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.interestRateBps / 100.0) }.orEmpty())
    }
    var startDateEpochDay by remember(initial?.id) {
        mutableLongStateOf(initial?.startDateEpochDay ?: LocalDate.now().toEpochDay())
    }
    var maturityDateEpochDay by remember(initial?.id) {
        mutableLongStateOf(initial?.maturityDateEpochDay ?: LocalDate.now().plusYears(1).toEpochDay())
    }
    var maturityAmount by remember(initial?.id) {
        mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.maturityAmountPaise / 100.0) }.orEmpty())
    }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var frequency by remember(initial?.id) { mutableStateOf(initial?.compoundingFrequency ?: CompoundingFrequency.QUARTERLY) }
    var isExisting by remember(initial?.id) { mutableStateOf(initial?.isExisting ?: false) }

    var accountMenuExpanded by remember { mutableStateOf(false) }
    var compoundingMenuExpanded by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showMaturityDatePicker by remember { mutableStateOf(false) }

    val selectedAccount = accounts.getOrNull(accountIndex)

    val principalPaise = parsePaise(principal)
    val rateBps = ((interestRate.toDoubleOrNull() ?: 0.0) * 100).toInt()
    val maturityAmountPaise = parsePaise(maturityAmount)

    val calculatedMaturityAmountPaise = remember(principal, interestRate, startDateEpochDay, maturityDateEpochDay, frequency, isMatured) {
        if (isMatured && initial != null) {
            initial.maturityAmountPaise
        } else {
            val p = parsePaise(principal)
            val r = ((interestRate.toDoubleOrNull() ?: 0.0) * 100).toInt()
            FixedDepositCalculator.calculateMaturityAmountPaise(
                principalPaise = p,
                interestRateBps = r,
                startDateEpochDay = startDateEpochDay,
                maturityDateEpochDay = maturityDateEpochDay,
                frequency = frequency
            )
        }
    }

    // Available balance calculation
    val availableBalance = if (selectedAccount == null) 0L else {
        if (isEditing && initial.status == FixedDepositStatus.ACTIVE && selectedAccount.id == initial.linkedAccountId && !initial.isExisting) {
            selectedAccount.balancePaise + initial.principalPaise
        } else {
            selectedAccount.balancePaise
        }
    }

    // Validation
    val isNameValid = name.isNotBlank()
    val isAccountValid = selectedAccount != null
    val isPrincipalValid = principalPaise > 0
    val isRateValid = rateBps >= 0
    val isDateValid = maturityDateEpochDay > startDateEpochDay
    val isMaturityAmountValid = calculatedMaturityAmountPaise >= principalPaise
    val isBalanceSufficient = isMatured || isExisting || availableBalance >= principalPaise

    val canSave = isNameValid && isAccountValid && isPrincipalValid && isRateValid && isDateValid && isMaturityAmountValid && isBalanceSufficient

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.click(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (initial == null) "Add Fixed Deposit" else if (isMatured) "Edit Matured Deposit" else "Edit Fixed Deposit",
                style = MaterialTheme.typography.displaySmall
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("FD name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Linked account selector
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (!isMatured) { haptics.click(); accountMenuExpanded = true } },
                modifier = Modifier.fillMaxWidth(),
                enabled = accounts.isNotEmpty() && !isMatured
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedAccount?.let { "Linked Account : ${it.name}" } ?: "Linked Account : Select account",
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    selectedAccount?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "(Avail: ${money(availableBalance)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                accounts.forEachIndexed { index, account ->
                    val accountAvail = if (isEditing && initial.status == FixedDepositStatus.ACTIVE && account.id == initial.linkedAccountId && !initial.isExisting) {
                        account.balancePaise + initial.principalPaise
                    } else {
                        account.balancePaise
                    }
                    DropdownMenuItem(
                        text = { Text("${account.name} · ${money(accountAvail)}") },
                        onClick = {
                            haptics.selection()
                            accountIndex = index
                            accountMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isMatured) { haptics.selection(); isExisting = !isExisting }
        ) {
            Checkbox(
                checked = isExisting,
                onCheckedChange = { haptics.selection(); isExisting = it },
                enabled = !isMatured
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text("Record as existing deposit", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Balance will not be deducted from the linked account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = principal,
            onValueChange = { principal = it },
            label = { Text("Principal amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = !isMatured,
            modifier = Modifier.fillMaxWidth()
        )

        if (!isBalanceSufficient && selectedAccount != null) {
            Text(
                "Insufficient balance. Available: ${money(availableBalance)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = interestRate,
            onValueChange = { interestRate = it },
            label = { Text("Interest rate (% p.a.)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = !isMatured,
            modifier = Modifier.fillMaxWidth()
        )

        // Compounding frequency selector
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (!isMatured) { haptics.click(); compoundingMenuExpanded = true } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMatured
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compounding frequency")
                    Text(frequency.displayName)
                }
            }
            DropdownMenu(expanded = compoundingMenuExpanded, onDismissRequest = { compoundingMenuExpanded = false }) {
                CompoundingFrequency.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.displayName) },
                        onClick = {
                            haptics.selection()
                            frequency = item
                            compoundingMenuExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { if (!isMatured) { haptics.click(); showStartDatePicker = true } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isMatured
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start date")
                Text(LocalDate.ofEpochDay(startDateEpochDay).toString())
            }
        }

        OutlinedButton(
            onClick = { if (!isMatured) { haptics.click(); showMaturityDatePicker = true } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isMatured
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Maturity date")
                Text(LocalDate.ofEpochDay(maturityDateEpochDay).toString())
            }
        }

        if (!isDateValid) {
            Text(
                "Maturity date must be after start date.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = formatMaturityAmount(calculatedMaturityAmountPaise),
            onValueChange = {},
            label = { Text("Maturity amount") },
            readOnly = true,
            singleLine = true,
            enabled = !isMatured,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                val interestPaise = (calculatedMaturityAmountPaise - principalPaise).coerceAtLeast(0L)
                Text(
                    "Estimated interest: ${money(interestPaise)} · Compounded ${frequency.displayName.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        if (!isMaturityAmountValid && principalPaise > 0) {
            Text(
                "Maturity amount must be at least the principal amount.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isMatured) {
            Text(
                "This deposit is MATURED. Accounting fields cannot be altered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                haptics.confirm()
                onSave(
                    FixedDepositEntity(
                        id = initial?.id ?: 0L,
                        name = name.trim(),
                        linkedAccountId = selectedAccount!!.id,
                        principalPaise = principalPaise,
                        interestRateBps = rateBps,
                        startDateEpochDay = startDateEpochDay,
                        maturityDateEpochDay = maturityDateEpochDay,
                        maturityAmountPaise = calculatedMaturityAmountPaise,
                        description = description.trim(),
                        status = initial?.status ?: FixedDepositStatus.ACTIVE,
                        compoundingFrequency = frequency,
                        isExisting = isExisting
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (initial == null) "Save Fixed Deposit" else "Save Changes")
        }

        if (initial != null) {
            val isDue = !isMatured && maturityDateEpochDay <= LocalDate.now().toEpochDay()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDue && onMature != null) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onMature(initial.id)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mature Now")
                    }
                }
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            haptics.reject()
                            onDelete(initial)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete")
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(startDateEpochDay)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    pickerState.selectedDateMillis?.let {
                        startDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showMaturityDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(maturityDateEpochDay)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showMaturityDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    pickerState.selectedDateMillis?.let {
                        maturityDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showMaturityDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showMaturityDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

private fun money(paise: Long) = formatIndianMoney(paise)

private fun formatMaturityAmount(paise: Long) = FixedDepositCalculator.formatMaturityAmount(paise)

private fun formatDate(epochDay: Long): String = formatEpochDate(epochDay)
