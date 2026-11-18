package com.fatearena.otherinteractables;

import com.fatearena.core.GameMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Dummy extends GameMob {
    public Dummy(Entity entity) {
        super(entity, 10000000);
        entity.setInvulnerable(true);
        if (entity instanceof ArmorStand stand) {
            stand.setVisible(false);
        }
    }
}
