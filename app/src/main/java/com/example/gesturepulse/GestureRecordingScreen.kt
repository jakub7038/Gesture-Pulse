package com.example.gesturepulse

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

    val minimumSteps = 3

    // Komenda budowana
    val recordedCommand = remember { mutableStateOf<Command?>(null) }

    // aktualizacja licznika przy każdej zmiane wartości
    val currentVariantCount = recordedCommand.value?.variants?.size ?: 0

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
            progress = { currentVariantCount.toFloat().coerceAtMost(minimumSteps.toFloat()) / minimumSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            "Postęp: Wariant ${currentVariantCount + 1} z minimum $minimumSteps",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Pole nazwy
        OutlinedTextField(
            value = gestureName,
            onValueChange = { gestureName = it },
            label = { Text("Nazwa gestu (np. Wymach)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = currentVariantCount == 0 && !isRecording,
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
                    currentVariantCount < minimumSteps -> "Aby algorytm działał precyzyjnie, musisz nagrać ten sam ruch minimum $minimumSteps razy. Pozostało: ${minimumSteps - currentVariantCount}."
                    currentVariantCount == minimumSteps -> "Wymagane próbki nagrane. Możesz teraz ZAPISAĆ lub dodać więcej."
                    else -> "Dodano ${currentVariantCount - minimumSteps} dodatkowych próbek. Warto dodać więcej!"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val buttonColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary

        val canSave = currentVariantCount >= minimumSteps && !isRecording

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
                } else {
                    isRecording = false

                    if (accelData.isEmpty() || gyroData.isEmpty()) {
                        Toast.makeText(context, "Brak danych! Spróbuj ponownie.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val newVariant = Gesture(
                        name = gestureName.trim(),
                        accelerometerData = accelData.toList(),
                        gyroscopeData = gyroData.toList()
                    )

                    if (currentVariantCount == 0) {
                        // inicjalizacja komendy
                        recordedCommand.value = Command(
                            name = gestureName.trim(),
                            variants = mutableListOf(newVariant)
                        )
                    } else {
                        // dodanie kolejnego wariantu
                        recordedCommand.value?.variants?.add(newVariant)
                    }

                    Toast.makeText(context, "Próbka zapisana. Dodano wariant ${currentVariantCount + 1}.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Text(
                if (isRecording) "STOP"
                else "NAGRAJ WARIANT",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // przycisk zapisania komendy
        if (canSave) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    recordedCommand.value?.let { command ->
                        GestureDataManager.addCommand(context, command)
                    }

                    Toast.makeText(context, "Gest '$gestureName' utworzony pomyślnie!", Toast.LENGTH_LONG).show()

                    navController.navigate("menu") {
                        popUpTo("menu") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text(
                    "ZAPISZ GEST ($currentVariantCount warianty)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentVariantCount > 0) {
            Text("Nie martw się o drobne różnice – to pomaga algorytmowi!", fontSize = 12.sp, color = Color.Gray)
        }
    }
}