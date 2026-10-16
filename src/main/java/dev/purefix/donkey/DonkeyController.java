package dev.purefix.donkey;

import dev.purefix.PureFix;
import dev.purefix.core.*;
import dev.purefix.ui.DonkeyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.security.SecureRandom;
import java.util.*;

public final class DonkeyController implements DonkeyTransfer.Port {
    public enum Phase { IDLE, WAITING, REVIEW, SPINNING, TRANSFERRING, DONE, STOPPED }
    public record Entry(int slot, ItemStack stack) {}
    private final PureFix mod;
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new SecureRandom();
    private Phase phase = Phase.IDLE;
    private String target = "", status = "";
    private AbstractContainerMenu menu;
    private Object level;
    private DonkeyScreen screen;
    private long startedAt, spinAt, duration;
    private int winningColor;
    private List<List<Entry>> groups = List.of();
    private final DonkeyAudio audio = new DonkeyAudio();
    private int stableTicks, inventoryBefore;
    private List<ItemStack> last = List.of();
    private List<Entry> entries = List.of(), winners = List.of();
    private final Set<Integer> excluded = new HashSet<>();
    private DonkeyTransfer transfer;
    private long delayMs;
    private int requested;
    private boolean sounds;
    public DonkeyController(PureFix mod) { this.mod = mod; }
    public Phase phase() { return phase; }
    public String target() { return target; }
    public String status() { return status; }
    public List<Entry> entries() { return entries; }
    public List<Entry> candidates() { return entries.stream().filter(e -> !excluded.contains(e.slot())).toList(); }
    public List<List<Entry>> groups() { return groups; }
    public int winningColor() { return winningColor; }
    public List<Entry> winners() { return winners; }
    public boolean excluded(int slot) { return excluded.contains(slot); }
    public int completed() { return transfer == null ? 0 : transfer.completed(); }
    public boolean busy() { return phase == Phase.WAITING || phase == Phase.REVIEW || phase == Phase.SPINNING || phase == Phase.TRANSFERRING; }
    public static long now() { return System.nanoTime() / 1_000_000; }
    public boolean intercept(String command) {
        if (!mod.settings().donkeyEnabled) return false;
        String player = DonkeyRules.player(mod.settings().donkeyAlias, command);
        if (player == null) return false;
        try { start(player); } catch (IllegalArgumentException e) { mod.message(e.getMessage()); }
        return true;
    }
    public void start(String player) {
        if (!mod.store.available()) throw new IllegalArgumentException("Fix the PureFix configuration error before starting Donkey.");
        if (!mod.multiplayer()) throw new IllegalArgumentException("Join a multiplayer server first.");
        if (!mod.settings().donkeyEnabled) throw new IllegalArgumentException("Enable Donkey in PureFix settings first.");
        if (busy()) throw new IllegalArgumentException("Donkey is already running. Finish or cancel the current draw first.");
        if (!player.matches("[A-Za-z0-9_.-]{1,36}")) throw new IllegalArgumentException("Enter a valid player name.");
        if (mc.gameMode.isSpectator()) throw new IllegalArgumentException("Leave spectator mode before transferring inventory items.");
        if (!mc.player.containerMenu.getCarried().isEmpty()) throw new IllegalArgumentException("Put down the stack on your cursor first.");
        if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        else mc.setScreenAndShow(null);
        target = player; level = mc.level; menu = null; screen = null; last = List.of(); stableTicks = 0;
        entries = winners = List.of(); groups = List.of(); excluded.clear(); transfer = null;
        requested = mod.settings().donkeyWinners; delayMs = mod.settings().donkeyDelayMs; sounds = mod.settings().donkeySound;
        phase = Phase.WAITING; startedAt = now(); status = "Waiting for " + target + "'s inventory...";
        mod.sendServerCommand(Rules.command(mod.settings().donkeyCommand.replace("{player}", target)));
    }
    public void toggle(int slot) {
        if (phase != Phase.REVIEW) return;
        if (!excluded.add(slot)) excluded.remove(slot);
        regroup();
    }
    public void spin() {
        if (phase != Phase.REVIEW || !valid()) return;
        List<Entry> pool = candidates();
        if (pool.isEmpty()) { status = "Include at least one stack."; return; }
        for (Entry e : pool) if (!ItemStack.matches(menu.getSlot(e.slot()).getItem(), e.stack())) {
            stop("The inventory changed. Reopen Donkey to capture a fresh inventory."); return;
        }
        if (groups.isEmpty()) regroup();
        winningColor = random.nextInt(groups.size()); winners = groups.get(winningColor);
        duration = DonkeyRules.duration(mod.settings().donkeySpeed);
        phase = Phase.SPINNING; spinAt = now(); status = "One color wins the whole bundle!";
        audio.start(sounds, mod.settings().donkeyOriginalSound, mod.settings().donkeySpeed);
    }
    private void regroup() {
        List<Entry> pool = candidates(); Map<Integer, Integer> identities = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            int type = i;
            for (int j = 0; j < i; j++) if (ItemStack.matches(pool.get(i).stack(), pool.get(j).stack())) { type = identities.get(pool.get(j).slot()); break; }
            identities.put(pool.get(i).slot(), type);
        }
        groups = DonkeyRules.groups(pool.stream().map(Entry::slot).toList(), identities, requested, random).stream()
            .map(group -> group.stream().map(slot -> pool.stream().filter(e -> e.slot() == slot).findFirst().orElseThrow()).toList()).toList();
    }
    public int revealed() { return phase == Phase.SPINNING || phase == Phase.REVIEW || phase == Phase.STOPPED ? 0 : winners.size(); }
    public double angle() {
        if (winners.isEmpty()) return 0;
        double t = phase == Phase.SPINNING ? Math.min(1, (now() - spinAt) / (double)duration) : 1;
        return DonkeyRules.landing(0, winningColor, groups.size()) * (1 - Math.pow(1 - t, 4));
    }
    public void tick() {
        if (!busy()) return;
        if (!mod.multiplayer() || mc.level != level || !mod.settings().donkeyEnabled) { stop("Donkey stopped: connection, world, or settings changed."); return; }
        if (phase == Phase.WAITING) { capture(); return; }
        if (!valid() || mc.gui.screen() != screen || !menu.getCarried().isEmpty()) { stop("Donkey stopped: inventory closed or changed."); return; }
        if (phase == Phase.SPINNING) audio.tick(angle(), groups.size());
        if (phase == Phase.SPINNING && now() - spinAt >= duration) {
            audio.finish();
            transfer = new DonkeyTransfer(winners.stream().map(Entry::slot).toList(), delayMs);
            phase = Phase.TRANSFERRING;
        }
        if (phase == Phase.TRANSFERRING) {
            transfer.tick(now(), this);
            status = "Transferring stacks: " + transfer.completed() + " / " + winners.size();
            if (transfer.state() == DonkeyTransfer.State.STOPPED) stop(transfer.reason());
            else if (transfer.state() == DonkeyTransfer.State.COMPLETE) {
                phase = Phase.DONE; status = "All " + winners.size() + " winning stacks transferred!";
                mod.message(status);
                if (sounds) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f));
            }
        }
    }
    private void capture() {
        if (now() - startedAt > 10000) { stop("Inventory not recognized. Check invsee permissions and the Donkey inventory-title setting."); return; }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> gui) || !(gui.getMenu() instanceof ChestMenu chest)) return;
        if (!Rules.clean(gui.getTitle().getString()).equalsIgnoreCase(Rules.clean(mod.settings().donkeyTitle.replace("{player}", target)))) return;
        if (chest != mc.player.containerMenu || !chest.getCarried().isEmpty()) return;
        List<ItemStack> items = new ArrayList<>();
        int top = chest.getRowCount() * 9;
        for (int i = 0; i < top; i++) items.add(chest.getSlot(i).getItem().copy());
        boolean same = menu == chest && items.size() == last.size();
        for (int i = 0; same && i < items.size(); i++) same = ItemStack.matches(items.get(i), last.get(i));
        stableTicks = same ? stableTicks + 1 : 0; last = items; menu = chest;
        if (stableTicks < 5 || items.stream().allMatch(ItemStack::isEmpty)) return;
        List<Entry> captured = new ArrayList<>();
        for (int i = 0; i < top; i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) continue;
            var lore = item.get(DataComponents.LORE);
            if (mod.settings().donkeyFilterInvsee && lore != null
                    && DonkeyRules.invseePlaceholder(lore.lines().stream().map(line -> line.getString()).toList())) continue;
            captured.add(new Entry(i, item));
        }
        if (captured.isEmpty()) { stop("No eligible inventory items. InvSee++ placeholders were filtered out."); return; }
        Collections.shuffle(captured, random); entries = List.copyOf(captured); regroup();
        phase = Phase.REVIEW; status = "Click slots to exclude plugin buttons. Each color holds " + Math.min(requested, entries.size()) + " slots.";
        screen = new DonkeyScreen(this); mc.setScreenAndShow(screen);
    }
    public void stop(String reason) {
        audio.stop();
        if (transfer != null) transfer.stop(reason);
        phase = Phase.STOPPED; status = reason; mod.message(reason);
    }
    public void disconnect() { if (busy()) stop("Donkey cancelled on disconnect. Completed transfers are kept."); }
    public void close() {
        if (busy()) stop("Donkey cancelled. Completed transfers are kept; an already sent click may still finish.");
        if (mc.player != null && mc.player.containerMenu == menu) mc.player.closeContainer();
        else mc.setScreenAndShow(null);
    }
    @Override public boolean valid() { return mod.multiplayer() && mc.level == level && menu != null && mc.player.containerMenu == menu; }
    private Entry entry(int slot) { return winners.stream().filter(e -> e.slot() == slot).findFirst().orElseThrow(); }
    private int staffCount(ItemStack item) {
        int count = 0;
        for (Slot slot : menu.slots) if (slot.container == mc.player.getInventory() && ItemStack.isSameItemSameComponents(item, slot.getItem())) count += slot.getItem().getCount();
        return count;
    }
    @Override public String problem(int source) {
        ItemStack stack = entry(source).stack(); Slot sourceSlot = menu.getSlot(source);
        if (mc.gameMode.isSpectator()) return "Leave spectator mode before transferring stacks.";
        if (!menu.getCarried().isEmpty()) return "The cursor holds a stack. Transfer stopped.";
        if (!sourceSlot.mayPickup(mc.player) || !ItemStack.matches(stack, sourceSlot.getItem())) return "A winning stack changed or cannot be taken. Transfer stopped.";
        int space = 0;
        for (Slot slot : menu.slots) if (slot.container == mc.player.getInventory() && slot.mayPlace(stack)) {
            ItemStack current = slot.getItem();
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack)) space += Math.max(0, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack)) - current.getCount());
        }
        return space >= stack.getCount() ? null : "Your inventory has no room for the next full stack. Transfer stopped.";
    }
    @Override public void send(int slot) {
        inventoryBefore = staffCount(entry(slot).stack());
        // Deliberately make no local inventory prediction. Empty changedSlots leaves the server's
        // remote snapshot intact, so its ordinary broadcast sends the authoritative slot updates.
        mc.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short)slot,
            (byte)0, ContainerInput.QUICK_MOVE, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }
    @Override public DonkeyTransfer.Receipt receipt(int slot) {
        ItemStack original = entry(slot).stack(), current = menu.getSlot(slot).getItem();
        if (current.isEmpty() && staffCount(original) >= inventoryBefore + original.getCount()) return DonkeyTransfer.Receipt.ACCEPTED;
        if (!current.isEmpty() && !ItemStack.matches(original, current)) return DonkeyTransfer.Receipt.REJECTED;
        return DonkeyTransfer.Receipt.PENDING;
    }
}
