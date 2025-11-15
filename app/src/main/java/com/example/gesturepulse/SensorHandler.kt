package com.example.gesturepulse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.serialization.Serializable

/**
 * Definicja pojedynczej próbki danych z sensora.
 */
@Serializable
data class SensorData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Wspólny handler do zarządzania SensorManagerem.
 * Odpowiada za rejestrowanie, wyrejestrowywanie i przekazywanie danych
 * z akcelerometru.
 */
class SensorHandler(context: Context) {

    // Prywatna funkcja callback, wywoływana przy każdej zmianie danych
    private var dataCallback: ((SensorData) -> Unit)? = null

    // Manager systemowy sensorów
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Domyślny sensor akcelerometru
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // TODO: Dodaj tu inne sensory (Żyroskop, Zbliżeniowy)
    // private val gyroscope: Sensor? = ...
    // private val proximity: Sensor? = ...

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            // Sprawdzamy, czy mamy callback i czy dane są z akcelerometru
            if (dataCallback != null && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val sensorData = SensorData(
                    timestamp = event.timestamp,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
                // Wywołaj callback z nowymi danymi
                dataCallback?.invoke(sensorData)
            }

            // TODO: Obsłuż tu dane z innych sensorów (np. żyroskopu)
            // if (dataCallback != null && event?.sensor?.type == Sensor.TYPE_GYROSCOPE) { ... }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Ignorujemy w tej implementacji
        }
    }

    /**
     * Rozpoczyna nasłuchiwanie i ustawia funkcję zwrotną (callback).
     */
    fun startListening(onData: (SensorData) -> Unit) {
        if (accelerometer == null) {
            // TODO: Obsłuż brak akcelerometru w telefonie
            return
        }
        dataCallback = onData
        // Rejestrujemy listener
        sensorManager.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME // Szybka częstotliwość dla gry
        )
        // TODO: Zarejestruj tu inne sensory...
    }

    /**
     * Kończy nasłuchiwanie i czyści callback, aby uniknąć wycieków pamięci.
     */
    fun stopListening() {
        dataCallback = null
        sensorManager.unregisterListener(sensorListener)
    }
}