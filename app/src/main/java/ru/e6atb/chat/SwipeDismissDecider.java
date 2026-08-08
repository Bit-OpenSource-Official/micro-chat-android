package ru.e6atb.chat;

final class SwipeDismissDecider {
	private SwipeDismissDecider() {
	}

	static float dragTranslation(float startTranslationPx, float downRawYPx, float currentRawYPx) {
		return Math.max(0.0f, startTranslationPx + currentRawYPx - downRawYPx);
	}

	static boolean shouldDismiss(float distancePx, int heightPx, float velocityYPx, float density) {
		float safeDensity = Math.max(1.0f, density);
		float distanceThreshold = Math.max(96.0f * safeDensity, heightPx * 0.28f);
		boolean farEnough = distancePx >= distanceThreshold;
		boolean fastEnough = distancePx >= 24.0f * safeDensity
				&& velocityYPx >= 900.0f * safeDensity;
		return distancePx > 0.0f && (farEnough || fastEnough);
	}
}
