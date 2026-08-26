# Orbit Shell 1.0 — Personal Launcher

A standalone Android home launcher designed for personal use and phone-only development.

## Build target
- compileSdk 34
- targetSdk 34
- minSdk 26
- JDK 17
- Android Gradle Plugin 8.2.2
- No third-party runtime dependencies

## Features
- Home/default launcher role
- Multiple pages
- Configurable grid 3–8 columns / 4–10 rows
- Drag & drop apps; drop app onto app to create folders
- Folder rename/open/remove
- Customizable dock
- App drawer with search
- Hidden apps
- Real Android widgets via AppWidgetHost, including widget configuration
- Photo cards via Android document picker
- Move/remove/rename home items
- Themes, accent color, icon scale, labels, glass/solid cards
- Clock/date/battery
- Backup/export/import layout JSON
- Settings shortcuts
- Offline/local storage

## Phone build
Open the project root in AndroidIDE-Rv2 (or another Gradle Android IDE). Use JDK 17 and Android SDK 34. Let Gradle sync, then assemble the debug APK. Install it and select Orbit Shell as the default Home app.

## Limitations
Android/OEMs restrict some launcher behaviors. This project uses public Android APIs and does not claim OEM-specific Nova parity. Widgets, icon packs, notification access and gesture behavior can vary by device.
