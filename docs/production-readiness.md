# 生产部署与客户端分发准备清单

本文档列出将 fucktheddl 后端部署到服务器、并分发 Android 客户端前需要完成的准备工作。

当前目标架构：

- Android 客户端保存用户日程和待办数据，当前实现使用本地 Room（SQLite）。
- 后端负责邮箱验证码登录、鉴权、AI 请求转发、ASR 会话配置下发、健康检查和必要的服务端审计。
- 用户可在客户端设置自己的 DeepSeek API Key、模型地址和后端 URL。
- 服务端不应长期保存用户日程正文、用户 DeepSeek API Key 或完整语音内容。

## 1. 架构边界确认

- 明确后端是“AI/ASR 网关”，不是日程数据库。
- 明确日程、待办、备注、完成状态、删除状态都以客户端 SQLite 为主。
- 明确服务端只接收当前请求所需的上下文，例如本次自然语言文本和客户端传来的本地 commitments 快照。
- 明确所有写入仍必须经过客户端确认，后端不得绕过确认直接创建日程。
- 明确后端对 LLM JSON 输出只做候选结构化结果，最终仍需要 deterministic validation。
- 明确未来如果引入多端同步，需要单独设计端到端加密或服务端用户数据模型，不要混入当前网关职责。

## 2. 后端 API 准备

- 为生产 API 增加版本前缀，例如 `/v1/health`、`/v1/agent/propose`、`/v1/asr/session`。
- 保留旧路径一段兼容期，避免已安装客户端升级前失效。
- 统一错误响应格式，至少包含 `code`、`message`、`retryable`。
- 增加请求 ID，例如 `X-Request-Id`，便于客户端问题排查。
- 增加后端版本字段，例如 `/health` 返回 `version`、`build_time`、`api_schema_version`。
- 将 `/health` 拆成轻量健康检查和深度连接测试，避免健康检查频繁调用外部模型。
- 在 `/agent/propose` 中继续支持客户端传入 `model_api_key`、`model_base_url`、`model`、`disable_thinking`。
- 在 `/agent/propose` 中限制请求体大小，避免超长上下文造成费用和延迟风险。
- 在 `/agent/propose` 中对 commitments 数量做上限，防止客户端误传全量历史导致超时。
- 为 ASR 会话接口设置短有效期，避免长期凭证泄露。

## 3. 鉴权与访问控制

- 当前实现使用邮箱验证码登录，邮箱是用户唯一标识。
- 客户端不展示鉴权 token；登录成功后把用户邮箱、用户 ID、访问 token 保存到本机登录态。
- 客户端请求后端时通过 `Authorization: Bearer <token>` 发送登录态 token。
- 后端只接受有效 token 访问 `/agent/propose`、`/agent/jobs/{job_id}`、`/commitments`、`/agent/confirm/{proposal_id}`、`/agent/proposal/{proposal_id}/edit`、`/agent/undo/{commitment_id}` 和 `/asr/session`。
- `/health`、`/auth/code/request`、`/auth/code/verify` 是公开接口。
- 用户退出登录时应调用 `/auth/logout` 吊销当前 token，并清理客户端登录态。
- 为连接测试提供不暴露敏感信息的认证结果。
- 禁止在 URL query 中传递 API Key 或访问 token。
- 服务端日志必须脱敏 `Authorization`、`model_api_key`、ASR key 和任何 access token。
- 后续如果支持多设备管理，需要增加 token 列表、设备名、最后使用时间和单设备吊销入口。

## 3.1 Email Login Deployment

Required backend environment variables:

```text
RESEND_API_KEY=
RESEND_FROM_EMAIL=
RESEND_FROM_NAME=DDL Agent
FUCKTHEDDL_REDIS_URL=
FUCKTHEDDL_AGENT_WORKERS=
FUCKTHEDDL_JOB_TTL_SECONDS=
DEEPSEEK defaults if used by server-side fallback
```

Operational checks:

