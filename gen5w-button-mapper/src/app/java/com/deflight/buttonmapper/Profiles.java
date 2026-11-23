package com.deflight.buttonmapper;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.json.JSONObject;

/**
 * The car's user profiles (Profile 1, Profile 2, Guest), each with its own buttons.
 *
 * The car's profile service is the framework's com.hkmc.userprofile.UserProfileManager
 * (automotive.jar): getUserType() 0 Guest, 1 Profile 1, 2 Profile 2 (-1 none);
 * getUserName(n); getUserImg(n), 4 = a picture from the phone app, read with
 * getTmsProfileImage(n). Reached by reflection: it's on the car, not in the SDK.
 *
 * In HMG-API's settings the top-level slot settings are the profile in use ("profile",
 * what the buttons' bindings read); every other profile's slots are kept under
 * "profile.<n>" as an object. "profile_follow" (default on): switch when the car does.
 * A switch (the hook in the home menu sends PROFILE when the car switches; BOOT_COMPLETED;
 * Use now) puts the in-use slots away and brings the other profile's up.
 */
final class Profiles {
    private Profiles() {}

    static final int GUEST = 0;
    /** the order on the setup screen */
    static final int[] ORDER = { 1, 2, GUEST };

    static String defaultName(int n) {
        return n == GUEST ? "Guest" : "Profile " + n;
    }

    static Object manager(Context c) {
        try {
            return Class.forName("com.hkmc.userprofile.UserProfileManager").getConstructor(Context.class).newInstance(c);
        } catch (Throwable t) {
            return null;
        }
    }

    /** the car's profile now: 0 Guest, 1, 2, or -1 if the car doesn't say */
    static int carUser(Context c) {
        Object m = manager(c);
        if (m == null) return -1;
        try {
            int u = (Integer) m.getClass().getMethod("getUserType").invoke(m);
            return u >= 0 && u <= 2 ? u : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** the name the owner gave the profile in the car, else Profile 1 / Profile 2 / Guest */
    static String name(Context c, Object m, int n) {
        if (n != GUEST && m != null) {
            try {
                String s = (String) m.getClass().getMethod("getUserName", int.class).invoke(m, n);
                if (s != null && s.trim().length() > 0) return s.trim();
            } catch (Throwable t) { /* the default */ }
        }
        return defaultName(n);
    }

    /** the profile's picture when it's one from the phone app (the car's own pictures aren't readable here), else null */
    static Drawable picture(Context c, Object m, int n) {
        if (m == null || n == GUEST) return null;
        try {
            int img = (Integer) m.getClass().getMethod("getUserImg", int.class).invoke(m, n);
            if (img != 4) return null;   // ProfileImageType.IMAGE_TMS
            byte[] b = (byte[]) m.getClass().getMethod("getTmsProfileImage", int.class).invoke(m, n);
            if (b == null || b.length == 0) return null;
            android.graphics.Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
            return bm == null ? null : new BitmapDrawable(c.getResources(), bm);
        } catch (Throwable t) {
            return null;
        }
    }

    /** the profile in use (whose buttons are the top-level settings); before profiles, the car's */
    static int inUse(JSONObject v) {
        return v.optInt("profile", -1);
    }

    static boolean follow(JSONObject v) {
        return v.optBoolean("profile_follow", true);
    }

    /** a key that belongs to a profile's buttons: a slot or its _custom / _app */
    static boolean slotKey(String k) {
        String base = k.replaceAll("_(custom|app)$", "");
        for (int i = 0; i < Choices.KEYS.length; i++) {
            if (base.equals(Choices.slot(i, false)) || base.equals(Choices.slot(i, true))) return true;
        }
        return false;
    }

    /** a stored (not in use) profile's buttons, created empty (all Default) if it has none */
    static JSONObject stored(JSONObject v, int n) {
        JSONObject o = v.optJSONObject("profile." + n);
        if (o == null) {
            o = new JSONObject();
            try { v.put("profile." + n, o); } catch (Exception e) { /* can't fail */ }
        }
        return o;
    }

    /** the slots of v (top level) or of o into a new object */
    static JSONObject slots(JSONObject from) {
        JSONObject out = new JSONObject();
        java.util.Iterator<?> it = from.keys();
        while (it.hasNext()) {
            String k = (String) it.next();
            if (slotKey(k)) try { out.put(k, from.get(k)); } catch (Exception e) { /* can't fail */ }
        }
        return out;
    }

    static void clearSlots(JSONObject v) {
        java.util.List<String> drop = new java.util.ArrayList<String>();
        java.util.Iterator<?> it = v.keys();
        while (it.hasNext()) { String k = (String) it.next(); if (slotKey(k)) drop.add(k); }
        for (String k : drop) v.remove(k);
    }

    /** first time with profiles: the buttons set so far belong to the car's profile now (Profile 1 if it can't say) */
    static boolean adopt(Context c, JSONObject v) {
        if (v.has("profile")) return false;
        int u = carUser(c);
        try { v.put("profile", u < 0 ? 1 : u); } catch (Exception e) { /* can't fail */ }
        return true;
    }

    /** makes profile n the one in use: the in-use buttons put away, n's brought up. True if it changed. */
    static boolean switchTo(JSONObject v, int n) {
        int cur = inUse(v);
        if (cur == n) return false;
        try {
            if (cur >= 0) v.put("profile." + cur, slots(v));
            clearSlots(v);
            JSONObject o = v.optJSONObject("profile." + n);
            if (o != null) {
                java.util.Iterator<?> it = o.keys();
                while (it.hasNext()) { String k = (String) it.next(); v.put(k, o.get(k)); }
            }
            v.remove("profile." + n);
            v.put("profile", n);
        } catch (Exception e) {
            Log.w(Keys.TAG, "profile switch: " + e);
        }
        return true;
    }

    /**
     * The car switched profile (from the hook in the home menu) or the car started: with
     * Follow on, its buttons come up. Not on the main thread.
     */
    static void carSwitched(Context c, int user, String why) {
        synchronized (Live.LOCK) {
            try {
                String t = Daemon.settingGet().trim();
                JSONObject v = new JSONObject(t.length() == 0 ? "{}" : t);
                if (user < 0) user = carUser(c);
                boolean changed = adopt(c, v);
                if (user >= 0 && follow(v) && switchTo(v, user)) changed = true;
                if (Live.flags(v, Live.phones(c)) || changed) {
                    Daemon.settingSet(v.toString());
                    Log.i(Keys.TAG, why + ": profile " + inUse(v) + " in use");
                }
            } catch (Exception e) {
                Log.w(Keys.TAG, why + ": profile not switched: " + e.getMessage());
            }
        }
        c.sendBroadcast(new android.content.Intent(Keys.P + "PROFILE_DONE").setPackage(c.getPackageName()));
    }
}
