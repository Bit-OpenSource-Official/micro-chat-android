package rs.ove.crypt.proto

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Mst5ImageDecoder {
    private const val MAX_PIXELS = 4L * 1024L * 1024L
    @JvmStatic fun decode(descriptor: ParcelFileDescriptor?, maxSide: Int): Bitmap {
        descriptor ?: throw IOException("image descriptor is unavailable")
        return decodeIntoBitmap(descriptor.fd, null, maxSide)
    }
    @JvmStatic fun decode(encoded: ByteArray?, maxSide: Int): Bitmap = decodeIntoBitmap(-1, encoded, maxSide)
    @JvmStatic fun prepareWebp(encoded: ByteArray?, maxSide: Int, square: Boolean): ByteArray = NativeMst5.prepareWebp(encoded, maxOf(1, maxSide), square)
    private fun decodeIntoBitmap(fd: Int, encoded: ByteArray?, maxSide: Int): Bitmap {
        val safeSide = maxOf(1, maxSide)
        val requestedPixels = minOf(MAX_PIXELS, safeSide.toLong() * safeSide)
        val pixels = ByteBuffer.allocateDirect((requestedPixels * 4).toInt()).order(ByteOrder.nativeOrder())
        val dimensions = if (fd >= 0) NativeMst5.decodeImageFd(fd, safeSide, requestedPixels, pixels) else NativeMst5.decodeImage(encoded, safeSide, requestedPixels, pixels)
        val width = (dimensions ushr 32).toInt()
        val height = dimensions.toInt()
        if (width <= 0 || height <= 0 || width.toLong() * height > requestedPixels) throw IOException("mst5-client returned invalid image dimensions")
        pixels.position(0); pixels.limit(width * height * 4)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(pixels) }
    }
}
