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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BullionGold
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoCaptionStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SettingsScreen(
    state: ScalpUiState,
    onSaveSettings: (balanceText: String, tgToken: String, tgChatId: String) -> Unit,
    onTestTelegram: () -> Unit,
    onResetDailyPnl: () -> Unit,
    onClearExperienceCsv: () -> Unit
) {
    var balanceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", state.config.balance)) }
    var tgToken by remember { mutableStateOf(state.config.tgToken) }
    var tgChatId by remember { mutableStateOf(state.config.tgChatId) }
    var showClearCsvDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.config.balance, state.config.tgToken, state.config.tgChatId) {
        balanceText = String.format(Locale.US, "%.2f", state.config.balance)
        tgToken = state.config.tgToken
        tgChatId = state.config.tgChatId
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BullionGold,
        unfocusedBorderColor = HairlineBorder,
        focusedLabelColor = BullionGold,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = BullionGold,
        focusedContainerColor = SurfaceElevated,
        unfocusedContainerColor = SurfaceElevated
    )

    val parsedBalance = balanceText.replace(",", ".").toDoubleOrNull() ?: state.config.balance

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
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Manajemen Risiko",
                                tint = BullionGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manajemen Risiko & Modal",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }

                        OutlinedTextField(
                            value = balanceText,
                            onValueChange = { balanceText = it },
                            label = { Text("Saldo Modal Akun (USD)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("balance_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Batas Rugi Harian (-3%): $${String.format(Locale.US, "%.2f", parsedBalance * 0.03)}",
                                style = MonoCaptionStyle,
                                color = SignalCrimson
                            )
                            Text(
                                text = "Maks Risk/Trade (1%): $${String.format(Locale.US, "%.2f", parsedBalance * 0.01)}",
                                style = MonoCaptionStyle,
                                color = SignalEmerald
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Notifikasi Bot Telegram (Gratis)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Mengirim pesan HTTP POST real-time saat sinyal BUY/SELL dieksekusi serta laporan hasil OCR PnL & akumulasi harian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        OutlinedTextField(
                            value = tgToken,
                            onValueChange = { tgToken = it },
                            label = { Text("Telegram Bot Token (tgToken)") },
                            placeholder = { Text("123456789:AAH...", color = TextMuted) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tg_token_input")
                        )

                        OutlinedTextField(
                            value = tgChatId,
                            onValueChange = { tgChatId = it },
                            label = { Text("Telegram Chat ID (tgChatId)") },
                            placeholder = { Text("987654321", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tg_chat_id_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSaveSettings(balanceText, tgToken, tgChatId) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BullionGold,
                                    contentColor = ObsidianCanvas
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("save_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Simpan Konfigurasi",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan", style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedButton(
                                onClick = {
                                    onSaveSettings(balanceText, tgToken, tgChatId)
                                    onTestTelegram()
                                },
                                enabled = !state.isTestingTelegram,
                                border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("test_telegram_button")
                            ) {
                                if (state.isTestingTelegram) {
                                    CircularProgressIndicator(
                                        color = SignalCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Uji Kirim Telegram",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Uji Bot", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

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
                            text = "Pemeliharaan Data Lokal",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        OutlinedButton(
                            onClick = onResetDailyPnl,
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_pnl_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset PnL Harian",
                                tint = BullionGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Akumulasi PnL Harian ke $0.00")
                        }

                        OutlinedButton(
                            onClick = { showClearCsvDialog = true },
                            border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCrimson),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clear_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Bersihkan experience_log.csv",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bersihkan experience_log.csv (${state.experienceRows.size} baris)")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showClearCsvDialog) {
        AlertDialog(
            onDismissRequest = { showClearCsvDialog = false },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Hapus experience_log.csv?") },
            text = {
                Text("Seluruh data feedback OCR lokal yang digunakan untuk auto-retraining MiniXGBoost akan dihapus permanen.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCsvDialog = false
                        onClearExperienceCsv()
                    }
                ) {
                    Text("Hapus", color = SignalCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCsvDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}
