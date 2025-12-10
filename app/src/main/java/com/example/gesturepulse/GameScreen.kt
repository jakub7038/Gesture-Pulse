package com.example.gesturepulse

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlin.math.ceil

// --- STAN GRY oraz logika ---

sealed class GameState {
    object Idle : GameState()
    data class LevelUp(val nextCommand: Command) : GameState()

    data class Countdown(val command: Command, val timeLeftMs: Long, val totalDurationMs: Long) : GameState()

    data class Recording(
        val command: Command,
        val totalTime: Long,
        val timeLeft: Long
    ) : GameState()

    data class Analyzing(val command: Command, val durationMs: Long) : GameState()

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
    val controller = remember(score, gameState, allCommands) {
        GameController(score, gameState, allCommands, context, navController)
    }

    val currentState = gameState.value
    val isMotionRecording = currentState is GameState.Recording && currentState.command.type == CommandType.MOTION

    DisposableEffect(isMotionRecording) {
        if (isMotionRecording) {
            liveAccelData.clear()
            liveGyroData.clear()
            sensorHandler.startListening(
                onAccel = { liveAccelData.add(it) },
                onGyro = { liveGyroData.add(it) }
            )
        } else {
            sensorHandler.stopListening()
        }
        onDispose { sensorHandler.stopListening() }
    }

    LaunchedEffect(gameState.value) {
        when (val state = gameState.value) {
            is GameState.Idle -> {}

            // Ekran przyśpieszania co 5 lvl;
            is GameState.LevelUp -> {
                delay(1000)
                val level = score.intValue / 5
                // -0.3s co 5lvl
                val countdownDuration = (2500L - (level * 300L)).coerceAtLeast(1500L)
                gameState.value = GameState.Countdown(state.nextCommand, countdownDuration, countdownDuration)
            }

            is GameState.Countdown -> {
                controller.currentRoundClicks.intValue = 0

                if (state.timeLeftMs > 0) {
                    delay(50)
                    gameState.value = state.copy(timeLeftMs = state.timeLeftMs - 50)
                } else {
                    //czas na gest
                    val baseTime = if (state.command.type == CommandType.MASHING) 4000L else 5000L
                    val reduction = (score.intValue / 5) * 500L
                    val allowedTime = (baseTime - reduction).coerceAtLeast(2000L)

                    gameState.value = GameState.Recording(
                        command = state.command,
                        totalTime = allowedTime,
                        timeLeft = allowedTime
                    )
                }
            }

            is GameState.Recording -> {
                if (state.timeLeft > 0) {
                    delay(50)
                    gameState.value = state.copy(timeLeft = state.timeLeft - 50)
                } else {
                    gameState.value = GameState.Analyzing(state.command, state.totalTime)
                }
            }

            is GameState.Analyzing -> {
                val cmd = state.command
                val success: Boolean
                val resultValue: Double
                val durationSec = state.durationMs / 1000.0

                when (cmd.type) {
                    CommandType.MOTION -> {
                        val totalDistance = GestureRecognizer.recognize(
                            trainingVariants = cmd.variants,
                            liveAccel = liveAccelData.toList(),
                            liveGyro = liveGyroData.toList()
                        )
                        success = totalDistance < cmd.threshold
                        resultValue = totalDistance
                    }
                    CommandType.MASHING -> {
                        val targetClicks = ceil(cmd.threshold * durationSec).toInt() + 4
                        val clicks = controller.currentRoundClicks.intValue
                        success = clicks >= targetClicks
                        resultValue = clicks.toDouble()
                    }
                    CommandType.DONT_TAP -> {
                        val clicks = controller.currentRoundClicks.intValue
                        success = clicks == 0
                        resultValue = clicks.toDouble()
                    }
                }

                val displayThreshold = if (cmd.type == CommandType.MASHING) {
                    (ceil(cmd.threshold * durationSec).toInt() + 4).toFloat()
                } else {
                    cmd.threshold
                }

                gameState.value = GameState.ShowResult(success, resultValue, displayThreshold)
            }

            is GameState.ShowResult -> {
                if (!state.success) vibrate(context)
                delay(2000)
                if (state.success) {
                    score.intValue++
                    if (allCommands.value.isNotEmpty()) {
                        val nextCommand = allCommands.value.random()

                        if (score.intValue > 0 && score.intValue % 5 == 0) {
                            gameState.value = GameState.LevelUp(nextCommand)
                        } else {
                            val level = score.intValue / 5
                            val countdownDuration = (2500L - (level * 300L)).coerceAtLeast(1500L)
                            gameState.value = GameState.Countdown(nextCommand, countdownDuration, countdownDuration)
                        }
                    } else {
                        gameState.value = GameState.GameOver
                    }
                } else {
                    gameState.value = GameState.GameOver
                }
            }
            is GameState.GameOver -> {}
        }
    }
    return controller
}


