package com.nanobuo.myfifty

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.myfifty.ui.AppRoot
import com.nanobuo.myfifty.ui.AppViewModel
import com.nanobuo.myfifty.ui.Gate
import com.nanobuo.myfifty.ui.theme.MyFiftyTheme
import com.nanobuo.myfifty.ui.theme.isAppDark

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val start = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            vm.gate.value == Gate.LOADING && vm.loadError.value == null &&
                System.currentTimeMillis() - start < 3000
        }
        enableEdgeToEdge()
        setContent {
            val mode by vm.theme.collectAsStateWithLifecycle()
            val dark = isAppDark(mode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            MyFiftyTheme(dark = dark) { AppRoot(vm) }
        }
    }
}
