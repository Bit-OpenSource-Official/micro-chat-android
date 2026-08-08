package ru.e6atb.chat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

final class SwipeDismissLayout extends FrameLayout {
	private final int touchSlop;
	private final float density;
	private Runnable dismissAction;
	private VelocityTracker velocityTracker;
	private float downRawX;
	private float downRawY;
	private float startTranslationY;
	private boolean dragging;
	private boolean finishing;

	SwipeDismissLayout(Context context) {
		super(context);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		density = context.getResources().getDisplayMetrics().density;
		setClipChildren(false);
		setClipToPadding(false);
	}

	void setDismissAction(Runnable action) {
		dismissAction = action;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				beginGesture(event);
				return false;
			case MotionEvent.ACTION_MOVE:
				track(event);
				float dx = event.getRawX() - downRawX;
				float dy = event.getRawY() - downRawY;
				if (!dragging && dy > touchSlop && dy > Math.abs(dx) * 1.15f && !descendantCanScrollUp(this)) {
					dragging = true;
					getParent().requestDisallowInterceptTouchEvent(true);
					return true;
				}
				return false;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				recycleTracker();
				return false;
			default:
				return false;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		track(event);
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_MOVE:
				if (!dragging) return false;
				setTranslationY(SwipeDismissDecider.dragTranslation(startTranslationY, downRawY, event.getRawY()));
				setAlpha(Math.max(0.72f, 1.0f - getTranslationY() / Math.max(1.0f, getHeight()) * 0.28f));
				return true;
			case MotionEvent.ACTION_UP:
				if (!dragging) {
					recycleTracker();
					return false;
				}
				float velocity = 0.0f;
				if (velocityTracker != null) {
					velocityTracker.computeCurrentVelocity(1000);
					velocity = velocityTracker.getYVelocity(event.getPointerId(0));
				}
				finishGesture(SwipeDismissDecider.shouldDismiss(getTranslationY(), getHeight(), velocity, density));
				return true;
			case MotionEvent.ACTION_CANCEL:
				if (dragging) finishGesture(false);
				else recycleTracker();
				return dragging;
			default:
				return dragging;
		}
	}

	private void beginGesture(MotionEvent event) {
		animate().cancel();
		downRawX = event.getRawX();
		downRawY = event.getRawY();
		startTranslationY = Math.max(0.0f, getTranslationY());
		dragging = false;
		finishing = false;
		recycleTracker();
		velocityTracker = VelocityTracker.obtain();
		velocityTracker.addMovement(event);
	}

	private void track(MotionEvent event) {
		if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
		velocityTracker.addMovement(event);
	}

	private void finishGesture(boolean dismiss) {
		if (finishing) return;
		finishing = true;
		dragging = false;
		recycleTracker();
		if (dismiss) {
			animate().translationY(Math.max(getHeight(), getResources().getDisplayMetrics().heightPixels))
					.alpha(0.0f).setDuration(180L).withEndAction(new Runnable() {
						@Override public void run() {
							if (dismissAction != null) dismissAction.run();
						}
					}).start();
		} else {
			animate().translationY(0.0f).alpha(1.0f).setDuration(180L).withEndAction(new Runnable() {
				@Override public void run() { finishing = false; }
			}).start();
		}
	}

	private void recycleTracker() {
		if (velocityTracker == null) return;
		velocityTracker.recycle();
		velocityTracker = null;
	}

	private static boolean descendantCanScrollUp(View view) {
		if (view != null && view.canScrollVertically(-1)) return true;
		if (!(view instanceof ViewGroup)) return false;
		ViewGroup group = (ViewGroup) view;
		for (int i = 0; i < group.getChildCount(); i++) {
			if (descendantCanScrollUp(group.getChildAt(i))) return true;
		}
		return false;
	}
}
