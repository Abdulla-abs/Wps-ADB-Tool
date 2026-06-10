import { Device, ADBLog } from './types';

export const INITIAL_DEVICES: Device[] = [
  {
    id: 'pixel6',
    name: 'Pixel 6 - Test A',
    serial: '2201117PG',
    type: 'physical',
    connectionType: 'wifi',
    status: 'online',
    androidVersion: 'Android 13',
    batteryLevel: 85,
    isCharging: true,
    storageUsed: '108GB',
    storageTotal: '128GB',
    storagePercent: 85,
    screenshot: 'https://lh3.googleusercontent.com/aida-public/AB6AXuArxeJhmFHsT2MVqW446qyOJToROLQXO4X_AiTgP-v5qTgDbRfIroiXMqTC-dkOh0WNuBYOkKnD4XrXSzHeNvL86yqS8ftj-FMtrPL5zuK8BU-yAp8IybcWtTqRL9aEeBKBCKG-gvcG7DHilUVRkFCVkhJT5a2DzOa3bLXvnOXzQ3-WmEvok5s9UK6lkVHHODGYHqor_rEQ7xmH7lovV6pjohHrhDgMBjsAWshByn9l4cSvY9LkurlK9MwRtC1En4Y-50c2IWbVc0Fc',
    screenDescription: 'Social Feed Client UI',
    apps: [
      { name: 'PhotoFlow Feed', packageName: 'com.android.photoflow', icon: 'photo_library' },
      { name: 'System Settings', packageName: 'com.android.settings', icon: 'settings' },
      { name: 'ADB Control Server', packageName: 'org.droid.control', icon: 'terminal' }
    ],
    currentAppIndex: 0,
    activityLog: [
      'activity_manager: Starting activity: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] pkg=com.android.photoflow }',
      'dalvikvm: GC_CONCURRENT freed 2048K, 12% free 12340K/13824K',
      'photoflow: Refreshing network feed image indices...',
      'window_manager: Focus gained by com.android.photoflow/MainActivity'
    ]
  },
  {
    id: 'galaxys22',
    name: 'Galaxy S22 - QA',
    serial: 'SM-S901B',
    type: 'physical',
    connectionType: 'usb',
    status: 'online',
    androidVersion: 'Android 12',
    batteryLevel: 42,
    isCharging: true,
    storageUsed: '51GB',
    storageTotal: '128GB',
    storagePercent: 40,
    screenshot: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBRiqFBo6sRacj60La-ErYR6ZJffZNwZbscZ8ByV-wgIoy4xXrIaiJsFf2X0XKiSdiP8WM2x51ii-L2ZZeHiaAB_b2hRE6sV_gJG1KGmYgxtl9RO03qvaGyZbWtdsfZU0YnqAJmUrqHtk1RdUmafPVBob_hWhVX6WKD787qoaJUdNtyXGE5pGevbvQIeirKAvjBqP2Q4Xf_h6vDCYLAoJNPvpngES9z6Mu47ufV9lAUQABhM-t1CL5glNRBPH4RMpYB2qeEaWcxBfJi',
    screenDescription: 'Hardware Telemetry Monitor',
    apps: [
      { name: 'System Analytics Dashboard', packageName: 'com.sec.sys.analytics', icon: 'monitoring' },
      { name: 'Wi-Fi Analyzer Tool', packageName: 'com.sec.wifi.analyzer', icon: 'wifi' }
    ],
    currentAppIndex: 0,
    activityLog: [
      'sys_analytics: Service initialized successfully.',
      'cpu_monitor: Sampling complete: Core 0-7 active, Temp: 38C',
      'gpu_monitor: Vulkan render context loaded.',
      'power_manager: State changed from normal to idle saver.'
    ]
  },
  {
    id: 'pixel4api30',
    name: 'Pixel_4_API_30',
    serial: 'emulator-5554',
    type: 'emulator',
    connectionType: 'emulator',
    status: 'online',
    androidVersion: 'Android 11',
    batteryLevel: 100,
    isCharging: true,
    storageUsed: '12GB',
    storageTotal: '64GB',
    storagePercent: 18,
    screenshot: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDN_u3BGkuFm-6_CudwuktxqPZwmeOL83hudngZsLym1zBfIlRnOe2oK8OJGLhpgPrFda8UBQvFHLlqFHkJYjciv1cOZy8aExE9mU4UXvYyCoEIUKB83X6DkKGDem9-Z1D-FE4fMgLz-OIFnHerxgu973TTOekUpRGSQWY6VgQ0Spb9vI1c_c4iQFBA9t2hrDPgrRw5WR0W4JO4UR1A7ZgPZDXZfWCHjgZ2AdA4HCCifDK_0pTBi7BFx5LdJ3xyK8FuaUbRjtwd8AI9',
    screenDescription: 'ADB Log Console App',
    apps: [
      { name: 'Log Inspector Terminal', packageName: 'com.android.terminal.inspect', icon: 'code' },
      { name: 'AOSP Test Harness', packageName: 'org.aosp.testharness', icon: 'bug_report' }
    ],
    currentAppIndex: 0,
    activityLog: [
      'adb_daemon: Listening on interface localhost:5554',
      'emulator_x86: IPC buffer initialized with 4MB memory range.',
      'system_server: PackageManager found 143 installed packages.',
      'logcat_capture: Streaming active stream buffer...'
    ]
  },
  {
    id: 'oneplus9',
    name: 'OnePlus 9 - Debug',
    serial: 'OP721110',
    type: 'physical',
    connectionType: 'usb',
    status: 'offline',
    androidVersion: 'Android 13',
    batteryLevel: 0,
    isCharging: false,
    storageUsed: '0GB',
    storageTotal: '256GB',
    storagePercent: 0,
    screenshot: '',
    screenDescription: 'Device is offline',
    apps: [],
    currentAppIndex: 0,
    activityLog: [
      'adb_daemon: Connection closed by target client peer.',
      'hardware_udev: USB disconnect on port 5-2: OP721110'
    ]
  },
  {
    id: 'redminote10',
    name: 'Redmi Note 10',
    serial: 'M2101K7AG',
    type: 'physical',
    connectionType: 'usb',
    status: 'online',
    androidVersion: 'Android 12',
    batteryLevel: 67,
    isCharging: true,
    storageUsed: '84GB',
    storageTotal: '128GB',
    storagePercent: 65,
    screenshot: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCu1EKXMBtlcteTDD1nASX1ZbSjR0_rF68g3Wp4yFseOfsYCdv2G2W46A5zN3Dpx3FvhhjLApYTofuOydO8O2jtaKIFr5gG0q3zswkgOitr-Nivsij2JsRVTruJQ7Pg9Daaiux9OVLevQwgBTkbvinUNCAEmiKH22UwaUEOaMYrKttvAhmGQ2MmJZVM3zWB3jqW9RDHgQvTgktampVN0Me8KFb3XuZ5XPUr6b6wRuWn2xUPN5nD63OgzlmdBjTrwxSdOKvxaRCLmlFs',
    screenDescription: 'Map HUD Routing App',
    apps: [
      { name: 'HUD Map Navigation', packageName: 'com.redmi.hud.maps', icon: 'map' },
      { name: 'Waze Navigator SDK', packageName: 'com.waze.sdk', icon: 'explore' }
    ],
    currentAppIndex: 0,
    activityLog: [
      'nav_hud: Initializing high-precision GNSS sensors...',
      'nav_hud: Lock acquired from 11 GPS satellites (precision: 2.1m).',
      'location_service: Sending coordinate callback: lat=25.033, lon=121.565'
    ]
  }
];

