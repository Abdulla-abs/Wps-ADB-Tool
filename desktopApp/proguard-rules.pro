# Ktor — align with coil-network-ktor3 transitive deps; suppress inner-class warnings from mixed artifacts
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Coil
-keep class coil3.** { *; }

# Skiko / JBR shared textures (optional runtime feature)
-dontwarn com.jetbrains.SharedTextures
-dontwarn org.jetbrains.skiko.swing.JbrSharedTexturesAdapter

# SLF4J bindings resolved at runtime
-dontwarn org.slf4j.**
