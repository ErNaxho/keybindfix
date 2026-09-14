<div align="center">

# KeybindFix

**A tiny client-side Fabric mod that fixes three long-standing vanilla bugs**
**around keyboard-bound "Use Item" and "Pick Block" in inventory screens.**

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2%20%7C%2026.2-3b8526?logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/Loader-Fabric-dbb69b)
![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Side](https://img.shields.io/badge/Environment-Client--side%20only-blue)
![License](https://img.shields.io/badge/License-MIT-informational)

</div>

---

## What is this?

If you rebind **"Use Item / Place Block"** or **"Pick Block"** away from the
mouse and onto a keyboard key, Minecraft's own inventory screens (chests,
furnaces, crafting tables, your own inventory, and containers added by other
mods) start behaving inconsistently. KeybindFix makes those keys behave
**exactly like their default mouse buttons** inside any inventory screen —
nothing more, nothing less. No new keybind, no config screen, no dependency
beyond Fabric API.

## Bugs fixed

| Ticket | Problem |
|---|---|
| [MC-19433](https://bugs.mojang.com/browse/MC/issues/MC-19433) | Can't place a single item (right-click behavior) when "Use Item" is bound to a keyboard key, inside inventory screens. |
| [MC-577](https://bugs.mojang.com/browse/MC/issues/MC-577) | Custom Pick/Use keybinds get ignored for inventory actions other than the default mouse bind. |
| [MC-117771](https://bugs.mojang.com/browse/MC/issues/MC-117771) | Holding "Pick Block" and dragging the cursor doesn't fill multiple slots, unlike holding the middle mouse button. |

## Requirements

- **Minecraft** 26.1.2 or 26.2
- **Fabric Loader** ≥ 0.19.3
- **Fabric API** (matching your Minecraft version)
- **Java** 25 to run the game

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2 or 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for the same version and drop it in `mods/`.
3. Download `keybindfix-<version>.jar` from the [Releases](../../releases) page and drop it in `mods/` too.
4. Launch the game with the matching Fabric profile.

## How it works

Minecraft deliberately ignores the "held down" state of a keyboard keybind
while an inventory screen is open — that's intentional vanilla behavior, not
a bug, so polling `KeyMapping#isDown()` from inside a screen doesn't work.

KeybindFix instead hooks into `ScreenKeyboardEvents`, the Fabric API built
specifically for reacting to key presses while a screen is open:

1. An accessor-only mixin (`@Accessor`/`@Invoker`, no `@Inject`) exposes the
   slot currently under the cursor and the same internal method a real
   mouse click runs, from `AbstractContainerScreen`.
2. On every inventory screen that opens, KeybindFix listens for the "Use"
   and "Pick Block" keys and replays the equivalent click (`PICKUP` /
   `CLONE`) on the hovered slot.
3. While the key stays held, a per-tick check repeats the click whenever the
   cursor enters a new slot — this is what fixes the drag-fill behavior.

No vanilla method is overridden or replaced — the mod purely observes key
events and reuses existing, untouched game logic to perform the click.

## Building from source

```bash
git clone <this-repo-url>
cd keybindfix
gradle wrapper --gradle-version 9.7.1   # first time only, see note below
./gradlew build
```

The compiled jar appears at `build/libs/keybindfix-<version>.jar`.

> **Note:** the Gradle Wrapper binaries aren't tracked in this repo. Run
> `gradle wrapper --gradle-version 9.7.1` once with your own Gradle
> installation before the first build; every build afterwards should go
> through `./gradlew` (or `gradlew.bat`), never a global `gradle` install,
> so the version stays pinned. Gradle needs to run on **JDK 25**.

## Testing checklist

- [ ] Rebind "Use Item / Place Block" and "Pick Block" to keyboard keys in **Options → Controls**.
- [ ] **MC-19433**: pick up a stack, press "Use" over another slot → deposits exactly one item.
- [ ] **MC-577**: repeat across chests, furnaces, dispensers, crafting tables.
- [ ] **MC-117771**: in Creative, hold "Pick Block" and drag over several empty slots → all get filled.

## Troubleshooting

- **Nothing happens when pressing the key** — check the key isn't already
  bound elsewhere; Minecraft flags conflicts with a ⚠️ in Controls.
- **Click works but drag doesn't** — make sure the key is actually held
  down (not a toggle) and that you're in Creative mode; `CLONE` clicks are
  discarded in Survival by the server itself.
- **Conflicts with other inventory mods** (e.g. Mouse Tweaks) — please open
  an issue with the other mod's name; it may just need a small
  load-order/priority tweak.

<details>
<summary><strong>Development notes: mappings, versions, and internals</strong> (click to expand)</summary>

### Toolchain

- Fabric Loom `1.16.2`, plugin id `net.fabricmc.fabric-loom` (the id for
  **unobfuscated** Minecraft; the classic `fabric-loom` id is only for
  1.21.11 and earlier).
- No `mappings` dependency — 26.1+ ships Mojang's official names directly,
  no obfuscation, no Yarn.
- Regular `implementation`/`api` Gradle configs instead of
  `modImplementation`/`modApi` (nothing gets remapped anymore).
- `jar` produces the final artifact directly — there's no more
  intermediate un-remapped jar, so `remapJar` is gone.
- Java 25 throughout: Gradle's JVM, `sourceCompatibility`/
  `targetCompatibility`, and the Mixin `compatibilityLevel` all need to
  agree on `JAVA_25`.

### One jar, two Minecraft versions

This project builds against **26.2** but declares `"minecraft": ">=26.1.2"`
in `fabric.mod.json`. KeybindFix only touches slot/click/keybind APIs that
are identical between 26.1.2 and 26.2 — the headline change in 26.2 is an
optional Vulkan rendering backend this mod never touches — so one jar
should cover both. If a future build ever throws a
`NoSuchMethodError`/`NoSuchFieldError` on one of the classes below when run
on 26.1.2 specifically, build a second jar pinned to
`minecraft_version=26.1.2` / the matching Fabric API build and publish it
as a separate release asset.

### Yarn (1.21.11) → official mappings (26.1.2 / 26.2)

| Old (Yarn) | New (official) |
|---|---|
| `net.minecraft.client.gui.screen.ingame.HandledScreen` | `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` |
| `HandledScreen#focusedSlot` | `AbstractContainerScreen#hoveredSlot` |
| `onMouseClick(Slot, int, int, SlotActionType)` | `slotClicked(Slot, int, int, ContainerInput)` |
| `net.minecraft.screen.slot.Slot`, field `id` | `net.minecraft.world.inventory.Slot`, field `index` |
| `net.minecraft.screen.slot.SlotActionType` | `net.minecraft.world.inventory.ContainerInput` (vanilla rename, same constants: `PICKUP`, `CLONE`, ...) |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `net.minecraft.client.option.GameOptions` | `net.minecraft.client.Options` (fields `keyUse`/`keyPickItem` unchanged) |
| `KeyBinding#matchesKey(input)` | `KeyMapping#matches(input)` |
| `ScreenEvents.afterRender(screen)` | replaced with `ScreenEvents.afterTick(screen)` — this mod never used the render context, so it moved off the (heavily reworked, in 26.2) render pipeline entirely and onto the tick loop instead |

`ContainerInput` is assumed to keep the same constant names as the old
`ClickType`/`SlotActionType` enum it replaces, based on two independent
migration write-ups for other mods; it was not verified against a
decompiled 26.2 jar. If a build ever fails specifically on
`ContainerInput`, run `./gradlew genSources` and check the generated class
under `build/loom-cache`.

Fabric API's own classes used here (`ScreenEvents`, `ScreenKeyboardEvents`)
were **not** renamed in this jump — only vanilla-facing names changed, as
part of Fabric's move from Yarn to Mojang's official mappings. Full list:
[Fabric API 26.1 porting guide](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api).

</details>

## License

MIT — see [`LICENSE`](LICENSE).