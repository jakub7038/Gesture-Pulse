package com.example.gesturepulse

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.*

@Composable
fun GestureEditorScreen(navController: NavController, gestureName: String) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var fullGesture by remember { mutableStateOf<Gesture?>(null) }
    var startIndex by remember { mutableFloatStateOf(0f) }
    var endIndex by remember { mutableFloatStateOf(100f) }
    var maxIndex by remember { mutableFloatStateOf(100f) }

    LaunchedEffect(gestureName) {
        try {
            val allGestures = GestureDataManager.loadAllGestures(context)
            val gestureToEdit = allGestures.find { it.name == gestureName }
            if (gestureToEdit != null) {
                fullGesture = gestureToEdit
                val dataSize = when {
                    gestureToEdit.accelerometerData.isNotEmpty() -> gestureToEdit.accelerometerData.size
                    gestureToEdit.gyroscopeData.isNotEmpty() -> gestureToEdit.gyroscopeData.size
                    else -> 100
                }
                maxIndex = max(dataSize.toFloat(), 1f)
                startIndex = 0f
                endIndex = maxIndex
            }
            isLoading = false
        } catch (e: Exception) {
            Log.e("GestureEditor", "Błąd", e)
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (fullGesture == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nie znaleziono gestu: $gestureName")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Wróć") }
            }
        }
        return
    }

    val accelData = fullGesture?.accelerometerData ?: emptyList()
    val gyroData = fullGesture?.gyroscopeData ?: emptyList()

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Edytor Gestu: $gestureName", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Text(
            "Dane: ${accelData.size} akcelerometr, ${gyroData.size} żyroskop",
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (accelData.isNotEmpty() || gyroData.isNotEmpty()) {
            // Bezpośrednie wyświetlanie View2D
            View2D(accelData, gyroData, startIndex.roundToInt(), endIndex.roundToInt(), Modifier.fillMaxWidth().height(300.dp))

            Spacer(Modifier.height(16.dp))
            Text("Początek: ${startIndex.roundToInt()}")
            Slider(value = startIndex, onValueChange = { startIndex = it.coerceAtMost(endIndex - 1f) }, valueRange = 0f..maxIndex, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Koniec: ${endIndex.roundToInt()}")
            Slider(value = endIndex, onValueChange = { endIndex = it.coerceAtLeast(startIndex + 1f) }, valueRange = 0f..maxIndex, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { navController.popBackStack() }) { Text("Anuluj") }
            Button(
                onClick = {
                    fullGesture?.let { gesture ->
                        val start = startIndex.roundToInt()
                        val end = endIndex.roundToInt()
                        val trimmedAccel = if (accelData.isNotEmpty()) {
                            val s = start.coerceIn(0, accelData.size)
                            val e = end.coerceIn(s + 1, accelData.size)
                            accelData.subList(s, e)
                        } else emptyList()
                        val trimmedGyro = if (gyroData.isNotEmpty() && trimmedAccel.isNotEmpty()) {
                            val st = trimmedAccel.first().timestamp
                            val et = trimmedAccel.last().timestamp
                            gyroData.filter { it.timestamp in st..et }
                        } else if (gyroData.isNotEmpty()) {
                            val s = start.coerceIn(0, gyroData.size)
                            val e = end.coerceIn(s + 1, gyroData.size)
                            gyroData.subList(s, e)
                        } else emptyList()
                        GestureDataManager.updateGesture(context, gesture.copy(accelerometerData = trimmedAccel, gyroscopeData = trimmedGyro))
                        navController.popBackStack()
                    }
                },
                enabled = fullGesture != null && (accelData.isNotEmpty() || gyroData.isNotEmpty())
            ) { Text("Zapisz") }
        }
    }
}

