<div align="center">

# KeybindFix

*A small Fabric mod that fixes an annoying vanilla quirk with keyboard-bound inventory keys.*

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2%20%7C%2026.2-3b8526?logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/Loader-Fabric-dbb69b)
![Client-side](https://img.shields.io/badge/Environment-Client--side%20only-blue)
![License](https://img.shields.io/badge/License-MIT-informational)

</div>

---

## What this mod does

Inside any inventory screen — chests, furnaces, crafting tables, your own
inventory, dispensers, containers from other mods — KeybindFix makes the
**"Use Item / Place Block"** and **"Pick Block"** keybinds work correctly
when they're bound to a keyboard key instead of the mouse:

- Pressing "Use" over a slot places a single item, same as a real
  right-click.
- Pressing "Pick Block" over a slot clones the stack, same as a real
  middle-click.
- Holding either key down and moving the cursor across multiple slots
  repeats the click on each new slot, same as holding the corresponding
  mouse button and dragging.

## Requirements

- Minecraft 26.1.2 or 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API (matching version)
- Java 25 to run the game

## Downloads

- **Modrinth:** [link here]
- **CurseForge:** [link here]
- **GitHub Releases:** [Releases](../../releases)

> *(Fill in the store links once the mod is published — for now, the
> GitHub Releases page is the source of truth for jars.)*

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Install Fabric API for the same version and drop it in your `mods` folder.
3. Download the KeybindFix jar from one of the links above and drop it in `mods` too.
4. Launch the game.

There's nothing to configure — it works as soon as it's installed.

## Building from Source

```bash
git clone <this-repo-url>
cd keybindfix
gradle wrapper --gradle-version 9.7.1   # only the first time, see note below
./gradlew build
```

Your jar shows up at `build/libs/keybindfix-<version>.jar`.

This repo doesn't track the Gradle Wrapper binaries, so the first time you
clone it you'll need to generate them yourself with whatever Gradle you
already have installed. After that, always build through `./gradlew` (or
`gradlew.bat` on Windows) rather than a global `gradle` — that's what
keeps everyone building with the exact same Gradle version. Gradle itself
needs to run on JDK 25 for this to work.

## Getting Help

If pressing the key does nothing at all, double check it's not already
bound to something else — Minecraft shows a little warning icon in
Controls when there's a conflict.

If clicking works but dragging to fill multiple slots doesn't, make sure
you're actually holding the key down (not toggling it) and that you're in
Creative mode — the server discards those particular clicks in Survival
on purpose, that's vanilla behavior and not something this mod can change.

If you're running another mod that also messes with inventory clicks
(Mouse Tweaks is the usual suspect) and see odd behavior combining them,
please [open an issue](../../issues) with both mod versions listed — it's
usually just a load-order thing.

For anything else, [open an issue](../../issues) with your Minecraft
version, Fabric Loader version, and the log from `.minecraft/logs/latest.log`.

## Join the community

- **Discord:** [link here]
- **Issues & feature requests:** [GitHub Issues](../../issues)

---

## License

MIT — see [`LICENSE`](LICENSE).