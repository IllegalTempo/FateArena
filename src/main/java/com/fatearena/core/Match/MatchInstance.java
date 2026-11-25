package com.fatearena.core.Match;

import com.fatearena.core.GamePlayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MatchInstance {
    private final UUID matchId;
    private final List<Team> teams;

    public MatchInstance(UUID matchId, Team[] teams) {
        this.matchId = matchId;
        this.teams = List.copyOf(Arrays.asList(teams));
    }

    public MatchInstance(UUID matchId, GamePlayer[] playerlist, int numberofTeams) {
        this(matchId, randomTeams(matchId, playerlist, numberofTeams));
    }

    public MatchInstance(UUID matchId, GamePlayer[] playerlist) {
        this(matchId, playerlist, 2);
    }

    private static Team[] randomTeams(UUID matchId, GamePlayer[] players, int numberofTeams) {
        if (numberofTeams <= 0) {
            throw new IllegalArgumentException("Number of teams must be positive");
        }

        GamePlayer[] shuffled = players.clone();
        Collections.shuffle(Arrays.asList(shuffled));
        GamePlayer[][] teamPlayers = new GamePlayer[numberofTeams][];
        for (int team = 0; team < numberofTeams; team++) {
            int size = players.length / numberofTeams;
            if (team < players.length % numberofTeams) {
                size++;
            }
            teamPlayers[team] = new GamePlayer[size];
        }
        for (int i = 0; i < shuffled.length; i++) {
            teamPlayers[i % numberofTeams][i / numberofTeams] = shuffled[i];
        }
        Team[] teams = new Team[numberofTeams];
        for (int team = 0; team < numberofTeams; team++) {
            teams[team] = new Team(team, matchId, teamPlayers[team]);
        }
        return teams;
    }


    public int getAliveTeamCount() {
        int count = 0;
        for (Team team : teams) {
            if (team.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public List<Team> getTeams() {
        return teams;
    }


}
