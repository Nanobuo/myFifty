package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.domain.Category
import com.nanobuo.myfifty.domain.ExpenseKind
import com.nanobuo.myfifty.domain.ExpenseWrite
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.components.ConfirmDialog
import com.nanobuo.myfifty.ui.components.SectionCard
import com.nanobuo.myfifty.ui.components.CategoryDialog
import com.nanobuo.myfifty.ui.components.iconVector
import com.nanobuo.myfifty.ui.components.uiColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(vm: AppViewModel, expenseId: String?, month: String?, onClose: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val d = data ?: return
    val existing = remember(expenseId) { expenseId?.let { id -> d.expenses.firstOrNull { it.id == id } } }
    val me = vm.myId ?: d.members.firstOrNull()?.id.orEmpty()
    val allIds = d.members.map { it.id }

    val defaultDate = remember(month) {
        val m = month?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (m == null || m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }

    // I saldi tra membri nascono dalla scheda di fine mese; qui si possono solo correggere.
    val isSettlement = existing?.kindEnum == ExpenseKind.SETTLEMENT
    var isRefund by rememberSaveable { mutableStateOf(existing?.kindEnum == ExpenseKind.REFUND) }
    val kind = when {
        isSettlement -> ExpenseKind.SETTLEMENT
        isRefund -> ExpenseKind.REFUND
        else -> ExpenseKind.EXPENSE
    }
    var amount by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.amountCents) } ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var category by rememberSaveable {
        mutableStateOf(existing?.category ?: d.categories.firstOrNull { !it.isOther }?.key ?: Category.OTHER)
    }
    var newCategory by remember { mutableStateOf(false) }
    var dateStr by rememberSaveable { mutableStateOf(existing?.localDate?.toString() ?: defaultDate.toString()) }
    var paidBy by rememberSaveable { mutableStateOf(existing?.paidBy ?: me) }
    var split by rememberSaveable { mutableStateOf(existing?.splitAmong ?: allIds) }
    var recipient by rememberSaveable {
        mutableStateOf(existing?.takeIf { it.kindEnum == ExpenseKind.SETTLEMENT }?.splitAmong?.firstOrNull()
            ?: allIds.firstOrNull { it != me } ?: me)
    }
    var recurring by rememberSaveable { mutableStateOf(existing?.recurring ?: false) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (expenseId != null && existing == null) {
        // eliminato da un altro membro mentre era aperto
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Questo movimento non esiste più.")
            TextButton(onClose) { Text("Torna indietro") }
        }
        return
    }

    val cents = Money.parse(amount)
    val date = LocalDate.parse(dateStr)
    val participants = if (kind == ExpenseKind.SETTLEMENT) listOf(recipient) else split
    val valid = cents != null && cents > 0 && participants.isNotEmpty() && paidBy.isNotEmpty() &&
        !(kind == ExpenseKind.SETTLEMENT && recipient == paidBy)

    fun save() {
        val c = cents ?: return
        vm.saveExpense(
            ExpenseWrite(
                id = existing?.id,
                householdId = d.household.id,
                kind = kind.key,
                description = description.trim().ifEmpty { if (kind == ExpenseKind.SETTLEMENT) "Saldo" else "" },
                category = if (kind == ExpenseKind.SETTLEMENT) Category.OTHER else category,
                amountCents = c,
                date = date.toString(),
                paidBy = paidBy,
                splitAmong = participants,
                recurring = recurring && kind != ExpenseKind.SETTLEMENT,
                notes = notes.trim(),
            ),
            onDone = onClose,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isSettlement -> "Saldo tra membri"
                            existing == null -> "Nuova spesa"
                            else -> "Modifica"
                        }
                    )
                },
                navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") } },
                actions = {
                    if (existing != null) IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Elimina") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isSettlement) {
                Text(
                    "Pagamento registrato per pareggiare i conti di fine mese.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) },
                label = { Text("Importo") },
                suffix = { Text(d.household.currency) },
                isError = amount.isNotEmpty() && (cents == null || cents <= 0),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                description, { description = it.take(120) },
                label = { Text(if (kind == ExpenseKind.SETTLEMENT) "Descrizione (facoltativa)" else "Descrizione") },
                placeholder = { Text(if (kind == ExpenseKind.EXPENSE) "es. Spesa Esselunga" else "") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton({ showDate = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Text("  " + date.format(dateFmt).replaceFirstChar { it.uppercase() })
            }

            if (!isSettlement) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("È un rimborso", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Soldi ricevuti (es. conguaglio di una bolletta, reso) da dividere tra i membri.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(isRefund, { isRefund = it })
                }

                Text("Categoria", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.categories.forEach { c ->
                        FilterChip(
                            selected = category == c.key, onClick = { category = c.key },
                            label = { Text(c.name) },
                            leadingIcon = { Icon(c.iconVector, null, Modifier.size(18.dp), tint = c.uiColor) },
                        )
                    }
                    AssistChip(
                        onClick = { newCategory = true },
                        label = { Text("Nuova") },
                        leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            Text(
                when (kind) {
                    ExpenseKind.EXPENSE -> "Pagato da"
                    ExpenseKind.REFUND -> "Ricevuto da"
                    ExpenseKind.SETTLEMENT -> "Da"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.members.forEach { m ->
                    FilterChip(paidBy == m.id, { paidBy = m.id }, label = { Text(if (m.id == me) "${m.name} (tu)" else m.name) })
                }
            }

            if (kind == ExpenseKind.SETTLEMENT) {
                Text("A", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.members.filter { it.id != paidBy }.forEach { m ->
                        FilterChip(recipient == m.id, { recipient = m.id }, label = { Text(m.name) })
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (kind == ExpenseKind.EXPENSE) "Diviso tra" else "Spetta a",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    )
                    TextButton({ split = if (split.size == allIds.size) emptyList() else allIds }) {
                        Text(if (split.size == allIds.size) "Nessuno" else "Tutti")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.members.forEach { m ->
                        val sel = m.id in split
                        FilterChip(sel, { split = if (sel) split - m.id else split + m.id }, label = { Text(m.name) })
                    }
                }
                if (cents != null && cents > 0 && split.isNotEmpty()) {
                    val shares = Ledger.split(cents, split)
                    SectionCard {
                        Text("Quote", style = MaterialTheme.typography.labelLarge)
                        shares.entries.sortedBy { d.nameOf(it.key).lowercase() }.forEach { (id, s) ->
                            Row {
                                Text(d.nameOf(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(s, d.household.currency), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spesa ricorrente", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Ogni mese ti verrà proposto di ricopiarla (es. affitto, internet).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(recurring, { recurring = it })
                }
            }

            OutlinedTextField(
                notes, { notes = it.take(500) }, label = { Text("Note") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            if (existing?.updatedAt != null && existing.updatedBy != null) {
                Text(
                    "Ultima modifica di ${d.nameOf(existing.updatedBy)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(::save, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (existing == null) "Salva" else "Salva modifiche")
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        dateStr = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }
    if (newCategory) {
        CategoryDialog(
            initial = null,
            existingNames = d.categories.map { it.name },
            onSave = { c -> vm.saveCategory(c) { key -> category = key } },
            onDismiss = { newCategory = false },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Eliminare il movimento?", "L'operazione non si può annullare e sparirà anche per gli altri membri.",
            "Elimina", { vm.deleteExpense(existing.id, onClose) }, { confirmDelete = false },
        )
    }
}
