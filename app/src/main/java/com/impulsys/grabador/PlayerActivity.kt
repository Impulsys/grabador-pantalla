package com.impulsys.grabador

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/** Reproductor de video propio (VideoView), no necesita ninguna app externa. */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "video_uri"
        const val EXTRA_TITLE = "video_title"
    }

    private lateinit var videoView: VideoView
    private var position = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.reproducir)

        videoView = findViewById(R.id.videoView)
        val error = findViewById<TextView>(R.id.txtError)

        val uriStr = intent.getStringExtra(EXTRA_URI)
        if (uriStr == null) {
            error.visibility = TextView.VISIBLE
            return
        }

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
            controller.show(0)
        }
        videoView.setOnErrorListener { _, _, _ ->
            error.visibility = TextView.VISIBLE
            true
        }
        videoView.setVideoURI(Uri.parse(uriStr))
        videoView.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        position = videoView.currentPosition
        videoView.pause()
    }

    override fun onResume() {
        super.onResume()
        if (position > 0) videoView.seekTo(position)
    }
}
