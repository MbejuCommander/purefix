package dev.purefix.mixin;

import dev.purefix.PureFix;
import dev.purefix.network.XaeroNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Run before Fabric dispatches/cancels the shared payload. Netty delivery is ignored;
// Fabric schedules registered payloads on the main thread in server packet order.
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 1100)
public abstract class CustomPayloadMixin {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"))
    private void purefix$worldId(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || (Object)this != client.getConnection() || PureFix.INSTANCE == null) return;
        var observation = XaeroNetworking.take(packet.payload());
        if (observation != null) PureFix.INSTANCE.receiveWorldId(observation.worldId());
    }
}
