package com.example.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.utils.AdminPin
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.launch

/** Change the Admin Console PIN (needs the current PIN). Admin-only; the PIN is stored hashed. */
@Composable
fun ChangeAdminPinDialog(
    viewModel: StoreViewModel,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    cardBorder: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
        focusedBorderColor = accent, unfocusedBorderColor = cardBorder, cursorColor = accent,
        focusedLabelColor = accent, unfocusedLabelColor = textSecondary
    )
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Change admin PIN", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("4–12 digits. Stored in Firebase as a one-way hash that only admins can access.", color = textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                PinField(current, "Current PIN", colors) { current = it }
                PinField(newPin, "New PIN", colors) { newPin = it }
                PinField(confirm, "Confirm new PIN", colors) { confirm = it }
                if (error.isNotBlank()) Text(error, color = Color(0xFFEF4444), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, cardBorder)) { Text("Cancel", color = textSecondary) }
                    Button(
                        onClick = {
                            error = ""
                            when {
                                !AdminPin.isValidFormat(newPin) -> error = "New PIN must be 4–12 digits."
                                newPin != confirm -> error = "New PINs don't match."
                                newPin == current -> error = "New PIN must be different."
                                else -> {
                                    busy = true
                                    scope.launch {
                                        val (status, rec) = viewModel.loadAdminPin()
                                        val currentOk = when (status) {
                                            "missing" -> true                       // nothing set yet — just create it
                                            "ok" -> rec != null && viewModel.verifyAdminPin(current, rec)
                                            else -> { error = "Couldn't reach Firebase. Check your connection."; busy = false; return@launch }
                                        }
                                        if (!currentOk) { error = "Current PIN is incorrect."; busy = false; return@launch }
                                        val saved = viewModel.saveAdminPin(newPin)
                                        busy = false
                                        if (saved) {
                                            Toast.makeText(context, "Admin PIN updated", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else error = "Couldn't save. Make sure you're signed in as admin."
                                    }
                                }
                            }
                        },
                        enabled = !busy && current.isNotBlank() && newPin.isNotBlank(),
                        modifier = Modifier.weight(1.3f).height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) { Text(if (busy) "Saving…" else "Save", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}


@Composable
private fun PinField(value: String, label: String, colors: TextFieldColors, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), colors = colors,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}
