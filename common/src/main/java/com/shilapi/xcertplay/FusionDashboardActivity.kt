package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shilapi.xcertplay.glance.CarPlayGlance

/** Native local composition, using the independently negotiated instrument map stream. */
class FusionDashboardActivity : Activity(), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private val key = "fusion:dashboard"
    private var surface: Surface? = null
    private var frames = 0L
    private lateinit var foreground: FusionDashboardView
    private lateinit var texture: TextureView
    private lateinit var root: FrameLayout
    private val aspectChanged: () -> Unit = { layoutMap() }
    private val tick = object : Runnable {
        private var count = 0
        override fun run() {
            foreground.update(CarPlayGlance.snapshot(), MapMirrors.streamActive)
            if (++count % 10 == 0) Log.i("FusionDashboard", "stream111=${MapMirrors.streamActive} presented=$frames surface=${surface != null}")
            handler.postDelayed(this, 350)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(3, 10, 17)) }
        texture = TextureView(this).apply { surfaceTextureListener = this@FusionDashboardActivity }
        root.addView(texture)
        foreground = FusionDashboardView(this)
        root.addView(foreground, FrameLayout.LayoutParams(-1, -1))
        val carplay = action("打开完整 CarPlay 与音乐") {
            startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        }
        val home = action("Home，返回 DiPlay 主界面并保留连接") {
            startActivity(Intent(this, DiPlayActivity::class.java).putExtra("page", "home")
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        root.addView(carplay); root.addView(home)
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutMap()
            place(carplay, .02f, .836f, .22f, .147f)
            place(home, .89f, .836f, .095f, .147f)
        }
        setContentView(root)
        MapMirrors.addAspectListener(aspectChanged)
    }

    private fun action(description: String, block: () -> Unit) = Button(this).apply {
        text = ""; contentDescription = description
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { block() }
    }
    private fun place(view: View, x: Float, y: Float, w: Float, h: Float) {
        val p = FrameLayout.LayoutParams((root.width * w).toInt(), (root.height * h).toInt()).apply {
            leftMargin = (root.width * x).toInt(); topMargin = (root.height * y).toInt()
        }
        val old = view.layoutParams as? FrameLayout.LayoutParams
        if (old?.width != p.width || old.height != p.height || old.leftMargin != p.leftMargin || old.topMargin != p.topMargin) view.layoutParams = p
    }
    private fun layoutMap() {
        if (root.width == 0 || root.height == 0) return
        val availableWidth = (root.width * .74f).toInt()
        val w = minOf(availableWidth, (root.height * MapMirrors.streamAspect).toInt())
        val h = (w / MapMirrors.streamAspect).toInt()
        val p = FrameLayout.LayoutParams(w, h).apply {
            leftMargin = root.width - w; topMargin = (root.height - h) / 2
        }
        val old = texture.layoutParams as? FrameLayout.LayoutParams
        if (old?.width != p.width || old.height != p.height || old.leftMargin != p.leftMargin || old.topMargin != p.topMargin) texture.layoutParams = p
    }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also { MapMirrors.set(key, it) }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) { frames++ }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        MapMirrors.set(key, null); surface?.release(); surface = null
        return true
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        MapMirrors.removeAspectListener(aspectChanged)
        MapMirrors.set(key, null); surface?.release(); surface = null
        super.onDestroy()
    }
}
