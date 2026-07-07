package com.impulsys.grabador

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var button: MaterialButton
    private lateinit var flyerButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var status: TextView
    private lateinit var flyerStatus: TextView
    private lateinit var emptyView: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: RecordingsAdapter

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var flyerUri: Uri? = null

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] ?: hasMic()
        if (micOk) ensureOverlayThenProject()
        else status.text = getString(R.string.necesita_micro)
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        launchProjectionRequest()
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

    private val flyerPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            flyerUri = uri
            flyerStatus.text = getString(R.string.portada_puesta)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        button = findViewById(R.id.btnRecord)
        flyerButton = findViewById(R.id.btnFlyer)
        refreshButton = findViewById(R.id.btnRefresh)
        status = findViewById(R.id.txtStatus)
        flyerStatus = findViewById(R.id.txtFlyer)
        emptyView = findViewById(R.id.txtEmpty)
        list = findViewById(R.id.listRecordings)

        adapter = RecordingsAdapter(
            onPlay = ::playRecording,
            onShare = ::shareRecording,
            onDelete = ::confirmDelete
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        button.setOnClickListener {
            if (RecorderService.isRunning) stopRecording() else requestAndStart()
        }
        flyerButton.setOnClickListener { flyerPicker.launch("image/*") }
        refreshButton.setOnClickListener { loadRecordings() }
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
        if (missing.isEmpty()) ensureOverlayThenProject()
        else permsLauncher.launch(missing.toTypedArray())
    }

    private fun ensureOverlayThenProject() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.permiso_overlay), Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        } else {
            launchProjectionRequest()
        }
    }

    private fun launchProjectionRequest() {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startRecordingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_DATA, data)
            flyerUri?.let { putExtra(RecorderService.EXTRA_FLYER, it.toString()) }
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
        // Damos un instante a que se finalice el archivo antes de refrescar.
        mainHandler.postDelayed({ loadRecordings() }, 1200)
    }

    private fun renderRecording(recording: Boolean) {
        button.text =
            if (recording) getString(R.string.detener) else getString(R.string.grabar)
        button.setIconResource(
            if (recording) R.drawable.ic_stop else R.drawable.ic_rec
        )
    }

    private fun loadRecordings() {
        ioExecutor.execute {
            val recordings = try {
                RecordingsStore.query(this)
            } catch (_: Exception) {
                emptyList()
            }
            mainHandler.post {
                adapter.submit(recordings)
                emptyView.visibility = if (recordings.isEmpty()) TextView.VISIBLE else TextView.GONE
            }
        }
    }

    private fun playRecording(rec: Recording) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(rec.uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.sin_reproductor), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecording(rec: Recording) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, rec.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.compartir_con)))
    }

    private fun confirmDelete(rec: Recording) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.eliminar_titulo))
            .setMessage("${rec.name}\n\n${getString(R.string.eliminar_mensaje)}")
            .setNegativeButton(getString(R.string.cancelar), null)
            .setPositiveButton(getString(R.string.eliminar)) { _, _ -> deleteRecording(rec) }
            .show()
    }

    private fun deleteRecording(rec: Recording) {
        ioExecutor.execute {
            val ok = RecordingsStore.delete(this, rec)
            mainHandler.post {
                val msg = if (ok) R.string.eliminada else R.string.no_eliminada
                Toast.makeText(this, getString(msg), Toast.LENGTH_SHORT).show()
                if (ok) loadRecordings()
            }
        }
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        renderRecording(RecorderService.isRunning)
        loadRecordings()
    }
}
