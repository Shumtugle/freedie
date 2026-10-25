package com.shumtugle.freedie;

import java.util.Random;

/**
 * Freedie herself: a stooping giant heaped together from what buildings are made of,
 * stone shoulders and fists, brick forearms, shins and jaw, planks for sinews, rusty rods
 * out of her back, eyes like coals; dressed as a maid in blocks, a black dress in tiers
 * with lace at the hem, a white apron and bib with buttons, a sash, a black bow. Her knees
 * glow, as her eyes do. She holds a golden hammer.
 *
 * She is a set of boxes hung on a dozen joints, posed by angles; she faces -z, turned about
 * the upright by the root's own angle. Her model is in units of her own, drawn size times
 * bigger. When she breaks, every box becomes a body of the game's own physics and falls
 * where it will.
 */
final class Frida {
    // the joints, each turning about its own pivot, after its parent
    static final int ROOT = 0, CHEST = 1, HEAD = 2, ARM_A = 3, FORE_A = 4, ARM_B = 5, FORE_B = 6,
            LEG_A = 7, SHIN_A = 8, LEG_B = 9, SHIN_B = 10, HAMMER = 11, JOINTS = 12;
    private static final int[] PARENT = {-1, ROOT, CHEST, CHEST, ARM_A, CHEST, ARM_B, ROOT, LEG_A, ROOT, LEG_B, FORE_A};
    private static final float[] PIVOT = {
            0f, 2.0f, 0f,       // root, at the hips
            0f, 2.1f, 0f,       // chest
            0f, 3.4f, 0f,       // head, at the neck
            1.22f, 3.2f, 0f,    // the hammer arm, at the shoulder
            1.3f, 2.2f, 0f,     // its elbow
            -1.22f, 3.2f, 0f,   // the other arm
            -1.3f, 2.2f, 0f,
            0.55f, 2.0f, 0f,    // a hip
            0.55f, 1.15f, 0f,   // a knee
            -0.55f, 2.0f, 0f,
            -0.55f, 1.15f, 0f,
            1.34f, 1.08f, -0.04f // the hammer, in the fist
    };

    // what she is made of, as Body materials, for when she breaks
    private static final int ST = Body.STONE, WD = Body.WOOD, AU = Body.GOLD;
    static final int DRESS = 0x1E1C22, WHITE = 0xF2F0EA, LACE = 0xEDEBE4, BRICK = 0xB5553A, PALE = 0xC9C1B3;
    static final int GOLD = 0xE2AE34, GOLD_DARK = 0xA87A1E;

    // the parts: joint, middle (model space, at rest), half sizes, color, how drawn, what it weighs like
    private int n;
    private final int[] joint = new int[64], color = new int[64], body = new int[64];
    private final float[] mid = new float[64 * 3], half = new float[64 * 3], look = new float[64];
    private int eyeFrom, eyeTo, kneeFrom, kneeTo;

    // the pose per joint: angles about x (forward and back), z (out to the side), y (across)
    final float[] ax = new float[JOINTS], az = new float[JOINTS], ay = new float[JOINTS];
    float x, z;                              // where she stands; she faces -z
    float lift;                              // how far her hips are raised (or, below 0, sunk), her units
    float size = 1f;                         // meters to her unit
    float eyes = 0.4f;                       // how bright her eyes burn, 0 to 1
    // in the fight: a flash when hit; knees put out, one by one; blows the head has left in it;
    // and whether she burns in the golden fire the red world shows her in
    float hurt;
    boolean kneeOutA, kneeOutB, fire;
    int head = 3;

    // each joint's place in the world: a turn (row by row) and an offset
    private final float[] jr = new float[JOINTS * 9], jt = new float[JOINTS * 3];

