package com.fatearena.character.fate;

import com.fatearena.FateArenaPlugin;
import com.fatearena.character.AttackInfo;
import com.fatearena.character.CharacterClass;
import com.fatearena.core.Managers.Effect3DManager;
import com.fatearena.core.Managers.GameManager;
import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Saber extends CharacterClass {

    public Saber(GamePlayer player) {
        super("saber",player);
    }

    private static final AttackInfo ATTACK_AUTO_A1 = new AttackInfo(10, "a1", 20, 7, 0);
    private static final AttackInfo ATTACK_AUTO_A2 = new AttackInfo(15, "a2", 25, 10, 0);
    private static final AttackInfo ATTACK_SKILL_1 = new AttackInfo(40, "s1", 40, 20, 20);
    private static final int SKILL_1_HIT_WINDOW_TICKS = 10;
    private static final double SKILL_1_HIT_RANGE = 2.0;
    private static final double SKILL_1_WIND_LENGTH = 12.0;
    private static final double SKILL_1_WIND_RADIUS = 19.0 / 3.0;
    private static final int SKILL_1_WIND_STEPS = 80;
    private static final Particle.DustOptions SKILL_1_AQUA_DUST =
            new Particle.DustOptions(Color.AQUA, 0.85f);
    private static final Particle.DustOptions SKILL_1_WHITE_DUST =
            new Particle.DustOptions(Color.WHITE, 0.7f);
    private static final FateArenaPlugin plugin = FateArenaPlugin.getInstance();

    private int attackIndex = 0;
    private Runnable[] autoAttackSequence = new Runnable[]{
            this::Auto_A1,
            this::Auto_A2
    };

    @Override
    protected String getSkinTextureUrl() {
        // URL decoded from the supplied MineSkin texture value.
        return "http://textures.minecraft.net/texture/a8e8d9be86268e096729233f6bf521334e8543afbc61320b4456d53b69e83990";
    }

    @Override
    public void autoAttack() {
        if (!player.canAttack()) return;

        super.autoAttack();
        autoAttackSequence[attackIndex].run();
        attackIndex = (attackIndex + 1) % autoAttackSequence.length;

    }

    @Override
    public void skill1() {
        if(ATTACK_SKILL_1.manaCost > player.getMana()) return;
        super.skill1();
        player.setCancelable(false);

        player.useMana(ATTACK_SKILL_1.manaCost);
        player.getAnimationManager().playAttackAnimation(ATTACK_SKILL_1);
        Vector backward = player.getPlayer().getLocation().getDirection()
                .setY(0)
                .normalize()
                .multiply(-1)
                .setY(0.1);
        player.getPlayer().setVelocity(backward);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isValid() || player.getChoosenCharacter() != this) return;

            Vector forward = player.getPlayer().getLocation().getDirection()
                    .setY(0)
                    .normalize()
                    .multiply(2)
                    .setY(0.1);
            player.getPlayer().setVelocity(forward);

            player.setCancelable(true);
            detectSkill1Hit();
        }, ATTACK_SKILL_1.attackDelayTicks);
    }

    private void detectSkill1Hit() {
        new BukkitRunnable() {
            private int ticksRemaining = SKILL_1_HIT_WINDOW_TICKS;

            @Override
            public void run() {
                if (!player.isValid() || player.getChoosenCharacter() != Saber.this) {
                    cancel();
                    return;
                }
                GameMob target = findSkill1Target();
                if (target != null) {
                    cancel();
                    target.damage(ATTACK_SKILL_1.damage, Saber.this::Skill1_Onhit);
                    Vector backward = player.getPlayer().getLocation().getDirection()
                            .setY(0)
                            .normalize()
                            .multiply(-0.2)
                            .setY(0.1);
                    player.getPlayer().setVelocity(backward);
                } else if (--ticksRemaining == 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private GameMob findSkill1Target() {
        var caster = player.getPlayer();
        Location origin = caster.getLocation();
        double range = SKILL_1_HIT_RANGE;
        double nearestDistanceSquared = range * range;
        GameMob nearest = null;
        GameManager gameManager = plugin.getGameManager();
        for (Entity entity : caster.getNearbyEntities(range, range, range)) {
            GameMob target = gameManager.getGameMob(entity.getUniqueId());
            if (target == null || target == player || !target.isValid()) continue;

            double distanceSquared = origin.distanceSquared(entity.getLocation());
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = target;
            }
        }
        return nearest;
    }

    private void Skill1_Onhit(GameMob hitTarget)
    {
        Location caster = player.getPlayer().getLocation().add(0, 1, 0);
        Location impact = hitTarget.getEntity().getLocation().add(0, 1, 0);
        spawnSkill1WindCone(caster, impact);
        Effect3DManager.getInstance().play3DEffect(
                impact.setDirection(caster.getDirection()),
                getCharacterName(),
                "s1_onhit",
                0,
                24);
    }

    private void spawnSkill1WindCone(Location caster, Location impact) {
        Vector axis = impact.toVector().subtract(caster.toVector());
        if (axis.lengthSquared() < 1e-6) {
            axis = caster.getDirection();
        }
        axis.normalize();

        // Keep the cone's tip on Saber's side of the impact, even on point-blank hits.
        double tipOffset = Math.min(0.45, caster.distance(impact));
        Location tip = impact.clone().subtract(axis.clone().multiply(tipOffset));
        Vector reference = Math.abs(axis.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = reference.crossProduct(axis).normalize();
        Vector up = axis.getCrossProduct(right);
        World world = impact.getWorld();
        world.spawnParticle(Particle.DUST, tip, 1, 0, 0, 0, 0, SKILL_1_WHITE_DUST);

        // Three spirals widen toward the rim. Cloud particles travel outward along the spiral.
        for (int step = 1; step <= SKILL_1_WIND_STEPS; step++) {
            double progress = (double) step / SKILL_1_WIND_STEPS;
            Location center = tip.clone().add(axis.clone().multiply(SKILL_1_WIND_LENGTH * progress));
            double radius = SKILL_1_WIND_RADIUS * progress;
            for (int strand = 0; strand < 3; strand++) {
                double angle = 2 * Math.PI * (progress * 5.0 / 3.0 + strand / 3.0);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vector radial = right.clone().multiply(cos).add(up.clone().multiply(sin));
                Location point = center.clone().add(radial.multiply(radius));
                Particle.DustOptions dust = strand == 0 ? SKILL_1_WHITE_DUST : SKILL_1_AQUA_DUST;
                world.spawnParticle(Particle.DUST, point, 10, 0, 0.1, 0.1, 0.1, dust);
                if (step % 3 == 0) {
                    Vector drift = axis.clone().add(right.clone().multiply(-sin)).add(up.clone().multiply(cos));
                    world.spawnParticle(Particle.CLOUD, point, 0, drift.getX(), drift.getY(), drift.getZ(), 0.1);
                }
            }
        }
    }

    private void Auto_A1()
    {
        normalAutoAttack(ATTACK_AUTO_A1);
    }
    private void Auto_A2()
    {
        normalAutoAttack(ATTACK_AUTO_A2);


    }

}
