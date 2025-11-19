package com.example.gesturepulse

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController

object SettingsManager {
    private const val PREF_NAME = "gesture_pulse_settings"
    private const val KEY_VOLUME = "volume_level"
    private const val KEY_VIBRATION = "vibration_strength"

    const val VIBRATION_OFF = 0
    const val VIBRATION_LIGHT = 1
    const val VIBRATION_STRONG = 2

    fun saveVolume(context: Context, volume: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit { putFloat(KEY_VOLUME, volume) }
    }

    fun getVolume(context: Context): Float {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getFloat(KEY_VOLUME, 1.0f)
    }

    fun saveVibrationStrength(context: Context, strength: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_VIBRATION, strength) }
    }

    fun getVibrationStrength(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_VIBRATION, VIBRATION_LIGHT)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var volume by remember { mutableFloatStateOf(SettingsManager.getVolume(context)) }
    var vibrationStrength by remember { mutableIntStateOf(SettingsManager.getVibrationStrength(context)) }

    val audioColor = MaterialTheme.colorScheme.secondary
    val vibrationColor = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USTAWIENIA", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- AUDIO ---
            SettingsSectionHeader("AUDIO", Icons.AutoMirrored.Filled.VolumeUp, audioColor)
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = audioColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(16.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        SettingsManager.saveVolume(context, it)
                    },
                    valueRange = 0f..1f,
                    steps = 9,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = audioColor,
                        activeTrackColor = audioColor,
                        activeTickColor = Color.Black,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "${(volume * 100).toInt()}%",
                    color = audioColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // --- WIBRACJE ---
            SettingsSectionHeader("MOC HAPTYKI", Icons.Default.Vibration, vibrationColor)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // KModifier.weight(1f) miejsce po równo
                VibrationButton(
                    label = "WYŁ",
                    strengthLevel = SettingsManager.VIBRATION_OFF,
                    currentSelection = vibrationStrength,
                    activeColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                ) {
                    vibrationStrength = it
                    SettingsManager.saveVibrationStrength(context, it)
                }

                VibrationButton(
                    label = "LEKKA",
                    strengthLevel = SettingsManager.VIBRATION_LIGHT,
                    currentSelection = vibrationStrength,
                    activeColor = vibrationColor,
                    modifier = Modifier.weight(1f)
                ) {
                    vibrationStrength = it
                    SettingsManager.saveVibrationStrength(context, it)
                    forceVibration(context, it)
                }

                VibrationButton(
                    label = "MOCNA",
                    strengthLevel = SettingsManager.VIBRATION_STRONG,
                    currentSelection = vibrationStrength,
                    activeColor = vibrationColor,
                    modifier = Modifier.weight(1f)
                ) {
                    vibrationStrength = it
                    SettingsManager.saveVibrationStrength(context, it)
                    forceVibration(context, it)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Wybierz poziom, aby poczuć wibrację.", fontSize = 12.sp, color = Color.DarkGray)
        }
    }
}

// --- POMOCNICZE ---
@Composable
fun SettingsSectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f).height(2.dp).background(Brush.horizontalGradient(colors = listOf(color, Color.Transparent))))
    }
}

@Composable
fun VibrationButton(
    label: String,
    strengthLevel: Int,
    currentSelection: Int,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit
) {
    val isSelected = strengthLevel == currentSelection
    OutlinedButton(
        onClick = { onClick(strengthLevel) },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (isSelected) activeColor else Color.Gray
        ),
        border = BorderStroke(1.dp, if (isSelected) activeColor else MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

fun forceVibration(context: Context, level: Int) {
    if (level == SettingsManager.VIBRATION_OFF) return

    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val vibrator = vibratorManager.defaultVibrator

    if (vibrator.hasVibrator()) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val isStrong = (level == SettingsManager.VIBRATION_STRONG)
        // Krótszy czas dla "Lekka", dłuższy dla "Mocna"
        val duration = if (isStrong) 400L else 70L
        val amplitude = if (isStrong) 255 else 100

        val effect = VibrationEffect.createOneShot(duration, amplitude)

        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, attributes)
    }
}