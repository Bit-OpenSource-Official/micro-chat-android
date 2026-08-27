@file:Suppress("DEPRECATION")
package ru.e6atb.chat

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.*
import android.widget.FrameLayout
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.Collections

class QrScannerActivity : Activity(), SurfaceHolder.Callback, Camera.PreviewCallback {
    private val reader = MultiFormatReader(); private lateinit var root: FrameLayout; private lateinit var preview: SurfaceView; private var overlay: ScannerOverlayView? = null; private var camera: Camera? = null; private var completed = false; private var previewScale = 1f
    override fun onCreate(state: Bundle?) { super.onCreate(state); root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); clipChildren = true }; preview = SurfaceView(this).also { it.holder.addCallback(this) }; root.addView(preview, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); overlay = ScannerOverlayView().also { item -> item.setOnClickListener { focusCameraCenter() }; root.addView(item, FrameLayout.LayoutParams(-1, -1)) }; setContentView(root) }
    override fun surfaceCreated(holder: SurfaceHolder) { try { camera = Camera.open().also { active -> active.setDisplayOrientation(90); val size = active.parameters.previewSize; holder.setFixedSize(size.width, size.height); active.setPreviewDisplay(holder); configurePreviewLayout(size); active.setPreviewCallback(this); active.startPreview() } } catch (_: Exception) { releaseCamera(); setResult(RESULT_CANCELED); finish() } }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { camera?.parameters?.previewSize?.let(::configurePreviewLayout) }
    override fun surfaceDestroyed(holder: SurfaceHolder) = releaseCamera()
    private fun focusCameraCenter() { val active = camera ?: return; if (completed) return; try { active.cancelAutoFocus(); val parameters = active.parameters; parameters.supportedFocusModes?.let { modes -> when { modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO; modes.contains(Camera.Parameters.FOCUS_MODE_MACRO) -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_MACRO } }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CenterFocusArea.apply(parameters); active.parameters = parameters; active.autoFocus { _, _ -> } } catch (_: RuntimeException) { } }
    private object CenterFocusArea { private val area = Collections.singletonList(Camera.Area(Rect(-250, -250, 250, 250), 1000)); fun apply(parameters: Camera.Parameters) { if (parameters.maxNumFocusAreas > 0) parameters.focusAreas = area; if (parameters.maxNumMeteringAreas > 0) parameters.meteringAreas = area } }
    private fun configurePreviewLayout(size: Camera.Size?) { if (size == null || !::root.isInitialized || !::preview.isInitialized) return; val width = root.width; val height = root.height; if (width <= 0 || height <= 0) { root.post { configurePreviewLayout(size) }; return }; val scale = maxOf(width / size.height.toFloat(), height / size.width.toFloat()); previewScale = scale; preview.layoutParams = FrameLayout.LayoutParams(maxOf(width, (size.height * scale).toInt()), maxOf(height, (size.width * scale).toInt()), Gravity.CENTER) }
    override fun onPreviewFrame(data: ByteArray?, source: Camera?) { val view = overlay ?: return; if (completed || data == null || source == null) return; try { val size = source.parameters.previewSize; val crop = minOf(maxOf(1, (view.scanSize() / maxOf(.01f, previewScale)).toInt()), minOf(size.width, size.height)); val left = maxOf(0, (size.width - crop) / 2); val top = maxOf(0, (size.height - crop) / 2); val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(data, size.width, size.height, left, top, crop, crop, false)))); if (OAuthCodeParser.parse(result.text).isNotEmpty()) { completed = true; setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, result.text)); finish() } } catch (_: Exception) { } finally { reader.reset() } }
    override fun onPause() { releaseCamera(); super.onPause() }
    private fun releaseCamera() { camera?.let { try { it.setPreviewCallback(null); it.stopPreview() } catch (_: Exception) { }; it.release() }; camera = null }
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt(); private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity
    private inner class ScannerOverlayView : View(this@QrScannerActivity) { private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6000000.toInt(); style = Paint.Style.FILL }; private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(2f).toFloat() }; private val hintBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC101827.toInt() }; private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = sp(16f); textAlign = Paint.Align.LEFT }; private val scan = RectF(); private val mask = Path()
        init { contentDescription = getString(R.string.oauth_scan_hint) }
        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) { var side = minOf(minOf(width - dp(48f), height - dp(160f)), dp(360f)).toFloat(); side = maxOf(dp(160f).toFloat(), side); val left = (width - side) / 2f; val top = (height - side) / 2f; scan.set(left, top, left + side, top + side) }
        fun scanSize(): Float = if (scan.width() > 0) scan.width() else dp(240f).toFloat()
        override fun onDraw(canvas: Canvas) { val radius = dp(24f).toFloat(); mask.reset(); mask.fillType = Path.FillType.EVEN_ODD; mask.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW); mask.addRoundRect(scan, radius, radius, Path.Direction.CW); canvas.drawPath(mask, shade); canvas.drawRoundRect(scan, radius, radius, border); drawHint(canvas) }
        private fun drawHint(canvas: Canvas) { val maxWidth = maxOf(dp(180f), minOf(scan.width().toInt(), width - dp(48f))); val hint = StaticLayout(getString(R.string.oauth_scan_hint), hintPaint, maxWidth - dp(28f), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false); val boxHeight = hint.height + dp(24f); val left = (width - maxWidth) / 2f; val top = minOf(scan.bottom + dp(22f), (height - boxHeight - dp(20f)).toFloat()); val box = RectF(left, top, left + maxWidth, top + boxHeight); canvas.drawRoundRect(box, dp(14f).toFloat(), dp(14f).toFloat(), hintBackground); canvas.save(); canvas.translate(left + dp(14f), top + dp(12f)); hint.draw(canvas); canvas.restore() }
    }
    companion object { const val EXTRA_RESULT = "qr_result" }
}
