package com.example.view

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.viewmodel.StoreViewModel

@Composable
fun AuthScreen(
    viewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var authMode by remember { mutableStateOf("LOGIN") }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }

    var showTermsDialog by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshTermsAgreements()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.auth_illustration_1783940512728),
                contentDescription = "Auth Illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                contentScale = ContentScale.Fit
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    text = when(authMode) {
                        "LOGIN" -> "Login"
                        "SIGNUP" -> "Register"
                        else -> "Reset Password"
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = when(authMode) {
                        "LOGIN" -> "Please Sign in to continue."
                        "SIGNUP" -> "Please register to login."
                        else -> "Enter your email to reset."
                    },
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                if (authMode == "SIGNUP") {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "person", tint = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("auth_name_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1E293B),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF1E293B),
                            unfocusedLabelColor = Color.Gray,
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                        )
                    )
                }
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "email", tint = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("auth_email_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1E293B),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF1E293B),
                        unfocusedLabelColor = Color.Gray,
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                    )
                )
                
                if (authMode != "FORGOT") {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "lock", tint = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "toggle password visibility",
                                    tint = if (isPasswordVisible) Color(0xFF1E293B) else Color.Gray,
                                    modifier = Modifier.scale(if (isPasswordVisible) 1.1f else 0.95f)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("auth_password_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1E293B),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = Color(0xFF1E293B),
                            unfocusedLabelColor = Color.Gray,
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                        )
                    )
                }

                if (authMode == "LOGIN") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Remember me nextime", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                            Switch(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF1E293B), 
                                    checkedTrackColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFE2E8F0),
                                    uncheckedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                        Text(
                            text = "Forgot Password?",
                            color = Color(0xFF1E293B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { authMode = "FORGOT" }
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Button(
                    onClick = {
                        if (email.isBlank() || !email.contains("@")) {
                            Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (authMode != "FORGOT" && password.length < 6) {
                            Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (authMode == "SIGNUP" && displayName.isBlank()) {
                            Toast.makeText(context, "Please enter a display name.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val performAuth = {
                            isLoading = true
                            when (authMode) {
                                "LOGIN" -> {
                                    viewModel.signInWithEmail(email, password) { success, msg ->
                                        isLoading = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "SIGNUP" -> {
                                    viewModel.signUpWithEmail(email, password, displayName) { success, msg ->
                                        isLoading = false
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            viewModel.recordTermsAgreementOnServer(
                                                explicitEmail = email,
                                                explicitName = displayName,
                                                explicitUid = viewModel.userUid.value
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (authMode == "FORGOT") {
                            isLoading = true
                            viewModel.resetUserPassword(email) { success, msg ->
                                isLoading = false
                                Toast.makeText(context, msg ?: "Completed reset dispatch.", Toast.LENGTH_LONG).show()
                                if (success) authMode = "LOGIN"
                            }
                        } else if (authMode == "LOGIN") {
                            performAuth()
                        } else {
                            val cleanEmail = email.lowercase().trim()
                            if (viewModel.hasUserAgreedToTerms(cleanEmail)) {
                                 performAuth()
                            } else {
                                 pendingAuthAction = {
                                     viewModel.markTermsAcceptedForEmail(
                                         email = cleanEmail,
                                         name = displayName
                                     )
                                     performAuth()
                                 }
                                 showTermsDialog = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_submit_btn")
                ) {
                    Text(
                        text = when (authMode) {
                            "LOGIN" -> "Sign In"
                            "SIGNUP" -> "Sign Up"
                            else -> "Reset Password"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (authMode == "FORGOT") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Back to Sign In",
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { authMode = "LOGIN" }.padding(4.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (authMode == "LOGIN") "Don't have account? " else "Already have account? ",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (authMode == "LOGIN") "Sign Up" else "Sign In",
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                authMode = if (authMode == "LOGIN") "SIGNUP" else "LOGIN"
                            }
                        )
                    }
                }
            }
        }
        
        if (showTermsDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                TermsAgreementDialog(
                    viewModel = viewModel,
                    onAccept = {
                        showTermsDialog = false
                        pendingAuthAction?.invoke()
                    }
                )
            }
        }
    }
}
