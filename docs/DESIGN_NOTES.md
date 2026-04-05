# Design notes

## What changed in this revision

- Added a persistent JSON config at `config/vitalpopups.json`.
- Added optional Mod Menu integration through the `modmenu` entrypoint.
- Added an in-game config screen implemented with vanilla widgets instead of an external config UI library.
- Kept the gameplay/config implementation in one main Java file.
- Added a second tiny Java file only for the optional Mod Menu bridge, because putting `ModMenuApi` directly on the main client class would make Mod Menu a hard runtime dependency.
- Corrected the Gradle dependency setup to use Loom mod dependencies.

## Publishability

The implementation remains original in structure and code style. It does not mirror the linked HealthIndicators repository's multi-loader Architectury layout or its implementation details.

## Known uncertainty

This container cannot resolve Minecraft/Fabric dependencies from the network, so a real Gradle compile could not be executed here. The current code is written against the 26.1.1-era Fabric/Mod Menu APIs, but if Mojang or Fabric changed a mapped class name again, the most likely adjustment points are:

1. `LevelRenderEvents.END_MAIN` / `LevelRenderContext`
2. vanilla screen widget class names or signatures
3. Mod Menu's API package staying at `com.terraformersmc.modmenu.api`

These are low-to-moderate risk areas, not confirmed build failures.
