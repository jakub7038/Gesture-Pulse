package com.example.gesturepulse

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

const val GESTURE_FILE_NAME = "my_gestures.json"

/**
 * Definicja pojedynczej próbki danych z sensora.
 */
@Serializable
data class SensorSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Struktura danych dla gestu.
 * Dodano pole 'threshold' z wartością domyślną 20.0f.
 */

/**
 * TODO: te warianty mają być częscią gesty a nie standalone, (ale narazie tak jest i działa)
 */
@Serializable
data class Gesture(
    val name: String,
    val accelerometerData: List<SensorSample>,
    val gyroscopeData: List<SensorSample>,
    val threshold: Float = 20.0f // margines błędu
)

object GestureDataManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    //READ
    fun loadAllGestures(context: Context): MutableList<Gesture> {
        return try {
            val file = File(context.filesDir, GESTURE_FILE_NAME)
            if (!file.exists()) {
                return mutableListOf()
            }
            val content = file.readText()
            Json.decodeFromString(content)
        } catch (e: Exception) {
            Log.e("GestureDataManager", "Błąd wczytywania gestów", e)
            mutableListOf()
        }
    }

    //CREATE
    fun saveAllGestures(context: Context, gestures: List<Gesture>) {
        try {
            val file = File(context.filesDir, GESTURE_FILE_NAME)
            val jsonString = json.encodeToString(gestures)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("GestureDataManager", "Błąd zapisu gestów", e)
        }
    }
    fun addGesture(context: Context, gesture: Gesture) {
        val allGestures = loadAllGestures(context)
        allGestures.add(gesture)
        saveAllGestures(context, allGestures)
    }


    // margines błedu dla takich samych wariantów (UPDATE)
    fun updateGestureThreshold(context: Context, gestureName: String, newThreshold: Float) {
        val allGestures = loadAllGestures(context)
        var updated = false

        val newGestures = allGestures.map {
            if (it.name == gestureName) {
                updated = true
                it.copy(threshold = newThreshold)
            } else {
                it
            }
        }

        if (updated) {
            saveAllGestures(context, newGestures)
            Log.d("GestureDataManager", "Zaktualizowano threshold dla '$gestureName' na $newThreshold")
        }
    }

    //DELETE
    fun deleteGesture(context: Context, gestureName: String) {
        val allGestures = loadAllGestures(context)
        val filtered = allGestures.filter { it.name != gestureName }

        if (filtered.size != allGestures.size) {
            saveAllGestures(context, filtered)
        }
    }
}