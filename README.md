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

<details>
<summary><strong>For contributors: mappings, versions, and how it works internally</strong></summary>

<br>

### Why this needs a mod at all

Minecraft *deliberately* stops listening to whether a keyboard key is
"held down" the moment an inventory screen opens — that's intentional, or
things like walking or attacking would fire weirdly while a chest is open
— but it also means the naive fix (poll the key every frame) never works
once you're inside a GUI. This is the root cause behind
[MC-19433](https://bugs.mojang.com/browse/MC/issues/MC-19433),
[MC-577](https://bugs.mojang.com/browse/MC/issues/MC-577), and
[MC-117771](https://bugs.mojang.com/browse/MC/issues/MC-117771).

KeybindFix hooks directly into Fabric's screen-keyboard events, which *do*
fire correctly while a screen is open. When it sees "Use" or "Pick Block"
pressed over a slot, it triggers the exact same internal click logic the
game already runs for a real mouse click — same code path, same result.
While the key stays held, it also checks which slot the cursor is over on
every game tick, so dragging across slots keeps clicking each new one.

There's a small accessor-only mixin (`@Accessor`/`@Invoker`, no
`@Inject`) involved to reach two members of the inventory screen class
that aren't normally exposed to other mods — the hovered slot, and the
internal click method — but nothing is overridden or replaced.

### Porting from 1.21.11 to 26.1.2 / 26.2

This mod was originally written for 1.21.11 and later ported. That jump
is bigger than it looks, because 26.1 is the version where Mojang stopped
shipping an obfuscated jar and switched to their own official names
directly — no more Yarn, no more remapping step. Worth knowing if you're
touching this code:

- Loom uses the plugin id `net.fabricmc.fabric-loom` now (a separate
  legacy id exists for pre-26.1, obfuscated versions), there's no
  `mappings` dependency anymore, and mod dependencies use the plain
  `implementation`/`api` configs instead of `modImplementation`/`modApi`
  since nothing needs remapping. The build produces the final jar
  straight from `jar` — `remapJar` is gone.
- Everything needs to agree on Java 25: Gradle's own JVM, the compiler's
  source/target compatibility, and the Mixin `compatibilityLevel`.
- A bunch of classes got renamed as part of the same jump: `HandledScreen`
  is now `AbstractContainerScreen`, `MinecraftClient` is `Minecraft`,
  `GameOptions` is `Options`, and the old `SlotActionType`/`ClickType`
  enum became `ContainerInput` (same constants — `PICKUP`, `CLONE`, etc.
  — just a new name). None of that is Fabric API's doing, it's Mojang's
  own official mappings replacing Yarn's community names.
- Fabric API's `ScreenEvents.afterRender` also got dropped in favor of
  `ScreenEvents.afterTick` here — not because it disappeared, but because
  26.2 rewrote a good chunk of the rendering pipeline
  (`GuiGraphics` → `GuiGraphicsExtractor`, among other things) and this
  mod never actually used the render context it was handed anyway. Moving
  to a per-tick check instead of per-frame sidesteps all of that, and
  nobody can tell the difference in practice.

One honest caveat: I couldn't fully verify `ContainerInput`'s exact shape
against a decompiled jar, just against a couple of other mods' migration
notes that describe it as a drop-in rename. If a build ever fails
specifically there, `./gradlew genSources` and checking the generated
class under `build/loom-cache` will tell you for sure.

This project builds against 26.2 but declares compatibility down to
26.1.2 in `fabric.mod.json`, since nothing this mod touches changed
between those two versions — the only headline change in 26.2 is an
optional Vulkan renderer this mod never goes near. If that ever turns out
to be wrong for some future patch, building a second jar pinned to
26.1.2's Fabric API build and publishing it separately is the standard
fix.

</details>

## License

MIT — see [`LICENSE`](LICENSE).