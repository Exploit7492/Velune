package com.nikhil.yt

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

object ConsentUrls {
    const val PAWNS_PRIVACY = "https://pawns.app/privacy-policy"
    const val PAWNS_ACCEPTABLE_USE = "https://pawns.app/acceptable-use-policy"
    const val APP_PRIVACY = "https://mavinapp.com/privacy"
    const val APP_TERMS = "https://mavinapp.com/terms"
    const val APP_LEGAL = "https://mavinapp.com/legal"
}

val TAB_NAMES = listOf("General", "Privacy", "Data Protection", "Data Sharing")

@Composable
fun ConsentModal(
    visible: Boolean,
    apiKey: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    var activeTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var showFullConsent by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.94f)
                    .shadow(20.dp, RoundedCornerShape(20.dp))
                    .border(1.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top hairline
                    Divider(
                        modifier = Modifier.height(2.dp).fillMaxWidth(),
                        color = colors.primary,
                        thickness = 2.dp
                    )

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .background(colors.primary, RoundedCornerShape(2.dp))
                        )
                        Column {
                            Text(
                                text = "🎵 Streaming Music",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Review all tabs before enabling this feature",
                                fontSize = 12.sp,
                                color = colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Tab Row
                    ScrollableTabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TAB_NAMES.forEachIndexed { index, title ->
                            val isSelected = activeTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { activeTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.primary else colors.onSurface.copy(alpha = 0.6f)
                                    )
                                },
                                selectedContentColor = colors.primary,
                                unselectedContentColor = colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Tab content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        when (activeTab) {
                            0 -> GeneralTab()
                            1 -> PrivacyTab()
                            2 -> DataProtectionTab()
                            3 -> DataSharingTab()
                        }
                    }

                    Divider(color = colors.primary.copy(alpha = 0.4f), thickness = 1.dp)

                    // Footer – consent summary with "More"/"Less"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showFullConsent) {
                            Text(
                                text = "By tapping Accept you confirm you have read and agree to Netflix Pro's Privacy Policy, Terms of Service, the Pawns Privacy Policy and Acceptable Use Policy, and that you are at least 18 years of age and the primary account holder on the internet connection used by this device. ",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Less",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primary,
                                modifier = Modifier.clickable { showFullConsent = false }
                            )
                        } else {
                            Text(
                                text = "By tapping Accept you agree to Netflix Pro's Privacy Policy, Terms, and the Pawns policies, and confirm you're 18+ and the account holder on this connection. ",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = colors.onSurface
                            )
                            Text(
                                text = "More",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primary,
                                modifier = Modifier.clickable { showFullConsent = true }
                            )
                        }
                    }

                    Divider(color = colors.primary.copy(alpha = 0.4f), thickness = 1.dp)

                    // Action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedButtonDefaults.outlinedButtonColors(
                                contentColor = colors.onSurface.copy(alpha = 0.8f)
                            ),
                            border = OutlinedButtonDefaults.outlinedBorder(
                                color = colors.primary.copy(alpha = 0.5f)
                            ),
                            enabled = !isLoading
                        ) {
                            Text("⚙ Settings", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (!isLoading) {
                                    isLoading = true
                                    // Call the accept handler – it should handle the SDK init
                                    onAccept()
                                    // The parent will close the modal
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                                disabledContainerColor = colors.primary.copy(alpha = 0.5f)
                            ),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Accept", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(
                        modifier = Modifier.height(1.dp).fillMaxWidth(),
                        color = colors.primary.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

// ─── Tab content composables ─────────────────────────────────────────────────

@Composable
private fun GeneralTab() {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Welcome to Streaming Music",
                style = typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Your all-in-one music streaming destination. Discover, stream, and save tracks from emerging and established artists — all in one beautifully designed app.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "Stream & Download",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Stream millions of tracks at high quality or download them for offline listening. Your library travels with you wherever you go.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "Built for Artists & Fans",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Designed for both listeners and creators. Artists can upload tracks, release full albums, view detailed play analytics, and connect directly with their fanbase.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "Always Your Choice",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Certain optional features require your explicit consent before they activate. You are in full control at all times.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PrivacyTab() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Privacy",
                style = typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "By enabling this feature you confirm you have read and agree to the legal documentation:",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            listOf(
                "Privacy Policy" to ConsentUrls.APP_PRIVACY,
                "Terms & Conditions" to ConsentUrls.APP_TERMS,
                "Legal Notice" to ConsentUrls.APP_LEGAL
            ).forEach { (label, url) ->
                Row {
                    Text(
                        text = "• ",
                        style = typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = label,
                        style = typography.bodySmall.copy(
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.clickable { context.openLink(url) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Third-Party SDK Privacy",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "The bandwidth sharing feature uses a third-party SDK with its own independent privacy policies. Enabling this feature also means you agree to those policies.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row {
                Text(
                    text = "• ",
                    style = typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Pawns Privacy Policy",
                    style = typography.bodySmall.copy(
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.clickable { context.openLink(ConsentUrls.PAWNS_PRIVACY) }
                )
            }
            Row {
                Text(
                    text = "• ",
                    style = typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Pawns Acceptable Use Policy",
                    style = typography.bodySmall.copy(
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.clickable { context.openLink(ConsentUrls.PAWNS_ACCEPTABLE_USE) }
                )
            }
            Text(
                text = "The SDK may collect device identifiers, IP addresses, and bandwidth usage statistics as described in the Pawns Privacy Policy. The app does not receive or store this data.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DataProtectionTab() {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Encryption in Transit",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "All traffic passing through your device while the earnings feature is active is fully encrypted using industry-standard TLS protocols. The app never has access to the contents of this traffic.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "On-Device Data Security",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Your consent decision is stored securely on your device. This data never leaves your device and is never transmitted to any third party.",
                style = typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = "No Personal Data Sold",
                style = typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Your personal information is never sold, rented, or shared with advertisers 
