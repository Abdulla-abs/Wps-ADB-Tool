package `fun`.abbas.wps_adb.ui.groups



import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import `fun`.abbas.wps_adb.i18n.localizedName

import `fun`.abbas.wps_adb.model.BatchActionParams

import `fun`.abbas.wps_adb.model.Device

import `fun`.abbas.wps_adb.model.DeviceStatus

import `fun`.abbas.wps_adb.model.DeviceType

import `fun`.abbas.wps_adb.model.FilterTab

import `fun`.abbas.wps_adb.platform.pickApkFile

import `fun`.abbas.wps_adb.theme.CarbonColors

import kotlinx.coroutines.launch

import org.jetbrains.compose.resources.stringResource

import wpsadbtool.shared.generated.resources.*

import wpsadbtool.shared.generated.resources.Res



private data class BatchConfig(

    val actionKey: String,

    val actionName: String,

    val msgInitiating: String,

)



@Composable

fun GroupManagementScreen(

    devices: List<Device>,

    onBatchAction: suspend (FilterTab, String, BatchActionParams) -> List<String>,

    modifier: Modifier = Modifier,

) {

    var activeGroup by remember { mutableStateOf(FilterTab.ALL) }

    var runningAction by remember { mutableStateOf<String?>(null) }

    var percent by remember { mutableFloatStateOf(0f) }

    var batchConsole by remember { mutableStateOf<List<String>>(emptyList()) }

    var showPackageDialog by remember { mutableStateOf(false) }

    var packageNameInput by remember { mutableStateOf("com.android.settings") }

    val scope = rememberCoroutineScope()



    val actionInstall = stringResource(Res.string.groups_action_install)

    val actionWipe = stringResource(Res.string.groups_action_wipe)

    val actionReboot = stringResource(Res.string.groups_action_reboot)

    val actionBattery = stringResource(Res.string.groups_action_battery)

    val initiatingPrefix = stringResource(Res.string.groups_console_initiating, "").trimEnd(' ', ':')

    val apkCancelled = stringResource(Res.string.groups_apk_pick_cancelled)



    val groups = listOf(

        Triple(FilterTab.ALL, stringResource(Res.string.groups_all_connected), devices.count { it.status == DeviceStatus.ONLINE }),

        Triple(FilterTab.PHYSICAL, stringResource(Res.string.groups_qa_physical), devices.count { it.status == DeviceStatus.ONLINE && it.type == DeviceType.PHYSICAL }),

        Triple(FilterTab.EMULATORS, stringResource(Res.string.groups_emulator_bench), devices.count { it.status == DeviceStatus.ONLINE && it.type == DeviceType.EMULATOR }),

    )



    val activeDevices = devices.filter { d ->

        d.status == DeviceStatus.ONLINE && when (activeGroup) {

            FilterTab.PHYSICAL -> d.type == DeviceType.PHYSICAL

            FilterTab.EMULATORS -> d.type == DeviceType.EMULATOR

            FilterTab.ALL -> true

        }

    }



    val installBatch = BatchConfig(

        actionKey = "install-package",

        actionName = actionInstall,

        msgInitiating = stringResource(Res.string.groups_console_initiating, actionInstall),

    )

    val wipeBatch = BatchConfig(

        actionKey = "pm clear",

        actionName = actionWipe,

        msgInitiating = stringResource(Res.string.groups_console_initiating, actionWipe),

    )

    val rebootBatch = BatchConfig(

        actionKey = "reboot",

        actionName = actionReboot,

        msgInitiating = stringResource(Res.string.groups_console_initiating, actionReboot),

    )

    val batteryBatch = BatchConfig(

        actionKey = "dumpsys battery",

        actionName = actionBattery,

        msgInitiating = stringResource(Res.string.groups_console_initiating, actionBattery),

    )



    fun runBatch(config: BatchConfig, params: BatchActionParams = BatchActionParams()) {

        if (activeDevices.isEmpty()) return

        runningAction = config.actionName

        percent = 0.1f

        batchConsole = listOf(config.msgInitiating)

        scope.launch {

            try {

                val lines = onBatchAction(activeGroup, config.actionKey, params)

                batchConsole = batchConsole + lines

                percent = 1f

            } finally {

                runningAction = null

            }

        }

    }



    if (showPackageDialog) {

        AlertDialog(

            onDismissRequest = { showPackageDialog = false },

            title = { Text(stringResource(Res.string.groups_dialog_package_title)) },

            text = {

                OutlinedTextField(

                    value = packageNameInput,

                    onValueChange = { packageNameInput = it },

                    label = { Text(stringResource(Res.string.groups_dialog_package_label)) },

                    singleLine = true,

                    modifier = Modifier.fillMaxWidth(),

                )

            },

            confirmButton = {

                Button(

                    onClick = {

                        showPackageDialog = false

                        runBatch(wipeBatch, BatchActionParams(packageName = packageNameInput.trim()))

                    },

                    enabled = packageNameInput.isNotBlank(),

                ) {

                    Text(stringResource(Res.string.groups_dialog_confirm))

                }

            },

            dismissButton = {

                TextButton(onClick = { showPackageDialog = false }) {

                    Text(stringResource(Res.string.groups_dialog_cancel))

                }

            },

        )

    }



    Row(

        modifier = modifier.padding(24.dp).fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(24.dp),

    ) {

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text(stringResource(Res.string.groups_select_target), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)

            groups.forEach { (tab, name, count) ->

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clickable { activeGroup = tab }

                        .border(1.dp, if (activeGroup == tab) CarbonColors.Primary else CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))

                        .background(if (activeGroup == tab) CarbonColors.Primary.copy(alpha = 0.05f) else CarbonColors.SurfaceContainer)

                        .padding(16.dp),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically,

                ) {

                    Column {

                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)

                        Text(stringResource(Res.string.groups_group_suffix, tab.localizedName()), fontSize = 10.sp, color = CarbonColors.Outline)

                    }

                    Text(stringResource(Res.string.groups_online_count, count), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)

                }

            }

        }



        Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(

                stringResource(Res.string.groups_batch_deck, activeDevices.size),

                fontSize = 16.sp,

                fontWeight = FontWeight.Bold,

                color = CarbonColors.OnSurface,

            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                BatchButton(

                    stringResource(Res.string.groups_batch_sideload),

                    runningAction != null,

                    {

                        scope.launch {

                            val apkPath = pickApkFile()

                            if (apkPath == null) {

                                batchConsole = listOf(apkCancelled)

                                return@launch

                            }

                            runBatch(installBatch, BatchActionParams(apkPath = apkPath))

                        }

                    },

                    Modifier.weight(1f),

                )

                BatchButton(

                    stringResource(Res.string.groups_batch_wipe_cache),

                    runningAction != null,

                    {

                        packageNameInput = "com.android.settings"

                        showPackageDialog = true

                    },

                    Modifier.weight(1f),

                )

            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                BatchButton(stringResource(Res.string.groups_batch_reboot), runningAction != null, { runBatch(rebootBatch) }, Modifier.weight(1f))

                BatchButton(stringResource(Res.string.groups_batch_battery), runningAction != null, { runBatch(batteryBatch) }, Modifier.weight(1f))

            }



            if (runningAction != null) {

                val actionLabel = runningAction!!

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {

                    Text(stringResource(Res.string.groups_in_progress, actionLabel), fontSize = 12.sp, color = CarbonColors.OnSurfaceVariant)

                    Text("${(percent * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = CarbonColors.Primary)

                }

                LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth(), color = CarbonColors.Primary)

            }



            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .height(140.dp)

                    .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(12.dp))

                    .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(12.dp))

                    .padding(12.dp)

                    .verticalScroll(rememberScrollState()),

            ) {

                if (batchConsole.isEmpty()) {

                    Text(stringResource(Res.string.groups_console_empty), fontSize = 11.sp, color = CarbonColors.Outline, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                } else {

                    batchConsole.forEach { line ->

                        Text(

                            line,

                            fontSize = 10.sp,

                            fontFamily = FontFamily.Monospace,

                            color = when {

                                line.startsWith("✓") || line.startsWith("[OK]") -> CarbonColors.Primary

                                line.startsWith(initiatingPrefix) -> CarbonColors.Secondary

                                line.startsWith("[FAIL]") -> CarbonColors.Error

                                else -> CarbonColors.OnSurfaceVariant

                            },

                        )

                    }

                }

            }

        }

    }

}



@Composable

private fun BatchButton(label: String, disabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {

    Text(

        label,

        fontSize = 12.sp,

        fontWeight = FontWeight.Bold,

        color = if (disabled) CarbonColors.Outline.copy(alpha = 0.4f) else CarbonColors.OnSurface,

        modifier = modifier

            .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))

            .background(CarbonColors.SurfaceContainerLow)

            .clickable(enabled = !disabled, onClick = onClick)

            .padding(16.dp),

    )

}


