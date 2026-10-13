package dev.wamble.gpaddons.data;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.util.Gp;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * Copies bans from the BanFromClaim plugin (plugins/BanFromClaim/data.dat). Its file is only read, never changed.
 * Bans are recorded as made by the claim's owner, and its "ban everyone" claims get the entry whitelist.
 */
public final class BanFromClaimImporter {

    private final GPAddons plugin;

    public BanFromClaimImporter(@NotNull GPAddons plugin) {
        this.plugin = plugin;
    }

    /** Import on first start, if enabled and the file exists */
    public void importIfNeeded() {
        if (plugin.getConfig().getBoolean("bans.import-banfromclaim", true) && !plugin.store().importedBanFromClaim()) {
            run();
        }
    }

    /** @return a short summary of what was imported */
    @NotNull
    public String run() {
        final File file = new File(plugin.getDataFolder().getParentFile(), "BanFromClaim/data.dat");
        if (!file.exists()) {
            plugin.store().importedBanFromClaim(true);
            return "No BanFromClaim data found at " + file.getPath();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int bans = 0;
        int skipped = 0;
        int whitelisted = 0;
        final ConfigurationSection claims = yaml.getConfigurationSection("bfc_claim_data");
        if (claims != null) {
            for (String id : claims.getKeys(false)) {
                final Claim claim = claim(id);
                if (claim == null) {
                    skipped += claims.getStringList(id).size();
                    continue;
                }
                for (String value : claims.getStringList(id)) {
                    final UUID banned = uuid(value);
                    if (banned == null || Gp.isOwner(claim, banned)) {
                        skipped++;
                        continue;
                    }
                    final ClaimData data = plugin.store().edit(claim);
                    if (data.bans().putIfAbsent(banned, new ClaimData.Ban(claim.getOwnerID(), System.currentTimeMillis())) == null) {
                        claim.dropPermission(banned.toString());
                        bans++;
                    }
                }
                Gp.save(claim);
            }
        }
        // "Ban everyone" claims, stored as a section or a list of claim IDs
        final List<String> banAll = yaml.isConfigurationSection("claims-ban-all")
                ? yaml.getConfigurationSection("claims-ban-all").getKeys(false).stream()
                .filter(id -> yaml.getBoolean("claims-ban-all." + id, true)).toList()
                : yaml.getStringList("claims-ban-all");
        for (String id : banAll) {
            final Claim claim = claim(id);
            if (claim != null) {
                plugin.store().edit(claim).whitelist(true);
                whitelisted++;
            }
        }
        plugin.store().importedBanFromClaim(true);
        final String summary = "Imported %d bans and %d whitelisted claims from BanFromClaim (%d skipped: claim gone or invalid)"
                .formatted(bans, whitelisted, skipped);
        plugin.getLogger().info(summary);
        return summary;
    }

    private static Claim claim(@NotNull String id) {
        try {
            return Gp.data().getClaim(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuid(@NotNull String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
