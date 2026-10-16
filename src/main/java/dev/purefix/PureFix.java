package dev.purefix;

import dev.purefix.core.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.commands.SharedSuggestionProvider;
import dev.purefix.ui.PureFixScreen;
import dev.purefix.network.XaeroNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class PureFix implements ClientModInitializer {
    public static PureFix INSTANCE;
    public final dev.purefix.donkey.DonkeyController donkey = new dev.purefix.donkey.DonkeyController(this);
    public final Store store = new Store(FabricLoader.getInstance().getConfigDir().resolve("purefix"));
    private final Minecraft mc = Minecraft.getInstance();
    private final WorldIdentity identity = new WorldIdentity();
    private final SpectateSession session = new SpectateSession();
    private String fingerprint = "", selectedId = "", lastCapture = "";
    private final Set<String> eligible = new HashSet<>(), attempted = new HashSet<>();
    private long tick, restoreDeadline, lastPearlTick = -100;
    private boolean menuVerified, internalCommand, positionReady;
    private int readyTicks, closedTicks;
    private Data.Pending awaiting;
    private long awaitingDeadline, positionSerial, awaitingPositionSerial;
    private boolean openMenu;
    private String registeredDonkeyAlias;
    private boolean registeredDonkeyEnabled;

    @Override public void onInitializeClient() {
        INSTANCE = this; store.load(); XaeroNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            eligible.clear(); store.data.pending.forEach(p -> eligible.add(p.id));
            attempted.clear(); awaiting = null; readyTicks = 0;
            if (!store.error.isBlank()) message(store.error);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnection());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(literal("purefix").executes(context -> { openMenu = true; return 1; }));
            registerDonkeyCommand(dispatcher);
        });
    }
    private void registerDonkeyCommand(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        registeredDonkeyAlias = settings().donkeyAlias; registeredDonkeyEnabled = settings().donkeyEnabled;
        if (!store.available()) return;
        DonkeyCommands.register(dispatcher, registeredDonkeyAlias,
            (context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder),
            player -> { try { donkey.start(player); } catch (IllegalArgumentException e) { message(e.getMessage()); } },
            () -> message("Usage: " + settings().donkeyAlias.replace("{player}", "<player>") + ". Type a space after the command to see player suggestions."),
            prefix -> !internalCommand && settings().donkeyEnabled && store.available()
                && (Rules.command(settings().donkeyAlias).equals(prefix) || Rules.command(settings().donkeyAlias).startsWith(prefix + " ")));
    }
    public Data.Settings settings() { return store.data.settings; }
    public boolean multiplayer() { return mc.player != null && mc.level != null && mc.getConnection() != null && !mc.isLocalServer() && mc.getCurrentServer() != null; }
    private boolean spectating() { return mc.gameMode != null && mc.gameMode.isSpectator(); }
    public String address() { return multiplayer() ? Rules.address(mc.getCurrentServer().ip) : ""; }
    public String dimension() { return multiplayer() ? mc.level.dimension().identifier().toString() : ""; }
    public String fingerprint() { return fingerprint; }
    public String account() { return mc.player == null ? "" : mc.player.getUUID().toString(); }
    public void message(String text) { if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[PureFix] " + text)); }
    public boolean save() {
        boolean ok = store.save();
        if (!ok) message(store.error);
        else if (mc.getConnection() != null && (registeredDonkeyEnabled != settings().donkeyEnabled
                || !Objects.equals(registeredDonkeyAlias, settings().donkeyAlias))) {
            try {
                var dispatcher = ClientCommands.getActiveDispatcher();
                if (dispatcher != null) { registerDonkeyCommand(dispatcher); ClientCommands.refreshCommandCompletions(); }
            }
            catch (IllegalStateException notReady) { /* The first server command packet registers the current settings. */ }
        }
        return ok;
    }
    private void resetConnection() {
        donkey.disconnect();
        identity.reset();
        session.reset();
        fingerprint = ""; selectedId = ""; lastCapture = ""; eligible.clear(); attempted.clear();
        awaiting = null; positionReady = false; readyTicks = 0; restoreDeadline = 0; menuVerified = false;
    }
    /** Seed is the server-provided obfuscated seed, not the private world seed. */
    public void observeWorld(long seed) {
        // Respawn may establish spectator mode; a world transition alone must never clear a saved return.
        if (session.armed() && spectating()) session.modeChanged(true);
        identity.changeWorld(seed, tick, store.data.xaeroNetworks.contains(address()));
        fingerprint = ""; selectedId = ""; lastCapture = ""; readyTicks = 0; positionReady = false;
        restoreDeadline = 0; menuVerified = false;
    }
    public void receiveWorldId(Integer worldId) {
        if (!multiplayer()) return;
        identity.receive(worldId, tick);
        if (worldId != null && store.available() && store.data.xaeroNetworks.add(address())) save();
        updateIdentity();
    }
    private void updateIdentity() {
        String next = settings().mapModCompanionEnabled ? identity.fingerprint(tick) : identity.fallback();
        if (!next.equals(fingerprint)) {
            fingerprint = next; selectedId = ""; lastCapture = ""; readyTicks = 0;
        }
        // Record a fallback association only while the server has actually confirmed this Xaero ID.
        if (settings().mapModCompanionEnabled && XaeroProtocol.isIdentity(fingerprint)) {
            Data.Server current = currentServer();
            if (current != null && !identity.fallback().equals(current.fallbackFingerprint)) {
                current.fallbackFingerprint = identity.fallback(); save();
            }
        }
    }
    public void setMapModCompanionEnabled(boolean enabled) {
        settings().mapModCompanionEnabled = enabled;
        if (multiplayer()) updateIdentity();
    }
    public String detectionStatus() {
        if (!multiplayer()) return "Join a multiplayer server to check world detection.";
        Integer worldId = identity.currentId(tick);
        if (worldId != null) return "MapModCompanion-compatible world data detected. World ID: " + worldId
            + ". This channel does not identify the exact server plugin.";
        if (identity.receivedThisConnection()) return "Xaero World Map data received on this connection; waiting for the current world's ID. MapModCompanion itself cannot be confirmed.";
        if (identity.sawTraffic()) return "Xaero World Map channel traffic detected, but no supported world ID. MapModCompanion could not be confirmed.";
        return "No compatible world ID received yet. MapModCompanion could not be confirmed; no response does not prove it is absent.";
    }
    public String detectionSummary() {
        if (!multiplayer()) return "Not connected";
        if (!settings().mapModCompanionEnabled) return "Map detection OFF | Seed fallback";
        if (XaeroProtocol.isIdentity(fingerprint)) return "World ID: " + fingerprint.substring(XaeroProtocol.PREFIX.length());
        return fingerprint.isBlank() ? "Waiting for world ID" : "Seed fallback";
    }
    public void checkMapModCompanion() { message(detectionStatus()); }
    public void positionReceived() { positionReady = true; positionSerial++; }
    public Data.Server currentServer() {
        if (!multiplayer()) return null;
        if (!selectedId.isEmpty()) return store.data.servers.stream()
            .filter(s -> s.id.equals(selectedId) && Rules.address(s.address).equals(address())).findFirst().orElse(null);
        if (fingerprint.isBlank()) return null;
        List<Data.Server> matches = store.data.servers.stream()
            .filter(s -> Rules.address(s.address).equals(address()) && matchesIdentity(s)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    private boolean matchesIdentity(Data.Server server) {
        return server.fingerprint.equals(fingerprint) || (!settings().mapModCompanionEnabled
            && !fingerprint.isBlank() && server.fallbackFingerprint.equals(fingerprint));
    }
    public void useServer(Data.Server server) {
        if (!multiplayer() || !Rules.address(server.address).equals(address())) throw new IllegalArgumentException("Connect to this server address first.");
        if (XaeroProtocol.isIdentity(fingerprint) && !server.fingerprint.equals(fingerprint))
            throw new IllegalArgumentException("This profile does not match the received world ID. Bind it to the current ID first.");
        selectedId = server.id; readyTicks = 0;
        message("Using subserver: " + server.subserver + ". Selection lasts until disconnect or detected world switch.");
    }
    public Data.Server draftServer() {
        if (!multiplayer()) throw new IllegalArgumentException("Join a multiplayer server first.");
        if (fingerprint.isBlank()) throw new IllegalArgumentException("Waiting for a current world ID. Try again after world detection finishes.");
        Data.Server server = new Data.Server(); server.address = address(); server.name = mc.getCurrentServer().name;
        server.subserver = "New subserver"; server.fingerprint = fingerprint;
        server.fallbackFingerprint = identity.fallback();
        server.observedDimensions.add(dimension());
        return server;
    }
    private void capture() {
        if (!settings().autoCapture || fingerprint.isBlank()) return;
        String key = address() + "|" + fingerprint;
        String visit = key + "|" + dimension();
        if (visit.equals(lastCapture)) return;
        lastCapture = visit;
        if (store.data.ignored.contains(key)) return;
        Data.Server existing = currentServer();
        if (existing != null) { existing.lastSeen = System.currentTimeMillis(); existing.observedDimensions.add(dimension()); save(); return; }
        // A duplicate fingerprint means manual profiles are present; never invent a third one.
        if (store.data.servers.stream().anyMatch(s -> Rules.address(s.address).equals(address()) && matchesIdentity(s))) return;
        Data.Server server = draftServer();
        server.subserver = XaeroProtocol.isIdentity(fingerprint) ? "World " + fingerprint.substring(XaeroProtocol.PREFIX.length())
            : fingerprint.equals("unknown") ? "Unidentified subserver" : "Detected " + fingerprint.substring(5);
        store.data.servers.add(server); save();
    }
    public void deleteServer(Data.Server server) {
        store.data.ignored.add(Rules.address(server.address) + "|" + server.fingerprint);
        store.data.servers.remove(server);
        if (selectedId.equals(server.id)) selectedId = "";
        // Pending returns are intentionally retained; deleting a label must not discard recovery data.
        save();
    }
    public void forget(Data.Pending pending) { store.data.pending.remove(pending); eligible.remove(pending.id); if (awaiting == pending) awaiting = null; if (session.pendingId().equals(pending.id)) session.reset(); save(); }
    public void retry(Data.Pending pending) { eligible.add(pending.id); attempted.remove(pending.id); readyTicks = 0; }

    /** Called before vanilla sends the command. False cancels only an unsafe, unsaved spectate entry. */
    public boolean outgoing(String command) {
        if (internalCommand || !multiplayer()) return true;
        if (donkey.intercept(command)) return false;
        if (settings().enderEnabled && settings().restoreCommands.stream().anyMatch(p -> Rules.matches(p, command))) {
            restoreDeadline = tick + 2400; menuVerified = false; closedTicks = 0;
        }
        if (!settings().spectateEnabled) return true;
        boolean entry = settings().spectateCommands.stream().anyMatch(p -> Rules.matches(p, command));
        if (!entry) return true;
        // Exit/retarget commands must always reach the server; neither clears nor replaces the origin.
        if (session.armed() || spectating()) return true;
        updateIdentity();
        if (fingerprint.isBlank()) {
            message("Waiting for the current world ID. Spectate entry cancelled. You can disable MapModCompanion detection in PureFix > SpectateFix settings.");
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f));
            return false;
        }
        Data.Server server = currentServer();
        if (server == null && store.available()) {
            // Explicit spectate use needs a recovery profile even with passive auto-capture disabled.
            if (store.data.servers.stream().noneMatch(s -> Rules.address(s.address).equals(address()) && matchesIdentity(s))) {
                server = draftServer(); server.subserver = "Spectate return server"; store.data.servers.add(server);
            }
        }
        if (server == null || !store.available()) { message("Select a subserver in Server Saves before spectating. Command cancelled."); return false; }
        String serverId = server.id;
        Data.Pending previous = store.data.pending.stream().filter(p -> p.account.equals(account()) && p.serverId.equals(serverId)).findFirst().orElse(null);
        if (previous != null) {
            session.arm(previous.id, spectating());
            eligible.remove(previous.id); if (awaiting == previous) awaiting = null;
            return true;
        }
        String world = server.worlds.getOrDefault(dimension(), "");
        if (!Rules.validWorld(world)) { message("Set this dimension's Bukkit world in Server Saves first. Command cancelled."); return false; }
        Data.Pending pending = new Data.Pending(); pending.account = account(); pending.serverId = server.id;
        pending.dimension = dimension(); pending.world = world;
        pending.worldFingerprint = fingerprint;
        pending.x = mc.player.getX(); pending.y = mc.player.getY(); pending.z = mc.player.getZ();
        store.data.pending.add(pending);
        if (!save()) { store.data.pending.remove(pending); return false; }
        session.arm(pending.id, false);
        message("Spectate return saved in " + world + "."); return true;
    }
    /** Called only after a server CHANGE_GAME_MODE event, never from rendering or disconnect. */
    public void gameModeChanged() {
        if (!multiplayer() || !settings().spectateEnabled || !store.available()) return;
        boolean spectator = spectating();
        if (!session.armed() && spectator) {
            Data.Server server = currentServer();
            if (server != null) store.data.pending.stream()
                .filter(p -> p.account.equals(account()) && p.serverId.equals(server.id)).findFirst()
                .ifPresent(p -> session.arm(p.id, true));
        }
        if (!session.modeChanged(spectator)) return;
        Data.Pending pending = store.data.pending.stream()
            .filter(p -> p.id.equals(session.pendingId()) && p.account.equals(account())).findFirst().orElse(null);
        session.reset();
        if (pending == null) return;
        store.data.pending.remove(pending);
        if (!save()) { store.data.pending.add(pending); return; }
        eligible.remove(pending.id); attempted.remove(pending.id);
        if (awaiting == pending) awaiting = null;
        message("Spectate ended: your saved return was cleared after the server changed your game mode.");
    }
    private void tick() {
        tick++;
        donkey.tick();
        if (openMenu) { openMenu = false; mc.setScreenAndShow(new PureFixScreen(null)); }
        if (!multiplayer() || !store.available()) return;
        updateIdentity();
        capture();
        verifyMenu();
        if (positionReady) readyTicks++;
        if (!settings().spectateEnabled) return;
        if (awaiting != null) {
            Data.Pending p = awaiting;
            Data.Server current = currentServer();
            if (current != null && RecoveryPolicy.identityMatches(p, fingerprint, settings().mapModCompanionEnabled)
                && RecoveryPolicy.arrived(p, account(), current.id, dimension(), positionSerial > awaitingPositionSerial,
                mc.player.getX(), mc.player.getY(), mc.player.getZ())) {
                store.data.pending.remove(p);
                if (!save()) { store.data.pending.add(p); awaiting = null; return; }
                eligible.remove(p.id); awaiting = null;
                if (session.pendingId().equals(p.id)) session.reset();
                if (settings().spectateSound) sound();
                message("Spectate return completed.");
            } else if (tick > awaitingDeadline) {
                awaiting = null; message("Return was not confirmed. Check permissions/world mapping, then Retry in SpectateFix.");
            }
            return;
        }
        if (!positionReady || readyTicks < settings().joinDelayTicks) return;
        Data.Server server = currentServer();
        for (Data.Pending pending : store.data.pending) {
            if (!RecoveryPolicy.canAttempt(pending, server, account(), fingerprint, !selectedId.isBlank(), positionReady,
                readyTicks, settings().joinDelayTicks, eligible, attempted, settings().mapModCompanionEnabled)) continue;
            attempted.add(pending.id);
            try {
                send(Rules.render(settings().spectateCommand, pending.location()));
                awaiting = pending; awaitingDeadline = tick + 200; awaitingPositionSerial = positionSerial;
                message("Returning to your saved spectate position...");
            } catch (IllegalArgumentException e) { message(e.getMessage()); }
            break;
        }
    }
    private void verifyMenu() {
        if (tick > restoreDeadline) { menuVerified = false; return; }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof ChestMenu menu)) {
            if (menuVerified && ++closedTicks > 10) { menuVerified = false; restoreDeadline = 0; }
            return;
        }
        closedTicks = 0;
        if (menu.getRowCount() != 1 || !Rules.clean(screen.getTitle().getString()).equals(settings().playerDataTitle)) return;
        if (menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().is(Items.BONE)
            && menu.getSlot(3).getItem().is(Items.OAK_SAPLING) && menu.getSlot(4).getItem().is(Items.BED.red())
            && menu.getSlot(5).getItem().is(Items.COMPASS) && menu.getSlot(6).getItem().is(Items.DIAMOND)
            && menu.getSlot(7).getItem().isEmpty() && menu.getSlot(8).getItem().isEmpty()) {
            menuVerified = true; restoreDeadline = tick + 12000;
        }
    }
    public boolean pearlClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ContainerInput input) {
        if (!multiplayer() || !settings().enderEnabled || !menuVerified || tick > restoreDeadline || slot == null
            || !(screen.getMenu() instanceof ChestMenu menu) || menu.getRowCount() != 6 || slotId != 49
            || slot != menu.getSlot(49) || !Rules.clean(screen.getTitle().getString()).equals(settings().backupTitle)
            || !slot.getItem().is(Items.ENDER_PEARL)
            || !Rules.clean(slot.getItem().getHoverName().getString()).equals(settings().pearlTitle)) return false;
        // Never allow modified clicks on the identified action button to execute a second plugin teleport.
        if (input != ContainerInput.PICKUP || (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT)) return true;
        if (tick - lastPearlTick < 10) return true;
        lastPearlTick = tick;
        var lore = slot.getItem().get(DataComponents.LORE);
        var location = lore == null ? Optional.<Rules.Location>empty() : Rules.parseLore(lore.lines().stream().map(Component::getString).toList());
        if (location.isEmpty()) { message("The teleport pearl has invalid world/coordinate data. Click cancelled."); return true; }
        try {
            String command = Rules.render(settings().enderCommand, location.get());
            mc.player.closeContainer(); send(command);
            menuVerified = false; restoreDeadline = 0;
            if (settings().enderSound) sound();
        } catch (IllegalArgumentException e) { message(e.getMessage()); }
        return true;
    }
    private void send(String command) {
        internalCommand = true;
        try { mc.getConnection().sendCommand(command); } finally { internalCommand = false; }
    }
    public void sendServerCommand(String command) { send(command); }
    private void sound() {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 1f));
    }
}
