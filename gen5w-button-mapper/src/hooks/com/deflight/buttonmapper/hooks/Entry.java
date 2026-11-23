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
import hmgapi.hook.ModEntry;
import java.lang.reflect.Method;

/**
 * Runs in the car's CarPlay app (com.daudio.app.dipo). Keeps its CarPlayService
 * (com.daudio.app.dipo.telechips.CarPlayService, not an obfuscated name) and, on this
 * mod's REQUEST_UI broadcast (extras "key", "target"), does what the app worked out from
 * the owner's pick: a URL goes to the service's own requestUI(String), the same call the
 * CarPlay app makes for its own shortcuts; "#C:<button>" presses a CarPlay button
 * (home, play/pause, next, previous); "#Siri" starts Siri. Works while CarPlay is on screen too.
 */
public final class Entry extends ModEntry {
    static final String DIPO = "com.daudio.app.dipo";
    static final String SERVICE = "com.daudio.app.dipo.telechips.CarPlayService";
    static final String ACTION = "com.deflight.buttonmapper.REQUEST_UI";

    static volatile Object service;
    /** the CarPlay app's main service: its media commands (next, previous, play/pause) */
    static volatile Object dipoService;
    static final String DIPO_SERVICE = "com.daudio.app.dipo.service.DipoService";
    static volatile Method requestUI;
    static ModContext mod;
    static boolean registered;

    @Override
    public void appLoaded(final ModContext m, LoadedApp app) throws Throwable {
        if (Launcher.PKG.equals(app.packageName) && Launcher.PKG.equals(app.processName)) { Launcher.loaded(m, app); return; }
        if (AndroidAuto.PKG.equals(app.packageName) && AndroidAuto.PKG.equals(app.processName)) { AndroidAuto.loaded(m, app); return; }
        if (!DIPO.equals(app.packageName) || !DIPO.equals(app.processName)) return;
        mod = m;
        Hooks.findAndHookMethod(SERVICE, app.classLoader, "onCreate", new OnCreate());
        Hooks.findAndHookMethod(DIPO_SERVICE, app.classLoader, "onCreate", new DipoCreate());
        hookCreateCarPlay(app.classLoader);
        hookSessions(app.classLoader);
        m.log("watching the CarPlay app");
    }

    /*
     * createCarPlay(...) tells the phone which CarPlay buttons the car has (its consumer HID
     * device). Arguments 26 and 27 are Home and Back; stock passes false, so the phone ignores
     * ACHome. Turning them on is what makes the home key act like CarPlay's own home button.
     * (Same change as samrum/HMG-Leafia-Plus "hid-home-back" on the Ioniq 5's e.b.l().)
     * Takes effect from the next CarPlay connection.
     */
    static final int HID_HOME = 26, HID_BACK = 27, PLAYBACK = 24;

    static void hookCreateCarPlay(ClassLoader cl) throws Throwable {
        Class<?> c = Class.forName(SERVICE, false, cl);
        for (Method m : c.getDeclaredMethods()) {
            if (!"createCarPlay".equals(m.getName())) continue;
            Class<?>[] t = m.getParameterTypes();
            if (t.length != 57 || t[HID_HOME] != boolean.class || t[HID_BACK] != boolean.class
                    || !t[24].getName().endsWith("CarPlayPlaybackConfig")) {
                mod.log("createCarPlay looks different on this car: home button left off");
                return;
            }
            Object[] a = new Object[t.length + 1];
            System.arraycopy(t, 0, a, 0, t.length);
            a[t.length] = new HomeBack();
            Hooks.findAndHookMethod(SERVICE, cl, "createCarPlay", a);
            mod.log("CarPlay home/back buttons will be declared to the phone");
            return;
        }
        mod.log("no createCarPlay: home button left off");
    }

