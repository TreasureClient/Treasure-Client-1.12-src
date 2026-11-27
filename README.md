# Treasure Launcher

A Minecraft launcher for Treasure Client, written in Rust. It runs on Linux (.deb, .rpm, .AppImage), Windows and macOS.

- **Instances:** each one has its own mods, settings and worlds. New launchers start with Treasure Pack 26.3 and Treasure Pack 1.21.11, which download the first time you press Play.
- **New instance:** pick *With Treasure Client* (Fabric + Treasure Client + Fabric API), *Without* (plain Fabric or vanilla), or *From a modpack* (any Modrinth modpack or a `.mrpack` file; you can also drop one on the window).
- **Modrinth built in:** browse modpacks, mods, resource packs and shaders, and add them to an instance. Required mods come along automatically. *Update all* only offers full releases.
- **Java is automatic:** the launcher uses Mojang's own Java runtimes, the right one for each version.
- **Every download is checked** against its SHA-1 and size, and *Repair* re-checks everything.
- **Sign-in** uses Microsoft's code flow, so the launcher never sees your password. You need an account that owns Minecraft: Java Edition, and there's no offline/cracked play.
- **No ads, no tracking.** The launcher only talks to Mojang, Microsoft, Fabric, Modrinth and the Treasure Client website.

## Layout

| Folder | What's in it |
|---|---|
| `core/` | The engine (plain Rust, no window). It handles Mojang versions, libraries, assets, Java, Fabric, Modrinth, `.mrpack`, instances, sign-in and launching. |
| `cli/` | `tl`, a command-line tool to test the engine (`cargo run -p tl -- help`). |
| `app/src-tauri/` | The window (Tauri 2). It connects the page to the engine. |
| `app/ui/` | The page: plain HTML/CSS/JS in Treasure Client's themes. Fonts are bundled. |
| `tools/make_channel.py` | Writes `channel.json`, which tells the launcher where Treasure Client and the Treasure Pack are for each version. |

## Building

- **Engine and test tool:** `cargo build` / `cargo test -p treasure-core` (no system libraries needed).
- **Installers:** built by GitHub Actions (`.github/workflows/launcher.yml`) on every push to the `Launcher` branch. Download them from the run's *Artifacts*.
- **Microsoft sign-in:** it turns on when the Azure app's client ID is set, either as the `TREASURE_MS_CLIENT_ID` repository variable or in `core/src/auth.rs`.

## Where files go

| System | Folder |
|---|---|
| Linux | `~/.local/share/treasure-launcher` |
| Windows | `%APPDATA%\TreasureLauncher` |
| macOS | `~/Library/Application Support/TreasureLauncher` |

Games, libraries, assets and Java are shared between instances, so each Minecraft version only downloads once.

## Credits

Fonts: Archivo (Omnibus-Type) and JetBrains Mono (JetBrains), both under the SIL Open Font License; see `app/ui/fonts/README.txt`.
