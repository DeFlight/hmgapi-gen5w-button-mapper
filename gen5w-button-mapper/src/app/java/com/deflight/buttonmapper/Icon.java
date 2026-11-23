package com.deflight.buttonmapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A choice's icon, drawn here (no font or image: the same on every car, day and night): a
 * rounded tile in a faint accent with a simple line glyph. Brand apps (Spotify, Waze ...) get
 * a generic glyph by kind; their logos are their owners' and aren't shipped. Car apps show the
 * app's own icon instead (Setup).
 */
final class Icon extends View {
    final String kind;
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path p = new Path();
    final RectF r = new RectF();

    Icon(Context c, String kind, int colour, int tileColour) {
        super(c);
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(colour);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(colour);
        tile.setColor(tileColour);
    }

    /** the glyph for a choice (Choices.VALUES), or null for none (Default) */
    static String kindOf(String v) {
        if (v == null || "off".equals(v)) return null;
        if (v.startsWith("aa_")) return aaKind(v.substring(3));
        if ("homekey".equals(v) || "carhome".equals(v)) return "home";
        if ("back".equals(v) || "carback".equals(v)) return "back";
        if ("maps".equals(v) || "waze".equals(v) || "googlemaps".equals(v)) return "nav";
        if ("music".equals(v) || "spotify".equals(v) || "tidal".equals(v) || "pandora".equals(v) || "amznmp3".equals(v) || "youtubemusic".equals(v)) return "note";
        if ("nowplaying".equals(v)) return "bars";
        if ("podcasts".equals(v)) return "podcast";
        if ("audiobooks".equals(v) || "audible".equals(v)) return "book";
        if ("mobilephone".equals(v) || "answercall".equals(v) || "endcall".equals(v)) return "phone";
        if ("messages".equals(v) || "whatsapp".equals(v) || "claude".equals(v)) return "bubble";
        if ("calendar".equals(v)) return "calendar";
        if ("settings".equals(v)) return "gear";
        if ("siri".equals(v)) return "mic";
        if ("playpause".equals(v)) return "playpause";
        if ("next".equals(v)) return "next";
        if ("previous".equals(v)) return "prev";
        if ("seekup".equals(v)) return "ff";
        if ("seekdown".equals(v)) return "rew";
        if ("tuneup".equals(v)) return "up";
        if ("tunedown".equals(v)) return "down";
        if ("presetup".equals(v)) return "chevup";
        if ("presetdown".equals(v)) return "chevdown";
        if ("custom".equals(v)) return "code";
        if ("carapp".equals(v)) return "grid";
        return "dot";
    }

    /** Android Auto's choices (Choices.aaCode), by the part after "aa_" */
    static String aaKind(String k) {
        if ("navigation".equals(k)) return "nav";
        if ("media".equals(k)) return "note";
        if ("assistant".equals(k)) return "mic";
        if ("previous".equals(k)) return "prev";
        return k;   // home, back, phone, playpause, play, pause, next
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float x0 = (w - s) / 2, y0 = (h - s) / 2;
        r.set(x0, y0, x0 + s, y0 + s);
        c.drawRoundRect(r, s * 0.26f, s * 0.26f, tile);
        stroke.setStrokeWidth(s * 0.075f);
        // glyph box: the middle 56%
        float g = s * 0.56f, gx = x0 + (s - g) / 2, gy = y0 + (s - g) / 2;
        draw(c, gx, gy, g);
    }

    float X(float gx, float g, float f) { return gx + g * f; }

    void tri(Canvas c, float ax, float ay, float bx, float by, float cx, float cy) {
        p.reset(); p.moveTo(ax, ay); p.lineTo(bx, by); p.lineTo(cx, cy); p.close();
        c.drawPath(p, fill);
    }