export const INITIAL_LOGS: ADBLog[] = [
  { id: '1', timestamp: '10:14:19.402', tag: 'AdbDaemon', level: 'I', message: 'ADB Server version 1.0.41 starting...', deviceId: 'system' },
  { id: '2', timestamp: '10:14:19.421', tag: 'AdbDaemon', level: 'I', message: 'Binding socket listener to port localhost:5037', deviceId: 'system' },
  { id: '3', timestamp: '10:14:20.105', tag: 'ServiceTracker', level: 'D', message: 'Discovered connected device via USB: type physical [SM-S901B]', deviceId: 'galaxys22' },
  { id: '4', timestamp: '10:14:20.334', tag: 'EmulatorControl', level: 'D', message: 'Attaching local socket port 5554 to emulator daemon', deviceId: 'pixel4api30' },
  { id: '5', timestamp: '10:14:21.011', tag: 'ActivityManager', level: 'I', message: 'Device pixel6 is connected over local network (192.168.1.104:5555)', deviceId: 'pixel6' },
  { id: '6', timestamp: '10:14:21.554', tag: 'DeviceTracker', level: 'W', message: 'Connection timeout exceeded on interface OP721110. Flagging: OFFLINE', deviceId: 'oneplus9' },
  { id: '7', timestamp: '10:14:22.091', tag: 'ApkInstaller', level: 'I', message: 'Acomplished package scanning on user-pushed file tree', deviceId: 'system' },
  { id: '8', timestamp: '10:14:23.003', tag: 'LogcatService', level: 'V', message: 'Piping main system ring logs buffer containing 1,842 events...', deviceId: 'system' }
];

