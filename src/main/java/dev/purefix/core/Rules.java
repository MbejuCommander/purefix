package dev.purefix.core;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Pure, Minecraft-independent parsing and validation. */
public final class Rules {
    private Rules() {}
    public static final String TELEPORT = "/co teleport #{world} {x} {y} {z}";
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9_.:/-]{1,128}");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    public record Location(String world, double x, double y, double z) {
        public Location {
            if (!validWorld(world) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 20_000_000)
                throw new IllegalArgumentException("Invalid world or coordinates.");
        }
    }
    public static String clean(String text) { return text.replaceAll("\u00a7[0-9A-FK-ORa-fk-orxX]", "").strip(); }
    public static String command(String text) { return text.strip().replaceFirst("^/", "").replaceAll("\\s+", " "); }
    public static boolean validWorld(String world) { return world != null && WORLD.matcher(world).matches(); }
    public static boolean matches(String pattern, String input) {
        String[] p = command(pattern).split(" "), c = command(input).split(" ");
        if (p.length != c.length) return false;
        for (int i = 0; i < p.length; i++) {
            if (p[i].equals("{player}")) { if (!c[i].matches("[A-Za-z0-9_.-]{1,36}")) return false; }
            else if (!p[i].equalsIgnoreCase(c[i])) return false;
        }
        return true;
    }
    public static void validateTrigger(String value, boolean playerRequired) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("Enter a single command pattern.");
        String normalized = command(value);
        if (!normalized.matches("[A-Za-z0-9_:. -]+(?: \\{player})?")
            || (playerRequired && !normalized.endsWith(" {player}")))
            throw new IllegalArgumentException("Use a command followed by {player}.");
    }
    public static void validateTemplate(String template) {
        if (template == null || template.isBlank() || template.length() > 240 || template.contains("\n") || template.contains("\r"))
            throw new IllegalArgumentException("Enter one teleport command (up to 240 characters).");
        String rest = template;
        for (String field : List.of("world", "x", "y", "z")) {
            if (!template.contains("{" + field + "}")) throw new IllegalArgumentException("Missing {" + field + "}.");
            rest = rest.replace("{" + field + "}", "");
        }
        if (rest.contains("{") || rest.contains("}")) throw new IllegalArgumentException("Unknown placeholder.");
    }
    public static String number(double value) {
        // CoreProtect rejects coordinate tokens of 12+ characters. Milliblock precision is
        // ample for a return point; reduce fractional digits near the world border.
        BigDecimal decimal = BigDecimal.valueOf(value);
        for (int scale = 3; scale >= 0; scale--) {
            String result = decimal.setScale(scale, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            if (result.length() < 12) return result;
        }
        throw new IllegalArgumentException("Coordinate is too long for CoreProtect.");
    }
    public static String render(String template, Location point) {
        validateTemplate(template);
        String result = command(template.replace("{world}", point.world()).replace("{x}", number(point.x()))
            .replace("{y}", number(point.y())).replace("{z}", number(point.z())));
        if (result.length() > 256) throw new IllegalArgumentException("Expanded command exceeds 256 characters.");
        return result;
    }
    public static Optional<Location> parseLore(List<String> lines) {
        Map<String, String> fields = new HashMap<>();
        for (String line : lines) {
            String[] pair = clean(line).split(":", 2);
            if (pair.length != 2) continue;
            String key = pair[0].strip().toLowerCase(Locale.ROOT);
            if (Set.of("world", "x", "y", "z").contains(key)
                && fields.putIfAbsent(key, pair[1].strip()) != null) return Optional.empty();
        }
        try {
            for (String axis : List.of("x", "y", "z"))
                if (!NUMBER.matcher(fields.getOrDefault(axis, "")).matches()) return Optional.empty();
            return Optional.of(new Location(fields.get("world"), Double.parseDouble(fields.get("x")),
                Double.parseDouble(fields.get("y")), Double.parseDouble(fields.get("z"))));
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
    public static String address(String value) {
        String result = value.strip().toLowerCase(Locale.ROOT);
        return result.endsWith(":25565") ? result.substring(0, result.length() - 6) : result;
    }
    public static String defaultWorld(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "world";
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> "";
        };
    }
    public static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
