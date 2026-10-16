package dev.purefix.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path directory;
    @Test void donkeyDefaultsMigrateAndOptionsSurviveRestart() throws Exception {
        Files.writeString(directory.resolve("purefix.json"), "{\"settings\":{}}");
        Store store = new Store(directory); store.load(); assertTrue(store.available());
        assertTrue(store.data.settings.donkeyEnabled); assertTrue(store.data.settings.donkeySound);
        assertTrue(store.data.settings.donkeyFilterInvsee);
        assertTrue(store.data.settings.donkeyOriginalSound); assertEquals(1, store.data.settings.donkeySpeed);
        assertEquals(15, store.data.settings.donkeyWinners); assertEquals(300, store.data.settings.donkeyDelayMs);
        assertEquals("/donkey {player}", store.data.settings.donkeyAlias);
        store.data.settings.donkeyEnabled = false; store.data.settings.donkeySound = false;
        store.data.settings.donkeyFilterInvsee = false;
        store.data.settings.donkeyOriginalSound = false; store.data.settings.donkeySpeed = 1.5;
        store.data.settings.donkeyWinners = 7; store.data.settings.donkeyDelayMs = 3456;
        assertTrue(store.save()); Store restart = new Store(directory); restart.load();
        assertFalse(restart.data.settings.donkeyFilterInvsee);
        assertFalse(restart.data.settings.donkeyOriginalSound); assertEquals(1.5, restart.data.settings.donkeySpeed);
        assertFalse(restart.data.settings.donkeyEnabled); assertFalse(restart.data.settings.donkeySound);
        assertEquals(7, restart.data.settings.donkeyWinners); assertEquals(3456, restart.data.settings.donkeyDelayMs);
    }
    @Test void oldDonkeyDefaultsMigrateOnceAndCustomValuesSurvive() throws Exception {
        Files.writeString(directory.resolve("purefix.json"), "{\"settings\":{\"donkeyAlias\":\"/shame {player}\",\"donkeyDelayMs\":2000}}");
        Store old = new Store(directory); old.load(); assertTrue(old.available());
        assertEquals("/donkey {player}", old.data.settings.donkeyAlias); assertEquals(300, old.data.settings.donkeyDelayMs);
        assertTrue(old.save());
        old.data.settings.donkeyAlias = "/shame {player}"; old.data.settings.donkeyDelayMs = 2000; assertTrue(old.save());
        Store restored = new Store(directory); restored.load();
        assertEquals("/shame {player}", restored.data.settings.donkeyAlias); assertEquals(2000, restored.data.settings.donkeyDelayMs);
        Files.writeString(directory.resolve("purefix.json"), "{\"settings\":{\"donkeyAlias\":\"/loot {player}\",\"donkeyDelayMs\":750}}");
        Store custom = new Store(directory); custom.load(); assertTrue(custom.available());
        assertEquals("/loot {player}", custom.data.settings.donkeyAlias); assertEquals(750, custom.data.settings.donkeyDelayMs);
    }
    @Test void legacyDelayDefaultMigratesAndOtherDelaysSurvive() throws Exception {
        for (int delay : new int[]{10, 0, 60}) {
            Files.writeString(directory.resolve("purefix.json"), "{\"settings\":{\"joinDelayTicks\":" + delay + ",\"exitCommands\":[\"/s\"]}}");
            Store migrated = new Store(directory); migrated.load();
            assertTrue(migrated.available());
            assertEquals(delay == 10 ? 0 : delay, migrated.data.settings.joinDelayTicks);
            assertTrue(migrated.data.settings.mapModCompanionEnabled);
            assertTrue(migrated.save());
            assertFalse(Files.readString(migrated.path).contains("exitCommands"));
        }
        Store current = new Store(directory); current.load(); current.data.settings.joinDelayTicks = 10;
        current.data.settings.mapModCompanionEnabled = false; assertTrue(current.save());
        Store restart = new Store(directory); restart.load();
        assertEquals(10, restart.data.settings.joinDelayTicks); assertFalse(restart.data.settings.mapModCompanionEnabled);
    }
    @Test void upgradeUntouchedDefaultsButKeepCustomCommandLists() throws Exception {
        Store original = new Store(directory);
        original.data.settings.restoreCommands = new java.util.ArrayList<>(java.util.List.of("/inventoryrollback restore {player}", "/ir restore {player}"));
        assertTrue(original.save());
        Store upgraded = new Store(directory); upgraded.load(); assertEquals(Data.DEFAULT_RESTORE_COMMANDS, upgraded.data.settings.restoreCommands);
        upgraded.data.settings.restoreCommands = new java.util.ArrayList<>(java.util.List.of("/custom restore {player}"));
        assertTrue(upgraded.save());
        Store customized = new Store(directory); customized.load(); assertEquals(java.util.List.of("/custom restore {player}"), customized.data.settings.restoreCommands);
    }
    @Test void returnSurvivesRestartAndKeepsFullPrecision() throws Exception {
        Store first = new Store(directory); first.load();
        Data.Server server = new Data.Server(); server.address = "example.com"; server.subserver = "Survival 2";
        first.data.servers.add(server);
        Data.Pending p = new Data.Pending(); p.account = "account-a"; p.serverId = server.id;
        p.dimension = "minecraft:the_nether"; p.world = "world_nether"; p.x = -145.6123456789; p.y = 128; p.z = 308.7;
        first.data.pending.add(p); assertTrue(first.save());
        Store second = new Store(directory); second.load();
        assertTrue(second.available()); assertEquals(p.x, second.data.pending.getFirst().x);
        assertEquals(server.id, second.data.pending.getFirst().serverId);
        second.data.pending.clear(); assertTrue(second.save());
        assertTrue(Files.readString(directory.resolve("purefix.json.bak")).contains(p.id));
        Store third = new Store(directory); third.load(); assertTrue(third.data.pending.isEmpty());
    }
    @Test void malformedDataIsNotOverwritten() throws Exception {
        Path file = directory.resolve("purefix.json"); Files.writeString(file, "{broken}");
        Store store = new Store(directory); store.load(); assertFalse(store.available());
        assertFalse(store.save()); assertEquals("{broken}", Files.readString(file));
    }
    @Test void futureSchemaAndNullFieldsFailClosed() throws Exception {
        for (String data : new String[]{"null", "{\"schemaVersion\":2}", "{\"settings\":null}", "{\"servers\":[null]}"}) {
            Files.writeString(directory.resolve("purefix.json"), data);
            Store store = new Store(directory); store.load(); assertFalse(store.available()); assertFalse(store.save());
        }
    }
    @Test void writeFailureIsReported() throws Exception {
        Path file = directory.resolve("not-a-directory"); Files.writeString(file, "x");
        Store store = new Store(file); assertFalse(store.save()); assertFalse(store.error.isBlank());
    }
    @Test void csvExportsAllMappings() throws Exception {
        Store store = new Store(directory); Data.Server server = new Data.Server(); server.name = "Staff, \"test\""; server.address = "example.com";
        store.data.servers.add(server); String csv = Files.readString(store.exportCsv());
        assertTrue(csv.contains("\"Staff, \"\"test\"\"\"")); assertTrue(csv.contains("world_nether"));
        assertEquals(4, csv.lines().count());
    }
}
