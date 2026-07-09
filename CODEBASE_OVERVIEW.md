# SkySense - Codebase Overview & Interview Guide

This document is designed to give you a deep, structural understanding of the SkySense application. If you are asked to explain this project in a technical interview, use this guide as your cheat sheet.

---

## 1. Architectural Pattern: MVVM + Clean Architecture Concepts

SkySense is built using the **Model-View-ViewModel (MVVM)** pattern, heavily relying on **Unidirectional Data Flow (UDF)** and **Reactive Programming**.

*   **Model (Data Layer):** Responsible for fetching data from the hardware sensors (GNSS, Compass), Local Database (Room), and Remote APIs (Gemini). It exposes this data as continuous streams (Kotlin `Flow`s).
*   **ViewModel (Presentation Layer):** Acts as the bridge. It collects data from the Data Layer, applies business logic (via the Domain layer), and exposes UI State (`StateFlow`) to the UI. It survives configuration changes (like screen rotations).
*   **View (UI Layer):** 100% Jetpack Compose. The View is "dumb"—it only knows how to render the State it receives from the ViewModel and sends User Intents (button clicks) back to the ViewModel.

---

## 2. Tech Stack & Core Libraries

If an interviewer asks, "What libraries did you use and why?", here is your answer:

*   **Jetpack Compose:** Used for the entire UI layer. Why? It's the modern, declarative toolkit for Android. It eliminates XML layouts and makes writing reactive UIs much faster and less prone to state bugs.
*   **Kotlin Coroutines & Flow:** Used for all asynchronous tasks and data streaming. Why? It's the native Kotlin way to handle threading. `Flow` is used because GNSS data is a continuous stream of updates (1 per second), perfectly matching Flow's design.
*   **Room Database:** Used for the `History` section. Why? It provides an abstraction layer over SQLite, allowing compile-time verification of SQL queries and seamless integration with Kotlin Flows.
*   **Google Tink & Jetpack DataStore:** Used for storing the Gemini API key. Why? Standard `SharedPreferences` are unencrypted and easily readable on rooted devices. Tink provides military-grade AES-256-GCM encryption backed by the Android Keystore to securely lock down user API keys.
*   **OkHttp & Gson:** Used for communicating with the Gemini AI REST API. Why? Retrofit was overkill for a single endpoint. OkHttp provides a lightweight, highly efficient HTTP client, and Gson handles the JSON serialization/deserialization.
*   **Vico (Compose Charts):** Used for rendering the history graphs. Why? It is built specifically for Jetpack Compose, offering highly customizable, performant charts without the heavy overhead of older view-based chart libraries like MPAndroidChart.

---

## 3. Directory Structure & File Breakdown

### `com.skysense.app.data.*` (The Data Layer)
This is where data comes from (Sensors, Network, Database).
*   **`db/`**: Contains the Room Database setup (`AppDatabase`), Entities (`GnssHistoryEntity`), and DAOs (`GnssHistoryDao`).
*   **`model/`**: Pure Kotlin data classes (POJOs) representing the core objects in the app (`SatelliteInfo`, `GnssSnapshot`, `AiChat`).
*   **`remote/GeminiApiClient.kt`**: Handles the raw HTTP POST requests to Google's Generative Language API using OkHttp.
*   **`repository/GnssRepository.kt`**: The single source of truth for live satellite data. It acts as a middleman, collecting data from the `GnssService` and caching it to Room.
*   **`sensor/CompassManager.kt`**: Listens to the device's `ROTATION_VECTOR` hardware sensor to calculate the phone's physical heading (azimuth) relative to true North.
*   **`store/SecurePreferencesManager.kt`**: Wraps Google Tink and DataStore to securely encrypt, save, and retrieve the Gemini API key.

### `com.skysense.app.service.*`
*   **`GnssService.kt`**: This is an Android `Service` that interfaces directly with the Android OS's `LocationManager`. It implements `GnssStatus.Callback` to listen to live satellite connections and publishes the data into Kotlin `MutableStateFlow`s.

### `com.skysense.app.domain.*` (The Business Logic)
*   **`LocalInterpretationEngine.kt`**: The "brain" of the dashboard. It's a pure Kotlin file containing rules that take raw mathematical data (like `pdop = 1.2`) and translate it into human-readable insights (e.g., "Excellent accuracy").

### `com.skysense.app.ui.*` (The UI Layer)
*   **`theme/`**: Contains the design system (Colors, Typography, Shapes).
*   **`navigation/NavGraph.kt`**: Defines the routes for every screen using Compose Navigation.
*   **`screens/`**: Contains all the screens in the app. Each package generally contains a `[Name]Screen.kt` (the Compose UI) and a `[Name]ViewModel.kt` (the state manager).
    *   *Notable File:* `SkyMapScreen.kt`. Uses a highly customized Compose `Canvas` to draw concentric circles (representing elevation) and plots satellites based on basic trigonometry (converting azimuth/elevation angles into X/Y screen coordinates). It also dynamically applies the `-heading` rotation from `CompassManager` to rotate the map physically.

---

## 4. Key Interview Questions & Answers

**Q: How do you handle continuous stream of data from the GPS hardware without freezing the UI?**
**A:** "I wrapped the Android `LocationManager` and `GnssStatus.Callback` inside an Android `Service`. When the callback receives new satellite data (usually once per second), it emits that data into a Kotlin `MutableStateFlow`. The ViewModels collect this flow asynchronously using Coroutines, and the UI layer observes it safely using `collectAsStateWithLifecycle()`. This ensures the UI thread is never blocked by hardware polling, and data collection stops automatically when the app is in the background."

**Q: How did you implement the radar / Sky Map?**
**A:** "I built it from scratch using Jetpack Compose `Canvas`. The radar consists of 3 concentric circles representing 0°, 45°, and 90° elevation. To plot a satellite, I use trigonometry: I convert the satellite's Elevation and Azimuth into polar coordinates, map that to the screen's pixel radius, and draw the dots. To make the map rotate with the phone, I hooked into the Android `ROTATION_VECTOR` sensor, smoothed the incoming data, and applied a global rotation transformation to the Canvas."

**Q: If I decompile your app, can I steal the Gemini API key?**
**A:** "No. The user inputs their own API key, which is encrypted locally. I didn't use standard `SharedPreferences` because they are plain text XML files. Instead, I used `Google Tink` combined with `Jetpack DataStore`. Tink uses the Android hardware Keystore to generate an AES-256-GCM key, which then encrypts the API key before it is saved to the disk. Even if the device is rooted, the key cannot be extracted without breaking hardware-backed encryption."

**Q: Why use MVVM instead of MVI or just passing everything in Compose?**
**A:** "MVVM provides a clean separation of concerns. If I passed everything directly in Compose, business logic (like interpreting signal strength) would be tangled with UI code. By using ViewModels, the UI remains purely a reflection of State. The ViewModel survives configuration changes (like rotating the phone), so the satellite data doesn't get wiped out and re-fetched every time the user turns their screen."
