package dev.purefix.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DonkeyRulesTest {
    @Test void recognizesInvseePlaceholderLoreWithFormatting() {
        assertTrue(DonkeyRules.invseePlaceholder(List.of("InvSee++ placeholder")));
        assertTrue(DonkeyRules.invseePlaceholder(List.of("Off-hand", "\u00a75\u00a7oInvSee++ placeholder")));
        assertTrue(DonkeyRules.invseePlaceholder(List.of("", " InvSee++ placeholder ")));
    }
    @Test void ordinaryGlassAndUnrelatedCustomLoreRemainEligible() {
        assertFalse(DonkeyRules.invseePlaceholder(List.of()));
        assertFalse(DonkeyRules.invseePlaceholder(null));
        assertFalse(DonkeyRules.invseePlaceholder(List.of("Off-hand", "A player's glass pane")));
        assertFalse(DonkeyRules.invseePlaceholder(List.of("Not an InvSee++ placeholder")));
    }
    @Test void selectsDistinctSourceStacksAndCapsSmallInventories() {
        var pool = java.util.stream.IntStream.range(0, 45).boxed().toList();
        for (int seed = 0; seed < 100; seed++) {
            var draw = DonkeyRules.draw(pool, 15, new Random(seed));
            assertEquals(15, new HashSet<>(draw).size());
            assertTrue(pool.containsAll(draw));
        }
        assertEquals(Set.of(1, 2), new HashSet<>(DonkeyRules.draw(List.of(1, 1, 2), 15, new Random(0))));
        assertTrue(DonkeyRules.draw(List.of(), 15, new Random()).isEmpty());
    }
    @Test void delayAllowsBlankZeroAndLargeValuesButRejectsInvalidInput() {
        assertEquals(300, DonkeyRules.delay("  "));
        assertEquals(0, DonkeyRules.delay("0"));
        assertEquals(Long.MAX_VALUE, DonkeyRules.delay(Long.toString(Long.MAX_VALUE)));
        for (String invalid : List.of("-1", "1.5", "abc", "9223372036854775808"))
            assertThrows(IllegalArgumentException.class, () -> DonkeyRules.delay(invalid));
    }
    @Test void colorsHoldWholeBundlesWithoutDuplicateCombinations() {
        var pool = java.util.stream.IntStream.range(0, 40).boxed().toList();
        Map<Integer, Integer> identities = new HashMap<>(); pool.forEach(i -> identities.put(i, i));
        var groups = DonkeyRules.groups(pool, identities, 15, new Random(4));
        assertEquals(8, groups.size());
        assertEquals(new HashSet<>(pool), groups.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet()));
        Set<Set<Integer>> signatures = new HashSet<>();
        for (var group : groups) {
            assertEquals(15, group.size()); assertEquals(15, new HashSet<>(group).size());
            assertTrue(pool.containsAll(group)); assertTrue(signatures.add(new HashSet<>(group)));
        }
    }
    @Test void sameItemBundlesCannotRepeatByUsingDifferentEquivalentSlots() {
        var pool = java.util.stream.IntStream.range(0, 40).boxed().toList();
        Map<Integer, Integer> identities = new HashMap<>(); pool.forEach(i -> identities.put(i, i < 38 ? 0 : 1));
        var groups = DonkeyRules.groups(pool, identities, 15, new Random(6));
        assertEquals(3, groups.size()); // zero, one, or two copies of the second item type
        Set<List<Integer>> signatures = new HashSet<>();
        for (var group : groups) assertTrue(signatures.add(group.stream().map(identities::get).sorted().toList()));
        pool.forEach(i -> identities.put(i, 0));
        assertEquals(1, DonkeyRules.groups(pool, identities, 15, new Random(7)).size());
    }
    @Test void fewSlotsProduceOnlyPossibleDistinctBundles() {
        var ids = Map.of(0, 0, 1, 1, 2, 2);
        assertEquals(3, DonkeyRules.groups(List.of(0, 1, 2), ids, 2, new Random()).size());
        assertEquals(List.of(Set.of(0, 1, 2)), DonkeyRules.groups(List.of(0, 1, 2), ids, 15, new Random()).stream().map(HashSet::new).toList());
        assertTrue(DonkeyRules.groups(List.of(), Map.of(), 15, new Random()).isEmpty());
    }
    @Test void singleSlotColorsStillIncludeEveryDistinctItem() {
        var pool = java.util.stream.IntStream.range(0, 54).boxed().toList();
        Map<Integer, Integer> identities = new HashMap<>(); pool.forEach(i -> identities.put(i, i));
        assertEquals(54, DonkeyRules.groups(pool, identities, 1, new Random()).size());
    }
    @Test void animationAndSoundShareSpeedWithEightSecondDefault() {
        assertEquals(8000, DonkeyRules.duration(1));
        assertEquals(4000, DonkeyRules.duration(2));
        assertEquals(6400, DonkeyRules.duration(1.25));
        for (double speed : new double[]{0, .5, 2.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> DonkeyRules.duration(speed));
    }
    @Test void aliasRequiresOneTarget() {
        assertEquals("Steve", DonkeyRules.player("/shame {player}", "shame Steve"));
        assertNull(DonkeyRules.player("/shame {player}", "invsee Steve"));
        assertNull(DonkeyRules.player("/shame {player}", "shame Steve extra"));
    }
    @Test void EveryWinningSectorLandsUnderTopPointer() {
        for (int count = 1; count <= 54; count++) for (int i = 0; i < count; i++) {
            double angle = DonkeyRules.landing(12.4, i, count);
            double center = angle + (i + .5) * Math.PI * 2 / count;
            assertEquals(0, Math.cos(center), 1e-10);
            assertEquals(-1, Math.sin(center), 1e-10);
            assertTrue(angle - 12.4 >= 6 * Math.PI);
        }
    }
}
