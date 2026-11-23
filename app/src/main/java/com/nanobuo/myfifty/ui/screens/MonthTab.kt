package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Category
import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.ExpenseKind
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.domain.Recurring
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.components.CategoryBadge
import com.nanobuo.myfifty.ui.components.EmptyState
import com.nanobuo.myfifty.ui.components.StatTile
import com.nanobuo.myfifty.ui.components.balanceColor
import com.nanobuo.myfifty.ui.components.monthLabel
import com.nanobuo.myfifty.ui.theme.LocalMoneyColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

@Composable
fun MonthTab(
    vm: AppViewModel,
    d: HouseholdData,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onOpen: (String) -> Unit,
) {
    val me = vm.myId
    val cur = d.household.currency
    val monthExpenses = remember(d.expenses, month) { d.expenses.filter { it.yearMonth == month } }
    val summary = remember(monthExpenses) { Ledger.summary(monthExpenses) }
    val monthBalance = remember(monthExpenses, me) { me?.let { Ledger.balances(monthExpenses)[it] } ?: 0L }
    val pending = remember(d.expenses, month) { Recurring.pending(d.expenses, month) }
    val byDay = remember(monthExpenses) { monthExpenses.groupBy { it.localDate }.toSortedMap(compareByDescending { it }) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Mese precedente") }
                Text(
                    monthLabel(month, long = true), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).clickable { onMonth(YearMonth.now()) },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton({ onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Mese successivo") }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spese del mese", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(Money.format(summary.totalCents, cur), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("La tua quota", Money.format(me?.let { summary.shareOf[it] } ?: 0, cur), Modifier.weight(1f))
                        StatTile("Hai pagato", Money.format(me?.let { summary.paidBy[it] } ?: 0, cur), Modifier.weight(1f))
                        StatTile("Saldo mese", Money.formatSigned(monthBalance, cur), Modifier.weight(1f), balanceColor(monthBalance))
                    }
                }
            }
        }
        if (pending.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(if (pending.size == 1) "1 spesa ricorrente da aggiungere" else "${pending.size} spese ricorrenti da aggiungere", style = MaterialTheme.typography.titleSmall)
                            Text(
                                pending.joinToString { it.description.ifBlank { it.categoryEnum.label } },
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton({ vm.addRecurring(month) }) { Text("Aggiungi") }
                    }
                }
            }
        }
        if (monthExpenses.isEmpty()) {
            item {
                EmptyState(
                    "Nessun movimento a ${monthLabel(month).lowercase()}",
                    "Tocca \"Aggiungi\" per registrare una spesa, un rimborso o un saldo tra membri.",
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "h$day") {
                Text(
                    dayLabel(day), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            items(list, key = { it.id }) { e -> ExpenseRow(e, d, me) { onOpen(e.id) } }
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (monthExpenses.isNotEmpty()) item {
            TextButton({ onMonth(YearMonth.now()) }, Modifier.fillMaxWidth()) { Text("Vai al mese corrente") }
        }
    }
}

private fun dayLabel(day: LocalDate): String = when (day) {
    LocalDate.now() -> "Oggi"
    LocalDate.now().minusDays(1) -> "Ieri"
    else -> day.format(dayFmt).replaceFirstChar { it.uppercase() }
}

@Composable
fun ExpenseRow(e: Expense, d: HouseholdData, me: String?, onClick: () -> Unit) {
    val cur = d.household.currency
    val kind = e.kindEnum
    val title = when (kind) {
        ExpenseKind.SETTLEMENT -> "${d.nameOf(e.paidBy)} → ${d.nameOf(e.splitAmong.firstOrNull().orEmpty())}"
        else -> e.description.ifBlank { e.categoryEnum.label }
    }
    val subtitle = when (kind) {
        ExpenseKind.EXPENSE -> "Pagato da ${if (e.paidBy == me) "te" else d.nameOf(e.paidBy)} · " +
            if (e.splitAmong.size == d.members.size) "diviso tra tutti" else "diviso tra ${e.splitAmong.size}"
        ExpenseKind.REFUND -> "Rimborso ricevuto da ${if (e.paidBy == me) "te" else d.nameOf(e.paidBy)}"
        ExpenseKind.SETTLEMENT -> e.description.takeIf { it.isNotBlank() && it != "Saldo" } ?: "Saldo tra membri"
    }
    val myEffect = me?.let { Ledger.effect(e)[it] } ?: 0L
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (kind) {
                ExpenseKind.SETTLEMENT -> CategoryBadge(Category.OTHER, icon = Icons.Outlined.Handshake)
                ExpenseKind.REFUND -> CategoryBadge(e.categoryEnum, icon = Icons.AutoMirrored.Outlined.Undo)
                else -> CategoryBadge(e.categoryEnum)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (e.recurring) Icon(Icons.Outlined.Autorenew, "Ricorrente", Modifier.padding(start = 4.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (kind == ExpenseKind.REFUND) "−" else "") + Money.format(e.amountCents, cur),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (kind == ExpenseKind.REFUND) LocalMoneyColors.current.positive else MaterialTheme.colorScheme.onSurface,
                )
                if (myEffect != 0L) {
                    Text(
                        if (myEffect > 0) "ti spettano ${Money.format(myEffect, cur)}" else "devi ${Money.format(-myEffect, cur)}",
                        style = MaterialTheme.typography.labelSmall, color = balanceColor(myEffect),
                    )
                }
            }
        }
    }
}
