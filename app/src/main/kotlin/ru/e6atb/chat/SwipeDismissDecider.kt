package ru.e6atb.chat

object SwipeDismissDecider {
    @JvmStatic fun dragTranslation(startTranslationPx: Float, downRawYPx: Float, currentRawYPx: Float) = maxOf(0f, startTranslationPx + currentRawYPx - downRawYPx)
    @JvmStatic fun animationOffset(startPx: Float, targetPx: Float, progress: Float) = startPx + (targetPx - startPx) * progress.coerceIn(0f, 1f)
    @JvmStatic fun shouldDismiss(distancePx: Float, heightPx: Int, velocityYPx: Float, density: Float): Boolean {
        val safeDensity = maxOf(1f, density)
        return distancePx > 0f && (distancePx >= maxOf(96f * safeDensity, heightPx * .28f) || (distancePx >= 24f * safeDensity && velocityYPx >= 900f * safeDensity))
    }
}
