#!/usr/bin/env python3
"""Writes settings.json and the "input" bindings in mod.json from the lists below.

The button choices live in three places that must agree: this file (settings.json and
mod.json), the setup screen (src/app/java/com/deflight/buttonmapper/Choices.java) and
the hook (src/hooks/.../Entry.java, what each choice sends). Change all three together.

  python3 tools/gen.py
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# (value, title) in the order the setup screen shows them
CHOICES = [
    ("homekey", "CarPlay: Home Button"),
    ("back", "CarPlay: Back"),
    ("maps", "CarPlay: Maps"),
    ("music", "CarPlay: Music"),
    ("nowplaying", "CarPlay: Now Playing"),
    ("podcasts", "CarPlay: Podcasts"),
    ("audiobooks", "CarPlay: Audiobooks"),
    ("mobilephone", "CarPlay: Phone"),
    ("messages", "CarPlay: Messages"),
    ("calendar", "CarPlay: Calendar"),
    ("settings", "CarPlay: Settings"),
    ("siri", "CarPlay: Siri"),
    ("playpause", "CarPlay: Play/Pause"),
    ("next", "CarPlay: Next Track"),
    ("previous", "CarPlay: Previous Track"),
    ("seekup", "Seek Up"),
    ("seekdown", "Seek Down"),
    ("tuneup", "Tune Up"),
    ("tunedown", "Tune Down"),
    ("presetup", "Preset Up"),
    ("presetdown", "Preset Down"),
    ("spotify", "CarPlay: Spotify"),
    ("tidal", "CarPlay: Tidal"),
    ("audible", "CarPlay: Audible"),
    ("pandora", "CarPlay: Pandora"),
    ("amznmp3", "CarPlay: Amazon Music"),
    ("youtubemusic", "CarPlay: YouTube Music"),
    ("waze", "CarPlay: Waze"),
    ("googlemaps", "CarPlay: Google Maps"),
    ("whatsapp", "CarPlay: WhatsApp"),
    ("claude", "CarPlay: Claude"),
    ("aa_home", "Android Auto: Home"),
    ("aa_back", "Android Auto: Back"),
    ("aa_navigation", "Android Auto: Navigation"),
    ("aa_media", "Android Auto: Media"),
    ("aa_phone", "Android Auto: Phone"),
    ("aa_assistant", "Android Auto: Assistant"),
    ("aa_playpause", "Android Auto: Play/Pause"),
    ("aa_play", "Android Auto: Play"),
    ("aa_pause", "Android Auto: Pause"),
    ("aa_next", "Android Auto: Next Track"),
    ("aa_previous", "Android Auto: Previous Track"),
    ("carhome", "Home"),
    ("carback", "Back"),
    ("answercall", "Answer"),
    ("endcall", "Reject/End"),
    ("custom", "CarPlay: Custom"),
    ("carapp", "Car app"),
    ("off", "Default"),
]

# (key, label): every button the setup screen lists, in Choices.KEYS's order. The star keys
# (custom, wheel_custom) have only a hold binding: their press is picked in the car's ★ menu.
BUTTONS = [
    ("custom", "Head Unit \u2606"), ("wheel_custom", "Steering Wheel \u2605"),
    ("home", "HOME"), ("map", "MAP"), ("navi", "NAV"), ("media", "MEDIA"), ("radio", "RADIO"), ("phone", "PHONE"), ("setup", "SETUP"),
    ("seek_up", "SEEK UP (head unit)"), ("seek_down", "SEEK DOWN (head unit)"),
    ("tune_up", "TUNE UP"), ("tune_down", "TUNE DOWN"),
    ("mode", "MODE"), ("call", "CALL"), ("ptt", "VOICE"), ("mute", "MUTE"),
    ("channel_up", "SEEK UP (steering wheel)"), ("channel_down", "SEEK DOWN (steering wheel)"),
    ("poi", "POI"), ("connect", "CONNECT (Bluelink / Kia Connect)"),
]
STARS = ("custom", "wheel_custom")
# the car's keys a press can send (Choices.carKey): seek, tune, the wheel's up/down (presets), HOME,
# and the Call button's press (answer a call, or hold / switch during one)
# (value of <slot>_send, the press HMG-API makes the car see)
CAR_KEYS = (("seek_up", "seek_up short"), ("seek_down", "seek_down short"), ("tune_up", "tune_up short"),
            ("tune_down", "tune_down short"), ("channel_up", "channel_up short"), ("channel_down", "channel_down short"),
            ("home", "home short"), ("call", "call short"))

CUSTOM_HELP = ("Used when this press is set to CarPlay: Custom. The CarPlay screen's name as the phone "
               "knows it, e.g. maps, music, nowplaying, mobilephone, calshow. Spaces and new lines are "
               "ignored; capital letters are kept. If the phone doesn't know the name, nothing happens.")


CHOICE_HELP = "Picked on the setup screen: " + ", ".join(v for v, _ in CHOICES) + " (off: Default, the car's own)."


def slots():
    """(slot, title): each button's press, then its hold"""
    for key, label in BUTTONS:
        yield key, label + " press"
        yield key + "_hold", label + " hold"


