package com.shumtugle.freedie;

import java.util.Random;

/**
 * The dispatcher's bridge: the same room the player glances into between rounds, but here he
 * sits down at it. One set; the episodes are its channels, and a big red knob on the front of
 * the desk clicks from one to the next. There are only as many channels as the game has
 * reached: the knob turns as far as the last one and stops against it there, and nothing
 * says how many more there will be.
 *
 * Over the set, the slot. A channel paid for shows its picture clear, and its cube stands lit
 * in the slot's window. A channel not paid for comes scrambled, as a pay channel did: grey,
 * torn into bands that slide sideways and roll; the slot's window is an empty slit and its
 * lamp blinks amber. A tap on the slot drops a cube from the tray on the desk into it, like a
 * token, and the picture comes clear for good. With no cubes left the slot only clicks.
 *
 * The green button goes into the channel: its episode's first level, or the fight. The key
 * at the left, the number of the level the player came from on it, goes back there, to the
 * street as it was. Everything a finger touches is big.
 *
 * And the room is alive while one sits there: the lamp sways a little and its light with it,
 * a fan's shadow crawls round on the wall, the tube flickers, the eye breathes.
 *
 * Drawn on the flat layer; the still parts of the room are the same picture the console
 * between rounds is drawn from.
 */
final class Bridge {
    static final int SCREENS = Episode.COUNT + 3, HUB = Episode.COUNT, GAME_A = Episode.COUNT + 1, GAME_B = Episode.COUNT + 2;
    static final int HOME = 3;
    static final float OPEN_T = 0.3f, TV_ON_T = 0.3f;

    // where things are in the room's picture (274 x 640), its own pixels
    private static final int PH = 640;
    private static final int SX0 = -4, SY0 = 162, SW = 282, SH = 212;
    private static final int SLOT_X0 = 176, SLOT_Y0 = 46, SLOT_X1 = 262, SLOT_Y1 = 142, CX = 218, CY = 96;
    private static final int[] BX = {4, 72, 140, 208};
    private static final int BY = 422, BW = 58, BH = 32;
    private static final int DY = 474;
    private static final int KEY_X0 = 16, KEY_Y0 = DY + 4, KEY_X1 = 84, KEY_Y1 = DY + 64;
    private static final int TRAY_X0 = 196, TRAY_Y0 = DY + 10, TRAY_X1 = 262, TRAY_Y1 = DY + 58;
    private static final int FRONT = 548;
    private static final int KX = 137, KY = 592, KR = 50;
    private static final float STEP = 0.9f;
    private static final int AMBER = 0xFF8A2A, AMBER_OFF = 0x1A0B04;

    private final World w;
    private final World.Host host;
    private final Random rnd = new Random(0xB21D6EL);
    float t;                                  // seconds since it opened
    // the channels there are, in order: the episodes reached, then the fight once she has come
    private final int[] chan = new int[SCREENS];
    private int chans, at;
    private float knob, knobV;                // the knob's turn, in channels, and how fast it moves
    private float switchT = 9f, stopT = 9f, emptyT = 9f, flashT = 9f;
    private int pressed = -1;                 // a button just pressed, for its going in
    private float pressT = 9f;
    private float tokenT = -1f;               // a cube on its way from the tray into the slot
    private float clearT = 9f;                // since the channel paid for began to come clear
    private int leaving = -1;
    private float leaveT;
    private float downX, downY;
    private boolean down;
    private final boolean[] slot = new boolean[SCREENS];
    private final boolean fought;

    private static int[] art;
    private static int artW, artH;
    private static int[][] stills;
    private static int stillW, stillH;

