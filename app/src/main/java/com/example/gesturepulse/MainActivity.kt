package com.example.gesturepulse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gesturepulse.ui.theme.GesturePulseTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensorHandler: SensorHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorHandler = SensorHandler(applicationContext)

        enableEdgeToEdge()
        setContent {
            GesturePulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "menu") {
                        // --- Menu ---
                        composable("menu") {
                            MainMenuScreen(navController = navController)
                        }

                        // --- Nagrywanie ---
                        composable("record_gesture") {
                            GestureRecordingScreen(
                                navController = navController,
                                sensorHandler = sensorHandler
                            )
                        }

                        // --- Lista Gestów ---
                        composable("gesture_list") {
                            GestureListScreen(navController = navController)
                        }

                        // --- edytor / tester ---
                        composable(
                            route = "editor/{gestureName}",
                            arguments = listOf(
                                navArgument("gestureName") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val gestureName = backStackEntry.arguments?.getString("gestureName") ?: ""
                            GestureEditorScreen(
                                navController = navController,
                                gestureName = gestureName,
                                sensorHandler = sensorHandler
                            )
                        }

                        // --- Ekranu Gry ---
                        composable("game") {
                            GameScreen(
                                navController = navController,
                                sensorHandler = sensorHandler
                            )
                        }

                        // --- EKRAN RANKINGU ---
                        composable("rankings") {
                            RankingScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainMenuScreen(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findActivity()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Gesture Pulse",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "v1.0.0.1",
            fontSize = 16.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(48.dp))

        MenuButton(
            text = "GRAJ",
            onClick = { navController.navigate("game") },
            color = MaterialTheme.colorScheme.primary,
            textColor = Color.Black
        )

        Spacer(modifier = Modifier.height(32.dp))

        MenuButton(
            text = "Nagraj gest",
            onClick = {
                navController.navigate("record_gesture")
            },
            color = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Edytor Gestów",
            onClick = { navController.navigate("gesture_list") },
            color = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Rankingi",
            onClick = { navController.navigate("rankings") },
            color = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Ustawienia",
            onClick = { /*TODO: navController.navigate("settings")*/ },
            color = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        // WYJŚCIE (Mniej widoczne)
        MenuButton(
            text = "Wyjście",
            onClick = {
                activity?.finish()
            },
            color = Color.Transparent,
            textColor = Color.Gray
        )
    }
}

@Composable
fun MenuButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, textColor: Color = Color.Black
) {
    val buttonModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .height(60.dp)

    val border = if (color == Color.Transparent) {
        ButtonDefaults.outlinedButtonBorder(enabled = true)
    } else {
        null
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (color == Color.Transparent) Color.Transparent else color,
            contentColor = textColor
        ),
        border = border
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// Funkcja rozszerzająca do bezpiecznego pobierania Activity z Context
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}