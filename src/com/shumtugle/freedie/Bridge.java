package com.shumtugle.freedie;

import java.util.Random;

/**
 * The dispatcher's bridge: the same room the player glances into between rounds, but here he
 * sits down at it. One set; the episodes are its channels, and a big red knob on the front of
 * the desk clicks from one to the next. There are only as many channels as the game has
 * reached: the knob turns as far as the last one and stops against it there, and nothing
 * says how many more there will be.
 *
 * Every level played so far is open, free. The counter on the desk shows a level of the
 * channel on now: its first, or, on the channel being played, the level the player came
 * from. The two keys at the right of the row step it back and on, through the levels of that
 * episode the player has reached, and held they run. The green key goes there: onto the
 * street at that level. The key at the left of the desk, the number of the level the player
 * came from on it, goes back there, to the street as it was. Everything a finger touches is big.
 *
 * Past the last episode the knob finds one more channel: a game, the ball and the hammer.
 * Its green key goes into it; it has no levels to step through.
 *
 * And the room is alive while one sits there: the lamp sways a little and its light with it,
 * a fan's shadow crawls round on the wall, the tube flickers, the eye breathes.
 *
 * Drawn on the flat layer; the still parts of the room are the same picture the console
 * between rounds is drawn from.
 */
final class Bridge {
    static final float OPEN_T = 0.3f, TV_ON_T = 0.3f;
    private static final int HOME = -2, GAME_KEY = -3;
    // the channel of the game, after the episodes
    static final int GAME = Episode.COUNT;

    // where things are in the room's picture (274 x 640), its own pixels
    private static final int PH = 640;
    private static final int SX0 = -4, SY0 = 162, SW = 282, SH = 212;
    // the two wells under the set: the green key in the left, the two steps in the right
    private static final int[] BX = Console.BX;
    private static final int BY = 422, BW = Console.BW, BH = 32;
    private static final int STEP_W = (BW - 4) / 2;
    private static final int DY = 474;
    private static final int KEY_X0 = 16, KEY_Y0 = DY + 4, KEY_X1 = 84, KEY_Y1 = DY + 64;
    private static final int FRONT = 548;
    private static final int KX = 137, KY = 592, KR = 50;
    private static final float STEP = 0.9f;
    private static final int AMBER = 0xFF8A2A, AMBER_OFF = 0x1A0B04;
    // a step key held: it runs after a while, so many levels a second
    private static final float RUN_AFTER = 0.45f, RUN_EVERY = 0.09f;

    private final World w;
    private final World.Host host;
    private final Random rnd = new Random(0xB21D6EL);
    float t;                                  // seconds since it opened
    // the channels there are, in order: the episodes reached
    private final int[] chan = new int[Episode.COUNT + 1];
    private int chans, at;
    // the level shown on each channel, counted from one
    private final int[] pick = new int[Episode.COUNT + 1];
    private float knob, knobV;                // the knob's turn, in channels, and how fast it moves
    private float switchT = 9f, stopT = 9f, edgeT = 9f, flashT = 9f, countT = 9f;
    private int pressed = -1;                 // a button just pressed, for its going in
    private float pressT = 9f;
    private int held;                         // a step key held down: -1 back, 1 on, 0 none
    private float heldT;
    private int leaving = -1;
    private float leaveT;
    private float downX, downY;
    private boolean down;

    private static int[] art;
    private static int artW, artH;
    private static int[][] stills;
    private static int stillW, stillH;

    Bridge(World w, World.Host host) {
        this.w = w;
        this.host = host;
        int best = w.best(), now = w.level + 1;
        for (int i = 0; i < Episode.COUNT; i++)
            if (reached(i)) {
                pick[i] = Episode.first(now) || Episode.of(now) != i ? Episode.FIRST[i] : now;
                chan[chans++] = i;
            }
        if (chans == 0) {
            chan[chans++] = 0;
            pick[0] = 1;
        }
        chan[chans++] = GAME;
        int cur = Episode.of(now);
        if (cur < Episode.COUNT && reached(cur)) pick[cur] = now;
        for (int k = 0; k < chans; k++) if (chan[k] == cur) at = k;
        knob = at;
        if (stills == null) readStills(host.asset("screens.bin"));
        if (art == null) readArt(host.asset("console.bin"));
    }

