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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mide.trifedetect.MainActivity
import com.mide.trifedetect.R
import com.mide.trifedetect.ui.theme.TriFeDetectTheme

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun Greeting(modifier: Modifier = Modifier) {
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column {
            Column(modifier = Modifier.padding(top = 30.dp, start = 30.dp, end = 30.dp)) {
                Text(text = "三价铁浓度")
                Text(
                    text = "114.514 ppm",
                    textAlign = TextAlign.Center,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorSch.primary
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
                    text = stringResource(R.string.loading),
                    modifier = Modifier
                        .padding(10.dp)
                        .verticalScroll(scrollState)
                )
            }

        }
        FloatingActionButton(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                3.dp,
                8.dp
            )
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Detection Start/Stop"
            )
        }


    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun BluetoothDeviceCard(deviceName: String, isConnected: Boolean, isValid: Boolean) {
    val colorSch = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        dynamicLightColorScheme(LocalContext.current)
    }
    val cardModifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp, start = 20.dp, end = 20.dp)
    Card(
        modifier = if (isValid) cardModifier.clickable { } else cardModifier,
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
            if (isConnected) Icon(
                Icons.Filled.Check,
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

    Box(modifier = modifier.fillMaxSize()) {
        when (bluetoothStatus.value) {
            0 -> Text(
                text = bluetoothText.value,
                modifier = Modifier.align(Alignment.Center)
            )

            1 -> LazyColumn {
                items(20) { index ->
                    BluetoothDeviceCard(
                        deviceName = "Bluetooth Device #$index",
                        isConnected = index % 3 == 0,
                        isValid = index % 5 != 0
                    )
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
    bluetoothStatus: MutableState<Int> = mutableIntStateOf(0)
) {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("主页", "蓝牙")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Settings)
    TriFeDetectTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(items[selectedItem]) },
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
                            selected = selectedItem == index,
                            onClick = {
                                selectedItem = index
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (selectedItem) {
                0 -> Greeting(
                    modifier = Modifier.padding(innerPadding)
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

