package rs.ove.crypt.proto;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** JPEG/PNG/WebP decoder backed by mst5-client, including EXIF orientation and limits. */
public final class Mst5ImageDecoder {
	private static final long MAX_PIXELS = 4L * 1024L * 1024L;

	private Mst5ImageDecoder() {}

	public static Bitmap decode(ParcelFileDescriptor descriptor, int maxSide) throws IOException {
		if (descriptor == null) throw new IOException("image descriptor is unavailable");
		return decodeIntoBitmap(descriptor.getFd(), null, maxSide);
	}

	public static Bitmap decode(byte[] encoded, int maxSide) throws IOException {
		return decodeIntoBitmap(-1, encoded, maxSide);
	}

	private static Bitmap decodeIntoBitmap(int fd, byte[] encoded, int maxSide) throws IOException {
		int safeSide = Math.max(1, maxSide);
		long requestedPixels = Math.min(MAX_PIXELS, (long) safeSide * (long) safeSide);
		ByteBuffer pixels = ByteBuffer.allocateDirect((int) (requestedPixels * 4L));
		pixels.order(ByteOrder.nativeOrder());
		long dimensions = fd >= 0
				? NativeMst5.decodeImageFd(fd, safeSide, requestedPixels, pixels)
				: NativeMst5.decodeImage(encoded, safeSide, requestedPixels, pixels);
		int width = (int) (dimensions >>> 32);
		int height = (int) dimensions;
		if (width <= 0 || height <= 0 || (long) width * height > requestedPixels) {
			throw new IOException("mst5-client returned invalid image dimensions");
		}
		pixels.position(0);
		pixels.limit(width * height * 4);
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.copyPixelsFromBuffer(pixels);
		return bitmap;
	}
}
