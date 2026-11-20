package com.lnxv.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class OsLanguagesTest {
    @Test fun `adds and removes`() {
        assertEquals(listOf("en", "hi"), OsLanguages.toggle(listOf("en"), "hi"))
        assertEquals(listOf("en"), OsLanguages.toggle(listOf("en", "hi"), "hi"))
    }
    @Test fun `last language cannot be removed`() = assertEquals(listOf("en"), OsLanguages.toggle(listOf("en"), "en"))
    @Test fun `codes are lower case and unique`() {
        val codes = OsLanguages.ALL.map { it.first }
        assertEquals(codes.map { it.lowercase() }, codes)
        assertEquals(codes.size, codes.toSet().size)
    }
}
