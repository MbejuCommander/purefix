package dev.purefix.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    @Test void screenshotCoordinatesArePreserved() {
        var point = Rules.parseLore(List.of("\u00a76World: \u00a7fworld_nether", "X: -145.6", "Y: 128.0", "Z: 308.7")).orElseThrow();
        assertEquals("co teleport #world_nether -145.6 128 308.7", Rules.render(Rules.TELEPORT, point));
    }
    @Test void invalidAndAmbiguousLoreNeverProducesACommand() {
        for (String invalid : List.of("NaN", "Infinity", "1 2", "1e8", "30,5", "30000001"))
            assertTrue(Rules.parseLore(List.of("World: world", "X: " + invalid, "Y: 90", "Z: 0")).isEmpty());
        assertTrue(Rules.parseLore(List.of("World: world", "X: 0", "X: 1", "Y: 90", "Z: 0")).isEmpty());
        assertTrue(Rules.parseLore(List.of("World: world\n/s other", "X: 0", "Y: 90", "Z: 0")).isEmpty());
        assertTrue(Rules.parseLore(List.of("World: world", "X: 0", "Y: 90")).isEmpty());
    }
    @Test void namespacedWorldAndFractionalNegativeCoordinates() {
        var point = Rules.parseLore(List.of("World: custom:moon", "X: -.5", "Y: -64", "Z: +12.25")).orElseThrow();
        assertEquals("custom:moon", point.world()); assertEquals(-0.5, point.x());
    }
    @Test void patternsAreAnchoredAndTreatPlayerAsOneToken() {
        assertTrue(Rules.matches("/inventoryrollback restore {player}", "inventoryrollback restore Alex_2"));
        assertFalse(Rules.matches("/s {player}", "s Alex extra"));
        assertFalse(Rules.matches("/s {player}", "s"));
        assertFalse(Rules.matches("/s {player}", "say Alex"));
        assertTrue(Rules.matches("/spectate", "spectate"));
        Rules.validateTrigger("/inventoryrollbackplus:ir restore {player}", true);
        assertThrows(IllegalArgumentException.class, () -> Rules.validateTrigger("/s {other}", true));
    }
    @Test void templatesRequireAllFieldsAndRejectUnknownFields() {
        assertThrows(IllegalArgumentException.class, () -> Rules.validateTemplate("/co teleport #{world} x y z"));
        assertThrows(IllegalArgumentException.class, () -> Rules.validateTemplate(Rules.TELEPORT + " {player}"));
        assertThrows(IllegalArgumentException.class, () -> Rules.validateTemplate(Rules.TELEPORT + "\n/op Alex"));
        assertEquals("tp 0 90 0 world_nether", Rules.render("/tp {x} {y} {z} {world}", new Rules.Location("world_nether", 0, 90, 0)));
    }
    @Test void coreProtectLengthLimitAndDecimalPrecision() {
        assertEquals("123.123", Rules.number(123.123456789123));
        assertTrue(Rules.number(-29999999.123456789).length() < 12);
        assertFalse(Rules.number(0.000000002).contains("E"));
        assertEquals("0", Rules.number(-0.0));
    }
    @Test void canonicalAddressAndCsvQuoting() {
        assertEquals("example.com", Rules.address("EXAMPLE.com:25565"));
        assertEquals("[::1]", Rules.address("[::1]:25565"));
        assertEquals("example.com:25566", Rules.address("example.com:25566"));
        assertEquals("\"Name, \"\"quoted\"\"\"", Rules.csv("Name, \"quoted\""));
    }
}
