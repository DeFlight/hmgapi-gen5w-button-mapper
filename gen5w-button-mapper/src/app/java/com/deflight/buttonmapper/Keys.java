package com.deflight.buttonmapper;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

/**
 * Gets HMG-API's button broadcasts and the car's star (★) key choices, and does the
 * owner's pick for that press (a slot: Choices).
 * PRESS: a button's press or hold (extras hmgapi.key, hmgapi.press "short" or "hold").
 * STAR_HU / STAR_WHEEL: the car's ★ set to this mod's entry: the ★'s press slot.
 * FOUND: a press while Choose my buttons runs (the setup screen hears it); if the screen is
 *   gone (Setup.findingNow false), Choose my buttons is turned off here so the buttons work again.
 * PROFILE: from the hook in the car's home menu, the car switched user profile (extra "user":
 *   0 Guest, 1, 2; -1 a profile's name or picture changed): that profile's buttons (Profiles).
 * PHONE: from the hooks in the CarPlay and Android Auto apps, a CarPlay session (or, extra "aa"
 *   true, Android Auto) started (extra "on" true) or ended; BOOT_COMPLETED: no phone yet. Both
 *   work out which presses HMG-API takes (Live).
 *
 * A CarPlay screen sends the CarPlay app's own shortcut, com.hkmc.intent.action.DIPO_CUSTOM_SHORTCUT
 * (int extra CarPlayMode: 0 home, 1 maps, 5 now playing), which brings CarPlay up from the
 * car's own screens (the CarPlay app skips it while CarPlay is on screen), then tells this
 * mod's hook in the CarPlay app what to do (REQUEST_UI, extra "target"). A media key goes
 * to the hook alone.
 * NAV presses also count toward NEMESIS's own trigger (three NAV presses, each within
 * 1.5 s of the last, open NEMESIS Manager), which HMG-API's replaced press otherwise
 * hides from NEMESIS.
 */
public final class Keys extends BroadcastReceiver {
    static final String TAG = "ButtonMapper";
    static final String P = "com.deflight.buttonmapper.";
    static final String CONNECTED = " connected", ENDED = " ended";
    static final ComponentName NEMESIS = new ComponentName("com.nemesis.manager", "com.nemesis.manager.MainActivity");

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        final Context c = context.getApplicationContext();

        if ((P + "PROFILE").equals(action)) {
            final int user = intent.getIntExtra("user", -1);
            final PendingResult done = goAsync();
            new Thread("ButtonMapper profile") {
                @Override public void run() {
                    try { Profiles.carSwitched(c, user, user < 0 ? "profile name or picture changed" : "car switched to profile " + user); }
                    finally { done.finish(); }
                }
            }.start();
            return;
        }
        final String key, slot, why;
        if ((P + "FOUND").equals(action)) {
            if (Setup.findingNow) return;
            key = null;
            slot = null;
            why = "Choose my buttons left on";
        } else if ((P + "PHONE").equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
            boolean on = !boot && intent.getBooleanExtra("on", false);
            boolean aa = intent.getBooleanExtra("aa", false);
            Live.phone(c, boot ? 0 : aa ? Live.ANDROID_AUTO : Live.CARPLAY, on);
            key = null;
            slot = null;
            why = boot ? "boot" : (aa ? "Android Auto" : "CarPlay") + (on ? CONNECTED : ENDED);
        } else if ((P + "STAR_HU").equals(action)) {
            key = slot = "custom";
            why = null;
        } else if ((P + "STAR_WHEEL").equals(action)) {
            key = slot = "wheel_custom";
            why = null;
        } else if ((P + "PRESS").equals(action)) {
            key = intent.getStringExtra("hmgapi.key");
            if (key == null) return;
            boolean hold = "hold".equals(intent.getStringExtra("hmgapi.press"));
            if (!hold && "navi".equals(key) && thirdPress(c, key)) {
                open(c, NEMESIS, key + " x3");
                return;
            }
            slot = hold ? key + Choices.HOLD : key;
            why = null;
        } else {
            return;
        }

