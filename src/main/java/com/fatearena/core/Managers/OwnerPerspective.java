package com.fatearena.core.Managers;

import com.fatearena.core.GamePlayer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class OwnerPerspective {
    private static final float LEFT_OFFSET = 1.25f;
    private static final double LOOK_DISTANCE = 64;

    private final Plugin plugin;
    private final GamePlayer gamePlayer;
    private final Player owner;
    private boolean thirdPerson;
    private OwnerAppearance appearance;

    OwnerPerspective(Plugin plugin, GamePlayer gamePlayer) {
        this.plugin = plugin;
        this.gamePlayer = gamePlayer;
        this.owner = gamePlayer.getPlayer();
    }

    boolean toggle() {
        thirdPerson = !thirdPerson;
        return thirdPerson;
    }

    void update(Location anchor, ItemDisplay body, ItemDisplay head) {
        if (gamePlayer.getChoosenCharacter() == null) {
            clear();
            return;
        }

        ServerPlayer player = ((CraftPlayer) owner).getHandle();
        if (appearance == null) {
            appearance = OwnerAppearance.attach(player, plugin, gamePlayer);
        }
        if (appearance == null) {
            return;
        }

        boolean showModel = thirdPerson && body != null;
        List<OwnerAppearance.DisplayPose> poses = showModel ? getModelPoses(anchor, body, head) : List.of();
        appearance.update(player, showModel, poses);
        if (body != null) {
            setVisible(body, showModel);
            setVisible(head, showModel);
        }
    }

    private void setVisible(ItemDisplay display, boolean visible) {
        if (owner.canSee(display) != visible) {
            if (visible) {
                owner.showEntity(plugin, display);
            } else {
                owner.hideEntity(plugin, display);
            }
        }
    }

    private List<OwnerAppearance.DisplayPose> getModelPoses(Location anchor, ItemDisplay body, ItemDisplay head) {
        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection();
        RayTraceResult hit = eye.getWorld().rayTrace(eye, look, LOOK_DISTANCE, FluidCollisionMode.NEVER,
                false, 0, entity -> !entity.equals(owner) && ((CraftEntity) entity).getHandle().isPickable());
        Vector target = hit == null ? eye.toVector().add(look.multiply(LOOK_DISTANCE)) : hit.getHitPosition();

        double yaw = Math.toRadians(eye.getYaw());
        Vector3f left = new Vector3f((float) Math.cos(yaw), 0, (float) Math.sin(yaw)).mul(LEFT_OFFSET);
        double dx = target.getX() - anchor.getX() - left.x;
        double dz = target.getZ() - anchor.getZ() - left.z;
        double horizontalDistance = Math.hypot(dx, dz);
        double aimYaw = horizontalDistance < 1.0e-6 ? yaw : Math.atan2(-dx, dz);
        float modelYaw = (float) (-aimYaw - Math.PI);
        double headY = anchor.getY() + AnimationManager.HEAD_OFFSET_Y + 0.25;
        float pitch = (float) Math.atan2(headY - target.getY(), horizontalDistance);
        Quaternionf headRotation = new Quaternionf().rotationYXZ(modelYaw, -pitch, 0);
        return List.of(
                new OwnerAppearance.DisplayPose(body.getEntityId(), left, new Quaternionf().rotationY(modelYaw)),
                new OwnerAppearance.DisplayPose(head.getEntityId(), AnimationManager.headOffset(left, headRotation), headRotation));
    }

    void clear() {
        if (appearance != null) {
            appearance.restore(((CraftPlayer) owner).getHandle());
            appearance = null;
        }
    }
}
