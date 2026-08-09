/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.settings

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.PawnsManager
import com.nikhil.yt.R
import com.nikhil.yt.constants.DisableScreenshotKey
import com.nikhil.yt.constants.PauseListenHistoryKey
import com.nikhil.yt.constants.PauseSearchHistoryKey
import com.nikhil.yt.ui.component.DefaultDialog
import com.nikhil.yt.ui.component.PreferenceEntry
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val colors = MaterialTheme.colorScheme

    // ─── Existing preferences ────────────────────────────────────────────────────
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(
        key = PauseListenHistoryKey,
        defaultValue = false
    )
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(
        key = PauseSearchHistoryKey,
        defaultValue = false
    )
    val (disableScreenshot, onDisableScreenshotChange) = rememberPreference(
        key = DisableScreenshotKey,
        defaultValue = false
    )

    // ─── Dialog states ──────────────────────────────────────────────────────────
    var showClearListenHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    // ─── Accordion expanded states ─────────────────────────────────────────────
    var privacySettingsExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    var dataProtectionExpanded by remember { mutableStateOf(false) }
    var dataSharingExpanded by remember { mutableStateOf(false) }

    // ─── Bandwidth sharing state ───────────────────────────────────────────────
    val pawnsManager = PawnsManager.getInstance(context)
    var uiToggleOn by remember { mutableStateOf(false) }
    var isToggling by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf<AlertDialogConfig?>(null) }
    var showDestructiveAlert by remember { mutableStateOf<DestructiveAlertConfig?>(null) }

    // Check actual consent status on mount
    LaunchedEffect(Unit) {
        try {
            val consentGiven = withContext(Dispatchers.IO) {
                context.getSharedPreferences("pawns_prefs", Context.MODE_PRIVATE)
                    .getBoolean("consent_given", false)
            }
            uiToggleOn = consentGiven
        } catch (e: Exception) {
            uiToggleOn = false
        }
    }

    // ─── Alert dialogs ──────────────────────────────────────────────────────────
    if (showAlert != null) {
        AlertDialog(
            onDismissRequest = { showAlert = null },
            title = { Text(showAlert!!.title) },
            text = { Text(showAlert!!.message) },
            confirmButton = {
                TextButton(onClick = { showAlert = null }) {
                    Text("OK")
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface.copy(alpha = 0.7f)
        )
    }

    if (showDestructiveAlert != null) {
        AlertDialog(
            onDismissRequest = { showDestructiveAlert = null },
            title = { Text(showDestructiveAlert!!.title) },
            text = { Text(showDestructiveAlert!!.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDestructiveAlert!!.onConfirm()
                        showDestructiveAlert = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(showDestructiveAlert!!.confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveAlert = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface.copy(alpha = 0.7f)
        )
    }

    // ─── Clear history dialogs ──────────────────────────────────────────────────
    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showClearListenHistoryDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query { clearListenHistory() }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showClearSearchHistoryDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query { clearSearchHistory() }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    // ─── Bandwidth sharing toggle handler ──────────────────────────────────────
    val handleBandwidthSharingToggle = { value: Boolean ->
        if (value) {
            if (!isToggling) {
            scope.launch {
                isToggling = true
                try {
                    val apiKey = pawnsManager.getStoredApiKey() ?: ""
                    pawnsManager.initialize(apiKey)
                    pawnsManager.optIn()
                    withContext(Dispatchers.IO) {
                        context.getSharedPreferences("pawns_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("consent_given", true).apply()
                    }
                    uiToggleOn = true
                    showAlert = AlertDialogConfig(
                        title = "Bandwidth Sharing Enabled",
                        message = "Your device is now sharing idle bandwidth. You can disable this at any time."
                    )
                } catch (e: Exception) {
                    showAlert = AlertDialogConfig(
                        title = "Error",
                        message = "Failed to enable bandwidth sharing. Please try again later."
                    )
                    uiToggleOn = false
                } finally {
                    isToggling = false
                }
            }
            }
        } else {
            showDestructiveAlert = DestructiveAlertConfig(
                title = "Withdraw Consent?",
                message = "⚠️ IMPORTANT NOTICE\n\nIf you turn off this feature:\n\n• Some SDK functions will stop working\n• The background service will stop immediately\n\nYou can re-enable this feature at any time.",
                confirmText = "Turn Off",
                onConfirm = {
                    scope.launch {
                        if (isToggling) return@launch
                        isToggling = true
                        try {
                            pawnsManager.optOut()
                            withContext(Dispatchers.IO) {
                                context.getSharedPreferences("pawns_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("consent_given", false).apply()
                            }
                            uiToggleOn = false
                            showAlert = AlertDialogConfig(
                                title = "Bandwidth Sharing Disabled",
                                message = "The service has been stopped and your consent has been withdrawn."
                            )
                        } catch (e: Exception) {
                            showAlert = AlertDialogConfig(
                                title = "Error",
                                message = "Failed to disable bandwidth sharing. Please try again."
                            )
                            uiToggleOn = true
                        } finally {
                            isToggling = false
                        }
                    }
                }
            )
        }
    }

    // ─── Main UI ──────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ─── Privacy & Security Section with Accordions ──────────────────────────
        Text(
            text = "Privacy & Security",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = colors.primary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // ─── Card container ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, colors.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                // ─── Privacy Settings Accordion ──────────────────────────────────
                PrivacyAccordionSection(
                    title = "Privacy Settings",
                    icon = R.drawable.security,
                    expanded = privacySettingsExpanded,
                    onToggle = { privacySettingsExpanded = !privacySettingsExpanded },
                    colors = colors
                ) {
                    // Existing privacy settings
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.history), null) },
                        checked = pauseListenHistory,
                        onCheckedChange = onPauseListenHistoryChange,
                    )
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_listen_history)) },
                        icon = { Icon(painterResource(R.drawable.delete_history), null) },
                        onClick = { showClearListenHistoryDialog = true },
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pause_search_history)) },
                        icon = { Icon(painterResource(R.drawable.search_off), null) },
                        checked = pauseSearchHistory,
                        onCheckedChange = onPauseSearchHistoryChange,
                    )
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_search_history)) },
                        icon = { Icon(painterResource(R.drawable.clear_all), null) },
                        onClick = { showClearSearchHistoryDialog = true },
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_screenshot)) },
                        description = stringResource(R.string.disable_screenshot_desc),
                        icon = { Icon(painterResource(R.drawable.screenshot), null) },
                        checked = disableScreenshot,
                        onCheckedChange = onDisableScreenshotChange,
                    )
                }

                // ─── Security Accordion ──────────────────────────────────────────
                PrivacyAccordionSection(
                    title = "Security",
                    icon = R.drawable.security,
                    expanded = securityExpanded,
                    onToggle = { securityExpanded = !securityExpanded },
                    colors = colors
                ) {
                    SettingNavRow(
                        label = "Two-Factor Authentication",
                        sub = "Add an extra layer of security",
                        icon = R.drawable.security,
                        onClick = { /* Open 2FA */ }
                    )
                    SettingNavRow(
                        label = "Change Password",
                        icon = R.drawable.lock,
                        onClick = { /* Open change password */ }
                    )
                }

                // ─── Data Protection Accordion ───────────────────────────────────
                PrivacyAccordionSection(
                    title = "Data Protection",
                    icon = R.drawable.security,
                    expanded = dataProtectionExpanded,
                    onToggle = { dataProtectionExpanded = !dataProtectionExpanded },
                    colors = colors
                ) {
                    SettingNavRow(
                        label = "Encryption & Security",
                        sub = "Learn how your data is protected",
                        icon = R.drawable.security,
                        onClick = { /* Open encryption info */ }
                    )
                    SettingNavRow(
                        label = "Your Data Rights",
                        sub = "Access, correct, or delete your data",
                        icon = R.drawable.storage,
                        onClick = { /* Open data rights */ }
                    )
                }

                // ─── Data Sharing Accordion ──────────────────────────────────────
                PrivacyAccordionSection(
                    title = "Data Sharing",
                    icon = R.drawable.share,
                    expanded = dataSharingExpanded,
                    onToggle = { dataSharingExpanded = !dataSharingExpanded },
                    colors = colors,
                    isLast = true
                ) {
                    BandwidthSharingRow(
                        label = if (uiToggleOn) "Bandwidth Sharing (ON)" else "Bandwidth Sharing (OFF)",
                        sub = if (uiToggleOn) "Tap to disable bandwidth sharing" else "Tap to enable bandwidth sharing and earn rewards",
                        icon = R.drawable.wifi_proxy,
                        value = uiToggleOn,
                        onToggle = handleBandwidthSharingToggle,
                        isLoading = isToggling
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ─── TopAppBar ──────────────────────────────────────────────────────────────
    TopAppBar(
        title = { Text(stringResource(R.string.privacy)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

// ─── Accordion Section Component ─────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PrivacyAccordionSection(
    title: String,
    icon: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    colors: ColorScheme,
    isLast: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.expand_less else R.drawable.expand_more
                ),
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, end = 14.dp, bottom = 8.dp)
            ) {
                content()
            }
        }

        // Divider (except for last item)
        if (!isLast) {
            Divider(
                modifier = Modifier.padding(start = 14.dp),
                color = colors.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }
    }
}

// ─── Bandwidth Sharing Row ────────────────────────────────────────────────────

@Composable
fun BandwidthSharingRow(
    label: String,
    sub: String? = null,
    icon: Int,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
    isLoading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        fontSize = 11.sp,
                        color = colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.primary,
                strokeWidth = 2.dp
            )
        } else {
            Switch(
                checked = value,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary.copy(alpha = 0.5f),
                    uncheckedTrackColor = colors.onSurface.copy(alpha = 0.2f),
                    checkedThumbColor = colors.primary,
                    uncheckedThumbColor = colors.onSurface.copy(alpha = 0.4f)
                )
            )
        }
    }
}

// ─── Setting Navigation Row ──────────────────────────────────────────────────

@Composable
fun SettingNavRow(
    label: String,
    sub: String? = null,
    icon: Int,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        fontSize = 11.sp,
                        color = colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = colors.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Data classes for alerts ─────────────────────────────────────────────────

data class AlertDialogConfig(
    val title: String,
    val message: String
)

data class DestructiveAlertConfig(
    val title: String,
    val message: String,
    val confirmText: String,
    val onConfirm: () -> Unit
)
