package com.helltar.fosshine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.helltar.fosshine.ui.FosshineTheme
import com.helltar.fosshine.ui.StationScreen

class MainActivity : ComponentActivity() {

    private val viewModel: StationViewModel by viewModels()

    // the link works either way: without notifications the service's status and alerts are just not shown,
    // without location Android keeps the AP's name, so the serial, to itself, and without nearby devices
    // the connect card cannot tell that the station is still starting up
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.connect() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FosshineTheme {
                StationScreen(viewModel, onConnect = ::connect)
            }
        }
    }

    private fun connect() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        else viewModel.connect()
    }
}
