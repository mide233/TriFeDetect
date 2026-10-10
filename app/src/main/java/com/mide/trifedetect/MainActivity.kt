package com.mide.trifedetect

import android.Manifest
import android.annotation.SuppressLint
import android.app.LocaleManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import java.util.Locale
import java.util.UUID

data class BluetoothDeviceItem(
    val name: String,
    val isConnected: Boolean,
    val isValid: Boolean,
    val mac: String
)

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "EEEEEMainActivity"
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        const val DEVICE_PREFIX = "CONC_"
        const val STATUS_POLL_MS = 1000L
        const val PREF_NAME = "trifedetect"
        const val KEY_SHOW_LOG = "show_log"
        const val KEY_RESULT_MIN = "result_min"
        const val KEY_RESULT_MAX = "result_max"
        const val KEY_LANGUAGE = "language"
    }

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
    var displayNum: MutableState<String> = mutableStateOf("—")

    /** 期望/已激活的设备 MAC (null = 未连接) */
    var connectedDeviceMac: String? = null

    /** 当前已建立链路的设备 MAC ("" = 无) */
    var activeMac: MutableState<String> = mutableStateOf("")

    // ---- 协议驱动的状态 ----
    var workStatus: MutableState<Int> = mutableIntStateOf(TriFeProtocol.WorkState.READY.value)
    var progress: MutableState<Int> = mutableIntStateOf(0)
    var battery: MutableState<Int> = mutableIntStateOf(0)
    var lastError: MutableState<String> = mutableStateOf("")
    var rawResult: MutableState<Double> = mutableDoubleStateOf(0.0)

    /** 当前设备校准值 (按 MAC 自动匹配, 无匹配为 0) */
    var calibrationValue: MutableState<Double> = mutableDoubleStateOf(0.0)
    var calibrationUv: MutableState<Int> = mutableIntStateOf(0)

    /** 最近一次采集的输出值 (公式结果; NaN = 无) */
    var resultValue: MutableState<Double> = mutableDoubleStateOf(Double.NaN)

    /** 输出值允许范围 (文本, 空 = 不限制) */
    var resultMin: MutableState<String> = mutableStateOf("")
    var resultMax: MutableState<String> = mutableStateOf("")

    /** 当前设备是否已存储校准值 */
    var hasCalibration: MutableState<Boolean> = mutableStateOf(false)

    /** 全局公式表达式 */
    var formulaText: MutableState<String> = mutableStateOf(Formula.DEFAULT_EXPRESSION)

    /** 是否在主页显示日志框 (默认关) */
    var showLog: MutableState<Boolean> = mutableStateOf(false)

    /** 应用语言标签 ("" = 跟随系统) */
    var language: MutableState<String> = mutableStateOf("")

    /** 当前已应用的语言标签, 用于判断是否需要重建 Activity */
    private var appliedLanguageTag: String = ""

    private lateinit var formulaStore: FormulaStore
    private val frameParser = TriFeProtocol.FrameParser()
    private var lastStatusQueryMs = 0L

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        bluetoothStatus.value = 0
                        bluetoothText.value = getString(R.string.bt_need_enable)
                    }

                    BluetoothAdapter.STATE_ON -> {
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
                        val isConnected = device.bondState == BluetoothDevice.BOND_BONDED
                        val isValid = deviceName.startsWith(DEVICE_PREFIX)
                        val item = BluetoothDeviceItem(
                            name = deviceName,
                            isConnected = isConnected,
                            isValid = isValid,
                            mac = deviceHardwareAddress
                        )

                        if (devicesListUi.none { it.mac == deviceHardwareAddress }) {
                            if (isValid) devicesListUi.add(0, item)
                            else devicesListUi.add(item)
                        }
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val tag = newBase.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
        appliedLanguageTag = tag
        if (tag.isEmpty()) {
            super.attachBaseContext(newBase)
        } else {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    @SuppressLint("DefaultLocale", "SimpleDateFormat")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        formulaStore = FormulaStore(this)
        formulaText = mutableStateOf(formulaStore.getFormula())
        showLog.value = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean(KEY_SHOW_LOG, false)
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).let { prefs ->
            resultMin.value = prefs.getString(KEY_RESULT_MIN, "") ?: ""
            resultMax.value = prefs.getString(KEY_RESULT_MAX, "") ?: ""
            language.value = prefs.getString(KEY_LANGUAGE, "") ?: ""
        }

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
        registerReceiver(btFoundReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        bluetoothThread = Thread {
            var failureCounter = 0

            try {
                while (!Thread.currentThread().isInterrupted) {
                    if (!checkBluetoothPermission(false)) {
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

                            val frames = ArrayList<TriFeProtocol.Frame>()
                            frameParser.feed(buffer, buffer.size, frames)
                            for (frame in frames) handleDeviceFrame(frame)
                            failureCounter = 0
                        } else {
                            failureCounter++
                            if (failureCounter >= 5) {
                                bluetoothSocket = null
                                activeMac.value = ""
                                failureCounter = 0
                            }
                        }

                        // 定期查询状态, 刷新进度/电量/工作状态
                        val now = System.currentTimeMillis()
                        if (now - lastStatusQueryMs >= STATUS_POLL_MS) {
                            lastStatusQueryMs = now
                            enqueueFrame(TriFeProtocol.buildFrame(TriFeProtocol.HostCmd.STATUS_QUERY))
                        }

                        sendPendingFrames()

                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in bluetooth loop", e)
                        bluetoothSocket = null
                        activeMac.value = ""
                        Thread.sleep(2000)
                    }
                }
            } catch (_: InterruptedException) {
                // Activity 销毁/重建时中断读线程, 静默退出 (避免未捕获异常导致崩溃)
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
            try {
                bluetoothSocket?.close()
            } catch (_: Exception) {
            }
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        }
        bluetoothThread?.interrupt()
        unregisterReceiver(btReceiver)
        unregisterReceiver(btFoundReceiver)
    }

    // ============================================================== 命令发送

    private fun enqueueFrame(frame: ByteArray) {
        synchronized(bluetoothSendQueue) {
            for (b in frame) bluetoothSendQueue.add(b)
        }
    }

    private fun sendPendingFrames() {
        val socket = bluetoothSocket ?: return
        val bytes = synchronized(bluetoothSendQueue) {
            if (bluetoothSendQueue.isEmpty()) return
            val arr = bluetoothSendQueue.toByteArray()
            bluetoothSendQueue.clear()
            arr
        }
        if (!socket.isConnected) socket.connect()
        socket.outputStream.apply {
            write(bytes)
            flush()
        }
        Log.d(TAG, "Sent: ${bytes.joinToString(" ") { String.format("%02X", it) }}")
    }

    /** 发送一条无载荷命令 (仅当已连接) */
    fun sendCommand(cmd: Int) {
        if (bluetoothSocket == null) {
            toast(getString(R.string.go_connect_first))
            return
        }
        enqueueFrame(TriFeProtocol.buildFrame(cmd))
    }

    fun sendStart() {
        val st = currentWorkState()
        if (st?.isError == true) {
            toast(getString(R.string.err_cannot_run, workStateMessage(st)))
            return
        }
        sendCommand(TriFeProtocol.HostCmd.START)
    }

    fun sendStop() = sendCommand(TriFeProtocol.HostCmd.STOP)

    fun sendCalibration() {
        val st = currentWorkState()
        if (st?.isError == true) {
            toast(getString(R.string.err_cannot_run, workStateMessage(st)))
            return
        }
        sendCommand(TriFeProtocol.HostCmd.CALIBRATION)
    }

    fun requestStatus() = sendCommand(TriFeProtocol.HostCmd.STATUS_QUERY)

    // ============================================================== 帧处理

    private fun handleDeviceFrame(frame: TriFeProtocol.Frame) {
        Log.d(TAG, "Recv frame: $frame")
        when (frame.cmd) {
            TriFeProtocol.DeviceCmd.STATE -> {
                TriFeProtocol.parseState(frame)?.let { st ->
                    applyWorkState(st)
                    if (st.isError) reportError(st)
                }
            }

            TriFeProtocol.DeviceCmd.ERROR -> {
                TriFeProtocol.parseState(frame)?.let { st ->
                    applyWorkState(st)
                    reportError(st)
                }
            }

            TriFeProtocol.DeviceCmd.RESULT -> {
                TriFeProtocol.parseResult(frame)?.let { onResult(it) }
            }

            TriFeProtocol.DeviceCmd.CALIBRATION -> {
                TriFeProtocol.parseCalibration(frame)?.let { onCalibration(it) }
            }

            TriFeProtocol.DeviceCmd.STATUS -> {
                TriFeProtocol.parseStatus(frame)?.let { s ->
                    s.workState?.let { applyWorkState(it) }
                    progress.value = s.progress.coerceIn(0, 100)
                    battery.value = s.battery
                }
            }
        }
    }

    private fun applyWorkState(st: TriFeProtocol.WorkState) {
        workStatus.value = st.value
        bluetoothThreadCtrl.value = st == TriFeProtocol.WorkState.WORKING ||
                st == TriFeProtocol.WorkState.CALIBRATION
        if (!st.isError) lastError.value = ""
        if (st == TriFeProtocol.WorkState.CALIBRATION || st == TriFeProtocol.WorkState.WORKING) {
            progress.value = 0
        }
    }

    private fun reportError(st: TriFeProtocol.WorkState) {
        val msg = workStateMessage(st)
        lastError.value = msg
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            logLine("错误: $msg")
        }
    }

    private fun onResult(raw: Double) {
        rawResult.value = raw
        val cal = calibrationValue.value
        val out = runCatching { Formula.eval(formulaText.value, cal, raw) }.getOrNull()
        resultValue.value = if (out == null || out.isNaN() || out.isInfinite()) Double.NaN else out
        displayNum.value = if (out == null || out.isNaN() || out.isInfinite()) "—" else formatValue(out)
        runOnUiThread {
            logLine("采集结果 val=${formatValue(raw)}  cal=${formatValue(cal)}  →  ${displayNum.value}")
        }
    }

    private fun onCalibration(c: TriFeProtocol.Calibration) {
        val mac = activeMac.value
        if (mac.isNotEmpty()) {
            formulaStore.saveCalibration(mac, c.rawValue, c.uvLightLevel)
        }
        calibrationValue.value = c.rawValue
        calibrationUv.value = c.uvLightLevel
        hasCalibration.value = true
        runOnUiThread {
            Toast.makeText(this, getString(R.string.cal_saved), Toast.LENGTH_SHORT).show()
            logLine(
                "校准完成: raw=${formatValue(c.rawValue)} uv=${c.uvLightLevel}" +
                        if (mac.isNotEmpty()) " (已保存 $mac)" else ""
            )
        }
    }

    /** 切换主页日志框显示, 并持久化 */
    fun setShowLog(v: Boolean) {
        showLog.value = v
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOW_LOG, v).apply()
    }

    /** 设置应用语言 (tag = "" 表示跟随系统) */
    fun setAppLanguage(tag: String) {
        if (tag == language.value) return
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, tag).apply()
        language.value = tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用系统"按应用语言": 由系统以配置变更方式重建 Activity,
            // 可正确保留沉浸式状态栏 (手动 recreate() 在部分 ROM 上会丢失 edge-to-edge)
            val locales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
            getSystemService(LocaleManager::class.java).applicationLocales = locales
        }
    }

    /** 语言已改变但尚未应用到当前配置时返回 true (供 Compose 触发重建) */
    fun needsLanguageRecreate(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            language.value != appliedLanguageTag

    /** 更新输出值下限并持久化 */
    fun setResultMin(v: String) {
        resultMin.value = v
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
            .putString(KEY_RESULT_MIN, v).apply()
    }

    /** 更新输出值上限并持久化 */
    fun setResultMax(v: String) {
        resultMax.value = v
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
            .putString(KEY_RESULT_MAX, v).apply()
    }

    private fun rangeMin(): Double? = resultMin.value.trim().toDoubleOrNull()

    private fun rangeMax(): Double? = resultMax.value.trim().toDoubleOrNull()

    /**
     * 输出值相对设定范围的位置:
     *  1 = 高于上限, -1 = 低于下限, 0 = 在范围内 / 未设置 / 无结果
     */
    fun resultTrend(): Int {
        val v = resultValue.value
        if (v.isNaN() || v.isInfinite()) return 0
        val max = rangeMax()
        if (max != null && v > max) return 1
        val min = rangeMin()
        if (min != null && v < min) return -1
        return 0
    }

    /** 清除当前设备已存储的校准值, 并复位为 0 */
    fun clearCalibration() {        val mac = activeMac.value
        if (mac.isNotEmpty()) formulaStore.clearCalibration(mac)
        calibrationValue.value = 0.0
        calibrationUv.value = 0
        hasCalibration.value = false
        toast(getString(R.string.cal_cleared))
        runOnUiThread {
            logLine("已清除校准值" + if (mac.isNotEmpty()) " ($mac)" else "")
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun logLine(text: String) {
        suggestBoxText.value += "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())} $text\n"
    }

    private fun toast(text: String) = runOnUiThread {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    fun workStateMessage(st: TriFeProtocol.WorkState): String = when (st) {
        TriFeProtocol.WorkState.READY -> getString(R.string.status_ready)
        TriFeProtocol.WorkState.CALIBRATION -> getString(R.string.status_calibrating)
        TriFeProtocol.WorkState.WORKING -> getString(R.string.status_working)
        TriFeProtocol.WorkState.ERR_TILT -> getString(R.string.err_tilt)
        TriFeProtocol.WorkState.ERR_OPEN -> getString(R.string.err_open)
        TriFeProtocol.WorkState.ERR_LOW_POWER -> getString(R.string.err_low_power)
        TriFeProtocol.WorkState.ERR_NO_CONTAINER -> getString(R.string.err_no_container)
    }

    fun currentWorkState(): TriFeProtocol.WorkState? =
        TriFeProtocol.WorkState.from(workStatus.value)

    // ============================================================== 公式

    /** 校验并保存公式; 返回错误信息, null 表示成功 */
    fun saveFormula(expression: String): String? {
        return try {
            Formula.parse(expression)
            formulaStore.saveFormula(expression)
            formulaText.value = expression
            null
        } catch (e: FormulaException) {
            e.message ?: "公式无效"
        }
    }

    fun formatValue(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        return BigDecimal(v.toString()).setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }

    // ============================================================== 连接管理

    fun connectDevice(mac: String) {
        connectedDeviceMac = mac
    }

    fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close socket failed", e)
        }
        bluetoothSocket = null
        connectedDeviceMac = null
        activeMac.value = ""
        bluetoothThreadCtrl.value = false
        lastError.value = ""
        calibrationValue.value = 0.0
        calibrationUv.value = 0
        hasCalibration.value = false
        workStatus.value = TriFeProtocol.WorkState.READY.value
        progress.value = 0
        toast(getString(R.string.bt_disconnected))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkBluetoothPermission(requirePermission: Boolean = true): Boolean {
        val hasConnectPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val hasScanPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (hasConnectPermission && hasScanPermission) {
            initBluetoothFlow(requirePermission)
        } else {
            if (requirePermission) {
                if (!hasScanPermission) permissionLauncher?.launch(Manifest.permission.BLUETOOTH_SCAN)
                if (!hasConnectPermission) permissionLauncher?.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        return hasConnectPermission && hasScanPermission
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun initBluetoothFlow(requireBluetooth: Boolean = true) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
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
                        val name = device.name ?: return@forEach
                        val valid = name.startsWith(DEVICE_PREFIX)
                        if (valid) {
                            devicesListUi.add(
                                0,
                                BluetoothDeviceItem(
                                    name = name, isConnected = true, isValid = true, mac = device.address
                                )
                            )
                        } else {
                            devicesListUi.add(
                                BluetoothDeviceItem(
                                    name = name, isConnected = true, isValid = false, mac = device.address
                                )
                            )
                        }
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
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()
        }

        Thread {
            try {
                val bluetoothTarget = bluetoothAdapter?.getRemoteDevice(mac)
                val localBluetoothSocket = bluetoothTarget?.createRfcommSocketToServiceRecord(
                    UUID.fromString(SPP_UUID)
                )
                localBluetoothSocket?.connect()
                Log.d(TAG, "Successfully connected to device: $mac")

                bluetoothSocket = localBluetoothSocket
                lastStatusQueryMs = 0L

                runOnUiThread {
                    connectedDeviceMac = mac
                    activeMac.value = mac
                    val cal = formulaStore.getCalibration(mac)
                    calibrationValue.value = cal?.rawValue ?: 0.0
                    calibrationUv.value = cal?.uvLightLevel ?: 0
                    hasCalibration.value = cal != null

                    val idx = devicesListUi.indexOfFirst { it.mac == mac }
                    if (idx != -1) {
                        devicesListUi[idx] = devicesListUi[idx].copy(isConnected = true)
                    }
                    if (suggestSuccess) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.connect_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    logLine("已连接 $mac" + if (cal == null) " (无校准值)" else " (已载入校准值)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connect to device failed: $mac", e)
                runOnUiThread {
                    if (suggestFailure) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.connect_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }.start()
    }
}
