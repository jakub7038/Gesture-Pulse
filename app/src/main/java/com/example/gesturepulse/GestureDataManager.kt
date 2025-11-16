package com.example.gesturepulse

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
 * Struktura danych dla gestu w pliku JSON.
 */
@Serializable
data class Gesture(
    val name: String,
    val accelerometerData: List<SensorSample>,
    val gyroscopeData: List<SensorSample>
)

/**
 * Singleton do zarządzania wczytywaniem i zapisywaniem gestów do pliku JSON.
 */
object GestureDataManager {

    private val json = Json { prettyPrint = true }

    /**
     * Wczytuje wszystkie gesty z pliku JSON.
     */
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

    /**
     * Zapisuje całą listę gestów do pliku, nadpisując jego zawartość.
     */
    fun saveAllGestures(context: Context, gestures: List<Gesture>) {
        try {
            val file = File(context.filesDir, GESTURE_FILE_NAME)
            val jsonString = json.encodeToString(gestures)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("GestureDataManager", "Błąd zapisu gestów", e)
        }
    }

    /**
     * funkcja do zapisywania pojedynczego, zaktualizowanego gestu.
     */
    fun updateGesture(context: Context, updatedGesture: Gesture) {
        val allGestures = loadAllGestures(context)
        val index = allGestures.indexOfFirst { it.name == updatedGesture.name }

        if (index != -1) {
            allGestures[index] = updatedGesture
            saveAllGestures(context, allGestures)
        } else {
            Log.w("GestureDataManager", "Nie znaleziono gestu '${updatedGesture.name}' do aktualizacji.")
        }
    }

    fun deleteGesture(context: Context, gestureName: String) {
        val allGestures = loadAllGestures(context)
        val gestureToRemove = allGestures.find { it.name == gestureName }

        if (gestureToRemove != null) {
            allGestures.remove(gestureToRemove)
            saveAllGestures(context, allGestures)
        } else {
            Log.w("GestureDataManager", "Nie znaleziono gestu '$gestureName' do usunięcia.")
        }
    }
}