# KeybindFix

A **client-side** Fabric mod for Minecraft **26.1.2 / 26.2** that fixes
three vanilla bugs related to binding keyboard keys (instead of the mouse)
to "Use Item / Place Block" (`keyUse`) and "Pick Block" (`keyPickItem`),
inside any inventory screen (chests, furnaces, crafting tables, the player
inventory, dispensers, containers from other mods, etc.).

> Migrated from the 1.21.11 release. See "Migration notes" below for what
> changed and why.

## Bugs fixed

- **[MC-117771](https://bugs.mojang.com/browse/MC/issues/MC-117771)** —
  Pick Block bound to a keyboard key doesn't fill multiple slots when
  "dragged".
- **[MC-577](https://bugs.mojang.com/browse/MC/issues/MC-577)** — Custom
  Pick/Use bindings block inventory controls that aren't the default bind.
- **[MC-19433](https://bugs.mojang.com/browse/MC/issues/MC-19433)** — Can't
  place a single item (right-click) when "Use Item" is remapped to a
  keyboard key, inside inventory screens.

## How it works (technical summary)

**Important**: Minecraft deliberately ignores the "pressed" state of
keyboard keybinds while a screen (GUI) is open — this is intentional
vanilla behavior, not a bug. Because of this, an implementation based on
`KeyMapping#isDown()` (like an earlier version of this mod) **does not
work** inside chests/inventories.

The correct solution uses `ScreenKeyboardEvents` from Fabric API
(`fabric-screen-api-v1`), the API designed exactly for receiving key
presses inside screens:

1. An `AbstractContainerScreenAccessor` mixin (only `@Accessor`/`@Invoker`,
   no `@Inject`) exposes `hoveredSlot` (the slot under the cursor) and
   `slotClicked(Slot, int, int, ClickType)` (the same logic a real mouse
   click runs) from outside `AbstractContainerScreen`.
2. In `KeybindFixClient`, for every `AbstractContainerScreen` that opens
   (`ScreenEvents.AFTER_INIT`), the following are registered:
   - `ScreenKeyboardEvents.afterKeyPress`: if the key matches
     `keyUse`/`keyPickItem` and there's a slot under the cursor, calls
     `slotClicked` with `button=1(right)/PICKUP` or
     `button=2(middle)/CLONE` — fixes the single-click case (MC-19433,
     MC-577).
   - `ScreenKeyboardEvents.afterKeyRelease`: clears the "drag" state.
   - `ScreenEvents.afterRender`: while the key stays "active", every frame
     checks whether the cursor entered a new slot and repeats the click
     there — fixes the multi-slot drag (MC-117771).

No new keybind or config menu is added — the mod just reuses the existing
vanilla keybinds as-is, as requested (option 1).

## Migration notes (1.21.11 → 26.1.2 / 26.2)

Minecraft 26.1 was the version where Mojang stopped obfuscating the game
and switched to their own year-based version numbering
(`26.1`, `26.1.1`, `26.1.2`, `26.2`, ...). This is a **major** toolchain
change, not just a version bump. What changed in this mod as a direct
consequence:

| Area | 1.21.11 (Yarn) | 26.1.2 / 26.2 (official mappings) |
|---|---|---|
| Loom plugin id | `fabric-loom` | `net.fabricmc.fabric-loom` (new id for **unobfuscated** MC; the old id stays only for ≤1.21.11) |
| `mappings` dependency in `build.gradle` | required (`net.fabricmc:yarn:...`) | **removed** — the game itself already ships official names |
| Mod dependency configs | `modImplementation`, `modApi`, ... | `implementation`, `api`, ... (no remapping needed anymore) |
| Jar task | `remapJar` | `jar` (no intermediate un-remapped jar) |
| Java version | 21 | **25** |
| `HandledScreen` | `net.minecraft.client.gui.screen.ingame.HandledScreen` | `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` |
| `HandledScreen#focusedSlot` | `focusedSlot` | `hoveredSlot` |
| `HandledScreen#onMouseClick` | `onMouseClick(Slot, int, int, SlotActionType)` | `slotClicked(Slot, int, int, ContainerInput)` |
| `Slot` | `net.minecraft.screen.slot.Slot`, field `id` | `net.minecraft.world.inventory.Slot`, field `index` |
| `SlotActionType` | `net.minecraft.screen.slot.SlotActionType` | `net.minecraft.world.inventory.ContainerInput` (constants `PICKUP`, `CLONE`, ... — this is a **vanilla Mojang rename**, done in the same 26.1 jump that removed obfuscation, not a Fabric API rename) |
| `MinecraftClient` | `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `GameOptions` / `useKey` / `pickItemKey` | `net.minecraft.client.option.GameOptions` | `net.minecraft.client.Options`, fields `keyUse` / `keyPickItem` (same field names as GameOptions, only the class/package changed) |
| `KeyBinding#matchesKey(...)` | `matchesKey(input)` | `KeyMapping#matches(input)` (input is still a single key-event object; the parameter type itself already changed in 1.21.9, before this migration) |
| `ScreenEvents.afterRender` | `afterRender(screen).register((scrn, context, mouseX, mouseY, delta) -> ...)` | not used anymore — switched to `afterTick(screen).register(scrn -> ...)` (see caveat below) |
| `fabricloader` / `minecraft` / `java` deps in `fabric.mod.json` | `>=0.18.4` / `~1.21.11` / `>=21` | `>=0.19.3` / `>=26.1.2` / `>=25` |
| Mixin `compatibilityLevel` | `JAVA_21` | `JAVA_25` |

> ⚠️ **One caveat I can't fully verify without a real build**: 26.2 rewrote most of the rendering pipeline (`GuiGraphics` → `GuiGraphicsExtractor`, `render()` → `extractRenderState()`, `renderBg`/`renderLabels` → `extractBackground`/`extractLabels`, `MultiBufferSource` → a new "submit-node" pipeline). Fabric API's `ScreenEvents.afterRender` is tied to that pipeline, and since our per-frame "did the cursor enter a new slot" check never actually used the graphics context it received, I switched it to `ScreenEvents.afterTick(screen)` instead — same idea, runs once per game tick instead of once per frame, and has zero dependency on whatever the render pipeline looks like this version. This sidesteps the whole rename mess rather than chasing it, and 20 checks/second is still instant from a player's perspective.
>
> The one thing I could **not** independently confirm against an official source is the exact shape of `ContainerInput` (whether it's a straight 1:1 rename of the old `ClickType`/`SlotActionType` enum with the same constants, or a wrapper record around it). The evidence I found (a migration changelog for a similar mod, plus a Kotlin migration blog post) both describe it as "just a renamed type used the same way," which is what the code above assumes (`ContainerInput.PICKUP`, `ContainerInput.CLONE`). If the build still fails specifically on `ContainerInput`, run:
> ```bash
> ./gradlew genSources
> ```
> and open the generated `ContainerInput` class (`build/loom-cache`, or via your IDE) to see its real constants/constructor — then adjust `ContainerInput.PICKUP`/`ContainerInput.CLONE` in `KeybindFixClient.java` and `AbstractContainerScreenAccessor.java` accordingly. I'd rather flag this openly than pretend I decompiled the real jar.

Fabric API itself was **not** renamed for the classes this mod uses
(`ScreenEvents`, `ScreenKeyboardEvents`, `KeyMappingHelper` aren't touched
here) — only vanilla-facing renames applied, driven by the switch from
Yarn to Mojang's official names. If you're curious about the full list of
Fabric API renames for 26.1, see the [official porting
guide](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api).

### About targeting both 26.1.2 and 26.2 with one jar

This project compiles against **26.2** (the newest stable release), but
declares `"minecraft": ">=26.1.2"` in `fabric.mod.json` instead of pinning
to `26.2` only. This is intentional: KeybindFix only touches screen/input/
inventory APIs that did **not** change between 26.1.2 and 26.2 — the
headline change in 26.2 is the new optional Vulkan rendering backend,
which this mod never touches. In practice a single build should work
unmodified on both versions.

If you ever see a `NoSuchFieldError`/`NoSuchMethodError` mentioning
`AbstractContainerScreen`, `Slot` or `ClickType` when running on 26.1.2
specifically, that would mean Mojang changed one of those members between
26.1.2 and 26.2 — in that case, build a second jar with
`minecraft_version=26.1.2` and `fabric_version=0.152.1+26.1.2` in
`gradle.properties` (check [fabricmc.net/develop](https://fabricmc.net/develop)
for the exact current numbers) and publish it as a separate file, the same
way most Fabric mods do for major version jumps.

## ⚠️ Required first step: generate the Gradle Wrapper

This project **does not include the Gradle Wrapper**
(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) because those are binaries
and can't be reliably handwritten. Generate it once, using your globally
installed Gradle, **before** compiling:

```powershell
gradle wrapper --gradle-version 9.7.1
```

**Important**: Fabric Loom `1.16.2` requires Gradle with
`plugin.api-version >= 9.2.0` — **it does not work with Gradle 8.x**, so
use 9.7.1 (or any 9.2+), never an 8.x version.

From then on, always use `./gradlew` (or `gradlew.bat` on Windows) instead
of your global `gradle`, so the Gradle version stays pinned and the build
is reproducible:

```powershell
.\gradlew.bat build
```

## About Java 25

Minecraft 26.1+ requires **Java 25** end-to-end: the JDK Gradle runs on
(`org.gradle.java.home` in `gradle.properties`), the `sourceCompatibility`/
`targetCompatibility` in `build.gradle`, and the Mixin
`compatibilityLevel` in `keybindfix.mixins.json` all need to agree on 25.
Unlike the 1.21.11 era (where JDK 21 was recommended for the *build*
tooling even though the target bytecode was 21), by 26.1 the whole
toolchain (Loom, Mixin, ASM) already has first-class Java 25 support, so
there's no longer a reason to keep a separate, older JDK just for Gradle
itself.

If you see class-file-version errors after switching JDKs, stop the old
Gradle daemons first:

```powershell
.\gradlew.bat --stop
```

and confirm with `.\gradlew.bat --version` that the "Daemon JVM" is 25.

## Installation (to play)

1. Install **Fabric Loader** ≥ 0.19.3 for Minecraft 26.1.2 or 26.2.
2. Download **Fabric API** for your target version and drop it into
   `mods/` (`0.152.1+26.1.2` or `0.152.2+26.2` at the time of this
   writing — always check [fabricmc.net/develop](https://fabricmc.net/develop)
   for the current recommended build).
3. Build the mod (see below) or drop the already-built `.jar` into `mods/`.
4. Launch the corresponding Fabric profile (26.1.2 or 26.2).

## Building

```bash
./gradlew build
```

The resulting `.jar` appears at `build/libs/keybindfix-1.0.0.jar`.

## How to test it

1. Open **Options → Controls** and rebind:
   - "Use Item / Place Block" to a keyboard key, e.g. `Control`.
   - "Pick Block" to another keyboard key, e.g. `Alt`.
2. **MC-19433**: open a chest, furnace, crafting table, or your own
   inventory, pick up a stack of an item, and press your "Use" key over
   another slot — it should deposit just one unit, exactly like a real
   right-click.
3. **MC-577**: repeat the test above across different containers (furnace,
   dispenser, crafting table) to confirm it's not blocked in any of them.
4. **MC-117771**: in Creative mode, with an item held on the cursor, hold
   down your "Pick Block" key and move the mouse over several empty
   inventory slots — they should all get filled, just like holding down
   the middle mouse button.

## Possible issues

- **Compile error in the mixin due to a wrong signature**:
  `AbstractContainerScreenAccessor` only references `hoveredSlot` and
  `slotClicked`, two members that have been very stable for many
  versions. If it still fails, run `./gradlew genSources` and open the
  generated `AbstractContainerScreen` class (under `build/loom-cache`, or
  via your IDE after importing the project) to confirm the exact
  field/method name in the version you're building against.
- **Nothing happens when pressing the key**: check that the assigned key
  isn't already conflicting with another keybind (Minecraft warns with a
  ⚠️ on the Controls screen).
- **The click works but the "drag" doesn't (MC-117771)**: confirm you're
  still physically holding the key down (it's not a toggle) and that the
  player is in Creative mode, since `CLONE` gets discarded in Survival by
  the server itself.
- **"could not find any targets matching 'keyReleased'"**: this error came
  from an earlier version of the mod, which injected directly into
  `keyPressed`/`keyReleased`. The current version doesn't use `@Inject` on
  those methods at all (see above), so it shouldn't reappear.
- **Mods like Mouse Tweaks**: if you use mods that also modify inventory
  click behavior (Mouse Tweaks, for example) and notice any odd behavior
  combining them with KeybindFix, let me know — it might need a small
  mod-order/priority adjustment.
