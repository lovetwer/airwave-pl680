package com.airwave.pl680

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.airwave.pl680.ui.AirwaveApp
import com.airwave.pl680.ui.AirwaveTheme

class MainActivity : ComponentActivity() {
    private lateinit var app: RadioApp
    private val notifyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = RadioApp(this)
        app.onFirstPlay = { askNotification() }
        app.connect()
        setContent {
            AirwaveTheme {
                AirwaveApp(app)
            }
        }
    }

    private fun askNotification() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        app.connect()
    }

    override fun onStop() {
        app.pauseForBackground()
        super.onStop()
    }

    override fun onDestroy() {
        app.release()
        super.onDestroy()
    }
}
