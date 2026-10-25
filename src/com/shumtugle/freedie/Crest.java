package com.shumtugle.freedie;

/**
 * The top of the frame during a level, laid out like a coat of arms. In the middle a medallion
 * with the level's number; its colour is the attempts at this level, ripening from green
 * through amber to red as the tenth nears, and a ring of ten segments round it counts them.
 * On the left wing the stars, nearest first, one burnt a throw, and under them the heavy
 * throws left; on the right wing the way through the level, filling toward a flag as the
 * building comes down, and under it the golden cubes in the rack.
 *
 * Every change shows: a star flares and scatters sparks as it burns, a segment of the ring
 * flashes as an attempt is lost, the track sparks at its edge and turns green with the flag
 * when it is reached, the stars flash on a win, the cubes catch a glint now and then, and
 * with the golden hammer in hand the medallion turns to gold.
 */
final class Crest {
    // what it shows, set by the level every frame
    int level, stars, fails, cubes, heavyShown, heavyLeft;
    boolean heavyOn, goldOn, won;
    float knocked;                 // share of the building off the plinth
    float markT = -1f;             // the new heavy marks dropping in, seconds; below 0 none
    static final float MARK_FALL = 0.32f, MARK_GAP = 0.24f;
    static final int TRIES = 10;   // the attempts a ring has room for

    private float clock;
    private final float[] burnt = {-1f, -1f, -1f, -1f, -1f};   // seconds since each star burnt
    private int failsShown;
    private float failFlash = -1f, reached = -1f;

    static final int CX = World.W / 2, CY = 21, R = 15;

    void update(float dt) {
        clock += dt;
        for (int i = 0; i < burnt.length; i++) if (burnt[i] >= 0f) burnt[i] += dt;
        if (failFlash >= 0f) failFlash += dt;
        boolean done = knocked >= Game.CLEAR;
        if (done && reached < 0f) reached = 0f;
        else if (!done) reached = -1f;
        if (reached >= 0f) reached += dt;
    }

    /** A star has just been spent: it flares and scatters. */
    void burn(int i) {
        if (i >= 0 && i < burnt.length) burnt[i] = 0f;
    }

    /** A new level or a new try: the stars whole again. */
    void fresh() {
        for (int i = 0; i < burnt.length; i++) burnt[i] = -1f;
        reached = -1f;
    }

    /** The attempts as kept from an earlier game: shown as they are, without a flash. */
    void settle(int n) {
        failsShown = n;
    }

    /** The attempts as they now stand, shown once the street is back in sight: a new one flashes. */
    void attempts(int n) {
        if (n > failsShown) failFlash = 0f;
        failsShown = n;
    }

    void draw(Px f) {
        medallion(f);
        // left wing: the stars from the medallion outward, the outermost spent first
        for (int i = 0; i < Game.STARS; i++) star(f, CX - R - 13 - i * 15, CY - 3, 6.8f, i);
        heavy(f);
        // right wing: the way through the level, and the cubes
        track(f, CX + R + 7, CY - 6, 70);
        int x0 = CX + R + 7;
        if (cubes <= 7) for (int i = 0; i < cubes; i++) cube(f, x0 + i * 11, CY + 6, i);
        else {
            cube(f, x0, CY + 6, 0);
            bold(f, "X" + cubes, x0 + 12, CY + 7, 1, 0xFFFFE89A, 0xFF3A2410);
        }
    }

    // ---- the medallion ----

    /** How ripe the attempts are: green, amber, red as the tenth nears. */
    static int ripe(int n) {
        float r = Math.min(1f, n / (float) TRIES);
        return r < 0.5f ? mix(0xFF9ED67A, 0xFFF2B640, r * 2f) : mix(0xFFF2B640, 0xFFE0402C, (r - 0.5f) * 2f);
    }

