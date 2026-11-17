package com.lnxv.player.core.model

/** Audio/subtitle choices remembered for one video. */
data class RememberedTracks(
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
    val subtitlesOff: Boolean = false
) {
    val isEmpty: Boolean get() = audioTrackId == null && subtitleTrackId == null && !subtitlesOff

    /** The saved audio track if it is present and not already selected; null otherwise. */
    fun audioToApply(tracks: List<AudioTrackInfo>): AudioTrackInfo? =
        audioTrackId?.let { id -> tracks.firstOrNull { it.id == id && !it.isSelected } }

    fun subtitleToApply(tracks: List<SubtitleTrackInfo>): SubtitleTrackInfo? =
        if (subtitlesOff) null else subtitleTrackId?.let { id -> tracks.firstOrNull { it.id == id && !it.isSelected } }

    /** True when the saved choices can be resolved against [audio]/[subtitles] (so the pending restore can stop waiting). */
    fun isResolvable(audio: List<AudioTrackInfo>, subtitles: List<SubtitleTrackInfo>): Boolean =
        (audioTrackId == null || audio.any { it.id == audioTrackId }) &&
            (subtitlesOff || subtitleTrackId == null || subtitles.any { it.id == subtitleTrackId })
}
