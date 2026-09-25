# MediaPipe setup

Step 1 provides `PoseExtractor.createOptions()` and packages a real pose model.
Video decoding, `extract(video)`, persistence, and reconstruction playback are subsequent
increments. The existing picker is unchanged.

- Dependency: `com.google.mediapipe:tasks-vision:1.0.0` (pinned).
- Mode: `VIDEO`, one pose per frame, CPU delegate.
- Model: Google Pose Landmarker Lite, float16, version 1.
- Asset: `app/src/main/assets/pose_landmarker_lite.task`.
- Source: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
- SHA-256: `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`.
- Integration reference: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android

Lite and CPU are the initial baseline; climbing-video accuracy and performance have
not yet been evaluated. Packaging the model allows initialization without downloading
a model on the user's device.

## Verification

Run the configuration unit tests:

```sh
./gradlew :app:testDebugUnitTest --tests com.example.sendit.domain.PoseExtractorSetupTest
```

With an Android device connected, verify real native initialization and model loading:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.sendit.domain.PoseExtractorSetupInstrumentedTest
```

The device test closes the engine after initialization; it does not yet test inference
on a video. APK assembly alone does not establish that this device test passes.

The existing Room unit tests still contain `TODO` bodies. Their compilation blockers
were repaired, but they are not completed database tests and remain separate work.
AGP's built-in Kotlin is enabled so Kotlin sources and tests are actually compiled.
The incorrectly packaged Room compiler was removed from runtime dependencies to
resolve duplicate annotation classes. Room still needs KSP code generation configured
before the database is used. The unit tests use a test-only Java helper to access
MediaPipe's package-private option getters without loading its native engine.

Verified on 2026-09-25: 2 setup unit tests passed and the real-model initialization
test passed on the connected Android 16 phone (24090RA29G). No video inference or
reconstruction playback has been implemented or validated in this step.
