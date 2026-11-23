package com.deflight.buttonmapper.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runs in the car's Android Auto app (com.google.android.projection.sink, "GPM"; its class
 * names aren't obfuscated). Checked against the US EV6's GPM (260507).
 *
 * Keys: the phone acts only on the keys the car lists when Android Auto connects
 * (GalConnectionSession.initGalReceiver -> GalReceiver.registerKeyCodes). Stock lists previous,
 * next, search (Assistant), mute, navigation, play, pause, media and phone. Home, Back and
 * Play/Pause are added here. A key is sent the way the car sends its own:
 * GalConnectionManager.sessionSendKeyDownEvent, then sessionSendKeyUpEvent 100 ms later.
 *
 * Play/Pause: the car's InputSource.translateKeycode turns 85 (play/pause) into 126 (play),
 * for the car's own keys too. To send the real toggle without changing the car's keys, this
 * mod lists and sends PLAY_PAUSE_MARK, which translateKeycode passes through untouched and
 * the hook below turns into 85.
 *
 * Connection: GalReceiverService.onConnected / onDisconnected -> PHONE (extras "on", "aa"),
 * so the app takes a button's Android Auto choices only while Android Auto is connected.
 */
final class AndroidAuto {
    private AndroidAuto() {}

    static final String PKG = "com.google.android.projection.sink";
    static final String SERVICE = "com.google.android.projection.service.GalReceiverService";
    static final String RECEIVER = "com.google.android.projection.protocol.GalReceiver";
    static final String INPUT = "com.google.android.projection.protocol.InputSource";
    static final String KEY = "com.deflight.buttonmapper.AA_KEY";

    static final int HOME = 3, BACK = 4, PLAY_PAUSE = 85;
    /** stands in for 85 on its way through translateKeycode (not a key the car uses) */
    static final int PLAY_PAUSE_MARK = 0x20000000 + PLAY_PAUSE;
    static final int[] ADDED = { HOME, BACK, PLAY_PAUSE_MARK };

    static ModContext mod;
    static volatile Object service;
    static boolean registered;

    static void loaded(ModContext m, LoadedApp app) {
        mod = m;
        ClassLoader cl = app.classLoader;
        try {
            Hooks.findAndHookMethod(SERVICE, cl, "onCreate", new Created());
        } catch (Throwable t) {
            m.error("Android Auto: service not found, Android Auto choices off", t);
            return;
        }
        try {
            Hooks.findAndHookMethod(RECEIVER, cl, "registerKeyCodes", int[].class, new AddKeys());
            Hooks.findAndHookMethod(INPUT, cl, "translateKeycode", int.class, new PlayPause());
        } catch (Throwable t) {
            m.error("Android Auto: key list not changed (Home, Back and Play/Pause won't reach the phone)", t);
        }
        try {
            Hooks.findAndHookMethod(SERVICE, cl, "onConnected", new Connected(true));
            Hooks.findAndHookMethod(SERVICE, cl, "onDisconnected", int.class, boolean.class, int.class, new Connected(false));
        } catch (Throwable t) {
            m.error("Android Auto connections not watched: Android Auto choices stay off", t);
        }
        m.log("watching the Android Auto app");
    }

    /** the service is up: no session yet (also after the app restarted mid-session); listen for keys */
    static final class Created extends MethodHook {
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            service = p.thisObject;
            Context c = (Context) p.thisObject;
            tell(c, false);
            register(c.getApplicationContext());
            mod.log("Android Auto service up: keys ready");
        }
    }

    static final class AddKeys extends MethodHook {
        @Override
        protected void beforeHookedMethod(Param p) throws Throwable {
            int[] stock = (int[]) p.args[0];
            if (stock == null) return;
            int[] all = new int[stock.length + ADDED.length];
            System.arraycopy(stock, 0, all, 0, stock.length);
            System.arraycopy(ADDED, 0, all, stock.length, ADDED.length);
            p.args[0] = all;
            mod.log("Android Auto keys: Home, Back and Play/Pause added to the car's " + stock.length);
        }
    }

    static final class PlayPause extends MethodHook {
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            if (((Integer) p.args[0]).intValue() == PLAY_PAUSE_MARK) p.setResult(PLAY_PAUSE);
        }
    }

    static final class Connected extends MethodHook {
        final boolean on;
        Connected(boolean on) { this.on = on; }
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            tell((Context) p.thisObject, on);
            mod.log("Android Auto " + (on ? "connected" : "disconnected") + ": told Button Mapper");
        }
    }

    static void tell(Context c, boolean on) {
        c.sendBroadcast(new Intent("com.deflight.buttonmapper.PHONE").setPackage("com.deflight.buttonmapper")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra("on", on).putExtra("aa", true));
    }

    static synchronized void register(Context c) {
        if (registered) return;
        registered = true;
        c.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                int code = i.getIntExtra("code", 0);
                String key = i.getStringExtra("key");
                long delay = i.getLongExtra("delay", 0);
                if (code == PLAY_PAUSE) code = PLAY_PAUSE_MARK;
                final int send = code;
                final String k = key;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() { send(k, send); }
                }, delay);
            }
        }, new IntentFilter(KEY), Opener.PERMISSION, null);
    }

    static void send(final String key, final int code) {
        Object s = service;
        if (s == null || code == 0) { mod.log(key + ": Android Auto isn't running"); return; }
        try {
            Field f = s.getClass().getDeclaredField("mConnectionManager");
            f.setAccessible(true);
            final Object cm = f.get(s);
            if (cm == null) { mod.log(key + ": no Android Auto connection"); return; }
            Method down = cm.getClass().getMethod("sessionSendKeyDownEvent", int.class);
            final Method up = cm.getClass().getMethod("sessionSendKeyUpEvent", int.class);
            down.invoke(cm, code);
            mod.log(key + " -> Android Auto key " + name(code) + " down");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try { up.invoke(cm, code); mod.log(key + " -> Android Auto key " + name(code) + " up"); }
                    catch (Throwable t) { mod.error(key + ": Android Auto key up failed", t); }
                }
            }, 100);
        } catch (Throwable t) {
            mod.error(key + ": Android Auto key " + name(code) + " failed", t);
        }
    }

    static String name(int code) {
        return code == PLAY_PAUSE_MARK ? "85 (play/pause)" : code >= 0x10000 ? "0x" + Integer.toHexString(code) : String.valueOf(code);
    }
}
