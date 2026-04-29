# Android Native Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a root-level Kotlin + Jetpack Compose Android skeleton for the fucktheddl schedule Agent app.

**Architecture:** A single `app` module owns the native shell. A local starter data provider feeds Compose screens so the app is useful before backend integration. Gradle wrapper files make builds reproducible from a clean terminal.

**Tech Stack:** Android Gradle Plugin, Kotlin, Compose Material 3, JUnit.

---

### Task 1: Gradle Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`

- [ ] Add Gradle plugin management and dependency repositories.
- [ ] Configure the Android app module with namespace `com.zpc.fucktheddl`, compile SDK 36, min SDK 26, and Compose enabled.
- [ ] Add Compose, Activity Compose, Lifecycle, and JUnit dependencies.

### Task 2: Starter Data Test

**Files:**
- Create: `app/src/test/java/com/zpc/fucktheddl/schedule/StarterScheduleRepositoryTest.kt`

- [ ] Write local JVM tests before production Kotlin model files.
- [ ] Run `./gradlew testDebugUnitTest` and confirm the test fails because the repository does not exist yet.

### Task 3: Starter Data Model

**Files:**
- Create: `app/src/main/java/com/zpc/fucktheddl/schedule/StarterScheduleModels.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/schedule/StarterScheduleRepository.kt`

- [ ] Implement immutable schedule shell data classes.
- [ ] Implement a deterministic starter repository for Today/Calendar tabs, sample events, open slots, and sync state.
- [ ] Run `./gradlew testDebugUnitTest` and confirm the test passes.

### Task 4: Compose App Shell

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/zpc/fucktheddl/MainActivity.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`

- [ ] Implement `MainActivity` as a Compose entry point.
- [ ] Render Today and Calendar tabs from the starter repository.
- [ ] Add a persistent Agent composer at the bottom.
- [ ] Use Material 3 colors aligned with `docs/frontend-design.md`.

### Task 5: Verification

**Files:**
- Generated: `gradlew`
- Generated: `gradle/wrapper/gradle-wrapper.jar`
- Generated: `gradle/wrapper/gradle-wrapper.properties`

- [ ] Generate the Gradle wrapper.
- [ ] Run `./gradlew testDebugUnitTest`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Run `adb devices -l` and confirm the connected device is authorized.
- [ ] Run `./gradlew installDebug` when a device is connected.
