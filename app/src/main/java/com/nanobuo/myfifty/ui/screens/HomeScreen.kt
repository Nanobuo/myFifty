package com.nanobuo.myfifty.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.components.Logo
import androidx.compose.ui.unit.dp
import java.time.YearMonth

private enum class Tab(val label: String, val icon: ImageVector) {
    MONTH("Movimenti", Icons.AutoMirrored.Outlined.ReceiptLong),
    STATS("Statistiche", Icons.Outlined.BarChart),
    SETTINGS("Altro", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAddExpense: (month: String) -> Unit,
    onOpenExpense: (id: String) -> Unit,
    onAddHousehold: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    val data by vm.data.collectAsStateWithLifecycle()
    val households by vm.households.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var switcher by remember { mutableStateOf(false) }

    // Al ritorno in primo piano ricarica (il realtime potrebbe essersi disconnesso).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val current = Tab.entries[tab]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { switcher = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Logo(32.dp)
                                Text(
                                    data?.household?.name ?: "myFifty",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                                Icon(Icons.Outlined.ExpandMore, "Cambia casa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(switcher, { switcher = false }) {
                            households.orEmpty().forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(h.name) },
                                    trailingIcon = { if (h.id == data?.household?.id) Icon(Icons.Outlined.Check, null) },
                                    onClick = { switcher = false; vm.selectHousehold(h.id) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Crea o entra in un'altra casa") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { switcher = false; onAddHousehold() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (current == Tab.MONTH) {
                ExtendedFloatingActionButton(
                    onClick = { onAddExpense(monthStr) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Aggiungi") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        val d = data
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            if (d == null) return@PullToRefreshBox
            when (current) {
                Tab.MONTH -> MonthTab(vm, d, month, { monthStr = it.toString() }, onOpenExpense)
                Tab.STATS -> StatsTab(vm, d)
                Tab.SETTINGS -> SettingsTab(vm, d, onAddHousehold)
            }
        }
    }
}
