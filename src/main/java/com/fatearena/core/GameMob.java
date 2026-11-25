package com.fatearena.core;

import com.fatearena.FateArenaPlugin;
import com.fatearena.core.Managers.GameManager;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class GameMob extends InteractableEntity{
    private int health;
    private int maxHealth;
    private int mana;
    private int maxMana;
    private boolean isDefending = false;

    private final Set<Player> damageGlowViewers = new HashSet<>();
    private PlayerTeam damageGlowTeam;
    private BukkitTask damageGlowTask;

    public GameMob(Entity entity, int maxhealth, int maxmana) {
        super(entity);
        this.health = maxhealth;
        this.maxHealth = maxhealth;
        this.mana = maxmana;
        this.maxMana = maxmana;

    }
    public boolean isDefending() {
        return isDefending;
    }
    public void setDefending(boolean defending) {
        isDefending = defending;
    }
    public int getHealth() {
        return health;
    }
    public float getHealthPercentage() {
        return (float) health * 100 / maxHealth;
    }
    public int getMana() {
        return mana;
    }

    public float getManaPercentage() {
        return (float) mana * 100 / maxMana;
    }

    public boolean isValid() {
        return entity.isValid();
    }
    public void damage(int amount)
    {
        damage(amount, null);
    }

    /** Applies damage and then runs the optional hit callback on the calling thread. */
    public void damage(int amount, Consumer<GameMob> onHit) {
        if(isDefending)
        {
            entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation(), 100,
                    0.2, 1.0, 0.5, 0.0);
            return;
        }
        health -= amount;
        if (amount > 0 && entity.isValid()) {
            DamageNumber.spawn(
                    entity.getLocation().add(0, entity.getHeight() + 0.15, 0), amount);
        }
        entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation(), 100,
                0.5, 1.0, 0.5, 0.0, Material.REDSTONE_BLOCK.createBlockData());
        flashDamageGlow();
        if (onHit != null) {
            onHit.accept(this);
        }
    }

    public void useMana(int amount) {
        mana -= amount;
    }
    public void gainMana(int amount) {
        mana = Math.min(mana + amount, maxMana);
    }
    private void flashDamageGlow() {
        if (damageGlowTask != null) {
            damageGlowTask.cancel();
        } else {
            damageGlowTeam = ViewerGlow.createDamageTeam(entity);
        }
        Set<Player> viewers = new HashSet<>(entity.getTrackedBy());
        if (entity instanceof Player player) {
            viewers.add(player);
        }
        for (Player viewer : viewers) {
            if (damageGlowViewers.add(viewer)) {
                ViewerGlow.sendDamageTeam(viewer, damageGlowTeam);
            }
            ViewerGlow.send(viewer, entity, true);
        }
        FateArenaPlugin plugin = FateArenaPlugin.getInstance();
        damageGlowTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::clearDamageGlow, 4L);
    }

    boolean hasDamageGlow(Player viewer) {
        return damageGlowTask != null && damageGlowViewers.contains(viewer);
    }

    private void clearDamageGlow() {
        damageGlowTask = null;
        GameManager gameManager = FateArenaPlugin.getInstance().getGameManager();
        for (Player viewer : damageGlowViewers) {
            if (!viewer.isOnline()) continue;
            ViewerGlow.restoreTeam(viewer, damageGlowTeam, entity);
            GamePlayer gamePlayer = gameManager.getGamePlayer(viewer.getUniqueId());
            ViewerGlow.send(viewer, entity, gamePlayer != null && gamePlayer.hasSelectionGlow(entity));
        }
        damageGlowViewers.clear();
        damageGlowTeam = null;
    }

    @Override
    public void cleanup() {
        if (damageGlowTask != null) {
            damageGlowTask.cancel();
            clearDamageGlow();
        }
    }
}
