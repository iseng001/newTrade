package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scalp.CalibrationOverlay
import com.example.scalp.CalibrationTarget
import com.example.scalp.LogSeverity
import com.example.scalp.ScalpExecutorService
import com.example.scalp.ScalpPreferences
import com.example.scalp.ScalpStateBus
import com.example.ui.CalibrationLogScreen
import com.example.ui.FiltersScreen
import com.example.ui.ScalpUiState
import com.example.ui.ScalpViewModel
import com.example.ui.SettingsScreen
import com.example.ui.TerminalScreen
import com.example.ui.theme.BullionGold
import com.example.ui.theme.BullionGoldContainer
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ScalpShizukuTheme
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: ScalpViewModel by viewModels()
    private lateinit var calibrationOverlay: CalibrationOverlay

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshPermissions()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshPermissions()
    }

    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_CODE) {
                viewModel.refreshPermissions()
                val ok = grantResult == PackageManager.PERMISSION_GRANTED
                ScalpStateBus.appendLog(
                    if (ok) "Otorisasi Shizuku berhasil diberikan."
                    else "Otorisasi Shizuku ditolak oleh pengguna.",
                    if (ok) LogSeverity.SUCCESS else LogSeverity.WARNING
                )
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.refreshPermissions()
            if (!granted) {
                viewModel.showBanner("Izin notifikasi ditolak; status foreground mungkin tersembunyi.")
            }
        }

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startExecutorService(result.resultCode, result.data)
            } else {
                viewModel.showBanner("Izin MediaProjection dibatalkan — engine Live tidak dimulai.")
                ScalpStateBus.appendLog("Dialog MediaProjection dibatalkan pengguna.", LogSeverity.WARNING)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        calibrationOverlay = CalibrationOverlay(applicationContext, ScalpPreferences(applicationContext))

        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
        } catch (_: Throwable) {
        }

        setContent {
            ScalpShizukuTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                ScalpShizukuApp(
                    state = uiState,
                    onToggleEngine = ::handleToggleEngine,
                    onEvaluateNow = viewModel::runManualEvaluation,
                    onToggleDryRun = viewModel::toggleDryRun,
                    onRequestOverlay = ::requestOverlayPermission,
                    onRequestShizuku = ::requestShizukuPermission,
                    onRequestNotifications = ::requestNotificationPermission,
                    onLaunchCalibration = ::launchCalibrationOverlay,
                    onSaveSettings = viewModel::saveSettings,
                    onTestTelegram = viewModel::testTelegramNotification,
                    onResetDailyPnl = viewModel::resetDailyPnl,
                    onClearExperienceCsv = viewModel::clearExperienceCsv,
                    onDismissBanner = viewModel::dismissBanner
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun handleToggleEngine() {
        val state = viewModel.uiState.value
        if (state.isEngineRunning) {
            stopExecutorService()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.permissions.notificationsGranted) {
            requestNotificationPermission()
        }

        if (state.config.isDryRun) {
            startExecutorService(Activity.RESULT_CANCELED, null)
            return
        }

        // Live Auto-Tap mode prerequisites
        if (!state.permissions.shizukuGranted) {
            viewModel.showBanner("Otorisasi Shizuku diperlukan untuk mode Live Auto-Tap.")
            requestShizukuPermission()
            return
        }
        if (!state.config.isFullyCalibrated) {
            viewModel.showBanner("Lengkapi kalibrasi tombol BUY, SELL, dan ROI PnL di tab Kalibrasi.")
            return
        }

        val projectionMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionMgr.createScreenCaptureIntent())
    }

    private fun startExecutorService(resultCode: Int, projectionData: Intent?) {
        val intent = Intent(this, ScalpExecutorService::class.java).apply {
            action = ScalpExecutorService.ACTION_START
            putExtra(ScalpExecutorService.EXTRA_PROJECTION_CODE, resultCode)
            if (projectionData != null) {
                putExtra(ScalpExecutorService.EXTRA_PROJECTION_DATA, projectionData)
            }
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopExecutorService() {
        val intent = Intent(this, ScalpExecutorService::class.java).apply {
            action = ScalpExecutorService.ACTION_STOP
        }
        startService(intent)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            viewModel.showBanner("Izin Draw Over Other Apps sudah aktif.")
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                viewModel.showBanner("Layanan Shizuku belum berjalan. Buka aplikasi Shizuku dan aktifkan Wireless Debugging.")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                viewModel.showBanner("Otorisasi Shizuku sudah aktif.")
                viewModel.refreshPermissions()
                return
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
        } catch (t: Throwable) {
            viewModel.showBanner("Gagal menghubungi Shizuku: ${t.message}")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.showBanner("Izin notifikasi otomatis aktif pada versi Android ini.")
        }
    }

    private fun launchCalibrationOverlay(target: CalibrationTarget) {
        if (!Settings.canDrawOverlays(this)) {
            viewModel.showBanner("Aktifkan izin Draw Over Other Apps terlebih dahulu.")
            requestOverlayPermission()
            return
        }
        calibrationOverlay.show(
            target = target,
            onSaved = { x, y ->
                viewModel.showBanner("${target.displayName} dikalibrasi di X=$x, Y=$y")
            }
        )
        viewModel.showBanner("Overlay ${target.displayName} aktif. Pindah ke MT5 lalu geser & tekan Simpan.")
    }

    companion object {
        private const val SHIZUKU_PERMISSION_CODE = 1091
    }
}

private data class NavTabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScalpShizukuApp(
    state: ScalpUiState,
    onToggleEngine: () -> Unit,
    onEvaluateNow: () -> Unit,
    onToggleDryRun: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotifications: () -> Unit,
    onLaunchCalibration: (CalibrationTarget) -> Unit,
    onSaveSettings: (String, String, String) -> Unit,
    onTestTelegram: () -> Unit,
    onResetDailyPnl: () -> Unit,
    onClearExperienceCsv: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = remember {
        listOf(
            NavTabItem("Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_tab_terminal"),
            NavTabItem("14 Filter", Icons.Filled.FilterAlt, Icons.Outlined.FilterAlt, "nav_tab_filters"),
            NavTabItem("Kalibrasi", Icons.Filled.GpsFixed, Icons.Outlined.GpsFixed, "nav_tab_calibration"),
            NavTabItem("Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
        )
    }

    // BackHandler returns to Terminal tab from secondary tabs cleanly
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    LaunchedEffect(state.feedbackBanner) {
        val msg = state.feedbackBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissBanner()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
    ) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = SurfaceCard,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = BullionGold,
                                selectedTextColor = BullionGold,
                                indicatorColor = BullionGoldContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = ObsidianCanvas,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "SCALPSHIZUKU",
                                style = MaterialTheme.typography.titleMedium,
                                color = BullionGold
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = SurfaceCard,
                            titleContentColor = TextPrimary
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = SurfaceCard,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BullionGold,
                                        selectedTextColor = BullionGold,
                                        indicatorColor = BullionGoldContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> TerminalScreen(
                            state = state,
                            onToggleEngine = onToggleEngine,
                            onEvaluateNow = onEvaluateNow,
                            onToggleDryRun = onToggleDryRun,
                            onRequestOverlay = onRequestOverlay,
                            onRequestShizuku = onRequestShizuku,
                            onRequestNotifications = onRequestNotifications,
                            onOpenFiltersTab = { selectedTab = 1 }
                        )
                        1 -> FiltersScreen(
                            state = state,
                            onEvaluateNow = onEvaluateNow
                        )
                        2 -> CalibrationLogScreen(
                            state = state,
                            onLaunchCalibration = onLaunchCalibration
                        )
                        3 -> SettingsScreen(
                            state = state,
                            onSaveSettings = onSaveSettings,
                            onTestTelegram = onTestTelegram,
                            onResetDailyPnl = onResetDailyPnl,
                            onClearExperienceCsv = onClearExperienceCsv
                        )
                    }
                }
            }
        }
    }
}
