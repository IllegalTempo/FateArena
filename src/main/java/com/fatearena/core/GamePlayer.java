package com.fatearena.core;

import com.fatearena.FateArenaPlugin;
import com.fatearena.character.Character;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

public class GamePlayer extends GameMob{
    private final Player player;
    private final Plugin plugin = JavaPlugin.getProvidingPlugin(GamePlayer.class);
    private Character choosenCharacter;
    private ItemStack previousHelmet;
    private boolean characterHeadEquipped;

    private AnimationManager animationManager;
    private static final int SELECTION_HOLD_TICKS = 5;
    private InteractableEntity facingEntity;
    private Entity glowingEntity;
    private int selectionTicksRemaining;

    private int attackCooldownTicks = 0;

    public GamePlayer(Player player) {
        super(player, 100);
        this.player = player;
        animationManager = new AnimationManager(this);
    }
    public void setAttackCooldownTicks(int ticks) {
        attackCooldownTicks = ticks;
    }
    public boolean canAttack() {
        return attackCooldownTicks <= 0;
    }
    public int getAttackCooldownTicks() {
        return attackCooldownTicks;
    }
    public void tick() {
        animationManager.updateItemDisplay();
        animationManager.animationTick();
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        RaycastFacingEntity();
    }

    private void RaycastFacingEntity(){
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(eye,eye.getDirection(),10, FluidCollisionMode.NEVER,true,0.1f,entity -> FateArenaPlugin.getInstance().getInteractableEntity(entity.getUniqueId()) != null && entity != player);
        Entity hitEntity = result == null ? null : result.getHitEntity();
        InteractableEntity newFacingEntity = hitEntity == null ? null
                : FateArenaPlugin.getInstance().getInteractableEntity(hitEntity.getUniqueId());

        if (newFacingEntity != null) {
            if (glowingEntity != null && !glowingEntity.getUniqueId().equals(hitEntity.getUniqueId())) {
                ViewerGlow.send(player, glowingEntity, false);
            }
            glowingEntity = hitEntity;
            facingEntity = newFacingEntity;
            selectionTicksRemaining = SELECTION_HOLD_TICKS;
        } else if (selectionTicksRemaining > 0) {
            // tick() runs once per server tick: keep the selection for one second.
            if (--selectionTicksRemaining == 0) {
                clearSelectionGlow();
                facingEntity = null;
            }
        }
        if (glowingEntity != null) {
            if (!glowingEntity.isValid() || glowingEntity.getWorld() != player.getWorld()) {
                clearSelectionGlow();
                facingEntity = null;
                selectionTicksRemaining = 0;
            } else {
                // Refresh because ordinary server metadata can overwrite the client-only flag.
                ViewerGlow.send(player, glowingEntity, true);
            }
        }
    }

    private void clearSelectionGlow() {
        if (glowingEntity != null) {
            ViewerGlow.send(player, glowingEntity, false);
            glowingEntity = null;
        }
    }

    public InteractableEntity getFacingEntity() {
        return facingEntity;
    }

    public void playSelectedTargetAttack(int a1cooldownTicks) {
        if (facingEntity != null) {
            animationManager.playAttackAnimation("a1", a1cooldownTicks);
        }
    }
    public void SelectCharacter(Character character) {
        this.choosenCharacter = character;
        if (character == null) {
            animationManager.clearAnimation();
            restoreHelmet();
            return;
        }
        ItemStack head = character.createPlayerHead();
        if (head != null) {
            if (!characterHeadEquipped) {
                ItemStack helmet = player.getInventory().getHelmet();
                previousHelmet = helmet == null ? null : helmet.clone();
                characterHeadEquipped = true;
            }
            player.getInventory().setHelmet(head);
        } else {
            restoreHelmet();
        }
        animationManager.playAnimation("idle");
    }

    private void restoreHelmet() {
        if (characterHeadEquipped) {
            player.getInventory().setHelmet(previousHelmet);
            previousHelmet = null;
            characterHeadEquipped = false;
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        clearSelectionGlow();
        facingEntity = null;
        selectionTicksRemaining = 0;
        animationManager.removeItemDisplay();
        restoreHelmet();
    }
    public void hideFromViewer(Player viewer) {
        if (animationManager.isAnimationHidden() && !viewer.getUniqueId().equals(player.getUniqueId())) {
            viewer.hideEntity(plugin, player);
        }
    }
    public AnimationManager getAnimationManager() {return animationManager;}
    public Character getChoosenCharacter() {
        return choosenCharacter;
    }
    public Player getPlayer() {
        return player;
    }






}
