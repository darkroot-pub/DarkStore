package com.example.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.StoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TermsAgreementDialog(
    viewModel: StoreViewModel,
    onAccept: () -> Unit
) {
    val appPolicy by viewModel.appPolicy.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    
    val agreementPoints = remember(appPolicy.content) {
        val parsed = appPolicy.content.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (parsed.isNotEmpty()) parsed else listOf(
            "I am responsible for the apps and content I upload.",
            "I will not upload malware, viruses, spyware, ransomware, or any harmful software.",
            "I will not upload apps that infringe copyrights, trademarks, or other intellectual property rights.",
            "I will not upload illegal, deceptive, or fraudulent content.",
            "I understand that every submitted app will be reviewed by the Dark Store team before publication.",
            "I understand that Dark Store may reject or remove any app that violates these terms.",
            "I will not impersonate another person, developer, or organization.",
            "I understand that my account may be suspended or permanently banned for repeated violations.",
            "I acknowledge that I download and install apps at my own discretion and responsibility.",
            "I agree to follow all Dark Store rules and future policy updates."
        )
    }

    var isAgreed by remember { mutableStateOf(false) }
    
    val currentDate = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB)) // Desk/table background
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("terms_agreement_onboarding_root"),
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "TERMS & CONDITIONS",
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
                    text = "Please read these terms and conditions carefully before using the Dark Store application. By clicking 'I Agree' below, you signify your acceptance of this agreement.",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    color = Color.Black,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                agreementPoints.forEachIndexed { index, point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            text = point,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "By signing below (clicking 'Agree'), you acknowledge that you have read, understood, and agree to be bound by the above terms.",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    color = Color.DarkGray,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // "Signature" section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(2f).padding(end = 8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                        Text(
                            text = "Account / Identity",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = userEmail.ifBlank { "User Guest" },
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                        Text(
                            text = "Date",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = currentDate,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Accept action
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
                    text = "I have read and agree to the terms.",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    color = Color.Black,
                    modifier = Modifier.clickable { isAgreed = !isAgreed }.padding(start = 4.dp).weight(1f)
                )
            }
            
            Button(
                onClick = onAccept,
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
                    text = "SIGN & CONTINUE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}
