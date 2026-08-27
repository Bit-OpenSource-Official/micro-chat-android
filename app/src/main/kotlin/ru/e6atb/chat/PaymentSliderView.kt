package ru.e6atb.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

internal class PaymentSliderView(context: Context, private val hint: String?, private val confirmLeft: Boolean, private val theme: Theme) : View(context) {
    interface Theme { fun dp(value: Int): Int; fun elementRadius(): Int; fun blend(a: Int, b: Int, t: Float): Int; fun surfaceHi(): Int; fun primary(): Int; fun muted(): Int; fun onPrimary(): Int }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG); private val trackRect = RectF(); private val fillRect = RectF(); private val thumbRect = RectF()
    private var confirmAction: Runnable? = null; private var resetAnimation: Runnable? = null; private var progress = if (confirmLeft) 1f else 0f; private var touchOffset = 0f; private var tracking = false
    init { isFocusable = true }
    fun setOnConfirmAction(action: Runnable?) { confirmAction = action }
    override fun performClick(): Boolean = super.performClick()
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(resolveSize(dp(240), widthMeasureSpec), resolveSize(dp(56), heightMeasureSpec)) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val width = width; val height = height; val inset = dp(2).toFloat(); val left = inset; val top = inset; val right = maxOf(left, width - inset); val bottom = maxOf(top, height - inset); val radius = minOf(elementRadius().toFloat(), maxOf(0f, (bottom - top) / 4f)); val thumbWidth = thumbWidth(); val thumbHeight = thumbHeight(); val xInset = thumbHorizontalInset(); val yInset = thumbVerticalInset(); val thumbLeft = left + xInset + (right - left - thumbWidth - xInset * 2f) * progress
        trackRect.set(left, top, right, bottom); paint.color = blend(surfaceHi(), Color.BLACK, .35f); canvas.drawRoundRect(trackRect, radius, radius, paint)
        if (confirmLeft) fillRect.set(maxOf(left, thumbLeft - xInset), top, right, bottom) else fillRect.set(left, top, minOf(right, thumbLeft + thumbWidth + xInset), bottom)
        paint.color = blend(primary(), Color.BLACK, .55f); canvas.drawRoundRect(fillRect, radius, radius, paint)
        textPaint.textSize = dp(14).toFloat(); textPaint.typeface = Typeface.DEFAULT_BOLD; textPaint.textAlign = Paint.Align.CENTER; textPaint.color = muted(); canvas.drawText(hint.orEmpty(), width / 2f, height / 2f - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f, textPaint)
        thumbRect.set(thumbLeft, top + yInset, thumbLeft + thumbWidth, top + yInset + thumbHeight); paint.color = blend(primary(), Color.WHITE, .14f); val thumbRadius = minOf(elementRadius().toFloat(), thumbHeight / 3f); canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, paint)
        textPaint.textSize = dp(18).toFloat(); textPaint.typeface = Typeface.DEFAULT_BOLD; textPaint.color = onPrimary(); canvas.drawText(if (confirmLeft) "<" else ">", thumbRect.centerX(), thumbRect.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f, textPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = when (event.action) {
        MotionEvent.ACTION_DOWN -> if (!touchInsideThumb(event.x, event.y)) false else { cancelResetAnimation(); tracking = true; touchOffset = event.x - currentThumbLeft(); parent?.requestDisallowInterceptTouchEvent(true); true }
        MotionEvent.ACTION_MOVE -> if (tracking) { updateProgressFromTouch(event.x); true } else false
        MotionEvent.ACTION_UP -> if (!tracking) false else { updateProgressFromTouch(event.x); val confirmed = if (confirmLeft) progress <= .14f else progress >= .86f; tracking = false; parent?.requestDisallowInterceptTouchEvent(false); if (confirmed) { cancelResetAnimation(); progress = if (confirmLeft) 0f else 1f; invalidate(); performClick(); confirmAction?.run() } else resetThumb(); true }
        MotionEvent.ACTION_CANCEL -> if (!tracking) false else { tracking = false; parent?.requestDisallowInterceptTouchEvent(false); resetThumb(); true }
        else -> false
    }
    private fun updateProgressFromTouch(x: Float) { val inset = thumbHorizontalInset(); val usable = maxOf(1f, width - dp(4) - thumbWidth() - inset * 2f); progress = ((x - touchOffset - dp(2) - inset) / usable).coerceIn(0f, 1f); invalidate() }
    private fun touchInsideThumb(x: Float, y: Float): Boolean { val w = thumbWidth(); val h = thumbHeight(); val left = currentThumbLeft(); val top = (height - h) / 2f; val slop = dp(8).toFloat(); return x >= left - slop && x <= left + w + slop && y >= top - slop && y <= top + h + slop }
    private fun currentThumbLeft(): Float { val inset = thumbHorizontalInset(); return dp(2) + inset + maxOf(1f, width - dp(4) - thumbWidth() - inset * 2f) * progress }
    private fun thumbWidth(): Float = maxOf(dp(64).toFloat(), minOf(dp(82).toFloat(), thumbHeight() * 1.65f)); private fun thumbHeight(): Float = maxOf(dp(36).toFloat(), minOf(dp(44).toFloat(), height - dp(10).toFloat())); private fun thumbHorizontalInset() = dp(4).toFloat(); private fun thumbVerticalInset() = maxOf(0f, (maxOf(0f, height - dp(4).toFloat()) - thumbHeight()) / 2f)
    private fun resetThumb() = animateThumbToStart()
    private fun animateThumbToStart() { cancelResetAnimation(); val start = progress; val target = if (confirmLeft) 1f else 0f; if (kotlin.math.abs(start - target) <= .001f) { progress = target; invalidate(); return }; val startTime = System.currentTimeMillis(); resetAnimation = object : Runnable { override fun run() { val time = minOf(1f, (System.currentTimeMillis() - startTime) / 180f); val eased = 1f - (1f - time) * (1f - time) * (1f - time); progress = start + (target - start) * eased; invalidate(); if (time < 1f) postDelayed(this, 16L) else { progress = target; resetAnimation = null; invalidate() } } }; post(resetAnimation) }
    private fun cancelResetAnimation() { resetAnimation?.let(::removeCallbacks); resetAnimation = null }
    private fun dp(value: Int) = theme.dp(value); private fun elementRadius() = theme.elementRadius(); private fun blend(a: Int, b: Int, t: Float) = theme.blend(a, b, t); private fun surfaceHi() = theme.surfaceHi(); private fun primary() = theme.primary(); private fun muted() = theme.muted(); private fun onPrimary() = theme.onPrimary()
}
