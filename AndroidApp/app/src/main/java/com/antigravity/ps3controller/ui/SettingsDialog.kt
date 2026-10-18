package com.antigravity.ps3controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(
    initialHost: String,
    initialPort: Int,
    initialDeadZone: Float,
    initialSensitivity: Float,
    initialHaptics: Boolean,
    onDismiss: () -> Unit,
    onSave: (host: String, port: Int, deadZone: Float, sensitivity: Float, haptics: Boolean) -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var deadZone by remember { mutableFloatStateOf(initialDeadZone) }
    var sensitivity by remember { mutableFloatStateOf(initialSensitivity) }
    var haptics by remember { mutableStateOf(initialHaptics) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF1B1E24), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Controller & Server Settings",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Mac IP Address", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF374151)
                    )
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF374151)
                    )
                )
            }

            // Dead Zone Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stick Dead Zone", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    Text("${(deadZone * 100).toInt()}%", color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = deadZone,
                    onValueChange = { deadZone = it },
                    valueRange = 0.01f..0.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3B82F6),
                        activeTrackColor = Color(0xFF3B82F6),
                        inactiveTrackColor = Color(0xFF374151)
                    )
                )
            }

            // Sensitivity Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stick Curve / Sensitivity", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    Text(String.format("%.1fx", sensitivity), color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3B82F6),
                        activeTrackColor = Color(0xFF3B82F6),
                        inactiveTrackColor = Color(0xFF374151)
                    )
                )
            }

            // Haptics Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vibration / Haptic Feedback", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                Switch(
                    checked = haptics,
                    onCheckedChange = { haptics = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6)
                    )
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull() ?: 6000
                        onSave(host.trim(), parsedPort, deadZone, sensitivity, haptics)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("Save & Connect")
                }
            }
        }
    }
}
