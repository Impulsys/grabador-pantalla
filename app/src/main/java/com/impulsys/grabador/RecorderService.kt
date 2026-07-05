package com.impulsys.grabador

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.impulsys.grabador.START"
        const val ACTION_STOP = "com.impulsys.grabador.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "grabador_channel"
        private const val NOTIF_ID = 1337

        @Volatile
        var isRunning = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var outputFile: File? = null
    private var outputUri: Uri? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtraCompat(EXTRA_DATA)
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification())
                startRecording(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        try {
            val metrics = screenMetrics()
            val width = (metrics.widthPixels / 2) * 2
            val height = (metrics.heightPixels / 2) * 2
            val dpi = metrics.densityDpi

            prepareOutput()
            val recorder = createRecorder(width, height)
            mediaRecorder = recorder

            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, data)
            mediaProjection = projection
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                    stopSelf()
                }
            }, null)

            virtualDisplay = projection.createVirtualDisplay(
                "GrabadorPantalla",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface, null, null
            )
            recorder.start()
            isRunning = true
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_iniciar) + " " + e.message, Toast.LENGTH_LONG).show()
            stopRecording()
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRunning && mediaRecorder == null) return
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        finalizeOutput()
        isRunning = false
    }

    private fun createRecorder(width: Int, height: Int): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setVideoSize(width, height)
        recorder.setVideoFrameRate(30)
        recorder.setVideoEncodingBitRate(8_000_000)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pfd = contentResolver.openFileDescriptor(outputUri!!, "rw")!!
            recorder.setOutputFile(pfd.fileDescriptor)
        } else {
            recorder.setOutputFile(outputFile!!.absolutePath)
        }
        recorder.prepare()
        return recorder
    }

    private fun prepareOutput() {
        val name = "Grabacion_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Grabador")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            )
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Grabador"
            )
            if (!dir.exists()) dir.mkdirs()
            outputFile = File(dir, "$name.mp4")
        }
    }

    private fun finalizeOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputUri?.let { uri ->
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, values, null, null)
            }
        } else {
            outputFile?.let { file ->
                android.media.MediaScannerConnection.scanFile(
                    this, arrayOf(file.absolutePath), arrayOf("video/mp4"), null
                )
            }
        }
    }

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Grabación de pantalla",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.grabando))
            .setSmallIcon(R.drawable.ic_rec)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}

private fun Intent.getParcelableExtraCompat(key: String): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
