package com.example.scalp

enum class TradeDirection(val label: String) {
    BUY("BUY"),
    SELL("SELL"),
    NONE("HOLD")
}

enum class HmmRegime(val code: Int, val label: String) {
    RANGING(0, "Ranging"),
    TRENDING(1, "Trending"),
    VOLATILE(2, "Volatile");

    companion object {
        fun fromCode(code: Int): HmmRegime = entries.firstOrNull { it.code == code } ?: RANGING
    }
}

enum class CalibrationTarget(val keyPrefix: String, val displayName: String, val isRoiBox: Boolean) {
    BUY("buy", "Tombol BUY", false),
    SELL("sell", "Tombol SELL", false),
    PNL_ROI("pnl", "Area ROI PnL (360×100)", true)
}

data class OhlcCandle(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class NewsEvent(
    val title: String,
    val timestampMs: Long,
    val impactStars: Int
)

data class FilterCheckItem(
    val number: Int,
    val name: String,
    val criteria: String,
    val currentValue: String,
    val passed: Boolean
)

data class RiskPlan(
    val direction: TradeDirection,
    val entryPrice: Double,
    val atr14: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val lotSize: Double,
    val riskFraction: Double,
    val confidence: Double
)

data class FeatureSummary(
    val features: FloatArray,
    val ema9: Double,
    val ema21: Double,
    val ema50: Double,
    val rsi14: Double,
    val stochK: Double,
    val stochD: Double,
    val macdHist: Double,
    val atr14: Double,
    val atrRatio: Double,
    val bbPos: Double,
    val bodyRatio: Double,
    val rangeAtr: Double,
    val sessionId: Int,
    val sessionLabel: String,
    val newsImpact: Float,
    val activeBullSmc: List<String>,
    val activeBearSmc: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeatureSummary) return false
        return features.contentEquals(other.features) && ema9 == other.ema9 && atr14 == other.atr14
    }

    override fun hashCode(): Int = features.contentHashCode()
}

data class EvaluationResult(
    val timestampMs: Long = 0L,
    val symbol: String = "GC=F (XAUUSD)",
    val lastPrice: Double = 0.0,
    val candleCount: Int = 0,
    val regime: HmmRegime = HmmRegime.RANGING,
    val buyProbability: Double = 0.5,
    val sellProbability: Double = 0.5,
    val candidateDirection: TradeDirection = TradeDirection.NONE,
    val executableDirection: TradeDirection = TradeDirection.NONE,
    val filters: List<FilterCheckItem> = emptyList(),
    val passedFilterCount: Int = 0,
    val featureSummary: FeatureSummary? = null,
    val riskPlan: RiskPlan? = null,
    val nextHighImpactNews: NewsEvent? = null,
    val modelTrainedSamples: Int = 0,
    val csvRowsUsed: Int = 0,
    val statusMessage: String = "Menunggu siklus evaluasi pertama...",
    val errorMessage: String? = null
)

data class ExperienceRow(
    val timestampMs: Long,
    val direction: String,
    val pnl: Double,
    val label: Int,
    val features: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExperienceRow) return false
        return timestampMs == other.timestampMs && direction == other.direction && pnl == other.pnl
    }

    override fun hashCode(): Int = (timestampMs.hashCode() * 31) + direction.hashCode()
}

data class ScalpConfig(
    val buyX: Int = -1,
    val buyY: Int = -1,
    val sellX: Int = -1,
    val sellY: Int = -1,
    val pnlX: Int = -1,
    val pnlY: Int = -1,
    val balance: Double = 1000.0,
    val dailyPnl: Double = 0.0,
    val winRate: Double = 0.58,
    val totalTrades: Int = 0,
    val totalWins: Int = 0,
    val lastTradeTimeMs: Long = 0L,
    val isDryRun: Boolean = true,
    val tgToken: String = "",
    val tgChatId: String = ""
) {
    val isBuyCalibrated: Boolean get() = buyX >= 0 && buyY >= 0
    val isSellCalibrated: Boolean get() = sellX >= 0 && sellY >= 0
    val isPnlCalibrated: Boolean get() = pnlX >= 0 && pnlY >= 0
    val isFullyCalibrated: Boolean get() = isBuyCalibrated && isSellCalibrated && isPnlCalibrated
    val hasTelegramConfigured: Boolean get() = tgToken.isNotBlank() && tgChatId.isNotBlank()
}

enum class LogSeverity { INFO, SIGNAL, SUCCESS, WARNING, ERROR }

data class EngineLogItem(
    val id: Long,
    val timestampMs: Long,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO
)
