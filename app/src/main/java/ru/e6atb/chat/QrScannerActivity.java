package ru.e6atb.chat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public final class QrScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
	public static final String EXTRA_RESULT = "qr_result";
	private final MultiFormatReader reader = new MultiFormatReader();
	private FrameLayout root;
	private SurfaceView preview;
	private ScannerOverlayView overlay;
	private Camera camera;
	private boolean completed;
	private float previewScale = 1f;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		root.setClipChildren(true);

		preview = new SurfaceView(this);
		preview.getHolder().addCallback(this);
		FrameLayout.LayoutParams previewLayout = new FrameLayout.LayoutParams(-1, -1);
		previewLayout.gravity = Gravity.CENTER;
		root.addView(preview, previewLayout);

		overlay = new ScannerOverlayView();
		overlay.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				focusCameraCenter();
			}
		});
		root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
		setContentView(root);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			camera = Camera.open();
			camera.setDisplayOrientation(90);
			Camera.Size size = camera.getParameters().getPreviewSize();
			holder.setFixedSize(size.width, size.height);
			camera.setPreviewDisplay(holder);
			configurePreviewLayout(size);
			camera.setPreviewCallback(this);
			camera.startPreview();
		} catch (Exception error) {
			releaseCamera();
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (camera == null) return;
		configurePreviewLayout(camera.getParameters().getPreviewSize());
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		releaseCamera();
	}

	private void focusCameraCenter() {
		final Camera activeCamera = camera;
		if (activeCamera == null || completed) return;
		try {
			activeCamera.cancelAutoFocus();
			Camera.Parameters parameters = activeCamera.getParameters();
			List<String> focusModes = parameters.getSupportedFocusModes();
			if (focusModes != null) {
				if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
					parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				} else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
					parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
				}
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				CenterFocusArea.apply(parameters);
			}
			activeCamera.setParameters(parameters);
			activeCamera.autoFocus(new Camera.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success, Camera focusedCamera) {
					// Keeping the selected focus mode locks the result until the next tap.
				}
			});
		} catch (RuntimeException ignored) {
			// Fixed-focus and some vendor cameras reject autofocus parameters.
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static final class CenterFocusArea {
		private static final List<Camera.Area> AREA = Collections.singletonList(
				new Camera.Area(new Rect(-250, -250, 250, 250), 1000)
		);

		private CenterFocusArea() {
		}

		static void apply(Camera.Parameters parameters) {
			if (parameters.getMaxNumFocusAreas() > 0) {
				parameters.setFocusAreas(AREA);
			}
			if (parameters.getMaxNumMeteringAreas() > 0) {
				parameters.setMeteringAreas(AREA);
			}
		}
	}

	private void configurePreviewLayout(final Camera.Size size) {
		if (size == null || root == null || preview == null) return;
		final int rootWidth = root.getWidth();
		final int rootHeight = root.getHeight();
		if (rootWidth <= 0 || rootHeight <= 0) {
			root.post(new Runnable() {
				@Override
				public void run() {
					configurePreviewLayout(size);
				}
			});
			return;
		}
		// Camera buffers are landscape. setDisplayOrientation(90) swaps their displayed axes.
		float scale = Math.max(
				rootWidth / (float) size.height,
				rootHeight / (float) size.width
		);
		int displayWidth = Math.max(rootWidth, Math.round(size.height * scale));
		int displayHeight = Math.max(rootHeight, Math.round(size.width * scale));
		previewScale = scale;
		FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(displayWidth, displayHeight);
		layout.gravity = Gravity.CENTER;
		preview.setLayoutParams(layout);
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera source) {
		if (completed || data == null || overlay == null) return;
		Camera.Size size = source.getParameters().getPreviewSize();
		try {
			int cropSide = Math.round(overlay.scanSize() / Math.max(0.01f, previewScale));
			cropSide = Math.max(1, Math.min(cropSide, Math.min(size.width, size.height)));
			int left = Math.max(0, (size.width - cropSide) / 2);
			int top = Math.max(0, (size.height - cropSide) / 2);
			PlanarYUVLuminanceSource luminance = new PlanarYUVLuminanceSource(
					data, size.width, size.height, left, top, cropSide, cropSide, false);
			Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(luminance)));
			if (result != null && OAuthCodeParser.parse(result.getText()).length() > 0) {
				completed = true;
				Intent output = new Intent();
				output.putExtra(EXTRA_RESULT, result.getText());
				setResult(RESULT_OK, output);
				finish();
			}
		} catch (Exception ignored) {
		} finally {
			reader.reset();
		}
	}

	@Override
	protected void onPause() {
		releaseCamera();
		super.onPause();
	}

	private void releaseCamera() {
		if (camera == null) return;
		try {
			camera.setPreviewCallback(null);
			camera.stopPreview();
		} catch (Exception ignored) {
		}
		camera.release();
		camera = null;
	}

	private int dp(float value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private float sp(float value) {
		return value * getResources().getDisplayMetrics().scaledDensity;
	}

	private final class ScannerOverlayView extends View {
		private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint hintBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
		private final RectF scan = new RectF();
		private final Path mask = new Path();

		ScannerOverlayView() {
			super(QrScannerActivity.this);
			shade.setColor(0xE6000000);
			shade.setStyle(Paint.Style.FILL);
			border.setColor(0xE6FFFFFF);
			border.setStyle(Paint.Style.STROKE);
			border.setStrokeWidth(dp(2));
			hintBackground.setColor(0xCC101827);
			hintBackground.setStyle(Paint.Style.FILL);
			hintPaint.setColor(Color.WHITE);
			hintPaint.setTextSize(sp(16));
			hintPaint.setTextAlign(Paint.Align.LEFT);
			setContentDescription(getString(R.string.oauth_scan_hint));
		}

		@Override
		protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
			super.onSizeChanged(width, height, oldWidth, oldHeight);
			float side = Math.min(
					Math.min(width - dp(48), height - dp(160)),
					dp(360)
			);
			side = Math.max(dp(160), side);
			float left = (width - side) / 2f;
			float top = (height - side) / 2f;
			scan.set(left, top, left + side, top + side);
		}

		float scanSize() {
			return scan.width() > 0 ? scan.width() : dp(240);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			float radius = dp(24);
			mask.reset();
			mask.setFillType(Path.FillType.EVEN_ODD);
			mask.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
			mask.addRoundRect(scan, radius, radius, Path.Direction.CW);
			canvas.drawPath(mask, shade);
			canvas.drawRoundRect(scan, radius, radius, border);
			drawHint(canvas);
		}

		private void drawHint(Canvas canvas) {
			int maxWidth = Math.max(dp(180), Math.min(Math.round(scan.width()), getWidth() - dp(48)));
			StaticLayout hint = new StaticLayout(
					getString(R.string.oauth_scan_hint),
					hintPaint,
					maxWidth - dp(28),
					Layout.Alignment.ALIGN_CENTER,
					1f,
					0f,
					false
			);
			float boxWidth = maxWidth;
			float boxHeight = hint.getHeight() + dp(24);
			float left = (getWidth() - boxWidth) / 2f;
			float top = Math.min(scan.bottom + dp(22), getHeight() - boxHeight - dp(20));
			RectF box = new RectF(left, top, left + boxWidth, top + boxHeight);
			canvas.drawRoundRect(box, dp(14), dp(14), hintBackground);
			canvas.save();
			canvas.translate(left + dp(14), top + dp(12));
			hint.draw(canvas);
			canvas.restore();
		}
	}
}
