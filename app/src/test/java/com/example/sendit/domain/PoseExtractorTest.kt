package com.example.sendit.domain

import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.IOException
import java.util.Optional
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test


class PoseExtractorTest {
    // Checks the pose extractor opens the selected video's uri and makes sure each returned frame is from the correct attempt.
    @Test
    fun extract_opensSelectedVideoAndAssociatesFramesWithAttempt() = runBlocking {
        val session = FakeVideoSession(listOf(result(0L), result(47L)))
        var openedUri: String? = null
        val extractor = PoseExtractor(openVideo = { uri ->
            openedUri = uri
            session
        })

        val frames = extractor.extract(video())

        assertEquals(VIDEO_URI, openedUri)
        assertEquals(2, frames.size)
        assertTrue(frames.all { it.attemptId == ATTEMPT_ID })
        assertTrue(frames.all { it.id.isNotBlank() })
        assertEquals(frames.size, frames.map { it.id }.toSet().size)
    }

    // Checks the frames keep their original, sequential timestamps
    @Test
    fun extract_preservesIrregularVideoTimestampsAndSequentialFrameIndices() = runBlocking {
        val timestamps = listOf(0L, 47L, 133L, 205L)
        val session = FakeVideoSession(timestamps.map { result(it) })

        val frames = PoseExtractor(openVideo = { session }).extract(video())

        assertEquals(timestamps, frames.map { it.timestamp })
        assertEquals(listOf(0, 1, 2, 3), frames.map { it.frameIndex })
    }

    // Checks that all landmarks are stored in their original order (with x,y,z coordinates, visibility and presence).
    @Test
    fun extract_preservesAllLandmarksInXyzVisibilityPresenceOrder() = runBlocking {
        val pose = landmarks()
        val session = FakeVideoSession(listOf(result(0L, pose)))

        val frame = PoseExtractor(openVideo = { session }).extract(video()).single()

        val expected = pose.flatMap {
            listOf(it.x(), it.y(), it.z(), it.visibility().get(), it.presence().get())
        }.toFloatArray()
        assertEquals(LANDMARK_COUNT * VALUES_PER_LANDMARK, frame.coordinates.size)
        assertArrayEquals(expected, frame.coordinates, EXACT_COPY_TOLERANCE)
    }

    // Checks a frame with no detectedpose keeps its timestamp but has an empty coordinate array.
    @Test
    fun extract_keepsMissingPoseBetweenDetectionsWithoutRepeatingPreviousPose() = runBlocking {
        val session = FakeVideoSession(listOf(result(0L), result(80L, null), result(160L)))

        val frames = PoseExtractor(openVideo = { session }).extract(video())

        assertEquals(listOf(0L, 80L, 160L), frames.map { it.timestamp })
        assertEquals(listOf(0, 1, 2), frames.map { it.frameIndex })
        assertFalse(frames.first().coordinates.isEmpty())
        assertTrue(frames[1].coordinates.isEmpty())
        assertFalse(frames.last().coordinates.isEmpty())
    }

    // Checks low confidence values are kept, missing values must be stored as NaN.
    @Test
    fun extract_preservesLowConfidenceAndMarksUnavailableConfidenceAsUnknown() = runBlocking {
        val pose = landmarks().toMutableList()
        pose[0] = NormalizedLandmark.create(
            0.25f, 0.5f, -0.1f, Optional.of(0.05f), Optional.of(0.1f)
        )
        pose[1] = NormalizedLandmark.create(0.3f, 0.6f, -0.2f)
        val session = FakeVideoSession(listOf(result(0L, pose)))

        val coordinates = PoseExtractor(openVideo = { session })
            .extract(video()).single().coordinates

        assertArrayEquals(
            floatArrayOf(0.25f, 0.5f, -0.1f, 0.05f, 0.1f),
            coordinates.copyOfRange(0, VALUES_PER_LANDMARK),
            EXACT_COPY_TOLERANCE
        )
        assertTrue(coordinates[VALUES_PER_LANDMARK + VISIBILITY_OFFSET].isNaN())
        assertTrue(coordinates[VALUES_PER_LANDMARK + PRESENCE_OFFSET].isNaN())
    }

    // Checks video session is closed once after pose extraction
    @Test
    fun extract_closesVideoSessionAfterSuccess() = runBlocking {
        val session = FakeVideoSession(listOf(result(0L)))

        PoseExtractor(openVideo = { session }).extract(video())

        assertEquals(1, session.closeCount)
    }

