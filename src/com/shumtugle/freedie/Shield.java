package com.shumtugle.freedie;

import java.util.ArrayList;

/**
 * A tall wooden shield standing on the road before the plinth: on the 10th level, and after
 * it on about one level in three, never two running, which ones not to be guessed. Five
 * upright planks held by two battens, nailed with iron, leaning back a little onto the
 * plinth's face. It covers the face and the lowest meter of the building, to one side or the
 * other, so a throw must go over it or round it, or through it: it is fixed where it stands,
 * the hammer bounces off it, and it gives at the third blow, or at once to the heavy hammer
 * or the golden one. Then its planks come loose and fall as bodies of their own.
 *
 * A building that is more than half sandbags never has one: it is stubborn enough as it is.
 */
final class Shield {
    static final int FROM = 9;                  // level, counted from 0
    static final int BLOWS = 3;                 // plain blows it takes
    static final float CHANCE = 0.55f;          // how often a level after one without draws one
    // half sizes of the whole board: across, up, through
    static final float HX = 0.475f, HY = 1.05f, HZ = 0.045f;
    static final float LEAN = 0.09f;            // back onto the plinth, radians
    static final int PLANKS = 5;
    static final int WOOD = 0xB9824E, WOOD_DARK = 0x8E5F36, BATTEN = 0x7A5232, IRON = 0x4A4542, RAW = 0xF0D3A0;

    /** The board while it stands: fixed, as solid as the plinth. */
    final Body board;
    private int blows;
    boolean broken;
    private float shake;
    private final ArrayList<Body> loose = new ArrayList<Body>();
    // which planks are split, and where along them
    private final boolean[] split = new boolean[PLANKS];
    private final float[] splitAt = new float[PLANKS];
    private final float[] turn = new float[9];

    private Shield(float x, float z) {
        board = new Body(HX, HY, HZ, Body.SHIELD, WOOD);
        // leaning back about the across axis: the top toward the plinth
        float h = LEAN * 0.5f;
        board.setPose(x, HY * (float) Math.cos(LEAN), z + HY * (float) Math.sin(LEAN), (float) Math.cos(h), (float) Math.sin(h), 0f, 0f);
    }

    /**
     * A shield for a level and its building, or none: in town only, from the 10th, never
     * before a building mostly of sandbags. Which side it stands to goes by the seed; on the
     * level whose plinth corner breaks, always the right, away from the corner.
     */
    static Shield forLevel(int level, ArrayList<Body> made, long seed) {
        if (level < FROM || level >= Game.SEA_FROM) return null;
        int bags = 0;
        for (Body b : made) if (b.material == Body.SANDBAG) bags++;
        if (bags * 2 > made.size()) return null;
        if (!rolled(level)) return null;
        java.util.Random r = new java.util.Random(seed ^ 0x5A1E1DL);
        r.nextFloat();
        float off = 0.3f + 0.4f * r.nextFloat();
        float side = level == Game.CHIP_LEVEL || r.nextBoolean() ? 1f : -1f;
        float z = Builder.CZ - Builder.PHZ - 0.18f - HY * (float) Math.sin(LEAN);
        return new Shield(Builder.CX + side * off, z);
    }

    /**
     * Whether a level draws a shield: the 10th always, where it is first met; after that now
     * and then, unlooked for, but never two levels running, and the same each time a level
     * is played again.
     */
    static boolean rolled(int level) {
        if (level < FROM) return false;
        if (level == FROM) return true;
        if (rolled(level - 1)) return false;
        return new java.util.Random(Game.seedOf(level) ^ 0x5A1E1DL).nextFloat() < CHANCE;
    }

    /** All its bodies, for the physics: the board while it stands, the planks once loose. */
    void addTo(Physics p) {
        if (!broken) p.add(board);
    }

