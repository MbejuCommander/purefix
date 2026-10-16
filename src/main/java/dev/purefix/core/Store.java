package dev.purefix.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;

/** Atomic, forced writes; a corrupt file is preserved and never silently replaced. */
public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final Path path;
    public Data data = new Data();
    public String error = "";
    private boolean blocked;
    public Store(Path directory) { path = directory.resolve("purefix.json"); }
    public void load() {
        if (!Files.exists(path)) return;
        try {
            var json = com.google.gson.JsonParser.parseString(Files.readString(path));
            Data loaded = GSON.fromJson(json, Data.class);
            if (loaded == null) throw new IllegalArgumentException("Empty data file.");
            loaded.validate();
            // The removed exit list identifies an older configuration. Migrate its old delay default once.
            if (json.getAsJsonObject().has("settings") && json.getAsJsonObject().getAsJsonObject("settings").has("exitCommands")
                && loaded.settings.joinDelayTicks == 10) loaded.settings.joinDelayTicks = 0;
            // Upgrade only the untouched 1.0 default list; preserve deliberate user customizations.
            if (loaded.settings.restoreCommands.equals(java.util.List.of("/inventoryrollback restore {player}", "/ir restore {player}")))
                loaded.settings.restoreCommands = new java.util.ArrayList<>(Data.DEFAULT_RESTORE_COMMANDS);
            var settingsJson = json.getAsJsonObject().getAsJsonObject("settings");
            if (settingsJson != null && (!settingsJson.has("donkeyDefaultsVersion") || loaded.settings.donkeyDefaultsVersion < 2)) {
                if (Rules.command(loaded.settings.donkeyAlias).equals("shame {player}")) loaded.settings.donkeyAlias = Data.DEFAULT_DONKEY_ALIAS;
                if (loaded.settings.donkeyDelayMs == 2000) loaded.settings.donkeyDelayMs = Data.DEFAULT_DONKEY_DELAY_MS;
                loaded.settings.donkeyDefaultsVersion = 2;
            }
            data = loaded;
        } catch (Exception e) { blocked = true; error = "Cannot load PureFix data. Original file preserved: " + e.getMessage(); }
    }
    public synchronized boolean save() {
        if (blocked) return false;
        try {
            data.validate();
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("purefix.json.tmp");
            byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, CREATE, TRUNCATE_EXISTING, WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling("purefix.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            error = ""; return true;
        } catch (Exception e) { error = "Could not save PureFix data: " + e.getMessage(); return false; }
    }
    public boolean available() { return !blocked; }
    public Path exportCsv() throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder csv = new StringBuilder("name,address,subserver,fingerprint,dimension,bukkit_world\r\n");
        for (Data.Server server : data.servers) for (var world : server.worlds.entrySet())
            csv.append(String.join(",", Rules.csv(server.name), Rules.csv(server.address), Rules.csv(server.subserver),
                Rules.csv(server.fingerprint), Rules.csv(world.getKey()), Rules.csv(world.getValue()))).append("\r\n");
        Path result = path.resolveSibling("servers.csv");
        Files.writeString(result, csv, StandardCharsets.UTF_8); return result;
    }
}
