package com.zpc.fucktheddl.quests

import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.QuestBookEntity
import com.zpc.fucktheddl.commitments.room.QuestNodeEntity
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class RoomQuestRepository(
    private val database: CommitmentDatabase,
) {
    private val dao = database.questDao()

    fun listBooks(kind: QuestBookKind): List<QuestBook> {
        return dao.listBooks(kind.storageKey).map { it.toQuestBook() }
    }

    fun getBookTree(bookId: String): QuestBookTree? {
        val book = dao.getBook(bookId)?.toQuestBook() ?: return null
        return QuestBookTree(
            book = book,
            nodes = dao.listNodes(bookId).map { it.toQuestNode() },
        )
    }

    fun getNode(nodeId: String): QuestNode? {
        return dao.getNode(nodeId)?.toQuestNode()
    }

    fun createBook(
        kind: QuestBookKind,
        title: String,
        description: String,
        location: String,
        targetDate: String,
    ): QuestBook {
        val now = nowIso()
        val book = QuestBookEntity(
            id = "quest_book_${UUID.randomUUID()}",
            kind = kind.storageKey,
            title = title.trim().ifBlank { "未命名任务书" },
            description = description.trim(),
            location = location.trim(),
            targetDate = targetDate.trim(),
            done = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertBook(book)
        return book.toQuestBook()
    }

    fun updateBook(book: QuestBook): QuestBook {
        val updated = book.copy(updatedAt = nowIso())
        dao.upsertBook(updated.toEntity())
        return updated
    }

    fun deleteBook(bookId: String) {
        dao.deleteNodesForBook(bookId)
        dao.deleteBook(bookId)
    }

    fun createNode(
        bookId: String,
        parentId: String?,
        title: String,
        description: String = "",
        location: String = "",
        targetDate: String = "",
    ): QuestNode {
        val now = nowIso()
        val node = QuestNodeEntity(
            id = "quest_node_${UUID.randomUUID()}",
            bookId = bookId,
            parentId = parentId,
            title = title.trim().ifBlank { "新的小目标" },
            description = description.trim(),
            location = location.trim(),
            targetDate = targetDate.trim(),
            done = false,
            expanded = true,
            sortOrder = dao.nextSortOrder(bookId, parentId),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertNode(node)
        return node.toQuestNode()
    }

    fun updateNode(node: QuestNode): QuestNode {
        val updated = node.copy(updatedAt = nowIso())
        dao.upsertNode(updated.toEntity())
        return updated
    }

    fun deleteNode(nodeId: String) {
        deleteNodeCascade(nodeId)
    }

    private fun deleteNodeCascade(nodeId: String) {
        dao.listChildren(nodeId).forEach { child ->
            deleteNodeCascade(child.id)
        }
        dao.deleteNode(nodeId)
    }
}

private fun QuestBookEntity.toQuestBook(): QuestBook {
    return QuestBook(
        id = id,
        kind = QuestBookKind.fromStorage(kind),
        title = title,
        description = description,
        location = location,
        targetDate = targetDate,
        done = done,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun QuestBook.toEntity(): QuestBookEntity {
    return QuestBookEntity(
        id = id,
        kind = kind.storageKey,
        title = title,
        description = description,
        location = location,
        targetDate = targetDate,
        done = done,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun QuestNodeEntity.toQuestNode(): QuestNode {
    return QuestNode(
        id = id,
        bookId = bookId,
        parentId = parentId,
        title = title,
        description = description,
        location = location,
        targetDate = targetDate,
        done = done,
        expanded = expanded,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun QuestNode.toEntity(): QuestNodeEntity {
    return QuestNodeEntity(
        id = id,
        bookId = bookId,
        parentId = parentId,
        title = title,
        description = description,
        location = location,
        targetDate = targetDate,
        done = done,
        expanded = expanded,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
