package com.shumtugle.freedie;

/**
 * The top of the frame during a level, laid out like a coat of arms and drawn at the screen's
 * own grain, round and soft-edged, over the street. In the middle a medallion with the level's
 * number, and round it a ring of five petals: the attempt at this level it is, one petal lit
 * gold for each, the first lit as soon as the level opens; the face warms from green toward
 * gold with them, and the ring full, a fifth attempt lost, the golden hammer comes. On the left wing the stars left, nearest first, and
 * under them the heavy hammers, one hammer and how many; on the right wing the way through
 * the level, a bar filling toward a flag as the building comes down, and under it the golden
 * cubes in the rack.
 *
 * It lives with the game. A blow on the building jolts the medallion, harder the harder the
 * blow, with a ring of shock off its rim; a building felled turns the medallion over like a
 * coin, the next number on its other face; with the golden hammer in hand it burns as gold
 * does, orange with a white-hot heart, tongues of fire over its rim. A star spent rolls away
 * and melts; the bar follows what is down a little behind, and the flag waves once it is
 * reached; a heavy hammer given drops in and bounces; a cube gained is swallowed by the rack.
 * It shows only what the player has, never what is missing.
 */
final class Crest {
    // what it shows, set by the level every frame
    int level, stars, fails, cubes, heavyShown, heavyLeft;
    boolean heavyOn, goldOn, won;
    float knocked;                 // share of the building off the plinth
    float markT = -1f;             // the new heavy marks dropping in, seconds; below 0 none
    static final float MARK_FALL = 0.32f, MARK_GAP = 0.24f;
    static final int TRIES = Game.GOLD_AFTER;   // the attempts a ring has room for
    /** How many heavy marks went in a row in the old crest; kept for the most the game gives. */
    static final int ROW = 9;

    // the layer it is drawn on, and the frame's grain on it: so many layer pixels to a frame pixel
    Ink ink;
    static final int K = 4, ROWS = 52;

    private float clock;
    private final float[] burnt = {-1f, -1f, -1f, -1f, -1f};   // seconds since each star burnt
    private int failsShown;
    private float failFlash = -1f, reached = -1f;
    private float hitT = -1f, hitK, flipT = -1f, track, bounceT = -1f, gulpT = -1f;
    private int heavyWas = -1, cubesWas = -1;

    static final int CX = World.W / 2, CY = 22, R = 15;

    void update(float dt) {
        clock += dt;
        for (int i = 0; i < burnt.length; i++) if (burnt[i] >= 0f) burnt[i] += dt;
        if (failFlash >= 0f) failFlash += dt;
        boolean done = knocked >= Game.CLEAR;
        if (done && reached < 0f) reached = 0f;
        else if (!done) reached = -1f;
        if (reached >= 0f) reached += dt;
        if (hitT >= 0f && (hitT += dt) > 0.6f) hitT = -1f;
        if (won && flipT < 0f) flipT = 0f;
        if (flipT >= 0f) flipT += dt;
        if (bounceT >= 0f && (bounceT += dt) > 0.5f) bounceT = -1f;
        if (gulpT >= 0f && (gulpT += dt) > 0.5f) gulpT = -1f;
        // the bar follows what is down, a little behind
        float k = Math.min(1f, knocked / Game.CLEAR);
        track += (k - track) * Math.min(1f, dt * 6f);
        if (heavyWas >= 0 && heavyShown > heavyWas) bounceT = 0f;
        heavyWas = heavyShown;
        if (cubesWas >= 0 && cubes > cubesWas) gulpT = 0f;
        cubesWas = cubes;
    }

    /** The layer was drawn over by something else: the next frame draws it whole again. */
    void dirty() {
        drawnKey = Long.MIN_VALUE;
    }

    /** A star has just been spent: it rolls away. */
    void burn(int i) {
        if (i >= 0 && i < burnt.length) burnt[i] = 0f;
    }

