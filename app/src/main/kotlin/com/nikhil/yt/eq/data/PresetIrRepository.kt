package com.nikhil.yt.eq.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** One manufacturer folder in the catalog, e.g. "Sennheiser". */
data class PresetManufacturer(
    val name: String,
    /** GitHub API "contents" URL for this folder — already correctly
     *  percent-encoded by GitHub itself (handles names like "B&W"), so
     *  [PresetIrRepository.listModels] just re-fetches this directly
     *  instead of re-building a path from [name]. */
    val apiUrl: String,
)

/** One downloadable correction filter, e.g. "Sennheiser HD 600". */
data class PresetHeadphoneModel(
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

class PresetIrCatalogException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Connects the Convolution preset picker to the ASH-IR-Dataset project
 * (github.com/ShanonPearce/ASH-IR-Dataset) instead of shipping a
 * hand-built preset library — a few thousand real measured single-channel
 * headphone correction filters (HpCFs), organised by manufacturer, browsed
 * and downloaded on demand rather than bundled in the APK (the full set
 * is tens of MB across thousands of tiny files; embedding it all would
 * bloat every install for filters most users will never touch).
 *
 * Licensing: this dataset is CC BY-NC-SA 4.0 (attribution, non-commercial,
 * share-alike) — see [ATTRIBUTION_NOTICE]. That's the license on the
 * dataset itself; it does not become the license of Velune. But it does
 * mean this integration is only appropriate if Velune's own distribution
 * is non-commercial (no ads, no paid tier gating this feature, etc) — if
 * that's not the case, this needs a different, permissively-licensed
 * source instead. Worth confirming before shipping, not something this
 * patch can verify on its own.
 *
 * Each individual filter file is downloaded as-is, unmodified, directly
 * from GitHub's raw content host — nothing is redistributed through any
 * server Velune controls.
 */
class PresetIrRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    companion object {
        private const val REPO_CONTENTS_BASE =
            "https://api.github.com/repos/ShanonPearce/ASH-IR-Dataset/contents/HpCFs"

        const val ATTRIBUTION_NOTICE =
            "Headphone correction filters from the ASH-IR-Dataset by Shanon Pearce, " +
                "licensed CC BY-NC-SA 4.0."
        const val ATTRIBUTION_URL = "https://github.com/ShanonPearce/ASH-IR-Dataset"

        /**
         * Parses a GitHub "contents" API array into the manufacturer
         * subfolders only — the HpCFs folder also has a handful of loose
         * top-level files (uncategorised filters/targets) that aren't
         * part of the manufacturer browse flow, so those are skipped here
         * (`type == "dir"` filters them out) rather than mis-rendered as
         * an empty manufacturer.
         */
        internal fun parseManufacturers(json: String): List<PresetManufacturer> {
            val entries = MiniJson.parse(json).asArray()
            return entries
                .map { it.asObject() }
                .filter { it.stringOrNull("type") == "dir" }
                .map { PresetManufacturer(name = it.string("name"), apiUrl = it.string("url")) }
                .sortedBy { it.name.lowercase() }
        }

        /**
         * Parses a manufacturer subfolder's "contents" API array into
         * downloadable models — only `.wav` files with a usable
         * `download_url` (a null download_url shows up for e.g. nested
         * subfolders some manufacturers have, like AKG's loose vs
         * lettered-alternative naming; those get filtered rather than
         * producing a model entry with nothing to download).
         */
        internal fun parseModels(json: String): List<PresetHeadphoneModel> {
            val entries = MiniJson.parse(json).asArray()
            return entries
                .map { it.asObject() }
                .filter { it.stringOrNull("type") == "file" }
                .mapNotNull { obj ->
                    val name = obj.stringOrNull("name") ?: return@mapNotNull null
                    if (!name.endsWith(".wav", ignoreCase = true)) return@mapNotNull null
                    val downloadUrl = obj.stringOrNull("download_url") ?: return@mapNotNull null
                    PresetHeadphoneModel(
                        displayName = prettifyFileName(name),
                        fileName = name,
                        downloadUrl = downloadUrl,
                        sizeBytes = obj.numberOrNull("size")?.toLong() ?: 0L,
                    )
                }
                .sortedBy { it.displayName.lowercase() }
        }

        /**
         * "HpCF_Sennheiser_HD_600.wav" -> "Sennheiser HD 600"
         * "HpCF_AKG_K1000_Avg.wav" -> "AKG K1000 Avg" (alternative-measurement
         * suffix left as-is rather than guessed at — "A"/"B"/"Avg" is
         * meaningful to someone comparing measurements, not just noise).
         */
        internal fun prettifyFileName(fileName: String): String {
            var base = fileName
            if (base.endsWith(".wav", ignoreCase = true)) base = base.dropLast(4)
            if (base.startsWith("HpCF_")) base = base.removePrefix("HpCF_")
            return base.replace('_', ' ').trim()
        }

        private fun cacheKeyFor(name: String): String =
            name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }

    private fun catalogCacheDir(): File =
        File(context.cacheDir, "preset_ir_catalog").apply { mkdirs() }

    private fun fetchText(url: String, cacheFile: File, forceRefresh: Boolean): String {
        if (!forceRefresh && cacheFile.exists()) {
            return cacheFile.readText()
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // Prefer a stale cache over a hard failure — GitHub's
                // unauthenticated REST API is rate-limited (60 req/hr per
                // IP), and browsing shouldn't break just because the
                // limit was hit; only the "refresh" action should ever
                // surface that as an actual error to the user.
                if (cacheFile.exists()) return cacheFile.readText()
                throw PresetIrCatalogException(
                    "Couldn't reach the preset library (HTTP ${response.code}). " +
                        "This can happen if GitHub's rate limit was hit — try again in a bit."
                )
            }
            val body = response.body?.string()
                ?: throw PresetIrCatalogException("Empty response from the preset library")
            cacheFile.writeText(body)
            return body
        }
    }

    /** Top-level manufacturer list, e.g. "AKG", "Sennheiser", "Sony"... */
    suspend fun listManufacturers(forceRefresh: Boolean = false): List<PresetManufacturer> {
        val json = fetchText(
            REPO_CONTENTS_BASE,
            File(catalogCacheDir(), "manufacturers.json"),
            forceRefresh,
        )
        return parseManufacturers(json)
    }

    /** Headphone models available under one manufacturer. */
    suspend fun listModels(
        manufacturer: PresetManufacturer,
        forceRefresh: Boolean = false,
    ): List<PresetHeadphoneModel> {
        val json = fetchText(
            manufacturer.apiUrl,
            File(catalogCacheDir(), "models_${cacheKeyFor(manufacturer.name)}.json"),
            forceRefresh,
        )
        return parseModels(json)
    }

    /**
     * Downloads [model]'s WAV bytes to [destination]. Caller is
     * responsible for validating the result (same
     * [ImpulseResponseLoader.load] path used for a user-picked file) —
     * this only fetches bytes, it doesn't parse or trust them.
     */
    suspend fun download(model: PresetHeadphoneModel, destination: File) {
        val request = Request.Builder().url(model.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PresetIrCatalogException("Download failed (HTTP ${response.code})")
            }
            val body = response.body
                ?: throw PresetIrCatalogException("Empty response downloading ${model.displayName}")
            destination.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }
}
