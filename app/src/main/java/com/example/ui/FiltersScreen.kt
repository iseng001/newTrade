package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scalp.FilterCheckItem
import com.example.scalp.TradeDirection
import com.example.ui.theme.BullionGold
import com.example.ui.theme.BullionGoldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoCaptionStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FiltersScreen(
    state: ScalpUiState,
    onEvaluateNow: () -> Unit
) {
    val eval = state.evaluation
    val summary = eval.featureSummary

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Gerbang 14 Entry Filter Ketat",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Wajib lolos 14/14 untuk memicu eksekusi ${eval.candidateDirection.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Button(
                                onClick = onEvaluateNow,
                                enabled = !state.isManualEvaluating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BullionGold,
                                    contentColor = ObsidianCanvas
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("filters_refresh_btn")
                            ) {
                                if (state.isManualEvaluating) {
                                    CircularProgressIndicator(
                                        color = ObsidianCanvas,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Segarkan 14 Filter",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cek", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val progress = (eval.passedFilterCount / 14f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (eval.passedFilterCount == 14) SignalEmerald else BullionGold,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lolos: ${eval.passedFilterCount} / 14",
                                style = MonoMetricStyle,
                                color = if (eval.passedFilterCount == 14) SignalEmerald else BullionGold
                            )
                            Text(
                                text = "Arah Kandidat: ${eval.candidateDirection.label}",
                                style = MonoMetricStyle,
                                color = when (eval.candidateDirection) {
                                    TradeDirection.BUY -> SignalEmerald
                                    TradeDirection.SELL -> SignalCrimson
                                    TradeDirection.NONE -> TextMuted
                                }
                            )
                        }
                    }
                }
            }

            if (summary != null) {
                item {
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Diagnostik 37 Fitur (SMC • EMA • Momentum)",
                                style = MaterialTheme.typography.titleSmall,
                                color = BullionGold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "SMC Bull: ${if (summary.activeBullSmc.isEmpty()) "—" else summary.activeBullSmc.joinToString()}",
                                    style = MonoCaptionStyle,
                                    color = SignalEmerald
                                )
                                Text(
                                    text = "SMC Bear: ${if (summary.activeBearSmc.isEmpty()) "—" else summary.activeBearSmc.joinToString()}",
                                    style = MonoCaptionStyle,
                                    color = SignalCrimson
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.US, "RSI14: %.1f", summary.rsi14),
                                    style = MonoCaptionStyle,
                                    color = TextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "ATR Ratio: %.2f", summary.atrRatio),
                                    style = MonoCaptionStyle,
                                    color = TextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "Body: %.2f", summary.bodyRatio),
                                    style = MonoCaptionStyle,
                                    color = TextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "BB: %.2f", summary.bbPos),
                                    style = MonoCaptionStyle,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (eval.filters.isEmpty()) {
                item {
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = "Belum Ada Evaluasi",
                                tint = BullionGold,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Belum Ada Data Evaluasi M1",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tekan tombol 'Cek' di atas untuk menarik candle M1 GC=F, kalender berita USD Investing.com, menghitung 37 fitur, dan memeriksa ke-14 filter secara langsung.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(eval.filters, key = { it.number }) { filter ->
                    FilterGateCard(filter = filter)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FilterGateCard(filter: FilterCheckItem) {
    val statusColor = if (filter.passed) SignalEmerald else SignalCrimson
    val statusBg = if (filter.passed) SignalEmeraldContainer else SignalCrimsonContainer

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (filter.passed) SignalEmerald.copy(alpha = 0.25f) else HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (filter.passed) SignalEmeraldContainer else BullionGoldContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = String.format(Locale.US, "%02d", filter.number),
                        style = MonoMetricStyle,
                        color = if (filter.passed) SignalEmerald else BullionGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = filter.criteria,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Aktual: ${filter.currentValue}",
                    style = MonoCaptionStyle,
                    color = if (filter.passed) TextPrimary else SignalCrimson
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = statusBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (filter.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = if (filter.passed) "Lolos" else "Blokir",
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (filter.passed) "LOLOS" else "BLOKIR",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
