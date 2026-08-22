package com.example.view

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.viewmodel.StoreViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// This is the Web Client ID (client_type: 3) already present in this project's own
// google-services.json — required by GoogleSignInOptions.requestIdToken() to get a
// verifiable ID token back, which is what the existing (already-correct)
// loginWithGoogleIdToken() backend call needs.
private const val GOOGLE_WEB_CLIENT_ID = "210511589455-90vu807op09vmokh1g9niflgid076dfd.apps.googleusercontent.com"

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
    
    var isLoading by remember { mutableStateOf(false) }

    var showTermsDialog by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showResetEmailSentDialog by remember { mutableStateOf(false) }
    var resetEmailSentTo by remember { mutableStateOf("") }

    // FEATURE FIX: "Continue with Google" previously had no working entry point at
    // all — the backend call (loginWithGoogleIdToken) already existed and was
    // correct, but the actual Google Sign-In library was never included in the
    // build (see build.gradle.kts), so there was no way to launch a real account
    // picker. Wired up here using the standard GoogleSignInClient flow.
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // User backed out of the account picker — not an error, just do nothing.
            return@rememberLauncherForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                Toast.makeText(context, "Google sign-in failed: no ID token returned.", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            isLoading = true
            viewModel.loginWithGoogleIdToken(
                idToken = idToken,
                fallbackEmail = account.email ?: "",
                fallbackName = account.displayName ?: ""
            ) { success, msg ->
                isLoading = false
                Toast.makeText(context, msg ?: if (success) "Signed in with Google!" else "Google sign-in failed.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Google sign-in failed (code ${e.statusCode}).", Toast.LENGTH_SHORT).show()
        }
    }

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
                        horizontalArrangement = Arrangement.End
                    ) {
                        // "Remember me" toggle removed — it was never actually wired to
                        // anything (the session is already kept signed in by default via
                        // Firebase Auth's normal persistence), so the switch just sat
                        // there doing nothing. Session-keeping is simply always on now.
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
                                if (success) {
                                    // Popup with clear next steps instead of a Toast that
                                    // vanishes in a couple seconds — the user needs to
                                    // actually go check their email and act on a link, not
                                    // just see a brief confirmation.
                                    resetEmailSentTo = email
                                    showResetEmailSentDialog = true
                                    authMode = "LOGIN"
                                } else {
                                    Toast.makeText(context, msg ?: "Failed to send reset email.", Toast.LENGTH_LONG).show()
                                }
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

                if (authMode != "FORGOT") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(
                            text = "  OR  ",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Divider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }

                    OutlinedButton(
                        onClick = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_google_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google",
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Continue with Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
                
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

        if (showResetEmailSentDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showResetEmailSentDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email sent",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Check Your Email",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We've sent a password reset link to $resetEmailSentTo. Open the email and tap the link to set a new password.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Don't see it? Check your Spam or Junk folder — reset emails sometimes land there.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showResetEmailSentDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
