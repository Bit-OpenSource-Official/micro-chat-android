package ru.e6atb.chat;

import android.content.Context;
import android.widget.ScrollView;

final class BoundedScrollView extends ScrollView {
	private final int maxHeight;

	BoundedScrollView(Context context, int maxHeight) {
		super(context);
		this.maxHeight = maxHeight;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int mode = MeasureSpec.getMode(heightMeasureSpec);
		int size = MeasureSpec.getSize(heightMeasureSpec);
		int height = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
	}
}
