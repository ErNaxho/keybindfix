# KeybindFix

Fabric **client-side** mod for Minecraft **1.21.11** that fixes three vanilla bugs related to assigning keyboard keys (instead of mouse buttons) to **"Use Item / Place Block"** (`useKey`) and **"Pick Block"** (`pickItemKey`) inside any inventory screen (chests, furnaces, crafting tables, player inventory, dispensers, containers from other mods, etc.).

## Bugs it fixes

* **[MC-117771](https://bugs.mojang.com/browse/MC/issues/MC-117771)** — Pick Block assigned to a keyboard key does not fill multiple slots when "dragging".

* **[MC-577](https://bugs.mojang.com/browse/MC/issues/MC-577)** — Custom Pick/Use keybinds block inventory controls that aren't assigned to the default bind.

* **[MC-19433](https://bugs.mojang.com/browse/MC/issues/MC-19433)** — You cannot place a single item (right-click) when "Use Item" is remapped to a keyboard key inside inventory screens.

## How it works (technical overview)

**Important**: Minecraft deliberately ignores the "pressed" state of keyboard keybinds while a screen (GUI) is open — this is intentional vanilla behavior, not a bug. Therefore, an implementation based on `KeyBinding#isPressed()` (like an earlier version of this mod) **does not work** inside chests/inventories.

The correct solution uses Fabric API's `ScreenKeyboardEvents` (`fabric-screen-api-v1`), the API specifically designed to receive keyboard input while screens are open:

1. A `HandledScreenAccessor` mixin (only `@Accessor`/`@Invoker`, with no `@Inject`) exposes `focusedSlot` (the slot under the cursor) and `onMouseClick(Slot, int, int, SlotActionType)` (the same logic executed by an actual mouse click) from outside `HandledScreen`.

2. In `KeybindFixClient`, for each `HandledScreen` that opens (`ScreenEvents.AFTER_INIT`), the following are registered:

   * `ScreenKeyboardEvents.afterKeyPress`: if the key matches `useKey`/`pickItemKey` and there is a slot under the cursor, calls `onMouseClick` with `button=1(right)/PICKUP` or `button=2(middle)/CLONE` — fixes the individual click (MC-19433, MC-577).

   * `ScreenKeyboardEvents.afterKeyRelease`: clears the "dragging" state.

   * `ScreenEvents.afterRender`: while the key is still "active", each frame checks whether the cursor has moved onto a new slot and repeats the click there — fixes multi-slot dragging (MC-117771).

No new keybinds or configuration menus are added — the mod reuses the existing vanilla keybinds exactly as requested (option 1).

## ⚠️ Before compiling for the first time

Minecraft 1.21.11 rewrote the input system (new `KeyInput`, `Click`, and `MouseButtonInfo` records in `net.minecraft.client.input`).

I have verified in the official Yarn mappings for `1.21.11+build.4` that:

* `HandledScreen.keyPressed(KeyInput)` / `keyReleased(KeyInput)` — signatures used by this mod.
* `GameOptions.useKey` / `pickItemKey` and `KeyBinding.matchesKey(KeyInput)` — confirmed.
* `HandledScreen.focusedSlot` and `onMouseClick(Slot, int, int, SlotActionType)` — unchanged for many versions.
* `render(DrawContext, int, int, float)` — unchanged.

Even so, **before the first actual build**, run:

```bash
./gradlew genSources
```

and open the generated `HandledScreen` class (in `build/loom-cache` or through your IDE after importing the project) to confirm that the exact signatures of `keyPressed`, `keyReleased`, and `render` match those used in the mixin. If a later Yarn build changes anything, you will only need to adjust the corresponding `@Inject` method signature.

## ⚠️ Required preliminary step: generate the Gradle Wrapper

This project **does not include the Gradle Wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) because these are generated files and cannot be reliably created by hand. Generate it once using your globally installed Gradle **before compiling**:

```powershell
gradle wrapper --gradle-version 9.7.1
```

**Important**: Fabric Loom `1.14.10` requires Gradle with `plugin.api-version >= 9.2.0` — **it does not work with Gradle 8.x**, so use 9.7.1 (or any 9.2+ version), never an 8.x version.

From then on, always use `./gradlew` (or `gradlew.bat` on Windows) instead of your global `gradle`, so the Gradle version remains pinned and the build is reproducible:

```powershell
.\gradlew.bat build
```

## About the "Unsupported class file major version 69" (Java 25) error

This error occurs if Gradle is running on JDK 25 with a version of Gradle that does not support it (Gradle 8.x or 9.0). With the wrapper already set to **9.7.1** (see above), this should not happen again, since full Java 25 support was added in Gradle 9.1.0. However, it is still **recommended to use JDK 21** to run Gradle in this project (see `org.gradle.java.home` in `gradle.properties`), because Loom, Mixin, and the internal decompilers (based on ASM) are designed and tested against Java 21, not 25 — using 25 could cause subtle errors later even if Gradle itself understands it.

If you change the `org.gradle.java.home` path or remove the line and this error appears again, stop the old daemons first:

```powershell
.\gradlew.bat --stop
```

Then confirm with:

```powershell
.\gradlew.bat --version
```

that the "Daemon JVM" is Java 21.

## About the "Unsupported unpick version" error

If you see this error, it means the **Fabric Loom** version is too old for the `unpick v3` format used by Minecraft 1.21.11. This project already pins Loom to `1.14.10` in `build.gradle` (line `id 'fabric-loom' version '1.14.10'`). If it still fails, check [fabricmc.net/develop](https://fabricmc.net/develop) to see whether a newer Loom version is available and update that line.

## Installation (for playing)

1. Install **Fabric Loader** ≥ 0.18.4 for Minecraft 1.21.11.
2. Download **Fabric API** for 1.21.11 and place it in `mods/`.
3. Build the mod (see below) or place the already compiled `.jar` in `mods/`.
4. Launch the Fabric 1.21.11 profile.

## Build

```bash
./gradlew build
```

The resulting `.jar` appears at:

```text
build/libs/keybindfix-1.0.0.jar
```

## How to test it

1. Open **Options → Controls** and remap:

   * **"Use Item / Place Block"** to a keyboard key, e.g. `Control`.
   * **"Pick Block"** to another keyboard key, e.g. `Alt`.

2. **MC-19433**: open a chest, furnace, crafting table, or your inventory, pick up a stack of an item, and press your "Use" key over another slot — it should deposit only one item, just like a real right-click.

3. **MC-577**: repeat the previous test in different containers (furnace, dispenser, crafting table) to confirm that it doesn't get blocked in any inventory.

4. **MC-117771**: in Creative mode, with an item on your cursor, hold down your "Pick Block" key and move the mouse over several empty inventory slots — all of them should be filled, just like holding the middle mouse button.

## Possible issues

* **Compilation error in the mixin due to an incorrect signature**: `HandledScreenAccessor` only references `focusedSlot` and `onMouseClick`, two members that have been stable for many versions. If it still fails, run `genSources` (see above) to confirm the exact field/method name.

* **Nothing happens when pressing the key**: check that the assigned key isn't already conflicting with another keybind (Minecraft displays a ⚠️ on the Controls screen).

* **The click works but "dragging" doesn't (MC-117771)**: make sure you are physically holding the key down (not using a toggle), and that the player is in Creative mode, since `CLONE` is discarded in Survival by the server itself.

* **"could not find any targets matching 'keyReleased'"**: this error came from an earlier version of the mod that did inject directly into `keyPressed`/`keyReleased`. The current version does not use `@Inject` on those methods at all (see above), so it should not occur again.

* **Mods such as Mouse Tweaks**: if you use mods that also modify inventory click behavior (Mouse Tweaks, for example) and notice unusual behavior when combined with KeybindFix, let me know — it may require a small adjustment to mod ordering/priority.