    private void medallion(Px f) {
        int bg = goldOn ? 0xFFF2C64A : ripe(failsShown);
        // the last attempts left: it beats
        float beat = !goldOn && failsShown >= TRIES - 2 ? 0.5f + 0.5f * (float) Math.sin(clock * 7f) : 0f;
        if (beat > 0f) bg = mix(bg, 0xFFFFE0A0, beat * 0.35f);
        float sweep = (clock * 0.8f) % 2.4f;
        for (int y = -R - 4; y <= R + 4; y++)
            for (int x = -R - 4; x <= R + 4; x++) {
                float d = (float) Math.sqrt(x * x + y * y);
                if (d > R + 3.6f) continue;
                double turn = (Math.atan2(x, -y) + Math.PI * 2) % (Math.PI * 2) / (Math.PI * 2) * TRIES;
                int seg = (int) turn;
                float within = (float) (turn - seg);
                if (d > R + 0.5f) {
                    // the ring of attempts, a gap between segments
                    if (within < 0.12f || within > 0.88f) continue;
                    int c;
                    if (goldOn) c = mix(0xFFC0902C, 0xFFFFF0B0, 0.5f + 0.5f * (float) Math.sin(clock * 5f + seg));
                    else if (seg < failsShown) {
                        c = ripe(seg + 1);
                        // the newest one flashes white as it lights
                        if (seg == failsShown - 1 && failFlash >= 0f && failFlash < 0.8f)
                            c = mix(0xFFFFFFFF, c, failFlash / 0.8f);
                    } else c = 0xFF3A2410;
                    f.set(CX + x, CY + y, c);
                } else if (d > R - 1.5f) {
                    f.set(CX + x, CY + y, goldOn ? 0xFF8A5A12 : 0xFF5A3A12);
                } else {
                    float lit = 1.15f - 0.35f * (x + y) / (2f * R);
                    int c = Px.scale(bg, lit);
                    // gold catches a sweep of light
                    if (goldOn) {
                        float u = (x + y + 2f * R) / (4f * R);
                        float band = 1f - Math.min(1f, Math.abs(u - sweep + 0.6f) * 8f);
                        if (band > 0f) c = mix(c, 0xFFFFFFFF, band * 0.6f);
                    }
                    f.set(CX + x, CY + y, c);
                }
            }
        String lv = String.valueOf(level + 1);
        boolean warm = goldOn || failsShown >= TRIES - 2;
        bold(f, lv, CX - PixelFont.width(lv, 2) / 2, CY - 7, 2, warm ? 0xFFFFF0A0 : 0xFFFFFFFF, 0xFF3A2410);
    }

    // ---- the stars ----

    private void star(Px f, int cx, int cy, float r, int i) {
        boolean on = i < stars;
        float age = burnt[i];
        boolean burning = !on && age >= 0f && age < 0.7f;
        boolean flash = won && on && ((int) (clock * 6) & 1) == 0;
        int R0 = (int) Math.ceil(r) + 2;
        float flare = burning ? Math.max(0f, 1f - age / 0.35f) : 0f;
        for (int y = -R0; y <= R0; y++)
            for (int x = -R0; x <= R0; x++) {
                float d = starDist(x, y, r * (1f + 0.35f * flare));
                if (on || flare > 0f) {
                    if (d < -1.0f) {
                        float k = 1.12f - 0.3f * (x + y) / (2f * r);
                        int c = Px.scale(0xFFFFC93A, k);
                        if (x + y < -r * 0.5f) c = mix(c, 0xFFFFF4C0, 0.6f);
                        if (flash) c = 0xFFFFFFFF;
                        if (flare > 0f) c = mix(c, 0xFFFFFFFF, flare);
                        f.set(cx + x, cy + y, c);
                    } else if (d < 0.3f) f.set(cx + x, cy + y, 0xFF7A4A0E);
                } else {
                    if (d < -1.0f) blend(f, cx + x, cy + y, 0xFF2A1C10, 0.45f);
                    else if (d < 0.3f) blend(f, cx + x, cy + y, 0xFF5A3A12, 0.8f);
                }
            }
        if (on) {
            // a glint on the upper left; the last star left glows round its edge
            f.set(cx - 2, cy - 2, 0xFFFFFFFF);
            f.set(cx - 3, cy - 2, 0xFFFFF4C0);
            f.set(cx - 2, cy - 3, 0xFFFFF4C0);
            if (i == stars - 1 && stars == 1) {
                float g = 0.35f + 0.25f * (float) Math.sin(clock * 6f);
                for (int y = -R0; y <= R0; y++)
                    for (int x = -R0; x <= R0; x++) {
                        float d = starDist(x, y, r);
                        if (d > 0.3f && d < 2.2f) blend(f, cx + x, cy + y, 0xFFFFE9A0, g * (1f - (d - 0.3f) / 1.9f));
                    }
            }
        }
        if (burning) {
            // sparks fly out and fall, fading from white to ember
            for (int s = 0; s < 9; s++) {
                double a = s * 2.399 + i * 1.3;
                float v = 14f + (s * 37 % 11);
                float sx = cx + (float) Math.cos(a) * v * age, sy = cy + (float) Math.sin(a) * v * age + 30f * age * age;
                int c = mix(0xFFFFFFFF, 0xFFE0602C, age / 0.7f);
                f.set(Math.round(sx), Math.round(sy), c);
                if (age < 0.3f) f.set(Math.round(sx) + 1, Math.round(sy), mix(c, 0xFFFFC93A, 0.5f));
            }
        }
    }