    /** Whether the game has got that far: an episode once its first level is reached. */
    private boolean reached(int i) {
        return Episode.FIRST[i] <= Math.min(w.best(), Game.LAST + 1);
    }

    /** The last level of an episode the player has reached, counted from one. */
    private int lastOf(int c) {
        if (c == GAME) return 0;
        return Math.min(Episode.LAST[c], Math.min(w.best(), Game.LAST + 1));
    }

    /** count, width, height, then for each still a palette of 256 colors and one byte a pixel. */
    private static void readStills(byte[] b) {
        if (b == null || b.length < 5) return;
        int n = b[0] & 0xFF, sw = ((b[1] & 0xFF) << 8) | (b[2] & 0xFF), sh = ((b[3] & 0xFF) << 8) | (b[4] & 0xFF);
        if (b.length < 5 + n * (768 + sw * sh)) return;
        int[][] s = new int[n][sw * sh];
        int p = 5;
        int[] pal = new int[256];
        for (int k = 0; k < n; k++) {
            for (int c = 0; c < 256; c++, p += 3)
                pal[c] = 0xFF000000 | ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
            for (int q = 0; q < sw * sh; q++) s[k][q] = pal[b[p++] & 0xFF];
        }
        stillW = sw;
        stillH = sh;
        stills = s;
    }

