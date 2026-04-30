package com.zpc.fucktheddl.schedule

class StarterScheduleRepository {
    fun loadInitialState(): ScheduleShellState {
        val tabs = listOf(
            ScheduleTab(label = "今天", destination = TabDestination.Today),
            ScheduleTab(label = "待办", destination = TabDestination.Todo),
        )

        return ScheduleShellState(
            tabs = tabs,
            selectedTab = tabs.first(),
            events = listOf(
                ScheduleEvent(
                    timeRange = "09:30 - 10:15",
                    title = "复盘 DDL 队列",
                    detail = "先整理紧急截止事项，再用语音快速规划。",
                    tag = "规划",
                    risk = ScheduleRisk.Deadline,
                ),
                ScheduleEvent(
                    timeRange = "14:00 - 15:30",
                    title = "深度工作时段",
                    detail = "为当前项目保留不被打断的专注时间。",
                    tag = "专注",
                    risk = ScheduleRisk.Focus,
                ),
                ScheduleEvent(
                    timeRange = "20:00 - 20:20",
                    title = "每日收尾",
                    detail = "确认明天第一件事和提醒设置。",
                    tag = "例行",
                    risk = ScheduleRisk.Normal,
                ),
            ),
            todos = listOf(
                TodoItem(
                    title = "完成安卓语音外壳",
                    dueLabel = "今天截止",
                    detail = "保持第一版原生闭环可构建、可安装。",
                    tag = "安卓",
                    priority = TodoPriority.High,
                    done = false,
                ),
                TodoItem(
                    title = "定义 JSON 补丁协议",
                    dueLabel = "明天截止",
                    detail = "后端写入前区分日程补丁和待办补丁。",
                    tag = "后端",
                    priority = TodoPriority.Medium,
                    done = false,
                ),
                TodoItem(
                    title = "归档浏览器原型笔记",
                    dueLabel = "本周截止",
                    detail = "把已验证的原型决策沉淀到原生界面任务中。",
                    tag = "设计",
                    priority = TodoPriority.Low,
                    done = true,
                ),
            ),
            openSlots = listOf(
                OpenSlot(
                    timeRange = "10:30 - 11:30",
                    suggestion = "适合处理短小的行政杂事。",
                ),
                OpenSlot(
                    timeRange = "16:00 - 17:00",
                    suggestion = "适合重新排期或补录安排。",
                ),
            ),
            agentState = AgentState(
                status = "正在生成待确认方案",
                activeChain = listOf(
                    AgentStep(label = "识别用户意图", state = AgentStepState.Done),
                    AgentStep(label = "读取日程和待办事实", state = AgentStepState.Done),
                    AgentStep(label = "校验时间冲突", state = AgentStepState.Active),
                    AgentStep(label = "等待用户确认", state = AgentStepState.Waiting),
                    AgentStep(label = "写入 JSON 并提交 Git", state = AgentStepState.Waiting),
                ),
                pendingProposal = null,
                writePolicy = "proposal_required",
            ),
            syncState = SyncState(
                kind = "clean",
                label = "Git 已同步",
            ),
        )
    }
}
