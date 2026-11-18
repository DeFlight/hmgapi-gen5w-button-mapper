package com.deflight.buttonmapper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

/**
 * Which presses HMG-API takes from the car right now: each slot's "<slot>_on" setting,
 * which mod.json's bindings use as their "when". A slot is on when it isn't Default and,
 * for a choice that needs CarPlay, a phone is connected; with no phone those presses stay
 * the car's own, so a button set to a CarPlay screen still works without the phone.
 * HMG-API re-reads the bindings whenever the settings are saved.
 *
 * Worked out again when the setup screen saves, when the hook in the CarPlay app reports
 * a CarPlay session starting or ending (PHONE), and at boot (no phone yet).
 */
final class Live {
    private Live() {}

    static final Object LOCK = new Object();

    /** the phone flag counts only for this boot (a flag left from before a restart is off) */
    static boolean phone(Context c) {
        SharedPreferences p = c.getSharedPreferences("live", Context.MODE_PRIVATE);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return p.getBoolean("phone", false) && Math.abs(p.getLong("boot", 0) - boot) < 10000;
    }

    static void phone(Context c, boolean on) {
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        c.getSharedPreferences("live", Context.MODE_PRIVATE).edit().putBoolean("phone", on).putLong("boot", boot).commit();
    }

    /** sets every "<slot>_on" in v from its choice and the phone; true if any changed */
    static boolean flags(JSONObject v, boolean phone) {
        boolean changed = false;
        boolean finding = v.optBoolean("finding", false);   // Choose my buttons: every button's own bindings off
        for (int i = 0; i < Choices.KEYS.length; i++) {
            for (int h = 0; h < 2; h++) {
                boolean hold = h == 1;
                if (!hold && Choices.isStar(Choices.KEYS[i])) continue;   // the ★ press comes from the car's ★ menu
                String slot = Choices.slot(i, hold);
                String val = Choices.value(v, slot);
                String send = finding ? null : Choices.carKey(val);
                boolean on = !finding && send == null && !"off".equals(val) && (phone || !Choices.needsPhone(val));
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
                boolean phone = phone(c);
                boolean ended = endFinding && v.optBoolean("finding", false);
                if (ended) v.put("finding", false);
                if (flags(v, phone) || ended) {
                    Daemon.settingSet(v.toString());
                    Log.i(Keys.TAG, why + ": presses updated (phone " + (phone ? "connected" : "not connected") + ")");
                }
            } catch (Exception e) {
                Log.w(Keys.TAG, why + ": can't update presses: " + e.getMessage());
            }
        }
    }
}
