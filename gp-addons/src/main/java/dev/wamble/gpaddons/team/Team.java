package dev.wamble.gpaddons.team;

import dev.wamble.gpaddons.data.ClaimData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A team: a leader, members with ranks, and the ranks themselves (highest first).
 */
public final class Team {

    private final String id;
    private String name;
    private UUID leader;
    private final long created;
    /** Members other than the leader, with their rank IDs */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final List<Rank> ranks = new ArrayList<>();
    @Nullable
    private ClaimData.Home home;
    private int points;
    private final Set<String> completed = new LinkedHashSet<>();
    private final Map<String, Long> progress = new HashMap<>();
    private final Map<String, Integer> upgrades = new HashMap<>();
    /** The shared vault's slots (up to three rows) */
    private final ItemStack[] vault = new ItemStack[27];

    public Team(@NotNull String id, @NotNull String name, @NotNull UUID leader, long created) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.created = created;
    }

    @NotNull
    public String id() {
        return id;
    }

    /** The name as typed, possibly with colour tags */
    @NotNull
    public String name() {
        return name;
    }

    public void name(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    public UUID leader() {
        return leader;
    }

    public void leader(@NotNull UUID leader) {
        this.leader = leader;
    }

    public long created() {
        return created;
    }

    @NotNull
    public Map<UUID, String> members() {
        return members;
    }

    @NotNull
    public List<Rank> ranks() {
        return ranks;
    }

    public boolean isMember(@NotNull UUID uuid) {
        return leader.equals(uuid) || members.containsKey(uuid);
    }

    /** Everyone, leader included */
    public int size() {
        return members.size() + 1;
    }

    @Nullable
    public ClaimData.Home home() {
        return home;
    }

    public void home(@Nullable ClaimData.Home home) {
        this.home = home;
    }

    /** Team points, earned from quests and spent on upgrades */
    public int points() {
        return points;
    }

    public void points(int points) {
        this.points = Math.max(0, points);
    }

    /** IDs of finished quests */
    @NotNull
    public Set<String> completed() {
        return completed;
    }

    /** Progress towards unfinished quests, by quest ID */
    @NotNull
    public Map<String, Long> progress() {
        return progress;
    }

    /** Upgrade levels by upgrade ID; missing means level 0 */
    @NotNull
    public Map<String, Integer> upgrades() {
        return upgrades;
    }

    public int level(@NotNull TeamUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade.id(), 0);
    }

    @NotNull
    public ItemStack[] vault() {
        return vault;
    }

    @Nullable
    public Rank rank(@NotNull String id) {
        for (Rank rank : ranks) {
            if (rank.id().equals(id)) {
                return rank;
            }
        }
        return null;
    }

    /** A member's rank, or null for the leader (or a non-member) */
    @Nullable
    public Rank rankOf(@NotNull UUID uuid) {
        final String id = members.get(uuid);
        return id == null ? null : rank(id);
    }

    /** 0 for the top rank, counting down; the leader is -1 and non-members are last */
    public int position(@NotNull UUID uuid) {
        if (leader.equals(uuid)) {
            return -1;
        }
        final Rank rank = rankOf(uuid);
        return rank == null ? Integer.MAX_VALUE : ranks.indexOf(rank);
    }

    public boolean can(@NotNull UUID uuid, @NotNull TeamPermission permission) {
        if (leader.equals(uuid)) {
            return true;
        }
        final Rank rank = rankOf(uuid);
        return rank != null && rank.permissions().contains(permission);
    }

    @NotNull
    public Rank lowestRank() {
        return ranks.get(ranks.size() - 1);
    }

}
