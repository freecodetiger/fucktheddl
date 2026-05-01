# FuckTheDDL

**自然语言驱动的日程管理 Agent** — 用说话的方式管理你的日程和待办。

> "明天下午三点上地理课"、"周五前完成计算机网络作业"、"把明天的午睡改到四点"

AI 理解你的自然语言，生成结构化的日程提案，你确认后才会写入 — **AI 决不会直接修改你的数据**。

## 架构

```
你说话/打字 → Android App → 后端 AI Agent (DeepSeek) → 生成提案 → 你确认/修改 → 写入本地数据库
```

- **Propose-Apply 写策略**：AI 只提议不动手，所有写入必须经过用户明确确认
- **本地优先**：日程和待办数据存在手机本地（Room SQLite），后端只做 AI 推理
- **自带 API Key**：用户可在应用设置中填写自己的 DeepSeek API Key，数据不经过服务端

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Python / FastAPI / LangGraph / LangChain / Redis / SQLite |
| 前端 | Kotlin / Jetpack Compose / Material3 / Room |
| AI 模型 | DeepSeek (默认 `deepseek-v4-flash`) |
| 语音识别 | 阿里云 DashScope FunASR 实时语音转文字 |
| 邮件验证 | Resend API（免密码邮箱登录） |

## 功能

- 自然语言创建日程和待办
- 语音输入支持（阿里云 ASR 实时识别）
- 删除、修改、查询已有日程
- 冲突检测和影响分析
- 邮箱验证码免密码登录
- 深色/浅色主题
- Git 审计追踪（每次写入自动提交）

## 项目结构

```
fucktheddl/
├── backend/                      # Python 后端
│   ├── fucktheddl_agent/
│   │   ├── api.py                # FastAPI 路由
│   │   ├── auth.py               # 认证服务
│   │   ├── auth_store.py         # SQLite 认证存储
│   │   ├── config.py             # 配置（环境变量）
│   │   ├── email_sender.py       # Resend 邮件发送
│   │   ├── jobs.py               # Redis 异步任务队列
│   │   ├── model_gateway.py      # LLM 调用网关
│   │   ├── schemas.py            # Pydantic 数据模型
│   │   ├── service.py            # Agent 核心服务
│   │   ├── storage.py            # JSON + Git 数据持久化
│   │   └── workflow.py           # LangGraph Agent 状态机
│   └── tests/
├── app/                          # Android 客户端
│   └── src/main/java/com/zpc/fucktheddl/
│       ├── agent/                # API 客户端
│       ├── auth/                 # 认证与会话管理
│       ├── commitments/          # Room 本地数据库
│       ├── ui/                   # Compose UI
│       └── voice/                # ASR 语音客户端
├── prototype/                    # UI 原型
├── docs/                         # 设计文档
└── pyproject.toml
```

## 快速开始

### 后端

```bash
git clone https://github.com/freecodetiger/fucktheddl.git
cd fucktheddl

# 安装依赖
python -m venv .venv && source .venv/bin/activate
pip install -e .

# 配置环境变量（复制并编辑 .env）
cp .env.example .env

# 启动 Redis（异步任务队列需要）
redis-server

# 启动后端
python -m uvicorn fucktheddl_agent.api:app --app-dir backend --host 0.0.0.0 --port 8000
```

### 环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `OPENAI_API_KEY` | DeepSeek API Key | - |
| `OPENAI_BASE_URL` | API 地址 | `https://api.deepseek.com/v1` |
| `OPENAI_MODEL` | 模型名称 | `deepseek-v4-flash` |
| `FUCKTHEDDL_USE_MODEL` | 启用服务端模型 | `false` |
| `ALIYUN_API_KEY` | 阿里云 ASR Key | - |
| `RESEND_API_KEY` | Resend 邮件 API Key | - |
| `RESEND_FROM_EMAIL` | 发件邮箱 | - |
| `FUCKTHEDDL_REDIS_URL` | Redis 地址 | `redis://127.0.0.1:6379/0` |
| `FUCKTHEDDL_DATA_ROOT` | 数据目录 | `.` |

### Android 客户端

1. Android Studio 打开项目根目录
2. 在 `local.properties` 中设置 `agent.baseUrl` 指向后端地址
3. `./gradlew assembleDebug` 构建
4. 安装 APK 到设备

## API 概要

| 端点 | 方法 | 说明 |
|---|---|---|
| `/health` | GET | 服务状态 |
| `/auth/code/request` | POST | 发送邮箱验证码 |
| `/auth/code/verify` | POST | 验证码校验，返回 Token |
| `/agent/propose` | POST | 提交自然语言指令，返回 job_id |
| `/agent/jobs/{job_id}` | GET | 轮询任务结果 |
| `/agent/confirm/{proposal_id}` | POST | 确认提案 |
| `/agent/proposal/{proposal_id}/edit` | POST | 修改提案 |
| `/agent/undo/{commitment_id}` | POST | 撤销（软删除） |
| `/commitments` | GET | 列出所有日程和待办 |
| `/asr/session` | GET | 获取 ASR 会话凭证 |

## License

MIT
