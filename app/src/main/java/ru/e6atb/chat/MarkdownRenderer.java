package ru.e6atb.chat;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;

import java.util.Locale;

final class MarkdownRenderer {
	interface Callbacks {
		void copyCode(String code);
		void openUrl(String url);
		void openMention(String login);
		int linkColor();
	}

	private final Callbacks callbacks;

	MarkdownRenderer(Callbacks callbacks) {
		this.callbacks = callbacks;
	}

	CharSequence render(String value) {
		value = DisplayText.safe(value);
		if (value.length() == 0) return "";
		SpannableStringBuilder out = new SpannableStringBuilder();
		int i = 0;
		while (i < value.length()) {
			if (value.charAt(i) == '`') {
				int end = value.indexOf('`', i + 1);
				if (end > i + 1) {
					appendCodeSpan(out, value.substring(i + 1, end));
					i = end + 1;
					continue;
				}
			}
			int urlEnd = linkEnd(value, i);
			if (urlEnd > i) {
				String label = value.substring(i, urlEnd);
				String url = label.startsWith("www.") ? "https://" + label : label;
				appendUrlSpan(out, label, url);
				i = urlEnd;
				continue;
			}
			int mentionEnd = mentionEnd(value, i);
			if (mentionEnd > i) {
				String mention = value.substring(i + 1, mentionEnd).toLowerCase(Locale.US);
				appendMentionSpan(out, value.substring(i, mentionEnd), mention);
				i = mentionEnd;
				continue;
			}
			if (startsWith(value, i, "**")) {
				int end = value.indexOf("**", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StyleSpan(Typeface.BOLD));
					i = end + 2;
					continue;
				}
			}
			if (startsWith(value, i, "__")) {
				int end = value.indexOf("__", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StyleSpan(Typeface.BOLD));
					i = end + 2;
					continue;
				}
			}
			if (startsWith(value, i, "~~")) {
				int end = value.indexOf("~~", i + 2);
				if (end > i + 2) {
					appendStyleSpan(out, value.substring(i + 2, end), new StrikethroughSpan());
					i = end + 2;
					continue;
				}
			}
			if (value.charAt(i) == '*') {
				int end = value.indexOf('*', i + 1);
				if (end > i + 1) {
					appendStyleSpan(out, value.substring(i + 1, end), new StyleSpan(Typeface.ITALIC));
					i = end + 1;
					continue;
				}
			}
			if (value.charAt(i) == '_') {
				int end = value.indexOf('_', i + 1);
				if (end > i + 1) {
					appendStyleSpan(out, value.substring(i + 1, end), new StyleSpan(Typeface.ITALIC));
					i = end + 1;
					continue;
				}
			}
			int cp = value.codePointAt(i);
			out.append(value, i, i + Character.charCount(cp));
			i += Character.charCount(cp);
		}
		return out;
	}

	private boolean startsWith(String value, int offset, String prefix) {
		return offset + prefix.length() <= value.length() && value.startsWith(prefix, offset);
	}

	private int linkEnd(String value, int offset) {
		if (!startsWith(value, offset, "http://") &&
			!startsWith(value, offset, "https://") &&
			!startsWith(value, offset, "www.")) {
			return -1;
		}
		int end = offset;
		while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
			end++;
		}
		while (end > offset && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
			end--;
		}
		return end > offset ? end : -1;
	}

	private int mentionEnd(String value, int offset) {
		if (value.charAt(offset) != '@') return -1;
		if (offset > 0) {
			char prev = value.charAt(offset - 1);
			if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '.') return -1;
		}
		int end = offset + 1;
		while (end < value.length()) {
			char c = value.charAt(end);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') break;
			end++;
		}
		return end > offset + 1 ? end : -1;
	}

	private void appendStyleSpan(SpannableStringBuilder out, String value, Object span) {
		int start = out.length();
		out.append(value);
		out.setSpan(span, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendCodeSpan(SpannableStringBuilder out, final String value) {
		int start = out.length();
		out.append(value);
		out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				if (callbacks != null) callbacks.copyCode(value);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setTypeface(Typeface.MONOSPACE);
				ds.setColor(linkColor());
				ds.setUnderlineText(false);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendUrlSpan(SpannableStringBuilder out, String label, final String url) {
		int start = out.length();
		out.append(label);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				if (callbacks != null) callbacks.openUrl(url);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setColor(linkColor());
				ds.setUnderlineText(true);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private void appendMentionSpan(SpannableStringBuilder out, String label, final String login) {
		int start = out.length();
		out.append(label);
		out.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				if (callbacks != null) callbacks.openMention(login);
			}

			@Override
			public void updateDrawState(TextPaint ds) {
				super.updateDrawState(ds);
				ds.setColor(linkColor());
				ds.setUnderlineText(false);
			}
		}, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	private int linkColor() {
		return callbacks == null ? 0xff7fb4ff : callbacks.linkColor();
	}
}
