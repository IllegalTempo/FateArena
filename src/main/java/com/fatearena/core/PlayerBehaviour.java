package com.fatearena.core;

import com.fatearena.FateArenaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerBehaviour implements Listener {
    private GamePlayer getAttackingPlayer(Player player) {
        GamePlayer gamePlayer = FateArenaPlugin.getInstance().getGamePlayer(player.getUniqueId());
        return gamePlayer != null && gamePlayer.getChoosenCharacter() != null ? gamePlayer : null;
    }

    @EventHandler
    public void onAttackSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        GamePlayer player = getAttackingPlayer(event.getPlayer());
        if (player == null) return;
        GameMob target = player.getFacingEntity() instanceof GameMob ? (GameMob) player.getFacingEntity() : null;
        player.getChoosenCharacter().autoAttack(target);
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        GamePlayer player = getAttackingPlayer(event.getPlayer());
        if (player == null) return;

        GameMob target = player.getFacingEntity() instanceof GameMob ? (GameMob) player.getFacingEntity() : null;
        player.getChoosenCharacter().autoAttack(target);
        event.setCancelled(true);
    }

    @EventHandler
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        GamePlayer player = getAttackingPlayer(attacker);
        if (player == null || player.getFacingEntity() == null) return;
        GameMob target = FateArenaPlugin.getInstance().getGameMob(event.getEntity().getUniqueId()) != null ? FateArenaPlugin.getInstance().getGameMob(event.getEntity().getUniqueId()) : null;
        player.getChoosenCharacter().autoAttack(target);
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        GamePlayer player = FateArenaPlugin.getInstance().getGamePlayer(event.getPlayer().getUniqueId());
        if(player == null) return;
        if(player.getChoosenCharacter() == null) return;
        if(isInputMoving(event))
        {
            player.getAnimationManager().isMoving = true;
        }
        else
        {
            player.getAnimationManager().isMoving = false;
        }
    }
    private boolean isInputMoving(PlayerInputEvent event) {
        return event.getInput().isForward() ^ event.getInput().isBackward() || event.getInput().isLeft() ^ event.getInput().isRight();
    }
}