- Verify Resend sender/domain before public testing.
- Confirm `/health` is public.
- Confirm `/agent/propose` returns `401` without token.
- Confirm `/auth/code/request` sends a real email.
- Confirm `/auth/code/verify` returns `user_id` and `access_token`.
- Confirm app settings do not display the token.
- Confirm a logged-in user's local Room data is isolated by `user_id`.
- Confirm Redis is reachable before starting the API, because `/agent/propose` uses the queue path.

## 4. 用户密钥策略

- 用户 DeepSeek API Key 优先只存客户端本地，不在服务端落库。
- 客户端本地存储 API Key 时需要使用 Android Keystore 或加密 SharedPreferences 方案。
- 后端只在本次请求内临时使用用户传来的 API Key 调用模型。
- 后端日志、异常、trace 中不得打印用户 API Key。
- 连接测试只返回“是否可用”和模型摘要，不返回密钥内容。
- 如果未来改为服务端保存用户密钥，需要先引入加密存储、密钥轮换、删除账号时销毁密钥、管理员不可明文查看等机制。

## 5. 模型网关准备

- 默认模型使用 DeepSeek OpenAI-compatible 格式。
- 默认关闭思考模式，当前请求应携带 `disable_thinking=true`。
- 后端 prompt 必须要求模型只返回 JSON object，不返回 Markdown。
- 后端需要兼容模型返回 fenced JSON 的情况。
- 对模型 JSON 做字段白名单校验，只接受已定义字段。
- 对 `commitment_type` 做枚举校验，只允许 `schedule`、`todo`、`delete`、`update`、`query`、`suggestion`、`clarify`。
- 对时间、日期、截止日期进行本地二次解析和校验。
- 对备注识别保留规则兜底：用户说“任务内容是”“备注”“记得”“注意”等时必须写入 notes。
- 模型失败时返回可理解错误，不应清空客户端录音结果。
- 增加模型超时控制，避免 UI 长时间卡在“正在整理”。

## 6. 语音服务准备

- 服务端保存 ASR 主密钥，客户端不得内置长期 ASR 密钥。
- 客户端通过 `/asr/session` 获取短期 ASR 会话参数。
- ASR 会话返回值需要有明确过期时间。
- ASR 接口失败时，客户端应显示“语音服务不可用”，不影响本地日程查看。
- 客户端录音权限说明必须清楚，说明录音仅用于语音识别。
- 评估是否需要服务端代理音频流；当前推荐客户端直连 ASR，后端只下发短期配置，延迟更低。
- 记录 ASR 错误码和连接耗时，但不要记录完整音频和完整隐私文本。
- 手机弱网场景需要测试：开始录音失败、录音中断、松手后 final 结果超时。

## 7. 数据持久化与迁移

- 当前 Android 使用 Room，本地数据库名为 `fucktheddl_commitments.db`。
- `schedules` 和 `todos` 表都包含 `ownerUserId`，用于同一设备上不同邮箱用户的数据隔离。
- 生产前需要明确 SQLite schema 版本升级策略。
- 每次 Room schema 变更必须提供 migration，生产版本不得直接丢弃用户数据。
- 增加本地备份/导出方案，例如导出 JSON。
- 增加本地恢复方案，例如从 JSON 导入。
- 增加数据删除能力，至少支持清空本地数据。
- 明确日程按年月组织的查询能力，支持跨月浏览。
- 确认删除是软删除还是硬删除；如果是硬删除，需要二次确认和可选撤销窗口。
- 如果未来需要多端同步，先设计冲突策略，不要直接把本地 SQLite 上传为服务端真源。

## 8. 客户端设置准备

- 设置页需要显示后端 URL。
- 设置页需要显示后端连接状态：未连接、连接中、连接成功、连接失败。
- 设置页需要支持测试后端连接。
- 设置页需要支持测试模型调用。
- 设置页需要支持测试 ASR 会话。
- 设置页需要填写 DeepSeek API Key、模型 Base URL、模型名称。
- 设置页需要说明用户 API Key 的存储位置和使用方式。
- 设置页需要显示 App 版本号、后端版本号和 API schema 版本。
- 设置页需要支持主题选择：经典浅色、深色、第三主题。
- 设置页需要提供重置连接配置能力。
- 设置页用户入口只显示当前登录邮箱和退出登录，不显示后端鉴权 token。