    Bridge(World w, World.Host host) {
        this.w = w;
        this.host = host;
        for (int i = 0; i < SCREENS; i++) slot[i] = host.loadInt("slot" + i) == 1;
        fought = host.loadInt("fought") == 1;
        // a cube put into a screen the game has not reached yet goes back to the tray: nothing
        // is opened ahead of where the player has got to
        for (int i = 0; i < SCREENS; i++)
            if (slot[i] && !reached(i)) {
                slot[i] = false;
                host.saveInt("slot" + i, 0);
                w.cubes++;
                host.saveInt("cubes", w.cubes);
            }
        for (int i = 0; i < Episode.COUNT; i++) if (reached(i)) chan[chans++] = i;
        if (reached(GAME_B)) chan[chans++] = GAME_B;
        if (chans == 0) chan[chans++] = 0;
        int cur = Episode.of(w.level + 1);
        for (int k = 0; k < chans; k++) if (chan[k] == cur) at = k;
        knob = at;
        if (stills == null) readStills(host.asset("screens.bin"));
        if (art == null) readArt(host.asset("console.bin"));
    }

    /** Whether the game has got that far: an episode once its first level is reached, the fight once she has come. */
    boolean reached(int i) {
        if (i < Episode.COUNT) return Episode.FIRST[i] <= w.best();
        if (i == GAME_B) return fought;
        return false;
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

    /** The room's picture: width, height, then RGBA rows. */
    private static void readArt(byte[] b) {
        if (b == null || b.length < 4) return;
        int ww = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), hh = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if (b.length < 4 + ww * hh * 4) return;
        int[] a = new int[ww * hh];
        for (int k = 0, p = 4; k < a.length; k++, p += 4)
            a[k] = ((b[p + 3] & 0xFF) << 24) | ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
        artW = ww;
        artH = hh;
        art = a;
    }

    private int channel() {
        return chan[at];
    }

    private boolean paid() {
        return slot[channel()];
    }

    // ---- time ----

    void update(float dt) {
        t += dt;
        switchT += dt;
        stopT += dt;
        emptyT += dt;
        flashT += dt;
        pressT += dt;
        clearT += dt;
        // the knob springs to its click, a little past and back
        for (int k = 0; k < 4; k++) {
            float h = dt / 4f;
            float acc = -260f * (knob - at) - 16f * knobV;
            knobV += acc * h;
            knob += knobV * h;
        }
        if (tokenT >= 0f) {
            tokenT += dt;
            if (tokenT >= TOKEN_T) {
                tokenT = -1f;
                int c = channel();
                slot[c] = true;
                host.saveInt("slot" + c, 1);
                clearT = 0f;
                host.sound(Synth.LATCH, 1f, 1f);
                host.sound(Synth.CLANG, 0.5f, 0.6f);
                host.buzz(40);
            }
        }
        if (leaving >= 0 && (leaveT += dt) >= 0.22f) {
            int k = leaving;
            leaving = -1;
            if (k == HOME) w.bridgeAct(HOME);
            else if (k == GAME_B) w.bridgeFight();
            else w.bridgeEnter(k);
        }
    }

    private static final float TOKEN_T = 0.5f, CLEAR_T = 0.8f;

    // ---- touch ----

