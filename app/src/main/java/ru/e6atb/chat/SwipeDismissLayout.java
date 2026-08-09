package ru.e6atb.chat;

import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.FrameLayout;

final class SwipeDismissLayout extends FrameLayout {
	private final int touchSlop;
	private final float density;
	private Runnable dismissAction;
	private VelocityTracker velocityTracker;
	private float downRawX;
	private float downRawY;
	private float startOffsetY;
	private float offsetY;
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
				setOffsetY(SwipeDismissDecider.dragTranslation(startOffsetY, downRawY, event.getRawY()));
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
				finishGesture(SwipeDismissDecider.shouldDismiss(offsetY, getHeight(), velocity, density));
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
		clearAnimation();
		downRawX = event.getRawX();
		downRawY = event.getRawY();
		startOffsetY = Math.max(0.0f, offsetY);
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
			animateOffset(Math.max(getHeight(), getResources().getDisplayMetrics().heightPixels), true);
		} else {
			animateOffset(0.0f, false);
		}
	}

	private void animateOffset(final float targetY, final boolean dismiss) {
		final float initialY = offsetY;
		Animation animation = new Animation() {
			@Override
			protected void applyTransformation(float progress, Transformation transformation) {
				setOffsetY(SwipeDismissDecider.animationOffset(initialY, targetY, progress));
				if (dismiss) transformation.setAlpha(Math.max(0.0f, 1.0f - progress));
			}
		};
		animation.setDuration(180L);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override public void onAnimationStart(Animation ignored) {}
			@Override public void onAnimationRepeat(Animation ignored) {}
			@Override public void onAnimationEnd(Animation ignored) {
				if (dismiss) {
					if (dismissAction != null) dismissAction.run();
				} else {
					setOffsetY(0.0f);
					finishing = false;
				}
			}
		});
		startAnimation(animation);
	}

	private void setOffsetY(float value) {
		float safe = Math.max(0.0f, value);
		int delta = Math.round(safe - offsetY);
		if (delta != 0) {
			offsetTopAndBottom(delta);
			offsetY += delta;
		}
	}

	private void recycleTracker() {
		if (velocityTracker == null) return;
		velocityTracker.recycle();
		velocityTracker = null;
	}

	private static boolean descendantCanScrollUp(View view) {
		if (canScrollUp(view)) return true;
		if (!(view instanceof ViewGroup)) return false;
		ViewGroup group = (ViewGroup) view;
		for (int i = 0; i < group.getChildCount(); i++) {
			if (descendantCanScrollUp(group.getChildAt(i))) return true;
		}
		return false;
	}

	private static boolean canScrollUp(View view) {
		if (view == null) return false;
		if (Build.VERSION.SDK_INT >= 14) {
			try {
				Object result = View.class.getMethod("canScrollVertically", int.class).invoke(view, -1);
				if (result instanceof Boolean) return ((Boolean) result).booleanValue();
			} catch (Exception ignored) {
			}
		}
		if (view instanceof AbsListView) {
			AbsListView list = (AbsListView) view;
			return list.getChildCount() > 0
					&& (list.getFirstVisiblePosition() > 0
					|| list.getChildAt(0).getTop() < list.getPaddingTop());
		}
		return view.getScrollY() > 0;
	}
}
