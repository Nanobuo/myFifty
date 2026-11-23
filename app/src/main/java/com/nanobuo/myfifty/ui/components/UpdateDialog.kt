package com.nanobuo.myfifty.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.BuildConfig
import com.nanobuo.myfifty.data.AppRelease
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.UpdateState

/** Finestra globale per gli aggiornamenti (controllo, download, installazione). */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()

    // Tornando dalle impostazioni dopo aver concesso il permesso, prosegue con l'installazione.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        (state as? UpdateState.NeedsPermission)?.let { vm.installUpdate(it.release) }
    }

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        UpdateState.UpToDate -> Simple(
            "Nessun aggiornamento",
            "Stai usando l'ultima versione (${BuildConfig.VERSION_NAME}).",
            onDismiss = vm::dismissUpdate,
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            icon = { Icon(Icons.Outlined.SystemUpdate, null) },
            title = { Text("Nuova versione ${s.release.version}") },
            text = { Notes(s.release, "Installata: ${BuildConfig.VERSION_NAME}") },
            confirmButton = { TextButton({ vm.downloadUpdate(s.release) }) { Text("Aggiorna") } },
            dismissButton = { TextButton(vm::dismissUpdate) { Text("Più tardi") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Download in corso…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(vm::dismissUpdate) { Text("Annulla") } },
        )
        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text("Serve un permesso") },
            text = {
                Text(
                    "Per installare l'aggiornamento consenti a myFifty di installare app " +
                        "(\"Consenti da questa fonte\"), poi torna qui."
                )
            },
            confirmButton = { TextButton(vm::openInstallPermission) { Text("Apri impostazioni") } },
            dismissButton = { TextButton(vm::dismissUpdate) { Text("Annulla") } },
        )
        is UpdateState.Ready -> Simple(
            "Installazione avviata",
            "Conferma l'aggiornamento nella finestra di Android. Se Android segnala un conflitto di firma, " +
                "disinstalla myFifty e installa la nuova versione.",
            onDismiss = vm::dismissUpdate,
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text("Aggiornamento non riuscito") },
            text = { Text(s.message) },
            confirmButton = {
                if (s.release != null) TextButton({ vm.openReleasePage(s.release); vm.dismissUpdate() }) { Text("Apri su GitHub") }
                else TextButton(vm::dismissUpdate) { Text("OK") }
            },
            dismissButton = { if (s.release != null) TextButton(vm::dismissUpdate) { Text("Chiudi") } },
        )
    }
}

@Composable
private fun Notes(release: AppRelease, footer: String) {
    Column(
        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (release.notes.isNotBlank()) {
            Text(release.notes.replace(Regex("(?m)^#+\\s*"), "").replace("**", ""), style = MaterialTheme.typography.bodyMedium)
        }
        Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Simple(title: String, text: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onDismiss) { Text("OK") } },
)
