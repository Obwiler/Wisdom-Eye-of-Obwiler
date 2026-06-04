package com.obwiler.weo.server

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.obwiler.weo.camera.CameraHolder
import com.obwiler.weo.config.AppConfig
import com.obwiler.weo.config.ConfigHolder
import com.obwiler.weo.event.AppEvents
import com.obwiler.weo.image.ImagePipeline
import com.obwiler.weo.photo.PhotoRepository
import com.obwiler.weo.sensor.ImuProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WeoApiHandler(
    private val context: Context,
    private val cameraHolder: CameraHolder,
    private val configHolder: ConfigHolder,
    private val imuProvider: ImuProvider,
    private val hotspotManager: WifiHotspotManager,
) {
    companion object {
        private const val TAG = "WEO/ApiHandler"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun handle(request: WeoRequest): WeoResponse {
        if (request.method == "OPTIONS") {
            return WeoResponse(204, headers = mapOf(
                "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers" to "Content-Type",
                "Access-Control-Max-Age" to "86400",
            ))
        }

        val path = request.path.removePrefix("/")
        return when {
            path == "ping" || path == "api/ping" -> handlePing()
            path == "api/status" && request.method == "GET" -> handleStatus()
            path == "api/config" && request.method == "GET" -> handleGetConfig()
            path == "api/config" && request.method == "POST" -> handleSetConfig(request)
            path == "api/capture" && request.method == "POST" -> handleCapture()
            path == "api/photos" && request.method == "GET" -> handlePhotoList()
            path.startsWith("api/photo/") && request.method == "GET" -> handlePhoto(path.removePrefix("api/photo/"))
            path == "api/sensors" && request.method == "GET" -> handleSensors()
            else -> WeoResponse.notFound()
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    private fun handlePing(): WeoResponse {
        return WeoResponse.okJson("""{"pong":true,"version":"0.2.0","timestamp":${System.currentTimeMillis()}}""")
    }

    private suspend fun handleStatus(): WeoResponse {
        val hotspot = hotspotManager.hotspotInfo
        val cfg = configHolder.config.first()
        val json = JSONObject().apply {
            put("version", "0.2.0")
            put("versionCode", 2)
            put("packageName", "com.obwiler.weo")
            put("wifi", JSONObject().apply {
                put("hotspotSsid", hotspot.ssid)
                put("hotspotActive", hotspot.active)
                put("ipAddress", hotspot.ipAddress ?: "")
                put("serverPort", hotspot.port)
            })
            put("config", JSONObject().apply {
                put("modelName", cfg.modelName)
                put("apiBaseUrl", cfg.apiBaseUrl)
                put("textCorrectionEnabled", cfg.textCorrectionEnabled)
            })
            put("timestamp", System.currentTimeMillis())
        }
        return WeoResponse.okJson(json.toString())
    }

    private suspend fun handleGetConfig(): WeoResponse {
        val cfg = configHolder.config.first()
        val json = JSONObject().apply {
            put("apiBaseUrl", cfg.apiBaseUrl)
            put("modelName", cfg.modelName)
            put("systemPrompt", cfg.systemPrompt)
            put("userPrompt", cfg.userPrompt)
            put("temperature", cfg.temperature.toDouble())
            put("maxTokens", cfg.maxTokens)
            put("timeoutMs", cfg.timeoutMs)
            put("textCorrectionEnabled", cfg.textCorrectionEnabled)
            put("maxAnswerChars", cfg.maxAnswerChars)
            val key = cfg.apiKey
            put("apiKey", if (key.length >= 8) key.take(4) + "..." + key.takeLast(4)
                          else if (key.isNotEmpty()) "****" else "")
        }
        return WeoResponse.okJson(json.toString())
    }

    private suspend fun handleSetConfig(request: WeoRequest): WeoResponse {
        return try {
            val input = JSONObject(request.body)
            val current = configHolder.config.first()

            val newConfig = AppConfig(
                apiBaseUrl = input.optString("apiBaseUrl", current.apiBaseUrl),
                apiKey = if (input.has("apiKey") && input.getString("apiKey").isNotBlank())
                    input.getString("apiKey") else current.apiKey,
                modelName = input.optString("modelName", current.modelName),
                systemPrompt = input.optString("systemPrompt", current.systemPrompt),
                userPrompt = input.optString("userPrompt", current.userPrompt),
                temperature = input.optDouble("temperature", current.temperature.toDouble()).toFloat(),
                maxTokens = input.optInt("maxTokens", current.maxTokens),
                timeoutMs = input.optLong("timeoutMs", current.timeoutMs),
                textCorrectionEnabled = input.optBoolean("textCorrectionEnabled", current.textCorrectionEnabled),
                maxAnswerChars = input.optInt("maxAnswerChars", current.maxAnswerChars),
            )

            configHolder.save(newConfig)
            AppEvents.configUpdated.trySend(Unit)
            WeoResponse.okJson("""{"ok":true,"message":"config saved"}""")
        } catch (e: Exception) {
            Log.e(TAG, "Config update failed", e)
            WeoResponse.badRequest("""{"error":"invalid config JSON","detail":"${e.message?.replace("\"", "'")}"}""")
        }
    }

    private suspend fun handleCapture(): WeoResponse {
        return try {
            // 1. Capture raw frame
            val rawBytes = withContext(Dispatchers.IO) { cameraHolder.capture() }
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return WeoResponse.internalError("""{"error":"bitmap decode failed"}""")

            // 2. Process through image pipeline (returns JPEG bytes)
            val jpegBytes = withContext(Dispatchers.IO) {
                val cfg = configHolder.config.first()
                ImagePipeline.process(
                    original = bmp,
                    pitchDeg = imuProvider.pitchDeg,
                    rollDeg = imuProvider.rollDeg,
                    enableCorrection = cfg.textCorrectionEnabled,
                )
            }

            // 3. Decode back to get dimensions for metadata
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)

            val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("format", "jpeg")
                put("base64", b64)
                put("size", jpegBytes.size)
                put("width", opts.outWidth)
                put("height", opts.outHeight)
                put("pitchDeg", imuProvider.pitchDeg.toDouble())
                put("rollDeg", imuProvider.rollDeg.toDouble())
                put("timestamp", System.currentTimeMillis())
            }
            // Cleanup
            bmp.recycle()
            WeoResponse.okJson(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            WeoResponse.internalError("""{"error":"capture failed","detail":"${e.message?.replace("\"", "'")}"}""")
        }
    }

    private suspend fun handlePhotoList(): WeoResponse {
        val photos = withContext(Dispatchers.IO) {
            PhotoRepository.loadAll(context)
                .sortedByDescending { it.dateTaken }
                .take(50)
        }
        val arr = JSONArray()
        for (p in photos) {
            arr.put(JSONObject().apply {
                put("fileName", p.displayName)
                put("filePath", p.filePath)
                put("takenAt", p.dateTaken)
                put("summary", p.aiSummary)
                put("sizeBytes", File(p.filePath).length())
            })
        }
        return WeoResponse.okJson(arr.toString())
    }

    private suspend fun handlePhoto(fileName: String): WeoResponse {
        return try {
            val photos = withContext(Dispatchers.IO) {
                PhotoRepository.loadAll(context)
            }
            val photo = photos.find { it.displayName == fileName }
                ?: return WeoResponse.notFound("""{"error":"photo not found"}""")

            val file = File(photo.filePath)
            if (!file.exists()) return WeoResponse.notFound("""{"error":"file missing"}""")

            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("fileName", photo.displayName)
                put("format", "jpeg")
                put("base64", b64)
                put("sizeBytes", bytes.size)
                put("takenAt", photo.dateTaken)
                put("summary", photo.aiSummary)
            }
            WeoResponse.okJson(json.toString())
        } catch (e: Exception) {
            WeoResponse.internalError("""{"error":"${e.message?.replace("\"", "'")}"}""")
        }
    }

    private fun handleSensors(): WeoResponse {
        val json = JSONObject().apply {
            put("pitchDeg", imuProvider.pitchDeg.toDouble())
            put("rollDeg", imuProvider.rollDeg.toDouble())
            put("timestamp", System.currentTimeMillis())
        }
        return WeoResponse.okJson(json.toString())
    }
}
