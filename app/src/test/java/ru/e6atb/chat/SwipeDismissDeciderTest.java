package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public final class SwipeDismissDeciderTest {
	@Test
	public void dismissesForDistanceOrDownwardFlingOnly() {
		assertTrue(SwipeDismissDecider.shouldDismiss(300.0f, 800, 0.0f, 1.0f));
		assertTrue(SwipeDismissDecider.shouldDismiss(30.0f, 800, 1200.0f, 1.0f));
		assertFalse(SwipeDismissDecider.shouldDismiss(20.0f, 800, 2000.0f, 1.0f));
		assertFalse(SwipeDismissDecider.shouldDismiss(100.0f, 800, -1500.0f, 1.0f));
	}

	@Test
	public void rawPointerCoordinatesProduceStableMonotonicTranslation() {
		assertEquals(50.0f, SwipeDismissDecider.dragTranslation(0.0f, 100.0f, 150.0f), 0.001f);
		assertEquals(60.0f, SwipeDismissDecider.dragTranslation(0.0f, 100.0f, 160.0f), 0.001f);
		assertEquals(35.0f, SwipeDismissDecider.dragTranslation(30.0f, 200.0f, 205.0f), 0.001f);
		assertEquals(0.0f, SwipeDismissDecider.dragTranslation(0.0f, 100.0f, 80.0f), 0.001f);
	}

	@Test
	public void legacyAnimationInterpolationIsBounded() {
		assertEquals(20.0f, SwipeDismissDecider.animationOffset(20.0f, 100.0f, -1.0f), 0.001f);
		assertEquals(60.0f, SwipeDismissDecider.animationOffset(20.0f, 100.0f, 0.5f), 0.001f);
		assertEquals(100.0f, SwipeDismissDecider.animationOffset(20.0f, 100.0f, 2.0f), 0.001f);
	}
}
