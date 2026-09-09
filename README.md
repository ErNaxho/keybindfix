# KeybindFix

A **client-side** Fabric mod for Minecraft **1.21.11** that fixes three
vanilla bugs related to binding keyboard keys (instead of the mouse) to
"Use Item / Place Block" (`useKey`) and "Pick Block" (`pickItemKey`),
inside any inventory screen (chests, furnaces, crafting tables, the player
inventory, dispensers, containers from other mods, etc.).

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
`KeyBinding#isPressed()` (like an earlier version of this mod) **does not
work** inside chests/inventories.

The correct solution uses `ScreenKeyboardEvents` from Fabric API
(`fabric-screen-api-v1`), the API designed exactly for receiving key
presses inside screens:

1. A `HandledScreenAccessor` mixin (only `@Accessor`/`@Invoker`, no
   `@Inject`) exposes `focusedSlot` (the slot under the cursor) and
   `onMouseClick(Slot, int, int, SlotActionType)` (the same logic a real
   mouse click runs) from outside `HandledScreen`.
2. In `KeybindFixClient`, for every `HandledScreen` that opens
   (`ScreenEvents.AFTER_INIT`), the following are registered:
   - `ScreenKeyboardEvents.afterKeyPress`: if the key matches
     `useKey`/`pickItemKey` and there's a slot under the cursor, calls
     `onMouseClick` with `button=1(right)/PICKUP` or
     `button=2(middle)/CLONE` — fixes the single-click case (MC-19433,
     MC-577).
   - `ScreenKeyboardEvents.afterKeyRelease`: clears the "drag" state.
   - `ScreenEvents.afterRender`: while the key stays "active", every frame
     checks whether the cursor entered a new slot and repeats the click
     there — fixes the multi-slot drag (MC-117771).

No new keybind or config menu is added — the mod just reuses the existing
vanilla keybinds as-is, as requested (option 1).

## ⚠️ Before compiling for the first time

Minecraft 1.21.11 reworked the input system (new `KeyInput`, `Click`,
`MouseButtonInfo` records in `net.minecraft.client.input`). I verified in
the official Yarn mappings for `1.21.11+build.4` that:

- `HandledScreen.keyPressed(KeyInput)` / `keyReleased(KeyInput)` — signature
  used in this mod.
- `GameOptions.useKey` / `pickItemKey` and `KeyBinding.matchesKey(KeyInput)`
  — confirmed.
- `HandledScreen.focusedSlot` and
  `onMouseClick(Slot, int, int, SlotActionType)` — unchanged for many
  versions.
- `render(DrawContext, int, int, float)` — unchanged.

Even so, **before the first real build**, run:

```bash
./gradlew genSources
```

and open the generated `HandledScreen` class (under `build/loom-cache`, or
via your IDE after importing the project) to confirm that the exact
signatures of `keyPressed`, `keyReleased`, and `render` match the ones used
in the mixin. If a later Yarn build changes something, you'll only need to
adjust the signature of the corresponding `@Inject` method.

## ⚠️ Required first step: generate the Gradle Wrapper

This project **does not include the Gradle Wrapper**
(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) because those are binaries
and can't be reliably handwritten. Generate it once, using your globally
installed Gradle, **before** compiling:

```powershell
gradle wrapper --gradle-version 9.7.1
```

**Important**: Fabric Loom `1.14.10` requires Gradle with
`plugin.api-version >= 9.2.0` — **it does not work with Gradle 8.x**, so
use 9.7.1 (or any 9.2+), never an 8.x version.

From then on, always use `./gradlew` (or `gradlew.bat` on Windows) instead
of your global `gradle`, so the Gradle version stays pinned and the build
is reproducible:

```powershell
.\gradlew.bat build
```

## About the "Unsupported class file major version 69" error (Java 25)

This error shows up if Gradle runs on a JDK 25 with a Gradle version that
doesn't support it (Gradle 8.x, or 9.0). With the wrapper already on
**9.7.1** (see above) this shouldn't happen again, since full Java 25
support landed in Gradle 9.1.0. Even so, **it's still recommended to use
JDK 21** to run Gradle in this project (see `org.gradle.java.home` in
`gradle.properties`), because Loom, Mixin, and the internal decompilers
(ASM-based) are built and tested against Java 21, not 25 — using 25 could
cause subtle issues down the line even though Gradle itself now understands
it.

If you change the `org.gradle.java.home` path or remove that line, and
this error comes back, stop the old daemons first:

```powershell
.\gradlew.bat --stop
```

and confirm with `.\gradlew.bat --version` that the "Daemon JVM" is 21.

## About the "Unsupported unpick version" error

If you see this error, it means your **Fabric Loom** version is too old
for the `unpick v3` format used by Minecraft 1.21.11. This project already
pins Loom to `1.14.10` in `build.gradle` (the line
`id 'fabric-loom' version '1.14.10'`). If it still fails, check
[fabricmc.net/develop](https://fabricmc.net/develop) for a newer Loom
version and update that line.

## Installation (to play)

1. Install **Fabric Loader** ≥ 0.18.4 for Minecraft 1.21.11.
2. Download **Fabric API** for 1.21.11 and drop it into `mods/`.
3. Build the mod (see below) or drop the already-built `.jar` into `mods/`.
4. Launch the Fabric 1.21.11 profile.

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
  `HandledScreenAccessor` only references `focusedSlot` and `onMouseClick`,
  two members that have been very stable for many versions. If it still
  fails, run `genSources` (see above) to confirm the exact field/method
  name.
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