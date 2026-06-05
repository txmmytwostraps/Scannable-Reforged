# Scannable Reforged

**Scannable Reforged** is a NeoForge **1.21.1** port and fork of [Scannable](https://www.curseforge.com/minecraft/mc-mods/scannable) by Florian "Sangar" Nücke. Equip a scanner, install modules, and have nearby ores, mobs, and other points of interest highlighted right in the world.

## Credit — this is a fork

Full credit for the original mod, its design, and the bulk of this code goes to **Sangar (MightyPirates)**:

- Original mod: https://www.curseforge.com/minecraft/mc-mods/scannable
- Original source: https://github.com/MightyPirates/Scannable

This fork exists to bring Scannable to Minecraft 1.21.1 (NeoForge) and add a couple of features. It is distributed under the same MIT license, with the original copyright notice retained.

## What it does

Craft a **Scanner**, then craft and install **modules** to choose what it finds. Hold-use to charge a scan; a pulse sweeps outward and tags everything in range with floating markers and an on-screen overlay.

Modules:

- **Common Ores** / **Rare Ores** — tag-driven (`c:ores`), so modded ores work out of the box
- **Animals** / **Monsters** — by mob category, so modded mobs are included
- **Living Entity (configurable)** — target specific mobs via spawn eggs
- **Block (configurable)** — target specific blocks
- **Fluids** — find tagged fluids
- **Chests** — chests, barrels, shulker boxes (and modded storage tagged `c:chests`/`c:barrels`)
- **Spawners** *(new in this fork)* — find mob spawners
- **Range** — extend scan distance

## What this fork changes

- **Ported to Minecraft 1.21.1 (NeoForge)** — the original targets up to 1.20.4.
- **New module — Scanner Module: Spawners** (`scannable:spawner_module`): highlights nearby mob spawners (vanilla; trial spawners / vaults can be added via config).
- **Increased the scanner's internal energy (FE) capacity** from **5,000 to 20,000**.

## Compatibility

- Minecraft **1.21.1**, **NeoForge** only (Fabric/Forge not currently provided).
- Tested against a large modded pack (AllTheOres, Allthemodium, Occultism, Sophisticated Storage…) and Iris + Sodium shaders.

## License & use in modpacks

Code is licensed under the **MIT License** (the original copyright notice is retained). Assets are released under **CC0 1.0**. Free to use in any modpack, public or private, as long as the license and source credits are kept — same spirit as the original.
