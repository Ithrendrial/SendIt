package com.example.sendit.lightweight

import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import android.system.Os
import android.view.WindowManager
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.graphics.Rect
import android.graphics.Point
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import androidx.work.WorkManager
import com.example.sendit.MainActivity
import com.example.sendit.SendItApplication
import com.example.sendit.data.AppDatabase
import com.example.sendit.domain.PoseFrame
import com.example.sendit.ui.viewmodels.AttemptFormViewModel
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.rules.TestWatcher
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EvaluationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (app.applicationContext as SendItApplication).repository
    private val videos get() = args.getString("videos", "sendit-eval-first.mp4")!!.split(",")
    private val repetitions get() = args.getString("videoRepetitions", "5")!!.toInt()
    private val interactions get() = args.getString("interactionRepetitions", "30")!!.toInt()
    private val evidence get() = File(app.filesDir, "evaluation-last.json")
    private val footprintEvidence get() = File(app.filesDir, "evaluation-footprint.json")
    private val output get() = File(app.getExternalFilesDir(null), "evaluation.jsonl")
    private val fixtures = mutableMapOf<String, Uri>()
    private lateinit var activity: ActivityScenario<MainActivity>
    private lateinit var form: AttemptFormViewModel
    private var processingTrials = 0
    @get:Rule val outcome = object : TestWatcher() {
        override fun succeeded(description: Description) = record("test_method", "pass",
            extra = JSONObject().put("method", description.methodName))
        override fun failed(error: Throwable, description: Description) = record("test_method", "error",
            extra = JSONObject().put("method", description.methodName).put("error", error.stackTraceToString()))
    }
    private val database by lazy { Room.databaseBuilder(app, AppDatabase::class.java, "sendit.db")
        .addMigrations(AppDatabase.MIGRATION_1_2).build() }

    @Before fun open() {
        device.wakeUp()
        device.executeShellCommand("cmd statusbar collapse") // Untimed setup: restore the app's input focus.
        activity = ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            form = ViewModelProvider(it)[AttemptFormViewModel::class.java]
        }
        device.waitForIdle()
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val display = DisplayMetrics()
        activity.onActivity { host ->
            host.windowManager.defaultDisplay.getRealMetrics(display)
            val configuration = host.resources.configuration
            record("configuration", "recorded", extra = JSONObject()
                .put("android", Build.VERSION.RELEASE).put("processor", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE)
                .put("physical_ram_bytes", memory.totalMem).put("display_width_px", display.widthPixels)
                .put("display_height_px", display.heightPixels).put("density_dpi", display.densityDpi)
                .put("window_width_dp", host.window.decorView.width / display.density)
                .put("refresh_rate_hz", host.windowManager.defaultDisplay.refreshRate)
                .put("font_scale", configuration.fontScale).put("locale", configuration.locales.toLanguageTags())
                .put("debuggable", app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                .put("power_save", app.getSystemService(PowerManager::class.java).isPowerSaveMode)
                .put("thermal_status", app.getSystemService(PowerManager::class.java).currentThermalStatus)
                .put("keyboard", Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
                .put("measured_character_input", "single virtual-hardware key; software keyboard hidden")
                .put("window_animation_scale", Settings.Global.getFloat(app.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f))
                .put("transition_animation_scale", Settings.Global.getFloat(app.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f))
                .put("animator_duration_scale", Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)))
        }
    }
    @After fun close() {
        if (device.currentPackageName == app.packageName)
            device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "evaluation-ui.xml"))
        activity.close(); database.close()
    }

    @Test fun aProcessing() {
        val completed = mutableListOf<Long>()
        // Slow phones can run one video's five trials per Test Lab execution.
        check(args.getString("phase") != "main" || (videos.size in listOf(1, 3) && videos.distinct().size == videos.size))
        for (video in videos) for (repetition in 1..repetitions) {
            backToForm()
            WorkManager.getInstance(app).pruneWork().result.get()
            database.clearAllTables()
            inventory("initialised_empty")
            process(video, repetition)?.let(completed::add)
        }
        check(args.getString("phase") != "main" || videos.map(::sourceHash).toSet().size == videos.size)
        check(completed.size == videos.size * repetitions) {
            "Not all trials completed; inspect the raw observations. Native timings are evaluated after export."
        }
    }

    @Test fun bReload() {
        check(evidence.exists()) { "Run aProcessing first, preserving app data" }
        val expected = JSONObject(evidence.readText())
        check(expected.getInt("pid") != Process.myPid()) { "Reload requires a real process restart" }
        val id = expected.getString("id")
        await { form.completedAttemptId == id }
        check(fingerprint(id) == expected.getString("fingerprint"))
        waitForResult()
        checkPlayback()
        inventory("after_restart")
        record("reload", "pass")
    }

    @Test fun zLatencyCalibration() {
        FrameProbe("calibration").use { probe ->
            for (delay in listOf(0L, 250L)) {
                lateinit var dialog: Dialog
                val buttonId = View.generateViewId()
                val feedbackId = View.generateViewId()
                activity.onActivity { host ->
                    val feedback = TextView(host).apply { id = feedbackId; text = "Waiting" }
                    val button = Button(host).apply {
                        id = buttonId; text = "Change feedback"
                        setOnClickListener { Handler(Looper.getMainLooper()).postDelayed({ feedback.text = "Ready" }, delay) }
                    }
                    dialog = Dialog(host).apply {
                        window!!.setTitle("SendItCalibration$delay")
                        setContentView(LinearLayout(host).apply {
                            orientation = LinearLayout.VERTICAL; addView(button); addView(feedback)
                        }); show()
                    }
                }
                device.waitForIdle()
                val bounds = Rect()
                activity.onActivity {
                    val button = dialog.findViewById<Button>(buttonId)
                    val location = IntArray(2)
                    button.getLocationOnScreen(location)
                    bounds.set(location[0], location[1], location[0] + button.width, location[1] + button.height)
                }
                try {
                    probe.sample("calibration_$delay", { root -> root.findViewById<TextView>(feedbackId)?.text == "Ready" },
                        { probe.tap(bounds.centerX(), bounds.centerY()) }, JSONObject().put("expected_delay_ms", delay))
                } finally { activity.onActivity { dialog.dismiss() } }
            }
        }
    }

    // Fast diagnostic for untimed setup; these repetitions are not P2 measurements.
    @Test fun yRoutePreparation() {
        backToForm()
        repeat(interactions) { check(text(newRoute()).exists()) }
        record("route_preparation", "pass", extra = JSONObject().put("trials", interactions))
    }

    @Test fun cInteractions() {
        // One controlled saved result per suite; every navigation trial reuses it.
        backToForm(); WorkManager.getInstance(app).pruneWork().result.get(); database.clearAllTables()
        check(process(videos.first(), 1, "setup") != null)
        backToForm()
        val route = runBlocking { repository.observeRoutes().first().first().name }
        FrameProbe("interactions").use { probe ->
            repeat(interactions) { repetition ->
                val date = LocalDate.now()
                tap(probe, "open_route", repetition, center(scrollDescription("Route selector"))) {
                    FrameProbe.popup(it) && FrameProbe.text(it, "+ New Route")
                }
                tap(probe, "select_route", repetition, center(text(route))) {
                    !FrameProbe.popup(it) && FrameProbe.text(it, route)
                }
                probe.sample("type_route_name_${repetition + 1}", { FrameProbe.editable(it, "New route name", "a") },
                    probe::character, interaction("type_route_name", repetition).put("input", "key"), prepare = {
                        description("Route selector").click(); scrollText("+ New Route").click()
                        device.findObject(UiSelector().className("android.widget.EditText")).click()
                        hideKeyboard()
                    })
                addRoute()
                tap(probe, "open_grade", repetition, center(scrollText("VB"))) {
                    FrameProbe.popup(it) && FrameProbe.text(it, "V0")
                }
                tap(probe, "select_grade", repetition, center(text("V0"))) {
                    !FrameProbe.popup(it) && FrameProbe.text(it, "V0")
                }
                tap(probe, "open_date", repetition, center(scrollText(dateText(date)))) {
                    FrameProbe.datePicker(it)?.isShown == true
                }
                val day = if (date.dayOfMonth == 15) 14 else 15
                tap(probe, "select_date", repetition, center(device.findObject(UiSelector().text(day.toString())).visibleBounds)) {
                    FrameProbe.datePicker(it)?.dayOfMonth == day
                }
                tap(probe, "confirm_date", repetition,
                    center(device.findObject(UiSelector().resourceId("android:id/button1")).visibleBounds)) {
                    FrameProbe.text(it, dateText(date.withDayOfMonth(day)))
                }
                scrollText("Sent").click(); device.waitForIdle()
                tap(probe, "select_outcome", repetition, center(scrollText("Fall"))) { FrameProbe.selected(it, "Fall") }
                for (field in listOf("Location", "Notes")) {
                    scrollDescription(field).click(); hideKeyboard()
                    val action = "type_${field.lowercase()}"
                    probe.sample("${action}_${repetition + 1}", { FrameProbe.editable(it, field, "a") },
                        probe::character, interaction(action, repetition).put("input", "key"))
                }
                // Restore the saved result outside the navigation measurement, preserving its real data.
                activity.close(); open()
                await { form.completedAttemptId != null }
                waitForResult()
                tap(probe, "back_to_form", repetition, center(description("Back"))) {
                    FrameProbe.text(it, "New Attempt") && FrameProbe.description(it, "Route selector")
                }
            }
        }
        for (action in InteractionActions) {
            record("interaction", "not_applicable", extra = JSONObject().put("action", action).put("condition", "processing")
                .put("reason", "The app disables form inputs during processing; the results screen is not yet available"))
        }
    }

    @Test fun dInvalidVideo() {
        backToForm()
        WorkManager.getInstance(app).pruneWork().result.get()
        selectVideo("sendit-eval-invalid.mp4")
        val original = sourceHash("sendit-eval-invalid.mp4")
        newRoute()
        scrollText("UPLOAD & ANALYSE").click()
        await(SafetyTimeoutMs) { form.errorMessage != null }
        check(form.completedAttemptId == null)
        val failed = WorkManager.getInstance(app).getWorkInfosForUniqueWork("process_attempt").get().first { it.state == androidx.work.WorkInfo.State.FAILED }
        check(runBlocking { repository.getAttempt(failed.id.toString()) }?.videoAspectRatio == null)
        check(sourceHash("sendit-eval-invalid.mp4") == original)
        check(!File(app.noBackupFilesDir, "processing/${failed.id}.mp4").exists())
        inventory("after_failure")
        record("failure_cleanup", "pass")
    }

    @Test fun eFootprint() {
        backToForm(); WorkManager.getInstance(app).pruneWork().result.get(); database.clearAllTables()
        val baseline = retainedBytes()
        inventory("footprint_baseline")
        var sourceBytes = 0L
        val saved = JSONArray()
        repeat(FootprintAttempts) { index ->
            backToForm()
            val video = videos[index % videos.size]
            check(process(video, index + 1, "footprint_processing") != null)
            saved.put(JSONObject(evidence.readText()))
            sourceBytes += app.contentResolver.openFileDescriptor(fixtures.getValue(video), "r")!!.use { it.statSize }
            if (index + 1 in listOf(1, 5, 10)) {
                val retained = retainedBytes()
                val growth = retained - baseline
                record("storage", "measured", video, index + 1, extra = JSONObject().put("retained_growth_bytes", growth)
                    .put("baseline_bytes", baseline).put("retained_bytes", retained)
                    .put("source_bytes", sourceBytes).put("reduction_percent", 100.0 * (1.0 - growth.toDouble() / sourceBytes)))
                inventory("after_${index + 1}_attempts")
            }
        }
        footprintEvidence.writeText(saved.toString())
    }

    @Test fun fFootprintReload() {
        val saved = JSONArray(footprintEvidence.readText())
        check(saved.length() == FootprintAttempts)
        for (index in 0 until saved.length()) {
            val expected = saved.getJSONObject(index)
            check(expected.getInt("pid") != Process.myPid())
            check(fingerprint(expected.getString("id")) == expected.getString("fingerprint"))
        }
        await { form.completedAttemptId == saved.getJSONObject(saved.length() - 1).getString("id") }
        waitForResult()
        checkPlayback()
        inventory("footprint_after_restart")
        record("footprint_reload", "pass", extra = JSONObject().put("attempts", saved.length()))
    }

    // The host-side trace analysis uses the accepted-submission marker and actual presentation.
    // This method also records an explicitly labelled upper bound as diagnostic evidence.
    private fun process(video: String, repetition: Int, kind: String = "processing"): Long? {
        try {
            selectVideo(video)
            val routeName = newRoute()
            val original = sourceHash(video)
            val submit = scrollText("UPLOAD & ANALYSE")
            check(submit.isEnabled)
            val start = SystemClock.elapsedRealtime()
            if (kind == "processing") {
                val sample = "processing_${++processingTrials}"
                FrameProbe(sample).use { probe ->
                    var processingState: Any? = null
                    instrumentation.runOnMainSync {
                        Snapshot.observe(readObserver = { processingState = it }, writeObserver = null) { form.processingStatus }
                    }
                    var accepted = false
                    val observer = Snapshot.registerGlobalWriteObserver { state ->
                        if (state === processingState && form.processingStatus && !accepted) {
                            Trace.beginSection("SendItAccepted $sample"); Trace.endSection()
                            accepted = true
                        }
                    }
                    val point = center(submit)
                    try {
                        probe.sample(sample, { root -> FrameProbe.description(root, "Pose reconstruction") && FrameProbe.text(root, routeName) },
                            { probe.tap(point.x, point.y) }, JSONObject().put("kind", "processing").put("input", "submission")
                                .put("video", video).put("repetition", repetition).put("first_run_in_process", processingTrials == 1),
                            SafetyTimeoutMs, { form.errorMessage })
                    } finally { observer.dispose() }
                    check(accepted) { "Accepted-submission marker was not observed" }
                }
            } else {
                submit.click()
                await(SafetyTimeoutMs) { form.completedAttemptId != null || form.errorMessage != null }
            }
            check(form.errorMessage == null) { form.errorMessage.orEmpty() }
            val id = requireNotNull(form.completedAttemptId)
            waitForResult()
            val duration = SystemClock.elapsedRealtime() - start
            val saved = runBlocking { requireNotNull(repository.observeAttempt(id).first()) }
            check(saved.attempt.id == id && !saved.attempt.processingStatus && saved.attempt.videoAspectRatio!! > 0)
            val frames = saved.frames.sortedBy { it.frameIndex }
            check(frames.isNotEmpty() && frames.any { it.coordinates.isNotEmpty() })
            check(frames.withIndex().all { (i, frame) -> frame.attemptId == id && frame.frameIndex == i && frame.timestamp >= 0 &&
                (i == 0 || frame.timestamp > frames[i - 1].timestamp) &&
                (frame.coordinates.isEmpty() || frame.coordinates.size == PoseFrame.LANDMARK_COUNT * PoseFrame.VALUES_PER_LANDMARK) })
            check(frames.all { frame -> frame.coordinates.asList().chunked(PoseFrame.VALUES_PER_LANDMARK).all { landmark ->
                landmark.take(3).all(Float::isFinite) && landmark.drop(3).all { it.isNaN() || it in 0f..1f }
            } })
            check(saved.findings.all { it.attemptId == id && it.startFrameIndex in frames.indices &&
                it.endFrameIndex in frames.indices && it.startFrameIndex <= it.endFrameIndex })
            check(text(saved.route.name).exists())
            check(!File(app.noBackupFilesDir, "processing/$id.mp4").exists() && sourceHash(video) == original)
            check(app.getDatabasePath("sendit.db").canonicalPath.startsWith(app.dataDir.canonicalPath + "/"))
            evidence.writeText(JSONObject().put("id", id).put("pid", Process.myPid()).put("fingerprint", fingerprint(id)).toString())
            if (kind == "processing") inventory("after_success")
            record(kind, "completed", video, repetition, JSONObject().put("observed_upper_bound_ms", duration)
                .put("timing_status", if (kind == "processing") "requires_trace_analysis" else "diagnostic_upper_bound")
                .put("frames", frames.size).put("findings", saved.findings.size)
                .put("source_sha256", original).put("private_storage", true).put("temporary_removed", true))
            return duration
        } catch (failure: Throwable) {
            failure.printStackTrace()
            if (device.currentPackageName == app.packageName)
                device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "evaluation-ui.xml"))
            record(kind, if (form.processingStatus) "timeout" else "error", video, repetition,
                JSONObject().put("error", failure.stackTraceToString()).put("foreground_package", device.currentPackageName)
                    .put("selected_uri", form.selectedVideo).put("completed_id", form.completedAttemptId))
            if (failure is VirtualMachineError) throw failure
            check(!form.processingStatus) { "Unfinished processing: stop rather than collect invalid later trials" }
            if (device.currentPackageName?.endsWith(".documentsui") == true) device.pressBack()
            if (description("New route name").exists()) {
                hideKeyboard()
                device.pressBack()
                check(description("New route name").waitUntilGone(UiTimeoutMs))
            }
            return null
        }
    }

    private fun selectVideo(video: String) {
        val provider = Uri.parse("content://com.example.sendit.lightweight.test.fixtures")
        val uri = requireNotNull(app.contentResolver.call(provider, "video", video, null)?.getParcelable<Uri>("uri"))
        instrumentation.runOnMainSync { form.selectVideo(uri) }
        check(device.findObject(UiSelector().textContains(video)).waitForExists(UiTimeoutMs))
        fixtures[video] = requireNotNull(form.selectedVideo)
        if (video == "sendit-eval-invalid.mp4") return
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(app, form.selectedVideo)
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            check(kotlin.math.abs(duration - WorkloadMs) <= DurationToleranceMs) { "Expected a 40-second video: $video is $duration ms" }
            val extractor = MediaExtractor()
            val format = try {
                extractor.setDataSource(app, form.selectedVideo!!, null)
                (0 until extractor.trackCount).map(extractor::getTrackFormat)
                    .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            } finally { extractor.release() }
            record("fixture", "loaded", video, extra = JSONObject().put("duration_ms", duration)
                .put("width", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                .put("height", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                .put("fps", if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getNumber(MediaFormat.KEY_FRAME_RATE) else JSONObject.NULL)
                .put("video_codec_mime", format.getString(MediaFormat.KEY_MIME))
                .put("source_bytes", app.contentResolver.openFileDescriptor(fixtures.getValue(video), "r")!!.use { it.statSize })
                .put("source_sha256", sourceHash(video)))
        }
    }

    private fun newRoute(): String {
        scrollDescription("Route selector").click(); scrollText("+ New Route").click()
        val name = "Eval-${UUID.randomUUID().toString().take(8)}"
        device.findObject(UiSelector().className("android.widget.EditText")).setText(name)
        addRoute()
        return name
    }
    private fun addRoute() {
        // Untimed preparation: use the accessibility action so IME animation cannot move the tap target.
        device.waitForIdle()
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == "Add") return node
            return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::find) }
        }
        await {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
            var button = find(root) ?: return@await false
            while (!button.isClickable) button = button.parent ?: return@await false
            button.isEnabled && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        check(description("New route name").waitUntilGone(UiTimeoutMs))
    }
    private fun backToForm() {
        device.waitForIdle()
        if (form.completedAttemptId != null) {
            waitForResult()
            device.pressBack() // Untimed preparation also works with legacy long headings.
            await { form.completedAttemptId == null }
        }
    }
    private fun text(value: String) = device.findObject(UiSelector().text(value))
    private fun description(value: String) = device.findObject(UiSelector().description(value))
    private fun scrollText(value: String) = scroll(UiSelector().text(value))
    private fun scrollDescription(value: String) = scroll(UiSelector().description(value))
    private fun scroll(selector: UiSelector): UiObject {
        hideKeyboard()
        if (!device.findObject(selector).exists())
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(selector)
        return device.findObject(selector)
    }
    private fun await(timeoutMs: Long = UiTimeoutMs, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Condition not satisfied within $timeoutMs ms" }
            SystemClock.sleep(10) // Functional state wait; never a latency endpoint.
        }
    }
    private fun hideKeyboard() {
        instrumentation.runOnMainSync {
            for (root in WindowInspector.getGlobalWindowViews())
                app.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
        }
        device.waitForIdle()
    }
    private fun waitForResult() {
        device.executeShellCommand("cmd statusbar collapse")
        check(description("Pose reconstruction").waitForExists(UiTimeoutMs))
    }
    private fun checkPlayback() {
        fun position(): Float? {
            var value: Float? = null
            instrumentation.runOnMainSync {
                value = WindowInspector.getGlobalWindowViews().flatMap(FrameProbe::nodes)
                    .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current }
            }
            return value
        }
        val before = requireNotNull(position())
        check(description("Playback position").isEnabled)
        description("Playback position").click()
        await { position()?.let { it != before } == true }
    }
    private fun sourceHash(video: String) = app.contentResolver.openInputStream(fixtures.getValue(video))!!.use {
        MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
    }
    private fun fingerprint(id: String): String = runBlocking {
        val saved = requireNotNull(repository.observeAttempt(id).first())
        val content = "${saved.attempt}|${saved.route}|${saved.findings.sortedBy { it.id }}|" + saved.frames.sortedBy { it.frameIndex }
            .joinToString { "${it.id}:${it.timestamp}:${it.coordinates.joinToString { value -> value.toRawBits().toString() }}" }
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun retainedBytes(): Long {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { check(it.moveToFirst() && it.getInt(0) == 0) }
        return listOf(File(app.dataDir, "databases"), app.filesDir, app.cacheDir, app.noBackupFilesDir)
            .flatMap { it.walkTopDown().filter(File::isFile).toList() }.filter {
                it != evidence && it != footprintEvidence && it != File(app.cacheDir, "drishti_asset_cache/pose_landmarker_full.task")
            }
            .sumOf { Os.stat(it.absolutePath).st_blocks * DiskBlockBytes }
    }
    private fun inventory(stage: String) {
        val files = JSONArray()
        val roots = mapOf("databases" to File(app.dataDir, "databases"), "files" to app.filesDir,
            "cache" to app.cacheDir, "no_backup" to app.noBackupFilesDir,
            "external_files" to app.getExternalFilesDir(null), "external_cache" to app.externalCacheDir)
        for ((label, root) in roots) if (root != null && root.exists()) {
            for (file in root.walkTopDown().filter(File::isFile)) {
                files.put(JSONObject().put("root", label).put("path", file.canonicalPath).put("bytes", file.length())
                    .put("allocated_bytes", Os.stat(file.absolutePath).st_blocks * DiskBlockBytes))
            }
        }
        record("inventory", "recorded", extra = JSONObject().put("stage", stage).put("files", files))
    }
    private fun dateText(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    private fun center(rect: Rect) = Point(rect.centerX(), rect.centerY())
    private fun center(target: UiObject) = center(target.visibleBounds)
    private fun interaction(action: String, repetition: Int) = JSONObject().put("kind", "interaction")
        .put("action", action).put("repetition", repetition + 1).put("condition", "idle")
    private fun tap(probe: FrameProbe, action: String, repetition: Int, point: Point, expected: (View) -> Boolean) {
        probe.sample("${action}_${repetition + 1}", expected, { probe.tap(point.x, point.y) }, interaction(action, repetition))
    }
    private fun record(kind: String, outcome: String, video: String = "", repetition: Int = 1, extra: JSONObject = JSONObject()) {
        val row = JSONObject().put("kind", kind).put("outcome", outcome).put("video", video).put("repetition", repetition)
            .put("device", "${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}").put("api", android.os.Build.VERSION.SDK_INT)
            .put("phase", args.getString("phase", "pilot")).put("build", args.getString("buildId", "unrecorded"))
        extra.keys().forEach { row.put(it, extra.get(it)) }; output.appendText("$row\n"); println(row)
    }
    companion object {
        private val InteractionActions = listOf("open_route", "select_route", "type_route_name", "open_grade", "select_grade",
            "open_date", "select_date", "confirm_date", "select_outcome", "type_location", "type_notes", "back_to_form")
        private const val SafetyTimeoutMs = 480_000L // Safety only; the P1 acceptance limit remains 120 seconds.
        private const val UiTimeoutMs = 15_000L
        private const val FootprintAttempts = 10
        private const val DiskBlockBytes = 512L
        private const val WorkloadMs = 40_000L
        private const val DurationToleranceMs = 200L // Agreed tolerance for converted 20 fps fixtures, 2026-10-26.
    }
}
