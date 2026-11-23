package com.deflight.buttonmapper;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * The setup screen (the Button Mapper tile in the car's app menu): a card per button, in
 * sections, each with a Press row and a Hold row showing that slot's choice (Choices);
 * tapping a row opens a picker with the choices in groups. A slot set to Custom shows a
 * box for the app's link name under its row. Reset all puts every slot back to Default.
 * Every change is saved at once through HMG-API (Daemon.settingSet), with the "_on" flags
 * worked out (Live); HMG-API re-reads the button bindings when the settings change, so no
 * Apply or restart is needed.
 */
public final class Setup extends Activity {
    /*
     * Colours, night and day, with the cyan of the app-menu tiles. Night follows the car's
     * display (uiMode night, as the launcher's own icons do) unless the owner picks Day or
     * Night on this screen (kept in this app's preferences).
     */
    int BG_TOP, BG_BOTTOM, CARD, CARD_PRESSED, CARD_EDGE, ACCENT, ACCENT_PRESSED, ACCENT_DIM, ON_ACCENT,
        TEXT, TEXT_2, TEXT_3, CHIP, PRESS, FIELD, PANEL, DIM, BAD, LINE, WARN, WARN_DIM;

    void palette(boolean night) {
        if (night) {
            BG_TOP = 0xFF0E121A; BG_BOTTOM = 0xFF06080C;
            CARD = 0xFF161C27; CARD_PRESSED = 0xFF1F2736; CARD_EDGE = 0xFF263042;
            ACCENT = 0xFF35C9DC; ACCENT_PRESSED = 0xFF5FDCEB; ACCENT_DIM = 0x3335C9DC; ON_ACCENT = 0xFF061016;
            TEXT = 0xFFF2F5F8; TEXT_2 = 0xFF9AA4B2; TEXT_3 = 0xFF677180;
            CHIP = 0x14FFFFFF; PRESS = 0x14FFFFFF; FIELD = 0xFF0D1118; PANEL = 0xFF121822; DIM = 0xCC000000; BAD = 0xFFFF7A7A;
            LINE = 0xFF222A36; WARN = 0xFFF2A65A; WARN_DIM = 0x1AF2A65A;
        } else {
            BG_TOP = 0xFFF1F4F8; BG_BOTTOM = 0xFFE1E6ED;
            CARD = 0xFFFFFFFF; CARD_PRESSED = 0xFFE8EDF3; CARD_EDGE = 0xFFD2D9E2;
            ACCENT = 0xFF087D8F; ACCENT_PRESSED = 0xFF06697A; ACCENT_DIM = 0x55087D8F; ON_ACCENT = 0xFFFFFFFF;
            TEXT = 0xFF121821; TEXT_2 = 0xFF4A5563; TEXT_3 = 0xFF66707E;
            CHIP = 0x10000000; PRESS = 0x18000000; FIELD = 0xFFF3F5F8; PANEL = 0xFFF7F9FB; DIM = 0x99000000; BAD = 0xFFC62828;
            LINE = 0xFFE3E8EE; WARN = 0xFFB8650F; WARN_DIM = 0x1AB8650F;
        }
    }

    static final String[] LOOKS = { "auto", "day", "night" };
    static final String[] LOOK_TITLES = { "Auto", "Day", "Night" };

    String look() {
        return getSharedPreferences("setup", MODE_PRIVATE).getString("look", "auto");
    }

    boolean night() {
        String l = look();
        if ("day".equals(l)) return false;
        if ("night".equals(l)) return true;
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    static final int N = Choices.KEYS.length;
    /** slots: 2 * button + (0 press, 1 hold) */
    static final int NS = 2 * N;

    static String slot(int s) { return Choices.slot(s / 2, s % 2 == 1); }
    static String rowName(int s) { return s % 2 == 1 ? "HOLD" : "PRESS"; }

    final TextView[] value = new TextView[NS];
    final FrameLayout[] iconBox = new FrameLayout[NS];
    final TextView[] kind = new TextView[NS];
    final EditText[] custom = new EditText[NS];
    final View[] customRow = new View[NS];
    final View[] row = new View[NS];
    final TextView[] note = new TextView[N];
    TextView status;
    View reset;
    JSONObject values = new JSONObject();
    boolean loaded, textPending;
    final Handler ui = new Handler();
    Typeface light, regular, bold;
    Dialog picker;
    /** which buttons this car has (Choices.onThisCar), null: unknown, show them all */
    boolean[] has;

    /*
     * Which cards show. The model's list (Choices.onThisCar, from the car's key layout) is every
     * button that model can have, not what this car has. My buttons shows the ones the owner
     * chose (Choose my buttons; kept in this app's preferences, "mine"); All buttons shows the
     * model's whole list. A button that's set to something always shows.
     */
    android.content.SharedPreferences prefs() {
        return getSharedPreferences("setup", MODE_PRIVATE);
    }

    java.util.Set<String> mine() {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        for (String k : prefs().getString("mine" + editing, "").split(",")) if (k.length() > 0) out.add(k);
        return out;
    }

    /** All buttons (true) or My buttons; until buttons are chosen, All buttons */
    boolean showAll() {
        return prefs().getBoolean("all" + editing, mine().isEmpty());
    }

    boolean set(int i) {
        return !"off".equals(Choices.value(ed(), Choices.slot(i, false))) || !"off".equals(Choices.value(ed(), Choices.slot(i, true)));
    }

    boolean onModel(int i) {
        return has == null || has[i];
    }

    boolean shown(int i) {
        if (finding) return onModel(i);
        if (set(i)) return true;
        if (showAll()) return onModel(i);
        return mine().contains(Choices.KEYS[i]);
    }

    /*
     * Profiles (Profiles): the car's Profile 1, Profile 2 and Guest each have their own buttons
     * and their own My buttons. "editing" is the profile this screen shows; the one in use is
     * Profiles.inUse (its buttons are the top-level settings, the others under "profile.<n>").
     */
    int editing = -1;
    final String[] pname = { "Guest", "Profile 1", "Profile 2" };
    final Drawable[] ppic = new Drawable[3];
    android.content.BroadcastReceiver profileReceiver;

    /** the settings object holding the edited profile's buttons */
    JSONObject ed() {
        return editing < 0 || editing == Profiles.inUse(values) ? values : Profiles.stored(values, editing);
    }

    boolean editingInUse() {
        return editing < 0 || editing == Profiles.inUse(values);
    }

    /** Choose my buttons is running (Keys turns it off if this screen is gone) */
    static volatile boolean findingNow;
    boolean finding;
    /** the buttons checked while choosing */
    final java.util.Set<String> found = new java.util.LinkedHashSet<String>();
    final View[] cardView = new View[N];
    final TextView[] checkBox = new TextView[N];
    TextView chosenCount;
    android.content.BroadcastReceiver foundReceiver;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        bold = Typeface.create("sans-serif", Typeface.BOLD);
        has = Choices.onThisCar();
        build();
        load();
        // the car switched profile (Keys has already brought its buttons up): show the new state
        profileReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, android.content.Intent i) { if (!finding && !textPending) load(); }
        };
        registerReceiver(profileReceiver, new android.content.IntentFilter(Keys.P + "PROFILE_DONE"));
    }

    @Override
    protected void onDestroy() {
        if (profileReceiver != null) { try { unregisterReceiver(profileReceiver); } catch (Exception e) { /* gone */ } }
        super.onDestroy();
    }

    /** the car switched day/night: redraw if following it */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if ("auto".equals(look())) build();
    }

    /** Draws the whole screen in the current colours (again after a day/night change). */
    void build() {
        palette(night());
        // our own colours everywhere, whatever theme the car gives the app
        getWindow().setBackgroundDrawable(new ColorDrawable(BG_BOTTOM));
        if (picker != null) { picker.dismiss(); picker = null; }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(32), dp(14), dp(32), dp(10));
        page.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { BG_TOP, BG_BOTTOM }));

        // header: title and subtitle, the save status, Reset all, Auto/Day/Night, close
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Button Mapper", 26, TEXT, light));
        titles.addView(text("Press and hold each do their own thing  ·  Default = what the car does", 14, TEXT_2, regular));
        // the titles take what's left (the subtitle wraps), so the controls on the right always fit
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (loaded) {
            LinearLayout.LayoutParams cl0 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl0.leftMargin = dp(24);
            head.addView(profileChip(), cl0);
        }
        status = text("Loading", 15, TEXT_2, regular);
        status.setPadding(dp(16), dp(6), dp(16), dp(6));
        status.setBackgroundDrawable(round(CHIP, 0, dp(16)));
        head.addView(status);
        TextView r = text("Reset all to Default", 15, TEXT_2, regular);
        r.setPadding(dp(18), dp(10), dp(18), dp(10));
        r.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, CARD_EDGE, dp(20)));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (loaded) askReset(); }
        });
        reset = r;
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.leftMargin = dp(16);
        head.addView(phoneSwitch(new Runnable() {
            @Override public void run() { build(); }
        }), pl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.leftMargin = dp(16);
        head.addView(r, rl);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = dp(16);
        head.addView(lookSwitch(), ll);
        TextView close = text("✕", 26, TEXT_2, light);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, 0, dp(28)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(56), dp(56));
        cl.leftMargin = dp(16);
        head.addView(close, cl);
        page.addView(head);
        if (loaded && !editingInUse() && !finding) page.addView(otherBar());
        page.addView(findBar());

        // the buttons by section, two cards to a row
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        java.util.Arrays.fill(row, null);
        java.util.Arrays.fill(cardView, null);
        java.util.Arrays.fill(checkBox, null);
        int cards = 0;
        for (int i = 0; i < N; i++) if (shown(i)) cards++;
        if (cards == 0) {
            TextView none = text("No buttons here yet. Tap Choose my buttons to pick the ones you use.", 18, TEXT_2, regular);
            none.setPadding(dp(4), dp(24), dp(4), dp(24));
            grid.addView(none);
        }
        for (int sec = 0; sec < Choices.SECTIONS.length; sec++) {
            int from = Choices.SECTION_AT[sec], to = sec + 1 < Choices.SECTIONS.length ? Choices.SECTION_AT[sec + 1] : N;
            java.util.List<Integer> list = new java.util.ArrayList<Integer>();
            for (int i = from; i < to; i++) if (shown(i)) list.add(i);
            if (list.isEmpty()) continue;
            TextView st = text(Choices.SECTIONS[sec].toUpperCase(), 15, TEXT_3, regular);
            st.setPadding(dp(4), dp(12), 0, dp(8));
            grid.addView(st);
            final int per = 3;
            for (int k = 0; k < list.size(); k += per) {
                LinearLayout line = new LinearLayout(this);
                for (int j = k; j < k + per; j++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    lp.leftMargin = j == k ? 0 : dp(6);
                    lp.rightMargin = j == k + per - 1 ? 0 : dp(6);
                    lp.bottomMargin = dp(12);
                    line.addView(j < list.size() ? card(list.get(j)) : new View(this), lp);
                }
                grid.addView(line);
            }
        }
        TextView notes = text((aaSide() ? "Android Auto" : "CarPlay") + " choices work while "
                + (aaSide() ? "Android Auto" : "CarPlay") + " is connected; "
                + "otherwise the button does what the car does.", 14, TEXT_3, regular);
        notes.setPadding(dp(4), dp(4), dp(4), dp(8));
        grid.addView(notes);

        page.addView(scrolling(grid), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
        for (int s = 0; s < NS; s++) {
            if (row[s] == null) continue;
            row[s].setEnabled(loaded && !finding);
            if (loaded) { show(s); custom[s].setText(ed().optString(slot(s) + "_custom", "")); }
        }
        if (loaded) status.setVisibility(View.INVISIBLE);
    }

    /** Auto / Day / Night, a small segmented switch in the header */
    View lookSwitch() {
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackgroundDrawable(round(CHIP, 0, dp(22)));
        String now = look();
        for (int k = 0; k < LOOKS.length; k++) {
            final String l = LOOKS[k];
            boolean on = l.equals(now);
            TextView t = text(LOOK_TITLES[k], 15, on ? ON_ACCENT : TEXT_2, on ? bold : regular);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setBackgroundDrawable(on ? round(ACCENT, 0, dp(18)) : pressable(0, PRESS, 0, dp(18)));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    getSharedPreferences("setup", MODE_PRIVATE).edit().putString("look", l).commit();
                    build();
                }
            });
            seg.addView(t);
        }
        return seg;
    }

    /** One button's card: its name, a Press row and a Hold row, and a note for the ★ keys and NAV. */
    View card(final int i) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(10), dp(18), dp(6));
        cardView[i] = c;
        c.setBackgroundDrawable(round(CARD, CARD_EDGE, dp(16)));
        TextView name = text(Choices.LABELS[i].toUpperCase(), 13, ACCENT, bold);
        name.setSingleLine(true);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (finding) {   // a checkbox; tapping anywhere on the card checks or unchecks it
            TextView box = text("", 18, ON_ACCENT, bold);
            box.setGravity(Gravity.CENTER);
            checkBox[i] = box;
            top.addView(box, new LinearLayout.LayoutParams(dp(30), dp(30)));
            c.setClickable(true);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { check(Choices.KEYS[i], !found.contains(Choices.KEYS[i])); }
            });
        } else if (!showAll()) {   // ✕: this button back to Default (press and hold) and out of My buttons
            TextView x = text("✕", 16, TEXT_3, regular);
            x.setGravity(Gravity.CENTER);
            x.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, 0, dp(14)));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!loaded) return;
                    confirm("Remove " + Choices.LABELS[i] + "?", "Its press and hold go back to Default (what the car does) and it leaves My buttons. All buttons still lists it.",
                            "Remove", new Runnable() { @Override public void run() { remove(i); } });
                }
            });
            top.addView(x, new LinearLayout.LayoutParams(dp(32), dp(28)));
        }
        top.setPadding(0, 0, 0, dp(2));
        c.addView(top);
        c.addView(slotRow(2 * i));
        c.addView(slotRow(2 * i + 1));
        String key = Choices.KEYS[i];
        String n = "custom".equals(key) ? "Press: set the car's ☆ to “Button Mapper: Head Unit ☆”"
                : "wheel_custom".equals(key) ? "Press: set the car's ★ to “Button Mapper: Steering Wheel ★”"
                : "navi".equals(key) ? "3 quick presses still open NEMESIS Manager" : null;
        if (n != null) {
            TextView t = text(n, 12, TEXT_3, regular);
            t.setPadding(0, dp(4), 0, dp(2));
            note[i] = t;
            c.addView(t);
        }
        if (finding) mark(i);
        return c;
    }

    /** a card's look while choosing: checked lit, unchecked dimmed */
    void mark(int i) {
        if (cardView[i] == null) return;
        boolean on = found.contains(Choices.KEYS[i]);
        cardView[i].setBackgroundDrawable(round(on ? ACCENT_DIM : CARD, on ? ACCENT : CARD_EDGE, dp(16)));
        cardView[i].setAlpha(on ? 1f : 0.5f);
        checkBox[i].setText(on ? "✓" : "");
        checkBox[i].setBackgroundDrawable(round(on ? ACCENT : Color.TRANSPARENT, on ? 0 : TEXT_3, dp(6)));
    }

    /** checks or unchecks a button while choosing (a tap on its card, or its press on the car) */
    void check(String key, boolean on) {
        if (!finding) return;
        if (on) found.add(key); else found.remove(key);
        for (int i = 0; i < N; i++) if (Choices.KEYS[i].equals(key)) mark(i);
        if (chosenCount != null) chosenCount.setText(found.size() + " checked");
        ui.removeCallbacks(findTimeout);
        ui.postDelayed(findTimeout, 300000);
    }

    /** One slot's row: PRESS or HOLD, its choice, its tag, and the Custom box when picked. */
    View slotRow(final int s) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        if (!finding) {   // while choosing, a tap goes to the card
            r.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, 0, dp(10)));
            r.setClickable(true);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pick(s); }
            });
        }
        TextView label = text(rowName(s), 12, TEXT_3, regular);
        r.addView(label, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        iconBox[s] = new FrameLayout(this);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(30), dp(30));
        il.rightMargin = dp(10);
        r.addView(iconBox[s], il);
        value[s] = text("", 19, TEXT, light);
        value[s].setSingleLine(true);
        r.addView(value[s], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        kind[s] = text("", 11, TEXT_2, regular);
        kind[s].setPadding(dp(8), dp(2), dp(8), dp(2));
        kind[s].setBackgroundDrawable(round(CHIP, 0, dp(10)));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kl.rightMargin = dp(8);
        r.addView(kind[s], kl);
        r.addView(text("›", 24, TEXT_3, light));
        row[s] = r;
        wrap.addView(r);

        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.VERTICAL);
        cr.setPadding(dp(56), 0, 0, dp(6));
        final EditText e = new EditText(this);
        e.setSingleLine(true);   // no new lines: Enter is Done
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT_3);
        e.setTypeface(regular);
        e.setHint("App link name, e.g. spotify");
        e.setPadding(dp(16), dp(8), dp(16), dp(8));
        e.setBackgroundDrawable(round(FIELD, ACCENT_DIM, dp(12)));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                hideKeyboard(v);
                v.clearFocus();
                return true;
            }
        });
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence q, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence q, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable t) { typed(s, t.toString()); }
        });
        e.setEnabled(!finding);
        custom[s] = e;
        cr.addView(e);
        customRow[s] = cr;
        cr.setVisibility(View.GONE);
        wrap.addView(cr);
        return wrap;
    }

    /** Shows a slot's current choice on its row. */
    void show(int s) {
        if (row[s] == null) return;
        String v = Choices.value(ed(), slot(s));
        int at = Choices.index(v);
        String shown = Choices.shortTitle(at);
        boolean dim = "off".equals(v);
        if ("carapp".equals(v)) {
            String flat = ed().optString(slot(s) + "_app", "");
            String label = CarApps.label(this, flat);
            shown = flat.length() == 0 ? "Pick an app" : label == null ? "(not installed)" : label;
            dim = label == null;
        }
        value[s].setText(shown);
        iconBox[s].removeAllViews();
        android.graphics.drawable.Drawable appIcon = "carapp".equals(v) ? CarApps.icon(this, ed().optString(slot(s) + "_app", "")) : null;
        if (appIcon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageDrawable(appIcon);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iconBox[s].addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (Icon.kindOf(v) != null) {
            iconBox[s].addView(new Icon(this, Icon.kindOf(v), ACCENT, ACCENT_DIM), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        String g = Choices.groupOf(at);
        kind[s].setText(g);
        kind[s].setVisibility(g.length() == 0 ? View.GONE : View.VISIBLE);
        value[s].setTextColor(dim ? TEXT_2 : TEXT);
        customRow[s].setVisibility("custom".equals(v) ? View.VISIBLE : View.GONE);
    }

    String title(int s) {
        return Choices.LABELS[s / 2] + "  ·  " + (s % 2 == 1 ? "Hold" : "Press");
    }

    /** The picker: every choice, in groups, as tiles; the current one lit. */
    void pick(final int s) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        picker = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(DIM);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(24), dp(32), dp(24));
        panel.setBackgroundDrawable(round(PANEL, CARD_EDGE, dp(22)));
        panel.setClickable(true);   // taps inside don't close it

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(title(s), 24, TEXT, light), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams psl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        psl.rightMargin = dp(16);
        head.addView(phoneSwitch(new Runnable() {
            @Override public void run() { d.dismiss(); build(); pick(s); }
        }), psl);
        TextView x = text("✕", 24, TEXT_2, light);
        x.setGravity(Gravity.CENTER);
        x.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, 0, dp(24)));
        x.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(x, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(head);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        String now = Choices.value(ed(), slot(s));
        final int cols = 6;   // every choice on one screen on the 1920 x 720 display
        boolean aa = aaSide();
        String[] groups = Choices.groups(aa);
        for (int g = 0; g < groups.length; g++) {
            TextView gt = text(groups[g].toUpperCase(), 13, TEXT_3, bold);
            gt.setPadding(dp(4), dp(18), 0, dp(8));
            list.addView(gt);
            int[] items = Choices.groupItems(aa)[g];
            for (int r = 0; r < items.length; r += cols) {
                LinearLayout line = new LinearLayout(this);
                for (int k = r; k < r + cols; k++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1);
                    lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                    if (k >= items.length) { line.addView(new View(this), lp); continue; }
                    final int at = items[k];
                    boolean on = Choices.VALUES[at].equals(now);
                    LinearLayout t = new LinearLayout(this);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(dp(10), 0, dp(10), 0);
                    t.setBackgroundDrawable(on ? pressable(ACCENT, ACCENT_PRESSED, 0, dp(14))
                                               : pressable(CARD, CARD_PRESSED, CARD_EDGE, dp(14)));
                    String ik = Icon.kindOf(Choices.VALUES[at]);
                    if (ik != null) {
                        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(28), dp(28));
                        gl.rightMargin = dp(10);
                        t.addView(new Icon(this, ik, on ? ON_ACCENT : ACCENT, on ? 0x22000000 : ACCENT_DIM), gl);
                    }
                    TextView tl = text(Choices.shortTitle(at), 18, on ? ON_ACCENT : TEXT, on ? bold : regular);
                    tl.setSingleLine(true);
                    t.addView(tl);
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { d.dismiss(); chose(s, at); }
                    });
                    line.addView(t, lp);
                }
                list.addView(line);
            }
        }
        panel.addView(scrolling(list), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        showPanel(d, dim, panel);
    }

    void showPanel(Dialog d, FrameLayout dim, View panel) {
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        pp.setMargins(dp(64), dp(28), dp(64), dp(28));
        dim.addView(panel, pp);
        d.setContentView(dim);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        d.show();
    }

    void chose(int s, int at) {
        String k = slot(s), v = Choices.VALUES[at];
        if ("carapp".equals(v)) { appList(s); return; }   // saved once an app is picked
        boolean changed = !v.equals(Choices.value(ed(), k));
        put(k, v);
        show(s);
        if (changed) save();
        if ("custom".equals(v)) {
            custom[s].requestFocus();
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(custom[s], InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Car app: the apps on the head unit, read now, with their icons; the slot's current one lit. */
    void appList(final int s) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        picker = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(DIM);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(24), dp(32), dp(24));
        panel.setBackgroundDrawable(round(PANEL, CARD_EDGE, dp(22)));
        panel.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title(s) + "  ·  Car app", 24, TEXT, light));
        titles.addView(text("Apps installed on the head unit", 15, TEXT_2, regular));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView x = text("✕", 24, TEXT_2, light);
        x.setGravity(Gravity.CENTER);
        x.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, 0, dp(24)));
        x.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(x, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(head);

        final String now = ed().optString(slot(s) + "_app", "");
        java.util.List<CarApps.App> all = CarApps.list(this);
        final java.util.List<CarApps.App> common = new java.util.ArrayList<CarApps.App>(), added = new java.util.ArrayList<CarApps.App>(),
                other = new java.util.ArrayList<CarApps.App>();
        for (CarApps.App a : all) (a.common ? common : a.added ? added : other).add(a);
        boolean otherHasNow = false;
        for (CarApps.App a : other) otherHasNow |= a.cn.flattenToString().equals(now);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, 0);
        appGroup(list, "COMMON", common, now, s, d);
        appGroup(list, "ADDED APPS", added, now, s, d);
        if (!other.isEmpty()) {
            if (otherHasNow) {
                appGroup(list, "OTHER APPS", other, now, s, d);
            } else {
                final TextView more = text("Other apps (" + other.size() + ")  ›", 17, ACCENT, regular);
                more.setPadding(dp(16), dp(14), dp(16), dp(14));
                more.setBackgroundDrawable(pressable(Color.TRANSPARENT, PRESS, CARD_EDGE, dp(14)));
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ml.setMargins(dp(5), dp(14), dp(5), dp(5));
                list.addView(more, ml);
                more.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        list.removeView(more);
                        appGroup(list, "OTHER APPS", other, now, s, d);
                    }
                });
            }
        }
        if (all.isEmpty()) list.addView(text("No apps found.", 18, TEXT_2, regular));
        panel.addView(scrolling(list), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        showPanel(d, dim, panel);
    }

    /** one group of the Car app list: a heading, then the apps two to a row */
    void appGroup(LinearLayout list, String title, java.util.List<CarApps.App> apps, String now, final int s, final Dialog d) {
        if (apps.isEmpty()) return;
        TextView gt = text(title, 13, TEXT_3, bold);
        gt.setPadding(dp(8), dp(14), 0, dp(6));
        list.addView(gt);
        for (int r = 0; r < apps.size(); r += 2) {
            LinearLayout line = new LinearLayout(this);
            for (int k = r; k < r + 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(68), 1);
                lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                if (k >= apps.size()) { line.addView(new View(this), lp); continue; }
                final CarApps.App a = apps.get(k);
                final String flat = a.cn.flattenToString();
                boolean on = flat.equals(now);
                LinearLayout t = new LinearLayout(this);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(16), 0, dp(16), 0);
                t.setBackgroundDrawable(on ? pressable(ACCENT_DIM, ACCENT_DIM, ACCENT, dp(14)) : pressable(CARD, CARD_PRESSED, CARD_EDGE, dp(14)));
                android.widget.ImageView ic = new android.widget.ImageView(this);
                if (a.icon != null) ic.setImageDrawable(a.icon);
                ic.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                t.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout names = new LinearLayout(this);
                names.setOrientation(LinearLayout.VERTICAL);
                names.setPadding(dp(16), 0, 0, 0);
                TextView n = text(a.label, 19, TEXT, on ? bold : regular);
                n.setSingleLine(true);
                names.addView(n);
                if (!a.common && !a.added) {
                    TextView pk = text(a.cn.getPackageName(), 12, TEXT_3, regular);
                    pk.setSingleLine(true);
                    names.addView(pk);
                }
                t.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (on) t.addView(text("✓", 22, ACCENT, bold));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { d.dismiss(); pickedApp(s, flat); }
                });
                line.addView(t, lp);
            }
            list.addView(line);
        }
    }

    void pickedApp(int s, String flat) {
        put(slot(s), "carapp");
        put(slot(s) + "_app", flat);
        show(s);
        save();
    }

    /**
     * The bar under the title: My buttons | All buttons, what's shown, and Choose my buttons;
     * while choosing, what to do, how many are checked, Cancel and Done.
     */
    View findBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(10);
        bar.setLayoutParams(bl);
        if (finding) {
            bar.setBackgroundDrawable(round(ACCENT_DIM, ACCENT, dp(16)));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text("Tap the buttons you use, or press them on the car (hold ☆ ★).", 17, TEXT, regular));
            t.addView(text("Unchecked buttons go back to Default and leave My buttons.", 14, TEXT_2, regular));
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            chosenCount = text(found.size() + " checked", 16, ACCENT, bold);
            chosenCount.setPadding(dp(10), 0, dp(10), 0);
            bar.addView(chosenCount);
            bar.addView(chip("Cancel", false, new View.OnClickListener() {
                @Override public void onClick(View v) { stopFinding(false); }
            }));
            bar.addView(chip("Done", true, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!found.isEmpty()) { stopFinding(true); return; }
                    confirm("No buttons checked?", "Every button goes back to Default (what the car does).",
                            "Done", new Runnable() { @Override public void run() { stopFinding(true); } });
                }
            }));
            return bar;
        }
        bar.setBackgroundDrawable(round(CARD, CARD_EDGE, dp(16)));
        final boolean all = showAll();
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackgroundDrawable(round(CHIP, 0, dp(22)));
        String[] names = { "My buttons", "All buttons" };
        for (int k = 0; k < 2; k++) {
            final boolean wantAll = k == 1;
            boolean on = wantAll == all;
            TextView t = text(names[k], 16, on ? ON_ACCENT : TEXT_2, on ? bold : regular);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(20), dp(8), dp(20), dp(8));
            t.setBackgroundDrawable(on ? round(ACCENT, 0, dp(18)) : pressable(0, PRESS, 0, dp(18)));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (wantAll == showAll()) return;
                    prefs().edit().putBoolean("all" + editing, wantAll).commit();
                    build();
                }
            });
            seg.addView(t);
        }
        bar.addView(seg);
        int n = 0;
        for (int i = 0; i < N; i++) if (shown(i)) n++;
        String what = all ? "Every button this model can have" : "Your " + n + (n == 1 ? " button" : " buttons");
        TextView w = text(what, 16, TEXT_2, regular);
        w.setPadding(dp(16), 0, 0, 0);
        bar.addView(w, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(chip("Choose my buttons", true, new View.OnClickListener() {
            @Override public void onClick(View v) { if (loaded) startFinding(); }
        }));
        return bar;
    }

    /**
     * CarPlay | Android Auto: which phone's choices the picker shows (this app's preferences,
     * "phone"). Until picked, Android Auto when only Android Auto is connected, else CarPlay.
     * Buttons keep what they're set to either way.
     */
    boolean aaSide() {
        String p = prefs().getString("phone", "");
        if (p.length() > 0) return "aa".equals(p);
        return Live.phones(this) == Live.ANDROID_AUTO;
    }

    /** the switch; then runs after a change (the screen or picker built again) */
    View phoneSwitch(final Runnable then) {
        LinearLayout seg = new LinearLayout(this);
        seg.setGravity(Gravity.CENTER_VERTICAL);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackgroundDrawable(round(CHIP, 0, dp(22)));
        boolean aa = aaSide();
        String[] titles = { "CarPlay", "Android Auto" };
        for (int k = 0; k < 2; k++) {
            final boolean side = k == 1;
            boolean on = side == aa;
            TextView t = text(titles[k], 15, on ? ON_ACCENT : TEXT_2, on ? bold : regular);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setBackgroundDrawable(on ? round(ACCENT, 0, dp(18)) : pressable(0, PRESS, 0, dp(18)));
            if (!on) t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs().edit().putString("phone", side ? "aa" : "carplay").commit();
                    then.run();
                }
            });
            seg.addView(t);
        }
        return seg;
    }

    /** the profile being edited, in the header: picture, name, IN USE; tap for the profiles */
    View profileChip() {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(6), dp(6), dp(16), dp(6));
        t.setBackgroundDrawable(pressable(CARD, CARD_PRESSED, CARD_EDGE, dp(26)));
        t.addView(avatar(editing, 38), new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView nm = text(pname[editing], 18, TEXT, bold);
        nm.setSingleLine(true);
        nm.setPadding(dp(12), 0, 0, 0);
        t.addView(nm);
        if (editing == Profiles.inUse(values)) t.addView(inUseTag(), tagParams());
        TextView arrow = text("▾", 18, TEXT_2, regular);
        arrow.setPadding(dp(10), 0, 0, 0);
        t.addView(arrow);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (loaded && !finding) profiles(); }
        });
        return t;
    }

    TextView inUseTag() {
        TextView b = text("IN USE", 11, ON_ACCENT, bold);
        b.setPadding(dp(8), dp(2), dp(8), dp(2));
        b.setBackgroundDrawable(round(ACCENT, 0, dp(10)));
        return b;
    }

    LinearLayout.LayoutParams tagParams() {
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = dp(10);
        return bl;
    }

    /** the profiles pop-out under the chip: Profile 1, Profile 2, Guest (tap one to edit its buttons) and Follow the car's profile */
    void profiles() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        picker = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0x73000000);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        panel.setBackgroundDrawable(round(PANEL, CARD_EDGE, dp(20)));
        panel.setClickable(true);
        panel.addView(text("EDIT BUTTONS FOR", 13, TEXT_3, bold));
        int inUse = Profiles.inUse(values);
        for (final int n : Profiles.ORDER) {
            boolean ed = n == editing;
            LinearLayout t = new LinearLayout(this);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(16), dp(10), dp(16), dp(10));
            t.setBackgroundDrawable(ed ? round(ACCENT_DIM, ACCENT, dp(14)) : pressable(CARD, CARD_PRESSED, CARD_EDGE, dp(14)));
            t.addView(avatar(n, 44), new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(dp(14), 0, 0, 0);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView nm = text(pname[n], 19, TEXT, ed ? bold : regular);
            nm.setSingleLine(true);
            top.addView(nm);
            if (n == inUse) top.addView(inUseTag(), tagParams());
            names.addView(top);
            names.addView(text(Profiles.defaultName(n), 13, TEXT_2, regular));
            t.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (ed) t.addView(text("✓", 22, ACCENT, bold));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); if (n != editing) { editing = n; build(); } }
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = dp(10);
            panel.addView(t, tl);
        }
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        ll.topMargin = dp(16);
        panel.addView(line, ll);
        // Follow the car's profile
        final boolean follow = Profiles.follow(values);
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(0, dp(12), 0, 0);
        LinearLayout ft = new LinearLayout(this);
        ft.setOrientation(LinearLayout.VERTICAL);
        ft.addView(text("Follow the car's profile", 17, TEXT, regular));
        ft.addView(text(follow ? "Switches with the car" : "The profile you pick (Use now) stays in use", 13, TEXT_2, regular));
        f.addView(ft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        FrameLayout knob = new FrameLayout(this);
        knob.setBackgroundDrawable(round(follow ? ACCENT : CARD_EDGE, 0, dp(15)));
        View dot = new View(this);
        GradientDrawable dd = new GradientDrawable();
        dd.setShape(GradientDrawable.OVAL);
        dd.setColor(0xFFF2F5F8);
        dot.setBackgroundDrawable(dd);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER_VERTICAL | (follow ? Gravity.RIGHT : Gravity.LEFT));
        dl.leftMargin = dl.rightMargin = dp(3);
        knob.addView(dot, dl);
        f.addView(knob, new LinearLayout.LayoutParams(dp(52), dp(30)));
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                try { values.put("profile_follow", !follow); } catch (Exception e) { /* can't fail */ }
                if (!follow) {   // on again: the car's profile now comes up
                    int u = Profiles.carUser(Setup.this);
                    if (u >= 0) Profiles.switchTo(values, u);
                }
                build();
                save();
            }
        });
        panel.addView(f);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        pp.leftMargin = dp(300);
        pp.topMargin = dp(70);
        dim.addView(panel, pp);
        d.setContentView(dim);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        d.show();
    }

    /** a profile's picture (from the phone app) in a circle, else its first letter */
    View avatar(int n, int sizeDp) {
        Drawable pic = ppic[n];
        if (pic instanceof android.graphics.drawable.BitmapDrawable) {
            android.graphics.Bitmap src = ((android.graphics.drawable.BitmapDrawable) pic).getBitmap();
            int size = dp(sizeDp);
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.BitmapShader sh = new android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
            float sc = (float) size / Math.min(src.getWidth(), src.getHeight());
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.setScale(sc, sc);
            mx.postTranslate((size - src.getWidth() * sc) / 2f, (size - src.getHeight() * sc) / 2f);
            sh.setLocalMatrix(mx);
            pt.setShader(sh);
            cv.drawCircle(size / 2f, size / 2f, size / 2f, pt);
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(out);
            return iv;
        }
        String nm = pname[n];
        TextView t = text(nm.length() == 0 ? "?" : nm.substring(0, 1).toUpperCase(), sizeDp / 2, 0xFF061016, bold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(n == 1 ? 0xFF6FA8DC : n == 2 ? 0xFFF2A65A : 0xFF9AA4B2);
        t.setBackgroundDrawable(g);
        return t;
    }

    /** editing a profile that isn't in use: says so, Copy (the in-use profile's buttons) and Use now */
    View otherBar() {
        final int inUse = Profiles.inUse(values);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(10);
        bar.setLayoutParams(bl);
        bar.setBackgroundDrawable(round(WARN_DIM, WARN, dp(16)));
        final String me = pname[editing], them = inUse >= 0 && inUse <= 2 ? pname[inUse] : "another profile";
        String msg = "Editing " + me + "'s buttons. " + them + "'s are in use; " + me + "'s take over "
                + (Profiles.follow(values) ? "when the car switches to " + me + "." : "when you tap Use now.");
        bar.addView(text(msg, 16, TEXT, regular), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (inUse >= 0 && inUse <= 2) {
            bar.addView(chip("Copy " + them + "'s buttons", false, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    confirm("Copy " + them + "'s buttons?", me + "'s buttons are replaced with " + them + "'s (press, hold and My buttons).",
                            "Copy", new Runnable() { @Override public void run() { copyFrom(inUse); } });
                }
            }));
        }
        bar.addView(chip("Use now", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Profiles.switchTo(values, editing);
                build();
                save();
            }
        }));
        return bar;
    }

    /** the edited profile's buttons and My buttons become a copy of profile n's (the one in use) */
    void copyFrom(int n) {
        JSONObject from = Profiles.slots(n == Profiles.inUse(values) ? values : Profiles.stored(values, n));
        JSONObject to = ed();
        Profiles.clearSlots(to);
        java.util.Iterator<?> it = from.keys();
        while (it.hasNext()) {
            String k = (String) it.next();
            try { to.put(k, from.get(k)); } catch (Exception e) { /* can't fail */ }
        }
        android.content.SharedPreferences p = prefs();
        android.content.SharedPreferences.Editor e = p.edit().putString("mine" + editing, p.getString("mine" + n, ""));
        if (p.contains("all" + n)) e.putBoolean("all" + editing, p.getBoolean("all" + n, false)); else e.remove("all" + editing);
        e.commit();
        build();
        save();
    }

    TextView chip(String label, boolean main, View.OnClickListener l) {
        TextView t = text(label, 16, main ? ON_ACCENT : TEXT, main ? bold : regular);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setBackgroundDrawable(main ? pressable(ACCENT, ACCENT_PRESSED, 0, dp(18)) : pressable(Color.TRANSPARENT, PRESS, CARD_EDGE, dp(18)));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(10);
        t.setLayoutParams(p);
        return t;
    }

    /*
     * Choose my buttons: every button the model can have, each with a checkbox; My buttons start
     * checked. The "finding" setting turns on bindings that take every listed button's press (the
     * ☆ ★ keys' hold: their press belongs to the car's star menu) and send FOUND here, with the
     * buttons' own bindings off meanwhile (Live.flags), so a press checks its card instead of
     * leaving this screen. Done: the checked buttons are My buttons, the rest go back to Default.
     * Cancel, leaving the screen or 5 minutes with no tap: nothing changes.
     */
    void startFinding() {
        found.clear();
        for (int i = 0; i < N; i++) if (shown(i) && (!showAll() || set(i) || mine().contains(Choices.KEYS[i]))) found.add(Choices.KEYS[i]);
        finding = true;
        findingNow = true;
        foundReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, android.content.Intent i) {
                String k = i.getStringExtra("hmgapi.key");
                if (k != null && finding) check(k, true);
            }
        };
        registerReceiver(foundReceiver, new android.content.IntentFilter(Keys.P + "FOUND"));
        putFinding(true);
        ui.removeCallbacks(findTimeout);
        ui.postDelayed(findTimeout, 300000);
        build();
        save();
    }

    final Runnable findTimeout = new Runnable() {
        @Override public void run() { if (finding) stopFinding(false); }
    };

    void stopFinding(boolean keep) {
        if (!finding) return;
        finding = false;
        findingNow = false;
        chosenCount = null;
        ui.removeCallbacks(findTimeout);
        if (foundReceiver != null) { try { unregisterReceiver(foundReceiver); } catch (Exception e) { /* gone */ } foundReceiver = null; }
        if (keep) {
            StringBuilder b = new StringBuilder();
            for (String k : found) b.append(b.length() == 0 ? "" : ",").append(k);
            prefs().edit().putString("mine" + editing, b.toString()).putBoolean("all" + editing, found.isEmpty()).commit();
            // every button not checked goes back to Default, press and hold
            for (int i = 0; i < N; i++) {
                if (found.contains(Choices.KEYS[i])) continue;
                for (int h = 0; h < 2; h++) {
                    String sl = Choices.slot(i, h == 1);
                    ed().remove(sl);
                    ed().remove(sl + "_custom");
                    ed().remove(sl + "_app");
                }
            }
        }
        putFinding(false);
        build();
        save();
    }

    void putFinding(boolean on) {
        try { values.put("finding", on); } catch (Exception e) { /* can't fail */ }
    }

    /** A card's ✕ (My buttons): the button's press and hold back to Default (choice, custom name and car app cleared) and out of My buttons. */
    void remove(int i) {
        for (int h = 0; h < 2; h++) {
            String sl = Choices.slot(i, h == 1);
            ed().remove(sl);
            ed().remove(sl + "_custom");
            ed().remove(sl + "_app");
        }
        java.util.Set<String> m = mine();
        m.remove(Choices.KEYS[i]);
        StringBuilder b = new StringBuilder();
        for (String k : m) b.append(b.length() == 0 ? "" : ",").append(k);
        prefs().edit().putString("mine" + editing, b.toString()).putBoolean("all" + editing, false).commit();
        build();
        save();
    }

    /** Reset all: asks first, then every slot back to Default (custom names and picked apps cleared too). */
    void askReset() {
        confirm("Reset all to Default?", "Every button's press and hold for " + pname[editing < 0 ? 1 : editing] + " go back to what the car does. Their picks, custom names and car apps are cleared.",
                "Reset all", new Runnable() { @Override public void run() { resetAll(); } });
    }

    /** a question with Cancel and an action button; the action runs if it's tapped */
    void confirm(String title, String message, String action, final Runnable then) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        picker = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(DIM);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(40), dp(32), dp(40), dp(28));
        panel.setBackgroundDrawable(round(PANEL, CARD_EDGE, dp(22)));
        panel.setClickable(true);
        panel.addView(text(title, 28, TEXT, light));
        TextView m = text(message, 18, TEXT_2, regular);
        m.setPadding(0, dp(12), 0, dp(24));
        panel.addView(m);
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.RIGHT);
        TextView no = text("Cancel", 19, TEXT, regular);
        no.setGravity(Gravity.CENTER);
        no.setPadding(dp(28), dp(14), dp(28), dp(14));
        no.setBackgroundDrawable(pressable(CARD, CARD_PRESSED, CARD_EDGE, dp(14)));
        no.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        btns.addView(no);
        TextView yes = text(action, 19, ON_ACCENT, bold);
        yes.setGravity(Gravity.CENTER);
        yes.setPadding(dp(28), dp(14), dp(28), dp(14));
        yes.setBackgroundDrawable(pressable(ACCENT, ACCENT_PRESSED, 0, dp(14)));
        yes.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); then.run(); }
        });
        LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        yl.leftMargin = dp(16);
        btns.addView(yes, yl);
        panel.addView(btns);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(760), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        dim.addView(panel, pp);
        d.setContentView(dim);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        d.show();
    }

    void resetAll() {
        ui.removeCallbacks(saveText);
        textPending = false;
        Profiles.clearSlots(ed());
        for (int s = 0; s < NS; s++) {
            if (row[s] == null) continue;
            show(s);
            custom[s].setText("");
        }
        save();
    }

    final Runnable saveText = new Runnable() {
        @Override public void run() { textPending = false; save(); }
    };

    void typed(int s, String t) {
        if (!loaded) return;
        String clean = t.replaceAll("\\s+", "");
        if (clean.equals(ed().optString(slot(s) + "_custom", ""))) return;
        put(slot(s) + "_custom", clean);
        ui.removeCallbacks(saveText);
        textPending = true;
        ui.postDelayed(saveText, 800);   // once typing pauses
    }

    void put(String k, String v) {
        try { ed().put(k, v); } catch (Exception e) { /* String keys and values: can't fail */ }
    }

    void load() {
        new Thread("ButtonMapper load") {
            @Override public void run() {
                String err = null;
                JSONObject v = null;
                try {
                    String t = Daemon.settingGet().trim();
                    v = new JSONObject(t.length() == 0 ? "{}" : t);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final String[] names = new String[3];
                final Drawable[] pics = new Drawable[3];
                Object m = Profiles.manager(Setup.this);
                for (int n = 0; n < 3; n++) { names[n] = Profiles.name(Setup.this, m, n); pics[n] = Profiles.picture(Setup.this, m, n); }
                ui.post(new Runnable() {
                    @Override public void run() { System.arraycopy(names, 0, pname, 0, 3); System.arraycopy(pics, 0, ppic, 0, 3); }
                });
                final JSONObject got = v;
                final String why = err;
                ui.post(new Runnable() {
                    @Override public void run() { loaded(got, why); }
                });
            }
        }.start();
    }

    void loaded(JSONObject v, String err) {
        if (v == null) {
            status(false, "Can't reach HMG-API: " + err);
            return;
        }
        values = v;
        boolean moved = Profiles.adopt(this, values);   // the first time: the car's profile now is the one in use
        if (editing < 0 || editing > 2) editing = Profiles.inUse(values);
        loaded = true;
        build();   // which cards show can depend on the settings (a set button stays shown)
        status.setVisibility(View.INVISIBLE);
        boolean stale = values.optBoolean("finding", false);   // left on (the screen was closed mid-way)
        if (stale) putFinding(false);
        if (moved || stale || Live.flags(values, Live.phones(this))) save();
    }

    void save() {
        Live.flags(values, Live.phones(this));
        final String json = values.toString();
        status(true, "Saving");
        final Context app = getApplicationContext();
        new Thread("ButtonMapper save") {
            @Override public void run() {
                String err = null;
                synchronized (Live.LOCK) {
                    try { Daemon.settingSet(json); } catch (Exception e) { err = e.getMessage(); }
                }
                final String why = err;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (why == null) {
                            status(true, "✓  Saved");
                            ui.removeCallbacks(hideStatus);
                            ui.postDelayed(hideStatus, 2500);
                        } else {
                            status(false, "Not saved: " + why);
                        }
                    }
                });
            }
        }.start();
    }

    final Runnable hideStatus = new Runnable() {
        @Override public void run() { status.setVisibility(View.INVISIBLE); }
    };

    void status(boolean ok, String s) {
        status.setText(s);
        status.setTextColor(ok ? ACCENT : BAD);
        status.setVisibility(View.VISIBLE);
    }

    /** leaving the screen: a custom name still waiting for a pause in typing is saved now */
    @Override
    protected void onPause() {
        super.onPause();
        if (finding) stopFinding(false);
        if (loaded && textPending) {
            ui.removeCallbacks(saveText);
            textPending = false;
            save();
        }
    }

    /**
     * content in a scroll view with a scroll bar always shown on its right (track and
     * thumb in this screen's colours) when there's more than fits, so it's clear there's more
     */
    View scrolling(View content) {
        LinearLayout row = new LinearLayout(this);
        Scroller sv = new Scroller(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content);
        row.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        Bar bar = new Bar(this, sv, CARD_EDGE, ACCENT);
        sv.bar = bar;
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.MATCH_PARENT);
        bl.leftMargin = dp(12);
        bl.topMargin = dp(6);
        bl.bottomMargin = dp(6);
        row.addView(bar, bl);
        return row;
    }

    static final class Scroller extends ScrollView {
        Bar bar;
        Scroller(Context c) { super(c); }
        int range() { return computeVerticalScrollRange(); }
        int offset() { return computeVerticalScrollOffset(); }
        int extent() { return computeVerticalScrollExtent(); }
        @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            if (bar != null) bar.invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (bar != null) bar.postInvalidate();
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (bar != null) bar.postInvalidate();
        }
    }

    /** the scroll bar: a rounded track, the thumb sized and placed by what's shown; hidden if everything fits */
    static final class Bar extends View {
        final Scroller sv;
        final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint thumb = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF r = new android.graphics.RectF();

        Bar(Context c, Scroller sv, int trackColour, int thumbColour) {
            super(c);
            this.sv = sv;
            track.setColor(trackColour);
            thumb.setColor(thumbColour);
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            int range = sv.range(), extent = sv.extent(), offset = sv.offset();
            if (range <= extent + 1) return;   // all of it fits: no bar
            float w = getWidth(), h = getHeight(), rad = w / 2f;
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, track);
            float th = Math.max(h * extent / range, w * 3);
            float top = (h - th) * offset / (float) (range - extent);
            r.set(0, top, w, top + th);
            c.drawRoundRect(r, rad, rad, thumb);
        }
    }

    void hideKeyboard(View v) {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    TextView text(String s, int sp, int colour, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    static GradientDrawable round(int fill, int edge, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (edge != 0) g.setStroke(2, edge);
        return g;
    }

    static Drawable pressable(int fill, int pressed, int edge, int radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_pressed }, round(pressed, edge, radius));
        s.addState(new int[0], round(fill, edge, radius));
        return s;
    }
}
