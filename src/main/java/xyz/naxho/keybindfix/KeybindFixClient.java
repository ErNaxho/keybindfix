package xyz.naxho.keybindfix;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import xyz.naxho.keybindfix.mixin.HandledScreenAccessor;

public class KeybindFixClient implements ClientModInitializer {

    public static final String MOD_ID = "keybindfix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        LOGGER.info("[KeybindFix] Cargado. Arreglando MC-117771 / MC-577 / MC-19433 para teclas de Usar y Seleccionar Bloque.");
    }

    private void onScreenInit(MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        DragState dragState = new DragState();

        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, input) -> {
            GameOptions options = MinecraftClient.getInstance().options;
            Slot slot = accessor.keybindfix$getFocusedSlot();
            if (slot == null) {
                return;
            }

            if (options.useKey.matchesKey(input)) {
                dragState.useHeld = true;
                if (dragState.useVisited.add(slot)) {
                    accessor.keybindfix$invokeOnMouseClick(slot, slot.id, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP);
                }
            } else if (options.pickItemKey.matchesKey(input)) {
                dragState.pickHeld = true;
                if (dragState.pickVisited.add(slot)) {
                    accessor.keybindfix$invokeOnMouseClick(slot, slot.id, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, SlotActionType.CLONE);
                }
            }
        });

        ScreenKeyboardEvents.afterKeyRelease(screen).register((scrn, input) -> {
            GameOptions options = MinecraftClient.getInstance().options;

            if (options.useKey.matchesKey(input)) {
                dragState.useHeld = false;
                dragState.useVisited.clear();
            } else if (options.pickItemKey.matchesKey(input)) {
                dragState.pickHeld = false;
                dragState.pickVisited.clear();
            }
        });

        // Mientras la tecla se mantiene pulsada, cada frame comprueba si el
        // cursor entró en un slot nuevo y repite el click ahí -> arregla el
        // "drag fill" de MC-117771.
        ScreenEvents.afterRender(screen).register((scrn, context, mouseX, mouseY, tickDelta) -> {
            Slot slot = accessor.keybindfix$getFocusedSlot();
            if (slot == null) {
                return;
            }

            if (dragState.useHeld && dragState.useVisited.add(slot)) {
                accessor.keybindfix$invokeOnMouseClick(slot, slot.id, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP);
            }
            if (dragState.pickHeld && dragState.pickVisited.add(slot)) {
                accessor.keybindfix$invokeOnMouseClick(slot, slot.id, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, SlotActionType.CLONE);
            }
        });
    }

    /** Estado de "arrastre" propio de cada instancia de pantalla abierta. */
    private static final class DragState {
        boolean useHeld = false;
        boolean pickHeld = false;
        final Set<Slot> useVisited = new HashSet<>();
        final Set<Slot> pickVisited = new HashSet<>();
    }
}
