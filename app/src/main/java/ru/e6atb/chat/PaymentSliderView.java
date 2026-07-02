package ru.e6atb.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

final class PaymentSliderView extends View {
	interface Theme {
		int dp(int value);
		int elementRadius();
		int blend(int a, int b, float t);
		int surfaceHi();
		int primary();
		int muted();
		int onPrimary();
	}

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF trackRect = new RectF();
	private final RectF fillRect = new RectF();
	private final RectF thumbRect = new RectF();
	private final String hint;
	private final boolean confirmLeft;
	private final Theme theme;
	private Runnable confirmAction;
	private Runnable resetAnimation;
	private float progress;
	private float touchOffset;
	private boolean tracking;

	PaymentSliderView(Context context, String hint, boolean confirmLeft, Theme theme) {
		super(context);
		this.hint = hint;
		this.confirmLeft = confirmLeft;
		this.theme = theme;
		this.progress = confirmLeft ? 1f : 0f;
		setFocusable(true);
	}

	void setOnConfirmAction(Runnable confirmAction) {
		this.confirmAction = confirmAction;
	}

	@Override
	public boolean performClick() {
		return super.performClick();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int desiredHeight = dp(56);
		int desiredWidth = dp(240);
		setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int w = getWidth();
		int h = getHeight();
		float inset = dp(2);
		float trackLeft = inset;
		float trackTop = inset;
		float trackRight = Math.max(trackLeft, w - inset);
		float trackBottom = Math.max(trackTop, h - inset);
		float radius = Math.min(elementRadius(), Math.max(0f, (trackBottom - trackTop) / 4f));
		float thumbWidth = thumbWidth();
		float thumbHeight = thumbHeight();
		float thumbXInset = thumbHorizontalInset();
		float thumbYInset = thumbVerticalInset();
		float thumbTop = trackTop + thumbYInset;
		float thumbLeft = trackLeft + thumbXInset + (trackRight - trackLeft - thumbWidth - thumbXInset * 2f) * progress;

		trackRect.set(trackLeft, trackTop, trackRight, trackBottom);
		paint.setColor(blend(surfaceHi(), Color.BLACK, 0.35f));
		canvas.drawRoundRect(trackRect, radius, radius, paint);

		if (confirmLeft) {
			fillRect.set(
				Math.max(trackLeft, thumbLeft - thumbXInset),
				trackTop,
				trackRight,
				trackBottom
			);
		} else {
			fillRect.set(
				trackLeft,
				trackTop,
				Math.min(trackRight, thumbLeft + thumbWidth + thumbXInset),
				trackBottom
			);
		}
		paint.setColor(blend(primary(), Color.BLACK, 0.55f));
		canvas.drawRoundRect(fillRect, radius, radius, paint);

		textPaint.setTextSize(dp(14));
		textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setColor(muted());
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		float textY = h / 2f - (fm.ascent + fm.descent) / 2f;
		canvas.drawText(hint, w / 2f, textY, textPaint);

		thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight);
		paint.setColor(blend(primary(), Color.WHITE, 0.14f));
		float thumbRadius = Math.min(elementRadius(), thumbHeight / 3f);
		canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, paint);

		textPaint.setTextSize(dp(18));
		textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setColor(onPrimary());
		Paint.FontMetrics arrowFm = textPaint.getFontMetrics();
		float arrowY = thumbRect.centerY() - (arrowFm.ascent + arrowFm.descent) / 2f;
		canvas.drawText(confirmLeft ? "<" : ">", thumbRect.centerX(), arrowY, textPaint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN) {
			if (!touchInsideThumb(event.getX(), event.getY())) return false;
			cancelResetAnimation();
			tracking = true;
			touchOffset = event.getX() - currentThumbLeft();
			if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
			return true;
		}
		if (action == MotionEvent.ACTION_MOVE && tracking) {
			updateProgressFromTouch(event.getX());
			return true;
		}
		if (action == MotionEvent.ACTION_UP && tracking) {
			updateProgressFromTouch(event.getX());
			boolean confirmed = confirmLeft ? progress <= 0.14f : progress >= 0.86f;
			tracking = false;
			if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
			if (confirmed) {
				cancelResetAnimation();
				progress = confirmLeft ? 0f : 1f;
				invalidate();
				performClick();
				if (confirmAction != null) confirmAction.run();
			} else {
				resetThumb();
			}
			return true;
		}
		if (action == MotionEvent.ACTION_CANCEL && tracking) {
			tracking = false;
			if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
			resetThumb();
			return true;
		}
		return false;
	}

	private void updateProgressFromTouch(float x) {
		float trackLeft = dp(2);
		float thumbInset = thumbHorizontalInset();
		float usable = Math.max(1f, getWidth() - dp(4) - thumbWidth() - thumbInset * 2f);
		progress = Math.max(0f, Math.min(1f, (x - touchOffset - trackLeft - thumbInset) / usable));
		invalidate();
	}

	private boolean touchInsideThumb(float x, float y) {
		float thumbWidth = thumbWidth();
		float thumbHeight = thumbHeight();
		float left = currentThumbLeft();
		float top = (getHeight() - thumbHeight) / 2f;
		float slop = dp(8);
		return x >= left - slop && x <= left + thumbWidth + slop && y >= top - slop && y <= top + thumbHeight + slop;
	}

	private float currentThumbLeft() {
		float trackLeft = dp(2);
		float thumbInset = thumbHorizontalInset();
		float usable = Math.max(1f, getWidth() - dp(4) - thumbWidth() - thumbInset * 2f);
		return trackLeft + thumbInset + usable * progress;
	}

	private float thumbWidth() {
		return Math.max(dp(64), Math.min(dp(82), thumbHeight() * 1.65f));
	}

	private float thumbHeight() {
		return Math.max(dp(36), Math.min(dp(44), getHeight() - dp(10)));
	}

	private float thumbHorizontalInset() {
		return dp(4);
	}

	private float thumbVerticalInset() {
		float trackHeight = Math.max(0f, getHeight() - dp(4));
		return Math.max(0f, (trackHeight - thumbHeight()) / 2f);
	}

	private void resetThumb() {
		animateThumbToStart();
	}

	private void animateThumbToStart() {
		cancelResetAnimation();
		final float startProgress = progress;
		final float targetProgress = confirmLeft ? 1f : 0f;
		if (Math.abs(startProgress - targetProgress) <= 0.001f) {
			progress = targetProgress;
			invalidate();
			return;
		}
		final long startTime = System.currentTimeMillis();
		final long durationMs = 180L;
		resetAnimation = new Runnable() {
			@Override
			public void run() {
				float t = Math.min(1f, (System.currentTimeMillis() - startTime) / (float)durationMs);
				float eased = 1f - (1f - t) * (1f - t) * (1f - t);
				progress = startProgress + (targetProgress - startProgress) * eased;
				invalidate();
				if (t < 1f) {
					postDelayed(this, 16L);
				} else {
					progress = targetProgress;
					resetAnimation = null;
					invalidate();
				}
			}
		};
		post(resetAnimation);
	}

	private void cancelResetAnimation() {
		if (resetAnimation != null) {
			removeCallbacks(resetAnimation);
			resetAnimation = null;
		}
	}

	private int dp(int value) {
		return theme.dp(value);
	}

	private int elementRadius() {
		return theme.elementRadius();
	}

	private int blend(int a, int b, float t) {
		return theme.blend(a, b, t);
	}

	private int surfaceHi() {
		return theme.surfaceHi();
	}

	private int primary() {
		return theme.primary();
	}

	private int muted() {
		return theme.muted();
	}

	private int onPrimary() {
		return theme.onPrimary();
	}
}
