package com.shilapi.xcertplay

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local foreground only: the live map remains a separate hardware decoded texture. */
internal class FusionDashboardView(context: Context) : View(context) {
    data class VehiclePreview(val speed: Int? = 48, val gear: String = "D", val power: String = "2.6",
        val battery: Int? = 78, val voltage: String = "72.8", val current: String = "35.7", val range: Int? = 66, val odometer: String = "1,186", val powerKw: Double? = 2.6)

    private var vehicle = VehiclePreview()
    private val speedDisplay = DashboardDisplayValue(45, snapZero = true).apply { retarget(48.0, 0, true) }
    private val powerDisplay = DashboardDisplayValue(60, resetOnSignChange = true).apply { retarget(2.6, 0, true) }
    private var frameNs = System.nanoTime()
    fun setVehicle(value: VehiclePreview, nowNs: Long = System.nanoTime(), snap: Boolean = false) {
        vehicle = value
        speedDisplay.retarget(value.speed?.toDouble(), nowNs, snap)
        powerDisplay.retarget(value.powerKw, nowNs, snap)
    }
    fun renderFrame(nowNs: Long) { frameNs = nowNs; invalidate() }
    private var drawWindowNs = 0L
    private var drawCount = 0
    private var drawCostNs = 0L
    private var drawMaxNs = 0L
    fun resetFrameMetrics() { drawWindowNs = 0; drawCount = 0; drawCostNs = 0; drawMaxNs = 0 }
    private var indicators = DemoDashboardIndicators.snapshot()
    private var night = true
    private var mapNight = true
    fun setMapNightMode(value: Boolean) { mapNight = value; invalidate() }
    internal var themeFraction = 1f
        private set
    private var themeAnimator: ValueAnimator? = null
    var onBackgroundChanged: ((Int) -> Unit)? = null
    fun setIndicators(value: DashboardIndicatorSnapshot) { indicators = value }
    fun setNightMode(value: Boolean, animate: Boolean = isAttachedToWindow && width > 0) {
        if (night == value) return
        night = value
        themeAnimator?.cancel()
        val target = if (value) 1f else 0f
        val transitionDuration = FusionThemeSettings.duration(context, toNight = value)
        if (!animate || transitionDuration == 0) { themeFraction = target; applyPalette(); return }
        themeAnimator = ValueAnimator.ofFloat(themeFraction, target).apply {
            duration = transitionDuration.toLong()
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { themeFraction = it.animatedValue as Float; applyPalette() }
            start()
        }
    }
    private fun themed(day: Int, night: Int) = ColorUtils.blendARGB(day, night, themeFraction)
    private fun applyPalette() {
        white = themed(Color.rgb(20, 35, 53), Color.rgb(244, 247, 255))
        muted = themed(Color.rgb(82, 102, 123), Color.rgb(167, 184, 210))
        green = themed(Color.rgb(0, 145, 82), Color.rgb(28, 233, 100))
        glyph?.setTint(white)
        rebuildBackgroundShaders()
        onBackgroundChanged?.invoke(themed(Color.rgb(232, 239, 246), Color.rgb(3, 10, 17)))
        invalidate()
    }
    override fun onDetachedFromWindow() {
        themeAnimator?.cancel(); themeAnimator = null
        themeFraction = if (night) 1f else 0f
        applyPalette()
        super.onDetachedFromWindow()
    }
    private var glance = CarPlayGlance.Snapshot()
    private var mapActive = false
    private var media = CarPlayMediaKeys.DashboardMedia(com.shilapi.xcertplay.media.CarPlayNowPlaying(), null)
    fun setMedia(value: CarPlayMediaKeys.DashboardMedia) { media = value }
    private var blink = false
    private var clock = ""
    private var clockSecond = -1L
    private val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var white = Color.rgb(244, 247, 255)
    private var muted = Color.rgb(167, 184, 210)
    private val blue = Color.rgb(16, 157, 255)
    private var green = Color.rgb(28, 233, 100)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 255 }
    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val rect = RectF()
    private val path = Path()
    private var leftFade = LinearGradient(0f, 0f, 1030f, 0f,
        intArrayOf(Color.rgb(3, 10, 17), Color.argb(247, 3, 10, 17), Color.argb(185, 3, 10, 17), Color.TRANSPARENT),
        floatArrayOf(0f, .53f, .76f, 1f), Shader.TileMode.CLAMP)
    private var gaugeFade: Shader? = null
    private var footerFill: Shader? = null
    private val speedArcShader = LinearGradient(76f, 666f, 602f, 140f,
        Color.rgb(8, 137, 242), Color.rgb(42, 179, 255), Shader.TileMode.CLAMP)
    private val recoveryArcShader = LinearGradient(76f, 666f, 602f, 140f,
        Color.rgb(0, 172, 94), Color.rgb(28, 233, 100), Shader.TileMode.CLAMP)
    private var logicalHeight = 864f
    private var edgeFade = LinearGradient(0f, 0f, 0f, 864f,
        intArrayOf(Color.argb(100, 3, 10, 17), Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(230, 3, 10, 17)),
        floatArrayOf(0f, .20f * 2f / 3f, .70f, 1f), Shader.TileMode.CLAMP)
    private val topFadeSize = 140f * 2f / 3f
    private val rightFadeSize = 170f * 2f / 3f
    private var mapTopFade: Shader? = null
    private var mapRightFade: Shader? = null
    private var glyphType: Int? = null
    private var glyph: android.graphics.drawable.Drawable? = null

    fun update(snapshot: CarPlayGlance.Snapshot, active: Boolean) {
        glance = snapshot
        mapActive = active
        blink = (android.os.SystemClock.uptimeMillis() / 700) % 2 == 0L
        val second = System.currentTimeMillis() / 1000
        if (second != clockSecond) { clockSecond = second; clock = formatter.format(Date()) }
        if (glyphType != snapshot.maneuverType) {
            glyphType = snapshot.maneuverType
            glyph = snapshot.maneuverType?.let { type ->
                val id = when (type) {
                    1, 20, 47 -> R.drawable.ic_maneuver_left
                    2, 21, 48 -> R.drawable.ic_maneuver_right
                    13, 22, 49, 52 -> R.drawable.ic_maneuver_slight_left
                    14, 23, 50, 53 -> R.drawable.ic_maneuver_slight_right
                    4, 18, 19, 26 -> if (snapshot.drivingSide == 1) R.drawable.ic_maneuver_u_turn_right else R.drawable.ic_maneuver_u_turn_left
                    6, 7, in 28..46 -> R.drawable.ic_maneuver_roundabout
                    10, 12, 24, 25, 27 -> R.drawable.ic_maneuver_destination
                    3, 5, 8, 9, 11, 51 -> R.drawable.ic_maneuver_straight
                    else -> 0
                }
                if (id == 0) null else ContextCompat.getDrawable(context, id)?.mutate()?.apply { setTint(white) }
            }
        }
        contentDescription = "${if (indicators.demonstration) "演示" else "车辆"}仪表，速度 ${vehicle.speed ?: "--"}，电量 ${vehicle.battery ?: "--"}%"
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        logicalHeight = if (w > 0) h * 1920f / w else 864f
        rebuildBackgroundShaders()
    }
    private fun rebuildBackgroundShaders() {
        val base = themed(Color.rgb(232, 239, 246), Color.rgb(3, 10, 17))
        val r = Color.red(base); val g = Color.green(base); val b = Color.blue(base)
        // Keep the texture's left boundary fully covered, then blend around the dial's contour.
        leftFade = LinearGradient(0f, 0f, 760f, 0f,
            intArrayOf(Color.rgb(r, g, b), Color.rgb(r, g, b), Color.argb(145, r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, 500f / 760f, .84f, 1f), Shader.TileMode.CLAMP)
        gaugeFade = RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.rgb(r, g, b), Color.rgb(r, g, b), Color.argb(165, r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, .60f, .78f, 1f), Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(540f, logicalHeight * .58f); postTranslate(380f, logicalHeight * .48f) })
        }
        footerFill = LinearGradient(0f, 722f, 0f, 850f,
            themed(Color.argb(247, 250, 253, 255), Color.argb(245, 9, 20, 31)),
            themed(Color.argb(247, 230, 238, 245), Color.argb(247, 4, 11, 18)), Shader.TileMode.CLAMP)
        // Sample a quintic smoothstep: flat slopes at both ends avoid a visible fade boundary.
        val stops = FloatArray(33) { it / 32f }
        fun fadeColors(increasing: Boolean) = IntArray(stops.size) { index ->
            val t = stops[index]
            val smooth = t * t * t * (t * (t * 6f - 15f) + 10f)
            Color.argb((255f * if (increasing) smooth else 1f - smooth).toInt().coerceIn(0, 255), r, g, b)
        }
        mapTopFade = LinearGradient(0f, 0f, 0f, topFadeSize, fadeColors(false), stops, Shader.TileMode.CLAMP)
        mapRightFade = LinearGradient(1920f - rightFadeSize, 0f, 1920f, 0f, fadeColors(true), stops, Shader.TileMode.CLAMP)
        edgeFade = LinearGradient(0f, 0f, 0f, logicalHeight,
            intArrayOf(Color.argb(100, r, g, b), Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(230, r, g, b)),
            floatArrayOf(0f, .20f * 2f / 3f, .70f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val measure = android.util.Log.isLoggable("DiPlayRender", android.util.Log.DEBUG)
        val drawStartedNs = if (measure) System.nanoTime() else 0L
        canvas.save()
        canvas.scale(width / 1920f, width / 1920f)
        paint.shader = null; paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = leftFade; paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 1920f, logicalHeight, paint)
        paint.shader = gaugeFade
        canvas.drawRect(0f, 0f, 1920f, logicalHeight, paint)
        paint.shader = edgeFade
        canvas.drawRect(0f, 0f, 1920f, logicalHeight, paint)
        paint.shader = mapTopFade
        canvas.drawRect(500f, 0f, 1920f, topFadeSize, paint)
        paint.shader = mapRightFade
        canvas.drawRect(1920f - rightFadeSize, 0f, 1920f, logicalHeight, paint)
        paint.shader = null

        drawIndicators(canvas)

        canvas.save()
        canvas.translate(0f, (logicalHeight - 864f) / 2)
        canvas.translate(137f, 0f)
        canvas.scale(1.07f, 1.07f, 339f, 403f)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 16f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = themed(Color.rgb(184, 201, 215), Color.rgb(17, 32, 44))
        rect.set(76f, 140f, 602f, 666f)
        canvas.drawArc(rect, 140f, 260f, false, paint)
        val powerRing = dashboardPowerRing(powerDisplay.sample(frameNs))
        val powerSweep = 260f * powerRing.fraction
        val accent = if (powerRing.recovery) Color.rgb(28, 233, 100) else Color.rgb(85, 197, 255)
        paint.color = Color.WHITE; paint.shader = if (powerRing.recovery) recoveryArcShader else speedArcShader
        canvas.drawArc(rect, 140f, powerSweep, false, paint)
        paint.shader = null
        paint.strokeWidth = 1.5f; paint.color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
        rect.set(86f, 150f, 592f, 656f)
        canvas.drawArc(rect, 140f, powerSweep, false, paint)
        paint.strokeWidth = 3f
        paint.color = Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))
        rect.set(61f, 125f, 617f, 681f)
        canvas.drawArc(rect, 140f, powerSweep, false, paint)
        paint.strokeCap = Paint.Cap.BUTT; paint.style = Paint.Style.FILL
        text(canvas, (speedDisplay.sample(frameNs)?.let { kotlin.math.round(it).toInt().toString() } ?: "--"), 339f, 409f, 224f, white, bold, Paint.Align.CENTER)
        text(canvas, "km/h", 339f, 461f, 42f, muted, regular, Paint.Align.CENTER)
        line(canvas, 155f, 502f, 523f, 502f)
        text(canvas, vehicle.gear, 339f, 577f, 68f, dashboardGearColor(vehicle.gear), medium, Paint.Align.CENTER)
        text(canvas, "${vehicle.power} kW", 339f, 643f, 42f, white, regular, Paint.Align.CENTER)
        canvas.restore()

        if (glance.maneuverType != null) {
            glass(canvas, 1030f, 8f, 1860f, 109f, 48f)
            glyph?.let { it.setBounds(1058, 30, 1119, 88); it.draw(canvas) }
            val distance = if (glance.distanceMeters < 1000) "${glance.distanceMeters} m"
                else String.format(Locale.US, "%.1f km", glance.distanceMeters / 1000.0)
            text(canvas, distance, 1140f, 72f, 47f, white, medium)
            text(canvas, shorten(glance.road.ifBlank { "沿导航路线行驶" }, 18), 1370f, 69f, 32f, white)
        }
        if (!mapActive) {
            text(canvas, "地图连接中", 1390f, logicalHeight / 2 - 20f, 42f, white, medium, Paint.Align.CENTER)
            text(canvas, "请连接 CarPlay 并启用副屏地图", 1390f, logicalHeight / 2 + 26f, 26f, muted, regular, Paint.Align.CENTER)
        }

        canvas.save()
        canvas.translate(0f, logicalHeight - 864f)
        glass(canvas, 10f, 722f, 1910f, 850f, 32f, footerFill)
        rect.set(53f, 750f, 125f, 822f)
        val cover = media.artwork?.takeUnless { it.isRecycled }
        if (cover != null) {
            canvas.save()
            path.reset(); path.addRoundRect(rect, 17f, 17f, Path.Direction.CW); canvas.clipPath(path)
            val side = minOf(cover.width, cover.height)
            val left = (cover.width - side) / 2; val top = (cover.height - side) / 2
            canvas.drawBitmap(cover, Rect(left, top, left + side, top + side), rect, artworkPaint)
            canvas.restore()
        } else {
            paint.color = Color.rgb(245, 44, 112); canvas.drawRoundRect(rect, 19f, 19f, paint)
            text(canvas, "♫", 89f, 806f, 52f, Color.WHITE, medium, Paint.Align.CENTER)
        }
        val song = media.info.title?.takeIf { it.isNotBlank() } ?: glance.song
        val artist = media.info.artist?.takeIf { it.isNotBlank() }
        text(canvas, fitLabel(song ?: "打开 CarPlay", 283f, 32f), 154f, 779f, 32f, white, medium)
        text(canvas, fitLabel(artist ?: if (song != null) if (media.info.playing || glance.playing) "正在播放" else "已暂停" else "应用与音乐  ›", 283f, 28f, regular), 154f, 809f, 28f, muted)
        val duration = media.info.durationMillis ?: 0L
        if (duration > 0L) {
            paint.color = themed(Color.rgb(184, 201, 215), Color.rgb(34, 52, 68))
            rect.set(154f, 820f, 437f, 824f); canvas.drawRoundRect(rect, 2f, 2f, paint)
            paint.color = blue
            rect.right = 154f + 283f * ((media.info.elapsedMillis ?: 0L).toFloat() / duration).coerceIn(0f, 1f)
            canvas.drawRoundRect(rect, 2f, 2f, paint)
        }
        battery(canvas, 480f, 770f)
        text(canvas, "电量", 565f, 775f, 28f, muted)
        text(canvas, "${vehicle.battery ?: "--"}%", 565f, 814f, 40f, white, medium)
        line(canvas, 680f, 755f, 680f, 818f)
        text(canvas, "电压", 711f, 775f, 28f, muted)
        text(canvas, "${vehicle.voltage} V", 711f, 814f, 36f, white, medium)
        text(canvas, "电流", 864f, 775f, 28f, muted)
        text(canvas, "${vehicle.current} A", 864f, 814f, 36f, white, medium)
        line(canvas, 1020f, 755f, 1020f, 818f)
        text(canvas, "估算续航", 1052f, 775f, 28f, muted)
        text(canvas, "${vehicle.range ?: "--"} km", 1052f, 814f, 40f, white, medium)
        line(canvas, 1225f, 755f, 1225f, 818f)
        text(canvas, "里程", 1260f, 775f, 28f, muted)
        text(canvas, "${vehicle.odometer} km", 1260f, 814f, 36f, white, medium)
        themeIcon(canvas, 1560f, 773f)
        text(canvas, if (night) "夜间" else "日间", 1560f, 826f, 25f, muted, regular, Paint.Align.CENTER)
        paint.color = white; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        path.reset(); path.moveTo(1652f, 754f); path.lineTo(1667f, 748f); path.lineTo(1682f, 754f); path.lineTo(1698f, 748f)
        path.lineTo(1698f, 786f); path.lineTo(1682f, 792f); path.lineTo(1667f, 786f); path.lineTo(1652f, 792f); path.close()
        canvas.drawPath(path, paint); canvas.drawLine(1667f, 748f, 1667f, 786f, paint); canvas.drawLine(1682f, 754f, 1682f, 792f, paint)
        paint.style = Paint.Style.FILL
        text(canvas, if (mapNight) "地图夜" else "地图日", 1675f, 826f, 23f, muted, regular, Paint.Align.CENTER)
        house(canvas, 1797f, 773f)
        text(canvas, "主页", 1797f, 826f, 25f, muted, regular, Paint.Align.CENTER)
        canvas.restore()
        canvas.restore()
        if (measure) {
            val end = System.nanoTime()
            if (drawWindowNs == 0L) drawWindowNs = drawStartedNs
            drawCount++; drawCostNs += end - drawStartedNs
            drawMaxNs = maxOf(drawMaxNs, end - drawStartedNs)
            if (end - drawWindowNs >= 5_000_000_000L) {
                android.util.Log.d("DiPlayRender", "foregroundDrawFps=%.1f meanDrawMs=%.2f maxDrawMs=%.2f".format(Locale.US,
                    drawCount * 1e9 / (end - drawWindowNs), drawCostNs / 1e6 / drawCount, drawMaxNs / 1e6))
                drawWindowNs = end; drawCount = 0; drawCostNs = 0; drawMaxNs = 0
            }
        } else { drawWindowNs = 0; drawCount = 0; drawCostNs = 0; drawMaxNs = 0 }
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
        font: Typeface = regular, align: Paint.Align = Paint.Align.LEFT) {
        textPaint.textSize = size; textPaint.color = color; textPaint.typeface = font; textPaint.textAlign = align
        // A subtle permanent edge avoids a conspicuous outline appearing only during transitions.
        run {
            val background = themed(Color.rgb(232, 239, 246), Color.rgb(3, 10, 17))
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = maxOf(.5f, size * .004f)
            textPaint.strokeJoin = Paint.Join.ROUND
            textPaint.color = if (ColorUtils.calculateLuminance(background) > .35)
                Color.argb(80, 10, 20, 35) else Color.argb(90, 250, 252, 255)
            c.drawText(value, x, y, textPaint)
            textPaint.style = Paint.Style.FILL; textPaint.color = color
        }
        c.drawText(value, x, y, textPaint)
    }
    private fun fitLabel(value: String, maxWidth: Float, size: Float, font: Typeface = medium): String {
        textPaint.textSize = size; textPaint.typeface = font
        return android.text.TextUtils.ellipsize(value, textPaint, maxWidth, android.text.TextUtils.TruncateAt.END).toString()
    }
    private fun shorten(value: String, max: Int) = if (value.length > max) value.take(max - 1) + "…" else value
    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, surface: Shader? = null) {
        rect.set(l, t, r, b); paint.style = Paint.Style.FILL
        paint.color = themed(Color.argb(235, 248, 251, 255), Color.argb(225, 5, 14, 24))
        if (surface != null) { paint.color = Color.WHITE; paint.shader = surface }
        c.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        paint.color = themed(Color.argb(120, 164, 184, 204), Color.argb(70, 59, 81, 105))
        c.drawRoundRect(rect, radius, radius, paint); paint.style = Paint.Style.FILL
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        paint.color = themed(Color.rgb(160, 178, 194), Color.rgb(49, 68, 87))
        paint.strokeWidth = 1.5f; c.drawLine(x1, y1, x2, y2, paint)
    }
    private fun arrow(c: Canvas, x: Float, y: Float, color: Int) {
        paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = 2f
        paint.color = color; path.reset(); path.moveTo(x - 26, y); path.lineTo(x - 4, y - 20)
        path.lineTo(x - 4, y - 8); path.lineTo(x + 21, y - 8); path.lineTo(x + 21, y + 8)
        path.lineTo(x - 4, y + 8); path.lineTo(x - 4, y + 20); path.close(); c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }
    private val warningIndicators = setOf(DashboardIndicator.FAULT, DashboardIndicator.ABS, DashboardIndicator.TCS, DashboardIndicator.HDC, DashboardIndicator.RIDER_UNSEATED)
    private val secondRow = arrayOf(DashboardIndicator.FAULT, DashboardIndicator.ENERGY_RECOVERY,
        DashboardIndicator.SIDE_STAND, DashboardIndicator.SEAT, DashboardIndicator.ABS,
        DashboardIndicator.RIDER_UNSEATED, DashboardIndicator.HDC, DashboardIndicator.CRUISE,
        DashboardIndicator.TCS, DashboardIndicator.CHARGING)
    private fun topIcon(c: Canvas, id: DashboardIndicator, x: Float) {
        c.save(); c.translate(x, 49f)
        when (id) {
            DashboardIndicator.HIGH_BEAM -> c.scale(.85f, 1f)
            DashboardIndicator.LOW_BEAM -> { c.scale(.85f, .95f); c.translate(0f, -3f) }
            DashboardIndicator.POSITION_LIGHT -> c.scale(.85f, 1f)
            DashboardIndicator.VEHICLE_BLUETOOTH -> { c.scale(.88f, .88f); c.translate(0f, 4f) }
            DashboardIndicator.PHONE_BLUETOOTH -> c.scale(1.2f, 1f)
            DashboardIndicator.GPS -> { c.scale(1.2f, 1.05f); c.translate(0f, -3f) }
            DashboardIndicator.GSM -> c.scale(1.2f, 1.1f)
            DashboardIndicator.READY -> c.scale(1.05f, 1.05f)
            else -> c.scale(.9f, .9f)
        }
        statusIcon(c, id, 0f, 0f); c.restore()
    }
    private fun drawIndicators(c: Canvas) {
        glass(c, 12f, 8f, 1008f, 90f, 38f)
        topIcon(c, DashboardIndicator.LEFT_TURN, 48f)
        topIcon(c, DashboardIndicator.HIGH_BEAM, 128f)
        topIcon(c, DashboardIndicator.LOW_BEAM, 210f)
        topIcon(c, DashboardIndicator.POSITION_LIGHT, 292f)
        topIcon(c, DashboardIndicator.VEHICLE_BLUETOOTH, 374f)
        topIcon(c, DashboardIndicator.READY, 466f)
        topIcon(c, DashboardIndicator.PHONE_BLUETOOTH, 574f)
        topIcon(c, DashboardIndicator.GPS, 652f)
        topIcon(c, DashboardIndicator.GSM, 730f)
        text(c, clock, 800f, 63f, 39f, white, medium)
        topIcon(c, DashboardIndicator.RIGHT_TURN, 970f)
        val capacity = ((logicalHeight - 280f) / 82f).toInt().coerceIn(1, secondRow.size)
        val step = 82f
        secondRow.forEachIndexed { index, id ->
            val active = indicators.signal(id).active
            val warning = id in warningIndicators
            if (active || indicators.demonstration || !warning)
                statusIcon(c, id, 54f + (index / capacity) * 80f, 170f + (index % capacity) * step)
        }
    }
    private fun statusColor(id: DashboardIndicator): Int {
        val state = indicators.signal(id)
        if (!state.active || (state.blinking && !blink)) return themed(Color.rgb(150, 164, 179), Color.rgb(52, 68, 86))
        return when (id) {
            DashboardIndicator.HIGH_BEAM, DashboardIndicator.VEHICLE_BLUETOOTH, DashboardIndicator.PHONE_BLUETOOTH -> blue
            DashboardIndicator.FAULT -> Color.rgb(244, 70, 75)
            DashboardIndicator.ABS, DashboardIndicator.TCS, DashboardIndicator.HDC, DashboardIndicator.SIDE_STAND,
            DashboardIndicator.RIDER_UNSEATED, DashboardIndicator.REPAIR_MODE -> Color.rgb(239, 169, 38)
            else -> green
        }
    }
    private fun motorcycle(c: Canvas, x: Float, y: Float) {
        c.drawCircle(x - 18, y + 8, 8f, paint); c.drawCircle(x + 18, y + 8, 8f, paint)
        path.reset(); path.moveTo(x - 18, y + 8); path.lineTo(x - 8, y - 7)
        path.cubicTo(x - 2, y - 12, x + 6, y - 11, x + 9, y - 4)
        path.lineTo(x + 1, y + 8); path.lineTo(x - 18, y + 8); c.drawPath(path, paint)
        c.drawLine(x - 18, y - 10, x - 6, y - 10, paint)
        c.drawLine(x + 8, y - 16, x + 18, y + 8, paint); c.drawLine(x + 8, y - 16, x + 17, y - 16, paint)
    }
    private fun seat(c: Canvas, x: Float, y: Float, open: Boolean) {
        path.reset(); path.moveTo(x - 24, y + 8)
        path.cubicTo(x - 12, y - 2, x + 9, y - 2, x + 23, y + 3)
        path.lineTo(x + 21, y + 14); path.lineTo(x - 22, y + 14); path.close(); c.drawPath(path, paint)
        if (open) {
            path.reset(); path.moveTo(x - 22, y + 2); path.cubicTo(x - 11, y - 14, x + 10, y - 20, x + 20, y - 17)
            c.drawPath(path, paint); c.drawLine(x + 20, y - 17, x + 22, y - 5, paint)
        }
    }
    private fun statusIcon(c: Canvas, id: DashboardIndicator, x: Float, y: Float) {
        val color = statusColor(id)
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (id) {
            DashboardIndicator.LEFT_TURN -> { paint.style = Paint.Style.FILL; arrow(c, x, y, color) }
            DashboardIndicator.RIGHT_TURN -> { paint.style = Paint.Style.FILL; c.save(); c.scale(-1f, 1f, x, y); arrow(c, x, y, color); c.restore() }
            DashboardIndicator.HIGH_BEAM -> headlamp(c, x - 9f, y, color)
            DashboardIndicator.LOW_BEAM -> headlamp(c, x - 9f, y, color, true)
            DashboardIndicator.POSITION_LIGHT -> {
                rect.set(x - 14, y - 13, x + 14, y + 13); c.drawOval(rect, paint)
                for (i in -1..1) { c.drawLine(x - 28, y + i * 9, x - 19, y + i * 9, paint); c.drawLine(x + 19, y + i * 9, x + 28, y + i * 9, paint) }
            }
            DashboardIndicator.PHONE_BLUETOOTH -> bluetooth(c, x, y, color)
            DashboardIndicator.VEHICLE_BLUETOOTH -> {
                rect.set(x - 23, y + 2, x + 23, y + 16); c.drawRoundRect(rect, 5f, 5f, paint)
                c.drawLine(x - 14, y + 2, x - 9, y - 9, paint); c.drawLine(x - 9, y - 9, x + 9, y - 9, paint); c.drawLine(x + 9, y - 9, x + 14, y + 2, paint)
                c.drawCircle(x - 13, y + 19, 3f, paint); c.drawCircle(x + 13, y + 19, 3f, paint)
                text(c, "B", x, y - 12, 18f, color, bold, Paint.Align.CENTER)
            }
            DashboardIndicator.GPS -> {
                c.drawCircle(x, y - 3, 12f, paint); c.drawCircle(x, y - 3, 3f, paint)
                path.reset(); path.moveTo(x - 10, y + 5); path.lineTo(x, y + 21); path.lineTo(x + 10, y + 5); c.drawPath(path, paint)
            }
            DashboardIndicator.GSM -> { for (i in 0..3) c.drawLine(x - 15 + i * 10, y + 17, x - 15 + i * 10, y + 6 - i * 9, paint) }
            DashboardIndicator.READY -> text(c, "READY", x, y + 7, 22f, color, bold, Paint.Align.CENTER)
            DashboardIndicator.ABS -> {
                rect.set(x - 20, y - 18, x + 20, y + 18); c.drawOval(rect, paint)
                rect.set(x - 28, y - 23, x + 28, y + 23)
                c.drawArc(rect, 145f, 70f, false, paint); c.drawArc(rect, -35f, 70f, false, paint)
                text(c, "ABS", x, y + 6, 17f, color, bold, Paint.Align.CENTER)
            }
            DashboardIndicator.TCS -> {
                c.drawCircle(x, y, 23f, paint)
                text(c, "TC", x, y + 8, 24f, color, bold, Paint.Align.CENTER)
            }
            DashboardIndicator.HDC -> {
                c.save(); c.rotate(20f, x, y); motorcycle(c, x, y - 4); c.drawLine(x - 25, y + 12, x + 25, y + 12, paint); c.restore()
                c.drawLine(x + 23, y - 20, x + 23, y - 6, paint); c.drawLine(x + 17, y - 12, x + 23, y - 6, paint); c.drawLine(x + 29, y - 12, x + 23, y - 6, paint)
            }
            DashboardIndicator.CRUISE -> {
                rect.set(x - 24, y - 23, x + 24, y + 25); c.drawArc(rect, 145f, 250f, false, paint)
                for (angle in listOf(150.0, 200.0, 250.0, 300.0, 350.0)) {
                    val a = Math.toRadians(angle)
                    c.drawLine(x + (19 * Math.cos(a)).toFloat(), y + (19 * Math.sin(a)).toFloat(), x + (24 * Math.cos(a)).toFloat(), y + (24 * Math.sin(a)).toFloat(), paint)
                }
                c.drawLine(x, y + 2, x + 12, y - 13, paint); c.drawCircle(x, y + 2, 3f, paint)
                c.drawLine(x - 19, y + 24, x + 15, y + 24, paint); c.drawLine(x + 8, y + 17, x + 15, y + 24, paint); c.drawLine(x + 8, y + 31, x + 15, y + 24, paint)
            }
            DashboardIndicator.FAULT -> {
                motorcycle(c, x, y + 5)
                text(c, "!", x + 24, y - 9, 25f, color, bold, Paint.Align.CENTER)
            }
            DashboardIndicator.ENERGY_RECOVERY -> {
                rect.set(x - 16, y - 11, x + 16, y + 11); c.drawRoundRect(rect, 3f, 3f, paint)
                c.drawLine(x + 16, y - 4, x + 20, y - 4, paint); c.drawLine(x + 20, y - 4, x + 20, y + 4, paint); c.drawLine(x + 20, y + 4, x + 16, y + 4, paint)
                path.reset(); path.moveTo(x + 3, y - 7); path.lineTo(x - 4, y + 1); path.lineTo(x + 3, y + 1); path.lineTo(x - 3, y + 8); c.drawPath(path, paint)
                rect.set(x - 26, y - 25, x + 26, y + 25)
                c.drawArc(rect, 200f, 125f, false, paint); c.drawArc(rect, 20f, 125f, false, paint)
                c.drawLine(x + 21, y - 20, x + 21, y - 12, paint); c.drawLine(x + 21, y - 12, x + 13, y - 14, paint)
                c.drawLine(x - 21, y + 20, x - 21, y + 12, paint); c.drawLine(x - 21, y + 12, x - 13, y + 14, paint)
            }
            DashboardIndicator.SIDE_STAND -> {
                motorcycle(c, x, y - 3); c.drawLine(x + 1, y + 1, x + 10, y + 23, paint); c.drawLine(x + 4, y + 23, x + 20, y + 23, paint)
            }
            DashboardIndicator.REPAIR_MODE -> {
                paint.strokeWidth = 6f
                c.drawLine(x - 15, y + 17, x + 6, y - 4, paint)
                rect.set(x - 1, y - 23, x + 23, y + 1); c.drawArc(rect, 20f, 260f, false, paint)
                c.drawCircle(x - 15, y + 17, 4f, paint); paint.strokeWidth = 4.5f
            }
            DashboardIndicator.SEAT -> {
                seat(c, x, y, true)
            }
            DashboardIndicator.RIDER_UNSEATED -> {
                seat(c, x, y + 8, false)
                c.drawLine(x, y - 25, x, y - 8, paint); c.drawLine(x - 7, y - 15, x, y - 8, paint); c.drawLine(x + 7, y - 15, x, y - 8, paint)
            }
            DashboardIndicator.CHARGING -> {
                rect.set(x - 16, y - 8, x + 16, y + 9); c.drawRoundRect(rect, 4f, 4f, paint)
                c.drawLine(x - 8, y - 19, x - 8, y - 8, paint); c.drawLine(x + 8, y - 19, x + 8, y - 8, paint); c.drawLine(x, y + 9, x, y + 22, paint)
            }
        }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
    }
    private fun themeIcon(c: Canvas, x: Float, y: Float) {
        paint.color = white
        if (night) {
            path.reset(); path.moveTo(x + 10, y - 20)
            path.cubicTo(x - 25, y - 12, x - 21, y + 25, x + 17, y + 19)
            path.cubicTo(x - 4, y + 17, x - 9, y - 1, x + 10, y - 20); path.close(); c.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; c.drawCircle(x, y, 12f, paint)
            for (i in 0..7) {
                val angle = i * Math.PI / 4
                val dx = kotlin.math.cos(angle).toFloat(); val dy = kotlin.math.sin(angle).toFloat()
                c.drawLine(x + dx * 18, y + dy * 18, x + dx * 26, y + dy * 26, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }
    private fun headlamp(c: Canvas, x: Float, y: Float, color: Int, dipped: Boolean = false) {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
        rect.set(x, y - 16, x + 32, y + 16); c.drawRoundRect(rect, 14f, 14f, paint)
        for (i in -2..2) c.drawLine(x - 22, y + i * 7f + if (dipped) 8f else 0f, x - 6, y + i * 7f, paint)
        paint.style = Paint.Style.FILL
    }
    private fun bluetooth(c: Canvas, x: Float, y: Float, color: Int) {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.5f
        path.reset(); path.moveTo(x, y - 20); path.lineTo(x + 14, y - 9); path.lineTo(x - 13, y + 13)
        path.moveTo(x - 13, y - 13); path.lineTo(x + 14, y + 9); path.lineTo(x, y + 20); path.lineTo(x, y - 20)
        c.drawPath(path, paint); paint.style = Paint.Style.FILL
    }
    private fun battery(c: Canvas, x: Float, y: Float) {
        paint.color = green; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        rect.set(x, y, x + 58, y + 32); c.drawRoundRect(rect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL; c.drawRect(x + 7, y + 7, x + 44, y + 25, paint)
        c.drawRect(x + 63, y + 9, x + 68, y + 23, paint)
    }
    private fun house(c: Canvas, x: Float, y: Float) {
        paint.color = white; path.reset(); path.moveTo(x - 22, y - 2); path.lineTo(x, y - 23)
        path.lineTo(x + 22, y - 2); path.lineTo(x + 22, y + 23); path.lineTo(x + 8, y + 23)
        path.lineTo(x + 8, y + 5); path.lineTo(x - 8, y + 5); path.lineTo(x - 8, y + 23)
        path.lineTo(x - 22, y + 23); path.close(); c.drawPath(path, paint)
    }
}
