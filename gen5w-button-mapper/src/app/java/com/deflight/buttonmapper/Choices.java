package com.deflight.buttonmapper;

import android.util.Log;

import org.json.JSONObject;

/**
 * The buttons, their choices and what each choice sends. Must match tools/gen.py
 * (settings.json and mod.json's bindings).
 *
 * Every button has two slots, its press and its hold. A slot's settings, by its name
 * (the button's HMG-API key for the press, key + "_hold" for the hold):
 *   <slot>          the choice (VALUES), "off" = Default, the car's own
 *   <slot>_custom   CarPlay: Custom's app link name
 *   <slot>_app      Car app's package/class
 *   <slot>_on       worked out by Live: HMG-API takes this press (mod.json's "when")
 * The star keys' press is picked in the car's own ★ menu (HMG-API gives mods only their
 * hold): the press slot runs when the ★ is set to this mod's entry for it, and has no
 * binding of its own.
 */
final class Choices {
    private Choices() {}

    /** button keys (HMG-API's names), their labels on the setup screen, their sections */
    static final String[] KEYS = {
        "custom", "wheel_custom",
        "home", "map", "navi", "media", "radio", "phone", "setup", "seek_up", "seek_down", "tune_up", "tune_down",
        "mode", "call", "ptt", "mute", "channel_up", "channel_down",
        "poi", "connect",
    };
    static final String[] LABELS = {
        "Head Unit ☆", "Steering Wheel ★",
        "HOME", "MAP", "NAV", "MEDIA", "RADIO", "PHONE", "SETUP", "SEEK UP", "SEEK DOWN", "TUNE UP", "TUNE DOWN",
        "MODE", "CALL", "VOICE", "MUTE", "SEEK UP", "SEEK DOWN",
        "POI", "CONNECT (Bluelink / Kia Connect)",
    };
    /**
     * each button's KeyEvent constant (without KEYCODE_), as in HMG-API's key table and the
     * car's key layout (/system/usr/keylayout): the setup screen asks Android which of them this
     * car's buttons can send (KeyCharacterMap.deviceHasKeys) and shows those
     */
    static final String[] KEYCODES = {
        "SHORTCUT_CUSTOM", "SHORTCUT_SWRC_CUSTOM",
        "SHORTCUT_HOME", "SHORTCUT_MAP", "SHORTCUT_NAVI", "SHORTCUT_MEDIA", "SHORTCUT_RADIO", "SHORTCUT_PHONE", "SHORTCUT_SETUP",
        "SEEK_UP", "SEEK_DOWN", "TUNE_UP", "TUNE_DOWN",
        "SHORTCUT_MODE", "CALL", "SHORTCUT_PTT", "MUTE", "CHANNEL_UP", "CHANNEL_DOWN",
        "SHORTCUT_POI", "SHORTCUT_BLUELINK_CENTER",
    };
    static final String[] SECTIONS = { "☆ ★ Star buttons", "Head unit", "Steering wheel", "Overhead" };
    /** first KEYS index of each section */
    static final int[] SECTION_AT = { 0, 2, 13, 19 };

