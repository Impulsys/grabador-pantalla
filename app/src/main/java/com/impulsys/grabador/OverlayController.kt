package com.impulsys.grabador

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Maneja las ventanas superpuestas (overlays) que SÍ deben verse en la grabación:
 * las cajas para tapar zonas sensibles y la portada/flyer inicial.
 */
class OverlayController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val covers = mutableListOf<View>()
    private var flyer: View? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), ctx.resources.displayMetrics
    ).toInt()

    /** Agrega una caja para tapar una zona. Se mueve con 1 dedo, agranda con 2, borra con toque largo. */
    fun addCover() {
        val box = View(ctx).apply {
            setBackgroundResource(R.drawable.cover_box)
        }
        val lp = WindowManager.LayoutParams(
            dp(220), dp(120),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = dp(40)
            y = dp(160)
        }
        box.setOnTouchListener(CoverTouch(box, lp))
        wm.addView(box, lp)
        covers.add(box)
    }

    private inner class CoverTouch(
        private val view: View,
        private val lp: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var initX = 0
        private var initY = 0
        private var initSpan = 0f
        private var initW = 0
        private var initH = 0
        private var resizing = false
        private var moved = false
        private val slop = dp(6)
        private val removeRunnable = Runnable { removeCover(view) }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY
                    initX = lp.x; initY = lp.y
                    moved = false; resizing = false
                    handler.postDelayed(removeRunnable, 700)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        handler.removeCallbacks(removeRunnable)
                        resizing = true
                        initSpan = spanOf(e)
                        initW = lp.width; initH = lp.height
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizing && e.pointerCount >= 2) {
                        val scale = spanOf(e) / initSpan.coerceAtLeast(1f)
                        lp.width = (initW * scale).toInt().coerceAtLeast(dp(60))
                        lp.height = (initH * scale).toInt().coerceAtLeast(dp(40))
                        wm.updateViewLayout(view, lp)
                    } else {
                        val dx = e.rawX - startX
                        val dy = e.rawY - startY
                        if (abs(dx) > slop || abs(dy) > slop) {
                            moved = true
                            handler.removeCallbacks(removeRunnable)
                        }
                        lp.x = initX + dx.toInt()
                        lp.y = initY + dy.toInt()
                        wm.updateViewLayout(view, lp)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(removeRunnable)
                    resizing = false
                }
            }
            return true
        }

        private fun spanOf(e: MotionEvent): Float {
            if (e.pointerCount < 2) return 0f
            return hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
        }
    }

    private fun removeCover(box: View) {
        try {
            wm.removeView(box)
        } catch (_: Exception) {
        }
        covers.remove(box)
    }

    /** Muestra la portada/flyer a pantalla completa por [durationMs] milisegundos. */
    fun showFlyer(uri: Uri, durationMs: Long) {
        removeFlyer()
        val frame = FrameLayout(ctx).apply { setBackgroundColor(0xFF000000.toInt()) }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                setImageURI(uri)
            } catch (_: Exception) {
            }
        }
        frame.addView(
            img,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        wm.addView(frame, lp)
        flyer = frame
        handler.postDelayed({ removeFlyer() }, durationMs)
    }

    private fun removeFlyer() {
        flyer?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        flyer = null
    }

    fun removeAll() {
        handler.removeCallbacksAndMessages(null)
        covers.toList().forEach { removeCover(it) }
        covers.clear()
        removeFlyer()
    }
}
