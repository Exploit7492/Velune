/*
 * Resolves the effective AI API key: user-provided key takes priority,
 * falls back to the BuildConfig default (populated from local.properties,
 * which is gitignored — never committed).
 */

package com.nikhil.yt.lyrics

import com.nikhil.yt.BuildConfig

object AiApiKeyResolver {
    fun resolve(userProvidedKey: String): String =
        userProvidedKey.ifBlank { BuildConfig.ZAI_API_KEY }
}
