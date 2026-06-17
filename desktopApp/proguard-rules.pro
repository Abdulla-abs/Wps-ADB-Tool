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
-keep class org.slf4j.** { *; }

# kotlinx-io — Ktor 3 keeps io.ktor entry points that reference kotlinx.io.* in a separate jar
-keep class kotlinx.io.** { *; }

# Guava (jadx) — j2objc annotations are compile-only, not bundled
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.google.common.hash.ChecksumHashFunction$ChecksumMethodHandles
-dontwarn com.google.common.hash.Hashing$Crc32cMethodHandles

# JNA — pty4j loads native PTY via reflection; ProGuard strips Native.dispose and crashes Shell
-dontwarn java.awt.**
-keep class com.sun.jna.* { *; }
-keep class com.sun.jna.ptr.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep class com.sun.jna.platform.win32.** { *; }
-dontwarn com.sun.jna.platform.**

# pty4j / JediTerm — interactive adb shell terminal
-keep class com.pty4j.** { *; }
-keep class com.jediterm.** { *; }

# RSyntaxTextArea — main() calls installSmaliSyntaxHighlighting() at startup
-keep class org.fife.ui.rsyntaxtextarea.** { *; }
-keep class fun.abbas.wps_adb.ui.editor.SmaliTokenMaker { *; }
