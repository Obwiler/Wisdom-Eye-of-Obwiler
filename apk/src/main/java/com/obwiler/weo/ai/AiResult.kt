package com.obwiler.weo.ai

data class AiResult(
    val answer: String,
    val steps: List<String> = emptyList(),
    val confidence: Float = 0f,
    val error: String? = null,
    val errorCategory: ErrorCategory? = null,
)

enum class ErrorCategory {
    NETWORK, AUTH, TIMEOUT, SERVER, UNKNOWN
}
