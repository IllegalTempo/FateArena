package com.fatearena.core.Managers;

import com.fatearena.character.fate.Saber;
import com.fatearena.core.DamageNumber;
import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;
import com.fatearena.core.InteractableEntity;
import com.fatearena.core.Match.MatchInstance;
import com.fatearena.core.PlayerBehaviour;
import com.fatearena.otherinteractables.Dummy;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class GameManager implements Listener {

    private static final PotionEffect SWING_HASTE = new PotionEffect(
            PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 254, false, false, false);

    private final Plugin plugin;
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, GameMob> gameMobs = new HashMap<>();
    private final Map<UUID, InteractableEntity> interactableEntities = new HashMap<>();
    private final Set<UUID> dummyIds = new LinkedHashSet<>();
    private BukkitTask updateTask;
    private BukkitTask oneSecondTask;

    private final Map<UUID, MatchInstance> activeMatches = new HashMap<>();



    public GameManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerBehaviour(this), plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::registerOnlinePlayer);
        loadDummies();
        plugin.getServer().getWorlds().forEach(world -> world.getEntities().forEach(this::registerDummy));
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::tick, 1L, 1L);
        oneSecondTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::oneSecondTick, 20L, 20L);

    }
    private void tick()
    {
        DamageNumber.tick();
        gamePlayers.values().forEach(GamePlayer::tick);
        gamePlayers.values().forEach(player -> player.updatePositionRing(this));
    }
    private void oneSecondTick()
    {
        gamePlayers.values().forEach(GamePlayer::oneSecondTick);
    }
    public void startMatch(GamePlayer[] joiningPlayers)
    {
        UUID matchId = UUID.randomUUID();
        activeMatches.put(matchId, new MatchInstance(matchId, joiningPlayers));

    }
    public MatchInstance getMatchInstance(UUID matchId) {
        return activeMatches.get(matchId);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (oneSecondTask != null) {
            oneSecondTask.cancel();
            oneSecondTask = null;
        }
        DamageNumber.clear();
        interactableEntities.values().forEach(InteractableEntity::cleanup);
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

    private void registerOnlinePlayer(Player player) {
        registerGamePlayer(player.getUniqueId(), new GamePlayer(player));
    }

    public void registerGamePlayer(UUID uuid, GamePlayer player) {
        gamePlayers.put(uuid, player);
        player.getPlayer().addPotionEffect(SWING_HASTE);
        registerInteractableEntity(uuid, player);
        gamePlayers.values().forEach(gamePlayer -> gamePlayer.getAnimationManager().hideFromViewer(player.getPlayer()));
    }

    public void unregisterGamePlayer(UUID uuid) {
        gamePlayers.remove(uuid);
        unregisterInteractableEntity(uuid);
    }

    private void loadDummies() {
        File file = new File(plugin.getDataFolder(), "dummies.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String value : data.getStringList("dummies")) {
            try {
                dummyIds.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid dummy UUID: " + value);
            }
        }
    }

    private void saveDummies() throws IOException {
        if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Cannot create plugin data folder");
        }
        YamlConfiguration data = new YamlConfiguration();
        data.set("dummies", dummyIds.stream().map(UUID::toString).toList());
        data.save(new File(plugin.getDataFolder(), "dummies.yml"));
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
        registerOnlinePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.addPotionEffect(SWING_HASTE);
            }
        });
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
    public boolean handleCommand(CommandSender sender, Command command, String[] args) {
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
                    plugin.getLogger().log(Level.SEVERE, "Could not save spawned dummy", e);
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
                plugin.getLogger().log(Level.SEVERE, "Could not save dummies", e);
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
