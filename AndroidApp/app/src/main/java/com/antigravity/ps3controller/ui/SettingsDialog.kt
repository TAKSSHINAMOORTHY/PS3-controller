package com.antigravity.ps3controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    initialButtonSensitivity: Float,
    initialHaptics: Boolean,
    onDismiss: () -> Unit,
    onEnterEditMode: () -> Unit,
    onSave: (host: String, port: Int, deadZone: Float, sensitivity: Float, buttonSensitivity: Float, haptics: Boolean) -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var deadZone by remember { mutableFloatStateOf(initialDeadZone) }
    var sensitivity by remember { mutableFloatStateOf(initialSensitivity) }
    var buttonSensitivity by remember { mutableFloatStateOf(initialButtonSensitivity) }
    var haptics by remember { mutableStateOf(initialHaptics) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .background(Color(0xFF1B1E24), RoundedCornerShape(16.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title & Edit Layout Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Controller & Server Settings",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        onDismiss()
                        onEnterEditMode()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("✏️ Edit Layout", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Host & Port Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Mac IP Address", color = Color(0xFF94A3B8), fontSize = 11.sp) },
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
                    label = { Text("Port", color = Color(0xFF94A3B8), fontSize = 11.sp) },
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

            // Row 1: Button Sensitivity & Stick Sensitivity
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Button Sensitivity", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Text(String.format("%.1fx", buttonSensitivity), color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = buttonSensitivity,
                        onValueChange = { buttonSensitivity = it },
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF374151)
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stick Sensitivity", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Text(String.format("%.1fx", sensitivity), color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            }

            // Row 2: Dead Zone & Haptics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stick Dead Zone", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Text("${(deadZone * 100).toInt()}%", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Switch(
                        checked = haptics,
                        onCheckedChange = { haptics = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF3B82F6)
                        )
                    )
                }
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedPort = portText.toIntOrNull() ?: 6000
                        onSave(host.trim(), parsedPort, deadZone, sensitivity, buttonSensitivity, haptics)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Save & Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
