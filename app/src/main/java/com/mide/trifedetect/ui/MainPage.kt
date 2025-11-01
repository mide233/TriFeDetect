package com.mide.trifedetect.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mide.trifedetect.MainActivity
import com.mide.trifedetect.R
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
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    fun bytes(vararg values: Int) = values.map { it.toByte() }
    fun Boolean.toInt(): Int = if (this) 1 else 0

    if (context is MainActivity) {
        if (context.bluetoothSocket == null) {
            AlertDialog(
                onDismissRequest = {
                    // 当用户点击对话框以外的地方或者按下系统返回键将会执行的代码
                },
                title = {
                    Text(
                        text = "设备未连接",
                        fontWeight = FontWeight.W700
                    )
                },
                text = {
                    Text(
                        text = "请先前往蓝牙设置页面连接设备",
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            navPageNum.value = 1
                        },
                    ) {
                        Text(
                            "确认",
                            fontWeight = FontWeight.W700,

                            )
                    }
                },
                dismissButton = {}
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            Column(modifier = Modifier.padding(top = 30.dp, start = 30.dp, end = 30.dp)) {
                Text(text = "三价铁浓度")
                Text(
                    text = displayNum.value,
                    textAlign = TextAlign.Center,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorSch.primary,
                    softWrap = false
                )

            }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
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
        FloatingActionButton(
            onClick = {
                if (context is MainActivity) {
                    if (context.bluetoothThread?.isAlive == true) {
                        bluetoothThreadCtrl.value = !bluetoothThreadCtrl.value
                        context.bluetoothSendQueue.addAll(
                            bytes(
                                0x39, bluetoothThreadCtrl.value.toInt(),
                                0x0D
                            )
                        )
                    }

                    if (!bluetoothThreadCtrl.value) context.suggestBoxText.value = ""
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                3.dp,
                8.dp
            )
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

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothDeviceCard(
    deviceName: String,
    isConnected: Boolean,
    isValid: Boolean,
    deviceMac: String
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

    if (isConnected && isValid) {
        if (context is MainActivity) {
            if (context.bluetoothSocket == null) {
                context.connectedDeviceMac = deviceMac
            }
        }
    }

    Card(
        modifier = if (isValid && !isConnected) cardModifier.clickable {
            if (context is MainActivity) {
                context.connectedDeviceMac = deviceMac
            }
        } else cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isValid) colorSch.secondaryContainer else colorSch.surfaceContainer,
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = deviceName,
                modifier = Modifier
                    .padding(20.dp)
            )
            if (isConnected && isValid) Icon(
                Icons.Filled.Check,
                contentDescription = "Connected Icon",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(15.dp)
            ) else if (isConnected) Icon(
                painterResource(id = R.drawable.link_24px),
                contentDescription = "Connected Icon",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(15.dp)
            )

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
    var isDiscovering = remember { mutableStateOf(false) }
    if (context is MainActivity && bluetoothStatus.value == 1) {
        isDiscovering =
            remember { mutableStateOf(context.bluetoothAdapter?.isDiscovering == true) }
        LaunchedEffect(context.bluetoothAdapter) {
            while (true) {
                isDiscovering.value = context.bluetoothAdapter?.isDiscovering == true
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

                1 -> LazyColumn {
                    items(
                        if (context is MainActivity) {
                            context.devicesListUi
                        } else mutableListOf()

                    ) { item ->
                        BluetoothDeviceCard(
                            deviceName = item.name,
                            isConnected = item.isConnected,
                            isValid = item.isValid,
                            deviceMac = item.mac
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (context is MainActivity) {
                    context.checkBluetoothPermission()

                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                3.dp,
                8.dp
            )
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Refresh Bluetooth"
            )
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
    displayNum: MutableState<String> = mutableStateOf("114.514 ppm")
) {
    val context = LocalContext.current
    val bluetoothConnectStatus = remember { mutableStateOf(false) }

    if (context is MainActivity)
        LaunchedEffect(context.bluetoothSocket) {
            while (true) {
                bluetoothConnectStatus.value = context.bluetoothSocket != null
                delay(1000)
            }
        }

    val items = listOf("主页", "蓝牙")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Settings)
    TriFeDetectTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            items[navPageNum.value] +
                                    if (bluetoothConnectStatus.value && navPageNum.value == 0) " - 已连接" else ""
                        )
                    },
                    navigationIcon = {
                    }
                )
            },
            bottomBar = {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(icons[index], contentDescription = null) },
                            label = { Text(item) },
                            selected = navPageNum.value == index,
                            onClick = {
                                navPageNum.value = index
                            }
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
            }
        }
    }
}