    /** A new level or a new try: the stars whole again, the medallion at rest. */
    void fresh() {
        for (int i = 0; i < burnt.length; i++) burnt[i] = -1f;
        reached = -1f;
        flipT = -1f;
        hitT = -1f;
        track = 0f;
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

    /** A blow on the building, so hard (meters a second): the medallion jolts. */
    void hit(float speed) {
        float k = Math.max(0f, Math.min(1f, (speed - 1f) / 9f));
        if (k <= 0.05f) return;
        // a harder blow takes over a softer one still ringing
        if (hitT >= 0f && hitK * (float) Math.exp(-hitT * 7f) > k) return;
        hitT = 0f;
        hitK = k;
    }

    // what was last drawn, so a frame with nothing new keeps the layer as it is
    private long drawnKey = Long.MIN_VALUE;
    private int beat;

    void draw(Px f) {
        if (ink == null) return;
        Ink main = ink;
        long key = key();
        boolean busy = busy();
        // nothing new: the layer stays as it is; something slow moving (fire, a glint, the bar
        // catching up): drawn every other frame; a jolt or the coin turning: every frame
        boolean quick = hitT >= 0f || flipT >= 0f && flipT < 0.6f;
        if (key == drawnKey && (!busy || !quick && (++beat & 1) == 1)) {
            main.used = true;
            return;
        }
        drawnKey = key;
        main.clear();
        medallion();
        stars();
        heavy();
        track();
        cubes();
        main.used = true;
    }

    /** Everything that, changed, changes the picture without anything moving. */
    private long key() {
        long k = level;
        k = k * 31 + stars;
        k = k * 31 + failsShown;
        k = k * 31 + cubes;
        k = k * 31 + heavyShown;
        k = k * 31 + (heavyOn ? 1 : 0);
        k = k * 31 + (goldOn ? 1 : 0);
        k = k * 31 + (won ? 1 : 0);
        k = k * 31 + Math.round(track * 400f);
        return k;
    }

    /** Whether something is moving: then it is drawn every frame. */
    private boolean busy() {
        if (hitT >= 0f || flipT >= 0f && flipT < 0.6f || bounceT >= 0f || gulpT >= 0f || markT >= 0f || goldOn || won) return true;
        if (failFlash >= 0f && failFlash < 0.85f) return true;
        for (float b : burnt) if (b >= 0f && b < 0.75f) return true;
        if (knocked >= Game.CLEAR && reached < 3f) return true;
        if (Math.abs(track - Math.min(1f, knocked / Game.CLEAR)) > 0.002f) return true;
        if ((clock * 0.5f) % 3f < 0.62f) return true;
        for (int i = 0; i < Math.min(cubes, 7); i++) {
            float gl = (clock * 1.3f - i * 0.12f) % 3.2f;
            if (gl > -0.05f && gl < 0.24f) return true;
        }
        return false;
    }

    // ---- what is drawn once and laid down again: the parts that do not move ----

    private Ink starSprite, starWhite, hammerSprite, cubeSprite, cubeShine, trackBack, medal, count, cubeCount;
    private int medalKey = Integer.MIN_VALUE, countKey = -1, cubeCountKey = -1;

    /** Draws onto a small layer of its own instead of the crest's, then back. */
    private Ink beginSprite(int w, int h) {
        Ink s = new Ink(w, h);
        held = ink;
        ink = s;
        return s;
    }

    private Ink held;

    private void endSprite() {
        ink = held;
    }

    // ---- colours ----

    private static final int INK = 0xFF3A2410, GREEN = 0xFF8FCB6C, GREEN_LIT = 0xFFC6EE9C, GOLD = 0xFFFFC840, GOLD_LIT = 0xFFFFE88C;
    private static final int SHADOW = 0xFF000000;

    static int mix(int a, int b, float t) {
        return Px.mix(a, b, Math.max(0f, Math.min(1f, t)));
    }

    private static float ease(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    // ---- the medallion ----

    private static final int MS = (R + 13) * 2 * K;    // the medallion's own layer, square
    private static final int GS = (R + 18) * 2 * K;    // and the glow of fire round it
    private Ink glow;

    private void medallion() {
        Ink g = ink;
        float mx = CX * K, my = CY * K, r = R * K;
        // the coin turning over when the building is down: the next number on its other face
        float across = 1f, down = 1f;
        int shown = level + 1;
        if (flipT >= 0f) {
            float an = (float) Math.PI * ease(flipT / 0.5f);
            across = Math.max(0.04f, Math.abs((float) Math.cos(an)));
            if (an > Math.PI / 2) shown = level + 2;
        }
        // a blow: squashed one way and then the other, dying away
        if (hitT >= 0f) {
            float jolt = hitK * 0.2f * (float) Math.exp(-hitT * 9f) * (float) Math.cos(hitT * 30f);
            across *= 1f + jolt;
            down *= 1f - jolt;
        }
        int mk = shown * 64 + failsShown * 2 + (goldOn ? 1 : 0);
        if (medal == null || mk != medalKey) {
            medalKey = mk;
            medal = beginSprite(MS, MS);
            medalFace(MS * 0.5f, MS * 0.5f, shown);
            endSprite();
        }
        g.squash(mx, my, across, down);
        // gold burning: a glow of fire round it
        if (goldOn) {
            if (glow == null) {
                glow = beginSprite(GS, GS);
                ink.disc(GS * 0.5f, GS * 0.5f, r + 6 * K, 0xFFFF8A20, 9f * K, 1f);
                endSprite();
            }
            g.blit(glow, mx - GS * 0.5f, my - GS * 0.5f, 0.4f + 0.12f * (float) Math.sin(clock * 7f));
        }
        g.blit(medal, mx - MS * 0.5f, my - MS * 0.5f, 1f);
        // the newest attempt flashing white as it lights
        if (!goldOn && failsShown > 0 && failsShown < TRIES && failFlash >= 0f && failFlash < 0.8f) {
            int s = failsShown;
            g.ring(mx, my, r + 1.2f * K, r + 4f * K, s / (float) TRIES + 0.018f, (s + 1) / (float) TRIES - 0.018f, 0xFFFFFFFF,
                    1f - failFlash / 0.8f);
        }
        if (goldOn) {
            // a white heart that breathes, and tongues of fire licking up its rim
            float breathe = 0.6f + 0.4f * (float) Math.sin(clock * 5f);
            g.disc(mx, my, r * 0.45f, 0xFFFFFFF0, r * 0.5f, 0.45f * breathe);
            for (int k = 0; k < 12; k++) {
                float an = (float) (k / 12.0 * Math.PI * 2 + clock * 0.4);
                float life = (clock * 1.6f + k * 0.29f) % 1f;
                // a tongue: from the rim out and up, thinning and reddening as it goes
                float r0 = r + 1.5f * K, r1 = r0 + (3f + 4f * (1f - life)) * K;
                float ux = (float) Math.sin(an), uy = -(float) Math.cos(an);
                float lift = life * 3f * K;
                float x0 = mx + ux * r0, y0 = my + uy * r0, x1 = mx + ux * r1, y1 = my + uy * r1 - lift - 2f * K;
                g.line(x0, y0, x1, y1, (1f - life) * 2.6f * K, mix(0xFFFFE6A0, 0xFFFF5A14, life), 0.8f * (1f - life * life));
            }
        } else {
            // now and then a sweep of light crosses the face
            float sw = (clock * 0.5f) % 3f;
            if (sw < 0.6f) {
                float u = sw / 0.6f;
                float x = mx - r + 2f * r * u;
                g.line(x - 0.35f * r, my + r * 0.75f, x + 0.35f * r, my - r * 0.75f, 0.25f * r, 0xFFFFFFFF, 0.2f * (float) Math.sin(u * Math.PI));
            }
        }
        g.unsquash();
        // the shock off the rim of a hard blow
        if (hitT >= 0f && hitK > 0.35f) {
            float u = hitT / 0.4f;
            if (u < 1f) {
                float rr = r + 4f * K + u * 9f * K;
                g.ring(mx, my, rr - 0.6f * K, rr + 0.6f * K, 0f, 1f, 0xFFFFFFFF, (1f - u) * 0.7f * hitK);
            }
        }
    }

    /** The medallion as it stands: its shadow, the ring of attempts, the face lit from the upper left, the rim, the number. */
    private void medalFace(float mx, float my, int shown) {
        Ink g = ink;
        float r = R * K;
        g.disc(mx + 3, my + 7, r + 4 * K, SHADOW, 9f, 0.28f);
        for (int s = 0; s < TRIES; s++) {
            float t0 = s / (float) TRIES + 0.018f, t1 = (s + 1) / (float) TRIES - 0.018f;
            // the petals: one for each attempt, this one too
            boolean on = s <= failsShown;
            int c = goldOn ? 0xFFFFB23A : on ? GOLD : INK;
            g.ring(mx, my, r + 1.2f * K, r + 4f * K, t0, t1, c, goldOn || on ? 1f : 0.62f);
        }
        // the face warms from green toward gold, attempt by attempt
        float warm = Math.min(1f, failsShown / (float) (TRIES - 1)) * 0.85f;
        int face = goldOn ? 0xFFF08A1E : mix(GREEN, 0xFFE8B83C, warm), faceLit = goldOn ? 0xFFFFE6A0 : mix(GREEN_LIT, 0xFFFFE6A0, warm);
        g.disc(mx, my, r, face, 1f, 1f);
        g.disc(mx - r * 0.18f, my - r * 0.22f, r * 0.72f, faceLit, r * 0.55f, 0.7f);
        g.ring(mx, my, r - 1.2f * K, r + 0.4f * K, 0f, 1f, INK, 1f);
        String lv = String.valueOf(shown);
        float th = 15f * K;
        float tw = Ink.digitsWidth(lv, th);
        g.digits(lv, mx - tw * 0.5f, my - th * 0.5f, th, goldOn ? 0xFFFFF6D0 : 0xFFFFFFFF, INK, 1.1f * K, 1f);
    }

    // ---- the stars ----

    private static final int SS = 22 * K;     // a star's own layer

    private void stars() {
        Ink g = ink;
        float ro = 7.2f * K, ri = 3.2f * K;
        if (starSprite == null) {
            starSprite = beginSprite(SS, SS);
            star(SS * 0.5f, SS * 0.5f, ro, ri, 0f, 1f, 0f);
            endSprite();
            starWhite = beginSprite(SS, SS);
            star(SS * 0.5f, SS * 0.5f, ro, ri, 0f, 1f, 1f);
            endSprite();
        }
        boolean flash = won && ((int) (clock * 6) & 1) == 0;
        for (int i = 0; i < Game.STARS; i++) {
            float cx = (CX - R - 14 - i * 16) * K, cy = (CY - 2) * K;
            if (i < stars) g.blit(flash ? starWhite : starSprite, cx - SS * 0.5f, cy - SS * 0.5f, 1f);
            else if (burnt[i] >= 0f && burnt[i] < 0.7f) {
                // spent: it rolls away down to the left, turning, and melts
                float u = burnt[i] / 0.7f;
                float x = cx - u * 6f * K, y = cy + u * u * 12f * K;
                star(x, y, ro * (1f - 0.4f * u), ri * (1f - 0.4f * u), -u * 2.5f, 1f - u, 0f);
            }
        }
    }

    private void star(float cx, float cy, float ro, float ri, float turn, float cover, float white) {
        Ink g = ink;
        g.starShape(cx + 2, cy + 6, ro, ri, turn);
        g.polyFill(SHADOW, SHADOW, 7f, 0.28f * cover);
        g.starShape(cx, cy, ro, ri, turn);
        g.polyFill(mix(GOLD_LIT, 0xFFFFFFFF, white), mix(0xFFF0A030, 0xFFFFFFFF, white), 1f, cover);
        // a lighter heart toward the light
        g.starShape(cx - ro * 0.08f, cy - ro * 0.12f, ro * 0.62f, ri * 0.62f, turn);
        g.polyFill(mix(0xFFFFF0B0, 0xFFFFFFFF, white), mix(GOLD, 0xFFFFFFFF, white), ro * 0.25f, 0.6f * cover);
        // its edge
        edge(cx, cy, ro, ri, turn, cover);
        g.disc(cx - ro * 0.3f, cy - ro * 0.45f, ro * 0.13f, 0xFFFFFFF4, 1f, 0.9f * cover);
    }

    private void edge(float cx, float cy, float ro, float ri, float turn, float cover) {
        float px = 0f, py = 0f;
        for (int k = 0; k <= 10; k++) {
            float r = (k & 1) == 0 ? ro : ri;
            double an = -Math.PI / 2 + turn + k * Math.PI / 5;
            float x = cx + r * (float) Math.cos(an), y = cy + r * (float) Math.sin(an);
            if (k > 0) ink.line(px, py, x, y, 0.85f * K, 0xFF8A5A12, cover);
            px = x;
            py = y;
        }
    }

    // ---- the heavy hammers ----

    private static final int HS = 20 * K;     // a hammer's own layer

    private void heavy() {
        int n = heavyShown;
        boolean falling = markT >= 0f && heavyLeft > heavyShown;
        if (n <= 0 && !falling) return;
        Ink g = ink;
        float hx = (CX - R - 40) * K, hy = (CY + 12) * K;
        if (hammerSprite == null) {
            hammerSprite = beginSprite(HS, HS);
            hammer(HS * 0.5f, HS * 0.5f, 1f);
            endSprite();
        }
        // a bounce as one lands
        float b = bounceT >= 0f ? -3f * K * (float) Math.abs(Math.sin(bounceT * 12f)) * (float) Math.exp(-bounceT * 8f) : 0f;
        if (n > 0) {
            g.blit(hammerSprite, hx - HS * 0.5f, hy - HS * 0.4f + b, 1f);
            // the count, golden while the heavy one is in hand
            int ck = n * 2 + (heavyOn ? 1 : 0);
            if (count == null || countKey != ck) {
                countKey = ck;
                count = number("x" + n, heavyOn ? GOLD_LIT : 0xFFFFFFFF);
            }
            g.blit(count, hx + 9 * K - 2 * K, hy - 4.5f * K - 2 * K, 1f);
        }
        // new ones dropping in from above, one after another
        if (falling)
            for (int i = 0; i < heavyLeft - heavyShown; i++) {
                float tk = markT - i * MARK_GAP;
                if (tk <= 0f || tk >= MARK_FALL) continue;
                float u = tk / MARK_FALL;
                g.blit(hammerSprite, hx - HS * 0.5f, hy - HS * 0.4f - 30f * K * (1f - u * u), 0.9f);
            }
    }

    /** A count in small digits on a layer of its own, two frame pixels of room round it. */
    private Ink number(String s, int color) {
        float th = 9f * K;
        Ink sp = beginSprite(Math.round(Ink.digitsWidth(s, th) + 4 * K), Math.round(th + 4 * K));
        ink.digits(s, 2 * K, 2 * K, th, color, INK, 0.8f * K, 1f);
        endSprite();
        return sp;
    }

    /** A little heavy hammer: the hammer's own model as a sign, its shadow under it. */
    private void hammer(float hx, float hy, float cover) {
        Hammer.icon(ink, hx + 1.2f * K, hy + 1.8f * K, 17f * K, Hammer.HEAVY_KIND, 0x000000, 0.3f * cover);
        Hammer.icon(ink, hx, hy, 17f * K, Hammer.HEAVY_KIND, -1, cover);
    }

    // ---- the way through the level ----

    private void track() {
        Ink g = ink;
        float x0 = (CX + R + 8) * K, y0 = (CY - 8) * K, w = 70 * K, h = 6 * K;
        boolean done = knocked >= Game.CLEAR;
        if (trackBack == null) {
            trackBack = beginSprite(Math.round(w + 6 * K), Math.round(h + 6 * K));
            ink.box(3 * K + 3, 3 * K + 7, 3 * K + w + 3, 3 * K + h + 7, h * 0.5f, SHADOW, SHADOW, 7f, 0.25f);
            ink.box(3 * K, 3 * K, 3 * K + w, 3 * K + h, h * 0.5f, 0xFF2A1A0C, 0xFF3A2614, 1f, 0.85f);
            endSprite();
        }
        g.blit(trackBack, x0 - 3 * K, y0 - 3 * K, 1f);
        float fill = Math.max(0f, Math.min(1f, track)) * (w - 1.6f * K);
        if (fill > h * 0.4f) {
            int top = done ? 0xFF9EE878 : 0xFFFFD27A, bottom = done ? 0xFF4FAE3E : 0xFFE8902A;
            g.box(x0 + 0.8f * K, y0 + 0.8f * K, x0 + 0.8f * K + fill, y0 + h - 0.8f * K, (h - 1.6f * K) * 0.5f, top, bottom, 1f, 1f);
            g.box(x0 + 1.6f * K, y0 + 1.2f * K, x0 + 0.8f * K + fill - K, y0 + 2.4f * K, K * 0.6f, 0xFFFFFFFF, 0xFFFFFFFF, 1f, 0.45f);
            // reached: a flash runs along it once
            float run = done && reached >= 0f && reached < 0.5f ? reached / 0.5f : -1f;
            if (run >= 0f) {
                float x = x0 + run * w;
                g.box(x - 4 * K, y0, x + 4 * K, y0 + h, h * 0.5f, 0xFFFFFFFF, 0xFFFFFFFF, 3f * K, 0.7f);
            }
        }
        // the flag at the end: red while to be reached, green and waving once it is
        float fx = x0 + w + 1.5f * K;
        g.line(fx, y0 - 6.5f * K, fx, y0 + h + 0.5f * K, 0.9f * K, INK, 1f);
        int pc = done ? 0xFF6FCF5A : 0xFFE0402C;
        float wave = done ? (float) Math.sin(clock * 9f) * 0.9f * K : 0f;
        g.shape(fx, y0 - 6.5f * K, fx + 6.5f * K, y0 - 4.6f * K + wave, fx, y0 - 2.4f * K);
        g.polyFill(mix(pc, 0xFFFFFFFF, 0.25f), pc, 1f, 1f);
    }

    // ---- the cubes ----

    private static final int CS = 14 * K;     // a cube's own layer

    private void cubes() {
        int n = cubes;
        if (n <= 0) return;
        Ink g = ink;
        if (cubeSprite == null) {
            cubeSprite = beginSprite(CS, CS);
            cube(CS * 0.5f, CS * 0.45f, 4.2f * K, false);
            endSprite();
            cubeShine = beginSprite(CS, CS);
            cube(CS * 0.5f, CS * 0.45f, 4.2f * K, true);
            endSprite();
        }
        float x0 = (CX + R + 12) * K, y0 = (CY + 9) * K;
        int shownCubes = n <= 7 ? n : 1;
        for (int i = 0; i < shownCubes; i++) {
            float gl = (clock * 1.3f - i * 0.12f) % 3.2f;
            float cx = x0 + i * 11 * K;
            if (i == shownCubes - 1 && gulpT >= 0f) {
                // swallowed by the rack: it swells and settles
                float sc = 1f + 0.35f * (float) Math.sin(Math.min(1f, gulpT / 0.35f) * Math.PI);
                g.squash(cx, y0, sc, sc);
            }
            g.blit(gl > 0f && gl < 0.18f ? cubeShine : cubeSprite, cx - CS * 0.5f, y0 - CS * 0.45f, 1f);
            g.unsquash();
        }
        if (n > 7) {
            if (cubeCount == null || cubeCountKey != n) {
                cubeCountKey = n;
                cubeCount = number("x" + n, 0xFFFFFFFF);
            }
            g.blit(cubeCount, x0 + 6 * K - 2 * K, y0 - 4.5f * K - 2 * K, 1f);
        }
    }

    /** A golden cube seen from a corner, from its middle, so far to each corner. */
    private void cube(float cx, float cy, float s, boolean shine) {
        Ink g = ink;
        g.shape(cx - s + 3, cy - s * 0.5f + 7, cx + 3, cy - s + 7, cx + s + 3, cy - s * 0.5f + 7, cx + s + 3, cy + s * 0.5f + 7, cx + 3, cy + s + 7,
                cx - s + 3, cy + s * 0.5f + 7);
        g.polyFill(SHADOW, SHADOW, 6f, 0.28f);
        int w = shine ? 0xFFFFFFFF : 0;
        g.shape(cx - s, cy - s * 0.5f, cx, cy, cx, cy + s, cx - s, cy + s * 0.5f);
        g.polyFill(mix(0xFFF8A23A, w, shine ? 0.5f : 0f), 0xFFE08420, 1f, 1f);
        g.shape(cx, cy, cx + s, cy - s * 0.5f, cx + s, cy + s * 0.5f, cx, cy + s);
        g.polyFill(mix(0xFFC8701E, w, shine ? 0.5f : 0f), 0xFFA4560E, 1f, 1f);
        g.shape(cx, cy - s, cx + s, cy - s * 0.5f, cx, cy, cx - s, cy - s * 0.5f);
        g.polyFill(mix(0xFFFFE08A, w, shine ? 0.6f : 0f), 0xFFFFC85A, 1f, 1f);
        g.disc(cx - s * 0.1f, cy - s * 0.55f, s * 0.22f, 0xFFFFF8D8, s * 0.2f, 0.8f);
    }
}
