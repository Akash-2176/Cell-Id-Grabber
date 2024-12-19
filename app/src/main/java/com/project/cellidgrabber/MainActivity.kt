package com.project.cellidgrabber

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.telephony.CellInfo
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.util.Log
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var cellTowerTable: TableLayout
    private lateinit var startStopButton: Button
    private lateinit var clearButton: Button
    private lateinit var saveButton: Button
    private lateinit var contentTextView: TextView

    private var isCollectingData = false
    private val cellTowerData = mutableListOf<CellTower>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentTextView = findViewById(R.id.content)
        cellTowerTable = findViewById(R.id.cellTowerTable)
        startStopButton = findViewById(R.id.startStopButton)
        clearButton = findViewById(R.id.clearButton)
        saveButton = findViewById(R.id.saveButton)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Check permissions
        checkPermissions()

        startStopButton.setOnClickListener { toggleDataCollection() }
        clearButton.setOnClickListener { clearData() }
        saveButton.setOnClickListener { saveData() }

        // Show initial structure
        updateTable()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check for phone state and location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty()) {
                var allGranted = true
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false
                        break
                    }
                }
                if (allGranted) {
                    // All permissions granted, proceed with your logic
                    getCellTowerInfo()
                } else {
                    contentTextView.text = getString(R.string.permissions_denied)
                }
            }
        }
    }

    private fun toggleDataCollection() {
        if (!isCollectingData) {
            isCollectingData = true
            startStopButton.text = getString(R.string.refresh)
            getCellTowerInfo()
        } else {
            getCellTowerInfo() // Refresh the data
        }
    }

    private fun clearData() {
        cellTowerData.clear()
        updateTable()
        Log.d("ClearData", "Cell tower data cleared")
    }

    private fun saveData() {
        val fileName = "cell_tower_data_${System.currentTimeMillis()}.txt"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)

        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri).use { outputStream ->
                    cellTowerData.forEachIndexed { index, tower ->
                        val line = "${index + 1}. MCC: ${tower.mcc}, MNC: ${tower.mnc}, LAC: ${tower.lac}, CID: ${tower.cid}\n"
                        outputStream?.write(line.toByteArray())
                    }
                }
                Log.d("SaveData", "Data saved successfully to $uri")
                contentTextView.text = "Data saved to ${uri.path}"
            } catch (e: Exception) {
                Log.e("SaveData", "Error saving data: ${e.message}")
            }
        } else {
            Log.e("SaveData", "Failed to create output stream")
        }
    }

    private fun getCellTowerInfo() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Use TelephonyCallback for Android 12+
                    val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
                            updateCellTowerData(cellInfo)
                        }
                    }
                    telephonyManager.registerTelephonyCallback(mainExecutor, telephonyCallback)
                } else {
                    // Use PhoneStateListener for Android < 12
                    val phoneStateListener = object : PhoneStateListener() {
                        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
                            updateCellTowerData(cellInfo)
                        }
                    }
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO)
                }
            } else {
                contentTextView.text = getString(R.string.permissions_denied)
            }
        } catch (e: SecurityException) {
            Log.e("CellInfoError", "Permission not granted: ${e.message}")
            contentTextView.text = getString(R.string.error_retrieving)
        }
    }

    private fun updateCellTowerData(cellInfoList: List<CellInfo>) {
        Log.d("CellInfoDebug", "CellInfo list size: ${cellInfoList.size}")
        if (cellInfoList.isNotEmpty()) {
            for (cellInfo in cellInfoList) {
                when (cellInfo) {
                    is android.telephony.CellInfoLte -> {
                        val cellIdentityLte = cellInfo.cellIdentity
                        val mcc = cellIdentityLte.mccString?.toIntOrNull() ?: 0
                        val mnc = cellIdentityLte.mncString?.toIntOrNull() ?: 0
                        val lac = cellIdentityLte.tac
                        val cid = cellIdentityLte.ci

                        if (mcc > 0 && mnc > 0 && lac != 65535 && cid != 268435455) {
                            val tower = CellTower(mcc, mnc, lac, cid)
                            if (!cellTowerData.contains(tower)) {
                                cellTowerData.add(tower)
                            }
                        }
                    }
                    is android.telephony.CellInfoGsm -> {
                        val cellIdentityGsm = cellInfo.cellIdentity
                        val mcc = cellIdentityGsm.mccString?.toIntOrNull() ?: 0
                        val mnc = cellIdentityGsm.mncString?.toIntOrNull() ?: 0
                        val lac = cellIdentityGsm.lac
                        val cid = cellIdentityGsm.cid

                        if (mcc > 0 && mnc > 0 && lac != 65535 && cid != 268435455) {
                            val tower = CellTower(mcc, mnc, lac, cid)
                            if (!cellTowerData.contains(tower)) {
                                cellTowerData.add(tower)
                            }
                        }
                    }
                }
            }
            updateTable()
        } else {
            contentTextView.text = getString(R.string.no_cell_info)
        }
    }

    private fun updateTable() {
        cellTowerTable.removeViews(1, cellTowerTable.childCount - 1) // Clear existing rows
        for ((index, tower) in cellTowerData.withIndex()) {
            val tableRow = TableRow(this)
            tableRow.addView(TextView(this).apply { text = (index + 1).toString() })
            tableRow.addView(TextView(this).apply { text = tower.mcc.toString() })
            tableRow.addView(TextView(this).apply { text = tower.mnc.toString() })
            tableRow.addView(TextView(this).apply { text = tower.lac.toString() })
            tableRow.addView(TextView(this).apply { text = tower.cid.toString() })
            cellTowerTable.addView(tableRow)
        }
    }

    data class CellTower(val mcc: Int, val mnc: Int, val lac: Int, val cid: Int)
}
