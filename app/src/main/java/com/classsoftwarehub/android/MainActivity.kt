package com.classsoftwarehub.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classsoftwarehub.android.data.model.AppSettings
import com.classsoftwarehub.android.ui.main.MainScreen
import com.classsoftwarehub.android.ui.theme.ClassSoftwareHubTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** 通知点击等场景传入的目标路由 */
        const val EXTRA_ROUTE = "extra_route"
    }

    private val pendingRoute = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // HyperOS/MIUI 会在透明导航栏下自作主张画一块白色对比度背板，
        // 盖在磨砂底栏上面形成"双条"。必须显式关掉。
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)

        val container = (application as ClassSoftwareHubApp).container
        container.downloadRepository.start()

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            ClassSoftwareHubTheme(
                themeMode = settings.themeMode,
                dynamicColorEnabled = settings.dynamicColorEnabled,
            ) {
                MainScreen(
                    initialRoute = pendingRoute.value,
                    onRouteConsumed = { pendingRoute.value = null },
                )
            }
        }

        maybeRequestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