def settings():
    out = []
    for slot, title in slots():
        out.append({
            "key": slot, "type": "text", "default": "off", "private": True,
            "title": title, "description": CHOICE_HELP,
        })
        out.append({
            "key": slot + "_custom", "type": "text", "default": "", "private": True,
            "title": title + ": custom CarPlay app", "description": CUSTOM_HELP,
        })
        out.append({
            "key": slot + "_app", "type": "text", "default": "", "private": True,
            "title": title + ": car app",
            "description": "Used when this press is set to Car app: the app's screen as package/class, picked on the setup screen.",
        })
        out.append({
            "key": slot + "_send", "type": "text", "default": "none", "private": True,
            "title": title + ": car key sent",
            "description": "Set by the app: the car button HMG-API presses instead (seek_up, seek_down, tune_up, tune_down, channel_up, channel_down), or none.",
        })
        out.append({
            "key": slot + "_on", "type": "boolean", "default": False, "private": True,
            "title": title + ": taken from the car",
            "description": "Set by the app: on when this press isn't Default and, for a CarPlay or Android Auto choice, that phone is connected.",
        })
    out.append({
        "key": "finding", "type": "boolean", "default": False, "private": True,
        "title": "Find my buttons running",
        "description": "Set by the setup screen while Find my buttons runs: every listed button's press (the stars' hold) goes to the setup screen.",
    })
    return {"version": 1, "settings": out}


def bindings():
    """one binding per press and hold; the app reads which choice it is (Keys.run)"""
    out = []
    action = "com.deflight.buttonmapper.PRESS"
    for key, _ in BUTTONS:
        if key not in STARS:
            out.append({"key": key, "press": "short", "stock": "replace", "when": "settings.%s_on" % key, "broadcast": action})
        out.append({"key": key, "press": "hold", "when": "settings.%s_hold_on" % key, "broadcast": action})
    # the car's own media keys: HMG-API presses that button instead (Choices.carKey)
    for key, _ in BUTTONS:
        for hold in (False, True):
            if not hold and key in STARS:
                continue
            slot = key + ("_hold" if hold else "")
            for send, spec in CAR_KEYS:
                b = {"key": key, "press": "hold" if hold else "short"}
                if not hold:
                    b["stock"] = "replace"
                b.update({"when": "settings.%s_send=%s" % (slot, send), "send": spec})
                out.append(b)
    # Find my buttons: the press (a star's hold, its press is the car's ★ menu's), taken from the car
    for key, _ in BUTTONS:
        if key in STARS:
            out.append({"key": key, "press": "hold", "when": "settings.finding", "broadcast": "com.deflight.buttonmapper.FOUND"})
        else:
            out.append({"key": key, "press": "short", "stock": "replace", "when": "settings.finding", "broadcast": "com.deflight.buttonmapper.FOUND"})
    return out


def main():
    with open(os.path.join(ROOT, "settings.json"), "w") as f:
        json.dump(settings(), f, indent=2)
        f.write("\n")
    p = os.path.join(ROOT, "mod.json")
    with open(p) as f:
        mod = json.load(f)
    mod["input"] = bindings()
    with open(p, "w") as f:
        json.dump(mod, f, indent=2)
        f.write("\n")
    print("settings.json: %d settings; mod.json: %d bindings" % (len(settings()["settings"]), len(mod["input"])))


if __name__ == "__main__":
    main()
