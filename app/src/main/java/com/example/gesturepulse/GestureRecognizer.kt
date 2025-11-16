package com.example.gesturepulse

import android.util.Log
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Obiekt narzędziowy do rozpoznawania gestów.
 */
object GestureRecognizer {

    const val DTW_THRESHOLD = 100.0

    /**
     * Oblicza dystans DTW między dwiema seriami danych 1D.
     */
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

    /**
     * Oblicza magnitude z normalizacją.
     */
    private fun extractMagnitude(data: List<SensorSample>): List<Float> {
        if (data.isEmpty()) return emptyList()

        // Oblicz prawdziwą magnitude (z pierwiastkiem)
        val magnitudes = data.map {
            sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
        }

        // Normalizacja - odejmij średnią i podziel przez odchylenie standardowe
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance).toFloat()

        if (stdDev < 0.001f) {
            return magnitudes.map { 0f }
        }

        return magnitudes.map { (it - mean) / stdDev }
    }

    /**
     * NOWA FUNKCJA: Sliding Window DTW
     * Szuka najlepszego dopasowania w dłuższej sekwencji live.
     */
    private fun slidingWindowDTW(saved: List<Float>, live: List<Float>): Double {
        if (saved.isEmpty() || live.isEmpty()) return Double.POSITIVE_INFINITY

        val savedSize = saved.size
        val liveSize = live.size

        // Jeśli live jest krótsze niż saved, porównaj bezpośrednio
        if (liveSize <= savedSize) {
            return dtwDistance(saved, live)
        }

        // Przesuwaj okno o rozmiarze saved po live i znajdź najmniejszy dystans
        var minDistance = Double.POSITIVE_INFINITY

        // Możemy też sprawdzać okna o różnych rozmiarach (80%-120% saved)
        val minWindowSize = (savedSize * 0.8).toInt().coerceAtLeast(10)
        val maxWindowSize = (savedSize * 1.2).toInt().coerceAtMost(liveSize)

        for (windowSize in minWindowSize..maxWindowSize step 5) {
            // Przesuń okno po całej długości live
            val maxStart = liveSize - windowSize
            for (start in 0..maxStart step 3) { // Krok 3 dla wydajności
                val window = live.subList(start, start + windowSize)
                val distance = dtwDistance(saved, window)

                if (distance < minDistance) {
                    minDistance = distance
                    Log.d("GestureRecognizer", "Lepsze dopasowanie: start=$start, size=$windowSize, dist=$distance")
                }
            }
        }

        return minDistance
    }

    /**
     * Główna funkcja porównująca dwa gesty z użyciem sliding window.
     */
    fun analyzeGesture(
        savedGesture: Gesture,
        liveAccel: List<SensorSample>,
        liveGyro: List<SensorSample>
    ): Double {

        if (liveAccel.isEmpty() || savedGesture.accelerometerData.isEmpty()) {
            Log.w("GestureRecognizer", "Brak danych akcelerometru")
            return Double.POSITIVE_INFINITY
        }

        if (liveGyro.isEmpty() || savedGesture.gyroscopeData.isEmpty()) {
            Log.w("GestureRecognizer", "Brak danych żyroskopu")
            return Double.POSITIVE_INFINITY
        }

        // Wyciągnij znormalizowane magnitude
        val savedAccelMag = extractMagnitude(savedGesture.accelerometerData)
        val liveAccelMag = extractMagnitude(liveAccel)

        val savedGyroMag = extractMagnitude(savedGesture.gyroscopeData)
        val liveGyroMag = extractMagnitude(liveGyro)

        // Użyj sliding window DTW zamiast zwykłego DTW
        val accelDistance = slidingWindowDTW(savedAccelMag, liveAccelMag)
        val gyroDistance = slidingWindowDTW(savedGyroMag, liveGyroMag)

        // Suma dystansów (możesz też użyć wag, np. 0.6*accel + 0.4*gyro)
        val totalDistance = accelDistance + gyroDistance

        Log.d("GestureRecognizer",
            "Sliding Window DTW: %.2f (A: %.2f, G: %.2f) | Próg: $DTW_THRESHOLD".format(
                totalDistance, accelDistance, gyroDistance
            )
        )

        Log.d("GestureRecognizer",
            "Rozmiary - Saved: A=${savedAccelMag.size}, G=${savedGyroMag.size} | " +
                    "Live: A=${liveAccelMag.size}, G=${liveGyroMag.size}"
        )

        return totalDistance
    }
}