package com.example.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.StoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EcosystemPolicyDialog(
    viewModel: StoreViewModel,
    isMandatoryAccept: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appPolicy by viewModel.appPolicy.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userUid by viewModel.userUid.collectAsStateWithLifecycle()
    
    val isAdmin = userRole == "admin" || userEmail.equals("davidstha900@gmail.com", ignoreCase = true) || userUid == "JN4BPhEKBBRUb5hpMdQJQmRrjiq1"

    var isEditingMode by remember { mutableStateOf(false) }
    var editedTitle by remember(appPolicy) { mutableStateOf(appPolicy.title) }
    var editedContent by remember(appPolicy) { mutableStateOf(appPolicy.content) }
    
    var isSaving by remember { mutableStateOf(false) }
    var isAgreed by remember { mutableStateOf(false) }

    val currentDate = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date()) }

    Dialog(
        onDismissRequest = {
            if (!isMandatoryAccept) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatoryAccept,
            dismissOnClickOutside = !isMandatoryAccept,
            usePlatformDefaultWidth = false // Make it full screen
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE5E7EB)) // Desk/table background
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(12.dp, RoundedCornerShape(2.dp))
                    .background(Color.White, RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(2.dp))
                    .padding(24.dp)
            ) {
                // Header (Edit button for Admins)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAdmin && !isEditingMode) {
                        IconButton(onClick = { isEditingMode = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Policy", tint = Color.Gray)
                        }
                    }
                    if (!isMandatoryAccept && !isEditingMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                }

                if (isEditingMode) {
                    // Admin Edit Mode
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Edit Document Content",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black
                        )
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            label = { Text("Document Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            label = { Text("Document Content") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editedTitle = appPolicy.title
                                editedContent = appPolicy.content
                                isEditingMode = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("CANCEL", fontFamily = FontFamily.Serif)
                        }
                        Button(
                            onClick = {
                                if (editedTitle.isBlank() || editedContent.isBlank()) {
                                    Toast.makeText(context, "Fields cannot be blank", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSaving = true
                                viewModel.saveAppPolicy(editedTitle, editedContent, userEmail) { success ->
                                    isSaving = false
                                    if (success) {
                                        isEditingMode = false
                                        Toast.makeText(context, "Document updated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to update document.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(if (isSaving) "SAVING..." else "SAVE", fontFamily = FontFamily.Serif)
                        }
                    }
                } else {
                    // Normal Document Viewer Mode
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = appPolicy.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Effective Date: $currentDate",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        )
                        
                        Text(
                            text = "Important guidelines, developer responsibilities & security standards:",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val parsedLines = appPolicy.content.lines().map { it.trim() }.filter { it.isNotBlank() }
                        val displayLines = if (parsedLines.isNotEmpty()) parsedLines else listOf(
                            "Respect intellectual property.",
                            "Do not distribute malware.",
                            "Comply with local and international laws."
                        )
                        
                        displayLines.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = Color.Black,
                                    modifier = Modifier.width(20.dp).padding(start = 4.dp)
                                )
                                Text(
                                    text = line,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = Color.Black,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (appPolicy.lastUpdated > 0L) {
                            Text(
                                text = "Last updated on record: " + android.text.format.DateFormat.format("MMMM dd, yyyy", appPolicy.lastUpdated),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isMandatoryAccept) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAgreed,
                                onCheckedChange = { isAgreed = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF10B981),
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.White
                                )
                            )
                            Text(
                                text = "I confirm that I acknowledge and adhere to these rules.",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif,
                                color = Color.Black,
                                modifier = Modifier.clickable { isAgreed = !isAgreed }.padding(start = 4.dp).weight(1f)
                            )
                        }
                        
                        Button(
                            onClick = {
                                viewModel.acceptEcosystemPolicy(userEmail)
                                onDismiss()
                                Toast.makeText(context, "Policy accepted. Thank you!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = isAgreed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White,
                                disabledContainerColor = Color.LightGray,
                                disabledContentColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "AGREE & CONTINUE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }
        }
    }
}
