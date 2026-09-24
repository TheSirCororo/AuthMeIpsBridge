# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AuthMeIpsBridge (repo/Gradle project name `McIpsAuth`) is a Bukkit/Paper plugin written in Kotlin. It mirrors AuthMe Reloaded accounts onto an Invision Power Suite (IPS) forum through the IPS REST API. When a player registers, logs in, changes their password or unregisters in Minecraft, the matching forum member is created, updated or deleted. The plugin targets servers from 1.8.x to 1.20.x. That is why it compiles against `paper-api:1.12.2` with a Java 11 toolchain. Do not raise these without a reason.

## Build and test

- `./gradlew shadowJar`: builds the deployable plugin jar in `build/libs/` (base name `AuthMeIpsBridge`). H2 and the Kotlin stdlib are shaded into it. Paper and AuthMe are `compileOnly`, and Gson comes from the server at runtime.
- `./gradlew test`: runs the JUnit 5 tests. Run one class with `./gradlew test --tests '*IpsClientTest'`, or one test by its backtick name with `./gradlew test --tests '*IpsClientTest.times out*'`.
- Tests cover only the Bukkit-free classes (`AuthCommand`, `Config`, `Database`, `IpsClient`). `IpsClientTest` runs against a local `com.sun.net.httpserver.HttpServer`. Keep new logic out of `McIpsAuth` when possible so it stays testable without a server.
- Tests use Gson 2.8.0, the version bundled with paper-api 1.12.2. The code has to work with the older Gson of 1.8 servers too, which is why `IpsClient` uses the instance `JsonParser().parse`.
- JUnit stays on 5.x: JUnit 6 requires Java 17, and the toolchain is 11.
- The version is set only in `build.gradle.kts`. `processResources` copies it into `plugin.yml` (`${version}`).

## CI and releases

`.github/workflows/build.yml` builds and tests every push and PR. On a push to `master`, if no tag matches the Gradle `version` yet, the workflow creates a GitHub release with the plain version as the tag (e.g. `1.0.3`, no `v`), titled `Version X`, with the shaded jar attached. `-SNAPSHOT` versions are never released. To publish a release, bump `version` and push to `master`.

## Architecture

All code is in `src/main/kotlin/ru/cororo/mcipsauth/`:

- `McIpsAuth.kt`: the plugin main class, which is also the only `Listener` and the `/fixips` command executor.
  - Passwords reach the plugin only as plaintext from typed commands. A `MONITOR` `PlayerCommandPreprocessEvent` handler parses them with `AuthCommand`. Passwords from `/login` and `/register` are held in `typedPasswords` until AuthMe fires `LoginEvent` or `RegisterEvent`, and are dropped on quit.
  - Before anything goes to the forum, the password is checked with `AuthMeApi.checkPassword`. This is the security boundary: a failed or mistyped command must never change forum state.
  - A forum member is created on login or registration only when the local DB has no ID for the player, or when `/fixips <name>` (permission `mcipsauth.admin`) queued the name in `pendingFix`.
  - For `/changepassword` there is no AuthMe event. The plugin polls `checkPassword` for the new password (up to 5 times, once a second) before it updates the forum.
  - Unregistering uses AuthMe's `UnregisterByPlayerEvent` and `UnregisterByAdminEvent`. The admin event has no online player, so it uses `playerName`.
  - All HTTP and DB work goes through `runAsync`, never on the server thread.
- `IpsClient.kt`: a blocking client for `{forum_url}/api/index.php?/core/members[/id]`, with connect and request timeouts. It authenticates with HTTP Basic, using the API key as the username. IPS reports errors in a JSON `errorMessage`/`errorCode` body, sometimes with a 2xx status, and the client turns that into `IpsResult.Failure`.
- `Database.kt`: an H2 file at `<worldContainer>/plugins/AuthMeIpsBridge/database.h2(.mv.db)`, kept at this path for compatibility with existing installs. Its single table `forum_users(username, forum_id)` maps Minecraft names to IPS member IDs. The methods are `@Synchronized` because they are called from async tasks.
- `Config.kt`: validates `config.yml`. The plugin disables itself if `forum_url` is not http(s) or `api_key` is missing or still the placeholder, and it warns when the URL is plain `http://`.
