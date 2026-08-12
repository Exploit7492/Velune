package com.nikhil.yt.ui.screens.recognition

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.R
import com.nikhil.yt.recognition.MusicRecognitionService
import com.nikhil.yt.recognition.RecognitionHistoryDataStore
import com.nikhil.yt.recognition.models.RecognitionStatus
import com.nikhil.yt.ui.utils.backToMain
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val status by MusicRecognitionService.recognitionStatus.collectAsState()
    var hasPermission by remember {
        mutableStateOf(MusicRecognitionService.hasRecordPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            scope.launch { MusicRecognitionService.recognize(context) }
        }
    }

    LaunchedEffect(status) {
        if (status is RecognitionStatus.Success) {
            RecognitionHistoryDataStore.addResult(context, (status as RecognitionStatus.Success).result)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f))
            },
            label = "recognition_status"
        ) { currentStatus ->
            when (currentStatus) {
                is RecognitionStatus.Ready -> RecognitionIdle(
                    hasPermission = hasPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onStartRecognition = {
                        if (hasPermission) {
                            scope.launch { MusicRecognitionService.recognize(context) }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                is RecognitionStatus.Listening -> RecognitionListening()
                is RecognitionStatus.Processing -> RecognitionProcessing()
                is RecognitionStatus.Success -> RecognitionSuccess(
                    result = currentStatus.result,
                    onPlay = {
                        // Navigate to search with the recognized song
                        navController.navigate("search/${currentStatus.result.title} ${currentStatus.result.artist}")
                    },
                    onHistory = {
                        navController.navigate("recognition_history")
                    },
                    onDismiss = {
                        MusicRecognitionService.reset()
                    }
                )
                is RecognitionStatus.Error -> RecognitionError(
                    message = currentStatus.message,
                    onRetry = {
                        if (hasPermission) {
                            scope.launch { MusicRecognitionService.recognize(context) }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onDismiss = {
                        MusicRecognitionService.reset()
                    }
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.recognize_music)) },
        navigationIcon = {
            com.nikhil.yt.ui.component.IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun RecognitionIdle(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartRecognition: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = if (hasPermission) onStartRecognition else onRequestPermission),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.hearing),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(if (hasPermission) R.string.tap_to_recognize else R.string.grant_mic_permission),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recognition_listening_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecognitionListening() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing animation handled by outer scale
            Icon(
                painter = painterResource(R.drawable.hearing),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.listening),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recognition_listening_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecognitionProcessing() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.graphic_eq),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.processing),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recognition_processing_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecognitionSuccess(
    result: com.nikhil.yt.recognition.models.RecognitionResult,
    onPlay: () -> Unit,
    onHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.check_circle),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = result.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.TextButton(onClick = onPlay) {
            Text(stringResource(R.string.search_and_play))
        }
        androidx.compose.material3.TextButton(onClick = onHistory) {
            Text(stringResource(R.string.view_history))
        }
        androidx.compose.material3.TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.done))
        }
    }
}

@Composable
private fun RecognitionError(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.no_match_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text(stringResource(R.string.try_again))
        }
        androidx.compose.material3.TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.done))
        }
    }
}
