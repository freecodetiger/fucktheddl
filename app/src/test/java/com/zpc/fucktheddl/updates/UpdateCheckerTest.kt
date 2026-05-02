package com.zpc.fucktheddl.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun extractsLatestTagAndDetectsNewerRelease() {
        val checker = UpdateChecker(
            fetchLatestReleaseJson = { """{"tag_name":"v0.2.3","apk_url":"https://example.test/app.apk"}""" },
        )

        val result = checker.check("0.2.2")

        assertTrue(result.updateAvailable)
        assertEquals("0.2.3", result.latestVersion)
        assertEquals("https://example.test/app.apk", result.apkUrl)
    }

    @Test
    fun doesNotReportUpdateForSameOrOlderRelease() {
        assertFalse(UpdateChecker { """{"tag_name":"v0.2.2"}""" }.check("0.2.2").updateAvailable)
        assertFalse(UpdateChecker { """{"tag_name":"v0.2.1"}""" }.check("0.2.2").updateAvailable)
    }

    @Test
    fun doesNotRequireApkUrlWhenAlreadyCurrent() {
        val result = UpdateChecker { """{"tag_name":"v0.2.4"}""" }.check("0.2.4")

        assertFalse(result.updateAvailable)
        assertEquals("", result.apkUrl)
        assertEquals(null, result.error)
    }

    @Test
    fun comparesVersionsByNumericParts() {
        assertTrue(UpdateChecker.compareVersions("0.2.10", "0.2.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "0.9.9") > 0)
        assertEquals(0, UpdateChecker.compareVersions("v0.2.2", "0.2.2"))
    }

    @Test
    fun extractsApkUrlFromProductMetadata() {
        assertEquals(
            "https://ddlagent.praw.top/downloads/DDLAgent-v0.2.4.apk",
            UpdateChecker.extractApkUrl(
                """
                    {
                      "tag_name": "v0.2.4",
                      "apk_url": "https://ddlagent.praw.top/downloads/DDLAgent-v0.2.4.apk"
                    }
                """.trimIndent(),
            ),
        )
    }
}
