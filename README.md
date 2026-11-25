# FakePlayerManager

A small **server-side Fabric addon** for Minecraft Java 1.21.11. It queues VexBot spawn commands over time and can apply an existing VexBot kit after each successful spawn.

## Important status

This is a starter source project, not a tested release JAR. It has not been compiled against your exact server installation. VexBot command syntax and permission behavior can vary by version; test with 1–2 bots first.

The addon delegates bot creation and kit handling to VexBot. It does not create its own fake-player implementation.

## Requirements

- Minecraft Java 1.21.11
- Fabric Loader 0.19.3 or newer compatible with 1.21.11
- Fabric API 0.141.6+1.21.11
- Java 21
- VexBot for Minecraft 1.21.11 (Fabric)
- An operator account with permission to run VexBot commands

## Commands

```text
/fakeplayer spawn <count>
/fakeplayer spawn <count> <kit>
/fakeplayer count
/fakeplayer clear
```

Examples:

```text
/fakeplayer spawn 20
/fakeplayer spawn 20 duelist
/fakeplayer count
/fakeplayer clear
```

`<kit>` is the ID/name of a kit already saved in VexBot. The addon sends VexBot's `givekit <kit> <target>` command after a successful spawn.

## Performance notes

- Maximum managed count: 500.
- Default spawn rate: one bot per server tick.
- This reduces the initial burst but does not make 500 full fakeplayers cheap.
- VexBot bots are real server-side player-like objects with their own update/network costs. Monitor MSPT, memory, and player client FPS; raise the spawn count gradually.
- The queue is in memory and is cleared on server restart.
- `/fakeplayer clear` only removes bots created/tracked by this addon during the current server session.

## Build

Install JDK 21, then in this folder run:

Windows:
```bat
gradlew.bat build
```

Linux/macOS:
```bash
./gradlew build
```

The JAR should appear in:

```text
build/libs/
```

## GitHub

Create a new repository, upload this project (including `gradlew`, `gradlew.bat`, and the `gradle/` wrapper folder if you add the wrapper), then use **Actions** or build locally. Do not upload only the `.java` file; Gradle needs the project files too.

## Known limitations to resolve during testing

1. Confirm the exact VexBot version and its command syntax.
2. Confirm VexBot accepts explicit coordinates from a player command source in your dimension.
3. Confirm your kit ID is accepted by `/vexbot givekit`.
4. If VexBot's spawn command reports success with a zero result, adjust the success check in `processQueue`.
5. The stored `dimension` is currently informational; VexBot uses the executor's current dimension. Keep the spawning player in the intended dimension until the queue finishes.
