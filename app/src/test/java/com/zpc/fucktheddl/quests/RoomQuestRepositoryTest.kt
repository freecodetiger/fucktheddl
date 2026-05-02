package com.zpc.fucktheddl.quests

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomQuestRepositoryTest {
    @Test
    fun booksAreSeparatedByKindAndContainIndependentTrees() {
        withRepository { repo ->
            val main = repo.createBook(
                kind = QuestBookKind.Main,
                title = "成为独立开发者",
                description = "长期主线",
                location = "杭州",
                targetDate = "2026-12-31",
            )
            val side = repo.createBook(
                kind = QuestBookKind.Side,
                title = "学会摄影",
                description = "周末支线",
                location = "西湖",
                targetDate = "",
            )
            val phase = repo.createNode(main.id, parentId = null, title = "完成第一款应用")
            repo.createNode(main.id, parentId = phase.id, title = "写完任务书模块")
            repo.createNode(side.id, parentId = null, title = "拍一组夜景")

            assertEquals(listOf("成为独立开发者"), repo.listBooks(QuestBookKind.Main).map { it.title })
            assertEquals(listOf("学会摄影"), repo.listBooks(QuestBookKind.Side).map { it.title })
            assertEquals(2, repo.getBookTree(main.id)?.nodes?.size)
            assertEquals(1, repo.getBookTree(side.id)?.nodes?.size)
        }
    }

    @Test
    fun deletingANodeDeletesItsDescendantsOnlyInsideThatBook() {
        withRepository { repo ->
            val book = repo.createBook(QuestBookKind.Main, "主线", "", "", "")
            val otherBook = repo.createBook(QuestBookKind.Main, "另一本", "", "", "")
            val parent = repo.createNode(book.id, parentId = null, title = "阶段一")
            val child = repo.createNode(book.id, parentId = parent.id, title = "子目标")
            val grandChild = repo.createNode(book.id, parentId = child.id, title = "更小目标")
            repo.createNode(otherBook.id, parentId = null, title = "别的书节点")

            repo.deleteNode(parent.id)

            assertEquals(emptyList<String>(), repo.getBookTree(book.id)?.nodes?.map { it.title })
            assertEquals(listOf("别的书节点"), repo.getBookTree(otherBook.id)?.nodes?.map { it.title })
            assertNull(repo.getNode(grandChild.id))
        }
    }

    @Test
    fun togglingNodeCompletionDoesNotAutomaticallyCompleteParent() {
        withRepository { repo ->
            val book = repo.createBook(QuestBookKind.Main, "主线", "", "", "")
            val parent = repo.createNode(book.id, parentId = null, title = "阶段一")
            val child = repo.createNode(book.id, parentId = parent.id, title = "子目标")

            repo.updateNode(child.copy(done = true))

            val tree = repo.getBookTree(book.id)
            assertEquals(false, tree?.nodes?.single { it.id == parent.id }?.done)
            assertEquals(true, tree?.nodes?.single { it.id == child.id }?.done)
        }
    }

    private fun withRepository(block: (RoomQuestRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CommitmentDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            block(RoomQuestRepository(db))
        } finally {
            db.close()
        }
    }
}
