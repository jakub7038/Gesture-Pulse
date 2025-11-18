package com.example.gesturepulse

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun GestureRecordingScreen(
    navController: NavController,
    sensorHandler: SensorHandler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // stany
    var gestureName by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    // licznik próbek min 3
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 3

    // tymczasowa lista do przechowywania nagranych wariantów przed ostatecznym zapisem
    val tempRecordedGestures = remember { mutableListOf<Gesture>() }

    var statusText by remember { mutableStateOf("Podaj nazwę i naciśnij Start") }

    // bufory na dane sensora
    val accelData = remember { mutableStateListOf<SensorSample>() }
    val gyroData = remember { mutableStateListOf<SensorSample>() }

    //  obsługa sensora
    DisposableEffect(isRecording) {
        if (isRecording) {
            sensorHandler.startListening(
                onAccel = { data ->
                    accelData.add(data)
                },
                onGyro = { data ->
                    gyroData.add(data)
                }
            )
            onDispose {
                sensorHandler.stopListening()
            }
        } else {
            sensorHandler.stopListening()
            onDispose { }
        }
    }

    // UI
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kreator Nowego Gestu",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // pasek postępu
        LinearProgressIndicator(
            progress = { (currentStep - 1) / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text("Postęp: Próbka $currentStep z $totalSteps", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(24.dp))

        // Pole nazwy
        OutlinedTextField(
            value = gestureName,
            onValueChange = { gestureName = it },
            label = { Text("Nazwa gestu (np. Wymach)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = currentStep == 1 && !isRecording,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // karta instrukcji
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    isRecording -> "Ruch trwa... Wykonaj gest!"
                    currentStep == 1 -> "Aby algorytm działał precyzyjnie, musisz nagrać ten sam ruch 3 razy."
                    currentStep <= totalSteps -> "Świetnie! Teraz powtórz ten sam ruch (Próba $currentStep)."
                    else -> "Gotowe!"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // przycisk nagrywania
        val buttonColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary

        //zapisywanie gestu
        Button(
            onClick = {
                if (gestureName.isBlank()) {
                    Toast.makeText(context, "Najpierw podaj nazwę gestu!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (!isRecording) {
                    accelData.clear()
                    gyroData.clear()
                    isRecording = true
                    statusText = "Nagrywanie próbki $currentStep..."
                } else {
                    isRecording = false

                    if (accelData.isEmpty() || gyroData.isEmpty()) {
                        Toast.makeText(context, "Brak danych! Spróbuj ponownie.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val sample = Gesture(
                        name = gestureName.trim(),
                        accelerometerData = accelData.toList(),
                        gyroscopeData = gyroData.toList()
                    )
                    tempRecordedGestures.add(sample)

                    if (currentStep < totalSteps) {
                        currentStep++
                        statusText = "Przygotuj się do próbki $currentStep..."
                        Toast.makeText(context, "Próbka zapisana. Kolejna...", Toast.LENGTH_SHORT).show()
                    } else {
                        saveAllSamples(context, tempRecordedGestures)
                        Toast.makeText(context, "Gest '$gestureName' utworzony pomyślnie!", Toast.LENGTH_LONG).show()

                        navController.navigate("menu") {
                            popUpTo("menu") { inclusive = true }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Text(
                if (isRecording) "STOP"
                else if (currentStep == 1) "START (Próba 1)"
                else "START (Próba $currentStep)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentStep > 1) {
            Text("Nie martw się o drobne różnice – to pomaga algorytmowi!", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
        ) {
            Text("Anuluj")
        }
    }
}


private fun saveAllSamples(context: Context, samples: List<Gesture>) {
    samples.forEach { gesture ->
        GestureDataManager.addGesture(context, gesture)
    }
}