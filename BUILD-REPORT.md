# Build report — MineCalculator multi-loader development port

- Java 21 compilation of `common/src/main/java`: passed.
- JSON syntax validation for all metadata/language resources: passed.
- Shared screen extraction and platform adapter source files: present.
- Full Fabric Gradle build: not run (Gradle executable and Wrapper are absent).
- Full Forge Gradle build: not run.
- Full NeoForge Gradle build: not run.
- In-game tests: not run.

This archive is a development source port. It is not a set of release-ready JARs. Forge and NeoForge API compatibility must be confirmed by running the Gradle builds and launching Minecraft 1.21.11 on each loader.
