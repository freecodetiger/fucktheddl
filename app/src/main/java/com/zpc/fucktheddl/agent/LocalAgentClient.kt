package com.zpc.fucktheddl.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class LocalAgentClient : AgentClient {
    override fun testService(settings: AgentConnectionSettings): AgentConnectionTestResult {
        if (settings.deepseekApiKey.isBlank()) {
            return AgentConnectionTestResult(false, "请填写 DeepSeek Key")
        }
        if (settings.aliyunApiKey.isBlank()) {
            return AgentConnectionTestResult(false, "请填写阿里云语音 Key")
        }
        val result = propose(
            text = "今天有哪些安排？",
            sessionId = "local-service-test",
            commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
            settings = settings,
        )
        return if (result.error == null) {
            AgentConnectionTestResult(true, "本地服务就绪", "DeepSeek 与阿里云语音配置已填写")
        } else {
            AgentConnectionTestResult(false, "模型不可用", result.error.safeLocalSummary())
        }
    }

    override fun propose(
        text: String,
        sessionId: String,
        commitments: AgentCommitmentsPayload?,
        settings: AgentConnectionSettings?,
    ): AgentSubmitResult {
        val activeSettings = settings ?: return AgentSubmitResult(null, "缺少本地模型设置")
        if (activeSettings.deepseekApiKey.isBlank()) {
            return AgentSubmitResult(null, "请在设置里填写 DeepSeek API Key")
        }
        return runCatching {
            val extraction = requestModelExtraction(text, activeSettings)
            val proposal = LocalAgentEngine().draftProposal(
                text = text,
                sessionId = sessionId,
                modelExtraction = extraction,
                commitments = commitments ?: AgentCommitmentsPayload(emptyList(), emptyList()),
            )
            AgentSubmitResult(proposal = proposal, error = null)
        }.getOrElse { error ->
            AgentSubmitResult(proposal = null, error = error.message ?: "DeepSeek 调用失败")
        }
    }

    private fun requestModelExtraction(text: String, settings: AgentConnectionSettings): JSONObject {
        val body = JSONObject()
            .put("model", settings.deepseekModel.trim().ifBlank { "deepseek-v4-flash" })
            .put("temperature", 0)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put("content", modelSystemPrompt()))
                .put(JSONObject().put("role", "user").put("content", text)))
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("thinking", JSONObject().put("type", "disabled"))
        val connection = URL("${settings.deepseekBaseUrl.trim().trimEnd('/')}/chat/completions")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 8000
        connection.readTimeout = 30000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", "Bearer ${settings.deepseekApiKey.trim()}")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.readText().orEmpty()
        if (connection.responseCode !in 200..299) {
            error("DeepSeek 调用失败：HTTP ${connection.responseCode}${raw.safeLocalSummary().prependIfNotBlank(" · ")}")
        }
        val content = JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        return parseModelJson(content)
    }
}

