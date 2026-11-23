package com.deflight.buttonmapper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

/**
 * Which presses HMG-API takes from the car right now: each slot's "<slot>_on" setting,
 * which mod.json's bindings use as their "when". A slot is on when it isn't Default and,
 * for a CarPlay choice, CarPlay is connected (for an Android Auto choice, Android Auto); otherwise
 * those presses stay the car's own, so a button set to a phone screen still works without the phone.
 * HMG-API re-reads the bindings whenever the settings are saved.
 *
 * Worked out again when the setup screen saves, when the hooks in the CarPlay and Android Auto
 * apps report a phone connecting or ending (PHONE), and at boot (no phone yet).
 */
final class Live {
    private Live() {}

    static final Object LOCK = new Object();

    /** which phones are connected (bits): CarPlay, Android Auto */
    static final int CARPLAY = 1, ANDROID_AUTO = 2;

    /** the connected phones; a flag counts only for this boot (one left from before a restart is off) */
    static int phones(Context c) {
        SharedPreferences p = c.getSharedPreferences("live", Context.MODE_PRIVATE);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(p.getLong("boot", 0) - boot) >= 10000) return 0;
        return (p.getBoolean("phone", false) ? CARPLAY : 0) | (p.getBoolean("aa", false) ? ANDROID_AUTO : 0);
    }

    /** a phone connected or ended; at boot (which = 0) neither is connected */
    static void phone(Context c, int which, boolean on) {
        SharedPreferences p = c.getSharedPreferences("live", Context.MODE_PRIVATE);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean same = Math.abs(p.getLong("boot", 0) - boot) < 10000;
        SharedPreferences.Editor e = p.edit().putLong("boot", boot);
        if (which == 0 || !same) e.putBoolean("phone", false).putBoolean("aa", false);
        if (which == CARPLAY) e.putBoolean("phone", on);
        if (which == ANDROID_AUTO) e.putBoolean("aa", on);
        e.commit();
    }

    static String describe(int phones) {
        return phones == 0 ? "no phone" : ((phones & CARPLAY) != 0 ? "CarPlay" : "") + (phones == 3 ? " and " : "")
                + ((phones & ANDROID_AUTO) != 0 ? "Android Auto" : "");
    }

    /** sets every "<slot>_on" in v from its choice and the connected phones; true if any changed */
    static boolean flags(JSONObject v, int phones) {
        boolean changed = false;
        boolean finding = v.optBoolean("finding", false);   // Choose my buttons: every button's own bindings off
        for (int i = 0; i < Choices.KEYS.length; i++) {
            for (int h = 0; h < 2; h++) {
                boolean hold = h == 1;
                if (!hold && Choices.isStar(Choices.KEYS[i])) continue;   // the ★ press comes from the car's ★ menu
                String slot = Choices.slot(i, hold);
                String val = Choices.value(v, slot);
                String send = finding ? null : Choices.carKey(val);
                boolean on = !finding && send == null && !"off".equals(val) && (Choices.phoneFor(val) & ~phones) == 0;
                if (v.optBoolean(slot + "_on", false) != on) {
                    try { v.put(slot + "_on", on); } catch (Exception e) { /* can't fail */ }
                    changed = true;
                }
                String was = v.optString(slot + "_send", "none");
                if (!was.equals(send == null ? "none" : send)) {
                    try { v.put(slot + "_send", send == null ? "none" : send); } catch (Exception e) { /* can't fail */ }
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** reads the settings, works out the flags, saves if they changed. Not on the main thread. */
    static void update(Context c, String why, boolean endFinding) {
        synchronized (LOCK) {
            try {
                String t = Daemon.settingGet().trim();
                JSONObject v = new JSONObject(t.length() == 0 ? "{}" : t);
                int phones = phones(c);
                boolean ended = endFinding && v.optBoolean("finding", false);
                if (ended) v.put("finding", false);
                if (flags(v, phones) || ended) {
                    Daemon.settingSet(v.toString());
                    Log.i(Keys.TAG, why + ": presses updated (" + describe(phones) + ")");
                }
            } catch (Exception e) {
                Log.w(Keys.TAG, why + ": can't update presses: " + e.getMessage());
            }
        }
    }
}