    // Checks that a video session with no samples must produce an empty list. 
    @Test
    fun extract_returnsEmptyListWhenVideoHasNoSamplesAndClosesSession() = runBlocking {
        val session = FakeVideoSession(emptyList())

        val frames = PoseExtractor(openVideo = { session }).extract(video())

        assertTrue(frames.isEmpty())
        assertEquals(1, session.closeCount)
    }

    // Checks error opening a selected video is passed back to caller (not processed)
    @Test
    fun extract_propagatesUnreadableVideoErrorInsteadOfReportingNoPose() {
        val failure = IOException("Selected video cannot be opened")
        val extractor = PoseExtractor(openVideo = { throw failure })

        val actual = assertThrows(IOException::class.java) {
            runBlocking { extractor.extract(video()) }
        }

        assertSame(failure, actual)
    }

    // Checks a processing error midway is passed to caller and session closed. No partial results.
    @Test
    fun extract_propagatesMidVideoFailureAndClosesSession() {
        val failure = IOException("Video decoding failed")
        val session = FakeVideoSession(listOf(result(0L)), terminalFailure = failure)
        val extractor = PoseExtractor(openVideo = { session })

        val actual = assertThrows(IOException::class.java) {
            runBlocking { extractor.extract(video()) }
        }

        // A partial sequence must not be returned as a successfully processed video.
        assertSame(failure, actual)
        assertEquals(1, session.closeCount)
    }

    // Cheks a cancellation midway is passed to caller and session closed. No partial results.
    @Test
    fun extract_propagatesCancellationAndClosesSession() {
        val cancellation = CancellationException("Processing cancelled")
        val session = FakeVideoSession(listOf(result(0L)), terminalFailure = cancellation)
        val extractor = PoseExtractor(openVideo = { session })

        assertThrows(CancellationException::class.java) {
            runBlocking { extractor.extract(video()) }
        }

        assertEquals(1, session.closeCount)
    }

    // Checks that a pose extraction with out of order timestamps is rejected and session closed.
    @Test
    fun extract_rejectsOutOfOrderTimestampsAndClosesSession() {
        val session = FakeVideoSession(listOf(result(100L), result(50L)))
        val extractor = PoseExtractor(openVideo = { session })

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { extractor.extract(video()) }
        }

        assertEquals(1, session.closeCount)
    }

    // Checks that a detected pose with fewer than expected landmarks is rejected and session closed.
    @Test
    fun extract_rejectsMalformedPoseInsteadOfSavingMisalignedCoordinates() {
        val session = FakeVideoSession(listOf(result(0L, landmarks().dropLast(1))))
        val extractor = PoseExtractor(openVideo = { session })

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { extractor.extract(video()) }
        }

        assertEquals(1, session.closeCount)
    }

    private fun video() = PoseExtractor.VideoInput(uri = VIDEO_URI, attemptId = ATTEMPT_ID)

    private fun landmarks(): List<NormalizedLandmark> = List(LANDMARK_COUNT) { index ->
        val fraction = index.toFloat() / LANDMARK_COUNT
        NormalizedLandmark.create(
            fraction, 1f - fraction, -fraction,
            Optional.of(0.9f), Optional.of(0.8f)
        )
    }

    private fun result(
        timestampMs: Long,
        pose: List<NormalizedLandmark>? = landmarks()
    ): PoseLandmarkerResult = object : PoseLandmarkerResult() {
        override fun timestampMs() = timestampMs
        override fun landmarks(): List<List<NormalizedLandmark>> =
            if (pose == null) emptyList() else listOf(pose)
        override fun worldLandmarks(): List<List<Landmark>> = emptyList()
        override fun segmentationMasks(): Optional<List<MPImage>> = Optional.empty()
    }

    private class FakeVideoSession(
        private val results: List<PoseLandmarkerResult>,
        private val terminalFailure: Exception? = null
    ) : PoseExtractor.VideoSession {
        private var index = 0
        var closeCount = 0
            private set

        override fun next(): PoseLandmarkerResult? {
            if (index < results.size) return results[index++]
            terminalFailure?.let { throw it }
            return null
        }

        override fun close() {
            closeCount++
        }
    }

    private companion object {
        const val VIDEO_URI = "content://com.example.sendit.test/videos/climb"
        const val ATTEMPT_ID = "attempt-pose-test"
        const val LANDMARK_COUNT = 33
        const val VALUES_PER_LANDMARK = 5
        const val VISIBILITY_OFFSET = 3
        const val PRESENCE_OFFSET = 4
        const val EXACT_COPY_TOLERANCE = 0f
    }
}
