package com.example.scalp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Draggable system overlay (`TYPE_APPLICATION_OVERLAY`) allowing the trader to position
 * precision crosshairs over MetaTrader 5 BUY/SELL buttons and a 360x100 px ROI frame over PnL.
 */
class CalibrationOverlay(
    private val context: Context,
    private val prefs: ScalpPreferences
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null

    fun isShowing(): Boolean = rootView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(target: CalibrationTarget, onSaved: (Int, Int) -> Unit, onDismissed: () -> Unit = {}) {
        if (!Settings.canDrawOverlays(context)) {
            ScalpStateBus.appendLog("Izin Draw Over Other Apps belum aktif.", LogSeverity.WARNING)
            return
        }
        dismiss()

        val density = context.resources.displayMetrics.density
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val currentCfg = prefs.loadConfig()
        val initialX = when (target) {
            CalibrationTarget.BUY -> if (currentCfg.isBuyCalibrated) currentCfg.buyX else 260
            CalibrationTarget.SELL -> if (currentCfg.isSellCalibrated) currentCfg.sellX else 260
            CalibrationTarget.PNL_ROI -> if (currentCfg.isPnlCalibrated) currentCfg.pnlX else 300
        }
        val initialY = when (target) {
            CalibrationTarget.BUY -> if (currentCfg.isBuyCalibrated) currentCfg.buyY else 480
            CalibrationTarget.SELL -> if (currentCfg.isSellCalibrated) currentCfg.sellY else 480
            CalibrationTarget.PNL_ROI -> if (currentCfg.isPnlCalibrated) currentCfg.pnlY else 380
        }

        val accentColor = when (target) {
            CalibrationTarget.BUY -> Color.parseColor("#10B981")
            CalibrationTarget.SELL -> Color.parseColor("#EF4444")
            CalibrationTarget.PNL_ROI -> Color.parseColor("#F5B731")
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((6 * density).roundToInt(), (6 * density).roundToInt(), (6 * density).roundToInt(), (6 * density).roundToInt())
        }

        val reticleView = ReticleTargetView(context, target, accentColor)
        val reticleWidth = if (target.isRoiBox) OcrFeedbackManager.ROI_WIDTH_PX else (84 * density).roundToInt()
        val reticleHeight = if (target.isRoiBox) OcrFeedbackManager.ROI_HEIGHT_PX else (84 * density).roundToInt()
        container.addView(reticleView, LinearLayout.LayoutParams(reticleWidth, reticleHeight))

        val coordLabel = TextView(context).apply {
            text = "${target.displayName} • Geser ke target MT5"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#E6090C10"))
            setPadding((10 * density).roundToInt(), (4 * density).roundToInt(), (10 * density).roundToInt(), (4 * density).roundToInt())
            gravity = Gravity.CENTER
        }
        container.addView(
            coordLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).roundToInt() }
        )

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val saveBtn = Button(context).apply {
            text = "Simpan"
            textSize = 12f
            setTextColor(Color.parseColor("#090C10"))
            setBackgroundColor(accentColor)
            minimumHeight = (48 * density).roundToInt()
            setPadding((14 * density).roundToInt(), 0, (14 * density).roundToInt(), 0)
        }

        val cancelBtn = Button(context).apply {
            text = "Batal"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1E293B"))
            minimumHeight = (48 * density).roundToInt()
            setPadding((14 * density).roundToInt(), 0, (14 * density).roundToInt(), 0)
        }

        btnRow.addView(
            saveBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (8 * density).roundToInt() }
        )
        btnRow.addView(
            cancelBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        container.addView(
            btnRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).roundToInt() }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initialX - reticleWidth / 2).coerceAtLeast(0)
            y = (initialY - reticleHeight / 2).coerceAtLeast(0)
        }

        fun currentCenterCoords(): Pair<Int, Int> {
            val loc = IntArray(2)
            reticleView.getLocationOnScreen(loc)
            val cx = loc[0] + reticleView.width / 2
            val cy = loc[1] + reticleView.height / 2
            return cx to cy
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        val dragListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (event.rawX - touchX)).roundToInt()
                    params.y = (startY + (event.rawY - touchY)).roundToInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                        val (cx, cy) = currentCenterCoords()
                        coordLabel.text = "${target.displayName} • X=$cx  Y=$cy"
                    } catch (_: Throwable) {
                    }
                    true
                }
                else -> false
            }
        }

        reticleView.setOnTouchListener(dragListener)
        coordLabel.setOnTouchListener(dragListener)

        saveBtn.setOnClickListener {
            val (cx, cy) = currentCenterCoords()
            prefs.saveCalibration(target, cx, cy)
            ScalpStateBus.appendLog(
                "Kalibrasi ${target.displayName} disimpan di X=$cx, Y=$cy",
                LogSeverity.SUCCESS
            )
            dismiss()
            onSaved(cx, cy)
        }

        cancelBtn.setOnClickListener {
            dismiss()
            onDismissed()
        }

        try {
            windowManager.addView(container, params)
            rootView = container
            container.post {
                val (cx, cy) = currentCenterCoords()
                coordLabel.text = "${target.displayName} • X=$cx  Y=$cy"
            }
        } catch (t: Throwable) {
            ScalpStateBus.appendLog("Gagal menampilkan overlay kalibrasi: ${t.message}", LogSeverity.ERROR)
        }
    }

    fun dismiss() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
        }
        rootView = null
    }

    private class ReticleTargetView(
        context: Context,
        private val target: CalibrationTarget,
        accentColor: Int
    ) : View(context) {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.FILL
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            if (target.isRoiBox) {
                canvas.drawRect(3f, 3f, width - 3f, height - 3f, fillPaint)
                canvas.drawRect(3f, 3f, width - 3f, height - 3f, dashPaint)
                canvas.drawLine(cx - 18f, cy, cx + 18f, cy, strokePaint)
                canvas.drawLine(cx, cy - 18f, cx, cy + 18f, strokePaint)
                canvas.drawCircle(cx, cy, 5f, dotPaint)
            } else {
                val radius = minOf(cx, cy) - 6f
                canvas.drawCircle(cx, cy, radius, fillPaint)
                canvas.drawCircle(cx, cy, radius, strokePaint)
                canvas.drawLine(8f, cy, width - 8f, cy, strokePaint)
                canvas.drawLine(cx, 8f, cx, height - 8f, strokePaint)
                canvas.drawCircle(cx, cy, 7f, dotPaint)
            }
        }
    }
}
