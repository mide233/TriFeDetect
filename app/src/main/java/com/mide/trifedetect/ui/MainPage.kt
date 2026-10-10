package com.mide.trifedetect.ui

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mide.trifedetect.FNode
import com.mide.trifedetect.Formula
import com.mide.trifedetect.MainActivity
import com.mide.trifedetect.R
import com.mide.trifedetect.TriFeProtocol
import com.mide.trifedetect.ui.theme.TriFeDetectTheme
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun Greeting(
    modifier: Modifier = Modifier,
    suggestBoxText: MutableState<String>,
    bluetoothThreadCtrl: MutableState<Boolean>,
    navPageNum: MutableState<Int>,
    displayNum: MutableState<String>
) {
    val context = LocalContext.current
    val ctx = context as? MainActivity
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    if (ctx != null && ctx.bluetoothSocket == null) {
        AlertDialog(
            onDismissRequest = {
                // 用户点击对话框以外或返回键时保持不变
            },
            title = {
                Text(
                    text = stringResource(R.string.disconnect),
                    fontWeight = FontWeight.W700
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.go_connect_first),
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { navPageNum.value = 1 },
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        fontWeight = FontWeight.W700,
                    )
                }
            },
            dismissButton = {}
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            if (ctx != null) {
                StatusCard(ctx)
            }
            Column(modifier = Modifier.padding(top = 20.dp, start = 30.dp, end = 30.dp)) {
                Text(text = stringResource(R.string.tri_fe_ppm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayNum.value,
                        textAlign = TextAlign.Center,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorSch.primary,
                        softWrap = false
                    )
                    val trend = ctx?.resultTrend() ?: 0
                    if (trend != 0) {
                        Icon(
                            imageVector = if (trend > 0) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (trend > 0) R.string.result_above_range
                                else R.string.result_below_range
                            ),
                            tint = if (trend > 0) colorSch.error else colorSch.tertiary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(38.dp)
                        )
                    }
                }
                if (ctx != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.raw_value_label) + ": " +
                                    ctx.formatValue(ctx.rawResult.value),
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.calibration_label) + ": " +
                                    ctx.formatValue(ctx.calibrationValue.value),
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.calibration_label) + ": " +
                                stringResource(
                                    if (ctx.hasCalibration.value) R.string.cal_status_saved
                                    else R.string.cal_status_none
                                ),
                        fontSize = 14.sp,
                        color = colorSch.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { ctx?.sendCalibration() },
                        enabled = ctx?.bluetoothSocket != null
                    ) {
                        Text(text = stringResource(R.string.btn_calibrate))
                    }
                    OutlinedButton(
                        onClick = { ctx?.requestStatus() },
                        enabled = ctx?.bluetoothSocket != null
                    ) {
                        Text(text = stringResource(R.string.btn_refresh_status))
                    }
                }
            }
            if (ctx?.showLog?.value == true) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colorSch.surfaceContainer,
                    ),
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = suggestBoxText.value,
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = {
                if (ctx != null) {
                    if (bluetoothThreadCtrl.value) {
                        ctx.sendStop()
                    } else {
                        ctx.sendStart()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            elevation = FloatingActionButtonDefaults.elevation(3.dp, 8.dp)
        ) {
            if (bluetoothThreadCtrl.value) {
                Icon(
                    painterResource(R.drawable.pause_24px),
                    contentDescription = "Detection Start/Stop"
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Detection Start/Stop"
                )
            }
        }
    }
}

