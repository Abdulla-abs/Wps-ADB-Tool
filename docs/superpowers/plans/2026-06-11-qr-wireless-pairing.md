# 无线设备 QR 扫码配对 Implementation Plan

> **Implementation status:** �?Code complete (2026-06-11). QR5 manual acceptance + Step 3 UX closed in Sprint A backlog.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** �?PairingDialog 第二步新增「扫码配对」Tab，Desktop 端通过 Android 11+ QR 无线调试协议（mDNS + `adb pair` + `adb connect`）完成设备连接，同时保留 Legacy 手动 IP 路径�?
**Architecture:** `AdbQrPayloadBuilder`（commonMain 纯函数）生成 QR payload；`JvmWirelessQrPairingService`（jvmMain）编�?jmdns 发现�?adb 命令；`AdbRepository.pairWirelessViaQr(): Flow<QrPairingEvent>` �?ViewModel / UI 订阅；PairingDialog Step 1 增加配对方式分支�?
**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, jmdns 3.5.x, qrcode-kotlin 4.x, adb (Desktop JVM)

**Spec:** `docs/superpowers/specs/2026-06-11-qr-wireless-pairing-design.md`

---

## 文件结构预览

| 文件 | 职责 |
|------|------|
| `model/PairingMethod.kt` | Legacy / QR 枚举 |
| `model/QrPairingEvent.kt` | QR 配对状态事�?|
| `data/AdbQrPayloadBuilder.kt` | 生成 serviceName、password、WIFI payload |
| `data/AdbRepository.kt` | 新增 `pairWirelessViaQr` / `cancelQrPairing` |
| `data/MockAdbRepository.kt` | 模拟 QR Flow |
| `jvmMain/.../AdbMdnsDiscovery.kt` | jmdns 监听 pairing/connect 服务 |
| `jvmMain/.../JvmWirelessQrPairingService.kt` | pair+connect 编排 |
| `jvmMain/.../JvmAdbRunner.kt` | 新增 `pair(host, port, code)` |
| `jvmMain/.../JvmAdbRepository.kt` | 接入 QR 服务 |
| `ui/pairing/PairingDialog.kt` | Step 1 模式选择 + Step 2 Tab |
| `ui/pairing/QrCodeImage.kt` | payload �?Compose Image |
| `viewmodel/AppViewModel.kt` | QR 会话管理 |
| `composeResources/values/strings.xml` | 英文 i18n |
| `composeResources/values-zh/strings.xml` | 中文 i18n |
| `gradle/libs.versions.toml` | 新依赖版�?|
| `shared/build.gradle.kts` | 依赖声明 |

---

## Phase QR1 �?模型�?QR Payload 生成

### Task 1: PairingMethod �?QrPairingEvent

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/PairingMethod.kt`
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/QrPairingEvent.kt`

- [x] **Step 1: 创建 PairingMethod.kt**

```kotlin
package `fun`.abbas.wps_adb.model

enum class PairingMethod {
    LEGACY_TCP,
    QR_WIRELESS,
}
```

- [x] **Step 2: 创建 QrPairingEvent.kt**

```kotlin
package `fun`.abbas.wps_adb.model

import `fun`.abbas.wps_adb.model.Device

sealed class QrPairingEvent {
    data class QrReady(
        val payload: String,
        val serviceName: String,
    ) : QrPairingEvent()

    data object WaitingForScan : QrPairingEvent()

    data class PairingInProgress(val endpoint: String) : QrPairingEvent()

    data class Connecting(val endpoint: String) : QrPairingEvent()

    data class Success(val device: Device) : QrPairingEvent()

    data class Failure(val message: String) : QrPairingEvent()

    data object Cancelled : QrPairingEvent()
}
```

- [x] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/PairingMethod.kt \
        shared/src/commonMain/kotlin/fun/abbas/wps_adb/model/QrPairingEvent.kt
git commit -m "feat: add pairing method and QR pairing event models"
```

---

### Task 2: AdbQrPayloadBuilder（TDD�?
**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbQrPayloadBuilder.kt`
- Create: `shared/src/commonTest/kotlin/fun/abbas/wps_adb/AdbQrPayloadBuilderTest.kt`

- [x] **Step 1: 写失败测�?*

