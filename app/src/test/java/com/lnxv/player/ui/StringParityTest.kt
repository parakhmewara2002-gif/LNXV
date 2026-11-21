package com.lnxv.player.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guards translations: every English string/plural has a Hindi one, and format placeholders match. */
class StringParityTest {
    private fun load(path: String): Map<String, String> {
        val f = File(path)
        assertTrue("missing $path (tests run from the app module)", f.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = LinkedHashMap<String, String>()
        val root = doc.documentElement.childNodes
        for (i in 0 until root.length) {
            val e = root.item(i) as? Element ?: continue
            if (e.tagName == "string" || e.tagName == "plurals" || e.tagName == "string-array") out[e.getAttribute("name")] = e.textContent
        }
        return out
    }

    private val en get() = load("src/main/res/values/strings.xml")
    private val hi get() = load("src/main/res/values-hi/strings.xml")

    @Test fun everyEnglishKeyHasHindi() {
        val missing = en.keys - hi.keys
        assertTrue("Missing Hindi strings: $missing", missing.isEmpty())
    }

    @Test fun noOrphanHindiKeys() {
        val extra = hi.keys - en.keys
        assertTrue("Hindi strings without English: $extra", extra.isEmpty())
    }

    @Test fun placeholdersMatch() {
        val re = Regex("%(\\d+\\$)?[sdf]")
        val e = en
        val h = hi
        for ((k, v) in e) {
            val hv = h[k] ?: continue
            val a = re.findAll(v).map { it.value }.toSortedSet()
            val b = re.findAll(hv).map { it.value }.toSortedSet()
            assertEquals("placeholders differ for $k", a, b)
        }
    }
}
