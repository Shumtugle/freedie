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
 * The room is a place in the world, not writing over it, so it is drawn at the world's own
 * grain, as the street is, and laid on the flat layer: the still parts are a picture read
 * from an asset (deflated, drawn finer and brought down here, so its forms are clean), the
 * tube in it left clear for the street to show through; the glass, the digits and the
 * buttons are drawn over it every frame with clean edges, a pixel soft. The hammer is not
 * drawn here: it is the world's own, the one in the hand on the street, drawn with the
 * street; the room works out where it goes and leaves a way through for it, the shape of it,
 * with the shadow it casts on the room.
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
    private static final int[] LIT = {0x4FE36A, 0xFFA830, 0x9AA3B4, 0xFF5A3C};

    // the timeline, seconds: the hammer coming up, each knock, the channel flicking over
    static final float RISE = 0.3f, KNOCK = 0.42f, FLICK = 0.18f;
    private static final float MOVE = 0.18f, DOWN = 0.08f, STAY = 0.06f;

    private int[] art;
    private int artW, artH;
    private final int[] press = new int[4];
    private int presses;
    private int giftKind = -1;
    private int shown, then;                          // the level on the counter now, and after the flick
    private final boolean[] lit = new boolean[4];
    private final float[] hitAt = new float[4];
    private float t = -1f;                            // seconds into the knocking, below 0 not yet
    private int heard;                                // knocks already sounded
    private final java.util.Random rnd = new java.util.Random(11);

    /** Reads the room's picture: width, height, then RGBA rows; deflated, or not. Kept premultiplied. */
    void read(byte[] b) {
        if (b == null || b.length < 4) return;
        if ((b[0] & 0xFF) == 0x78) b = inflate(b);
        if (b == null || b.length < 4) return;
        int w = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), h = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if ((long) b.length < 4L + (long) w * h * 4L) return;
        // drawn finer than it is shown: each pixel here the mean of those it covers, premultiplied
        int q = Math.max(1, w / (World.W * Q)), aw = w / q, ah = h / q, n = q * q;
        art = new int[aw * ah];
        for (int y = 0; y < ah; y++)
            for (int x = 0; x < aw; x++) {
                int sa = 0, sr = 0, sg = 0, sb = 0;
                for (int j = 0; j < q; j++)
                    for (int i = 0; i < q; i++) {
                        int p = 4 + ((y * q + j) * w + x * q + i) * 4, al = b[p + 3] & 0xFF;
                        sa += al;
                        sr += (b[p] & 0xFF) * al / 255;
                        sg += (b[p + 1] & 0xFF) * al / 255;
                        sb += (b[p + 2] & 0xFF) * al / 255;
                    }
                art[y * aw + x] = (sa / n << 24) | (sr / n << 16) | (sg / n << 8) | (sb / n);
            }
        artW = aw;
        artH = ah;
    }

    /** The room, drawn on its layer (premultiplied), laid over the flat layer (straight colours). */
    static void over(Px f, Ink room) {
        int[] d = f.a, s = room.a;
        int n = Math.min(d.length, s.length);
        for (int i = 0; i < n; i++) {
            int c = s[i], sa = c >>> 24;
            if (sa == 0) continue;
            if (sa == 255) {
                d[i] = c;
                continue;
            }
            int o = d[i], da = o >>> 24, keep = da * (255 - sa) / 255, oa = sa + keep;
            if (oa == 0) continue;
            int r = (((c >> 16) & 0xFF) * 255 + ((o >> 16) & 0xFF) * keep) / oa;
            int g = (((c >> 8) & 0xFF) * 255 + ((o >> 8) & 0xFF) * keep) / oa;
            int b = ((c & 0xFF) * 255 + (o & 0xFF) * keep) / oa;
            d[i] = (oa << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
        }
    }

    /** A deflated asset, inflated; null if it will not inflate. */
    static byte[] inflate(byte[] z) {
        java.util.zip.Inflater in = new java.util.zip.Inflater();
        in.setInput(z);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(z.length * 8);
        byte[] buf = new byte[1 << 16];
        try {
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) break;
                out.write(buf, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            return null;
        } finally {
            in.end();
        }
        return out.toByteArray();
    }

    /**
     * A new visit: the buttons to knock, in order (the last is NEXT or GAME); the hammer in
     * hand, and the one given, if any (its kind lights the hammer button); the level shown now
     * and after the flick, as the counter counts them, from one.
     */
    void begin(int[] buttons, int n, int handKind, int gift, int levelNow, int levelThen) {
        presses = Math.min(n, press.length);
        System.arraycopy(buttons, 0, press, 0, presses);
        giftKind = gift;
        shown = levelNow;
        then = levelThen;
        java.util.Arrays.fill(lit, false);
        java.util.Arrays.fill(hitAt, -9f);
        t = 0f;
        heard = 0;
    }

    private int bank;
    private boolean bankFresh;

    /** The bank this time: how many golden cubes are in it, and whether one came in with this round. */
    void bank(int n, boolean fresh) {
        bank = n;
        bankFresh = fresh;
    }

    /**
     * The bank, on the desk at the right: golden cubes behind a grille, won with the buildings
     * that wore them; they are not the player's yet. One come in with this round shines.
     */
    static void bankBox(Px f, int x0, int y0, int x1, int y1, int n, float shine) {
        f.rect(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF0E0F11);
        f.rect(x0, y0, x1, y1, 0xFF15171A);
        int cols = 4, shown = Math.min(8, n);
        float cw = (x1 - x0) / (float) cols;
        for (int i = 0; i < shown; i++) {
            int row = i / cols, col = i % cols;
            int cx = Math.round(x0 + cw * (col + 0.5f)), cy = y1 - 11 - row * 22;
            boolean lit = i == shown - 1 && shine > 0f;
            // a little golden cube: its top light, its two sides darker
            f.rect(cx - 7, cy - 7, cx + 7, cy - 2, lit ? 0xFFFFE6A0 : 0xFFFFC24A);
            f.rect(cx - 7, cy - 2, cx, cy + 7, 0xFFF08C1E);
            f.rect(cx, cy - 2, cx + 7, cy + 7, 0xFFB85A12);
        }
        // the grille over it, thin: they are behind bars, not in the hand
        for (int x = x0 + 8; x < x1 - 2; x += 17) f.rect(x, y0, x + 1, y1, 0xFF5A626C);
        f.rect(x0, y0, x1, y0 + 2, 0xFF5A626C);
        f.rect(x0, y1 - 2, x1, y1, 0xFF3A4048);
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

    // ---- drawing, on the fine layer ----

    /** Layer pixels to a frame pixel: the room is at the world's own grain. */
    static final int Q = 1;

    /**
     * The room over the frame, as far as it has closed in (0: not at all, 1: only the tube
     * left), and the hammer, as far as it is up out of the dark (0 to 1); on the fine layer,
     * which is cleared for it.
     */
    void draw(Ink g, int fh, float cover, float handUp) {
        if (art == null || cover <= 0f) return;
        g.clear();
        float oy = (fh - PH) / 2f;                      // the picture centred on the frame, cut at the top and bottom
        float split = SPLIT + oy;
        float e = ease(cover);
        float top = -(1f - e) * split, bot = (1f - e) * (fh - split);
        int topQ = Math.round(top * Q), botQ = Math.round(bot * Q), oyQ = Math.round(oy * Q), splitQ = Math.round(split * Q);
        int[] a = g.a;
        int gw = g.w;
        for (int y = 0; y < g.h; y++) {
            boolean upper = y < splitQ + topQ, lower = y >= splitQ + botQ;
            if (!upper && !lower) continue;             // the gap between, where the street still shows whole
            int sy = (upper ? y - topQ : y - botQ) - oyQ, row = y * gw;
            if (sy < 0 || sy >= artH) java.util.Arrays.fill(a, row, row + gw, 0xFF000000);
            else System.arraycopy(art, sy * artW, a, row, Math.min(artW, gw));
        }
        g.used = true;
        tube(g, oyQ, e);
        float dTop = top + oy, dBot = bot + oy;         // where the moving halves are, in frame pixels
        clock(g, dTop);
        buttons(g, dBot);
        counter(g, dBot);
        if (bank > 0) bankAt(g, 196, DY + 8 + dBot, 264, DY + 60 + dBot, bank, bankFresh ? 0.5f + 0.5f * (float) Math.sin(t * 9f) : 0f);
        if (handUp > 0f) {
            holes(g);
            // the lower dark takes it, as it takes the room
            darken(g, (PH - 4) + (fh - PH) / 2f);
        }
    }

    /** Where the lower half of the room is, in frame pixels, as far as it has closed in. */
    private static float lowerAt(int fh, float cover) {
        float oy = (fh - PH) / 2f;
        return (1f - ease(cover)) * (fh - SPLIT - oy) + oy;
    }

    // the glass, worked out once for where the tube is: how dark each pixel of it is, and whether white
    private byte[] glassA;
    private boolean[] glassW;
    private int[] glassFull;
    private int gx0, gy0, gw, gh;

    /** The glass: fine lines, darker at its edges, a sheen at the top; a moment of noise as it flicks. Under the room's edge. */
    private void tube(Ink g, int oyQ, float e) {
        int y0 = Math.max(0, SY0 * Q + oyQ), y1 = Math.min(g.h, (SY0 + SH) * Q + oyQ);
        int x0 = Math.max(0, SX0 * Q), x1 = Math.min(g.w, (SX0 + SW) * Q);
        if (glassA == null || gx0 != x0 || gy0 != y0 || gw != x1 - x0 || gh != y1 - y0) {
            gx0 = x0;
            gy0 = y0;
            gw = x1 - x0;
            gh = y1 - y0;
            glassA = new byte[gw * gh];
            glassW = new boolean[gw * gh];
            glassFull = new int[gw * gh];
            for (int y = y0; y < y1; y++) {
                float v = (y - oyQ - (SY0 + SH * 0.5f) * Q) / (SH * 0.5f * Q);
                for (int x = x0; x < x1; x++) {
                    float u = (x - (SX0 + SW * 0.5f) * Q) / (SW * 0.5f * Q);
                    float dark = 0.08f + 0.3f * (float) Math.pow(u * u * 0.7f + v * v, 1.4);
                    if ((y & 1) == 1) dark += 0.1f;
                    float sheen = Math.max(0f, 1f - (float) Math.hypot(u + 0.5f, v + 0.62f) / 0.5f);
                    sheen = sheen * sheen * 0.22f;
                    int k = (y - y0) * gw + (x - x0);
                    int ai = Math.round(Math.min(1f, dark + sheen) * 255f);
                    glassA[k] = (byte) ai;
                    glassW[k] = sheen > dark;
                    int w = glassW[k] ? ai : 0;
                    glassFull[k] = (ai << 24) | (w << 16) | (w << 8) | w;
                }
            }
        }
        boolean noise = t >= 0f && flicked() && t < RISE + presses * KNOCK + FLICK;
        int ek = Math.round(e * 256f);
        int[] a = g.a;
        for (int y = y0; y < y1; y++) {
            int row = y * g.w, grow = (y - y0) * gw - x0;
            for (int x = x0; x < x1; x++) {
                int k = row + x, d = a[k], da = d >>> 24;
                if (da == 255) continue;                    // the room
                int glass;
                if (da == 0 && ek == 256 && !noise) {
                    // the room fully closed in, nothing of it here: the glass as it is
                    a[k] = glassFull[grow + x];
                    continue;
                }
                if (noise) {
                    int c = (60 + rnd.nextInt(170)) * 0xE6 / 255;
                    glass = 0xE6000000 | (c << 16) | (c << 8) | c;
                } else {
                    int ai = ((glassA[grow + x] & 0xFF) * ek) >> 8, w = glassW[grow + x] ? ai : 0;
                    glass = (ai << 24) | (w << 16) | (w << 8) | w;
                }
                // the glass under whatever of the room's soft edge is there
                int keep = 255 - da;
                int ra = da + ((glass >>> 24) * keep + 127) / 255;
                int rr = ((d >> 16) & 0xFF) + (((glass >> 16) & 0xFF) * keep + 127) / 255;
                int rg = ((d >> 8) & 0xFF) + (((glass >> 8) & 0xFF) * keep + 127) / 255;
                int rb = (d & 0xFF) + ((glass & 0xFF) * keep + 127) / 255;
                a[k] = (Math.min(255, ra) << 24) | (Math.min(255, rr) << 16) | (Math.min(255, rg) << 8) | Math.min(255, rb);
            }
        }
    }

    // ---- what is drawn once and laid down again: digits, keys and their light, the bank, the hammer ----

    private final java.util.HashMap<Integer, Ink> sprites = new java.util.HashMap<Integer, Ink>();
    private static final float PAD = 6f;
    // the four kinds of digit: the clock's, the date's, the level's, the key's
    private static final float[] DW = {22, 11, 21, 12}, DH = {40, 16, 38, 21}, DT = {5, 2.2f, 4, 3};

    /** A digit of a kind, its top left at (x, y) in frame pixels; below 0, all dark. */
    private void digitAt(Ink g, int kind, int n, float x, float y) {
        int key = 1000 + kind * 16 + n + 1;
        Ink sp = sprites.get(key);
        if (sp == null) {
            sp = new Ink(Math.round((DW[kind] + 2 * PAD) * Q), Math.round((DH[kind] + 2 * PAD) * Q));
            int on = kind == 0 ? 0xFF000000 | VFD : kind == 1 ? 0xFF000000 | Street.scale(VFD, 0.8f) : 0xFF000000 | AMBER;
            int off = kind < 2 ? 0xFF000000 | VFD_OFF : kind == 2 ? 0xFF000000 | AMBER_OFF : 0xFF0C0502;
            seven(sp, PAD, PAD, DW[kind], DH[kind], DT[kind], n, on, off);
            sprites.put(key, sp);
        }
        g.blit(sp, (x - PAD) * Q, (y - PAD) * Q, 1f);
    }

    private static final int VFD = 0x6CF2D8, VFD_OFF = 0x0B1816, AMBER = 0xFF8A2A, AMBER_OFF = 0x1A0B04;

    /** The clock and the date over the set: the player's own, in glowing digits. */
    static void clock(Px f, int dy) {
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

    /** The clock and the date over the set: the player's own, in glowing digits. */
    private void clock(Ink g, float dy) {
        Calendar c = Calendar.getInstance();
        int hh = c.get(Calendar.HOUR_OF_DAY), mm = c.get(Calendar.MINUTE);
        int dd = c.get(Calendar.DAY_OF_MONTH), mo = c.get(Calendar.MONTH) + 1;
        int on = 0xFF000000 | VFD;
        digitAt(g, 0, hh / 10, 18, 63 + dy);
        digitAt(g, 0, hh % 10, 46, 63 + dy);
        // the colon, blinking with the seconds
        if (c.get(Calendar.SECOND) % 2 == 0)
            for (float cy : new float[]{74.5f, 91.5f}) g.disc(79.5f * Q, (cy + dy) * Q, 2.4f * Q, on, 1f, 1f);
        digitAt(g, 0, mm / 10, 92, 63 + dy);
        digitAt(g, 0, mm % 10, 120, 63 + dy);
        int dim = 0xFF000000 | Street.scale(VFD, 0.8f);
        digitAt(g, 1, dd / 10, 22, 117 + dy);
        digitAt(g, 1, dd % 10, 37, 117 + dy);
        g.disc(55.5f * Q, (131.5f + dy) * Q, 1.6f * Q, dim, 1f, 1f);
        digitAt(g, 1, mo / 10, 63, 117 + dy);
        digitAt(g, 1, mo % 10, 78, 117 + dy);
    }

    /**
     * A digit of seven segments, each a long six-sided bar with pointed ends, a hair apart;
     * lit ones glow softly round their edges. In frame pixels; n below 0 lights none.
     */
    private static void seven(Ink g, float x, float y, float w, float h, float t, int n, int on, int off) {
        String lit = n >= 0 && n <= 9 ? SEG[n] : "";
        float gap = t * 0.18f, hf = t * 0.5f, m = y + h * 0.5f;
        for (int pass = 0; pass < 2; pass++)
            for (int k = 0; k < 7; k++) {
                char ch = "agdfbec".charAt(k);
                boolean l = lit.indexOf(ch) >= 0;
                if (pass == 0 && !l) continue;
                float ax, ay, bx, by;
                switch (ch) {
                    case 'a': ax = x + hf; ay = y + hf; bx = x + w - hf; by = ay; break;
                    case 'g': ax = x + hf; ay = m; bx = x + w - hf; by = m; break;
                    case 'd': ax = x + hf; ay = y + h - hf; bx = x + w - hf; by = ay; break;
                    case 'f': ax = x + hf; ay = y + hf; bx = ax; by = m; break;
                    case 'b': ax = x + w - hf; ay = y + hf; bx = ax; by = m; break;
                    case 'e': ax = x + hf; ay = m; bx = ax; by = y + h - hf; break;
                    default: ax = x + w - hf; ay = m; bx = ax; by = y + h - hf; break;
                }
                bar7(g, ax, ay, bx, by, hf, gap);
                if (pass == 0) g.polyFill(on, on, t * Q * 1.6f, 0.28f);
                else g.polyFill(l ? on : off, l ? on : off, 1f, 1f);
            }
    }

    /** A segment from (ax, ay) to (bx, by), level or upright, so thick, pointed at its ends and drawn in a little. */
    private static void bar7(Ink g, float ax, float ay, float bx, float by, float hf, float gap) {
        if (ay == by) {
            float x0 = ax + gap, x1 = bx - gap;
            g.shape(x0 * Q, ay * Q, (x0 + hf) * Q, (ay - hf) * Q, (x1 - hf) * Q, (ay - hf) * Q, x1 * Q, ay * Q,
                    (x1 - hf) * Q, (ay + hf) * Q, (x0 + hf) * Q, (ay + hf) * Q);
        } else {
            float y0 = ay + gap, y1 = by - gap;
            g.shape(ax * Q, y0 * Q, (ax + hf) * Q, (y0 + hf) * Q, (ax + hf) * Q, (y1 - hf) * Q, ax * Q, y1 * Q,
                    (ax - hf) * Q, (y1 - hf) * Q, (ax - hf) * Q, (y0 + hf) * Q);
        }
    }

    /** The row of buttons: dark until knocked, then lit; the one just knocked in and flaring. */
    private void buttons(Ink g, float dy) {
        for (int i = 0; i < 4; i++) {
            float x = BX[i], y = BY + dy;
            float since = t - hitAt[i];
            boolean in = since >= 0f && since < 0.15f;
            int gift = i == HAMMER && lit[i] && giftKind >= 0 ? Math.min(giftKind, Hammer.DIAMOND) : -1;
            int key = 2000 + i * 64 + (lit[i] ? 32 : 0) + (in ? 16 : 0) + gift + 1;
            Ink sp = sprites.get(key);
            if (sp == null) {
                sp = new Ink((BW + 4) * Q, (BH + 6) * Q);
                key(sp, i, lit[i], in, gift);
                sprites.put(key, sp);
            }
            g.blit(sp, (x - 2) * Q, (y - 2) * Q, 1f);
            // its light, spilling onto the panel and the set as it goes in
            if (since >= 0f && since < 0.45f) {
                Ink gl = sprites.get(3000 + i);
                if (gl == null) {
                    gl = new Ink(116 * Q, 116 * Q);
                    gl.disc(58f * Q, 58f * Q, 10f * Q, 0xFF000000 | LIT[i], 46f * Q, 1f);
                    sprites.put(3000 + i, gl);
                }
                g.blit(gl, (x + BW / 2f - 58f) * Q, (y + BH / 2f - 58f) * Q, 0.55f * (1f - since / 0.45f));
            }
        }
    }

    /** A key, drawn once for how it is: a rounded key lit from above, its edge catching the light, its sign. */
    private void key(Ink sp, int i, boolean on, boolean in, int gift) {
        float x = 2f, y0 = 2f + (in ? 2f : 0f);
        int face = on ? LIT[i] : Street.scale(LIT[i], 0.25f);
        sp.box(x * Q, y0 * Q, (x + BW) * Q, (y0 + BH) * Q, 5f * Q, 0xFF000000 | Street.scale(face, in ? 0.85f : 1.15f),
                0xFF000000 | Street.scale(face, in ? 0.7f : 0.68f), 1f, 1f);
        if (!in) sp.box((x + 3) * Q, (y0 + 1.2f) * Q, (x + BW - 3) * Q, (y0 + 3.2f) * Q, 1f * Q, 0xFFFFFFFF, 0xFFFFFFFF, 1f,
                on ? 0.35f : 0.12f);
        int ic = in ? 0xFFFFFF : on ? Street.scale(face, 1.8f) : Street.scale(face, 1.6f);
        if (gift >= 0 && !in) ic = Hammer.head(gift);
        icon(sp, i, x + BW / 2f, y0 + BH / 2f, 0xFF000000 | ic);
    }

    private void icon(Ink g, int kind, float cx, float cy, int c) {
        switch (kind) {
            case NEXT:
                g.shape((cx - 5) * Q, (cy - 7.5f) * Q, (cx + 7.5f) * Q, cy * Q, (cx - 5) * Q, (cy + 7.5f) * Q);
                g.polyFill(c, c, 1f, 1f);
                break;
            case CUBE:
                g.shape(cx * Q, (cy - 7) * Q, (cx + 7) * Q, (cy - 3) * Q, cx * Q, (cy + 1) * Q, (cx - 7) * Q, (cy - 3) * Q);
                g.polyFill(c, c, 1f, 1f);
                int c2 = 0xFF000000 | Street.scale(c, 0.75f), c3 = 0xFF000000 | Street.scale(c, 0.55f);
                g.shape((cx - 7) * Q, (cy - 3) * Q, cx * Q, (cy + 1) * Q, cx * Q, (cy + 8) * Q, (cx - 7) * Q, (cy + 4) * Q);
                g.polyFill(c2, c2, 1f, 1f);
                g.shape((cx + 7) * Q, (cy - 3) * Q, cx * Q, (cy + 1) * Q, cx * Q, (cy + 8) * Q, (cx + 7) * Q, (cy + 4) * Q);
                g.polyFill(c3, c3, 1f, 1f);
                break;
            case HAMMER:
                // the hammer's own shape, as a sign
                Hammer.icon(g, cx * Q, (cy + 1f) * Q, 19f * Q, Hammer.HEAVY_KIND, c & 0xFFFFFF, 1f);
                break;
            default:
                g.ring(cx * Q, cy * Q, 5.2f * Q, 6.8f * Q, 0f, 1f, c, 1f);
        }
    }

    /** The level, big; and on the key the one it would take you back to. */
    private void counter(Ink g, float dy) {
        int n = flicked() ? then : shown;
        for (int i = 0; i < 3; i++) digitAt(g, 2, (n / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10, 104 + i * 27, DY + 15 + dy);
        int back = n - 1;
        for (int i = 0; i < 3; i++)
            digitAt(g, 3, back < 1 ? -1 : (Math.max(0, back) / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10, 28 + i * 16, DY + 40 + dy);
    }

    /** The bank, drawn once for how many and whether the newest shines, laid on the desk. */
    private void bankAt(Ink g, float x0, float y0, float x1, float y1, int n, float shine) {
        int key = 4000 + Math.min(8, n) * 2 + (shine > 0.5f ? 1 : 0);
        Ink sp = sprites.get(key);
        if (sp == null) {
            sp = new Ink(Math.round((x1 - x0 + 8) * Q), Math.round((y1 - y0 + 8) * Q));
            bankBox(sp, 4f, 4f, 4f + x1 - x0, 4f + y1 - y0, n, shine > 0.5f ? 1f : 0f);
            sprites.put(key, sp);
        }
        g.blit(sp, (x0 - 4f) * Q, (y0 - 4f) * Q, 1f);
    }

    /** The bank on the desk: golden cubes behind a thin grille; one come in with this round shines. */
    private static void bankBox(Ink g, float x0, float y0, float x1, float y1, int n, float shine) {
        g.box((x0 - 2) * Q, (y0 - 2) * Q, (x1 + 2) * Q, (y1 + 2) * Q, 5f * Q, 0xFF0E0F11, 0xFF15171A, 1f, 1f);
        g.box(x0 * Q, y0 * Q, x1 * Q, y1 * Q, 4f * Q, 0xFF181B1F, 0xFF101215, 1f, 1f);
        int cols = 4, shown = Math.min(8, n);
        float cw = (x1 - x0) / cols;
        for (int i = 0; i < shown; i++) {
            int row = i / cols, col = i % cols;
            float cx = x0 + cw * (col + 0.5f), cy = y1 - 11 - row * 22;
            boolean on = i == shown - 1 && shine > 0f;
            g.disc(cx * Q, cy * Q, 6f * Q, 0xFFFF9628, 7f * Q, 0.25f);
            g.shape(cx * Q, (cy - 7) * Q, (cx + 7) * Q, (cy - 3.5f) * Q, cx * Q, cy * Q, (cx - 7) * Q, (cy - 3.5f) * Q);
            g.polyFill(on ? 0xFFFFE6A0 : 0xFFFFC24A, on ? 0xFFFFE6A0 : 0xFFFFC24A, 1f, 1f);
            g.shape((cx - 7) * Q, (cy - 3.5f) * Q, cx * Q, cy * Q, cx * Q, (cy + 7.5f) * Q, (cx - 7) * Q, (cy + 4) * Q);
            g.polyFill(0xFFF08C1E, 0xFFF08C1E, 1f, 1f);
            g.shape((cx + 7) * Q, (cy - 3.5f) * Q, cx * Q, cy * Q, cx * Q, (cy + 7.5f) * Q, (cx + 7) * Q, (cy + 4) * Q);
            g.polyFill(0xFFB85A12, 0xFFB85A12, 1f, 1f);
        }
        for (float x = x0 + 8; x < x1 - 2; x += 17) g.line(x * Q, y0 * Q, x * Q, y1 * Q, 1.2f * Q, 0xFF5A626C, 1f);
        g.line(x0 * Q, (y0 + 1) * Q, x1 * Q, (y0 + 1) * Q, 1.6f * Q, 0xFF5A626C, 1f);
    }

    // which segments light for each digit: a top, b upper right, c lower right, d bottom, e lower left, f upper left, g middle
    private static final String[] SEG = {"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcfgd"};

    static void digit(Px f, int x, int y, int w, int h, int t, int n, int on, int off) {
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

    /**
     * Where the hand's hammer goes in the room, for the world to draw it there: its head's
     * middle in the frame, and how near (1, or a little more as it comes up off a button). Up
     * out of the dark at the lower right, over the button it is going for, down onto it, up
     * again; back down into the dark at the end. False while the room does not have it.
     */
    boolean hand(int fh, float cover, float handUp, float[] out) {
        if (art == null || cover <= 0f || handUp <= 0f || t < 0f || presses == 0) return false;
        float dy = lowerAt(fh, cover);
        float restX = 300f, restY = fh + 60f;
        float x, y, s;
        float k = t - RISE;
        int i = k < 0f ? 0 : Math.min(presses - 1, (int) (k / KNOCK));
        float in = k < 0f ? 0f : k - i * KNOCK;
        int b = press[i];
        float ax = BX[b] + BW / 2f + HEAD_DX, ay = BY + dy + HEAD_DY;     // its head on the button
        float hx = ax, hy = ay - 30f;                                     // over it, a little nearer
        if (k < 0f) {
            float e = ease(t / RISE);
            x = restX + (hx - restX) * e;
            y = restY + (hy - restY) * e;
            s = 1.08f;
        } else if (in < MOVE) {
            float px = i == 0 ? hx : BX[press[i - 1]] + BW / 2f + HEAD_DX;
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
        float e = ease(handUp);
        out[0] = restX + (x - restX) * e;
        out[1] = restY + (y - restY) * e;
        out[2] = s;
        return true;
    }

    // where the head's middle sits when it knocks a key, from the key's middle
    private static final float HEAD_DX = 0f, HEAD_DY = 0f;

    // the hammer's outline in the frame, part by part, as the world worked it out this frame
    private float[] cutPts;
    private int[] cutN;
    private int cuts;

    /** The hammer's outline for this frame (see Hammer.outline), or none. */
    void cut(float[] pts, int[] counts, int parts) {
        cutPts = pts;
        cutN = counts;
        cuts = parts;
    }

    /**
     * The way through the room for the hammer: first the shadow it casts, a little down and to
     * the right, then a hole the shape of it, a little inside its edge so no street shows round it.
     */
    private void holes(Ink g) {
        for (int pass = 0; pass < 2; pass++)
            for (int p = 0; p < cuts; p++)
                fillHull(g, cutPts, p * 16, cutN[p], pass == 0 ? 4f : 0f, pass == 0 ? 6f : 0f, pass == 0 ? 0.4f : -1f);
    }

    /** Fills a convex outline, moved by (ox, oy): darkened by so much (premultiplied), or, below 0, cleared. */
    private static void fillHull(Ink g, float[] p, int at, int n, float ox, float oy, float dark) {
        if (n < 3) return;
        float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            y0 = Math.min(y0, p[at + i * 2 + 1] + oy);
            y1 = Math.max(y1, p[at + i * 2 + 1] + oy);
        }
        int keep = Math.round((1f - Math.max(0f, dark)) * 256f);
        float inset = dark < 0f ? 0.35f : 0f;
        for (int y = Math.max(0, (int) Math.floor(y0 * Q)); y < Math.min(g.h, (int) Math.ceil(y1 * Q)); y++) {
            float cy = (y + 0.5f) / Q;
            float xl = Float.MAX_VALUE, xr = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ax = p[at + i * 2] + ox, ay = p[at + i * 2 + 1] + oy, bx = p[at + j * 2] + ox, by = p[at + j * 2 + 1] + oy;
                if ((ay <= cy && by > cy) || (by <= cy && ay > cy)) {
                    float x = ax + (cy - ay) / (by - ay) * (bx - ax);
                    xl = Math.min(xl, x);
                    xr = Math.max(xr, x);
                }
            }
            if (xl > xr) continue;
            int row = y * g.w;
            for (int x = Math.max(0, (int) Math.floor(xl * Q)); x < Math.min(g.w, (int) Math.ceil(xr * Q)); x++) {
                float cx = (x + 0.5f) / Q;
                if (cx < xl + inset || cx > xr - inset) continue;
                if (dark < 0f) {
                    g.a[row + x] = 0;
                    continue;
                }
                // premultiplied: the room under the shadow, darker, as solid as it was
                int c = g.a[row + x];
                g.a[row + x] = (c & 0xFF000000) | ((((c >> 16) & 0xFF) * keep >> 8) << 16) | ((((c >> 8) & 0xFF) * keep >> 8) << 8)
                        | ((c & 0xFF) * keep >> 8);
            }
        }
    }

    /** Everything below a row goes to black, fading in over the 90 rows before it. */
    private static void darken(Ink g, float dark) {
        int[] a = g.a;
        int from = Math.max(0, Math.round((dark - 90f) * Q)), to = g.h;
        for (int y = from; y < to; y++) {
            float k = y >= dark * Q ? 0f : (float) Math.pow((dark * Q - y) / (90f * Q), 1.3);
            int ki = Math.round(k * 256f), add = 255 - Math.round(k * 255f);
            int row = y * g.w;
            for (int x = 0; x < g.w; x++) {
                int c = a[row + x];
                int al = Math.min(255, (((c >>> 24) * ki) >> 8) + add);
                a[row + x] = (al << 24) | ((((c >> 16) & 0xFF) * ki >> 8) << 16) | ((((c >> 8) & 0xFF) * ki >> 8) << 8) | ((c & 0xFF) * ki >> 8);
            }
        }
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
