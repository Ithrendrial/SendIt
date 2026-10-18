package com.example.sendit.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

/** Sequential fallback for videos whose timestamp retriever repeats keyframes. */
internal class SequentialVideoDecoder private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    private val maxDimension: Int,
    private val rotation: Int,
    private val coroutineContext: CoroutineContext
) : Closeable {
    private var inputEnded = false
    private val outputInfo = MediaCodec.BufferInfo()

    fun read(timeUs: Long): Bitmap {
        val deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            feedInput()
            val slot = codec.dequeueOutputBuffer(outputInfo, BUFFER_WAIT_US)
            if (slot < 0) continue // Includes output-format changes; read codec.outputFormat below.
            try {
                if (outputInfo.size > 0 && outputInfo.presentationTimeUs >= timeUs) {
                    if (outputInfo.presentationTimeUs != timeUs) {
                        throw IOException("Decoded frame does not match requested timestamp")
                    }
                    val image = codec.getOutputImage(slot)
                        ?: throw IOException("Decoder did not supply a readable image")
                    return image.use { toBitmap(it) }
                }
                if (outputInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    throw IOException("Video ended before requested frame")
                }
            } finally {
                codec.releaseOutputBuffer(slot, false)
            }
        }
        throw IOException("Timed out decoding video frame")
    }

    private fun feedInput() {
        if (inputEnded) return
        val slot = codec.dequeueInputBuffer(BUFFER_WAIT_US)
        if (slot < 0) return
        val buffer = requireNotNull(codec.getInputBuffer(slot))
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputEnded = true
        } else {
            codec.queueInputBuffer(slot, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun toBitmap(image: Image): Bitmap {
        if (image.format != ImageFormat.YUV_420_888) throw IOException("Unsupported decoded pixel format")
        val crop = image.cropRect
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(crop.width(), crop.height()))
        val width = (crop.width() * scale).toInt().coerceAtLeast(1)
        val height = (crop.height() * scale).toInt().coerceAtLeast(1)
        val planes = image.planes
        val buffers = planes.map { it.buffer }
        fun sample(plane: Int, x: Int, y: Int): Int {
            val position = buffers[plane].position() + y * planes[plane].rowStride + x * planes[plane].pixelStride
            return buffers[plane].get(position).toInt() and 0xff
        }
        val format = codec.outputFormat
        val fullRange = format.getInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED) == MediaFormat.COLOR_RANGE_FULL
        // Standard YCbCr coefficients; respect the decoder's range and colour standard.
        val (redWeight, blueWeight) = when (format.getInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)) {
            MediaFormat.COLOR_STANDARD_BT709 -> 0.2126 to 0.0722
            MediaFormat.COLOR_STANDARD_BT2020 -> 0.2627 to 0.0593
            else -> 0.299 to 0.114
        }
        val greenWeight = 1 - redWeight - blueWeight
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            coroutineContext.ensureActive()
            val sourceY = crop.top + y * crop.height() / height
            for (x in 0 until width) {
                val sourceX = crop.left + x * crop.width() / width
                val luma = sample(0, sourceX, sourceY)
                val u = sample(1, sourceX / 2, sourceY / 2) - CHROMA_MIDPOINT
                val v = sample(2, sourceX / 2, sourceY / 2) - CHROMA_MIDPOINT
                val luminance = if (fullRange) luma.toDouble() else (luma - LIMITED_BLACK) * 255.0 / LIMITED_LUMA_RANGE
                val chromaScale = if (fullRange) 1.0 else 255.0 / LIMITED_CHROMA_RANGE
                val red = luminance + 2 * (1 - redWeight) * v * chromaScale
                val blue = luminance + 2 * (1 - blueWeight) * u * chromaScale
                val green = luminance - 2 * (blueWeight * (1 - blueWeight) * u + redWeight * (1 - redWeight) * v) * chromaScale / greenWeight
                pixels[y * width + x] = Color.rgb(red.toInt().coerceIn(0, 255), green.toInt().coerceIn(0, 255), blue.toInt().coerceIn(0, 255))
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        try { codec.release() } finally { extractor.release() }
    }

    companion object {
        private const val BUFFER_WAIT_US = 10_000L
        private const val DECODE_TIMEOUT_MS = 10_000L
        private const val CHROMA_MIDPOINT = 128
        private const val LIMITED_BLACK = 16
        private const val LIMITED_LUMA_RANGE = 219
        private const val LIMITED_CHROMA_RANGE = 224

        fun open(context: Context, uri: Uri, maxDimension: Int, rotation: Int, coroutineContext: CoroutineContext): SequentialVideoDecoder {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: throw IOException("Selected file contains no video track")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                format.setInteger(MediaFormat.KEY_ROTATION, 0) // Rotation is applied to the final bitmap once.
                codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
                codec.configure(format, null, null, 0)
                codec.start()
                return SequentialVideoDecoder(extractor, codec, maxDimension, rotation, coroutineContext)
            } catch (failure: Exception) {
                try { codec?.release() } finally { extractor.release() }
                throw failure
            }
        }
    }
}
