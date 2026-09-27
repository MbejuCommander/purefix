package dev.purefix.mixin;

import dev.purefix.PureFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ConnectionMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void purefix$command(String command, CallbackInfo ci) {
        if (PureFix.INSTANCE != null && !PureFix.INSTANCE.outgoing(command)) ci.cancel();
    }
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void purefix$login(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (PureFix.INSTANCE != null && Minecraft.getInstance().isSameThread()) PureFix.INSTANCE.observeWorld(packet.commonPlayerSpawnInfo().seed());
    }
    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void purefix$respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (PureFix.INSTANCE != null && Minecraft.getInstance().isSameThread()) PureFix.INSTANCE.observeWorld(packet.commonPlayerSpawnInfo().seed());
    }
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void purefix$position(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (PureFix.INSTANCE != null && Minecraft.getInstance().isSameThread()) PureFix.INSTANCE.positionReceived();
    }
}
