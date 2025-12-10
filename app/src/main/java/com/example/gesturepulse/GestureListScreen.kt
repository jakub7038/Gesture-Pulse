package com.example.gesturepulse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import android.widget.Toast

@Composable
fun GestureListScreen(navController: NavController) {
    val context = LocalContext.current
    var commands by remember { mutableStateOf(listOf<Command>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var commandNameToDelete by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun refreshGestures() {
        commands = GestureDataManager.loadAllGestures(context)
    }

    LaunchedEffect(Unit) { refreshGestures() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Zarządzanie Gestami", fontSize = 24.sp, fontWeight = FontWeight.Bold)

            IconButton(onClick = { showResetDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Przywróć domyślne",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(commands) { command ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(command.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                            val info = when(command.type) {
                                CommandType.MOTION -> "Ruch (${command.variants.size} warianty)"
                                CommandType.MASHING -> "Klikanie (Wymagane: ${command.threshold.toInt()})"
                                CommandType.DONT_TAP -> "Refleks (Nie dotykać)"
                            }
                            Text(info, fontSize = 12.sp, color = Color.Gray)
                        }

                        val isEditable = command.type == CommandType.MOTION

                        IconButton(
                            onClick = { navController.navigate("editor/${command.name}") },
                            enabled = isEditable
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edytuj",
                                tint = if(isEditable) MaterialTheme.colorScheme.onSurface else Color.LightGray.copy(alpha=0.5f)
                            )
                        }

                        IconButton(onClick = {
                            commandNameToDelete = command.name
                            showDeleteDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && commandNameToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Usuń") },
            text = { Text("Usunąć gest '$commandNameToDelete'?") },
            confirmButton = {
                Button(onClick = {
                    GestureDataManager.deleteCommand(context, commandNameToDelete!!)
                    refreshGestures()
                    showDeleteDialog = false
                }) { Text("Usuń") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Przywróć domyślne") },
            text = {
                Text("Czy na pewno chcesz usunąć WSZYSTKIE swoje gesty i wczytać zestaw fabryczny?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        GestureDataManager.resetToDefaults(context)
                        refreshGestures()
                        showResetDialog = false
                        Toast.makeText(context, "Przywrócono gesty!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Przywróć") }
            },
            dismissButton = {
                Button(onClick = { showResetDialog = false }) { Text("Anuluj") }
            }
        )
    }
}