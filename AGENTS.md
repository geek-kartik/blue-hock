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

The repo is built around a **generic, reusable BLE core** (`blekotsdk`) that
any BLE app can use. The Air Hockey game is implemented in the app as a thin,
UI-free domain layer (`com.client.bluehock.game`) on top of it.

## Tech stack

- Kotlin 2.4, Android Gradle Plugin 9.2, Gradle Kotlin DSL (`*.gradle.kts`)
- Jetpack Compose (Material3, Compose BOM) for the app UI
- Kotlin Coroutines + StateFlow for async / state
- BLE GATT (advertising, scanning, notify characteristics) for connectivity
- `compileSdk` / `targetSdk` 37, `minSdk` 24

## Repository layout

```
ble-connect-game/
├── settings.gradle.kts        # rootProject.name = "bluehock"; includes :blekotsdk, :airhockapp
├── build.gradle.kts           # top-level plugin aliases only
├── gradle/libs.versions.toml  # version catalog (single source of dependency versions)
├── blekotsdk/                 # public Android library — generic, app-agnostic BLE SDK
│   └── src/main/java/com/client/blekotsdk/
│       ├── api/BleKotSdk.kt   # public entry point (object): initialize, startScan,
│       │                      #   startScanCollecting, newConnection, newServer
│       ├── ble/               # BleScanner, BleConnection, BleGattServer, BleAdapter (generic GATT)
│       ├── model/             # BleDevice, ConnectionState, BleSdkError, BleConstants,
│       │                      #   GattServiceProfile
│       ├── permissions/       # BlePermissions — version-aware BLE runtime permissions
│       └── logging/           # Logger, SdkLog, AndroidLogLogger
└── airhockapp/                # Android application (package com.client.bluehock)
    └── src/main/java/com/client/bluehock/
        ├── AirHockeyActivity.kt     # launcher; handles BLE runtime permissions
        ├── AirHockeyViewModel.kt    # bridges UI to AirHockeyGame via StateFlow
        ├── bluetooth/               # BluetoothController — in-app BT enable/disable
        ├── game/                    # UI-free Air Hockey domain layer
        │   ├── api/AirHockeyGame.kt # AirHockeyGame: hostGame, startScan, connect, sendPaddle,
        │   │                         #   restart, disconnect, observeState/Connection/Errors
        │   ├── session/GameSession.kt   # orchestration: hosts/joins, owns engine + BLE
        │   ├── ble/                 # GameBleClient, GameBleServer, GameScanner, GameGattProfile
        │   ├── engine/              # AirHockeyEngine — deterministic physics + goal detection
        │   ├── protocol/            # GameProtocol — little-endian binary encode/decode
        │   └── model/               # GameConstants, GameRole, GamePhase, GameConnectionState,
        │                             #   GameDeviceInfo, AirHockeyState
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
| Build the app APK | `./gradlew :airhockapp:assembleDebug` |
| Install the app | `./gradlew :airhockapp:installDebug` |
| Run unit tests (protocol + engine) | `./gradlew :airhockapp:testDebugUnitTest` |
| Run all unit tests | `./gradlew testDebugUnitTest` |

## Key conventions

- **No Flutter**: this is Kotlin + Gradle + Compose.
- **SDK / UI split**: `blekotsdk` is the only library module and must stay
  app-agnostic. The game domain (`com.client.bluehock.game`) lives in the app
  but must stay UI-free. Game state flows one way: engine → `GameSession` →
  `StateFlow` → ViewModel → Compose. The UI never mutates game state directly.
- **Packages**: app = `com.client.bluehock` (game domain = `com.client.bluehock.game`),
  BLE core = `com.client.blekotsdk`.
- **Visibility**: `AirHockeyScreen` is the only public entry point in the app;
  `BleKotSdk` is the only public entry point of `blekotsdk`. Everything else in
  the app (game layer, composables, viewmodel) and BLE internals use `internal`.
- **State**: ViewModel exposes `StateFlow`; UI reads via `collectAsState()`.
- **Compose organization**: split components by feature into `ui/components/`
  (screens/sections) and `ui/board/` (canvas/game rendering). One logical unit
  per file.
- **Bluetooth**: never send the user to system Bluetooth settings from code.
  Turning Bluetooth on/off, runtime permissions and the system enable-consent
  dialog are owned by the SDK (`BleKotSdk.hasBluetoothPermissions`,
  `requestBluetoothPermissions`, `requestEnableBluetooth`). The app calls these
  through `BluetoothController`.
- **README Mermaid diagrams**: node/edge labels containing parentheses or
  colons must be double-quoted (e.g. `A["airhockapp (Game UI)"]`) or GitHub's
  renderer fails with "Unable to render rich display".
- **Comments**: match the existing style — KDoc on public/internal classes and
  functions, brief comments only for non-obvious logic. Do not add noisy
  inline comments.
- **Tests**: unit tests live in `airhockapp/src/test` mirroring the source
  packages.
