\# Milestones



Milestones is a simple Android app for tracking the days since (or until) meaningful events such as habits, anniversaries, or personal goals. You can add milestones with a name and start date, then reset or remove them as needed.



\## Features



\- Add milestones with a name and start date.

\- See the number of days since each milestone (or days until it starts).

\- Reset a milestone to start counting from today.

\- Remove milestones with confirmation.

\- Data persists locally using SharedPreferences.



\## Tech stack



\- Kotlin + AndroidX

\- RecyclerView + ViewBinding

\- Local JSON storage via SharedPreferences



\## Getting started



\### Prerequisites



\- Android Studio (latest stable recommended)

\- Android SDK 24+ (project targets SDK 36)

\- JDK 11



\### Run the app



1\. Open this repository in Android Studio.

2\. Let Gradle sync.

3\. Choose an emulator or device.

4\. Click \*\*Run\*\*.



\### Build from the command line



```bash

./gradlew assembleDebug

```



\### Run tests



```bash

./gradlew test

```



\## Project structure



\- `app/src/main/java/org/archuser/milestones/MainActivity.kt`: Main UI and interaction flow.

\- `app/src/main/java/org/archuser/milestones/MilestoneAdapter.kt`: RecyclerView adapter for milestone rows.

\- `app/src/main/java/org/archuser/milestones/MilestoneStorage.kt`: JSON encoding/decoding for local persistence.

\- `app/src/main/res/layout/`: Layouts for the main screen and list items.



\## License



See \[LICENSE](LICENSE).



