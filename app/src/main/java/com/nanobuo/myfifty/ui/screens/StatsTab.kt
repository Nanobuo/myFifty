package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.components.Avatar
import com.nanobuo.myfifty.ui.components.CategoryBadge
import com.nanobuo.myfifty.ui.components.CategoryDonut
import com.nanobuo.myfifty.ui.components.EmptyState
import com.nanobuo.myfifty.ui.components.HBar
import com.nanobuo.myfifty.ui.components.MonthlyBarChart
import com.nanobuo.myfifty.ui.components.SectionCard
import com.nanobuo.myfifty.ui.components.StatTile
import com.nanobuo.myfifty.ui.components.monthLabel
import com.nanobuo.myfifty.ui.components.uiColor
import java.time.YearMonth

private enum class Period(val label: String) { MONTH("Questo mese"), LAST12("Ultimi 12 mesi"), YEAR("Anno in corso"), ALL("Tutto") }

@Composable
fun StatsTab(vm: AppViewModel, d: HouseholdData) {
    val cur = d.household.currency
    var periodIdx by rememberSaveable { mutableIntStateOf(1) }
    val period = Period.entries[periodIdx]
    val now = YearMonth.now()

    val months: List<YearMonth> = remember(period, d.expenses) {
        when (period) {
            Period.MONTH -> listOf(now)
            Period.LAST12 -> (11 downTo 0).map { now.minusMonths(it.toLong()) }
            Period.YEAR -> (1..now.monthValue).map { YearMonth.of(now.year, it) }
            Period.ALL -> {
                val first = d.expenses.minOfOrNull { it.yearMonth } ?: now
                generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(now) }.toList().takeLast(120)
            }
        }
    }
    val inRange = remember(months, d.expenses) {
        val set = months.toSet(); d.expenses.filter { it.yearMonth in set }
    }
    val summary = remember(inRange) { Ledger.summary(inRange) }
    val monthly = remember(inRange, months) { Ledger.monthlyTotals(inRange, months) }
    val activeMonths = monthly.count { it.second != 0L }.coerceAtLeast(1)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEachIndexed { i, p ->
                    FilterChip(selected = i == periodIdx, onClick = { periodIdx = i }, label = { Text(p.label) })
                }
            }
        }
        if (summary.count == 0) {
            item { EmptyState("Ancora nessun dato", "Le statistiche compariranno quando avrete registrato qualche spesa.") }
            return@LazyColumn
        }
        item {
            SectionCard(title = "Riepilogo") {
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Totale", Money.format(summary.totalCents, cur), Modifier.weight(1f))
                    if (months.size > 1) StatTile("Media mensile", Money.format(summary.totalCents / activeMonths, cur), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Movimenti", summary.count.toString(), Modifier.weight(1f))
                    StatTile(
                        "Per persona",
                        Money.format(if (d.members.isEmpty()) 0 else summary.totalCents / d.members.size, cur),
                        Modifier.weight(1f),
                    )
                }
                summary.largest?.let { e ->
                    Text(
                        "Spesa più alta: ${e.description.ifBlank { d.categoryOf(e.category).name }} · ${Money.format(e.amountCents, cur)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (months.size > 1) item {
            SectionCard(title = "Andamento mensile") { MonthlyBarChart(monthly.takeLast(12), cur) }
        }
        item {
            SectionCard(title = "Per categoria") {
                CategoryDonut(summary.byCategory.map { d.categoryOf(it.first) to it.second }, cur, Modifier.fillMaxWidth())
                val maxCat = summary.byCategory.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                summary.byCategory.forEach { (key, v) ->
                    val c = d.categoryOf(key)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        CategoryBadge(c, 32.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row {
                                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(v, cur), style = MaterialTheme.typography.labelLarge)
                            }
                            HBar(v.toFloat() / maxCat, c.uiColor, Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "Chi ha pagato cosa") {
                val maxPaid = d.members.maxOfOrNull { summary.paidBy[it.id] ?: 0 }?.coerceAtLeast(1) ?: 1
                d.members.forEach { m ->
                    val paid = summary.paidBy[m.id] ?: 0
                    val share = summary.shareOf[m.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Avatar(m.name, 32.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row {
                                Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(paid, cur), style = MaterialTheme.typography.labelLarge)
                            }
                            HBar(paid.toFloat() / maxPaid, MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 4.dp))
                            Text("Quota di spesa: ${Money.format(share, cur)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (period != Period.MONTH) item {
            SectionCard(title = "Mese per mese") {
                monthly.filter { it.second != 0L }.reversed().forEach { (m, v) ->
                    Row {
                        Text(monthLabel(m, long = true), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(v, cur), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
