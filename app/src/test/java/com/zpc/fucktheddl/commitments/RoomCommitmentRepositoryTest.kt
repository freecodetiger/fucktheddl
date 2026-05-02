package com.zpc.fucktheddl.commitments

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zpc.fucktheddl.agent.AgentDeletePatch
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.AgentUpdatePatch
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun toggleTodoDonePersistsStatus() {
        withRepository { repo ->
            // Create a todo
            val result = repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "toggle-test",
                    commitmentType = CommitmentType.Todo,
                    title = "测试勾选",
                    summary = "",
                    impact = "",
                    requiresConfirmation = true,
                    todoPatch = AgentTodoPatch(
                        title = "测试勾选",
                        due = "2026-05-03",
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
            )
            val todoId = result.commitmentId

            // Initially active (done not set → defaults to active)
            val initial = repo.listCommitments("usr_a").todos.single()
            assertEquals("active", initial.status)

            // Toggle to done
            repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "toggle-on",
                    commitmentType = CommitmentType.Update,
                    title = "测试勾选",
                    summary = "完成",
                    impact = "",
                    requiresConfirmation = false,
                    todoPatch = AgentTodoPatch(
                        title = "测试勾选",
                        due = "2026-05-03",
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                        done = true,
                    ),
                    updatePatch = AgentUpdatePatch(
                        targetId = todoId,
                        targetType = "todo",
                        targetTitle = "测试勾选",
                        todoPatch = AgentTodoPatch(
                            title = "测试勾选",
                            due = "2026-05-03",
                            timezone = "Asia/Shanghai",
                            priority = "q2",
                            notes = "",
                            tags = emptyList(),
                            done = true,
                        ),
                    ),
                ),
            )

            val done = repo.listCommitments("usr_a").todos.single()
            assertEquals("done", done.status)

            // Toggle back to active
            repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "toggle-off",
                    commitmentType = CommitmentType.Update,
                    title = "测试勾选",
                    summary = "重开",
                    impact = "",
                    requiresConfirmation = false,
                    todoPatch = AgentTodoPatch(
                        title = "测试勾选",
                        due = "2026-05-03",
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                        done = false,
                    ),
                    updatePatch = AgentUpdatePatch(
                        targetId = todoId,
                        targetType = "todo",
                        targetTitle = "测试勾选",
                        todoPatch = AgentTodoPatch(
                            title = "测试勾选",
                            due = "2026-05-03",
                            timezone = "Asia/Shanghai",
                            priority = "q2",
                            notes = "",
                            tags = emptyList(),
                            done = false,
                        ),
                    ),
                ),
            )

            val activeAgain = repo.listCommitments("usr_a").todos.single()
            assertEquals("active", activeAgain.status)
        }
    }

    @Test
    fun noDeadlineTodoToggleSetsDueDate() {
        withRepository { repo ->
            // Create a todo without due date
            val result = repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "no-deadline-test",
                    commitmentType = CommitmentType.Todo,
                    title = "无截止日期待办",
                    summary = "",
                    impact = "",
                    requiresConfirmation = true,
                    todoPatch = AgentTodoPatch(
                        title = "无截止日期待办",
                        due = "",
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
            )
            val todoId = result.commitmentId

            // Initially has empty due
            val initial = repo.listCommitments("usr_a").todos.single()
            assertEquals("", initial.due)

            // Toggle to done — should set today's date
            val today = java.time.LocalDate.now().toString()
            repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "no-deadline-toggle",
                    commitmentType = CommitmentType.Update,
                    title = "无截止日期待办",
                    summary = "完成",
                    impact = "",
                    requiresConfirmation = false,
                    todoPatch = AgentTodoPatch(
                        title = "无截止日期待办",
                        due = today,
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                        done = true,
                    ),
                    updatePatch = AgentUpdatePatch(
                        targetId = todoId,
                        targetType = "todo",
                        targetTitle = "无截止日期待办",
                        todoPatch = AgentTodoPatch(
                            title = "无截止日期待办",
                            due = today,
                            timezone = "Asia/Shanghai",
                            priority = "q2",
                            notes = "",
                            tags = emptyList(),
                            done = true,
                        ),
                    ),
                ),
            )

            val done = repo.listCommitments("usr_a").todos.single()
            assertEquals("done", done.status)
            assertEquals(today, done.due)

            // Uncheck — should clear due back to empty for no-deadline todo
            repo.applyProposal(
                "usr_a",
                AgentProposal(
                    id = "no-deadline-untoggle",
                    commitmentType = CommitmentType.Update,
                    title = "无截止日期待办",
                    summary = "重开",
                    impact = "",
                    requiresConfirmation = false,
                    todoPatch = AgentTodoPatch(
                        title = "无截止日期待办",
                        due = "",
                        timezone = "Asia/Shanghai",
                        priority = "q2",
                        notes = "",
                        tags = emptyList(),
                        done = false,
                    ),
                    updatePatch = AgentUpdatePatch(
                        targetId = todoId,
                        targetType = "todo",
                        targetTitle = "无截止日期待办",
                        todoPatch = AgentTodoPatch(
                            title = "无截止日期待办",
                            due = "",
                            timezone = "Asia/Shanghai",
                            priority = "q2",
                            notes = "",
                            tags = emptyList(),
                            done = false,
                        ),
                    ),
                ),
            )

            val untoggled = repo.listCommitments("usr_a").todos.single()
            assertEquals("active", untoggled.status)
            assertEquals("", untoggled.due)
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
