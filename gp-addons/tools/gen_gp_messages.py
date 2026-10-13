"""Restyle GriefPrevention's own messages in the GPAddons palette.

Reads GriefPrevention's source (its default messages, and whether each is sent as an error, success, info,
instruction or warning) and writes src/main/resources/griefprevention-messages.yml, which GPAddons merges into
plugins/GriefPreventionData/messages.yml.

GriefPrevention's messages use legacy colour codes with $ in place of the section sign, so hex colours are
written as $x$f$f$d$8$b$e.

Usage: python3 -I gen_gp_messages.py <GriefPrevention src/main/java> <output yml>
"""
import collections
import pathlib
import re
import sys

import yaml

PEACH, PINK, LIGHT, MINT, RED, SKY, LILAC, CORAL, GREY = (
    "FFD8BE", "FFC8DD", "FFE5EC", "B5EAD7", "FF9AA2", "A2D2FF", "CDB4DB", "FFB5A7", "8A8A8A")

SMALL = dict(zip("abcdefghijklmnopqrstuvwxyz", "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"))

# Left exactly as GriefPrevention has them: a regex, chat keywords, and the claim book (beige pages, title limit)
UNTOUCHED = {"HowToClaimRegex", "TrappedChatKeyword", "BookAuthor", "BookTitle", "BookIntro",
             "BookDisabledChestClaims", "BookUsefulCommands", "BookLink", "BookTools"}
# Pieces put inside other messages: small caps only, so they take the colour around them
FRAGMENTS = {"CollectivePublic", "BuildPermission", "ContainersPermission", "AccessPermission",
             "PermissionsPermission", "LocationCurrentClaim", "LocationAllClaims", "OwnerNameForAdminClaims",
             "UnknownPlayerName"}
# Trust level names in /trustlist, coloured like the menus
LEVELS = {"Manage": CORAL, "Build": MINT, "Containers": LIGHT, "Access": SKY}
# Added to the end of other messages
SUFFIXES = {"IgnoreClaimsAdvertisement", "ContinueBlockMath"}
HEADERS = {"TrustListHeader", "ClaimsListHeader"}
# Messages GriefPrevention sends without naming the kind in the call
MODES = {
    "TransferClaimAdminOnly": "Err", "ClaimBlockLimit": "Err", "ManageUniversalPermissionsInstruction": "Instr",
    "ManageOneClaimPermissionsInstruction": "Instr", "DonateItemsInstruction": "Instr", "ChestFull": "Err",
    "DonationSuccess": "Success", "ThatPlayerPvPImmune": "Err", "NoBedPermission": "Err",
    "RestoreNaturePlayerInChunk": "Err", "NoCreativeUnClaim": "Err", "CreateClaimFailOverlapOtherPlayer": "Err",
    "AbandonClaimAdvertisement": "Instr", "ConfirmFluidRemoval": "Warn", "NoTeleportPvPCombat": "Err",
    "NotYourPet": "Err", "PlayerNotIgnorable": "Err", "TooMuchIpOverlap": "Err",
    "NetherPortalTrapDetectionMessage": "Warn",
}
PREFIX = {"Err": (RED, "✘"), "Success": (MINT, "✔"), "Info": (LILAC, "✦"), "Instr": (LILAC, "✎"),
          "Warn": (CORAL, "✦")}
EMPHASIS = {"ALL", "NOT"}

TOKEN = re.compile(r"(?P<url>https?://\S+)"
                   r"|(?P<cmd>(?<![\w/])/[A-Za-z0-9]+(?: (?:confirm|cancel))?)"
                   r"|(?P<ph>\{\d+\})"
                   r"|(?P<emph>\b(?:" + "|".join(EMPHASIS) + r")\b)")


def code(hex_color):
    return "$x" + "".join("$" + ch for ch in hex_color.lower())


def sc(text):
    return "".join(SMALL.get(ch.lower(), ch) for ch in text)


def guess_kinds(text, notes):
    """Colour for each {n}: pink for names, sky for links, light pink for numbers and other values"""
    kinds = {}
    for index, description in re.findall(r"\{?(\d)\}?\s*:\s*([^;,\n]+)", notes or ""):
        d = description.lower()
        if any(w in d for w in ("url", "video", "link")):
            kinds[index] = SKY
        elif any(w in d for w in ("name", "player", "owner", "permission", "group", "recipient")):
            kinds[index] = PINK
        else:
            kinds[index] = LIGHT
    for index in re.findall(r"\{(\d)\}", text):
        if index in kinds:
            continue
        if re.search(r"\{%s\}'s|by \{%s\}|\{%s\} (?:is|isn't|hasn't|uses|in)\b" % (index, index, index), text):
            kinds[index] = PINK
        elif re.search(r"Help: \{%s\}" % index, text):
            kinds[index] = SKY
        else:
            kinds[index] = LIGHT
    return kinds


