package com.example.gesturepulse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Wspólny handler do zarządzania SensorManagerem.
 * Odpowiada za rejestrowanie, wyrejestrowywanie i przekazywanie danych
 * z akcelerometru ORAZ żyroskopu.
 */
class SensorHandler(context: Context) {

    // Oddzielne callbacki dla każdego sensora
    private var accelCallback: ((SensorSample) -> Unit)? = null
    private var gyroCallback: ((SensorSample) -> Unit)? = null

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Dwa sensory
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // TODO: Dodaj tu Czujnik Zbliżeniowy (TYPE_PROXIMITY)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            // Kieruj dane do odpowiedniego callbacku
            when (event?.sensor?.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelCallback?.invoke(
                        SensorSample(
                            timestamp = event.timestamp,
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2]
                        )
                    )
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroCallback?.invoke(
                        SensorSample(
                            timestamp = event.timestamp,
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2]
                        )
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Ignorujemy
        }
    }

    /**
     * Rozpoczyna nasłuchiwanie i ustawia funkcje zwrotne (callbacki).
     */
    fun startListening(
        onAccel: (SensorSample) -> Unit,
        onGyro: (SensorSample) -> Unit
    ) {
        // Ustaw callbacki
        accelCallback = onAccel
        gyroCallback = onGyro

        // Zarejestruj oba sensory
        accelerometer?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        } ?: Log.w("SensorHandler", "Akcelerometr niedostępny!")

        gyroscope?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        } ?: Log.w("SensorHandler", "Żyroskop niedostępny!")
    }

    /**
     * Kończy nasłuchiwanie i czyści callbacki.
     */
    fun stopListening() {
        accelCallback = null
        gyroCallback = null
        sensorManager.unregisterListener(sensorListener)
    }
}