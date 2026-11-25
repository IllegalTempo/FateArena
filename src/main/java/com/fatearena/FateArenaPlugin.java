package com.fatearena;

import com.fatearena.core.Managers.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class FateArenaPlugin extends JavaPlugin {
    private GameManager gameManager;

    public static FateArenaPlugin getInstance() {
        return JavaPlugin.getPlugin(FateArenaPlugin.class);
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    @Override
    public void onEnable() {
        gameManager = new GameManager(this);
        gameManager.start();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return gameManager.handleCommand(sender, command, args);
    }
}
