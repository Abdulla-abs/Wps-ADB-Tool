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

# JADX — release ProGuard was stripping plugin jars (jadx-dex-input left as empty
# META-INF only) and enum $VALUES, so JadxArgs.<init> throws ClassCastException and
# smali→Java / DEX→Java appear to do nothing in the packaged EXE.
-keep class jadx.** { *; }
-keep class io.github.skylot.raung.** { *; }
-keepclassmembers enum jadx.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
}
-keepclassmembers enum io.github.skylot.raung.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
}
-keep class ** implements jadx.api.plugins.JadxPlugin { *; }
# ProGuard copies resources by default; adapt ServiceLoader descriptors if names change.
# (R8's `-keep resource` is not valid in ProGuard 7.x used by Compose Desktop.)
-adaptresourcefilecontents META-INF/services/jadx.api.plugins.JadxPlugin
-dontwarn jadx.**
-dontwarn io.github.skylot.raung.**

# smali / baksmali / dexlib2 — used by DEX editor assemble/disassemble paths
-keep class org.jf.** { *; }
-keepclassmembers enum org.jf.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
}
-dontwarn org.jf.**

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
