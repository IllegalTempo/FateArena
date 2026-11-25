package com.fatearena.character;

import com.fatearena.FateArenaPlugin;
import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;
import com.fatearena.core.ParticleEffects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Vector;

import java.net.URI;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class CharacterClass {
    private final String characterName;
    protected GamePlayer player;


    public CharacterClass(String characterName, GamePlayer player) {
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
    private void attackAction()
    {
        player.setDefending(false);
    }

    public void autoAttack()
    {
        if (!player.canAttack()) return;

        attackAction();
    };
    public void skill1()
    {
        attackAction();
    };
    protected void normalAutoAttack(AttackInfo info, Consumer<GameMob> onHitCallback) {
        player.getAnimationManager().playAttackAnimation(info.animationName, info.attackDuration, info.useVanillaHead);
        player.setCancelable(false);

        FateArenaPlugin plugin = FateArenaPlugin.getInstance();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            GameMob target = player.getFacingEntity() instanceof GameMob ? (GameMob) player.getFacingEntity() : null;
            if (target != null && target.isValid()) {
                target.damage(info.damage, onHitCallback);
            }
            player.setCancelable(true);

        }, info.attackDelayTicks);

    }
    protected void normalAutoAttack(AttackInfo info) {
        normalAutoAttack(info, null);
    }
}
