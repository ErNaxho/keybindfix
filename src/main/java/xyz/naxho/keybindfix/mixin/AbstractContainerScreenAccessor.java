package xyz.naxho.keybindfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Expone dos miembros protegidos de {@link AbstractContainerScreen} (nombre
 * oficial de Mojang; en Yarn/1.21.11 era {@code HandledScreen}) para poder
 * usarlos desde fuera de la clase (concretamente, desde los listeners de
 * {@code ScreenKeyboardEvents} / {@code ScreenEvents} registrados en
 * {@link xyz.naxho.keybindfix.KeybindFixClient}):
 * <ul>
 *   <li>{@code hoveredSlot} (Yarn: {@code focusedSlot}): el slot que el
 *       cursor está sobrevolando ahora mismo (puede ser {@code null}).</li>
 *   <li>{@code slotClicked(Slot, int, int, ContainerInput)} (Yarn/1.21.11:
 *       {@code onMouseClick(..., SlotActionType)}): el mismo método que
 *       ejecuta un click de ratón real sobre un slot. El propio Mojang
 *       renombró {@code ClickType} a {@code ContainerInput} en el salto a
 *       26.1 (ver nota de migración en el README) — mantiene las mismas
 *       constantes ({@code PICKUP}, {@code CLONE}, ...), así que el resto
 *       del código no cambia más allá del nombre del tipo.</li>
 * </ul>
 * Ambos miembros llevan sin cambiar muchas versiones de Minecraft (a
 * diferencia de {@code keyPressed}/{@code keyReleased}, que sí cambiaron de
 * firma en 1.21.9-1.21.11), por lo que este accessor es mucho más estable
 * que inyectar directamente sobre esos métodos.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot keybindfix$getFocusedSlot();

    @Invoker("slotClicked")
    void keybindfix$invokeOnMouseClick(Slot slot, int slotId, int button, ContainerInput actionType);
}