/** 主页常驻状态条: 工作状态 / 错误 / 进度 / 电量 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun StatusCard(ctx: MainActivity) {
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(ctx)
    } else {
        dynamicLightColorScheme(ctx)
    }
    val isErr = ctx.lastError.value.isNotEmpty()
    val state = ctx.currentWorkState()
    val stateText = state?.let { ctx.workStateMessage(it) }
        ?: stringResource(R.string.status_unknown)
    val running = state == TriFeProtocol.WorkState.CALIBRATION ||
            state == TriFeProtocol.WorkState.WORKING
    val shownProgress = if (running) ctx.progress.value.coerceIn(0, 100) else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isErr -> colorSch.errorContainer
                running -> colorSch.tertiaryContainer
                state == TriFeProtocol.WorkState.READY -> colorSch.primaryContainer
                else -> colorSch.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.status_label) + ": " + stateText,
                fontWeight = FontWeight.W600
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (running) {
                Text(
                    text = stringResource(R.string.progress_label) + ": $shownProgress%",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            LinearProgressIndicator(
                progress = { shownProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.battery_label) + ": ${ctx.battery.value}%",
                    fontSize = 13.sp
                )
                Text(
                    text = stringResource(R.string.device_label) + ": " +
                            (ctx.activeMac.value.ifEmpty { stringResource(R.string.no_device) }),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothDeviceCard(
    deviceName: String,
    isBonded: Boolean,
    isValid: Boolean,
    deviceMac: String,
    isActive: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    val cardModifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp, start = 20.dp, end = 20.dp)

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> colorSch.primaryContainer
                isValid -> colorSch.secondaryContainer
                else -> colorSch.surfaceContainer
            },
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp)) {
                Text(text = deviceName)
                Text(text = deviceMac, fontSize = 12.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                if (isActive) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Connected Icon",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    TextButton(onClick = onDisconnect) {
                        Text(text = stringResource(R.string.btn_disconnect))
                    }
                } else if (isValid) {
                    if (isBonded) Icon(
                        painterResource(id = R.drawable.link_24px),
                        contentDescription = "Bonded Icon",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    TextButton(onClick = onConnect) {
                        Text(text = stringResource(R.string.btn_connect))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothPage(
    bluetoothText: MutableState<String>,
    bluetoothStatus: MutableState<Int>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ctx = context as? MainActivity
    val isDiscovering = remember { mutableStateOf(false) }
    if (ctx != null && bluetoothStatus.value == 1) {
        LaunchedEffect(ctx.bluetoothAdapter) {
            while (true) {
                isDiscovering.value = ctx.bluetoothAdapter?.isDiscovering == true
                delay(1000)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isDiscovering.value) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp)
                )
            }

            when (bluetoothStatus.value) {
                0 -> Text(
                    text = bluetoothText.value,
                    modifier = Modifier.fillMaxSize(),
                    textAlign = TextAlign.Center,
                )

                1 -> {
                    val devices = ctx?.devicesListUi ?: mutableListOf()
                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.bt_empty_hint),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn {
                            items(devices) { item ->
                                BluetoothDeviceCard(
                                    deviceName = item.name,
                                    isBonded = item.isConnected,
                                    isValid = item.isValid,
                                    deviceMac = item.mac,
                                    isActive = ctx?.activeMac?.value == item.mac,
                                    onConnect = { ctx?.connectDevice(item.mac) },
                                    onDisconnect = { ctx?.disconnectDevice() }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { ctx?.checkBluetoothPermission() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            elevation = FloatingActionButtonDefaults.elevation(3.dp, 8.dp)
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Refresh Bluetooth"
            )
        }
    }
}

// =====================================================================
//                            公式编辑页
// =====================================================================

private fun defaultFormulaRoot(): FNode =
    FNode.Binary("-", FNode.Variable("val"), FNode.Variable("cal"))

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun FormulaPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ctx = context as? MainActivity
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    val initial = remember {
        runCatching { Formula.parse(ctx?.formulaText?.value ?: Formula.DEFAULT_EXPRESSION) }
            .getOrDefault(defaultFormulaRoot())
    }
    val root = remember { mutableStateOf(initial) }

    val cal = ctx?.calibrationValue?.value ?: 0.0
    val valIn = ctx?.rawResult?.value ?: 0.0
    val testResult = runCatching { Formula.evaluate(root.value, cal, valIn) }.getOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.formula_heading),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.formula_preview) + ":")
        Surface(
            color = colorSch.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = Formula.toExpression(root.value),
                modifier = Modifier.padding(10.dp),
                fontWeight = FontWeight.W600
            )
        }
        Text(
            text = stringResource(R.string.formula_test) + ": cal=" + (ctx?.formatValue(cal)
                ?: "0") +
                    ", val=" + (ctx?.formatValue(valIn) ?: "0") + "  →  " +
                    (testResult?.let {
                        if (it.isNaN() || it.isInfinite()) "—" else ctx?.formatValue(
                            it
                        )
                    } ?: "—")
        )

        Spacer(modifier = Modifier.height(12.dp))

        NodeEditor(
            node = root.value,
            onChange = { root.value = it },
            onDelete = null
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val err = ctx?.saveFormula(Formula.toExpression(root.value))
                    if (err == null) {
                        Toast.makeText(context, R.string.formula_saved, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.formula_invalid, err),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            ) {
                Text(text = stringResource(R.string.formula_save))
            }
            OutlinedButton(
                onClick = { root.value = defaultFormulaRoot() }
            ) {
                Text(text = stringResource(R.string.formula_reset))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.device_label) + ": " +
                    (ctx?.activeMac?.value?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.no_device)),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.calibration_label) + ": " +
                    stringResource(
                        if (ctx?.hasCalibration?.value == true) R.string.cal_status_saved
                        else R.string.cal_status_none
                    ),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { ctx?.clearCalibration() },
            enabled = ctx?.bluetoothSocket != null
        ) {
            Text(text = stringResource(R.string.btn_clear_calibration))
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.result_range_label),
            fontSize = 16.sp,
            fontWeight = FontWeight.W600
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = ctx?.resultMin?.value ?: "",
                onValueChange = { ctx?.setResultMin(it) },
                label = { Text(stringResource(R.string.result_min_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ctx?.resultMax?.value ?: "",
                onValueChange = { ctx?.setResultMax(it) },
                label = { Text(stringResource(R.string.result_max_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.show_log), fontSize = 16.sp)
            Switch(
                checked = ctx?.showLog?.value == true,
                onCheckedChange = { ctx?.setShowLog(it) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.language_label), fontSize = 16.sp)
            LanguageSelector(
                current = ctx?.language?.value ?: "",
                onSelect = { ctx?.setAppLanguage(it) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 语言选择下拉框: "" = 跟随系统, "en" = English, "zh" = 中文 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "" to R.string.lang_system,
        "en" to R.string.lang_english,
        "zh" to R.string.lang_chinese
    )
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: R.string.lang_system
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = stringResource(currentLabel))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (tag, res) ->
                DropdownMenuItem(
                    text = { Text(stringResource(res)) },
                    onClick = { expanded = false; onSelect(tag) }
                )
            }
        }
    }
}

private fun typeNameRes(node: FNode): Int = when (node) {
    is FNode.Num -> R.string.formula_type_number
    is FNode.Variable -> R.string.formula_type_variable
    is FNode.Unary -> R.string.formula_type_function
    is FNode.Binary ->
        if (node.op in Formula.BINARY_FUNCTIONS) R.string.formula_type_function
        else R.string.formula_type_operator
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NodeEditor(
    node: FNode,
    onChange: (FNode) -> Unit,
    onDelete: (() -> Unit)?
) {
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorSch.surfaceContainer
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(typeNameRes(node)), fontWeight = FontWeight.W600)
                Row {
                    ConvertMenu(onConvert = onChange)
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            when (node) {
                is FNode.Num -> NumberField(node, onChange)
                is FNode.Variable -> VariableSelector(node, onChange)
                is FNode.Unary -> {
                    FunctionSelector(node.op, binary = false) {
                        onChange(FNode.Unary(it, node.child))
                    }
                    ChildrenBlock(accent = colorSch.outline) {
                        NodeEditor(
                            node = node.child,
                            onChange = { onChange(FNode.Unary(node.op, it)) },
                            onDelete = null
                        )
                    }
                }

                is FNode.Binary -> {
                    if (node.op in Formula.BINARY_FUNCTIONS) {
                        FunctionSelector(node.op, binary = true) {
                            onChange(FNode.Binary(it, node.left, node.right))
                        }
                    } else {
                        OperatorSelector(node.op) {
                            onChange(FNode.Binary(it, node.left, node.right))
                        }
                    }
                    ChildrenBlock(accent = colorSch.outline) {
                        NodeEditor(
                            node = node.left,
                            onChange = { onChange(FNode.Binary(node.op, it, node.right)) },
                            onDelete = null
                        )
                        NodeEditor(
                            node = node.right,
                            onChange = { onChange(FNode.Binary(node.op, node.left, it)) },
                            onDelete = null
                        )
                    }
                }
            }
        }
    }
}

/**
 * 子节点容器：左侧竖向连接线 + 缩进，
 * 用于直观表达“父节点 -> 子表达式”的层级关系。
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun ChildrenBlock(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 6.dp)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
            content = content
        )
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ConvertMenu(onConvert: (FNode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = stringResource(R.string.formula_change_type))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.formula_type_number)) },
                onClick = { expanded = false; onConvert(FNode.Num(0.0)) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.formula_type_variable)) },
                onClick = { expanded = false; onConvert(FNode.Variable("val")) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.formula_type_operator)) },
                onClick = {
                    expanded = false; onConvert(
                    FNode.Binary(
                        "+",
                        FNode.Num(0.0),
                        FNode.Num(0.0)
                    )
                )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.formula_type_function)) },
                onClick = { expanded = false; onConvert(FNode.Unary("abs", FNode.Num(0.0))) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NumberField(node: FNode.Num, onChange: (FNode) -> Unit) {
    var text by remember { mutableStateOf(Formula.toExpression(node)) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s
            s.toDoubleOrNull()?.let { onChange(FNode.Num(it)) }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun VariableSelector(node: FNode.Variable, onChange: (FNode) -> Unit) {
    DropdownButton(
        label = node.name,
        options = Formula.VARIABLES,
        onSelect = { onChange(FNode.Variable(it)) }
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun OperatorSelector(op: String, onSelect: (String) -> Unit) {
    DropdownButton(
        label = op,
        options = Formula.OPERATORS + Formula.BINARY_FUNCTIONS,
        onSelect = onSelect
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun FunctionSelector(op: String, binary: Boolean, onSelect: (String) -> Unit) {
    val options = if (binary) Formula.BINARY_FUNCTIONS else listOf("-") + Formula.UNARY_FUNCTIONS
    DropdownButton(label = op, options = options, onSelect = onSelect)
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun DropdownButton(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { expanded = false; onSelect(opt) }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun GreetingPreview(
    bluetoothText: MutableState<String> = mutableStateOf(stringResource(R.string.loading)),
    bluetoothStatus: MutableState<Int> = mutableIntStateOf(0),
    navPageNum: MutableState<Int> = mutableIntStateOf(0),
    suggestBoxText: MutableState<String> = mutableStateOf(stringResource(R.string.loading)),
    bluetoothThreadCtrl: MutableState<Boolean> = mutableStateOf(false),
    displayNum: MutableState<String> = mutableStateOf("—")
) {
    val context = LocalContext.current
    val bluetoothConnectStatus = remember { mutableStateOf(false) }

    // 语言切换: 等下拉菜单弹窗关闭后再重建 Activity, 避免窗口令牌失效导致崩溃
    if (context is MainActivity) {
        val activeLang = context.language.value
        LaunchedEffect(activeLang) {
            if (context.needsLanguageRecreate()) {
                delay(50)
                context.recreate()
            }
        }
    }

    if (context is MainActivity)
        LaunchedEffect(context.bluetoothSocket) {
            while (true) {
                bluetoothConnectStatus.value = context.bluetoothSocket != null
                delay(1000)
            }
        }

    val items = listOf(
        stringResource(R.string.main_page),
        stringResource(R.string.bt_page),
        stringResource(R.string.setting_page)
    )
    val icons = listOf<@Composable () -> Unit>(
        { Icon(Icons.Filled.Home, contentDescription = null) },
        { Icon(painterResource(R.drawable.bluetooth_24px), contentDescription = null) },
        { Icon(Icons.Filled.Settings, contentDescription = null) }
    )
    TriFeDetectTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            items[navPageNum.value] +
                                    if (bluetoothConnectStatus.value && navPageNum.value == 0) " - " + stringResource(
                                        R.string.connected
                                    ) else ""
                        )
                    },
                    navigationIcon = {}
                )
            },
            bottomBar = {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { icons[index]() },
                            label = { Text(item) },
                            selected = navPageNum.value == index,
                            onClick = { navPageNum.value = index }
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (navPageNum.value) {
                0 -> Greeting(
                    modifier = Modifier.padding(innerPadding),
                    suggestBoxText = suggestBoxText,
                    bluetoothThreadCtrl = bluetoothThreadCtrl,
                    navPageNum = navPageNum,
                    displayNum = displayNum
                )

                1 -> BluetoothPage(
                    bluetoothText = bluetoothText,
                    bluetoothStatus = bluetoothStatus,
                    modifier = Modifier.padding(innerPadding)
                )

                2 -> FormulaPage(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
