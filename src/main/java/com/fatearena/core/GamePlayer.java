package com.fatearena.core;

import com.fatearena.FateArenaPlugin;
import com.fatearena.character.CharacterClass;
import com.fatearena.core.Managers.AnimationManager;
import com.fatearena.core.Managers.GameManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.util.function.Consumer;

public class GamePlayer extends GameMob{
    private static final int POSITION_RING_POINTS = 20;
    private static final double POSITION_RING_RADIUS = 0.45;
    private static final int POSITION_RING_INTERVAL = 4;
    private static final Particle.DustOptions POSITION_RING_DUST = new Particle.DustOptions(org.bukkit.Color.AQUA, 0.45f);
    private static final Particle.DustOptions ENEMY_RING_DUST = new Particle.DustOptions(org.bukkit.Color.RED, 0.45f);
    private static final Particle.DustOptions TEAMMATE_RING_DUST = new Particle.DustOptions(org.bukkit.Color.LIME, 0.45f);
    private static final Particle.DustOptions ENEMY_TARGET_RING_DUST = new Particle.DustOptions(org.bukkit.Color.RED, 0.65f);
    private static final Particle.DustOptions TEAMMATE_TARGET_RING_DUST = new Particle.DustOptions(org.bukkit.Color.LIME, 0.65f);
    private static final int TARGET_RING_LAST_TICK = 14;
    private static final double POSITION_RING_VIEW_RANGE_SQUARED = 32 * 32;

    private final Player player;
    private CharacterClass choosenCharacterClass;
    private ItemStack previousHelmet;
    private boolean characterHeadEquipped;

    private AnimationManager animationManager;
    private static final int SELECTION_HOLD_TICKS = 3;
    private InteractableEntity facingEntity;
    private int selectionTicksRemaining;
    private int targetRingTick;

    private int prevCooldownTicks = 0;
    private int attackCooldownTicks = 0;
    private boolean skill1UsedThisTick;
    private int positionRingCooldown;

    private boolean cancelable = false;

    private static final int DEFENSE_MANA_COST = 5;
    private static final int DEFENSE_ACTIVATE_MANA_COST = 10;
    private static final double DEFENSE_SPHERE_RADIUS = 1.25;
    private static final int DEFENSE_SPHERE_POINTS = 60;
    private static final Particle.DustOptions DEFENSE_DUST = new Particle.DustOptions(org.bukkit.Color.AQUA, 0.8f);

    private static final int MANA_REGEN_RATE = 5;

    public GamePlayer(Player player) {
        super(player, 100,100);
        this.player = player;
        animationManager = new AnimationManager(this);
    }
    public void setAttackCooldownTicks(int ticks) {
        attackCooldownTicks = ticks;
        prevCooldownTicks = ticks;
    }
    public boolean canAttack() {
        return attackCooldownTicks <= 0;
    }
    public int getAttackCooldownTicks() {
        return attackCooldownTicks;
    }

    public void useSkill1() {
        // A right-click can send both an entity interaction and an item use.
        if (skill1UsedThisTick || choosenCharacterClass == null || !canAttack()) return;
        skill1UsedThisTick = true;
        choosenCharacterClass.skill1();
    }

    public void tick() {
        skill1UsedThisTick = false;
        animationManager.updateItemDisplay();
        animationManager.animationTick();
        progressAttackCooldown();
        updatePlayerActionbarDisplay();
        RaycastFacingEntity();
        updateTargetRing();
        if(isDefending())
        {
            whileDefense();
        }
    }

