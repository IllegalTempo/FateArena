package com.fatearena.character;

import com.fatearena.animation.AnimationClip;
import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public abstract class Character {
    private final String characterName;
    protected GamePlayer player;

    private int a1cooldownTicks;


    public Character(String characterName, GamePlayer player) {
        this.characterName = characterName;
        this.player = player;
    }

    public String getCharacterName() {
        return characterName;
    }
    protected String getSkinTextureUrl() {
        return null;
    }

    /** Creates a fresh head from the character's fixed Minecraft texture. */
    public ItemStack createPlayerHead() {
        String textureUrl = getSkinTextureUrl();
        if (textureUrl == null) {
            return null;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(
                UUID.nameUUIDFromBytes(textureUrl.getBytes(StandardCharsets.UTF_8)));
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(URI.create(textureUrl).toURL());
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid character skin URL", exception);
        }
        profile.setTextures(textures);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
    public abstract void autoAttack(GameMob target);
}
