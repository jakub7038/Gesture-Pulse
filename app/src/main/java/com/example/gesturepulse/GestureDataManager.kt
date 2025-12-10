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

//no i jeszcze to klikanie
enum class CommandType {
    MOTION,
    MASHING,
    DONT_TAP
}

@Serializable
data class SensorSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Struktura danych dla gestu.
 */

@Serializable
data class Gesture(
    val name: String,
    val accelerometerData: List<SensorSample>,
    val gyroscopeData: List<SensorSample>
)

/**
 *  grupowane warianty i wspólny próg błędu
 * jest to teraz główna struktura gestów
 */
@Serializable
data class Command(
    val name: String,
    val type: CommandType = CommandType.MOTION,
    val variants: MutableList<Gesture> = mutableListOf(),
    val threshold: Float = 20.0f //margines błędu
)

object GestureDataManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    //READ
    fun loadAllGestures(context: Context): MutableList<Command> {
        val file = File(context.filesDir, GESTURE_FILE_NAME)
        if (!file.exists()) {
            Log.d("GestureDataManager", "Pierwsze uruchomienie: Inicjalizacja gestów domyślnych.")
            resetToDefaults(context)
        }

        return try {
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

    //SAVE
    fun saveAllGestures(context: Context, commands: List<Command>) {
        try {
            val file = File(context.filesDir, GESTURE_FILE_NAME)
            val jsonString = json.encodeToString(commands)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("GestureDataManager", "Błąd zapisu gestów", e)
        }
    }

    // CREATE
    fun addCommand(context: Context, command: Command) {
        val allCommands = loadAllGestures(context)
        allCommands.add(command)
        saveAllGestures(context, allCommands)
    }

    // UPDATE
    // margines błedu dla takich samych wariantów
    fun updateCommandThreshold(context: Context, commandName: String, newThreshold: Float) {
        val allCommands = loadAllGestures(context)
        var updated = false

        val newCommands = allCommands.map {
            if (it.name == commandName) {
                updated = true
                it.copy(threshold = newThreshold)
            } else {
                it
            }
        }
        if (updated) saveAllGestures(context, newCommands)
    }

    // Aktualizacja wariantu wewnątrz komendy
    fun updateVariant(context: Context, commandName: String, variantIndex: Int, newVariant: Gesture) {
        val allCommands = loadAllGestures(context)
        val command = allCommands.find { it.name == commandName }

        command?.let {
            if (variantIndex >= 0 && variantIndex < it.variants.size) {
                it.variants[variantIndex] = newVariant
                saveAllGestures(context, allCommands)
            }
        }
    }

    //DELETE
    fun deleteCommand(context: Context, commandName: String) {
        val allCommands = loadAllGestures(context)
        val filtered = allCommands.filter { it.name != commandName }

        if (filtered.size != allCommands.size) {
            saveAllGestures(context, filtered)
            Log.d("GestureDataManager", "Usunięto komendę '$commandName'")
        }
    }

    fun deleteVariant(context: Context, commandName: String, variantIndex: Int) {
        val allCommands = loadAllGestures(context)
        val command = allCommands.find { it.name == commandName }

        command?.let {
            if (variantIndex >= 0 && variantIndex < it.variants.size) {
                it.variants.removeAt(variantIndex)
                saveAllGestures(context, allCommands)
            }
        }
    }

    //inicjalizacja
    fun resetToDefaults(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.default_gestures)
            val content = inputStream.bufferedReader().use { it.readText() }

            val defaultCommands: List<Command> = json.decodeFromString(content)

            saveAllGestures(context, defaultCommands)
            Log.d("GestureDataManager", "Ustawiono gesty domyślne: ${defaultCommands.size} komend")

        } catch (e: Exception) {
            Log.e("GestureDataManager", "Błąd resetowania do domyślnych", e)
        }
    }
}