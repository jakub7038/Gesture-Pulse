package com.example.gesturepulse

import android.util.Log
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Obiekt narzędziowy do rozpoznawania gestów.
 * Zaktualizowany o obsługę Multi-Sample (k-NN dla DTW).
 */
object GestureRecognizer {
    /**
     * Analizuje gest wejściowy porównując go z całą LISTĄ próbek treningowych.
     * Zwraca wynik najlepszego dopasowania (najmniejszy dystans).
     */
    fun recognize( // Zmieniona nazwa
        trainingVariants: List<Gesture>,
        liveAccel: List<SensorSample>,
        liveGyro: List<SensorSample>
    ): Double {
        if (trainingVariants.isEmpty()) return Double.POSITIVE_INFINITY

        // Przygotuj dane wejściowe (live) raz, aby nie liczyć tego w pętli
        val liveAccelMag = extractMagnitude(liveAccel)
        val liveGyroMag = extractMagnitude(liveGyro)

        var bestTotalDistance = Double.POSITIVE_INFINITY

        // Sprawdź każdy wariant z zestawu treningowego
        for (sample in trainingVariants) {
            // Dane wzorca
            val savedAccelMag = extractMagnitude(sample.accelerometerData)
            val savedGyroMag = extractMagnitude(sample.gyroscopeData)

            // Oblicz dystans dla akcelerometru i żyroskopu
            val accelDist = slidingWindowDTW(savedAccelMag, liveAccelMag)
            val gyroDist = slidingWindowDTW(savedGyroMag, liveGyroMag)

            val totalDist = accelDist + gyroDist

            // Szukamy MINIMUM (najbliższego sąsiada)
            if (totalDist < bestTotalDistance) {
                bestTotalDistance = totalDist
            }
        }

        Log.d("GestureRecognizer", "Najlepszy wynik (spośród ${trainingVariants.size} próbek): $bestTotalDistance")
        return bestTotalDistance
    }

    private fun slidingWindowDTW(saved: List<Float>, live: List<Float>): Double {
        if (saved.isEmpty() || live.isEmpty()) return Double.POSITIVE_INFINITY

        val savedSize = saved.size
        val liveSize = live.size

        // Jeśli live jest krótsze lub równe saved, porównaj bezpośrednio
        if (liveSize <= savedSize) {
            return dtwDistance(saved, live)
        }

        var minDistance = Double.POSITIVE_INFINITY

        // Okno szukania (od 80% do 120% długości wzorca)
        val minWindowSize = (savedSize * 0.8).toInt().coerceAtLeast(10)
        val maxWindowSize = (savedSize * 1.2).toInt().coerceAtMost(liveSize)

        for (windowSize in minWindowSize..maxWindowSize step 5) {
            val maxStart = liveSize - windowSize
            // Krok 3 dla wydajności
            for (start in 0..maxStart step 3) {
                val window = live.subList(start, start + windowSize)
                val distance = dtwDistance(saved, window)

                if (distance < minDistance) {
                    minDistance = distance
                }
            }
        }
        return minDistance
    }

    private fun dtwDistance(seq1: List<Float>, seq2: List<Float>): Double {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return Double.POSITIVE_INFINITY

        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
        dtw[0][0] = 0.0

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(seq1[i - 1] - seq2[j - 1]).toDouble()
                val lastMin = min(dtw[i - 1][j], min(dtw[i][j - 1], dtw[i - 1][j - 1]))
                dtw[i][j] = cost + lastMin
            }
        }
        return dtw[n][m]
    }

    private fun extractMagnitude(data: List<SensorSample>): List<Float> {
        if (data.isEmpty()) return emptyList()

        val magnitudes = data.map {
            sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
        }

        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance).toFloat()

        if (stdDev < 0.001f) {
            return magnitudes.map { 0f }
        }

        return magnitudes.map { (it - mean) / stdDev }
    }
}