package com.example.scalp

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Native Second-Order Newton Gradient Boosting Classifier (Decision Stump Ensemble).
 * Spec parameters: 45 decision stumps, learning rate 0.08, L2 regularization lambda = 1.5.
 */
class MiniXGBoost(
    private val numTrees: Int = 45,
    private val learningRate: Double = 0.08,
    private val lambdaL2: Double = 1.5
) {
    private data class Stump(
        val featureIndex: Int,
        val threshold: Double,
        val leftWeight: Double,
        val rightWeight: Double
    )

    private var baseScore: Double = 0.0
    private var stumps: List<Stump> = emptyList()

    var trainedSampleCount: Int = 0
        private set

    fun train(featuresList: List<FloatArray>, labels: IntArray) {
        val n = minOf(featuresList.size, labels.size)
        if (n < 12) {
            baseScore = 0.0
            stumps = emptyList()
            trainedSampleCount = n
            return
        }

        val numFeatures = featuresList[0].size
        var posCount = 0.0
        for (i in 0 until n) {
            if (labels[i] == 1) posCount += 1.0
        }
        val posRate = (posCount / n.toDouble()).coerceIn(0.05, 0.95)
        baseScore = ln(posRate / (1.0 - posRate))

        val rawPreds = DoubleArray(n) { baseScore }
        val builtStumps = ArrayList<Stump>(numTrees)

        // Precompute candidate thresholds per feature using quantile sampling for fast on-device fit
        val candidateThresholds = Array(numFeatures) { fIdx ->
            val vals = DoubleArray(n) { i ->
                val v = featuresList[i][fIdx].toDouble()
                if (v.isFinite()) v else 0.0
            }
            vals.sort()
            val thresholds = DoubleArray(8)
            for (q in 0 until 8) {
                val idx = ((q + 1) * n / 9).coerceIn(0, n - 1)
                thresholds[q] = vals[idx]
            }
            thresholds.distinct().toDoubleArray()
        }

        val grads = DoubleArray(n)
        val hess = DoubleArray(n)

        for (treeIdx in 0 until numTrees) {
            var totalG = 0.0
            var totalH = 0.0
            for (i in 0 until n) {
                val p = sigmoid(rawPreds[i])
                val g = p - labels[i].toDouble()
                val h = max(p * (1.0 - p), 1e-6)
                grads[i] = g
                hess[i] = h
                totalG += g
                totalH += h
            }

            val rootScore = (totalG * totalG) / (totalH + lambdaL2)
            var bestGain = 1e-6
            var bestFeature = 0
            var bestThreshold = 0.0
            var bestLeftWeight = 0.0
            var bestRightWeight = 0.0

            for (fIdx in 0 until numFeatures) {
                val thresholds = candidateThresholds[fIdx]
                for (tIdx in thresholds.indices) {
                    val thresh = thresholds[tIdx]
                    var gLeft = 0.0
                    var hLeft = 0.0
                    for (i in 0 until n) {
                        val v = featuresList[i][fIdx].toDouble()
                        if ((if (v.isFinite()) v else 0.0) <= thresh) {
                            gLeft += grads[i]
                            hLeft += hess[i]
                        }
                    }
                    val gRight = totalG - gLeft
                    val hRight = totalH - hLeft
                    if (hLeft < 1e-4 || hRight < 1e-4) continue

                    val gain = 0.5 * (
                        (gLeft * gLeft) / (hLeft + lambdaL2) +
                            (gRight * gRight) / (hRight + lambdaL2) -
                            rootScore
                        )
                    if (gain > bestGain) {
                        bestGain = gain
                        bestFeature = fIdx
                        bestThreshold = thresh
                        bestLeftWeight = (-gLeft / (hLeft + lambdaL2)).coerceIn(-3.0, 3.0)
                        bestRightWeight = (-gRight / (hRight + lambdaL2)).coerceIn(-3.0, 3.0)
                    }
                }
            }

            if (bestGain <= 1e-6) break

            val stump = Stump(bestFeature, bestThreshold, bestLeftWeight, bestRightWeight)
            builtStumps.add(stump)

            for (i in 0 until n) {
                val v = featuresList[i][bestFeature].toDouble()
                val w = if ((if (v.isFinite()) v else 0.0) <= bestThreshold) {
                    bestLeftWeight
                } else {
                    bestRightWeight
                }
                rawPreds[i] += learningRate * w
            }
        }

        stumps = builtStumps
        trainedSampleCount = n
    }

    fun predictProbability(features: FloatArray): Double {
        if (stumps.isEmpty()) return sigmoid(baseScore)
        var score = baseScore
        for (stump in stumps) {
            val raw = features.getOrElse(stump.featureIndex) { 0f }.toDouble()
            val v = if (raw.isFinite()) raw else 0.0
            score += learningRate * if (v <= stump.threshold) stump.leftWeight else stump.rightWeight
        }
        return sigmoid(score)
    }

    private fun sigmoid(x: Double): Double {
        val clipped = x.coerceIn(-20.0, 20.0)
        return 1.0 / (1.0 + exp(-clipped))
    }
}
