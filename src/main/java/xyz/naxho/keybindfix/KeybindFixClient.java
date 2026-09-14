package xyz.naxho.keybindfix;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import xyz.naxho.keybindfix.mixin.AbstractContainerScreenAccessor;

/**
 * Punto de entrada client-side de KeybindFix.
 * <p>
 * Hace que las keybinds "Usar Objeto / Colocar Bloque" (Options#keyUse)
 * y "Seleccionar Bloque" (Options#keyPickItem), cuando están asignadas a
 * una tecla de TECLADO (no al ratón), se comporten dentro de cualquier
 * {@link AbstractContainerScreen} exactamente igual que su botón de ratón
 * por defecto (click derecho / click central).
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
 * <b>Por qué no basta con {@code KeyMapping#isDown()}:</b> Minecraft
 * ignora deliberadamente el estado de "pulsado" de las keybinds de teclado
 * mientras hay una pantalla (GUI) abierta — es así a propósito, no un bug.
 * Por eso este mod usa {@code ScreenKeyboardEvents}, la API de Fabric
 * pensada exactamente para recibir pulsaciones de teclado dentro de
 * pantallas, en vez de sondear {@code isDown()} o mixear directamente
 * {@code keyPressed}/{@code keyReleased} (cuya firma además cambió en
 * 1.21.9-1.21.11 y que ni siquiera está declarado en
 * {@code AbstractContainerScreen} para el caso de {@code keyReleased}).
 * <p>
 * Nota de migración a Minecraft 26.1.2 / 26.2: a partir de 26.1 el juego ya
 * no está ofuscado y Fabric usa directamente los nombres oficiales de
 * Mojang, así que {@code HandledScreen} -> {@code AbstractContainerScreen},
 * {@code MinecraftClient} -> {@code Minecraft}, {@code GameOptions} ->
 * {@code Options} y {@code SlotActionType} -> {@code ContainerInput} (este
 * último es un rename vanilla del propio Mojang, no de Fabric API, hecho
 * en el mismo salto a 26.1). La API de
 * {@code ScreenEvents}/{@code ScreenKeyboardEvents} de Fabric API no cambió
 * de nombre ni de paquete en este salto de versión; sí se cambió el evento
 * de "cada frame" ({@code afterRender}) por uno de "cada tick"
 * ({@code afterTick}) para no depender de la reescritura del pipeline de
 * renderizado de 26.2 ({@code GuiGraphics} -> {@code GuiGraphicsExtractor}).
 */
public class KeybindFixClient implements ClientModInitializer {

    public static final String MOD_ID = "keybindfix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        LOGGER.info("[KeybindFix] Cargado. Arreglando MC-117771 / MC-577 / MC-19433 para teclas de Usar y Seleccionar Bloque.");
    }

    private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
        DragState dragState = new DragState();

        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, input) -> {
            Options options = Minecraft.getInstance().options;
            Slot slot = accessor.keybindfix$getFocusedSlot();
            if (slot == null) {
                return;
            }

            if (options.keyUse.matches(input)) {
                dragState.useHeld = true;
                if (dragState.useVisited.add(slot)) {
                    accessor.keybindfix$invokeOnMouseClick(slot, slot.index, GLFW.GLFW_MOUSE_BUTTON_RIGHT, ContainerInput.PICKUP);
                }
                // Quita el foco de cualquier campo de texto (p. ej. el
                // buscador de la Creative Inventory) para que el carácter
                // que esta misma pulsación generaría no tenga ningún widget
                // enfocado al que Minecraft pueda enviárselo.
                containerScreen.setFocused(null);
            } else if (options.keyPickItem.matches(input)) {
                dragState.pickHeld = true;
                if (dragState.pickVisited.add(slot)) {
                    accessor.keybindfix$invokeOnMouseClick(slot, slot.index, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, ContainerInput.CLONE);
                }
                containerScreen.setFocused(null);
            }
        });

        ScreenKeyboardEvents.afterKeyRelease(screen).register((scrn, input) -> {
            Options options = Minecraft.getInstance().options;

            if (options.keyUse.matches(input)) {
                dragState.useHeld = false;
                dragState.useVisited.clear();
            } else if (options.keyPickItem.matches(input)) {
                dragState.pickHeld = false;
                dragState.pickVisited.clear();
            }
        });

        // Mientras la tecla se mantiene pulsada, cada tick comprueba si el
        // cursor entró en un slot nuevo y repite el click ahí -> arregla el
        // "drag fill" de MC-117771.
        //
        // NOTA: aquí se usa ScreenEvents.afterTick en vez de afterRender.
        // Antes (1.21.11) usábamos afterRender simplemente como "algo que
        // se ejecuta cada frame mientras la pantalla está abierta" — nunca
        // llegamos a usar el GuiGraphics/DrawContext que ese evento pasa.
        // En 26.2 Mojang reescribió a fondo el pipeline de renderizado
        // (GuiGraphics -> GuiGraphicsExtractor, render() -> 
        // extractRenderState(), etc.), así que atarnos a afterRender nos
        // ataría innecesariamente a esos cambios cada vez que Mojang
        // vuelva a tocar el renderizado. afterTick(Screen) no expone (ni
        // necesita) nada de eso, y 20 comprobaciones/segundo son de sobra
        // para que el arrastre de teclado se sienta instantáneo.
        ScreenEvents.afterTick(screen).register((scrn) -> {
            Slot slot = accessor.keybindfix$getFocusedSlot();
            if (slot == null) {
                return;
            }

            if (dragState.useHeld && dragState.useVisited.add(slot)) {
                accessor.keybindfix$invokeOnMouseClick(slot, slot.index, GLFW.GLFW_MOUSE_BUTTON_RIGHT, ContainerInput.PICKUP);
            }
            if (dragState.pickHeld && dragState.pickVisited.add(slot)) {
                accessor.keybindfix$invokeOnMouseClick(slot, slot.index, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, ContainerInput.CLONE);
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
