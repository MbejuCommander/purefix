package dev.purefix.donkey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Shared by the live wheel and preview; stop owned audio on cancellation. */
public final class DonkeyAudio {
    private SimpleSoundInstance sound;
    private boolean enabled, original;
    private float speed;
    private long clicked;
    private int sector = Integer.MIN_VALUE;
    public void start(boolean enabled, boolean original, double speed) {
        stop(); this.enabled = enabled; this.original = original; this.speed = (float)speed;
        sector = Integer.MIN_VALUE; clicked = 0;
        if (!enabled) return;
        sound = SimpleSoundInstance.forUI(original
            ? SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("purefix", "donkey.spin"))
            : SoundEvents.EXPERIENCE_ORB_PICKUP, this.speed);
        Minecraft.getInstance().getSoundManager().play(sound);
    }
    public void tick(double angle, int colors) {
        if (!enabled || original) return;
        int next = (int)(angle / (Math.PI * 2) * colors);
        if (next != sector && DonkeyController.now() - clicked >= 70 / speed) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, Math.min(2, 1.5f * speed)));
            clicked = DonkeyController.now(); sector = next;
        }
    }
    public void finish() {
        stop();
        if (enabled && !original) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, speed));
    }
    public void stop() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null;
    }
}
