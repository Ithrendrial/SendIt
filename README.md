# SendIt

SendIt is a native Android app for reviewing climbing attempts. This version of development has only developed the attempt form submission and video processing features, which includes:
- A user being able to upload a new climbing attempt, associated with a particular climbing route, and information about the attempt.
- Extraction of video pose data with MediaPipe
- Analysis of movement using informal climbing technique and hesitiation classification.


## Requirements

- Android Studio with Android SDK 37 installed
- An Android device or emulator running Android 10 / API 29 or later
- A USB cable and USB debugging enabled when using a physical device

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/Ithrendrial/SendIt.git
   cd SendIt
   ```

2. Open the `SendIt` folder in Android Studio.

3. Allow Android Studio to sync the Gradle project and install any requested
   SDK components.

## Run the app

Select an Android 10+ emulator or connected device in Android Studio, then
click **Run**.

Alternatively, from the repository root, install the debug build on a connected
device or running emulator with:

```bash
./gradlew installDebug
```
