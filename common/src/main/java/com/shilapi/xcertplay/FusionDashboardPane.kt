package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.FrameLayout
import com.shilapi.xcertplay.glance.CarPlayGlance

/** Remains mounted alongside the primary texture; changing page does not replace its decoder. */
internal class FusionDashboardPane(context: Context, private val main: () -> Unit,
    private val home: () -> Unit, private val onFrame: () -> Unit) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private val key = "fusion:dashboard"
    private var surface: Surface? = null
    var ready = false
        private set
    var pageActive = false
        set(value) {
            field = value
            importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            handler.removeCallbacks(tick)
            if (value) handler.post(tick)
        }
    private val foreground = FusionDashboardView(context)
    private val texture = TextureView(context).apply { surfaceTextureListener = this@FusionDashboardPane }
    private val swipe = DisplayPageSwipe(resources.displayMetrics.density) { main() }
    private val aspectChanged: () -> Unit = { layoutChildren() }
    private val tick = object : Runnable {
        override fun run() { foreground.update(CarPlayGlance.snapshot(), MapMirrors.streamActive); handler.postDelayed(this, 350) }
    }
    private val carplay = Button(context).apply {
        text = ""; contentDescription = "CarPlay 主屏与音乐"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { main() }
    }
    private val homeButton = Button(context).apply {
        text = ""; contentDescription = "返回 DiPlay 主页，保留连接"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { home() }
    }
    init {
        setBackgroundColor(Color.rgb(3, 10, 17))
        addView(texture)
        addView(foreground, LayoutParams(-1, -1))
        addView(carplay); addView(homeButton)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutChildren() }
        foreground.update(CarPlayGlance.snapshot(), MapMirrors.streamActive)
        pageActive = false
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!pageActive) return false
        if (swipe.handle(event, true)) { carplay.isPressed = false; homeButton.isPressed = false; return true }
        return super.dispatchTouchEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = pageActive
    private fun place(view: android.view.View, w: Int, h: Int, x: Int, y: Int) {
        val old = view.layoutParams as? LayoutParams
        if (old?.width == w && old.height == h && old.leftMargin == x && old.topMargin == y) return
        view.layoutParams = LayoutParams(w, h).apply { leftMargin = x; topMargin = y }
    }
    private fun layoutChildren() {
        if (width == 0 || height == 0) return
        val w = minOf((width * .74f).toInt(), (height * MapMirrors.streamAspect).toInt())
        val h = (w / MapMirrors.streamAspect).toInt()
        place(texture, w, h, width - w, (height - h) / 2)
        val scale = width / 1920f
        val top = (height - 142 * scale).toInt().coerceAtLeast(0)
        val buttonHeight = minOf(height - top, (128 * scale).toInt())
        place(carplay, (width * .22f).toInt(), buttonHeight, (width * .02f).toInt(), top)
        place(homeButton, (width * .095f).toInt(), buttonHeight, (width * .89f).toInt(), top)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); MapMirrors.addAspectListener(aspectChanged) }
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick); MapMirrors.removeAspectListener(aspectChanged)
        MapMirrors.set(key, null); surface?.release(); surface = null; ready = false
        super.onDetachedFromWindow()
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also { MapMirrors.set(key, it) }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) { ready = true; onFrame() }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        MapMirrors.set(key, null); surface?.release(); surface = null; ready = false
        return true
    }
}