class GameController(
    private val score: MutableIntState,
    private val gameState: MutableState<GameState>,
    private val allCommands: State<List<Command>>,
    private val context: Context,
    private val navController: NavController
) {
    val currentScore: Int by score
    val currentState: GameState by gameState
    var currentRoundClicks = mutableIntStateOf(0)

    val hasGestures: Boolean
        get() = allCommands.value.isNotEmpty()

    fun startGame() {
        if (allCommands.value.isNotEmpty()) {
            score.intValue = 0
            gameState.value = GameState.Countdown(allCommands.value.random(), 2500L, 2500L)
        }
    }

    fun stopRecording() {
        if (gameState.value is GameState.Recording) {
            val state = gameState.value as GameState.Recording
            gameState.value = GameState.Analyzing(state.command, state.totalTime)
        }
    }

    fun registerClick() {
        if (gameState.value is GameState.Recording) {
            currentRoundClicks.intValue++
            val state = gameState.value as GameState.Recording
            if (state.command.type == CommandType.DONT_TAP) {
                gameState.value = GameState.ShowResult(false, 1.0, 0f)
            }
        }
    }

    fun restartGame() { score.intValue = 0; gameState.value = GameState.Idle }
    fun saveScore(name: String, s: Int) { ScoreDataManager.addScore(context, ScoreEntry(name, s)) }
    fun exitGame() { navController.popBackStack() }
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

    // szybciej ekran
    if (state is GameState.LevelUp) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SZYBCIEJ!", color = Color.Red, fontSize = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("SZYBCIEJ!", color = Color.Red, fontSize = 50.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text("SZYBCIEJ!", color = Color.Red, fontSize = 60.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            }
        }
        return
    }

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
                is GameState.Idle -> {
                    Text(
                        "Gotowy?",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))

                    if (!controller.hasGestures) {
                        Text("Brak nagranych gestów!", textAlign = TextAlign.Center)
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
                        text = state.command.name,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 52.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(48.dp))

                    Text("PRZYGOTUJ SIĘ!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)

                    Spacer(Modifier.height(24.dp))

                    val progress = state.timeLeftMs.toFloat() / state.totalDurationMs.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    Spacer(Modifier.height(24.dp))

                    val instruction = when (state.command.type) {
                        CommandType.MOTION -> "Ustaw telefon..."
                        CommandType.MASHING -> "Przygotuj palec!"
                        CommandType.DONT_TAP -> "Ręce z dala od ekranu!"
                    }
                    Text(instruction, fontSize = 14.sp, color = Color.Gray)
                }

                // stopowanie przez przycisk
                is GameState.Recording -> {
                    val progress = state.timeLeft.toFloat() / state.totalTime.toFloat()
                    val cmdType = state.command.type
                    val barColor = if (cmdType == CommandType.DONT_TAP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 32.dp),
                        color = barColor, trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.timeLeft / 100f).toInt() / 10f} s", color = Color.Gray)
                    Spacer(Modifier.height(32.dp))

                    when (cmdType) {
                        CommandType.MOTION -> {
                            Button(
                                onClick = { controller.stopRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(260.dp).padding(16.dp),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("RUCH TRWA...", fontSize = 18.sp)
                                    Spacer(Modifier.height(16.dp))
                                    Text("GOTOWE", fontSize = 40.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        CommandType.MASHING -> {
                            val dynamicTarget = ceil(state.command.threshold * (state.totalTime / 1000.0)).toInt() + 4
                            Button(
                                onClick = { controller.registerClick() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.fillMaxWidth().height(260.dp).padding(16.dp),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("KLIKAJ!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    Spacer(Modifier.height(16.dp))
                                    Text("${controller.currentRoundClicks.intValue}", fontSize = 80.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                    Text("CEL: $dynamicTarget", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                }
                            }
                        }
                        CommandType.DONT_TAP -> {
                            Button(
                                onClick = { controller.registerClick() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth().height(260.dp).padding(16.dp),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("NIE KLIKAJ!", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Red)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Czekaj...", fontSize = 18.sp, color = Color.White)
                                }
                            }
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
                    val color = if (state.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
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
                else -> {}
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
    val strength = SettingsManager.getVibrationStrength(context)
    if (strength == SettingsManager.VIBRATION_OFF) return

    try {
        // Ponieważ minSdk = 31, VibratorManager jest zawsze dostępny
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator

        if (vibrator.hasVibrator()) {
            // Trik na Motorolę: AudioAttributes jako ALARM
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val isStrong = (strength == SettingsManager.VIBRATION_STRONG)

            // Krótsze czasy, bo tryb ALARM bije mocno
            val duration = if (isStrong) 500L else 100L
            val amplitude = if (isStrong) 255 else 80

            val effect = VibrationEffect.createOneShot(duration, amplitude)

            // Tłumimy ostrzeżenie, bo musimy użyć tej metody dla triku z AudioAttributes
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}