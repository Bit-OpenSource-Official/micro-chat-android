package ru.e6atb.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

final class MessageMetaLayout extends ViewGroup {
	private final int horizontalGap;
	private final int verticalGap;
	private View footer;

	MessageMetaLayout(Context context, int horizontalGap, int verticalGap) {
		super(context);
		this.horizontalGap = Math.max(0, horizontalGap);
		this.verticalGap = Math.max(0, verticalGap);
	}

	void setFooter(View footer) {
		this.footer = footer;
		if (footer != null && footer.getParent() != this) addView(footer);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int available = widthMode == MeasureSpec.UNSPECIFIED
				? Integer.MAX_VALUE / 4
				: Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
		for (int i = 0; i < getChildCount(); i++) {
			measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec);
		}

		FlowSize flow = flowSize(available);
		int desiredWidth = flow.maxWidth + getPaddingLeft() + getPaddingRight();
		int desiredHeight = flow.height + getPaddingTop() + getPaddingBottom();
		setMeasuredDimension(
				resolveSize(desiredWidth, widthMeasureSpec),
				resolveSize(desiredHeight, heightMeasureSpec)
		);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		int available = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
		int rowTop = getPaddingTop();
		int used = 0;
		int rowHeight = 0;

		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			if (child == footer || child.getVisibility() == GONE) continue;
			int childWidth = child.getMeasuredWidth();
			int childHeight = child.getMeasuredHeight();
			if (used > 0 && used + horizontalGap + childWidth > available) {
				rowTop += rowHeight + verticalGap;
				used = 0;
				rowHeight = 0;
			}
			int childLeft = getPaddingLeft() + (used == 0 ? 0 : used + horizontalGap);
			child.layout(childLeft, rowTop, childLeft + childWidth, rowTop + childHeight);
			used = used == 0 ? childWidth : used + horizontalGap + childWidth;
			rowHeight = Math.max(rowHeight, childHeight);
		}

		if (footer != null && footer.getVisibility() != GONE) {
			int footerWidth = footer.getMeasuredWidth();
			int footerHeight = footer.getMeasuredHeight();
			if (used > 0 && used + horizontalGap + footerWidth > available) {
				rowTop += rowHeight + verticalGap;
				used = 0;
				rowHeight = 0;
			}
			rowHeight = Math.max(rowHeight, footerHeight);
			int footerLeft = getPaddingLeft() + Math.max(used == 0 ? 0 : used + horizontalGap, available - footerWidth);
			int footerTop = rowTop + rowHeight - footerHeight;
			footer.layout(footerLeft, footerTop, footerLeft + footerWidth, footerTop + footerHeight);
		}
	}

	private FlowSize flowSize(int available) {
		int used = 0;
		int rowHeight = 0;
		int totalHeight = 0;
		int maxWidth = 0;
		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			if (child == footer || child.getVisibility() == GONE) continue;
			int childWidth = child.getMeasuredWidth();
			int childHeight = child.getMeasuredHeight();
			if (used > 0 && used + horizontalGap + childWidth > available) {
				maxWidth = Math.max(maxWidth, used);
				totalHeight += rowHeight + verticalGap;
				used = 0;
				rowHeight = 0;
			}
			used = used == 0 ? childWidth : used + horizontalGap + childWidth;
			rowHeight = Math.max(rowHeight, childHeight);
		}
		if (footer != null && footer.getVisibility() != GONE) {
			int footerWidth = footer.getMeasuredWidth();
			int footerHeight = footer.getMeasuredHeight();
			if (used > 0 && used + horizontalGap + footerWidth > available) {
				maxWidth = Math.max(maxWidth, used);
				totalHeight += rowHeight + verticalGap;
				used = 0;
				rowHeight = 0;
			}
			used = used == 0 ? footerWidth : Math.max(available, used + horizontalGap + footerWidth);
			rowHeight = Math.max(rowHeight, footerHeight);
		}
		maxWidth = Math.max(maxWidth, Math.min(available, used));
		if (rowHeight > 0) totalHeight += rowHeight;
		return new FlowSize(maxWidth, totalHeight);
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}

	private static final class FlowSize {
		final int maxWidth;
		final int height;

		FlowSize(int maxWidth, int height) {
			this.maxWidth = maxWidth;
			this.height = height;
		}
	}
}
