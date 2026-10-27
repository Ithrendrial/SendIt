@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.example.sendit.lightweight

import android.os.SystemClock
import android.os.Build
import android.os.Trace
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.DatePicker
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

// Draw markers identify the expected UI state. Only offline SurfaceFlinger presentation
// timestamps may become latency measurements; callback time and assertion waits never do.
class FrameProbe(private val name: String) : Closeable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val output = File(app.getExternalFilesDir(null), "$name.samples.jsonl")
    private val tracePath = "/data/misc/perfetto-traces/sendit-$name.pftrace"
    private val pid: Int

    init {
        output.writeText("")
        val config = File(app.getExternalFilesDir(null), "$name.config.pbtxt")
        config.writeText("""
            buffers { size_kb: 65536 fill_policy: RING_BUFFER }
            buffers { size_kb: 16384 fill_policy: RING_BUFFER }
            buffers { size_kb: 2048 fill_policy: RING_BUFFER }
            duration_ms: 1800000
            write_into_file: true
            file_write_period_ms: 1000
            flush_period_ms: 1000
            incremental_state_config { clear_period_ms: 5000 }
            data_sources { config { name: "linux.process_stats" target_buffer: 2 process_stats_config {
                scan_all_processes_on_start: true
            } } }
            data_sources { config { name: "linux.ftrace" ftrace_config {
                atrace_categories: "view"
                atrace_categories: "input"
                atrace_apps: "${app.packageName}"
            } } }
            data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 1 } }
        """.trimIndent())
        val configPath = "/data/misc/perfetto-configs/sendit-$name.pbtxt"
        device.executeShellCommand("cp ${config.absolutePath} $configPath")
        // Bundled Perfetto gained --background-wait in API 33; older versions need a readiness poll.
        val backgroundWait = Build.VERSION.SDK_INT >= 33 &&
            InstrumentationRegistry.getArguments().getString("legacyTraceStartup") != "true"
        val background = if (backgroundWait) "--background-wait" else "--background"
        val started = device.executeShellCommand("perfetto $background --txt -c $configPath -o $tracePath")
        pid = Regex("(?m)^\\d+$").find(started)?.value?.toInt()
            ?: error("Perfetto did not start: $started")
        // On older releases the app flag can change before the kernel recorder is ready.
        fun captureReady() = Trace.isEnabled() && (backgroundWait ||
            device.executeShellCommand("cat /sys/kernel/tracing/tracing_on").trim() == "1" ||
            device.executeShellCommand("cat /sys/kernel/debug/tracing/tracing_on").trim() == "1")
        val deadline = SystemClock.uptimeMillis() + TraceStartupTimeoutMs
        while (!captureReady() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(TraceStartupPollMs)
        if (!captureReady()) {
            device.executeShellCommand("kill -TERM $pid")
            error("Application trace capture did not become ready")
        }
    }

    fun sample(id: String, expected: (View) -> Boolean, input: () -> Unit, extra: JSONObject = JSONObject(),
               timeoutMs: Long = 15_000, abort: () -> String? = { null }, prepare: () -> Unit = {}) {
        val drawn = CountDownLatch(1)
        var rejection: String? = null
        val windows = linkedMapOf<Long, String>()
        val listeners = linkedMapOf<View, Pair<ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnDrawListener>>()
        var active = true
        fun observeWindows() {
            for (root in WindowInspector.getGlobalWindowViews()) {
                if (root in listeners) continue
                val params = root.layoutParams as WindowManager.LayoutParams
                if (!root.isLaidOut) {
                    params.title = "SendItEvaluationWindow${root.uniqueDrawingId}"
                    root.context.getSystemService(WindowManager::class.java).updateViewLayout(root, params)
                }
                windows[root.uniqueDrawingId] = params.title.toString()
                val before = ViewTreeObserver.OnPreDrawListener { observeWindows(); true }
                val draw = ViewTreeObserver.OnDrawListener {
                    if (active && abort() != null) { rejection = abort(); drawn.countDown() }
                    else if (active && root.isShown && root.alpha > 0 && expected(root)) {
                        Trace.beginSection("SendItFeedback $id ${root.uniqueDrawingId}")
                        Trace.endSection()
                        // Keep frames arriving so SurfaceFlinger can finalize the first response's timeline.
                        root.postInvalidateOnAnimation()
                        drawn.countDown()
                    }
                }
                listeners[root] = before to draw
                root.viewTreeObserver.addOnPreDrawListener(before)
                root.viewTreeObserver.addOnDrawListener(draw)
            }
        }
        val poll = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (active) { observeWindows(); Choreographer.getInstance().postFrameCallback(this) }
            }
        }
        instrumentation.runOnMainSync {
            observeWindows()
            Choreographer.getInstance().postFrameCallback(poll)
        }
        var failure: Throwable? = null
        try {
            // Observe new dialog windows before their first layout; preparation is untimed.
            prepare()
            instrumentation.runOnMainSync {
                check(WindowInspector.getGlobalWindowViews().none(expected)) { "Feedback already visible before input: $id" }
            }
            Trace.beginSection("SendItAction $id")
            try { input() } finally { Trace.endSection() }
            check(drawn.await(timeoutMs, TimeUnit.MILLISECONDS)) { "Expected feedback was not drawn: $id" }
            check(rejection == null) { rejection.orEmpty() }
            device.waitForIdle()
        } catch (error: Throwable) { failure = error }
        finally {
            instrumentation.runOnMainSync {
                active = false
                Choreographer.getInstance().removeFrameCallback(poll)
                for ((root, pair) in listeners) if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnPreDrawListener(pair.first)
                    root.viewTreeObserver.removeOnDrawListener(pair.second)
                }
            }
            val row = JSONObject().put("sample", id).put("pid", android.os.Process.myPid())
                .put("device", "${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}")
                .put("api", android.os.Build.VERSION.SDK_INT)
                .put("build", InstrumentationRegistry.getArguments().getString("buildId", "unrecorded"))
                .put("phase", InstrumentationRegistry.getArguments().getString("phase", "pilot"))
                .put("windows", JSONArray(windows.map { (id, title) -> JSONObject().put("id", id).put("title", title) }))
                .put("outcome", if (failure == null) "draw_observed" else "error")
                .put("error", failure?.toString() ?: JSONObject.NULL)
            extra.keys().forEach { row.put(it, extra.get(it)) }
            output.appendText("$row\n")
        }
        if (failure != null) throw failure
    }

    fun tap(x: Int, y: Int) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            // Older Android traces omit the action; this span identifies its native delivery.
            Trace.beginSection("SendItInput MotionEvent ${MotionEvent.actionToString(action)}")
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { Trace.endSection(); event.recycle() }
        }
    }

    fun character() {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_A, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
            val actionName = if (action == KeyEvent.ACTION_DOWN) "ACTION_DOWN" else "ACTION_UP"
            Trace.beginSection("SendItInput KeyEvent $actionName")
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { Trace.endSection() }
        }
    }

    override fun close() {
        SystemClock.sleep(PresentationDrainMs) // Allow presentation fences to arrive before stopping capture.
        device.executeShellCommand("kill -TERM $pid")
        SystemClock.sleep(500) // Export flush, outside every measured action.
        device.executeShellCommand("cp $tracePath ${File(app.getExternalFilesDir(null), "$name.pftrace").absolutePath}")
    }

    companion object {
        private const val TraceStartupTimeoutMs = 5_000L // AndroidX also allows five seconds for legacy startup.
        private const val TraceStartupPollMs = 100L
        private const val PresentationDrainMs = 1_000L
        fun nodes(view: View): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            // isTransparent is internal in the pinned Compose version; this is test-only visibility inspection.
            if (view is RootForTest) return descendants(view.semanticsOwner.rootSemanticsNode).filter {
                !it.isTransparent && it.boundsInWindow.width > 0 && it.boundsInWindow.height > 0
            }
            return if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
        }
        fun text(view: View, value: String) = nodes(view).any {
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == value }
        }
        fun description(view: View, value: String) = nodes(view).any {
            value in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }
        fun popup(view: View) = nodes(view).any { it.config.contains(SemanticsProperties.IsPopup) }
        fun datePicker(view: View): DatePicker? = when (view) {
            is DatePicker -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { datePicker(view.getChildAt(it)) }
            else -> null
        }
        fun editable(view: View, label: String, value: String): Boolean {
            val all = nodes(view)
            val labelled = all.firstOrNull { label in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                ?: return false
            return all.any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == value &&
                it.boundsInWindow.overlaps(labelled.boundsInWindow) }
        }
        fun selected(view: View, text: String) = nodes(view).any {
            it.config.getOrNull(SemanticsProperties.Selected) == true &&
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text }
        }
    }
}
