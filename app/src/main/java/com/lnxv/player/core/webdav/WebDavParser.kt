package com.lnxv.player.core.webdav

import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class WebDavEntry(val name: String, val url: String, val isDir: Boolean, val sizeBytes: Long)

/** Parses a WebDAV 207 Multi-Status body (PROPFIND, Depth 1) into folder and video entries. Pure JVM so it is unit tested. */
object WebDavParser {
    private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts", "flv", "wmv", "3gp", "mpg", "mpeg", "ogv")

    fun isVideoName(name: String) = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

    private fun normPath(p: String) = p.trimEnd('/')

    /** Returns folders first, then videos, each sorted by name. Throws IllegalArgumentException on bad or unsafe XML. */
    fun parse(xml: String, requestUrl: String): List<WebDavEntry> {
        // Refuse DTDs/entities outright (XXE and billion-laughs), whatever the parser would do with them.
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) { "DTD not allowed" }
        val base = URI(requestUrl)
        require(base.scheme == "https" || base.scheme == "http") { "Unsupported WebDAV scheme" }
        require(!base.host.isNullOrEmpty()) { "WebDAV URL has no host" }
        val basePath = normPath(base.path ?: "")
        val doc = try {
            val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
            f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a WebDAV response", e)
        }
        val out = ArrayList<WebDavEntry>()
        val responses = doc.getElementsByTagNameNS("*", "response")
        for (i in 0 until responses.length) {
            val r = responses.item(i) as? Element ?: continue
            val href = r.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim() ?: continue
            val resolved = try { base.resolve(URI(href.replace(" ", "%20"))) } catch (e: Exception) { continue }
            if (resolved.scheme != base.scheme ||
                !resolved.host.equals(base.host, ignoreCase = true) ||
                resolved.port != base.port ||
                resolved.userInfo != null
            ) continue // same origin only; never follow another host, scheme or port
            val path = resolved.path ?: continue
            val normalizedPath = normPath(path)
            if (normalizedPath == basePath) continue // the folder itself
            if (basePath.isNotEmpty() && !normalizedPath.startsWith("$basePath/")) continue // stay under configured root
            val isDir = r.getElementsByTagNameNS("*", "collection").length > 0
            val size = r.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: 0L
            val name = normalizedPath.substringAfterLast('/')
            if (name.isEmpty()) continue
            if (!isDir && !isVideoName(name)) continue
            // Never copy credentials from requestUrl into returned item URLs.
            val outputPath = if (isDir && !path.endsWith("/")) "$path/" else path
            val url = URI(base.scheme, null, base.host, base.port, outputPath, null, null).toASCIIString()
            out += WebDavEntry(name, url, isDir, size)
        }
        return out.sortedWith(compareBy<WebDavEntry>({ !it.isDir }, { it.name.lowercase() }))
    }
}
