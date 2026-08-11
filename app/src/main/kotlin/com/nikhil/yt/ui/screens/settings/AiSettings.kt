/*
 * Velune - AI & Lyrics Translation settings.
 * Adapted from Echo Music (GPL-3.0): provider help tooltips, DeepL support,
 * and AI recommendations were intentionally left out of this phase.
 */

package com.nikhil.yt.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.AiApiKeyKey
import com.nikhil.yt.constants.AiAutoTranslateKey
import com.nikhil.yt.constants.AiBaseUrlKey
import com.nikhil.yt.constants.AiModelKey
import com.nikhil.yt.constants.AiProviderKey
import com.nikhil.yt.constants.AiTranslateLanguageKey
import com.nikhil.yt.constants.AiTranslateModeKey
import com.nikhil.yt.constants.LanguageCodeToName
import com.nikhil.yt.ui.component.EnumDialog
import com.nikhil.yt.ui.component.Material3SettingsGroup
import com.nikhil.yt.ui.component.Material3SettingsItem
import com.nikhil.yt.ui.component.TextFieldDialog
import com.nikhil.yt.utils.rememberPreference

private val AI_PROVIDERS = mapOf(
    "Z.AI" to "https://api.z.ai/api/paas/v4/chat/completions",
    "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
    "OpenAI" to "https://api.openai.com/v1/chat/completions",
    "Perplexity" to "https://api.perplexity.ai/chat/completions",
    "Claude" to "https://api.anthropic.com/v1/messages",
    "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
    "Custom" to "",
)

