"""Generate GPAddons' messages.yml in the server's pastel palette (MiniMessage format).

Usage: python3 -I gen_messages.py <output path>
"""
import re
import sys

import yaml

PEACH, PINK, LIGHT, MINT, RED, SKY, LILAC, CORAL, GREY, WHITE = (
    "#FFD8BE", "#FFC8DD", "#FFE5EC", "#B5EAD7", "#FF9AA2", "#A2D2FF", "#CDB4DB", "#FFB5A7", "#8A8A8A", "#F2F2F2")

SMALL = dict(zip("abcdefghijklmnopqrstuvwxyz", "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"))


def sc(text):
    """Small caps for plain text, leaving <tags> and <placeholders> alone"""
    out, depth = [], 0
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
            out.append(ch)
            continue
        out.append(ch if depth else SMALL.get(ch.lower(), ch))
    return "".join(out)


def c(color, text):
    return f"<{color}>{text}</{color}>" if text else ""


def hl(text):
    """A highlighted value, e.g. a player name"""
    return c(PINK, text)


def ok(*parts):
    return c(MINT, "✔ ") + "".join(p if p.startswith("<") else c(PEACH, sc(p)) for p in parts)


def err(*parts):
    return c(RED, "✘ ") + "".join(p if p.startswith("<") else c(RED, sc(p)) for p in parts)


def header(text):
    return f"<gradient:{CORAL}:{PINK}><b>{sc(text)}</b></gradient>"


def title(text, extra=""):
    return f"{header('✦ ' + text)}" + (f" {c(GREY, '| ' + extra)}" if extra else "")


def name(text, symbol="✿", symbol_color=PINK, color=WHITE):
    return c(symbol_color, symbol + " ") + c(color, text)


def stat(label, value):
    return c(GREY, "• ") + c(WHITE, sc(label) + "  ") + value


def click(action, verb="click"):
    return f"<{PINK}>➜ <u>{sc(verb)}</u></{PINK}>" + c(PEACH, " " + sc(action))


def desc(*lines):
    return [c(PEACH, line) for line in lines]


def prompt(what):
    return c(LILAC, "✎ ") + c(PEACH, sc(what + ", or type ")) + c(RED, sc("cancel")) + c(PEACH, ".")


FLAGS = [
    ("block_place", "Place Blocks", ["Untrusted players may place blocks."]),
    ("block_break", "Break Blocks", ["Untrusted players may break blocks."]),
    ("block_interact", "Use Blocks", ["Untrusted players may use doors,", "gates, beds and workstations."]),
    ("redstone_interact", "Use Redstone", ["Untrusted players may press buttons,", "flip levers and step on plates."]),
    ("container_open", "Open Chests", ["Untrusted players may open chests,", "barrels, furnaces and hoppers."]),
    ("farm_block_break", "Harvest Crops", ["Untrusted players may break crops", "and trample farmland."]),
    ("farm_block_place", "Plant Crops", ["Untrusted players may plant seeds", "and crops."]),
    ("farm_block_interact", "Tend Crops", ["Untrusted players may bonemeal crops", "and use composters and hives."]),
    ("fill_bucket", "Fill Buckets", ["Untrusted players may scoop up", "water and lava."]),
    ("empty_bucket", "Empty Buckets", ["Untrusted players may pour out", "water and lava."]),
    ("place_hanging_entity", "Place Decorations", ["Untrusted players may hang paintings,", "item frames and armor stands."]),
    ("break_hanging_entity", "Break Decorations", ["Untrusted players may break paintings,", "item frames and armor stands."]),
    ("place_vehicle", "Place Vehicles", ["Untrusted players may place", "minecarts and boats."]),
    ("break_vehicle", "Break Vehicles", ["Untrusted players may break", "minecarts and boats."]),
    ("entity_interact", "Use Animals", ["Untrusted players may feed, ride,", "leash and trade with mobs."]),
    ("use_spawn_egg", "Spawn Eggs", ["Untrusted players may use spawn eggs", "and throw eggs."]),
    ("ender_pearl_teleport", "Ender Pearls", ["Untrusted players may teleport in", "with pearls and chorus fruit."]),
    ("start_raid", "Start Raids", ["Untrusted players with Bad Omen", "may start raids here."]),
    ("player_damage_player", "PvP", ["Players may fight each other here."]),
    ("player_damage_monster", "Fight Monsters", ["Untrusted players may hurt", "hostile mobs."]),
    ("player_damage_entity", "Hurt Animals", ["Untrusted players may hurt", "passive mobs."]),
    ("player_damage_persistent_entity", "Hurt Named Mobs", ["Untrusted players may hurt name-tagged", "and tamed mobs."]),
    ("monster_spawn", "Monster Spawning", ["Hostile mobs spawn here."]),
    ("passive_mob_spawn", "Animal Spawning", ["Passive mobs spawn here."]),
    ("monster_damage_terrain", "Mob Explosions", ["Creeper and wither explosions", "may break blocks here."]),
    ("explosion_damage_terrain", "TNT Explosions", ["TNT, TNT minecarts, beds and", "respawn anchors may break blocks here."]),
    ("explosion_damage_entity", "Explosion Damage", ["Explosions may hurt players,", "mobs and armor stands here."]),
    ("fire_burn", "Fire Burns Blocks", ["Fire may destroy blocks here."]),
    ("fire_spread", "Fire Spread", ["Fire may spread here."]),
    ("redstone_actuate", "Redstone Machines", ["Pistons and dispensers work here."]),
]