    /**
     * Which buttons this car has: Android's answer from the car's own key layout file. A button
     * whose KeyEvent constant this car's Android lacks counts as missing. null if Android can't say.
     */
    static boolean[] onThisCar() {
        try {
            int[] codes = new int[KEYCODES.length];
            for (int i = 0; i < KEYCODES.length; i++) {
                try {
                    codes[i] = android.view.KeyEvent.class.getField("KEYCODE_" + KEYCODES[i]).getInt(null);
                } catch (NoSuchFieldException e) {
                    codes[i] = -1;
                }
            }
            boolean[] has = android.view.KeyCharacterMap.deviceHasKeys(codes);
            boolean any = false;
            for (int i = 0; i < has.length; i++) {
                if (codes[i] < 0) has[i] = false;
                any |= has[i];
            }
            return any ? has : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static final String HOLD = "_hold";

    static boolean isStar(String key) {
        return "custom".equals(key) || "wheel_custom".equals(key);
    }

    /** slot names: [2i] the press, [2i + 1] the hold */
    static String slot(int i, boolean hold) {
        return hold ? KEYS[i] + HOLD : KEYS[i];
    }

    static final String[] VALUES = {
        "homekey", "back", "maps", "music", "nowplaying", "podcasts", "audiobooks", "mobilephone", "messages",
        "calendar", "settings", "siri", "playpause", "next", "previous",
        "seekup", "seekdown", "tuneup", "tunedown", "presetup", "presetdown",
        "spotify", "tidal", "audible", "pandora", "amznmp3", "youtubemusic", "waze", "googlemaps", "whatsapp", "claude",
        "carhome", "carback", "answercall", "endcall", "custom", "carapp", "off",
    };
    static final String[] TITLES = {
        "CarPlay: Home Button", "CarPlay: Back", "CarPlay: Maps", "CarPlay: Music", "CarPlay: Now Playing", "CarPlay: Podcasts",
        "CarPlay: Audiobooks", "CarPlay: Phone", "CarPlay: Messages", "CarPlay: Calendar", "CarPlay: Settings",
        "CarPlay: Siri", "CarPlay: Play/Pause", "CarPlay: Next Track", "CarPlay: Previous Track",
        "Seek Up", "Seek Down", "Tune Up", "Tune Down", "Preset Up", "Preset Down",
        "CarPlay: Spotify", "CarPlay: Tidal", "CarPlay: Audible", "CarPlay: Pandora", "CarPlay: Amazon Music", "CarPlay: YouTube Music", "CarPlay: Waze", "CarPlay: Google Maps", "CarPlay: WhatsApp", "CarPlay: Claude",
        "Home", "Back", "Answer", "Reject/End", "CarPlay: Custom", "Car app", "Default",
    };

    /*
     * What the hook in the CarPlay app does with a target: a URL for requestUI, or
     * "#C:<CarPlayConsumer name>" (a CarPlay button: press, release 100 ms later), or "#Siri".
     */
    static final String HOME = "#C:ACHome";
    static final String BACK = "#C:ACBack";
    static final String PLAYPAUSE = "#C:PlayOrPause";
    static final String NEXT = "#C:ScanNextTrack";
    static final String PREVIOUS = "#C:ScanPreviousTrack";
    static final String SIRI = "#Siri";

    /** the setup screen's picker: groups of VALUES indexes, in order */
    static final String[] GROUPS = { "CarPlay", "Other CarPlay apps", "Media keys", "Car", "More" };
    static final int[][] GROUP_ITEMS = {
        range("homekey", "siri"), range("spotify", "claude"), range("playpause", "presetdown"),
        pick("carhome", "carback", "answercall", "endcall", "carapp"), pick("custom", "off"),
    };

    /** these values, in order (the More group leaves out the old app choices) */
    static int[] pick(String... vs) {
        int[] out = new int[vs.length];
        for (int i = 0; i < vs.length; i++) out[i] = index(vs[i]);
        return out;
    }

    /** a choice that works without a phone: Car app, the car's Home and Back, and the car's own media keys */
    static boolean needsPhone(String v) {
        return !("off".equals(v) || "carapp".equals(v)
                || carKey(v) != null || "carback".equals(v) || "endcall".equals(v));
    }

    /*
     * The car's own media keys: HMG-API makes the car see that button's press instead (mod.json's
     * "send" bindings, on while "<slot>_send" names it), so it does what the car does with it in
     * any source: radio, USB, Bluetooth, CarPlay. Preset Up/Down are the steering wheel's ∧/∨,
     * which the car's own ∧/∨ setting makes presets or seek.
     */
    static String carKey(String v) {
        if ("seekup".equals(v)) return "seek_up";
        if ("seekdown".equals(v)) return "seek_down";
        if ("tuneup".equals(v)) return "tune_up";
        if ("tunedown".equals(v)) return "tune_down";
        if ("presetup".equals(v)) return "channel_up";
        if ("presetdown".equals(v)) return "channel_down";
        if ("carhome".equals(v)) return "home";   // the car's HOME button
        // the car's Call button, which the car hands to the phone in use (Bluetooth or CarPlay): a press
        // answers a call (and, with one going, holds it or switches). Its hold doesn't reject on a US
        // Ioniq 6, so Reject/End is the car's end-call command instead (Keys.run)
        if ("answercall".equals(v)) return "call";
        return null;
    }

    static int[] range(String from, String to) {
        int a = index(from), b = index(to);
        int[] out = new int[b - a + 1];
        for (int i = 0; i < out.length; i++) out[i] = a + i;
        return out;
    }

    /** a choice's name on the setup screen: without "CarPlay: " */
    static String shortTitle(int at) {
        String t = TITLES[at];
        if (t.startsWith("CarPlay: ")) t = t.substring(9);
        if ("homekey".equals(VALUES[at])) t = "Home Button";
        return t;
    }

    /** the small tag on a row */
    static String groupOf(int at) {
        if ("off".equals(VALUES[at])) return "";
        if ("custom".equals(VALUES[at])) return "Custom app";
        if ("carapp".equals(VALUES[at])) return "Car app";
        if ("carhome".equals(VALUES[at]) || "carback".equals(VALUES[at]) || "answercall".equals(VALUES[at]) || "endcall".equals(VALUES[at])) return "Car";
        for (int g = 0; g < GROUPS.length; g++)
            for (int i : GROUP_ITEMS[g]) if (i == at) return g == 1 ? "CarPlay app" : GROUPS[g];
        return "";
    }

    static int index(String value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(value)) return i;
        return -1;
    }

    /** The owner's saved values (empty if HMG-API can't be reached). */
    static JSONObject saved() {
        try {
            String t = Daemon.settingGet().trim();
            return new JSONObject(t.length() == 0 ? "{}" : t);
        } catch (Exception e) {
            Log.w(Keys.TAG, "settings: " + e.getMessage());
            return new JSONObject();
        }
    }

    /** a slot's choice; every slot starts as Default */
    static String value(JSONObject saved, String slot) {
        String v = saved.optString(slot, "off");
        return index(v) < 0 ? "off" : v;
    }

    /** What a slot set to this value sends, or null (Default, nothing typed, or not a CarPlay target). */
    static String target(JSONObject saved, String slot) {
        String v = value(saved, slot);
        if ("homekey".equals(v)) return HOME;
        if ("back".equals(v)) return BACK;
        if ("siri".equals(v)) return SIRI;
        if ("playpause".equals(v)) return PLAYPAUSE;
        if ("next".equals(v)) return NEXT;
        if ("previous".equals(v)) return PREVIOUS;
        if ("calendar".equals(v)) return "calshow:";           // the phone opens Calendar for calshow:, not calendar:
        if ("settings".equals(v)) return "carplaysettings:";   // CarPlay's own Settings app
        if ("custom".equals(v)) return custom(saved.optString(slot + "_custom", ""));
        if (!needsPhone(v)) return null;
        return v + ":";
    }

    /**
     * What the owner typed: "maps" and "maps:" both mean maps:. Spaces and new lines are
     * dropped; case is kept (app IDs like com.apple.CarPlaySettings are case-sensitive).
     */
    static String custom(String typed) {
        String c = typed.replaceAll("\\s+", "");
        if (c.length() == 0) return null;
        return c.indexOf(':') >= 0 ? c : c + ":";
    }

    /** a CarPlay button press (media keys, home): no screen change first */
    static boolean isButton(String target) {
        return target != null && target.startsWith("#C:") && !HOME.equals(target) && !BACK.equals(target);
    }
}
