package dev.purefix.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

public final class FormScreen extends Screen {
    private final Screen parent;
    private final String[] labels, values;
    private final Consumer<List<String>> commit;
    private final List<EditBox> boxes = new ArrayList<>();
    private String error = "";
    private final int accent;
    public FormScreen(Screen parent, String title, String[] labels, String[] values, Consumer<List<String>> commit) {
        super(Component.literal(title)); this.parent = parent; this.labels = labels; this.values = values; this.commit = commit;
        this.accent = parent instanceof MenuScreen menu ? menu.accent : Theme.PEARL;
    }
    @Override protected void init() {
        if (!boxes.isEmpty()) for (int i = 0; i < boxes.size(); i++) values[i] = boxes.get(i).getValue();
        boxes.clear();
        int w = Math.min(376, width - 48), x = (width - w) / 2;
        for (int i = 0; i < labels.length; i++) {
            EditBox box = new EditBox(font, x, 67 + i * 39, w, 20, Component.literal(labels[i]));
            box.setMaxLength(256); box.setValue(values[i]); boxes.add(addRenderableWidget(box));
        }
        addRenderableWidget(new PanelButton(width / 2 - 112, height - 39, 108, 20, "Save", "", Theme.Icon.NONE, accent, () -> {
            try { commit.accept(boxes.stream().map(EditBox::getValue).map(String::strip).toList()); onClose(); }
            catch (Exception e) { error = e.getMessage() == null ? "Unable to save." : e.getMessage(); }
        }));
        addRenderableWidget(new PanelButton(width / 2 + 4, height - 39, 108, 20, "Cancel", "", Theme.Icon.NONE, accent, this::onClose));
        setInitialFocus(boxes.getFirst());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(406, width - 20);
        Theme.panel(graphics, (width - panelWidth) / 2, 7, panelWidth, height - 14, accent);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 24, Theme.TEXT);
        graphics.centeredText(font, "Edit your preferences", width / 2, 42, Theme.MUTED);
        int x = (width - Math.min(376, width - 48)) / 2;
        for (int i = 0; i < labels.length; i++) graphics.text(font, font.plainSubstrByWidth(labels[i], width - x * 2), x, 56 + i * 39, Theme.MUTED);
        if (!error.isBlank()) graphics.textWithWordWrap(font, Component.literal(error), x, 55 + labels.length * 39, width - x * 2, 0xFFFF9990);
    }
    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