def messages():
    m = {
        "messages-version": MESSAGES_VERSION,
        "prefix": header("✦ claims") + " " + c(GREY, "• "),
        "general": {
            "no-permission": err("you don't have permission to do that."),
            "players-only": err("only players can do that."),
            "not-in-claim": err("stand in a claim to do that."),
            "cannot-manage": err("only the owner or a manager of this claim can do that."),
            "owner-only": err("only the claim's owner can do that."),
            "player-not-found": err("no player called ", hl("<player>"), " has played here."),
            "claim-gone": err("that claim no longer exists."),
            "other-world": err("go to this claim's world to see its outline."),
            "in-region": err("you can't claim here: it overlaps the protected area ", hl("<region>"), "."),
            "reloaded": ok("reloaded the config and messages."),
            "imported": ok("<summary>"),
            "usage": c(LILAC, "✎ ") + c(PEACH, sc("usage: ")) + hl("<usage>"),
            "date-format": "yyyy-MM-dd",
            "unnamed-claim": "Unnamed Claim",
            "unnamed-subdivision": "Subdivision",
            "admin-claim": "Admin Claim",
            "everyone": "Everyone",
        },
        "entry": {
            "banned": ok("banned ", hl("<player>"), " from this claim."),
            "unbanned": ok("unbanned ", hl("<player>"), "."),
            "already-banned": err("", hl("<player>"), " is already banned here."),
            "not-banned": err("", hl("<player>"), " isn't banned here."),
            "cannot-ban-self": err("you can't ban yourself."),
            "cannot-ban-owner": err("you can't ban the claim's owner."),
            "banned-enter": err("you're banned from ", hl("<owner>"), "'s claim."),
            "whitelist-enter": err("", hl("<owner>"), "'s claim is whitelisted. ask them to let you in."),
            "ejected": c(SKY, "➜ ") + c(PEACH, sc("you were moved out of ")) + hl("<owner>") + c(PEACH, sc("'s claim.")),
            "whitelist-on": ok("whitelist on: only trusted and whitelisted players can enter."),
            "whitelist-off": ok("whitelist off: anyone who isn't banned can enter."),
            "entry-added": ok("", hl("<player>"), " can now enter while the whitelist is on."),
            "entry-removed": ok("", hl("<player>"), " was taken off the whitelist."),
            "entry-already": err("", hl("<player>"), " is already on the whitelist."),
            "entry-not-found": err("", hl("<player>"), " isn't on this claim's whitelist."),
            "entry-banned": err("", hl("<player>"), " is banned here. unban them first."),
            "ban-list-header": c(GREY, "<st>━━━</st> ") + header("✦ banned from ") + " " + hl("<claim>") + c(GREY, " <st>━━━</st>"),
            "ban-list-entry": c(GREY, "• ") + hl("<player>") + c(PEACH, sc(" — banned by ")) + c(LIGHT, "<by>") + c(GREY, " (<date>)"),
            "ban-list-empty": c(MINT, "✔ ") + c(PEACH, sc("no one is banned here.")),
        },
        "flags": {
            "set": ok("set ", hl("<flag>"), " to ") + "<state>" + c(PEACH, "."),
            "reset": ok("", hl("<flag>"), " is back to the default: ") + "<state>" + c(PEACH, "."),
            "unknown": err("there's no flag called ", hl("<flag>"), "."),
            "no-flag-permission": err("you can't change ", hl("<flag>"), "."),
            "state-on": c(MINT, sc("✔ allowed")),
            "state-off": c(RED, sc("✘ denied")),
            "list-header": c(GREY, "<st>━━━</st> ") + header("✦ flags in ") + " " + hl("<claim>") + c(GREY, " <st>━━━</st>"),
            "list-entry": c(GREY, "• ") + c(WHITE, "<flag>") + c(GREY, " — ") + "<state>",
        },
        "trust": {
            "set": ok("gave ", hl("<player>"), " ") + "<level>" + c(PEACH, sc(" trust in this claim.")),
            "entry-set": ok("", hl("<player>"), " can now enter while the whitelist is on."),
            "removed": ok("took away ", hl("<player>"), "'s trust in this claim."),
            "manager-on": ok("", hl("<player>"), " is now a manager of this claim."),
            "manager-off": ok("", hl("<player>"), " is no longer a manager of this claim."),
            "cannot-grant": err("you can't give trust in this claim."),
            "too-high": err("you can only give trust up to your own level."),
            "manager-owner-only": err("only the claim's owner can add or remove managers."),
            "inherit-on": ok("this subdivision now trusts everyone trusted in the main claim."),
            "inherit-off": ok("this subdivision now only uses its own trust."),
            "banned-target": err("", hl("<player>"), " is banned here. unban them first."),
        },
        "levels": {
            "none": c(GREY, sc("no access")),
            "entry": c(LILAC, sc("entry")),
            "access": c(SKY, sc("access")),
            "container": c(LIGHT, sc("container")),
            "build": c(MINT, sc("build")),
            "manager": c(CORAL, sc("manager")),
        },
        "names": {
            "renamed": ok("renamed this claim to ", hl("<name>"), "."),
            "cleared": ok("removed this claim's name."),
            "invalid": err("claim names can be 1 to <max> characters."),
        },
        "transfer": {
            "done": ok("gave this claim to ", hl("<player>"), "."),
            "not-enough-blocks": err("", hl("<player>"), " needs <needed> unused claim blocks but has <have>."),
            "failed": err("griefprevention couldn't transfer this claim."),
            "main-only": err("only main claims can be given away, not subdivisions."),
            "self": err("they already own it."),
        },
        "abandon": {
            "done": ok("abandoned ", hl("<claim>"), ". you now have ", c(LIGHT, "<remaining>"), " claim blocks left."),
            "subdivision-done": ok("removed the subdivision ", hl("<claim>"), "."),
        },
        "prompt": {
            "team-invite": prompt("type the player to invite in chat"),
            "team-rename": prompt("type the new team name in chat"),
            "rank-create": prompt("type the new rank's name in chat"),
            "rank-rename": prompt("type the rank's new name in chat"),
            "guest": prompt("type the player to trust in chat"),
            "ban": prompt("type the player to ban in chat"),
            "entry": prompt("type the player to whitelist in chat"),
            "rename": prompt("type a new name for this claim in chat"),
            "transfer": prompt("type the player to give this claim to in chat"),
            "cancelled": c(GREY, "✘ " + sc("cancelled.")),
        },
        "teleport": {
            "warmup": c(SKY, "⌛ ") + c(PEACH, sc("teleporting to ")) + hl("<claim>") + c(PEACH, sc(" in "))
                      + c(LIGHT, "<seconds>") + c(PEACH, sc("s. don't move!")),
            "done": c(SKY, "➜ ") + c(PEACH, sc("teleported to ")) + hl("<claim>") + c(PEACH, "."),
            "cancelled": err("teleport cancelled because you moved or got hurt."),
            "cooldown": err("you can teleport again in <seconds>s."),
            "in-combat": err("you can't teleport during pvp combat."),
            "no-safe-spot": err("there's no safe spot to land. set a teleport point inside the claim first."),
            "home-set": ok("set this claim's teleport point to where you're standing."),
            "home-outside": err("stand inside this claim to set its teleport point."),
        },
        "icon": {
            "set": ok("changed this claim's icon."),
            "reset": ok("this claim's icon is back to the default."),
        },
        # Shown when walking into (and optionally out of) a claim
        "notices": {
            "enter": c(LILAC, "✿ ") + c(PEACH, sc("entering ")) + hl("<owner>") + c(PEACH, sc("'s claim")),
            "enter-named": c(LILAC, "✿ ") + c(PEACH, sc("entering ")) + hl("<name>")
                           + c(GREY, " • ") + c(PEACH, sc("by ")) + c(LIGHT, "<owner>"),
            "enter-own": c(MINT, "✿ ") + c(PEACH, sc("welcome home")),
            "enter-admin": c(CORAL, "✦ ") + c(PEACH, sc("entering an admin claim")),
            "enter-admin-named": c(CORAL, "✦ ") + c(PEACH, sc("entering ")) + hl("<name>"),
            "leave": c(GREY, "➜ " + sc("leaving ") + "<owner>" + sc("'s claim")),
            "leave-own": c(GREY, "➜ " + sc("leaving your claim")),
            "leave-admin": c(GREY, "➜ " + sc("leaving the admin claim")),
        },
        # The input screens (Minecraft 1.21.7+) used instead of typing in chat
        "dialog": {
            "guest": {"title": header("✦ add a guest"), "body": c(PEACH, "Who should be trusted in this claim?")},
            "ban": {"title": header("✦ ban a player"), "body": c(PEACH, "Who should be kept out of this claim?")},
            "entry": {"title": header("✦ whitelist a player"), "body": c(PEACH, "Who may enter while the whitelist is on?")},
            "transfer": {"title": header("✦ transfer claim"), "body": c(PEACH, "Who should this claim belong to?")},
            "rename": {"title": header("✦ rename claim"), "body": c(PEACH, "What should this claim be called?")},
            "player-label": c(WHITE, sc("player")),
            "name-label": c(WHITE, sc("claim name")),
            "confirm": c(MINT, sc("✔ confirm")),
            "cancel": c(RED, sc("✘ cancel")),
        },
        "team": team_messages(),
        "team-perms": {key: {"name": sc(label), "description": desc(*lines)} for key, label, lines in TEAM_PERMS},
        "team-quests": {key: {"name": c(PINK, sc(label)), "description": desc(*lines)} for key, label, lines in TEAM_QUESTS},
        "team-upgrades": {key: {"name": c(CORAL, sc(label)), "description": desc(*lines), "levels": [c(PEACH, sc(l)) for l in levels]}
                          for key, label, lines, levels in TEAM_UPGRADES},
        "menu": menu(),
        "flag": {key: {"name": sc(label), "description": desc(*lines)} for key, label, lines in FLAGS},
    }
    return m



