package com.deflight.buttonmapper.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import hmgapi.hook.ModContext;

/**
 * Runs in the car's home menu (com.android.launcher3, the system user like the car's mode
 * manager): opens what the Button Mapper app asks for as the menu itself would. The car's own
 * screens (Climate ...) don't open for an ordinary app; started by the system user they do.
 * Asked by an ordered broadcast, only from an app holding
 * com.deflight.buttonmapper.permission.OPEN (signature: Button Mapper' own key):
 *   OPEN   extra "intent": an Intent to start (an activity), or with "broadcast" true to send
 *   MENU_BACK  Android's Back key, from the car's button device (the system user may inject it)
 *   MENU_KEY   extra "intent" with "keycode" (SEEK_UP ...) and "hold": that key, the same way;
 *              held: down, a long-press repeat at 1 s, up (as a button held 1 s)
 * The result code 1 tells the app it was done; with no answer it does it itself.
 */
final class Opener {
    private Opener() {}

    static final String PERMISSION = "com.deflight.buttonmapper.permission.OPEN";
    static final String OPEN = "com.deflight.buttonmapper.OPEN", BACK = "com.deflight.buttonmapper.MENU_BACK",
            KEY = "com.deflight.buttonmapper.MENU_KEY";

    static void install(final ModContext m, final Context c) {
        IntentFilter f = new IntentFilter();
        f.addAction(OPEN);
        f.addAction(BACK);
        f.addAction(KEY);
        c.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                try {
                    if (KEY.equals(i.getAction())) {   // a car key (SEEK_UP ...) from the car's button device
                        Intent w = i.getParcelableExtra("intent");
                        String name = w == null ? null : w.getStringExtra("keycode");
                        int code = name == null ? 0 : KeyEvent.class.getField("KEYCODE_" + name).getInt(null);
                        if (code == 0) return;
                        final int dev = carKeys(), kc = code;
                        final String n = name;
                        final long t = SystemClock.uptimeMillis();
                        back(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, kc, 0, 0, dev, 0, 0, InputDevice.SOURCE_KEYBOARD));
                        if (w.getBooleanExtra("hold", false)) {   // held: the rest off this thread
                            new Thread("ButtonMapper hold") {
                                @Override public void run() {
                                    try {
                                        SystemClock.sleep(1000);
                                        back(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, kc, 1, 0, dev, 0, KeyEvent.FLAG_LONG_PRESS, InputDevice.SOURCE_KEYBOARD));
                                        SystemClock.sleep(100);
                                        back(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, kc, 0, 0, dev, 0, 0, InputDevice.SOURCE_KEYBOARD));
                                        m.log("home menu: " + n + " held");
                                    } catch (Throwable e) {
                                        m.error("home menu: " + n + " hold", e);
                                    }
                                }
                            }.start();
                            setResultCode(1);
                            return;
                        }
                        back(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, kc, 0, 0, dev, 0, 0, InputDevice.SOURCE_KEYBOARD));
                        setResultCode(1);
                        m.log("home menu: " + name + " sent");
                        return;
                    }
                    if (BACK.equals(i.getAction())) {
                        // from the car's own button device: its key dispatcher takes keys only from a real
                        // device (HMG-API's Input.send does the same); a virtual-keyboard Back was ignored
                        int dev = carKeys();
                        int src = InputDevice.SOURCE_KEYBOARD;
                        long t = SystemClock.uptimeMillis();
                        back(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, dev, 0, 0, src));
                        back(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, dev, 0, 0, src));
                        setResultCode(1);
                        m.log("home menu: Back sent");
                        return;
                    }
                    Intent what = i.getParcelableExtra("intent");
                    if (what == null) return;
                    if (i.getBooleanExtra("broadcast", false)) ctx.sendBroadcast(what);
                    else ctx.startActivity(what.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    setResultCode(1);
                                    } catch (Throwable t) {
                    m.error("opener", t);
                }
            }
        }, f, PERMISSION, null);
        m.log("home menu opener ready");
    }

    /** the car's button device (key layout daudio...), else the first real keyboard device, else 0 */
    static int carKeys() {
        int any = 0;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual() || (d.getSources() & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) continue;
            String n = d.getName() == null ? "" : d.getName().toLowerCase();
            if (n.contains("daudio")) return id;
            if (any == 0) any = id;
        }
        return any;
    }

    static void back(KeyEvent e) throws Exception {
        Object im = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null);
        im.getClass().getMethod("injectInputEvent", android.view.InputEvent.class, int.class).invoke(im, e, 0);
    }
}
