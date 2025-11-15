package com.example.gesturepulse

import android.content.Context
import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Gesture(
    val name: String,
    val sensorData: List<SensorData>
)

const val GESTURE_FILE_NAME = "my_gestures.json"

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

    val gestureData = remember { mutableStateListOf<SensorData>() }

    DisposableEffect(isRecording) {
        if (isRecording) {
            sensorHandler.startListening { data ->
                gestureData.add(data)
                statusText = "Nagrywanie... (przechwycono ${gestureData.size} próbek)"
            }

            onDispose {
                sensorHandler.stopListening()
            }
        } else {
            sensorHandler.stopListening()
            onDispose { }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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

        Button(
            onClick = {
                if (gestureName.isBlank()) {
                    Toast.makeText(context, "Najpierw podaj nazwę gestu!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!isRecording) {
                    gestureData.clear()
                    isRecording = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRecording
        ) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (isRecording) {
                    isRecording = false
                    statusText = "Nagrywanie zatrzymane. Zapisywanie..."

                    saveGesture(context, gestureName, gestureData.toList())

                    Toast.makeText(context, "Gest '$gestureName' zapisany!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isRecording
        ) {
            Text("Stop i Zapisz")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Anuluj")
        }
    }
}

private fun saveGesture(context: Context, name: String, data: List<SensorData>) {
    val newGesture = Gesture(name = name, sensorData = data)

    val file = File(context.filesDir, GESTURE_FILE_NAME)

    val gestures = try {
        val content = file.readText()
        Json.decodeFromString<MutableList<Gesture>>(content)
    } catch (e: Exception) {
        mutableListOf<Gesture>()
    }

    gestures.add(newGesture)

    try {
        val jsonString = Json.encodeToString(gestures)
        file.writeText(jsonString)
    } catch (e: Exception) {
        Log.e("GestureSave", "Błąd zapisu do pliku JSON", e)
    }
}