package com.nanobuo.myfifty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nanobuo.myfifty.ui.components.Logo
import com.nanobuo.myfifty.ui.screens.AuthScreen
import com.nanobuo.myfifty.ui.screens.ExpenseEditorScreen
import com.nanobuo.myfifty.ui.screens.HomeScreen
import com.nanobuo.myfifty.ui.screens.HouseholdSetupScreen

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

object Routes {
    const val HOME = "home"
    const val EXPENSE = "expense?id={id}&month={month}"
    const val ADD_HOUSEHOLD = "household/add"
    fun expense(id: String? = null, month: String? = null) =
        "expense?id=${id.orEmpty()}&month=${month.orEmpty()}"
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // Controllo automatico (max ogni 12 ore) solo per le build release: la debug è un'app separata.
    LaunchedEffect(Unit) { if (!com.nanobuo.myfifty.BuildConfig.DEBUG) vm.checkForUpdates(manual = false) }
    val gate by vm.gate.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (gate) {
                    Gate.LOADING -> LoadingScreen(vm)
                    Gate.NEEDS_CONFIG -> NotConfiguredScreen(vm)
                    Gate.SIGNED_OUT -> AuthScreen(vm)
                    Gate.NO_HOUSEHOLD -> HouseholdSetupScreen(vm, onBack = null)
                    Gate.READY -> MainNav(vm)
                }
                com.nanobuo.myfifty.ui.components.UpdateDialog(vm)
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                )
            }
        }
    }
}

/** Solo per build di sviluppo create senza supabase.properties: l'utente non deve inserire nulla. */
@Composable
private fun NotConfiguredScreen(vm: AppViewModel) {
    val error by vm.initError.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(120.dp)
        Text("Configurazione non valida", style = MaterialTheme.typography.titleLarge)
        Text(error.orEmpty(), textAlign = TextAlign.Center)
        Text(
            "Controlla i secret SUPABASE_URL e SUPABASE_ANON_KEY (o supabase.properties) e ricompila.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingScreen(vm: AppViewModel) {
    val error by vm.loadError.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(120.dp)
        if (error == null) {
            CircularProgressIndicator()
        } else {
            Text(error!!, textAlign = TextAlign.Center)
            Button(onClick = { vm.retryLoad() }) { Text("Riprova") }
            TextButton(onClick = { vm.signOut() }) { Text("Esci dall'account") }
        }
    }
}

@Composable
private fun MainNav(vm: AppViewModel) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onAddExpense = { month -> nav.navigate(Routes.expense(month = month)) },
                onOpenExpense = { id -> nav.navigate(Routes.expense(id = id)) },
                onAddHousehold = { nav.navigate(Routes.ADD_HOUSEHOLD) },
            )
        }
        composable(
            Routes.EXPENSE,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
                navArgument("month") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ExpenseEditorScreen(
                vm = vm,
                expenseId = entry.arguments?.getString("id").orEmpty().ifBlank { null },
                month = entry.arguments?.getString("month").orEmpty().ifBlank { null },
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.ADD_HOUSEHOLD) {
            HouseholdSetupScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
