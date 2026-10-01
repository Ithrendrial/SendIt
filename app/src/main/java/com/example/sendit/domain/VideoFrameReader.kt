package com.example.sendit.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads frames without assuming a fixed frame rate. */
internal class VideoFrameReader private constructor(
    private val retriever: MediaMetadataRetriever,
    private val presentationTimesUs: List<Long>
) : Closeable {
    class Frame(val timestampMs: Long, val bitmap: Bitmap) : Closeable {
        // Frees the frame's image memory once it is no longer needed.
        override fun close() = bitmap.recycle()
    }

    val totalFrames: Int get() = presentationTimesUs.size

    private var index = 0

    // Reads the next image at its video timestamp. Returns null at the end of the video.
    fun next(): Frame? {
        if (index == presentationTimesUs.size) return null
        val timeUs = presentationTimesUs[index++]
        // Android applies the video's display rotation here.
        val decoded = retriever.getScaledFrameAtTime(
            timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
            MAX_FRAME_DIMENSION_PX, MAX_FRAME_DIMENSION_PX
        ) ?: throw IOException("Cannot decode video frame at ${timeUs / MICROSECONDS_PER_MILLISECOND} ms")
        // MediaPipe needs ARGB pixels, so convert the image only if needed.
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
            try {
                decoded.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IOException("Cannot convert video frame to ARGB")
            } finally {
                decoded.recycle()
            }
        }
        // Android reads in microseconds and our pose frames store milliseconds, so convert.
        return Frame(timeUs / MICROSECONDS_PER_MILLISECOND, bitmap)
    }

    // Releases Android's video reader when processing ends.
    override fun close() = retriever.release()

    companion object {
        // Bounds inference image memory while preserving the source aspect ratio.
        private const val MAX_FRAME_DIMENSION_PX = 640
        private const val MICROSECONDS_PER_MILLISECOND = 1_000L
        private const val VIDEO_MIME_PREFIX = "video/"

        // Opens the selected video and gets its timestamps ready for reading frames.
        suspend fun open(context: Context, uri: Uri): VideoFrameReader {
            require(uri.scheme == "content" || uri.scheme == "file") { "A local video URI is required" }
            val retriever = MediaMetadataRetriever()
            try {
                val timestamps = readPresentationTimes(context, uri)
                currentCoroutineContext().ensureActive()
                retriever.setDataSource(context, uri)
                return VideoFrameReader(retriever, timestamps)
            } catch (failure: Exception) {
                try {
                    retriever.release()
                } catch (cleanupFailure: Exception) {
                    failure.addSuppressed(cleanupFailure)
                }
                if (failure is CancellationException || failure is IOException) throw failure
                throw IOException("Cannot open the selected video", failure)
            }
        }

        // Gets the actual frame times, as the gap between frames is not always the same.
        private suspend fun readPresentationTimes(context: Context, uri: Uri): List<Long> {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                // A file can contain audio as well, so select its video track.
                val videoTrack = (0 until extractor.trackCount).firstOrNull { track ->
                    extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)
                        ?.startsWith(VIDEO_MIME_PREFIX) == true
                } ?: throw IOException("Selected file contains no video track")
                extractor.selectTrack(videoTrack)
                val timestamps = mutableListOf<Long>()
                while (extractor.sampleTrackIndex >= 0) {
                    currentCoroutineContext().ensureActive()
                    // Negative preroll timestamps are not displayed video frames.
                    if (extractor.sampleTime >= 0) timestamps += extractor.sampleTime
                    if (!extractor.advance()) break
                }
                // Compressed frames may be stored out of playback order. Sort them and
                // keep one per millisecond, as MediaPipe needs increasing timestamps.
                return timestamps.sorted().distinctBy { it / MICROSECONDS_PER_MILLISECOND }
            } finally {
                extractor.release()
            }
        }
    }
}
