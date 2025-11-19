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
    // Przechowuje obiekt Command, a nie jego części
    data class Countdown(val command: Command, val timeLeft: Int) : GameState()
    data class Recording(val command: Command) : GameState()
    data class Analyzing(val command: Command) : GameState()
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

    // Wczytujemy listę Komend
    val allCommands = remember { mutableStateOf<List<Command>>(emptyList()) }

    val liveAccelData = remember { mutableStateListOf<SensorSample>() }
    val liveGyroData = remember { mutableStateListOf<SensorSample>() }

    // Wczytanie gestów przy starcie
    LaunchedEffect(Unit) {
        allCommands.value = GestureDataManager.loadAllGestures(context)
    }

    // pętla gry
    LaunchedEffect(gameState.value) {
        when (val state = gameState.value) {
            is GameState.Idle -> {}

            is GameState.Countdown -> {
                if (state.timeLeft > 0) {
                    delay(1000)
                    gameState.value = state.copy(timeLeft = state.timeLeft - 1)
                } else {
                    gameState.value = GameState.Recording(state.command)
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

                // Pobieramy próg i warianty bezpośrednio z obiektu Command
                val command = state.command
                val difficultyThreshold = command.threshold.toDouble()

                // Używamy nowej nazwy funkcji GestureRecognizer.recognize
                val totalDistance = GestureRecognizer.recognize(
                    trainingVariants = command.variants,
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
                    if (allCommands.value.isNotEmpty()) {
                        // Losowanie nowej Komendy (Command)
                        val nextCommand = allCommands.value.random()
                        gameState.value = GameState.Countdown(nextCommand, 2)
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

    // ZPrzekazujemy listę Komend
    return remember(score, gameState, allCommands) {
        GameController(
            score = score,
            gameState = gameState,
            allCommands = allCommands,
            context = context,
            navController = navController
        )
    }
}

// stan gry
class GameController(
    private val score: MutableIntState,
    private val gameState: MutableState<GameState>,
    private val allCommands: State<List<Command>>,
    private val context: Context,
    private val navController: NavController
) {
    val currentScore: Int by score
    val currentState: GameState by gameState
    val hasGestures: Boolean
        get() = allCommands.value.isNotEmpty()

    fun startGame() {
        if (allCommands.value.isNotEmpty()) {
            score.intValue = 0
            // Losowanie całego obiektu Command
            val k = allCommands.value.random()
            gameState.value = GameState.Countdown(k, 2)
        }
    }

    fun stopRecording() {
        if (gameState.value is GameState.Recording) {
            val state = gameState.value as GameState.Recording
            // Przechodzimy do Analyzing, przekazując obiekt Command
            gameState.value = GameState.Analyzing(state.command)
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
                        //  Pobieramy nazwę z obiektu Command
                        state.command.name,
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