    /**
     * The hammer struck it. Returns the planks that came loose, to be added to the physics,
     * or null if it holds.
     */
    ArrayList<Body> struck(World.Host host, java.util.Random fx, float speed, boolean heavy, boolean golden,
                           float hvx, float hvy, float hvz) {
        if (broken || speed < 2f) return null;
        blows += heavy || golden ? BLOWS : 1;
        shake = 1f;
        if (blows < BLOWS) {
            // a plank splits where it was struck, and the board shudders
            int p = fx.nextInt(PLANKS);
            for (int k = 0; k < PLANKS && split[p]; k++) p = (p + 1) % PLANKS;
            split[p] = true;
            splitAt[p] = 0.2f + 0.5f * fx.nextFloat();
            host.sound(Synth.WOOD, 1f, 0.55f + fx.nextFloat() * 0.1f);
            host.sound(Synth.TOCK, 0.6f, 0.6f);
            return null;
        }
        broken = true;
        board.ghost = true;
        host.sound(Synth.CRASH, 0.8f, 0.8f);
        host.sound(Synth.WOOD, 1f, 0.45f);
        // the planks, each a body of its own, pushed on by the blow and falling apart
        float w = HX / PLANKS;
        float sl = (float) Math.sqrt(hvx * hvx + hvy * hvy + hvz * hvz) + 1e-3f;
        float push = Math.min(4f, 1.2f + speed * 0.25f);
        for (int i = 0; i < PLANKS; i++) {
            float lx = -HX + w * (2 * i + 1);
            Body b = new Body(w - 0.006f, HY, HZ * 0.7f, Body.WOOD, i % 2 == 0 ? WOOD : WOOD_DARK);
            float wx = board.px + board.r00 * lx, wy = board.py + board.r10 * lx, wz = board.pz + board.r20 * lx;
            b.setPose(wx, wy, wz, board.qw, board.qx, board.qy, board.qz);
            b.vx = hvx / sl * push + (fx.nextFloat() - 0.5f) * 1.2f;
            b.vy = 0.6f + fx.nextFloat() * 1.2f;
            b.vz = hvz / sl * push * 0.5f + fx.nextFloat() * 0.4f;
            b.wx = (fx.nextFloat() - 0.5f) * 4f;
            b.wy = (fx.nextFloat() - 0.5f) * 4f;
            b.wz = (fx.nextFloat() - 0.5f) * 4f;
            b.asleep = false;
            loose.add(b);
        }
        // the two battens
        for (int k = -1; k <= 1; k += 2) {
            float ly = k * HY * 0.62f;
            Body b = new Body(HX * 0.96f, 0.05f, 0.03f, Body.WOOD, BATTEN);
            float f = -(HZ + 0.03f);
            b.setPose(board.px + board.r01 * ly + board.r02 * f, board.py + board.r11 * ly + board.r12 * f,
                    board.pz + board.r21 * ly + board.r22 * f, board.qw, board.qx, board.qy, board.qz);
            b.vx = (fx.nextFloat() - 0.5f) * 1.5f;
            b.vy = 0.4f + fx.nextFloat();
            b.wz = (fx.nextFloat() - 0.5f) * 6f;
            b.asleep = false;
            loose.add(b);
        }
        return loose;
    }

    void update(float dt) {
        shake = Math.max(0f, shake - dt * 3.5f);
    }

    /** The board as it stands, shuddering for a moment after a blow; or its planks, loose. */
    void draw(DrawList d) {
        if (broken) {
            for (int i = 0; i < loose.size(); i++) {
                Body b = loose.get(i);
                rows(b);
                d.box(b.px, b.py, b.pz, b.hx, b.hy, b.hz, turn, b.tint, DrawList.WOOD);
            }
            return;
        }
        rows(board);
        // a shudder: the top sways along the board's own through axis
        float sway = shake * shake * 0.05f * (float) Math.sin(shake * 40f);
        float w = HX / PLANKS;
        for (int i = 0; i < PLANKS; i++) {
            float lx = -HX + w * (2 * i + 1);
            int col = i % 2 == 0 ? WOOD : WOOD_DARK;
            if (!split[i]) {
                part(d, lx, 0f, 0f, w - 0.006f, HY, HZ, col, DrawList.WOOD, sway);
            } else {
                // split across: the raw pale wood showing in the break, the piece above
                // knocked askew and out toward the eye
                float at = -HY + 2f * HY * splitAt[i], gap = 0.035f;
                float lo = (at - gap + HY) * 0.5f, hi = (HY - at - gap) * 0.5f;
                part(d, lx, -HY + lo, 0f, w - 0.006f, lo, HZ, col, DrawList.WOOD, sway);
                part(d, lx, at, 0.01f, w - 0.02f, gap, HZ * 0.6f, RAW, DrawList.GRAIN, sway);
                part(d, lx + (i % 2 == 0 ? 0.025f : -0.025f), at + gap + hi, -0.03f, w - 0.012f, hi, HZ, col, DrawList.WOOD, sway);
            }
        }
        // battens across the front, an iron nail head where each crosses a plank
        for (int k = -1; k <= 1; k += 2) {
            float ly = k * HY * 0.62f;
            part(d, 0f, ly, -(HZ + 0.025f), HX * 0.98f, 0.055f, 0.025f, BATTEN, DrawList.WOOD, sway);
            for (int i = 0; i < PLANKS; i++)
                part(d, -HX + w * (2 * i + 1), ly, -(HZ + 0.052f), 0.018f, 0.018f, 0.004f, IRON, DrawList.PLAIN, sway);
        }
        // an iron band along the top
        part(d, 0f, HY - 0.03f, 0f, HX + 0.008f, 0.03f, HZ + 0.008f, IRON, DrawList.PLAIN, sway);
    }

    private void rows(Body b) {
        turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
        turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
        turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
    }

    /** A box in the board's own frame, swaying with it by the share of its height. */
    private void part(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, int col, int mat, float sway) {
        lz += sway * (ly + HY) / (2f * HY);
        float x = board.px + turn[0] * lx + turn[1] * ly + turn[2] * lz;
        float y = board.py + turn[3] * lx + turn[4] * ly + turn[5] * lz;
        float z = board.pz + turn[6] * lx + turn[7] * ly + turn[8] * lz;
        d.box(x, y, z, hx, hy, hz, turn, col, mat);
    }
}
