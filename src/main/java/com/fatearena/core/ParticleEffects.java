package com.fatearena.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.function.Consumer;

public final class ParticleEffects {
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private ParticleEffects() {
    }

    static void spawnRing(Location center, double radius, int points, Consumer<Location> spawnParticle) {
        for (int point = 0; point < points; point++) {
            double angle = point * Math.TAU / points;
            spawnParticle.accept(center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle)));
        }
    }

    public static <T> void spawnHollowSphere(Location center, double radius, int points,
                                            Particle particle, T data) {
        World world = center.getWorld();
        // Space points evenly in height and rotate each by the golden angle.
        for (int point = 0; point < points; point++) {
            double height = 1 - 2 * (point + 0.5) / points;
            double ringRadius = radius * Math.sqrt(1 - height * height);
            double angle = point * GOLDEN_ANGLE;
            double x = center.getX() + ringRadius * Math.cos(angle);
            double y = center.getY() + radius * height;
            double z = center.getZ() + ringRadius * Math.sin(angle);
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, data);
        }
    }
}
