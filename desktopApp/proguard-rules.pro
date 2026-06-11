# Ktor — align with coil-network-ktor3 transitive deps; suppress inner-class warnings from mixed artifacts
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Coil
-keep class coil3.** { *; }

# Okio — required for Coil local file loading; ProGuard otherwise breaks Kotlin inline stubs
# (VerifyError: BufferedSink not assignable to RealBufferedSink in Okio__OkioKt.buffer)
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }

# Skiko / JBR shared textures (optional runtime feature)
-dontwarn com.jetbrains.SharedTextures
-dontwarn org.jetbrains.skiko.swing.JbrSharedTexturesAdapter

# SLF4J bindings resolved at runtime
-dontwarn org.slf4j.**
