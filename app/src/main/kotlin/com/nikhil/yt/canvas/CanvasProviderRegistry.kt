package com.nikhil.yt.canvas

import com.nikhil.yt.applecanvas.AppleMusicCanvasProviderPro
import com.nikhil.yt.artistvideo.ArtistVideoCanvasProviderExtended
import com.nikhil.yt.canvas.providers.AppleMusicCanvasProvider
import com.nikhil.yt.canvas.providers.ArtistVideoCanvasProvider
import com.nikhil.yt.canvas.providers.EchoMusicCanvasProvider

object CanvasProviderRegistry {

    enum class Provider {
        VELUNE,
        ECHO,
        ARTIST_VIDEO,
        ARTIST_VIDEO_EXTENDED,
        APPLE_MUSIC,
        APPLE_MUSIC_PRO,
        AUTO,
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        provider: Provider = Provider.AUTO,
        storefront: String = "us",
    ): CanvasArtwork? {
        return when (provider) {
            Provider.VELUNE -> VeluneCanvas.getBySongArtist(song, artist, storefront)
            Provider.ECHO -> EchoMusicCanvasProvider.getBySongArtist(song, artist)
            Provider.ARTIST_VIDEO -> ArtistVideoCanvasProvider.getBySongArtist(song, artist)
            Provider.ARTIST_VIDEO_EXTENDED ->
                ArtistVideoCanvasProviderExtended.getBySongArtist(song, artist)?.toCanvasArtwork()
            Provider.APPLE_MUSIC -> AppleMusicCanvasProvider.getBySongArtist(song, artist)
            Provider.APPLE_MUSIC_PRO -> AppleMusicCanvasProviderPro.getBySongArtist(song, artist, storefront = storefront)
            Provider.AUTO -> fetchAuto(song, artist, storefront)
        }
    }

    suspend fun getByAlbumId(
        albumId: String,
        provider: Provider = Provider.AUTO,
        storefront: String = "us",
    ): CanvasArtwork? {
        return when (provider) {
            Provider.VELUNE -> VeluneCanvas.getByAlbumId(albumId)
            // Apple Music Pro is the only provider in this registry that can
            // resolve canvases purely from an album id, so AUTO tries it as
            // a second attempt after Velune's own catalog lookup.
            else -> VeluneCanvas.getByAlbumId(albumId)
                ?: AppleMusicCanvasProviderPro.getByAlbumId(albumId, storefront = storefront)
        }
    }

    suspend fun getByAlbumUrl(
        url: String,
        provider: Provider = Provider.AUTO,
    ): CanvasArtwork? {
        return when (provider) {
            Provider.VELUNE -> VeluneCanvas.getByAlbumUrl(url)
            else -> VeluneCanvas.getByAlbumUrl(url)
        }
    }

    private suspend fun fetchAuto(
        song: String,
        artist: String,
        storefront: String,
    ): CanvasArtwork? {
        // Try Velune first (existing, most reliable)
        VeluneCanvas.getBySongArtist(song, artist, storefront)?.let { return it }
        // Then Echo Music community canvas
        EchoMusicCanvasProvider.getBySongArtist(song, artist)?.let { return it }
        // Then Artist Video (fast path, song/artist only)
        ArtistVideoCanvasProvider.getBySongArtist(song, artist)?.let { return it }
        // Then Artist Video Extended (slower, but resolves via album+duration too)
        ArtistVideoCanvasProviderExtended.getBySongArtist(song, artist)
            ?.toCanvasArtwork()
            ?.let { return it }
        // Then Apple Music (fast path)
        AppleMusicCanvasProvider.getBySongArtist(song, artist)?.let { return it }
        // Finally Apple Music Pro (adds retries, token caching, multi-endpoint search)
        AppleMusicCanvasProviderPro.getBySongArtist(song, artist, storefront = storefront)?.let { return it }
        return null
    }
}

/**
 * Adapts the richer [com.nikhil.yt.artistvideo.ArtistVideoResponse] shape
 * (ported from Echo Music, carries album/duration-aware lookups) onto the
 * [CanvasArtwork] contract the rest of the registry expects. Field layout is
 * identical between the two types, so this is a lossless 1:1 mapping.
 */
private fun com.nikhil.yt.artistvideo.ArtistVideoResponse.toCanvasArtwork(): CanvasArtwork =
    CanvasArtwork(
        name = name,
        artist = artist,
        albumId = albumId,
        static = static,
        animated = animated,
        videoUrl = videoUrl,
    )
