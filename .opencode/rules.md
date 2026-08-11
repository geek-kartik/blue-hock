# Coding Rules

Hard rules for working in this repository. Follow these in addition to
`AGENTS.md`.

## Module & package rules

- Keep the SDK/UI split intact. Never add Compose/Android-UI code to `gamesdk`;
  never reach from `sampleapp` into `gamesdk` internals except via `GameSdk`.
- Package names are fixed: `com.client.bluehock` (app) and
  `com.client.blekotsdk` (BLE core) and `com.client.blekotsdk.game`
  (game SDK). Do not rename without an explicit request.
- Gameplay constants live in `GameConstants`, physics in `AirHockeyEngine`, and
  wire encoding in `GameProtocol`. Do not duplicate these values in the UI.

## State & threading

- All game state mutations happen in `GameSession` (host side) and are pushed
  through `StateFlow`. The UI only observes.
- Do not call blocking Bluetooth APIs on the main thread; use coroutines.
- Paddle input flows: gesture → ViewModel `onDrag` → `GameSdk.sendPaddle` →
  GATT write. Do not short-circuit the host as source of truth.

## Compose rules

- One logical unit per file: a screen, a section, or a renderer.
- New sections go in `ui/components/`, new board/canvas code in `ui/board/`,
  and new colors used by both belong in `ui/board/BoardColors.kt`.
- Components shared only within a package should be `private`; anything used
  across packages must be `internal`. Only `AirHockeyScreen` and `GameSdk` are
  `public`.
- Read state with `collectAsState()`; never pass the ViewModel down more than
  one level — pass plain values and lambdas instead.

## Bluetooth rules

- Turning Bluetooth on/off must go through `BluetoothController`. Do not fire
  intents that open system Bluetooth settings.
- Runtime permissions are handled once in `AirHockeyActivity`; do not add new
  permission prompts inside components without a good reason.
- Keep the BLE connection lifecycle (advertise / scan / connect / notify) in
  the `gamesdk` `ble/` package.

## Style

- Kotlin, no Java. Follow existing formatting (4-space indent, trailing
  commas).
- KDoc public/internal classes and functions that aren't self-explanatory;
  use brief comments only for non-obvious logic. No noisy inline comments.
- Prefer immutable data and `data class` for state snapshots.
- Keep functions small and composables parameterized (no hardcoded text in
  reusable components).

## Docs

- README Mermaid: double-quote any node/edge label containing `(`, `)`, or
  `:`, e.g. `A["sampleapp (Game UI)"]`. Validate with `mermaid.parse` if in
  doubt — GitHub's renderer is strict.
- When you add a module, package, or feature, update the layout/commands in
  `AGENTS.md` and the README module structure.

## Verification

- After changes, run the relevant tests: `./gradlew :gamesdk:testDebugUnitTest`
  for SDK logic, `./gradlew :sampleapp:assembleDebug` for the app.
- Do not commit until the user asks.
