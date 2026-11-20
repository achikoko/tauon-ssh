# Changelog

All notable changes to this project are recorded here, newest first. The format is
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/): one line per bullet, naming the API that
changed or the bug that was fixed.

## [Unreleased]

### Added

- Tabs (terminals, file browsers, logs) can be reordered by dragging, and split side by side or one above the other by dropping them near a content's edge (`ClosableTabbedPanel`).
- Each split group has its own "+" button; new tabs open in that group (`ClosableTabbedPanel.addTab(Component, Location)`).

### Changed

- GitHub links, update-check URL (`Constants.API_UPDATE_URL`) and the jediterm submodule now point to the renamed `achikoko/` repositories.

### Fixed

- Update checker compares versions number by number (`VersionEntry.compareTo`); `4.0.10` and `4.1.0` no longer collide and suffixed tags like `v3.3.0-r1` no longer break it.

## [4.0.0] - 2026-11-20

### Added

- Session Status screen to monitor each session's forwarded ports and their connection state (established, initiating, stopped, error, disabled).
- Name and enable option for each port forwarding.
- Keyboard shortcuts as in Muon ([42af469](https://github.com/achikoko/tauon-ssh/commit/42af469b8c9b44b6b00a931fcbecf39375c838d4), [185ade3](https://github.com/achikoko/tauon-ssh/commit/185ade3c1df35cf3e4252749afe42a66300824c7)).
- Option to open the window in its last location ([0fbe592](https://github.com/achikoko/tauon-ssh/commit/0fbe5923df2ded6751ae60331896d934810a2e80)).
- Copying text from tmux ([JetBrains/jediterm 97de919](https://github.com/JetBrains/jediterm/commit/97de9196b8ee24ca5535750313de0733f22fa923)).
- `./gradlew run` task to run Tauon from sources.

### Changed

- Build migrated from Maven to Gradle 9.6 (`./gradlew build`); building needs JDK 17+, the app still runs on Java 11.
- Jar is now `tauon-app/build/libs/tauonssh.jar` and the Debian package `tauon-app/build/distributions/tauonssh_<version>.deb`.
- Updated JediTerm from 3.53 to 3.76, now synced with upstream [JetBrains/jediterm](https://github.com/JetBrains/jediterm) and built from source as a Gradle included build.
- Mouse mode for DECSET 9 (X10, press-only reporting) renamed to `MouseMode.MOUSE_REPORTING_X10`; DECSET 1000 uses upstream's `MOUSE_REPORTING_NORMAL` again.
- EDT helpers `invokeAndWaitIfNeeded`/`invokeLaterIfNeeded` moved from `com.intellij.util.ui.UIUtil` to `tauon.app.util.misc.UIUtil`.
- Versioning follows [Romantic Versioning](https://romversioning.github.io/romver/) (`PROJECT.MAJOR.MINOR`).

### Removed

- Legacy `com.intellij.*` utility classes and the trove4j/eawtstub jars from the JediTerm fork.

### Fixed

- Exception when the local terminal is opened before any other session.

## [3.3.0] - 2025-06-06

### Added

- Download files.
- X11 support on Mac (from @devlinx9) ([020b542](https://github.com/achikoko/tauon-ssh/commit/020b542819f21ecd6a4d5ac95bb083200233457c)).
- Local terminal (as in Muon, by @devlinx9).
- Export and import everything from a zip ([devlinx9/muon-ssh 868f89f](https://github.com/devlinx9/muon-ssh/commit/868f89f2d2e194449fd06cb5eac7c9d9e53d2e32)).
- Confirmation before deleting in the local file system ([e309404](https://github.com/achikoko/tauon-ssh/commit/e30940472cb0ab0901027281d5bfb9cd8c45a106)).

### Changed

- Logo color, blue was too confusing next to Teams.
- Updated JediTerm to version 3.53.
- Change-name dialog (from @devlinx9) ([devlinx9/muon-ssh daf5300](https://github.com/devlinx9/muon-ssh/commit/daf53003696eaf3c35591992f7eb6e7a2d68ba42)).
- Site manager uses the root tree (from @devlinx9) ([devlinx9/muon-ssh 02fc2c4](https://github.com/devlinx9/muon-ssh/commit/02fc2c4510f76e86ac76f83900a635477426a60a), [1326080](https://github.com/devlinx9/muon-ssh/commit/13260808cf687a94e9154f6ce63c6d1ffa44e266)).
- Site import rewritten so it no longer overwrites existing sites.

### Removed

- Language option from the configuration.

### Fixed

- Export and import of zip backups.
- Deadlock while transferring files.
- Forwarded ports staying open after their session was closed.

## [3.2.1] - 2025-02-12

### Added

- New fonts.

### Changed

- More refactorings and stability fixes.

## [3.2.0] - 2024-11-10

### Added

- Collapsed session panel ([PR #3](https://github.com/achikoko/tauon-ssh/pull/3)).

### Changed

- JediTerm moved to an external repository.
- Simplified update checker.
- More refactorings and stability fixes.

### Removed

- Support for all languages except English.

## [3.1.0]

### Added

- SUDO capabilities in the disk space analyzer.

### Fixed

- Unoptimized loop and execution with environment variables in `SudoUtils`.