    void draw(Canvas c, float x, float y, float g) {
        String k = kind;
        if ("home".equals(k)) {
            p.reset();
            p.moveTo(x + g * .1f, y + g * .5f); p.lineTo(x + g * .5f, y + g * .12f); p.lineTo(x + g * .9f, y + g * .5f);
            p.moveTo(x + g * .22f, y + g * .42f); p.lineTo(x + g * .22f, y + g * .9f); p.lineTo(x + g * .78f, y + g * .9f); p.lineTo(x + g * .78f, y + g * .42f);
            c.drawPath(p, stroke);
        } else if ("back".equals(k)) {
            p.reset();
            p.moveTo(x + g * .38f, y + g * .2f); p.lineTo(x + g * .12f, y + g * .45f); p.lineTo(x + g * .38f, y + g * .7f);
            p.moveTo(x + g * .12f, y + g * .45f); p.lineTo(x + g * .62f, y + g * .45f);
            p.quadTo(x + g * .92f, y + g * .45f, x + g * .92f, y + g * .68f); p.quadTo(x + g * .92f, y + g * .9f, x + g * .62f, y + g * .9f);
            p.lineTo(x + g * .45f, y + g * .9f);
            c.drawPath(p, stroke);
        } else if ("nav".equals(k)) {
            p.reset();
            p.moveTo(x + g * .9f, y + g * .1f); p.lineTo(x + g * .1f, y + g * .45f); p.lineTo(x + g * .48f, y + g * .52f); p.lineTo(x + g * .55f, y + g * .9f); p.close();
            c.drawPath(p, fill);
        } else if ("note".equals(k)) {
            c.drawCircle(x + g * .3f, y + g * .75f, g * .15f, fill);
            c.drawCircle(x + g * .78f, y + g * .65f, g * .15f, fill);
            p.reset();
            p.moveTo(x + g * .43f, y + g * .75f); p.lineTo(x + g * .43f, y + g * .18f); p.lineTo(x + g * .91f, y + g * .08f); p.lineTo(x + g * .91f, y + g * .65f);
            c.drawPath(p, stroke);
        } else if ("bars".equals(k)) {
            float[] hs = { .45f, .8f, .6f, .95f };
            for (int i = 0; i < 4; i++) {
                float bx = x + g * (.14f + i * .24f);
                c.drawLine(bx, y + g * .95f, bx, y + g * (.95f - hs[i] * .85f), stroke);
            }
        } else if ("podcast".equals(k)) {
            c.drawCircle(x + g * .5f, y + g * .38f, g * .12f, fill);
            r.set(x + g * .14f, y + g * .02f, x + g * .86f, y + g * .74f);
            c.drawArc(r, 140, 260, false, stroke);
            c.drawLine(x + g * .5f, y + g * .55f, x + g * .5f, y + g * .95f, stroke);
        } else if ("book".equals(k)) {
            p.reset();
            p.moveTo(x + g * .5f, y + g * .22f); p.quadTo(x + g * .3f, y + g * .1f, x + g * .06f, y + g * .14f); p.lineTo(x + g * .06f, y + g * .84f);
            p.quadTo(x + g * .3f, y + g * .8f, x + g * .5f, y + g * .92f); p.quadTo(x + g * .7f, y + g * .8f, x + g * .94f, y + g * .84f);
            p.lineTo(x + g * .94f, y + g * .14f); p.quadTo(x + g * .7f, y + g * .1f, x + g * .5f, y + g * .22f); p.lineTo(x + g * .5f, y + g * .92f);
            c.drawPath(p, stroke);
        } else if ("phone".equals(k)) {
            p.reset();
            p.moveTo(x + g * .2f, y + g * .08f); p.lineTo(x + g * .38f, y + g * .08f); p.lineTo(x + g * .45f, y + g * .3f); p.lineTo(x + g * .33f, y + g * .4f);
            p.quadTo(x + g * .45f, y + g * .58f, x + g * .62f, y + g * .68f); p.lineTo(x + g * .72f, y + g * .56f); p.lineTo(x + g * .93f, y + g * .63f);
            p.lineTo(x + g * .93f, y + g * .82f); p.quadTo(x + g * .4f, y + g * .95f, x + g * .2f, y + g * .08f); p.close();
            c.drawPath(p, fill);
        } else if ("bubble".equals(k)) {
            r.set(x + g * .04f, y + g * .1f, x + g * .96f, y + g * .72f);
            c.drawRoundRect(r, g * .2f, g * .2f, stroke);
            p.reset(); p.moveTo(x + g * .25f, y + g * .72f); p.lineTo(x + g * .18f, y + g * .95f); p.lineTo(x + g * .45f, y + g * .72f);
            c.drawPath(p, stroke);
        } else if ("calendar".equals(k)) {
            r.set(x + g * .06f, y + g * .14f, x + g * .94f, y + g * .94f);
            c.drawRoundRect(r, g * .1f, g * .1f, stroke);
            c.drawLine(x + g * .06f, y + g * .38f, x + g * .94f, y + g * .38f, stroke);
            c.drawLine(x + g * .3f, y + g * .04f, x + g * .3f, y + g * .22f, stroke);
            c.drawLine(x + g * .7f, y + g * .04f, x + g * .7f, y + g * .22f, stroke);
        } else if ("gear".equals(k)) {
            c.drawCircle(x + g * .5f, y + g * .5f, g * .2f, stroke);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                float ix = (float) Math.cos(a), iy = (float) Math.sin(a);
                c.drawLine(x + g * (.5f + ix * .32f), y + g * (.5f + iy * .32f), x + g * (.5f + ix * .46f), y + g * (.5f + iy * .46f), stroke);
            }
        } else if ("mic".equals(k)) {
            r.set(x + g * .34f, y + g * .04f, x + g * .66f, y + g * .62f);
            c.drawRoundRect(r, g * .16f, g * .16f, fill);
            r.set(x + g * .16f, y + g * .3f, x + g * .84f, y + g * .78f);
            c.drawArc(r, 0, 180, false, stroke);
            c.drawLine(x + g * .5f, y + g * .78f, x + g * .5f, y + g * .96f, stroke);
        } else if ("playpause".equals(k)) {
            tri(c, x + g * .04f, y + g * .14f, x + g * .5f, y + g * .5f, x + g * .04f, y + g * .86f);
            c.drawLine(x + g * .68f, y + g * .16f, x + g * .68f, y + g * .84f, stroke);
            c.drawLine(x + g * .9f, y + g * .16f, x + g * .9f, y + g * .84f, stroke);
        } else if ("play".equals(k)) {
            tri(c, x + g * .2f, y + g * .12f, x + g * .86f, y + g * .5f, x + g * .2f, y + g * .88f);
        } else if ("pause".equals(k)) {
            r.set(x + g * .2f, y + g * .14f, x + g * .4f, y + g * .86f);
            c.drawRoundRect(r, g * .05f, g * .05f, fill);
            r.set(x + g * .6f, y + g * .14f, x + g * .8f, y + g * .86f);
            c.drawRoundRect(r, g * .05f, g * .05f, fill);
        } else if ("next".equals(k) || "prev".equals(k)) {
            boolean n = "next".equals(k);
            float a = n ? 0 : 1, d = n ? 1 : -1;
            tri(c, x + g * (a + d * .06f), y + g * .14f, x + g * (a + d * .46f), y + g * .5f, x + g * (a + d * .06f), y + g * .86f);
            tri(c, x + g * (a + d * .42f), y + g * .14f, x + g * (a + d * .82f), y + g * .5f, x + g * (a + d * .42f), y + g * .86f);
            c.drawLine(x + g * (a + d * .92f), y + g * .14f, x + g * (a + d * .92f), y + g * .86f, stroke);
        } else if ("ff".equals(k) || "rew".equals(k)) {
            boolean f = "ff".equals(k);
            float a = f ? 0 : 1, d = f ? 1 : -1;
            tri(c, x + g * (a + d * .04f), y + g * .14f, x + g * (a + d * .5f), y + g * .5f, x + g * (a + d * .04f), y + g * .86f);
            tri(c, x + g * (a + d * .5f), y + g * .14f, x + g * (a + d * .96f), y + g * .5f, x + g * (a + d * .5f), y + g * .86f);
        } else if ("up".equals(k)) {
            tri(c, x + g * .5f, y + g * .14f, x + g * .92f, y + g * .82f, x + g * .08f, y + g * .82f);
        } else if ("down".equals(k)) {
            tri(c, x + g * .08f, y + g * .18f, x + g * .92f, y + g * .18f, x + g * .5f, y + g * .86f);
        } else if ("chevup".equals(k) || "chevdown".equals(k)) {
            boolean u = "chevup".equals(k);
            p.reset();
            p.moveTo(x + g * .1f, y + g * (u ? .7f : .3f)); p.lineTo(x + g * .5f, y + g * (u ? .3f : .7f)); p.lineTo(x + g * .9f, y + g * (u ? .7f : .3f));
            c.drawPath(p, stroke);
        } else if ("code".equals(k)) {
            p.reset();
            p.moveTo(x + g * .32f, y + g * .2f); p.lineTo(x + g * .06f, y + g * .5f); p.lineTo(x + g * .32f, y + g * .8f);
            p.moveTo(x + g * .68f, y + g * .2f); p.lineTo(x + g * .94f, y + g * .5f); p.lineTo(x + g * .68f, y + g * .8f);
            c.drawPath(p, stroke);
        } else if ("grid".equals(k)) {
            float q = g * .4f;
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                r.set(x + g * (.04f + i * .52f), y + g * (.04f + j * .52f), x + g * (.04f + i * .52f) + q, y + g * (.04f + j * .52f) + q);
                c.drawRoundRect(r, g * .08f, g * .08f, fill);
            }
        } else {
            c.drawCircle(x + g * .5f, y + g * .5f, g * .18f, fill);
        }
    }
}
