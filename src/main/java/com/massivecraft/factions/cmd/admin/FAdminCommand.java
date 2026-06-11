package com.massivecraft.factions.cmd.admin;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.audit.FLogType;
import com.massivecraft.factions.cmd.audit.FactionLogs;
import com.massivecraft.factions.event.FPlayerJoinEvent;
import com.massivecraft.factions.event.FPlayerLeaveEvent;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;
import com.massivecraft.factions.scoreboards.FTeamWrapper;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.MiscUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FAdminCommand implements TabExecutor {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[Valoria] " + ChatColor.GRAY;
    private final FactionsPlugin plugin;

    public FAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("valoria.fadmin")) {
            reply(sender, ChatColor.RED + "Permission insuffisante.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        try {
            return execute(sender, args);
        } catch (NumberFormatException exception) {
            reply(sender, ChatColor.RED + "La valeur numerique est invalide.");
            return true;
        }
    }

    private boolean execute(CommandSender sender, String[] args) {
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "disband" -> disband(sender, args);
            case "rename" -> rename(sender, args);
            case "leader" -> leader(sender, args);
            case "power" -> power(sender, args);
            case "claim" -> claim(sender, args);
            case "claims" -> claims(sender, args);
            case "members" -> members(sender, args);
            case "kick" -> kick(sender, args);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender, args);
            case "money" -> money(sender, args);
            case "inspect" -> inspect(sender, args);
            case "logs" -> logs(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean disband(CommandSender sender, String[] args) {
        Faction faction = requireFaction(sender, args, 1);
        if (faction == null) return true;
        faction.disband(sender instanceof Player player ? player : null);
        reply(sender, "Faction " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + " supprimee.");
        return true;
    }

    private boolean rename(CommandSender sender, String[] args) {
        Faction faction = requireFaction(sender, args, 1);
        if (faction == null || args.length < 3) return usage(sender, "/fadmin rename <faction> <nouveauNom>");
        String newName = args[2];
        if (!MiscUtil.validateTag(newName).isEmpty()) {
            reply(sender, ChatColor.RED + String.join(" ", MiscUtil.validateTag(newName)));
            return true;
        }
        Faction existing = Factions.getInstance().getByTag(newName);
        if (existing != null && existing != faction) {
            reply(sender, ChatColor.RED + "Ce nom de faction est deja utilise.");
            return true;
        }
        String oldName = faction.getTag();
        faction.setTag(newName);
        FTeamWrapper.updatePrefixes(faction);
        log(faction, FLogType.FTAG_EDIT, actor(sender), newName);
        reply(sender, ChatColor.AQUA + oldName + ChatColor.GRAY + " renomme en " + ChatColor.AQUA + newName + ChatColor.GRAY + ".");
        return true;
    }

    private boolean leader(CommandSender sender, String[] args) {
        FPlayer target = requirePlayer(sender, args, 1);
        if (target == null) return true;
        if (!target.hasFaction()) {
            reply(sender, ChatColor.RED + "Ce joueur n'a pas de faction.");
            return true;
        }
        Faction faction = target.getFaction();
        FPlayer current = faction.getFPlayerLeader();
        if (current != null && current != target) current.setRole(Role.COLEADER);
        target.setRole(Role.LEADER);
        log(faction, FLogType.RANK_EDIT, actor(sender), target.getName(), ChatColor.RED + "Leader");
        reply(sender, ChatColor.AQUA + target.getName() + ChatColor.GRAY + " est maintenant chef de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + ".");
        return true;
    }

    private boolean power(CommandSender sender, String[] args) {
        if (args.length < 4) return usage(sender, "/fadmin power <set|add|remove> <faction> <valeur>");
        Faction faction = requireFaction(sender, args, 2);
        if (faction == null) return true;
        double value = parseNonNegative(args[3]);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> faction.setPowerBoost(faction.getPowerBoost() + value - faction.getPower());
            case "add" -> faction.setPowerBoost(faction.getPowerBoost() + value);
            case "remove" -> faction.setPowerBoost(faction.getPowerBoost() - value);
            default -> { return usage(sender, "/fadmin power <set|add|remove> <faction> <valeur>"); }
        }
        reply(sender, "Puissance de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + " : " + format(faction.getPower()) + ".");
        return true;
    }

    private boolean claim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            reply(sender, ChatColor.RED + "Cette commande necessite un joueur ciblant un chunk.");
            return true;
        }
        if (args.length < 3) return usage(sender, "/fadmin claim <add|remove> <faction>");
        Faction faction = requireFaction(sender, args, 2);
        if (faction == null) return true;
        FLocation location = FLocation.wrap(player.getLocation());
        FPlayer actor = FPlayers.getInstance().getByPlayer(player);
        Faction current = Board.getInstance().getFactionAt(location);
        if (args[1].equalsIgnoreCase("add")) {
            if (!current.isWilderness()) {
                reply(sender, ChatColor.RED + "Ce chunk appartient deja a " + current.getTag() + ".");
                return true;
            }
            LandClaimEvent event = new LandClaimEvent(location, faction, actor);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return cancelled(sender);
            Board.getInstance().setFactionAt(faction, location);
            log(faction, FLogType.CHUNK_CLAIMS, actor(sender), ChatColor.GREEN + "CLAIMED", "1", location.formatXAndZ(","));
            reply(sender, "Chunk ajoute a " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + ".");
            return true;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (current != faction) {
                reply(sender, ChatColor.RED + "Ce chunk n'appartient pas a cette faction.");
                return true;
            }
            LandUnclaimEvent event = new LandUnclaimEvent(location, faction, actor);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return cancelled(sender);
            Board.getInstance().removeAt(location);
            log(faction, FLogType.CHUNK_CLAIMS, actor(sender), ChatColor.RED + "UNCLAIMED", "1", location.formatXAndZ(","));
            reply(sender, "Chunk retire de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + ".");
            return true;
        }
        return usage(sender, "/fadmin claim <add|remove> <faction>");
    }

    private boolean claims(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) return usage(sender, "/fadmin claims clear <faction>");
        Faction faction = requireFaction(sender, args, 2);
        if (faction == null) return true;
        int count = faction.getLandRounded();
        Board.getInstance().unclaimAll(faction.getId());
        faction.clearAllClaimOwnership();
        log(faction, FLogType.CHUNK_CLAIMS, actor(sender), ChatColor.RED + "UNCLAIMED", String.valueOf(count), "all");
        reply(sender, count + " claims supprimes pour " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + ".");
        return true;
    }

    private boolean members(CommandSender sender, String[] args) {
        Faction faction = requireFaction(sender, args, 1);
        if (faction == null) return true;
        List<String> names = faction.getFPlayers().stream()
                .sorted(Comparator.comparingInt((FPlayer player) -> player.getRole().value).reversed())
                .map(player -> player.getRole().nicename + ":" + player.getName())
                .toList();
        reply(sender, "Membres de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + " (" + names.size() + ") : " + String.join(", ", names));
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        FPlayer target = requirePlayer(sender, args, 1);
        if (target == null) return true;
        return forceRemove(sender, target, FPlayerLeaveEvent.PlayerLeaveReason.KICKED, "expulse");
    }

    private boolean join(CommandSender sender, String[] args) {
        FPlayer target = requirePlayer(sender, args, 1);
        Faction faction = requireFaction(sender, args, 2);
        if (target == null || faction == null) return true;
        if (target.getFaction() == faction) {
            reply(sender, ChatColor.RED + "Ce joueur appartient deja a cette faction.");
            return true;
        }
        if (target.hasFaction() && !forceRemove(sender, target, FPlayerLeaveEvent.PlayerLeaveReason.JOINOTHER, null)) return true;
        FPlayerJoinEvent event = new FPlayerJoinEvent(target, faction, FPlayerJoinEvent.PlayerJoinReason.COMMAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return cancelled(sender);
        target.setAlt(false);
        target.setFaction(faction, false);
        target.setRole(faction.getDefaultRole());
        faction.deinvite(target);
        log(faction, FLogType.INVITES, actor(sender), ChatColor.GREEN + "force-joined", target.getName());
        reply(sender, ChatColor.AQUA + target.getName() + ChatColor.GRAY + " a rejoint " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + ".");
        return true;
    }

    private boolean leave(CommandSender sender, String[] args) {
        FPlayer target = requirePlayer(sender, args, 1);
        if (target == null) return true;
        return forceRemove(sender, target, FPlayerLeaveEvent.PlayerLeaveReason.LEAVE, "retire");
    }

    private boolean money(CommandSender sender, String[] args) {
        if (args.length < 4) return usage(sender, "/fadmin money <set|add|remove> <faction> <montant>");
        Faction faction = requireFaction(sender, args, 2);
        if (faction == null) return true;
        double amount = parseNonNegative(args[3]);
        String action;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                faction.setFactionBalance(amount);
                action = "SET";
            }
            case "add" -> {
                faction.setFactionBalance(faction.getFactionBalance() + amount);
                action = "DEPOSITED";
            }
            case "remove" -> {
                if (amount > faction.getFactionBalance()) {
                    reply(sender, ChatColor.RED + "Solde insuffisant.");
                    return true;
                }
                faction.setFactionBalance(faction.getFactionBalance() - amount);
                action = "WITHDREW";
            }
            default -> { return usage(sender, "/fadmin money <set|add|remove> <faction> <montant>"); }
        }
        log(faction, FLogType.BANK_EDIT, actor(sender), action, format(amount));
        reply(sender, "Banque de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + " : " + format(faction.getFactionBalance()) + ".");
        return true;
    }

    private boolean inspect(CommandSender sender, String[] args) {
        FPlayer target = requirePlayer(sender, args, 1);
        if (target == null) return true;
        Faction faction = target.getFaction();
        reply(sender, ChatColor.AQUA + "Inspection de " + target.getName());
        sender.sendMessage(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + (target.hasFaction() ? faction.getTag() : "Aucune"));
        sender.sendMessage(ChatColor.GRAY + "Role: " + ChatColor.WHITE + (target.hasFaction() ? target.getRole().nicename : "Aucun"));
        sender.sendMessage(ChatColor.GRAY + "Puissance: " + ChatColor.WHITE + format(target.getPower()) + "/" + format(target.getPowerMax()));
        sender.sendMessage(ChatColor.GRAY + "Claims: " + ChatColor.WHITE + (target.hasFaction() ? faction.getLandRounded() : 0));
        sender.sendMessage(ChatColor.GRAY + "Banque: " + ChatColor.WHITE + (target.hasFaction() ? format(faction.getFactionBalance()) : "0"));
        return true;
    }

    private boolean logs(CommandSender sender, String[] args) {
        Faction faction = requireFaction(sender, args, 1);
        if (faction == null) return true;
        FactionLogs logs = plugin.getFlogManager().getFactionLogMap().get(faction.getId());
        if (logs == null || logs.isEmpty()) {
            reply(sender, "Aucun historique pour cette faction.");
            return true;
        }
        Set<FLogType> allowed = Set.of(FLogType.CHUNK_CLAIMS, FLogType.ROLE_PERM_EDIT, FLogType.RANK_EDIT, FLogType.INVITES, FLogType.BANK_EDIT);
        List<LogLine> lines = new ArrayList<>();
        logs.getMostRecentLogs().forEach((type, entries) -> {
            if (!allowed.contains(type)) return;
            for (FactionLogs.FactionLog entry : entries) {
                String line = entry.getLogLine(type, true);
                if (type != FLogType.INVITES || ChatColor.stripColor(line).toLowerCase(Locale.ROOT).contains("kick")) {
                    lines.add(new LogLine(entry.getTimestamp(), line));
                }
            }
        });
        lines.sort(Comparator.comparingLong(LogLine::timestamp).reversed());
        reply(sender, "Historique de " + ChatColor.AQUA + faction.getTag() + ChatColor.GRAY + " :");
        lines.stream().limit(20).forEach(line -> sender.sendMessage(ChatColor.DARK_GRAY + "- " + line.text()));
        if (lines.isEmpty()) sender.sendMessage(ChatColor.DARK_GRAY + "- Aucun evenement de moderation.");
        return true;
    }

    private boolean forceRemove(CommandSender sender, FPlayer target, FPlayerLeaveEvent.PlayerLeaveReason reason, String successVerb) {
        if (!target.hasFaction()) {
            reply(sender, ChatColor.RED + "Ce joueur n'a pas de faction.");
            return true;
        }
        Faction oldFaction = target.getFaction();
        boolean wasLeader = target.getRole() == Role.LEADER;
        FPlayerLeaveEvent event = new FPlayerLeaveEvent(target, oldFaction, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return cancelled(sender);
        log(oldFaction, FLogType.INVITES, actor(sender), ChatColor.RED + reason.name().toLowerCase(Locale.ROOT), target.getName());
        target.resetFactionData();
        if (wasLeader && Factions.getInstance().getFactionById(oldFaction.getId()) != null) oldFaction.promoteNewLeader();
        if (successVerb != null) reply(sender, ChatColor.AQUA + target.getName() + ChatColor.GRAY + " " + successVerb + " de sa faction.");
        return true;
    }

    private Faction requireFaction(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            reply(sender, ChatColor.RED + "Faction manquante.");
            return null;
        }
        Faction faction = Factions.getInstance().getByTag(args[index]);
        if (faction == null) faction = Factions.getInstance().getBestTagMatch(args[index]);
        if (faction == null || !faction.isNormal()) {
            reply(sender, ChatColor.RED + "Faction introuvable: " + args[index]);
            return null;
        }
        return faction;
    }

    private FPlayer requirePlayer(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            reply(sender, ChatColor.RED + "Joueur manquant.");
            return null;
        }
        for (FPlayer fPlayer : FPlayers.getInstance().getAllFPlayers()) {
            if (fPlayer.getName() != null && fPlayer.getName().equalsIgnoreCase(args[index])) return fPlayer;
        }
        Player online = Bukkit.getPlayerExact(args[index]);
        if (online != null) return FPlayers.getInstance().getByPlayer(online);
        reply(sender, ChatColor.RED + "Joueur introuvable: " + args[index]);
        return null;
    }

    private double parseNonNegative(String raw) {
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value) || value < 0) throw new NumberFormatException(raw);
        return value;
    }

    private void log(Faction faction, FLogType type, String... arguments) {
        plugin.logFactionEvent(faction, type, arguments);
    }

    private String actor(CommandSender sender) {
        return sender.getName();
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private boolean usage(CommandSender sender, String usage) {
        reply(sender, ChatColor.RED + "Usage: " + usage);
        return true;
    }

    private boolean cancelled(CommandSender sender) {
        reply(sender, ChatColor.RED + "Operation annulee par un autre plugin.");
        return true;
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + message);
    }

    private void sendHelp(CommandSender sender) {
        reply(sender, ChatColor.AQUA + "Commandes administrateur");
        for (String line : List.of(
                "/fadmin disband|rename|members|logs <faction>",
                "/fadmin leader|kick|leave|inspect <joueur>",
                "/fadmin join <joueur> <faction>",
                "/fadmin power|money <set|add|remove> <faction> <valeur>",
                "/fadmin claim <add|remove> <faction>",
                "/fadmin claims clear <faction>")) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("valoria.fadmin")) return List.of();
        if (args.length == 1) return filter(args[0], List.of("disband", "rename", "leader", "power", "claim", "claims", "members", "kick", "join", "leave", "money", "inspect", "logs"));
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (root.equals("power") || root.equals("money"))) return filter(args[1], List.of("set", "add", "remove"));
        if (args.length == 2 && root.equals("claim")) return filter(args[1], List.of("add", "remove"));
        if (args.length == 2 && root.equals("claims")) return filter(args[1], List.of("clear"));
        if ((root.equals("leader") || root.equals("kick") || root.equals("join") || root.equals("leave") || root.equals("inspect")) && args.length == 2) return filter(args[1], playerNames());
        int factionIndex = (root.equals("power") || root.equals("money") || root.equals("claim") || root.equals("claims") || root.equals("join")) ? 3 : 2;
        if (args.length == factionIndex) return filter(args[args.length - 1], factionNames());
        return List.of();
    }

    private List<String> playerNames() {
        Set<String> names = new LinkedHashSet<>();
        FPlayers.getInstance().getAllFPlayers().forEach(player -> names.add(player.getName()));
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
        return new ArrayList<>(names);
    }

    private List<String> factionNames() {
        return Factions.getInstance().getAllNormalFactions().stream().map(Faction::getTag).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private List<String> filter(String input, List<String> values) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private record LogLine(long timestamp, String text) {
    }
}
