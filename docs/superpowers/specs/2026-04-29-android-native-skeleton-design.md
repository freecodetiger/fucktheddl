# Android Native Skeleton Design

## Goal

Initialize a command-line buildable native Android skeleton for fucktheddl, the Android-first schedule management Agent app described in `docs/product-plan.md` and `docs/frontend-design.md`.

## Scope

The first Android commit creates the app shell only. It does not connect to the backend, persist schedules, request runtime permissions, or implement Agent reasoning. The skeleton must build locally, install on a USB-connected Android phone, and give future AI coding sessions a clear Compose structure to extend.

## Architecture

Use a root-level Gradle Android project with one `app` module. The app uses Kotlin and Jetpack Compose with a single `MainActivity`. UI state comes from a small local model/provider pair so the first screen can render without network or storage dependencies.

## UI

The launch surface opens directly into the useful schedule interface:

- Today view with current-day summary, timeline cards, and open-slot guidance.
- Calendar view placeholder with week-focused planning copy.
- Persistent bottom Agent composer with text input affordance and send action.
- Compact sync/status indicator.

The visual style follows the existing front-end design: calm near-white surface, black ink, blue accent, sparse status colors, small radii, and no marketing screen.

## Testing

Add local JVM tests for the starter schedule model. The tests verify the initial UI data has events, open slots, a sync state, and stable Today/Calendar tab labels. Android build verification is `./gradlew testDebugUnitTest assembleDebug`.

## Acceptance

- `sdkmanager`/`adb` environment remains compatible with the already installed SDK.
- `./gradlew testDebugUnitTest` passes.
- `./gradlew assembleDebug` produces a debug APK.
- `./gradlew installDebug` can install to the currently connected authorized Xiaomi device.
