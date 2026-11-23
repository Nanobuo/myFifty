package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.components.Logo
import com.nanobuo.myfifty.ui.components.SectionCard

/** Primo avvio: collega l'app al progetto Supabase (se non è già nel build). */
@Composable
fun SetupScreen(vm: AppViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    val valid = url.trim().length > 8 && key.trim().length > 20

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Logo(140.dp, Modifier.padding(top = 24.dp))
        Text("Benvenuto in myFifty", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Per sincronizzare le spese con gli altri membri della casa, collega l'app al tuo progetto Supabase.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(title = "Dove trovo questi dati?") {
            Text(
                "Supabase → il tuo progetto → Project Settings → API (o \"Data API\"): copia il Project URL e la chiave " +
                    "anon / publishable. Trovi la guida completa nel file docs/SUPABASE_SETUP.md del repository.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Project URL") },
            placeholder = { Text("https://xxxx.supabase.co") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Chiave anon / publishable") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveConfig(url, key) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text("Collega")
        }
    }
}
