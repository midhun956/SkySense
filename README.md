<div align="center">

  <img src="logo_rounded.png" alt="SkySense Logo" width="200" height="200">

  # SkySense

  An educational, AI-powered GNSS visualizer built for Android. It turns your device into an advanced satellite tracker, helping you understand how GPS, Galileo, GLONASS, and other constellations work together to pinpoint your location on Earth.

  ![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
  ![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
  ![AI](https://img.shields.io/badge/AI-Gemini-4285F4?style=flat-square&logo=google&logoColor=white)

</div>

## 🌟 Features

*   **Live Sky Map:** Watch satellites orbit overhead in real-time on a beautifully animated, compass-reactive sky plot.
*   **Deep Satellite Analytics:** Track detailed telemetry including Elevation, Azimuth, Signal-to-Noise Ratio (C/N₀), and dual-band frequencies (L1/L5). 
*   **Multi-Constellation Support:** Supports and maps signals from GPS (USA), GLONASS (Russia), Galileo (EU), BeiDou (China), QZSS (Japan), and IRNSS/NavIC (India).
*   **Live Dashboard:** Instantly view real-time accuracy estimations and Dilution of Precision (PDOP/HDOP/VDOP) with easy-to-understand explanations driven by a local interpretation engine.
*   **Ask AI Integration:** Powered by Google Gemini, ask context-aware questions about your live GNSS data ("Why is my accuracy poor right now?", "What's the difference between L1 and L5?").
*   **Constellation Explorer:** Learn the history, purpose, and fun facts about the global navigation systems orbiting above us.
*   **Session History & Analytics:** View graphs and trends of your satellite connections and accuracy over time.

## 🚀 Installation

You can install SkySense directly on your Android device:

1.  Download the latest `app-release.apk` from the **[Releases](https://github.com/midhun956/SkySense/releases/)** section of this repository.
2.  Open the downloaded APK on your Android device.
3.  If prompted, allow your browser or file manager to "Install unknown apps".
4.  Once installed, open SkySense and grant the required Location permissions to start receiving satellite data.
5.  *(Optional)* To unlock the **Ask AI** feature, you need a free Google Gemini API Key:
    * Go to [Google AI Studio](https://aistudio.google.com/api-keys/).
    * Sign in with your Google Account and click **Create API Key**.
    * Copy the key, open the **Settings** tab in SkySense, and paste it under the AI configuration section.

> **Note:** Android Emulators do not have physical GNSS receivers. To experience the app's full capabilities, it must be installed on a physical Android device outside with a clear view of the sky.

## 🏗️ Project Architecture

SkySense is built using modern Android development practices, following an MVVM (Model-View-ViewModel) architecture:

*   **UI Layer (Compose):** 100% built with Jetpack Compose for a reactive, declarative, and smooth user interface. State is collected safely using `collectAsStateWithLifecycle`.
*   **Presentation Layer (ViewModels):** Manages UI state, handles user intents, and communicates with the data layer using Kotlin Coroutines and Flows.
*   **Domain Layer:** Contains offline, rule-based interpretation logic (`LocalInterpretationEngine`) to transform raw mathematical GNSS metrics into human-readable insights.
*   **Data Layer (Repositories & Services):**
    *   **Live Data:** Uses Android's native `GnssStatus` and `LocationManager` APIs wrapped in an Android Service to stream live satellite metrics as Kotlin Flows.
    *   **Persistence:** Uses Room Database for local caching of session history and analytics.
    *   **Remote/AI:** Uses the official Google Gemini SDK to send context-aware prompts based on the current GNSS state.
    *   **Security:** Utilizes Google Tink and Jetpack DataStore to securely encrypt and store API keys.

## 🔒 Privacy & Permissions

SkySense requires `ACCESS_FINE_LOCATION` to access the raw GNSS measurements from the Android OS. 
All interpretation and data plotting is done entirely **offline on your device**. Location data is never sent to any external server, except when explicitly using the "Ask AI" feature, which sends a snapshot of your current satellite metrics (not your coordinates) to the Gemini API.

## 🛠️ Built With

*   **Kotlin** & **Jetpack Compose**
*   **Google Gemini API** (Generative AI integration)
*   **Jetpack DataStore & Google Tink** (Encrypted preferences)
*   **Room Database** (Local persistence)
*   **Coroutines & Flows** (Asynchronous programming)

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.
