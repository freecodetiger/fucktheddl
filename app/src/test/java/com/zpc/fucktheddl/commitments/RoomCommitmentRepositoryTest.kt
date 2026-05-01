package com.zpc.fucktheddl.commitments

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zpc.fucktheddl.agent.AgentDeletePatch
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCommitmentRepositoryTest {
    @Test
    fun dataIsIsolatedByOwnerUserId() {
        withRepository { repo ->
            repo.applyProposal("usr_a", scheduleProposal("地理课"))

            assertEquals(1, repo.listCommitments("usr_a").events.size)
            assertEquals(0, repo.listCommitments("usr_b").events.size)
        }
    }

    @Test
    fun deleteOnlyAffectsCurrentOwner() {
        withRepository { repo ->
            val eventId = repo.applyProposal("usr_a", scheduleProposal("地理课")).commitmentId
            repo.applyProposal("usr_b", scheduleProposal("地理课"))

            val delete = AgentProposal(
                id = "delete-1",
                commitmentType = CommitmentType.Delete,
                title = "删除地理课",
                summary = "",
                impact = "",
                requiresConfirmation = true,
                deletePatch = AgentDeletePatch(
                    targetId = eventId,
                    targetType = "schedule",
                    targetTitle = "地理课",
                ),
            )

            repo.applyProposal("usr_a", delete)

            assertEquals(0, repo.listCommitments("usr_a").events.size)
            assertEquals(1, repo.listCommitments("usr_b").events.size)
        }
    }

    @Test
    fun todoIsStoredUnderCurrentOwner() {
        withRepository { repo ->
            repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "todo-proposal",
                    commitmentType = CommitmentType.Todo,
                    title = "完成报告",
                    summary = "",
                    impact = "",
                    requiresConfirmation = true,
                    todoPatch = AgentTodoPatch(
                        title = "完成报告",
                        due = "2026-05-03",
                        timezone = "Asia/Shanghai",
                        priority = "high",
                        notes = "整理面试反馈",
                        tags = emptyList(),
                    ),
                ),
            )

            assertEquals("完成报告", repo.listCommitments("usr_a").todos.single().title)
            assertEquals(0, repo.listCommitments("usr_b").todos.size)
        }
    }

    private fun withRepository(block: (RoomCommitmentRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CommitmentDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            block(RoomCommitmentRepository(db))
        } finally {
            db.close()
        }
    }

    private fun scheduleProposal(title: String): AgentProposal {
        return AgentProposal(
            id = "p-$title",
            commitmentType = CommitmentType.Schedule,
            title = title,
            summary = "",
            impact = "",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = title,
                start = "2026-05-02T09:00:00+08:00",
                end = "2026-05-02T10:00:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "",
                tags = emptyList(),
            ),
        )
    }
}