    private static float starDist(float x, float y, float r) {
        double a = Math.atan2(x, -y), seg = Math.PI * 2 / 5;
        double m = ((a % seg) + seg) % seg - seg / 2, t = Math.abs(m) / (seg / 2);
        return (float) (Math.sqrt(x * x + y * y) - (r + (r * 0.48 - r) * t));
    }

    // ---- the heavy throws ----

    private void heavy(Px f) {
        int x0 = CX - R - 19, y = CY + 8;
        for (int i = 0; i < heavyShown; i++) hammerMark(f, x0 - i * 12, y, heavyOn && i == 0);
        // new ones dropping in one after another, a bounce on landing and a glint
        if (markT >= 0f)
            for (int i = heavyShown; i < heavyLeft; i++) {
                float tk = markT - (i - heavyShown) * MARK_GAP;
                if (tk <= 0f) continue;
                float yy;
                if (tk < MARK_FALL) {
                    float u = tk / MARK_FALL;
                    yy = y - 30f * (1f - u * u);
                } else {
                    float b = tk - MARK_FALL;
                    yy = y - 4f * (float) Math.abs(Math.sin(b * 14f)) * (float) Math.exp(-b * 9f);
                }
                int mx = x0 - i * 12, my = Math.round(yy);
                float since = tk - MARK_FALL;
                if (since > 0f && since < 0.3f) {
                    int a = Math.round(2f + 3f * (1f - since / 0.3f));
                    f.rect(mx - a, my - a, mx + 11 + a, my + 8 + a, 0xFFFFF0B0);
                }
                hammerMark(f, mx, my, heavyOn && i == 0);
            }
    }

    /** A little heavy hammer, 11 by 8, ringed in gold when it is the one in hand. */
    private void hammerMark(Px f, int x, int y, boolean lit) {
        if (lit) {
            int ring = mix(0xFFFFC93A, 0xFFFFF4C0, 0.5f + 0.5f * (float) Math.sin(clock * 4f));
            f.rect(x - 1, y - 1, x + 12, y + 9, ring);
        }
        f.rect(x, y, x + 11, y + 8, 0xFF2E2A28);
        f.rect(x + 1, y + 1, x + 7, y + 5, 0xFF6A676A);
        f.rect(x + 1, y + 1, x + 7, y + 2, 0xFF8E8A8C);
        f.rect(x + 2, y + 1, x + 3, y + 5, 0xFFC08A3C);
        f.rect(x + 5, y + 1, x + 6, y + 5, 0xFFC08A3C);
        f.rect(x + 7, y + 2, x + 10, y + 4, 0xFF8A5A32);
    }

    // ---- the way through the level ----

