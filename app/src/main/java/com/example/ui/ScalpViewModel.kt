package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scalp.EngineLogItem
import com.example.scalp.EvaluationResult
import com.example.scalp.ExperienceRow
import com.example.scalp.LogSeverity
import com.example.scalp.ScalpConfig
import com.example.scalp.ScalpEngine
import com.example.scalp.ScalpPreferences
import com.example.scalp.ScalpStateBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class PermissionSnapshot(
    val overlayGranted: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val notificationsGranted: Boolean = true
)

data class ScalpUiState(
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val config: ScalpConfig = ScalpConfig(),
    val evaluation: EvaluationResult = EvaluationResult(),
    val isEngineRunning: Boolean = false,
    val isShizukuBound: Boolean = false,
    val isMediaProjectionActive: Boolean = false,
    val pendingOcrTargetTimeMs: Long? = null,
    val isManualEvaluating: Boolean = false,
    val isTestingTelegram: Boolean = false,
    val logs: List<EngineLogItem> = emptyList(),
    val experienceRows: List<ExperienceRow> = emptyList(),
    val feedbackBanner: String? = null
)

class ScalpViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ScalpPreferences(application)
    private val engine = ScalpEngine(application, prefs)

    private val _uiState = MutableStateFlow(ScalpUiState(config = prefs.loadConfig()))
    val uiState: StateFlow<ScalpUiState> = _uiState.asStateFlow()

    init {
        ScalpStateBus.updateConfig(prefs.loadConfig())
        refreshPermissions()
        reloadExperienceLog()

        viewModelScope.launch {
            ScalpStateBus.config.collect { cfg ->
                _uiState.update { it.copy(config = cfg) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.evaluation.collect { eval ->
                _uiState.update { it.copy(evaluation = eval) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.isEngineRunning.collect { running ->
                _uiState.update { it.copy(isEngineRunning = running) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.isShizukuBound.collect { bound ->
                _uiState.update { it.copy(isShizukuBound = bound) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.isMediaProjectionActive.collect { active ->
                _uiState.update { it.copy(isMediaProjectionActive = active) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.pendingOcrTargetTimeMs.collect { targetMs ->
                _uiState.update { it.copy(pendingOcrTargetTimeMs = targetMs) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.logs.collect { items ->
                _uiState.update { it.copy(logs = items) }
            }
        }
        viewModelScope.launch {
            ScalpStateBus.experienceVersion.collect {
                reloadExperienceLog()
            }
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        val overlayOk = Settings.canDrawOverlays(ctx)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val shizukuRunning = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        val shizukuGranted = if (shizukuRunning) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }

        _uiState.update {
            it.copy(
                permissions = PermissionSnapshot(
                    overlayGranted = overlayOk,
                    shizukuRunning = shizukuRunning,
                    shizukuGranted = shizukuGranted,
                    notificationsGranted = notifOk
                )
            )
        }
    }

    fun toggleDryRun(enabled: Boolean) {
        prefs.setDryRun(enabled)
        ScalpStateBus.appendLog(
            if (enabled) "Mode beralih ke Dry-Run (Sinyal & Telegram saja, tanpa tap MT5)."
            else "Mode beralih ke Live Auto-Tap Shizuku (Eksekusi nyata ke MT5).",
            if (enabled) LogSeverity.INFO else LogSeverity.WARNING
        )
    }

    fun runManualEvaluation() {
        if (_uiState.value.isManualEvaluating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isManualEvaluating = true) }
            ScalpStateBus.appendLog("Menjalankan evaluasi manual XAUUSD M1 + 14 filter...", LogSeverity.INFO)
            val result = engine.evaluate(forceRetrain = true)
            ScalpStateBus.updateEvaluation(result)
            if (result.errorMessage != null) {
                ScalpStateBus.appendLog("Evaluasi gagal: ${result.errorMessage}", LogSeverity.ERROR)
            } else {
                ScalpStateBus.appendLog(
                    "Evaluasi selesai: ${result.statusMessage} (${result.candleCount} candle M1)",
                    if (result.passedFilterCount == 14) LogSeverity.SIGNAL else LogSeverity.SUCCESS
                )
            }
            _uiState.update { it.copy(isManualEvaluating = false) }
        }
    }

    fun saveSettings(balanceText: String, tgToken: String, tgChatId: String) {
        val parsedBalance = balanceText.replace(",", ".").toDoubleOrNull()
        if (parsedBalance == null || parsedBalance < 10.0) {
            showBanner("Saldo modal minimal 10 USD dan harus berupa angka valid.")
            return
        }
        prefs.saveSettings(parsedBalance, tgToken, tgChatId)
        ScalpStateBus.appendLog("Konfigurasi saldo & Telegram berhasil disimpan.", LogSeverity.SUCCESS)
        showBanner("Konfigurasi tersimpan.")
    }

    fun testTelegramNotification() {
        if (_uiState.value.isTestingTelegram) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingTelegram = true) }
            val result = engine.sendTelegramMessage(
                "<b>✅ SCALPSHIZUKU TERHUBUNG</b>\nUji koneksi Bot API Telegram berhasil. Siap menerima sinyal XAUUSD & laporan OCR PnL."
            )
            result.fold(
                onSuccess = {
                    ScalpStateBus.appendLog("Pesan uji Telegram berhasil terkirim.", LogSeverity.SUCCESS)
                    showBanner("Pesan uji Telegram terkirim!")
                },
                onFailure = { err ->
                    ScalpStateBus.appendLog("Tes Telegram gagal: ${err.message}", LogSeverity.ERROR)
                    showBanner("Gagal kirim Telegram: ${err.message}")
                }
            )
            _uiState.update { it.copy(isTestingTelegram = false) }
        }
    }

    fun resetDailyPnl() {
        prefs.resetDailyPnl()
        ScalpStateBus.appendLog("Akumulasi PnL harian direset ke $0.00.", LogSeverity.INFO)
        showBanner("PnL harian berhasil direset.")
    }

    fun clearExperienceCsv() {
        engine.clearExperienceLog()
        reloadExperienceLog()
        ScalpStateBus.appendLog("experience_log.csv telah dibersihkan.", LogSeverity.WARNING)
        showBanner("Log pengalaman CSV dibersihkan.")
    }

    fun reloadExperienceLog() {
        val rows = engine.readExperienceLog(limit = 300).reversed()
        _uiState.update { it.copy(experienceRows = rows) }
    }

    fun showBanner(message: String) {
        _uiState.update { it.copy(feedbackBanner = message) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(feedbackBanner = null) }
    }
}
