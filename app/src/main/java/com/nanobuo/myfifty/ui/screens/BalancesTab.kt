package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.components.Avatar
import com.nanobuo.myfifty.ui.components.ConfirmDialog
import com.nanobuo.myfifty.ui.components.HBar
import com.nanobuo.myfifty.ui.components.SectionCard
import com.nanobuo.myfifty.ui.components.balanceColor
import kotlin.math.abs

@Composable
fun BalancesTab(vm: AppViewModel, d: HouseholdData) {
    val cur = d.household.currency
    val me = vm.myId
    val balances = d.balances
    val transfers = remember(balances) { Ledger.settleUp(balances) }
    val maxAbs = (balances.values.maxOfOrNull { abs(it) } ?: 0L).coerceAtLeast(1)
    var confirm by remember { mutableStateOf<Ledger.Transfer?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val mine = me?.let { balances[it] } ?: 0L
            SectionCard {
                Text("Il tuo saldo complessivo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatSigned(mine, cur), style = MaterialTheme.typography.displaySmall, color = balanceColor(mine))
                Text(
                    when {
                        mine > 0 -> "Gli altri membri ti devono questa cifra."
                        mine < 0 -> "Devi questa cifra agli altri membri."
                        else -> "Sei in pari. 🎉"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard(title = "Saldi dei membri") {
                d.members.forEach { m ->
                    val b = balances[m.id] ?: 0L
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.name)
                            Text(
                                m.name + if (m.id == me) " (tu)" else "",
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            Text(Money.formatSigned(b, cur), color = balanceColor(b), style = MaterialTheme.typography.titleSmall)
                        }
                        HBar(abs(b).toFloat() / maxAbs, balanceColor(b), Modifier.padding(start = 48.dp, top = 6.dp))
                    }
                }
                // ex membri con saldo residuo
                balances.filterKeys { it !in d.memberById }.filterValues { it != 0L }.forEach { (id, b) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar("?")
                        Text("Ex membro", Modifier.weight(1f).padding(horizontal = 12.dp))
                        Text(Money.formatSigned(b, cur), color = balanceColor(b))
                    }
                }
            }
        }
        item {
            Text("Come pareggiare i conti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (transfers.isEmpty()) {
            item { Text("Nessun pagamento necessario: siete tutti in pari.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(transfers) { t ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.nameOf(t.from), 32.dp)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(horizontal = 6.dp))
                    Avatar(d.nameOf(t.to), 32.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("${d.nameOf(t.from)} paga ${d.nameOf(t.to)}", style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(t.amountCents, cur), style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (t.from in d.memberById && t.to in d.memberById) {
                    FilledTonalButton({ confirm = t }, Modifier.fillMaxWidth()) { Text("Segna come pagato") }
                }
            }
        }
    }

    confirm?.let { t ->
        ConfirmDialog(
            title = "Registrare il pagamento?",
            text = "Verrà aggiunto un saldo di ${Money.format(t.amountCents, cur)} da ${d.nameOf(t.from)} a ${d.nameOf(t.to)} con la data di oggi.",
            confirm = "Registra",
            onConfirm = { vm.recordTransfer(t) },
            onDismiss = { confirm = null },
        )
    }
}
