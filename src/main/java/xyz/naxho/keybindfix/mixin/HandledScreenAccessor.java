package xyz.naxho.keybindfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("focusedSlot")
    Slot keybindfix$getFocusedSlot();

    @Invoker("onMouseClick")
    void keybindfix$invokeOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
}
