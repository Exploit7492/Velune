package com.nikhil.yt.canvas

import com.nikhil.yt.canvas.providers.AppleMusicCanvasProvider
import com.nikhil.yt.canvas.providers.ArtistVideoCanvasProvider
import com.nikhil.yt.canvas.providers.EchoMusicCanvasProvider

object CanvasProviderRegistry {

    enum class Provider {
        VELUNE,
        ECHO,
        ARTIST_VIDEO,
        APPLE_MUSIC,
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
            Provider.APPLE_MUSIC -> AppleMusicCanvasProvider.getBySongArtist(song, artist)
            Provider.AUTO -> fetchAuto(song, artist, storefront)
        }
    }

    suspend fun getByAlbumId(
        albumId: String,
        provider: Provider = Provider.AUTO,
    ): CanvasArtwork? {
        return when (provider) {
            Provider.VELUNE -> VeluneCanvas.getByAlbumId(albumId)
            else -> VeluneCanvas.getByAlbumId(albumId)
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
        // Then Artist Video
        ArtistVideoCanvasProvider.getBySongArtist(song, artist)?.let { return it }
        // Finally Apple Music
        AppleMusicCanvasProvider.getBySongArtist(song, artist)?.let { return it }
        return null
    }
}
