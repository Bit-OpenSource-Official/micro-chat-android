package ru.e6atb.chat

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.AbsListView
import android.widget.FrameLayout

internal class SwipeDismissLayout(context: Context) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private var dismissAction: Runnable? = null; private var velocityTracker: VelocityTracker? = null
    private var downRawX = 0f; private var downRawY = 0f; private var startOffsetY = 0f; private var offsetY = 0f; private var dragging = false; private var finishing = false
    init { clipChildren = false; clipToPadding = false }
    fun setDismissAction(action: Runnable?) { dismissAction = action }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { beginGesture(event); false }
        MotionEvent.ACTION_MOVE -> { track(event); val dx = event.rawX - downRawX; val dy = event.rawY - downRawY; if (!dragging && dy > touchSlop && dy > kotlin.math.abs(dx) * 1.15f && !descendantCanScrollUp(this)) { dragging = true; parent.requestDisallowInterceptTouchEvent(true); true } else false }
        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> { recycleTracker(); false }
        else -> false
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event); return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!dragging) false else { setOffsetY(SwipeDismissDecider.dragTranslation(startOffsetY, downRawY, event.rawY)); true }
            MotionEvent.ACTION_UP -> if (!dragging) { recycleTracker(); false } else { val velocity = velocityTracker?.let { it.computeCurrentVelocity(1000); it.getYVelocity(event.getPointerId(0)) } ?: 0f; finishGesture(SwipeDismissDecider.shouldDismiss(offsetY, height, velocity, density)); true }
            MotionEvent.ACTION_CANCEL -> { if (dragging) finishGesture(false) else recycleTracker(); dragging }
            else -> dragging
        }
    }
    private fun beginGesture(event: MotionEvent) { clearAnimation(); downRawX = event.rawX; downRawY = event.rawY; startOffsetY = maxOf(0f, offsetY); dragging = false; finishing = false; recycleTracker(); velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) } }
    private fun track(event: MotionEvent) { (velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }).addMovement(event) }
    private fun finishGesture(dismiss: Boolean) { if (finishing) return; finishing = true; dragging = false; recycleTracker(); animateOffset(if (dismiss) maxOf(height, resources.displayMetrics.heightPixels).toFloat() else 0f, dismiss) }
    private fun animateOffset(targetY: Float, dismiss: Boolean) {
        val initialY = offsetY; startAnimation(object : Animation() {
            override fun applyTransformation(progress: Float, transformation: Transformation) { setOffsetY(SwipeDismissDecider.animationOffset(initialY, targetY, progress)); if (dismiss) transformation.alpha = maxOf(0f, 1f - progress) }
        }.apply { duration = 180L; setAnimationListener(object : Animation.AnimationListener { override fun onAnimationStart(animation: Animation) {} ; override fun onAnimationRepeat(animation: Animation) {}; override fun onAnimationEnd(animation: Animation) { if (dismiss) dismissAction?.run() else { setOffsetY(0f); finishing = false } } }) })
    }
    private fun setOffsetY(value: Float) { val safe = maxOf(0f, value); val delta = kotlin.math.round(safe - offsetY).toInt(); if (delta != 0) { offsetTopAndBottom(delta); offsetY += delta } }
    private fun recycleTracker() { velocityTracker?.recycle(); velocityTracker = null }
    private fun descendantCanScrollUp(view: View): Boolean { if (canScrollUp(view)) return true; return view is ViewGroup && (0 until view.childCount).any { descendantCanScrollUp(view.getChildAt(it)) } }
    private fun canScrollUp(view: View?): Boolean { if (view == null) return false; if (Build.VERSION.SDK_INT >= 14) try { if (View::class.java.getMethod("canScrollVertically", Int::class.javaPrimitiveType).invoke(view, -1) as? Boolean == true) return true } catch (_: Exception) {}; return if (view is AbsListView) view.childCount > 0 && (view.firstVisiblePosition > 0 || view.getChildAt(0).top < view.paddingTop) else view.scrollY > 0 }
}
