package com.fatearena.core.Managers;

import com.fatearena.animation.AnimationClip;
import com.fatearena.character.AttackInfo;
import com.fatearena.core.GamePlayer;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public class AnimationManager {
    // The character's neck sits 0.4 blocks below the body display's origin.
    static final float HEAD_OFFSET_Y = -0.4f;

    private final GamePlayer gamePlayer;
    private boolean animationHidden;
    private final Plugin plugin = JavaPlugin.getProvidingPlugin(AnimationManager.class);
    private final OwnerPerspective ownerPerspective;
    private ItemDisplay itemDisplay;
    private ItemDisplay headDisplay;
    private ItemStack firstPersonItem;
    private ItemStack thirdPersonItem;
    private ItemStack characterHead;
    private boolean useVanillaHead;
    private AnimationClip currentAnimation;
    private final Player minecraftPlayer;

    private boolean passengerFollowing = true;

    public boolean isMoving;
    public AnimationManager(GamePlayer player)
    {
        this.gamePlayer = player;
        minecraftPlayer = player.getPlayer();
        ownerPerspective = new OwnerPerspective(plugin, player);
    }
    public void animationTick()
    {
        if(gamePlayer.canAttack() && !gamePlayer.isDefending())
        {

            if(isMoving)
            {
                playAnimation("walk");
            }
            else
            {
                playAnimation("idle");
            }
        }
    }
    public void clearAnimation() {
        currentAnimation = null;
        firstPersonItem = null;
        thirdPersonItem = null;
        setAnimationHidden(false);
        updateItemDisplay();
    }
    public void setAnimationHidden(boolean hidden) {
        if (animationHidden == hidden) {
            return;
        }
        animationHidden = hidden;
        for (Player viewer : minecraftPlayer.getServer().getOnlinePlayers()) {
            if (hidden) {
                hideFromViewer(viewer);
            } else if (!viewer.equals(minecraftPlayer)) {
                viewer.showEntity(plugin, minecraftPlayer);
            }
        }
    }

    void hideFromViewer(Player viewer) {
        if (animationHidden && !viewer.equals(minecraftPlayer)) {
            viewer.hideEntity(plugin, minecraftPlayer);
        }
    }

    private ItemDisplay spawnSharedDisplay(Location origin, ItemStack item, Vector3f translation, Quaternionf rotation) {
        return origin.getWorld().spawn(origin, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTeleportDuration(1);
            display.setInterpolationDuration(2);
            display.setItemStack(item);
            display.setTransformation(new Transformation(translation, rotation, new Vector3f(1), new Quaternionf()));
            minecraftPlayer.hideEntity(plugin, display);
        });
    }

    /** Toggles the owner's model visibility; the client camera is controlled with F5. */
    public boolean togglePerspective() {
        boolean thirdPerson = ownerPerspective.toggle();
        updateItemDisplay();
        return thirdPerson;
    }

    public void setCharacterHead(ItemStack head) {
        if (head == null && gamePlayer.getChoosenCharacter() != null) {
            head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(minecraftPlayer);
            head.setItemMeta(meta);
        }
        characterHead = head;
    }

    /** Switch between the riding anchor and the player's feet. */
    public void setPassengerFollowing(boolean enabled) {
        passengerFollowing = enabled;
        updateItemDisplay();
    }

    /** Called on the server thread each tick to follow movement and world changes. */
    public void updateItemDisplay() {
        if (thirdPersonItem == null || minecraftPlayer.isDead()) {
            removeDisplays();
            ownerPerspective.update(null, null, null);
            return;
        }

        Location anchor = minecraftPlayer.getLocation();
        float yaw = (float) Math.toRadians(-anchor.getYaw() - 180.0);
        float pitch = (float) Math.toRadians(-anchor.getPitch());
        if (passengerFollowing) {
            var player = ((CraftPlayer) minecraftPlayer).getHandle();
            Vec3 offset = player.getDimensions(player.getPose()).attachments()
                    .getClamped(EntityAttachment.PASSENGER, 0, anchor.getYaw());
            anchor.add(offset.x, offset.y, offset.z);
        }
        anchor.setYaw(0);
        anchor.setPitch(0);

        itemDisplay = updateSharedDisplay(itemDisplay, anchor, thirdPersonItem,
                new Vector3f(), new Quaternionf().rotationY(yaw));
        Quaternionf headRotation = new Quaternionf().rotationYXZ(yaw, pitch, 0);
        headDisplay = updateSharedDisplay(headDisplay, anchor, useVanillaHead ? characterHead : null,
                headOffset(new Vector3f(), headRotation), headRotation);
        ownerPerspective.update(anchor, itemDisplay, headDisplay);
    }

    private ItemDisplay updateSharedDisplay(ItemDisplay display, Location origin, ItemStack item,
                                            Vector3f translation, Quaternionf rotation) {
        if (display == null || !display.isValid() || !display.getWorld().equals(origin.getWorld())) {
            if (display != null) display.remove();
            return spawnSharedDisplay(origin, item, translation, rotation);
        }
        ItemStack displayed = display.getItemStack();
        if (item == null ? !displayed.getType().isAir() : !Objects.equals(displayed, item)) {
            display.setItemStack(item);
        }
        if (!origin.equals(display.getLocation())) {
            display.teleport(origin);
        }
        updateDisplayPose(display, translation, rotation);
        return display;
    }

    private void removeDisplays() {
        if (headDisplay != null) {
            headDisplay.remove();
            headDisplay = null;
        }
        if (itemDisplay != null) {
            itemDisplay.remove();
            itemDisplay = null;
        }
    }

    static Vector3f headOffset(Vector3f bodyOffset, Quaternionf rotation) {
        // Rotate around the skull item's base so the head stays attached at the neck.
        return new Vector3f(bodyOffset).add(0, HEAD_OFFSET_Y, 0)
                .add(rotation.transform(new Vector3f(0, 0.5f, 0)));
    }

    private static void updateDisplayPose(ItemDisplay display, Vector3f translation, Quaternionf rotation) {
        Transformation transform = display.getTransformation();
        if (rotation.dot(transform.getLeftRotation()) < 0) {
            rotation.set(-rotation.x(), -rotation.y(), -rotation.z(), -rotation.w());
        }
        if (!transform.getTranslation().equals(translation) || !transform.getLeftRotation().equals(rotation)) {
            transform.getTranslation().set(translation);
            transform.getLeftRotation().set(rotation);
            display.setInterpolationDelay(0);
            display.setTransformation(transform);
        }
    }

    public void playAnimation(String animationName) {
        playAnimation(animationName, true);
    }

    /** Uses the separate Minecraft head display when the animation needs a head. */
    public void playAnimation(String animationName, boolean useVanillaHead) {
        playAnimation(animationName, null, useVanillaHead);
    }

    private void playAnimation(String animationName, Integer frameOffset, boolean useVanillaHead) {
        if (gamePlayer.getChoosenCharacter() == null || minecraftPlayer.isDead()) {
            return;
        }

        AnimationClip clip = new AnimationClip(
                animationName, gamePlayer.getChoosenCharacter().getCharacterName(), frameOffset);
        if(clip.equals(currentAnimation) && this.useVanillaHead == useVanillaHead)
        {
            if (!firstPersonItem.equals(minecraftPlayer.getInventory().getItemInMainHand())) {
                minecraftPlayer.getInventory().setItemInMainHand(firstPersonItem);
            }
            return;
        }
        firstPersonItem = clip.getFirstPersonItem();
        thirdPersonItem = clip.getThirdPersonItem();
        this.useVanillaHead = useVanillaHead;
        minecraftPlayer.getInventory().setItemInMainHand(firstPersonItem);
        updateItemDisplay();
        currentAnimation = clip;
        setAnimationHidden(true);
    }

    public void playAttackAnimation(AttackInfo info) {
        playAttackAnimation(info.animationName, info.attackDuration, info.useVanillaHead);
    }
    public void playAttackAnimation(String animationName, int duration) {
        playAttackAnimation(animationName, duration, true);
    }

    public void playAttackAnimation(String animationName, int duration, boolean useVanillaHead) {
        if (gamePlayer.getChoosenCharacter() == null) {
            return;
        }
        // obj³ play_once encoding: shader clock is game time modulo 24000 ticks.
        if (duration <= 0 || duration > 0x1000000) {
            throw new IllegalArgumentException("Animation duration must be between 1 and 16777216 ticks");
        }
        long gameTime = minecraftPlayer.getWorld().getGameTime();
        int frameOffset = animationFrameOffset(gameTime, duration, 0);

        gamePlayer.setAttackCooldownTicks(duration);
        playAnimation(animationName, frameOffset, useVanillaHead);
    }

    public static int animationFrameOffset(long gameTime, long durationTicks, long startTick) {
        long animationStart = Math.clamp(startTick, 0, durationTicks - 1);
        long realDuration = durationTicks - animationStart;
        return (int) Math.floorMod((gameTime % 24000) - animationStart, realDuration);
    }
    public void removeItemDisplay() {
        setAnimationHidden(false);
        ownerPerspective.clear();
        removeDisplays();
    }

    public ItemDisplay getItemDisplay() {
        return itemDisplay;
    }

}