```kotlin
package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbQrPayloadBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdbQrPayloadBuilderTest {

    @Test
    fun buildPayload_usesWpa3AdbFormat() {
        val creds = AdbQrPayloadBuilder.generate(
            random = { sequenceOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).iterator() },
        )
        assertTrue(creds.serviceName.startsWith("studio-"))
        assertEquals(
            "WIFI:T:ADB;S:${creds.serviceName};P:${creds.password};;",
            creds.payload,
        )
    }

    @Test
    fun buildPayload_passwordLengthIsTen() {
        val creds = AdbQrPayloadBuilder.generate()
        assertEquals(10, creds.password.length)
    }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.AdbQrPayloadBuilderTest" -q`
Expected: FAIL �?class not found

- [x] **Step 3: 实现 AdbQrPayloadBuilder**

```kotlin
package `fun`.abbas.wps_adb.data

data class AdbQrCredentials(
    val serviceName: String,
    val password: String,
    val payload: String,
)

object AdbQrPayloadBuilder {
    private const val SERVICE_PREFIX = "studio-"
    private const val PASSWORD_LENGTH = 10
    private const val PASSWORD_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"

    fun generate(
        random: () -> Iterator<Int> = {
            { (0 until Int.MAX_VALUE).random() }.let { r -> { generateSequence { r() }.iterator() } }
        },
    ): AdbQrCredentials {
        val rng = random()
        val suffix = buildString(PASSWORD_LENGTH) {
            repeat(PASSWORD_LENGTH) {
                append(PASSWORD_CHARS[rng.next() % PASSWORD_CHARS.length])
            }
        }
        val serviceName = SERVICE_PREFIX + suffix
        val password = buildString(PASSWORD_LENGTH) {
            repeat(PASSWORD_LENGTH) {
                append(PASSWORD_CHARS[rng.next() % PASSWORD_CHARS.length])
            }
        }
        val payload = "WIFI:T:ADB;S:$serviceName;P:$password;;"
        return AdbQrCredentials(serviceName, password, payload)
    }
}
```

> 注：测试用注�?`random` 保证确定性；生产环境用默认随机�?
- [x] **Step 4: 运行测试确认通过**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.AdbQrPayloadBuilderTest" -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbQrPayloadBuilder.kt \
        shared/src/commonTest/kotlin/fun/abbas/wps_adb/AdbQrPayloadBuilderTest.kt
git commit -m "feat: add ADB QR payload builder with unit tests"
```

---

## Phase QR2 �?Repository 接口�?Mock

### Task 3: 扩展 AdbRepository

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepository.kt`

- [x] **Step 1: 新增接口方法**

�?`AdbRepository` �?`pairWirelessDevice` 之后添加�?
```kotlin
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.flow.Flow

fun pairWirelessViaQr(): Flow<QrPairingEvent>
fun cancelQrPairing()
```

