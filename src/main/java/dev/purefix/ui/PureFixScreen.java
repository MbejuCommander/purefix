package dev.purefix.ui;

import dev.purefix.PureFix;
import dev.purefix.core.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.*;

public final class PureFixScreen extends MenuScreen {
    private final PureFix mod = PureFix.INSTANCE;
    public PureFixScreen(Screen parent) { super(parent, "PureFix"); dashboard = true; subtitle = "Your multiplayer staff toolkit"; }
    @Override protected void populate() {
        card("EnderFix", "Backup teleports", Theme.Icon.PEARL, Theme.PEARL, () -> show(ender(this)));
        card("SpectateFix", "Return to safety", Theme.Icon.PLAYER, Theme.SPECTATE, () -> show(spectate(this)));
        card("Server Saves", "Worlds & profiles", Theme.Icon.COMPASS, Theme.GOLD, () -> show(servers(this)));
        card("Help & storage", "Guide & local data", Theme.Icon.BOOK, 0xFF91B9CC, () -> show(help(this)));
        if (!mod.store.error.isBlank()) action("Storage error: " + mod.store.error, () -> {}, false);
    }
    private String state(boolean enabled) { return enabled ? "ON" : "OFF"; }
    private void commit() { if (!mod.save()) throw new IllegalArgumentException(mod.store.error); }
    private MenuScreen menu(Screen parent, String title, String subtitle, Consumer<MenuScreen> populate) {
        MenuScreen result = new MenuScreen(parent, title) {
            @Override protected void populate() { populate.accept(this); }
        };
        result.subtitle = subtitle;
        return result;
    }
    private void toggle(MenuScreen screen, String label, BooleanSupplier get, Consumer<Boolean> set) {
        screen.action(label + ": " + state(get.getAsBoolean()), () -> { set.accept(!get.getAsBoolean()); mod.save(); screen.refresh(); });
    }
    private void textForm(Screen parent, String title, String label, String value, Consumer<String> accept) {
        show(new FormScreen(parent, title, new String[]{label}, new String[]{value}, values -> accept.accept(values.getFirst())));
    }
    private MenuScreen ender(Screen parent) {
        MenuScreen result = menu(parent, "EnderFix", "InventoryRollbackPlus > CoreProtect teleport", s -> {
            var c = mod.settings();
            toggle(s, "Enable EnderFix", () -> c.enderEnabled, v -> c.enderEnabled = v);
            toggle(s, "Ender pearl sound", () -> c.enderSound, v -> c.enderSound = v);
            s.action("Restore command patterns", () -> show(patterns(s, "Restore commands", c.restoreCommands, true)));
            s.action("Teleport command", () -> textForm(s, "EnderFix teleport", "Required: {world} {x} {y} {z}", c.enderCommand, v -> { Rules.validateTemplate(v); c.enderCommand = v; commit(); }));
            s.action("Menu recognition", () -> show(new FormScreen(s, "Menu recognition", new String[]{"Initial menu title", "Backup menu title", "Teleport pearl title"},
                new String[]{c.playerDataTitle, c.backupTitle, c.pearlTitle}, values -> {
                    if (values.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Titles cannot be empty.");
                    c.playerDataTitle = values.get(0); c.backupTitle = values.get(1); c.pearlTitle = values.get(2); commit();
                })));
        });
        result.icon = Theme.Icon.PEARL; result.accent = Theme.PEARL; return result;
    }
    private MenuScreen spectate(Screen parent) {
        MenuScreen result = menu(parent, "SpectateFix", "Saved returns | " + mod.detectionSummary(), s -> {
            s.subtitle = "Saved returns | " + mod.detectionSummary();
            var c = mod.settings();
            toggle(s, "Enable SpectateFix", () -> c.spectateEnabled, v -> c.spectateEnabled = v);
            toggle(s, "Enderman teleport sound", () -> c.spectateSound, v -> c.spectateSound = v);
            toggle(s, "MapModCompanion detection", () -> c.mapModCompanionEnabled, mod::setMapModCompanionEnabled);
            s.action("Check MapModCompanion", () -> { mod.checkMapModCompanion(); show(null); }, mod.multiplayer());
            s.action("Spectate command patterns", () -> show(patterns(s, "Spectate commands", c.spectateCommands, true)));
            s.action("Return teleport command", () -> textForm(s, "SpectateFix teleport", "Required: {world} {x} {y} {z}", c.spectateCommand, v -> { Rules.validateTemplate(v); c.spectateCommand = v; commit(); }));
            s.action("Join delay: " + c.joinDelayTicks + " ticks", () -> textForm(s, "Join delay", "Ticks after server position is received (0-1200)", Integer.toString(c.joinDelayTicks), v -> {
                int n; try { n = Integer.parseInt(v); } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a whole number."); }
                if (n < 0 || n > 1200) throw new IllegalArgumentException("Use 0 to 1200 ticks."); c.joinDelayTicks = n; commit();
            }));
            s.action("Saved returns (" + mod.store.data.pending.size() + ")", () -> show(pending(s)));
        });
        result.icon = Theme.Icon.PLAYER; result.accent = Theme.SPECTATE; return result;
    }
    private MenuScreen patterns(Screen parent, String title, List<String> values, boolean playerRequired) {
        return menu(parent, title, "Exact patterns; aliases are separate entries", s -> {
            s.action("Add command", () -> textForm(s, "Add command", playerRequired ? "Command ending in {player}" : "Exit command", "", v -> {
                Rules.validateTrigger(v, playerRequired); if (values.contains(v)) throw new IllegalArgumentException("Already added."); values.add(v); commit();
            }));
            if (values == mod.settings().restoreCommands) s.action("Reset", () -> show(confirm(s,
                "Reset restore commands?", "Replace this list with the three default commands.", () -> {
                    List<String> previous = new ArrayList<>(values);
                    values.clear(); values.addAll(Data.DEFAULT_RESTORE_COMMANDS);
                    if (!mod.save()) { values.clear(); values.addAll(previous); s.subtitle = mod.store.error; }
                    show(s);
                })));
            for (int index = 0; index < values.size(); index++) {
                int i = index;
                s.action(values.get(i), () -> show(menu(s, "Command pattern", values.get(i), edit -> {
                    edit.action("Edit", () -> textForm(edit, "Edit command", "Command pattern", values.get(i), v -> { Rules.validateTrigger(v, playerRequired); values.set(i, v); commit(); }));
                    edit.action("Delete", () -> { values.remove(i); mod.save(); show(s); });
                })));
            }
        });
    }
    private MenuScreen servers(Screen parent) {
        MenuScreen result = menu(parent, "Server Saves", "Your saved servers and worlds", s -> {
            toggle(s, "Automatic server capture", () -> mod.settings().autoCapture, v -> mod.settings().autoCapture = v);
            s.action("Add Server", () -> {
                Data.Server draft = mod.draftServer();
                show(new FormScreen(s, "Add Server", new String[]{"Server name", "Server address", "Subserver name"}, new String[]{draft.name, draft.address, ""}, values -> {
                    validateServer(values); draft.name = values.get(0); draft.address = Rules.address(values.get(1)); draft.subserver = values.get(2);
                    mod.store.data.servers.add(draft); commit();
                    if (draft.address.equals(mod.address())) mod.useServer(draft);
                }));
            }, mod.multiplayer() && !mod.fingerprint().isBlank());
            s.action("Export server saves to CSV", () -> { try { mod.store.exportCsv(); s.subtitle = "Saved to config/purefix/servers.csv"; } catch (Exception e) { s.subtitle = "Export failed: " + e.getMessage(); } });
            s.action("Allow deleted servers to be captured again", () -> { mod.store.data.ignored.clear(); mod.save(); s.subtitle = "Deleted servers may be captured on the next connection."; });
            for (Data.Server server : mod.store.data.servers) {
                String marker = mod.currentServer() == server ? "[Current] " : "";
                s.action(marker + server.name + " | " + server.address + " | " + server.subserver, () -> show(server(s, server)));
            }
        });
        result.icon = Theme.Icon.COMPASS; result.accent = Theme.GOLD; return result;
    }
    private void validateServer(List<String> values) {
        if (values.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("All fields are required.");
        if (values.get(1).chars().anyMatch(Character::isWhitespace) || values.get(1).contains("/")) throw new IllegalArgumentException("Use a hostname or IP, optionally with a port.");
    }
    private MenuScreen server(Screen parent, Data.Server server) {
        return menu(parent, "Server profile", server.name + " | " + server.subserver, s -> {
            s.action("Edit name, address, and subserver", () -> show(new FormScreen(s, "Edit server", new String[]{"Server name", "Server address", "Subserver name"},
                new String[]{server.name, server.address, server.subserver}, values -> {
                    validateServer(values); server.name = values.get(0); server.address = Rules.address(values.get(1)); server.subserver = values.get(2); commit();
                })));
            s.action("Use this subserver for this connection", () -> { mod.useServer(server); show(parent); }, mod.multiplayer() && Rules.address(server.address).equals(mod.address()));
            toggle(s, "Automatic spectate recovery", () -> server.automaticRecovery, v -> server.automaticRecovery = v);
            s.action("World mappings", () -> show(worlds(s, server)));
            s.action("Detected ID: " + server.fingerprint, () -> {}, false);
            s.action("Observed dimensions: " + String.join(", ", server.observedDimensions), () -> {}, false);
            s.action("Bind to current detected ID", () -> show(confirm(s, "Bind server profile?", "Use only while connected to the correct world.", () -> { server.fingerprint = mod.fingerprint(); mod.save(); show(s); })), mod.multiplayer() && !mod.fingerprint().isBlank() && Rules.address(server.address).equals(mod.address()));
            s.action("Delete server", () -> show(confirm(s, "Delete server?", "Saved returns are retained. Automatic recapture is suppressed.", () -> { mod.deleteServer(server); show(parent); })));
        });
    }
    private MenuScreen worlds(Screen parent, Data.Server server) {
        return menu(parent, "World mappings", "Minecraft dimension ID > Bukkit world used by CoreProtect", s -> {
            s.action("Add world mapping", () -> show(new FormScreen(s, "Add world mapping", new String[]{"Minecraft dimension ID", "Bukkit world name"},
                new String[]{mod.dimension(), ""}, values -> { setWorld(server, values.get(0), values.get(1)); commit(); })));
            for (var entry : server.worlds.entrySet()) {
                String dim = entry.getKey();
                s.action(dim + " > " + entry.getValue(), () -> show(menu(s, "World mapping", dim, edit -> {
                    edit.action("Edit Bukkit world", () -> textForm(edit, "Bukkit world", dim, server.worlds.get(dim), v -> { setWorld(server, dim, v); commit(); }));
                    edit.action("Delete mapping", () -> { server.worlds.remove(dim); mod.save(); show(s); });
                })));
            }
        });
    }
    private void setWorld(Data.Server server, String dimension, String world) {
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Use a dimension ID such as minecraft:the_nether.");
        if (!Rules.validWorld(world)) throw new IllegalArgumentException("Use a world name without spaces or command characters.");
        server.worlds.put(dimension, world);
    }
    private MenuScreen pending(Screen parent) {
        return menu(parent, "Saved returns", "Returns remain saved until arrival or explicit removal", s -> {
            if (mod.store.data.pending.isEmpty()) s.action("No saved returns", () -> {}, false);
            for (Data.Pending pending : mod.store.data.pending) {
                String name = mod.store.data.servers.stream().filter(server -> server.id.equals(pending.serverId)).map(server -> server.subserver).findFirst().orElse("Deleted server profile");
                s.action(name + " | " + pending.world + " | " + Rules.number(pending.x) + ", " + Rules.number(pending.y) + ", " + Rules.number(pending.z), () -> show(menu(s, "Saved return", pending.world, detail -> {
                    detail.action("Account: " + pending.account, () -> {}, false);
                    detail.action("Position: " + Rules.number(pending.x) + " " + Rules.number(pending.y) + " " + Rules.number(pending.z), () -> {}, false);
                    detail.action("Retry when this server is ready", () -> { mod.retry(pending); show(s); }, mod.multiplayer() && pending.account.equals(mod.account()));
                    detail.action("Forget this return", () -> show(confirm(detail, "Forget return?", "This permanently removes the saved return point.", () -> { mod.forget(pending); show(s); })));
                })));
            }
        });
    }
    private MenuScreen confirm(Screen parent, String title, String subtitle, Runnable confirmed) {
        return menu(parent, title, subtitle, s -> s.action("Confirm", confirmed));
    }
    private MenuScreen help(Screen parent) {
        return menu(parent, "PureFix help", "All data stays in your Minecraft config folder", s -> {
            s.action("Storage: config/purefix/purefix.json (+ .bak)", () -> {}, false);
            s.action("Open this menu with /purefix or Mod Menu.", () -> {}, false);
            s.action("EnderFix: restore command > Player Data > backup > pearl.", () -> {}, false);
            s.action("SpectateFix saves before /s <player> or /spectate <player>.", () -> {}, false);
            s.action("Leaving spectator mode clears the saved return.", () -> {}, false);
            s.action("Xaero World Map IDs take priority over seed detection.", () -> {}, false);
            s.action("SpectateFix > Check MapModCompanion reports in chat.", () -> {}, false);
            s.action("Shared/hidden seeds need manual subserver selection.", () -> {}, false);
            s.action("Set Bukkit world names before using custom worlds.", () -> {}, false);
            s.action("Server plugin permissions are still required.", () -> {}, false);
            s.action("If commands fail, use Saved returns > Retry / Forget.", () -> {}, false);
        });
    }
}