@Composable
fun View2D(accelData: List<SensorSample>, gyroData: List<SensorSample>, startIdx: Int, endIdx: Int, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { canvasSize = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            if (w == 0f || h == 0f) return@Canvas

            val h1 = h * 0.5f
            val h2 = h * 0.5f

            // Akcelerometr
            if (accelData.isNotEmpty()) {
                val minV = accelData.minOfOrNull { min(it.x, min(it.y, it.z)) } ?: -10f
                val maxV = accelData.maxOfOrNull { max(it.x, max(it.y, it.z)) } ?: 10f
                val range = (maxV - minV).coerceAtLeast(0.1f)
                fun scaleY1(v: Float) = h1 - ((v - minV) / range * h1)

                val stepX = w / accelData.size.toFloat()
                fun path(sel: (SensorSample) -> Float): Path {
                    val p = Path()
                    if (accelData.isEmpty()) return p
                    p.moveTo(0f, scaleY1(sel(accelData[0])))
                    for (i in 1 until accelData.size) p.lineTo(i * stepX, scaleY1(sel(accelData[i])))
                    return p
                }

                clipRect(0f, 0f, w, h1) {
                    drawPath(path { it.x }, Color.Gray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.y }, Color.Gray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.z }, Color.Gray.copy(0.3f), style = Stroke(2f))
                    clipRect(startIdx * stepX, 0f, endIdx * stepX, h1) {
                        drawPath(path { it.x }, Color.Red, style = Stroke(3f))
                        drawPath(path { it.y }, Color.Green, style = Stroke(3f))
                        drawPath(path { it.z }, Color.Blue, style = Stroke(3f))
                    }
                }

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        textSize = 30f
                        color = android.graphics.Color.WHITE
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText("Akcelerometr [m/s²]", 10f, 30f, paint)
                    paint.textSize = 24f
                    paint.color = android.graphics.Color.RED
                    canvas.nativeCanvas.drawText("X", 10f, h1 - 10f, paint)
                    paint.color = android.graphics.Color.GREEN
                    canvas.nativeCanvas.drawText("Y", 40f, h1 - 10f, paint)
                    paint.color = android.graphics.Color.BLUE
                    canvas.nativeCanvas.drawText("Z", 70f, h1 - 10f, paint)
                }
            }

            // Żyroskop
            if (gyroData.isNotEmpty()) {
                val minV = gyroData.minOfOrNull { min(it.x, min(it.y, it.z)) } ?: -10f
                val maxV = gyroData.maxOfOrNull { max(it.x, max(it.y, it.z)) } ?: 10f
                val range = (maxV - minV).coerceAtLeast(0.1f)
                fun scaleY2(v: Float) = h1 + h2 - ((v - minV) / range * h2)

                val stepX = w / gyroData.size.toFloat()
                fun path(sel: (SensorSample) -> Float): Path {
                    val p = Path()
                    if (gyroData.isEmpty()) return p
                    p.moveTo(0f, scaleY2(sel(gyroData[0])))
                    for (i in 1 until gyroData.size) p.lineTo(i * stepX, scaleY2(sel(gyroData[i])))
                    return p
                }

                clipRect(0f, h1, w, h) {
                    drawPath(path { it.x }, Color.Gray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.y }, Color.Gray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.z }, Color.Gray.copy(0.3f), style = Stroke(2f))

                    if (accelData.isNotEmpty()) {
                        val startTime = accelData.getOrNull(startIdx)?.timestamp ?: 0L
                        val endTime = accelData.getOrNull(endIdx - 1)?.timestamp ?: Long.MAX_VALUE
                        val gyroStartIdx = gyroData.indexOfFirst { it.timestamp >= startTime }
                        val gyroEndIdx = gyroData.indexOfLast { it.timestamp <= endTime } + 1

                        if (gyroStartIdx != -1 && gyroEndIdx > gyroStartIdx) {
                            clipRect(gyroStartIdx * stepX, h1, gyroEndIdx * stepX, h) {
                                drawPath(path { it.x }, Color(0xFFFFA500), style = Stroke(3f))
                                drawPath(path { it.y }, Color.Cyan, style = Stroke(3f))
                                drawPath(path { it.z }, Color.Magenta, style = Stroke(3f))
                            }
                        }
                    }
                }

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        textSize = 30f
                        color = android.graphics.Color.WHITE
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText("Żyroskop [rad/s]", 10f, h1 + 30f, paint)
                    paint.textSize = 24f
                    paint.color = android.graphics.Color.rgb(255, 165, 0)
                    canvas.nativeCanvas.drawText("X", 10f, h - 10f, paint)
                    paint.color = android.graphics.Color.CYAN
                    canvas.nativeCanvas.drawText("Y", 40f, h - 10f, paint)
                    paint.color = android.graphics.Color.MAGENTA
                    canvas.nativeCanvas.drawText("Z", 70f, h - 10f, paint)
                }
            }
        }
    }
}