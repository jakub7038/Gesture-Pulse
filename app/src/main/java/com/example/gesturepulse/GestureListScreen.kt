package com.example.gesturepulse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var gestures by remember { mutableStateOf(listOf<Gesture>()) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var gestureToDelete by remember { mutableStateOf<Gesture?>(null) }

    LaunchedEffect(Unit) {
        gestures = GestureDataManager.loadAllGestures(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)) {
        Text("Moje Gesty", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gestures) { gesture ->
                GestureItemRow(
                    gesture = gesture,
                    onEdit = {
                        navController.navigate("editor/${gesture.name}")
                    },
                    onDelete = {
                        gestureToDelete = gesture
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog && gestureToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                gestureToDelete = null
            },
            title = { Text("Potwierdź Usunięcie") },
            text = { Text("Czy na pewno chcesz usunąć gest '${gestureToDelete?.name}'?") },
            confirmButton = {
                Button(onClick = {
                    gestureToDelete?.let {
                        GestureDataManager.deleteGesture(context, it.name)
                        gestures = gestures.filter { g -> g.name != it.name }
                    }
                    showDeleteDialog = false
                    gestureToDelete = null
                }) { Text("Usuń") }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    gestureToDelete = null
                }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
fun GestureItemRow(gesture: Gesture, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(gesture.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Dane: ${gesture.accelerometerData.size} (A), ${gesture.gyroscopeData.size} (G)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edytuj")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}