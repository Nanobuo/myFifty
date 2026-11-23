package com.nanobuo.myfifty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nanobuo.myfifty.domain.Category

/**
 * Crea ([initial] null) o modifica una categoria: nome, icona e colore.
 * [onDelete] è mostrato solo in modifica e mai per "Altro".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    initial: Category?,
    existingNames: Collection<String>,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val start = initial ?: Category(key = Category.newKey(), name = "", icon = "tag", color = Category.palette[0])
    var name by remember { mutableStateOf(start.name) }
    var icon by remember { mutableStateOf(start.icon) }
    var color by remember { mutableStateOf(start.color) }
    val duplicate = existingNames.any { it.equals(name.trim(), true) && !it.equals(initial?.name, true) }
    val preview = start.copy(name = name, icon = icon, color = color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuova categoria" else "Modifica categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(preview, 44.dp)
                    OutlinedTextField(
                        name, { name = it.take(40) }, label = { Text("Nome") }, singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) ({ Text("Esiste già") }) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Icona", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.forEach { (key, vector) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (sel) preview.uiColor.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (sel) preview.uiColor else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, key, Modifier.size(22.dp), tint = if (sel) preview.uiColor else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text("Colore", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.palette.forEach { hex ->
                        val c = Color(0xFF000000 or hex.removePrefix("#").toLong(16))
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c).clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) { if (hex.equals(color, true)) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                if (onDelete != null && initial != null && !initial.isOther) {
                    TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Elimina categoria", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "I movimenti di questa categoria passeranno ad \"Altro\".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(preview.copy(name = name.trim())); onDismiss() },
                enabled = name.isNotBlank() && !duplicate,
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}
