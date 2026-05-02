package com.zpc.fucktheddl.quests

enum class QuestBookKind(val storageKey: String, val label: String) {
    Main("main", "主线"),
    Side("side", "支线");

    companion object {
        fun fromStorage(value: String): QuestBookKind {
            return values().firstOrNull { it.storageKey == value } ?: Main
        }
    }
}

data class QuestBook(
    val id: String,
    val kind: QuestBookKind,
    val title: String,
    val description: String,
    val location: String,
    val targetDate: String,
    val done: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class QuestNode(
    val id: String,
    val bookId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val location: String,
    val targetDate: String,
    val done: Boolean,
    val expanded: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class QuestBookTree(
    val book: QuestBook,
    val nodes: List<QuestNode>,
) {
    val completedNodeCount: Int = nodes.count { it.done }
    val totalNodeCount: Int = nodes.size
    val progress: Float = if (totalNodeCount == 0) 0f else completedNodeCount.toFloat() / totalNodeCount.toFloat()
}
