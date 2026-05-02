package com.zpc.fucktheddl.about

object AboutRepositoryInfo {
    const val RepositoryUrl = "https://github.com/freecodetiger/fucktheddl"

    val signatureText: String = listOf(
        "本项目由 freecodetiger 开发。",
        "日程、待办和任务书数据保存在本机；应用不依赖独立业务服务器。",
        "你填写的 DeepSeek 和阿里云配置只用于你主动发起的模型与语音服务调用。",
        "我们很注重保护用户隐私，也欢迎你到 GitHub 为这个项目点一个 Star。",
    ).joinToString("\n")
}
