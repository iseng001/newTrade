package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scalp.EngineLogItem
import com.example.scalp.HmmRegime
import com.example.scalp.LogSeverity
import com.example.scalp.TradeDirection
import com.example.ui.theme.BullionGold
import com.example.ui.theme.BullionGoldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoCaptionStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalAmberContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalScreen(
    state: ScalpUiState,
    onToggleEngine: () -> Unit,
    onEvaluateNow: () -> Unit,
    onToggleDryRun: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenFiltersTab: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroMarketConsoleCard(
                    state = state,
                    onOpenFiltersTab = onOpenFiltersTab
                )
            }

            item {
                ExecutionModeControlCard(
                    isDryRun = state.config.isDryRun,
                    isEngineRunning = state.isEngineRunning,
                    isManualEvaluating = state.isManualEvaluating,
                    onToggleDryRun = onToggleDryRun,
                    onToggleEngine = onToggleEngine,
                    onEvaluateNow = onEvaluateNow
                )
            }

            item {
                AccountRiskTelemetryRow(state = state)
            }

            item {
                PermissionReadinessSection(
                    state = state,
                    onRequestOverlay = onRequestOverlay,
                    onRequestShizuku = onRequestShizuku,
                    onRequestNotifications = onRequestNotifications
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Log Aktivitas Engine",
                            tint = BullionGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Eksekusi Real-Time",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${state.logs.size} entri",
                        style = MonoCaptionStyle,
                        color = TextMuted
                    )
                }
            }

            if (state.logs.isEmpty()) {
                item {
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Belum ada aktivitas. Tekan 'Evaluasi Sekarang' atau 'START SCALP ENGINE' untuk memulai analisis M1 XAUUSD.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.logs.take(12), key = { it.id }) { item ->
                    LogFeedRow(item = item)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeroMarketConsoleCard(
    state: ScalpUiState,
    onOpenFiltersTab: () -> Unit
) {
    val eval = state.evaluation
    val regimeColor = when (eval.regime) {
        HmmRegime.TRENDING -> SignalEmerald
        HmmRegime.VOLATILE -> SignalAmber
        HmmRegime.RANGING -> SignalCrimson
    }
    val regimeBg = when (eval.regime) {
        HmmRegime.TRENDING -> SignalEmeraldContainer
        HmmRegime.VOLATILE -> SignalAmberContainer
        HmmRegime.RANGING -> SignalCrimsonContainer
    }
    val glowColor = if (state.isEngineRunning) SignalEmerald.copy(alpha = 0.14f) else BullionGold.copy(alpha = 0.12f)

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (state.isEngineRunning) SignalEmerald.copy(alpha = 0.5f) else HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.65f
                    )
                )
            }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (state.isEngineRunning) SignalEmerald else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isEngineRunning) "ENGINE AKTIF • 5 DTK" else "ENGINE STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isEngineRunning) SignalEmerald else TextSecondary
                    )
                }
                Surface(
                    color = if (state.config.isDryRun) SignalAmberContainer else SignalCrimsonContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (state.config.isDryRun) "DRY-RUN" else "LIVE SHIZUKU",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.config.isDryRun) SignalAmber else SignalCrimson,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "XAUUSD • GC=F M1",
                        style = MaterialTheme.typography.labelMedium,
                        color = BullionGold
                    )
                    Text(
                        text = if (eval.lastPrice > 0.0) {
                            String.format(Locale.US, "%.2f", eval.lastPrice)
                        } else {
                            "—.—"
                        },
                        style = MonoPriceStyle,
                        color = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = regimeBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, regimeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "HMM: ${eval.regime.label.uppercase(Locale.US)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = regimeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenFiltersTab)
                    ) {
                        Text(
                            text = "FILTER: ${eval.passedFilterCount}/14",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eval.passedFilterCount == 14) SignalEmerald else BullionGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dual MiniXGBoost confidence meters with 78% threshold label
            ConfidenceBarRow(
                label = "XGB BUY",
                probability = eval.buyProbability,
                barColor = SignalEmerald
            )
            Spacer(modifier = Modifier.height(6.dp))
            ConfidenceBarRow(
                label = "XGB SELL",
                probability = eval.sellProbability,
                barColor = SignalCrimson
            )

            Spacer(modifier = Modifier.height(12.dp))

            val plan = eval.riskPlan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniSpecColumn(
                    title = "LOT KELLY",
                    value = if (plan != null) String.format(Locale.US, "%.2f", plan.lotSize) else "0.01",
                    valueColor = BullionGold
                )
                MiniSpecColumn(
                    title = "SL 1.2×ATR",
                    value = if (plan != null) String.format(Locale.US, "%.2f", plan.stopLoss) else "—",
                    valueColor = SignalCrimson
                )
                MiniSpecColumn(
                    title = "TP 1.8×ATR",
                    value = if (plan != null) String.format(Locale.US, "%.2f", plan.takeProfit) else "—",
                    valueColor = SignalEmerald
                )
                MiniSpecColumn(
                    title = "ATR14",
                    value = if (plan != null) String.format(Locale.US, "%.2f", plan.atr14) else "—",
                    valueColor = SignalCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = eval.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (eval.errorMessage != null) SignalCrimson
                else if (eval.executableDirection != TradeDirection.NONE) SignalEmerald
                else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfidenceBarRow(label: String, probability: Double, barColor: Color) {
    val pct = (probability * 100.0).coerceIn(0.0, 100.0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MonoCaptionStyle,
            color = TextSecondary,
            modifier = Modifier.width(66.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((probability.toFloat()).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
            // 78% strict confidence gate tick
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(BullionGold)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = String.format(Locale.US, "%4.1f%%", pct),
            style = MonoCaptionStyle,
            color = if (pct >= 78.0) barColor else TextSecondary,
            modifier = Modifier.width(46.dp)
        )
    }
}

@Composable
private fun MiniSpecColumn(title: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MonoMetricStyle, color = valueColor)
    }
}

@Composable
private fun ExecutionModeControlCard(
    isDryRun: Boolean,
    isEngineRunning: Boolean,
    isManualEvaluating: Boolean,
    onToggleDryRun: (Boolean) -> Unit,
    onToggleEngine: () -> Unit,
    onEvaluateNow: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianCanvas)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeSegmentButton(
                    title = "Dry-Run (Aman)",
                    icon = Icons.Default.Science,
                    selected = isDryRun,
                    activeColor = SignalAmber,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_dry_run_btn"),
                    onClick = { onToggleDryRun(true) }
                )
                ModeSegmentButton(
                    title = "Live Auto-Tap",
                    icon = Icons.Default.Bolt,
                    selected = !isDryRun,
                    activeColor = SignalEmerald,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_live_tap_btn"),
                    onClick = { onToggleDryRun(false) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val btnBg by animateColorAsState(
                    targetValue = if (isEngineRunning) SignalCrimson else BullionGold,
                    label = "engine_btn_color"
                )
                val btnContent = if (isEngineRunning) TextPrimary else ObsidianCanvas

                Button(
                    onClick = onToggleEngine,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnBg,
                        contentColor = btnContent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.45f)
                        .height(52.dp)
                        .testTag("toggle_engine_button")
                ) {
                    Icon(
                        imageVector = if (isEngineRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isEngineRunning) "Hentikan Engine" else "Mulai Engine",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEngineRunning) "STOP ENGINE" else "START ENGINE",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onEvaluateNow,
                    enabled = !isManualEvaluating,
                    border = BorderStroke(1.dp, BullionGold.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BullionGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("evaluate_now_button")
                ) {
                    if (isManualEvaluating) {
                        CircularProgressIndicator(
                            color = BullionGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Evaluasi Pasar Sekarang",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Evaluasi", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSegmentButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) activeColor.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        border = if (selected) BorderStroke(1.dp, activeColor.copy(alpha = 0.6f)) else null,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) activeColor else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) activeColor else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountRiskTelemetryRow(state: ScalpUiState) {
    val cfg = state.config
    val dailyColor = when {
        cfg.dailyPnl > 0.0 -> SignalEmerald
        cfg.dailyPnl < 0.0 -> SignalCrimson
        else -> TextPrimary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryMiniCard(
            label = "PnL HARIAN",
            value = String.format(Locale.US, "%+.2f", cfg.dailyPnl),
            sub = "Batas -3% ($${String.format(Locale.US, "%.0f", cfg.balance * 0.03)})",
            valueColor = dailyColor,
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            label = "WIN RATE EMA",
            value = String.format(Locale.US, "%.1f%%", cfg.winRate * 100.0),
            sub = "${cfg.totalWins}/${cfg.totalTrades} trade OCR",
            valueColor = BullionGold,
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            label = "XGBOOST DATA",
            value = "${state.evaluation.modelTrainedSamples}",
            sub = "CSV: ${state.experienceRows.size}/300",
            valueColor = SignalCyan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryMiniCard(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoMetricStyle.fontFamily),
                color = valueColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionReadinessSection(
    state: ScalpUiState,
    onRequestOverlay: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val perm = state.permissions
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Kesiapan Izin & Layanan Sistem",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            PermissionRow(
                icon = Icons.Default.Layers,
                title = "Draw Over Other Apps",
                subtitle = "Overlay kalibrasi BUY, SELL, dan ROI PnL di atas MT5",
                granted = perm.overlayGranted,
                statusText = if (perm.overlayGranted) "Aktif" else "Izinkan",
                testTagName = "perm_overlay_btn",
                onClick = onRequestOverlay
            )

            PermissionRow(
                icon = Icons.Default.Security,
                title = "Otorisasi Shizuku (UID 2000)",
                subtitle = when {
                    state.isShizukuBound -> "TouchUserService terhubung & siap tap"
                    perm.shizukuGranted -> "Diizinkan (akan terhubung saat Start)"
                    perm.shizukuRunning -> "Shizuku aktif — ketuk untuk otorisasi"
                    else -> "Aktifkan aplikasi Shizuku di perangkat terlebih dahulu"
                },
                granted = perm.shizukuGranted,
                statusText = when {
                    perm.shizukuGranted -> "Aktif"
                    perm.shizukuRunning -> "Otorisasi"
                    else -> "Offline"
                },
                testTagName = "perm_shizuku_btn",
                onClick = onRequestShizuku
            )

            PermissionRow(
                icon = Icons.Default.NotificationsActive,
                title = "Notifikasi Foreground Service",
                subtitle = "Menjaga polling 5 detik & timer OCR 5 menit tetap berjalan",
                granted = perm.notificationsGranted,
                statusText = if (perm.notificationsGranted) "Aktif" else "Izinkan",
                testTagName = "perm_notif_btn",
                onClick = onRequestNotifications
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    statusText: String,
    testTagName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTagName),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (granted) SignalEmerald else BullionGold,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = if (granted) SignalEmeraldContainer else BullionGoldContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = statusText,
                    tint = if (granted) SignalEmerald else BullionGold,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) SignalEmerald else BullionGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LogFeedRow(item: EngineLogItem) {
    val dotColor = when (item.severity) {
        LogSeverity.SIGNAL -> BullionGold
        LogSeverity.SUCCESS -> SignalEmerald
        LogSeverity.WARNING -> SignalAmber
        LogSeverity.ERROR -> SignalCrimson
        LogSeverity.INFO -> SignalCyan
    }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(item.timestampMs))
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeStr,
                style = MonoCaptionStyle,
                color = TextMuted,
                modifier = Modifier.width(58.dp)
            )
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
