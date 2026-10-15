package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.FrameLayout
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.vehicle.Esp32VehicleBleClient
import com.shilapi.xcertplay.vehicle.VehicleBleSettings
import com.shilapi.xcertplay.vehicle.VehicleState

/** Remains mounted alongside the primary texture; changing page does not replace its decoder. */
internal class FusionDashboardPane(context: Context, private val main: () -> Unit,
    private val home: () -> Unit, private val onFrame: () -> Unit, private val toggleTheme: () -> Unit = {}, private val toggleMapTheme: () -> Unit = {}) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val choreographer = Choreographer.getInstance()
    private val frameClock = DashboardFrameClock()
    private var lastState: VehicleState? = null
    private var lastEnabled: Boolean? = null
    private var lastPhoneConnected: Boolean? = null
    private var lastDemonstration: Boolean? = null
    private var mediaUpdatedNs = Long.MIN_VALUE
    private val key = "fusion:dashboard"
    private var surface: Surface? = null
    private var night = true
    private var indicatorSource: DashboardIndicatorSource = DemoDashboardIndicators
    fun setIndicatorSource(source: DashboardIndicatorSource) { indicatorSource = source; refreshForeground() }
    fun setNightMode(value: Boolean) {
        night = value
        foreground.setNightMode(value)
        themeButton.contentDescription = if (value) "切换到日间模式" else "切换到夜间模式"
        themeButton.tooltipText = themeButton.contentDescription
    }
    fun setMapNightMode(value: Boolean) {
        foreground.setMapNightMode(value)
        mapThemeButton.contentDescription = if (value) "仅地图切换到日间" else "仅地图切换到夜间"
        mapThemeButton.tooltipText = mapThemeButton.contentDescription
    }
    private fun refreshForeground(nowNs: Long = System.nanoTime()) {
        val state = Esp32VehicleBleClient.getInstance(context).snapshot()
        val enabled = VehicleBleSettings.enabled(context)
        val glance = CarPlayGlance.snapshot()
        if (state != lastState || enabled != lastEnabled || glance.connected != lastPhoneConnected) {
            val data = vehicleDashboardSnapshot(state, enabled)
            foreground.setVehicle(data.vehicle, nowNs, snap = lastDemonstration != data.indicators.demonstration)
            foreground.setIndicators(if (data.indicators.demonstration) indicatorSource.snapshot() else data.indicators)
            lastState = state; lastEnabled = enabled; lastPhoneConnected = glance.connected
            lastDemonstration = data.indicators.demonstration
        } else if (lastDemonstration == true) foreground.setIndicators(indicatorSource.snapshot())
        // Media, clock and blink stay lightweight at 20 Hz; telemetry is sampled each display frame.
        if (mediaUpdatedNs == Long.MIN_VALUE || nowNs - mediaUpdatedNs >= 50_000_000L) {
            mediaUpdatedNs = nowNs
            foreground.setMedia(CarPlayMediaKeys.dashboardSnapshot())
            foreground.update(glance, MapMirrors.streamActive)
        }
        foreground.renderFrame(nowNs)
    }
    var ready = false
        private set
    var pageActive = false
        set(value) {
            field = value
            importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            restartFrames()
        }
    private val foreground = FusionDashboardView(context)
    private val texture = TextureView(context).apply { surfaceTextureListener = this@FusionDashboardPane }
    private val swipe = DisplayPageSwipe(resources.displayMetrics.density) { main() }
    private val aspectChanged: () -> Unit = { layoutChildren() }
    private val tick = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!shouldAnimate()) return
            if (frameClock.accept(frameTimeNanos)) refreshForeground(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }
    private fun shouldAnimate() = pageActive && isAttachedToWindow && windowVisibility == VISIBLE && isShown
    private fun restartFrames() {
        choreographer.removeFrameCallback(tick)
        frameClock.reset(); lastState = null; lastDemonstration = null
        foreground.resetFrameMetrics()
        if (shouldAnimate()) choreographer.postFrameCallback(tick)
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) restartFrames()
    }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) restartFrames()
    }
    private val carplay = Button(context).apply {
        text = ""; contentDescription = "CarPlay 主屏与音乐"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { main() }
    }
    private val homeButton = Button(context).apply {
        text = ""; contentDescription = "返回 DiPlay 主页，保留连接"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { home() }
    }
    private val themeButton = Button(context).apply {
        text = ""; contentDescription = "切换到日间模式"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { toggleTheme() }
    }
    private val mapThemeButton = Button(context).apply {
        text = ""; contentDescription = "仅切换地图日夜模式"; setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { toggleMapTheme() }
    }
    init {
        setBackgroundColor(Color.rgb(3, 10, 17))
        addView(texture)
        addView(foreground, LayoutParams(-1, -1))
        addView(carplay); addView(homeButton); addView(themeButton); addView(mapThemeButton)
        foreground.onBackgroundChanged = { setBackgroundColor(it) }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutChildren() }
        refreshForeground()
        pageActive = false
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!pageActive) return false
        if (swipe.handle(event, true)) { carplay.isPressed = false; homeButton.isPressed = false; themeButton.isPressed = false; mapThemeButton.isPressed = false; return true }
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
        val w = (width * .74f).toInt()
        val h = height
        place(texture, w, h, width - w, 0)
        // Cover the whole map region without distortion; crop edges around the map's centre.
        val viewAspect = w.toFloat() / h
        val streamAspect = MapMirrors.streamAspect.toFloat()
        texture.setTransform(Matrix().apply {
            setScale(maxOf(1f, streamAspect / viewAspect), maxOf(1f, viewAspect / streamAspect), w / 2f, h / 2f)
        })
        val scale = width / 1920f
        val top = (height - 142 * scale).toInt().coerceAtLeast(0)
        val buttonHeight = minOf(height - top, (128 * scale).toInt())
        place(carplay, (width * .22f).toInt(), buttonHeight, (width * .02f).toInt(), top)
        place(homeButton, (width * (112f / 1920)).toInt(), buttonHeight, (width * (1748f / 1920)).toInt(), top)
        place(themeButton, (width * (110f / 1920)).toInt(), buttonHeight, (width * (1505f / 1920)).toInt(), top)
        place(mapThemeButton, (width * (110f / 1920)).toInt(), buttonHeight, (width * (1620f / 1920)).toInt(), top)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); MapMirrors.addAspectListener(aspectChanged)
        restartFrames()
    }
    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(tick); foreground.resetFrameMetrics()
        MapMirrors.removeAspectListener(aspectChanged)
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
