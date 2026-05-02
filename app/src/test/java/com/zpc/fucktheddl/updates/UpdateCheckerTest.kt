package com.zpc.fucktheddl.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun extractsLatestTagAndDetectsNewerRelease() {
        val checker = UpdateChecker(
            fetchLatestReleaseJson = { """{"tag_name":"v0.2.3","html_url":"https://example.test"}""" },
        )

        val result = checker.check("0.2.2")

        assertTrue(result.updateAvailable)
        assertEquals("0.2.3", result.latestVersion)
    }

    @Test
    fun doesNotReportUpdateForSameOrOlderRelease() {
        assertFalse(UpdateChecker { """{"tag_name":"v0.2.2"}""" }.check("0.2.2").updateAvailable)
        assertFalse(UpdateChecker { """{"tag_name":"v0.2.1"}""" }.check("0.2.2").updateAvailable)
    }

    @Test
    fun comparesVersionsByNumericParts() {
        assertTrue(UpdateChecker.compareVersions("0.2.10", "0.2.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "0.9.9") > 0)
        assertEquals(0, UpdateChecker.compareVersions("v0.2.2", "0.2.2"))
    }
}
