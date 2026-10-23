package com.bagimliligim.app.presentation

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bagimliligim.app.appContainer
import com.bagimliligim.app.R
import com.bagimliligim.app.presentation.components.NavGlyph
import com.bagimliligim.app.presentation.screens.DashboardScreen
import com.bagimliligim.app.presentation.screens.DisclosureSheet
import com.bagimliligim.app.presentation.screens.PrivacyDialog
import com.bagimliligim.app.presentation.screens.SettingsScreen
import com.bagimliligim.app.presentation.screens.StatisticsScreen
import com.bagimliligim.app.presentation.screens.SmokingScreen
import com.bagimliligim.app.presentation.screens.SocialScreen
import com.bagimliligim.app.presentation.theme.SwipeMeterTheme
import com.bagimliligim.app.service.SwipeAccessibilityService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: SwipeViewModel = viewModel(factory = SwipeViewModel.Factory(applicationContext, appContainer))
            val state by model.state.collectAsStateWithLifecycle()
            val prefs = state.preferences
            SwipeMeterTheme(prefs?.darkMode ?: true, prefs?.dynamicColor ?: false) {
                LaunchedEffect(prefs?.darkMode) {
                    val dark = prefs?.darkMode ?: true
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppContent(state, model)
            }
        }
    }

    @Composable
    private fun AppContent(state: SwipeUiState, model: SwipeViewModel) {
        val smoking: SmokingViewModel = viewModel(factory = SmokingViewModel.Factory(appContainer))
        val social: SocialViewModel = viewModel(factory = SocialViewModel.Factory(appContainer))
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showDisclosure by rememberSaveable { mutableStateOf(false) }
        var showPrivacy by rememberSaveable { mutableStateOf(false) }
        var serviceEnabled by remember { mutableStateOf(isServiceEnabled()) }
        val serviceConnected by appContainer.serviceConnected.collectAsStateWithLifecycle()
        var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(this).areNotificationsEnabled()) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
        DisposableEffect(lifecycle) {
            val refresh = {
                serviceEnabled = isServiceEnabled()
                notificationsAllowed = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                model.onResume()
                smoking.onResume()
            }
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
            val settingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { serviceEnabled = isServiceEnabled() }
            }
            lifecycle.addObserver(observer)
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, settingObserver)
            refresh()
            onDispose { lifecycle.removeObserver(observer); contentResolver.unregisterContentObserver(settingObserver) }
        }
        val titles = listOf("Bugün", "İstatistikler", "Sigara", "Çevrem", "Ayarlar")
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { NavGlyph(index, if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (tab == 0) Image(painterResource(R.drawable.ic_bond), contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colorResource(R.color.icon_background)))
                        Text(if (tab == 0) stringResource(R.string.app_name) else titles[tab],
                            style = if (tab == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.weight(1f))
                    }
                    if (tab != 0) Text(when (tab) { 1 -> "Günler geçer, izleri kalır."; 2 -> "Bir alışkanlığın gerçek karşılığı."; 3 -> "Küçük adımlar, güçlü bağlar."; else -> "Kendi temponu belirle." },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium) }
                val prefs = state.preferences
                if (prefs == null || !state.data.loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.error == null) CircularProgressIndicator()
                        else Text("Kayıtlar yüklenemedi.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    val active = serviceEnabled && serviceConnected && prefs.accessibilityConsent
                    val accessibilityAction = {
                        if (prefs.accessibilityConsent) openAccessibilitySettings() else showDisclosure = true
                    }
                    when (tab) {
                        0 -> DashboardScreen(state.data, prefs, active, accessibilityAction, model::refresh)
                        1 -> StatisticsScreen(state.data, model::selectPeriod)
                        2 -> SmokingScreen(smoking, prefs.onlinePrices)
                        3 -> SocialScreen(social)
                        else -> SettingsScreen(prefs, active, notificationsAllowed, model::setQuota, model::setLength,
                            onWarnings = { enabled ->
                                model.setWarnings(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }, onAppearance = model::setAppearance, onAccessibility = accessibilityAction,
                            onNotifications = {
                                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            }, onConsent = { showDisclosure = true }, onPrivacy = { showPrivacy = true },
                            onOnlinePrices = smoking::setOnline, pricesConfigured = smoking.configured)
                    }
                    if (!prefs.onboardingComplete || showDisclosure) DisclosureSheet(active,
                        onAccept = { model.completeOnboarding(true) { showDisclosure = false; openAccessibilitySettings() } },
                        onDecline = { model.completeOnboarding(false) { showDisclosure = false } })
                    if (showPrivacy) PrivacyDialog { showPrivacy = false }
                }
            }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(this, SwipeAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val service = it.resolveInfo.serviceInfo
            ComponentName(service.packageName, service.name) == component
        }
    }
    private fun openAccessibilitySettings() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}