    void touch(int type, float x, float y) {
        if (leaving >= 0) return;
        float py = y - offset();
        if (type == World.DOWN) {
            down = true;
            downX = x;
            downY = py;
            return;
        }
        if (type != World.UP || !down) return;
        down = false;
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
        if (in(x, py, SLOT_X0, SLOT_Y0, SLOT_X1, SLOT_Y1) || in(x, py, TRAY_X0, TRAY_Y0, TRAY_X1, TRAY_Y1)
                || in(x, py, BX[1], BY - 6, BX[1] + BW, BY + BH + 6)) {
            if (in(x, py, BX[1], BY - 6, BX[1] + BW, BY + BH + 6)) press(1);
            token();
            return;
        }
        if (in(x, py, BX[0], BY - 6, BX[0] + BW, BY + BH + 6)) {
            press(0);
            if (!paid() || tokenT >= 0f) {
                empty();
                return;
            }
            host.sound(Synth.CLANG, 0.7f, 1f);
            host.sound(Synth.TV_OFF, 0.8f, 1f);
            host.buzz(30);
            flashT = 0f;
            leaving = channel();
            leaveT = 0f;
            return;
        }
        if (in(x, py, BX[2], BY - 6, BX[2] + BW, BY + BH + 6) || in(x, py, BX[3], BY - 6, BX[3] + BW, BY + BH + 6)) {
            press(in(x, py, BX[2], BY - 6, BX[2] + BW, BY + BH + 6) ? 2 : 3);
            host.sound(Synth.TOCK, 0.8f, 0.45f);
            host.buzz(12);
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

    private static boolean in(float x, float y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private void press(int which) {
        pressed = which;
        pressT = 0f;
    }

    /** The knob, a click on: to the channel next to this one, or against its stop. */
    private void turn(int by) {
        int to = at + by;
        if (to < 0 || to >= chans || tokenT >= 0f) {
            stopT = 0f;
            knobV += by * 4f;
            host.sound(Synth.TOCK, 0.9f, 0.4f);
            host.buzz(25);
            return;
        }
        at = to;
        switchT = 0f;
        clearT = 9f;
        host.sound(Synth.TOCK, 0.8f, 1.6f);
        host.sound(Synth.TV_OFF, 0.3f, 1.6f);
        host.buzz(15);
    }

    /** A cube from the tray, into the slot like a token, for the channel on now. */
    private void token() {
        if (paid() || tokenT >= 0f) {
            host.sound(Synth.TOCK, 0.6f, 0.9f);
            return;
        }
        if (w.cubes <= 0) {
            empty();
            return;
        }
        w.spendCube();
        tokenT = 0f;
        host.sound(Synth.WHOOSH, 0.5f, 1.5f);
    }

    /** Nothing to pay with, or nothing paid: a dead click, the slot's lamp blinking twice. */
    private void empty() {
        emptyT = 0f;
        host.sound(Synth.TOCK, 0.9f, 0.45f);
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
        slotWindow(f, oy);
        buttons(f, oy);
        counter(f, oy);
        tray(f, oy);
        front(f, oy);
        token(f, oy);
        if (leaving >= 0) {
            float k = Math.min(1f, leaveT / 0.22f);
            for (int i = 0; i < f.a.length; i++) f.a[i] = Px.mix(f.a[i], 0xFF000000, k * 0.6f);
        }
    }

    /** The picture on the tube: the channel's still, clear, scrambled or snow, flickering. */
    private void tube(Px f, int oy) {
        int c = channel();
        boolean snow = switchT < 0.16f;
        float scr = paid() && tokenT < 0f ? Math.max(0f, 1f - clearT / CLEAR_T) : 1f;
        if (paid() && tokenT < 0f && clearT >= CLEAR_T) scr = 0f;
        int[] img = stills == null ? null : stills[Math.min(stills.length - 1, c < Episode.COUNT ? c : Episode.of(w.level + 1))];
        float flick = 1f + 0.04f * (float) Math.sin(t * 53f) * (float) Math.sin(t * 7.3f);
        int roll = Math.round(t * 40f * scr) % SH;
        int y0 = Math.max(0, SY0 + oy), y1 = Math.min(f.h, SY0 + SH + oy);
        for (int y = y0; y < y1; y++) {
            int ty = y - oy - SY0;
            float v = (ty - SH * 0.5f) / (SH * 0.5f);
            int band = ty / 8;
            int slide = scr > 0f ? Math.round(scr * (30f * (float) Math.sin(t * 7f + band * 0.37f) + (rnd.nextInt(13) - 6))) : 0;
            int sy = (ty + roll) % SH;
            for (int x = 0; x < f.w; x++) {
                int tx = x - SX0;
                float u = (tx - SW * 0.5f) / (SW * 0.5f);
                int col;
                if (snow || img == null) {
                    int g = 40 + rnd.nextInt(170);
                    col = (g << 16) | (g << 8) | g;
                } else {
                    int sx = ((tx + slide) % SW + SW) % SW;
                    col = img[Math.min(stillH - 1, sy * stillH / SH) * stillW + Math.min(stillW - 1, sx * stillW / SW)];
                    if (c == GAME_B) {
                        // the fight: her red street
                        int l = (((col >> 16) & 0xFF) * 3 + ((col >> 8) & 0xFF) * 5 + (col & 0xFF) * 2) / 10;
                        col = (Math.min(255, l * 3 / 2 + 30) << 16) | ((l * 2 / 5) << 8) | (l / 3);
                    }
                    if (scr > 0f) {
                        int l = (((col >> 16) & 0xFF) * 3 + ((col >> 8) & 0xFF) * 5 + (col & 0xFF) * 2) / 10;
                        col = Street.mix(col, (l << 16) | (l << 8) | l, scr);
                        if (ty % 3 == 0) col = Street.scale(col, 1f - 0.35f * scr);
                    }
                }
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

    /** The slot's window: the cube in it for a channel paid, or the slit waiting; its lamp under it. */
    private void slotWindow(Px f, int oy) {
        f.rect(188, 58 + oy, 251, 133 + oy, 0xFF0B0C0E);
        boolean shown = paid() && tokenT < 0f && switchT >= 0.16f;
        if (shown) cube(f, CX, CY + oy, 26);
        else {
            f.rect(CX - 20, CY - 2 + oy, CX + 21, CY + 3 + oy, 0xFF000000);
            f.rect(CX - 20, CY - 3 + oy, CX + 21, CY - 2 + oy, 0xFF3A3F46);
        }
        int lamp;
        if (emptyT < 0.5f) lamp = ((int) (emptyT * 8f) & 1) == 0 ? 0xFFFF5A30 : 0xFF2A0A04;
        else if (shown) lamp = 0xFF5CFF7A;
        else lamp = ((int) (t * 3f) & 1) == 0 ? 0xFFFFB030 : 0xFF3A2408;
        f.rect(212, 136 + oy, 225, 140 + oy, lamp);
    }

    private static void cube(Px f, int cx, int cy, int r) {
        tri(f, cx, cy - r, cx + r, cy - r / 2, cx, cy, 0xFFFFE07A);
        tri(f, cx, cy - r, cx - r, cy - r / 2, cx, cy, 0xFFFFE07A);
        quad(f, cx - r, cy - r / 2, cx, cy, cx, cy + r, cx - r, cy + r / 2, 0xFFE0A82E);
        quad(f, cx + r, cy - r / 2, cx, cy, cx, cy + r, cx + r, cy + r / 2, 0xFFB07E1E);
    }

    private static void quad(Px f, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        tri(f, x0, y0, x1, y1, x2, y2, c);
        tri(f, x0, y0, x2, y2, x3, y3, c);
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

    /** The row of buttons: enter, lit; the cube's, lit while there is a channel to pay; the hammer's dim; a cap where the games will be. */
    private void buttons(Px f, int oy) {
        int[] col = {0x4FE36A, 0xFFC93A, 0x9AA3B4};
        boolean[] lit = {paid(), !paid() && w.cubes > 0, false};
        for (int i = 0; i < 4; i++) {
            int x = BX[i], y = BY + oy;
            boolean in = pressed == i && pressT < 0.15f;
            int y0 = y + (in ? 2 : 0);
            if (i == 3) {
                // a blank cap on two screws, until there is a game behind it
                f.rect(x, y, x + BW, y + BH, 0xFF2E3226);
                f.rect(x, y, x + BW, y + 1, 0xFF3E4434);
                f.rect(x + 5, y + 14, x + 8, y + 17, 0xFF7A8070);
                f.rect(x + BW - 8, y + 14, x + BW - 5, y + 17, 0xFF7A8070);
                continue;
            }
            int face = Px.scale(0xFF000000 | col[i], lit[i] ? 1f : 0.32f);
            if (in) face = Px.scale(face, 1.25f);
            f.rect(x, y0, x + BW, y0 + BH, face);
            if (!in) {
                f.rect(x, y0, x + BW, y0 + 2, Px.scale(face, 1.35f));
                f.rect(x, y0 + BH - 3, x + BW, y0 + BH, Px.scale(face, 0.55f));
            }
            int ic = in || (lit[i] && i == 0) ? 0xFFFFFFFF : Px.scale(face, 1.8f);
            int cx = x + BW / 2, cy = y0 + BH / 2;
            if (i == 0) tri(f, cx - 5, cy - 7, cx + 7, cy, cx - 5, cy + 7, ic);
            else if (i == 1) cube(f, cx, cy, 7);
            else {
                f.rect(cx - 7, cy - 6, cx + 6, cy - 1, ic);
                f.rect(cx - 1, cy - 1, cx + 2, cy + 9, ic);
            }
        }
    }

    /** The first level of the channel on the counter, and on the back key the level the player came from. */
    private void counter(Px f, int oy) {
        int c = channel();
        int n = c < Episode.COUNT ? Episode.FIRST[c] : -1;
        for (int i = 0; i < 3; i++) {
            int d = n < 0 ? -1 : (n / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            Console.digit(f, 104 + i * 27, DY + 15 + oy, 21, 38, 4, d, AMBER, AMBER_OFF);
        }
        int back = w.level + 1;        // the level being played, counted from one
        boolean in = pressed == 4 && pressT < 0.15f;
        for (int i = 0; i < 3; i++) {
            int d = back < 1 ? -1 : (back / (i == 0 ? 100 : i == 1 ? 10 : 1)) % 10;
            Console.digit(f, 28 + i * 16, DY + 40 + oy + (in ? 2 : 0), 12, 21, 3, d, AMBER, 0x0C0502);
        }
    }

    /** The tray of spare cubes on the desk: as many as there are, as many as fit. */
    private void tray(Px f, int oy) {
        f.rect(TRAY_X0, TRAY_Y0 + oy, TRAY_X1, TRAY_Y1 + oy, 0xFF15171A);
        f.rect(TRAY_X0 + 2, TRAY_Y0 + 21 + oy, TRAY_X1 - 2, TRAY_Y0 + 23 + oy, 0xFF2A2F36);
        f.rect(TRAY_X0 + 2, TRAY_Y1 - 4 + oy, TRAY_X1 - 2, TRAY_Y1 - 2 + oy, 0xFF3A4048);
        int n = Math.min(8, w.cubes);
        for (int i = 0; i < n; i++) {
            int row = i / 4, col = i % 4;
            cube(f, TRAY_X0 + 11 + col * 15, TRAY_Y1 - 12 - (1 - row) * 0 - row * 22 + oy, 7);
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
        // the bank at the right of the front: the golden cubes won from the buildings, behind bars
        int bank = host.loadInt("bank");
        if (bank > 0) Console.bankBox(f, 200, FRONT + 14 + oy, 266, FRONT + 64 + oy, bank, 0f);
    }

    /** The cube on its way: up from the tray in an arc, into the slit. */
    private void token(Px f, int oy) {
        if (tokenT < 0f) return;
        float k = Math.min(1f, tokenT / TOKEN_T);
        k = k * k * (3f - 2f * k);
        float sx = TRAY_X0 + 11 + Math.min(3, w.cubes) * 15, sy = TRAY_Y1 - 12;
        float x = sx + (CX - sx) * k, y = sy + (CY - sy) * k - (float) Math.sin(k * Math.PI) * 60f;
        cube(f, Math.round(x), Math.round(y) + oy, Math.round(10 - 2 * k));
    }
}
