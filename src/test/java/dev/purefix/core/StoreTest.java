package dev.purefix.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path directory;
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