TEAM_PERMS = [
    ("invite", "Invite", ["May invite players to the team."]),
    ("kick", "Kick", ["May kick members of a lower rank."]),
    ("promote", "Promote & Demote", ["May change the rank of members", "below their own rank."]),
    ("manage_ranks", "Manage Ranks", ["May create, rename, reorder and", "change ranks below their own."]),
    ("rename", "Rename Team", ["May change the team's name."]),
    ("claim_access", "Team Claims", ["Gets the trust that members give", "the team in their claims."]),
    ("set_home", "Set Team Home", ["May move the team home."]),
    ("spend_points", "Spend Points", ["May spend team points", "on upgrades."]),
]

# Raise this when existing lines change, and list them in Messages.UPDATED so servers get the new text
MESSAGES_VERSION = 2

TEAM_QUESTS = [
    ("gathering_the_crew", "Gathering the Crew", ["Have <goal> members in the team."]),
    ("a_place_to_call_home", "A Place to Call Home", ["Set a team home with", "/team sethome."]),
    ("shared_ground", "Shared Ground", ["Give the team Build trust in", "one of your claims."]),
    ("into_the_unknown", "Into the Unknown", ["Have <goal> members in the Nether", "together at once."]),
    ("the_final_frontier", "The Final Frontier", ["Have <goal> members in the End", "together at once."]),
    ("family_reunion", "Family Reunion", ["Gather <goal> members at the", "team home at once."]),
    ("strength_in_numbers", "Strength in Numbers", ["Kill <goal> monsters with a", "teammate fighting nearby."]),
    ("harvest_festival", "Harvest Festival", ["Harvest <goal> fully grown crops", "in the team's claims."]),
    ("master_builders", "Master Builders", ["Place <goal> blocks in the", "team's claims."]),
    ("deep_delvers", "Deep Delvers", ["Mine <goal> ores together."]),
]

TEAM_UPGRADES = [
    ("bountiful_harvest", "Bountiful Harvest", ["Crops in the team claim", "grow faster."], ["+25% crop growth", "+50% crop growth", "+100% crop growth"]),
    ("animal_husbandry", "Animal Husbandry", ["Baby animals bred in the team", "claim grow up faster."], ["+25% faster", "+50% faster", "+75% faster"]),
    ("team_storage", "Team Storage", ["A shared chest for the team:", "/team storage."], ["1 row", "2 rows", "3 rows"]),
    ("bigger_team", "Bigger Team", ["More room for members (when", "the server has a member limit)."], ["+2 members", "+4 members", "+6 members"]),
    ("land_grant", "Land Grant", ["Members earn claim blocks", "faster."], ["+15% claim blocks", "+30% claim blocks", "+50% claim blocks"]),
    ("wisdom", "Wisdom", ["More XP while a teammate", "is close by."], ["+10% XP", "+20% XP", "+30% XP"]),
    ("swift_return", "Swift Return", ["A quicker /team home."], ["2s wait, 20s cooldown", "1s wait, 10s cooldown", "instant, 5s cooldown"]),
    ("peaceful_grounds", "Peaceful Grounds", ["Fewer hostile mobs spawn", "naturally in and around the", "team claim. Spawners and farms", "further away still work."], ["-25% hostile spawns", "-50% hostile spawns", "-75% hostile spawns"]),
    ("monster_lure", "Monster Lure", ["Lets the team claim turn on", "hostile mob spawning (flag)."], ["Monster Spawning flag unlocked"]),
]