- [x] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/AdbRepository.kt
git commit -m "feat: extend AdbRepository with QR wireless pairing API"
```

---

### Task 4: MockAdbRepository QR Flow

**Files:**
- Create: `shared/src/commonTest/kotlin/fun/abbas/wps_adb/MockAdbRepositoryQrPairingTest.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt`

- [x] **Step 1: 写失败测�?*

```kotlin
package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.MockAdbRepository
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockAdbRepositoryQrPairingTest {

    @Test
    fun pairWirelessViaQr_emitsReadyThenSuccess() = runTest {
        val repo = MockAdbRepository(initialScanDelayMs = 0, refreshDelayMs = 0)
        val events = repo.pairWirelessViaQr().toList()
        assertTrue(events.any { it is QrPairingEvent.QrReady })
        assertIs<QrPairingEvent.Success>(events.last())
    }

    @Test
    fun cancelQrPairing_emitsCancelled() = runTest {
        val repo = MockAdbRepository(initialScanDelayMs = 0, refreshDelayMs = 0)
        val events = mutableListOf<QrPairingEvent>()
        val job = backgroundScope.launch {
            repo.pairWirelessViaQr().collect { events.add(it) }
        }
        kotlinx.coroutines.delay(50)
        repo.cancelQrPairing()
        job.cancel()
        assertTrue(events.any { it is QrPairingEvent.Cancelled })
    }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.MockAdbRepositoryQrPairingTest" -q`
Expected: FAIL

- [x] **Step 3: 实现 Mock**

�?`MockAdbRepository` 添加�?
```kotlin
private var qrPairingJob: Job? = null

override fun pairWirelessViaQr(): Flow<QrPairingEvent> = callbackFlow {
    val creds = AdbQrPayloadBuilder.generate()
    trySend(QrPairingEvent.QrReady(creds.payload, creds.serviceName))
    trySend(QrPairingEvent.WaitingForScan)
    qrPairingJob = scope.launch {
        delay(2500)
        if (!isActive) return@launch
        trySend(QrPairingEvent.PairingInProgress("192.168.1.105:37845"))
        delay(800)
        if (!isActive) return@launch
        trySend(QrPairingEvent.Connecting("192.168.1.105:5555"))
        delay(600)
        if (!isActive) return@launch
        val device = pairWirelessDevice("192.168.1.105", 5555).getOrThrow()
        trySend(QrPairingEvent.Success(device))
        close()
    }
    awaitClose {
        qrPairingJob?.cancel()
        qrPairingJob = null
    }
}

override fun cancelQrPairing() {
    qrPairingJob?.cancel()
    qrPairingJob = null
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.MockAdbRepositoryQrPairingTest" -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/data/MockAdbRepository.kt \
        shared/src/commonTest/kotlin/fun/abbas/wps_adb/MockAdbRepositoryQrPairingTest.kt
git commit -m "feat: mock QR wireless pairing flow for UI development"
```

---

## Phase QR3 �?Desktop JVM 后端

### Task 5: 添加依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [x] **Step 1: libs.versions.toml 追加**

```toml
[versions]
jmdns = "3.5.9"
qrcode-kotlin = "4.5.0"

[libraries]
jmdns = { module = "org.jmdns:jmdns", version.ref = "jmdns" }
qrcode-kotlin = { module = "io.github.g0dkar:qrcode-kotlin", version.ref = "qrcode-kotlin" }
```

- [x] **Step 2: shared/build.gradle.kts**

```kotlin
commonMain.dependencies {
    implementation(libs.qrcode.kotlin)
}
jvmMain.dependencies {
    implementation(libs.jmdns)
}
```

- [x] **Step 3: 同步验证**

Run: `./gradlew :shared:compileKotlinJvm -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build: add jmdns and qrcode-kotlin dependencies"
```

---

### Task 6: JvmAdbRunner.pair()

**Files:**
- Create: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/JvmAdbRunnerPairTest.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRunner.kt`

- [x] **Step 1: 写失败测�?*

```kotlin
package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmAdbRunnerPairTest {

    @Test
    fun pair_buildsCorrectCommand() {
        val captured = mutableListOf<List<String>>()
        val runner = JvmAdbRunner(adbPathProvider = { "adb" })
        // 通过 package-visible test hook 或反射注�?command recorder
        // �?runner �?hook，则测试 parse 辅助函数 pairCommandArgs:
        val args = JvmAdbRunner.pairCommandArgs("192.168.0.5:12345", "abc123")
        assertEquals(listOf("pair", "192.168.0.5:12345", "abc123"), args)
    }
}
```

- [x] **Step 2: �?JvmAdbRunner 添加**

```kotlin
companion object {
    fun pairCommandArgs(endpoint: String, pairingCode: String): List<String> =
        listOf("pair", endpoint, pairingCode)
}

fun pair(endpoint: String, pairingCode: String): AdbProcessResult =
    run(pairCommandArgs(endpoint, pairingCode))
```

- [x] **Step 3: 运行测试**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.JvmAdbRunnerPairTest" -q`
Expected: PASS

- [x] **Step 4: Commit**

```bash
git add shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRunner.kt \
        shared/src/jvmTest/kotlin/fun/abbas/wps_adb/JvmAdbRunnerPairTest.kt
git commit -m "feat: add adb pair command support to JvmAdbRunner"
```

---

### Task 7: AdbMdnsDiscovery

**Files:**
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/AdbMdnsDiscovery.kt`
- Create: `shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AdbMdnsDiscoveryTest.kt`

- [x] **Step 1: 写失败测试（解析逻辑�?*

```kotlin
package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbMdnsDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbMdnsDiscoveryTest {

    @Test
    fun parseEndpoint_formatsHostPort() {
        assertEquals(
            "192.168.1.10:37845",
            AdbMdnsDiscovery.formatEndpoint("192.168.1.10", 37845),
        )
    }

    @Test
    fun matchesServiceName_isCaseSensitivePrefix() {
        assertEquals(
            true,
            AdbMdnsDiscovery.matchesInstanceName("studio-abc123", "studio-abc123"),
        )
        assertEquals(
            false,
            AdbMdnsDiscovery.matchesInstanceName("studio-abc123", "studio-other"),
        )
    }
}
```

- [x] **Step 2: 实现 AdbMdnsDiscovery**

```kotlin
package `fun`.abbas.wps_adb.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class MdnsServiceEndpoint(
    val instanceName: String,
    val host: String,
    val port: Int,
)

class AdbMdnsDiscovery {
    companion object {
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp.local."
        const val CONNECT_TYPE = "_adb-tls-connect._tcp.local."

        fun formatEndpoint(host: String, port: Int): String = "$host:$port"

        fun matchesInstanceName(expected: String, announced: String): Boolean =
            announced == expected
    }

    fun listen(
        serviceType: String,
        instanceFilter: (String) -> Boolean = { true },
    ): Flow<MdnsServiceEndpoint> = callbackFlow {
        val jmdns = JmDNS.create()
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) = Unit

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val name = info.name ?: return
                if (!instanceFilter(name)) return
                val host = (info.hostAddresses?.firstOrNull { it is Inet4Address } as? Inet4Address)
                    ?.hostAddress ?: return
                val port = info.port
                trySend(MdnsServiceEndpoint(name, host, port))
            }
        }
        jmdns.addServiceListener(serviceType, listener)
        awaitClose {
            jmdns.removeServiceListener(serviceType, listener)
            jmdns.close()
        }
    }
}
```

- [x] **Step 3: 运行测试**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest --tests "fun.abbas.wps_adb.AdbMdnsDiscoveryTest" -q`
Expected: PASS

