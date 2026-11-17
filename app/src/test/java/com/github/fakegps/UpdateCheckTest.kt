package com.github.fakegps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private fun releaseJson(
        tag: String = "v2.3.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """
            [{"name":"notes.txt","browser_download_url":"https://example.test/notes.txt"},
             {"name":"FakeGPS_v2.3.0.apk","browser_download_url":"https://example.test/FakeGPS_v2.3.0.apk"}]
        """.trimIndent()
    ): String = """
        {"tag_name":"$tag",
         "html_url":"https://example.test/releases/tag/$tag",
         "body":"  Bug fixes.  ",
         "draft":$draft,
         "prerelease":$prerelease,
         "assets":$assets}
    """.trimIndent()

    @Test
    fun parsesTagApkAndNotes() {
        val release = UpdateCheck.parseRelease(releaseJson())!!
        assertEquals(Version(2, 3, 0), release.version)
        assertEquals("https://example.test/FakeGPS_v2.3.0.apk", release.apkUrl)
        assertEquals("https://example.test/releases/tag/v2.3.0", release.pageUrl)
        assertEquals("Bug fixes.", release.notes)
    }

    @Test
    fun matchesUppercaseApkExtension() {
        val json = releaseJson(
            assets = """[{"browser_download_url":"https://example.test/APP.APK"}]"""
        )
        assertEquals("https://example.test/APP.APK", UpdateCheck.parseRelease(json)!!.apkUrl)
    }

    @Test
    fun noApkAssetLeavesDownloadEmpty() {
        val json = releaseJson(assets = """[{"browser_download_url":"https://example.test/a.zip"}]""")
        val release = UpdateCheck.parseRelease(json)!!
        assertNull(release.apkUrl)
        assertEquals("https://example.test/releases/tag/v2.3.0", release.pageUrl)
    }

    @Test
    fun skipsDraftAndPrerelease() {
        assertNull(UpdateCheck.parseRelease(releaseJson(draft = true)))
        assertNull(UpdateCheck.parseRelease(releaseJson(prerelease = true)))
    }

    @Test
    fun rejectsResponsesWithoutUsableTag() {
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"nonsense"}"""))
        assertNull(UpdateCheck.parseRelease("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parseRelease("not json at all"))
    }

    @Test
    fun jsonNullFieldsNeverBecomeNullText() {
        val json = """{"tag_name":"v2.3.0","html_url":null,"body":null,"assets":null}"""
        val release = UpdateCheck.parseRelease(json)!!
        assertEquals("", release.notes)
        assertEquals(UpdateCheck.RELEASES_URL, release.pageUrl)
        assertNull(release.apkUrl)
    }

    @Test
    fun treatsNewerTagAsUpdate() {
        val release = UpdateCheck.parseRelease(releaseJson(tag = "v99.0.0"))!!
        assertTrue(release.version > Version(2, 2, 2))
    }

    @Test
    fun promptsForNewVersionsAndRepeatsAfterThreeDays() {
        val latest = Version(2, 3, 0)
        val now = 1_000_000_000_000L
        val threeDays = 3L * 24 * 60 * 60 * 1000

        assertTrue(UpdateCheck.shouldPromptFor("", 0, latest, now))
        assertTrue(UpdateCheck.shouldPromptFor("2.2.2", 0, latest, now))
        assertFalse(UpdateCheck.shouldPromptFor("2.3.0", now - 1_000, latest, now))
        assertFalse(UpdateCheck.shouldPromptFor("2.3.0", now - threeDays + 1, latest, now))
        assertTrue(UpdateCheck.shouldPromptFor("2.3.0", now - threeDays, latest, now))
    }

    @Test
    fun plainNotesStripsMarkdown() {
        val notes = "# FakeGPS 2.3.0\n\n## Fixes\n\n- **Fast** start\n* second\n`code` stays"
        assertEquals(
            "FakeGPS 2.3.0\n\nFixes\n\n• Fast start\n• second\ncode stays",
            UpdateCheck.plainNotes(notes)
        )
    }
}
