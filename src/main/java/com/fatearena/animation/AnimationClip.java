package com.fatearena.animation;

import java.util.List;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.consumable.ConsumableComponent;

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

    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        return obj instanceof AnimationClip other
                && animationName.equals(other.animationName)
                && characterName.equals(other.characterName)
                && Objects.equals(frameOffset, other.frameOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(animationName, characterName, frameOffset);
    }

    public ItemStack getFirstPersonItem()
    {
        return createModelItem(firstPersonModelPath);
    }
    public ItemStack getThirdPersonItem()
    {
        return createModelItem(thirdPersonModelPath);
    }
    public ItemStack getEffectItem()
    {
        return createModelItem("fatearena:" + characterName + "_effect/" + animationName);
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
