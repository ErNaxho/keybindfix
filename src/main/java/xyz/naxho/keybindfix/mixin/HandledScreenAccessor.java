package xyz.naxho.keybindfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Expone dos miembros protegidos de {@link HandledScreen} para poder usarlos
 * desde fuera de la clase (concretamente, desde los listeners de
 * {@code ScreenKeyboardEvents} / {@code ScreenEvents} registrados en
 * {@link xyz.naxho.keybindfix.KeybindFixClient}):
 * <ul>
 *   <li>{@code focusedSlot}: el slot que el cursor está sobrevolando ahora
 *       mismo (puede ser {@code null}).</li>
 *   <li>{@code onMouseClick(Slot, int, int, SlotActionType)}: el mismo
 *       método que ejecuta un click de ratón real sobre un slot.</li>
 * </ul>
 * Ambos miembros llevan sin cambiar muchas versiones de Minecraft (a
 * diferencia de {@code keyPressed}/{@code keyReleased}, que sí cambiaron de
 * firma en 1.21.9-1.21.11), por lo que este accessor es mucho más estable
 * que inyectar directamente sobre esos métodos.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("focusedSlot")
    Slot keybindfix$getFocusedSlot();

    @Invoker("onMouseClick")
    void keybindfix$invokeOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
}