internal class LocalAgentEngine(
    private val todayProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Shanghai")) },
) {
    fun draftProposal(
        text: String,
        sessionId: String,
        modelExtraction: JSONObject,
        commitments: AgentCommitmentsPayload,
    ): AgentProposal {
        val heuristic = classifyHeuristic(text)
        val modelType = modelExtraction.optString("commitment_type", "clarify")
            .takeIf { it in setOf("schedule", "todo", "delete", "update", "query", "suggestion", "clarify") }
            ?: "clarify"
        val commitmentType = when {
            heuristic.commitmentType in setOf("delete", "update", "query", "suggestion") -> heuristic.commitmentType
            modelType == "clarify" && heuristic.commitmentType != "clarify" -> heuristic.commitmentType
            else -> modelType
        }
        val rawModelNotes = modelExtraction.optString("notes", "")
        val modelDateLabel = modelExtraction.optString("date", modelExtraction.optString("due", ""))
        val notes = extractNotes(text, rawModelNotes)
        val title = compactTitle(modelExtraction.optString("title", text)).ifBlank { heuristic.title }
        val state = AgentDraftState(
            text = text,
            sessionId = sessionId,
            commitmentType = commitmentType,
            title = if (notes.isNotBlank() && rawModelNotes.isBlank()) heuristic.title.ifBlank { title } else title.ifBlank { compactTitle(text) },
            dateLabel = resolveDraftDate(text, modelDateLabel),
            timeRange = normalizeTime(modelExtraction.optString("time", "")).orEmpty().ifBlank { heuristic.timeRange },
            dueLabel = modelExtraction.optString("due", "").ifBlank { heuristic.dueLabel },
            priority = modelExtraction.optString("priority", "medium").ifBlank { "medium" },
            notes = notes,
        )
        return draft(state, commitments)
    }

    private fun draft(state: AgentDraftState, commitments: AgentCommitmentsPayload): AgentProposal {
        val proposalId = sha1("${state.sessionId}:${state.text}").take(16)
        val candidates = findCommitmentCandidates(state.text, commitments)
        return when (state.commitmentType) {
            "schedule" -> {
                val start = extractStart(state)
                val end = plusOneHour(start)
                AgentProposal(
                    id = proposalId,
                    commitmentType = CommitmentType.Schedule,
                    title = state.title,
                    summary = "准备创建日程：${state.title}，${formatTimeWindow(start, end)}。",
                    impact = "确认后会作为日程加入日历；如果标题或时间不对，可以取消或编辑后再发送。",
                    requiresConfirmation = true,
                    schedulePatch = AgentSchedulePatch(
                        title = state.title,
                        start = start,
                        end = end,
                        timezone = "Asia/Shanghai",
                        location = "",
                        notes = state.notes,
                        tags = emptyList(),
                    ),
                )
            }
            "todo" -> {
                val due = state.dateLabel.ifBlank { extractDueDate(state.text) }
                AgentProposal(
                    id = proposalId,
                    commitmentType = CommitmentType.Todo,
                    title = state.title,
                    summary = "准备创建待办：${state.title}，截止到${formatDueLabel(due)}。",
                    impact = "确认后会加入待办列表；它不会占用日历时间。",
                    requiresConfirmation = true,
                    todoPatch = AgentTodoPatch(
                        title = state.title,
                        due = due,
                        timezone = "Asia/Shanghai",
                        priority = state.priority,
                        notes = state.notes,
                        tags = emptyList(),
                    ),
                )
            }
            "delete" -> draftDelete(proposalId, state, commitments, candidates)
            "update" -> draftUpdate(proposalId, state, commitments, candidates)
            "query" -> draftQuery(proposalId, state, commitments)
            "suggestion" -> AgentProposal(
                id = proposalId,
                commitmentType = CommitmentType.Suggestion,
                title = "日程建议",
                summary = suggestCommitments(commitments),
                impact = "这是基于当前日程和待办的建议，不会自动修改任何内容。",
                requiresConfirmation = false,
            )
            else -> AgentProposal(
                id = proposalId,
                commitmentType = CommitmentType.Clarify,
                title = state.title.ifBlank { "需要补充信息" },
                summary = "这条请求还缺少关键时间或截止信息，需要先补充。",
                impact = "暂时不会写入任何日程或待办。",
                requiresConfirmation = false,
            )
        }
    }

    private fun draftDelete(
        proposalId: String,
        state: AgentDraftState,
        commitments: AgentCommitmentsPayload,
        candidates: List<CommitmentCandidate>,
    ): AgentProposal {
        val target = candidates.singleOrNull()
        if (target != null) {
            return AgentProposal(
                id = proposalId,
                commitmentType = CommitmentType.Delete,
                title = target.title,
                summary = "准备删除${typeLabel(target.type)}：${target.title}。",
                impact = "确认后会从当前列表移除。",
                requiresConfirmation = true,
                deletePatch = AgentDeletePatch(target.id, target.type, target.title),
            )
        }
        val choices = proposalCandidates(candidates.ifEmpty { fallbackCandidates(state.text, commitments) }, state.text)
        return AgentProposal(
            id = proposalId,
            commitmentType = CommitmentType.Delete,
            title = if (choices.isEmpty()) "没有找到可取消的项目" else "选择要取消的项目",
            summary = if (choices.isEmpty()) "没有找到可取消的日程或待办。" else "我找到几个可能要取消的项目，请选一个继续。",
            impact = if (choices.isEmpty()) "当前列表里没有足够接近的候选项。" else "点选候选后会生成删除确认，不会直接删除。",
            requiresConfirmation = false,
            candidates = choices,
        )
    }

    private fun draftUpdate(
        proposalId: String,
        state: AgentDraftState,
        commitments: AgentCommitmentsPayload,
        candidates: List<CommitmentCandidate>,
    ): AgentProposal {
        val target = candidates.singleOrNull()
        if (target?.type == "schedule") {
            val start = extractStart(state)
            val end = plusOneHour(start)
            val patch = AgentSchedulePatch(target.title, start, end, "Asia/Shanghai", target.location, state.notes.ifBlank { target.notes }, target.tags)
            return AgentProposal(
                id = proposalId,
                commitmentType = CommitmentType.Update,
                title = target.title,
                summary = "准备修改日程：${target.title}，调整为${formatTimeWindow(start, end)}。",
                impact = "确认后会更新原日程；如果时间不对，可以取消后重新描述。",
                requiresConfirmation = true,
                schedulePatch = patch,
                updatePatch = AgentUpdatePatch(target.id, "schedule", target.title, schedulePatch = patch),
            )
        }
        if (target != null) {
            val due = state.dateLabel.ifBlank { extractDueDate(state.text) }
            val patch = AgentTodoPatch(target.title, due, "Asia/Shanghai", target.priority, target.notes, target.tags)
            return AgentProposal(
                id = proposalId,
                commitmentType = CommitmentType.Update,
                title = target.title,
                summary = "准备修改待办：${target.title}，截止到${formatDueLabel(due)}。",
                impact = "确认后会更新原待办。",
                requiresConfirmation = true,
                todoPatch = patch,
                updatePatch = AgentUpdatePatch(target.id, "todo", target.title, todoPatch = patch),
            )
        }
        val choices = proposalCandidates(candidates.ifEmpty { fallbackCandidates(state.text, commitments) }, state.text)
        return AgentProposal(
            id = proposalId,
            commitmentType = CommitmentType.Update,
            title = if (choices.isEmpty()) "没有找到要修改的项目" else "选择要修改的项目",
            summary = if (choices.isEmpty()) "没有找到要修改的日程或待办。" else "我找到几个可能要修改的项目，请选一个继续。",
            impact = if (choices.isEmpty()) "当前列表里没有足够接近的候选项。" else "点选候选后会生成修改确认，不会直接修改。",
            requiresConfirmation = false,
            candidates = choices,
        )
    }

    private fun draftQuery(proposalId: String, state: AgentDraftState, commitments: AgentCommitmentsPayload): AgentProposal {
        val items = queryCandidateItems(state.text, commitments)
        return AgentProposal(
            id = proposalId,
            commitmentType = CommitmentType.Query,
            title = queryTitle(state.text, items),
            summary = queryCommitments(state.text, commitments, items),
            impact = "这只是查询结果，不会写入任何内容。",
            requiresConfirmation = false,
            candidates = proposalCandidates(items, "删除", "删除"),
        )
    }

    private fun classifyHeuristic(text: String): AgentDraftState {
        val smalltalk = smalltalkReply(text)
        if (smalltalk.isNotBlank()) {
            return AgentDraftState(text, "", "suggestion", smalltalkTitle(text), conversationReply = smalltalk)
        }
        if (listOf("删除", "删掉", "取消", "移除", "撤销", "去掉").any { it in text }) {
            return AgentDraftState(text, "", "delete", compactTitle(text), timeRange = if (parseTime(text) != null) extractTimeRange(text) else "", dateLabel = if (mentionsDate(text)) extractDueDate(text) else "")
        }
        if (looksLikeUpdate(text)) {
            return AgentDraftState(text, "", "update", compactTitle(text), timeRange = if (parseTime(text) != null) extractTimeRange(text) else "", dateLabel = if (mentionsDate(text)) extractDueDate(text) else "", notes = extractNotes(text))
        }
        if (listOf("查", "查看", "看看", "有哪些", "有什么", "有啥", "啥安排", "列出", "今天安排", "明天安排").any { it in text }) {
            return AgentDraftState(text, "", "query", "查询日程", dateLabel = if (mentionsDate(text)) extractDueDate(text) else "")
        }
        if (listOf("建议", "推荐", "怎么安排", "帮我安排", "规划一下", "空档").any { it in text }) {
            return AgentDraftState(text, "", "suggestion", "日程建议")
        }
        val hasTime = listOf("点", ":", "：", "上午", "下午", "晚上", "早上").any { it in text }
        val hasDeadline = listOf("ddl", "截止", "前", "完成", "交", "due").any { it in text.lowercase(Locale.ROOT) }
        val type = when {
            hasTime && !looksLikeDeadlineOnly(text) -> "schedule"
            hasDeadline -> "todo"
            else -> "clarify"
        }
        return AgentDraftState(
            text = text,
            sessionId = "",
            commitmentType = type,
            title = compactTitle(text),
            timeRange = if (type == "schedule") extractTimeRange(text) else "",
            dateLabel = if (mentionsDate(text)) extractDueDate(text) else "",
            dueLabel = extractDueLabel(text),
            priority = if (listOf("紧急", "重要", "ddl", "截止").any { it in text }) "high" else "medium",
            notes = extractNotes(text),
        )
    }

    private fun extractStart(state: AgentDraftState): String {
        val date = state.dateLabel.ifBlank { extractDueDate(state.text) }
        val time = normalizeTime(state.timeRange) ?: parseTime(state.text) ?: "09:00:00"
        return "${date}T${time}+08:00"
    }

    private fun plusOneHour(start: String): String =
        OffsetDateTime.parse(start).plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun extractTimeRange(text: String): String = parseTime(text)?.take(5) ?: "Needs time"

    private fun extractDueLabel(text: String): String = when {
        "周五" in text || "星期五" in text || "friday" in text.lowercase(Locale.ROOT) -> "本周五截止"
        "今天" in text -> "今天截止"
        "明天" in text -> "明天截止"
        "后天" in text -> "后天截止"
        else -> "稍后截止"
    }

    private fun extractDueDate(text: String, fallbackDate: String = ""): String {
        val today = todayProvider()
        extractExplicitDate(text, today)?.let { return it.toString() }
        normalizeModelDate(fallbackDate, today)?.let { return it }
        return today.toString()
    }

    private fun resolveDraftDate(text: String, modelDate: String): String {
        normalizeModelDate(modelDate, todayProvider())?.let { return it }
        return extractDueDate(text)
    }

    private fun extractExplicitDate(text: String, today: LocalDate): LocalDate? {
        if (listOf("五一期间", "五一假期", "劳动节期间", "劳动节假期").any { it in text }) {
            val candidate = LocalDate.of(today.year, 5, 5)
            return if (candidate < today) candidate.plusYears(1) else candidate
        }
        Regex("""(20\d{2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})""").find(text)?.let {
            return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*(?:日|号)?""").find(text)?.let {
            return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        extractMonthDay(text, today)?.let { return it }
        return when {
            "今天" in text -> today
            "明天" in text -> today.plusDays(1)
            "后天" in text -> today.plusDays(2)
            "周五" in text || "星期五" in text -> nextWeekday(today, 5)
            else -> null
        }
    }

    private fun normalizeModelDate(value: String, today: LocalDate): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        extractExplicitDate(raw, today)?.let { return it.toString() }
        Regex("""^\d{1,2}\s*[-/.]\s*\d{1,2}$""").find(raw)?.let {
            val parts = raw.split(Regex("""\s*[-/.]\s*"""))
            val candidate = runCatching { LocalDate.of(today.year, parts[0].toInt(), parts[1].toInt()) }.getOrNull()
                ?: return null
            return (if (candidate < today) candidate.plusYears(1) else candidate).toString()
        }
        return null
    }

    private fun normalizeTime(value: String): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        parseTime(raw)?.let { return it }
        if (Regex("""\d{1,2}""").matches(raw)) {
            return "%02d:00:00".format(normalizeHour(raw.toInt(), raw))
        }
        return null
    }

    private fun extractNotes(text: String, modelNotes: String = ""): String {
        if (modelNotes.trim().isNotBlank()) return modelNotes.trim()
        val note = splitNotesTail(text).second
        if (note.isNotBlank()) return note
        return if (shouldPreserveOriginalAsNotes(text)) text.trim() else ""
    }

    private fun splitNotesTail(text: String): Pair<String, String> {
        val patterns = listOf(
            Regex("""(?<core>.+?)[，,。；;]\s*(?:这个)?(?<kind>任务|事项)(?:的)?内容(?:是|为|：|:)?(?<note>.+)$"""),
            Regex("""(?<core>.+?)(?:这个)?(?<kind>任务|事项)(?:的)?内容(?:是|为|：|:)?(?<note>.+)$"""),
            Regex("""(?<core>.+?)[，,。；;]\s*(?<note>(?:带|带着|拿|携带|材料|资料|文件|准备|需要|密码|链接|地址|地点|到时候|如果|因为).+)$"""),
            Regex("""(?<core>.+?)[，,。；;]\s*(?<marker>备注(?:是|为)?|备注一下|注意(?:一下)?|记得|到时候|需要|要带|带上|请带|准备好|别忘了|顺便)(?<note>.+)$"""),
            Regex("""(?<core>.+?)(?<marker>备注(?:是|为)?|备注一下|注意(?:一下)?|记得|到时候|需要|要带|带上|请带|准备好|别忘了|顺便|带|带着|拿|携带)(?<note>.+)$"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(text.trim()) ?: continue
            var core = match.namedGroup("core").trim(' ', '，', ',', '。', '；', ';')
            val note = match.namedGroup("note").trim(' ', '：', ':', '，', ',', '。', '；', ';')
            if (core.isBlank() || note.isBlank()) continue
            val kind = match.namedGroup("kind")
            if (kind.isNotBlank() && listOf("一个", "这个", "某个").any { core.endsWith(it) }) core += kind
            val marker = match.namedGroup("marker")
            return core to if (marker.isBlank() || marker.startsWith("备注")) note else marker + note
        }
        return text.trim() to ""
    }

    private fun shouldPreserveOriginalAsNotes(text: String): Boolean {
        val raw = text.trim()
        if (raw.length < 24) return false
        if (listOf("备注", "记得", "注意", "到时候", "别忘了", "顺便").any { it in raw }) return true
        if (!Regex("""[，,。；;]""").containsMatchIn(raw)) return false
        val tail = raw.split(Regex("""[，,。；;]"""), limit = 2).getOrNull(1)?.trim().orEmpty()
        if (tail.isBlank()) return false
        if (Regex("""(提前|到时候)?[零〇一二两三四五六七八九十\d]{0,3}\s*(分钟|小时)?提醒我?""").matches(tail)) return false
        return listOf("说", "可能", "临时", "流程", "材料", "准备", "带", "发消息", "联系", "如果", "因为", "先").any { it in tail }
    }

    private fun compactTitle(text: String): String {
        var cleaned = splitNotesTail(text).first
            .trim()
            .replace("，", " ")
            .replace(",", " ")
        cleaned = cleaned.replace(Regex("""(删除|删掉|取消|移除|修改|调整|改到|改成|提前|推迟|查询|查看|列出|完成|帮我|请|一下)"""), " ")
        cleaned = cleaned.replace(Regex("""20\d{2}\s*[-年]\s*\d{1,2}\s*[-月]\s*\d{1,2}\s*(日|号)?"""), " ")
        cleaned = cleaned.replace(Regex("""[零〇一二两三四五六七八九十\d]{1,3}\s*月\s*[零〇一二两三四五六七八九十\d]{1,3}\s*(日|号)?"""), " ")
        cleaned = cleaned.replace(Regex("""(本|下|这)?周[一二三四五六日天]"""), " ")
        cleaned = cleaned.replace(Regex("""(本|下|这)?星期[一二三四五六日天]"""), " ")
        cleaned = cleaned.replace(Regex("""(今天|明天|后天)?(上午|下午|晚上|早上|中午|凌晨)?\s*\d{1,2}\s*[:：]\s*\d{2}"""), " ")
        cleaned = cleaned.replace(Regex("""(今天|明天|后天)?(上午|下午|晚上|早上|中午|凌晨)?\s*[零〇一二两三四五六七八九十\d]{1,3}\s*点\s*(半)?钟?"""), " ")
        listOf("明天", "今天", "后天", "上午", "下午", "早上", "中午", "凌晨", "晚上", "截止", "前").forEach {
            cleaned = cleaned.replace(it, " ")
        }
        return cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }.joinToString(" ").take(48)
    }

    private fun findCommitmentCandidates(text: String, commitments: AgentCommitmentsPayload): List<CommitmentCandidate> {
        val candidates = commitmentCandidates(commitments)
        candidates.firstOrNull { it.id.isNotBlank() && "#${it.id}" in text || it.id.isNotBlank() && it.id in text }?.let {
            return listOf(it)
        }
        val query = compactTitle(text)
        val queryDate = if (mentionsDate(text)) extractDueDate(text) else ""
        val queryTime = parseTime(text)?.take(5).orEmpty()
        val scored = candidates.mapNotNull { item ->
            if (queryDate.isNotBlank() && item.date != queryDate) return@mapNotNull null
            var score = 0
            if (query.isNotBlank() && (query in item.title || item.title in query)) score += 8
            else if (query.isNotBlank() && query.split(" ").any { it.isNotBlank() && it in item.title }) score += 4
            if (queryDate.isNotBlank() && item.date == queryDate) score += 4
            if (queryTime.isNotBlank() && item.time == queryTime) score += 4
            if (item.type == "schedule" && listOf("日程", "课", "会", "午睡", "安排").any { it in text }) score += 1
            if (item.type == "todo" && listOf("待办", "任务", "截止", "完成").any { it in text }) score += 1
            if (score == 0) null else score to item
        }.sortedByDescending { it.first }
        if (scored.isEmpty()) return emptyList()
        if (scored.size == 1) return listOf(scored[0].second)
        val top = scored[0].first
        val second = scored[1].first
        if (top >= second + 4 && top >= 8) return listOf(scored[0].second)
        return scored.filter { it.first >= maxOf(1, top - 3) }.map { it.second }.take(5)
    }

    private fun fallbackCandidates(text: String, commitments: AgentCommitmentsPayload): List<CommitmentCandidate> {
        val today = todayProvider().toString()
        var pool = commitmentCandidates(commitments).filter { it.date >= today }.ifEmpty { commitmentCandidates(commitments) }
        if (listOf("活动", "日程", "安排", "课", "会").any { it in text }) {
            pool = pool.filter { it.type == "schedule" }.ifEmpty { pool }
        } else if (listOf("待办", "任务", "作业", "ddl", "截止").any { it in text }) {
            pool = pool.filter { it.type == "todo" }.ifEmpty { pool }
        }
        return pool.sortedWith(compareBy<CommitmentCandidate> { it.date }.thenBy { it.time }).take(5)
    }

    private fun proposalCandidates(items: List<CommitmentCandidate>, originalText: String, actionLabel: String = "选择"): List<AgentProposalCandidate> =
        items.take(5).map {
            AgentProposalCandidate(
                id = it.id,
                targetType = it.type,
                title = it.title,
                whenLabel = candidateWhen(it),
                detail = it.notes.ifBlank { typeLabel(it.type) },
                resolutionText = "$originalText #${it.id}",
                actionLabel = actionLabel,
            )
        }

    private fun commitmentCandidates(commitments: AgentCommitmentsPayload): List<CommitmentCandidate> =
        commitments.events.map {
            CommitmentCandidate(it.id, "schedule", it.title, it.start.take(10), it.start.drop(11).take(5), it.location, it.notes, it.tags)
        } + commitments.todos.map {
            CommitmentCandidate(it.id, "todo", it.title, it.due.take(10), "", "", it.notes, it.tags, it.priority)
        }

    private fun queryCandidateItems(text: String, commitments: AgentCommitmentsPayload): List<CommitmentCandidate> {
        val filteredByDate = if (looksLikeDeadlineQuery(text)) {
            val today = todayProvider()
            val endDate = deadlineQueryEndDate(text)
            commitmentCandidates(commitments).filter {
                val date = runCatching { LocalDate.parse(it.date.take(10)) }.getOrNull()
                date != null && date >= today && date <= endDate
            }
        } else {
            val targetDate = if (mentionsDate(text)) extractDueDate(text) else ""
            commitmentCandidates(commitments).filter { targetDate.isBlank() || it.date == targetDate }
        }
        val filtered = when (queryTypeFilter(text)) {
            "schedule" -> filteredByDate.filter { it.type == "schedule" }
            "todo" -> filteredByDate.filter { it.type == "todo" }
            else -> filteredByDate
        }
        return filtered.sortedWith(compareBy<CommitmentCandidate> { it.date }.thenBy { it.time }.thenBy { it.title }).take(8)
    }

    private fun queryTitle(text: String, items: List<CommitmentCandidate>): String = when {
        looksLikeDeadlineQuery(text) && "后天" in text -> "后天前截止"
        looksLikeDeadlineQuery(text) && "明天" in text -> "明天前截止"
        looksLikeDeadlineQuery(text) && "今天" in text -> "今天截止"
        "今天" in text -> "今天"
        "明天" in text -> "明天"
        "后天" in text -> "后天"
        items.isNotEmpty() -> formatDueLabel(items[0].date)
        else -> "查询结果"
    }

    private fun queryCommitments(text: String, commitments: AgentCommitmentsPayload, items: List<CommitmentCandidate>): String {
        if (commitmentCandidates(commitments).isEmpty()) return "当前没有已确认的日程或待办。"
        if (items.isEmpty()) return "${queryEmptyLabel(text)}没有安排。"
        val schedules = items.filter { it.type == "schedule" }
        val todos = items.filter { it.type == "todo" }
        return listOfNotNull(
            schedules.takeIf { it.isNotEmpty() }?.joinToString("；", prefix = "日程：") { "${it.time} ${it.title}" },
            todos.takeIf { it.isNotEmpty() }?.joinToString("；", prefix = "待办：") { "${it.title}，截止到${formatDueLabel(it.date)}" },
        ).joinToString("\n")
    }

    private fun suggestCommitments(commitments: AgentCommitmentsPayload): String {
        val activeTodos = commitments.todos.filter { it.status == "active" }
        activeTodos.firstOrNull { it.priority == "high" }?.let {
            return "建议先处理高优先级待办“${it.title}”，再把低优先级事项放到空档。"
        }
        if (commitments.events.size >= 4) return "今天固定日程偏多，建议不要再新增大块任务，只保留短待办。"
        activeTodos.firstOrNull()?.let { return "建议给“${it.title}”安排一个 45 分钟专注块。" }
        return "当前压力不高，建议保留一个空档给临时任务。"
    }

    private fun parseTime(text: String): String? {
        Regex("""(?<hour>\d{1,2})\s*[:：]\s*(?<minute>\d{2})""").find(text)?.let {
            return "%02d:%02d:00".format(normalizeHour(it.groups["hour"]!!.value.toInt(), text), it.groups["minute"]!!.value.toInt())
        }
        Regex("""(?<hour>[零〇一二两三四五六七八九十\d]{1,3})\s*点\s*(?<half>半)?""").find(text)?.let {
            val hour = chineseNumberToInt(it.groups["hour"]!!.value) ?: return null
            val minute = if (it.groups["half"] != null) 30 else 0
            return "%02d:%02d:00".format(normalizeHour(hour, text), minute)
        }
        return null
    }

    private fun normalizeHour(hour: Int, text: String): Int = when {
        hour < 0 || hour > 24 -> 9
        hour == 24 -> 0
        listOf("下午", "晚上").any { it in text } && hour in 1..11 -> hour + 12
        "凌晨" in text && hour == 12 -> 0
        else -> hour
    }

    private fun chineseNumberToInt(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val digits = mapOf("零" to 0, "〇" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9)
        if (value == "十") return 10
        if (value.startsWith("十")) return 10 + (digits[value.drop(1)] ?: 0)
        if ("十" in value) {
            val parts = value.split("十", limit = 2)
            return (digits[parts[0]] ?: 0) * 10 + (digits[parts.getOrElse(1) { "" }] ?: 0)
        }
        return digits[value]
    }

    private fun mentionsDate(text: String): Boolean =
        listOf("今天", "明天", "后天", "周", "星期", "-", "五一", "劳动节").any { it in text } ||
            Regex("""[零〇一二两三四五六七八九十\d]{1,3}\s*月\s*[零〇一二两三四五六七八九十\d]{1,3}\s*(?:日|号)?""").containsMatchIn(text)

    private fun extractMonthDay(text: String, today: LocalDate): LocalDate? {
        val match = Regex("""(?<month>[零〇一二两三四五六七八九十\d]{1,3})\s*月\s*(?<day>[零〇一二两三四五六七八九十\d]{1,3})\s*(?:日|号)?""").find(text) ?: return null
        val month = chineseNumberToInt(match.groups["month"]!!.value) ?: return null
        val day = chineseNumberToInt(match.groups["day"]!!.value) ?: return null
        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (candidate < today) candidate.plusYears(1) else candidate
    }

    private fun nextWeekday(today: LocalDate, isoDayOfWeek: Int): LocalDate {
        val daysAhead = (isoDayOfWeek - today.dayOfWeek.value + 7) % 7
        return today.plusDays((if (daysAhead == 0) 7 else daysAhead).toLong())
    }

    private fun formatTimeWindow(start: String, end: String): String =
        "${relativeDateLabel(LocalDate.parse(start.take(10)))} ${start.drop(11).take(5)}-${end.drop(11).take(5)}"

    private fun formatDueLabel(due: String): String = "${relativeDateLabel(LocalDate.parse(due.take(10)))}（${due.take(10)}）"

    private fun relativeDateLabel(target: LocalDate): String = when (target) {
        todayProvider() -> "今天"
        todayProvider().plusDays(1) -> "明天"
        todayProvider().plusDays(2) -> "后天"
        else -> target.toString()
    }

    private fun looksLikeDeadlineOnly(text: String): Boolean =
        listOf("ddl", "截止", "完成", "due").any { it in text.lowercase(Locale.ROOT) }

    private fun looksLikeDeadlineQuery(text: String): Boolean =
        listOf("ddl", "截止", "due").any { it in text.lowercase(Locale.ROOT) }

    private fun deadlineQueryEndDate(text: String): LocalDate {
        val today = todayProvider()
        return when {
            "后天" in text -> today.plusDays(2)
            "明天" in text -> today.plusDays(1)
            "今天" in text -> today
            else -> LocalDate.parse(extractDueDate(text))
        }
    }

    private fun queryEmptyLabel(text: String): String {
        return if (looksLikeDeadlineQuery(text)) {
            "${relativeDateLabel(deadlineQueryEndDate(text))}前截止的项目"
        } else {
            formatDueLabel(if (mentionsDate(text)) extractDueDate(text) else todayProvider().toString())
        }
    }

    private fun queryTypeFilter(text: String): String {
        val mentionsTodo = listOf("待办", "任务", "作业").any { it in text }
        val mentionsSchedule = listOf("日程", "课程", "会议").any { it in text } ||
            Regex("""(?<!截)止?课""").containsMatchIn(text) ||
            Regex("""(?<!开)会""").containsMatchIn(text)
        return when {
            mentionsTodo && !mentionsSchedule -> "todo"
            mentionsSchedule && !mentionsTodo -> "schedule"
            else -> ""
        }
    }

    private fun looksLikeUpdate(text: String): Boolean =
        listOf("改到", "改成", "修改", "推迟", "延后").any { it in text } ||
            ("调整" in text && listOf("把", "将", "日程", "待办", "时间", "改", "到").any { it in text })

    private fun smalltalkReply(text: String): String {
        val normalized = text.trim().lowercase(Locale.ROOT).replace(Regex("""[\s，,。.!！?？~～]+"""), "")
        return when (normalized) {
            "你好", "您好", "哈喽", "hello", "hi", "嗨", "在吗", "在不在" -> "我在。你可以直接说日程、待办、查询、修改或删除，我会先整理成可确认的结果，不会直接写入。"
            "谢谢", "谢了", "感谢", "thankyou", "thanks" -> "不客气。你继续说下一件事就行。"
            "你是谁", "你能做什么", "你可以做什么", "怎么用", "如何使用" -> "我是你的日程和待办助手。你可以直接说“明天九点上课”“周五前交报告”“取消下午的会”，我会识别后给你确认。"
            else -> ""
        }
    }

    private fun smalltalkTitle(text: String): String =
        if (text.contains("什么") || text.contains("怎么") || text.contains("如何")) "可以这样用" else "我在"

    private fun candidateWhen(item: CommitmentCandidate): String =
        if (item.type == "schedule") "${formatDueLabel(item.date)} ${item.time}".trim() else "截止 ${formatDueLabel(item.date)}"

    private fun typeLabel(type: String): String = if (type == "schedule") "日程" else "待办"
}

