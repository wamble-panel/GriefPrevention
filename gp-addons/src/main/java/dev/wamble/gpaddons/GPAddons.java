package dev.wamble.gpaddons;

import dev.wamble.gpaddons.command.Commands;
import dev.wamble.gpaddons.command.GpCommandMenus;
import dev.wamble.gpaddons.command.TeamCommand;
import dev.wamble.gpaddons.data.BanFromClaimImporter;
import dev.wamble.gpaddons.data.ClaimStore;
import dev.wamble.gpaddons.entry.ClaimNoticeListener;
import dev.wamble.gpaddons.entry.ClaimTeleports;
import dev.wamble.gpaddons.entry.EntryListener;
import dev.wamble.gpaddons.entry.EntryService;
import dev.wamble.gpaddons.flag.FlagListener;
import dev.wamble.gpaddons.flag.FlagService;
import dev.wamble.gpaddons.menu.ClaimActions;
import dev.wamble.gpaddons.menu.ClaimInfo;
import dev.wamble.gpaddons.menu.Menu;
import dev.wamble.gpaddons.menu.MenuManager;
import dev.wamble.gpaddons.outline.OutlineListener;
import dev.wamble.gpaddons.protect.CushionListener;
import dev.wamble.gpaddons.protect.DoorListener;
import dev.wamble.gpaddons.protect.RegionClaimGuard;
import dev.wamble.gpaddons.protect.SpongeListener;
import dev.wamble.gpaddons.team.TeamPerks;
import dev.wamble.gpaddons.team.TeamService;
import dev.wamble.gpaddons.team.TeamStorage;
import dev.wamble.gpaddons.text.GpMessages;
import dev.wamble.gpaddons.text.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * GPAddons: claim menus, HuskClaims-style flags, claim bans and an entry whitelist on top of GriefPrevention.
 * GriefPrevention keeps claims and trust; GPAddons keeps its own extras in data.yml, keyed by claim ID.
 */
public final class GPAddons extends JavaPlugin {

    private final ClaimStore store = new ClaimStore(this);
    private final Messages messages = new Messages(this);
    private final FlagService flags = new FlagService(this);
    private final EntryService entry = new EntryService(this);
    private final ClaimInfo info = new ClaimInfo(this);
    private final ClaimActions actions = new ClaimActions(this);
    private final MenuManager menus = new MenuManager(this);
    private final GpMessages gpMessages = new GpMessages(this);
    private final ClaimTeleports teleports = new ClaimTeleports(this);
    private final TeamService teams = new TeamService(this);
    private final TeamPerks perks = new TeamPerks(this);
    private final TeamStorage storage = new TeamStorage(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        // Add settings from newer versions to an existing config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();
        messages.reload();
        flags.reload();
        store.load();
        store.startAutoSave();
        teams.load();
        new BanFromClaimImporter(this).importIfNeeded();

        final PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new FlagListener(this), this);
        plugins.registerEvents(new EntryListener(this), this);
        plugins.registerEvents(new ClaimNoticeListener(this), this);
        plugins.registerEvents(teleports, this);
        plugins.registerEvents(teams, this);
        plugins.registerEvents(perks, this);
        plugins.registerEvents(storage, this);
        perks.start();
        teams.refreshAll();
        plugins.registerEvents(new SpongeListener(this), this);
        plugins.registerEvents(new CushionListener(), this);
        plugins.registerEvents(new DoorListener(this), this);
        RegionClaimGuard.register(this);
        plugins.registerEvents(menus, this);
        plugins.registerEvents(new GpCommandMenus(this), this);
        if (hasOutlineEvent()) {
            plugins.registerEvents(new OutlineListener(this), this);
        } else {
            getLogger().warning("This GriefPrevention version can't have its claim outline changed; the raised outline is off");
        }
        new Commands(this).register();
        new TeamCommand(this).register();

        if (getConfig().getBoolean("griefprevention-messages.restyle", true)) {
            try {
                getLogger().info(gpMessages.restyle(false));
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "Couldn't restyle GriefPrevention's messages", e);
            }
        }

        try {
            flags.applyAllExplosions();
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Couldn't apply explosion flags to claims", e);
        }
    }

    @Override
    public void onDisable() {
        // Nothing may be taken out of a menu once it stops responding to clicks
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        storage.closeAll();
        store.saveNow();
        teams.clearAll();
        teams.saveNow();
    }

    /** Re-read config.yml and messages.yml */
    public void reload() {
        reloadConfig();
        messages.reload();
        flags.reload();
        flags.applyAllExplosions();
        teams.refreshAll();
        perks.updateZones();
    }

    /**
     * One-off changes to an existing config.yml. Version 2: hostile mobs no longer spawn in claims by default
     * (owners unlock it with the Monster Lure team upgrade). Version 3: claims glow light blue instead of pink,
     * and the Team Vault upgrade is now Team Storage.
     */
    private void migrateConfig() {
        final int version = getConfig().getInt("config-version", 1);
        if (version < 2) {
            getConfig().set("flags.defaults.monster_spawn", false);
        }
        if (version < 3) {
            // Only colours still at the old defaults; ones the owner picked stay
            if ("#FFC8DD".equalsIgnoreCase(getConfig().getString("outline.glow-colors.claim", ""))) {
                getConfig().set("outline.glow-colors.claim", "#A2D2FF");
            }
            if ("#A2D2FF".equalsIgnoreCase(getConfig().getString("outline.glow-colors.other", ""))) {
                getConfig().set("outline.glow-colors.other", "#F2F2F2");
            }
            if (getConfig().isConfigurationSection("teams.upgrades.team_vault")) {
                getConfig().set("teams.upgrades.team_storage.costs",
                        getConfig().getIntegerList("teams.upgrades.team_vault.costs"));
                getConfig().set("teams.upgrades.team_vault", null);
            }
        }
        getConfig().set("config-version", 3);
        saveConfig();
    }

    private static boolean hasOutlineEvent() {
        try {
            Class.forName("com.griefprevention.events.BoundaryVisualizationEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @NotNull
    public ClaimStore store() {
        return store;
    }

    @NotNull
    public Messages messages() {
        return messages;
    }

    @NotNull
    public FlagService flags() {
        return flags;
    }

    @NotNull
    public EntryService entry() {
        return entry;
    }

    @NotNull
    public ClaimInfo info() {
        return info;
    }

    @NotNull
    public ClaimActions actions() {
        return actions;
    }

    @NotNull
    public MenuManager menus() {
        return menus;
    }

    @NotNull
    public TeamPerks perks() {
        return perks;
    }

    @NotNull
    public TeamStorage storage() {
        return storage;
    }

    @NotNull
    public TeamService teams() {
        return teams;
    }

    @NotNull
    public ClaimTeleports teleports() {
        return teleports;
    }

    @NotNull
    public GpMessages gpMessages() {
        return gpMessages;
    }

}