    Frida() {
        float dm = DrawList.PLAIN, rail = DrawList.RAIL, stone = DrawList.STONE, grain = DrawList.GRAIN,
                wood = DrawList.WOOD, glow = DrawList.GLOW, gold = DrawList.GOLD;
        // the skirt in two tiers, lace at the hem, the apron over it, the sash
        add(ROOT, 0f, 1.88f, 0f, 1.05f, 0.26f, 0.72f, DRESS, dm, WD);
        add(ROOT, 0f, 1.66f, 0f, 1.1f, 0.05f, 0.77f, LACE, rail, WD);
        add(ROOT, 0f, 1.5f, 0f, 1.22f, 0.2f, 0.84f, DRESS, dm, WD);
        add(ROOT, 0f, 1.25f, 0f, 1.3f, 0.07f, 0.9f, LACE, rail, WD);
        add(ROOT, 0f, 1.65f, -0.88f, 0.72f, 0.4f, 0.03f, WHITE, dm, WD);
        add(ROOT, 0f, 1.2f, -0.92f, 0.75f, 0.06f, 0.02f, LACE, rail, WD);
        add(ROOT, 0f, 2.12f, 0f, 0.92f, 0.1f, 0.66f, WHITE, dm, WD);
        // the bodice, the bib and its buttons, the bow at the collar
        add(CHEST, 0f, 2.72f, 0f, 0.86f, 0.52f, 0.6f, DRESS, dm, WD);
        add(CHEST, 0f, 2.72f, -0.62f, 0.4f, 0.46f, 0.025f, WHITE, dm, WD);
        for (int b = 0; b < 3; b++) add(CHEST, 0f, 2.98f - b * 0.23f, -0.655f, 0.05f, 0.05f, 0.012f, 0x151418, dm, ST);
        for (int s = -1; s <= 1; s += 2) add(CHEST, s * 0.17f, 3.3f, -0.6f, 0.15f, 0.09f, 0.05f, 0x151418, dm, WD);
        add(CHEST, 0f, 3.3f, -0.64f, 0.05f, 0.06f, 0.04f, 0x151418, dm, WD);
        // stone shoulders, lace on the straps, rusty rods out of the back
        for (int s = -1; s <= 1; s += 2) {
            add(CHEST, s * 1.2f, 3.22f, 0f, 0.52f, 0.46f, 0.55f, PALE, stone, ST);
            add(CHEST, s * 0.62f, 3.32f, -0.6f, 0.16f, 0.1f, 0.03f, LACE, rail, WD);
        }
        add(CHEST, -0.35f, 3.75f, 0.5f, 0.035f, 0.45f, 0.035f, 0x7B4A2C, dm, ST);
        add(CHEST, 0.1f, 3.9f, 0.55f, 0.035f, 0.55f, 0.035f, 0x7B4A2C, dm, ST);
        add(CHEST, 0.45f, 3.7f, 0.48f, 0.035f, 0.4f, 0.035f, 0x7B4A2C, dm, ST);
        // the head: a stone block, a brick cap and jaw, a brow, the eyes
        add(HEAD, 0f, 3.85f, -0.05f, 0.4f, 0.4f, 0.4f, 0xBDB5A6, stone, ST);
        add(HEAD, 0f, 4.3f, -0.05f, 0.42f, 0.07f, 0.42f, BRICK, grain, ST);
        add(HEAD, 0f, 3.55f, -0.32f, 0.34f, 0.15f, 0.18f, BRICK, grain, ST);
        add(HEAD, 0f, 4.02f, -0.46f, 0.36f, 0.04f, 0.02f, 0x6E6558, dm, ST);
        eyeFrom = n;
        for (int s = -1; s <= 1; s += 2) add(HEAD, s * 0.17f, 3.92f, -0.455f, 0.08f, 0.045f, 0.015f, 0xFFC24A, glow, ST);
        eyeTo = n;
        // arms: planks for the upper arm, a brick forearm, a stone fist
        for (int s = -1; s <= 1; s += 2) {
            int arm = s > 0 ? ARM_A : ARM_B, fore = s > 0 ? FORE_A : FORE_B;
            add(arm, s * 1.12f, 2.68f, -0.13f, 0.07f, 0.5f, 0.07f, 0x8A6A44, wood, WD);
            add(arm, s * 1.36f, 2.68f, 0.12f, 0.07f, 0.5f, 0.07f, 0x7E6040, wood, WD);
            add(arm, s * 1.2f, 2.68f, 0.18f, 0.06f, 0.48f, 0.06f, 0x8A6A44, wood, WD);
            add(fore, s * 1.32f, 1.8f, 0f, 0.32f, 0.45f, 0.32f, BRICK, grain, ST);
            add(fore, s * 1.34f, 1.08f, -0.04f, 0.36f, 0.28f, 0.36f, PALE, stone, ST);
        }
        // legs: brick shins, stone feet; the thighs mostly under the skirt
        kneeFrom = -1;
        for (int s = -1; s <= 1; s += 2) {
            int leg = s > 0 ? LEG_A : LEG_B, shin = s > 0 ? SHIN_A : SHIN_B;
            add(leg, s * 0.55f, 1.62f, 0f, 0.3f, 0.38f, 0.3f, BRICK, grain, ST);
            add(shin, s * 0.56f, 0.78f, 0f, 0.31f, 0.4f, 0.31f, BRICK, grain, ST);
            if (kneeFrom < 0) kneeFrom = n;
            add(shin, s * 0.56f, 1.16f, -0.32f, 0.11f, 0.08f, 0.015f, 0xFF8A3A, glow, ST);
            add(shin, s * 0.6f, 0.19f, -0.12f, 0.37f, 0.19f, 0.47f, 0x9C9486, stone, ST);
        }
        kneeTo = n;
        // the golden hammer: along the hand's -y at rest, its head long across the swing
        float hx = 1.34f, hy = 1.08f, hz = -0.04f;
        add(HAMMER, hx, hy - 0.85f, hz, 0.08f, 0.85f, 0.08f, GOLD_DARK, gold, AU);
        add(HAMMER, hx, hy - 1.95f, hz, 0.38f, 0.38f, 0.62f, GOLD, gold, AU);
        for (int e = -1; e <= 1; e += 2)
            add(HAMMER, hx, hy - 1.95f, hz + e * 0.42f, 0.41f, 0.41f, 0.06f, GOLD_DARK, gold, AU);
    }

