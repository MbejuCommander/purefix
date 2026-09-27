package dev.purefix.network;

import dev.purefix.PureFix;
import dev.purefix.core.XaeroProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class XaeroNetworking {
    public static final Identifier CHANNEL = Identifier.parse(XaeroProtocol.CHANNEL);
    // Identity keys avoid mixing separate packets whose payload records compare equal.
    // Bounded because packets from a disconnected/configuration listener may never be delivered.
    private static final Map<CustomPacketPayload, Observation> OBSERVED = new IdentityHashMap<>();
    public record Observation(Integer worldId) {}
    private XaeroNetworking() {}

    public static void register() {
        // Xaero owns its codec and receiver when installed. The passive codec observer below
        // reads a copy without consuming or replacing Xaero's packets or callbacks.
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) return;
        PayloadTypeRegistry.clientboundPlay().register(WorldPayload.TYPE, WorldPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(WorldPayload.TYPE, (payload, context) -> {});
    }

    /** Inspect a duplicate buffer; no retained buffers, allocations proportional to packet size, or cursor changes. */
    public static Observation inspect(FriendlyByteBuf original) {
        FriendlyByteBuf copy = new FriendlyByteBuf(original.duplicate());
        try {
            if (!copy.readIdentifier().equals(CHANNEL)) return null;
            if (copy.readableBytes() != 5) return new Observation(null);
            byte[] data = new byte[5]; copy.readBytes(data);
            var id = XaeroProtocol.decode(data);
            return new Observation(id.isPresent() ? id.getAsInt() : null);
        } catch (RuntimeException malformed) { return null; }
    }
    public static synchronized void remember(CustomPacketPayload payload, Observation observation) {
        if (observation == null) return;
        if (OBSERVED.size() >= 256) OBSERVED.clear();
        OBSERVED.put(payload, observation);
    }
    public static synchronized Observation take(CustomPacketPayload payload) { return OBSERVED.remove(payload); }

    public record WorldPayload() implements CustomPacketPayload {
        public static final Type<WorldPayload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldPayload> CODEC = new StreamCodec<>() {
            @Override public WorldPayload decode(RegistryFriendlyByteBuf buffer) {
                // The observer captures only the recognized five-byte ID. Other Xaero packet
                // types are ignored while running without Xaero, without disconnecting the user.
                buffer.skipBytes(buffer.readableBytes()); return new WorldPayload();
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, WorldPayload value) {
                throw new UnsupportedOperationException("PureFix only receives Xaero world data.");
            }
        };
        @Override public Type<WorldPayload> type() { return TYPE; }
    }
}
