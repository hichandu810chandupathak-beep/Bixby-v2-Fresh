package com.bixby.assistant

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val CALL_PERMISSION_REQUEST = 101
        private const val BLUETOOTH_PERMISSION_REQUEST = 102
    }

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var torchEnabled = false
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        findTorchCamera()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    private fun findTorchCamera() {
        try {
            val cameras = cameraManager?.cameraIdList ?: return

            for (id in cameras) {
                val characteristics =
                    cameraManager?.getCameraCharacteristics(id)

                val hasFlash =
                    characteristics?.get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true

                val lensFacing =
                    characteristics?.get(
                        CameraCharacteristics.LENS_FACING
                    )

                if (hasFlash &&
                    lensFacing == CameraCharacteristics.LENS_FACING_BACK
                ) {
                    cameraId = id
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setTorch(enabled: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            return
        }

        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, enabled)
                torchEnabled = enabled
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to control flashlight",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleTorch() {
        setTorch(!torchEnabled)
    }

    fun openWifiSettings() {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun requestWifiOn() {
        openWifiSettings()
    }

    fun requestWifiOff() {
        openWifiSettings()
    }

    fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun requestBluetoothOn() {
        openBluetoothSettings()
    }

    fun requestBluetoothOff() {
        openBluetoothSettings()
    }

    fun callContact(contactName: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST
            )
            return
        }

        val phoneNumber = findContactPhoneNumber(contactName)

        if (phoneNumber.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Contact not found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val normalizedNumber =
            PhoneNumberUtils.normalizeNumber(phoneNumber)

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$normalizedNumber")
        }

        startActivity(callIntent)
    }

    private fun findContactPhoneNumber(contactName: String): String? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                CALL_PERMISSION_REQUEST
            )
            return null
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"

        val selectionArgs = arrayOf("%$contactName%")

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex =
                    cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                if (numberIndex >= 0) {
                    return cursor.getString(numberIndex)
                }
            }
        }

        return null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (grantResults.isEmpty() ||
            grantResults[0] != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> setTorch(!torchEnabled)

            CALL_PERMISSION_REQUEST -> {
                Toast.makeText(
                    this,
                    "Permission granted. Please repeat the call command.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            BLUETOOTH_PERMISSION_REQUEST -> openBluetoothSettings()
        }
    }

    override fun onDestroy() {
        if (torchEnabled) {
            try {
                cameraId?.let {
                    cameraManager?.setTorchMode(it, false)
                }
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }
}
