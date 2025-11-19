package com.example.gesturepulse

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

// --- STAN GRY oraz logika ---

sealed class GameState {
    object Idle : GameState()
    data class Countdown(val commandName: String, val variants: List<Gesture>, val timeLeft: Int) : GameState()
    data class Recording(val commandName: String, val variants: List<Gesture>) : GameState()
    data class Analyzing(val commandName: String, val variants: List<Gesture>) : GameState()
    data class ShowResult(val success: Boolean, val score: Double, val threshold: Float) : GameState()
    object GameOver : GameState()
}

@Composable
fun rememberGameController(
    context: Context,
    sensorHandler: SensorHandler,
    navController: NavController
): GameController {
    val score = remember { mutableIntStateOf(0) }
    val gameState = remember { mutableStateOf<GameState>(GameState.Idle) }
    val allCommandsMap = remember { mutableStateOf<Map<String, List<Gesture>>>(emptyMap()) }
    val liveAccelData = remember { mutableStateListOf<SensorSample>() }
    val liveGyroData = remember { mutableStateListOf<SensorSample>() }

    // Wczytanie gestów przy starcie
    LaunchedEffect(Unit) {
        val loaded = GestureDataManager.loadAllGestures(context)
        allCommandsMap.value = loaded.groupBy { it.name }
    }

    // GŁÓWNA PĘTLA LOGIKI GRY
    LaunchedEffect(gameState.value) {
        when (val state = gameState.value) {
            is GameState.Idle -> {}

            is GameState.Countdown -> {
                if (state.timeLeft > 0) {
                    delay(1000)
                    gameState.value = state.copy(timeLeft = state.timeLeft - 1)
                } else {
                    gameState.value = GameState.Recording(state.commandName, state.variants)
                }
            }

            is GameState.Recording -> {
                liveAccelData.clear()
                liveGyroData.clear()
                sensorHandler.startListening(
                    onAccel = { liveAccelData.add(it) },
                    onGyro = { liveGyroData.add(it) }
                )
            }

            is GameState.Analyzing -> {
                sensorHandler.stopListening()
                val difficultyThreshold = state.variants.first().threshold.toDouble()
                val totalDistance = GestureRecognizer.recognizeGesture(
                    trainingSet = state.variants,
                    liveAccel = liveAccelData.toList(),
                    liveGyro = liveGyroData.toList()
                )
                val success = totalDistance < difficultyThreshold
                gameState.value = GameState.ShowResult(success, totalDistance, difficultyThreshold.toFloat())
            }

            is GameState.ShowResult -> {
                delay(2000)
                if (state.success) {
                    score.intValue++
                    if (allCommandsMap.value.isNotEmpty()) {
                        val nextKey = allCommandsMap.value.keys.random()
                        gameState.value = GameState.Countdown(nextKey, allCommandsMap.value[nextKey]!!, 2)
                    } else {
                        // Nie powinno się zdarzyć, ale na wszelki wypadek
                        gameState.value = GameState.GameOver
                    }
                } else {
                    vibrate(context)
                    gameState.value = GameState.GameOver
                }
            }

            is GameState.GameOver -> {
            }
        }
    }

    return remember(score, gameState, allCommandsMap) {
        GameController(
            score = score,
            gameState = gameState,
            allCommandsMap = allCommandsMap,
            context = context,
            navController = navController
        )
    }
}

// stan gry
class GameController(
    private val score: MutableIntState,
    private val gameState: MutableState<GameState>,
    private val allCommandsMap: State<Map<String, List<Gesture>>>,
    private val context: Context,
    private val navController: NavController
) {
    val currentScore: Int by score
    val currentState: GameState by gameState
    val hasGestures: Boolean
        get() = allCommandsMap.value.isNotEmpty()

    fun startGame() {
        if (allCommandsMap.value.isNotEmpty()) {
            score.intValue = 0
            val k = allCommandsMap.value.keys.random()
            gameState.value = GameState.Countdown(k, allCommandsMap.value[k]!!, 2)
        }
    }

    fun stopRecording() {
        if (gameState.value is GameState.Recording) {
            val state = gameState.value as GameState.Recording
            gameState.value = GameState.Analyzing(state.commandName, state.variants)
        }
    }

    fun restartGame() {
        score.intValue = 0
        gameState.value = GameState.Idle
    }

    fun saveScore(playerName: String, finalScore: Int) {
        ScoreDataManager.addScore(context, ScoreEntry(playerName, finalScore))
    }

    fun exitGame() {
        navController.popBackStack()
    }
}

