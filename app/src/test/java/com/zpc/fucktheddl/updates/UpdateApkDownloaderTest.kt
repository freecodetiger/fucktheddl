package com.zpc.fucktheddl.updates

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateApkDownloaderTest {
    @Test
    fun downloadsApkAndReportsProgress() {
        val bytes = ByteArray(10_000) { index -> (index % 251).toByte() }
        val destination = Files.createTempDirectory("ddl-update-test").toFile()
        val progress = mutableListOf<UpdateDownloadProgress>()
        val downloader = UpdateApkDownloader(
            openSource = {
                UpdateDownloadSource(
                    input = ByteArrayInputStream(bytes),
                    contentLength = bytes.size.toLong(),
                )
            },
        )

        val file = downloader.download(
            apkUrl = "https://example.test/DDLAgent.apk",
            destinationDir = destination,
            fileName = "DDLAgent-test.apk",
            onProgress = progress::add,
        )

        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), progress.last().bytesRead)
        assertEquals(bytes.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
    }
}
