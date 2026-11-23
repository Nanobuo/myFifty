package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.components.Logo

@Composable
fun AuthScreen(vm: AppViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPwd by rememberSaveable { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    val emailOk = email.contains('@') && email.contains('.')
    val canSubmit = emailOk && password.length >= 6 && (!register || name.isNotBlank()) && !busy

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(150.dp, Modifier.padding(top = 32.dp))
        Text("myFifty", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Le spese di casa, divise in modo semplice.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(!register, { register = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Accedi") }
            SegmentedButton(register, { register = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Registrati") }
        }
        if (register) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Il tuo nome") }, singleLine = true,
                supportingText = { Text("È il nome che vedranno gli altri membri") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton({ showPwd = !showPwd }) {
                    Icon(if (showPwd) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostra password")
                }
            },
            supportingText = if (register) ({ Text("Almeno 6 caratteri") }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { if (register) vm.signUp(name, email, password) else vm.signIn(email, password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (register) "Crea account" else "Accedi")
        }
        if (!register) {
            TextButton(onClick = { vm.resetPassword(email) }, enabled = emailOk && !busy) {
                Text("Password dimenticata?")
            }
        }
    }
}