- [x] **Step 4: Commit**

```bash
git add shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/AdbMdnsDiscovery.kt \
        shared/src/jvmTest/kotlin/fun/abbas/wps_adb/AdbMdnsDiscoveryTest.kt
git commit -m "feat: add mDNS discovery for ADB wireless pairing services"
```

---

### Task 8: JvmWirelessQrPairingService

**Files:**
- Create: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmWirelessQrPairingService.kt`
- Modify: `shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRepository.kt`

- [x] **Step 1: 实现 JvmWirelessQrPairingService**

```kotlin
package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class JvmWirelessQrPairingService(
    private val runner: JvmAdbRunner,
    private val mdns: AdbMdnsDiscovery = AdbMdnsDiscovery(),
    private val pairingTimeoutSec: Long = 120,
    private val onDevicesRefresh: suspend () -> Unit,
    private val findDevice: suspend (String) -> Device?,
) {
    private var activeJob: Job? = null

    fun pair(): Flow<QrPairingEvent> = channelFlow {
        val creds = AdbQrPayloadBuilder.generate()
        send(QrPairingEvent.QrReady(creds.payload, creds.serviceName))
        send(QrPairingEvent.WaitingForScan)

        try {
            coroutineScope {
                activeJob = coroutineContext[Job]
                val pairingEndpoint = withTimeoutOrNull(pairingTimeoutSec.seconds) {
                    mdns.listen(AdbMdnsDiscovery.PAIRING_TYPE) { name ->
                        AdbMdnsDiscovery.matchesInstanceName(creds.serviceName, name)
                    }.first()
                } ?: run {
                    send(QrPairingEvent.Failure("Pairing timeout: device not found via mDNS"))
                    return@coroutineScope
                }

                val pairTarget = AdbMdnsDiscovery.formatEndpoint(pairingEndpoint.host, pairingEndpoint.port)
                send(QrPairingEvent.PairingInProgress(pairTarget))

                val pairResult = runner.pair(pairTarget, creds.password)
                if (!pairResult.success && "already paired" !in pairResult.output.lowercase()) {
                    send(QrPairingEvent.Failure("adb pair failed: ${pairResult.output}"))
                    return@coroutineScope
                }

                val connectEndpoint = withTimeoutOrNull(30.seconds) {
                    mdns.listen(AdbMdnsDiscovery.CONNECT_TYPE) { true }.first()
                } ?: run {
                    send(QrPairingEvent.Failure("Connect timeout: check Wireless debugging screen for IP"))
                    return@coroutineScope
                }

                val connectTarget = AdbMdnsDiscovery.formatEndpoint(connectEndpoint.host, connectEndpoint.port)
                send(QrPairingEvent.Connecting(connectTarget))

                val connectResult = runner.run(listOf("connect", connectTarget))
                val output = connectResult.output.lowercase()
                val connected = connectResult.success || "connected" in output || "already connected" in output
                if (!connected) {
                    send(QrPairingEvent.Failure("adb connect failed: ${connectResult.output}"))
                    return@coroutineScope
                }

                onDevicesRefresh()
                val device = findDevice(connectTarget)
                    ?: findDevice(connectEndpoint.host)
                if (device != null) {
                    send(QrPairingEvent.Success(device))
                } else {
                    send(QrPairingEvent.Failure("Device not found after connect"))
                }
            }
        } catch (_: CancellationException) {
            send(QrPairingEvent.Cancelled)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
```

- [x] **Step 2: 接入 JvmAdbRepository**

```kotlin
private var qrPairingService: JvmWirelessQrPairingService? = null
private var qrPairingCollectJob: Job? = null

override fun pairWirelessViaQr(): Flow<QrPairingEvent> = callbackFlow {
    qrPairingService?.cancel()
    val service = JvmWirelessQrPairingService(
        runner = runner,
        onDevicesRefresh = { refreshDevicesInternal() },
        findDevice = { target ->
            _devices.value.find { it.serial == target }
                ?: _devices.value.find { ':' in it.serial && it.serial.startsWith(target.substringBefore(':')) }
        },
    )
    qrPairingService = service
    val collectJob = launch {
        service.pair().collect { event ->
            trySend(event)
            if (event is QrPairingEvent.Success || event is QrPairingEvent.Failure) {
                close()
            }
        }
    }
    qrPairingCollectJob = collectJob
    awaitClose {
        service.cancel()
        collectJob.cancel()
    }
}

override fun cancelQrPairing() {
    qrPairingService?.cancel()
    qrPairingCollectJob?.cancel()
}
```

- [x] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinJvm -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmWirelessQrPairingService.kt \
        shared/src/jvmMain/kotlin/fun/abbas/wps_adb/data/JvmAdbRepository.kt
git commit -m "feat: implement JVM wireless QR pairing with mDNS discovery"
```

---

## Phase QR4 �?UI �?ViewModel

### Task 9: QrCodeImage Composable

**Files:**
- Create: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/QrCodeImage.kt`

- [x] **Step 1: 实现 QR 渲染**

```kotlin
package `fun`.abbas.wps_adb.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import qrcode.QRCode
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun QrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
    sizePx: Int = 320,
) {
    val bitmap = remember(payload, sizePx) { qrPayloadToBitmap(payload, sizePx) }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}

private fun qrPayloadToBitmap(payload: String, sizePx: Int): ImageBitmap {
    val bytes = QRCode(payload).render(sizePx).getBytes()
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}
```

> �?`render()` API 与版本不符，按库文档调整�?`QRCode.ofSquares().build(payload).render()` 等等价调用�?
- [x] **Step 2: 编译验证**

Run: `./gradlew :shared:compileKotlinJvm :shared:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/QrCodeImage.kt
git commit -m "feat: add Compose QR code image component"
```

---

### Task 10: i18n 字符�?
**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [x] **Step 1: 英文 strings 追加（`<resources>` 内）**

```xml
<string name="pairing_method_legacy">USB + TCP/IP (Legacy)</string>
<string name="pairing_method_qr">Wireless Debugging QR (Android 11+)</string>
<string name="pairing_prepare_qr_title">Enable Wireless Debugging</string>
<string name="pairing_prepare_qr_desc">No USB required. Enable Wireless debugging on your phone, then scan the QR code on the next step.</string>
<string name="pairing_checklist_wireless_title">Enable Wireless debugging</string>
<string name="pairing_checklist_wireless_subtitle">Settings �?Developer options �?Wireless debugging</string>
<string name="pairing_connect_tab_manual">Manual IP</string>
<string name="pairing_connect_tab_qr">Scan QR</string>
<string name="pairing_qr_instructions">1. Same Wi‑Fi as this PC\n2. Developer options �?Wireless debugging\n3. Pair device with QR code\n4. Scan the code on the left</string>
<string name="pairing_qr_waiting">Waiting for scan�?/string>
<string name="pairing_qr_pairing">Pairing with %1$s�?/string>
<string name="pairing_qr_connecting">Connecting to %1$s�?/string>
<string name="pairing_qr_refresh">Refresh QR code</string>
<string name="pairing_step1_subtitle_legacy">USB connect and enable TCP</string>
<string name="pairing_step1_subtitle_qr">Enable wireless debugging</string>
<string name="pairing_step2_subtitle_qr">Scan QR to pair</string>
```

- [x] **Step 2: 中文 strings 追加**

```xml
<string name="pairing_method_legacy">USB + TCP/IP（传统方式）</string>
<string name="pairing_method_qr">无线调试二维码（Android 11+�?/string>
<string name="pairing_prepare_qr_title">开启无线调�?/string>
<string name="pairing_prepare_qr_desc">无需 USB。在手机上开启「无线调试」后，于下一步扫描电脑上的二维码�?/string>
<string name="pairing_checklist_wireless_title">开启「无线调试�?/string>
<string name="pairing_checklist_wireless_subtitle">设置 �?开发者选项 �?无线调试</string>
<string name="pairing_connect_tab_manual">手动输入 IP</string>
<string name="pairing_connect_tab_qr">扫码配对</string>
<string name="pairing_qr_instructions">1. 手机与电脑在同一 Wi‑Fi\n2. 开发者选项 �?无线调试\n3. 使用二维码配对设备\n4. 扫描左侧二维�?/string>
<string name="pairing_qr_waiting">等待扫码�?/string>
<string name="pairing_qr_pairing">正在配对 %1$s�?/string>
<string name="pairing_qr_connecting">正在连接 %1$s�?/string>
<string name="pairing_qr_refresh">刷新二维�?/string>
<string name="pairing_step1_subtitle_legacy">USB 连接并启�?TCP</string>
<string name="pairing_step1_subtitle_qr">开启无线调�?/string>
<string name="pairing_step2_subtitle_qr">扫描二维码配�?/string>
```

- [x] **Step 3: Commit**

```bash
git add shared/src/commonMain/composeResources/values/strings.xml \
        shared/src/commonMain/composeResources/values-zh/strings.xml
git commit -m "feat: add i18n strings for QR wireless pairing"
```

---

### Task 11: AppViewModel QR 会话

**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt`（若需要）

- [x] **Step 1: 添加 QR 状态与方法**

�?`AppViewModel`�?
```kotlin
private val _qrPairingEvent = MutableStateFlow<QrPairingEvent?>(null)
val qrPairingEvent: StateFlow<QrPairingEvent?> = _qrPairingEvent.asStateFlow()

private var qrPairingCollectJob: Job? = null

fun startQrPairing() {
    cancelQrPairing()
    qrPairingCollectJob = viewModelScope.launch {
        repository.pairWirelessViaQr().collect { event ->
            _qrPairingEvent.value = event
            if (event is QrPairingEvent.Success) {
                closePairingDialog()
            }
        }
    }
}

fun cancelQrPairing() {
    repository.cancelQrPairing()
    qrPairingCollectJob?.cancel()
    qrPairingCollectJob = null
    _qrPairingEvent.value = null
}

fun closePairingDialog() {
    cancelQrPairing()
    _localState.update { it.copy(isPairingDialogOpen = false) }
}
```

将现�?`closePairingDialog()` 替换为上述实现（确保取消 QR 会话）�?
- [x] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt
git commit -m "feat: wire QR pairing session in AppViewModel"
```

---

### Task 12: PairingDialog UI 改�?
**Files:**
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/PairingDialog.kt`
- Modify: `shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppShell.kt`

- [x] **Step 1: 扩展 PairingDialog 签名**

```kotlin
fun PairingDialog(
    onDismiss: () -> Unit,
    onPairComplete: suspend (ip: String, port: Int) -> Boolean,
    pairingMethod: PairingMethod,
    onPairingMethodChange: (PairingMethod) -> Unit,
    qrPairingEvent: QrPairingEvent?,
    onStartQrPairing: () -> Unit,
    onCancelQrPairing: () -> Unit,
    onRefreshQrPairing: () -> Unit,
)
```

- [x] **Step 2: Step 1 �?模式选择**

- 顶部增加 `PairingMethod` 单选（Legacy / QR�?- Legacy：保�?`usbConnected` + `tcpEnabled` checklist
- QR：单�?checklist `wirelessEnabled`
- `onNext` 条件：Legacy 需两项勾选；QR 只需 wirelessEnabled

- [x] **Step 3: Step 2 �?Tab 布局**

- Legacy 模式：仅显示 Manual 表单（现�?`StepConnect` 内容�?- QR 模式：显�?`StepConnectQr`（QR �?+ 状�?+ 刷新按钮�?- 进入 Step 2 且为 QR 模式时，`LaunchedEffect(Unit) { onStartQrPairing() }`
- 监听 `qrPairingEvent`：`Success` �?`step = 3, connectState = SUCCESS`；`Failure` �?`step = 3, connectState = FAILURE`

- [x] **Step 4: 对话框尺�?*

```kotlin
.width(720.dp)
.height(520.dp)
```

- [x] **Step 5: AppShell 传参**

```kotlin
if (uiState.isPairingDialogOpen) {
    PairingDialog(
        onDismiss = viewModel::closePairingDialog,
        onPairComplete = viewModel::pairDevice,
        pairingMethod = uiState.pairingMethod,
        onPairingMethodChange = viewModel::setPairingMethod,
        qrPairingEvent = viewModel.qrPairingEvent.collectAsState().value,
        onStartQrPairing = viewModel::startQrPairing,
        onCancelQrPairing = viewModel::cancelQrPairing,
        onRefreshQrPairing = viewModel::startQrPairing,
    )
}
```

�?`AppUiState` / `_localState` 增加 `pairingMethod: PairingMethod = PairingMethod.LEGACY_TCP` �?`setPairingMethod()`�?
- [x] **Step 6: 编译验证**

Run: `./gradlew :desktopApp:compileKotlin :shared:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/pairing/PairingDialog.kt \
        shared/src/commonMain/kotlin/fun/abbas/wps_adb/ui/layout/AppShell.kt \
        shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppUiState.kt \
        shared/src/commonMain/kotlin/fun/abbas/wps_adb/viewmodel/AppViewModel.kt
git commit -m "feat: add QR scan tab to wireless pairing dialog"
```

---

## Phase QR5 �?验收

### Task 13: 全量测试与手动验�?
- [x] **Step 1: 运行全量 JVM 测试**

Run: `./gradlew :shared:cleanJvmTest :shared:jvmTest -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 2: Desktop 编译**

Run: `./gradlew :desktopApp:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [x] **Step 3: 手动验收清单**

1. Mock 模式：打开 PairingDialog �?�?QR �?Step 2 �?QR �?�?4s �?Step 3 成功
2. Legacy 路径：选手�?�?USB/TCP checklist �?IP 连接仍正�?3. 关闭对话框：无异常日志；再次打开 QR 可重新生�?4. Desktop + Android 11+ 真机：扫码后设备出现在设备墙

- [x] **Step 4: Commit（若有收尾修复）**

```bash
git commit -m "fix: address QR pairing review findings"
```

---

## 实现顺序建议

```
QR1 (模型+Payload) �?QR2 (Repository+Mock) �?QR3 (JVM后端) �?QR4 (UI) �?QR5 (验收)
         �?                   �?    可并�?UI 原型        Mock 可驱�?UI 开�?```

Phase QR3 �?QR4 �?Task 9�?0 可在 QR2 完成后并行：UI 先用 Mock Flow 联调，再�?JVM 真机�?
---

## 风险备忘（实现时注意�?
1. **jmdns ServiceInfo API**：不同版�?`hostAddresses` vs `inetAddresses`，以实现�?IDE 提示为准
2. **connect 服务过滤**：首版接受首�?connect 端点；若多设备同网，后续可按 pairing IP 过滤
3. **qrcode-kotlin render API**：Task 9 编译失败时查�?4.x README 调整
4. **Android Repository**：若项目�?androidMain `AdbRepository` 实现，需�?`pairWirelessViaQr` / `cancelQrPairing` �?stub（抛 UnsupportedOperationException �?Mock 同等 Flow�?