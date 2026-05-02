package com.zpc.fucktheddl.updates

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val latestVersion: String,
    val error: String? = null,
)

class UpdateChecker(
    private val fetchLatestReleaseJson: () -> String = { httpGet(LatestVersionUrl) },
) {
    fun check(currentVersion: String): UpdateCheckResult {
        return runCatching {
            val latest = extractTagName(fetchLatestReleaseJson()).removePrefix("v")
            UpdateCheckResult(
                updateAvailable = compareVersions(latest, currentVersion.removePrefix("v")) > 0,
                latestVersion = latest,
            )
        }.getOrElse { error ->
            UpdateCheckResult(
                updateAvailable = false,
                latestVersion = currentVersion.removePrefix("v"),
                error = error.message ?: "检查更新失败",
            )
        }
    }

    companion object {
        const val ProductReleasePageUrl = "https://ddlagent.praw.top"
        const val GitHubReleasePageUrl = "https://github.com/freecodetiger/fucktheddl/releases"
        private const val LatestVersionUrl = "https://ddlagent.praw.top/version.json"

        fun extractTagName(json: String): String {
            val match = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)
            return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: error("版本信息解析失败")
        }

        fun compareVersions(left: String, right: String): Int {
            val leftParts = left.normalizedVersionParts()
            val rightParts = right.normalizedVersionParts()
            val maxSize = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until maxSize) {
                val diff = leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }

        private fun String.normalizedVersionParts(): List<Int> {
            return trim()
                .removePrefix("v")
                .lowercase(Locale.ROOT)
                .substringBefore("-")
                .split(".")
                .map { it.toIntOrNull() ?: 0 }
        }

        private fun httpGet(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*")
            return connection.inputStream.bufferedReader().use { it.readText() }
        }
    }
}
