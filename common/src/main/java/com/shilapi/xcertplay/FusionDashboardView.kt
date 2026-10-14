package com.shilapi.xcertplay

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local foreground only: the live map remains a separate hardware decoded texture. */
internal class FusionDashboardView(context: Context) : View(context) {
    data class VehiclePreview(val speed: Int = 48, val gear: String = "D", val power: String = "2.6",
        val battery: Int = 78, val range: Int = 62, val odometer: String = "1,186")

    private val vehicle = VehiclePreview()
    private var glance = CarPlayGlance.Snapshot()
    private var mapActive = false
    private var blink = false
    private var clock = ""
    private val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val white = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(167, 184, 210)
    private val blue = Color.rgb(16, 157, 255)
    private val green = Color.rgb(28, 233, 100)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val rect = RectF()
    private val path = Path()
    private val leftFade = LinearGradient(0f, 0f, 1030f, 0f,
        intArrayOf(Color.rgb(3, 10, 17), Color.argb(247, 3, 10, 17), Color.argb(185, 3, 10, 17), Color.TRANSPARENT),
        floatArrayOf(0f, .53f, .76f, 1f), Shader.TileMode.CLAMP)
    private val edgeFade = LinearGradient(0f, 0f, 0f, 864f,
        intArrayOf(Color.argb(100, 3, 10, 17), Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(230, 3, 10, 17)),
        floatArrayOf(0f, .20f, .70f, 1f), Shader.TileMode.CLAMP)
    private var glyphType: Int? = null
    private var glyph: android.graphics.drawable.Drawable? = null