    static final class HomeBack extends MethodHook {
        @Override
        protected void beforeHookedMethod(Param p) throws Throwable {
            p.args[HID_HOME] = Boolean.TRUE;
            p.args[HID_BACK] = Boolean.TRUE;
            mod.log("createCarPlay: home and back buttons on");
            // Play/Pause: stock passes CarPlayPlaybackConfig.PlayPause, which declares separate
            // Play (HID usage 0xB0) and Pause (0xB1) buttons (libCarPlayUtils.so,
            // TccCarPlayHIDConsumer_CreateDescriptor); the phone acts on neither. PlayOrPause
            // declares the Play/Pause toggle (0xCD) instead, which onConsumer(PlayOrPause) presses.
            try {
                Class<?> pc = ((Enum<?>) p.args[PLAYBACK]).getDeclaringClass();
                p.args[PLAYBACK] = pc.getField("PlayOrPause").get(null);
                mod.log("createCarPlay: play/pause toggle on");
            } catch (Throwable t) {
                mod.error("createCarPlay: play/pause toggle left as it was", t);
            }
        }
    }

    /*
     * CarPlay sessions (CarPlayService's callbacks from the CarPlay library, readable names on
     * every car: HMG-API's dipo.carplay.session_started / session_finalized): the app hears
     * PHONE (extra "on") and takes a button's CarPlay choices only while a phone is connected;
     * otherwise the button stays the car's own.
     */
    static void hookSessions(ClassLoader cl) {
        try {
            Hooks.findAndHookMethod(SERVICE, cl, "onSessionStarted", int.class, new Session(true));
            Hooks.findAndHookMethod(SERVICE, cl, "onSessionFinalized", int.class, new Session(false));
            Hooks.findAndHookMethod(SERVICE, cl, "onSessionFailed", new Session(false));
        } catch (Throwable t) {
            mod.error("CarPlay sessions not watched: CarPlay choices stay on without a phone", t);
        }
    }

