package com.fatearena.core.Match;

import com.fatearena.core.GamePlayer;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Team {
    private final int teamNumber;
    private final Map<GamePlayer, PlayerMatchStatus> players = new LinkedHashMap<>();

    public Team(int teamNumber, UUID matchId, GamePlayer[] players) {
        this.teamNumber = teamNumber;
        for (GamePlayer player : players) {
            PlayerMatchStatus status = new PlayerMatchStatus(this, matchId,player);
            this.players.put(player, status);
            player.joinMatch(status);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Team team = (Team) obj;
        return teamNumber == team.teamNumber;
    }

    public Color getTeamColor() {
        Color[] teamColors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.ORANGE, Color.MAGENTA};
        if (teamNumber < 0 || teamNumber >= teamColors.length) {
            throw new IllegalArgumentException("Invalid team number: " + teamNumber);
        }
        return teamColors[teamNumber];
    }
    public int getTeamNumber() {
        return teamNumber;
    }

    public List<GamePlayer> getPlayers() {
        return List.copyOf(players.keySet());
    }

    public PlayerMatchStatus getPlayerStatus(GamePlayer player) {
        return players.get(player);
    }

    public int getAlivePlayerCount() {
        int count = 0;
        for (PlayerMatchStatus status : players.values()) {
            if (status.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public boolean isAlive() {
        return getAlivePlayerCount() > 0;
    }
}
