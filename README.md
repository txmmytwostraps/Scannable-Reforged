# Scannable Reforged

A NeoForge **1.21.1** port and fork of [Scannable](https://www.curseforge.com/minecraft/mc-mods/scannable) by Florian "Sangar" Nücke. Point a scanner at the world and have nearby ores, mobs, and other points of interest highlighted, based on the modules you install.

## Credits & original project

This project is a fork of **Scannable** by **Florian "Sangar" Nücke** (MightyPirates).

- Original CurseForge: https://www.curseforge.com/minecraft/mc-mods/scannable
- Original GitHub: https://github.com/MightyPirates/Scannable

All credit for the original mod, its design, and the bulk of this code belongs to Sangar. This fork exists to bring Scannable to Minecraft 1.21.1 (NeoForge) and add a few changes. It is distributed under the same MIT license, with the original copyright notice retained.

## What this fork changes

- **Ported to Minecraft 1.21.1 / NeoForge** (the original targets up to 1.20.4).
- **Increased the scanner's energy storage capacity** (5,000 -> 20,000 FE), so the scanner holds more charge between recharges.
- **Added a new scanner module: Spawners** — detects mob spawners. Defaults to the vanilla `minecraft:spawner` block, which also covers mods that enhance it in place (e.g. Apotheosis). Trial-chamber blocks (`minecraft:trial_spawner`, `minecraft:vault`) or other modded spawner blocks can be added via the config.

## Minecraft version / loader

- Minecraft 1.21.1
- NeoForge only (Fabric/Forge not currently provided)

## License

Code is licensed under the [MIT License](LICENSE), retaining the original copyright of Florian "Sangar" Nücke alongside this fork's. Assets (textures and localization), including those added in this fork, are released under CC0 1.0 Universal (public domain) unless otherwise noted.

## Use in modpacks

Free to use in any modpack, public or private, as long as the license and source credits are kept — same spirit as the original.

## Extending

Custom scanning logic and modules are supported through the original scan result provider API; see the `api` package.
