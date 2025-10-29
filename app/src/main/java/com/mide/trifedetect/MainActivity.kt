package com.mide.trifedetect

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mide.trifedetect.ui.theme.TriFeDetectTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriFeDetectTheme {
                GreetingPreview()
            }
        }
    }
}

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
                    text = "LOADING",
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

@Composable
fun BluetoothPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "蓝牙页面",
            modifier = Modifier.align(Alignment.Center)
        )
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
fun GreetingPreview() {
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

                1 -> BluetoothPage(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}