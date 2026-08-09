package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CrashReportStoreTest {
	@Test
	public void reportIsVersionedRedactedAndBounded() throws Exception {
		String longLine = repeat("abcdef0123456789", 400);
		RuntimeException crash = new RuntimeException(
				"token=top-secret Bearer bearer-secret password=hunter2 " + longLine
		);
		String report = CrashReportStore.formatReport(
				"report-1", "2026-08-09T00:00:00.000Z", "0.9.3", 100041,
				"15", 35, "OVE Test Device", "background-sync", crash
		);
		assertTrue(report.startsWith(CrashReportStore.MARKER));
		assertTrue(report.contains("Report-ID: report-1"));
		assertTrue(report.contains("background-sync"));
		assertFalse(report.contains("top-secret"));
		assertFalse(report.contains("bearer-secret"));
		assertFalse(report.contains("hunter2"));
		assertTrue(report.getBytes("UTF-8").length <= 3400);
	}

	private static String repeat(String value, int times) {
		StringBuilder out = new StringBuilder(value.length() * times);
		for (int i = 0; i < times; i++) out.append(value);
		return out.toString();
	}
}
