package com.lnxv.player.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingIntentValidatorTest {
    @Test fun acceptsContentAndFileVideo() {
        assertTrue(IncomingIntentValidator.isAllowedViewData("content", "video/mp4"))
        assertTrue(IncomingIntentValidator.isAllowedViewData("FILE", "video/x-matroska"))
        assertTrue(IncomingIntentValidator.isAllowedViewData("content", null))
        assertTrue(IncomingIntentValidator.isAllowedViewData("content", "application/x-matroska"))
    }

    @Test fun rejectsOtherSchemes() {
        assertFalse(IncomingIntentValidator.isAllowedViewData("http", "video/mp4"))
        assertFalse(IncomingIntentValidator.isAllowedViewData("https", "video/mp4"))
        assertFalse(IncomingIntentValidator.isAllowedViewData("javascript", null))
        assertFalse(IncomingIntentValidator.isAllowedViewData("intent", null))
        assertFalse(IncomingIntentValidator.isAllowedViewData(null, "video/mp4"))
    }

    @Test fun rejectsNonVideoMime() {
        assertFalse(IncomingIntentValidator.isAllowedViewData("content", "text/html"))
        assertFalse(IncomingIntentValidator.isAllowedViewData("file", "application/vnd.android.package-archive"))
    }
}
