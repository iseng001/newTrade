package com.example.scalp

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Offline PnL feedback reader using ML Kit Text Recognition.
 * Crops a 360x100 px ROI centered at (pnlX, pnlY), extracts signed decimal numbers via
 * regex `[+-]?\d+\.\d{2}`, and invokes the feedback callback.
 */
class OcrFeedbackManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun analyzeFrameForPnl(
        fullScreenBitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        onResult: (pnl: Double?, rawText: String) -> Unit
    ) {
        if (fullScreenBitmap.isRecycled || fullScreenBitmap.width <= 0 || fullScreenBitmap.height <= 0) {
            onResult(null, "")
            return
        }

        val cropWidth = minOf(ROI_WIDTH_PX, fullScreenBitmap.width)
        val cropHeight = minOf(ROI_HEIGHT_PX, fullScreenBitmap.height)
        val left = (centerX - cropWidth / 2).coerceIn(0, fullScreenBitmap.width - cropWidth)
        val top = (centerY - cropHeight / 2).coerceIn(0, fullScreenBitmap.height - cropHeight)

        val cropped = try {
            Bitmap.createBitmap(fullScreenBitmap, left, top, cropWidth, cropHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed cropping PnL ROI bitmap", t)
            onResult(null, "")
            return
        }

        val inputImage = InputImage.fromBitmap(cropped, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.trim()
                val normalized = raw.replace(" ", "").replace(",", ".")
                val match = PNL_REGEX.find(normalized)
                val parsed = match?.value?.toDoubleOrNull()
                cropped.recycle()
                onResult(parsed, raw)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "ML Kit OCR failed", err)
                cropped.recycle()
                onResult(null, err.message ?: "OCR Error")
            }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "OcrFeedbackManager"
        const val ROI_WIDTH_PX = 360
        const val ROI_HEIGHT_PX = 100
        private val PNL_REGEX = Regex("[+-]?\\d+\\.\\d{2}")
    }
}
