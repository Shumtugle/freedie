package com.shumtugle.freedie;

import java.util.Calendar;

/**
 * Between rounds. The street stays where it is and goes still; from above and below the room
 * closes in over it, the dispatcher's own, until only the old set is left with the street on
 * its tube: its edges run off the sides of the frame, the room above and below goes off into
 * the dark. Over the set the clock and the date (the real ones, the player's own), and the
 * golden cube in its slot that powers the set; under it a row of buttons, and under those the
 * level, and a key back to the one before.
 *
 * No hand is drawn: the hammer is the hand. It comes up out of the dark, big, lazy, and knocks
 * the buttons one after another, each going in with a click and lighting up: the hammer it
 * gives, the cube, the game it leads to, and last the one that goes on. The set flicks over to
 * the next channel, and the room draws back off the street, where the next building is
 * already coming down.
 *
 * All of it is drawn on the flat layer, over the frame; the still parts of the room are a
 * picture read from an asset, the tube in it left clear for the street to show through.
 */
final class Console {
    // the buttons, left to right
    static final int NEXT = 0, CUBE = 1, HAMMER = 2, GAME = 3;

    // where things are in the room's picture, its own pixels (the picture is 274 x 640)
    private static final int PW = 274, PH = 640;
    private static final int SX0 = -4, SY0 = 162, SW = 282, SH = 212;   // the tube
    private static final int SPLIT = 268;                                // above it comes down, below it comes up
    private static final int[] BX = {4, 72, 140, 208};
    private static final int BY = 422, BW = 58, BH = 32;
    private static final int DY = 474;                                   // the desk
    private static final int[] LIT = {0x4FE36A, 0xFFC93A, 0x9AA3B4, 0xFF5A3C};

    // the timeline, seconds: the hammer coming up, each knock, the channel flicking over
    static final float RISE = 0.3f, KNOCK = 0.42f, FLICK = 0.18f;
    private static final float MOVE = 0.18f, DOWN = 0.08f, STAY = 0.06f;

    private int[] art;
    private int artW, artH;
    private final int[] press = new int[4];
    private int presses;
    private int hammerKind, giftKind = -1;
    private int shown, then;                          // the level on the counter now, and after the flick
    private final boolean[] lit = new boolean[4];
    private final float[] hitAt = new float[4];
    private float t = -1f;                            // seconds into the knocking, below 0 not yet
    private int heard;                                // knocks already sounded
    private final java.util.Random rnd = new java.util.Random(11);

