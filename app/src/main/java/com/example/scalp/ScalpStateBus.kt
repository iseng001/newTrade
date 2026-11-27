package com.example.scalp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared in-process state bus synchronizing [ScalpExecutorService], [CalibrationOverlay],
 * and the Compose UI without requiring bound IPC for local app state.
 */
object ScalpStateBus {

    private val logSeq = AtomicLong(1L)

    private val _isEngineRunning = MutableStateFlow(false)
    val isEngineRunning: StateFlow<Boolean> = _isEngineRunning.asStateFlow()

    private val _isShizukuBound = MutableStateFlow(false)
    val isShizukuBound: StateFlow<Boolean> = _isShizukuBound.asStateFlow()

    private val _isMediaProjectionActive = MutableStateFlow(false)
    val isMediaProjectionActive: StateFlow<Boolean> = _isMediaProjectionActive.asStateFlow()

    private val _pendingOcrTargetTimeMs = MutableStateFlow<Long?>(null)
    val pendingOcrTargetTimeMs: StateFlow<Long?> = _pendingOcrTargetTimeMs.asStateFlow()

    private val _config = MutableStateFlow(ScalpConfig())
    val config: StateFlow<ScalpConfig> = _config.asStateFlow()

    private val _evaluation = MutableStateFlow(EvaluationResult())
    val evaluation: StateFlow<EvaluationResult> = _evaluation.asStateFlow()

    private val _logs = MutableStateFlow<List<EngineLogItem>>(emptyList())
    val logs: StateFlow<List<EngineLogItem>> = _logs.asStateFlow()

    private val _experienceVersion = MutableStateFlow(0L)
    val experienceVersion: StateFlow<Long> = _experienceVersion.asStateFlow()

    fun setEngineRunning(running: Boolean) {
        _isEngineRunning.value = running
    }

    fun setShizukuBound(bound: Boolean) {
        _isShizukuBound.value = bound
    }

    fun setMediaProjectionActive(active: Boolean) {
        _isMediaProjectionActive.value = active
    }

    fun setPendingOcrTarget(targetMs: Long?) {
        _pendingOcrTargetTimeMs.value = targetMs
    }

    fun updateConfig(cfg: ScalpConfig) {
        _config.value = cfg
    }

    fun updateEvaluation(eval: EvaluationResult) {
        _evaluation.value = eval
    }

    fun notifyExperienceChanged() {
        _experienceVersion.value = System.currentTimeMillis()
    }

    fun appendLog(message: String, severity: LogSeverity = LogSeverity.INFO) {
        val item = EngineLogItem(
            id = logSeq.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            message = message,
            severity = severity
        )
        val current = _logs.value
        _logs.value = (listOf(item) + current).take(80)
    }
}