private val MODELS_BY_PROVIDER = mapOf(
    "Z.AI" to listOf("glm-5.2", "glm-4.7"),
    "OpenRouter" to listOf("google/gemini-2.5-flash-lite", "openai/gpt-4o-mini"),
    "OpenAI" to listOf("gpt-4o-mini", "gpt-4o"),
    "Perplexity" to listOf("sonar", "sonar-pro"),
    "Claude" to listOf("claude-3-5-haiku-latest", "claude-3-5-sonnet-latest"),
    "Gemini" to listOf("gemini-2.5-flash-lite", "gemini-2.5-flash"),
    "Custom" to listOf(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scrollState = rememberScrollState()

    var aiProvider by rememberPreference(AiProviderKey, "Z.AI")
    var aiApiKey by rememberPreference(AiApiKeyKey, "")
    var aiBaseUrl by rememberPreference(AiBaseUrlKey, AI_PROVIDERS["Z.AI"] ?: "")
    var aiModel by rememberPreference(AiModelKey, MODELS_BY_PROVIDER["Z.AI"]?.firstOrNull() ?: "")
    var translateLanguage by rememberPreference(AiTranslateLanguageKey, "en")
    var translateMode by rememberPreference(AiTranslateModeKey, "Literal")
    var autoTranslate by rememberPreference(AiAutoTranslateKey, false)

    val commonModels = MODELS_BY_PROVIDER[aiProvider] ?: listOf()

    var showProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showBaseUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomModelInput by rememberSaveable { mutableStateOf(false) }
    var showTranslateModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderDialog) {
        EnumDialog(
            onDismiss = { showProviderDialog = false },
            onSelect = {
                aiProvider = it
                aiBaseUrl = AI_PROVIDERS[it] ?: ""
                aiModel = MODELS_BY_PROVIDER[it]?.firstOrNull() ?: ""
                showProviderDialog = false
            },
            title = stringResource(R.string.ai_provider),
            current = aiProvider,
            values = AI_PROVIDERS.keys.toList(),
            valueText = { it },
        )
    }

    if (showApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_api_key)) },
            icon = { Icon(painterResource(R.drawable.lock), null) },
            initialTextFieldValue = TextFieldValue(text = aiApiKey),
            onDone = {
                aiApiKey = it
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showBaseUrlDialog && aiProvider == "Custom") {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_base_url)) },
            icon = { Icon(painterResource(R.drawable.link), null) },
            initialTextFieldValue = TextFieldValue(text = aiBaseUrl),
            onDone = {
                aiBaseUrl = it
                showBaseUrlDialog = false
            },
            onDismiss = { showBaseUrlDialog = false },
        )
    }

    if (showModelDialog) {
        EnumDialog(
            onDismiss = { showModelDialog = false },
            onSelect = {
                if (it == "custom_input") {
                    showCustomModelInput = true
                    showModelDialog = false
                } else {
                    aiModel = it
                    showModelDialog = false
                }
            },
            title = stringResource(R.string.ai_model),
            current = if (aiModel in commonModels) aiModel else "custom_input",
            values = commonModels + "custom_input",
            valueText = { if (it == "custom_input") "Custom" else it },
        )
    }

    if (showCustomModelInput) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_model)) },
            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
            initialTextFieldValue = TextFieldValue(text = aiModel),
            onDone = {
                aiModel = it
                showCustomModelInput = false
            },
            onDismiss = { showCustomModelInput = false },
        )
    }

    if (showTranslateModeDialog) {
        EnumDialog(
            onDismiss = { showTranslateModeDialog = false },
            onSelect = {
                translateMode = it
                showTranslateModeDialog = false
            },
            title = stringResource(R.string.ai_translation_mode),
            current = translateMode,
            values = listOf("Literal", "Transcribed"),
            valueText = {
                when (it) {
                    "Literal" -> stringResource(R.string.ai_translation_literal)
                    "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                    else -> it
                }
            },
        )
    }

    if (showLanguageDialog) {
        EnumDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                translateLanguage = it
                showLanguageDialog = false
            },
            title = stringResource(R.string.ai_target_language),
            current = translateLanguage,
            values = LanguageCodeToName.keys.sortedBy { LanguageCodeToName[it] },
            valueText = { LanguageCodeToName[it] ?: it },
        )
    }

    Column(
        androidx.compose.ui.Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            androidx.compose.ui.Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.ai_provider),
            items = listOfNotNull(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.explore_outlined),
                    title = { Text(stringResource(R.string.ai_provider)) },
                    description = { Text(aiProvider) },
                    onClick = { showProviderDialog = true },
                ),
                if (aiProvider == "Custom") {
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.link),
                        title = { Text(stringResource(R.string.ai_base_url)) },
                        description = { Text(aiBaseUrl.ifBlank { stringResource(R.string.not_set) }) },
                        onClick = { showBaseUrlDialog = true },
                    )
                } else null,
            ),
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.ai_setup_guide),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lock),
                    title = { Text(stringResource(R.string.ai_api_key)) },
                    description = {
                        Text(
                            if (aiApiKey.isNotEmpty())
                                "•".repeat(minOf(aiApiKey.length, 8))
                            else
                                stringResource(R.string.not_set)
                        )
                    },
                    onClick = { showApiKeyDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.discover_tune),
                    title = { Text(stringResource(R.string.ai_model)) },
                    description = { Text(aiModel.ifBlank { stringResource(R.string.not_set) }) },
                    onClick = { showModelDialog = true },
                ),
            ),
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.ai_translation_mode),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_translation_mode)) },
                    description = {
                        Text(
                            when (translateMode) {
                                "Literal" -> stringResource(R.string.ai_translation_literal)
                                "Transcribed" -> stringResource(R.string.ai_translation_transcribed)
                                else -> translateMode
                            }
                        )
                    },
                    onClick = { showTranslateModeDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.ai_target_language)) },
                    description = { Text(LanguageCodeToName[translateLanguage] ?: translateLanguage) },
                    onClick = { showLanguageDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_auto_translate)) },
                    description = { Text(stringResource(R.string.ai_auto_translate_desc)) },
                    trailingContent = {
                        Switch(checked = autoTranslate, onCheckedChange = { autoTranslate = it })
                    },
                    onClick = { autoTranslate = !autoTranslate },
                ),
            ),
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        Spacer(androidx.compose.ui.Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_lyrics_translation)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
