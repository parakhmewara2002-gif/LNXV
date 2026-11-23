package com.lnxv.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** JSON fixtures are hand-written from the public API documentation (no live calls, no recorded real traffic). */
class OpenSubtitlesApiTest {
    private val searchJson = """
    {"total_pages":1,"total_count":3,"per_page":60,"page":1,"data":[
      {"id":"1","type":"subtitle","attributes":{"subtitle_id":"1","language":"en","download_count":50,"hearing_impaired":false,
        "ratings":6.5,"ai_translated":false,"machine_translated":false,"release":"Movie.2020.1080p",
        "files":[{"file_id":111,"cd_number":1,"file_name":"Movie.2020.srt"}]}},
      {"id":"2","type":"subtitle","attributes":{"subtitle_id":"2","language":"en","download_count":10,"hearing_impaired":true,
        "ratings":9.0,"moviehash_match":true,"ai_translated":false,"machine_translated":false,"release":"Movie.2020.WEB",
        "files":[{"file_id":222,"file_name":"Movie.WEB.srt"}]}},
      {"id":"3","type":"subtitle","attributes":{"language":"hi","download_count":500,"ratings":9.5,"ai_translated":true,"release":"x",
        "files":[{"file_id":333,"file_name":"x.srt"}]}},
      {"id":"4","type":"subtitle","attributes":{"language":"en","files":[]}},
      {"id":"5","type":"subtitle","attributes":{"language":"en","files":[{"file_id":111,"file_name":"dup.srt"}]}}
    ]}"""

    @Test fun `parse search skips entries without files and keeps flags`() {
        val r = OpenSubtitlesApi.parseSearch(searchJson)
        assertEquals(listOf(111L, 222L, 333L, 111L), r.map { it.fileId })
        assertTrue(r[1].hashMatch); assertTrue(r[1].hearingImpaired)
        assertTrue(r[2].aiTranslated); assertEquals("hi", r[2].language)
        assertEquals(6.5, r[0].rating, 0.0)
    }

    @Test fun `ranking is hash match then rating then downloads and removes duplicates`() {
        val ranked = OpenSubtitlesApi.rank(OpenSubtitlesApi.parseSearch(searchJson))
        assertEquals(listOf(222L, 333L, 111L), ranked.map { it.fileId })
    }

    @Test fun `ties on rating fall back to download count`() {
        fun s(id: Long, rating: Double, dl: Int) = OsSubtitle(id, "", "", "en", dl, rating, false, false, false, false)
        assertEquals(listOf(2L, 1L), OpenSubtitlesApi.rank(listOf(s(1, 5.0, 10), s(2, 5.0, 99))).map { it.fileId })
    }

    @Test fun `malformed search response is rejected`() {
        try { OpenSubtitlesApi.parseSearch("not json"); fail() } catch (e: OsError) { assertEquals(OsError.BadResponse, e) }
        try { OpenSubtitlesApi.parseSearch("{}"); fail() } catch (e: OsError) { assertEquals(OsError.BadResponse, e) }
    }

    @Test fun `search url is lower case alphabetical and encoded`() {
        val url = OpenSubtitlesApi.searchUrl(
            OpenSubtitlesApi.SearchQuery(movieHash = "8E245D9679D31E12", query = "The Matrix", languages = listOf("hi", "EN"), season = 2, episode = 5)
        )
        assertEquals(
            "https://api.opensubtitles.com/api/v1/subtitles?episode_number=5&languages=en%2Chi&moviehash=8e245d9679d31e12" +
                "&moviehash_match=include&query=the+matrix&season_number=2",
            url
        )
    }

    @Test fun `no hash means no hash parameters`() {
        val url = OpenSubtitlesApi.searchUrl(OpenSubtitlesApi.SearchQuery(query = "abc"))
        assertFalse(url.contains("moviehash"))
        assertEquals("https://api.opensubtitles.com/api/v1/subtitles?query=abc", url)
    }

    @Test fun `host is restricted to opensubtitles`() {
        assertEquals("vip-api.opensubtitles.com", OpenSubtitlesApi.host("https://vip-api.opensubtitles.com/"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("evil.example.com"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host(null))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("evilopensubtitles.com.attacker.io"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("evilopensubtitles.com"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("https://api.opensubtitles.com.attacker.io"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("https://user@api.opensubtitles.com"))
        assertEquals(OpenSubtitlesApi.DEFAULT_HOST, OpenSubtitlesApi.host("http://api.opensubtitles.com"))
    }

    @Test fun `download parse with quota`() {
        val d = OpenSubtitlesApi.parseDownload("""{"link":"https://www.opensubtitles.com/download/abc/x.srt","file_name":"x.srt","requests":3,"remaining":197,"message":"ok","reset_time_utc":"2026-11-19T00:00:00.000Z"}""")
        assertEquals(197, d.remaining); assertEquals(3, d.requests); assertEquals("x.srt", d.fileName)
        assertEquals("2026-11-19T00:00:00.000Z", d.resetTimeUtc)
    }

    @Test fun `download without link is a bad response`() {
        try { OpenSubtitlesApi.parseDownload("""{"remaining":1}"""); fail() } catch (e: OsError) { assertEquals(OsError.BadResponse, e) }
    }

    @Test fun `login parse`() {
        val l = OpenSubtitlesApi.parseLogin("""{"user":{"allowed_downloads":100},"token":"abc.def","status":200,"base_url":"vip-api.opensubtitles.com"}""")
        assertEquals("abc.def", l.token); assertEquals("vip-api.opensubtitles.com", l.baseUrl)
    }

    @Test fun `bodies are valid json with the expected keys`() {
        val d = org.json.JSONObject(OpenSubtitlesApi.downloadBody(42))
        assertEquals(42L, d.getLong("file_id")); assertEquals("srt", d.getString("sub_format"))
        assertTrue(OpenSubtitlesApi.loginBody("u", "p\"q").contains("\"password\":\"p\\\"q\""))
    }

    @Test fun `status mapping`() {
        assertEquals(OsError.Unauthorized, OsError.fromStatus(401))
        assertEquals(OsError.Forbidden, OsError.fromStatus(403))
        assertEquals(OsError.QuotaExceeded, OsError.fromStatus(406))
        assertEquals(OsError.RateLimited, OsError.fromStatus(429))
        assertTrue(OsError.fromStatus(503) is OsError.Server)
        assertTrue(OsError.fromStatus(418) is OsError.Other)
    }

    @Test fun `download links must be https opensubtitles`() {
        assertTrue(OpenSubtitlesApi.isAllowedDownloadLink("https://www.opensubtitles.com/download/x"))
        assertTrue(OpenSubtitlesApi.isAllowedDownloadLink("https://dl.opensubtitles.org/a"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("http://www.opensubtitles.com/download/x"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("https://opensubtitles.com.evil.io/x"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("https://evil.io/opensubtitles.com"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("https://user@www.opensubtitles.com/download/x"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("https://www.opensubtitles.com:8443/download/x"))
        assertFalse(OpenSubtitlesApi.isAllowedDownloadLink("not a url"))
    }
}
