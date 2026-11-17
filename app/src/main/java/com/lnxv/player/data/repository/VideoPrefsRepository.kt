package com.lnxv.player.data.repository

import com.lnxv.player.core.database.VideoPlaybackPrefsEntity
import com.lnxv.player.core.database.VideoPrefsDao
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.RememberedTracks

/** Per-video decoder and track memory. Callers must not pass vault videos (nothing about them is stored). */
class VideoPrefsRepository(private val dao: VideoPrefsDao) {

    data class Remembered(val decoderOverride: DecoderMode?, val tracks: RememberedTracks)

    suspend fun get(videoId: Long): Remembered? {
        val e = dao.get(videoId) ?: return null
        return Remembered(
            decoderOverride = e.decoderMode?.let { name -> DecoderMode.values().firstOrNull { it.name == name } },
            tracks = RememberedTracks(e.audioTrackId, e.subtitleTrackId, e.subtitlesOff)
        )
    }

    suspend fun setDecoder(videoId: Long, mode: DecoderMode?) = update(videoId) { it.copy(decoderMode = mode?.name) }

    suspend fun setAudioTrack(videoId: Long, trackId: String?) = update(videoId) { it.copy(audioTrackId = trackId) }

    /** [trackId] null with [off] true = subtitles switched off; a track id re-enables them. */
    suspend fun setSubtitle(videoId: Long, trackId: String?, off: Boolean) =
        update(videoId) { it.copy(subtitleTrackId = trackId, subtitlesOff = off) }

    private suspend fun update(videoId: Long, change: (VideoPlaybackPrefsEntity) -> VideoPlaybackPrefsEntity) {
        val current = dao.get(videoId) ?: VideoPlaybackPrefsEntity(videoId, null, null, null, false)
        dao.upsert(change(current))
    }
}
