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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun GestureRecordingScreen(
    navController: NavController,
    sensorHandler: SensorHandler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var gestureName by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Naciśnij 'Start', aby nagrać gest") }

    // Dwie oddzielne listy na dane
    val accelData = remember { mutableStateListOf<SensorSample>() }
    val gyroData = remember { mutableStateListOf<SensorSample>() }

    DisposableEffect(isRecording) {
        if (isRecording) {
            // Przekaż oba callbacki do handlera
            sensorHandler.startListening(
                onAccel = { data ->
                    accelData.add(data)
                    // Zaktualizuj status o rozmiar obu list
                    statusText = "Nagrywanie... (A: ${accelData.size}, G: ${gyroData.size})"
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

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Nagrywanie Nowego Gestu")
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = gestureName,
            onValueChange = { gestureName = it },
            label = { Text("Nazwa gestu (np. Wymach)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = statusText)
        Spacer(modifier = Modifier.height(16.dp))

        // Przycisk Start
        Button(
            onClick = {
                if (gestureName.isBlank()) {
                    Toast.makeText(context, "Najpierw podaj nazwę gestu!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val gestures = GestureDataManager.loadAllGestures(context)
                if (gestures.any { it.name == gestureName }) {
                    Toast.makeText(context, "Gest o tej nazwie już istnieje!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (!isRecording) {
                    accelData.clear()
                    gyroData.clear()
                    isRecording = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRecording
        ) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Przycisk Stop / Zapisz
        Button(
            onClick = {
                if (isRecording) {
                    isRecording = false
                    statusText = "Nagrywanie zatrzymane. Przechodzenie do edytora..."

                    saveNewGesture(context, gestureName, accelData.toList(), gyroData.toList())

                    Toast.makeText(context, "Gest '$gestureName' zapisany!", Toast.LENGTH_SHORT).show()

                    navController.navigate("editor/$gestureName") {
                        popUpTo("menu")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isRecording
        ) {
            Text("Stop i Przejdź do Edytora")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Anuluj")
        }
    }
}

/**
 * Logika zapisu nowego gestu.
 */
private fun saveNewGesture(
    context: Context,
    name: String,
    accel: List<SensorSample>,
    gyro: List<SensorSample>
) {
    val newGesture = Gesture(
        name = name,
        accelerometerData = accel,
        gyroscopeData = gyro
    )

    val allGestures = GestureDataManager.loadAllGestures(context)
    allGestures.add(newGesture)
    GestureDataManager.saveAllGestures(context, allGestures)
}