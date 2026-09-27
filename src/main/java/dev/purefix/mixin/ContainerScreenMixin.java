package dev.purefix.mixin;

import dev.purefix.PureFix;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void purefix$click(Slot slot, int id, MouseButtonEvent event, ContainerInput input, CallbackInfo ci) {
        if (PureFix.INSTANCE != null && PureFix.INSTANCE.pearlClick((AbstractContainerScreen<?>)(Object)this, slot, id, event == null ? -1 : event.button(), input)) ci.cancel();
    }
}
