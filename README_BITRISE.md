# Orbit Shell — Bitrise build

This project is prepared for Bitrise CI and builds a debug APK.

## Bitrise
1. Upload/import this repository/project into Bitrise.
2. Select the `primary` workflow, or let Bitrise detect the Android project.
3. Build the project.
4. After a successful build, download the APK from the build Artifacts section.

The project contains `gradlew`, `settings.gradle`, and an `app` application module so the Bitrise Android scanner can detect it.

This is a personal/debug build. It is not Play Store signed.
