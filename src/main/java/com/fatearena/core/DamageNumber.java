package com.fatearena.core;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageNumber {
    private static final int LIFETIME = 20;
    private static final int FADE_START = 5;
    private static final int MAX_SCALE_DAMAGE = 100;
    private static final float MAX_SCALE = 3.0f;
    private static final double UPWARD_LAUNCH_SPEED = 0.16;
    private static final double MIN_HORIZONTAL_SPEED = 0.035;
    private static final double MAX_HORIZONTAL_SPEED = 0.075;
    private static final double CARRIER_GRAVITY = 0.016;
    private static final double CARRIER_SCALE = 0.0625;
    private static final Set<DamageNumber> activeNumbers = new HashSet<>();

    private final TextDisplay display;
    private final ArmorStand carrier;
    private int age;

    private DamageNumber(TextDisplay display, ArmorStand carrier) {
        this.display = display;
        this.carrier = carrier;
    }

    public static void spawn(Location origin, int damage) {
        float progress = (Math.clamp(damage, 1, MAX_SCALE_DAMAGE) - 1f) / (MAX_SCALE_DAMAGE - 1);
        float scale = 1f + (MAX_SCALE - 1f) * progress;
        TextDisplay display = origin.getWorld().spawn(origin, TextDisplay.class, text -> {
            text.setText(ChatColor.RED + Integer.toString(damage));
            text.setBillboard(Display.Billboard.CENTER);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(0));
            text.setShadowed(true);
            text.setGravity(false);
            text.setPersistent(false);
            Transformation transform = text.getTransformation();
            transform.getScale().set(scale);
            text.setTransformation(transform);
        });
        // The display rides a tiny invisible stand so Minecraft handles its motion and gravity.
        ArmorStand carrier = origin.getWorld().spawn(origin, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setCollidable(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setGravity(true);
            stand.getAttribute(Attribute.GRAVITY).setBaseValue(CARRIER_GRAVITY);
            stand.getAttribute(Attribute.SCALE).setBaseValue(CARRIER_SCALE);
        });
        DamageNumber number = new DamageNumber(display, carrier);
        if (!carrier.addPassenger(display)) {
            number.remove();
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.TAU);
        double speed = random.nextDouble(MIN_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        carrier.setVelocity(new Vector(Math.cos(angle) * speed, UPWARD_LAUNCH_SPEED, Math.sin(angle) * speed));
        activeNumbers.add(number);
    }

    public static void tick() {
        var iterator = activeNumbers.iterator();
        while (iterator.hasNext()) {
            DamageNumber number = iterator.next();
            TextDisplay display = number.display;
            int age = ++number.age;
            if (!display.isValid() || !number.carrier.isValid() || age >= LIFETIME) {
                number.remove();
                iterator.remove();
                continue;
            }

            if (age > FADE_START) {
                int alpha = 255 * (LIFETIME - age) / (LIFETIME - FADE_START);
                display.setTextOpacity((byte) alpha);
            }
        }
    }

    public static void clear() {
        activeNumbers.forEach(DamageNumber::remove);
        activeNumbers.clear();
    }

    private void remove() {
        display.remove();
        carrier.remove();
    }
}
