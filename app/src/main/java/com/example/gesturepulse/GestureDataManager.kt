package com.example.gesturepulse

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

const val GESTURE_FILE_NAME = "my_gestures.json"

@Serializable
data class SensorSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

@Serializable
data class Gesture(
    val name: String,
    val accelerometerData: List<SensorSample>,
    val gyroscopeData: List<SensorSample>
)

object GestureDataManager {

    private val json = Json { prettyPrint = true }

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
     * Dodaje nowy gest do listy (pozwala na duplikaty nazw - warianty).
     */
    fun addGesture(context: Context, gesture: Gesture) {
        val allGestures = loadAllGestures(context)
        allGestures.add(gesture)
        saveAllGestures(context, allGestures)
    }

    fun updateGesture(context: Context, updatedGesture: Gesture) {
        // Ta funkcja aktualizuje PIERWSZE wystąpienie o danej nazwie
        // W modelu multi-sample rzadziej używana, ale zostawiamy dla edytora
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
        // Usuwa WSZYSTKIE warianty o tej nazwie
        val allGestures = loadAllGestures(context)
        val filtered = allGestures.filter { it.name != gestureName }

        if (filtered.size != allGestures.size) {
            saveAllGestures(context, filtered)
        }
    }
}