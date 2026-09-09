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
import net.minecraft.client.option.GameOptions;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import xyz.naxho.keybindfix.mixin.HandledScreenAccessor;

/**
 * Punto de entrada client-side de KeybindFix.
 * <p>
 * Hace que las keybinds "Usar Objeto / Colocar Bloque" (GameOptions#useKey)
 * y "Seleccionar Bloque" (GameOptions#pickItemKey), cuando están asignadas a
 * una tecla de TECLADO (no al ratón), se comporten dentro de cualquier
 * {@link HandledScreen} exactamente igual que su botón de ratón por
 * defecto (click derecho / click central).
 * <p>
 * Bugs vanilla que esto corrige:
 * <ul>
 *   <li><b>MC-19433</b>: no se puede colocar un único objeto (click derecho)
 *       cuando "Usar Objeto" está remapeado a una tecla de teclado, dentro
 *       de inventarios de cofres, hornos, mesa de crafteo, etc.</li>
 *   <li><b>MC-577</b>: las keybinds de Pick Block / Use asignadas a teclado
 *       quedan bloqueadas para acciones de inventario que no sean las
 *       asignaciones de ratón por defecto.</li>
 *   <li><b>MC-117771</b>: "Pick Block" asignado a teclado no permite
 *       arrastrar el cursor para rellenar varios slots (comportamiento de
 *       modo creativo).</li>
 * </ul>
 * <p>
 * <b>Por qué no basta con {@code KeyBinding#isPressed()}:</b> Minecraft
 * ignora deliberadamente el estado de "pulsado" de las keybinds de teclado
 * mientras hay una pantalla (GUI) abierta — es así a propósito, no un bug.
 * Por eso este mod usa {@code ScreenKeyboardEvents}, la API de Fabric
 * pensada exactamente para recibir pulsaciones de teclado dentro de
 * pantallas, en vez de sondear {@code isPressed()} o mixear directamente
 * {@code keyPressed}/{@code keyReleased} (cuya firma además cambió en
 * 1.21.9-1.21.11 y que ni siquiera está declarado en {@code HandledScreen}
 * para el caso de {@code keyReleased}).
 */
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
                // Quita el foco de cualquier campo de texto (p. ej. el
                // buscador de la Creative Inventory) para que el carácter
                // que esta misma pulsación generaría no tenga ningún widget
                // enfocado al que Minecraft pueda enviárselo.
                handledScreen.setFocused(null);
            } else if (options.pickItemKey.matchesKey(input)) {
                dragState.pickHeld = true;
                if (dragState.pickVisited.add(slot)) {
                    accessor.keybindfix$invokeOnMouseClick(slot, slot.id, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, SlotActionType.CLONE);
                }
                handledScreen.setFocused(null);
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
