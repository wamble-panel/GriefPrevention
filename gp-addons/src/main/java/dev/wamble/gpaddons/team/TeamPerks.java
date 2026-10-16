package dev.wamble.gpaddons.team;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.data.ClaimData;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.events.AccrueClaimBlocksEvent;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Team quests and upgrades: counting progress from what players already do, and the upgrades' effects.
 * Everything here is a quick check on an event that happens anyway, plus one light check every 10 seconds
 * for the "be somewhere together" quests.
 */
public final class TeamPerks implements Listener {

    private static final Set<String> CROPS = Set.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART",
            "COCOA", "TORCHFLOWER_CROP", "PITCHER_CROP");
    private static final Set<String> SPAWNED_BY_PLAYERS = Set.of("SPAWNER", "SPAWNER_EGG", "EGG", "DISPENSE_EGG",
            "TRIAL_SPAWNER", "COMMAND", "CUSTOM", "BUILD_WITHER", "BUILD_IRONGOLEM", "BUILD_SNOWMAN", "SLIME_SPLIT");
    private static final int NEARBY = 24;
    private static final int TOGETHER = 48;
    private static final int AT_HOME = 8;
    private static final int PLACED_MEMORY = 100_000;

    private final GPAddons plugin;
    /** Recently placed blocks, so placing and breaking the same block can't farm quests */
    private final Map<String, Boolean> placed = new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > PLACED_MEMORY;
        }
    };

    public TeamPerks(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    private TeamService teams() {
        return plugin.teams();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkGatherings, 200L, 200L);
    }

    // ---- Quests ----

    public int goal(@NotNull TeamQuest quest) {
        return Math.max(1, plugin.getConfig().getInt("teams.quests." + quest.id() + ".goal", quest.defaultGoal()));
    }

    public int reward(@NotNull TeamQuest quest) {
        return Math.max(0, plugin.getConfig().getInt("teams.quests." + quest.id() + ".points", 1));
    }

    public long progress(@NotNull Team team, @NotNull TeamQuest quest) {
        return team.completed().contains(quest.id()) ? goal(quest) : team.progress().getOrDefault(quest.id(), 0L);
    }

    /** Add to a counting quest (can be negative) */
    public void add(@Nullable Team team, @NotNull TeamQuest quest, long amount) {
        if (team == null || !teams().enabled() || team.completed().contains(quest.id())) {
            return;
        }
        final long value = Math.max(0, team.progress().getOrDefault(quest.id(), 0L) + amount);
        if (value >= goal(quest)) {
            complete(team, quest);
        } else {
            team.progress().put(quest.id(), value);
            teams().changed();
        }
    }

    /** Record a "how many at once" quest's best so far */
    public void reach(@Nullable Team team, @NotNull TeamQuest quest, long value) {
        if (team == null || !teams().enabled() || team.completed().contains(quest.id())) {
            return;
        }
        if (value >= goal(quest)) {
            complete(team, quest);
        } else if (value > team.progress().getOrDefault(quest.id(), 0L)) {
            team.progress().put(quest.id(), value);
            teams().changed();
        }
    }

    public void complete(@Nullable Team team, @NotNull TeamQuest quest) {
        if (team == null || !teams().enabled() || !team.completed().add(quest.id())) {
            return;
        }
        team.progress().remove(quest.id());
        team.points(team.points() + reward(quest));
        teams().changed();
        teams().broadcast(team, "team.quest-complete",
                plugin.messages().formatted("quest", "team-quests." + quest.id() + ".name"),
                Messages.text("points", Integer.toString(reward(quest))));
        for (Player member : teams().online(team)) {
            member.playSound(member.getLocation(), "minecraft:ui.toast.challenge_complete", 0.6F, 1.0F);
        }
    }

    // ---- Upgrades ----

    public int cost(@NotNull TeamUpgrade upgrade, int level) {
        final List<Integer> costs = plugin.getConfig().getIntegerList("teams.upgrades." + upgrade.id() + ".costs");
        return costs.size() >= level && level > 0 ? Math.max(0, costs.get(level - 1)) : upgrade.defaultCost(level);
    }

    public boolean buy(@NotNull Player player, @NotNull TeamUpgrade upgrade) {
        final Team team = teams().teamOf(player.getUniqueId());
        if (team == null || !teams().enabled()) {
            plugin.messages().send(player, team == null ? "team.no-team" : "team.disabled");
            return false;
        }
        if (!team.can(player.getUniqueId(), TeamPermission.SPEND_POINTS)) {
            plugin.messages().send(player, "team.no-permission");
            return false;
        }
        final int level = team.level(upgrade);
        if (level >= upgrade.maxLevel()) {
            plugin.messages().send(player, "team.upgrade-maxed");
            return false;
        }
        final int cost = cost(upgrade, level + 1);
        if (team.points() < cost) {
            plugin.messages().send(player, "team.not-enough-points", Messages.text("cost", Integer.toString(cost)),
                    Messages.text("points", Integer.toString(team.points())));
            return false;
        }
        team.points(team.points() - cost);
        team.upgrades().put(upgrade.id(), level + 1);
        teams().changed();
        teams().broadcast(team, "team.upgrade-bought", Messages.text("player", player.getName()),
                plugin.messages().formatted("upgrade", "team-upgrades." + upgrade.id() + ".name"),
                Messages.text("level", Integer.toString(level + 1)));
        for (Player member : teams().online(team)) {
            member.playSound(member.getLocation(), "minecraft:entity.player.levelup", 0.6F, 1.2F);
        }
        return true;
    }

    /** The team whose member owns the claim here, if any (not for admin claims) */
    @Nullable
    private Team claimTeam(@NotNull Location location) {
        final Claim claim = Gp.claimAt(location, null);
        if (claim == null || claim.isAdminClaim()) {
            return null;
        }
        final UUID owner = Gp.top(claim).getOwnerID();
        return owner == null ? null : teams().teamOf(owner);
    }

    /** The player's team, if they're in one of its claims */
    @Nullable
    private Team inOwnTeamClaim(@NotNull Player player, @NotNull Location location) {
        final Team team = teams().teamOf(player.getUniqueId());
        return team != null && claimTeam(location) == team ? team : null;
    }

    /** Whether a teammate is close by */
    private boolean teammateNear(@NotNull Player player, @NotNull Team team, int distance) {
        final double squared = (double) distance * distance;
        for (Player other : teams().online(team)) {
            if (other != player && other.getWorld() == player.getWorld()
                    && other.getLocation().distanceSquared(player.getLocation()) <= squared) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static String key(@NotNull Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    // ---- Quest progress from play ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        placed.put(key(event.getBlockPlaced()), Boolean.TRUE);
        add(inOwnTeamClaim(event.getPlayer(), event.getBlockPlaced().getLocation()), TeamQuest.MASTER_BUILDERS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        final Block block = event.getBlock();
        final boolean wasPlaced = placed.remove(key(block)) != null;
        final Team team = teams().teamOf(event.getPlayer().getUniqueId());
        if (team == null) {
            return;
        }
        final String type = block.getType().name();
        final Team claimTeam = claimTeam(block.getLocation());
        if (wasPlaced && claimTeam == team) {
            add(team, TeamQuest.MASTER_BUILDERS, -1);
        }
        if (!wasPlaced && (type.endsWith("_ORE") || type.equals("ANCIENT_DEBRIS"))) {
            add(team, TeamQuest.DEEP_DELVERS, 1);
        }
        if (claimTeam == team && CROPS.contains(type) && block.getBlockData() instanceof Ageable crop
                && crop.getAge() >= crop.getMaximumAge()) {
            add(team, TeamQuest.HARVEST_FESTIVAL, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(@NotNull EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Enemy)
                || SPAWNED_BY_PLAYERS.contains(entity.getEntitySpawnReason().name())) {
            return;
        }
        final Team team = teams().teamOf(killer.getUniqueId());
        if (team != null && !team.completed().contains(TeamQuest.STRENGTH_IN_NUMBERS.id())
                && teammateNear(killer, team, NEARBY)) {
            add(team, TeamQuest.STRENGTH_IN_NUMBERS, 1);
        }
    }

    // Trusting the team with build (or manager) rights in one of the team's claims
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrust(@NotNull TrustChangedEvent event) {
        if (!event.isGiven()) {
            return;
        }
        final Team team = teams().teamOfTrustId(event.getIdentifier());
        final ClaimPermission level = event.getClaimPermission();
        if (team == null || level != null && level != ClaimPermission.Build) {
            return;
        }
        for (Claim claim : event.getClaims()) {
            final UUID owner = Gp.top(claim).getOwnerID();
            if (owner != null && team.isMember(owner)) {
                complete(team, TeamQuest.SHARED_GROUND);
                return;
            }
        }
    }

    /** Every 10 seconds: crew size, exploring the Nether and End together, and gathering at home */
    private void checkGatherings() {
        if (!teams().enabled()) {
            return;
        }
        for (Team team : teams().teams()) {
            reach(team, TeamQuest.GATHERING_THE_CREW, team.size());
            final List<Player> online = teams().online(team);
            if (online.size() < 2) {
                continue;
            }
            reach(team, TeamQuest.INTO_THE_UNKNOWN, largestGroup(online, "NETHER"));
            reach(team, TeamQuest.THE_FINAL_FRONTIER, largestGroup(online, "THE_END"));
            final ClaimData.Home home = team.home();
            if (home != null && !team.completed().contains(TeamQuest.FAMILY_REUNION.id())) {
                int atHome = 0;
                for (Player player : online) {
                    final Location at = player.getLocation();
                    if (at.getWorld() != null && at.getWorld().getName().equals(home.world())
                            && square(at.getX() - home.x()) + square(at.getY() - home.y()) + square(at.getZ() - home.z())
                            <= AT_HOME * AT_HOME) {
                        atHome++;
                    }
                }
                reach(team, TeamQuest.FAMILY_REUNION, atHome);
            }
        }
    }

    private static double square(double value) {
        return value * value;
    }

    /** The most members standing close together in a dimension */
    private static int largestGroup(@NotNull List<Player> online, @NotNull String environment) {
        final List<Player> there = new ArrayList<>();
        for (Player player : online) {
            if (player.getWorld().getEnvironment().name().equals(environment)) {
                there.add(player);
            }
        }
        int best = 0;
        for (Player player : there) {
            int near = 0;
            for (Player other : there) {
                if (other.getWorld() == player.getWorld()
                        && other.getLocation().distanceSquared(player.getLocation()) <= TOGETHER * TOGETHER) {
                    near++;
                }
            }
            best = Math.max(best, near);
        }
        return best;
    }

    // ---- Upgrade effects ----

    // Bountiful Harvest: a chance for crops in team claims to grow an extra stage
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(@NotNull BlockGrowEvent event) {
        final BlockData grown = event.getNewState().getBlockData();
        if (!(grown instanceof Ageable crop) || crop.getAge() >= crop.getMaximumAge() || !teams().enabled()) {
            return;
        }
        final Team team = claimTeam(event.getBlock().getLocation());
        final int level = team == null ? 0 : team.level(TeamUpgrade.BOUNTIFUL_HARVEST);
        if (level == 0 || ThreadLocalRandom.current().nextDouble() >= new double[]{0.25, 0.5, 1.0}[Math.min(level, 3) - 1]) {
            return;
        }
        final Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() == grown.getMaterial() && block.getBlockData() instanceof Ageable now
                    && now.getAge() < now.getMaximumAge()) {
                now.setAge(now.getAge() + 1);
                block.setBlockData(now);
            }
        });
    }

    // Animal Husbandry: babies bred in team claims grow up sooner
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(@NotNull EntityBreedEvent event) {
        if (!teams().enabled() || !(event.getEntity() instanceof org.bukkit.entity.Ageable baby)) {
            return;
        }
        final Team team = claimTeam(baby.getLocation());
        final int level = team == null ? 0 : team.level(TeamUpgrade.ANIMAL_HUSBANDRY);
        if (level > 0 && baby.getAge() < 0) {
            baby.setAge((int) (baby.getAge() * (1 - new double[]{0.25, 0.5, 0.75}[Math.min(level, 3) - 1])));
        }
    }

    // Land Grant: members earn claim blocks faster
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAccrue(@NotNull AccrueClaimBlocksEvent event) {
        final Team team = teams().enabled() ? teams().teamOf(event.getPlayer().getUniqueId()) : null;
        final int level = team == null ? 0 : team.level(TeamUpgrade.LAND_GRANT);
        if (level > 0) {
            event.setBlocksToAccrue((int) Math.round(event.getBlocksToAccrue()
                    * (1 + new double[]{0.15, 0.30, 0.50}[Math.min(level, 3) - 1])));
        }
    }

    // Wisdom: more XP with a teammate nearby
    @EventHandler(priority = EventPriority.HIGH)
    public void onExp(@NotNull PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0 || !teams().enabled()) {
            return;
        }
        final Team team = teams().teamOf(event.getPlayer().getUniqueId());
        final int level = team == null ? 0 : team.level(TeamUpgrade.WISDOM);
        if (level > 0 && teammateNear(event.getPlayer(), team, NEARBY)) {
            final double boosted = event.getAmount() * (1 + 0.1 * Math.min(level, 3));
            final int whole = (int) boosted;
            event.setAmount(whole + (ThreadLocalRandom.current().nextDouble() < boosted - whole ? 1 : 0));
        }
    }

    /** Whether hostile mobs may be allowed to spawn in this claim: its owner's team needs the Monster Lure */
    public boolean monsterSpawningUnlocked(@NotNull Claim claim) {
        if (!teams().enabled() || !plugin.getConfig().getBoolean("teams.upgrades.monster_lure.required", true)) {
            return true;
        }
        final Claim top = Gp.top(claim);
        if (top.isAdminClaim() || top.getOwnerID() == null) {
            return true;
        }
        final Team team = teams().teamOf(top.getOwnerID());
        return team != null && team.level(TeamUpgrade.MONSTER_LURE) > 0;
    }

}