    public void oneSecondTick() {
        regenMana();
    }
    private void regenMana()
    {
        gainMana(MANA_REGEN_RATE);
    }
    public void updatePositionRing(GameManager gameManager) {
        if (positionRingCooldown > 0) {
            positionRingCooldown--;
            return;
        }
        positionRingCooldown = POSITION_RING_INTERVAL - 1;

        Location feet = player.getLocation().add(0, 0.05, 0);
        for (Player viewer : feet.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(feet) > POSITION_RING_VIEW_RANGE_SQUARED) continue;

            GamePlayer gameViewer = gameManager.getGamePlayer(viewer.getUniqueId());
            Particle.DustOptions dust = gameViewer != null && gameViewer.getFacingEntity() == this
                    ? selectedRingDust(viewer, player) : POSITION_RING_DUST;
            spawnPositionRing(viewer, feet, dust);
        }
        if (facingEntity instanceof GameMob target && !(target instanceof GamePlayer)) {
            Entity selected = target.getEntity();
            spawnPositionRing(player, selected.getLocation().add(0, 0.05, 0), selectedRingDust(player, selected));
        }
    }

    private static Particle.DustOptions selectedRingDust(Player viewer, Entity target) {
        return isTeammate(viewer, target) ? TEAMMATE_RING_DUST : ENEMY_RING_DUST;
    }

    private static boolean isTeammate(Player viewer, Entity target) {
        return ((CraftEntity) viewer).getHandle().isAlliedTo(((CraftEntity) target).getHandle());
    }

    private static void spawnPositionRing(Player viewer, Location feet, Particle.DustOptions dust) {
        ParticleEffects.spawnRing(feet, POSITION_RING_RADIUS, POSITION_RING_POINTS,
                point -> viewer.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust));
    }

    private void updateTargetRing() {
        if (!(facingEntity instanceof GameMob target)) return;

        int tick = targetRingTick;
        targetRingTick = (targetRingTick + 1) % (TARGET_RING_LAST_TICK + 2);
        if (tick % 2 != 0) return;

        Entity selected = target.getEntity();
        double rise = Math.max(1.1, selected.getHeight()) * tick / TARGET_RING_LAST_TICK;
        Location center = selected.getLocation().add(0, 0.05 + rise, 0);
        double radius = Math.max(0.65, selected.getWidth() * 0.5 + 0.2);
        Particle.DustOptions dust = isTeammate(player, selected) ? TEAMMATE_TARGET_RING_DUST : ENEMY_TARGET_RING_DUST;
        ParticleEffects.spawnRing(center, radius, 16,
                point -> player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust));
    }
    public boolean isCancelable() {
        return cancelable;
    }
    private void whileDefense()
    {
        var center = getPlayer().getLocation().add(0, 1, 0);
        ParticleEffects.spawnHollowSphere(center, DEFENSE_SPHERE_RADIUS, DEFENSE_SPHERE_POINTS,
                Particle.DUST, DEFENSE_DUST);
    }
    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }
    public void switchDefense()
    {
        boolean prevdef = isDefending();
        boolean sneak = !prevdef;
        if(sneak)
        {
            if(getMana() < DEFENSE_MANA_COST)
            {
                sneak = false;
            }
        }
        setDefending(sneak);
        if(prevdef != isDefending())
        {
            if(isDefending())
            {
                defense();
            } else {
                undefense();
            }
        }
    }
    public void defense()
    {
        useMana(DEFENSE_ACTIVATE_MANA_COST);
        player.getPlayer().setWalkSpeed(0.1f);
        getAnimationManager().playAnimation("defense", false);

        setAttackCooldownTicks(0);
        player.getPlayer().setVelocity(new Vector(0, 0.2, 0));
    }
    public void undefense()
    {
        player.getPlayer().setWalkSpeed(0.2f);

    }
    @Override
    public void damage(int amount, Consumer<GameMob> onHit) {

        super.damage(amount, onHit);
        if(isDefending())
        {
            useMana(DEFENSE_MANA_COST);
            if(getMana() < DEFENSE_MANA_COST)
            {
                setDefending(false);
                undefense();
            }
        }
    }
    private void progressAttackCooldown() {
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
    }
    private void updatePlayerActionbarDisplay() {
        int healthBar = getHealth();
        int manaBar = getMana();
        int cdfillBar = (int) Math.round((double) attackCooldownTicks / prevCooldownTicks * 100);
        String message =
                "\uF017\uF016\uF015\uF013\uF010"
                        + "\uDB81\uDE00\uF016\uF015\uF014\uF013\uF010"
                        + "\uF003\uF001"
                        + Character.toString(0xF0000 + Integer.parseInt(String.valueOf(Math.min(100, (healthBar <= 0L ? 0 : healthBar >= 100L ? 50 : (int) (((double) healthBar - 0L) * 100 / (((double) 100L - 0L) * 2))) * 2)), 16))
                        + "\uF016\uF015\uF012\uF010"
                        + Character.toString(0xF0200 + Integer.parseInt(String.valueOf(Math.min(100, (manaBar <= 0L ? 0 : manaBar >= 100L ? 50 : (int) (((double) manaBar - 0L) * 100 / (((double) 100L - 0L) * 2))) * 2)), 16))
                        + "\uF016\uF015\uF012\uF010"
                        + "\uF012\uF011\uF010"
                        + Character.toString(0xF0400 + Integer.parseInt(String.valueOf(Math.clamp(cdfillBar, 0, 100)), 16))
                        + "\uF012"
                        + "\uF007\uF006\uF005\uF003";
        showActionBar(message);
    }

    private void showActionBar(String message) {
        var component = new TextComponent(message);

        component.setFont("fontui:hud");
        component.setShadowColor(new Color(0, true));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, component);
    }
    public InteractableEntity findFacingEntity() {
        GameManager gameManager = FateArenaPlugin.getInstance().getGameManager();
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), 10,
                FluidCollisionMode.NEVER, true, 1f,
                entity -> entity != player && gameManager.getInteractableEntity(entity.getUniqueId()) != null);
        Entity hitEntity = result == null ? null : result.getHitEntity();
        return hitEntity == null ? null : gameManager.getInteractableEntity(hitEntity.getUniqueId());
    }

    private void RaycastFacingEntity(){
        InteractableEntity newFacingEntity = findFacingEntity();
        if (newFacingEntity != null) {
            if (facingEntity != newFacingEntity) {
                clearSelectionGlow();
                facingEntity = newFacingEntity;
                targetRingTick = 0;
            }
            selectionTicksRemaining = SELECTION_HOLD_TICKS;
        } else if (selectionTicksRemaining > 0 && --selectionTicksRemaining == 0) {
            clearSelectionGlow();
        }
        if (facingEntity != null) {
            Entity target = facingEntity.getEntity();
            if (!target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) {
                clearSelectionGlow();
                selectionTicksRemaining = 0;
            } else {
                // Refresh because ordinary server metadata can overwrite the client-only flag.
                ViewerGlow.send(player, target, true);
            }
        }
    }

    private void clearSelectionGlow() {
        if (facingEntity != null) {
            ViewerGlow.send(player, facingEntity.getEntity(), false);
            facingEntity = null;
        }
    }

    boolean hasSelectionGlow(Entity target) {
        return facingEntity != null && target.equals(facingEntity.getEntity());
    }

    public InteractableEntity getFacingEntity() {
        return facingEntity;
    }

    public void playSelectedTargetAttack(int a1cooldownTicks) {
        if (facingEntity != null) {
            animationManager.playAttackAnimation("a1", a1cooldownTicks);
        }
    }
    public void SelectCharacter(CharacterClass characterClass) {
        this.choosenCharacterClass = characterClass;
        if (characterClass == null) {

            animationManager.setCharacterHead(null);
            animationManager.clearAnimation();
            restoreHelmet();
            return;
        }
        ItemStack head = characterClass.createPlayerHead();
        animationManager.setCharacterHead(head);
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
        selectionTicksRemaining = 0;
        animationManager.removeItemDisplay();
        restoreHelmet();
    }
    public AnimationManager getAnimationManager() {return animationManager;}
    public CharacterClass getChoosenCharacter() {
        return choosenCharacterClass;
    }
    public Player getPlayer() {
        return player;
    }






}
