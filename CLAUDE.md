# Tauon SSH

Graphical SSH/SFTP client in Java Swing (fork of Muon/Snowflake): terminal (JediTerm), file browser, log viewer,
process/disk tools, port forwarding. Runs on Java 11+, Linux/Windows (macOS best effort).

## Build, run, test

- Gradle 9 wrapper; **building needs JDK 17+**, bytecode targets Java 11 (`options.release = 11`).
- `./gradlew build` — compiles, tests, and produces the artifacts:
  - `tauon-app/build/libs/tauonssh.jar` (uncompressed fat jar, `Main-Class: tauon.app.App`)
  - `tauon-app/build/distributions/tauonssh_<version>.deb` (jdeb via its Ant task)
- `./gradlew run` — runs the app from compiled classes.
- `./gradlew :tauon-app:test --tests '*ClosableTabbedPanelTest'` — one test class.
- A root `./gradlew build` does **not** run the jediterm tests; use `./gradlew :tauon-jediterm:core:test :tauon-jediterm:ui:test`.
- Tests run headless (`java.awt.headless=true` in `tauon-app/build.gradle.kts`). Swing tests must install the skin first:
  `App.skin = new AppSkinLight(); UIManager.setLookAndFeel(App.skin.getLaf());` (the skin's getters read `UIManager`).
  UI behaviour is tested by dispatching real `MouseEvent`/`KeyEvent`s to components (see `ClosableTabbedPanelTest`).

## Layout

- `settings.gradle.kts` — composite build: `tauon-app` + `includeBuild("tauon-jediterm")` with dependency substitution
  for `org.jetbrains.jediterm:jediterm-core|ui`. Version lives in root `gradle.properties`.
- `tauon-app/` — the application (`tauon.app.*`).
  - `src/main/java-templates/` — `Version.java`, expanded by the `generateTemplateSources` task.
  - `src/deb/` — Debian control file and desktop entry.
- `tauon-jediterm/` — git submodule: fork of JetBrains/jediterm (`achikoko/jediterm`). Its Gradle build is kept
  identical to upstream; fork patches and sync procedure are in `tauon-jediterm/TAUON_FORK.md`.
- `flatpak/`, `snap/` — packaging (snap/flatpak are not tested in CI).

## Architecture (tauon-app)

- `App` — entry point; creates `App.skin` (`AppSkinDark`/`AppSkinLight`, Nimbus-based) and the main `AppWindow`.
- `settings/` — persisted models: `SiteInfo` (a saved session; extends `HopEntry`), `PortForwardingRule`, `Settings`.
- `services/` — config managers. Files live in `~/.tauon-ssh/` (`Constants.CONFIG_DIR`).
  - **Secrets are never in the JSON.** `HopEntry.password` is `@JsonIgnore`; `SitesConfigManager` keeps a
    `Map<siteId, PasswordEntry>` serialized with Jackson and stored as a secret-key entry in `passwords.pfx`
    (PBE/AES-256 with the master password, or an empty password if none). `populatePasswordsInto`/`setPasswords`
    copy secrets between that map and the `SiteInfo` tree. Adding a secret = new field in `PasswordEntry`
    (deserialization ignores unknown/missing fields, so older files keep loading).
- `ssh/` — `SSHConnectionHandler` (per session) owns `TauonSSHClient` (sshj; hops, X11, port forwarding).
  `TauonSSHClient.PortForwardingState` runs each `-L`/`-R` rule and exposes a `ConnectionStatusValue`.
- `ui/containers/session/` — `SessionContentPanel` (one per open session) hosts the pages: files, terminal, logs,
  info, tools, status. Pages are `Page`; pages with sub-pages are `SubpagingPage` + `Subpage` (e.g. `StatusPage`).
- `ui/components/closabletabs/` — `ClosableTabbedPanel`: closable tabs that can be dragged to reorder and split
  (tree of `TabGroup`s in `JSplitPane`s). `addTab(component, Location)`; a `Location` comes from the new-tab
  callback, `locationOf(tab)` or `beside(tab, side)`; `null` = active group.
- `ui/components/simpletable/` — `SimpleTable` builder (columns from lambdas) used by the status pages.
- `ui/dialogs/sessions/` — site editor (`SessionInfoPanel` tabs: connection, directories, proxy, jump hosts,
  port forwarding). Each tab panel edits the `SiteInfo` given to `setInfo`.
- `vnc/` — `VncSession` (one per site VNC connection, owned by `SSHConnectionHandler`): SSH tunnel
  (`TauonSSHClient.openLocalTunnel`) + external TurboVNC viewer process, monitored with `Process.onExit()`;
  `TurboVncLocator` finds the viewer. Shown in Session Status → VNC.
- `util/misc/PlatformUtils` — OS detection (`IS_WINDOWS`, ...) and launching external programs.
- i18n: `tauon-app/src/main/resources/i18n/messages.properties` (English only, by design). Use `getBundle().getString(key)`.

## Conventions

- **Git:** commit directly on `develop` (jediterm: `master`). Create a branch only when asked (bigger features).
  The user pushes; don't push unless told. When the submodule changes, commit it first and push it **before**
  tauon-ssh, so the submodule pointer exists on GitHub.
- **Changelog:** `CHANGELOG.md`, Keep a Changelog, one line per bullet naming the API or bug; new entries under
  `## [Unreleased]`.
- **Versioning:** Romantic Versioning (`PROJECT.MAJOR.MINOR`). Release tags are plain `vX.Y.Z` on `master`; the
  in-app update checker (`VersionEntry`) reads the latest GitHub release's tag.
- Keep the look and feel: reuse `App.skin` colors/fonts, `SkinnedSplitPane`, `SkinnedScrollPane`, FontAwesome icons
  (`FontAwesomeContants`).
- No new dependencies without asking (e.g. VNC is planned via an external TurboVNC viewer, not a Java library).

## Known issues

- `SitesConfigManager.populatePasswordsInto` never increments its hop index: all jump-host passwords load into the
  first jump host.
- `SiteInfo.proxyPassword` is stored in plain JSON (`// TODO encrypt`).