export const KMP_CODES = {
  'App.kt': `package org.droidcluster.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.droidcluster.desktop.theme.CarbonTheme
import org.droidcluster.desktop.ui.DeviceWall
import org.droidcluster.desktop.ui.Sidebar

/**
 * Compose Multiplatform Entry point for Windows, macOS and Linux versions.
 * Shared UI code renders natively using Skia graphics engine.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DroidCluster Desktop - Seamless Android Management",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        CarbonTheme {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Fixed 240dp Sidebar (Universal across Mac/Win)
                Sidebar(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight(),
                    onNavigate = { view ->
                        // Navigation handled in common code
                    }
                )

                // Main Adaptive Workspace Canvas
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    DeviceWall(
                        devices = sampleDevicesState,
                        onAddDeviceClicked = {
                            // Show pairing dialog modal
                        }
                    )
                }
            }
        }
    }
}`,

  'DeviceCard.kt': `package org.droidcluster.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.droidcluster.desktop.model.Device
import org.droidcluster.desktop.theme.PrimaryGreen
import org.droidcluster.desktop.theme.CarbonSurface

@Composable
fun DeviceCard(
    device: Device,
    onMirrorClicked: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3C4A3F), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = CarbonSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Model & Status dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = device.serial,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                
                // Android Glow status indicator
                val statusColor = if (device.status == "online") PrimaryGreen else Color.Red
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, RoundedCornerShape(percent = 50))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screen Mirroring Aspect Frame
            DeviceScreenMockup(screenshotUrl = device.screenshot)

            Spacer(modifier = Modifier.height(12.dp))

            // Footer parameters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = device.androidVersion, fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = if (device.status == "online") "\${device.batteryLevel}%" else "--%",
                    fontSize = 11.sp,
                    color = PrimaryGreen
                )
            }
        }
    }
}`,

  'ConnectionWizard.kt': `package org.droidcluster.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.droidcluster.desktop.ui.components.WizardStepIndicator
import org.droidcluster.desktop.ui.steps.PrepareStep
import org.droidcluster.desktop.ui.steps.InputAddressStep
import org.droidcluster.desktop.ui.steps.FinalizeHandshakeStep

@Composable
fun ConnectionWizardDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(640.dp).height(480.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Shared step indicator (Left panel)
                WizardStepIndicator(
                    activeStep = step,
                    modifier = Modifier.width(220.dp).fillMaxHeight()
                )
                
                // Multi-view Wizard controller (Right panel)
                Column(
                    modifier = Modifier.weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    when (step) {
                        1 -> PrepareStep(onNext = { step = 2 })
                        2 -> InputAddressStep(onNext = { step = 3 }, onBack = { step = 1 })
                        3 -> FinalizeHandshakeStep(
                            onSuccess = { onConnected() },
                            onBackToEdit = { step = 2 }
                        )
                    }
                }
            }
        }
    }
}`
};
