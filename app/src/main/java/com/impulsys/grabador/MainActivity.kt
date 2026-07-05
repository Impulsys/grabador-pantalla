package com.impulsys.grabador

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var button: MaterialButton
    private lateinit var status: TextView

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] ?: hasMic()
        if (micOk) {
            launchProjectionRequest()
        } else {
            status.text = getString(R.string.necesita_micro)
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
            renderRecording(true)
            status.text = getString(R.string.grabando)
        } else {
            status.text = getString(R.string.permiso_denegado)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        button = findViewById(R.id.btnRecord)
        status = findViewById(R.id.txtStatus)
        button.setOnClickListener {
            if (RecorderService.isRunning) stopRecording() else requestAndStart()
        }
        renderRecording(RecorderService.isRunning)
    }

    private fun requestAndStart() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) launchProjectionRequest()
        else permsLauncher.launch(missing.toTypedArray())
    }

    private fun launchProjectionRequest() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecording() {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_STOP
        }
        startService(intent)
        renderRecording(false)
        status.text = getString(R.string.guardado)
    }

    private fun renderRecording(recording: Boolean) {
        button.text =
            if (recording) getString(R.string.detener) else getString(R.string.grabar)
        button.setIconResource(
            if (recording) R.drawable.ic_stop else R.drawable.ic_rec
        )
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        renderRecording(RecorderService.isRunning)
    }
}
