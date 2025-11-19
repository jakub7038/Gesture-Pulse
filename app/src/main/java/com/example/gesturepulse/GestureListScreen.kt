package com.example.gesturepulse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

@Composable
fun GestureListScreen(navController: NavController) {
    val context = LocalContext.current
    var commands by remember { mutableStateOf(listOf<Command>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var commandNameToDelete by remember { mutableStateOf<String?>(null) }

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
        Text("Zarządzanie Gestami", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(commands) { command ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(command.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Warianty: ${command.variants.size}", fontSize = 12.sp, color = Color.Gray)
                        }

                        IconButton(onClick = {
                            navController.navigate("editor/${command.name}")
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Ustawienia")
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
}