package dev.purefix.core;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/** Xaero World Map LevelMapProperties: marker 0 followed by a signed big-endian int. */
public final class XaeroProtocol {
    public static final String CHANNEL = "xaeroworldmap:main";
    public static final String PREFIX = "xaero-worldmap:";
    private XaeroProtocol() {}

    public static OptionalInt decode(byte[] bytes) {
        if (bytes.length != 5 || bytes[0] != 0) return OptionalInt.empty();
        return OptionalInt.of(ByteBuffer.wrap(bytes, 1, 4).getInt());
    }
    public static boolean isIdentity(String value) { return value != null && value.startsWith(PREFIX); }
    public static String identity(int id) { return PREFIX + id; }
}
