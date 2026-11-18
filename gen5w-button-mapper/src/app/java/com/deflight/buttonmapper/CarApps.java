package com.deflight.buttonmapper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * "Car app": apps on the head unit a button can open, one entry per app. The list is read
 * from the package manager each time from the exported activities with a MAIN action (the
 * head unit's launcher and HMG-API's tiles start apps by component, so many have MAIN
 * without LAUNCHER). An app with several (com.hkmc.bluetooth has one per screen) is listed
 * once, opening its LAUNCHER activity if it has one, else one named like *Main*, else the
 * first; under the app's own label. A slot keeps the component as "package/class" in
 * "<slot>_app".
 */
final class CarApps {
    private CarApps() {}

    static final class App {
        final ComponentName cn;
        final String label;
        final Drawable icon;
        /** in COMMON: shown first, under its plain name */
        final boolean common;
        /** installed on top of the car's software (in /data/app, not a system app): mods and the like */
        final boolean added;
        App(ComponentName cn, String label, Drawable icon, boolean common, boolean added) {
            this.cn = cn; this.label = label; this.icon = icon; this.common = common; this.added = added;
        }
    }

    /*
     * Apps people open, under plain names (their own labels are often package names or
     * internal names, e.g. "HKMC_TAppEV", "VechileSettingsWP"), from the Car app list of a
     * 2025 Ioniq 6 (US, 260507): the car's own apps (system apps; NEMESIS Manager is one too),
     * and HMG-API and Button Mapper, which every car running this app has.
     * Only the ones installed on a car show. Apps installed on top of the car's software are
     * found by themselves ("Added apps"); every other app is under "Other apps".
     */
    /*
     * Common, in the car home menu's order and names, as on a US Ioniq 6 (260507): { name,
     * package, screen, package that must be installed }. A screen of "tile/<name>" is a menu
     * item that isn't a screen (Keys.tile: Climate, Valet Mode, Notifications, Hyundai Pay).
     * Screens another app may not open are opened by the hook in the home menu (Keys.viaMenu).
     * Then the settings screens and HMG-API, Button Mapper and NEMESIS. Entries whose app isn't
     * installed aren't shown.
     */
    static final String[][] COMMON_SCREENS = {
        { "Map", "com.mnsoft.navi", "com.mnsoft.navi.MapApp", "com.mnsoft.navi" },
        { "Radio", "com.hkmc.av.app.radiona", "com.hkmc.av.app.radiona.RadioMainActivity", "com.hkmc.av.app.radiona" },
        { "Media", "com.daudio.av.app.usbmusic", "com.daudio.av.app.usbmusic.ui.MusicActivity", "com.daudio.av.app.usbmusic" },
        { "Navigation Menu", "com.mnsoft.navi", "com.mnsoft.navi.NaviApp", "com.mnsoft.navi" },
        { "Voice Memo", "com.daudio.app.voicememo", "com.daudio.app.voicememo.MainActivity", "com.daudio.app.voicememo" },
        { "User's Manual", "com.android.settings", "com.android.settings.customviews.WebManualSub", "com.android.settings" },
        { "HD Radio Data", "com.hkmc.av.app.hddata", "com.hkmc.av.app.hddata.HDDataMainActivity", "com.hkmc.av.app.hddata" },
        { "Quiet Mode", "com.android.rear_care", "com.android.rear_care.RearCareModeMainActivity", "com.android.rear_care" },
        { "Hyundai Pay", "tile", "carpay", "com.hkmc.telematics.app.paymentnew" },
        { "Apple CarPlay", "com.daudio.app.dipo", "com.daudio.app.dipo.ui.DipoActivity", "com.daudio.app.dipo" },
        { "Warmer/Ventilation", "com.hkmc.telematics.service.apps.climate", "com.hkmc.telematics.service.apps.climate.activities.SWRCFrontSeatHeatVentControl", "com.hkmc.telematics.service.apps.climate" },
        { "Climate", "tile", "climate", "com.hkmc.telematics.service.apps.climate" },
        { "Notifications", "tile", "notification", "com.hkmc.telematics.apps.Notification" },
        { "EV", "com.hkmc.daudio.app.ev3", "com.hkmc.daudio.app.ev3.IgnCheckActivity", "com.hkmc.daudio.app.ev3" },
        { "Phone", "com.hkmc.bluetooth", "com.android.contacts.DialtactsActivity", "com.hkmc.bluetooth" },
        { "Bluelink", "com.hkmc.telematics.apps.Vrm.main", "com.hkmc.telematics.apps.Vrm.main.BluelinkUvoMenu", "com.hkmc.telematics.apps.Vrm.main" },
        { "Valet Mode", "tile", "valetmode", "com.hkmc.telematics.apps.Vrm.main" },
        { "Setup", "com.android.settings", "com.android.settings.Settings", "com.android.settings" },
        { "Home Screen", "com.android.launcher3", "com.android.launcher3.view.Launcher", "com.android.launcher3" },
        { "Vehicle Settings", "com.android.vehiclesettings", "com.android.vehiclesettings.VehicleSetting", "com.android.vehiclesettings" },
        { "Sound Settings", "com.android.soundsettings", "com.android.soundsettings.SoundSetting", "com.android.soundsettings" },
        { "Display Settings", "com.android.displaysettings", "com.android.displaysettings.DisplaySetting", "com.android.displaysettings" },
        { "General Settings", "com.android.generalsettings", "com.android.generalsettings.GeneralSettings", "com.android.generalsettings" },
        { "Button Settings", "com.android.buttonsettings", "com.android.buttonsettings.ButtonSettings", "com.android.buttonsettings" },
        { "Device Connection Settings", "com.android.btsettings", "com.android.settings2.bluetooth.BluetoothSettings", "com.android.btsettings" },
        { "Data/Network Settings", "com.hkmc.wifisettings", "com.hkmc.wifisettings.settings.ui.NetworkConnectionSettings", "com.hkmc.wifisettings" },
        { "Phone Projection Settings", "com.daudio.app.phoneprojection", "com.daudio.app.phoneprojection.settings.ui.DeviceConnectionSettings", "com.daudio.app.phoneprojection" },
        { "Screen Theme / Layout", "com.android.themesettings", "com.android.themesettings.MainActivity", "com.android.themesettings" },
        { "User Profile", "com.hkmc.system.app.userprofile", "com.hkmc.system.app.userprofile.UserProfileMainActivity", "com.hkmc.system.app.userprofile" },
        { "Sounds of Nature", "com.hkmc.av.app.naturesound", "com.hkmc.av.app.naturesound.ui.NatureSoundActivity", "com.hkmc.av.app.naturesound" },
        { "NEMESIS Manager", "com.nemesis.manager", "com.nemesis.manager.MainActivity", "com.nemesis.manager" },
        // every car running this app has these two
        { "HMG-API", "hmgapi.app", "hmgapi.app.MainActivity", "hmgapi.app" },
        { "Button Mapper", "com.deflight.buttonmapper", "com.deflight.buttonmapper.Setup", "com.deflight.buttonmapper" },
    };

