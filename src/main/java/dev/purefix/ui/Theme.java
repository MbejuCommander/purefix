package dev.purefix.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Pixel borders and vanilla textures; safe before item registries are loaded. */
public final class Theme {
    public static final int PEARL = 0xFF65D6B3, SPECTATE = 0xFFB5A0E0, GOLD = 0xFFE2C07A;
    public static final int TEXT = 0xFFE5E8E4, MUTED = 0xFFA8B5B8;
    public enum Icon { NONE, PEARL, PLAYER, COMPASS, BOOK, DONKEY }
    private Theme() {}
    public static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, light); g.fill(x, y, x + 1, y + h, light);
        g.fill(x, y + h - 1, x + w, y + h, dark); g.fill(x + w - 1, y, x + w, y + h, dark);
    }
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(0, 0, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(), 0x880A1012);
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x88000000);
        bevel(g, x, y, w, h, 0xF01D2529, 0xFF64716F, 0xFF0B1013);
        bevel(g, x + 3, y + 3, w - 6, h - 6, 0xF020292D, 0xFF11191D, 0xFF48514F);
        g.fill(x + 5, y + 5, x + w - 5, y + 7, accent);
        g.fill(x + 8, y + 47, x + w - 8, y + 48, 0xFF45514F);
        for (int px : new int[]{x + 5, x + w - 8}) for (int py : new int[]{y + 10, y + h - 13})
            bevel(g, px, py, 3, 3, 0xFF697473, 0xFF97A29A, 0xFF111719);
    }
    public static void icon(GuiGraphicsExtractor g, Icon icon, int x, int y, int size) {
        if (icon == Icon.NONE) return;
        if (icon == Icon.DONKEY) {
            var state = new net.minecraft.client.renderer.entity.state.DonkeyRenderState();
            state.entityType = net.minecraft.world.entity.EntityTypes.DONKEY;
            state.hasChest = true; state.scale = 1; state.ageScale = 1;
            state.boundingBoxHeight = 1.5f; state.boundingBoxWidth = 1.4f;
            state.lightCoords = 15728880;
            state.pose = net.minecraft.world.entity.Pose.STANDING;
            g.entity(state, size * .55f, new org.joml.Vector3f(0, .75f, 0),
                new org.joml.Quaternionf().rotationZ((float)Math.PI).rotateY(.7f), null, x, y, x + size, y + size);
            return;
        }
        if (icon == Icon.PLAYER) {
            var player = Minecraft.getInstance().player;
            Identifier texture = player == null ? Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")
                : player.getSkin().body().texturePath();
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
            if (player != null) g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
            return;
        }
        String item = switch (icon) { case PEARL -> "ender_pearl"; case COMPASS -> "compass_16"; case BOOK -> "book"; default -> throw new IllegalStateException(); };
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/item/" + item + ".png"), x, y, 0f, 0f, size, size, 16, 16, 16, 16);
    }
}
