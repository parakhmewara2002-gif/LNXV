package com.lnxv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lnxv.player.core.bookmarks.Bookmark
import com.lnxv.player.core.model.ChapterItem
import com.lnxv.player.core.model.EqPreset
import com.lnxv.player.core.model.OutputRoute
import com.lnxv.player.ui.player.AudioFxContent
import com.lnxv.player.ui.player.BookmarksContent
import com.lnxv.player.ui.player.ChaptersContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the new player panels on a dark glass-like background, checks they show and react, and writes PNGs to
 * app/build/screenshots/ for people to look at (CI uploads them). There is no golden-image comparison: that needs a
 * reviewed baseline, which has to come from the owner's device or an approved render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class PanelRenderTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        // Best effort: Robolectric image capture can time out on some setups. Behaviour is asserted separately; a failed
        // capture leaves a .FAILED.txt next to where the PNG would be so the CI artifact says why.
        try {
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            File(dir, "$name.FAILED.txt").writeText(t.toString())
        }
    }

    @Composable
    private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        Column(Modifier.background(Color(0xFF111418)).padding(16.dp), content = content)
    }

    @Test fun bookmarksPanelEmptyAndFilled() {
        var added = 0
        rule.setContent { Panel { BookmarksContent(emptyList(), { added++ }, {}, {}, {}, {}) } }
        rule.onNodeWithText("Add here").assertExists().performClick()
        assertEquals(1, added)
        rule.onNodeWithText("No bookmarks yet", substring = true).assertExists()
        shot("bookmarks_empty")

        var jumped = -1L
        rule.setContent { Panel { BookmarksContent(listOf(Bookmark(65_000), Bookmark(3_661_000, "Fight scene")), {}, { jumped = it }, {}, {}, {}) } }
        rule.onNodeWithText("1:05").assertExists().performClick()
        assertEquals(65_000L, jumped)
        rule.onNodeWithText("1:01:01  Fight scene").assertExists()
        shot("bookmarks_filled")
    }

    @Test fun chaptersPanelHighlightsAndJumps() {
        var jumped = -1L
        val items = listOf(ChapterItem(0, "Intro"), ChapterItem(60_000, "Act 1"), ChapterItem(180_000, "Act 2"))
        rule.setContent { Panel { ChaptersContent(items, 70_000, { jumped = it }) } }
        rule.onNodeWithText("Act 2").assertExists().performClick()
        assertEquals(180_000L, jumped)
        shot("chapters")
    }

    @Test fun audioFxPanelShowsPresetsAndRoute() {
        var preset: EqPreset? = null
        rule.setContent { Panel { AudioFxContent(EqPreset.FLAT, 300, 0, OutputRoute.BLUETOOTH, { preset = it }, {}, {}) } }
        rule.onNodeWithText("Saved for: Bluetooth").assertExists()
        rule.onNodeWithText("Bass boost").assertExists()
        rule.onNodeWithText("30%").assertExists()
        val first = EqPreset.values().first { it != EqPreset.FLAT }
        rule.onAllNodesWithText(first.label)[0].performClick()
        assertEquals(first, preset)
        shot("audiofx")
    }

    @Test fun everyClickableHasATextOrDescription() {
        rule.setContent { Panel { BookmarksContent(listOf(Bookmark(1_000)), {}, {}, {}, {}, {}) } }
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        for (n in nodes) {
            val cfg = n.config
            val hasText = cfg.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
            val hasDesc = cfg.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
            val childText = n.children.any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text) }
            assertTrue("clickable node without a label: ${n.id}", hasText || hasDesc || childText)
        }
    }
}