    fun update(snapshot: CarPlayGlance.Snapshot, active: Boolean) {
        glance = snapshot
        mapActive = active
        blink = (android.os.SystemClock.uptimeMillis() / 700) % 2 == 0L
        clock = formatter.format(Date())
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
        contentDescription = "演示仪表，速度 ${vehicle.speed}，电量 ${vehicle.battery}%，续航 ${vehicle.range} 公里"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 1920f, height / 864f)
        paint.shader = leftFade; paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 1920f, 864f, paint)
        paint.shader = edgeFade
        canvas.drawRect(0f, 0f, 1920f, 864f, paint)
        paint.shader = null

        glass(canvas, 12f, 8f, 720f, 90f, 38f)
        arrow(canvas, 68f, 49f, if (blink) green else Color.rgb(15, 72, 42))
        headlamp(canvas, 172f, 49f)
        bluetooth(canvas, 300f, 49f, if (glance.connected) muted else Color.DKGRAY)
        text(canvas, clock, 364f, 64f, 43f, white, medium)
        glass(canvas, 540f, 27f, 700f, 71f, 22f)
        text(canvas, "车辆数据演示", 620f, 56f, 21f, muted, regular, Paint.Align.CENTER)

        paint.style = Paint.Style.STROKE; paint.strokeWidth = 16f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(17, 32, 44)
        rect.set(76f, 140f, 602f, 666f)
        canvas.drawArc(rect, 140f, 260f, false, paint)
        paint.color = blue
        canvas.drawArc(rect, 140f, 126f, false, paint)
        paint.strokeWidth = 3f
        paint.color = Color.argb(90, 45, 169, 255)
        rect.set(61f, 125f, 617f, 681f)
        canvas.drawArc(rect, 140f, 126f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT; paint.style = Paint.Style.FILL
        text(canvas, vehicle.speed.toString(), 339f, 409f, 224f, white, bold, Paint.Align.CENTER)
        text(canvas, "km/h", 339f, 461f, 42f, muted, regular, Paint.Align.CENTER)
        line(canvas, 155f, 502f, 523f, 502f)
        text(canvas, vehicle.gear, 339f, 577f, 68f, white, medium, Paint.Align.CENTER)
        text(canvas, "${vehicle.power} kW", 339f, 643f, 42f, white, regular, Paint.Align.CENTER)

        glass(canvas, 1030f, 8f, 1860f, 109f, 48f)
        if (glance.maneuverType != null) {
            glyph?.let { it.setBounds(1058, 30, 1119, 88); it.draw(canvas) }
            val distance = if (glance.distanceMeters < 1000) "${glance.distanceMeters} m"
                else String.format(Locale.US, "%.1f km", glance.distanceMeters / 1000.0)
            text(canvas, distance, 1140f, 72f, 47f, white, medium)
            text(canvas, shorten(glance.road.ifBlank { "沿导航路线行驶" }, 18), 1370f, 69f, 32f, white)
        } else {
            text(canvas, if (mapActive) "CarPlay 导航地图" else "等待 CarPlay 地图", 1070f, 53f, 33f, white, medium)
            text(canvas, if (glance.connected) "在 iPhone 上开始导航" else "连接 iPhone 后显示实时地图", 1070f, 87f, 24f, muted)
        }
        if (!mapActive) {
            text(canvas, "地图连接中", 1390f, 391f, 42f, white, medium, Paint.Align.CENTER)
            text(canvas, "请连接 DiPlay 并启用副屏地图", 1390f, 437f, 26f, muted, regular, Paint.Align.CENTER)
        }

        glass(canvas, 10f, 722f, 1910f, 850f, 52f)
        paint.color = Color.rgb(245, 44, 112)
        rect.set(53f, 753f, 125f, 823f); canvas.drawRoundRect(rect, 19f, 19f, paint)
        text(canvas, "♫", 89f, 806f, 52f, white, medium, Paint.Align.CENTER)
        text(canvas, shorten(glance.song ?: "打开 CarPlay", 11), 154f, 786f, 30f, white, medium)
        text(canvas, if (glance.song != null) if (glance.playing) "正在播放  ›" else "已暂停  ›" else "应用与音乐  ›", 154f, 816f, 23f, muted)
        battery(canvas, 505f, 770f)
        text(canvas, "电量", 611f, 797f, 28f, muted)
        text(canvas, "${vehicle.battery}%", 685f, 798f, 40f, white, medium)
        line(canvas, 842f, 755f, 842f, 818f)
        text(canvas, "续航", 913f, 797f, 28f, muted)
        text(canvas, "${vehicle.range}", 989f, 798f, 40f, white, medium)
        text(canvas, "km", 1051f, 797f, 27f, muted)
        line(canvas, 1182f, 755f, 1182f, 818f)
        text(canvas, "里程", 1250f, 797f, 28f, muted)
        text(canvas, vehicle.odometer, 1329f, 798f, 40f, white, medium)
        text(canvas, "km", 1449f, 797f, 27f, muted)
        house(canvas, 1797f, 773f)
        text(canvas, "Home", 1797f, 826f, 25f, muted, regular, Paint.Align.CENTER)
        canvas.restore()
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
        font: Typeface = regular, align: Paint.Align = Paint.Align.LEFT) {
        textPaint.textSize = size; textPaint.color = color; textPaint.typeface = font; textPaint.textAlign = align
        c.drawText(value, x, y, textPaint)
    }
    private fun shorten(value: String, max: Int) = if (value.length > max) value.take(max - 1) + "…" else value
    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b); paint.style = Paint.Style.FILL; paint.color = Color.argb(225, 5, 14, 24)
        c.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = Color.argb(115, 59, 81, 105)
        c.drawRoundRect(rect, radius, radius, paint); paint.style = Paint.Style.FILL
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        paint.color = Color.rgb(49, 68, 87); paint.strokeWidth = 1.5f; c.drawLine(x1, y1, x2, y2, paint)
    }
    private fun arrow(c: Canvas, x: Float, y: Float, color: Int) {
        paint.color = color; path.reset(); path.moveTo(x - 26, y); path.lineTo(x - 4, y - 20)
        path.lineTo(x - 4, y - 8); path.lineTo(x + 21, y - 8); path.lineTo(x + 21, y + 8)
        path.lineTo(x - 4, y + 8); path.lineTo(x - 4, y + 20); path.close(); c.drawPath(path, paint)
    }
    private fun headlamp(c: Canvas, x: Float, y: Float) {
        paint.color = white; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        rect.set(x, y - 16, x + 32, y + 16); c.drawRoundRect(rect, 14f, 14f, paint)
        for (i in -2..2) c.drawLine(x - 22, y + i * 7f, x - 6, y + i * 7f, paint)
        paint.style = Paint.Style.FILL
    }
    private fun bluetooth(c: Canvas, x: Float, y: Float, color: Int) {
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
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
