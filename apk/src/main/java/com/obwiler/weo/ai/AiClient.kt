package com.obwiler.weo.ai

import com.obwiler.weo.config.AppConfig

interface AiClient {
    suspend fun analyze(image: ByteArray, config: AppConfig): AiResult
}
