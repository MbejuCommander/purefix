package dev.purefix.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.purefix.network.XaeroNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the wire format before another mod's codec consumes it; preserve the original payload. */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class PayloadCodecMixin {
    @WrapMethod(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;")
    private CustomPacketPayload purefix$observe(FriendlyByteBuf buffer, Operation<CustomPacketPayload> original) {
        var observation = XaeroNetworking.inspect(buffer);
        CustomPacketPayload payload = original.call(buffer);
        XaeroNetworking.remember(payload, observation);
        return payload;
    }
}
