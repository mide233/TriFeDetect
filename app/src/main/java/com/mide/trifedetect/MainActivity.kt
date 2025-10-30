package com.mide.trifedetect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.mide.trifedetect.ui.GreetingPreview
import com.mide.trifedetect.ui.theme.TriFeDetectTheme

data class BluetoothDeviceItem(
    val name: String,
    val isConnected: Boolean,
    val isValid: Boolean,
    val mac: String
)

class MainActivity : ComponentActivity() {
    var bluetoothText: MutableState<String> = mutableStateOf("")
    var bluetoothStatus: MutableState<Int> = mutableIntStateOf(0)
    var enableBluetoothLauncher: ActivityResultLauncher<Intent>? = null
    var permissionLauncher: ActivityResultLauncher<String>? = null
    var devicesListUi = mutableStateListOf<BluetoothDeviceItem>()
    var bluetoothManager: BluetoothManager? = null
    var bluetoothAdapter: BluetoothAdapter? = null


    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> { /* 处理已关闭 */
                        bluetoothStatus.value = 0
                        bluetoothText.value = getString(R.string.bt_need_enable)
                    }

                    BluetoothAdapter.STATE_ON -> { /* 处理已打开 */
                        bluetoothStatus.value = 1
                        bluetoothText.value = getString(R.string.bt_ok)
                    }

                }
            }
        }
    }

    private val btFoundReceiver = object : BroadcastReceiver() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address

                    if (deviceName != null && deviceHardwareAddress != null) {
                        val isConnected =
                            device.bondState == BluetoothDevice.BOND_BONDED
                        val isValid =
                            deviceName.startsWith("JDY")
                        val item = BluetoothDeviceItem(
                            name = deviceName,
                            isConnected = isConnected,
                            isValid = isValid,
                            mac = deviceHardwareAddress
                        )
                        if (isValid) devicesListUi.add(0, item)
                        else devicesListUi.add(item)
                        devicesListUi.distinct()

                    }
                }
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothText = mutableStateOf(getString(R.string.loading))

        bluetoothManager = getSystemService(this, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        if (enableBluetoothLauncher == null) {
            enableBluetoothLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    bluetoothStatus.value = 1
                    bluetoothText.value = getString(R.string.bt_ok)
                    initBluetoothFlow()
                } else {
                    bluetoothStatus.value = 0
                    bluetoothText.value = getString(R.string.bt_need_enable)
                }
            }
        }
        if (permissionLauncher == null) {
            permissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) initBluetoothFlow()
                else {
                    bluetoothStatus.value = 0
                    bluetoothText.value = getString(R.string.bt_need_permission)
                }
            }
        }

        setContent {
            TriFeDetectTheme {
                GreetingPreview(bluetoothText = bluetoothText, bluetoothStatus = bluetoothStatus)
            }
        }

        if (!checkBluetoothPermission(false)) {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_need_permission)
        }

        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(
            btFoundReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(btReceiver)
        unregisterReceiver(btFoundReceiver)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkBluetoothPermission(requirePermission: Boolean = true): Boolean {
        val hasConnectPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val hasScanPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (hasConnectPermission && hasScanPermission) {
            initBluetoothFlow(requirePermission)
        } else {
            if (requirePermission) {
                if (!hasScanPermission) {
                    permissionLauncher?.launch(Manifest.permission.BLUETOOTH_SCAN)
                }
                if (!hasConnectPermission) {
                    permissionLauncher?.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        }

        return hasConnectPermission && hasScanPermission
    }

    fun initBluetoothFlow(requireBluetooth: Boolean = true) {

        if (bluetoothAdapter == null) {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_not_supported)
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_need_enable)
            if (requireBluetooth) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher?.launch(enableBtIntent)
            }
        } else {
            bluetoothStatus.value = 1
            bluetoothText.value = getString(R.string.bt_ok)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (!bluetoothAdapter!!.isDiscovering) {
                    devicesListUi.clear()

                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                    pairedDevices?.forEach { device ->
                        devicesListUi.add(
                            BluetoothDeviceItem(
                                name = device.name,
                                isConnected = true,
                                isValid = device.name.startsWith("JDY"),
                                mac = device.address
                            )
                        )
                    }

                    bluetoothAdapter!!.startDiscovery()
                }
            } else {
                bluetoothStatus.value = 0
                bluetoothText.value = getString(R.string.bt_need_permission)
            }

        }
    }

}