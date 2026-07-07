package com.impulsys.grabador

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class RecordingsAdapter(
    private val onPlay: (Recording) -> Unit,
    private val onShare: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    private val items = ArrayList<Recording>()
    private val thumbExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun submit(newItems: List<Recording>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rec = items[position]
        holder.name.text = rec.name
        holder.meta.text = buildMeta(holder.itemView.context, rec)

        holder.btnPlay.setOnClickListener { onPlay(rec) }
        holder.btnShare.setOnClickListener { onShare(rec) }
        holder.btnDelete.setOnClickListener { onDelete(rec) }

        holder.thumb.setImageResource(R.drawable.thumb_placeholder)
        holder.thumb.tag = rec.id
        loadThumb(holder.thumb, rec)
    }

    private fun buildMeta(context: Context, rec: Recording): String {
        val dur = formatDuration(rec.durationMs)
        val size = formatSize(rec.sizeBytes)
        val date = dateFormat.format(Date(rec.dateMs))
        return "$date · $dur · $size"
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }

    private fun loadThumb(imageView: ImageView, rec: Recording) {
        val context = imageView.context.applicationContext
        thumbExecutor.execute {
            val bmp: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(rec.uri, Size(220, 150), null)
                } else {
                    @Suppress("DEPRECATION")
                    rec.dataPath?.let {
                        ThumbnailUtils.createVideoThumbnail(
                            it, MediaStore.Images.Thumbnails.MINI_KIND
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                mainHandler.post {
                    if (imageView.tag == rec.id) imageView.setImageBitmap(bmp)
                }
            }
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.imgThumb)
        val name: TextView = view.findViewById(R.id.txtName)
        val meta: TextView = view.findViewById(R.id.txtMeta)
        val btnPlay: MaterialButton = view.findViewById(R.id.btnPlay)
        val btnShare: MaterialButton = view.findViewById(R.id.btnShare)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDelete)
    }
}
