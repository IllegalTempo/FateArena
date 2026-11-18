package com.fatearena.core;

import org.bukkit.entity.Entity;

public class InteractableEntity {
    protected Entity entity;

    public InteractableEntity(Entity entity) {
        this.entity = entity;
    }

    public void cleanup() {
    }
}
