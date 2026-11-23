package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.components.Logo
import com.nanobuo.myfifty.ui.components.SectionCard

/** Crea una nuova casa o entra in una esistente con il codice invito. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSetupScreen(vm: AppViewModel, onBack: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val households by vm.households.collectAsStateWithLifecycle()

    // Dopo aver creato/unito una casa dalla schermata impostazioni, torna indietro.
    val initialCount = remember { households?.size ?: 0 }
    LaunchedEffect(households?.size) {
        if (onBack != null && (households?.size ?: 0) > initialCount) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onBack == null) "" else "Aggiungi una casa") },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
                },
                actions = {
                    if (onBack == null) TextButton({ vm.signOut() }) { Text("Esci") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack == null) {
                Logo(110.dp)
                Text("Configura la tua casa", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Crea una casa e condividi il codice invito con chi vive con te, oppure inserisci il codice che ti hanno mandato.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = "Crea una nuova casa") {
                OutlinedTextField(
                    name, { name = it.take(60) }, label = { Text("Nome (es. Casa Via Roma)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button({ vm.createHousehold(name) }, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Crea casa")
                }
            }
            SectionCard(title = "Entra con un codice invito") {
                OutlinedTextField(
                    code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    label = { Text("Codice di 8 caratteri") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton({ vm.joinHousehold(code) }, enabled = code.length == 8 && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Entra")
                }
            }
        }
    }
}
