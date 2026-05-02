package com.zpc.fucktheddl.updates

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateDownloadSource(
    val input: InputStream,
    val contentLength: Long,
)

data class UpdateDownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

class UpdateApkDownloader(
    private val openSource: (String) -> UpdateDownloadSource = ::openHttpSource,
) {
    fun download(
        apkUrl: String,
        destinationDir: File,
        fileName: String,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File {
        require(apkUrl.startsWith("https://")) { "安装包地址必须使用 HTTPS" }
        destinationDir.mkdirs()
        val target = File(destinationDir, fileName)
        val temp = File(destinationDir, "$fileName.part")
        if (temp.exists()) temp.delete()
        if (target.exists()) target.delete()

        val source = openSource(apkUrl)
        var bytesRead = 0L
        source.input.use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    onProgress(UpdateDownloadProgress(bytesRead, source.contentLength))
                }
            }
        }
        check(bytesRead > 0L) { "安装包下载为空" }
        check(temp.renameTo(target)) { "安装包保存失败" }
        onProgress(UpdateDownloadProgress(bytesRead, source.contentLength.takeIf { it > 0L } ?: bytesRead))
        return target
    }

    companion object {
        private fun openHttpSource(apkUrl: String): UpdateDownloadSource {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
            if (connection.responseCode !in 200..299) {
                error("安装包下载失败：HTTP ${connection.responseCode}")
            }
            return UpdateDownloadSource(
                input = connection.inputStream,
                contentLength = connection.contentLengthLong,
            )
        }
    }
}
