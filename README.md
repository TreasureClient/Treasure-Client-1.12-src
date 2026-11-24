# Treasure Client for Minecraft (Fabric)

Treasure Client as a Fabric mod for real Minecraft: the 1.5 main menu, the Mod Menu with themes and
backgrounds, HUD mods, cosmetics and a Mods list.

## Download

Ready-made jars are in [`builds/`](builds). Pick the one for your Minecraft version, put it in your
`mods` folder with Fabric Loader and Fabric API.

| Minecraft | Jar |
|---|---|
| 1.21.11 | `treasure-client-1.5.0+1.21.11.jar` |
| 26.1 | `treasure-client-1.5.0+26.1.jar` |
| 26.1.2 | `treasure-client-1.5.0+26.1.2.jar` |
| 26.2 | `treasure-client-1.5.0+26.2.jar` |
| 26.3 | `treasure-client-1.5.0+26.3.jar` |

These builds are a beta and haven't been tested much yet.

## Building

Needs Java 25. `tools/build_all.sh` builds every version into `builds/` (or give it versions, e.g.
`tools/build_all.sh 26.3`). `./gradlew build -Pmc=<version>` builds one into `build/libs/`.

MIT License (see `LICENSE`).
