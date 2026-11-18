package com.fatearena.core;

import com.fatearena.animation.AnimationClip;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

public class AnimationManager {
    private GamePlayer gamePlayer;
    private boolean animationHidden;
    private final Plugin plugin = JavaPlugin.getProvidingPlugin(AnimationManager.class);
    private ItemDisplay itemDisplay;
    private ItemStack thirdPersonItem;
    private AnimationClip currentAnimation;
    private Player minecraftPlayer;

    private Location previousLocation;
    private boolean passengerFollowing = true;
    private boolean thirdPersonPerspective;
    private boolean ownerBodyHidden;
    private boolean previousInvisible;
    private float lastModelYaw = Float.NaN;


    public boolean isMoving;
    public AnimationManager(GamePlayer player)
    {
        this.gamePlayer = player;
        minecraftPlayer = player.getPlayer();
        spawnItemDisplay();
        previousLocation = minecraftPlayer.getLocation();

    }
    public void animationTick()
    {
        if(gamePlayer.canAttack())
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
    public boolean isAnimationHidden() {return animationHidden;}
    public void clearAnimation() {
        currentAnimation = null;
        thirdPersonItem = null;
        itemDisplay.setItemStack(null);
        setAnimationHidden(false);
        updateItemDisplay();
    }
    public void setAnimationHidden(boolean hidden) {
        if (animationHidden == hidden) {
            return;
        }
        animationHidden = hidden;
        updateOwnerBodyVisibility();
        Player player = gamePlayer.getPlayer();
        for (Player viewer : player.getServer().getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (hidden) {
                viewer.hideEntity(plugin, player);
            } else {
                viewer.showEntity(plugin, player);
            }
        }
    }
    private void spawnItemDisplay() {
        lastModelYaw = Float.NaN;
        itemDisplay = minecraftPlayer.getWorld().spawn(getDisplayLocation(), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTeleportDuration(passengerFollowing ? 0 : 1);
            display.setInterpolationDuration(2);
            display.setItemStack(thirdPersonItem);
        });
        updateOwnerDisplayVisibility();
    }

    /** Toggles the owner's model visibility; the client camera is controlled with F5. */
    public boolean togglePerspective() {
        thirdPersonPerspective = !thirdPersonPerspective;
        updateOwnerDisplayVisibility();
        updateOwnerBodyVisibility();
        return thirdPersonPerspective;
    }

    private void updateOwnerDisplayVisibility() {
        if (thirdPersonPerspective) {
            minecraftPlayer.showEntity(plugin, itemDisplay);
        } else {
            minecraftPlayer.hideEntity(plugin, itemDisplay);
        }
    }

    private void updateOwnerBodyVisibility() {
        boolean hideBody = thirdPersonPerspective && animationHidden;
        if (hideBody && !ownerBodyHidden) {
            previousInvisible = minecraftPlayer.isInvisible();
            minecraftPlayer.setInvisible(true);
            ownerBodyHidden = true;
        } else if (!hideBody && ownerBodyHidden) {
            minecraftPlayer.setInvisible(previousInvisible);
            ownerBodyHidden = false;
        }
    }

    /** Switch back to teleport following if passenger rendering is unsuitable. */
    public void setPassengerFollowing(boolean enabled) {
        passengerFollowing = enabled;
        itemDisplay.leaveVehicle();
        itemDisplay.setTeleportDuration(enabled ? 0 : 1);
        updateItemDisplay();
    }

    /** Called on the server thread each tick to follow movement and world changes. */
    public void updateItemDisplay() {
        if (!itemDisplay.isValid() || !itemDisplay.getWorld().equals(minecraftPlayer.getWorld())) {
            itemDisplay.remove();
            spawnItemDisplay();
        }
        if (passengerFollowing && gamePlayer.getChoosenCharacter() != null && !minecraftPlayer.isDead()) {
            if (!minecraftPlayer.equals(itemDisplay.getVehicle())) {
                itemDisplay.leaveVehicle();
                itemDisplay.teleport(getDisplayLocation());
                minecraftPlayer.addPassenger(itemDisplay);
            }
            if (minecraftPlayer.equals(itemDisplay.getVehicle())) {
                updateModelRotation();
                return;
            }
        }
        itemDisplay.leaveVehicle();
        itemDisplay.teleport(getDisplayLocation());
        updateModelRotation();
    }

    /** Smooth visual yaw independently of passenger position; translation is untouched. */
    private void updateModelRotation() {
        float yaw = (minecraftPlayer.getLocation().getYaw() + 180.0f) % 360.0f;
        if (yaw < 0.0f) {
            yaw += 360.0f;
        }
        if (Float.compare(yaw, lastModelYaw) == 0) {
            return;
        }
        Transformation transformation = itemDisplay.getTransformation();
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
        // Equivalent quaternion signs avoid discontinuities when yaw crosses 360 degrees.
        if (rotation.dot(transformation.getLeftRotation()) < 0.0f) {
            rotation.set(-rotation.x(), -rotation.y(), -rotation.z(), -rotation.w());
        }
        transformation.getLeftRotation().set(rotation);
        itemDisplay.setInterpolationDelay(0);
        itemDisplay.setTransformation(transformation);
        lastModelYaw = yaw;
    }
    public void playAnimation(String animationName) {
        playAnimation(animationName, null);
    }

    private void playAnimation(String animationName, Integer frameOffset) {
        if (gamePlayer.getChoosenCharacter() == null) {
            return;
        }
        AnimationClip clip = new AnimationClip(
                animationName, gamePlayer.getChoosenCharacter().getCharacterName(), frameOffset);
        ItemStack fpItem = clip.getFirstPersonItem();
        thirdPersonItem = clip.getThirdPersonItem();
        minecraftPlayer.getInventory().setItemInMainHand(fpItem);
        updateItemDisplay();
        itemDisplay.setItemStack(thirdPersonItem);
        currentAnimation = clip;
        setAnimationHidden(true);
    }
    public void playAttackAnimation(String animationName, int duration)
    {
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
        playAnimation(animationName, frameOffset);
    }

    private static int animationFrameOffset(long gameTime, long durationTicks, long startTick) {
        long animationStart = Math.clamp(startTick, 0, durationTicks - 1);
        long realDuration = durationTicks - animationStart;
        return (int) Math.floorMod((gameTime % 24000) - animationStart, realDuration);
    }
    public void removeItemDisplay() {
        setAnimationHidden(false);
        itemDisplay.remove();
    }

    /** Hides the vanilla entity from a newly connected viewer during animation. */

    public ItemDisplay getItemDisplay() {
        return itemDisplay;
    }

    private Location getDisplayLocation() {
        Location location = minecraftPlayer.getLocation();
        location.setYaw(0.0f);
        location.setPitch(0);
        return location;
    }

}
