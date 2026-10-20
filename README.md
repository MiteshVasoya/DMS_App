# Driver Monitoring System (DMS) for Android

An intelligent, on-device Driver Monitoring System (DMS) Android application built with modern **Jetpack Compose**, **CameraX**, and **Google ML Kit Face Detection**. The app actively monitors driver alertness in real-time through the front camera and issues multi-modal audiovisual warnings if signs of drowsiness or distraction are detected.

---

## Features

### 👁️ Real-Time Drowsiness Detection
- Uses on-device Google ML Kit Face Detection to track **left and right eye-open probabilities**.
- Accurately differentiates natural eye blinks from prolonged eye closure (microsleeps).
- **Two-tier alert response**:
  - **Warning Stage (Eyes closed > 1.0s)**: Gentle pulsing amber warning banner and prompt tones.
  - **Critical Stage (Eyes closed > 1.8s)**: High-priority flashing red emergency screen overlay, loud alarm beeps, and haptic vibrations.

### 🚗 Distraction & Head Pose Tracking
- Tracks driver head orientation across 3D Euler angles:
  - **Yaw (Rotation Left/Right)**: Alerts if looking away from the road (> 28°).
  - **Pitch (Nodding/Looking Down)**: Alerts if driver's head tilts downward (> 22°).
  - **Face Absence**: Alerts if the driver leaves the frame for an extended duration.

### 🔋 Battery Saver Mode (Camera Display Off)
- Allows drivers to turn off the video preview to save battery and reduce phone temperature on long trips.
- In Battery Saver mode, the screen displays a clean, OLED-friendly black background while the underlying computer vision and alerting pipeline continues running at full fidelity.

### 📊 Driver Telemetry HUD
- Real-time gauge metrics for **Left Eye Open %** and **Right Eye Open %**.
- Live **Head Pose angle** readout.
- Frame rate (**FPS**) performance counter and driver status updates.

### 🔊 Multi-Modal Alerting
- **ToneGenerator (STREAM_ALARM)**: Instantaneous, zero-latency audible beeps and emergency alert tones at high volume.
- **Haptic Feedback**: Pulsing vibration alarms via Android `Vibrator` / `VibratorManager`.
- **Screen Flashing**: High-visibility animated border and backdrop pulses designed to catch peripheral vision.

### ⚙️ Driver Controls & Customization
- **Start / Pause**: Quick-toggle button to pause monitoring when taking a break.
- **Mute Controls**: Independent toggles for audio alarms and vibration.
- **Sensitivity Tuning**: In-app settings dialog to adjust:
  - Eye closure sensitivity threshold (15% – 60%).
  - Warning trigger delay (0.6s – 2.5s).

### 🔒 100% Private & Offline
- All computer vision inferences run **locally on-device** using bundled ML Kit models.
- No video or telemetry data is recorded, saved, or transmitted over the internet.

---

## Tech Stack & Architecture

- **Language**: Kotlin (100%)
- **UI Framework**: Jetpack Compose + Material 3
- **Camera Pipeline**: CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`)
- **Computer Vision**: Google ML Kit Face Detection (`com.google.mlkit:face-detection`)
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & `StateFlow`
- **Minimum SDK**: Android 13 (API 33)
- **Target SDK**: Android 15 (API 35) / Android 16 (API 37)

---

## Project Structure

```text
DMS_App
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/dms_app
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── audio/
│   │   │   │   ├── detection/
│   │   │   │   ├── model/
│   │   │   │   └── ui/
│   │   │   └── res/
│   │   ├── androidTest/
│   │   └── test/
│   └── build.gradle.kts
├── gradle
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── .gitignore
```

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer.
- Android device or emulator running Android 13 (API 33) or higher with front camera support.

### Running from Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/ZijianOu/DMS_App.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Connect an Android phone via USB (with USB Debugging enabled) or start an emulator with a virtual webcam.
5. Click **Run** (`Shift + F10`).
6. Grant camera permission on the phone when prompted.

### Building the Test APK
To compile a standalone debug APK:
```bash
./gradlew assembleDebug
```
The output file will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Testing & Calibration Tips

1. **Mounting Position**: For best accuracy, mount your phone at eye level on a dashboard or windshield phone mount facing the driver.
2. **Drowsiness Testing**: Close both eyes for > 1 second to trigger the warning tone, and keep them closed for > 1.8 seconds to trigger the critical alarm.
3. **Distraction Testing**: Turn your head sideways (as if looking out the window) for > 1.5 seconds.
4. **Glasses / Sunglasses**: If wearing thick-framed glasses, open the Settings (⚙️) dialog and adjust the eye sensitivity slider to best match your eyes' resting position.

---

## 🗺️ Roadmap & Upcoming Features

- [ ] **Background & Navigation Overlay Mode**: Run DMS as a Foreground Service with a floating overlay / Picture-in-Picture (PiP) window so drivers can navigate with **Google Maps / Waze** while alertness tracking and audible alarms stay active.
- [ ] **Yawn & Fatigue Detection**: Measure mouth opening ratio (MAR) to catch early signs of drowsiness before micro-sleeps occur.
- [ ] **Low-Light / Night Driving Optimization**: Dynamic camera exposure boosting for dark vehicle cabins.
- [ ] **Trip History & Analytics**: Log driving sessions, alert frequency, and safety scores to local database.
- [ ] **Customizable Voice Warnings**: Replace tone beeps with natural voice prompts (e.g., *"Driver drowsy, please pull over"*).

---

## ⚠️ Known Limitations

- **Sunglasses & Polarized Eyewear**: Dark tinted lenses can obstruct eye landmark detection; head pose tracking continues to operate.
- **Low Light / Night Driving**: In total darkness without ambient cabin light, standard front RGB cameras may have reduced face detection confidence.
- **Extreme Mounting Angles**: Mounting the phone too far to the side (> 45° off-center) can skew yaw and head orientation estimates.