def team_messages():
    return {
        "disabled": err("teams are turned off on this server."),
        "no-team": err("you're not in a team. make one with ", hl("/team create <name>"), "."),
        "already-in-team": err("you're already in a team. leave it first."),
        "target-in-team": err("", hl("<player>"), " is already in a team."),
        "not-member": err("", hl("<player>"), " isn't in your team."),
        "no-permission": err("your rank can't do that."),
        "leader-only": err("only the team leader can do that."),
        "outranked": err("", hl("<player>"), " has the same or a higher rank than you."),
        "not-found": err("there's no team called ", hl("<team>"), "."),
        "created": ok("created the team ", "<team>", ". invite players with ", hl("/team invite <player>"), "."),
        "name-invalid": err("team names are <min> to <max> letters, numbers or underscores."),
        "name-taken": err("a team with that name already exists."),
        "no-color-permission": err("you don't have permission to use colours in names."),
        "invited": ok("invited ", hl("<player>"), " to the team."),
        "already-invited": err("", hl("<player>"), " already has an invite."),
        "invite-received": c(LILAC, "✦ ") + hl("<player>") + c(PEACH, sc(" invited you to ")) + "<team>"
                           + c(PEACH, ". ") + "<accept><hover:show_text:'"
                           + c(MINT, sc("join the team")) + "'>" + c(MINT, "<b>" + sc("[✔ accept]") + "</b>")
                           + "</hover></accept> <deny><hover:show_text:'"
                           + c(RED, sc("say no")) + "'>" + c(RED, "<b>" + sc("[✘ deny]") + "</b>") + "</hover></deny>",
        "no-invites": err("you don't have any team invites."),
        "denied": ok("turned down the invite from ", "<team>", "."),
        "joined": ok("you joined ", "<team>", "!"),
        "member-joined": c(MINT, "✿ ") + hl("<player>") + c(PEACH, sc(" joined the team!")),
        "member-left": c(GREY, "✘ ") + hl("<player>") + c(GREY, sc(" left the team.")),
        "left": ok("you left ", "<team>", "."),
        "kicked": ok("kicked ", hl("<player>"), " from the team."),
        "member-kicked": c(GREY, "✘ ") + hl("<player>") + c(GREY, sc(" was kicked from the team.")),
        "you-were-kicked": err("you were kicked from ", "<team>", "."),
        "rank-changed": c(LILAC, "✦ ") + hl("<player>") + c(PEACH, sc(" is now ")) + "<rank>" + c(PEACH, "."),
        "cant-promote": err("", hl("<player>"), " can't be promoted any higher by you."),
        "cant-demote": err("", hl("<player>"), " is already the lowest rank."),
        "renamed": ok("the team is now called ", "<team>", "."),
        "new-leader": c(CORAL, "✦ ") + hl("<player>") + c(PEACH, sc(" is the new team leader!")),
        "disbanded": c(RED, "✘ ") + "<team>" + c(RED, sc(" was disbanded.")),
        "leader-cant-leave": err("give the team to someone else first, or disband it."),
        "full": err("the team is full (<max> members)."),
        "rank-created": ok("created the rank ", "<rank>", "."),
        "rank-deleted": ok("deleted the rank ", "<rank>", "."),
        "rank-renamed": ok("renamed the rank to ", "<rank>", "."),
        "rank-moved": ok("moved ", "<rank>", "."),
        "rank-exists": err("the team already has a rank with that name."),
        "rank-last": err("a team needs at least one rank."),
        "rank-not-found": err("there's no rank called ", hl("<rank>"), "."),
        "rank-too-high": err("you can only manage ranks below your own."),
        "max-ranks": err("a team can have at most <max> ranks."),
        "perm-set": ok("", "<rank>", " ") + "<state>" + c(PEACH, " ") + hl("<perm>") + c(PEACH, "."),
        "perm-on": c(MINT, sc("can now")),
        "perm-off": c(RED, sc("can no longer")),
        "claim-trust": ok("set your team's trust in this claim to ") + "<level>" + c(PEACH, "."),
        "leader-rank": c(CORAL, "<b>" + sc("leader") + "</b>"),
        "home-set": ok("", hl("<player>"), " set the team home."),
        "no-home": err("your team has no home yet. set one with ", hl("/team sethome"), "."),
        "home-not-in-claim": err("the team home has to be in a claim owned by someone in your team."),
        "home-label": sc("team home"),
        "quest-complete": c(MINT, "✦ ") + header("quest complete!") + " " + "<quest>" + c(PEACH, sc(" — +"))
                          + c(LIGHT, "<points>") + c(PEACH, sc(" team point")),
        "upgrade-bought": c(CORAL, "✦ ") + hl("<player>") + c(PEACH, sc(" upgraded ")) + "<upgrade>"
                          + c(PEACH, sc(" to level ")) + c(LIGHT, "<level>") + c(PEACH, "!"),
        "upgrade-maxed": err("that upgrade is already at its highest level."),
        "not-enough-points": err("that costs <cost> team points; your team has <points>."),
        "storage-locked": err("unlock team storage with the ", hl("Team Storage"), " upgrade first."),
        "storage-in-use": err("", hl("<player>"), " is using the team storage right now."),
        "storage-title": header("✦ team storage") + " " + "<team>",
        "claim-none-here": err("stand in the claim you want as the team claim."),
        "claim-not-yours": err("the team claim has to be one of your own claims."),
        "claim-already": err("that's already the team claim."),
        "claim-confirm": c(LILAC, "✎ ") + c(PEACH, sc("moving the team claim costs ")) + c(LIGHT, "<cost>")
                         + c(PEACH, sc(" team points. ")) + "<click:run_command:'/team setclaim confirm'><hover:show_text:'"
                         + c(MINT, sc("spend the points")) + "'>" + c(MINT, "<b>" + sc("[✔ confirm]") + "</b>") + "</hover></click>",
        "claim-set": c(LILAC, "✦ ") + hl("<player>") + c(PEACH, sc(" made ")) + hl("<claim>")
                     + c(PEACH, sc(" the team claim. team upgrades now work there!")),
        "claim-moved": c(LILAC, "✦ ") + hl("<player>") + c(PEACH, sc(" moved the team claim to ")) + hl("<claim>")
                       + c(PEACH, sc(" for ")) + c(LIGHT, "<cost>") + c(PEACH, sc(" team points.")),
        "info-header": c(GREY, "<st>━━━</st> ") + "<team>" + c(GREY, " <st>━━━</st>"),
        "info-line": c(GREY, "• ") + c(WHITE, "<label>  ") + "<value>",
        "list-header": c(GREY, "<st>━━━</st> ") + header("✦ teams") + c(GREY, " <st>━━━</st>"),
        "list-entry": c(GREY, "• ") + "<team>" + c(GREY, " — ") + c(LIGHT, "<members> " + sc("members")),
        "help": [
            c(GREY, "<st>━━━</st> ") + header("✦ team commands") + c(GREY, " <st>━━━</st>"),
            hl("/team") + c(GREY, " — ") + c(PEACH, sc("open your team menu")),
            hl("/team create <name>") + c(GREY, " — ") + c(PEACH, sc("start a team")),
            hl("/team invite <player>") + c(GREY, " — ") + c(PEACH, sc("invite someone")),
            hl("/team accept") + c(GREY, " / ") + hl("deny") + c(GREY, " — ") + c(PEACH, sc("answer an invite")),
            hl("/team promote|demote <player>") + c(GREY, " — ") + c(PEACH, sc("change a member's rank")),
            hl("/team rank create|delete|rename|perm") + c(GREY, " — ") + c(PEACH, sc("manage ranks")),
            hl("/team rename <name>") + c(GREY, " — ") + c(PEACH, sc("rename the team")),
            hl("/team kick|transfer <player>") + c(GREY, " — ") + c(PEACH, sc("kick or hand over the team")),
            hl("/team leave") + c(GREY, " / ") + hl("disband") + c(GREY, " — ") + c(PEACH, sc("leave or end the team")),
            hl("/team home") + c(GREY, " / ") + hl("sethome") + c(GREY, " — ") + c(PEACH, sc("go to or move the team home")),
            hl("/team setclaim") + c(GREY, " — ") + c(PEACH, sc("make the claim you're in the team claim")),
            hl("/team quests") + c(GREY, " / ") + hl("upgrades") + c(GREY, " / ") + hl("storage") + c(GREY, " — ") + c(PEACH, sc("earn and spend team points")),
            hl("/team info [team]") + c(GREY, " / ") + hl("list") + c(GREY, " — ") + c(PEACH, sc("look up teams")),
        ],
        "labels": {"leader": sc("leader"), "members": sc("members"), "your-rank": sc("your rank"),
                   "created": sc("created")},
    }