    /** packages Common covers: not listed again under Other apps */
    static final java.util.Set<String> COMMON_PKGS = new java.util.HashSet<String>();
    static {
        for (String[] e : COMMON_SCREENS) COMMON_PKGS.add(e[3]);
        COMMON_PKGS.add("com.hkmc.av.app.mediagate");   // the menu's Media is USB Music's screen
    }

    static boolean installed(PackageManager pm, String pkg) {
        try { pm.getPackageInfo(pkg, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    /** the package of a home menu tile that isn't a screen: "tile/<name>" ("valetmode", "notification", "carpay") */
    static final String TILE = "tile";

    static String commonName(String pkg) {
        return null;
    }

    static List<App> list(Context c) {
        PackageManager pm = c.getPackageManager();
        java.util.Map<String, ActivityInfo> launcher = new java.util.HashMap<String, ActivityInfo>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            ActivityInfo a = r.activityInfo;
            if (usable(a) && !launcher.containsKey(a.packageName)) launcher.put(a.packageName, a);
        }
        // one activity per package, in the order found
        java.util.Map<String, ActivityInfo> pick = new java.util.LinkedHashMap<String, ActivityInfo>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN), 0)) {
            ActivityInfo a = r.activityInfo;
            if (!usable(a)) continue;
            ActivityInfo had = pick.get(a.packageName);
            if (launcher.containsKey(a.packageName)) a = launcher.get(a.packageName);
            else if (had != null && (!a.name.contains("Main") || had.name.contains("Main"))) continue;
            pick.put(a.packageName, a);
        }
        for (java.util.Map.Entry<String, ActivityInfo> e : launcher.entrySet()) if (!pick.containsKey(e.getKey())) pick.put(e.getKey(), e.getValue());
        List<App> out = new ArrayList<App>();
        for (String[] e : COMMON_SCREENS) {
            if (!installed(pm, e[3])) continue;
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(e[3]); } catch (Throwable t) { /* none */ }
            out.add(new App(new ComponentName(e[1], e[2]), e[0], icon, true, false));
        }
        for (ActivityInfo a : pick.values()) {
            if (COMMON_PKGS.contains(a.packageName)) continue;
            CharSequence l;
            try { l = pm.getApplicationLabel(a.applicationInfo); } catch (Throwable t) { l = null; }
            if (l == null || l.length() == 0) l = a.loadLabel(pm);
            String common = commonName(a.packageName);
            String label = common != null ? common : l == null || l.length() == 0 ? a.packageName : l.toString();
            Drawable icon;
            try { icon = pm.getApplicationIcon(a.applicationInfo); } catch (Throwable t) { icon = null; }
            int f = a.applicationInfo == null ? 0 : a.applicationInfo.flags;
            boolean added = (f & (android.content.pm.ApplicationInfo.FLAG_SYSTEM | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
            out.add(new App(new ComponentName(a.packageName, a.name), label, icon, common != null, added && common == null));
        }
        // Common keeps the menu's order; the other groups are sorted by name (a stable sort)
        Collections.sort(out, new Comparator<App>() {
            @Override public int compare(App x, App y) {
                int gx = x.common ? 0 : x.added ? 1 : 2, gy = y.common ? 0 : y.added ? 1 : 2;
                if (gx != gy) return gx - gy;
                return x.common ? 0 : x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    /** an activity another app may start: exported, enabled, no permission we don't hold */
    static boolean usable(ActivityInfo a) {
        return a != null && a.exported && a.enabled && a.permission == null;
    }

    /** a Car app pick's icon: its app's own (a menu-only item: its app's), or null */
    static Drawable icon(Context c, String flat) {
        ComponentName cn = flat == null || flat.length() == 0 ? null : ComponentName.unflattenFromString(flat);
        if (cn == null) return null;
        String pkg = cn.getPackageName();
        if (TILE.equals(pkg)) {
            pkg = null;
            for (String[] e : COMMON_SCREENS) if (e[1].equals(TILE) && e[2].equals(cn.getClassName())) pkg = e[3];
            if (pkg == null) return null;
        }
        try { return c.getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    /** the app's label, or null if it isn't installed (or the screen is gone) */
    static String label(Context c, String flat) {
        ComponentName cn = flat == null || flat.length() == 0 ? null : ComponentName.unflattenFromString(flat);
        if (cn == null) return null;
        for (String[] e : COMMON_SCREENS) if (e[1].equals(cn.getPackageName()) && e[2].equals(cn.getClassName())) return e[0];
        if (TILE.equals(cn.getPackageName())) return null;
        try {
            ActivityInfo a = c.getPackageManager().getActivityInfo(cn, 0);
            String common = commonName(cn.getPackageName());
            if (common != null) return common;
            CharSequence l = a.loadLabel(c.getPackageManager());
            return l == null || l.length() == 0 ? cn.getPackageName() : l.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
