package com.fatearena.core.Match;

import com.fatearena.core.GameMob;
import com.fatearena.core.GamePlayer;
import org.bukkit.Bukkit;

import java.awt.*;
import java.util.UUID;

public class PlayerMatchStatus {
    private final GamePlayer player;
    private boolean isAlive;
    private int damageDealt;
    private int damageTaken;
    private final Team team;
    private UUID matchId;
    public PlayerMatchStatus(Team team, UUID matchId, GamePlayer player)
    {
        isAlive = true;
        damageDealt = 0;
        damageTaken = 0;
        this.team = team;
        this.matchId = matchId;
        this.player = player;
    }
    public void Die(GameMob killerm)
    {
        setAlive(false);
        if(killerm instanceof GamePlayer killer)
        {
            Bukkit.broadcastMessage(killer.getCurrentMatchStatus().getTeam().getTeamColor() + killer.getPlayer().getName() + Color.WHITE + " has killed " + team.getTeamColor() + player.getPlayer().getName());
            if(!team.isAlive())
            {
                Bukkit.broadcastMessage(team.getTeamColor().toString() + "Team " + team.getTeamNumber() + " has been eliminated!");
            }
        }

    }
    public UUID getMatchId() {
        return matchId;
    }
    public Team getTeam() {
        return team;
    }
    public boolean isAlive() {
        return isAlive;
    }
    private void setAlive(boolean alive) {
        isAlive = alive;
    }
    public int getDamageDealt() {
        return damageDealt;
    }
    public int getDamageTaken() {
        return damageTaken;
    }
    public void addDamageDealt(int damage) {
        damageDealt += damage;
    }
    public void addDamageTaken(int damage) {
        damageTaken += damage;
    }
}
