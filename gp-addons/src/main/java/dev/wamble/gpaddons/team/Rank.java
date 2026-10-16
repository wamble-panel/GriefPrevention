package dev.wamble.gpaddons.team;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * A rank in a team. Ranks are ordered in their team, highest first; the leader sits above them all.
 */
public final class Rank {

    private final String id;
    private String name;
    private final Set<TeamPermission> permissions = EnumSet.noneOf(TeamPermission.class);

    public Rank(@NotNull String id, @NotNull String name) {
        this.id = id;
        this.name = name;
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
    public Set<TeamPermission> permissions() {
        return permissions;
    }

}
