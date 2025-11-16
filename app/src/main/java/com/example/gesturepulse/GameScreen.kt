package com.example.gesturepulse

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // <-- POPRAWKA: DODANY BRAKUJĄCY IMPORT
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// --- ZMIANA W STANACH GRY ---
private sealed class GameState {
    object Idle : GameState()
    data class ShowingCommand(val command: Command) : GameState()
    data class Listening(val command: Command) : GameState()
    data class Analyzing(val command: Command) : GameState()
    data class ShowResult(val success: Boolean, val percentage: Float) : GameState()
    object GameOver : GameState()
}

private data class Command(val name: String, val type: CommandType, val gestureData: Gesture)
private enum class CommandType { CUSTOM_GESTURE }


@Composable
fun GameScreen(navController: NavController, sensorHandler: SensorHandler) {
    val context = LocalContext.current
    var score by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf<GameState>(GameState.Idle) }
    var allCommands by remember { mutableStateOf<List<Command>>(emptyList()) }
    var currentCommandText by remember { mutableStateOf("Naciśnij Start") }

    val liveAccelData = remember { mutableStateListOf<SensorSample>() }
    val liveGyroData = remember { mutableStateListOf<SensorSample>() }

    var countdownTimer by remember { mutableFloatStateOf(0f) }
    var totalDurationMs by remember { mutableLongStateOf(1L) }

    LaunchedEffect(Unit) {
        val loadedGestures = GestureDataManager.loadAllGestures(context)
        val customCommands = loadedGestures.map {
            Command(name = it.name, type = CommandType.CUSTOM_GESTURE, gestureData = it)
        }
        allCommands = customCommands
        Log.d("GameScreen", "Załadowano ${allCommands.size} komend.")
    }


    LaunchedEffect(gameState) {
        when (val state = gameState) {
            is GameState.ShowingCommand -> {
                currentCommandText = state.command.name
                countdownTimer = 0f
                // TODO: tts dla komend

                delay(1000)
                liveAccelData.clear()
                liveGyroData.clear()
                gameState = GameState.Listening(state.command)
            }

            is GameState.Listening -> {
                val g = state.command.gestureData
                val gestureTimeNs = (g.accelerometerData.lastOrNull()?.timestamp ?: 0L) - (g.accelerometerData.firstOrNull()?.timestamp ?: 0L)
                val durationMs = (gestureTimeNs / 1_000_000L).coerceIn(1000, 3000) + 500L

                totalDurationMs = durationMs
                countdownTimer = durationMs.toFloat()

                sensorHandler.startListening(
                    onAccel = { liveAccelData.add(it) },
                    onGyro = { liveGyroData.add(it) }
                )

                val steps = (durationMs / 100L).toInt()
                for (i in 0 until steps) {
                    delay(100L)
                    countdownTimer -= 100f
                }

                val remainingTime = durationMs % 100L
                if (remainingTime > 0) delay(remainingTime)
                countdownTimer = 0f

                sensorHandler.stopListening()
                gameState = GameState.Analyzing(state.command)
            }

            is GameState.Analyzing -> {
                val command = state.command

                // --- POBIERZ WYNIK ---
                val totalDistance = GestureRecognizer.analyzeGesture(
                    command.gestureData,
                    liveAccelData.toList(),
                    liveGyroData.toList()
                )

                // --- OBLICZ PROCENT ---
                val percent = (1.0f - (totalDistance / (GestureRecognizer.DTW_THRESHOLD * 2.0f))) * 100.0f
                val clampedPercent = percent.toFloat().coerceIn(0f, 100f)

                // Sprawdź sukces
                val success = totalDistance < GestureRecognizer.DTW_THRESHOLD

                // Przejdź do nowego stanu ShowResult
                gameState = GameState.ShowResult(success, clampedPercent)
            }

            // --- NOWA OBSŁUGA STANU WYNIKU ---
            is GameState.ShowResult -> {
                // Pokaż wynik przez 2 sekundy
                currentCommandText = if (state.success) "Dobrze!" else "Błąd!"
                delay(2000)

                if (state.success) {
                    score++
                    val nextCommand = allCommands.randomOrNull()
                    if (nextCommand != null) {
                        gameState = GameState.ShowingCommand(nextCommand)
                    } else {
                        currentCommandText = "Brak komend!"
                        gameState = GameState.Idle
                    }
                } else {
                    vibrate(context)
                    gameState = GameState.GameOver
                }
            }

            else -> { /* GameOver */ }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sensorHandler.stopListening()
        }
    }


    // --- INTERFEJS UŻYTKOWNIKA ---
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            if (gameState == GameState.GameOver) {
                Text("Koniec Gry!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text("Twój wynik: $score", fontSize = 24.sp)
                Spacer(Modifier.height(32.dp))
                Button(onClick = {
                    score = 0
                    gameState = GameState.Idle
                    currentCommandText = "Naciśnij Start"
                }) {
                    Text("Zagraj Ponownie")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Wyjdź do Menu")
                }
            } else {
                // UI GŁÓWNEJ GRY
                Text("Wynik: $score", fontSize = 24.sp)
                Spacer(Modifier.height(64.dp))

                Text(currentCommandText, fontSize = 32.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(16.dp))

                if (gameState is GameState.Listening || gameState is GameState.ShowResult) {
                    LinearProgressIndicator(
                        progress = { countdownTimer / totalDurationMs.toFloat() },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Czas: %.1f s".format(countdownTimer / 1000f),
                        fontSize = 16.sp
                    )
                }

                if (gameState is GameState.ShowResult) {
                    val state = gameState as GameState.ShowResult
                    Text(
                        "Podobieństwo: ${state.percentage.roundToInt()}%",
                        fontSize = 20.sp,
                        color = if (state.success) Color.Green else Color.Red
                    )
                }

                Spacer(Modifier.height(64.dp))

                if (gameState == GameState.Idle) {
                    Button(onClick = {
                        val firstCommand = allCommands.randomOrNull()
                        if (firstCommand != null) {
                            gameState = GameState.ShowingCommand(firstCommand)
                        } else {
                            currentCommandText = "Brak gestów! Nagraj jakiś."
                        }
                    }, enabled = allCommands.isNotEmpty()) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

private fun vibrate(context: Context) {
    try {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

    } catch (e: Exception) {
        Log.e("GameScreen", "Błąd wibracji: ${e.message}")
    }
}