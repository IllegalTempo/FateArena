package com.fatearena.animation;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public class AnimationClip {
    private final String animationName;
    private final String characterName;
    private final Integer frameOffset;
    private String firstPersonModelPath;
    private String thirdPersonModelPath;
    public static String IDLE_ANIMATION = "idle";
    //fatearena:saber_fp/walk
    public AnimationClip(String animationName, String characterName)
    {
        this(animationName, characterName, null);
    }

    public AnimationClip(String animationName, String characterName, Integer frameOffset) {
        this.animationName = animationName;
        this.characterName = characterName;
        this.frameOffset = frameOffset;
        this.firstPersonModelPath = "fatearena:" + characterName + "_fp/" + animationName;
        this.thirdPersonModelPath = "fatearena:" + characterName + "_tp/" + animationName;
    }
    public ItemStack getFirstPersonItem()
    {
        return createModelItem(firstPersonModelPath);
    }
    public ItemStack getThirdPersonItem()
    {
        return createModelItem(thirdPersonModelPath);
    }

    private ItemStack createModelItem(String modelPath)
    {
        NamespacedKey modelKey = NamespacedKey.fromString(modelPath);
        if (modelKey == null) {
            throw new IllegalArgumentException("Invalid item model path: " + modelPath);
        }

        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(modelKey);
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of(characterName, animationName));
        if (frameOffset != null) {
            modelData.setColors(List.of(Color.fromRGB(frameOffset)));
        }
        meta.setCustomModelDataComponent(modelData);
        item.setItemMeta(meta);
        return item;
    }
}
