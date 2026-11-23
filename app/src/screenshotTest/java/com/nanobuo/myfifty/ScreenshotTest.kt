package com.nanobuo.myfifty

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.Household
import com.nanobuo.myfifty.domain.Member
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.HouseholdData
import com.nanobuo.myfifty.ui.screens.AuthScreen
import com.nanobuo.myfifty.ui.screens.SettingsTab
import com.nanobuo.myfifty.ui.screens.ExpenseEditorScreen
import com.nanobuo.myfifty.ui.screens.HomeScreen
import com.nanobuo.myfifty.ui.screens.StatsTab
import com.nanobuo.myfifty.ui.theme.MyFiftyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * Genera screenshot delle schermate principali (chiaro/scuro) con dati finti.
 * Eseguito solo se -Dscreenshots=true / SCREENSHOTS=1, l'output va in build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val enabled = System.getenv("SCREENSHOTS") == "1"

    private fun sample(): HouseholdData {
        val h = Household("h", "Casa Via Roma", "K7QM2XWP")
        val m = listOf(Member("a", "Anna", "owner"), Member("b", "Bruno"), Member("c", "Carla"))
        val now = YearMonth.now()
        val prev = now.minusMonths(1)
        fun d(m: YearMonth, day: Int) = m.atDay(minOf(day, m.lengthOfMonth())).toString()
        var n = 0
        fun e(kind: String, desc: String, cat: String, cents: Long, date: String, paid: String, split: List<String> = listOf("a", "b", "c"), rec: Boolean = false) =
            Expense("e${n++}", "h", kind, desc, cat, cents, date, paid, split, rec)
        val list = mutableListOf(
            e("expense", "Spesa Esselunga", "groceries", 8640, d(now, LocalDate.now().dayOfMonth), "a"),
            e("expense", "Bolletta luce", "electricity", 11230, d(now, 3), "b", rec = true),
            e("refund", "Conguaglio gas", "gas", 2400, d(now, 3), "c"),
            e("settlement", "Saldo", "other", 3000, d(now, 2), "c", listOf("a")),
            e("expense", "Fibra", "internet", 2990, d(now, 1), "a", rec = true),
            e("expense", "Netflix", "subscriptions", 1399, d(now, 1), "b", listOf("a", "b")),
            e("expense", "Affitto", "rent", 120000, d(prev, 1), "a", rec = true),
        )
        for (i in 2..11) {
            val mm = now.minusMonths(i.toLong())
            list += e("expense", "Spesa", "groceries", 15000L + i * 1700, d(mm, 5), listOf("a", "b", "c")[i % 3])
            list += e("expense", "Bolletta gas", "gas", 6000L + (i % 4) * 2500, d(mm, 12), "b")
            list += e("expense", "Fibra", "internet", 2990, d(mm, 1), "a")
        }
        // un saldo di fine mese ancora aperto per il mese precedente
        list += e("expense", "Spesa grande", "groceries", 30000, d(prev, 20), "a")
        return HouseholdData(h, m, list, loaded = true)
    }

    private fun shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable (AppViewModel) -> Unit) {
        if (!enabled) return
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.showPreview(sample(), "a", today = LocalDate.now().withDayOfMonth(3))
        rule.setContent {
            MyFiftyTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box { content(vm) } }
            }
        }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeLight() = shot("1_home", false) { HomeScreen(it, {}, {}, {}) }
    @Test fun homeDark() = shot("1_home", true) { HomeScreen(it, {}, {}, {}) }
    @Test fun settings() = shot("2_altro", true) { vm -> SettingsTab(vm, sample()) {} }
    @Test fun stats() = shot("3_statistiche", false) { vm -> StatsTab(vm, sample()) }
    @Test fun editor() = shot("4_editor", false) { vm -> ExpenseEditorScreen(vm, null, null) {} }
    @Test fun auth() = shot("0_login", true) { vm -> AuthScreen(vm) }
}
