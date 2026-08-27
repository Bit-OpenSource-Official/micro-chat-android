package ru.e6atb.chat

import android.content.Context
import android.view.View
import android.view.ViewGroup

internal class MessageMetaLayout(context: Context, horizontalGap: Int, verticalGap: Int) : ViewGroup(context) {
    private val horizontalGap = horizontalGap.coerceAtLeast(0)
    private val verticalGap = verticalGap.coerceAtLeast(0)
    private var footer: View? = null
    fun setFooter(value: View?) { footer = value; if (value != null && value.parent != this) addView(value) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 4 else (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        for (index in 0 until childCount) measureChild(getChildAt(index), widthMeasureSpec, heightMeasureSpec)
        val flow = flowSize(available)
        setMeasuredDimension(resolveSize(flow.maxWidth + paddingLeft + paddingRight, widthMeasureSpec), resolveSize(flow.height + paddingTop + paddingBottom, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val available = (right - left - paddingLeft - paddingRight).coerceAtLeast(0); var rowTop = paddingTop; var used = 0; var rowHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index); if (child == footer || child.visibility == GONE) continue
            val width = child.measuredWidth; val height = child.measuredHeight
            if (used > 0 && used + horizontalGap + width > available) { rowTop += rowHeight + verticalGap; used = 0; rowHeight = 0 }
            val childLeft = paddingLeft + if (used == 0) 0 else used + horizontalGap
            child.layout(childLeft, rowTop, childLeft + width, rowTop + height); used = if (used == 0) width else used + horizontalGap + width; rowHeight = maxOf(rowHeight, height)
        }
        footer?.takeIf { it.visibility != GONE }?.let { value ->
            val width = value.measuredWidth; val height = value.measuredHeight
            if (used > 0 && used + horizontalGap + width > available) { rowTop += rowHeight + verticalGap; used = 0; rowHeight = 0 }
            rowHeight = maxOf(rowHeight, height); val footerLeft = paddingLeft + maxOf(if (used == 0) 0 else used + horizontalGap, available - width); val footerTop = rowTop + rowHeight - height
            value.layout(footerLeft, footerTop, footerLeft + width, footerTop + height)
        }
    }
    private fun flowSize(available: Int): FlowSize {
        var used = 0; var rowHeight = 0; var totalHeight = 0; var maxWidth = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index); if (child == footer || child.visibility == GONE) continue
            val width = child.measuredWidth; val height = child.measuredHeight
            if (used > 0 && used + horizontalGap + width > available) { maxWidth = maxOf(maxWidth, used); totalHeight += rowHeight + verticalGap; used = 0; rowHeight = 0 }
            used = if (used == 0) width else used + horizontalGap + width; rowHeight = maxOf(rowHeight, height)
        }
        footer?.takeIf { it.visibility != GONE }?.let { value ->
            val width = value.measuredWidth; val height = value.measuredHeight
            if (used > 0 && used + horizontalGap + width > available) { maxWidth = maxOf(maxWidth, used); totalHeight += rowHeight + verticalGap; used = 0; rowHeight = 0 }
            used = if (used == 0) width else maxOf(available, used + horizontalGap + width); rowHeight = maxOf(rowHeight, height)
        }
        maxWidth = maxOf(maxWidth, minOf(available, used)); if (rowHeight > 0) totalHeight += rowHeight; return FlowSize(maxWidth, totalHeight)
    }
    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    private class FlowSize(val maxWidth: Int, val height: Int)
}
