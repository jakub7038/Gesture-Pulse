package com.example.gesturepulse

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureEditorScreen(navController: NavController, gestureName: String, sensorHandler: SensorHandler) {
    val context = LocalContext.current

    // dane
    var variants by remember { mutableStateOf(listOf<Gesture>()) }
    var currentThreshold by remember { mutableFloatStateOf(20.0f) }
    var hasChanges by remember { mutableStateOf(false) }

    // selekcja wariantów
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    var isChartExpanded by remember { mutableStateOf(true) }

    // suwaki przycinania
    var trimStartIndex by remember { mutableFloatStateOf(0f) }
    var trimEndIndex by remember { mutableFloatStateOf(100f) }
    var maxDataSize by remember { mutableFloatStateOf(100f) }

    // tester TODO: coś z nim jest nie tak, ale szzcerze nie wiem co
    var isRecordingTest by remember { mutableStateOf(false) }
    var lastTestScore by remember { mutableDoubleStateOf(0.0) }
    var lastTestSuccess by remember { mutableStateOf<Boolean?>(null) }
    val liveAccel = remember { mutableStateListOf<SensorSample>() }
    val liveGyro = remember { mutableStateListOf<SensorSample>() }

    // odświeżanie danu wariantu
    fun loadVariantData(gestures: List<Gesture>, index: Int) {
        if (gestures.isNotEmpty()) {
            val safeIndex = index.coerceIn(gestures.indices)
            if (safeIndex != index) selectedVariantIndex = safeIndex

            val g = gestures[safeIndex]
            maxDataSize = g.accelerometerData.size.toFloat().coerceAtLeast(1f)
            trimStartIndex = 0f
            trimEndIndex = maxDataSize
        }
    }

    LaunchedEffect(gestureName) {
        val all = GestureDataManager.loadAllGestures(context)
        variants = all.filter { it.name == gestureName }
        if (variants.isNotEmpty()) {
            currentThreshold = variants.first().threshold
            loadVariantData(variants, 0)
        }
    }

    LaunchedEffect(selectedVariantIndex) {
        loadVariantData(variants, selectedVariantIndex)
    }

    DisposableEffect(isRecordingTest) {
        if (isRecordingTest) {
            liveAccel.clear()
            liveGyro.clear()
            sensorHandler.startListening(onAccel = { liveAccel.add(it) }, onGyro = { liveGyro.add(it) })
            onDispose { sensorHandler.stopListening() }
        } else {
            onDispose { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edycja: $gestureName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
                },
                actions = {
                    IconButton(onClick = {
                        GestureDataManager.updateGestureThreshold(context, gestureName, currentThreshold)
                        hasChanges = false
                        Toast.makeText(context, "Zapisano!", Toast.LENGTH_SHORT).show()
                    }) {
                        val tint = if(hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Icon(Icons.Default.Check, "Zapisz Trudność", tint = tint)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (variants.isNotEmpty()) {
                val currentVariant = variants.getOrNull(selectedVariantIndex)

                // karuzela wzorców
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    // belka tytułowa
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { isChartExpanded = !isChartExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Edytor Próbek", fontWeight = FontWeight.Bold)
                            Text("${variants.size} warianty w pamięci", fontSize = 12.sp, color = Color.Gray)
                        }
                        Icon(if (isChartExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Zwiń")
                    }

                    if (isChartExpanded && currentVariant != null) {
                        Column(Modifier.padding(16.dp)) {

                            // NAWIGACJA PRÓBEK + USUWANIE
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { if (selectedVariantIndex > 0) selectedVariantIndex-- }, enabled = selectedVariantIndex > 0) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzedni")
                                }

                                Text("Wariant ${selectedVariantIndex + 1}", fontWeight = FontWeight.Bold)

                                IconButton(onClick = { if (selectedVariantIndex < variants.size - 1) selectedVariantIndex++ }, enabled = selectedVariantIndex < variants.size - 1) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następny")
                                }

                                Spacer(Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        val allGestures = GestureDataManager.loadAllGestures(context)
                                        val globalIndices = allGestures.mapIndexedNotNull { idx, g -> if (g.name == gestureName) idx else null }

                                        if (globalIndices.size > selectedVariantIndex) {
                                            val realIndex = globalIndices[selectedVariantIndex]
                                            allGestures.removeAt(realIndex)
                                            GestureDataManager.saveAllGestures(context, allGestures)

                                            variants = allGestures.filter { it.name == gestureName }
                                            if (selectedVariantIndex >= variants.size) {
                                                selectedVariantIndex = (variants.size - 1).coerceAtLeast(0)
                                            }
                                            loadVariantData(variants, selectedVariantIndex)
                                            Toast.makeText(context, "Usunięto wariant", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = variants.size > 1
                                ) {
                                    Icon(Icons.Default.Delete, "Usuń wariant", tint = if(variants.size > 1) MaterialTheme.colorScheme.error else Color.Gray)
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 8.dp))

                            // WYKRES
                            View2D(
                                accelData = currentVariant.accelerometerData,
                                gyroData = currentVariant.gyroscopeData,
                                startIdx = trimStartIndex.roundToInt(),
                                endIdx = trimEndIndex.roundToInt(),
                                modifier = Modifier.height(220.dp).fillMaxWidth().background(Color.Black.copy(0.05f))
                            )

                            Spacer(Modifier.height(16.dp))

                            Text("Początek przycięcia", fontSize = 12.sp, color = Color.Gray)
                            Slider(
                                value = trimStartIndex,
                                onValueChange = { trimStartIndex = it.coerceAtMost(trimEndIndex - 5f) },
                                valueRange = 0f..maxDataSize
                            )

                            Text("Koniec przycięcia", fontSize = 12.sp, color = Color.Gray)
                            Slider(
                                value = trimEndIndex,
                                onValueChange = { trimEndIndex = it.coerceAtLeast(trimStartIndex + 5f) },
                                valueRange = 0f..maxDataSize
                            )

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val s = trimStartIndex.toInt()
                                    val e = trimEndIndex.toInt()
                                    if (s < e && currentVariant.accelerometerData.size > e) {
                                        val newAccel = currentVariant.accelerometerData.subList(s, e)
                                        if (newAccel.isNotEmpty()) {
                                            val tStart = newAccel.first().timestamp
                                            val tEnd = newAccel.last().timestamp
                                            val newGyro = currentVariant.gyroscopeData.filter { it.timestamp in tStart..tEnd }

                                            val updatedVariant = currentVariant.copy(accelerometerData = newAccel, gyroscopeData = newGyro)
                                            val allGestures = GestureDataManager.loadAllGestures(context)
                                            val globalIndices = allGestures.mapIndexedNotNull { idx, g -> if (g.name == gestureName) idx else null }

                                            if (globalIndices.size > selectedVariantIndex) {
                                                val realIndex = globalIndices[selectedVariantIndex]
                                                allGestures[realIndex] = updatedVariant
                                                GestureDataManager.saveAllGestures(context, allGestures)
                                                variants = allGestures.filter { it.name == gestureName }
                                                loadVariantData(variants, selectedVariantIndex)
                                                Toast.makeText(context, "Przycięto i zapisano!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("ZAPISZ PRZYCIĘCIE TEGO WARIANTU")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // suwak trudności
                Text("Trudność dla '$gestureName'", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Text("${currentThreshold.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = currentThreshold,
                    onValueChange = { currentThreshold = it; hasChanges = true },
                    valueRange = 5f..100f,
                    steps = 94
                )
                Text("5 = Bardzo Trudny | 100 = Bardzo Łatwy", fontSize = 12.sp, color = Color.Gray)
                Text("(Ta wartość dotyczy tylko gestu '$gestureName')", fontSize = 10.sp, color = Color.Gray)

                Spacer(Modifier.height(24.dp))

                // tester
                Text("Tester Gestu", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = {
                        if (!isRecordingTest) {
                            isRecordingTest = true
                            lastTestSuccess = null
                        } else {
                            isRecordingTest = false
                            val score = GestureRecognizer.recognizeGesture(variants, liveAccel, liveGyro)
                            lastTestScore = score
                            lastTestSuccess = score < currentThreshold
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if(isRecordingTest) Color.Red else MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(if(isRecordingTest) Icons.Default.PlayArrow else Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(isRecordingTest) "STOP" else "SPRAWDŹ (TEST)")
                }

                if (lastTestSuccess != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(if(lastTestSuccess == true) "PASUJE (Błąd: ${lastTestScore.toInt()})" else "PUDŁO (Błąd: ${lastTestScore.toInt()})",
                        color = if(lastTestSuccess == true) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Brak danych gestu.")
            }
        }
    }
}

// wykresiki
@Composable
fun View2D(accelData: List<SensorSample>, gyroData: List<SensorSample>, startIdx: Int, endIdx: Int, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { canvasSize = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
            if (w == 0f || h == 0f) return@Canvas
            val h1 = h * 0.5f; val h2 = h * 0.5f

            fun drawSensor(data: List<SensorSample>, top: Float, height: Float) {
                if (data.isEmpty()) return
                val minV = data.minOfOrNull { min(it.x, min(it.y, it.z)) } ?: -10f
                val maxV = data.maxOfOrNull { max(it.x, max(it.y, it.z)) } ?: 10f
                val range = (maxV - minV).coerceAtLeast(0.1f)
                val stepX = w / data.size.toFloat()

                fun scale(v: Float) = top + height - ((v - minV) / range * height)
                fun path(sel: (SensorSample) -> Float): Path {
                    val p = Path().apply { moveTo(0f, scale(sel(data[0]))) }
                    for (i in 1 until data.size) p.lineTo(i * stepX, scale(sel(data[i])))
                    return p
                }

                clipRect(0f, top, w, top + height) {
                    drawPath(path { it.x }, Color.LightGray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.y }, Color.LightGray.copy(0.3f), style = Stroke(2f))
                    drawPath(path { it.z }, Color.LightGray.copy(0.3f), style = Stroke(2f))
                }

                val clipStart = startIdx * stepX
                val clipEnd = endIdx * stepX
                clipRect(clipStart, top, clipEnd, top + height) {
                    drawPath(path { it.x }, if(top==0f) Color.Red else Color(0xFFFFA500), style = Stroke(3f))
                    drawPath(path { it.y }, if(top==0f) Color.Green else Color.Cyan, style = Stroke(3f))
                    drawPath(path { it.z }, if(top==0f) Color.Blue else Color.Magenta, style = Stroke(3f))
                }
            }

            drawSensor(accelData, 0f, h1)
            drawSensor(gyroData, h1, h2)

            val stepX = w / accelData.size.toFloat()
            drawLine(Color.Black, Offset(startIdx * stepX, 0f), Offset(startIdx * stepX, h), strokeWidth = 2f)
            drawLine(Color.Black, Offset(endIdx * stepX, 0f), Offset(endIdx * stepX, h), strokeWidth = 2f)

            drawIntoCanvas {
                val paint = android.graphics.Paint().apply {
                    textSize = 32f
                    color = android.graphics.Color.DKGRAY
                    isFakeBoldText = true
                }
                it.nativeCanvas.drawText("AKCELEROMETR (Siła/Ruch)", 20f, 40f, paint)
                it.nativeCanvas.drawText("ŻYROSKOP (Obrót)", 20f, h1 + 40f, paint)
            }

            drawLine(Color.Gray, Offset(0f, h1), Offset(w, h1), strokeWidth = 1f)
        }
    }
}