    /**
     * The room's picture: width, height, then RGBA rows, deflated. It is drawn finer than the
     * frame (see Console); here it is brought down to the frame's own grain, each pixel the
     * mean of the ones it covers.
     */
    private static void readArt(byte[] b) {
        if (b == null || b.length < 4) return;
        if ((b[0] & 0xFF) == 0x78) b = Console.inflate(b);
        if (b == null || b.length < 4) return;
        int ww = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), hh = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if ((long) b.length < 4L + (long) ww * hh * 4L) return;
        int q = Math.max(1, ww / World.W), aw = ww / q, ah = hh / q;
        int[] a = new int[aw * ah];
        for (int y = 0; y < ah; y++)
            for (int x = 0; x < aw; x++) {
                int sa = 0, sr = 0, sg = 0, sb = 0;
                for (int j = 0; j < q; j++)
                    for (int i = 0; i < q; i++) {
                        int p = 4 + ((y * q + j) * ww + x * q + i) * 4, al = b[p + 3] & 0xFF;
                        sa += al;
                        sr += (b[p] & 0xFF) * al;
                        sg += (b[p + 1] & 0xFF) * al;
                        sb += (b[p + 2] & 0xFF) * al;
                    }
                int al = sa / (q * q);
                a[y * aw + x] = sa == 0 ? 0 : (al << 24) | (sr / sa << 16) | (sg / sa << 8) | (sb / sa);
            }
        artW = aw;
        artH = ah;
        art = a;
    }

    private int channel() {
        return chan[at];
    }

    // ---- time ----

    void update(float dt) {
        t += dt;
        switchT += dt;
        stopT += dt;
        edgeT += dt;
        flashT += dt;
        pressT += dt;
        countT += dt;
        // the knob springs to its click, a little past and back
        for (int k = 0; k < 4; k++) {
            float h = dt / 4f;
            float acc = -260f * (knob - at) - 16f * knobV;
            knobV += acc * h;
            knob += knobV * h;
        }
        // a step key held runs on
        if (held != 0 && leaving < 0) {
            float was = heldT;
            heldT += dt;
            if (heldT >= RUN_AFTER && (int) ((was - RUN_AFTER) / RUN_EVERY) != (int) ((heldT - RUN_AFTER) / RUN_EVERY))
                step(held);
        }
        if (leaving != -1 && (leaveT += dt) >= 0.22f) {
            int k = leaving;
            leaving = -1;
            if (k == HOME) w.bridgeHome();
            else if (k == GAME_KEY) w.bridgeGame();
            else w.bridgeEnter(k);
        }
    }

    // ---- touch ----

    void touch(int type, float x, float y) {
        if (leaving != -1) return;
        float py = y - offset();
        if (type == World.DOWN) {
            down = true;
            downX = x;
            downY = py;
            // a step key goes at once, and runs on while held
            int st = stepAt(x, py);
            if (st != 0) {
                held = st;
                heldT = 0f;
                press(st < 0 ? 2 : 3);
                step(st);
            }
            return;
        }
        if (type == World.MOVE) {
            if (held != 0 && stepAt(x, py) != held) held = 0;
            return;
        }
        if (type != World.UP || !down) return;
        down = false;
        if (held != 0) {
            held = 0;
            return;
        }
        float dx = x - downX, dy = py - downY;
        // a swipe across the set or the knob: the next channel or the one before
        if (Math.abs(dx) > 24f && Math.abs(dx) > Math.abs(dy) && downY > SY0 && downY < SY0 + SH + 4
                || Math.abs(dx) > 24f && Math.hypot(downX - KX, downY - KY) < KR + 14) {
            turn(dx < 0 ? 1 : -1);
            return;
        }
        if (Math.abs(dx) > 12f || Math.abs(dy) > 12f) return;
        if (Math.hypot(x - KX, py - KY) < KR + 10) {
            turn(x >= KX ? 1 : -1);
            return;
        }
        if (in(x, py, BX[0], BY - 6, BX[0] + BW, BY + BH + 6)) {
            // onto the street, at the level on the counter
            press(0);
            host.sound(Synth.CLANG, 0.7f, 1f);
            host.sound(Synth.TV_OFF, 0.8f, 1f);
            host.buzz(30);
            flashT = 0f;
            leaving = channel() == GAME ? GAME_KEY : pick[channel()];
            leaveT = 0f;
            return;
        }
        if (in(x, py, KEY_X0, KEY_Y0, KEY_X1, KEY_Y1)) {
            // back to the level the player came from, the street as it was
            press(4);
            host.sound(Synth.LATCH, 0.8f, 0.8f);
            host.sound(Synth.TV_OFF, 0.9f, 1f);
            leaving = HOME;
            leaveT = 0f;
        }
    }

    /** Which step key is under a point: -1 back, 1 on, 0 neither. */
    private static int stepAt(float x, float y) {
        if (y < BY - 6 || y >= BY + BH + 6) return 0;
        if (x >= BX[1] && x < BX[1] + STEP_W + 2) return -1;
        if (x >= BX[1] + BW - STEP_W - 2 && x < BX[1] + BW) return 1;
        return 0;
    }

    private static boolean in(float x, float y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private void press(int which) {
        pressed = which;
        pressT = 0f;
    }

    /** The counter, a level back or on, within the levels of this episode reached; against its end, a dead click. */
    private void step(int by) {
        int c = channel(), to = pick[c] + by;
        if (c == GAME || to < Episode.FIRST[c] || to > lastOf(c)) {
            edgeT = 0f;
            held = 0;
            host.sound(Synth.TOCK, 0.9f, 0.45f);
            host.buzz(15);
            return;
        }
        pick[c] = to;
        countT = 0f;
        host.sound(Synth.TOCK, 0.6f, by > 0 ? 1.5f : 1.3f);
        host.buzz(8);
    }

    /** The knob, a click on: to the channel next to this one, or against its stop. */
    private void turn(int by) {
        int to = at + by;
        if (to < 0 || to >= chans) {
            stopT = 0f;
            knobV += by * 4f;
            host.sound(Synth.TOCK, 0.9f, 0.4f);
            host.buzz(25);
            return;
        }
        at = to;
        switchT = 0f;
        host.sound(Synth.TOCK, 0.8f, 1.6f);
        host.sound(Synth.TV_OFF, 0.3f, 1.6f);
        host.buzz(15);
    }

    // ---- drawing ----

    /** Where the room's picture is on the frame, down from the top: centred on it. */
    private int offset() {
        return baseOy;
    }

    private int baseOy;

    void draw(Px f, float alpha) {
        int fw = f.w, fh = f.h;
        baseOy = (fh - PH) / 2;
        int oy = baseOy + Math.round((float) Math.sin(t * Math.PI * 2 / 4.2f) * 0.7f) + (stopT < 0.1f ? 1 : 0);
        java.util.Arrays.fill(f.a, 0xFF000000);
        tube(f, oy);
        // the still parts of the room over it
        if (art != null)
            for (int y = 0; y < fh; y++) {
                int sy = y - oy;
                if (sy < 0 || sy >= artH) continue;
                for (int x = 0; x < Math.min(fw, artW); x++) {
                    int c = art[sy * artW + x];
                    if ((c >>> 24) != 0) f.a[y * fw + x] = c | 0xFF000000;
                }
            }
        alive(f, oy);
        Console.clock(f, oy);
        buttons(f, oy);
        counter(f, oy);
        front(f, oy);
        if (leaving != -1) {
            float k = Math.min(1f, leaveT / 0.22f);
            for (int i = 0; i < f.a.length; i++) f.a[i] = Px.mix(f.a[i], 0xFF000000, k * 0.6f);
        }
    }

    /** The picture on the tube: the channel's still, flickering; snow for a moment as it switches. */
    private void tube(Px f, int oy) {
        int c = channel();
        boolean snow = switchT < 0.16f;
        int[] img = stills == null || c == GAME ? null : stills[Math.min(stills.length - 1, c)];
        float flick = 1f + 0.04f * (float) Math.sin(t * 53f) * (float) Math.sin(t * 7.3f);
        int y0 = Math.max(0, SY0 + oy), y1 = Math.min(f.h, SY0 + SH + oy);
        for (int y = y0; y < y1; y++) {
            int ty = y - oy - SY0;
            float v = (ty - SH * 0.5f) / (SH * 0.5f);
            for (int x = 0; x < f.w; x++) {
                int tx = x - SX0;
                float u = (tx - SW * 0.5f) / (SW * 0.5f);
                int col;
                if (snow || img == null && c != GAME) {
                    int g = 40 + rnd.nextInt(170);
                    col = (g << 16) | (g << 8) | g;
                } else if (c == GAME) col = gamePicture(tx, ty);
                else col = img[Math.min(stillH - 1, ty * stillH / SH) * stillW + Math.min(stillW - 1, tx * stillW / SW)];
                float k = flick * (1f - 0.3f * (float) Math.pow(u * u * 0.7f + v * v, 1.4));
                if ((y & 1) == 1) k *= 0.84f;
                col = Street.scale(col, k);
                float sheen = Math.max(0f, 1f - (float) Math.hypot(u + 0.5f, v + 0.62f) / 0.5f);
                if (sheen > 0f) col = Street.mix(col, 0xFFFFFF, sheen * sheen * 0.2f);
                f.a[y * f.w + x] = 0xFF000000 | col;
            }
        }
    }

    /** The lamp's light swaying on the wall over the set, and a fan's shadow crawling round there. */
    private void alive(Px f, int oy) {
        float la = 0.06f * (float) Math.sin(t * 1.1f);
        float lx = 161f + 30f * (float) Math.sin(la);
        for (int sy = 30; sy < 150; sy++) {
            int y = sy + oy;
            if (y < 0 || y >= f.h) continue;
            float half = 18f + (sy - 40) * 0.55f;
            for (int x = 0; x < f.w; x++) {
                int i = y * f.w + x, c = f.a[i];
                float k = 1f;
                float d = Math.abs(x - lx - (sy - 40) * (float) Math.sin(la));
                if (sy > 40 && d < half) k += (1f - d / half) * 0.35f * (float) Math.exp(-(sy - 40) / 170f);
                // the fan, somewhere off to the right, its three blades going round
                double a = Math.atan2(sy - 70, x - 320) - t * 0.9;
                if (Math.cos(a * 3) > 0.6 && Math.hypot(x - 320, sy - 70) < 260) k *= 0.75f;
                if (k != 1f) f.a[i] = Px.scale(c, k);
            }
        }
    }

    /**
     * The game's channel: a ball coming out of a deep blue, its red stitching, and the hammer's
     * head beside it; drawn here, a point of the tube at a time.
     */
    private int gamePicture(int tx, int ty) {
        float u = tx - SW * 0.42f, v = ty - SH * 0.5f, r = SH * 0.3f;
        float d = (float) Math.sqrt(u * u + v * v);
        if (d < r) {
            // the stitching: two arcs on the ball
            float a = (float) Math.abs(Math.hypot(u + r * 1.25f, v) - r * 0.95f), b = (float) Math.abs(Math.hypot(u - r * 1.25f, v) - r * 0.95f);
            if (Math.min(a, b) < 2.2f && ((ty / 4) & 1) == 0) return 0xD8322A;
            float k = 1f - 0.35f * d / r;
            return Street.scale(0xF4F1EA, k);
        }
        // the hammer's head, a block of wood with dark bands, to the right
        float hx = tx - SW * 0.78f, hy = ty - SH * 0.42f;
        if (Math.abs(hx) < 26f && Math.abs(hy) < 38f) return Math.abs(hy) > 28f ? 0x55524F : 0xDFA868;
        if (Math.abs(hx) < 6f && hy > 38f && hy < 100f) return 0x9A6638;
        return Street.mix(0x0E2A5A, 0x2F5FA8, ty / (float) SH);
    }

    /** A filled triangle, hard-edged. */
    private static void tri(Px f, float x0, float y0, float x1, float y1, float x2, float y2, int c) {
        int ya = (int) Math.floor(Math.min(y0, Math.min(y1, y2))), yb = (int) Math.ceil(Math.max(y0, Math.max(y1, y2)));
        int xa = (int) Math.floor(Math.min(x0, Math.min(x1, x2))), xb = (int) Math.ceil(Math.max(x0, Math.max(x1, x2)));
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-3f) return;
        for (int y = ya; y <= yb; y++)
            for (int x = xa; x <= xb; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area;
                float w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area;
                float w2 = 1f - w0 - w1;
                if (w0 >= -0.01f && w1 >= -0.01f && w2 >= -0.01f) f.set(x, y, c);
            }
    }

    private static void quad(Px f, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        tri(f, x0, y0, x1, y1, x2, y2, c);
        tri(f, x0, y0, x2, y2, x3, y3, c);
    }

    /** A key in a well: its face lit or dim, its top edge catching the light, pressed in or not. */
    private static void keyFace(Px f, int x, int y, int w, int face, boolean in) {
        int y0 = y + (in ? 2 : 0);
        if (in) face = Px.scale(face, 1.25f);
        f.rect(x, y0, x + w, y0 + BH, face);
        if (!in) {
            f.rect(x, y0, x + w, y0 + 2, Px.scale(face, 1.35f));
            f.rect(x, y0 + BH - 3, x + w, y0 + BH, Px.scale(face, 0.55f));
        }
    }

    /** The row of keys: the green one that goes, and the two that step the level back and on. */
    private void buttons(Px f, int oy) {
        int y = BY + oy;
        boolean in0 = pressed == 0 && pressT < 0.15f;
        keyFace(f, BX[0], y, BW, 0xFF4FE36A, in0);
        int cx = BX[0] + BW / 2, cy = y + (in0 ? 2 : 0) + BH / 2;
        tri(f, cx - 6, cy - 9, cx + 9, cy, cx - 6, cy + 9, 0xFFFFFFFF);
        int c = channel();
        for (int k = 0; k < 2; k++) {
            int x = k == 0 ? BX[1] : BX[1] + BW - STEP_W;
            boolean can = c != GAME && (k == 0 ? pick[c] > Episode.FIRST[c] : pick[c] < lastOf(c));
            boolean in = (pressed == 2 + k && pressT < 0.15f) || held == (k == 0 ? -1 : 1);
            int face = Px.scale(0xFF000000 | AMBER, can ? 0.85f : 0.3f);
            keyFace(f, x, y, STEP_W, face, in);
            int sx = x + STEP_W / 2, sy = y + (in ? 2 : 0) + BH / 2, dir = k == 0 ? -1 : 1;
            int ic = can ? 0xFF2A1404 : Px.scale(face, 1.6f);
            tri(f, sx - dir * 6, sy - 9, sx + dir * 8, sy, sx - dir * 6, sy + 9, ic);
        }
    }

    /** The level on the counter, the channel's own; on the back key the level the player came from. */
    private void counter(Px f, int oy) {
        int n = pick[channel()];
        boolean game = channel() == GAME;
        // against the end of the episode, the counter blinks once
        int on = edgeT < 0.3f && ((int) (edgeT * 10f) & 1) == 0 ? 0xFFD06A : AMBER;
        for (int i = 0; i < 3; i++) {
            int d = game ? -1 : (n / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            Console.digit(f, 104 + i * 27, DY + 15 + oy, 21, 38, 4, d, on, AMBER_OFF);
        }
        int back = w.level + 1;        // the level being played, counted from one
        boolean in = pressed == 4 && pressT < 0.15f;
        for (int i = 0; i < 3; i++) {
            int d = back < 1 ? -1 : (back / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            Console.digit(f, 28 + i * 16, DY + 40 + oy + (in ? 2 : 0), 12, 21, 3, d, AMBER, 0x0C0502);
        }
    }

    /** The front of the desk: the big red knob in its middle, the marks of the channels round it. */
    private void front(Px f, int oy) {
        f.rect(0, FRONT + oy, f.w, f.h, 0xFF23272D);
        f.rect(0, FRONT + oy, f.w, FRONT + 1 + oy, 0xFF3E444C);
        // the marks of the channels reached, the one on now brighter
        for (int c = 0; c < chans; c++) {
            double am = -Math.PI / 2 - STEP * at + STEP * c;
            int mx = (int) Math.round(KX + (KR + 9) * Math.cos(am)), my = (int) Math.round(KY + oy + (KR + 9) * Math.sin(am));
            f.disc(mx, my, 3.5f, c == at ? 0xFFFF8A2A : 0xFF7A3A10);
        }
        // the knob: shadow, a ribbed black rim, the red cap with its shine, a fat white pointer
        float a0 = (float) (-Math.PI / 2 - STEP * at + STEP * knob);
        int ky = KY + oy;
        f.disc(KX + 3, ky + 5, KR, 0xFF0C0D0F);
        for (int y = -KR; y <= KR; y++)
            for (int x = -KR; x <= KR; x++) {
                float r = (float) Math.sqrt(x * x + y * y);
                if (r > KR) continue;
                int c;
                if (r > KR - 7) {
                    double a = Math.atan2(y, x) - a0;
                    c = ((int) Math.floor(a / (Math.PI * 2) * 36 + 360) & 1) == 0 ? 0xFF3A3F46 : 0xFF262A30;
                } else if (r > KR - 10) c = 0xFF8E2214;
                else {
                    c = 0xFFC8361E;
                    float sx = x + 0.45f * KR, sy = y + 0.45f * KR;
                    if (sx * sx + sy * sy < (KR * 0.55f) * (KR * 0.55f) && x + y < -KR * 0.35f) c = 0xFFF0684A;
                }
                f.set(KX + x, ky + y, c);
            }
        float px = (float) Math.cos(a0), py = (float) Math.sin(a0), nx = -py, ny = px;
        quad(f, KX + nx * 6 - px * 6, ky + ny * 6 - py * 6, KX - nx * 6 - px * 6, ky - ny * 6 - py * 6,
                KX - nx * 4 + px * (KR - 12), ky - ny * 4 + py * (KR - 12), KX + nx * 4 + px * (KR - 12), ky + ny * 4 + py * (KR - 12), 0xFFF4F0E6);
        f.disc(KX + px * (KR - 18), ky + py * (KR - 18), 3.5f, 0xFFFFFFFF);
    }
}
