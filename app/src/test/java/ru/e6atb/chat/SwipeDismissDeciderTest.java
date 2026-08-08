package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SwipeDismissDeciderTest {
	@Test
	public void dismissesForDistanceOrDownwardFlingOnly() {
		assertTrue(SwipeDismissDecider.shouldDismiss(300.0f, 800, 0.0f, 1.0f));
		assertTrue(SwipeDismissDecider.shouldDismiss(30.0f, 800, 1200.0f, 1.0f));
		assertFalse(SwipeDismissDecider.shouldDismiss(20.0f, 800, 2000.0f, 1.0f));
		assertFalse(SwipeDismissDecider.shouldDismiss(100.0f, 800, -1500.0f, 1.0f));
	}
}
