package com.example.gesturepulse

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

const val SCORE_FILE_NAME = "my_scores.json"

/**
 * Struktura danych dla pojedynczego wpisu w rankingu.
 */
@Serializable
data class ScoreEntry(
    val name: String,
    val score: Int
)

/**
 * Singleton do zarządzania wczytywaniem i zapisywaniem wyników do pliku JSON.
 */
object ScoreDataManager {

    private val json = Json { prettyPrint = true }

    /**
     * Wczytuje wszystkie wyniki z pliku JSON.
     */
    fun loadAllScores(context: Context): MutableList<ScoreEntry> {
        return try {
            val file = File(context.filesDir, SCORE_FILE_NAME)
            if (!file.exists()) {
                return mutableListOf()
            }
            val content = file.readText()
            Json.decodeFromString(content)
        } catch (e: Exception) {
            Log.e("ScoreDataManager", "Błąd wczytywania wyników", e)
            mutableListOf()
        }
    }

    /**
     * Zapisuje całą listę wyników do pliku, nadpisując jego zawartość.
     */
    fun saveAllScores(context: Context, scores: List<ScoreEntry>) {
        try {
            val file = File(context.filesDir, SCORE_FILE_NAME)
            val jsonString = json.encodeToString(scores)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("ScoreDataManager", "Błąd zapisu wyników", e)
        }
    }

    /**
     * Dodaje nowy wpis do rankingu.
     */
    fun addScore(context: Context, scoreEntry: ScoreEntry) {
        try {
            val allScores = loadAllScores(context)
            allScores.add(scoreEntry)
            saveAllScores(context, allScores)
        } catch (e: Exception) {
            Log.e("ScoreDataManager", "Błąd dodawania wyniku", e)
        }
    }
}