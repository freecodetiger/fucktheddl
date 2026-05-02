package com.zpc.fucktheddl.agent

import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitmentEditProposalFactoryTest {
    @Test
    fun buildsScheduleUpdateProposalWithEditedFields() {
        val event = ScheduleEvent(
            id = "evt-1",
            date = "2026-05-06",
            timeRange = "09:00 - 10:00",
            title = "面试",
            detail = "带简历",
            tag = "日程",
            risk = ScheduleRisk.Normal,
        )

        val proposal = event.toScheduleUpdateProposal(
            title = "金山面试",
            date = "2026-05-07",
            startTime = "14:30",
            endTime = "15:30",
            notes = "准备作品集",
        )

        assertEquals(CommitmentType.Update, proposal.commitmentType)
        assertEquals("evt-1", proposal.updatePatch?.targetId)
        assertEquals("schedule", proposal.updatePatch?.targetType)
        assertEquals("金山面试", proposal.schedulePatch?.title)
        assertEquals("2026-05-07T14:30:00+08:00", proposal.schedulePatch?.start)
        assertEquals("2026-05-07T15:30:00+08:00", proposal.schedulePatch?.end)
        assertEquals("准备作品集", proposal.schedulePatch?.notes)
    }

    @Test
    fun buildsTodoUpdateProposalWithEditedFields() {
        val todo = TodoItem(
            id = "todo-1",
            dueDate = "2026-05-01",
            title = "完成接口",
            dueLabel = "2026-05-01 截止",
            detail = "识别人脸表情",
            tag = "待办",
            priority = TodoPriority.ImportantNotUrgent,
            done = false,
        )

        val proposal = todo.toTodoUpdateProposal(
            title = "完成人脸接口",
            due = "2026-05-03",
            notes = "输出后端接口文档",
            priority = "q1",
        )

        assertEquals(CommitmentType.Update, proposal.commitmentType)
        assertEquals("todo-1", proposal.updatePatch?.targetId)
        assertEquals("todo", proposal.updatePatch?.targetType)
        assertEquals("完成人脸接口", proposal.todoPatch?.title)
        assertEquals("2026-05-03", proposal.todoPatch?.due)
        assertEquals("q1", proposal.todoPatch?.priority)
        assertEquals("输出后端接口文档", proposal.todoPatch?.notes)
    }
}
