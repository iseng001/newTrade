package com.example.scalp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Core processing engine for ScalpShizuku:
 * - Free public Yahoo Finance M1 OHLC scraper (GC=F, min 60 bars)
 * - Investing.com USD Impact-3 economic calendar HTML scraper (15-min cache)
 * - 37-feature extractor (indices 0..36)
 * - 8-bar Viterbi HMM Regime detector (0=Ranging, 1=Trending, 2=Volatile)
 * - Native MiniXGBoost auto-retraining (historical 5-bar > 1.2*ATR + last 300 CSV rows)
 * - 14 Strict Entry Filters + ATR/Half-Kelly Risk Engine + CSV Logger + Telegram Bot API
 */
class ScalpEngine(
    private val context: Context,
    private val prefs: ScalpPreferences
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val buyModel = MiniXGBoost(numTrees = 45, learningRate = 0.08, lambdaL2 = 1.5)
    private val sellModel = MiniXGBoost(numTrees = 45, learningRate = 0.08, lambdaL2 = 1.5)

    private var cachedNewsEvents: List<NewsEvent> = emptyList()
    private var lastNewsFetchMs: Long = 0L
    private var lastRetrainMs: Long = 0L
    private var lastCsvRowsUsed: Int = 0

    private val csvFile: File
        get() = File(context.filesDir, CSV_FILENAME)

    /**
     * Runs a complete market evaluation cycle.
     */
    suspend fun evaluate(forceRetrain: Boolean = false): EvaluationResult = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val config = prefs.loadConfig()

        val candles = try {
            fetchYahooGoldM1()
        } catch (t: Throwable) {
            Log.e(TAG, "Yahoo Finance fetch error", t)
            return@withContext EvaluationResult(
                timestampMs = nowMs,
                statusMessage = "Gagal mengambil data M1 GC=F",
                errorMessage = t.message ?: "Kesalahan jaringan Yahoo Finance"
            )
        }

        if (candles.size < 60) {
            return@withContext EvaluationResult(
                timestampMs = nowMs,
                candleCount = candles.size,
                statusMessage = "Data candle kurang (${candles.size}/60 bar M1)",
                errorMessage = "Minimal 60 bar M1 diperlukan untuk perhitungan fitur."
            )
        }

        val newsEvents = fetchUsdHighImpactNewsCached(nowMs)
        val nearestNews = findNearestNews(newsEvents, nowMs)
        val isNewsBlackout = nearestNews != null && abs(nearestNews.timestampMs - nowMs) <= NEWS_BLACKOUT_MS
        val newsImpactScore = computeNewsImpactScore(nearestNews, nowMs)

        // Precompute full series for 37 features & Viterbi HMM
        val featureSeries = buildFeatureSeries(candles, newsImpactScore)
        val latestIdx = candles.lastIndex
        val latestSummary = buildFeatureSummary(candles, featureSeries, latestIdx, newsImpactScore)

        // Detect HMM regime via 8-bar Viterbi smoothing
        val regime = detectHmmRegimeViterbi(featureSeries)

        // Train / retrain MiniXGBoost (every 60s or on demand / initial run)
        if (forceRetrain || buyModel.trainedSampleCount == 0 || (nowMs - lastRetrainMs) >= 60_000L) {
            retrainModels(candles, featureSeries)
            lastRetrainMs = nowMs
        }

        val buyProb = buyModel.predictProbability(latestSummary.features)
        val sellProb = sellModel.predictProbability(latestSummary.features)

        // Determine candidate direction based on XGBoost confidence
        val candidateDirection = when {
            buyProb >= sellProb -> TradeDirection.BUY
            else -> TradeDirection.SELL
        }
        val activeConfidence = if (candidateDirection == TradeDirection.BUY) buyProb else sellProb

        // Evaluate all 14 Strict Entry Filters
        val filters = evaluate14Filters(
            nowMs = nowMs,
            config = config,
            summary = latestSummary,
            regime = regime,
            isNewsBlackout = isNewsBlackout,
            nearestNews = nearestNews,
            candidateDirection = candidateDirection,
            confidence = activeConfidence
        )

        val passedCount = filters.count { it.passed }
        val allPassed = passedCount == 14
        val executableDirection = if (allPassed) candidateDirection else TradeDirection.NONE

        val riskPlan = buildRiskPlan(
            direction = candidateDirection,
            entryPrice = candles.last().close,
            atr14 = latestSummary.atr14,
            confidence = activeConfidence,
            config = config
        )

        val statusMsg = when {
            allPassed -> "SINYAL VALID ${executableDirection.label} (14/14 Filter Lolos)"
            else -> {
                val firstBlocked = filters.firstOrNull { !it.passed }
                "HOLD — $passedCount/14 lolos (Blokir #${firstBlocked?.number}: ${firstBlocked?.name})"
            }
        }

        EvaluationResult(
            timestampMs = nowMs,
            lastPrice = candles.last().close,
            candleCount = candles.size,
            regime = regime,
            buyProbability = buyProb,
            sellProbability = sellProb,
            candidateDirection = candidateDirection,
            executableDirection = executableDirection,
            filters = filters,
            passedFilterCount = passedCount,
            featureSummary = latestSummary,
            riskPlan = riskPlan,
            nextHighImpactNews = nearestNews,
            modelTrainedSamples = buyModel.trainedSampleCount,
            csvRowsUsed = lastCsvRowsUsed,
            statusMessage = statusMsg,
            errorMessage = null
        )
    }

    /**
     * Fetches M1 OHLC bars for Gold Futures (GC=F) from Yahoo Finance free public endpoint.
     */
    private fun fetchYahooGoldM1(): List<OhlcCandle> {
        val request = Request.Builder()
            .url(YAHOO_CHART_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Yahoo HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Respons kosong Yahoo")
            val root = JSONObject(body)
            val result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val timestamps = result.getJSONArray("timestamp")
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            val opens = quote.getJSONArray("open")
            val highs = quote.getJSONArray("high")
            val lows = quote.getJSONArray("low")
            val closes = quote.getJSONArray("close")
            val volumes = quote.getJSONArray("volume")

            val candles = ArrayList<OhlcCandle>(timestamps.length())
            for (i in 0 until timestamps.length()) {
                if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue
                val o = opens.optDouble(i, Double.NaN)
                val h = highs.optDouble(i, Double.NaN)
                val l = lows.optDouble(i, Double.NaN)
                val c = closes.optDouble(i, Double.NaN)
                val v = if (volumes.isNull(i)) 0.0 else volumes.optDouble(i, 0.0)
                if (!o.isFinite() || !h.isFinite() || !l.isFinite() || !c.isFinite() || c <= 0.0) continue
                candles.add(
                    OhlcCandle(
                        timestampMs = timestamps.getLong(i) * 1000L,
                        open = o,
                        high = max(h, max(o, c)),
                        low = min(l, min(o, c)),
                        close = c,
                        volume = max(0.0, v)
                    )
                )
            }
            return candles
        }
    }

    /**
     * Scrapes Investing.com economic calendar for 3-star USD high-impact events with 15-min cache.
     */
    private fun fetchUsdHighImpactNewsCached(nowMs: Long): List<NewsEvent> {
        if (nowMs - lastNewsFetchMs < NEWS_CACHE_TTL_MS && lastNewsFetchMs > 0L) {
            return cachedNewsEvents
        }
        val parsed = ArrayList<NewsEvent>()
        try {
            val doc = Jsoup.connect(INVESTING_CALENDAR_URL)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(8000)
                .get()

            val rows = doc.select("tr.js-event-item")
            val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            for (row in rows) {
                val currency = row.selectFirst("td.flagCur")?.text()?.uppercase(Locale.US) ?: ""
                if (!currency.contains("USD")) continue

                val bullIcons = row.select("td.sentiment i.grayFullBullishIcon").size
                val altImpact = row.select("td.sentiment[data-img_key=bull3]").size
                val impactStars = if (altImpact > 0) 3 else bullIcons
                if (impactStars < 3) continue

                val dateTimeAttr = row.attr("data-event-datetime")
                val title = row.selectFirst("td.event a")?.text()?.trim() ?: "USD High Impact News"
                val eventTs = try {
                    fmt.parse(dateTimeAttr)?.time ?: 0L
                } catch (_: Throwable) {
                    0L
                }
                if (eventTs > 0L) {
                    parsed.add(NewsEvent(title = title, timestampMs = eventTs, impactStars = 3))
                }
            }
            cachedNewsEvents = parsed
            lastNewsFetchMs = nowMs
        } catch (t: Throwable) {
            Log.w(TAG, "Investing.com scrape unavailable, keeping cached schedule", t)
            lastNewsFetchMs = nowMs
        }
        return cachedNewsEvents
    }

    private fun findNearestNews(events: List<NewsEvent>, nowMs: Long): NewsEvent? {
        return events.minByOrNull { abs(it.timestampMs - nowMs) }
    }

    private fun computeNewsImpactScore(nearest: NewsEvent?, nowMs: Long): Float {
        if (nearest == null) return 0f
        val diffMin = abs(nearest.timestampMs - nowMs) / 60_000.0
        return when {
            diffMin <= 15.0 -> 1.0f
            diffMin <= 60.0 -> (1.0 - (diffMin - 15.0) / 45.0).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /**
     * Builds the 37-feature matrix across the candle series (indices 0..36 matching PDF spec).
     */
    private fun buildFeatureSeries(candles: List<OhlcCandle>, newsImpact: Float): Array<FloatArray> {
        val n = candles.size
        val closes = DoubleArray(n) { candles[it].close }
        val highs = DoubleArray(n) { candles[it].high }
        val lows = DoubleArray(n) { candles[it].low }
        val opens = DoubleArray(n) { candles[it].open }
        val volumes = DoubleArray(n) { candles[it].volume }

        val ema9 = computeEma(closes, 9)
        val ema12 = computeEma(closes, 12)
        val ema21 = computeEma(closes, 21)
        val ema26 = computeEma(closes, 26)
        val ema50 = computeEma(closes, 50)
        val macdLine = DoubleArray(n) { ema12[it] - ema26[it] }
        val macdSignal = computeEma(macdLine, 9)
        val macdHist = DoubleArray(n) { macdLine[it] - macdSignal[it] }

        val rsi7 = computeRsi(closes, 7)
        val rsi14 = computeRsi(closes, 14)
        val atr14 = computeAtr(highs, lows, closes, 14)
        val atr50 = computeAtr(highs, lows, closes, 50)
        val (stochK, stochD) = computeStoch(highs, lows, closes, 14, 3)

        val result = Array(n) { FloatArray(37) }
        var lastSwingHigh = highs[0]
        var lastSwingLow = lows[0]
        var trendBias = 0 // 1 = bull, -1 = bear

        for (i in 0 until n) {
            val c = closes[i]
            val o = opens[i]
            val h = highs[i]
            val l = lows[i]
            val atr = max(atr14[i], 1e-4)

            // Update confirmed 3-bar pivot swing high/low using past bars only (no lookahead)
            if (i >= 4) {
                val pIdx = i - 2
                if (highs[pIdx] > highs[pIdx - 1] && highs[pIdx] >= highs[pIdx + 1] &&
                    highs[pIdx] > highs[pIdx - 2] && highs[pIdx] >= highs[pIdx + 2]
                ) {
                    lastSwingHigh = highs[pIdx]
                }
                if (lows[pIdx] < lows[pIdx - 1] && lows[pIdx] <= lows[pIdx + 1] &&
                    lows[pIdx] < lows[pIdx - 2] && lows[pIdx] <= lows[pIdx + 2]
                ) {
                    lastSwingLow = lows[pIdx]
                }
            } else {
                lastSwingHigh = max(lastSwingHigh, h)
                lastSwingLow = min(lastSwingLow, l)
            }

            // 0..9 Smart Money Concepts (BOS, CHoCH, FVG, OB, Swing distances ATR-normalized)
            val brokeHigh = c > lastSwingHigh && i > 0 && closes[i - 1] <= lastSwingHigh
            val brokeLow = c < lastSwingLow && i > 0 && closes[i - 1] >= lastSwingLow

            val bosBull = if (brokeHigh && trendBias >= 0) 1f else 0f
            val bosBear = if (brokeLow && trendBias <= 0) 1f else 0f
            val chochBull = if (brokeHigh && trendBias < 0) 1f else 0f
            val chochBear = if (brokeLow && trendBias > 0) 1f else 0f

            if (brokeHigh) trendBias = 1
            if (brokeLow) trendBias = -1

            // Fair Value Gap (3-candle imbalance)
            val fvgBull = if (i >= 2 && lows[i] > highs[i - 2]) 1f else 0f
            val fvgBear = if (i >= 2 && highs[i] < lows[i - 2]) 1f else 0f

            // Order Block proxy: opposing candle followed by impulsive displacement > 0.8*ATR
            val obBull = if (i >= 2 && closes[i - 1] < opens[i - 1] && (c - o) > 0.8 * atr) 1f else 0f
            val obBear = if (i >= 2 && closes[i - 1] > opens[i - 1] && (o - c) > 0.8 * atr) 1f else 0f

            val distSwingH = ((lastSwingHigh - c) / atr).toFloat()
            val distSwingL = ((c - lastSwingLow) / atr).toFloat()

            // 10..15 Return & ROC
            val ret1 = pctReturn(closes, i, 1)
            val ret3 = pctReturn(closes, i, 3)
            val ret5 = pctReturn(closes, i, 5)
            val ret15 = pctReturn(closes, i, 15)
            val roc5 = ret5 * 100.0
            val roc10 = pctReturn(closes, i, 10) * 100.0

            // 16..20 EMA Structure (ATR-normalized)
            val ema9Dist = (c - ema9[i]) / atr
            val ema21Dist = (c - ema21[i]) / atr
            val ema50Dist = (c - ema50[i]) / atr
            val ema921Diff = (ema9[i] - ema21[i]) / atr
            val ema2150Diff = (ema21[i] - ema50[i]) / atr

            // 26..30 Volatility & Volume
            val atrRatio = atr / max(atr50[i], 1e-4)
            val (bbPos, bbWidth) = computeBollinger(closes, i, 20)
            val volZ = computeVolumeZScore(volumes, i, 20)

            // 31..34 Candle Anatomy
            val candleRange = max(h - l, 1e-4)
            val body = abs(c - o)
            val bodyRatio = (body / candleRange).coerceIn(0.0, 1.0)
            val upperWick = ((h - max(o, c)) / candleRange).coerceIn(0.0, 1.0)
            val lowerWick = ((min(o, c) - l) / candleRange).coerceIn(0.0, 1.0)
            val rangeAtr = candleRange / atr

            // 35..36 Session & Macro
            val sessionId = getUtcSessionId(candles[i].timestampMs)

            val f = result[i]
            f[0] = bosBull
            f[1] = bosBear
            f[2] = chochBull
            f[3] = chochBear
            f[4] = fvgBull
            f[5] = fvgBear
            f[6] = obBull
            f[7] = obBear
            f[8] = distSwingH
            f[9] = distSwingL
            f[10] = ret1.toFloat()
            f[11] = ret3.toFloat()
            f[12] = ret5.toFloat()
            f[13] = ret15.toFloat()
            f[14] = roc5.toFloat()
            f[15] = roc10.toFloat()
            f[16] = ema9Dist.toFloat()
            f[17] = ema21Dist.toFloat()
            f[18] = ema50Dist.toFloat()
            f[19] = ema921Diff.toFloat()
            f[20] = ema2150Diff.toFloat()
            f[21] = rsi7[i].toFloat()
            f[22] = rsi14[i].toFloat()
            f[23] = stochK[i].toFloat()
            f[24] = stochD[i].toFloat()
            f[25] = macdHist[i].toFloat()
            f[26] = atr.toFloat()
            f[27] = atrRatio.toFloat()
            f[28] = bbPos.toFloat()
            f[29] = bbWidth.toFloat()
            f[30] = volZ.toFloat()
            f[31] = bodyRatio.toFloat()
            f[32] = upperWick.toFloat()
            f[33] = lowerWick.toFloat()
            f[34] = rangeAtr.toFloat()
            f[35] = sessionId.toFloat()
            f[36] = newsImpact
        }
        return result
    }

    private fun buildFeatureSummary(
        candles: List<OhlcCandle>,
        featureSeries: Array<FloatArray>,
        idx: Int,
        newsImpact: Float
    ): FeatureSummary {
        val closes = DoubleArray(candles.size) { candles[it].close }
        val ema9 = computeEma(closes, 9)[idx]
        val ema21 = computeEma(closes, 21)[idx]
        val ema50 = computeEma(closes, 50)[idx]
        val f = featureSeries[idx]

        // Look back up to 3 recent candles for active SMC structures
        val start = max(0, idx - 2)
        val bullSmc = mutableListOf<String>()
        val bearSmc = mutableListOf<String>()
        if ((start..idx).any { featureSeries[it][0] > 0.5f }) bullSmc.add("BOS")
        if ((start..idx).any { featureSeries[it][2] > 0.5f }) bullSmc.add("CHoCH")
        if ((start..idx).any { featureSeries[it][4] > 0.5f }) bullSmc.add("FVG")
        if ((start..idx).any { featureSeries[it][6] > 0.5f }) bullSmc.add("OB")

        if ((start..idx).any { featureSeries[it][1] > 0.5f }) bearSmc.add("BOS")
        if ((start..idx).any { featureSeries[it][3] > 0.5f }) bearSmc.add("CHoCH")
        if ((start..idx).any { featureSeries[it][5] > 0.5f }) bearSmc.add("FVG")
        if ((start..idx).any { featureSeries[it][7] > 0.5f }) bearSmc.add("OB")

        val sessId = getUtcSessionId(System.currentTimeMillis())
        val sessLabel = when (sessId) {
            1 -> "Sydney / Asia"
            2 -> "London (Optimal)"
            3 -> "New York (Optimal)"
            else -> "Off-Session"
        }

        return FeatureSummary(
            features = f,
            ema9 = ema9,
            ema21 = ema21,
            ema50 = ema50,
            rsi14 = f[22].toDouble(),
            stochK = f[23].toDouble(),
            stochD = f[24].toDouble(),
            macdHist = f[25].toDouble(),
            atr14 = f[26].toDouble(),
            atrRatio = f[27].toDouble(),
            bbPos = f[28].toDouble(),
            bodyRatio = f[31].toDouble(),
            rangeAtr = f[34].toDouble(),
            sessionId = sessId,
            sessionLabel = sessLabel,
            newsImpact = newsImpact,
            activeBullSmc = bullSmc,
            activeBearSmc = bearSmc
        )
    }

    /**
     * Viterbi smoothing over last 8 bars using atr_ratio (idx 27) and |ema9_21_diff| (idx 19).
     * States: 0 = Ranging, 1 = Trending, 2 = Volatile.
     */
    private fun detectHmmRegimeViterbi(featureSeries: Array<FloatArray>): HmmRegime {
        val window = min(8, featureSeries.size)
        if (window <= 0) return HmmRegime.RANGING
        val startIdx = featureSeries.size - window

        // State centroids for (atr_ratio, |ema9_21_diff|)
        val meanAtr = doubleArrayOf(0.75, 1.15, 2.05)
        val stdAtr = doubleArrayOf(0.30, 0.35, 0.55)
        val meanTrend = doubleArrayOf(0.18, 0.85, 0.55)
        val stdTrend = doubleArrayOf(0.20, 0.45, 0.50)

        val transLog = arrayOf(
            doubleArrayOf(ln(0.72), ln(0.18), ln(0.10)),
            doubleArrayOf(ln(0.15), ln(0.73), ln(0.12)),
            doubleArrayOf(ln(0.15), ln(0.20), ln(0.65))
        )
        val dp = Array(window) { DoubleArray(3) }

        fun emissionLogProb(state: Int, atrRatio: Double, trendMag: Double): Double {
            val z1 = (atrRatio - meanAtr[state]) / stdAtr[state]
            val z2 = (trendMag - meanTrend[state]) / stdTrend[state]
            return -0.5 * (z1 * z1 + z2 * z2) - ln(stdAtr[state] * stdTrend[state])
        }

        for (t in 0 until window) {
            val f = featureSeries[startIdx + t]
            val atrR = f[27].toDouble().coerceIn(0.2, 4.0)
            val trendMag = abs(f[19].toDouble()).coerceIn(0.0, 4.0)
            for (s in 0 until 3) {
                val emit = emissionLogProb(s, atrR, trendMag)
                if (t == 0) {
                    dp[t][s] = ln(1.0 / 3.0) + emit
                } else {
                    var bestPrev = Double.NEGATIVE_INFINITY
                    for (p in 0 until 3) {
                        val candidate = dp[t - 1][p] + transLog[p][s]
                        if (candidate > bestPrev) bestPrev = candidate
                    }
                    dp[t][s] = bestPrev + emit
                }
            }
        }

        val lastRow = dp[window - 1]
        var bestState = 0
        for (s in 1 until 3) {
            if (lastRow[s] > lastRow[bestState]) bestState = s
        }
        return HmmRegime.fromCode(bestState)
    }

    /**
     * Auto-retrains BUY and SELL MiniXGBoost models combining historical labels
     * (5-candle M1 move > 1.2 * ATR) with up to the last 300 rows from experience_log.csv.
     */
    private fun retrainModels(candles: List<OhlcCandle>, featureSeries: Array<FloatArray>) {
        val buyX = ArrayList<FloatArray>()
        val buyY = ArrayList<Int>()
        val sellX = ArrayList<FloatArray>()
        val sellY = ArrayList<Int>()

        val usableEnd = candles.size - 6
        for (i in 50 until usableEnd) {
            val atr = max(featureSeries[i][26].toDouble(), 0.05)
            val diff5 = candles[i + 5].close - candles[i].close
            val isBullWin = if (diff5 > 1.2 * atr) 1 else 0
            val isBearWin = if (-diff5 > 1.2 * atr) 1 else 0

            buyX.add(featureSeries[i])
            buyY.add(isBullWin)
            sellX.add(featureSeries[i])
            sellY.add(isBearWin)
        }

        // Merge last 300 rows from experience_log.csv
        val csvRows = readExperienceLog(limit = 300)
        lastCsvRowsUsed = csvRows.size
        for (row in csvRows) {
            if (row.features.size == 37) {
                if (row.direction.equals("BUY", ignoreCase = true)) {
                    buyX.add(row.features)
                    buyY.add(row.label)
                } else if (row.direction.equals("SELL", ignoreCase = true)) {
                    sellX.add(row.features)
                    sellY.add(row.label)
                }
            }
        }

        buyModel.train(buyX, buyY.toIntArray())
        sellModel.train(sellX, sellY.toIntArray())
    }

    /**
     * Evaluates all 14 Strict Scalping Entry Filters specified in PDF Section 4B.
     */
    private fun evaluate14Filters(
        nowMs: Long,
        config: ScalpConfig,
        summary: FeatureSummary,
        regime: HmmRegime,
        isNewsBlackout: Boolean,
        nearestNews: NewsEvent?,
        candidateDirection: TradeDirection,
        confidence: Double
    ): List<FilterCheckItem> {
        val maxDailyLoss = -0.03 * config.balance
        val filter1 = config.dailyPnl > maxDailyLoss

        val elapsedMs = if (config.lastTradeTimeMs == 0L) COOLDOWN_MS else (nowMs - config.lastTradeTimeMs)
        val filter2 = elapsedMs >= COOLDOWN_MS
        val remainingSec = max(0L, (COOLDOWN_MS - elapsedMs) / 1000L)

        val filter3 = !isNewsBlackout
        val newsVal = if (nearestNews == null) {
            "Aman (Tidak ada USD 3★)"
        } else {
            val diffMin = ((nearestNews.timestampMs - nowMs) / 60_000L)
            "${nearestNews.title.take(18)} (${if (diffMin >= 0) "+$diffMin" else "$diffMin"}m)"
        }

        val utcHour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY)
        val filter4 = (utcHour in 7..11) || (utcHour in 12..20)

        val filter5 = regime != HmmRegime.RANGING
        val filter6 = summary.atrRatio in 0.6..2.2
        val filter7 = summary.bodyRatio >= 0.40
        val filter8 = summary.rangeAtr <= 2.5
        val filter9 = confidence >= 0.78

        val activeSmc = if (candidateDirection == TradeDirection.BUY) {
            summary.activeBullSmc
        } else {
            summary.activeBearSmc
        }
        val filter10 = activeSmc.isNotEmpty()

        val filter11 = if (candidateDirection == TradeDirection.BUY) {
            summary.ema9 > summary.ema21 && summary.ema21 > summary.ema50
        } else {
            summary.ema9 < summary.ema21 && summary.ema21 < summary.ema50
        }

        val filter12 = if (candidateDirection == TradeDirection.BUY) {
            summary.rsi14 in 45.0..68.0
        } else {
            summary.rsi14 in 32.0..55.0
        }

        val filter13 = if (candidateDirection == TradeDirection.BUY) {
            summary.stochK > summary.stochD && summary.macdHist > 0.0
        } else {
            summary.stochK < summary.stochD && summary.macdHist < 0.0
        }

        val filter14 = if (candidateDirection == TradeDirection.BUY) {
            summary.bbPos < 0.85
        } else {
            summary.bbPos > 0.15
        }

        return listOf(
            FilterCheckItem(
                1, "Batas Rugi Harian", "dailyPnl > -3% × balance",
                String.format(Locale.US, "$%.2f (Batas $%.2f)", config.dailyPnl, maxDailyLoss), filter1
            ),
            FilterCheckItem(
                2, "Cooldown Entry", "Jeda ≥ 3 menit (180 dtk)",
                if (filter2) "Siap (≥180 dtk)" else "Tunggu ${remainingSec} dtk", filter2
            ),
            FilterCheckItem(
                3, "News Blackout", "Hindari ±15 mnt USD Impact-3",
                newsVal, filter3
            ),
            FilterCheckItem(
                4, "Kesadaran Sesi", "London 07–11 / NY 12–20 UTC",
                "${summary.sessionLabel} (${utcHour}:00 UTC)", filter4
            ),
            FilterCheckItem(
                5, "Regime HMM", "Tolak Ranging (regime ≠ 0)",
                "${regime.label} (${regime.code})", filter5
            ),
            FilterCheckItem(
                6, "Rasio Volatilitas", "atr_ratio di rentang 0.6 – 2.2",
                String.format(Locale.US, "%.2f", summary.atrRatio), filter6
            ),
            FilterCheckItem(
                7, "Ketegasan Candle", "body_ratio ≥ 0.40",
                String.format(Locale.US, "%.2f", summary.bodyRatio), filter7
            ),
            FilterCheckItem(
                8, "Batas Spike", "range_atr ≤ 2.5",
                String.format(Locale.US, "%.2f", summary.rangeAtr), filter8
            ),
            FilterCheckItem(
                9, "Confidence XGBoost", "Probabilitas ≥ 78% (${candidateDirection.label})",
                String.format(Locale.US, "%.1f%%", confidence * 100.0), filter9
            ),
            FilterCheckItem(
                10, "Konfirmasi SMC", "Min 1 struktur: BOS/CHoCH/FVG/OB",
                if (activeSmc.isEmpty()) "Tidak ada" else activeSmc.joinToString(", "), filter10
            ),
            FilterCheckItem(
                11, "Alignment EMA",
                if (candidateDirection == TradeDirection.BUY) "EMA9 > EMA21 > EMA50" else "EMA9 < EMA21 < EMA50",
                String.format(Locale.US, "%.1f / %.1f / %.1f", summary.ema9, summary.ema21, summary.ema50), filter11
            ),
            FilterCheckItem(
                12, "Zona RSI 14",
                if (candidateDirection == TradeDirection.BUY) "BUY: 45 – 68" else "SELL: 32 – 55",
                String.format(Locale.US, "RSI %.1f", summary.rsi14), filter12
            ),
            FilterCheckItem(
                13, "Momentum Ganda",
                if (candidateDirection == TradeDirection.BUY) "Stoch K>D & MACD>0" else "Stoch K<D & MACD<0",
                String.format(Locale.US, "K%.0f/D%.0f | M%.3f", summary.stochK, summary.stochD, summary.macdHist), filter13
            ),
            FilterCheckItem(
                14, "Posisi Bollinger",
                if (candidateDirection == TradeDirection.BUY) "BUY: bb_pos < 0.85" else "SELL: bb_pos > 0.15",
                String.format(Locale.US, "bb_pos %.2f", summary.bbPos), filter14
            )
        )
    }

    /**
     * Dynamic Risk Engine:
     * SL = 1.2 * ATR, TP = 1.8 * ATR (fixed R:R = 1:1.5)
     * Half-Kelly: f* = max(0, (p*b - (1-p)) / b) * 0.5, hard capped at 1% max risk, min lot 0.01.
     */
    private fun buildRiskPlan(
        direction: TradeDirection,
        entryPrice: Double,
        atr14: Double,
        confidence: Double,
        config: ScalpConfig
    ): RiskPlan {
        val safeAtr = max(atr14, 0.10)
        val slDist = 1.2 * safeAtr
        val tpDist = 1.8 * safeAtr
        val stopLoss = if (direction == TradeDirection.BUY) entryPrice - slDist else entryPrice + slDist
        val takeProfit = if (direction == TradeDirection.BUY) entryPrice + tpDist else entryPrice - tpDist

        // Blend historical EMA winRate with XGBoost confidence for Half-Kelly p
        val p = ((config.winRate + confidence) * 0.5).coerceIn(0.05, 0.95)
        val b = 1.5
        val rawKelly = max(0.0, ((p * b) - (1.0 - p)) / b) * 0.5
        val cappedRisk = min(rawKelly, 0.01)

        val riskUsd = config.balance * cappedRisk
        // XAUUSD standard contract: 1.00 lot = 100 oz ($100 per $1 move)
        val rawLot = if (slDist > 0.0) riskUsd / (slDist * 100.0) else 0.01
        val lotSize = max(0.01, round(rawLot * 100.0) / 100.0)

        return RiskPlan(
            direction = direction,
            entryPrice = entryPrice,
            atr14 = safeAtr,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            lotSize = lotSize,
            riskFraction = cappedRisk,
            confidence = confidence
        )
    }

    // --- CSV Experience Log (experience_log.csv) ---

    fun appendExperienceRow(direction: TradeDirection, pnl: Double, features: FloatArray) {
        try {
            val label = if (pnl > 0.0) 1 else 0
            val ts = System.currentTimeMillis()
            val featureStr = (0 until 37).joinToString(",") { idx ->
                val v = features.getOrElse(idx) { 0f }
                String.format(Locale.US, "%.6f", if (v.isFinite()) v else 0f)
            }
            val line = "$ts,${direction.label},${String.format(Locale.US, "%.2f", pnl)},$label,$featureStr\n"
            csvFile.appendText(line)
            ScalpStateBus.notifyExperienceChanged()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed writing experience_log.csv", t)
        }
    }

    fun readExperienceLog(limit: Int = 300): List<ExperienceRow> {
        val file = csvFile
        if (!file.exists()) return emptyList()
        return try {
            val lines = file.readLines().filter { it.isNotBlank() }
            val recent = if (lines.size > limit) lines.takeLast(limit) else lines
            recent.mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 41) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val dir = parts[1]
                val pnl = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val label = parts[3].toIntOrNull() ?: return@mapNotNull null
                val feats = FloatArray(37) { i -> parts[4 + i].toFloatOrNull() ?: 0f }
                ExperienceRow(ts, dir, pnl, label, feats)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed reading experience_log.csv", t)
            emptyList()
        }
    }

    fun clearExperienceLog() {
        try {
            if (csvFile.exists()) csvFile.delete()
            ScalpStateBus.notifyExperienceChanged()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed clearing experience_log.csv", t)
        }
    }

    // --- Telegram Bot API HTTP POST ---

    suspend fun sendTelegramMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = prefs.loadConfig()
        if (!cfg.hasTelegramConfigured) {
            return@withContext Result.failure(IllegalStateException("Token atau Chat ID Telegram belum diisi."))
        }
        try {
            val url = "https://api.telegram.org/bot${cfg.tgToken}/sendMessage"
            val body = FormBody.Builder()
                .add("chat_id", cfg.tgChatId)
                .add("text", message)
                .add("parse_mode", "HTML")
                .build()
            val req = Request.Builder().url(url).post(body).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(180) ?: "HTTP ${resp.code}"
                    return@withContext Result.failure(IllegalStateException("Telegram error: $err"))
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun sendEntryTelegramAlert(plan: RiskPlan, isDryRun: Boolean) {
        val modeTag = if (isDryRun) "🧪 [DRY-RUN]" else "⚡ [LIVE TAP]"
        val msg = buildString {
            appendLine("<b>$modeTag SCALPSHIZUKU XAUUSD</b>")
            appendLine("Arah: <b>${plan.direction.label}</b>")
            appendLine("Harga Acuan: <code>${String.format(Locale.US, "%.2f", plan.entryPrice)}</code>")
            appendLine("Lot (Half-Kelly): <code>${String.format(Locale.US, "%.2f", plan.lotSize)}</code> (${String.format(Locale.US, "%.2f%%", plan.riskFraction * 100.0)})")
            appendLine("SL (1.2×ATR): <code>${String.format(Locale.US, "%.2f", plan.stopLoss)}</code>")
            appendLine("TP (1.8×ATR): <code>${String.format(Locale.US, "%.2f", plan.takeProfit)}</code>")
            appendLine("Confidence XGBoost: <b>${String.format(Locale.US, "%.1f%%", plan.confidence * 100.0)}</b>")
        }
        sendTelegramMessage(msg)
    }

    suspend fun sendOcrFeedbackTelegram(direction: TradeDirection, pnl: Double, updatedConfig: ScalpConfig) {
        val icon = if (pnl >= 0.0) "✅" else "❌"
        val sign = if (pnl >= 0.0) "+" else ""
        val msg = buildString {
            appendLine("<b>$icon LAPORAN OCR PnL — XAUUSD (${direction.label})</b>")
            appendLine("Hasil Trade: <b>$sign${String.format(Locale.US, "%.2f", pnl)} USD</b>")
            appendLine("Akumulasi Harian: <code>${String.format(Locale.US, "%.2f", updatedConfig.dailyPnl)} USD</code>")
            appendLine("Win Rate (EMA 0.1): <code>${String.format(Locale.US, "%.1f%%", updatedConfig.winRate * 100.0)}</code>")
            appendLine("CSV Retraining: <i>Baris pengalaman baru tersimpan</i>")
        }
        sendTelegramMessage(msg)
    }

    // --- Technical Indicators Helpers ---

    private fun pctReturn(arr: DoubleArray, i: Int, lag: Int): Double {
        if (i < lag || arr[i - lag] == 0.0) return 0.0
        return (arr[i] - arr[i - lag]) / arr[i - lag]
    }

    private fun computeEma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size)
        if (values.isEmpty()) return out
        val k = 2.0 / (period + 1.0)
        out[0] = values[0]
        for (i in 1 until values.size) {
            out[i] = values[i] * k + out[i - 1] * (1.0 - k)
        }
        return out
    }

    private fun computeRsi(closes: DoubleArray, period: Int): DoubleArray {
        val n = closes.size
        val out = DoubleArray(n) { 50.0 }
        if (n <= period) return out
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val delta = closes[i] - closes[i - 1]
            if (delta > 0) avgGain += delta else avgLoss -= delta
        }
        avgGain /= period
        avgLoss /= period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        for (i in (period + 1) until n) {
            val delta = closes[i] - closes[i - 1]
            val gain = max(delta, 0.0)
            val loss = max(-delta, 0.0)
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return out
    }

    private fun computeAtr(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int): DoubleArray {
        val n = closes.size
        val tr = DoubleArray(n)
        val atr = DoubleArray(n)
        for (i in 0 until n) {
            tr[i] = if (i == 0) {
                highs[i] - lows[i]
            } else {
                max(
                    highs[i] - lows[i],
                    max(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
                )
            }
        }
        var running = 0.0
        for (i in 0 until n) {
            if (i < period) {
                running += tr[i]
                atr[i] = running / (i + 1)
            } else {
                atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period
            }
        }
        return atr
    }

    private fun computeStoch(
        highs: DoubleArray,
        lows: DoubleArray,
        closes: DoubleArray,
        kPeriod: Int,
        dPeriod: Int
    ): Pair<DoubleArray, DoubleArray> {
        val n = closes.size
        val kArr = DoubleArray(n) { 50.0 }
        val dArr = DoubleArray(n) { 50.0 }
        for (i in 0 until n) {
            val start = max(0, i - kPeriod + 1)
            var hh = highs[start]
            var ll = lows[start]
            for (j in start..i) {
                if (highs[j] > hh) hh = highs[j]
                if (lows[j] < ll) ll = lows[j]
            }
            val span = hh - ll
            kArr[i] = if (span > 1e-6) ((closes[i] - ll) / span) * 100.0 else 50.0
        }
        for (i in 0 until n) {
            val start = max(0, i - dPeriod + 1)
            var sum = 0.0
            for (j in start..i) sum += kArr[j]
            dArr[i] = sum / (i - start + 1)
        }
        return kArr to dArr
    }

    private fun computeBollinger(closes: DoubleArray, idx: Int, period: Int): Pair<Double, Double> {
        val start = max(0, idx - period + 1)
        val count = idx - start + 1
        var mean = 0.0
        for (j in start..idx) mean += closes[j]
        mean /= count
        var variance = 0.0
        for (j in start..idx) {
            val d = closes[j] - mean
            variance += d * d
        }
        val std = sqrt(variance / max(1, count))
        val upper = mean + 2.0 * std
        val lower = mean - 2.0 * std
        val span = max(upper - lower, 1e-4)
        val pos = ((closes[idx] - lower) / span).coerceIn(-0.5, 1.5)
        val width = if (mean > 0.0) span / mean else 0.0
        return pos to width
    }

    private fun computeVolumeZScore(volumes: DoubleArray, idx: Int, period: Int): Double {
        val start = max(0, idx - period + 1)
        val count = idx - start + 1
        var mean = 0.0
        for (j in start..idx) mean += volumes[j]
        mean /= count
        var variance = 0.0
        for (j in start..idx) {
            val d = volumes[j] - mean
            variance += d * d
        }
        val std = sqrt(variance / max(1, count))
        return if (std > 1e-4) ((volumes[idx] - mean) / std).coerceIn(-5.0, 5.0) else 0.0
    }

    private fun getUtcSessionId(timestampMs: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestampMs
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..6 -> 1   // Sydney / Asia
            in 7..11 -> 2  // London
            in 12..20 -> 3 // New York
            else -> 0      // Off
        }
    }

    companion object {
        private const val TAG = "ScalpEngine"
        private const val YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=1m&range=1d"
        private const val INVESTING_CALENDAR_URL = "https://www.investing.com/economic-calendar/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val NEWS_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val NEWS_BLACKOUT_MS = 15 * 60 * 1000L
        private const val COOLDOWN_MS = 180_000L
        private const val CSV_FILENAME = "experience_log.csv"
    }
}
