package dev.purefix.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One small generated texture per sector count; rotation is a GPU transform, not per-frame pixel work. */
final class DonkeyWheel implements AutoCloseable {
    private static final int SIZE = 256;
    private static final int[] COLORS = {0xED474E, 0xFFAD32, 0xF4D63D, 0x58CB56, 0x25C5D5, 0x487DED, 0x8F52E8, 0xEB51B5};
    static int color(int sector, int count) {
        if (count <= COLORS.length) return 0xFF000000 | COLORS[sector % COLORS.length];
        float hue = sector * 6f / count; int part = (int)hue;
        int low = 55, high = 240, up = (int)(low + (high - low) * (hue - part)), down = high + low - up;
        int[] rgb = switch (part % 6) {
            case 0 -> new int[]{high, up, low}; case 1 -> new int[]{down, high, low};
            case 2 -> new int[]{low, high, up}; case 3 -> new int[]{low, down, high};
            case 4 -> new int[]{up, low, high}; default -> new int[]{high, low, down};
        };
        return 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
    }
    private final Identifier texture = Identifier.fromNamespaceAndPath("purefix", "wheel/" + java.util.UUID.randomUUID());
    private int count = -1;
    void draw(GuiGraphicsExtractor g, int cx, int cy, int radius, int sectors, double angle) {
        sectors = Math.max(1, sectors);
        if (sectors != count) build(sectors);
        g.pose().pushMatrix(); g.pose().translate(cx, cy); g.pose().rotate((float)(angle % (Math.PI * 2)));
        g.blit(RenderPipelines.GUI_TEXTURED, texture, -radius, -radius, 0, 0, radius * 2, radius * 2, SIZE, SIZE, SIZE, SIZE);
        g.pose().popMatrix();
        // Fixed pointer; its tip is exactly the winning sector's radial center.
        for (int row = 0; row < 12; row++) g.fill(cx - (12 - row) / 2, cy - radius - 5 + row, cx + (12 - row) / 2 + 1, cy - radius - 4 + row, 0xFFFFE6A0);
        Theme.bevel(g, cx - 6, cy - 6, 12, 12, 0xFFE9B33C, 0xFFFFE7A0, 0xFF795012);
    }
    private void build(int sectors) {
        if (count != -1) Minecraft.getInstance().getTextureManager().release(texture);
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            double dx = x - 127.5, dy = y - 127.5, radius = Math.hypot(dx, dy);
            int color = 0;
            if (radius <= 126) {
                double angle = (Math.atan2(dy, dx) + Math.PI * 2) % (Math.PI * 2);
                double sector = angle / (Math.PI * 2) * sectors;
                if (radius > 119) {
                    int gold = (int)(190 + 60 * Math.cos(angle - 4));
                    color = 0xFF000000 | gold << 16 | (gold * 3 / 4) << 8 | 36;
                } else if (sector % 1 < .017 || sector % 1 > .983) color = 0xFF533329;
                else {
                    int rgb = color((int)sector, sectors); double light = .65 + .35 * radius / 119;
                    color = 0xFF000000 | ((int)((rgb >> 16 & 255) * light) << 16) | ((int)((rgb >> 8 & 255) * light) << 8) | (int)((rgb & 255) * light);
                    if (radius > 109 && radius < 114 && (int)(angle * 60) % 3 == 0) color = 0xFFFFEDB0;
                }
            }
            image.setPixel(x, y, color);
        }
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "PureFix Donkey wheel", image)); count = sectors;
    }
    @Override public void close() { if (count != -1) { Minecraft.getInstance().getTextureManager().release(texture); count = -1; } }
}
