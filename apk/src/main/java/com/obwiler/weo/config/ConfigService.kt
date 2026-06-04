package com.obwiler.weo.config

import kotlinx.coroutines.flow.StateFlow

interface ConfigService {
    val config: StateFlow<AppConfig>
    fun load(): AppConfig
    fun observe(onChange: (AppConfig) -> Unit)
    fun invalidate()
}
