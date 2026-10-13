# GPAddons

Claim menus, HuskClaims-style flags, claim bans and an entry whitelist for GriefPrevention 16.18,
in a pastel palette with small caps. GriefPrevention itself stays stock: GPAddons is a separate plugin
that keeps its own data in `plugins/GPAddons/data.yml`.

## Install

1. Stop the server.
2. Put `GriefPrevention-16.18.7.jar` (or a later 16.18 release) and `GPAddons-1.0.0.jar` in `plugins/`.
   Keep `plugins/GriefPreventionData/`, which holds your claims.
3. If you used BanFromClaim, leave its `plugins/BanFromClaim/data.dat` in place for the first start. Its bans and
   "ban everyone" claims are copied over once. You can delete the BanFromClaim jar.
4. Start the server. The console says how many bans were imported and how many GriefPrevention messages were
   restyled.

## What players get

| Command | What it does |
| --- | --- |
| `/claims` | Your claims, claim blocks and claim tool. Click a claim to manage it. |
| `/claims here` | The menu for the claim you're standing in (owners and managers) |
| `/claimflags [flag] [on\|off\|default]` | Flags menu, or set one flag |
| `/claimban <player>`, `/claimunban <player>`, `/claimbans` | Bans (no name opens the bans menu) |
| `/claimwhitelist [on\|off\|add\|remove]` | Entry whitelist (no option opens its menu) |
| `/claimname <name\|clear>` | Name the claim you're standing in |
| `/claimslist`, `/trustlist`, `/abandonclaim`, `/trust` with no name | Open the menus instead of chat lists |

Each claim menu has guests and trust levels, 30 flags, bans, the whitelist, subdivisions (with an
"inherit trust" switch), show outline, rename, transfer and abandon. Picking a player shows online players'
heads, with a book for typing the name of someone offline.

## Admin

- `/gpaddons reload` re-reads `config.yml` and `messages.yml`.
- `/gpaddons import-banfromclaim` copies BanFromClaim's bans again.
- `/gpaddons gp-messages` restyles GriefPrevention's messages again. Add `force` to also restyle messages you
  changed by hand, or `restore` to put the originals back. The original is kept as
  `plugins/GriefPreventionData/messages.yml.before-gpaddons`.
- Permissions: `gpaddons.admin` (any claim, reload; includes `gpaddons.other` for `/claims <player>` and
  `/claims admin`, and `gpaddons.bypass` to ignore bans and whitelists). Players have `gpaddons.menu`, `ban`,
  `whitelist`, `flags`, `rename` and `transfer` by default. Set `gpaddons.flag.<flag>` to false to stop players
  changing one flag.

## Settings

`config.yml` covers the outline (glowing or raised blocks, height, glow colours), whether GriefPrevention's
commands open menus, restyling GriefPrevention's messages, sponge protection, transfers and the default for
every flag. All text is in `messages.yml` (MiniMessage).

## Building

GitHub Actions builds the jar on every push (`.github/workflows/gp-addons.yml`) and checks it against
Adventure 5, which Paper 26.x uses. `tools/` has the scripts that generate both message files.