    static final class Session extends MethodHook {
        final boolean on;
        Session(boolean on) { this.on = on; }
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            Context c = (Context) p.thisObject;
            c.sendBroadcast(new Intent("com.deflight.buttonmapper.PHONE").setPackage("com.deflight.buttonmapper")
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra("on", on));
            mod.log("CarPlay session " + (on ? "started" : "ended") + ": told Button Mapper");
        }
    }

    static final class OnCreate extends MethodHook {
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            service = p.thisObject;
            requestUI = p.thisObject.getClass().getMethod("requestUI", String.class);
            register((Context) p.thisObject);
            mod.log("CarPlayService up: buttons ready");
        }
    }

    static final class DipoCreate extends MethodHook {
        @Override
        protected void afterHookedMethod(Param p) throws Throwable {
            dipoService = p.thisObject;
            mod.log("CarPlay media commands ready");
        }
    }

    static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            String key = i.getStringExtra("key");
            long delay = i.getLongExtra("delay", 0);
            // the app sends what to do: a URL, "#C:<CarPlayConsumer>" or "#Siri"
            String url = i.getStringExtra("target");
            if (url == null) { mod.log("no target for " + key); return; }
            new Handler(Looper.getMainLooper()).postDelayed(new Request(key, url), delay);
        }
    }

    static final class Request implements Runnable {
        final String key, url;
        Request(String key, String url) { this.key = key; this.url = url; }
        @Override
        public void run() { request(key, url); }
    }

    static synchronized void register(Context c) {
        if (registered) return;
        registered = true;
        c.getApplicationContext().registerReceiver(new Receiver(), new IntentFilter(ACTION));
    }

    /** "#C:<name>": a CarPlay button (CarPlayConsumer: ACHome, PlayOrPause, ScanNextTrack, ScanPreviousTrack, ...). */
    static final String BUTTON = "#C:";

    /** Siri: the car's own voice-key call, requestSiriAction(ButtonDown / ButtonUp). */
    static final String SIRI = "#Siri";

    static void request(String key, String url) {
        Object s = service;
        if (s == null || requestUI == null) { mod.log(key + ": CarPlay isn't running"); return; }
        if (url.startsWith(BUTTON)) {
            String name = url.substring(BUTTON.length());
            if (mediaCommand(key, name)) return;
            button(key, s, name);
            return;
        }
        if (SIRI.equals(url)) { siri(key, s); return; }
        try {
            requestUI.invoke(s, url);
            mod.log(key + " -> requestUI(\"" + url + "\")");
        } catch (Throwable t) {
            mod.error(key + ": requestUI failed", t);
        }
    }

    /**
     * Presses and releases a CarPlay button: CarPlayService.onConsumer(CarPlayConsumer, boolean),
     * the call the CarPlay app's own performConsumerKey() makes, down then up 100 ms later.
     * The phone acts on the buttons the car declared at connection: stock declares play/pause
     * and previous/next; Home and Back come from HomeBack above.
     */
    static void button(final String key, final Object s, String name) {
        try {
            ClassLoader cl = s.getClass().getClassLoader();
            Class<?> cc = Class.forName("com.daudio.app.dipo.telechips.CarPlayConsumer", false, cl);
            final Object b = cc.getField(name).get(null);
            final Method on = s.getClass().getMethod("onConsumer", cc, boolean.class);
            Object r = on.invoke(s, b, true);
            mod.log(key + " -> CarPlay " + name + " down: " + r);
            new Handler(Looper.getMainLooper()).postDelayed(new Up(key, s, on, b, name), 100);
        } catch (Throwable t) {
            mod.error(key + ": CarPlay " + name + " failed", t);
        }
    }

    static final class Up implements Runnable {
        final String key, name; final Object s, b; final Method on;
        Up(String key, Object s, Method on, Object b, String name) { this.key = key; this.s = s; this.on = on; this.b = b; this.name = name; }
        @Override
        public void run() {
            try { mod.log(key + " -> CarPlay " + name + " up: " + on.invoke(s, b, false)); }
            catch (Throwable t) { mod.error(key + ": CarPlay " + name + " up failed", t); }
        }
    }

    /*
     * Media keys the way the car sends its own: the CarPlay app's remote media interface
     * (com.hkmc.remote.media.IRemoteCarPlayService, what DipoService.onBind returns for
     * android.intent.action.REMOTE_CARPLAY_SERVICE), sendCommand(cmd, 0). DipoService's
     * onPerformCommand then does what the car does: 1 next track, 2 previous track,
     * with its own state updates (pressing those keys directly, button(), reaches the
     * phone but is ignored). Its 3 (Play or Pause by whether CarPlay is playing) isn't used.
     */
    static final String REMOTE = "android.intent.action.REMOTE_CARPLAY_SERVICE";

    static boolean mediaCommand(String key, String name) {
        // Play/Pause is pressed as the toggle instead (button()): command 3 sends Play or Pause,
        // which the phone ignores (see HomeBack)
        int cmd = "ScanNextTrack".equals(name) ? 1 : "ScanPreviousTrack".equals(name) ? 2 : 0;
        if (cmd == 0) return false;
        Object d = dipoService;
        if (d == null) { mod.log(key + ": CarPlay media commands not ready, pressing " + name); return false; }
        try {
            Object binder = d.getClass().getMethod("onBind", android.content.Intent.class).invoke(d, new android.content.Intent(REMOTE));
            if (binder == null) { mod.log(key + ": no remote media interface, pressing " + name); return false; }
            binder.getClass().getMethod("sendCommand", int.class, int.class).invoke(binder, cmd, 0);
            mod.log(key + " -> CarPlay media command " + cmd + " (" + name + ")");
            return true;
        } catch (Throwable t) {
            mod.error(key + ": media command " + cmd + " failed, pressing " + name, t);
            return false;
        }
    }

    /** Siri as the car's voice key does it (service.w): requestSiriAction(ButtonDown), then ButtonUp. */
    static void siri(final String key, final Object s) {
        try {
            Class<?> sa = Class.forName("com.daudio.app.dipo.telechips.CarPlaySiriAction", false, s.getClass().getClassLoader());
            final Method req = s.getClass().getMethod("requestSiriAction", sa);
            final Object down = sa.getField("ButtonDown").get(null), up = sa.getField("ButtonUp").get(null);
            req.invoke(s, down);
            mod.log(key + " -> Siri button down");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try { req.invoke(s, up); mod.log(key + " -> Siri button up"); }
                    catch (Throwable t) { mod.error(key + ": Siri button up failed", t); }
                }
            }, 100);
        } catch (Throwable t) {
            mod.error(key + ": Siri failed", t);
        }
    }
}
