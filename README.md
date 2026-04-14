# Milestones

Milestones is a simple Android app for tracking the days since or until meaningful events such as habits, anniversaries, or personal goals.

## Features

- Add milestones with a name and start date.
- View the number of days since each milestone, or days remaining until a future date.
- Reset a milestone to start counting from today.
- Remove milestones with confirmation.
- Import and export milestones as JSON.
- Optional Material You dynamic color theming.
- Persist milestone data locally with `SharedPreferences`.

## Tech Stack

- Kotlin
- AndroidX
- Material Components
- RecyclerView
- ViewBinding
- JSON serialization stored in `SharedPreferences`

## Getting Started

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK 24 or newer
- JDK 11

The app currently builds with `compileSdk` and `targetSdk` set to 36.

### Run the App

1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. Select an emulator or connected device.
4. Run the `app` configuration.

### Build from the Command Line

```bash
./gradlew assembleDebug
```

### Run Tests

```bash
./gradlew test
```

## Project Structure

- `app/src/main/java/org/archuser/milestones/MainActivity.kt`: Main screen, navigation drawer actions, import/export, and milestone creation flow.
- `app/src/main/java/org/archuser/milestones/MilestoneAdapter.kt`: RecyclerView adapter for milestone rows.
- `app/src/main/java/org/archuser/milestones/Milestone.kt`: Milestone model.
- `app/src/main/java/org/archuser/milestones/MilestoneStorage.kt`: JSON encoding and decoding for persisted milestone data.
- `app/src/main/res/layout/`: Layout resources for the activity, drawer content, and milestone rows.
- `appicon.png`: Source image copied into generated Android drawable resources during build.

## License

See [LICENSE](LICENSE).
