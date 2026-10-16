package dev.purefix.core;

import java.util.*;

public final class DonkeyRules {
    private DonkeyRules() {}
    public static boolean invseePlaceholder(List<String> lore) {
        return lore != null && lore.stream().filter(Objects::nonNull)
            .map(Rules::clean).anyMatch(line -> line.equalsIgnoreCase("InvSee++ placeholder"));
    }
    public static long delay(String value) {
        if (value == null || value.isBlank()) return Data.DEFAULT_DONKEY_DELAY_MS;
        try {
            long result = Long.parseLong(value.strip());
            if (result < 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a non-negative whole number of milliseconds, or leave blank for 300."); }
    }
    public static List<Integer> draw(List<Integer> slots, int count, Random random) {
        if (count < 1) throw new IllegalArgumentException("Choose at least one winning stack.");
        List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(slots));
        Collections.shuffle(unique, random);
        return List.copyOf(unique.subList(0, Math.min(count, unique.size())));
    }
    /** Identity is the full stack content, so reordered or visually identical bundles do not repeat. */
    public static List<List<Integer>> groups(List<Integer> slots, Map<Integer, Integer> identities, int size, Random random) {
        if (size < 1) throw new IllegalArgumentException("Choose at least one slot per color.");
        var unique = new ArrayList<>(new LinkedHashSet<>(slots));
        int take = Math.min(size, unique.size());
        if (take == 0) return List.of();
        int limit = Math.max(8, (unique.size() + take - 1) / take);
        Map<List<Integer>, List<Integer>> result = new LinkedHashMap<>();
        // Cover every source slot before filling extra sectors with random combinations.
        Collections.shuffle(unique, random);
        for (int at = 0; at < unique.size(); at += take) {
            var selected = new ArrayList<>(unique.subList(at, Math.min(at + take, unique.size())));
            var rest = new ArrayList<>(unique); rest.removeAll(selected); Collections.shuffle(rest, random);
            selected.addAll(rest.subList(0, take - selected.size())); Collections.shuffle(selected, random);
            result.putIfAbsent(selected.stream().map(identities::get).sorted().toList(), List.copyOf(selected));
        }
        for (int attempt = 0; attempt < 256 && result.size() < limit; attempt++) {
            var selected = draw(unique, take, random);
            result.putIfAbsent(selected.stream().map(identities::get).sorted().toList(), selected);
        }
        if (result.size() < limit) {
            Map<Integer, List<Integer>> types = new LinkedHashMap<>();
            Collections.shuffle(unique, random);
            for (int slot : unique) types.computeIfAbsent(identities.get(slot), k -> new ArrayList<>()).add(slot);
            var pools = new ArrayList<>(types.values());
            int[] remaining = new int[pools.size() + 1];
            for (int i = pools.size() - 1; i >= 0; i--) remaining[i] = remaining[i + 1] + pools.get(i).size();
            enumerate(pools, remaining, 0, take, new ArrayList<>(), identities, result, limit);
        }
        return List.copyOf(result.values());
    }
    private static void enumerate(List<List<Integer>> pools, int[] remaining, int at, int left,
            List<Integer> chosen, Map<Integer, Integer> identities, Map<List<Integer>, List<Integer>> result, int limit) {
        if (result.size() == limit || left > remaining[at]) return;
        if (left == 0) {
            result.putIfAbsent(chosen.stream().map(identities::get).sorted().toList(), List.copyOf(chosen));
            return;
        }
        if (at == pools.size()) return;
        var pool = pools.get(at);
        for (int n = Math.max(0, left - remaining[at + 1]); n <= Math.min(left, pool.size()) && result.size() < limit; n++) {
            int before = chosen.size(); chosen.addAll(pool.subList(0, n));
            enumerate(pools, remaining, at + 1, left - n, chosen, identities, result, limit);
            chosen.subList(before, chosen.size()).clear();
        }
    }
    public static long duration(double speed) {
        if (!Double.isFinite(speed) || speed < 1 || speed > 2) throw new IllegalArgumentException("Use a speed from 1 to 2.");
        return Math.round(8000 / speed);
    }
    public static String player(String pattern, String command) {
        if (!Rules.matches(pattern, command)) return null;
        String[] parts = Rules.command(command).split(" ");
        return parts[parts.length - 1];
    }
    public static double landing(double start, int sector, int sectors) {
        double turn = Math.PI * 2;
        double base = -Math.PI / 2 - (sector + .5) * turn / sectors;
        return base + (Math.ceil((start - base) / turn) + 3) * turn;
    }
}
