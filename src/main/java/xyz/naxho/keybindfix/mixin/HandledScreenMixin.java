package xyz.naxho.keybindfix.mixin;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected Slot focusedSlot;

    @Shadow
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        throw new UnsupportedOperationException("Shadowed by Mixin - never actually called");
    }

    /** Slots ya procesados en el arrastre actual de "Usar Objeto". */
    @Unique
    private final Set<Slot> keybindfix$useVisited = new HashSet<>();

    /** Slots ya procesados en el arrastre actual de "Seleccionar Bloque". */
    @Unique
    private final Set<Slot> keybindfix$pickVisited = new HashSet<>();

    @Inject(method = "render", at = @At("TAIL"))
    private void keybindfix$onRenderTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GameOptions options = MinecraftClient.getInstance().options;

        if (options.useKey.isPressed()) {
            this.keybindfix$handleHeld(this.keybindfix$useVisited, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP);
        } else {
            this.keybindfix$useVisited.clear();
        }

        if (options.pickItemKey.isPressed()) {
            this.keybindfix$handleHeld(this.keybindfix$pickVisited, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, SlotActionType.CLONE);
        } else {
            this.keybindfix$pickVisited.clear();
        }
    }

    @Unique
    private void keybindfix$handleHeld(Set<Slot> visited, int button, SlotActionType actionType) {
        Slot slot = this.focusedSlot;
        if (slot == null || !visited.add(slot)) {
            return;
        }
        this.onMouseClick(slot, slot.id, button, actionType);
    }
}