def colourise(text, base, kinds):
    """Small caps in the base colour, with values, commands and links picked out"""
    out = []
    last = 0
    current = None

    def colour(c):
        nonlocal current
        if c != current:
            out.append(code(c))
            current = c

    def plain(part):
        if part:
            if base:
                colour(base)
            out.append(sc(part))

    for m in TOKEN.finditer(text):
        plain(text[last:m.start()])
        last = m.end()
        if m.group("url"):
            colour(SKY)
            out.append(m.group())
        elif m.group("cmd"):
            colour(PINK)
            out.append(m.group())
        elif m.group("ph"):
            if base:
                colour(kinds.get(m.group()[1:-1], LIGHT))
            out.append(m.group())
        elif m.group("emph"):
            if base:
                colour(base)
                out.append("$l" + sc(m.group()))
                current = None  # the next colour code also ends the bold
            else:
                out.append(sc(m.group()))
    plain(text[last:])
    return "".join(out)


def gradient(text, start=CORAL, end=PINK):
    a = [int(start[i:i + 2], 16) for i in (0, 2, 4)]
    b = [int(end[i:i + 2], 16) for i in (0, 2, 4)]
    chars = list(text)
    out = []
    for i, ch in enumerate(chars):
        t = i / max(1, len(chars) - 1)
        rgb = "".join("%02x" % round(a[k] + (b[k] - a[k]) * t) for k in range(3))
        out.append(code(rgb) + "$l" + ch if ch != " " else " ")
    return "".join(out)


def guess_mode(key):
    """Permission denials and limits are sent as errors through GriefPrevention's permission checks"""
    return "Err" if re.match(r"(No|Cant|Not|NonSiege)|.*(TooMany|TooSmall)", key) else "Info"


def style(key, text, notes, modes):
    if key in UNTOUCHED:
        return text
    if key in FRAGMENTS:
        return sc(text)
    if key in LEVELS:
        return code(LEVELS[key]) + sc(text)
    if key in HEADERS:
        bar = code(GREY) + "━━━"
        return bar + " " + gradient("✦ " + sc(text.rstrip(":"))) + " " + bar
    kinds = guess_kinds(text, notes)
    if key in SUFFIXES:
        return colourise(text, GREY, kinds)
    mode = modes.most_common(1)[0][0] if modes else MODES.get(key, guess_mode(key))
    colour, symbol = PREFIX.get(mode, PREFIX["Info"])
    base = RED if mode == "Err" else PEACH
    body = colourise(text.lstrip(), base, kinds)
    if colour == base and body.startswith(code(base)):
        body = body[len(code(base)):]
    lead = text[:len(text) - len(text.lstrip())]
    return lead + code(colour) + symbol + " " + body


def main(src, out_path):
    src = pathlib.Path(src)
    data_store = (src / "me/ryanhamshire/GriefPrevention/DataStore.java").read_text(encoding="utf-8")
    pattern = re.compile(r'addDefault\(defaults,\s*Messages\.(\w+),\s*"((?:[^"\\]|\\.)*)",\s*'
                         r'(null|"((?:[^"\\]|\\.)*)")\s*\);')
    defaults = {}
    for m in pattern.finditer(data_store):
        decode = lambda s: s.encode("latin-1", "backslashreplace").decode("unicode_escape")
        defaults[m.group(1)] = (decode(m.group(2)), None if m.group(3) == "null" else decode(m.group(4)))
    modes = collections.defaultdict(collections.Counter)
    for f in src.rglob("*.java"):
        for m in re.finditer(r"TextMode\.(\w+)\s*,\s*Messages\.(\w+)", f.read_text(encoding="utf-8")):
            modes[m.group(2)][m.group(1)] += 1
    messages = {}
    for key, (text, notes) in defaults.items():
        messages[key] = {"Text": style(key, text, notes, modes.get(key)), "Default": text}
    header = (
        "# GriefPrevention's messages in the GPAddons palette, generated by tools/gen_gp_messages.py.\n"
        "# GPAddons copies each Text into plugins/GriefPreventionData/messages.yml unless you changed that message.\n"
        "# Default is GriefPrevention's own text, used to tell whether a message was changed.\n"
    )
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(header)
        yaml.safe_dump({"Messages": messages}, f, allow_unicode=True, sort_keys=False, width=1000)
    print("wrote", len(messages), "messages")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
