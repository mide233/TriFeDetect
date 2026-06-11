package com.mide.trifedetect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

data class BluetoothDeviceItem(
    val name: String,
    val isConnected: Boolean,
    val isValid: Boolean,
    val mac: String
)

class MainActivity : ComponentActivity() {
    var bluetoothText: MutableState<String> = mutableStateOf("")
    var bluetoothStatus: MutableState<Int> = mutableIntStateOf(0)
    var bluetoothManager: BluetoothManager? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothSocket: BluetoothSocket? = null
    var bluetoothThread: Thread? = null
    var enableBluetoothLauncher: ActivityResultLauncher<Intent>? = null
    var permissionLauncher: ActivityResultLauncher<String>? = null
    var devicesListUi = mutableStateListOf<BluetoothDeviceItem>()
    var suggestBoxText: MutableState<String> = mutableStateOf("")
    var bluetoothThreadCtrl: MutableState<Boolean> = mutableStateOf(false)
    var bluetoothSendQueue: ArrayList<Byte> = ArrayList()
    var navPageNum: MutableState<Int> = mutableIntStateOf(0)
    var displayNum: MutableState<String> = mutableStateOf("Loading...")
    var connectedDeviceMac: String? = null

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

                        // 检查是否已存在相同设备
                        if (devicesListUi.none { it.name == deviceName }) {
                            if (isValid) devicesListUi.add(0, item)
                            else devicesListUi.add(item)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("DefaultLocale", "SimpleDateFormat")
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
                GreetingPreview(
                    bluetoothText = bluetoothText,
                    bluetoothStatus = bluetoothStatus,
                    suggestBoxText = suggestBoxText,
                    bluetoothThreadCtrl = bluetoothThreadCtrl,
                    navPageNum = navPageNum,
                    displayNum = displayNum
                )
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

        bluetoothThread = Thread {
            fun Float.roundHalfUp(scale: Int): Float =
                BigDecimal(this.toString()).setScale(scale, RoundingMode.HALF_UP).toFloat()

            var lastDisplayNum = ""
            var failureCounter = 0

            while (true) {
                if (!checkBluetoothPermission(false)) {
                    Log.d(
                        "EEEEEMainActivity",
                        "Bluetooth Disable, socket is null: ${bluetoothSocket == null}, ctrl: ${bluetoothThreadCtrl.value}"
                    )
                    Thread.sleep(1500)
                    continue
                } else if (bluetoothSocket == null) {
                    if (connectedDeviceMac == null) {
                        Thread.sleep(1500)
                        continue
                    } else {
                        connectBluetoothDevice(connectedDeviceMac!!, suggestFailure = false)
                        Thread.sleep(2000)
                        continue
                    }
                }

                try {
                    if (!bluetoothSocket!!.isConnected) {
                        bluetoothSocket!!.connect()
                    }
                    val inputStream = bluetoothSocket!!.inputStream
                    val availableBytes = inputStream.available()
                    if (availableBytes > 0) {
                        val buffer = ByteArray(availableBytes)
                        inputStream.read(buffer)

                        val extractedData = extractBetweenCRLF(buffer)
                        if (extractedData.isNotEmpty()) {
                            for (dataSegment in extractedData) {
                                if (dataSegment.isEmpty()) continue
                                val debugData = dataSegment.joinToString(" ") {
                                    String.format(
                                        "%02X",
                                        it
                                    )
                                }
                                Log.d("EEEEEMainActivity", "rec data: $debugData")

                                bluetoothThreadCtrl.value = dataSegment.removeAt(0) == 0x31.toByte()
                                val intVal = dataSegment.removeAt(0).toUInt().toInt()
                                val floatVal = dataSegment.removeAt(0).toUByte().toFloat() / 256.0f
                                if (intVal > 255 || floatVal >= 1.0f) {
                                    Log.d(
                                        "EEEEEMainActivity",
                                        "invalid data extractedData from buffer: $debugData, got intVal: $intVal, floatVal: $floatVal"
                                    )
                                    continue

                                }
                                displayNum.value =
                                    String.format(
                                        "%03d.%02d μM",
                                        intVal,
                                        (floatVal.roundHalfUp(2) * 100).toInt()
                                    )

                            }
                        }

                        if (bluetoothThreadCtrl.value && displayNum.value != lastDisplayNum) {
                            runOnUiThread {
                                suggestBoxText.value += "${
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(
                                        Date()
                                    )
                                } ${displayNum.value}\n"
                            }
                            lastDisplayNum = displayNum.value
                        }
                    } else {
                        Log.d("EEEEEMainActivity", "No data available to read")
                        failureCounter++
                        if (failureCounter >= 5) {
                            bluetoothSocket = null
                            failureCounter = 0
                        }
                    }

                    if (bluetoothSendQueue.isNotEmpty()) {
                        if (!bluetoothSocket!!.isConnected) {
                            bluetoothSocket!!.connect()
                        }
                        val outputStream = bluetoothSocket!!.outputStream
                        val byteArray = bluetoothSendQueue.toByteArray()
                        outputStream.write(byteArray)
                        outputStream.flush()
                        bluetoothSendQueue.clear()
                        Log.d(
                            "EEEEEMainActivity",
                            "Sent data: ${
                                byteArray.joinToString(" ") {
                                    String.format(
                                        "%02X",
                                        it
                                    )
                                }
                            }"
                        )

                    }

                    Thread.sleep(500)
                } catch (e: Exception) {
                    Log.e("EEEEEMainActivity", "Error reading data", e)
                    bluetoothSocket = null
                    Thread.sleep(2000)
                }

            }
        }
        bluetoothThread?.start()

    }

    override fun onDestroy() {
        super.onDestroy()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothSocket?.close()
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        }
        bluetoothThread?.interrupt()
        unregisterReceiver(btReceiver)
        unregisterReceiver(btFoundReceiver)
    }

    fun extractBetweenCRLF(data: ByteArray): List<ArrayList<Byte>> {
        val results = ArrayList<ArrayList<Byte>>()
        var i = 0
        while (i < data.size) {
            if (data[i] == 0x0D.toByte()) {
                var j = i + 1
                while (j < data.size && data[j] != 0x0A.toByte()) j++
                if (j < data.size && data[j] == 0x0A.toByte()) {
                    val segment = ArrayList<Byte>(j - (i + 1))
                    for (k in (i + 1) until j) segment.add(data[k])
                    results.add(segment)
                    i = j + 1
                    continue
                } else {
                    break
                }
            }
            i++
        }
        return results
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun initBluetoothFlow(requireBluetooth: Boolean = true) {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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

                if (!bluetoothAdapter!!.isDiscovering && requireBluetooth) {
                    devicesListUi.clear()

                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                    pairedDevices?.forEach { device ->
                        if (device.name.startsWith("JDY"))
                            devicesListUi.add(
                                0,
                                BluetoothDeviceItem(
                                    name = device.name,
                                    isConnected = true,
                                    isValid = true,
                                    mac = device.address
                                )
                            )
                        else devicesListUi.add(
                            BluetoothDeviceItem(
                                name = device.name,
                                isConnected = true,
                                isValid = false,
                                mac = device.address
                            )
                        )
                    }
                    bluetoothAdapter!!.startDiscovery()
                }
            }
        } else {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_need_permission)
            checkBluetoothPermission(requireBluetooth)
        }
    }

    fun connectBluetoothDevice(
        mac: String,
        suggestSuccess: Boolean = true,
        suggestFailure: Boolean = true
    ) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        }

        Thread {
            try {
                val bluetoothTarget = bluetoothAdapter?.getRemoteDevice(mac)
                val localBluetoothSocket = bluetoothTarget?.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )

                localBluetoothSocket?.connect()
                Log.d("MainActivity", "Successfully connected to device: $mac")

                bluetoothSocket = localBluetoothSocket

                runOnUiThread {
                    val deviceIndex = devicesListUi.indexOfFirst { it.mac == mac }
                    if (deviceIndex != -1) {
                        devicesListUi[deviceIndex] =
                            devicesListUi[deviceIndex].copy(isConnected = true)
                    }
                    if (suggestSuccess) Toast.makeText(
                        applicationContext,
                        getString(R.string.connect_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }


            } catch (e: Exception) {
                Log.e("MainActivity", "Connect to device failed: $mac", e)
                runOnUiThread {
                    if (suggestFailure) Toast.makeText(
                        applicationContext,
                        getString(R.string.connect_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }
}