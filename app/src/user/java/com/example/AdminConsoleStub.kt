package com.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.AppEntity
import com.example.viewmodel.StoreViewModel

/**
 * The admin console does not exist in the user app. MainActivity still calls this from its (never reachable
 * here, because isAdmin is always false in this flavor) "Console" tab, so a do-nothing version keeps it compiling.
 * The real implementation lives in src/admin/.../AdminConsole.kt.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ConsoleTabContent(
    viewModel: StoreViewModel,
    apps: List<AppEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    userEmail: String,
    onShowAppDetails: (com.example.data.AppEntity) -> Unit = {}
) {
}