def team_menu():
    blank = ""
    return {
        "title": header("✦ team") + " " + "<team>",
        "header": {"name": "<team>",
                   "lore": [stat("leader", hl("<leader>")), stat("members", c(LIGHT, "<members>")),
                            stat("your rank", "<rank>"), stat("team points", c(MINT, "<points>")),
                            stat("created", c(LIGHT, "<created>"))]},
        "home": {"name": name(sc("team home"), symbol="➜", symbol_color=SKY),
                 "lore": desc("Where the team gathers.") + [blank, stat("set", "<state>")],
                 "click": [blank, click("to teleport")],
                 "click-set": [click("to set it here", verb="shift-click")]},
        "quests": {"name": name(sc("quests")), "lore": desc("Team goals that earn", "team points."),
                   "click": [blank, click("to view")]},
        "upgrades": {"name": name(sc("upgrades"), symbol="✦", symbol_color=CORAL), "lore": desc("Spend team points on", "upgrades for the team."),
                     "click": [blank, click("to view")]},
        "storage": {"name": name(sc("team storage")), "lore": desc("A shared chest for the team.", "Unlock it with the Team Storage upgrade."),
                    "click": [blank, click("to open")]},
        "team-claim": {"name": name(sc("team claim"), symbol="✦", symbol_color=LILAC),
                       "lore": desc("The leader's claim where", "team upgrades work: crops,", "animals, peaceful grounds", "and the monster lure."),
                       "set": [stat("claim", hl("<name>")), stat("where", c(LIGHT, "<world> <x>, <z>"))],
                       "none": [c(GREY, sc("✘ not chosen yet"))],
                       "gone": [c(RED, sc("✘ the claim is gone or no longer the team's"))],
                       "click": [blank, click("to choose (free)")],
                       "click-cost": [blank, click("to move it"), c(GREY, sc("moving it costs ")) + c(LIGHT, "<cost>") + c(GREY, sc(" team points"))],
                       "leader-only": [blank, c(GREY, sc("✦ the team leader picks this"))]},
        "pick-claim-title": header("✦ choose the team claim"),
        "pick-claim": {"click": [blank, click("to make this the team claim")],
                       "click-cost": [blank, click("to move the team claim here"), c(GREY, sc("costs ")) + c(LIGHT, "<cost>") + c(GREY, sc(" team points"))],
                       "current": [blank, c(MINT, sc("✔ this is the team claim"))],
                       "confirm-title": header("✦ move team claim") + " " + c(GREY, "→ <name>?"),
                       "confirm": {"name": name(sc("move the team claim to") + " <name>", symbol="✦", symbol_color=LILAC),
                                   "lore": desc("Team upgrades will work here", "instead.") + [blank, stat("cost", c(LIGHT, "<cost> " + sc("team points"))), stat("team points", c(MINT, "<points>"))]},
                       "empty": {"name": name(sc("no claims yet"), symbol="✘", symbol_color=RED),
                                 "lore": desc("Make a claim with a golden", "shovel first.")}},
        "quests-title": header("✦ quests") + " " + "<team>",
        "upgrades-title": header("✦ upgrades") + " " + "<team>",
        "points": {"name": c(MINT, "✦ ") + c(WHITE, "<points> ") + c(WHITE, sc("team points")),
                   "lore": desc("Finish quests to earn points,", "then spend them on upgrades.")},
        "quest": {"name": name("<quest>"),
                  "progress": stat("progress", "<bar>"),
                  "reward": stat("reward", c(MINT, "+<points> " + sc("team point"))),
                  "done": [blank, c(MINT, sc("✔ complete"))],
                  "open": [blank, c(GREY, sc("✦ in progress"))]},
        "upgrade": {"name": name("<upgrade>"),
                    "level": stat("level", c(LIGHT, "<level>/<max>")),
                    "reached": c(MINT, "✔ ") + "<effect>",
                    "locked": c(GREY, "✘ ") + "<effect>",
                    "maxed": [c(MINT, sc("✔ fully upgraded"))],
                    "cost": stat("next level", c(LIGHT, "<cost> " + sc("team points"))),
                    "click": [blank, click("to upgrade")],
                    "short": [blank, c(RED, sc("✘ not enough team points"))]},
        "members": {"name": name(sc("members")), "lore": desc("Everyone in the team and", "their ranks."),
                    "click": [blank, click("to view")]},
        "ranks": {"name": name(sc("ranks")), "lore": desc("Ranks and what each may do."),
                  "click": [blank, click("to manage")]},
        "invite": {"name": name(sc("invite a player"), symbol="✚", symbol_color=MINT),
                   "lore": desc("Ask someone to join the team."), "click": [blank, click("to invite")]},
        "rename": {"name": name(sc("rename team")), "lore": desc("Change the team's name.") + [blank, stat("name", "<team>")],
                   "click": [blank, click("to rename")]},
        "leave": {"name": name(sc("leave team"), symbol="✘", symbol_color=RED, color=RED),
                  "lore": desc("Leave this team."), "click": [blank, click("to leave")]},
        "disband": {"name": name(sc("disband team"), symbol="✘", symbol_color=RED, color=RED),
                    "lore": desc("End the team for everyone.", "It can't be undone."), "click": [blank, click("to disband")]},
        "members-title": header("✦ members") + " " + "<team>",
        "member": {"name": name("<player>"), "lore": [stat("rank", "<rank>"), stat("online", "<online>")],
                   "click": [blank, click("to manage")]},
        "member-title": header("✦ member") + " " + c(GREY, "| <player>"),
        "promote": {"name": name(sc("promote"), symbol="▲", symbol_color=MINT), "lore": desc("Move up one rank.") + [blank, stat("becomes", "<rank>")],
                    "click": [blank, click("to promote")]},
        "demote": {"name": name(sc("demote"), symbol="▼", symbol_color=RED), "lore": desc("Move down one rank.") + [blank, stat("becomes", "<rank>")],
                   "click": [blank, click("to demote")]},
        "set-rank": {"name": name(sc("set rank")), "lore": desc("Pick any rank below yours."), "click": [blank, click("to choose")]},
        "kick": {"name": name(sc("kick"), symbol="✘", symbol_color=RED, color=RED), "lore": desc("Remove them from the team."),
                 "click": [blank, click("to kick")]},
        "make-leader": {"name": name(sc("make leader"), symbol="✦", symbol_color=CORAL),
                        "lore": desc("Hand the team over to them.", "You'll become the top rank."), "click": [blank, click("to hand over")]},
        "ranks-title": header("✦ ranks") + " " + "<team>",
        "rank": {"name": "<rank>", "lore": [stat("position", c(LIGHT, "<position>")), stat("members", c(LIGHT, "<members>")), blank],
                 "click": [blank, click("to manage")]},
        "rank-perm-line": c(GREY, "• ") + "<state> " + c(WHITE, "<perm>"),
        "create-rank": {"name": name(sc("create rank"), symbol="✚", symbol_color=MINT),
                        "lore": desc("Add a new rank just above", "the lowest one."), "click": [blank, click("to create")]},
        "rank-title": header("✦ rank") + " " + "<rank>",
        "perm": {"name": name("<perm>"), "status": stat("allowed", "<state>"),
                 "click": [blank, click("to toggle")]},
        "move-up": {"name": name(sc("move up"), symbol="▲", symbol_color=MINT), "lore": desc("Swap with the rank above."),
                    "click": [blank, click("to move")]},
        "move-down": {"name": name(sc("move down"), symbol="▼", symbol_color=RED), "lore": desc("Swap with the rank below."),
                      "click": [blank, click("to move")]},
        "rename-rank": {"name": name(sc("rename rank")), "lore": desc("Give this rank a new name."), "click": [blank, click("to rename")]},
        "delete-rank": {"name": name(sc("delete rank"), symbol="✘", symbol_color=RED, color=RED),
                        "lore": desc("Its members move down a rank."), "click": [blank, click("to delete")]},
        "pick-rank-title": header("✦ choose a rank"),
        "pick-rank": {"name": "<rank>", "lore": [], "click": [blank, click("to choose")]},
        "claim": {"name": name(sc("team access"), symbol="✦", symbol_color=LILAC),
                  "lore": desc("What your team may do here.", "Ranks without Team Claims", "don't get this.") + [blank, stat("team", "<team>"), stat("trust", "<level>")],
                  "click": [blank, click("to change")]},
        "yes": c(MINT, sc("✔ yes")), "no": c(GREY, sc("✘ no")),
        "online": c(MINT, sc("online")), "offline": c(GREY, sc("offline")),
    }


