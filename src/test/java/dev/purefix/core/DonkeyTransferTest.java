package dev.purefix.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.purefix.core.DonkeyTransfer.*;

class DonkeyTransferTest {
    static class Server implements Port {
        boolean connected = true;
        String problem;
        Receipt reply = Receipt.PENDING;
        List<Integer> clicks = new ArrayList<>();
        public boolean valid() { return connected; }
        public String problem(int slot) { return problem; }
        public void send(int slot) { clicks.add(slot); reply = Receipt.PENDING; }
        public Receipt receipt(int slot) { return reply; }
    }
    @Test void transfersFifteenStacksOnceEachAndWaitsAfterAcknowledgement() {
        var slots = java.util.stream.IntStream.range(0, 15).boxed().toList();
        var queue = new DonkeyTransfer(slots, 2000); var server = new Server();
        long now = 0;
        for (int i = 0; i < 15; i++) {
            queue.tick(now, server); assertEquals(i + 1, server.clicks.size());
            queue.tick(now + 100, server); assertEquals(i + 1, server.clicks.size());
            server.reply = Receipt.ACCEPTED; queue.tick(now + 200, server);
            assertEquals(i + 1, queue.completed());
            queue.tick(now + 2199, server); assertEquals(i + 1, server.clicks.size());
            now += 2200;
        }
        assertEquals(State.COMPLETE, queue.state()); assertEquals(slots, server.clicks);
    }
    @Test void timeoutNeverRetriesUnconfirmedClick() {
        var queue = new DonkeyTransfer(List.of(1, 2), 0); var server = new Server();
        queue.tick(0, server); queue.tick(9999, server); assertEquals(State.WAITING, queue.state());
        queue.tick(10000, server); queue.tick(20000, server);
        assertEquals(State.STOPPED, queue.state()); assertEquals(List.of(1), server.clicks);
    }
    @Test void zeroDelayStillWaitsForServerBeforeSendingNextStack() {
        var queue = new DonkeyTransfer(List.of(1, 2), 0); var server = new Server();
        queue.tick(0, server); queue.tick(50, server); assertEquals(1, server.clicks.size());
        server.reply = Receipt.ACCEPTED; queue.tick(100, server); assertEquals(1, server.clicks.size());
        queue.tick(150, server); assertEquals(List.of(1, 2), server.clicks);
    }
    @Test void fullOrChangedInventoryStopsBeforeClick() {
        for (String problem : List.of("Inventory full", "Winning stack changed")) {
            var queue = new DonkeyTransfer(List.of(1), 2000); var server = new Server(); server.problem = problem;
            queue.tick(0, server); assertEquals(State.STOPPED, queue.state()); assertTrue(server.clicks.isEmpty());
        }
    }
    @Test void disconnectCancellationAndRejectedPartialMoveStopQueue() {
        for (int condition = 0; condition < 3; condition++) {
            var queue = new DonkeyTransfer(List.of(1, 2), 0); var server = new Server(); queue.tick(0, server);
            if (condition == 0) server.connected = false;
            if (condition == 1) queue.stop("Cancelled");
            if (condition == 2) server.reply = Receipt.REJECTED;
            queue.tick(50, server); queue.tick(100, server);
            assertEquals(State.STOPPED, queue.state()); assertEquals(0, queue.completed()); assertEquals(1, server.clicks.size());
        }
    }
    @Test void largestDelayDoesNotOverflowIntoImmediateTransfer() {
        var queue = new DonkeyTransfer(List.of(1, 2), Long.MAX_VALUE); var server = new Server();
        queue.tick(100, server); server.reply = Receipt.ACCEPTED; queue.tick(200, server); queue.tick(100000, server);
        assertEquals(1, server.clicks.size());
    }
}