## 9. Android Release 准备

- 确定正式 `applicationId`，发布后不要随意更改。
- 创建 release signing keystore，并安全备份。
- 不要把 keystore、密码或 signing 配置提交到 Git。
- 配置 `versionCode` 和 `versionName` 的升级规则。
- 区分 debug 和 release 默认后端 URL。
- release 包默认不要指向 `127.0.0.1` 或局域网地址。
- 生产服务器必须使用 HTTPS，release 包默认禁用明文 HTTP。
- 检查 `network_security_config`，避免 release 误允许任意明文流量。
- 构建 release APK 或 AAB 前运行完整测试。
- 真机安装 release 包验证麦克风权限、网络请求、ASR、AI 解析、本地 SQLite 写入。
- 如果通过应用商店分发，需要准备隐私政策、应用描述、权限说明和截图。

## 10. 后端部署环境准备

- 准备服务器，例如云主机、轻量应用服务器或容器平台。
- 准备域名，例如 `api.example.com`。
- 配置 HTTPS 证书，推荐自动续期。
- 使用反向代理，例如 Caddy 或 Nginx。
- 使用进程管理，例如 Docker Compose、systemd 或平台托管服务。
- 配置环境变量：`OPENAI_BASE_URL`、`OPENAI_MODEL`、`OPENAI_DISABLE_THINKING`、`ALIYUN_API_KEY`、`ALIYUN_ASR_URL`。
- 如果使用用户自带模型 API Key，服务端默认 `OPENAI_API_KEY` 可以为空，但代码路径必须测试。
- 配置 `FUCKTHEDDL_USE_MODEL` 的生产值，明确是否允许服务端默认模型。
- 配置日志目录和日志轮转。
- 配置备份策略，至少备份服务配置和部署脚本。
- 配置防火墙，只开放必要端口：80、443、SSH。
- SSH 禁止密码登录，使用密钥登录。

## 11. Docker 与发布流水线

- 为后端增加 Dockerfile。
- 为本地和服务器增加 `docker-compose.yml`。
- 容器中使用非 root 用户运行服务。
- 镜像构建时不要复制 `.env`。
- `.env` 只在服务器部署目录存在，不进入镜像和 Git。
- 增加启动命令，例如 `uvicorn fucktheddl_agent.api:app --app-dir backend --host 0.0.0.0 --port 8000`。
- 增加容器健康检查，调用 `/health`。
- 增加部署脚本：拉取代码、构建镜像、重启服务、检查健康状态。
- 增加回滚方案：保留上一版镜像 tag。
- 如果后续多人使用，增加横向扩容前的共享状态审查，避免本地 `.runtime` 文件导致多副本不一致。

当前 Redis 队列配置：

```bash
export FUCKTHEDDL_REDIS_URL="redis://127.0.0.1:6379/0"
export FUCKTHEDDL_AGENT_WORKERS="2"
export FUCKTHEDDL_JOB_TTL_SECONDS="3600"
```

当前后端只保留 Redis 队列链路处理 AI 请求：`POST /agent/propose` 必定返回 `202 Accepted` 和 `job_id`，客户端轮询 `GET /agent/jobs/{job_id}` 获取 `queued/running/succeeded/failed` 状态。生产环境必须提供 Redis；Android 客户端已经兼容该异步 job 响应。

## 12. 高并发与稳定性

- 为 `/agent/propose` 增加并发限制。
- 为每个用户或 token 增加速率限制。
- 为模型调用增加超时，例如 10 到 20 秒。
- 为 ASR session 下发增加速率限制。
- 对外部 AI 服务失败做熔断或短期退避。
- 后端需要区分可重试错误和不可重试错误。
- 客户端收到超时时应允许用户重试，不应重复创建日程。
- proposal id 需要稳定，避免网络重试产生重复候选。
- 增加基本指标：请求量、错误率、P95 延迟、模型调用耗时、ASR session 成功率。
- 增加异常告警：5xx 升高、模型连续失败、ASR session 连续失败。

