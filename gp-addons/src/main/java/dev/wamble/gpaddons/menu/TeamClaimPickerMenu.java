package dev.wamble.gpaddons.menu;

import dev.wamble.gpaddons.GPAddons;
import dev.wamble.gpaddons.team.Team;
import dev.wamble.gpaddons.text.Messages;
import dev.wamble.gpaddons.util.Gp;
import dev.wamble.gpaddons.util.Items;
import me.ryanhamshire.GriefPrevention.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The leader picks which of their claims is the team claim. The first pick is free; moving it costs team
 * points, so that asks first.
 */
public final class TeamClaimPickerMenu extends PagedMenu<Claim> {

    private final Team team;

    public TeamClaimPickerMenu(@NotNull GPAddons plugin, @NotNull Player viewer, @NotNull Team team, @Nullable Menu back) {
        super(plugin, viewer, back);
        this.team = team;
    }

    @Override
    protected @NotNull Component title() {
        return messages().get("menu.team.pick-claim-title");
    }

    @Override
    protected boolean valid() {
        return plugin.teams().enabled() && plugin.teams().teamOf(viewer.getUniqueId()) == team
                && team.leader().equals(viewer.getUniqueId());
    }

    @Override
    protected @NotNull List<Claim> entries() {
        return Gp.claimsOf(viewer.getUniqueId()).stream().filter(claim -> claim.parent == null && !claim.isAdminClaim())
                .toList();
    }

    private boolean current(@NotNull Claim claim) {
        return claim.getID().equals(team.teamClaim());
    }

    @Override
    protected @NotNull ItemStack icon(@NotNull Claim claim) {
        final ItemStack item = plugin.info().item(claim, viewer, false);
        final int cost = plugin.teams().claimChangeCost(team);
        final List<Component> extra = current(claim) ? messages().lore("menu.team.pick-claim.current")
                : messages().lore(cost > 0 ? "menu.team.pick-claim.click-cost" : "menu.team.pick-claim.click",
                Messages.text("cost", Integer.toString(cost)));
        item.editMeta(meta -> {
            final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.addAll(extra);
            meta.lore(lore);
        });
        return current(claim) ? Items.glint(item) : item;
    }

    @Override
    protected @Nullable Action action(@NotNull Claim claim) {
        if (current(claim)) {
            return null;
        }
        return click -> {
            final int cost = plugin.teams().claimChangeCost(team);
            if (cost == 0) {
                pick(claim, false);
                return;
            }
            new ConfirmMenu(plugin, viewer, this,
                    messages().get("menu.team.pick-claim.confirm-title", Messages.text("name", plugin.info().name(claim))),
                    Items.item("lily_of_the_valley", messages().itemName("menu.team.pick-claim.confirm.name",
                                    Messages.text("name", plugin.info().name(claim))),
                            messages().lore("menu.team.pick-claim.confirm.lore", Messages.text("cost", Integer.toString(cost)),
                                    Messages.text("points", Integer.toString(team.points())))),
                    () -> valid() && Gp.exists(claim), () -> pick(claim, true)).open();
        };
    }

    private void pick(@NotNull Claim claim, boolean confirmed) {
        if (plugin.teams().setTeamClaim(viewer, claim, confirmed) && back != null && back.valid()) {
            back.open();
        }
    }

    @Override
    protected @Nullable ItemStack emptyIcon() {
        return Items.item("golden_shovel", messages().itemName("menu.team.pick-claim.empty.name"),
                messages().lore("menu.team.pick-claim.empty.lore"));
    }

}
