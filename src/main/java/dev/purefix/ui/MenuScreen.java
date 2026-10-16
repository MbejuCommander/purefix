package dev.purefix.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Inventory-style panels, keyboard navigation, and responsive pages. */
public class MenuScreen extends Screen {
    protected final Screen parent;
    protected String subtitle = "";
    protected Theme.Icon icon = Theme.Icon.NONE;
    protected int accent = Theme.PEARL;
    protected boolean dashboard;
    protected int page;
    private int panelX, panelWidth, rows, pages;
    protected final List<Action> actions = new ArrayList<>();
    protected record Action(String label, String description, Theme.Icon icon, int accent, Runnable run, boolean enabled) {}
    public MenuScreen(Screen parent, String title) {
        super(Component.literal(title)); this.parent = parent;
        if (parent instanceof MenuScreen menu) { accent = menu.accent; icon = menu.icon; }
    }
    protected void populate() {}
    protected void action(String label, Runnable run) { action(label, run, true); }
    protected void action(String label, Runnable run, boolean enabled) { actions.add(new Action(label, "", Theme.Icon.NONE, accent, run, enabled)); }
    protected void card(String label, String description, Theme.Icon icon, int color, Runnable run) { actions.add(new Action(label, description, icon, color, run, true)); }
    protected void show(Screen screen) { minecraft.setScreenAndShow(screen); }
    protected void refresh() { rebuildWidgets(); }
    @Override protected void init() {
        actions.clear(); populate();
        panelWidth = Math.min(dashboard ? 426 : 406, width - 20); panelX = (width - panelWidth) / 2;
        boolean grid = dashboard && width >= 380 && height >= 230 && actions.size() <= 4;
        rows = grid ? 4 : Math.max(1, (height - 108) / 24);
        pages = Math.max(1, (actions.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int x = panelX + 12, w = panelWidth - 24;
        for (int i = page * rows; i < Math.min(actions.size(), (page + 1) * rows); i++) {
            Action action = actions.get(i); int row = i % rows;
            int buttonW = grid ? (w - 8) / 2 : w;
            int buttonX = grid ? x + (row % 2) * (buttonW + 8) : x;
            int buttonY = grid ? 68 + (row / 2) * 60 : 64 + row * 24;
            PanelButton button = new PanelButton(buttonX, buttonY, buttonW, grid ? 54 : 21,
                action.label, grid ? action.description : "", action.icon, action.accent, () -> {
                    try { action.run.run(); } catch (IllegalArgumentException e) { subtitle = e.getMessage(); }
                });
            button.active = action.enabled; addRenderableWidget(button);
        }
        if (pages > 1) {
            PanelButton previous = new PanelButton(x, height - 39, 30, 20, "<", "", Theme.Icon.NONE, accent, () -> { page--; refresh(); });
            previous.active = page > 0; addRenderableWidget(previous);
            PanelButton next = new PanelButton(x + w - 30, height - 39, 30, 20, ">", "", Theme.Icon.NONE, accent, () -> { page++; refresh(); });
            next.active = page + 1 < pages; addRenderableWidget(next);
        }
        addRenderableWidget(new PanelButton(width / 2 - 52, height - 39, 104, 20, "Back", "", Theme.Icon.NONE, accent, this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme.panel(g, panelX, 7, panelWidth, height - 14, accent);
        super.extractRenderState(g, mouseX, mouseY, delta);
        String heading = title.getString();
        int headerWidth = font.width(heading) + (icon == Theme.Icon.NONE ? 0 : 23);
        int start = width / 2 - headerWidth / 2;
        if (icon != Theme.Icon.NONE) Theme.icon(g, icon, start, 20, 16);
        g.text(font, heading, start + (icon == Theme.Icon.NONE ? 0 : 23), 24, Theme.TEXT);
        g.centeredText(font, font.plainSubstrByWidth(subtitle, panelWidth - 30), width / 2, 42, Theme.MUTED);
        g.centeredText(font, pages > 1 ? "Page " + (page + 1) + " / " + pages : dashboard ? "PUREFIX  /  1.4.1" : "PUREFIX", width / 2, height - 15, 0xFF8E9A97);
    }
    @Override public void onClose() { show(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
