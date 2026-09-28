package dev.purefix.smoke;

import dev.purefix.ui.*;
import dev.purefix.PureFix;
import dev.purefix.core.Data;
import dev.purefix.network.XaeroNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;

/** Runs only through runSmokeClient. No external servers or accounts are used. */
public final class SmokeClient implements ClientModInitializer {
    private int ticks, step = -1;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (step == -1) {
                // A fresh game directory can show accessibility onboarding before TitleScreen.
                if (mc.gui.screen() == null || mc.gui.overlay() != null) return;
                step = 0; ticks = 0;
            }
            if (++ticks % 30 != 0) return;
            try {
                switch (step++) {
                    case 0 -> {
                        Class.forName("net.minecraft.client.multiplayer.ClientPacketListener");
                        Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
                        networkSmoke();
                        mc.setScreenAndShow(new PureFixModMenu().getModConfigScreenFactory().create(mc.gui.screen()));
                    }
                    case 1 -> { shot(mc); press(mc, "EnderFix"); }
                    case 2 -> { shot(mc); press(mc, "Restore command patterns"); }
                    case 3 -> { shot(mc); press(mc, "Reset"); }
                    case 4 -> { shot(mc); press(mc, "Confirm"); }
                    case 5 -> {
                        if (!PureFix.INSTANCE.settings().restoreCommands.equals(Data.DEFAULT_RESTORE_COMMANDS)) throw new AssertionError("Reset did not restore defaults");
                        press(mc, "Back"); press(mc, "Teleport command");
                    }
                    case 6 -> { shot(mc); press(mc, "Cancel"); }
                    case 7 -> { press(mc, "Back"); press(mc, "SpectateFix"); }
                    case 8 -> {
                        boolean enabled = PureFix.INSTANCE.settings().mapModCompanionEnabled;
                        press(mc, "MapModCompanion detection: " + (enabled ? "ON" : "OFF"));
                        if (PureFix.INSTANCE.settings().mapModCompanionEnabled == enabled) throw new AssertionError("Map detection toggle failed");
                        press(mc, "MapModCompanion detection: " + (enabled ? "OFF" : "ON"));
                        shot(mc); press(mc, ">");
                    }
                    case 9 -> { shot(mc); press(mc, "Back"); press(mc, "Server Saves"); }
                    case 10 -> { shot(mc); press(mc, "Back"); press(mc, "Help & storage"); }
                    case 11 -> { shot(mc); System.out.println("PUREFIX_SMOKE_PASS: shared Xaero decoder observation, mixins, themed menus, Reset, editor, SpectateFix, Server Saves."); }
                    case 12 -> mc.stop();
                }
            } catch (Throwable error) {
                error.printStackTrace(); System.err.println("PUREFIX_SMOKE_FAIL"); mc.stop();
            }
        });
    }
    private void press(Minecraft mc, String label) {
        Button button = mc.gui.screen().children().stream().filter(c -> c instanceof Button)
            .map(c -> (Button)c).filter(b -> b.getMessage().getString().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing button: " + label));
        if (!button.active) throw new AssertionError("Disabled button: " + label);
        button.onPress(null);
    }
    private void shot(Minecraft mc) { Screenshot.grab(mc, false); }

    private record ForeignPayload(int id) implements CustomPacketPayload {
        static final Type<ForeignPayload> TYPE = new Type<>(XaeroNetworking.CHANNEL);
        @Override public Type<ForeignPayload> type() { return TYPE; }
    }
    private void networkSmoke() {
        // Simulate another mod owning the Xaero channel: its decoded object and bytes must survive unchanged.
        StreamCodec<FriendlyByteBuf, ForeignPayload> foreignCodec = new StreamCodec<>() {
            public ForeignPayload decode(FriendlyByteBuf buffer) { buffer.readByte(); return new ForeignPayload(buffer.readInt()); }
            public void encode(FriendlyByteBuf buffer, ForeignPayload payload) { buffer.writeByte(0); buffer.writeInt(payload.id); }
        };
        var codec = CustomPacketPayload.<FriendlyByteBuf>codec(id -> DiscardedPayload.codec(id, 32767),
            List.of(new CustomPacketPayload.TypeAndCodec<>(ForeignPayload.TYPE, foreignCodec)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeIdentifier(XaeroNetworking.CHANNEL); buffer.writeByte(0); buffer.writeInt(1337);
            CustomPacketPayload payload = codec.decode(buffer);
            if (!(payload instanceof ForeignPayload p) || p.id != 1337 || buffer.isReadable()) throw new AssertionError("Shared packet changed");
            var observation = XaeroNetworking.take(payload);
            if (observation == null || observation.worldId() == null || observation.worldId() != 1337) throw new AssertionError("Xaero packet not observed");
            if (XaeroNetworking.take(payload) != null) throw new AssertionError("Duplicate delivery");
        } finally { buffer.release(); }
        // Exercise the actual registered Fabric/Xaero decoder, not just the independent fixture.
        RegistryFriendlyByteBuf registered = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            registered.writeIdentifier(XaeroNetworking.CHANNEL); registered.writeByte(0); registered.writeInt(1337);
            var packet = ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(registered);
            if (packet.payload() instanceof DiscardedPayload || registered.isReadable()) throw new AssertionError("World channel is not registered");
            var observed = XaeroNetworking.take(packet.payload());
            if (observed == null || observed.worldId() == null || observed.worldId() != 1337) throw new AssertionError("Registered packet not observed");
            System.out.println("PUREFIX_CHANNEL_PASS: " + packet.payload().getClass().getName());
        } finally { registered.release(); }
    }
}