private data class AgentDraftState(
    val text: String,
    val sessionId: String,
    val commitmentType: String,
    val title: String,
    val timeRange: String = "",
    val dateLabel: String = "",
    val dueLabel: String = "",
    val priority: String = "medium",
    val notes: String = "",
    val conversationReply: String = "",
)

private data class CommitmentCandidate(
    val id: String,
    val type: String,
    val title: String,
    val date: String,
    val time: String,
    val location: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val priority: String = "medium",
)

private fun modelSystemPrompt(): String =
    "当前日期是 ${LocalDate.now(ZoneId.of("Asia/Shanghai"))}。" +
        "只提取日程管理承诺，必须只返回一个 JSON object，不要解释，不要 Markdown。" +
        "有明确开始时间、需要在某个时间出现或参与的是 schedule；只要求在截止前完成的是 todo；信息不足时是 clarify。" +
        "取消、删除、移除已有事项是 delete；修改、改到、推迟已有事项是 update；询问已有安排是 query；请求规划或建议是 suggestion。" +
        "title 必须使用简洁中文，不要包含日期和时间。把日程或待办本身之外的补充要求、任务内容、携带材料、注意事项、上下文放入 notes。" +
        "短句也要区分标题和备注：例如“明天下午三点项目会，带电脑”返回 title=项目会, notes=带电脑；“周五前交报告，材料放在群文件”返回 title=交报告, notes=材料放在群文件。" +
        "不要把“带电脑、材料放在群文件、准备证件、记得发链接”等备注内容并入 title。" +
        "JSON 字段固定为：commitment_type, title, date, time, due, priority, reminder_minutes, notes。无法确定的字段用空字符串。"

private fun parseModelJson(content: String): JSONObject {
    var raw = content.trim()
    if (raw.startsWith("```")) {
        raw = raw.replace(Regex("""^```(?:json)?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*```$"""), "")
    }
    return runCatching { JSONObject(raw) }.getOrElse {
        val match = Regex("""\{.*}""", RegexOption.DOT_MATCHES_ALL).find(raw)
            ?: throw it
        JSONObject(match.value)
    }
}

private fun sha1(value: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun String.safeLocalSummary(): String =
    trim()
        .replace(Regex(""""model_api_key"\s*:\s*"[^"]*"""") , """"model_api_key":"***"""")
        .replace(Regex(""""api_key"\s*:\s*"[^"]*"""") , """"api_key":"***"""")
        .replace(Regex(""""Authorization"\s*:\s*"[^"]*"""") , """"Authorization":"***"""")
        .take(140)

private fun String.prependIfNotBlank(prefix: String): String = if (isBlank()) "" else prefix + this

private fun MatchResult.namedGroup(name: String): String =
    runCatching { groups[name]?.value.orEmpty() }.getOrDefault("")
