package com.zpc.fucktheddl.ui

import com.zpc.fucktheddl.quests.QuestNode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestTreeLayoutTest {
    @Test
    fun visibleRowsPreserveDeepNestingAndStableSiblingIndent() {
        val root = node(id = "root", parentId = null, title = "根")
        val childA = node(id = "child-a", parentId = root.id, title = "一层 A")
        val childB = node(id = "child-b", parentId = root.id, title = "一层 B")
        val grandChild = node(id = "grand-child", parentId = childA.id, title = "二层")
        val deep = node(id = "deep", parentId = grandChild.id, title = "三层")
        val deeper = node(id = "deeper", parentId = deep.id, title = "四层")
        val deepest = node(id = "deepest", parentId = deeper.id, title = "五层")

        val rows = visibleQuestRows(listOf(root, childB, childA, grandChild, deep, deeper, deepest))

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 1), rows.map { it.depth })
        assertEquals(questNodeIndentDp(childA.depthFrom(rows)), questNodeIndentDp(childB.depthFrom(rows)))
        assertEquals(questNodeIndentDp(4) + QuestNodeIndentStepDp, questNodeIndentDp(5))
    }

    private fun node(id: String, parentId: String?, title: String): QuestNode {
        return QuestNode(
            id = id,
            bookId = "book",
            parentId = parentId,
            title = title,
            description = "",
            location = "",
            targetDate = "",
            done = false,
            expanded = true,
            sortOrder = 0,
            createdAt = id,
            updatedAt = id,
        )
    }

    private fun QuestNode.depthFrom(rows: List<QuestVisibleNode>): Int {
        return rows.single { it.node.id == id }.depth
    }
}