        // reading the settings asks HMG-API's daemon: off the main thread. A press doesn't hold the
        // broadcast (goAsync) while it runs: waiting on the home menu would hold back the next press,
        // and three quick NAV presses (NEMESIS) would no longer be quick.
        final PendingResult done = slot == null ? goAsync() : null;
        new Thread("ButtonMapper " + (slot != null ? slot : why)) {
            @Override public void run() {
                try {
                    if (slot == null) {
                        Live.update(c, why, !why.endsWith(CONNECTED) && !why.endsWith(ENDED));
                        if ("boot".equals(why)) Profiles.carSwitched(c, -1, "boot");   // the profile the car started with
                    }
                    else Keys.run(c, key, slot);
                } finally {
                    if (done != null) done.finish();
                }
            }
        }.start();
    }

    /** HMG-API key names (Choices.carKey) to their KeyEvent constants */
    static final java.util.Map<String, String> CAR_KEYCODES = new java.util.HashMap<String, String>();
    static {
        CAR_KEYCODES.put("seek_up", "SEEK_UP");
        CAR_KEYCODES.put("seek_down", "SEEK_DOWN");
        CAR_KEYCODES.put("tune_up", "TUNE_UP");
        CAR_KEYCODES.put("tune_down", "TUNE_DOWN");
        CAR_KEYCODES.put("channel_up", "CHANNEL_UP");
        CAR_KEYCODES.put("channel_down", "CHANNEL_DOWN");
        CAR_KEYCODES.put("call", "CALL");
    }

    /** does what the owner picked for this slot */
    static void run(Context c, String key, String slot) {
        JSONObject saved = Choices.saved();
        String v = Choices.value(saved, slot);
        if ("carapp".equals(v)) { openCarApp(c, saved, slot); return; }
        if ("carhome".equals(v)) {   // a press with no binding (a star key's press): the home screen, as Android's HOME does
            try {
                c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                note(c, slot + " -> home screen");
            } catch (Throwable t) {
                note(c, slot + " -> home screen failed: " + t);
            }
            return;
        }
        if ("endcall".equals(v)) {
            // the car's end-call command (ModeManager.ACTION_SHORTCUT_BTCALLEND, reason HWKEY_SWRC): the
            // CarPlay app (DiPO) answers it, while CarPlay is connected, with CarPlay's Drop (hang up /
            // reject); its receiver takes it from any app
            c.sendBroadcast(new Intent("com.hkmc.intent.action.BTCALLEND_SHORTCUT").putExtra("com.hkmc.mode.EXTRA_SHORTCUT_REASON", 0x13));
            note(c, slot + " -> end call (the car's end-call command)");
            return;
        }
        if ("rearcamera".equals(v)) {
            // the car's own Driving Rear View shortcut (what a ★ key set to the car's rear view sends):
            // the car's camera service (PgsService) turns the rear view on or off, on cars that have
            // the feature (isSupportDrivingRearView); reverse gear still comes first. Others ignore it.
            c.sendBroadcast(new Intent("com.hkmc.intent.action.DRVM_SHORTCUT"));
            note(c, slot + " -> rear camera (the car's Driving Rear View shortcut)");
            return;
        }
        if ("carback".equals(v)) {
            // the car's Back key, sent by the hook in the home menu from the car's button device; if
            // it doesn't answer, the broadcast that follows every back on the car (seen on a US
            // Ioniq 6, 260507), which the car's apps act on
            if (viaMenu(c, null, false, P + "MENU_BACK")) { note(c, slot + " -> Back key sent by the home menu"); return; }
            c.sendBroadcast(new Intent("com.android.keyboard.back"));
            note(c, slot + " -> car Back (the home menu didn't answer: com.android.keyboard.back)");
            return;
        }
        String car = Choices.carKey(v);
        if (car != null) {
            // a car key with no button binding to pass it on (a star's press, from the car's star
            // menu): the hook in the home menu sends it from the car's button device, as Back
            String kc = CAR_KEYCODES.get(car);
            if (kc != null && viaMenu(c, new Intent().putExtra("keycode", kc), false, P + "MENU_KEY")) {
                note(c, slot + " -> " + kc + " sent by the home menu");
            } else {
                note(c, slot + ": " + car + " not sent (the home menu didn't answer)");
            }
            return;
        }
        if (Choices.isAndroidAuto(v)) { toAndroidAuto(c, slot, v); return; }
        String target = Choices.target(saved, slot);
        if (target == null) { Log.w(TAG, slot + ": nothing to send (" + v + ")"); note(c, slot + ": nothing to send (" + v + ")"); return; }
        note(c, slot + " -> CarPlay " + target);
        if (Choices.isButton(target)) {
            toCarPlay(c, slot, target, 0);
            return;
        }
        int mode = "navi".equals(key) ? 1 : "media".equals(key) ? 5 : 0;
        c.sendBroadcast(new Intent("com.hkmc.intent.action.DIPO_CUSTOM_SHORTCUT").putExtra("CarPlayMode", mode));
        toCarPlay(c, slot, target, 600);
        Log.i(TAG, slot + " -> shortcut CarPlayMode=" + mode);
    }

    /** Car app: the app the owner picked for this slot on the setup screen */
    static void openCarApp(Context c, JSONObject saved, String slot) {
        String flat = saved.optString(slot + "_app", "");
        ComponentName cn = flat.length() == 0 ? null : ComponentName.unflattenFromString(flat);
        if (cn == null) { Log.w(TAG, slot + ": no car app picked"); note(c, slot + ": no car app picked"); return; }
        if (CarApps.TILE.equals(cn.getPackageName())) { tile(c, cn.getClassName(), slot); return; }
        openApp(c, cn, slot);
    }

    /*
     * An Android Auto key, sent by this mod's hook in the Android Auto app as the car sends its own.
     * A key that changes the screen first brings Android Auto up with the car's own shortcut,
     * GPM_CUSTOM_SHORTCUT (CarPlayMode 0: Android Auto as it was; skipped while it's on screen).
     */
    static void toAndroidAuto(Context c, String slot, String v) {
        int code = Choices.aaCode(v);
        long delay = 0;
        if (Choices.aaScreen(v)) {
            c.sendBroadcast(new Intent("com.hkmc.intent.action.GPM_CUSTOM_SHORTCUT").putExtra("CarPlayMode", 0));
            delay = 600;
        }
        c.sendBroadcast(new Intent(P + "AA_KEY").setPackage("com.google.android.projection.sink")
                .putExtra("key", slot).putExtra("code", code).putExtra("delay", delay));
        note(c, slot + " -> Android Auto key " + code + (delay > 0 ? " (Android Auto brought up first)" : ""));
    }

    /** to this mod's hook in the CarPlay app */
    static void toCarPlay(Context c, String slot, String target, long delay) {
        c.sendBroadcast(new Intent(P + "REQUEST_UI").setPackage("com.daudio.app.dipo")
                .putExtra("key", slot).putExtra("target", target).putExtra("delay", delay));
        Log.i(TAG, slot + " -> " + target);
    }

    /** NEMESIS's rule: presses of the same button, each within 1.5 s of the last; the third opens it. */
    static boolean thirdPress(Context c, String key) {
        SharedPreferences p = c.getSharedPreferences("presses", Context.MODE_PRIVATE);
        long now = SystemClock.elapsedRealtime();
        long last = p.getLong("t", 0);
        int n = key.equals(p.getString("k", "")) && now - last <= 1500 && now >= last ? p.getInt("n", 0) + 1 : 1;
        if (n >= 3) {
            p.edit().putString("k", "").putInt("n", 0).putLong("t", 0).commit();
            return true;
        }
        p.edit().putString("k", key).putInt("n", n).putLong("t", now).commit();
        return false;
    }

    /*
     * A car app the way the car's mode manager opens a home menu tile (ModeManagerService
     * handleEventShortcut_intent, recorded on a US Ioniq 6, 260507): MAIN, LAUNCHER, NEW_TASK |
     * REORDER_TO_FRONT, extras activityEntryPoint 0 and widget_update true. Started without them,
     * Climate, Radio and Phone close at once and the car goes to its home screen. Quiet Mode
     * (rear_care) gets its own extras, as the mode manager sends them.
     */
    /**
     * Asks the hook in the car's home menu to start (or send) this, as the system user, as the
     * menu would; waits up to 4 s for its answer. Not on the main thread. True if it did.
     */
    static boolean viaMenu(Context c, Intent what, boolean broadcast, String action) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final int[] result = { 0 };
        android.os.HandlerThread t = new android.os.HandlerThread("ButtonMapper menu");
        t.start();
        Intent ask = new Intent(action == null ? P + "OPEN" : action).setPackage("com.android.launcher3");
        if (what != null) ask.putExtra("intent", what).putExtra("broadcast", broadcast);
        c.sendOrderedBroadcast(ask, null, new BroadcastReceiver() {
            @Override public void onReceive(Context x, Intent i) { result[0] = getResultCode(); done.countDown(); }
        }, new android.os.Handler(t.getLooper()), 0, null, null);
        try { done.await(4000, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException e) { /* give up */ }
        t.quit();
        return result[0] == 1;
    }

    static void openApp(Context c, ComponentName cn, String why) {
        Intent i;
        if ("com.android.rear_care".equals(cn.getPackageName())) {
            i = new Intent().setComponent(cn).addFlags(0x10008000)
                    .putExtra("launch_by_menu_key", true).putExtra("restart_rearcaremode", true);
        } else {
            i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("activityEntryPoint", 0).putExtra("widget_update", true);
        }
        if (viaMenu(c, i, false, null)) {
            note(c, why + " -> opened " + cn.flattenToShortString() + " by the home menu");
            return;
        }
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            note(c, why + " -> opened " + cn.flattenToShortString() + " (the home menu didn't answer)");
        } catch (Throwable t) {
            note(c, why + " -> can't open " + cn.flattenToShortString() + ": " + t);
            open(c, cn, why);
        }
    }

    /*
     * Home menu tiles that aren't a screen ("valetmode", "notification", "carpay"): the menu sends a
     * broadcast and the owning app opens its screen. Valet Mode and Notifications: LAUNCH_APPLICATION
     * with the car's mode number (183, 221); Hyundai Pay: ACTION_NAVI_GCARPAY_LIST.
     */
    static void tile(Context c, String name, String why) {
        Intent i = null;
        if ("valetmode".equals(name)) i = new Intent("com.hkmc.intent.action.LAUNCH_APPLICATION").putExtra("global.mode", 183);
        else if ("notification".equals(name)) i = new Intent("com.hkmc.intent.action.LAUNCH_APPLICATION").putExtra("global.mode", 221);
        else if ("carpay".equals(name)) i = new Intent("com.hkmc.intent.action.ACTION_NAVI_GCARPAY_LIST");
        // Climate: the menu asks the climate app, which opens NewClimateControl (recorded on a US Ioniq 6, 260507)
        else if ("climate".equals(name)) i = new Intent("com.hkmc.telematics.service.apps.climate.REQUEST_CLIMATE")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("activityEntryPoint", 0);
        if (i == null) { note(c, why + ": home menu tile " + name + " isn't known"); return; }
        if (viaMenu(c, i, true, null)) { note(c, why + " -> home menu tile " + name + " sent by the home menu"); return; }
        c.sendBroadcast(i);
        note(c, why + " -> home menu tile " + name + " (" + i.getAction() + ")");
    }

    static void open(Context c, ComponentName cn, String why) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, why + " -> " + cn.flattenToShortString());
            note(c, why + " -> opened " + cn.flattenToShortString());
        } catch (Throwable t) {
            Log.e(TAG, why + ": can't open " + cn.flattenToShortString(), t);
            note(c, why + " -> can't open " + cn.flattenToShortString() + ": " + t);
        }
    }

    /** what a press did, to the log */
    static void note(Context c, String line) {
        Log.i(TAG, line);
    }
}
