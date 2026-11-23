package com.nanobuo.myfifty.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobuo.myfifty.R
import com.nanobuo.myfifty.domain.Category
import com.nanobuo.myfifty.domain.Money
import com.nanobuo.myfifty.ui.theme.LocalIsDark
import com.nanobuo.myfifty.ui.theme.LocalMoneyColors

/** Logo che segue il tema effettivo dell'app (chiaro/scuro). */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    val res = if (LocalIsDark.current) R.drawable.logo_dark else R.drawable.logo_light
    Image(painterResource(res), contentDescription = "myFifty", modifier = modifier.size(size).clip(CircleShape))
}

/** Icone disponibili per le categorie (la chiave è salvata su Supabase). */
val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "cart" to Icons.Outlined.ShoppingCart,
    "bolt" to Icons.Outlined.Bolt,
    "fire" to Icons.Outlined.LocalFireDepartment,
    "water" to Icons.Outlined.WaterDrop,
    "wifi" to Icons.Outlined.Wifi,
    "tv" to Icons.Outlined.Subscriptions,
    "house" to Icons.Outlined.House,
    "building" to Icons.Outlined.Apartment,
    "cleaning" to Icons.Outlined.CleaningServices,
    "build" to Icons.Outlined.Build,
    "restaurant" to Icons.Outlined.Restaurant,
    "car" to Icons.Outlined.DirectionsCar,
    "pets" to Icons.Outlined.Pets,
    "health" to Icons.Outlined.LocalHospital,
    "gift" to Icons.Outlined.CardGiftcard,
    "sport" to Icons.Outlined.FitnessCenter,
    "travel" to Icons.Outlined.Flight,
    "baby" to Icons.Outlined.ChildCare,
    "school" to Icons.Outlined.School,
    "phone" to Icons.Outlined.PhoneAndroid,
    "drink" to Icons.Outlined.LocalBar,
    "plant" to Icons.Outlined.LocalFlorist,
    "laundry" to Icons.Outlined.LocalLaundryService,
    "tag" to Icons.Outlined.Sell,
    "more" to Icons.Outlined.MoreHoriz,
)
private val iconByKey = CategoryIcons.toMap()

val Category.iconVector: ImageVector get() = iconByKey[icon] ?: Icons.Outlined.Sell

val Category.uiColor: Color get() = Color(argb)

@Composable
fun CategoryBadge(category: Category, size: Dp = 40.dp, icon: ImageVector = category.iconVector) {
    Box(
        Modifier.size(size).clip(CircleShape).background(category.uiColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = category.uiColor, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Avatar(name: String, size: Dp = 36.dp, color: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun balanceColor(cents: Long): Color = when {
    cents > 0 -> LocalMoneyColors.current.positive
    cents < 0 -> LocalMoneyColors.current.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Logo(96.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = "Conferma",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun AmountText(cents: Long, currency: String, signed: Boolean = false, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    Text(
        if (signed) Money.formatSigned(cents, currency) else Money.format(cents, currency),
        style = style,
        color = if (signed) balanceColor(cents) else Color.Unspecified,
    )
}

@Composable
fun LabeledRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        value()
    }
}
