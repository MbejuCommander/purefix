package dev.purefix.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

class PanelButton extends Button {
    private final int accent;
    private final Theme.Icon icon;
    private final String description;
    PanelButton(int x, int y, int w, int h, String label, String description, Theme.Icon icon, int accent, Runnable run) {
        super(x, y, w, h, Component.literal(label), button -> run.run(), DEFAULT_NARRATION);
        this.accent = accent; this.icon = icon; this.description = description;
        setTooltip(Tooltip.create(Component.literal(description.isBlank() ? label : label + "\n" + description)));
    }
    @Override public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean focused = active && isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        Theme.bevel(g, x, y, w, h, !active ? 0xFF252D30 : focused ? 0xFF3C504E : 0xFF303D41,
            focused ? accent : 0xFF66716D, 0xFF0F171A);
        if (active) g.fill(x + 2, y + 2, x + 4, y + h - 2, accent);
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString(), badge = "";
        if (label.endsWith(": ON") || label.endsWith(": OFF")) {
            int colon = label.lastIndexOf(':'); badge = label.substring(colon + 2); label = label.substring(0, colon);
        }
        int iconSize = description.isBlank() ? 16 : 22;
        int textX = x + 10;
        if (icon != Theme.Icon.NONE) {
            Theme.icon(g, icon, x + 10, y + (h - iconSize) / 2, iconSize);
            textX += iconSize + 8;
        }
        int available = x + w - textX - (badge.isBlank() ? 9 : 37);
        int textY = y + (description.isBlank() ? (h - 8) / 2 : 12);
        g.text(font, fit(label, Math.max(8, available)), textX, textY, active ? Theme.TEXT : 0xFF7C898B);
        if (!description.isBlank()) g.text(font, fit(description, Math.max(8, x + w - textX - 8)), textX, textY + 14, Theme.MUTED);
        if (!badge.isBlank()) {
            int color = badge.equals("ON") ? Theme.PEARL : 0xFF9EAAA9;
            g.fill(x + w - 31, y + (h - 14) / 2, x + w - 6, y + (h + 14) / 2, 0xFF192326);
            g.centeredText(font, badge, x + w - 19, y + (h - 8) / 2, color);
        }
    }
    private String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }
}
