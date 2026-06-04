# Keep annotations, generics signatures, and inner classes for runtime reflection
-keepattributes *Annotation*, Signature, InnerClasses

# ---- WEO entry points (declared in AndroidManifest.xml) ----
-keep class com.obwiler.weo.app.WEOApplication { *; }
-keep class com.obwiler.weo.MainActivity { *; }
-keep class com.obwiler.weo.service.KeepAliveService { *; }

# ---- Data models used in manual JSON parsing ----
-keep class com.obwiler.weo.config.AppConfig { *; }
-keep class com.obwiler.weo.ai.AiResult { *; }
-keep class com.obwiler.weo.ai.AiResult$ErrorCategory { *; }

# ---- Compose navigation: sealed class hierarchy needed for when() exhaustiveness ----
-keep class com.obwiler.weo.ui.Screen { *; }
-keep class com.obwiler.weo.ui.Screen$* { *; }

# ---- Event channels accessed across modules ----
-keep class com.obwiler.weo.event.AppEvents { *; }

# ---- Third-party libraries ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn com.rokid.**