package com.example.scalp

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale

/**
 * Foreground Service orchestrating the 5-second XAUUSD scalping loop:
 * - Compliant with Android 10-15 MediaProjection lifecycle:
 *   calls startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) BEFORE getMediaProjection(),
 *   and registers MediaProjection.Callback BEFORE createVirtualDisplay().
 * - Binds Shizuku TouchUserService for low-latency non-root tap execution.
 * - Schedules 5-minute (5 M1 candles) ImageReader capture + ML Kit OCR PnL feedback loop.
 */
class ScalpExecutorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var prefs: ScalpPreferences
    private lateinit var engine: ScalpEngine
    private val ocrManager = OcrFeedbackManager()

    private var pollingJob: Job? = null
    private var pendingOcrJob: Job? = null

    private var touchService: ITouchService? = null
    private var shizukuServiceArgs: Shizuku.UserServiceArgs? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var screenDensityDpi: Int = 320

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            ScalpStateBus.appendLog("MediaProjection dihentikan oleh sistem.", LogSeverity.WARNING)
            releaseProjectionResources()
        }
    }

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                touchService = ITouchService.Stub.asInterface(service)
                ScalpStateBus.setShizukuBound(true)
                ScalpStateBus.appendLog("Shizuku TouchUserService terhubung (UID 2000).", LogSeverity.SUCCESS)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            touchService = null
            ScalpStateBus.setShizukuBound(false)
            ScalpStateBus.appendLog("Koneksi Shizuku TouchUserService terputus.", LogSeverity.WARNING)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = ScalpPreferences(applicationContext)
        engine = ScalpEngine(applicationContext, prefs)
        createNotificationChannel()
        measureDisplayMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ScalpStateBus.appendLog("Menghentikan ScalpExecutorService...", LogSeverity.INFO)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_PROJECTION_CODE, Activity.RESULT_CANCELED)
                val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                }

                // Mandatory Android 10-15 sequence: call startForeground() BEFORE getMediaProjection()
                val hasProjectionGrant = resultCode == Activity.RESULT_OK && projectionData != null
                val notification = buildNotification(
                    if (hasProjectionGrant) "Live Engine + OCR MediaProjection aktif..."
                    else "Dry-Run Engine XAUUSD M1 aktif..."
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val fgsType = if (hasProjectionGrant) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                if (resultCode == Activity.RESULT_OK && projectionData != null) {
                    setupMediaProjection(resultCode, projectionData)
                } else {
                    ScalpStateBus.setMediaProjectionActive(false)
                    ScalpStateBus.appendLog(
                        "Engine berjalan tanpa MediaProjection (Mode Dry-Run).",
                        LogSeverity.INFO
                    )
                }

                bindShizukuIfAvailable()
                startEvaluationLoop()
            }
        }
        return START_STICKY
    }

    private fun measureDisplayMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width().coerceAtLeast(720)
            screenHeight = bounds.height().coerceAtLeast(1280)
            screenDensityDpi = resources.displayMetrics.densityDpi
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            screenWidth = dm.widthPixels.coerceAtLeast(720)
            screenHeight = dm.heightPixels.coerceAtLeast(1280)
            screenDensityDpi = dm.densityDpi
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            releaseProjectionResources()
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data) ?: return
            mediaProjection = projection

            // Mandatory Android 14+ requirement: register Callback BEFORE createVirtualDisplay()
            projection.registerCallback(mediaProjectionCallback, mainHandler)

            val reader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "ScalpShizukuCapture",
                screenWidth,
                screenHeight,
                screenDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )

            ScalpStateBus.setMediaProjectionActive(true)
            ScalpStateBus.appendLog(
                "MediaProjection aktif (${screenWidth}×${screenHeight}) siap untuk OCR PnL.",
                LogSeverity.SUCCESS
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed setting up MediaProjection", t)
            ScalpStateBus.setMediaProjectionActive(false)
            ScalpStateBus.appendLog("Gagal inisialisasi MediaProjection: ${t.message}", LogSeverity.ERROR)
        }
    }

    private fun bindShizukuIfAvailable() {
        try {
            if (!Shizuku.pingBinder()) {
                ScalpStateBus.setShizukuBound(false)
                return
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ScalpStateBus.setShizukuBound(false)
                return
            }
            val args = Shizuku.UserServiceArgs(
                ComponentName(packageName, TouchUserService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("scalptouch")
                .debuggable(false)
                .version(1)

            shizukuServiceArgs = args
            Shizuku.bindUserService(args, shizukuConnection)
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku bind error", t)
            ScalpStateBus.appendLog("Shizuku belum aktif/diizinkan: ${t.message}", LogSeverity.WARNING)
        }
    }

    private fun startEvaluationLoop() {
        pollingJob?.cancel()
        ScalpStateBus.setEngineRunning(true)
        ScalpStateBus.appendLog("Siklus polling ScalpEngine dimulai (interval 5 detik).", LogSeverity.SUCCESS)

        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val eval = engine.evaluate()
                    ScalpStateBus.updateEvaluation(eval)
                    updateNotification(
                        String.format(
                            Locale.US,
                            "XAU %.2f | %s | %d/14 Filter",
                            eval.lastPrice,
                            eval.regime.label,
                            eval.passedFilterCount
                        )
                    )

                    if (eval.errorMessage != null) {
                        ScalpStateBus.appendLog("Eval error: ${eval.errorMessage}", LogSeverity.ERROR)
                    } else if (eval.executableDirection != TradeDirection.NONE && eval.riskPlan != null) {
                        handleExecutableSignal(eval)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Polling cycle error", t)
                    ScalpStateBus.appendLog("Kesalahan siklus evaluasi: ${t.message}", LogSeverity.ERROR)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun handleExecutableSignal(eval: EvaluationResult) {
        val plan = eval.riskPlan ?: return
        val summary = eval.featureSummary ?: return
        val config = prefs.loadConfig()

        // Lock entry cooldown immediately (3 min = 180,000 ms)
        prefs.recordTradeTimestamp(System.currentTimeMillis())

        if (config.isDryRun) {
            ScalpStateBus.appendLog(
                "[DRY-RUN] Sinyal ${plan.direction.label} @ ${String.format(Locale.US, "%.2f", plan.entryPrice)} | Lot ${plan.lotSize} | SL ${String.format(Locale.US, "%.2f", plan.stopLoss)} | TP ${String.format(Locale.US, "%.2f", plan.takeProfit)} (Tanpa Tap MT5)",
                LogSeverity.SIGNAL
            )
            if (config.hasTelegramConfigured) {
                engine.sendEntryTelegramAlert(plan, isDryRun = true)
            }
            return
        }

        val targetX = if (plan.direction == TradeDirection.BUY) config.buyX else config.sellX
        val targetY = if (plan.direction == TradeDirection.BUY) config.buyY else config.sellY

        if (targetX < 0 || targetY < 0) {
            ScalpStateBus.appendLog(
                "Sinyal ${plan.direction.label} dibatalkan: koordinat tombol belum dikalibrasi!",
                LogSeverity.WARNING
            )
            return
        }

        val svc = touchService
        if (svc == null) {
            ScalpStateBus.appendLog(
                "Sinyal ${plan.direction.label} dibatalkan: Shizuku TouchUserService belum terhubung.",
                LogSeverity.ERROR
            )
            bindShizukuIfAvailable()
            return
        }

        try {
            svc.tap(targetX, targetY)
            ScalpStateBus.appendLog(
                "[LIVE TAP] ${plan.direction.label} dieksekusi @ ($targetX, $targetY) | Harga ${String.format(Locale.US, "%.2f", plan.entryPrice)} | Lot ${plan.lotSize}",
                LogSeverity.SIGNAL
            )
            if (config.hasTelegramConfigured) {
                engine.sendEntryTelegramAlert(plan, isDryRun = false)
            }
            scheduleOcrFeedback(plan.direction, summary.features.copyOf())
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku tap execution failed", t)
            ScalpStateBus.appendLog("Gagal mengirim tap Shizuku: ${t.message}", LogSeverity.ERROR)
        }
    }

    /**
     * Schedules screen capture and offline ML Kit OCR PnL evaluation after 5 minutes (5 M1 candles).
     */
    private fun scheduleOcrFeedback(direction: TradeDirection, entryFeatures: FloatArray) {
        pendingOcrJob?.cancel()
        val targetTimeMs = System.currentTimeMillis() + OCR_DELAY_MS
        ScalpStateBus.setPendingOcrTarget(targetTimeMs)
        ScalpStateBus.appendLog(
            "OCR PnL dijadwalkan dalam 5 menit (5 candle M1) untuk trade ${direction.label}.",
            LogSeverity.INFO
        )

        pendingOcrJob = serviceScope.launch {
            delay(OCR_DELAY_MS)
            ScalpStateBus.setPendingOcrTarget(null)
            captureAndProcessOcr(direction, entryFeatures)
        }
    }

    private fun captureAndProcessOcr(direction: TradeDirection, entryFeatures: FloatArray) {
        val cfg = prefs.loadConfig()
        if (!cfg.isPnlCalibrated) {
            ScalpStateBus.appendLog("OCR dilewati: Area ROI PnL belum dikalibrasi.", LogSeverity.WARNING)
            return
        }
        val bitmap = captureLatestFrameBitmap()
        if (bitmap == null) {
            ScalpStateBus.appendLog("OCR gagal: Tidak ada frame layar dari ImageReader.", LogSeverity.WARNING)
            return
        }

        ocrManager.analyzeFrameForPnl(bitmap, cfg.pnlX, cfg.pnlY) { pnl, rawText ->
            bitmap.recycle()
            if (pnl == null) {
                ScalpStateBus.appendLog(
                    "OCR tidak menemukan angka PnL valid (teks: '${rawText.take(40)}').",
                    LogSeverity.WARNING
                )
                return@analyzeFrameForPnl
            }

            val updatedCfg = prefs.applyOcrFeedback(pnl)
            engine.appendExperienceRow(direction, pnl, entryFeatures)
            ScalpStateBus.appendLog(
                "OCR Feedback: PnL ${String.format(Locale.US, "%+.2f", pnl)} USD | Harian ${String.format(Locale.US, "%.2f", updatedCfg.dailyPnl)} | WR ${String.format(Locale.US, "%.1f%%", updatedCfg.winRate * 100.0)}",
                if (pnl >= 0.0) LogSeverity.SUCCESS else LogSeverity.WARNING
            )

            serviceScope.launch {
                if (updatedCfg.hasTelegramConfigured) {
                    engine.sendOcrFeedbackTelegram(direction, pnl, updatedCfg)
                }
            }
        }
    }

    private fun captureLatestFrameBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (t: Throwable) {
            Log.e(TAG, "acquireLatestImage failed", t)
            null
        } ?: return null

        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmapWidth = image.width + rowPadding / pixelStride
            val rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)
            if (bitmapWidth != image.width) {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                rawBitmap.recycle()
                cropped
            } else {
                rawBitmap
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Bitmap conversion failed", t)
            null
        } finally {
            image.close()
        }
    }

    private fun releaseProjectionResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        mediaProjection = null
        ScalpStateBus.setMediaProjectionActive(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScalpExecutorService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(0, "STOP", pendingStop)
            .build()
    }

    private fun updateNotification(contentText: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(contentText))
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        pendingOcrJob?.cancel()
        serviceScope.cancel()
        releaseProjectionResources()
        ocrManager.close()
        try {
            shizukuServiceArgs?.let { Shizuku.unbindUserService(it, shizukuConnection, true) }
        } catch (_: Throwable) {
        }
        touchService = null
        ScalpStateBus.setShizukuBound(false)
        ScalpStateBus.setPendingOcrTarget(null)
        ScalpStateBus.setEngineRunning(false)
        ScalpStateBus.appendLog("ScalpExecutorService berhenti.", LogSeverity.INFO)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScalpExecutorService"
        private const val CHANNEL_ID = "scalp_shizuku_engine_channel"
        private const val NOTIFICATION_ID = 4109
        private const val POLL_INTERVAL_MS = 5_000L
        private const val OCR_DELAY_MS = 5 * 60 * 1000L

        const val ACTION_START = "com.example.scalp.ACTION_START"
        const val ACTION_STOP = "com.example.scalp.ACTION_STOP"
        const val EXTRA_PROJECTION_CODE = "extra_projection_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
    }
}
