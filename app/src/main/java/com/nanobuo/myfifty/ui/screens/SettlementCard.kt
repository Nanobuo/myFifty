package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.components.Avatar
import com.nanobuo.myfifty.ui.components.ConfirmDialog
import com.nanobuo.myfifty.ui.components.monthLabel
import java.time.YearMonth

/**
 * Saldo di fine mese: compare negli ultimi giorni del mese e all'inizio del successivo
 * (finché il mese non è in pari). Mostra i pagamenti minimi per pareggiare i conti.
 */
@Composable
fun SettlementCard(
    vm: AppViewModel,
    d: HouseholdData,
    month: YearMonth,
    transfers: List<Ledger.Transfer>,
) {
    val cur = d.household.currency
    val me = vm.myId
    val isCurrent = month == YearMonth.now()
    var confirm by remember { mutableStateOf<Ledger.Transfer?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Handshake, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Saldo di ${monthLabel(month).lowercase()}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isCurrent) "Il mese sta finendo: ecco come pareggiare i conti."
                        else "Il mese è finito: ecco come pareggiare i conti.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            transfers.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f))
                val involvesMe = t.from == me || t.to == me
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.nameOf(t.from), 30.dp)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(horizontal = 4.dp))
                    Avatar(d.nameOf(t.to), 30.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            when {
                                t.from == me -> "Devi dare a ${d.nameOf(t.to)}"
                                t.to == me -> "${d.nameOf(t.from)} ti deve dare"
                                else -> "${d.nameOf(t.from)} → ${d.nameOf(t.to)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (involvesMe) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(Money.format(t.amountCents, cur), style = MaterialTheme.typography.titleMedium)
                    }
                    if (t.from in d.memberById && t.to in d.memberById) {
                        FilledTonalButton({ confirm = t }) { Text("Pagato") }
                    }
                }
            }
        }
    }

    confirm?.let { t ->
        ConfirmDialog(
            title = "Segnare come pagato?",
            text = "Verrà registrato un saldo di ${Money.format(t.amountCents, cur)} da ${d.nameOf(t.from)} " +
                "a ${d.nameOf(t.to)} nel mese di ${monthLabel(month).lowercase()}.",
            confirm = "Registra",
            onConfirm = { vm.recordTransfer(t, month) },
            onDismiss = { confirm = null },
        )
    }
}
