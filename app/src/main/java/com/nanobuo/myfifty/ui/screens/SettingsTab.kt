package com.nanobuo.myfifty.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.BuildConfig
import com.nanobuo.myfifty.data.ThemeMode
import com.nanobuo.myfifty.io.ImportExport
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.components.Avatar
import com.nanobuo.myfifty.ui.components.ConfirmDialog
import com.nanobuo.myfifty.ui.components.Logo
import com.nanobuo.myfifty.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsTab(vm: AppViewModel, d: HouseholdData, onAddHousehold: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val myName by vm.myName.collectAsStateWithLifecycle()
    val me = vm.myId
    val h = d.household

    var dialog by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ImportExport.ImportResult?>(null) }

    val safeName = h.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "casa" }
    val stamp = LocalDate.now().toString()

    fun write(uri: Uri?, content: String?) {
        if (uri == null || content == null) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
            }.onSuccess { vm.toast("File esportato") }.onFailure { vm.toast("Esportazione non riuscita: ${it.message}") }
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        write(it, vm.exportJson())
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        write(it, vm.exportCsv())
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
            }.getOrElse { vm.toast("Impossibile leggere il file"); return@launch }
            vm.prepareImport(text)
                .onSuccess { importPreview = it }
                .onFailure { vm.toast("File non valido: ${it.message}") }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------------- casa
        item {
            SectionCard(title = "Casa") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton({ dialog = "rename" }) { Icon(Icons.Outlined.Edit, "Rinomina") }
                }
                Text("Codice invito", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.inviteCode.chunked(4).joinToString(" "),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        letterSpacing = 2.sp, modifier = Modifier.weight(1f),
                    )
                    IconButton({ copy(ctx, h.inviteCode); vm.toast("Codice copiato") }) { Icon(Icons.Outlined.ContentCopy, "Copia") }
                    IconButton({ shareInvite(ctx, h.name, h.inviteCode) }) { Icon(Icons.Outlined.Share, "Condividi") }
                    IconButton({ dialog = "regen" }) { Icon(Icons.Outlined.Refresh, "Nuovo codice") }
                }
                Text(
                    "Chi vive con te installa myFifty, si registra e inserisce questo codice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "Membri (${d.members.size})") {
                d.members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Avatar(m.name)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(m.name + if (m.id == me) " (tu)" else "")
                            if (m.role == "owner") Text("Ha creato la casa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (m.id == me) IconButton({ dialog = "name" }) { Icon(Icons.Outlined.Edit, "Cambia nome") }
                    }
                }
                TextButton(onAddHousehold) { Text("Crea o entra in un'altra casa") }
            }
        }
        // ---------------- tema
        item {
            SectionCard(title = "Aspetto") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(theme == mode, { vm.setTheme(mode) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                            Text(mode.label)
                        }
                    }
                }
            }
        }
        // ---------------- dati
        item {
            SectionCard(title = "Importa ed esporta") {
                ActionRow(Icons.Outlined.Download, "Backup completo (JSON)", "Reimportabile senza creare duplicati") {
                    exportJson.launch("myfifty_${safeName}_$stamp.json")
                }
                ActionRow(Icons.Outlined.TableChart, "Esporta in CSV", "Per Excel o Fogli Google") {
                    exportCsv.launch("myfifty_${safeName}_$stamp.csv")
                }
                ActionRow(Icons.Outlined.Upload, "Importa da file", "JSON di myFifty o CSV (data;importo;pagato_da;…)") {
                    importFile.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
                }
            }
        }
        // ---------------- account
        item {
            SectionCard(title = "Account") {
                Text(myName.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                Text(vm.myEmail.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.signOut() }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null); Text("  Esci")
                    }
                    TextButton({ dialog = "leave" }) { Text("Lascia la casa", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(56.dp)
                Text("myFifty ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    when (dialog) {
        "rename" -> TextInputDialog("Nome della casa", h.name, onDismiss = { dialog = null }) { vm.renameHousehold(it) }
        "name" -> TextInputDialog("Il tuo nome", myName, onDismiss = { dialog = null }) { vm.updateName(it) }
        "regen" -> ConfirmDialog(
            "Generare un nuovo codice?", "Il codice attuale smetterà di funzionare. I membri già presenti non vengono toccati.",
            "Genera", { vm.regenerateInvite() }, { dialog = null },
        )
        "leave" -> ConfirmDialog(
            "Lasciare \"${h.name}\"?",
            "Non vedrai più le spese di questa casa. Se sei l'ultimo membro la casa e tutti i suoi movimenti verranno eliminati definitivamente. Ti consigliamo di esportare un backup prima.",
            "Lascia", { vm.leaveHousehold() }, { dialog = null },
        )
    }

    importPreview?.let { r ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("Importare i movimenti?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nuovi: ${r.newCount}")
                    Text("Da aggiornare: ${r.updatedCount}")
                    if (r.skipped.isNotEmpty()) {
                        Text("Scartati: ${r.skipped.size}", color = MaterialTheme.colorScheme.error)
                        r.skipped.take(20).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (r.skipped.size > 20) Text("… e altri ${r.skipped.size - 20}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton({ vm.applyImport(r); importPreview = null }, enabled = r.toWrite.isNotEmpty()) { Text("Importa") }
            },
            dismissButton = { TextButton({ importPreview = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth()) {
        Icon(icon, null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
        confirmButton = { TextButton({ onSave(text); onDismiss() }, enabled = text.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Codice invito", text))
}

private fun shareInvite(ctx: Context, name: String, code: String) {
    val msg = "Unisciti alla casa \"$name\" su myFifty per dividere le spese! Codice invito: $code"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }
    ctx.startActivity(Intent.createChooser(intent, "Condividi codice invito"))
}
