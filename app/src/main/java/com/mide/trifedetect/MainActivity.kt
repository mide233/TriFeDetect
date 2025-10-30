package com.mide.trifedetect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.mide.trifedetect.ui.GreetingPreview
import com.mide.trifedetect.ui.theme.TriFeDetectTheme

class MainActivity : ComponentActivity() {
    var bluetoothText: MutableState<String> = mutableStateOf("")
    var bluetoothStatus: MutableState<Int> = mutableIntStateOf(0)
    var enableBluetoothLauncher: ActivityResultLauncher<Intent>? = null;
    var permissionLauncher: ActivityResultLauncher<String>? = null;

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetoothText = mutableStateOf(getString(R.string.loading))

        if (enableBluetoothLauncher == null) {
            enableBluetoothLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    bluetoothStatus.value = 1
                    bluetoothText.value = getString(R.string.bt_ok)
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

    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkBluetoothPermission(requirePermission: Boolean = true): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            initBluetoothFlow()
        } else {
            if (requirePermission) {
                permissionLauncher?.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        return hasPermission
    }

    fun initBluetoothFlow() {
        val bluetoothManager: BluetoothManager? =
            getSystemService(this, BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_not_supported)
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            bluetoothStatus.value = 0
            bluetoothText.value = getString(R.string.bt_need_enable)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher?.launch(enableBtIntent)
        } else {
            bluetoothStatus.value = 1
            bluetoothText.value = getString(R.string.bt_ok)
        }
    }
}