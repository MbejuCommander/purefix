package dev.purefix.core;

import java.util.List;

/** One normal inventory click in flight; only server-observed changes advance the queue. */
public final class DonkeyTransfer {
    public enum State { READY, WAITING, COMPLETE, STOPPED }
    public enum Receipt { PENDING, ACCEPTED, REJECTED }
    public interface Port {
        boolean valid();
        String problem(int slot);
        void send(int slot);
        Receipt receipt(int slot);
    }
    private final List<Integer> slots;
    private final long delayMs;
    private int completed;
    private long sentAt, confirmedAt;
    private State state = State.READY;
    private String reason = "";
    public DonkeyTransfer(List<Integer> slots, long delayMs) {
        if (delayMs < 0) throw new IllegalArgumentException("Negative delay");
        this.slots = List.copyOf(slots); this.delayMs = delayMs;
    }
    public State state() { return state; }
    public int completed() { return completed; }
    public String reason() { return reason; }
    public void stop(String reason) { if (state != State.COMPLETE) { state = State.STOPPED; this.reason = reason; } }
    public void tick(long now, Port port) {
        if (state == State.STOPPED || state == State.COMPLETE) return;
        if (!port.valid()) { stop("Inventory closed or changed. Transfer stopped."); return; }
        if (completed == slots.size()) { state = State.COMPLETE; return; }
        int slot = slots.get(completed);
        if (state == State.WAITING) {
            Receipt receipt = port.receipt(slot);
            if (receipt == Receipt.ACCEPTED) {
                completed++; confirmedAt = now;
                state = completed == slots.size() ? State.COMPLETE : State.READY;
            } else if (receipt == Receipt.REJECTED) stop("The stack changed or was only partly moved. Check the inventory before starting again.");
            else if (now - sentAt >= 10000) stop("No transfer confirmation. Check inventory space and invsee edit permissions.");
            return;
        }
        if (completed > 0 && now - confirmedAt < delayMs) return;
        String problem = port.problem(slot);
        if (problem != null) { stop(problem); return; }
        state = State.WAITING; sentAt = now;
        try { port.send(slot); } catch (RuntimeException e) { stop("The inventory click could not be sent."); }
    }
}
