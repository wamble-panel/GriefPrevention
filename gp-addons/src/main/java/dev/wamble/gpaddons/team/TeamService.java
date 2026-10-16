package dev.wamble.gpaddons.team;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Teams: creating, inviting, ranks and their permissions, and team trust in claims.
 * <p>
 * Team trust uses GriefPrevention's own permission trust: members whose rank has Team Claims get the
 * permission gpaddons.teammember.&lt;team&gt; while online, and a claim owner trusts
 * [gpaddons.teammember.&lt;team&gt;] like any [permission] group, so GriefPrevention does the checking.
 * <p>
 * Everyone can only act on members and ranks below their own; the leader is above every rank.
 */
public final class TeamService implements Listener {

    private static final String NODE_PREFIX = "gpaddons.teammember.";

    private final GPAddons plugin;
    private final TeamStore store;
    private final Map<UUID, Team> byMember = new HashMap<>();
    /** Pending invites: player, then team ID and when it runs out */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public TeamService(@NotNull GPAddons plugin) {
        this.plugin = plugin;
        this.store = new TeamStore(plugin);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("teams.enabled", true);
    }

    public void load() {
        store.load();
        index();
        store.startAutoSave();
    }

    public void saveNow() {
        store.saveNow();
    }

    private void index() {
        byMember.clear();
        for (Team team : store.teams().values()) {
            byMember.put(team.leader(), team);
            team.members().keySet().forEach(member -> byMember.put(member, team));
        }
    }

    // ---- Lookups ----

    @Nullable
    public Team teamOf(@NotNull UUID uuid) {
        return byMember.get(uuid);
    }

    @Nullable
    public Team byName(@NotNull String name) {
        final String wanted = TeamNames.plain(TeamNames.normalize(name)).toLowerCase(Locale.ROOT);
        for (Team team : store.teams().values()) {
            if (TeamNames.plain(team.name()).toLowerCase(Locale.ROOT).equals(wanted)) {
                return team;
            }
        }
        return null;
    }

    @NotNull
    public Collection<Team> teams() {
        return store.teams().values();
    }

    @Nullable
    public Rank rankByName(@NotNull Team team, @NotNull String name) {
        final String wanted = TeamNames.plain(TeamNames.normalize(name)).toLowerCase(Locale.ROOT);
        for (Rank rank : team.ranks()) {
            if (TeamNames.plain(rank.name()).toLowerCase(Locale.ROOT).equals(wanted)) {
                return rank;
            }
        }
        return null;
    }

    @NotNull
    public Component display(@NotNull Team team) {
        return TeamNames.team(team.name());
    }

    @NotNull
    public String plainName(@NotNull Team team) {
        return TeamNames.plain(team.name());
    }

    /** A member's rank, or "Leader" */
    @NotNull
    public Component rankDisplay(@NotNull Team team, @NotNull UUID uuid) {
        if (team.leader().equals(uuid)) {
            return plugin.messages().get("team.leader-rank");
        }
        final Rank rank = team.rankOf(uuid);
        return rank == null ? Component.empty() : TeamNames.rank(rank.name());
    }

    /** The trust ID to give a team in a claim */
    @NotNull
    public String trustId(@NotNull Team team) {
        return "[" + NODE_PREFIX + team.id() + "]";
    }

    /** The team a claim trust ID stands for, if it's a team's */
    @Nullable
    public Team teamOfTrustId(@NotNull String id) {
        final String prefix = "[" + NODE_PREFIX;
        if (!id.toLowerCase(Locale.ROOT).startsWith(prefix) || !id.endsWith("]")) {
            return null;
        }
        return store.teams().get(id.substring(prefix.length(), id.length() - 1));
    }

    // ---- Rules ----

    /** Whether the actor may do this to the target: they need the permission and a higher rank */
    public boolean canActOn(@NotNull Team team, @NotNull UUID actor, @NotNull UUID target, @NotNull TeamPermission permission) {
        return team.can(actor, permission) && team.position(actor) < team.position(target);
    }

