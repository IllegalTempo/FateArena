package com.fatearena.core;

import com.fatearena.core.Managers.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerBehaviour implements Listener {
    private final GameManager gameManager;

    public PlayerBehaviour(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    private GamePlayer getPlayerWithCharacter(Player player) {
        GamePlayer gamePlayer = gameManager.getGamePlayer(player.getUniqueId());
        return gamePlayer != null && gamePlayer.getChoosenCharacter() != null ? gamePlayer : null;
    }

    @EventHandler
    public void onAttackSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        GamePlayer player = getPlayerWithCharacter(event.getPlayer());
        if (player == null) return;
        player.getChoosenCharacter().autoAttack();
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        GamePlayer player = getPlayerWithCharacter(event.getPlayer());
        if (player == null) return;

        player.getChoosenCharacter().autoAttack();
        event.setCancelled(true);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        GamePlayer player = getPlayerWithCharacter(event.getPlayer());
        if (player == null) return;

        event.setCancelled(true);
        player.useSkill1();
    }

    @EventHandler
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        GamePlayer player = getPlayerWithCharacter(event.getPlayer());
        if (player == null) return;

        event.setCancelled(true);
        player.useSkill1();
    }

    @EventHandler
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        GamePlayer player = getPlayerWithCharacter(attacker);
        if (player == null || player.getFacingEntity() == null) return;
        player.getChoosenCharacter().autoAttack();
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        GamePlayer player = getPlayerWithCharacter(event.getPlayer());
        if (player == null) return;

        // Spigot fires this event before updating the player's current input.
        boolean shiftPressed = event.getInput().isSneak() && !event.getPlayer().getCurrentInput().isSneak();
        if (shiftPressed && (player.isCancelable() || player.canAttack())) {
            player.switchDefense();
        }

        if(isInputMoving(event))
        {
            player.getAnimationManager().isMoving = true;
        }
        else
        {
            player.getAnimationManager().isMoving = false;
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && getPlayerWithCharacter(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }
    private boolean isInputMoving(PlayerInputEvent event) {
        return event.getInput().isForward() ^ event.getInput().isBackward() || event.getInput().isLeft() ^ event.getInput().isRight();
    }
}
