package com.deflight.buttonmapper.hooks;

import android.content.Context;
import android.content.Intent;
import com.hkmc.userprofile.UserProfileListener;
import com.hkmc.userprofile.UserProfileManager;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.ModContext;

/**
 * Runs in the car's home menu (com.android.launcher3, always running): starts the Opener,
 * and tells the Button Mapper app when the car switches user profile (the car's
 * UserProfileManager, onUserSwitched: 0 Guest, 1 Profile 1, 2 Profile 2) or a profile's
 * name or picture changes, with the PROFILE broadcast (extra "user", -1 for a name or
 * picture change).
 */
final class Launcher {
    private Launcher() {}

    static final String PKG = "com.android.launcher3";

    static void loaded(final ModContext m, LoadedApp app) {
        final Context c = app.context;
        if (c == null) { m.log("home menu: no context yet, opener and profiles off"); return; }
        try { Opener.install(m, c); } catch (Throwable t) { m.error("home menu opener", t); }
        try {
            new UserProfileManager(c).addListener(new UserProfileListener() {
                @Override public void onUserSwitched(int user, boolean reset) { tell(m, c, user, "switched to " + user); }
                @Override public void onUserNameChanged(int user, String name, boolean b) { tell(m, c, -1, "name changed"); }
                @Override public void onProfileImageChanged(int user, int type, boolean b) { tell(m, c, -1, "picture changed"); }
            });
            m.log("watching the car's user profiles");
        } catch (Throwable t) {
            m.error("user profiles not watched: profiles don't switch by themselves", t);
        }
    }

    static void tell(ModContext m, Context c, int user, String what) {
        try {
            c.sendBroadcast(new Intent("com.deflight.buttonmapper.PROFILE").setPackage("com.deflight.buttonmapper")
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra("user", user));
            m.log("profile " + what + ": told Button Mapper");
        } catch (Throwable t) {
            m.error("profile " + what, t);
        }
    }
}
