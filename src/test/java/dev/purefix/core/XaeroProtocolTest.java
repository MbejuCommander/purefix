package dev.purefix.core;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class XaeroProtocolTest {
    @Test void decodesPublishedMapModCompanionExample() {
        assertEquals(1337, XaeroProtocol.decode(new byte[]{0, 0, 0, 5, 57}).orElseThrow());
    }
    @Test void signedWorldIdsIncludeZeroAndNegativeNumbers() {
        for (int id : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            byte[] wire = ByteBuffer.allocate(5).put((byte)0).putInt(id).array();
            assertEquals(id, XaeroProtocol.decode(wire).orElseThrow());
        }
    }
    @Test void unknownTruncatedOrExtendedPacketsAreNotWorldIds() {
        for (byte[] wire : new byte[][]{ {}, {0}, {0, 1, 2, 3}, {1, 0, 0, 0, 1}, {0, 0, 0, 0, 1, 2} })
            assertTrue(XaeroProtocol.decode(wire).isEmpty());
    }
}
