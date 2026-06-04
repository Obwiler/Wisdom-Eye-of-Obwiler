package com.obwiler.weo.config

import android.os.FileObserver
import android.util.Log
import com.obwiler.weo.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

class ConfigHolder(private val configDir: File, private val configFileName: String) : ConfigService {

    companion object {
        private const val TAG = "WEO/Config"
    }

    private val configFile = File(configDir, configFileName)

    private val _config = MutableStateFlow(loadFromFile())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    private var observer: FileObserver? = null

    init {
        configDir.mkdirs()
    }

    override fun load(): AppConfig = _config.value

    override fun invalidate() {
        val updated = loadFromFile()
        _config.value = updated
        Log.d(TAG, "Config invalidated: key=${updated.apiKey.take(4)}... url=${updated.apiBaseUrl}")
    }

    fun startWatching() {
        observer?.stopWatching()
        observer = object : FileObserver(configFile, MODIFY or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (event and (MODIFY or CREATE) != 0) {
                    invalidate()
                }
            }
        }.also { it.startWatching() }
        Log.d(TAG, "FileObserver started on ${configFile.absolutePath}")
    }

    fun stopWatching() {
        observer?.stopWatching()
        observer = null
    }


    /** 保存配置到文件并通知 FileObserver */
    fun save(config: AppConfig) {
        try {
            val json = JSONObject().apply {
                put("apiBaseUrl", config.apiBaseUrl)
                put("apiKey", config.apiKey)
                put("modelName", config.modelName)
                put("systemPrompt", config.systemPrompt)
                put("userPrompt", config.userPrompt)
                put("temperature", config.temperature.toDouble())
                put("maxTokens", config.maxTokens)
                put("timeoutMs", config.timeoutMs)
                put("textCorrectionEnabled", config.textCorrectionEnabled)
                put("maxAnswerChars", config.maxAnswerChars)
            }
            configFile.writeText(json.toString(2))
            Log.d(TAG, "Config saved: key=${config.apiKey.take(4)}... url=${config.apiBaseUrl}")
        } catch (e: Exception) {
            Log.e(TAG, "Config save failed", e)
            throw e
        }
    }
    private fun loadFromFile(): AppConfig {
        if (!configFile.exists()) {
            Log.d(TAG, "Config file not found, using defaults")
            return AppConfig()
        }
        return try {
            val json = JSONObject(configFile.readText())
            val apiBaseUrl = json.optString("apiBaseUrl", "")
            val apiKey = json.optString("apiKey", "")
            if (apiBaseUrl.isBlank()) {
                Log.w(TAG, "Config: apiBaseUrl is empty")
            }
            if (apiKey.isBlank()) {
                Log.w(TAG, "Config: apiKey is empty")
            }
            AppConfig(
                apiBaseUrl = apiBaseUrl,
                apiKey = apiKey,
                modelName = json.optString("modelName", ""),
                systemPrompt = json.optString("systemPrompt", ""),
                userPrompt = json.optString("userPrompt", "请分析图片内容"),
                temperature = json.optDouble("temperature", 0.3).toFloat().coerceIn(0f, 2f),
                maxTokens = json.optInt("maxTokens", 1500).coerceIn(1, 8000),
                timeoutMs = json.optLong("timeoutMs", 60_000L).coerceIn(15_000L, 180_000L),
                textCorrectionEnabled = json.optBoolean("textCorrectionEnabled", true),
                maxAnswerChars = json.optInt("maxAnswerChars", 2000).coerceIn(100, 10000),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Config parse failed, using defaults", e)
            AppConfig()
        }
    }
}
