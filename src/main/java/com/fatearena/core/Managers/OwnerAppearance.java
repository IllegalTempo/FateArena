package com.fatearena.core.Managers;

import com.fatearena.core.GamePlayer;
import com.mojang.datafixers.util.Pair;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.Plugin;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** Maintains the owner's display and gives an empty client hand a right-click input. */
final class OwnerAppearance extends ChannelDuplexHandler {
    private static final EntityDataAccessor<Byte> FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    // Display metadata layout in Spigot 26.3.
    private static final int POSITION_INTERPOLATION = Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID.id();
    private static final EntityDataAccessor<Integer> TRANSFORM_START =
            new EntityDataAccessor<>(POSITION_INTERPOLATION - 2, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> TRANSLATION =
            new EntityDataAccessor<>(POSITION_INTERPOLATION + 1, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Quaternionfc> LEFT_ROTATION =
            new EntityDataAccessor<>(POSITION_INTERPOLATION + 3, EntityDataSerializers.QUATERNION);
    private static final int INVISIBLE = 0x20;
    private static final int GLOWING = 0x40;
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final Channel channel;
    private final int entityId;
    private final Plugin plugin;
    private final GamePlayer gamePlayer;
    private final ItemStack inputItem;
    private final ClientboundSetEquipmentPacket inputEquipment;
    private final ClientboundSetEquipmentPacket blankEquipment;
    private volatile View view;
    private byte flags;

    private record View(boolean hidden, boolean emptyMainHand, List<DisplayPose> displays) {
        DisplayPose poseFor(int entityId) {
            for (DisplayPose pose : displays) {
                if (pose.entityId() == entityId) return pose;
            }
            return null;
        }
    }

    record DisplayPose(int entityId, Vector3fc translation, Quaternionfc rotation) {
        ClientboundSetEntityDataPacket applyTo(List<SynchedEntityData.DataValue<?>> original) {
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(original.size() + 3);
            for (var value : original) {
                if (value.id() != TRANSFORM_START.id() && value.id() != TRANSLATION.id()
                        && value.id() != LEFT_ROTATION.id()) {
                    values.add(value);
                }
            }
            values.add(SynchedEntityData.DataValue.create(TRANSFORM_START, 0));
            values.add(SynchedEntityData.DataValue.create(TRANSLATION, translation));
            values.add(SynchedEntityData.DataValue.create(LEFT_ROTATION, rotation));
            return new ClientboundSetEntityDataPacket(entityId, values);
        }
    }

    private OwnerAppearance(Channel channel, int entityId, byte flags, Plugin plugin, GamePlayer gamePlayer) {
        this.channel = channel;
        this.entityId = entityId;
        this.flags = flags;
        this.plugin = plugin;
        this.gamePlayer = gamePlayer;
        this.inputItem = new ItemStack(Items.STICK);
        inputItem.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air"));
        this.inputEquipment = new ClientboundSetEquipmentPacket(entityId,
                List.of(Pair.of(EquipmentSlot.MAINHAND, inputItem)));
        this.blankEquipment = new ClientboundSetEquipmentPacket(entityId,
                SLOTS.stream().map(slot -> Pair.of(slot, slot == EquipmentSlot.MAINHAND ? inputItem : ItemStack.EMPTY)).toList());
    }

    static OwnerAppearance attach(ServerPlayer player, Plugin plugin, GamePlayer gamePlayer) {
        List<Connection> connections = player.server.getConnection().getConnections();
        Channel channel;
        synchronized (connections) {
            channel = connections.stream()
                    .filter(connection -> connection.getPacketListener() == player.connection)
                    .findFirst().map(connection -> connection.channel).orElse(null);
        }
        if (channel == null || !channel.isActive()) {
            return null;
        }

        OwnerAppearance appearance = new OwnerAppearance(channel, player.getId(), player.getEntityData().get(FLAGS),
                plugin, gamePlayer);
        channel.eventLoop().execute(() -> channel.pipeline().addBefore("packet_handler", "fatearena_owner", appearance));
        return appearance;
    }

    void update(ServerPlayer player, boolean hidden, List<DisplayPose> displays) {
        View previous = view;
        boolean emptyMainHand = player.getMainHandItem().isEmpty();
        View current = new View(hidden, emptyMainHand, displays);
        if (current.equals(previous)) return;

        view = current;
        boolean appearanceChanged = previous == null ? hidden || emptyMainHand
                : previous.hidden() != hidden || previous.emptyMainHand() != emptyMainHand;
        if (appearanceChanged) {
            player.connection.send(appearancePacket(player));
        }
        for (DisplayPose pose : displays) {
            if (previous == null || !pose.equals(previous.poseFor(pose.entityId()))) {
                player.connection.send(pose.applyTo(List.of()));
            }
        }
    }

    void restore(ServerPlayer player) {
        view = null;
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().context(this) != null) {
                channel.pipeline().remove(this);
            }
        });
        if (channel.isActive() && player.connection != null) {
            player.connection.send(appearancePacket(player));
        }
    }

    private ClientboundBundlePacket appearancePacket(ServerPlayer player) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = SLOTS.stream()
                .map(slot -> Pair.of(slot, player.getItemBySlot(slot).copy())).toList();
        return new ClientboundBundlePacket(List.of(flagsPacket(player.getEntityData().get(FLAGS)),
                new ClientboundSetEquipmentPacket(entityId, equipment)));
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        View current = view;
        context.fireChannelRead(message);
        // Spigot skips PlayerInteractEvent when the real server hand is empty.
        // Let Minecraft acknowledge the use packet normally and handle just that missing event.
        if (current != null && current.emptyMainHand() && message instanceof ServerboundUseItemPacket use
                && use.hand() == InteractionHand.MAIN_HAND) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var player = (CraftPlayer) gamePlayer.getPlayer();
                var handle = player.getHandle();
                if (view != null && player.isOnline() && !handle.isImmobile()
                        && handle.connection.hasClientLoaded()
                        && player.getGameMode() != GameMode.SPECTATOR && handle.getMainHandItem().isEmpty()) {
                    gamePlayer.useSkill1();
                }
            });
        }
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        View current = view;
        if (current == null || (!current.hidden() && !current.emptyMainHand() && current.displays().isEmpty())) {
            context.write(message, promise);
        } else if (message instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            for (var packet : bundle.subPackets()) {
                appendMaskedPackets(packets, packet, current);
            }
            context.write(new ClientboundBundlePacket(packets), promise);
        } else if (message instanceof Packet<?> packet && affectsAppearance(packet, current)) {
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            @SuppressWarnings("unchecked")
            Packet<? super ClientGamePacketListener> gamePacket = (Packet<? super ClientGamePacketListener>) packet;
            appendMaskedPackets(packets, gamePacket, current);
            context.write(packets.size() == 1 ? packets.getFirst() : new ClientboundBundlePacket(packets), promise);
        } else {
            context.write(message, promise);
        }
    }

    private boolean affectsAppearance(Packet<?> packet, View current) {
        return packet instanceof ClientboundRespawnPacket || refreshesEquipment(packet)
                || (packet instanceof ClientboundSetEquipmentPacket equipment && equipment.getEntity() == entityId)
                || (packet instanceof ClientboundSetEntityDataPacket metadata
                        && ((current.hidden() && metadata.id() == entityId) || current.poseFor(metadata.id()) != null));
    }

    private void appendMaskedPackets(List<Packet<? super ClientGamePacketListener>> packets,
                                     Packet<? super ClientGamePacketListener> packet, View current) {
        if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            DisplayPose pose = current.poseFor(metadata.id());
            if (pose != null) {
                // Pairing and animation updates both carry the shared transform.
                packets.add(pose.applyTo(metadata.packedItems()));
            } else {
                packets.add(current.hidden() ? maskFlags(metadata) : metadata);
            }
        } else if (packet instanceof ClientboundSetEquipmentPacket equipment && equipment.getEntity() == entityId) {
            if (current.hidden()) {
                packets.add(blankEquipment);
            } else {
                packets.add(equipment);
                packets.add(inputEquipment);
            }
        } else {
            packets.add(packet);
            if (packet instanceof ClientboundRespawnPacket) {
                if (current.hidden()) {
                    packets.add(flagsPacket(hiddenFlags(flags)));
                }
                packets.add(current.hidden() ? blankEquipment : inputEquipment);
            } else if (refreshesEquipment(packet)) {
                // Apply the mask after the inventory sync in the same client tick.
                packets.add(current.hidden() ? blankEquipment : inputEquipment);
            }
        }
    }

    private ClientboundSetEntityDataPacket maskFlags(ClientboundSetEntityDataPacket packet) {
        if (packet.id() != entityId) {
            return packet;
        }
        List<SynchedEntityData.DataValue<?>> values = packet.packedItems();
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            if (value.id() == FLAGS.id() && value.value() instanceof Byte sharedFlags) {
                flags = sharedFlags;
                byte hidden = hiddenFlags(flags);
                if (hidden != flags) {
                    var masked = new ArrayList<>(values);
                    masked.set(i, SynchedEntityData.DataValue.create(FLAGS, hidden));
                    return new ClientboundSetEntityDataPacket(entityId, masked);
                }
                break;
            }
        }
        return packet;
    }

    private static boolean refreshesEquipment(Packet<?> packet) {
        return packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket
                || packet instanceof ClientboundSetHeldSlotPacket;
    }

    private static byte hiddenFlags(byte flags) {
        return (byte) ((flags | INVISIBLE) & ~GLOWING);
    }

    private ClientboundSetEntityDataPacket flagsPacket(byte flags) {
        return new ClientboundSetEntityDataPacket(entityId, List.of(SynchedEntityData.DataValue.create(FLAGS, flags)));
    }
}
