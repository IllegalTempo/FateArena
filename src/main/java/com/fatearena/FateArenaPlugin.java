package com.fatearena;

import com.fatearena.core.GamePlayer;
import com.fatearena.core.GameMob;
import com.fatearena.character.fate.Saber;
import com.fatearena.core.InteractableEntity;
import com.fatearena.core.PlayerBehaviour;
import com.fatearena.otherinteractables.Dummy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.File;
import java.io.IOException;

public final class FateArenaPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, GameMob> gameMobs = new HashMap<>();
    private final Map<UUID, InteractableEntity> interactableEntities = new HashMap<>();
    private final Set<UUID> dummyIds = new LinkedHashSet<>();
    private BukkitTask displayUpdateTask;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerBehaviour(), this);
        getServer().getOnlinePlayers().forEach(player -> {
            GamePlayer gamePlayer = new GamePlayer(player);
            registerGamePlayer(player.getUniqueId(), gamePlayer);
        });
        loadDummies();
        getServer().getWorlds().forEach(world -> world.getEntities().forEach(this::registerDummy));
        displayUpdateTask = getServer().getScheduler().runTaskTimer(this,
                () -> gamePlayers.values().forEach(GamePlayer::tick), 1L, 1L);
    }

    public static FateArenaPlugin getInstance() {
        return JavaPlugin.getPlugin(FateArenaPlugin.class);
    }
    @Override
    public void onDisable() {
        if (displayUpdateTask != null) {
            displayUpdateTask.cancel();
        }
        gamePlayers.values().forEach(GamePlayer::cleanup);
        gamePlayers.clear();
        gameMobs.clear();
        interactableEntities.clear();
        dummyIds.clear();
    }
    public void registerInteractableEntity(UUID uuid, InteractableEntity entity) {
        interactableEntities.put(uuid, entity);
        if (entity instanceof GameMob mob) {
            gameMobs.put(uuid, mob);
        } else {
            gameMobs.remove(uuid);
        }
    }
    public void unregisterInteractableEntity(UUID uuid) {
        interactableEntities.remove(uuid);
        gameMobs.remove(uuid);
    }
    public void registerGamePlayer(UUID uuid, GamePlayer player) {
        gamePlayers.put(uuid, player);
        registerInteractableEntity(uuid, player);
        gamePlayers.values().forEach(gamePlayer -> gamePlayer.hideFromViewer(player.getPlayer()));

    }
    public void unregisterGamePlayer(UUID uuid) {
        gamePlayers.remove(uuid);
        unregisterInteractableEntity(uuid);
    }
    private void loadDummies() {
        File file = new File(getDataFolder(), "dummies.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String value : data.getStringList("dummies")) {
            try {
                dummyIds.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid dummy UUID: " + value);
            }
        }
    }

    private void saveDummies() throws IOException {
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            throw new IOException("Cannot create plugin data folder");
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("dummies", dummyIds.stream().map(UUID::toString).toList());
        data.save(new File(getDataFolder(), "dummies.yml"));
    }

    private void registerDummy(Entity entity) {
        if (entity instanceof ArmorStand && dummyIds.contains(entity.getUniqueId())) {
            if (!interactableEntities.containsKey(entity.getUniqueId())) {
                registerInteractableEntity(entity.getUniqueId(), new Dummy(entity));
            }
        }
    }

    @EventHandler
    public void onDummyDamage(EntityDamageEvent event) {
        // Invulnerable entities can still be damaged by creative players.
        if (dummyIds.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::registerDummy);
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (dummyIds.contains(entity.getUniqueId())) {
                InteractableEntity dummy = interactableEntities.get(entity.getUniqueId());
                if (dummy != null) {
                    dummy.cleanup();
                }
                unregisterInteractableEntity(entity.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        GamePlayer p = new GamePlayer(event.getPlayer());
        registerGamePlayer(event.getPlayer().getUniqueId(), p);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GamePlayer gamePlayer = gamePlayers.get(event.getPlayer().getUniqueId());
        unregisterGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer != null) {
            gamePlayer.cleanup();
        }
    }

    /** Returns the online player's wrapper, or null if they are not registered. */
    public GamePlayer getGamePlayer(UUID uuid) {
        return gamePlayers.get(uuid);
    }
    /** Returns a registered player or dummy mob, or null if not registered. */
    public GameMob getGameMob(UUID uuid) {
        return gameMobs.get(uuid);
    }
    public InteractableEntity getInteractableEntity(UUID uuid) {
        return interactableEntities.get(uuid);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dummy")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can add or spawn dummies.");
                return true;
            }
            if (args.length != 1 || (!args[0].equalsIgnoreCase("add")
                    && !args[0].equalsIgnoreCase("spawn"))) {
                sender.sendMessage("Usage: /dummy <add|spawn>");
                return true;
            }
            if (args[0].equalsIgnoreCase("spawn")) {
                ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, dummy -> {
                    dummy.setCustomName("Training Dummy");
                    dummy.setArms(true);
                    dummy.setPersistent(true);
                    dummy.getEquipment().setHelmet(new ItemStack(Material.HAY_BLOCK));
                    dummy.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    dummy.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                    dummy.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
                });
                UUID id = stand.getUniqueId();
                dummyIds.add(id);
                try {
                    saveDummies();
                } catch (IOException e) {
                    dummyIds.remove(id);
                    stand.remove();
                    getLogger().log(java.util.logging.Level.SEVERE, "Could not save spawned dummy", e);
                    sender.sendMessage("Could not save the dummy. Check the server log.");
                    return true;
                }
                registerDummy(stand);
                sender.sendMessage("Spawned training dummy: " + id);
                return true;
            }
            ArmorStand nearest = null;
            double nearestDistance = 100.0;
            for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
                if (entity instanceof ArmorStand stand && stand.isValid()) {
                    double distance = stand.getLocation().distanceSquared(player.getLocation());
                    if (distance <= nearestDistance) {
                        nearest = stand;
                        nearestDistance = distance;
                    }
                }
            }
            if (nearest == null) {
                sender.sendMessage("No armor stand found within 10 blocks.");
                return true;
            }
            UUID id = nearest.getUniqueId();
            if (!dummyIds.add(id)) {
                registerDummy(nearest);
                sender.sendMessage("That armor stand is already a dummy.");
                return true;
            }
            try {
                saveDummies();
            } catch (IOException e) {
                dummyIds.remove(id);
                getLogger().log(java.util.logging.Level.SEVERE, "Could not save dummies", e);
                sender.sendMessage("Could not save the dummy. Check the server log.");
                return true;
            }
            registerDummy(nearest);
            sender.sendMessage("Added dummy: " + id);
            return true;
        }
        if (command.getName().equalsIgnoreCase("toggleperspective")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can toggle perspective.");
                return true;
            }
            if (args.length != 0) {
                sender.sendMessage("Usage: /toggleperspective");
                return true;
            }
            GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
            if (gamePlayer == null) {
                sender.sendMessage("Your player is not registered yet. Please rejoin.");
                return true;
            }
            boolean thirdPerson = gamePlayer.getAnimationManager().togglePerspective();
            sender.sendMessage("Perspective: " + (thirdPerson ? "third person" : "first person"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("displayfollow")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can change display following.");
                return true;
            }
            if (args.length != 1 || (!args[0].equalsIgnoreCase("passenger")
                    && !args[0].equalsIgnoreCase("teleport"))) {
                sender.sendMessage("Usage: /displayfollow <passenger|teleport>");
                return true;
            }
            GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
            if (gamePlayer != null) {
                boolean passenger = args[0].equalsIgnoreCase("passenger");
                gamePlayer.getAnimationManager().setPassengerFollowing(passenger);
                sender.sendMessage("Display following: " + (passenger ? "passenger" : "teleport"));
            }
            return true;
        }
        if (!command.getName().equalsIgnoreCase("select")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can select a character.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /select <character> (available: saber)");
            return true;
        }
        if (!args[0].equalsIgnoreCase("saber")) {
            sender.sendMessage("Unknown character. Available: saber");
            return true;
        }
        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        if (gamePlayer == null) {
            sender.sendMessage("Your player is not registered yet. Please rejoin.");
            return true;
        }
        gamePlayer.SelectCharacter(new Saber(gamePlayer));
        sender.sendMessage("Selected Saber.");
        return true;
    }
}
