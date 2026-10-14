package com.shilapi.xcertplay

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

/** Two parallel fingers; pinch/rotation/vertical gestures remain with their original owner. */
internal class DisplayPageSwipe(private val density: Float, private val switch: (Boolean) -> Unit) {
    private var candidate = false
    private var captured = false
    private var x = 0f
    private var y = 0f
    private var span = 0f
    private var firstX = 0f
    private var secondX = 0f

    fun handle(event: MotionEvent, fusion: Boolean): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { candidate = false; captured = false }
        if (captured) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) captured = false
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            candidate = event.pointerCount == 2
            if (candidate) {
                x = (event.getX(0) + event.getX(1)) / 2
                y = (event.getY(0) + event.getY(1)) / 2
                firstX = event.getX(0); secondX = event.getX(1)
                span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
            }
        }
        if (candidate && event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 2) {
            val dx = (event.getX(0) + event.getX(1)) / 2 - x
            val dy = (event.getY(0) + event.getY(1)) / 2 - y
            val currentSpan = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
            if (abs(currentSpan - span) > maxOf(24 * density, span * .18f) || abs(dy) > maxOf(32 * density, abs(dx))) candidate = false
            else if (abs(dx) >= 72 * density && abs(dx) > abs(dy) * 1.7f &&
                (event.getX(0) - firstX) * (event.getX(1) - secondX) > 0 &&
                ((!fusion && dx > 0) || (fusion && dx < 0))) {
                captured = true; candidate = false; switch(!fusion); return true
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) candidate = false
        return false
    }
}