    private void add(int j, float cx, float cy, float cz, float hx, float hy, float hz, int rgb, float drawn, int mat) {
        joint[n] = j;
        mid[n * 3] = cx;
        mid[n * 3 + 1] = cy;
        mid[n * 3 + 2] = cz;
        half[n * 3] = hx;
        half[n * 3 + 1] = hy;
        half[n * 3 + 2] = hz;
        color[n] = rgb;
        look[n] = drawn;
        body[n] = mat;
        n++;
    }

    /** At rest: arms hanging a little out, head bowed, the hammer held forward and down. */
    void rest(float time) {
        java.util.Arrays.fill(ax, 0f);
        java.util.Arrays.fill(az, 0f);
        java.util.Arrays.fill(ay, 0f);
        ax[CHEST] = 0.04f * (float) Math.sin(time * 2f);
        ax[HEAD] = -0.3f;
        az[ARM_A] = 0.12f;
        az[ARM_B] = -0.12f;
        ax[ARM_B] = 0.1f;
        ax[HAMMER] = 1.2f;
    }

    // ---- where everything is ----

    private final float[] r = new float[9], m = new float[9];

    /** Works out every joint's place in the world from the pose. */
    void pose() {
        for (int j = 0; j < JOINTS; j++) {
            // the joint's own turn: about x, then about z, then about y
            float cxr = (float) Math.cos(ax[j]), sxr = (float) Math.sin(ax[j]);
            float czr = (float) Math.cos(az[j]), szr = (float) Math.sin(az[j]);
            float cyr = (float) Math.cos(ay[j]), syr = (float) Math.sin(ay[j]);
            // Rz * Rx
            float a0 = czr, a1 = -szr * cxr, a2 = szr * sxr;
            float c0 = 0f, c1 = sxr, c2 = cxr;
            // Ry * (Rz * Rx)
            r[0] = cyr * a0 + syr * c0; r[1] = cyr * a1 + syr * c1; r[2] = cyr * a2 + syr * c2;
            r[3] = szr; r[4] = czr * cxr; r[5] = -czr * sxr;
            r[6] = -syr * a0 + cyr * c0; r[7] = -syr * a1 + cyr * c1; r[8] = -syr * a2 + cyr * c2;
            float px = PIVOT[j * 3] * size, py = PIVOT[j * 3 + 1] * size, pz = PIVOT[j * 3 + 2] * size;
            // local: p -> R (p - pivot) + pivot
            float tx = px - (r[0] * px + r[1] * py + r[2] * pz);
            float ty = py - (r[3] * px + r[4] * py + r[5] * pz);
            float tz = pz - (r[6] * px + r[7] * py + r[8] * pz);
            int p = PARENT[j];
            if (p < 0) {
                System.arraycopy(r, 0, jr, 0, 9);
                jt[0] = tx + x;
                jt[1] = ty + lift * size;
                jt[2] = tz + z;
            } else {
                // world = parent (local)
                for (int a = 0; a < 3; a++)
                    for (int b = 0; b < 3; b++)
                        m[a * 3 + b] = jr[p * 9 + a * 3] * r[b] + jr[p * 9 + a * 3 + 1] * r[3 + b] + jr[p * 9 + a * 3 + 2] * r[6 + b];
                System.arraycopy(m, 0, jr, j * 9, 9);
                for (int a = 0; a < 3; a++)
                    jt[j * 3 + a] = jr[p * 9 + a * 3] * tx + jr[p * 9 + a * 3 + 1] * ty + jr[p * 9 + a * 3 + 2] * tz + jt[p * 3 + a];
            }
        }
    }

