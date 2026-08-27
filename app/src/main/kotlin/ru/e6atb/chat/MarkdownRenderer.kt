package ru.e6atb.chat

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import java.util.Locale

internal class MarkdownRenderer(private val callbacks: Callbacks?) {
    interface Callbacks { fun copyCode(code: String?); fun openUrl(url: String?); fun openMention(login: String?); fun canRunBotCommand(): Boolean; fun runBotCommand(command: String?); fun linkColor(): Int }
    fun render(raw: String?): CharSequence {
        val value = DisplayText.safe(raw); if (value.isEmpty()) return ""
        val out = SpannableStringBuilder(); var index = 0
        while (index < value.length) {
            val codeEnd = if (value[index] == '`') value.indexOf('`', index + 1) else -1
            if (codeEnd > index + 1) { appendCodeSpan(out, value.substring(index + 1, codeEnd)); index = codeEnd + 1; continue }
            val urlEnd = linkEnd(value, index)
            if (urlEnd > index) { val label = value.substring(index, urlEnd); appendUrlSpan(out, label, if (label.startsWith("www.")) "https://$label" else label); index = urlEnd; continue }
            val mentionEnd = mentionEnd(value, index)
            if (mentionEnd > index) { appendMentionSpan(out, value.substring(index, mentionEnd), value.substring(index + 1, mentionEnd).lowercase(Locale.US)); index = mentionEnd; continue }
            val commandEnd = commandEnd(value, index)
            if (commandEnd > index && callbacks?.canRunBotCommand() == true) { appendCommandSpan(out, value.substring(index, commandEnd)); index = commandEnd; continue }
            val match = listOf("**" to StyleSpan(Typeface.BOLD), "__" to StyleSpan(Typeface.BOLD), "~~" to StrikethroughSpan()).firstOrNull { (prefix, _) -> startsWith(value, index, prefix) }
            if (match != null) { val end = value.indexOf(match.first, index + match.first.length); if (end > index + match.first.length) { appendStyleSpan(out, value.substring(index + match.first.length, end), match.second); index = end + match.first.length; continue } }
            if (value[index] == '*' || value[index] == '_') { val end = value.indexOf(value[index], index + 1); if (end > index + 1) { appendStyleSpan(out, value.substring(index + 1, end), StyleSpan(Typeface.ITALIC)); index = end + 1; continue } }
            val codePoint = value.codePointAt(index); out.append(value, index, index + Character.charCount(codePoint)); index += Character.charCount(codePoint)
        }
        return out
    }
    private fun startsWith(value: String, offset: Int, prefix: String) = offset + prefix.length <= value.length && value.startsWith(prefix, offset)
    private fun linkEnd(value: String, offset: Int): Int { if (!startsWith(value, offset, "http://") && !startsWith(value, offset, "https://") && !startsWith(value, offset, "www.")) return -1; var end = offset; while (end < value.length && !Character.isWhitespace(value[end])) end++; while (end > offset && ".,;:!?)]}".indexOf(value[end - 1]) >= 0) end--; return if (end > offset) end else -1 }
    private fun mentionEnd(value: String, offset: Int): Int { if (value[offset] != '@') return -1; if (offset > 0 && (Character.isLetterOrDigit(value[offset - 1]) || value[offset - 1] == '_' || value[offset - 1] == '.')) return -1; var end = offset + 1; while (end < value.length && (Character.isLetterOrDigit(value[end]) || value[end] == '_' || value[end] == '-' || value[end] == '.')) end++; return if (end > offset + 1) end else -1 }
    private fun commandEnd(value: String, offset: Int): Int { if (value[offset] != '/') return -1; if (offset > 0 && (Character.isLetterOrDigit(value[offset - 1]) || value[offset - 1] == '_' || value[offset - 1] == '-')) return -1; var end = offset + 1; while (end < value.length && (value[end].isLetterOrDigit() || value[end] == '_')) end++; return if (end > offset + 1 && end - offset <= 33) end else -1 }
    private fun appendStyleSpan(out: SpannableStringBuilder, value: String, span: Any) { val start = out.length; out.append(value); out.setSpan(span, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun appendCodeSpan(out: SpannableStringBuilder, value: String) { val start = out.length; out.append(value); out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); out.setSpan(span(value, false) { callbacks?.copyCode(value) }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun appendUrlSpan(out: SpannableStringBuilder, label: String, url: String) { val start = out.length; out.append(label); out.setSpan(span(url, true) { callbacks?.openUrl(url) }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun appendMentionSpan(out: SpannableStringBuilder, label: String, login: String) { val start = out.length; out.append(label); out.setSpan(span(login, false) { callbacks?.openMention(login) }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun appendCommandSpan(out: SpannableStringBuilder, command: String) { val start = out.length; out.append(command); out.setSpan(span(command, false) { callbacks?.runBotCommand(command) }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun span(value: String, underline: Boolean, click: () -> Unit) = object : ClickableSpan() { override fun onClick(widget: View) = click(); override fun updateDrawState(paint: TextPaint) { super.updateDrawState(paint); paint.color = callbacks?.linkColor() ?: 0xff7fb4ff.toInt(); paint.isUnderlineText = underline; if (!underline) paint.typeface = Typeface.MONOSPACE.takeIf { value.isNotEmpty() } ?: paint.typeface } }
}
