package com.obwiler.weo.ai

import android.util.Base64
import android.util.Log
import com.obwiler.weo.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class HttpAiClient : AiClient {

    companion object {
        private const val MAX_RETRIES = 2  // 1 initial + 1 retry
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun analyze(image: ByteArray, config: AppConfig): AiResult {
        if (config.apiBaseUrl.isBlank()) {
            return AiResult(answer = "未配置 AI 服务", error = "请在设置中填入 API 地址和 Key", errorCategory = ErrorCategory.UNKNOWN)
        }
        if (config.apiKey.isBlank()) {
            return AiResult(answer = "未配置 API Key", error = "请在 PC 工具中填入 API Key 并推送配置", errorCategory = ErrorCategory.AUTH)
        }

        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = withTimeout(config.timeoutMs) {
                    callApi(image, config)
                }
                // 429 / rate-limit: wait longer before retry
                if (result.errorCategory == ErrorCategory.TIMEOUT && attempt < MAX_RETRIES - 1) {
                    Log.w("WEO/AI", "Rate-limited (429), retry ${attempt + 1}/${MAX_RETRIES - 1}")
                    delay(2000L * (attempt + 1))
                    continue
                }
                return result
            } catch (e: SocketTimeoutException) {
                if (attempt < MAX_RETRIES - 1) {
                    Log.w("WEO/AI", "Timeout, retry ${attempt + 1}/${MAX_RETRIES - 1}")
                    delay(1000L * (attempt + 1))
                } else {
                    return AiResult(answer = "请求超时", error = e.message, errorCategory = ErrorCategory.TIMEOUT)
                }
            } catch (e: ConnectException) {
                if (attempt < MAX_RETRIES - 1) {
                    Log.w("WEO/AI", "Network error, retry ${attempt + 1}/${MAX_RETRIES - 1}")
                    delay(1000L * (attempt + 1))
                } else {
                    return AiResult(answer = "网络不通", error = "请检查 WiFi 连接", errorCategory = ErrorCategory.NETWORK)
                }
            }
        }

        return AiResult(answer = "AI 调用失败", error = "重试已用尽", errorCategory = ErrorCategory.UNKNOWN)
    }

    private suspend fun callApi(image: ByteArray, config: AppConfig): AiResult = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(image, Base64.NO_WRAP)
        val url = buildUrl(config.apiBaseUrl)

        val payload = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().apply {
                if (config.systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", config.systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", config.userPrompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$b64")
                            })
                        })
                    })
                })
            })
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature.toDouble())
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        response.body?.close()

        if (response.isSuccessful) {
            parseResponse(responseBody, config.maxAnswerChars)
        } else {
            val category = when (response.code) {
                401, 403 -> ErrorCategory.AUTH
                429 -> ErrorCategory.TIMEOUT   // triggers retry with longer backoff
                in 500..599 -> ErrorCategory.SERVER
                else -> ErrorCategory.UNKNOWN
            }
            AiResult(answer = "API 错误 ${response.code}", error = responseBody.take(100), errorCategory = category)
        }
    }

    private fun buildUrl(baseUrl: String): String {
        val raw = baseUrl.trimEnd('/')
        return when {
            raw.endsWith("/chat/completions") -> raw
            raw.endsWith("/v1") -> "$raw/chat/completions"
            else -> "$raw/chat/completions"
        }
    }

    private fun parseResponse(body: String, maxAnswerChars: Int): AiResult {
        return try {
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.getJSONObject(0).optJSONObject("message")
                val rawContent = msg?.opt("content")
                val content = extractText(rawContent)
                AiResult(answer = content.take(maxAnswerChars))
            } else {
                AiResult(answer = "无响应内容", error = body.take(100), errorCategory = ErrorCategory.UNKNOWN)
            }
        } catch (e: Exception) {
            AiResult(answer = body.take(300).trim(), error = e.message, errorCategory = ErrorCategory.UNKNOWN)
        }
    }

    private fun extractText(raw: Any?): String {
        return when (raw) {
            is String -> raw
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until raw.length()) {
                    val item = raw.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") {
                        sb.append(item.optString("text", ""))
                    }
                }
                sb.toString()
            }
            else -> raw?.toString() ?: ""
        }
    }
}