package com.example.scalp

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists calibration coordinates (BUY, SELL, PnL ROI), risk parameters, daily PnL,
 * EMA win rate, execution mode (Dry-Run vs Live), and Telegram credentials in SharedPreferences.
 */
class ScalpPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): ScalpConfig {
        return ScalpConfig(
            buyX = prefs.getInt(KEY_BUY_X, -1),
            buyY = prefs.getInt(KEY_BUY_Y, -1),
            sellX = prefs.getInt(KEY_SELL_X, -1),
            sellY = prefs.getInt(KEY_SELL_Y, -1),
            pnlX = prefs.getInt(KEY_PNL_X, -1),
            pnlY = prefs.getInt(KEY_PNL_Y, -1),
            balance = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_BALANCE, java.lang.Double.doubleToRawLongBits(1000.0))
            ),
            dailyPnl = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_DAILY_PNL, java.lang.Double.doubleToRawLongBits(0.0))
            ),
            winRate = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_WIN_RATE, java.lang.Double.doubleToRawLongBits(0.58))
            ),
            totalTrades = prefs.getInt(KEY_TOTAL_TRADES, 0),
            totalWins = prefs.getInt(KEY_TOTAL_WINS, 0),
            lastTradeTimeMs = prefs.getLong(KEY_LAST_TRADE_MS, 0L),
            isDryRun = prefs.getBoolean(KEY_DRY_RUN, true),
            tgToken = prefs.getString(KEY_TG_TOKEN, "") ?: "",
            tgChatId = prefs.getString(KEY_TG_CHAT_ID, "") ?: ""
        )
    }

    fun saveCalibration(target: CalibrationTarget, x: Int, y: Int): ScalpConfig {
        prefs.edit().apply {
            when (target) {
                CalibrationTarget.BUY -> {
                    putInt(KEY_BUY_X, x)
                    putInt(KEY_BUY_Y, y)
                }
                CalibrationTarget.SELL -> {
                    putInt(KEY_SELL_X, x)
                    putInt(KEY_SELL_Y, y)
                }
                CalibrationTarget.PNL_ROI -> {
                    putInt(KEY_PNL_X, x)
                    putInt(KEY_PNL_Y, y)
                }
            }
        }.apply()
        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    fun saveSettings(balance: Double, tgToken: String, tgChatId: String): ScalpConfig {
        val safeBalance = balance.coerceAtLeast(10.0)
        prefs.edit()
            .putLong(KEY_BALANCE, java.lang.Double.doubleToRawLongBits(safeBalance))
            .putString(KEY_TG_TOKEN, tgToken.trim())
            .putString(KEY_TG_CHAT_ID, tgChatId.trim())
            .apply()
        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    fun setDryRun(enabled: Boolean): ScalpConfig {
        prefs.edit().putBoolean(KEY_DRY_RUN, enabled).apply()
        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    fun recordTradeTimestamp(timestampMs: Long): ScalpConfig {
        prefs.edit().putLong(KEY_LAST_TRADE_MS, timestampMs).apply()
        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    /**
     * Updates dailyPnl and exponential moving average winRate (alpha = 0.1) after OCR feedback.
     */
    fun applyOcrFeedback(pnlValue: Double): ScalpConfig {
        val current = loadConfig()
        val isWin = if (pnlValue > 0.0) 1.0 else 0.0
        val nextDailyPnl = current.dailyPnl + pnlValue
        val nextWinRate = (current.winRate * 0.9) + (isWin * 0.1)
        val nextTrades = current.totalTrades + 1
        val nextWins = current.totalWins + if (pnlValue > 0.0) 1 else 0

        prefs.edit()
            .putLong(KEY_DAILY_PNL, java.lang.Double.doubleToRawLongBits(nextDailyPnl))
            .putLong(KEY_WIN_RATE, java.lang.Double.doubleToRawLongBits(nextWinRate))
            .putInt(KEY_TOTAL_TRADES, nextTrades)
            .putInt(KEY_TOTAL_WINS, nextWins)
            .apply()

        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    fun resetDailyPnl(): ScalpConfig {
        prefs.edit()
            .putLong(KEY_DAILY_PNL, java.lang.Double.doubleToRawLongBits(0.0))
            .apply()
        val updated = loadConfig()
        ScalpStateBus.updateConfig(updated)
        return updated
    }

    companion object {
        private const val PREFS_NAME = "scalp_shizuku_prefs"
        private const val KEY_BUY_X = "calib_buy_x"
        private const val KEY_BUY_Y = "calib_buy_y"
        private const val KEY_SELL_X = "calib_sell_x"
        private const val KEY_SELL_Y = "calib_sell_y"
        private const val KEY_PNL_X = "calib_pnl_x"
        private const val KEY_PNL_Y = "calib_pnl_y"
        private const val KEY_BALANCE = "risk_balance"
        private const val KEY_DAILY_PNL = "risk_daily_pnl"
        private const val KEY_WIN_RATE = "risk_win_rate"
        private const val KEY_TOTAL_TRADES = "risk_total_trades"
        private const val KEY_TOTAL_WINS = "risk_total_wins"
        private const val KEY_LAST_TRADE_MS = "last_trade_ms"
        private const val KEY_DRY_RUN = "mode_dry_run"
        private const val KEY_TG_TOKEN = "telegram_bot_token"
        private const val KEY_TG_CHAT_ID = "telegram_chat_id"
    }
}