def menu():
    blank = ""
    return {
        "back": {"name": c(PINK, sc("◀ back")), "lore": []},
        "previous": {"name": c(PINK, sc("◀ previous page")), "lore": []},
        "next": {"name": c(PINK, sc("next page ▶")), "lore": []},
        "locked": [blank, c(RED, "✘ " + sc("you can't use this here"))],
        "claims": {
            "title": title("claims", "<owner> (<page>/<pages>)"),
            "admin-title": title("admin claims", "(<page>/<pages>)"),
            "item": {
                "name": name("<name>", symbol_color=MINT),
                "lore": [
                    "<owner_line>",
                    blank,
                    stat("world", c(LIGHT, "<world>")),
                    stat("center", c(LIGHT, "<x>, <z>")),
                    stat("area", c(LIGHT, "<area> " + sc("blocks") + " (<width>×<length>)")),
                    stat("subdivisions", c(LIGHT, "<subdivisions>")),
                    stat("guests", c(LIGHT, "<guests>")),
                    stat("bans", c(LIGHT, "<bans>")),
                    stat("whitelist", "<whitelist>"),
                ],
                "click": [blank, click("to manage")],
                "teleport": [click("to teleport", verb="right-click")],
            },
            "owner-you": c(PEACH, "One of your claims."),
            "team-claim": c(LILAC, "✦ ") + c(PEACH, sc("team claim of ")) + "<team>",
            "owner-other": c(PEACH, "Owned by ") + hl("<owner>") + c(PEACH, "."),
            "owner-admin": c(PEACH, "An admin claim."),
            "info": {
                "name": name("<player>", symbol="✦", symbol_color=CORAL, color=PINK),
                "lore": [
                    stat("claims", c(LIGHT, "<claims>")),
                    stat("blocks left", c(MINT, "<remaining>")),
                    stat("blocks earned", c(LIGHT, "<accrued>")),
                    stat("bonus blocks", c(LIGHT, "<bonus>")),
                ],
            },
            "empty": {
                "name": c(RED, sc("✘ no claims yet")),
                "lore": desc("Claim land with a golden shovel:", "right-click two opposite corners."),
            },
            "on": c(MINT, sc("✔ on")),
            "off": c(GREY, sc("✘ off")),
            "point-set": c(LIGHT, sc("your teleport point")),
            "point-center": c(LIGHT, sc("the middle of the claim")),
            "tool": {
                "name": name(sc("claim tool"), symbol="✦", symbol_color=CORAL),
                "lore": desc("What your golden shovel does", "when you right-click land.") + [blank, stat("mode", "<mode>")],
                "click": [blank, click("to switch")],
            },
            "modes": {
                "basic": c(MINT, sc("claiming land")),
                "subdivide": c(LILAC, sc("making subdivisions")),
                "admin": c(CORAL, sc("admin claims")),
                "other": c(SKY, sc("restoring nature")),
            },
        },
        "claim": {
            "title": title("claim", "<name>"),
            "buttons": {
                "guests": {"name": name(sc("guests")),
                           "lore": desc("Who may do what here.", "", "Give players access, container,", "build or manager trust."),
                           "click": [blank, click("to manage guests")]},
                "flags": {"name": name(sc("flags")),
                          "lore": desc("What everyone may do here.", "", "Building, chests, PvP, explosions,", "mob spawning and more."),
                          "click": [blank, click("to change flags")]},
                "bans": {"name": name(sc("bans")),
                         "lore": desc("Players kept out of this claim", "and all its subdivisions.") + [blank, stat("banned", c(LIGHT, "<bans>"))],
                         "click": [blank, click("to manage bans")]},
                "whitelist": {"name": name(sc("entry whitelist")),
                              "lore": desc("When on, only the owner, trusted", "and whitelisted players may enter.") + [blank, stat("status", "<state>")],
                              "click": [blank, click("to manage")]},
                "subdivisions": {"name": name(sc("subdivisions")),
                                 "lore": desc("Smaller areas inside this claim", "with their own trust and flags.") + [blank, stat("subdivisions", c(LIGHT, "<count>"))],
                                 "click": [blank, click("to view")]},
                "inherit": {"name": name(sc("inherit trust")),
                            "lore": desc("Whether players trusted in the", "main claim are trusted here too.") + [blank, stat("inherit", "<state>")],
                            "click": [blank, click("to switch")]},
                "teleport": {"name": name(sc("teleport"), symbol="➜", symbol_color=SKY),
                             "lore": desc("Go straight to this claim.") + [blank, stat("arrive at", "<point>")],
                             "click": [blank, click("to teleport")],
                             "click-set": [click("to set it to where you stand", verb="shift-click")]},
                "icon": {"name": name(sc("change icon")),
                         "lore": desc("Hold any item, then click to", "make it this claim's icon.", "Click with an empty hand to reset."),
                         "click": [blank, click("to change")]},
                "main-claim": {"name": name(sc("main claim")),
                               "lore": desc("This is a subdivision of", "<main>."),
                               "click": [blank, click("to open it")]},
                "outline": {"name": name(sc("show outline")),
                            "lore": desc("See this claim's borders."),
                            "click": [blank, click("to show")]},
                "rename": {"name": name(sc("rename")),
                           "lore": desc("Give this claim a name.") + [blank, stat("name", c(LIGHT, "<name>"))],
                           "click": [blank, click("to rename")]},
                "transfer": {"name": name(sc("transfer")),
                             "lore": desc("Give this claim away.", "", "Moves ownership and the block", "cost to another player."),
                             "click": [blank, click("to transfer")]},
                "abandon": {"name": name(sc("abandon"), symbol="✘", symbol_color=RED, color=RED),
                            "lore": desc("Delete this claim.", "", "Removes the claim and refunds", "its blocks."),
                            "click": [blank, click("to abandon")]},
            },
        },
        "guests": {
            "title": title("guests", "<name>"),
            "player": {"name": name("<player>"),
                       "lore": [stat("trust", "<level>")]},
            "group": {"name": name("<group>", symbol="✦", symbol_color=LILAC),
                      "lore": desc("Everyone with this permission.") + [blank, stat("trust", "<level>")]},
            "everyone": {"name": name(sc("everyone"), symbol="✦", symbol_color=LILAC),
                         "lore": desc("What anyone may do here.") + [blank, stat("trust", "<level>")],
                         "click": [blank, click("to change")]},
            "manager-badge": c(CORAL, "✦ ") + c(CORAL, sc("manager")),
            "click": [blank, click("to change trust", "left-click"), click("to remove", "right-click")],
            "add": {"name": name(sc("add guest"), symbol="✚", symbol_color=MINT, color=MINT),
                    "lore": desc("Trust a player by typing", "their name in chat."),
                    "click": [blank, click("to add")]},
            "empty": {"name": c(GREY, sc("no guests yet")),
                      "lore": desc("Trust a player so they can", "build, open chests or visit."),
                      "click": [blank, click("to add a guest")]},
        },
        "trust": {
            "title": title("trust", "<player>"),
            "entry": {"name": name(sc("entry"), symbol_color=LILAC),
                      "lore": desc("May enter while the whitelist", "is on. Nothing else.")},
            "access": {"name": name(sc("access"), symbol_color=SKY),
                       "lore": desc("Doors, buttons, levers and beds.")},
            "container": {"name": name(sc("container"), symbol_color=LIGHT),
                          "lore": desc("Chests, furnaces, animals and", "everything Access allows.")},
            "build": {"name": name(sc("build"), symbol_color=MINT),
                      "lore": desc("Build, break and everything", "Container allows.")},
            "manager": {"name": name(sc("manager"), symbol_color=CORAL),
                        "lore": desc("May trust others, change flags,", "ban and whitelist. Owner only.") + [blank, stat("manager", "<state>")]},
            "remove": {"name": name(sc("remove trust"), symbol="✘", symbol_color=RED, color=RED),
                       "lore": desc("Take away all their trust here.")},
            "current": [blank, c(MINT, "✔ " + sc("current"))],
            "click": [blank, click("to give")],
            "toggle": [blank, click("to toggle")],
        },
        "flags": {
            "title": title("claim flags", "<name>"),
            "item": {"name": name("<flag>")},
            "category": {"player": c(GREY, sc("for untrusted players")),
                         "environment": c(GREY, sc("environment"))},
            "status": c(LILAC, "✦ ") + c(WHITE, sc("status  ")) + "<state>",
            "lure-locked": ["", c(RED, sc("✘ only in a team claim with the")), c(CORAL, sc("monster lure")) + c(RED, sc(" team upgrade"))],
            "source": {"claim": c(GREY, sc("set in this claim")),
                       "main_claim": c(GREY, sc("from the main claim")),
                       "default": c(GREY, sc("default"))},
            "click": [blank, click("to toggle"), click("to reset", "shift-click")],
        },
        "bans": {
            "title": title("bans", "<name>"),
            "item": {"name": name("<player>", symbol="✘", symbol_color=RED),
                     "lore": [stat("banned by", c(LIGHT, "<by>")), stat("since", c(LIGHT, "<date>"))],
                     "click": [blank, click("to unban")]},
            "add": {"name": name(sc("ban a player"), symbol="✘", symbol_color=RED, color=RED),
                    "lore": desc("Keep a player out of this claim", "and its subdivisions."),
                    "click": [blank, click("to ban")]},
            "empty": {"name": c(MINT, sc("✔ no one is banned")), "lore": []},
        },
        "whitelist": {
            "title": title("whitelist", "<name>"),
            "on": {"name": c(MINT, sc("✔ whitelist on")),
                   "lore": desc("Only the owner, trusted players", "and the players below may enter."),
                   "click": [blank, click("to turn off")]},
            "off": {"name": c(GREY, sc("✘ whitelist off")),
                    "lore": desc("Anyone who isn't banned", "may enter."),
                    "click": [blank, click("to turn on")]},
            "entry": {"name": name("<player>", symbol_color=LILAC),
                      "lore": [stat("may enter", c(MINT, sc("✔ yes")))],
                      "click": [blank, click("to remove")]},
            "trusted": {"name": name("<player>"),
                        "lore": [stat("trust", "<level>")] + desc("", "Trusted players may always enter.", "Change their trust in Guests.")},
            "add": {"name": name(sc("add player"), symbol="✚", symbol_color=MINT, color=MINT),
                    "lore": desc("Let a player in without", "giving them any trust."),
                    "click": [blank, click("to add")]},
        },
        "subdivisions": {
            "title": title("subdivisions", "<name>"),
            "item": {"name": name("<name>", symbol_color=WHITE),
                     "lore": [stat("area", c(LIGHT, "<area> " + sc("blocks") + " (<width>×<length>)")),
                              stat("center", c(LIGHT, "<x>, <z>")),
                              stat("guests", c(LIGHT, "<guests>"))],
                     "click": [blank, click("to manage")]},
            "empty": {"name": c(GREY, sc("no subdivisions")),
                      "lore": desc("Make one with /subdivideclaims", "and a golden shovel.")},
        },
        "team": team_menu(),
        "picker": {
            "team-invite": {"title": title("invite to team")},
            "guest": {"title": title("add a guest")},
            "ban": {"title": title("ban a player")},
            "entry": {"title": title("whitelist a player")},
            "transfer": {"title": title("give this claim to")},
            "player": {"name": name("<player>"), "lore": [], "click": [blank, click("to choose")]},
            "type": {"name": name(sc("type a name"), symbol="✎", symbol_color=LILAC),
                     "lore": desc("For players who aren't online.", "Type their name in chat."),
                     "click": [blank, click("to type")]},
            "empty": {"name": c(GREY, sc("no one else is online")),
                      "lore": desc("Use the book to type a name.")},
        },
        "confirm": {
            "confirm": {"name": c(MINT, "<b>" + sc("✔ confirm") + "</b>"), "lore": []},
            "cancel": {"name": c(RED, "<b>" + sc("✘ cancel") + "</b>"), "lore": []},
            "abandon": {"title": c(RED, "<b>" + sc("✘ abandon") + "</b>") + " " + c(GREY, "<name>?"),
                        "name": name(sc("abandon") + " <name>", symbol="✘", symbol_color=RED, color=RED),
                        "lore": desc("This deletes the claim and", "all its subdivisions.") + [c(RED, "It can't be undone.")]},
            "transfer": {"title": header("✦ transfer") + " " + c(GREY, "<name> → <player>?"),
                         "name": name(sc("give to") + " <player>"),
                         "lore": desc("<name> will belong to <player>.", "You'll lose access unless", "they trust you.")},
        },
    }


