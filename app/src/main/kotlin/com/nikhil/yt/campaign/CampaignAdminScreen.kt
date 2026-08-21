package com.nikhil.yt.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nikhil.yt.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Owner-only campaign management — the counterpart to the read-only,
 * anon-key `CampaignCardSection` shown to every user on Home. Gated by a
 * real Supabase Auth sign-in, not just being able to reach this screen:
 * see [CampaignAdminRepository]'s doc for why (no service-role key ever
 * ships in the app).
 *
 * Not linked from anywhere in the normal navigation flow a regular user
 * would hit — reached only via the "Manage Campaigns" entry under
 * Settings → Content, which is exactly as discoverable as any other
 * settings item. This is a personal-project admin tool, not something
 * hidden behind a secret gesture; the real gate is the sign-in, not
 * obscurity — see campaign_schema.sql's admin RLS policies for the actual
 * enforcement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignAdminScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CampaignAdminRepository(context) }
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf<Boolean?>(null) } // null = still checking
    LaunchedEffect(Unit) { signedIn = repository.isSignedIn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Campaigns") },
                navigationIcon = {
                    com.nikhil.yt.ui.component.IconButton(onClick = onBackClick, onLongClick = {}) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (signedIn == true) {
                        TextButton(onClick = {
                            scope.launch {
                                repository.signOut()
                                signedIn = false
                            }
                        }) {
                            Text("Sign out")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (signedIn) {
            null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            false -> CampaignSignInForm(
                modifier = Modifier.padding(innerPadding),
                onSignedIn = { signedIn = true },
                repository = repository,
            )
            true -> CampaignManagementList(
                modifier = Modifier.padding(innerPadding),
                repository = repository,
            )
        }
    }
}

@Composable
private fun CampaignSignInForm(
    repository: CampaignAdminRepository,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Sign in with your Supabase admin account to create or edit campaigns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    repository.signIn(email.trim(), password)
                        .onSuccess { onSignedIn() }
                        .onFailure { error = it.message ?: "Sign-in failed" }
                    loading = false
                }
            },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }
    }
}

@Composable
private fun CampaignManagementList(
    repository: CampaignAdminRepository,
    modifier: Modifier = Modifier,
) {
    var campaigns by remember { mutableStateOf<List<AdminCampaignRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingCampaign by remember { mutableStateOf<AdminCampaignRow?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        repository.fetchAllCampaigns()
            .onSuccess { campaigns = it; loadError = null }
            .onFailure { loadError = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            loadError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Couldn't load campaigns: $loadError", color = MaterialTheme.colorScheme.error)
            }
            campaigns.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No campaigns yet. Tap + to create one — just a URL and a date range to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(campaigns, key = { it.id }) { campaign ->
                    CampaignAdminRow(
                        campaign = campaign,
                        onEdit = { editingCampaign = campaign },
                        onToggleActive = {
                            scope.launch {
                                repository.setActive(campaign.id, !campaign.active)
                                reload()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteCampaign(campaign.id)
                                reload()
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) } // room for the FAB
            }
        }

        FloatingActionButton(
            onClick = { showCreateSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(painter = painterResource(R.drawable.add), contentDescription = "New campaign")
        }
    }

    if (showCreateSheet) {
        CampaignEditSheet(
            existing = null,
            onDismiss = { showCreateSheet = false },
            onSave = { input ->
                scope.launch {
                    repository.createCampaign(input)
                    showCreateSheet = false
                    reload()
                }
            },
        )
    }

    editingCampaign?.let { campaign ->
        CampaignEditSheet(
            existing = campaign,
            onDismiss = { editingCampaign = null },
            onSave = { input ->
                scope.launch {
                    repository.updateCampaign(campaign.id, input)
                    editingCampaign = null
                    reload()
                }
            },
        )
    }
}

@Composable
private fun CampaignAdminRow(
    campaign: AdminCampaignRow,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = campaign.status()
    val statusColor = when (status) {
        CampaignStatus.LIVE -> Color(0xFF4CAF50)
        CampaignStatus.SCHEDULED -> Color(0xFF2196F3)
        CampaignStatus.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
        CampaignStatus.PAUSED -> Color(0xFFFF9800)
    }

    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (campaign.certified) {
                    AssistChip(onClick = {}, label = { Text("Certified", style = MaterialTheme.typography.labelSmall) })
                    Spacer(Modifier.width(4.dp))
                }
                if (campaign.isLive) {
                    AssistChip(onClick = {}, label = { Text("Live", style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(campaign.sourceUrl, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC) }
            Text(
                "${formatter.format(campaign.startDate)} \u2192 ${formatter.format(campaign.endDate)}  \u00b7  ${campaign.playCount} plays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = onToggleActive) {
                    Text(if (campaign.active) "Pause" else "Resume")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampaignEditSheet(
    existing: AdminCampaignRow?,
    onDismiss: () -> Unit,
    onSave: (CampaignInput) -> Unit,
) {
    var sourceUrl by remember { mutableStateOf(existing?.sourceUrl ?: "") }
    var startDate by remember { mutableStateOf(existing?.startDate ?: Instant.now()) }
    var endDate by remember { mutableStateOf(existing?.endDate ?: Instant.now().plusSeconds(14 * 24 * 3600)) }
    var certified by remember { mutableStateOf(existing?.certified ?: false) }
    var isLive by remember { mutableStateOf(existing?.isLive ?: false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (existing == null) "New campaign" else "Edit campaign",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                label = { Text("YouTube URL") },
                placeholder = { Text("https://music.youtube.com/watch?v=...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Start: ${formatter.format(startDate)}")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("End: ${formatter.format(endDate)}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Certified (reviewed pick)", modifier = Modifier.weight(1f))
                Switch(checked = certified, onCheckedChange = { certified = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Live stream", modifier = Modifier.weight(1f))
                Switch(checked = isLive, onCheckedChange = { isLive = it })
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        CampaignInput(
                            sourceUrl = sourceUrl.trim(),
                            startDate = startDate,
                            endDate = endDate,
                            certified = certified,
                            isLive = isLive,
                            active = existing?.active ?: true,
                        )
                    )
                },
                enabled = sourceUrl.isNotBlank() && endDate.isAfter(startDate),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (existing == null) "Create campaign" else "Save changes")
            }
            if (endDate.isBefore(startDate) || endDate == startDate) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "End date must be after the start date.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showStartPicker) {
        InstantDatePickerDialog(
            initial = startDate,
            onDismiss = { showStartPicker = false },
            onConfirm = { startDate = it; showStartPicker = false },
        )
    }
    if (showEndPicker) {
        InstantDatePickerDialog(
            initial = endDate,
            onDismiss = { showEndPicker = false },
            onConfirm = { endDate = it; showEndPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstantDatePickerDialog(
    initial: Instant,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: initial.toEpochMilli()
                onConfirm(Instant.ofEpochMilli(millis))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
