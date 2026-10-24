# BotForge — standalone fake-player prototype

BotForge is an original Paper plugin project for Minecraft **1.21.11**. It does not depend on Citizens or another NPC plugin. It uses Paper's internal (NMS) player packets through `paperweight-userdev` to render a player-shaped fake client-side entity, with its own small movement controller.

## Current prototype scope

- `/bf spawn <name>` — spawn a packet-rendered fake player at your position
- `/bf remove <name>` — remove it
- `/bf list` — list bots
- `/bf follow <name>` — make the bot follow the command issuer
- `/bf wander <name>` — make the bot wander locally
- `/bf stop <name>` — stop movement
- no Citizens dependency
- GitHub Actions workflow builds the project

## Honest limitations

This is a **prototype, not a finished full player simulation**. The fake player is packet-rendered and is not a real authenticated Minecraft client or Bukkit `Player`. It currently has no server-authoritative hitbox, inventory, block interaction, or combat. Movement packets and internal Minecraft classes are version-sensitive. The 1.21.11 NMS API should be verified against the exact Paper build you run; the project has not been compiled or integration-tested in this environment.

A production-grade bot needs a server-side simulation layer in addition to the client-visible fake player, plus robust packet resynchronization for players joining later and world changes. That is the next step rather than disguising an ArmorStand or Citizens NPC as a real player.

## Build

Requirements: Java 21, Git, and internet access for Gradle to download Paper's dev bundle.

Windows PowerShell:
```powershell
cd path\to\BotForge
.\gradlew.bat build
```

The JAR should be in `build\libs`. If the `gradlew.bat` wrapper is absent, install Gradle or generate a wrapper with `gradle wrapper`.

## Install

Copy the built JAR into the Paper server's `plugins` folder and restart the server. Grant `botforge.admin` to operators or a chosen permission group.

## GitHub Actions

Push this project to a GitHub repository. The workflow at `.github/workflows/build.yml` compiles the plugin and uploads the JAR as a workflow artifact.

## Why NMS?

Paper's public Bukkit API does not expose a supported way to create genuine fake `Player` entities. Paper's `paperweight-userdev` exposes the server internals needed for this version-specific approach. See PaperMC's developer documentation: https://docs.papermc.io/paper/dev/userdev/
