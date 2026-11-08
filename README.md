# SendBeta 🧗

**SendBeta** is an Android app for indoor boulderers. Record your climbing attempts, review your "beta" (the sequence of moves) with a pose-tracking overlay, keep track of the routes you're projecting, and find climbing gyms around Toronto.

> COMP3074 – Mobile Application Development I · George Brown College · Fall 2026
> Project: Functional Prototype

**Demo video:** _add your video link here_

---

## Screenshots

_Add 3–5 screenshots here (Home, Routes, Analyzer, Map). In Android Studio: Running Devices panel → camera icon, then drag the PNGs into a `screenshots/` folder._

<!--
<p float="left">
  <img src="screenshots/home.png" width="200" />
  <img src="screenshots/routes.png" width="200" />
  <img src="screenshots/analyzer.png" width="200" />
  <img src="screenshots/map.png" width="200" />
</p>
-->

---

## Features in this prototype

| Screen | What you can do |
|---|---|
| **Splash** | Branded launch screen that fades into the app |
| **Home** | Dashboard with recent attempts, a "Record Beta" shortcut, and a link to all routes |
| **Routes** | Search routes by name, gym, or style, and filter with chips (Overhang, Crimps, Slab, Dynamic). Shows a live result count and an empty state |
| **Route Detail** | Grade, gym, and attempt history for a route; log a new attempt or view the gym on the map |
| **Capture** | Camera screen with record, flip-camera, and gallery controls |
| **Beta Analyzer** | Custom-drawn overlay (`BetaAnalyzerOverlayView`) showing a pose skeleton, the hip trajectory path, and START / CRUX / TOP hold markers, plus playback controls and a scrubber |
| **Gyms Map** | Tap a gym marker to see its details, then open turn-by-turn directions in Google Maps |
| **About** | App info and a link to this repository |

**Navigation:** a bottom navigation bar (Home · Routes · Capture · Gyms) plus a fragment back stack for detail screens.

---

## Implemented vs. planned

| Feature | Prototype status | Planned for the final version |
|---|---|---|
| Navigation & all screens | ✅ Working | — |
| Route search & filtering | ✅ Working (sample data) | Load routes from a local database (Room) |
| Directions to a gym | ✅ Opens Google Maps | Live map with the Google Maps SDK |
| Pose overlay | 🟡 Drawn from sample coordinates | Real-time pose detection with ML Kit |
| Video recording | 🟡 UI only | Record with CameraX |
| Saving attempts | 🟡 UI only | Store attempts and notes on the device |

---

## Tech stack

- **Language:** Kotlin
- **UI:** XML layouts, Fragments, Material Components (BottomNavigationView, Chips, Cards)
- **Custom drawing:** `Canvas` / `Paint` / `Path` in a custom `View`
- **Intents:** `ACTION_VIEW` for maps and web links
- **Min SDK:** 24 · **Target SDK:** 34
- **Build:** Gradle (Kotlin DSL) with a version catalog

---

## Project structure

```
app/src/main/
├── java/com/example/sendbeta/
│   ├── SplashActivity.kt
│   ├── MainActivity.kt              # Bottom navigation + fragment container
│   └── ui/
│       ├── HomeFragment.kt
│       ├── RoutesFragment.kt        # Search + filter logic
│       ├── RouteDetailFragment.kt
│       ├── CaptureFragment.kt
│       ├── AnalyzerFragment.kt
│       ├── BetaAnalyzerOverlayView.kt  # Custom-drawn pose overlay
│       ├── MapFragment.kt
│       └── AboutFragment.kt
└── res/
    ├── layout/      # One layout per screen
    ├── drawable/    # Vector icons and shapes
    └── values/      # Colors, strings, themes
```

---

## How to run

1. Clone the repo:
   ```bash
   git clone https://github.com/cozyyyaria/SendBeta.git
   ```
2. Open the folder in **Android Studio** (Koala or newer).
3. Let Gradle sync finish.
4. Run on an emulator or a device with Android 7.0 (API 24) or higher.

---

## Author

**Ariana Cruz** – Computer Programming & Analysis (T197), George Brown College
