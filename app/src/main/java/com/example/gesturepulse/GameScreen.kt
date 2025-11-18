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

private sealed class GameState {
    object Idle : GameState()
    data class Countdown(val commandName: String, val variants: List<Gesture>, val timeLeft: Int) : GameState()
    data class Recording(val commandName: String, val variants: List<Gesture>) : GameState()
    data class Analyzing(val commandName: String, val variants: List<Gesture>) : GameState()
    data class ShowResult(val success: Boolean, val score: Double, val threshold: Float) : GameState()
    object GameOver : GameState()
}

@Composable
fun GameScreen(navController: NavController, sensorHandler: SensorHandler) {
    val context = LocalContext.current
    var score by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf<GameState>(GameState.Idle) }

    var allCommandsMap by remember { mutableStateOf<Map<String, List<Gesture>>>(emptyMap()) }

    val liveAccelData = remember { mutableStateListOf<SensorSample>() }
    val liveGyroData = remember { mutableStateListOf<SensorSample>() }

    var showSaveScoreDialog by remember { mutableStateOf(false) }
    var scoreSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = GestureDataManager.loadAllGestures(context)
        allCommandsMap = loaded.groupBy { it.name }
    }

    // logika gry
    LaunchedEffect(gameState) {
        when (val state = gameState) {
            is GameState.Idle -> {}

            is GameState.Countdown -> {
                if (state.timeLeft > 0) {
                    delay(1000)
                    gameState = state.copy(timeLeft = state.timeLeft - 1)
                } else {
                    gameState = GameState.Recording(state.commandName, state.variants)
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
                gameState = GameState.ShowResult(success, totalDistance, difficultyThreshold.toFloat())
            }

            is GameState.ShowResult -> {
                delay(2000)
                if (state.success) {
                    score++
                    if (allCommandsMap.isNotEmpty()) {
                        val nextKey = allCommandsMap.keys.random()
                        gameState = GameState.Countdown(nextKey, allCommandsMap[nextKey]!!, 2)
                    } else {
                        gameState = GameState.GameOver
                    }
                } else {
                    vibrate(context)
                    gameState = GameState.GameOver
                }
            }

            else -> {}
        }
    }

    // ui gry
    Box(Modifier.fillMaxSize()) {

        if (gameState !is GameState.GameOver && gameState !is GameState.Idle) {
            Text(
                "Punkty: $score",
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
            verticalArrangement = Arrangement.Center // <--- TO KLUCZ DO CENTROWANIA PIONOWEGO
        ) {

            // ekran startowy
            if (gameState is GameState.Idle) {
                Text(
                    "Gotowy?",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(32.dp))

                if (allCommandsMap.isEmpty()) {
                    Text("Brak nagranych gestów!\nIdź do menu i nagraj coś.", textAlign = TextAlign.Center)
                } else {
                    Button(
                        onClick = {
                            val k = allCommandsMap.keys.random()
                            gameState = GameState.Countdown(k, allCommandsMap[k]!!, 2)
                        },
                        modifier = Modifier.fillMaxWidth(0.6f).height(60.dp)
                    ) {
                        Text("START", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // time
            else if (gameState is GameState.Countdown) {
                val state = gameState as GameState.Countdown

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
            else if (gameState is GameState.Recording) {
                val state = gameState as GameState.Recording

                Button(
                    onClick = {
                        gameState = GameState.Analyzing(state.commandName, state.variants)
                    },
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

            // analiza, nie wiem czemu czasami dalej długo zajmuje
            else if (gameState is GameState.Analyzing) {
                Text("Analizowanie...", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp
                )
            }

            // wyniki
            else if (gameState is GameState.ShowResult) {
                val state = gameState as GameState.ShowResult
                val color = if (state.success) Color(0xFF2E7D32) else Color.Red
                val text = if (state.success) "DOSKONALE!" else "PUDŁO!"

                Text(text, fontSize = 56.sp, fontWeight = FontWeight.Black, color = color)

                Spacer(Modifier.height(24.dp))
                Text("Wynik: ${"%.1f".format(state.score)}", fontSize = 24.sp)
                Text("Limit błędu: ${state.threshold.toInt()}", fontSize = 16.sp, color = Color.Gray)
            }

            // game over
            else if (gameState is GameState.GameOver) {
                Text("KONIEC GRY", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                Spacer(Modifier.height(16.dp))
                Text("Twój wynik:", fontSize = 20.sp)
                Text("$score", fontSize = 80.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = {
                        score = 0
                        scoreSaved = false
                        gameState = GameState.Idle
                    },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                ) { Text("Zagraj Ponownie") }

                Spacer(Modifier.height(16.dp))

                if (!scoreSaved) {
                    OutlinedButton(
                        onClick = { showSaveScoreDialog = true },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) { Text("Zapisz Wynik") }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { navController.popBackStack() }) { Text("Wyjdź") }
            }
        }
    }

    // zapis
    if (showSaveScoreDialog) {
        var playerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveScoreDialog = false },
            title = { Text("Zapisz Wynik") },
            text = {
                Column {
                    Text("Wynik: $score")
                    OutlinedTextField(value = playerName, onValueChange = { playerName = it }, label = { Text("Imię") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    ScoreDataManager.addScore(context, ScoreEntry(playerName, score))
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
    } catch (e: Exception) {}
}