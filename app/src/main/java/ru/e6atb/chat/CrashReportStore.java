package ru.e6atb.chat;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

final class CrashReportStore {
	static final String MARKER = "[OVE_ANDROID_CRASH_V1]";
	private static final String DIRECTORY = "crash-reports";
	private static final String SUFFIX = ".pending";
	private static final int MAX_REPORT_BYTES = 3400;
	private static final int MAX_REPORTS = 8;
	private static final Pattern AUTH_VALUE = Pattern.compile(
			"(?i)((?:token|password|authorization|secret)\\s*[=:]\\s*)([^\\s,;]+)"
	);
	private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)([^\\s,;]+)");
	private static final Pattern LONG_SECRET = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{64,}(?![A-Za-z0-9])");
	private static volatile boolean installed;

	private CrashReportStore() {
	}

	static String nativeReportPath(Context context) {
		File directory = directory(context);
		return directory == null ? "" : new File(directory, "native-crash" + SUFFIX).getAbsolutePath();
	}

	static synchronized void install(final Context context) {
		if (installed || context == null) return;
		final Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable error) {
				try {
					capture(app, thread, error);
				} catch (Throwable ignored) {
				}
				if (previous != null) {
					previous.uncaughtException(thread, error);
				}
			}
		});
		installed = true;
	}

	private static void capture(Context context, Thread thread, Throwable error) throws Exception {
		File directory = directory(context);
		if (directory == null) return;
		String id = System.currentTimeMillis() + "-" + Long.toHexString(System.nanoTime());
		String report = formatReport(
				id,
				utcNow(),
				BuildConfig.VERSION_NAME,
				BuildConfig.VERSION_CODE,
				Build.VERSION.RELEASE,
				Build.VERSION.SDK_INT,
				Build.MANUFACTURER + " " + Build.MODEL,
				thread == null ? "unknown" : thread.getName(),
				error
		);
		File temporary = new File(directory, id + ".tmp");
		File destination = new File(directory, id + SUFFIX);
		FileOutputStream output = new FileOutputStream(temporary);
		try {
			output.write(report.getBytes("UTF-8"));
			output.flush();
			output.getFD().sync();
		} finally {
			output.close();
		}
		if (!temporary.renameTo(destination)) {
			temporary.delete();
			return;
		}
		prune(directory);
	}

	static String formatReport(String id, String timestamp, String versionName, int versionCode,
	                         String androidRelease, int sdk, String device, String threadName,
	                         Throwable error) {
		StringWriter stack = new StringWriter();
		PrintWriter writer = new PrintWriter(stack);
		if (error == null) {
			writer.println("Unknown uncaught exception");
		} else {
			error.printStackTrace(writer);
		}
		writer.flush();
		String report = MARKER
				+ "\nReport-ID: " + safe(id)
				+ "\nUTC: " + safe(timestamp)
				+ "\nApp: " + safe(versionName) + " (" + versionCode + ")"
				+ "\nAndroid: " + safe(androidRelease) + " (SDK " + sdk + ")"
				+ "\nDevice: " + safe(device)
				+ "\nThread: " + safe(threadName)
				+ "\n\n" + stack.toString();
		return truncateUtf8(redact(report), MAX_REPORT_BYTES);
	}

	static synchronized List<PendingReport> pending(Context context) {
		File directory = directory(context);
		ArrayList<PendingReport> reports = new ArrayList<PendingReport>();
		if (directory == null) return reports;
		File[] files = directory.listFiles();
		if (files == null) return reports;
		Arrays.sort(files, new Comparator<File>() {
			@Override
			public int compare(File left, File right) {
				return left.getName().compareTo(right.getName());
			}
		});
		for (File file : files) {
			if (!file.isFile() || !file.getName().endsWith(SUFFIX)) continue;
			String text = read(file);
			if (text.length() == 0 && file.getName().equals("native-crash" + SUFFIX)) continue;
			if (text.length() == 0 || !text.startsWith(MARKER)) {
				file.delete();
				continue;
			}
			String name = file.getName();
			reports.add(new PendingReport(name.substring(0, name.length() - SUFFIX.length()), text, file));
		}
		return reports;
	}

	static synchronized void remove(PendingReport report) {
		if (report == null || report.file == null) return;
		if (report.file.getName().equals("native-crash" + SUFFIX)) {
			try {
				FileOutputStream output = new FileOutputStream(report.file, false);
				output.getFD().sync();
				output.close();
			} catch (Exception ignored) {
			}
			return;
		}
		report.file.delete();
	}

	private static File directory(Context context) {
		if (context == null) return null;
		File root = context.getFilesDir();
		if (root == null) return null;
		File directory = new File(root, DIRECTORY);
		if (!directory.exists() && !directory.mkdirs()) return null;
		return directory;
	}

	private static void prune(File directory) {
		File[] files = directory.listFiles();
		if (files == null || files.length <= MAX_REPORTS) return;
		Arrays.sort(files, new Comparator<File>() {
			@Override
			public int compare(File left, File right) {
				return left.getName().compareTo(right.getName());
			}
		});
		int remove = files.length - MAX_REPORTS;
		for (int i = 0; i < files.length && remove > 0; i++) {
			if (files[i].isFile() && files[i].delete()) remove--;
		}
	}

	private static String read(File file) {
		FileInputStream input = null;
		try {
			input = new FileInputStream(file);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int total = 0;
			int count;
			while ((count = input.read(buffer)) >= 0 && total < MAX_REPORT_BYTES) {
				int accepted = Math.min(count, MAX_REPORT_BYTES - total);
				output.write(buffer, 0, accepted);
				total += accepted;
			}
			return new String(output.toByteArray(), "UTF-8");
		} catch (Exception ignored) {
			return "";
		} finally {
			if (input != null) try { input.close(); } catch (Exception ignored) {}
		}
	}

	private static String redact(String value) {
		String result = AUTH_VALUE.matcher(safe(value)).replaceAll("$1[REDACTED]");
		result = BEARER.matcher(result).replaceAll("$1[REDACTED]");
		return LONG_SECRET.matcher(result).replaceAll("[REDACTED_LONG_VALUE]");
	}

	private static String truncateUtf8(String value, int maxBytes) {
		try {
			if (value.getBytes("UTF-8").length <= maxBytes) return value;
			String suffix = "\n...[truncated]";
			int low = 0;
			int high = value.length();
			while (low < high) {
				int middle = (low + high + 1) / 2;
				String candidate = value.substring(0, middle) + suffix;
				if (candidate.getBytes("UTF-8").length <= maxBytes) low = middle;
				else high = middle - 1;
			}
			return value.substring(0, low) + suffix;
		} catch (Exception ignored) {
			return value.length() <= maxBytes ? value : value.substring(0, maxBytes);
		}
	}

	private static String utcNow() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date());
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	static final class PendingReport {
		final String id;
		final String text;
		final File file;

		PendingReport(String id, String text, File file) {
			this.id = id;
			this.text = text;
			this.file = file;
		}
	}
}
