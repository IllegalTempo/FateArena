package com.fatearena.core;

import com.fatearena.FateArenaPlugin;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Sends viewer-only metadata without changing the server's entity flags (Spigot 26.3). */
final class ViewerGlow {
    // Entity's shared flags occupy metadata index 0 in 26.3.
    private static final EntityDataAccessor<Byte> FLAGS =
            new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    private ViewerGlow() {}

    static PlayerTeam createDamageTeam(Entity target) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), "fa_hit_" + target.getUniqueId());
        team.setColor(Optional.of(TeamColor.RED));
        team.getPlayers().add(((CraftEntity) target).getHandle().getScoreboardName());
        return team;
    }

    static void sendDamageTeam(Player viewer, PlayerTeam team) {
        ((CraftPlayer) viewer).getHandle().connection.send(
                ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    static void restoreTeam(Player viewer, PlayerTeam damageTeam, Entity target) {
        var connection = ((CraftPlayer) viewer).getHandle().connection;
        connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(damageTeam));
        String entry = ((CraftEntity) target).getHandle().getScoreboardName();
        PlayerTeam actualTeam = ((CraftScoreboard) viewer.getScoreboard()).getHandle().getPlayersTeam(entry);
        if (actualTeam != null) {
            connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    actualTeam, entry, ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    static void send(Player viewer, Entity target, boolean glowing) {
        if (!viewer.isOnline() || !viewer.canSee(target)) {
            return;
        }
        byte flags = ((CraftEntity) target).getHandle().getEntityData().get(FLAGS);
        GameMob mob = FateArenaPlugin.getInstance().getGameManager().getGameMob(target.getUniqueId());
        glowing = glowing || (mob != null && mob.hasDamageGlow(viewer));
        // On removal, restore current server flags, including any real glowing effect.
        byte clientFlags = glowing ? (byte) (flags | 0x40) : flags;
        var packet = new ClientboundSetEntityDataPacket(target.getEntityId(),
                List.of(SynchedEntityData.DataValue.create(FLAGS, clientFlags)));
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
