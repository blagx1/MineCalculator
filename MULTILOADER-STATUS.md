# MineCalculator — multi-loader development port

Target: Minecraft Java 1.21.11, Java 21.

## Changes in this source package
- Existing screen moved without intentional UI redesign from `fabric` into `client-common`, so loader adapters can reuse one screen implementation.
- Fabric entrypoint now imports the shared screen.
- Added Forge and NeoForge module scaffolding, metadata and client keybinding/tick adapters.
- Added RU/EN language resource copies to Forge and NeoForge modules.
- Existing `common` math, history and config sources remain unchanged.

## Validation status
- `common` Java sources compile with Java 21 (`javac`).
- All JSON resource files parse successfully.
- Full Gradle builds have NOT been run: this environment has no Gradle executable or Gradle Wrapper in the supplied archive.
- Forge/NeoForge Minecraft API signatures and runtime behavior remain unverified.

## Before publishing
Run `:fabric:build`, `:forge:build`, `:neoforge:build` using Gradle/JDK 21, then launch each loader on Minecraft 1.21.11 and test screen opening, input, history/config persistence, RU/EN display and dedicated-server safety. Do not publish the loader JARs until those checks pass.