    /** Where a point of her model, moved with a joint, is in the world. */
    void place(int j, float lx, float ly, float lz, float[] out) {
        lx *= size;
        ly *= size;
        lz *= size;
        for (int a = 0; a < 3; a++)
            out[a] = jr[j * 9 + a * 3] * lx + jr[j * 9 + a * 3 + 1] * ly + jr[j * 9 + a * 3 + 2] * lz + jt[j * 3 + a];
    }

    private final float[] at = new float[3], turn = new float[9];

    void draw(DrawList d) {
        for (int i = 0; i < n; i++) {
            int j = joint[i];
            place(j, mid[i * 3], mid[i * 3 + 1], mid[i * 3 + 2], at);
            System.arraycopy(jr, j * 9, turn, 0, 9);
            float s = size;
            d.box(at[0], at[1], at[2], half[i * 3] * s, half[i * 3 + 1] * s, half[i * 3 + 2] * s, turn, shade(i), drawn(i));
        }
    }

    /** How a part is drawn: a knee put out goes dark; in the fight all of her burns. */
    private float drawn(int i) {
        float m = look[i];
        if (m == DrawList.GLOW && out(i)) m = DrawList.PLAIN;
        return fire && m != DrawList.GLOW ? DrawList.material((int) m, DrawList.FIRE) : m;
    }

    /** Whether a part is a knee's glow that has been put out. */
    private boolean out(int i) {
        if (i < kneeFrom || i >= kneeTo || look[i] != DrawList.GLOW) return false;
        return joint[i] == SHIN_A ? kneeOutA : kneeOutB;
    }

    /**
     * The eyes and knees burn as bright as they are let to, the eyes going from yellow through
     * orange to red with the blows to the head; a blow flashes all of her pale.
     */
    private int shade(int i) {
        int c = color[i];
        if (i >= eyeFrom && i < eyeTo)
            c = Street.mix(0x3A1A08, head >= 3 ? c : head == 2 ? 0xFFA02A : 0xE0502A, eyes);
        else if (out(i)) c = 0x2E2628;
        else if (i >= kneeFrom && i < kneeTo && look[i] == DrawList.GLOW) c = Street.mix(0x3A1A08, c, 0.5f + 0.5f * eyes);
        return hurt > 0f ? Street.mix(c, 0xFFE0B0, Math.min(1f, hurt) * 0.6f) : c;
    }

    // ---- what the fight needs to know: where the weak points are ----

    /** The glow on a knee: a on the hammer side, else the other. */
    void knee(boolean a, float[] out) {
        place(a ? SHIN_A : SHIN_B, a ? 0.56f : -0.56f, 1.16f, -0.32f, out);
    }

    void headCenter(float[] out) {
        place(HEAD, 0f, 3.9f, -0.1f, out);
    }

    void chest(float[] out) {
        place(CHEST, 0f, 2.7f, 0f, out);
    }

    /** The empty fist, the one that throws: the bottom of it. */
    void fist(float[] out) {
        place(FORE_B, -1.34f, 0.8f, -0.04f, out);
    }

    // ---- breaking ----

    final Physics physics = new Physics();
    private Body[] bodies;
    private int[] bodyPart;
    boolean broken;

