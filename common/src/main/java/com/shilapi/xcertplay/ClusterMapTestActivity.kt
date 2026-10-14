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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Receives only stream 111. No screenshot copy, overlay permission, or vehicle data. */
class ClusterMapTestActivity : Activity(), TextureView.SurfaceTextureListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var surface: Surface? = null
    private var frames = 0L
    private val key = "research:cluster-test"
    private val update = object : Runnable {
        override fun run() {
            status.text = "副屏流 111：${if (MapMirrors.streamActive) "已建立" else "等待 iPhone"} · 画面更新 $frames 次\n请在 iPhone 打开 Apple 地图并开始导航"
            Log.i("ClusterMapTest", "stream111=${MapMirrors.streamActive} presented=$frames surface=${surface != null}")
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f }
        root.addView(status)
        val buttons = LinearLayout(this)
        buttons.addView(Button(this).apply {
            text = "启用副屏并重新连接"
            setOnClickListener {
                startActivity(Intent(this@ClusterMapTestActivity, DiPlayActivity::class.java)
                    .putExtra("cluster_test_reconnect", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
        })
        buttons.addView(Button(this).apply {
            text = "回到 CarPlay"
            setOnClickListener { startActivity(Intent(this@ClusterMapTestActivity, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); finish() }
        })
        root.addView(buttons)
        val viewport = FrameLayout(this)
        val texture = TextureView(this).apply { surfaceTextureListener = this@ClusterMapTestActivity }
        viewport.addView(texture)
        viewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = viewport.width
            val height = viewport.height
            val aspect = MapMirrors.streamAspect
            val w = minOf(width, (height * aspect).toInt())
            val h = (w / aspect).toInt()
            texture.layoutParams = FrameLayout.LayoutParams(w, h, android.view.Gravity.CENTER)
        }
        root.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update); super.onPause() }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also { MapMirrors.set(key, it) }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) { frames++ }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        MapMirrors.set(key, null)
        surface?.release(); surface = null
        return true
    }
    override fun onDestroy() {
        handler.removeCallbacks(update)
        MapMirrors.set(key, null)
        surface?.release(); surface = null
        super.onDestroy()
    }
}
