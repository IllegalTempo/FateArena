package com.fatearena.core.Managers;

import com.fatearena.FateArenaPlugin;
import com.fatearena.animation.AnimationClip;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;

import static com.fatearena.core.Managers.AnimationManager.animationFrameOffset;

public class Effect3DManager {
    private static final FateArenaPlugin plugin = FateArenaPlugin.getInstance();
    private static final Effect3DManager instance = new Effect3DManager();

    public static Effect3DManager getInstance() {
        return instance;
    }
    public void play3DEffect(Location loc,String characterName, String animationName, int startframe, int duration) {
        long gameTime = loc.getWorld().getGameTime();
        int frameOffset = animationFrameOffset(gameTime, duration, startframe);

        AnimationClip clip = new AnimationClip(
                animationName, characterName, frameOffset);
        var item = clip.getEffectItem();
        ItemDisplay itemDisplay = loc.getWorld().spawn(loc, ItemDisplay.class);
        itemDisplay.setItemStack(item);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            itemDisplay.remove();
        }, duration);
    }

}
