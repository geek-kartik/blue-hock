# AGENTS.md

Guidance for AI coding agents working in this repository. Read this before
making changes.

## Project overview

A two-player **Air Hockey** game played between two Android devices over
Bluetooth Low Energy (BLE). One device **hosts** the game (runs the
authoritative 120&nbsp;Hz simulation and a GATT server), the other **joins**
by scanning and connecting. The board, puck physics, scoring, and match state
are synchronized wirelessly in real time.

This is a **native Android / Kotlin project** (NOT Flutter, despite the
`flutter/sdk` directory path).

## Tech stack

- Kotlin 2.4, Android Gradle Plugin 9.2, Gradle Kotlin DSL (`*.gradle.kts`)
- Jetpack Compose (Material3, Compose BOM) for the app UI
- Kotlin Coroutines + StateFlow for async / state
- BLE GATT (advertising, scanning, notify characteristics) for connectivity
- `compileSdk` / `targetSdk` 37, `minSdk` 24

## Repository layout

```
ble-connect-game/
├── settings.gradle.kts        # rootProject.name = "bluehock"; includes :gamesdk, :sampleapp
├── build.gradle.kts           # top-level plugin aliases only
├── gradle/libs.versions.toml  # version catalog (single source of dependency versions)
├── gamesdk/                   # public Android library — reusable game SDK
│   └── src/main/java/com/client/bluehock/gamesdk/
│       ├── GameSdk.kt         # public entry point (object): hostGame, startScan, connect,
│       │                      #   sendPaddle, restart, disconnect, observeState/Connection/Errors
│       ├── GameSession.kt     # internal orchestration: hosts/joins, owns engine + GATT
│       ├── ble/               # GameGattServer, GameGattClient, GameScanner
│       ├── engine/            # AirHockeyEngine — deterministic physics + goal detection
│       ├── protocol/          # GameProtocol — little-endian binary encode/decode
│       └── model/             # GameConstants, GameRole, GamePhase, GameConnectionState,
│                              #   GameDeviceInfo, AirHockeyState
└── sampleapp/                 # Android application (package com.client.bluehock)
    └── src/main/java/com/client/bluehock/
        ├── AirHockeyActivity.kt     # launcher; handles BLE runtime permissions
        ├── AirHockeyViewModel.kt    # bridges UI to GameSdk via StateFlow
        ├── bluetooth/               # BluetoothController — in-app BT enable/disable
        └── ui/
            ├── AirHockeyScreen.kt   # screen orchestrator (score header, board, overlays, controls)
            ├── board/               # AirHockeyBoard, BoardRenderer, BoardInterpolator, BoardColors
            ├── components/          # BluetoothStatusSection, ScoreHeader, PhaseOverlay, ControlBar
            └── theme/               # AirHockeyTheme, Color, Type
```

## Build & test commands

Run from the repository root (`./gradlew` wrapper):

| Task | Command |
| --- | --- |
| Build the app APK | `./gradlew :sampleapp:assembleDebug` |
| Install the app | `./gradlew :sampleapp:installDebug` |
| Run SDK unit tests (protocol + engine) | `./gradlew :gamesdk:testDebugUnitTest` |
| Run app unit tests | `./gradlew :sampleapp:testDebugUnitTest` |
| Run all unit tests | `./gradlew testDebugUnitTest` |

## Key conventions

- **No Flutter**: this is Kotlin + Gradle + Compose.
- **SDK / UI split**: `gamesdk` must stay UI-free and app-agnostic. All UI lives
  in `sampleapp`. Game state flows one way: engine → `GameSession` → `StateFlow`
  → ViewModel → Compose. The UI never mutates game state directly.
- **Packages**: app = `com.client.bluehock`, SDK = `com.client.bluehock.gamesdk`.
- **Visibility**: `AirHockeyScreen` and `GameSdk` are the only public entry
  points. Internal helpers (composables, engine bits) use `internal`.
- **State**: ViewModel exposes `StateFlow`; UI reads via `collectAsState()`.
- **Compose organization**: split components by feature into `ui/components/`
  (screens/sections) and `ui/board/` (canvas/game rendering). One logical unit
  per file.
- **Bluetooth**: never send the user to system Bluetooth settings from code.
  Use `BluetoothController` to enable/disable in-app.
- **README Mermaid diagrams**: node/edge labels containing parentheses or
  colons must be double-quoted (e.g. `A["sampleapp (Game UI)"]`) or GitHub's
  renderer fails with "Unable to render rich display".
- **Comments**: match the existing style — KDoc on public/internal classes and
  functions, brief comments only for non-obvious logic. Do not add noisy
  inline comments.
- **Tests**: unit tests live in `gamesdk/src/test` and `sampleapp/src/test`
  mirroring the source packages.
