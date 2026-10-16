package dev.purefix.core;

import java.util.*;

public final class Data {
    public static final String DEFAULT_DONKEY_ALIAS = "/donkey {player}";
    public static final long DEFAULT_DONKEY_DELAY_MS = 300;
    public static final List<String> DEFAULT_RESTORE_COMMANDS = List.of(
        "/ir restore {player}", "/irp restore {player}", "/inventoryrollback restore {player}");
    public int schemaVersion = 1;
    public Settings settings = new Settings();
    public List<Server> servers = new ArrayList<>();
    public List<Pending> pending = new ArrayList<>();
    public Set<String> ignored = new HashSet<>();
    public Set<String> xaeroNetworks = new HashSet<>();
    public static class Settings {
        public boolean autoCapture = true, enderEnabled = true, enderSound = true, spectateEnabled = true, spectateSound = true;
        public boolean mapModCompanionEnabled = true;
        public boolean donkeyEnabled = true, donkeySound = true;
        public String donkeyAlias = DEFAULT_DONKEY_ALIAS, donkeyCommand = "/invsee {player}";
        public String donkeyTitle = "{player}'s Inventory";
        public int donkeyWinners = 15;
        public long donkeyDelayMs = DEFAULT_DONKEY_DELAY_MS;
        public int donkeyDefaultsVersion = 2;
        public boolean donkeyOriginalSound = true;
        public boolean donkeyFilterInvsee = true;
        public double donkeySpeed = 1;
        public String enderCommand = Rules.TELEPORT, spectateCommand = Rules.TELEPORT;
        public List<String> restoreCommands = new ArrayList<>(DEFAULT_RESTORE_COMMANDS);
        public List<String> spectateCommands = new ArrayList<>(List.of("/s {player}", "/spectate {player}"));
        public String playerDataTitle = "Player Data", backupTitle = "Main Inventory Backup";
        public String pearlTitle = "Teleport to where this entry was logged.";
        public int joinDelayTicks = 0;
    }
    public static class Server {
        public String id = UUID.randomUUID().toString();
        public String name = "", address = "", subserver = "", fingerprint = "";
        public String fallbackFingerprint = "";
        public boolean automaticRecovery = true;
        public long lastSeen = System.currentTimeMillis();
        public Set<String> observedDimensions = new LinkedHashSet<>();
        public Map<String, String> worlds = new LinkedHashMap<>(Map.of(
            "minecraft:overworld", "world", "minecraft:the_nether", "world_nether", "minecraft:the_end", "world_the_end"));
    }
    public static class Pending {
        public String id = UUID.randomUUID().toString();
        public String account, serverId, dimension, world;
        public String worldFingerprint = "";
        public double x, y, z;
        public long createdAt = System.currentTimeMillis();
        public Rules.Location location() { return new Rules.Location(world, x, y, z); }
    }
    public void validate() {
        if (schemaVersion != 1 || settings == null || servers == null || pending == null || ignored == null || xaeroNetworks == null)
            throw new IllegalArgumentException("Unsupported or incomplete PureFix data.");
        Rules.validateTemplate(settings.enderCommand); Rules.validateTemplate(settings.spectateCommand);
        DonkeyRules.duration(settings.donkeySpeed);
        Rules.validateTrigger(settings.donkeyAlias, true); Rules.validateTrigger(settings.donkeyCommand, true);
        if (settings.donkeyWinners < 1 || settings.donkeyWinners > 54 || settings.donkeyDelayMs < 0
            || settings.donkeyTitle == null || !settings.donkeyTitle.contains("{player}")) throw new IllegalArgumentException("Invalid Donkey settings.");
        for (String s : settings.restoreCommands) Rules.validateTrigger(s, true);
        for (String s : settings.spectateCommands) Rules.validateTrigger(s, true);
        if (settings.playerDataTitle == null || settings.backupTitle == null || settings.pearlTitle == null
            || settings.joinDelayTicks < 0 || settings.joinDelayTicks > 1200) throw new IllegalArgumentException("Invalid settings.");
        Set<String> ids = new HashSet<>();
        for (Server s : servers) {
            if (s == null || s.id == null || !ids.add(s.id) || s.name == null || s.address == null || s.address.isBlank()
                || s.subserver == null || s.fingerprint == null || s.fallbackFingerprint == null || s.worlds == null || s.observedDimensions == null) throw new IllegalArgumentException("Invalid server record.");
            for (var entry : s.worlds.entrySet())
                if (entry.getKey() == null || !Rules.validWorld(entry.getValue())) throw new IllegalArgumentException("Invalid world mapping.");
        }
        Set<String> pendingIds = new HashSet<>();
        for (Pending p : pending) {
            if (p == null || p.id == null || !pendingIds.add(p.id) || p.account == null || p.serverId == null || p.dimension == null || p.worldFingerprint == null)
                throw new IllegalArgumentException("Invalid recovery record.");
            p.location();
        }
    }
}
