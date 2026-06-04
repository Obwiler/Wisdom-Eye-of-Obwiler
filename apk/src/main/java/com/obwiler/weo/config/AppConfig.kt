package com.obwiler.weo.config

data class AppConfig(
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val systemPrompt: String = "",
    val userPrompt: String = "请分析图片内容",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 1500,
    val timeoutMs: Long = 60_000L,
    val textCorrectionEnabled: Boolean = false,
    val maxAnswerChars: Int = 2000,
)