    /** Reads the room's picture: width, height, then RGBA rows. */
    void read(byte[] b) {
        if (b == null || b.length < 4) return;
        int w = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), h = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if (b.length < 4 + w * h * 4) return;
        art = new int[w * h];
        for (int k = 0, p = 4; k < art.length; k++, p += 4)
            art[k] = ((b[p + 3] & 0xFF) << 24) | ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
        artW = w;
        artH = h;
    }

    /**
     * A new visit: the buttons to knock, in order (the last is NEXT or GAME); the hammer in
     * hand, and the one given, if any (its kind lights the hammer button); the level shown now
     * and after the flick, as the counter counts them, from one.
     */
    void begin(int[] buttons, int n, int handKind, int gift, int levelNow, int levelThen) {
        presses = Math.min(n, press.length);
        System.arraycopy(buttons, 0, press, 0, presses);
        hammerKind = handKind;
        giftKind = gift;
        shown = levelNow;
        then = levelThen;
        java.util.Arrays.fill(lit, false);
        java.util.Arrays.fill(hitAt, -9f);
        t = 0f;
        heard = 0;
    }

    /** How long the knocking and the flick take, seconds. */
    float length() {
        return RISE + presses * KNOCK + FLICK;
    }

    /** Moves on; returns the button knocked just now, or -1. */
    int update(float dt) {
        if (t < 0f) return -1;
        t += dt;
        if (heard < presses && t >= RISE + heard * KNOCK + MOVE + DOWN) {
            int b = press[heard];
            lit[b] = true;
            hitAt[b] = t;
            heard++;
            return b;
        }
        return -1;
    }

    /** Whether the set has flicked over to the next channel. */
    boolean flicked() {
        return t >= RISE + presses * KNOCK;
    }

    void stop() {
        t = -1f;
    }

    // ---- drawing ----

    /**
     * The room over the frame, as far as it has closed in (0: not at all, 1: only the tube
     * left), and the hammer, as far as it is up out of the dark (0 to 1).
     */
    void draw(Px f, float cover, float handUp) {
        if (art == null || cover <= 0f) return;
        int fw = f.w, fh = f.h;
        int oy = (fh - PH) / 2;                         // the picture centred on the frame, cut at the top and bottom
        int split = SPLIT + oy;
        float e = cover * cover * (3f - 2f * cover);
        int top = Math.round(-(1f - e) * split), bot = Math.round((1f - e) * (fh - split));
        int[] a = f.a;
        for (int y = 0; y < fh; y++) {
            boolean upper = y < split + top;
            boolean lower = y >= split + bot;
            if (!upper && !lower) continue;             // the gap between, where the street still shows whole
            int sy = (upper ? y - top : y - bot) - oy;
            int row = y * fw;
            for (int x = 0; x < fw; x++) {
                int c = sy < 0 || sy >= artH || x >= artW ? 0xFF000000 : art[sy * artW + x];
                if ((c >>> 24) == 0) continue;
                a[row + x] = c;
            }
        }
        tube(f, oy, e);
        int dTop = top + oy, dBot = bot + oy;           // where the moving halves are, in frame pixels
        clock(f, dTop);
        buttons(f, dBot);
        counter(f, dBot);
        hammer(f, dBot, handUp, fh);
    }

    /** The glass: its lines, darker at its edges, a sheen at the top; a moment of noise as it flicks. */
    private void tube(Px f, int oy, float e) {
        boolean noise = t >= 0f && flicked() && t < RISE + presses * KNOCK + FLICK;
        int y0 = Math.max(0, SY0 + oy), y1 = Math.min(f.h, SY0 + SH + oy);
        int x0 = Math.max(0, SX0), x1 = Math.min(f.w, SX0 + SW);
        for (int y = y0; y < y1; y++) {
            float v = (y - oy - SY0 - SH * 0.5f) / (SH * 0.5f);
            for (int x = x0; x < x1; x++) {
                int k = y * f.w + x;
                if ((f.a[k] >>> 24) != 0) continue;         // the room, or the corners of the tube
                float u = (x - SX0 - SW * 0.5f) / (SW * 0.5f);
                if (noise) {
                    int g = 60 + rnd.nextInt(170);
                    f.a[k] = 0xE6000000 | (g << 16) | (g << 8) | g;
                    continue;
                }
                float dark = 0.08f + 0.3f * (float) Math.pow(u * u * 0.7f + v * v, 1.4);
                if ((y & 1) == 1) dark += 0.16f;
                float sheen = Math.max(0f, 1f - (float) Math.hypot(u + 0.5f, v + 0.62f) / 0.5f);
                sheen = sheen * sheen * 0.22f;
                float al = Math.min(1f, (dark + sheen) * e);
                int g = sheen > dark ? 255 : 0;
                f.a[k] = (Math.round(al * 255f) << 24) | (g << 16) | (g << 8) | g;
            }
        }
    }

    private static final int VFD = 0x6CF2D8, VFD_OFF = 0x0B1816, AMBER = 0xFF8A2A, AMBER_OFF = 0x1A0B04;

    /** The clock and the date over the set: the player's own, in glowing digits. */
    private void clock(Px f, int dy) {
        Calendar c = Calendar.getInstance();
        int hh = c.get(Calendar.HOUR_OF_DAY), mm = c.get(Calendar.MINUTE);
        int dd = c.get(Calendar.DAY_OF_MONTH), mo = c.get(Calendar.MONTH) + 1;
        digit(f, 18, 63 + dy, 22, 40, 5, hh / 10, VFD, VFD_OFF);
        digit(f, 46, 63 + dy, 22, 40, 5, hh % 10, VFD, VFD_OFF);
        // the colon, blinking with the seconds
        if (c.get(Calendar.SECOND) % 2 == 0) {
            f.rect(77, 72 + dy, 82, 77 + dy, 0xFF000000 | VFD);
            f.rect(77, 89 + dy, 82, 94 + dy, 0xFF000000 | VFD);
        }
        digit(f, 92, 63 + dy, 22, 40, 5, mm / 10, VFD, VFD_OFF);
        digit(f, 120, 63 + dy, 22, 40, 5, mm % 10, VFD, VFD_OFF);
        int dim = Street.scale(VFD, 0.8f);
        digit(f, 22, 117 + dy, 11, 16, 2, dd / 10, dim, VFD_OFF);
        digit(f, 37, 117 + dy, 11, 16, 2, dd % 10, dim, VFD_OFF);
        f.rect(54, 130 + dy, 57, 133 + dy, 0xFF000000 | dim);
        digit(f, 63, 117 + dy, 11, 16, 2, mo / 10, dim, VFD_OFF);
        digit(f, 78, 117 + dy, 11, 16, 2, mo % 10, dim, VFD_OFF);
    }

    /** The row of buttons: dark until knocked, then lit; the one just knocked in and flaring. */
    private void buttons(Px f, int dy) {
        for (int i = 0; i < 4; i++) {
            int x = BX[i], y = BY + dy;
            float since = t - hitAt[i];
            boolean in = since >= 0f && since < 0.15f;
            int face = lit[i] ? LIT[i] : Street.scale(LIT[i], 0.25f);
            // the hammer button takes the colour of the hammer given
            int iconC = 0;
            if (i == HAMMER && lit[i] && giftKind >= 0) iconC = HEAD[Math.min(giftKind, HEAD.length - 1)];
            int y0 = y + (in ? 2 : 0);
            f.rect(x, y0, x + BW, y0 + BH, 0xFF000000 | face);
            if (!in) {
                f.rect(x, y0, x + BW, y0 + 2, 0xFF000000 | Street.scale(face, 1.35f));
                f.rect(x, y0 + BH - 3, x + BW, y0 + BH, 0xFF000000 | Street.scale(face, 0.55f));
            } else {
                f.rect(x, y0, x + BW, y0 + 1, 0xFF000000 | Street.scale(face, 0.5f));
            }
            int ic = in ? 0xFFFFFF : lit[i] ? Street.scale(face, 1.8f) : Street.scale(face, 1.6f);
            if (iconC != 0 && !in) ic = iconC;
            icon(f, i, x + BW / 2, y0 + BH / 2, 0xFF000000 | ic);
            // its light, spilling onto the panel and the set as it goes in
            if (since >= 0f && since < 0.45f) glow(f, x + BW / 2, y + BH / 2, LIT[i], 0.5f * (1f - since / 0.45f));
        }
    }

    private void icon(Px f, int kind, int cx, int cy, int c) {
        switch (kind) {
            case NEXT:
                for (int r = -7; r <= 7; r++) {
                    int len = 12 - Math.abs(r) * 12 / 7;
                    f.rect(cx - 5, cy + r, cx - 5 + len, cy + r + 1, c);
                }
                break;
            case CUBE:
                poly(f, cx, cy - 7, cx + 7, cy - 3, cx, cy + 1, cx - 7, cy - 3, c);
                poly(f, cx - 7, cy - 3, cx, cy + 1, cx, cy + 8, cx - 7, cy + 4, 0xFF000000 | Street.scale(c, 0.75f));
                poly(f, cx + 7, cy - 3, cx, cy + 1, cx, cy + 8, cx + 7, cy + 4, 0xFF000000 | Street.scale(c, 0.55f));
                break;
            case HAMMER:
                f.rect(cx - 7, cy - 6, cx + 6, cy - 1, c);
                f.rect(cx - 1, cy - 1, cx + 2, cy + 9, c);
                break;
            default:
                for (int a = 0; a < 40; a++) {
                    double ang = a * Math.PI * 2 / 40;
                    f.set(cx + (int) Math.round(Math.cos(ang) * 6), cy + (int) Math.round(Math.sin(ang) * 6), c);
                }
        }
    }

    private void poly(Px f, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        qx[0] = x0; qy[0] = y0; qx[1] = x1; qy[1] = y1; qx[2] = x2; qy[2] = y2; qx[3] = x3; qy[3] = y3;
        fill(f, c);
    }

    /** Light laid over what is there, fading off round a point. */
    private static void glow(Px f, int cx, int cy, int rgb, float k) {
        int r = 52;
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        for (int y = Math.max(0, cy - r); y < Math.min(f.h, cy + r); y++)
            for (int x = Math.max(0, cx - r * 13 / 10); x < Math.min(f.w, cx + r * 13 / 10); x++) {
                float d = (float) Math.hypot((x - cx) / 1.3f, y - cy) / r;
                if (d >= 1f) continue;
                float a = (1f - d) * (1f - d) * k;
                int i = y * f.w + x, c = f.a[i];
                if ((c >>> 24) < 200) continue;          // only on the room, not through the glass
                int nr = Math.min(255, ((c >> 16) & 0xFF) + Math.round(cr * a));
                int ng = Math.min(255, ((c >> 8) & 0xFF) + Math.round(cg * a));
                int nb = Math.min(255, (c & 0xFF) + Math.round(cb * a));
                f.a[i] = (c & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
            }
    }

    /** The level, big; and on the key the one it would take you back to. */
    private void counter(Px f, int dy) {
        int n = flicked() ? then : shown;
        for (int i = 0; i < 3; i++) {
            int dgt = (n / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            digit(f, 104 + i * 27, DY + 15 + dy, 21, 38, 4, dgt, AMBER, AMBER_OFF);
        }
        int back = n - 1;
        for (int i = 0; i < 3; i++) {
            int dgt = (Math.max(0, back) / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            digit(f, 28 + i * 16, DY + 40 + dy, 12, 21, 3, back < 1 ? -1 : dgt, AMBER, 0x0C0502);
        }
    }

    // which segments light for each digit: a top, b upper right, c lower right, d bottom, e lower left, f upper left, g middle
    private static final String[] SEG = {"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcfgd"};

    private static void digit(Px f, int x, int y, int w, int h, int t, int n, int on, int off) {
        String lit = n >= 0 && n <= 9 ? SEG[n] : "";
        int m = h / 2;
        seg(f, x + t, y, x + w - t, y + t, lit.indexOf('a') >= 0 ? on : off);
        seg(f, x + t, y + m - t / 2, x + w - t, y + m + (t + 1) / 2, lit.indexOf('g') >= 0 ? on : off);
        seg(f, x + t, y + h - t, x + w - t, y + h, lit.indexOf('d') >= 0 ? on : off);
        seg(f, x, y + t, x + t, y + m - t / 2, lit.indexOf('f') >= 0 ? on : off);
        seg(f, x + w - t, y + t, x + w, y + m - t / 2, lit.indexOf('b') >= 0 ? on : off);
        seg(f, x, y + m + (t + 1) / 2, x + t, y + h - t, lit.indexOf('e') >= 0 ? on : off);
        seg(f, x + w - t, y + m + (t + 1) / 2, x + w, y + h - t, lit.indexOf('c') >= 0 ? on : off);
    }

    private static void seg(Px f, int x0, int y0, int x1, int y1, int c) {
        f.rect(x0, y0, x1, y1, 0xFF000000 | c);
    }

    // ---- the hammer ----

    // the head's colours by kind (plain, heavy, golden, diamond): side, top, striking face, grain, bands
    private static final int[] HEAD = {0xE8B45E, 0x4A4C52, 0xF2C64A, 0x6CC8EC};
    private static final int[][] HEAD_SET = {
            {0xE8B45E, 0xF4D490, 0xFBE3A6, 0xDCA552, 0x4A423A},
            {0x4A4C52, 0x6A6D74, 0x8A8E96, 0x42444A, 0xC08A34},
            {0xF2C64A, 0xFFE08A, 0xFFF0B8, 0xD8AC34, 0xFFF0B0},
            {0x6CC8EC, 0xA8E4F6, 0xE8FAFF, 0x5AB4DC, 0xE8FAFF}};
    private static final float ANG = (float) Math.toRadians(-24);
    private static final float HL = 122f, HH = 82f, TOP = 14f;
    private final float[] qx = new float[4], qy = new float[4];

    /**
     * Where the hammer's head is now and how near: up out of the dark at the lower right, over
     * the button it is going for, down onto it, up again; back down into the dark at the end.
     */
    private void hammer(Px f, int dy, float up, int fh) {
        if (up <= 0f) return;
        float restX = 300f, restY = fh + 60f;
        float x = restX, y = restY, s = 1f;
        if (t >= 0f && presses > 0) {
            float k = t - RISE;
            int i = k < 0f ? 0 : Math.min(presses - 1, (int) (k / KNOCK));
            float in = k < 0f ? 0f : k - i * KNOCK;
            int b = press[i];
            float ax = BX[b] + BW / 2f + 87f, ay = BY + dy - 2f;     // its head on the button
            float hx = ax, hy = ay - 30f;                               // over it, a little nearer
            if (k < 0f) {
                float e = ease(t / RISE);
                x = restX + (hx - restX) * e;
                y = restY + (hy - restY) * e;
                s = 1.08f;
            } else if (in < MOVE) {
                // from over the last button to over this one
                float px = i == 0 ? hx : BX[press[i - 1]] + BW / 2f + 87f;
                float e = ease(in / MOVE);
                x = px + (hx - px) * e;
                y = hy;
                s = 1.08f;
            } else if (in < MOVE + DOWN) {
                float e = (in - MOVE) / DOWN;
                x = hx;
                y = hy + (ay - hy) * e * e;
                s = 1.08f - 0.08f * e;
            } else if (in < MOVE + DOWN + STAY) {
                x = ax;
                y = ay;
                s = 1f;
            } else {
                float e = ease((in - MOVE - DOWN - STAY) / (KNOCK - MOVE - DOWN - STAY));
                x = ax;
                y = ay + (hy - ay) * e;
                s = 1f + 0.08f * e;
            }
        }
        // back down into the dark as the room draws off, or while it closes in
        float e = ease(up);
        x = restX + (x - restX) * e;
        y = restY + (y - restY) * e;
        // the lower dark takes it, as it takes the room
        darkFrom = (PH - 4) + (fh - PH) / 2f;
        drawHammer(f, x, y, s, hammerKind);
        darkFrom = -1f;
    }

    // while the hammer is drawn: the row at which the dark below is whole (it fades in over 90 rows before it)
    private float darkFrom = -1f;

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private void drawHammer(Px f, float hx, float hy, float s, int kind) {
        int[] col = HEAD_SET[Math.max(0, Math.min(HEAD_SET.length - 1, kind))];
        float c = (float) Math.cos(ANG), sn = (float) Math.sin(ANG);
        float dxh = -sn, dyh = c;                        // down the handle
        // the handle: short and thick, down and right out of the frame
        bar(f, hx + dxh * 30 * s, hy + dyh * 30 * s, dxh, dyh, 290 * s, 20 * s, 0xFF5A3416);
        bar(f, hx + (dxh * 30 - 2 * c) * s, hy + (dyh * 30 - 2 * sn) * s, dxh, dyh, 290 * s, 16 * s, 0xFF9A5E2E);
        bar(f, hx + (dxh * 30 - 11 * c) * s, hy + (dyh * 30 - 11 * sn) * s, dxh, dyh, 290 * s, 5 * s, 0xFFC0803E);
        float hl = HL * s / 2f, hh = HH * s / 2f, tp = TOP * s;
        // the top, seen a little from above
        quad(f, hx, hy, c, sn, -hl, -hh, hl, -hh, hl + 6 * s, -hh - tp, -hl + 6 * s, -hh - tp, 0xFF000000 | col[1]);
        // the side
        quad(f, hx, hy, c, sn, -hl, -hh, hl, -hh, hl, hh, -hl, hh, 0xFF000000 | col[0]);
        // grain along it
        for (float g = -hh + 4 * s; g < hh - 2 * s; g += 5 * s)
            quad(f, hx, hy, c, sn, -hl + 2, g, hl - 2, g, hl - 2, g + 1, -hl + 2, g + 1, 0xFF000000 | col[3]);
        // the bands
        for (float b : new float[]{-hl + 16 * s, hl - 16 * s})
            quad(f, hx, hy, c, sn, b - 5 * s, -hh - tp, b + 5 * s, -hh - tp, b + 5 * s, hh, b - 5 * s, hh, 0xFF000000 | col[4]);
        // the striking face, at the left end, lit
        quad(f, hx, hy, c, sn, -hl - 8 * s, -hh - 4 * s, -hl, -hh, -hl, hh, -hl - 8 * s, hh - 4 * s, 0xFF000000 | col[2]);
        // its shadowed lower edge
        quad(f, hx, hy, c, sn, -hl, hh - 2, hl, hh - 2, hl, hh, -hl, hh, 0xFF000000 | Street.scale(col[0], 0.66f));
    }

    /** A bar from a point along a direction, so long and wide. */
    private void bar(Px f, float x, float y, float dx, float dy, float len, float half, int c) {
        float nx = -dy * half, ny = dx * half;
        qx[0] = x + nx; qy[0] = y + ny;
        qx[1] = x - nx; qy[1] = y - ny;
        qx[2] = x - nx + dx * len; qy[2] = y - ny + dy * len;
        qx[3] = x + nx + dx * len; qy[3] = y + ny + dy * len;
        fill(f, c);
    }

    /** A four-cornered shape in the head's own frame (turned by c, s about hx, hy). */
    private void quad(Px f, float hx, float hy, float c, float s, float x0, float y0, float x1, float y1, float x2, float y2,
                      float x3, float y3, int col) {
        float[] lx = {x0, x1, x2, x3}, ly = {y0, y1, y2, y3};
        for (int i = 0; i < 4; i++) {
            qx[i] = hx + lx[i] * c - ly[i] * s;
            qy[i] = hy + lx[i] * s + ly[i] * c;
        }
        fill(f, col);
    }

    /** Fills the convex four-cornered shape in qx, qy, hard-edged. */
    private void fill(Px f, int col) {
        float minY = Math.min(Math.min(qy[0], qy[1]), Math.min(qy[2], qy[3]));
        float maxY = Math.max(Math.max(qy[0], qy[1]), Math.max(qy[2], qy[3]));
        int y0 = Math.max(0, (int) Math.ceil(minY - 0.5f)), y1 = Math.min(f.h - 1, (int) Math.floor(maxY - 0.5f));
        for (int y = y0; y <= y1; y++) {
            float py = y + 0.5f, lo = 1e9f, hi = -1e9f;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                float ya = qy[i], yb = qy[j];
                if ((py < ya) == (py < yb)) continue;
                float xh = qx[i] + (py - ya) / (yb - ya) * (qx[j] - qx[i]);
                lo = Math.min(lo, xh);
                hi = Math.max(hi, xh);
            }
            if (hi < lo) continue;
            int xa = Math.max(0, (int) Math.ceil(lo - 0.5f)), xb = Math.min(f.w - 1, (int) Math.floor(hi - 0.5f));
            int c = col;
            if (darkFrom > 0f) {
                float k = Math.max(0f, Math.min(1f, (darkFrom - y) / 90f));
                k = (float) Math.pow(k, 1.3);
                c = 0xFF000000 | (Math.round(((col >> 16) & 0xFF) * k) << 16) | (Math.round(((col >> 8) & 0xFF) * k) << 8)
                        | Math.round((col & 0xFF) * k);
            }
            for (int x = xa; x <= xb; x++) f.a[y * f.w + x] = c;
        }
    }
}
