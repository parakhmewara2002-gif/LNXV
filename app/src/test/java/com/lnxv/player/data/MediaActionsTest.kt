package com.lnxv.player.data

import com.lnxv.player.data.mediastore.MediaActions
import org.junit.Assert.*
import org.junit.Test

class MediaActionsTest {
    @Test fun keepsExtension() = assertEquals("Holiday.mkv", MediaActions.sanitizeName("Holiday", "old.mkv"))
    @Test fun noDoubleExtension() = assertEquals("Holiday.mkv", MediaActions.sanitizeName("Holiday.mkv", "old.mkv"))
    @Test fun stripsSeparators() = assertEquals("abc.mp4", MediaActions.sanitizeName("a/b\\c", "x.mp4"))
    @Test fun blankIsNull() = assertNull(MediaActions.sanitizeName("  ../ ", "x.mp4"))
}
