package com.zpc.fucktheddl.voice

class TranscriptAccumulator {
    private val finalSegments = mutableListOf<String>()

    fun onPartial(text: String): String {
        return joinedWith(text)
    }

    fun onFinal(text: String): String {
        val cleaned = text.trim()
        if (cleaned.isNotBlank()) {
            finalSegments += cleaned
        }
        return currentText()
    }

    fun currentText(): String {
        return finalSegments.joinToString(" ")
    }

    fun reset() {
        finalSegments.clear()
    }

    private fun joinedWith(partial: String): String {
        val cleaned = partial.trim()
        return (finalSegments + listOf(cleaned))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
