package com.example.sendit.domain

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual video opened through Android media system and runs MediaPipe. (Uses sample video for test) */
@RunWith(AndroidJUnit4::class)
class PoseExtractionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    // Checks a real video keeps its timestamps, detects the climber and records the final blank frame as no pose.
    @Test
    fun extract_contentVideoPreservesTimestampsAndDetectsPersonThenMissingPose() = runBlocking {
        withTestVideo("pose_sequence.mp4") { uri ->
            val frames = PoseExtractor(context).extract(
                PoseExtractor.VideoInput(uri.toString(), "fixture-attempt")
            )

            assertEquals(FIXTURE_TIMESTAMPS_MS, frames.map { it.timestamp })
            assertEquals(frames.indices.toList(), frames.map { it.frameIndex })
            assertTrue(frames.all { it.attemptId == "fixture-attempt" })
            assertTrue("Fixture's person must be detected", frames.take(PERSON_FRAME_COUNT).any {
                it.coordinates.size == EXPECTED_COORDINATE_COUNT
            })
            assertTrue("Final black frame must remain a missing pose", frames.last().coordinates.isEmpty())
            frames.filter { it.coordinates.isNotEmpty() }.forEach { frame ->
                assertEquals(EXPECTED_COORDINATE_COUNT, frame.coordinates.size)
                // Each group contains x, y, z, visibility and presence for one landmark.
                frame.coordinates.toList().chunked(VALUES_PER_LANDMARK).forEach { landmark ->
                    assertTrue(landmark.take(3).all { it.isFinite() })
                    assertTrue(landmark.drop(3).all { it.isNaN() || it in 0f..1f })
                }
            }
            // Extracting a video must not delete or modify its original content.
            val expected = instrumentation.context.assets.open("pose_sequence.mp4").use { it.readBytes() }
            val actual = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertTrue(expected.contentEquals(actual))
        }
    }

    // Checks the video's 90-degree rotation is applied once and the frame keeps its timestamp.
    @Test
    fun frameReader_appliesVideoRotationOnceAndPreservesTimestamp() = runBlocking {
        withTestVideo("pose_sequence_rotated.mp4") { uri ->
            VideoFrameReader.open(context, uri).use { reader ->
                requireNotNull(reader.next()).use { frame ->
                    // Encoded 640x480 with a 90-degree display transform.
                    assertEquals(480, frame.bitmap.width)
                    assertEquals(640, frame.bitmap.height)
                    assertEquals(0L, frame.timestampMs)
                }
            }
        }
    }

    // Checks a missing video causes an error instead of returning an empty successful result.
    @Test
    fun extract_unreadableVideoFailsInsteadOfReturningSuccessfulEmptyResult() {
        val missingFile = java.io.File(context.cacheDir, "missing-${UUID.randomUUID()}.mp4")

        assertThrows(IOException::class.java) {
            runBlocking {
                PoseExtractor(context).extract(
                    PoseExtractor.VideoInput(Uri.fromFile(missingFile).toString(), "missing-attempt")
                )
            }
        }
    }

    // Copies a test video into phone storage, runs the test, then removes that copy.
    private suspend fun withTestVideo(asset: String, block: suspend (Uri) -> Unit) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "sendit-test-${UUID.randomUUID()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SendItTests")
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        try {
            instrumentation.context.assets.open(asset).use { input ->
                requireNotNull(resolver.openOutputStream(uri)).use { input.copyTo(it) }
            }
            assertNotNull(resolver.getType(uri))
            block(uri)
        } finally {
            // Only remove the temporary entry created by this test.
            resolver.delete(uri, null, null)
        }
    }

    private companion object {
        val FIXTURE_TIMESTAMPS_MS = listOf(0L, 80L, 200L, 320L, 500L, 720L, 900L, 1080L, 1260L)
        const val PERSON_FRAME_COUNT = 6
        const val VALUES_PER_LANDMARK = 5
        const val EXPECTED_COORDINATE_COUNT = 33 * VALUES_PER_LANDMARK
    }
}
