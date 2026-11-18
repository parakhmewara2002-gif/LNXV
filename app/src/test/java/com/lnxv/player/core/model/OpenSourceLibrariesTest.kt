package com.lnxv.player.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLibrariesTest {
    private fun findUp(rel: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("Not found: $rel")
    }

    private fun tomlVersions(): Map<String, String> =
        findUp("gradle/libs.versions.toml").readLines()
            .map { it.trim() }
            .takeWhile { it != "[libraries]" }
            .mapNotNull { Regex("""^([A-Za-z0-9_]+)\s*=\s*"([^"]+)"""").find(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun `versions shown match libs versions toml`() {
        val toml = tomlVersions()
        for (lib in OpenSourceLibraries.ALL.filter { it.tomlKey != null }) {
            assertEquals("Version of ${lib.name}", toml[lib.tomlKey], lib.version)
        }
    }

    @Test fun `every license text asset exists and is not empty`() {
        for (type in LicenseType.values()) {
            val f = findUp("app/src/main/assets/${type.assetPath}")
            assertTrue("${type.assetPath} is empty", f.length() > 200)
        }
    }

    @Test fun `FFmpeg is listed as LGPL and dav1d as BSD`() {
        val ff = OpenSourceLibraries.ALL.first { it.name.startsWith("FFmpeg") }
        assertEquals(LicenseType.LGPL_2_1, ff.license)
        assertEquals(LicenseType.BSD_2, OpenSourceLibraries.ALL.first { it.name == "dav1d" }.license)
    }

    @Test fun `FFmpeg and dav1d versions match the pinned build`() {
        val env = findUp("decoders/tools/versions.env").readText()
        fun v(key: String) = Regex("""^$key=(.+)$""", RegexOption.MULTILINE).find(env)!!.groupValues[1].trim()
        assertEquals(v("FFMPEG_TAG"), OpenSourceLibraries.ALL.first { it.name.startsWith("FFmpeg") }.version)
        assertEquals(v("DAV1D_TAG"), OpenSourceLibraries.ALL.first { it.name == "dav1d" }.version)
        assertEquals(v("CPU_FEATURES_TAG"), OpenSourceLibraries.ALL.first { it.name == "cpu_features" }.version)
    }
}
