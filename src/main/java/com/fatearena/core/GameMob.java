package com.fatearena.core;

import org.bukkit.entity.Entity;

public class GameMob extends InteractableEntity{
    private int health;

    public GameMob(Entity entity, int health) {
        super(entity);
        this.health = health;
    }

    public int getHealth() {
        return health;
    }
    public void damage(int amount)
    {
        health -= amount;

    }
}
