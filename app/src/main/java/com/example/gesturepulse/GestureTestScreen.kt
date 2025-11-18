package com.example.gesturepulse

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureTestScreen(navController: NavController, sensorHandler: SensorHandler) {
    val context = LocalContext.current

    // --- STANY ---
    var allGestures by remember { mutableStateOf(listOf<Gesture>()) }
    var selectedGestureName by remember { mutableStateOf<String?>(null) }

    // Zbiór treningowy dla wybranej nazwy gestu
    var trainingSamples by remember { mutableStateOf(listOf<Gesture>()) }

    // Suwak dokładności
    var accuracyThreshold by remember { mutableFloatStateOf(GestureRecognizer.DTW_THRESHOLD.toFloat()) }

    // Nagrywanie i Wyniki
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Wybierz gest do testowania") }

    var lastResultScore by remember { mutableDoubleStateOf(0.0) }
    var lastResultSuccess by remember { mutableStateOf<Boolean?>(null) } // null = brak, true = sukces

    // Dane tymczasowe (bufor nagrywania testowego)
    val liveAccel = remember { mutableStateListOf<SensorSample>() }
    val liveGyro = remember { mutableStateListOf<SensorSample>() }

    // --- EFEKTY ---

    // 1. Wczytaj gesty na starcie
    LaunchedEffect(Unit) {
        allGestures = GestureDataManager.loadAllGestures(context)
        if (allGestures.isNotEmpty()) {
            selectedGestureName = allGestures.first().name
        }
    }

    // 2. Aktualizuj "zestaw treningowy" gdy zmieni się wybór lub dodamy nowy gest
    LaunchedEffect(selectedGestureName, allGestures) {
        selectedGestureName?.let { name ->
            // Filtrujemy wszystkie, które mają tę samą nazwę
            trainingSamples = allGestures.filter { it.name == name }
        }
    }

    // 3. Obsługa sensora
    DisposableEffect(isRecording) {
        if (isRecording) {
            liveAccel.clear()
            liveGyro.clear()
            sensorHandler.startListening(
                onAccel = { liveAccel.add(it) },
                onGyro = { liveGyro.add(it) }
            )
            onDispose { sensorHandler.stopListening() }
        } else {
            onDispose { }
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laboratorium Gestów") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Wróć")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (allGestures.isEmpty()) {
                Text("Brak nagranych gestów. Nagraj coś w menu głównym.")
                return@Column
            }

            // --- SEKCJA 1: WYBÓR GESTU ---
            Text("Testowany Gest:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uniqueNames = allGestures.map { it.name }.distinct()
                var currentIndex = uniqueNames.indexOf(selectedGestureName).coerceAtLeast(0)

                Button(onClick = {
                    if(uniqueNames.isNotEmpty()) {
                        currentIndex = (currentIndex - 1 + uniqueNames.size) % uniqueNames.size
                        selectedGestureName = uniqueNames[currentIndex]
                        lastResultSuccess = null // Reset wyniku przy zmianie gestu
                        statusText = "Gotowy do testu: ${uniqueNames[currentIndex]}"
                    }
                }) { Text("<") }

                Text(
                    selectedGestureName ?: "Brak",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(onClick = {
                    if(uniqueNames.isNotEmpty()) {
                        currentIndex = (currentIndex + 1) % uniqueNames.size
                        selectedGestureName = uniqueNames[currentIndex]
                        lastResultSuccess = null
                        statusText = "Gotowy do testu: ${uniqueNames[currentIndex]}"
                    }
                }) { Text(">") }
            }

            Spacer(Modifier.height(16.dp))

            // Info o próbkach
            Text("Liczba wariantów w bazie: ${trainingSamples.size}", fontSize = 14.sp, color = Color.Gray)

            Spacer(Modifier.height(24.dp))

            // --- SEKCJA 2: SUWAK DOKŁADNOŚCI ---
            Text("Czułość (Próg błędu): ${accuracyThreshold.toInt()}", fontWeight = FontWeight.Bold)
            Slider(
                value = accuracyThreshold,
                onValueChange = { accuracyThreshold = it },
                valueRange = 20f..300f,
                steps = 27
            )
            Text("Im niższa wartość, tym gest musi być bardziej idealny.", fontSize = 12.sp)

            Spacer(Modifier.height(24.dp))

            // --- SEKCJA 3: PRZYCISK TESTU ---
            Text(statusText, fontSize = 18.sp, color = if(isRecording) Color.Red else MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!isRecording) {
                        // START
                        isRecording = true
                        statusText = "Nagrywanie... Wykonaj gest!"
                        lastResultSuccess = null
                    } else {
                        // STOP -> ANALIZA
                        isRecording = false
                        statusText = "Analizowanie..."

                        val score = GestureRecognizer.recognizeGesture(
                            trainingSamples, // Przekazujemy wszystkie warianty
                            liveAccel.toList(),
                            liveGyro.toList()
                        )

                        lastResultScore = score
                        val success = score < accuracyThreshold
                        lastResultSuccess = success

                        statusText = if (success) "SUKCES!" else "PORAŻKA"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if(isRecording) Icons.Default.PlayArrow else Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRecording) "STOP" else "TESTUJ (Nagraj próbkę)")
            }

            Spacer(Modifier.height(16.dp))

            // --- SEKCJA 4: WYNIK I DOUCZANIE ---
            if (lastResultSuccess != null) {
                val isSuccess = lastResultSuccess!!
                val bgColor = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                val txtColor = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if(isSuccess) "Gest Rozpoznany!" else "Gest Nierozpoznany",
                        fontWeight = FontWeight.Bold, color = txtColor, fontSize = 20.sp)

                    Text("Twój wynik (różnica): ${"%.2f".format(lastResultScore)}", fontSize = 16.sp)
                    Text("Wymagany próg: ${accuracyThreshold.toInt()}", fontSize = 14.sp)

                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))

                    Text("Czy ten ruch był poprawny?", fontWeight = FontWeight.Bold)
                    Text("Jeśli tak, dodaj go do bazy, aby system uczył się Twoich wariantów.", fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(8.dp))

                    // Przycisk "Douczania"
                    Button(
                        onClick = {
                            selectedGestureName?.let { name ->
                                // Tworzymy nowy obiekt gestu z tą samą nazwą
                                val newVariant = Gesture(
                                    name = name,
                                    accelerometerData = liveAccel.toList(),
                                    gyroscopeData = liveGyro.toList()
                                )
                                // Dodajemy do bazy
                                GestureDataManager.addGesture(context, newVariant)

                                // Odświeżamy listę w UI
                                allGestures = GestureDataManager.loadAllGestures(context)

                                Toast.makeText(context, "Dodano wariant do '$name'", Toast.LENGTH_SHORT).show()
                                lastResultSuccess = null // Ukryj wynik
                                statusText = "Baza wiedzy zaktualizowana!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("DODAJ DO BAZY WIEDZY")
                    }
                }
            }
        }
    }
}