## 13. 隐私与合规

- 隐私政策必须说明收集哪些数据：语音识别文本、日程文本、设备连接配置、错误日志。
- 明确哪些数据保存在本地，哪些会发送到后端，哪些会发送到 DeepSeek/ASR 服务。
- 提供删除本地数据入口。
- 不记录完整 Authorization、用户 DeepSeek Key、ASR Key。
- 不在崩溃日志中上传完整日程正文。
- 生产日志只保留必要时间，设置过期清理。
- 如果面向公众用户，增加用户协议和第三方服务说明。
- 如果服务覆盖未成年人或敏感行业，需要额外合规评估。

## 14. 安全检查

- 检查 `.env`、`local.properties`、keystore 是否都在 `.gitignore`。
- 检查历史提交中是否已经泄露过密钥。
- 后端所有外部请求都必须走 HTTPS。
- 后端 CORS 策略不要使用无条件 `*`，除非只服务移动客户端且无浏览器入口。
- 限制请求体大小。
- 对所有用户输入做日志脱敏。
- 对模型返回 JSON 做 schema 校验，拒绝未知 mutation。
- 不让模型直接决定删除确认结果，删除必须由用户确认。
- 不让客户端传入任意文件路径或服务端执行参数。
- ASR session 接口必须鉴权。

## 15. 测试矩阵

- 后端单元测试：`pytest backend/tests -q`。
- Android 单元测试：`./gradlew testDebugUnitTest`。
- Android 编译测试：`./gradlew assembleDebug`。
- Release 构建测试：`./gradlew assembleRelease` 或 `./gradlew bundleRelease`。
- 连接测试：客户端填写生产 URL 后 `/health` 成功。
- 模型测试：用户填写 DeepSeek API Key 后可生成 schedule proposal。
- 备注测试：复杂请求能自动拆分 notes。
- 增删改查测试：自然语言创建、删除、修改、查询都可用。
- 模糊删除测试：候选列表正确，不误删。
- ASR 测试：按住说话、松手发送、滑动取消、弱网失败提示。
- SQLite 测试：确认 proposal 后本地持久化，杀进程重启后仍存在。
- 跨月测试：5 月创建事项后，切换月份能看到。
- 主题测试：三套主题可切换并持久化。
- 离线测试：无后端时仍能浏览本地日程和待办。
- 升级测试：旧版本安装后升级新版，SQLite 数据不丢。

## 16. 发布前验收清单

- 后端部署在 HTTPS 域名下。
- `/health` 返回后端版本、模型配置状态和 ASR 配置状态。
- 生产后端日志不泄露任何密钥。
- 客户端 release 包默认连接生产域名或引导用户填写域名。
- 客户端设置页能直观看到连接成功/失败。
- 用户 DeepSeek API Key 可以填写、保存、测试、清除。
- AI 生成方案使用非思考模式。
- 语音识别可用，失败时不影响本地数据。
- 创建日程、创建待办、修改、删除、查询都通过真机测试。
- 复杂语音输入能识别备注并在确认预览中展示。
- 本地 SQLite 数据重启后保留。
- 删除操作有确认或可撤销机制。
- release APK 使用正式签名。
- 隐私政策和权限说明已准备。

## 17. 建议执行顺序

1. 冻结当前 API schema，补 `/v1` 路由和版本字段。
2. 增加后端鉴权和日志脱敏。
3. 完成 Dockerfile、Compose、HTTPS 反向代理部署。
4. 补齐设置页连接测试中的后端版本、模型、ASR 状态展示。
5. 加固 Android 本地密钥保存。
6. 补 SQLite migration、导入导出和升级测试。
7. 配置 release 签名和 release 网络安全策略。
8. 在真机上跑完整验收矩阵。
9. 小范围分发 APK。
10. 收集崩溃、连接失败、ASR 失败和模型失败日志后再扩大分发。