    /**
     * She breaks where she stands: each part a body of its own, moving as it was moving,
     * thrown out from the blow at (bx, by, bz).
     */
    void shatter(float bx, float by, float bz, Random rnd) {
        broken = true;
        brokenFor = 0f;
        physics.clear();
        // the parts are packed into one another: at first each meets only the ground
        physics.pairs = false;
        bodies = new Body[n];
        bodyPart = new int[n];
        float[] q = new float[4];
        for (int i = 0; i < n; i++) {
            int j = joint[i];
            place(j, mid[i * 3], mid[i * 3 + 1], mid[i * 3 + 2], at);
            Body b = new Body(half[i * 3] * size, half[i * 3 + 1] * size, half[i * 3 + 2] * size, body[i], color[i]);
            quat(jr, j * 9, q);
            b.setPose(at[0], at[1], at[2], q[0], q[1], q[2], q[3]);
            // out from the middle of her, up, and away from the blow
            float ox = at[0] - x, oy = at[1] - 2.4f * size, oz = at[2] - z;
            float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-3f;
            float kick = 2.2f + rnd.nextFloat() * 2.6f;
            b.vx = ox / ol * kick + (rnd.nextFloat() - 0.5f) * 1.5f;
            b.vy = oy / ol * kick + 1.2f + rnd.nextFloat() * 1.8f;
            b.vz = oz / ol * kick + (rnd.nextFloat() - 0.5f) * 1.5f + 1.2f;
            if (j == HAMMER) {
                // the hammer goes on where it was swung, toward the eye, and drops
                b.vx = (rnd.nextFloat() - 0.5f) * 0.6f;
                b.vy = 0.8f;
                b.vz = -1.2f;
            }
            b.wx = (rnd.nextFloat() - 0.5f) * 7f;
            b.wy = (rnd.nextFloat() - 0.5f) * 7f;
            b.wz = (rnd.nextFloat() - 0.5f) * 7f;
            bodies[i] = b;
            bodyPart[i] = i;
            physics.add(b);
        }
    }

    private float brokenFor;

    void fall(float dt) {
        if (!broken) return;
        brokenFor += dt;
        if (brokenFor > 0.45f) physics.pairs = true;
        physics.step(Math.min(dt, 1f / 30f));
    }

    /** The ground trembles under the heap: some of the pieces jump. */
    void jolt(Random rnd) {
        if (bodies == null) return;
        for (Body b : bodies) {
            if (b.py > 3f || rnd.nextFloat() < 0.4f) continue;
            b.vy += 2.5f + rnd.nextFloat() * 3f;
            b.vx += (rnd.nextFloat() - 0.5f) * 1.5f;
            b.wx += (rnd.nextFloat() - 0.5f) * 6f;
            b.asleep = false;
        }
    }

    void drawBroken(DrawList d) {
        for (int k = 0; k < bodies.length; k++) {
            Body b = bodies[k];
            int i = bodyPart[k];
            turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
            turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
            turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
            d.box(b.px, b.py, b.pz, b.hx, b.hy, b.hz, turn, shade(i), drawn(i));
        }
    }

    /** The turn at m[o..o+8], row by row, as a quaternion w, x, y, z. */
    static void quat(float[] mm, int o, float[] q) {
        float m00 = mm[o], m01 = mm[o + 1], m02 = mm[o + 2];
        float m10 = mm[o + 3], m11 = mm[o + 4], m12 = mm[o + 5];
        float m20 = mm[o + 6], m21 = mm[o + 7], m22 = mm[o + 8];
        float tr = m00 + m11 + m22;
        if (tr > 0f) {
            float s = (float) Math.sqrt(tr + 1f) * 2f;
            q[0] = 0.25f * s;
            q[1] = (m21 - m12) / s;
            q[2] = (m02 - m20) / s;
            q[3] = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
            q[0] = (m21 - m12) / s;
            q[1] = 0.25f * s;
            q[2] = (m01 + m10) / s;
            q[3] = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
            q[0] = (m02 - m20) / s;
            q[1] = (m01 + m10) / s;
            q[2] = 0.25f * s;
            q[3] = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
            q[0] = (m10 - m01) / s;
            q[1] = (m02 + m20) / s;
            q[2] = (m12 + m21) / s;
            q[3] = 0.25f * s;
        }
    }
}