    /** Whether the actor may change this rank: Manage Ranks, and the rank is below their own */
    public boolean canManageRank(@NotNull Team team, @NotNull UUID actor, @NotNull Rank rank) {
        return team.can(actor, TeamPermission.MANAGE_RANKS) && team.ranks().indexOf(rank) > team.position(actor);
    }

    public boolean canManageMember(@NotNull Team team, @NotNull UUID actor, @NotNull UUID target) {
        return canActOn(team, actor, target, TeamPermission.PROMOTE) || canActOn(team, actor, target, TeamPermission.KICK)
                || team.leader().equals(actor) && !actor.equals(target);
    }

    /** Check a typed name: valid characters and length, and colours only with permission. Null if not allowed */
    @Nullable
    private String checkName(@NotNull Player actor, @NotNull String input, int min, int max) {
        final String raw = TeamNames.normalize(input);
        if (TeamNames.isStyled(raw) && !actor.hasPermission("gpaddons.team.color")) {
            plugin.messages().send(actor, "team.no-color-permission");
            return null;
        }
        if (!TeamNames.valid(raw, min, max)) {
            plugin.messages().send(actor, "team.name-invalid",
                    Messages.text("min", Integer.toString(min)), Messages.text("max", Integer.toString(max)));
            return null;
        }
        return raw;
    }

    @Nullable
    private String checkTeamName(@NotNull Player actor, @NotNull String input, @Nullable Team except) {
        final String raw = checkName(actor, input,
                Math.max(1, plugin.getConfig().getInt("teams.name-length.min", 3)),
                Math.max(1, plugin.getConfig().getInt("teams.name-length.max", 16)));
        if (raw == null) {
            return null;
        }
        final Team existing = byName(raw);
        if (existing != null && existing != except) {
            plugin.messages().send(actor, "team.name-taken");
            return null;
        }
        return raw;
    }

    @Nullable
    private Team requireTeam(@NotNull Player player) {
        if (!enabled()) {
            plugin.messages().send(player, "team.disabled");
            return null;
        }
        final Team team = teamOf(player.getUniqueId());
        if (team == null) {
            plugin.messages().send(player, "team.no-team");
        }
        return team;
    }

    private boolean require(@NotNull Player player, @NotNull Team team, @NotNull TeamPermission permission) {
        if (team.can(player.getUniqueId(), permission)) {
            return true;
        }
        plugin.messages().send(player, "team.no-permission");
        return false;
    }

    @NotNull
    private TagResolver teamTag(@NotNull Team team) {
        return Messages.component("team", display(team));
    }

