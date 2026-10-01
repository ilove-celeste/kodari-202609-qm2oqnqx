# AkumaVote

AkumaVote is a client-side Fabric mod for Minecraft 1.21.11 that provides an in-game voting dashboard for **play.akumamc.net**.

## Features

- Opens the voting dashboard with `/akumavote`.
- Provides two tabs: **Voting** and **Settings**.
- Tracks daily vote status locally and resets statuses when the day changes.
- Marks a site as **Voted** when the **Vote** button is pressed and keeps that state when returning with **Back**.
- Embeds voting websites directly inside Minecraft with **Microsoft WebView2** on supported Windows x64 systems.
- Does not use MCEF-Modern, JCEF, or an external browser.
- Settings contain only **Debug** and **Theme**.
- Debug logging is disabled by default.
- Supports Dark and Light themes.
- Includes **Reset All** for local vote status.

## Supported environment

- Minecraft: **1.21.11**
- Fabric Loader: **0.19.2 or newer**
- Fabric API: required
- Java: **21 or newer**
- OS: **Windows x64**
- Microsoft Edge WebView2 Runtime: required

The embedded browser is a native WebView2 child window hosted over the Minecraft client window.

## Voting sites

The dashboard currently includes 12 configured sites:

1. CurseForge
2. MinecraftServers.org
3. ServersForMinecraft
4. Minecraft-Servers.co
5. Minecraft-MP
6. Minecraft.buzz
7. Minecraft Server List
8. Best Minecraft Servers
9. Minerank
10. Play Minecraft Servers
11. TopG
12. Minecraft Best Servers

The configured URLs are defined in `src/client/java/dev/akumavote/VoteSite.java`.

## Usage

1. Join **play.akumamc.net**.
2. Run `/akumavote`.
3. Select a site and press **Vote**.
4. Complete the site's voting flow in the embedded WebView2 window.
5. Press **Back** to return to the dashboard.

The command is available only while connected to the configured Akuma server.

## Configuration and logs

Local settings and vote data are stored under Minecraft's:

```text
config/akumavote/
```

When Debug is enabled, diagnostic messages are written to:

```text
logs/akumavote.log
```

The native WebView2 bridge writes its diagnostic log to:

```text
logs/akumavote-webview2.log
```

The WebView2 browser profile is stored under:

```text
config/akumavote/webview2/
```

The native bridge DLL is extracted from the mod JAR to:

```text
config/akumavote/native/akumavote-webview2.dll
```

## Building

The project uses Gradle and Fabric Loom. The CI build runs on **Windows** because the native WebView2 bridge is compiled with Microsoft Visual C++.

For a local build:

```text
gradlew build
```

The resulting JARs are written to:

```text
build/libs/
```

The GitHub Actions workflow:

1. Downloads the Microsoft WebView2 SDK.
2. Compiles `native/akuma_webview2.cpp`.
3. Places the resulting DLL in the client resources.
4. Runs the Gradle build.
5. Uploads the generated JAR artifact.

## Project structure

```text
src/main/                       Fabric metadata
src/client/java/dev/akumavote/  Client-side Java sources
src/client/resources/            Native WebView2 resource
native/                          C++ WebView2 bridge
.github/workflows/build.yml      Windows CI build
build.gradle                     Gradle and Fabric Loom configuration
gradle.properties                Minecraft and mod versions
```

## Current version

**1.0.3**

## License

AkumaVote is licensed under the **MIT License**. See the [`LICENSE`](LICENSE) file for the full license text.
