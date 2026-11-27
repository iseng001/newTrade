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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scalp.CalibrationTarget
import com.example.scalp.ExperienceRow
import com.example.ui.theme.BullionGold
import com.example.ui.theme.BullionGoldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoCaptionStyle
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalAmberContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
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
fun CalibrationLogScreen(
    state: ScalpUiState,
    onLaunchCalibration: (CalibrationTarget) -> Unit
) {
    val cfg = state.config

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Kalibrasi Koordinat Overlay MT5",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Aktifkan overlay, berpindah ke chart XAUUSD MetaTrader 5, geser target tepat di atas tombol atau angka PnL, lalu tekan Simpan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        CalibrationTargetRow(
                            icon = Icons.Default.GpsFixed,
                            title = "Tombol BUY MetaTrader 5",
                            coordText = if (cfg.isBuyCalibrated) "X = ${cfg.buyX} px   Y = ${cfg.buyY} px" else "Belum dikalibrasi",
                            isSet = cfg.isBuyCalibrated,
                            accent = SignalEmerald,
                            testTagName = "calibrate_buy_button",
                            onClick = { onLaunchCalibration(CalibrationTarget.BUY) }
                        )

                        CalibrationTargetRow(
                            icon = Icons.Default.GpsFixed,
                            title = "Tombol SELL MetaTrader 5",
                            coordText = if (cfg.isSellCalibrated) "X = ${cfg.sellX} px   Y = ${cfg.sellY} px" else "Belum dikalibrasi",
                            isSet = cfg.isSellCalibrated,
                            accent = SignalCrimson,
                            testTagName = "calibrate_sell_button",
                            onClick = { onLaunchCalibration(CalibrationTarget.SELL) }
                        )

                        CalibrationTargetRow(
                            icon = Icons.Default.CropFree,
                            title = "Area ROI Angka PnL (360×100 px)",
                            coordText = if (cfg.isPnlCalibrated) "Pusat X = ${cfg.pnlX} px   Y = ${cfg.pnlY} px" else "Belum dikalibrasi",
                            isSet = cfg.isPnlCalibrated,
                            accent = BullionGold,
                            testTagName = "calibrate_pnl_button",
                            onClick = { onLaunchCalibration(CalibrationTarget.PNL_ROI) }
                        )
                    }
                }
            }

            if (state.pendingOcrTargetTimeMs != null) {
                item {
                    val targetStr = SimpleDateFormat("HH:mm:ss", Locale.US)
                        .format(Date(state.pendingOcrTargetTimeMs))
                    Surface(
                        color = SignalAmberContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Jadwal OCR PnL",
                                tint = SignalAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "OCR Feedback Loop Terjadwal",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SignalAmber
                                )
                                Text(
                                    text = "Tangkapan layar MediaProjection & pembacaan ML Kit OCR akan berjalan pada $targetStr.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat Pengalaman CSV",
                            tint = BullionGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Retraining (experience_log.csv)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${state.experienceRows.size} / 300",
                        style = MonoCaptionStyle,
                        color = BullionGold
                    )
                }
            }

            if (state.experienceRows.isEmpty()) {
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
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Belum Ada Baris Pengalaman OCR",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Setiap transaksi Live Auto-Tap yang selesai dibaca ML Kit OCR setelah 5 menit akan otomatis menyimpan 37 fitur + label ke experience_log.csv untuk auto-retraining MiniXGBoost.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.experienceRows.take(40)) { idx, row ->
                    ExperienceRowCard(index = idx + 1, row = row)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CalibrationTargetRow(
    icon: ImageVector,
    title: String,
    coordText: String,
    isSet: Boolean,
    accent: Color,
    testTagName: String,
    onClick: () -> Unit
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSet) accent.copy(alpha = 0.35f) else HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = coordText,
                    style = MonoCaptionStyle,
                    color = if (isSet) accent else TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.copy(alpha = 0.18f),
                    contentColor = accent
                ),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag(testTagName)
            ) {
                Text(
                    text = if (isSet) "Ubah" else "Kalibrasi",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ExperienceRowCard(index: Int, row: ExperienceRow) {
    val isWin = row.label == 1
    val pnlColor = if (row.pnl >= 0.0) SignalEmerald else SignalCrimson
    val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(row.timestampMs))

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (row.direction == "BUY") SignalEmeraldContainer else SignalCrimsonContainer,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    text = row.direction,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.direction == "BUY") SignalEmerald else SignalCrimson,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#$index • $timeStr • 37 Fitur",
                    style = MonoCaptionStyle,
                    color = TextSecondary
                )
                Text(
                    text = if (isWin) "Label: 1 (Profit)" else "Label: 0 (Loss)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Surface(
                color = if (isWin) SignalEmeraldContainer else BullionGoldContainer,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%+.2f USD", row.pnl),
                    style = MonoMetricStyle,
                    color = pnlColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