def check(m):
    """Every MiniMessage colour tag opened must be closed in each string, and lore must be lists"""
    problems = []

    def walk(node, path):
        if isinstance(node, dict):
            for k, v in node.items():
                walk(v, f"{path}.{k}" if path else k)
        elif isinstance(node, list):
            for i, v in enumerate(node):
                walk(v, f"{path}[{i}]")
        elif isinstance(node, str):
            opened = re.findall(r"<(#[0-9A-Fa-f]{6}|b|u|st|gradient:[^>]+)>", node)
            closed = re.findall(r"</(#[0-9A-Fa-f]{6}|b|u|st|gradient)>", node)
            if len(opened) != len(closed):
                problems.append(f"{path}: {len(opened)} opened, {len(closed)} closed: {node}")
    walk(m, "")
    return problems


if __name__ == "__main__":
    data = messages()
    problems = check(data)
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    header_comment = (
        "# GPAddons messages, in MiniMessage format: https://docs.advntr.dev/minimessage/format\n"
        "# Placeholders look like <player>. Set a chat message to '' to stop it being sent.\n"
        "# In menus, \"name\" is an item's name and \"lore\" and \"click\" are the lines under it.\n"
    )
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        f.write(header_comment)
        yaml.safe_dump(data, f, allow_unicode=True, sort_keys=False, width=1000, default_flow_style=False)
    print("ok", sum(1 for _ in open(sys.argv[1], encoding="utf-8")), "lines")
