package dev.purefix.ui;

import dev.purefix.PureFix;
import dev.purefix.donkey.*;
import dev.purefix.core.DonkeyRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class DonkeyScreen extends Screen {
    private final DonkeyController controller;
    private final Screen parent;
    private final DonkeyWheel wheel = new DonkeyWheel();
    private final DonkeyAudio audio = new DonkeyAudio();
    private final List<PanelButton> slots = new ArrayList<>();
    private final List<List<Integer>> demoGroups;
    private PanelButton spin;
    private long previewStart, previewDuration;
    private int previewWinner;
    private boolean previewFinished;
    private int left, panelW, radius, centerX, centerY, gridX, gridY;
    private static final String[] DEMO_ITEMS = {"diamond", "gold_ingot", "emerald", "ender_pearl", "apple", "iron_ingot", "diamond_sword", "carrot", "netherite_ingot", "totem_of_undying", "diamond_pickaxe", "diamond_boots", "bread", "coal", "amethyst_shard"};
    public DonkeyScreen(DonkeyController controller) {
        super(Component.literal("Donkey")); this.controller = controller; parent = null; demoGroups = List.of();
    }
    private DonkeyScreen(Screen parent) {
        super(Component.literal("Donkey preview")); controller = null; this.parent = parent;
        var pool = java.util.stream.IntStream.range(0, 30).boxed().toList();
        Map<Integer, Integer> identities = new HashMap<>(); pool.forEach(i -> identities.put(i, i % DEMO_ITEMS.length));
        demoGroups = DonkeyRules.groups(pool, identities, PureFix.INSTANCE.settings().donkeyWinners, new Random());
    }
    public static Screen preview(Screen parent) { return new DonkeyScreen(parent); }
    @Override protected void init() {
        slots.clear(); panelW = Math.min(width - 12, 560); left = (width - panelW) / 2;
        radius = Math.max(35, Math.min((height - 100) / 2, (panelW - 206) / 2));
        centerX = left + (panelW - 184) / 2; centerY = 63 + radius;
        gridX = left + panelW - 178; gridY = 76;
        if (controller != null) for (var entry : controller.entries()) {
            int index = entry.slot(), x = gridX + (index % 9) * 18, y = gridY + (index / 9) * 17;
            var button = new PanelButton(x, y, 17, 17, "Slot " + (index + 1), "", Theme.Icon.NONE, Theme.GOLD, () -> controller.toggle(index)) {
                @Override public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
                    Theme.bevel(g, getX(), getY(), 17, 17, controller.excluded(index) ? 0xFF412A2D : 0xFF334039, 0xFF819085, 0xFF121817);
                    g.item(entry.stack(), getX(), getY()); g.itemDecorations(font, entry.stack(), getX(), getY());
                    if (controller.excluded(index)) g.fill(getX(), getY() + 7, getX() + 16, getY() + 9, 0xCCD13C48);
                    if (isHoveredOrFocused()) g.setTooltipForNextFrame(font, entry.stack(), mouseX, mouseY);
                }
            };
            slots.add(button); addRenderableWidget(button);
        }
        spin = addRenderableWidget(new PanelButton(left + 12, height - 35, Math.max(100, panelW / 2 - 18), 20,
            controller == null ? "Spin preview" : "Spin & transfer", "", Theme.Icon.NONE, Theme.GOLD, () -> {
                if (controller != null) { controller.spin(); return; }
                var settings = PureFix.INSTANCE.settings();
                previewStart = DonkeyController.now(); previewDuration = DonkeyRules.duration(settings.donkeySpeed);
                previewFinished = false; previewWinner = new Random().nextInt(demoGroups.size());
                audio.start(settings.donkeySound, settings.donkeyOriginalSound, settings.donkeySpeed);
            }));
        addRenderableWidget(new PanelButton(left + panelW / 2 + 6, height - 35, panelW / 2 - 18, 20,
            controller == null ? "Back" : "Cancel / close", "", Theme.Icon.NONE, Theme.GOLD, this::onClose));
    }
    @Override public void tick() {
        if (controller != null) {
            spin.active = controller.phase() == DonkeyController.Phase.REVIEW;
            slots.forEach(s -> s.active = spin.active);
        } else {
            spin.active = previewStart == 0 || previewFinished;
            if (previewStart != 0 && !previewFinished) {
                audio.tick(previewAngle(), demoGroups.size());
                if (DonkeyController.now() - previewStart >= previewDuration) { previewFinished = true; audio.finish(); }
            }
        }
    }
    private int colors() { return controller == null ? demoGroups.size() : controller.groups().size(); }
    private int groupSize(int color) { return controller == null ? demoGroups.get(color).size() : controller.groups().get(color).size(); }
    private void groupItem(GuiGraphicsExtractor g, int color, int index, int x, int y, int size, boolean details, int mouseX, int mouseY) {
        if (controller == null) {
            String name = DEMO_ITEMS[demoGroups.get(color).get(index) % DEMO_ITEMS.length];
            g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/item/" + name + ".png"), x, y, 0, 0, size, size, 16, 16, 16, 16);
        } else {
            var entry = controller.groups().get(color).get(index);
            g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(size / 16f);
            g.item(entry.stack(), 0, 0);
            if (details) g.itemDecorations(font, entry.stack(), 0, 0);
            g.pose().popMatrix();
            if (details && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size)
                g.setTooltipForNextFrame(font, entry.stack(), mouseX, mouseY);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme.panel(g, left, 8, panelW, height - 16, Theme.GOLD);
        g.centeredText(font, controller == null ? "Donkey | Preview" : "Donkey | " + controller.target(), width / 2, 22, Theme.TEXT);
        g.centeredText(font, font.plainSubstrByWidth(controller == null ? "One color wins the whole bundle!" : controller.status(), panelW - 20), width / 2, 41, Theme.MUTED);
        int count = colors(); double angle = controller == null ? previewAngle() : controller.angle();
        wheel.draw(g, centerX, centerY, radius, count, angle);
        for (int color = 0; color < count; color++) {
            int n = groupSize(color), layers = (int)Math.ceil((Math.sqrt(8 * n + 1) - 1) / 2);
            int iconSize = Math.max(3, Math.min(10, (int)(radius * .65 / layers)));
            for (int index = 0, row = 0; index < n; row++) {
                int cells = Math.min(row + 1, n - index);
                for (int col = 0; col < cells; col++, index++) {
                    double theta = angle + (color + .5 + ((col + .5) / cells - .5) * .72) * Math.PI * 2 / count;
                    double distance = radius * (.23 + .65 * (row + 1) / layers);
                    int x = centerX + (int)(Math.cos(theta) * distance) - iconSize / 2;
                    int y = centerY + (int)(Math.sin(theta) * distance) - iconSize / 2;
                    groupItem(g, color, index, x, y, iconSize, false, mouseX, mouseY);
                }
            }
        }
        int inspect = -1;
        boolean hovering = count > 0 && Math.hypot(mouseX - centerX, mouseY - centerY) < radius;
        if (hovering) {
            double theta = (Math.atan2(mouseY - centerY, mouseX - centerX) - angle) % (Math.PI * 2);
            if (theta < 0) theta += Math.PI * 2;
            inspect = Math.min(count - 1, (int)(theta / (Math.PI * 2) * count));
        } else if (controller == null) inspect = previewFinished ? previewWinner : 0;
        else if (controller.revealed() > 0) inspect = controller.winningColor();
        else if (controller.phase() == DonkeyController.Phase.SPINNING && count > 0) {
            double theta = (-Math.PI / 2 - angle) % (Math.PI * 2); if (theta < 0) theta += Math.PI * 2;
            inspect = Math.min(count - 1, (int)(theta / (Math.PI * 2) * count));
        }
        final boolean inventoryVisible = inspect < 0;
        slots.forEach(s -> s.visible = inventoryVisible);
        if (inspect >= 0 && count > 0) {
            boolean won = controller == null ? previewFinished && inspect == previewWinner : controller.revealed() > 0 && inspect == controller.winningColor();
            g.text(font, (won ? "Winner" : "Color " + (inspect + 1)) + " | " + groupSize(inspect) + " slots", gridX, gridY - 14, DonkeyWheel.color(inspect, count));
            for (int i = 0; i < groupSize(inspect); i++) groupItem(g, inspect, i, gridX + i % 9 * 18, gridY + i / 9 * 17, 16, true, mouseX, mouseY);
        } else g.text(font, "Inventory | click to exclude", gridX, gridY - 14, Theme.TEXT);
        g.text(font, "Hover a color to inspect", gridX, height - 54, Theme.MUTED);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    private double previewAngle() {
        if (previewStart == 0) return 0;
        double t = Math.min(1, (DonkeyController.now() - previewStart) / (double)previewDuration);
        return DonkeyRules.landing(0, previewWinner, demoGroups.size()) * (1 - Math.pow(1 - t, 4));
    }
    @Override public void onClose() { if (controller == null) minecraft.setScreenAndShow(parent); else controller.close(); }
    @Override public void removed() {
        audio.stop(); wheel.close();
        if (controller != null && controller.busy()) controller.stop("Donkey cancelled: the roulette screen was closed.");
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return controller != null; }
}
