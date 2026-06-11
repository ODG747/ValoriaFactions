package com.massivecraft.factions.util.flight.stuct;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.util.TitleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * SaberFactionsX - Developed by Driftay.
 * All rights reserved 2020.
 * Creation Date: 10/27/2020
 */
public class AsyncPlayerMap implements Runnable {

    private final Server server = Bukkit.getServer();

    public AsyncPlayerMap(Plugin bukkitPlugin) {
        Bukkit.getScheduler().runTaskTimer(bukkitPlugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        for (Player pl : server.getOnlinePlayers()) {
            if (pl.isOnline() && pl.hasMetadata("showFactionTitle")) {
                processPlayer(pl);
            }
        }
    }

    private void processPlayer(Player pl) {
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(pl);
        Faction factionTo = Board.getInstance().getFactionAt(fPlayer.getLastStoodAt());
        TitleUtil.sendFactionChangeTitle(fPlayer, factionTo);
    }
}
