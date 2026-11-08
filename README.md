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
| **Home** | Live stats from the database (total sends, sends this week, active projects, top grade sent) and your 3 most recent attempts |
| **Routes** | Full **CRUD**: add a route (+ button), edit or delete it (long-press), search by name/gym/grade/style, filter with chips |
| **Route Detail** | Notes, style tags, rating, status (Projecting / Sent) and the full attempt history from the database; edit/delete the route, delete an attempt (long-press), share the route, log a new attempt |
| **Capture** | Record with the device camera app **or import a climbing video** from the device |
| **Beta Analyzer** | **Real pose detection** with Google ML Kit: the climber's skeleton is drawn on the video, the hip path is traced across the clip, and a summary shows detection coverage, vertical progress and highest reach. **Hold annotations**: tap Start/Crux/Top, then tap the hold on the paused video (tap again to move, long-press to remove). Play/pause, scrubbing, 0.5×/1×/2× speed. **Save** stores the attempt (route, sent/fell, note, duration, video, hold positions) in the database |
| **Gyms Map** | Real OpenStreetMap map with all 12 Toronto climbing gyms (loaded from the database): tap pins or chips, zoom, my location, distance, open/closed from real hours, directions |
| **About** | App info and a link to this repository |

---

## Architecture & data layer

SendBeta follows the recommended Android architecture: **UI → ViewModel → Repository → Room (SQLite)**.

```
Fragments (UI)  ──observe LiveData──▶  ViewModels  ──▶  SendBetaRepository  ──▶  Room DAOs  ──▶  sendbeta.db
```

| Layer | Files | Responsibility |
|---|---|---|
| **Entities** | `data/Entities.kt` | `Gym`, `Route`, `Attempt` tables (+ `AttemptWithRoute` join result). `Attempt.routeId` is a foreign key with `ON DELETE CASCADE` |
| **DAOs** | `data/Daos.kt` | SQL queries; list queries return `Flow` so the UI updates automatically |
| **Database** | `data/SendBetaDatabase.kt`, `data/SeedData.kt` | Singleton Room database; seeded on first launch with the 12 Toronto gyms (torontoclimbs.ca) and sample routes/attempts |
| **Repository** | `data/SendBetaRepository.kt` | Single source of truth; copies saved videos into app-private storage and cleans them up on delete |
| **ViewModels** | `ui/ViewModels.kt` | `HomeViewModel`, `RoutesViewModel`, `RouteDetailViewModel` expose `LiveData` and run writes in `viewModelScope` |

Data persists between app launches. To reset it, uninstall the app (or clear its storage).

---

## Implemented vs. planned

| Feature | Prototype status | Planned for the final version |
|---|---|---|
| Navigation & all screens | ✅ Working | — |
| Local database (Room) | ✅ Routes, attempts, gyms persisted | Cloud sync / user accounts |
| Routes CRUD, search & filter | ✅ Working | Photos of each route |
| Record / import video | ✅ Camera app + file import | In-app CameraX recording |
| Video playback & analysis controls | ✅ Working | Frame-by-frame stepping |
| Gym map | ✅ Real map, 12 gyms, directions | Live gym busy-ness |
| Pose analysis | ✅ ML Kit pose detection on sampled frames + exact paused frame, hip path, summary | Live tracking during recording, move-by-move breakdown |
| Hold annotations | ✅ Start / Crux / Top placed on the video, saved per attempt | More hold types, free-form notes on the frame |

---

## Tech stack

- **Language:** Kotlin
- **UI:** XML layouts, Fragments, RecyclerView, Material Components (BottomNavigationView, Chips, Cards, Dialogs)
- **Data:** Room (SQLite) with KSP, Kotlin Coroutines & Flow, ViewModel + LiveData
- **Map:** osmdroid (OpenStreetMap), following the OSM tile usage policy
- **Media:** VideoView / MediaPlayer, camera & file-picker intents (Activity Result API)
- **Computer vision:** Google ML Kit Pose Detection (on-device, 33 body landmarks) + `MediaMetadataRetriever` frame extraction (`analysis/PoseAnalyzer.kt`)
- **Custom drawing:** `Canvas` / `Paint` / `Path` in a custom `View`, mapped to the video's on-screen area
- **Min SDK:** 24 · **Target SDK:** 34

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
