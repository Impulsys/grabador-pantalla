package com.impulsys.grabador

import android.net.Uri

data class Recording(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateMs: Long,
    val sizeBytes: Long,
    val durationMs: Long,
    val dataPath: String?
)