    private void track(Px f, int x, int y, int w) {
        float k = Math.min(1f, knocked / Game.CLEAR);
        boolean done = knocked >= Game.CLEAR;
        f.rect(x - 1, y - 1, x + w + 1, y + 7, 0xFF5A3A12);
        shade(f, x, y, x + w, y + 6, 0xFF1A120A, 0.55f);
        int fill = Math.round(w * k);
        // reached: a flash runs along it once
        float run = done && reached >= 0f && reached < 0.5f ? reached / 0.5f : -1f;
        for (int i = 0; i < fill; i++) {
            int c = done ? 0xFF6FCF5A : mix(0xFFE8963C, 0xFFFFC93A, i / (float) w);
            if (run >= 0f && Math.abs(i / (float) w - run) < 0.12f) c = mix(c, 0xFFFFFFFF, 0.7f);
            for (int j = 0; j < 6; j++) f.set(x + i, y + j, j == 0 ? mix(c, 0xFFFFFFFF, 0.5f) : j == 5 ? Px.scale(c, 0.75f) : c);
        }
        if (fill > 0 && !done) {
            // a spark at the edge of what is down
            boolean on = ((int) (clock * 12f) & 1) == 0;
            f.set(x + fill - 1, y + 1, on ? 0xFFFFFFFF : 0xFFFFF4C0);
            f.set(x + fill, y + 2, on ? 0xFFFFF4C0 : 0xFFFFC93A);
        }
        for (int q = 1; q < 4; q++) {
            int tx = x + w * q / 4;
            if (tx >= x + fill) f.set(tx, y + 5, 0xFF8A6A3A);
        }
        // the flag at the end: red while to be reached, green and waving once it is
        int fx = x + w + 2;
        f.rect(fx, y - 6, fx + 1, y + 7, 0xFF3A2A1A);
        int pc = done ? 0xFF6FCF5A : 0xFFE0402C;
        for (int r = 0; r < 4; r++) {
            int wave = done ? Math.round((float) Math.sin(clock * 9f - r * 0.9f)) : 0;
            f.rect(fx + 1, y - 6 + r + wave * (r > 0 ? 1 : 0), fx + 6 - r, y - 5 + r + wave * (r > 0 ? 1 : 0), r == 0 ? mix(pc, 0xFFFFFFFF, 0.4f) : pc);
        }
    }

    // ---- the cubes ----

    // a golden cube seen from a corner: o edge, t lit top, l the face to the light, r the shaded one, g a glint
    private static final String[] CUBE = {
            "...ooo...",
            ".oottgoo.",
            "otttttgto",
            "olltttrro",
            "olllorrro",
            "olllorrro",
            "olllorrro",
            ".olloroo.",
            "...ooo..."};

    private void cube(Px f, int x, int y, int i) {
        // now and then a glint runs along the row, cube after cube
        float g = (clock * 1.3f - i * 0.12f) % 3.2f;
        boolean shine = g > 0f && g < 0.18f;
        for (int j = 0; j < CUBE.length; j++)
            for (int k = 0; k < CUBE[j].length(); k++) {
                char c = CUBE[j].charAt(k);
                int col = c == 'o' ? 0xFF7A4A0E : c == 't' ? 0xFFFFE07A : c == 'g' ? 0xFFFFFFFF : c == 'l' ? 0xFFF2B83A : c == 'r' ? 0xFFB8862A : 0;
                if (col == 0) continue;
                if (shine && c != 'o') col = mix(col, 0xFFFFFFFF, 0.6f);
                f.set(x + k, y + j, col);
            }
    }

    // ---- drawing ----

    static int mix(int a, int b, float t) {
        return Px.mix(a, b, Math.max(0f, Math.min(1f, t)));
    }

    /** A color laid over a pixel at so much cover, keeping the flat layer's see-through where it was. */
    private static void blend(Px f, int x, int y, int c, float k) {
        if (x < 0 || y < 0 || x >= f.w || y >= f.h) return;
        int d = f.a[y * f.w + x];
        int da = d >>> 24;
        int a = Math.round(Math.max(0f, Math.min(1f, k)) * 255f);
        if (da == 0) {
            f.a[y * f.w + x] = (a << 24) | (c & 0xFFFFFF);
            return;
        }
        int ra = Math.min(255, a + da * (255 - a) / 255);
        f.a[y * f.w + x] = (ra << 24) | (Px.mix(d, c, a / 255f) & 0xFFFFFF);
    }

    private static void shade(Px f, int x0, int y0, int x1, int y1, int c, float k) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) blend(f, x, y, c, k);
    }

    /** Text in the pixel font, so big, with a dark edge all round and a light top to every stroke. */
    static void bold(Px f, String s, int x, int y, int scale, int ink, int edge) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) if (dx != 0 || dy != 0) PixelFont.draw(f, s, x + dx, y + dy, scale, edge);
        PixelFont.draw(f, s, x + 1, y + 1, scale, edge);
        PixelFont.draw(f, s, x, y, scale, ink);
        int top = mix(ink, 0xFFFFFFFF, 0.55f);
        for (int yy = Math.max(1, y); yy < Math.min(f.h, y + 7 * scale); yy++)
            for (int xx = Math.max(0, x); xx < Math.min(f.w, x + PixelFont.width(s, scale)); xx++)
                if (f.a[yy * f.w + xx] == ink && f.a[(yy - 1) * f.w + xx] == edge) f.a[yy * f.w + xx] = top;
    }
}
