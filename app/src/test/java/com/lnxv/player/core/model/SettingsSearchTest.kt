package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun emptyQueryMatchesEverything() {
        assertEquals(SettingsSection.values().toList(), SettingsSearch.sectionsFor(""))
        assertEquals(SettingsSection.values().toList(), SettingsSearch.sectionsFor("   "))
    }

    @Test fun caseInsensitiveSubstring() {
        assertTrue(SettingsSearch.matches("AMOL", SettingsSection.APPEARANCE_GLASS))
        assertFalse(SettingsSearch.matches("amoled", SettingsSection.STORAGE))
    }

    @Test fun allWordsMustMatch() {
        assertTrue(SettingsSearch.matches("colour blind", SettingsSection.ADVANCED))
        assertFalse(SettingsSearch.matches("colour vault", SettingsSection.ADVANCED))
    }

    @Test fun newFeaturesAreFindable() {
        assertTrue(SettingsSearch.sectionsFor("picture").contains(SettingsSection.ADVANCED))
        assertTrue(SettingsSearch.sectionsFor("bookmarks").contains(SettingsSection.ADVANCED))
        assertTrue(SettingsSearch.sectionsFor("opensubtitles").contains(SettingsSection.ONLINE_SUBTITLES))
        assertTrue(SettingsSearch.sectionsFor("av1").contains(SettingsSection.LICENSES))
    }

    @Test fun hindiKeywordsWork() {
        assertEquals(listOf(SettingsSection.SECURITY), SettingsSearch.sectionsFor("सुरक्षा"))
    }

    @Test fun noMatchGivesEmpty() {
        assertTrue(SettingsSearch.sectionsFor("zzzzqq").isEmpty())
    }

    @Test fun everySectionHasKeywords() {
        SettingsSection.values().forEach { assertTrue(it.name, it.keywords.size >= 3) }
    }
}
