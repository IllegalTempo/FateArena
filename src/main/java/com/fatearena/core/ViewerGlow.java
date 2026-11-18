package com.fatearena.core;

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/** Sends viewer-only metadata without changing the server's entity flags (Spigot 26.3). */
final class ViewerGlow {
    // Entity's shared flags occupy metadata index 0 in 26.3.
    private static final EntityDataAccessor<Byte> FLAGS =
            new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    private ViewerGlow() {}

    static void send(Player viewer, Entity target, boolean glowing) {
        if (!viewer.isOnline()) {
            return;
        }
        byte flags = ((CraftEntity) target).getHandle().getEntityData().get(FLAGS);
        // On removal, restore current server flags, including any real glowing effect.
        byte clientFlags = glowing ? (byte) (flags | 0x40) : flags;
        var packet = new ClientboundSetEntityDataPacket(target.getEntityId(),
                List.of(SynchedEntityData.DataValue.create(FLAGS, clientFlags)));
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
