package com.example.gesturepulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                                sensorHandler = sensorHandler // Przekaż ten sam handler
                            )
                        }
                        // --- TODO: Ekran Gry ---
                    }
                }
            }
        }
    }
}

/**
 * Ekran Menu Głównego
 * Zaktualizowano, aby przyjmował NavController
 */
@Composable
fun MainMenuScreen(navController: NavController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        MenuButton(
            text = "Graj",
            onClick = { /* TODO: navController.navigate("game") */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Nagraj swój gest",
            onClick = {
                navController.navigate("record_gesture")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Rankingi",
            onClick = { /* TODO: logika */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Ustawienia",
            onClick = { /* TODO: logika */ }
        )


        Spacer(modifier = Modifier.height(16.dp))

        MenuButton(
            text = "Exit",
            onClick = { /* TODO: logika */ }
        )
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buttonModifier = modifier.fillMaxWidth()
        .padding(horizontal = 32.dp)
        .height(60.dp)

    val buttonShape = RoundedCornerShape(16.dp)

    val colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFFF5A22),
        contentColor = Color(0xFFFFFFFF)
    )

    val fontWeight = FontWeight.Bold

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        shape = buttonShape,
        colors = colors
    ) {
        Text(text, fontSize = 20.sp, fontWeight = fontWeight)
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GesturePulseTheme {
        val navController = rememberNavController()
        MainMenuScreen(navController = navController)
    }
}