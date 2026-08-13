# Coding Rules

Hard rules for working in this repository. Follow these in addition to
`AGENTS.md`.

## Module & package rules

- Keep the SDK/UI split intact. Never add Compose/Android-UI code to
  `blekotsdk`; never reach into `blekotsdk` internals except via `BleKotSdk`.
  The game domain lives in the app as `com.client.bluehock.game` and must stay
  UI-free — the UI only reaches it via `AirHockeyGame`.
- Package names are fixed: `com.client.bluehock` (app, with the game domain in
  `com.client.bluehock.game`) and `com.client.blekotsdk` (BLE core). Do not
  rename without an explicit request.
- Gameplay constants live in `GameConstants`, physics in `AirHockeyEngine`, and
  wire encoding in `GameProtocol`. Do not duplicate these values in the UI.

## State & threading

- All game state mutations happen in `GameSession` (host side) and are pushed
  through `StateFlow`. The UI only observes.
- Do not call blocking Bluetooth APIs on the main thread; use coroutines.
- Paddle input flows: gesture → ViewModel `onDrag` → `AirHockeyGame.sendPaddle` →
  GATT write. Do not short-circuit the host as source of truth.

## Compose rules

- One logical unit per file: a screen, a section, or a renderer.
- New sections go in `ui/components/`, new board/canvas code in `ui/board/`,
  and new colors used by both belong in `ui/board/BoardColors.kt`.
- Components shared only within a package should be `private`; anything used
  across packages must be `internal`. Only `AirHockeyScreen` and `BleKotSdk`
  are `public`.
- Read state with `collectAsState()`; never pass the ViewModel down more than
  one level — pass plain values and lambdas instead.

## Bluetooth rules

- Turning Bluetooth on/off must go through `BluetoothController`, which
  delegates to the SDK. Do not fire intents that open system Bluetooth
  settings; the SDK's `requestEnableBluetooth` shows the system enable-consent
  dialog via a registered `ActivityResultLauncher`.
- Runtime permissions are owned by the SDK: use `BleKotSdk.requiredBluetoothPermissions`
  and `requestBluetoothPermissions`. The initial prompt stays in
  `AirHockeyActivity`; do not add new permission prompts inside components
  without a good reason.
- Keep the BLE connection lifecycle (advertise / scan / connect / notify) in
  the `blekotsdk` module; game-specific GATT concerns live in the
  `com.client.bluehock.game.ble` package.

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
  `:`, e.g. `A["airhockapp (Game UI)"]`. Validate with `mermaid.parse` if in
  doubt — GitHub's renderer is strict.
- When you add a module, package, or feature, update the layout/commands in
  `AGENTS.md` and the README module structure.

## Verification

- After changes, run the relevant tests: `./gradlew :blekotsdk:testDebugUnitTest`
  for SDK logic, `./gradlew :airhockapp:testDebugUnitTest` for the game domain,
  and `./gradlew :airhockapp:assembleDebug` for the app.
- Do not commit until the user asks.