    @NotNull
    private static String newId(@NotNull Collection<String> taken) {
        String id;
        do {
            id = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000000, Integer.MAX_VALUE));
        } while (taken.contains(id));
        return id;
    }

    // ---- Creating and ending ----

    public boolean create(@NotNull Player player, @NotNull String input) {
        if (!enabled()) {
            plugin.messages().send(player, "team.disabled");
            return false;
        }
        if (!player.hasPermission("gpaddons.team.create")) {
            plugin.messages().send(player, "general.no-permission");
            return false;
        }
        if (teamOf(player.getUniqueId()) != null) {
            plugin.messages().send(player, "team.already-in-team");
            return false;
        }
        final String name = checkTeamName(player, input, null);
        if (name == null) {
            return false;
        }
        final Team team = new Team(newId(store.teams().keySet()), name, player.getUniqueId(), System.currentTimeMillis());
        team.ranks().add(defaultRank(team, "Officer", TeamPermission.INVITE, TeamPermission.KICK,
                TeamPermission.PROMOTE, TeamPermission.CLAIM_ACCESS));
        team.ranks().add(defaultRank(team, "Member", TeamPermission.CLAIM_ACCESS));
        team.ranks().add(defaultRank(team, "Recruit"));
        store.teams().put(team.id(), team);
        byMember.put(player.getUniqueId(), team);
        invites.remove(player.getUniqueId());
        store.changed();
        refresh(player);
        plugin.messages().send(player, "team.created", teamTag(team));
        return true;
    }

    @NotNull
    private static Rank defaultRank(@NotNull Team team, @NotNull String name, @NotNull TeamPermission... permissions) {
        final Rank rank = new Rank(newId(team.ranks().stream().map(Rank::id).toList()), name);
        rank.permissions().addAll(List.of(permissions));
        return rank;
    }

    /** End a team: its leader, or an admin */
    public boolean disband(@NotNull Player actor, @NotNull Team team) {
        if (!team.leader().equals(actor.getUniqueId()) && !actor.hasPermission("gpaddons.team.admin")) {
            plugin.messages().send(actor, "team.leader-only");
            return false;
        }
        broadcast(team, "team.disbanded", teamTag(team));
        if (!team.isMember(actor.getUniqueId())) {
            plugin.messages().send(actor, "team.disbanded", teamTag(team));
        }
        final List<UUID> everyone = new ArrayList<>(team.members().keySet());
        everyone.add(team.leader());
        store.teams().remove(team.id());
        everyone.forEach(byMember::remove);
        invites.values().forEach(pending -> pending.remove(team.id()));
        store.changed();
        everyone.forEach(this::refreshId);
        // Take the team's trust out of every claim
        final String trust = trustId(team).toLowerCase(Locale.ROOT);
        for (Claim claim : Gp.data().getClaims()) {
            dropTrust(claim, trust);
            claim.children.forEach(child -> dropTrust(child, trust));
        }
        return true;
    }

    private static void dropTrust(@NotNull Claim claim, @NotNull String trust) {
        if (Gp.trust(claim).containsKey(trust) || Gp.isManager(claim, trust)) {
            claim.dropPermission(trust);
            Gp.save(claim);
        }
    }

    // ---- Joining and leaving ----

    public boolean invite(@NotNull Player actor, @NotNull OfflinePlayer target) {
        final Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.INVITE)) {
            return false;
        }
        final String targetName = name(target);
        if (teamOf(target.getUniqueId()) != null) {
            plugin.messages().send(actor, "team.target-in-team", Messages.text("player", targetName));
            return false;
        }
        if (full(team)) {
            plugin.messages().send(actor, "team.full", Messages.text("max", Integer.toString(maxMembers())));
            return false;
        }
        final Map<String, Long> pending = invites.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>());
        pruneInvites(pending);
        if (pending.containsKey(team.id())) {
            plugin.messages().send(actor, "team.already-invited", Messages.text("player", targetName));
            return false;
        }
        pending.put(team.id(), System.currentTimeMillis()
                + Math.max(10, plugin.getConfig().getLong("teams.invite-expiry-seconds", 300)) * 1000L);
        plugin.messages().send(actor, "team.invited", Messages.text("player", targetName));
        final Player online = target.getPlayer();
        if (online != null) {
            sendInvite(online, team, actor.getName());
        }
        return true;
    }

    private void sendInvite(@NotNull Player player, @NotNull Team team, @NotNull String from) {
        final String plain = plainName(team);
        plugin.messages().send(player, "team.invite-received", Messages.text("player", from), teamTag(team),
                Messages.command("accept", "/team accept " + plain), Messages.command("deny", "/team deny " + plain));
    }

    private static void pruneInvites(@NotNull Map<String, Long> pending) {
        final long now = System.currentTimeMillis();
        pending.values().removeIf(expiry -> expiry < now);
    }

    /** Teams that invited this player and are still waiting */
    @NotNull
    public List<Team> invitesOf(@NotNull UUID player) {
        final Map<String, Long> pending = invites.get(player);
        if (pending == null) {
            return List.of();
        }
        pruneInvites(pending);
        return pending.keySet().stream().map(store.teams()::get).filter(t -> t != null).toList();
    }

    /** The invite to answer: the named team's, or the only one there is */
    @Nullable
    private Team pickInvite(@NotNull Player player, @Nullable String teamName) {
        final List<Team> pending = invitesOf(player.getUniqueId());
        Team team = null;
        if (teamName != null) {
            final Team named = byName(teamName);
            if (named != null && pending.contains(named)) {
                team = named;
            }
        } else if (!pending.isEmpty()) {
            team = pending.get(pending.size() - 1);
        }
        if (team == null) {
            plugin.messages().send(player, "team.no-invites");
        }
        return team;
    }

    public boolean accept(@NotNull Player player, @Nullable String teamName) {
        if (!enabled()) {
            plugin.messages().send(player, "team.disabled");
            return false;
        }
        if (teamOf(player.getUniqueId()) != null) {
            plugin.messages().send(player, "team.already-in-team");
            return false;
        }
        final Team team = pickInvite(player, teamName);
        if (team == null) {
            return false;
        }
        if (full(team)) {
            plugin.messages().send(player, "team.full", Messages.text("max", Integer.toString(maxMembers())));
            return false;
        }
        invites.remove(player.getUniqueId());
        team.members().put(player.getUniqueId(), team.lowestRank().id());
        byMember.put(player.getUniqueId(), team);
        store.changed();
        refresh(player);
        plugin.messages().send(player, "team.joined", teamTag(team));
        broadcast(team, "team.member-joined", player.getUniqueId(), Messages.text("player", player.getName()));
        return true;
    }

    public boolean deny(@NotNull Player player, @Nullable String teamName) {
        final Team team = pickInvite(player, teamName);
        if (team == null) {
            return false;
        }
        final Map<String, Long> pending = invites.get(player.getUniqueId());
        if (pending != null) {
            pending.remove(team.id());
        }
        plugin.messages().send(player, "team.denied", teamTag(team));
        return true;
    }

    public boolean leave(@NotNull Player player) {
        final Team team = requireTeam(player);
        if (team == null) {
            return false;
        }
        if (team.leader().equals(player.getUniqueId())) {
            if (team.size() == 1) {
                return disband(player, team);
            }
            plugin.messages().send(player, "team.leader-cant-leave");
            return false;
        }
        removeMember(team, player.getUniqueId());
        plugin.messages().send(player, "team.left", teamTag(team));
        broadcast(team, "team.member-left", Messages.text("player", player.getName()));
        return true;
    }

    public boolean kick(@NotNull Player actor, @NotNull UUID target) {
        final Team team = requireTeam(actor);
        if (team == null || !memberCheck(actor, team, target)) {
            return false;
        }
        if (!canActOn(team, actor.getUniqueId(), target, TeamPermission.KICK)) {
            sendDenied(actor, team, target, TeamPermission.KICK);
            return false;
        }
        removeMember(team, target);
        final String name = Gp.name(target);
        plugin.messages().send(actor, "team.kicked", Messages.text("player", name));
        broadcast(team, "team.member-kicked", actor.getUniqueId(), Messages.text("player", name));
        final Player online = plugin.getServer().getPlayer(target);
        if (online != null) {
            plugin.messages().send(online, "team.you-were-kicked", teamTag(team));
        }
        return true;
    }

    private void removeMember(@NotNull Team team, @NotNull UUID member) {
        team.members().remove(member);
        byMember.remove(member);
        store.changed();
        refreshId(member);
    }

    private boolean memberCheck(@NotNull Player actor, @NotNull Team team, @NotNull UUID target) {
        if (!team.isMember(target)) {
            plugin.messages().send(actor, "team.not-member", Messages.text("player", Gp.name(target)));
            return false;
        }
        return true;
    }

    private void sendDenied(@NotNull Player actor, @NotNull Team team, @NotNull UUID target, @NotNull TeamPermission permission) {
        if (!team.can(actor.getUniqueId(), permission)) {
            plugin.messages().send(actor, "team.no-permission");
        } else {
            plugin.messages().send(actor, "team.outranked", Messages.text("player", Gp.name(target)));
        }
    }

    private int maxMembers() {
        return plugin.getConfig().getInt("teams.max-members", 0);
    }

    private boolean full(@NotNull Team team) {
        return maxMembers() > 0 && team.size() >= maxMembers();
    }

    // ---- Ranks of members ----

    /** The rank a member would move to, one step up (-1) or down (+1), if the actor may do it */
    @Nullable
    public Rank step(@NotNull Team team, @NotNull UUID actor, @NotNull UUID target, int direction) {
        if (!canActOn(team, actor, target, TeamPermission.PROMOTE)) {
            return null;
        }
        final int next = team.position(target) + direction;
        if (next < 0 || next >= team.ranks().size() || next <= team.position(actor)) {
            return null;
        }
        return team.ranks().get(next);
    }

    public boolean promote(@NotNull Player actor, @NotNull UUID target) {
        return move(actor, target, -1);
    }

    public boolean demote(@NotNull Player actor, @NotNull UUID target) {
        return move(actor, target, 1);
    }

    private boolean move(@NotNull Player actor, @NotNull UUID target, int direction) {
        final Team team = requireTeam(actor);
        if (team == null || !memberCheck(actor, team, target)) {
            return false;
        }
        if (!canActOn(team, actor.getUniqueId(), target, TeamPermission.PROMOTE)) {
            sendDenied(actor, team, target, TeamPermission.PROMOTE);
            return false;
        }
        final Rank next = step(team, actor.getUniqueId(), target, direction);
        if (next == null) {
            plugin.messages().send(actor, direction < 0 ? "team.cant-promote" : "team.cant-demote",
                    Messages.text("player", Gp.name(target)));
            return false;
        }
        return applyRank(team, target, next);
    }

    public boolean setRank(@NotNull Player actor, @NotNull UUID target, @NotNull Rank rank) {
        final Team team = requireTeam(actor);
        if (team == null || !memberCheck(actor, team, target)) {
            return false;
        }
        if (!canActOn(team, actor.getUniqueId(), target, TeamPermission.PROMOTE)) {
            sendDenied(actor, team, target, TeamPermission.PROMOTE);
            return false;
        }
        if (team.ranks().indexOf(rank) <= team.position(actor.getUniqueId())) {
            plugin.messages().send(actor, "team.rank-too-high");
            return false;
        }
        return applyRank(team, target, rank);
    }

    private boolean applyRank(@NotNull Team team, @NotNull UUID target, @NotNull Rank rank) {
        team.members().put(target, rank.id());
        store.changed();
        refreshId(target);
        broadcast(team, "team.rank-changed", Messages.text("player", Gp.name(target)),
                Messages.component("rank", TeamNames.rank(rank.name())));
        return true;
    }

    /** Hand the team to another member; the old leader takes the top rank */
    public boolean transfer(@NotNull Player actor, @NotNull UUID target) {
        final Team team = requireTeam(actor);
        if (team == null || !memberCheck(actor, team, target)) {
            return false;
        }
        if (!team.leader().equals(actor.getUniqueId()) || target.equals(actor.getUniqueId())) {
            plugin.messages().send(actor, "team.leader-only");
            return false;
        }
        team.members().remove(target);
        team.members().put(actor.getUniqueId(), team.ranks().get(0).id());
        team.leader(target);
        store.changed();
        refreshId(actor.getUniqueId());
        refreshId(target);
        broadcast(team, "team.new-leader", Messages.text("player", Gp.name(target)));
        return true;
    }

    public boolean rename(@NotNull Player actor, @NotNull String input) {
        final Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.RENAME)) {
            return false;
        }
        final String name = checkTeamName(actor, input, team);
        if (name == null) {
            return false;
        }
        team.name(name);
        store.changed();
        broadcast(team, "team.renamed", teamTag(team));
        return true;
    }

    // ---- Ranks ----

    private int maxRanks() {
        return Math.max(1, plugin.getConfig().getInt("teams.max-ranks", 10));
    }

    @Nullable
    private String checkRankName(@NotNull Player actor, @NotNull Team team, @NotNull String input, @Nullable Rank except) {
        final String raw = checkName(actor, input, 1, 16);
        if (raw == null) {
            return null;
        }
        final Rank existing = rankByName(team, raw);
        if (existing != null && existing != except) {
            plugin.messages().send(actor, "team.rank-exists");
            return null;
        }
        return raw;
    }

    /** New ranks go just above the lowest, so the rank new members join at stays the same */
    public boolean createRank(@NotNull Player actor, @NotNull String input) {
        final Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.MANAGE_RANKS)) {
            return false;
        }
        if (team.ranks().size() >= maxRanks()) {
            plugin.messages().send(actor, "team.max-ranks", Messages.text("max", Integer.toString(maxRanks())));
            return false;
        }
        final String name = checkRankName(actor, team, input, null);
        if (name == null) {
            return false;
        }
        final Rank rank = new Rank(newId(team.ranks().stream().map(Rank::id).toList()), name);
        final int at = Math.max(team.ranks().size() - 1, team.position(actor.getUniqueId()) + 1);
        team.ranks().add(Math.min(at, team.ranks().size()), rank);
        store.changed();
        plugin.messages().send(actor, "team.rank-created", Messages.component("rank", TeamNames.rank(name)));
        return true;
    }

    public boolean deleteRank(@NotNull Player actor, @NotNull Rank rank) {
        final Team team = requireTeam(actor);
        if (team == null || !rankCheck(actor, team, rank)) {
            return false;
        }
        if (team.ranks().size() <= 1) {
            plugin.messages().send(actor, "team.rank-last");
            return false;
        }
        final int index = team.ranks().indexOf(rank);
        final Rank fallback = index + 1 < team.ranks().size() ? team.ranks().get(index + 1) : team.ranks().get(index - 1);
        team.ranks().remove(rank);
        team.members().replaceAll((member, rankId) -> rankId.equals(rank.id()) ? fallback.id() : rankId);
        store.changed();
        refreshTeam(team);
        plugin.messages().send(actor, "team.rank-deleted", Messages.component("rank", TeamNames.rank(rank.name())));
        return true;
    }

    public boolean renameRank(@NotNull Player actor, @NotNull Rank rank, @NotNull String input) {
        final Team team = requireTeam(actor);
        if (team == null || !rankCheck(actor, team, rank)) {
            return false;
        }
        final String name = checkRankName(actor, team, input, rank);
        if (name == null) {
            return false;
        }
        rank.name(name);
        store.changed();
        plugin.messages().send(actor, "team.rank-renamed", Messages.component("rank", TeamNames.rank(name)));
        return true;
    }

    /** Swap a rank with the one above (-1) or below (+1); both must be below the actor */
    public boolean moveRank(@NotNull Player actor, @NotNull Rank rank, int direction) {
        final Team team = requireTeam(actor);
        if (team == null || !rankCheck(actor, team, rank)) {
            return false;
        }
        final int index = team.ranks().indexOf(rank);
        final int other = index + direction;
        if (other < 0 || other >= team.ranks().size() || other <= team.position(actor.getUniqueId())) {
            plugin.messages().send(actor, "team.rank-too-high");
            return false;
        }
        team.ranks().set(index, team.ranks().get(other));
        team.ranks().set(other, rank);
        store.changed();
        plugin.messages().send(actor, "team.rank-moved", Messages.component("rank", TeamNames.rank(rank.name())));
        return true;
    }

    /** Turn a rank permission on or off; nobody can hand out a permission they don't have */
    public boolean togglePermission(@NotNull Player actor, @NotNull Rank rank, @NotNull TeamPermission permission,
                                    @Nullable Boolean value) {
        final Team team = requireTeam(actor);
        if (team == null || !rankCheck(actor, team, rank)) {
            return false;
        }
        if (!team.can(actor.getUniqueId(), permission)) {
            plugin.messages().send(actor, "team.no-permission");
            return false;
        }
        final boolean on = value != null ? value : !rank.permissions().contains(permission);
        if (on) {
            rank.permissions().add(permission);
        } else {
            rank.permissions().remove(permission);
        }
        store.changed();
        if (permission == TeamPermission.CLAIM_ACCESS) {
            refreshTeam(team);
        }
        plugin.messages().send(actor, "team.perm-set", Messages.component("rank", TeamNames.rank(rank.name())),
                plugin.messages().formatted("state", on ? "team.perm-on" : "team.perm-off"),
                plugin.messages().formatted("perm", "team-perms." + permission.id() + ".name"));
        return true;
    }

    private boolean rankCheck(@NotNull Player actor, @NotNull Team team, @NotNull Rank rank) {
        if (!require(actor, team, TeamPermission.MANAGE_RANKS)) {
            return false;
        }
        if (!team.ranks().contains(rank) || !canManageRank(team, actor.getUniqueId(), rank)) {
            plugin.messages().send(actor, "team.rank-too-high");
            return false;
        }
        return true;
    }

    // ---- Messages ----

    private void broadcast(@NotNull Team team, @NotNull String key, @NotNull TagResolver... placeholders) {
        broadcast(team, key, (UUID) null, placeholders);
    }

    /** Tell every online member, except one (who was told already) */
    private void broadcast(@NotNull Team team, @NotNull String key, @Nullable UUID except, @NotNull TagResolver... placeholders) {
        final List<UUID> everyone = new ArrayList<>(team.members().keySet());
        everyone.add(team.leader());
        for (UUID uuid : everyone) {
            final Player online = plugin.getServer().getPlayer(uuid);
            if (online != null && !uuid.equals(except)) {
                plugin.messages().send(online, key, placeholders);
            }
        }
    }

    @NotNull
    private static String name(@NotNull OfflinePlayer player) {
        return player.getName() == null ? Gp.name(player.getUniqueId()) : player.getName();
    }

    // ---- Team claim trust (permission attachments) ----

    private void refreshId(@NotNull UUID uuid) {
        final Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            refresh(online);
        }
    }

    public void refreshTeam(@NotNull Team team) {
        refreshId(team.leader());
        team.members().keySet().forEach(this::refreshId);
    }

    /** Give the player their team's claim permission if their rank has Team Claims, and take any other away */
    public void refresh(@NotNull Player player) {
        final PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // Already gone
            }
        }
        final Team team = enabled() ? teamOf(player.getUniqueId()) : null;
        if (team != null && team.can(player.getUniqueId(), TeamPermission.CLAIM_ACCESS)) {
            final PermissionAttachment attachment = player.addAttachment(plugin);
            attachment.setPermission(NODE_PREFIX + team.id(), true);
            attachments.put(player.getUniqueId(), attachment);
        }
    }

    public void refreshAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    public void clearAll() {
        attachments.forEach((uuid, attachment) -> {
            final Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                try {
                    online.removeAttachment(attachment);
                } catch (IllegalArgumentException ignored) {
                    // Already gone
                }
            }
        });
        attachments.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        refresh(event.getPlayer());
        if (enabled() && teamOf(event.getPlayer().getUniqueId()) == null) {
            for (Team team : invitesOf(event.getPlayer().getUniqueId())) {
                sendInvite(event.getPlayer(), team, Gp.name(team.leader()));
            }
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
    }

}