// UI
@Composable
fun GameScreen(navController: NavController, sensorHandler: SensorHandler) {
    val context = LocalContext.current
    val controller = rememberGameController(context, sensorHandler, navController)

    GameScreenContent(controller)
}

@Composable
private fun GameScreenContent(controller: GameController) {
    val state = controller.currentState
    var showSaveScoreDialog by remember { mutableStateOf(false) }
    var scoreSaved by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        if (state !is GameState.GameOver && state !is GameState.Idle) {
            Text(
                "Punkty: ${controller.currentScore}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            when (state) {
                // ekran startowy
                is GameState.Idle -> {
                    Text(
                        "Gotowy?",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))

                    if (!controller.hasGestures) {
                        Text("Brak nagranych gestów!.", textAlign = TextAlign.Center)
                    } else {
                        Button(
                            onClick = { controller.startGame() },
                            modifier = Modifier.fillMaxWidth(0.6f).height(60.dp)
                        ) {
                            Text("START", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // time
                is GameState.Countdown -> {
                    Text("Wykonaj:", fontSize = 24.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        state.commandName,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 52.sp
                    )

                    Spacer(Modifier.height(64.dp))

                    Text(
                        if(state.timeLeft > 0) "${state.timeLeft}..." else "START!",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text("Ustaw telefon...", fontSize = 16.sp, color = Color.Gray)
                }

                // stopowanie przez przycisk
                is GameState.Recording -> {
                    Button(
                        onClick = { controller.stopRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("RUCH TRWA...", fontSize = 18.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("ZAKOŃCZ", fontSize = 40.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // analiza
                is GameState.Analyzing -> {
                    Text("Analizowanie...", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                }

                // wyniki
                is GameState.ShowResult -> {
                    val color = if (state.success) Color(0xFF2E7D32) else Color.Red
                    val text = if (state.success) "DOSKONALE!" else "PUDŁO!"

                    Text(text, fontSize = 56.sp, fontWeight = FontWeight.Black, color = color)

                    Spacer(Modifier.height(24.dp))
                    Text("Wynik: ${"%.1f".format(state.score)}", fontSize = 24.sp)
                    Text("Limit błędu: ${state.threshold.toInt()}", fontSize = 16.sp, color = Color.Gray)
                }

                // game over
                is GameState.GameOver -> {
                    Text("KONIEC GRY", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    Spacer(Modifier.height(16.dp))
                    Text("Twój wynik:", fontSize = 20.sp)
                    Text("${controller.currentScore}", fontSize = 80.sp, fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(48.dp))

                    Button(
                        onClick = { controller.restartGame() },
                        modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                    ) { Text("Zagraj Ponownie") }

                    Spacer(Modifier.height(16.dp))

                    if (!scoreSaved && controller.currentScore > 0) {
                        OutlinedButton(
                            onClick = { showSaveScoreDialog = true },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) { Text("Zapisz Wynik") }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { controller.exitGame() }) { Text("Wyjdź") }
                }
            }
        }
    }

    // dialog zapisu
    if (showSaveScoreDialog) {
        var playerName by remember { mutableStateOf("") }
        val finalScore = controller.currentScore
        AlertDialog(
            onDismissRequest = { showSaveScoreDialog = false },
            title = { Text("Zapisz Wynik") },
            text = {
                Column {
                    Text("Wynik: $finalScore")
                    OutlinedTextField(value = playerName, onValueChange = { playerName = it }, label = { Text("Imię") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    controller.saveScore(playerName, finalScore)
                    scoreSaved = true
                    showSaveScoreDialog = false
                }) { Text("Zapisz") }
            },
            dismissButton = { Button(onClick = { showSaveScoreDialog = false }) { Text("Anuluj") } }
        )
    }
}

private fun vibrate(context: Context) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        v.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}