# DDL Agent

用 **说话** 的方式管理日程和待办。

DDL Agent 是一个本地优先的 Android 日程助手。你可以直接说「明天下午三点上地理课」「周五前交作业」「取消下午的会议」，AI 会把自然语言整理成结构化提案，只有在你确认后才写入。

<p>
  <a href="https://github.com/freecodetiger/fucktheddl/releases/latest/download/app-debug.apk"><strong>下载 Android APK</strong></a>
  ·
  <a href="https://ddlagent.praw.top">产品页</a>
  ·
  <a href="https://ddl.praw.top/health">服务状态</a>
</p>

<p>
  <img src="docs/assets/app-store-promo.png" alt="DDL Agent 产品宣传图" width="900">
</p>

## 核心体验

| 方向 | 说明 |
|---|---|
| 语音优先 | 底部按住说话，松手发送；识别文本会进入 AI 提案流程。 |
| 本地数据 | 日程和待办存储在手机 Room SQLite，服务端只负责认证、语音授权和 AI 转发。 |
| 确认写入 | AI 只生成提案，用户确认、编辑或取消后才会写入本地数据。 |
| 自带 Key | 用户在设置中填写自己的 DeepSeek API Key，费用和调用归用户掌控。 |
| 深浅主题 | 提供克制的浅色主题和纯黑深色主题，贴近 iOS 原生工具感。 |

## 功能

- 自然语言创建、修改、删除、查询日程和待办。
- 长按日程或待办进入编辑卡片。
- 全局创建入口，可手动创建日程或待办；待办支持不设截止日期。
- 模糊删除会返回候选列表，用户选择后再确认。
- 语音输入使用阿里云 DashScope FunASR 实时识别。
- 邮箱验证码登录，每个用户通过 token 区分。
- Redis 队列异步处理 Agent 请求，削峰并避免阻塞 API 入口。

## 架构

```text
Android App
  ├─ Room SQLite：本地日程和待办
  ├─ 语音录音与实时 ASR
  └─ Propose-Apply：确认后本地写入

Backend
  ├─ FastAPI：认证、ASR 会话、Agent API
  ├─ Redis Queue：异步执行 Agent 请求
  ├─ Resend：邮箱验证码
  └─ DeepSeek/OpenAI-compatible：自然语言解析
```

## 技术栈

| 层 | 技术 |
|---|---|
| Android | Kotlin / Jetpack Compose / Material3 / Room |
| 后端 | Python / FastAPI / LangGraph / LangChain / Redis / SQLite |
| 模型 | DeepSeek，默认 `deepseek-v4-flash` |
| 语音 | 阿里云 DashScope FunASR 实时语音识别 |
| 邮件 | Resend API |
| 发布 | GitHub Actions 自动构建 APK 和 Release |

## 快速开始

### 后端

```bash
git clone https://github.com/freecodetiger/fucktheddl.git
cd fucktheddl

python -m venv .venv
source .venv/bin/activate
pip install -e .

cp .env.example .env
redis-server

python -m uvicorn fucktheddl_agent.api:app \
  --app-dir backend \
  --host 0.0.0.0 \
  --port 8000
```

### Android

```bash
echo "agent.baseUrl=https://ddl.praw.top" >> local.properties
./gradlew assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `OPENAI_API_KEY` | 服务端默认模型 Key，通常关闭，让用户填写自己的 Key | - |
| `OPENAI_BASE_URL` | OpenAI-compatible API 地址 | `https://api.deepseek.com/v1` |
| `OPENAI_MODEL` | 模型名称 | `deepseek-v4-flash` |
| `OPENAI_DISABLE_THINKING` | 关闭思考模式 | `true` |
| `FUCKTHEDDL_USE_MODEL` | 是否启用服务端默认模型 | `false` |
| `ALIYUN_API_KEY` | 阿里云 ASR Key | - |
| `RESEND_API_KEY` | Resend 邮件 API Key | - |
| `RESEND_FROM_EMAIL` | 验证码发件地址 | - |
| `FUCKTHEDDL_REDIS_URL` | Redis 队列地址 | `redis://127.0.0.1:6379/0` |
| `FUCKTHEDDL_DATA_ROOT` | 服务端运行数据目录 | `.` |

## API 概要

| 端点 | 方法 | 说明 |
|---|---|---|
| `/health` | `GET` | 服务状态 |
| `/auth/code/request` | `POST` | 发送邮箱验证码 |
| `/auth/code/verify` | `POST` | 验证码登录，返回 token |
| `/agent/propose` | `POST` | 提交自然语言，返回 job id |
| `/agent/jobs/{job_id}` | `GET` | 查询 Agent 异步结果 |
| `/commitments` | `GET` | 兼容接口，客户端当前以本地 Room 为主 |
| `/asr/session` | `GET` | 获取 ASR 会话凭证 |

## 项目结构

```text
backend/                         Python 后端
  fucktheddl_agent/
    api.py                       FastAPI 路由
    auth.py                      邮箱登录与 token 鉴权
    jobs.py                      Redis 队列
    model_gateway.py             LLM 调用网关
    service.py                   Agent 服务层
    workflow.py                  LangGraph 状态机
app/                             Android 客户端
  src/main/java/com/zpc/fucktheddl/
    agent/                       API 模型与客户端
    auth/                        登录会话
    commitments/room/            Room 本地持久化
    ui/                          Compose UI
    voice/                       实时 ASR 客户端
docs/                            文档与展示资源
prototype/                       Web 原型
```

## 验证

```bash
.venv/bin/pytest backend/tests -q
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## License

MIT
