package com.shumtugle.freedie;

/**
 * Freedie, as the player is: a giant heaped together from what the town's buildings are
 * made of, and dressed as a maid. Brick feet, shins, body and forearms laid in courses; big
 * stone blocks for shoulders and fists, a brick on each shoulder; iron bands at the ankles,
 * under the shoulders and at the wrists; planks along the upper arms like sinews, a yoke of
 * planks across chest and back. A small head: a brick jaw, a stone face, two eyes like coals,
 * a cap of plank; two stakes up behind it. The dress: a black skirt in tiers over white
 * ruffles, an apron with a pocket, a bib with buttons, a black bow at the collar; at the back
 * the straps cross and the sash ties in a bow.
 *
 * Only boxes, as everything in the game is, each one riding on a bone of a humanoid skeleton
 * (see Moves): the blocks are laid out round the skeleton's rest pose, and each keeps where
 * it sits on its bone as the bone turns, so she walks, runs, rolls, jumps, throws and falls
 * as the skeleton's motions go. She stands at the origin facing -z; she is drawn so many
 * times the skeleton's own size, turned about the upright.
 */
final class Frida {
    static final int BRICK = 0xC9502F, BRICK_DARK = 0xA8402A, STONE = 0xBDB0A6, STONE_DARK = 0x9A8F88,
            FACE = 0xD0C0B6, PLANK = 0x8F6241, YOKE = 0x8A6650, YOKE_DARK = 0x7A5944, CAP = 0xA55E21,
            STAKE = 0x7A4A2A, IRON = 0x5F514C, DRESS = 0x1E1C21, WHITE = 0xEFE9E6, LACE = 0xE8E2DE,
            POCKET = 0xE0D8D4, EYES = 0xFFF0A0, MORTAR = 0x6E5A50, SLAB = 0xC9C1B8;

    /** How far her eyes are lit, 0 (dark coals) to 1. */
    static float eyes = 1f;

    private static Moves moves;
    // the parts: bone, place and turn on it (worked out from the rest pose), half sizes, color, material
    private static final int MAX = 200;
    private static int n;
    private static final int[] BONE = new int[MAX], COL = new int[MAX], MAT = new int[MAX];
    private static final float[] LP = new float[MAX * 3], LQ = new float[MAX * 4], HALF = new float[MAX * 3];
    // the pose now: each bone's turn and place
    private static float[] pq, pp;

    private Frida() {
    }

    /** The skeleton and its motions; until they are given she is not drawn. */
    static void use(Moves m) {
        if (m == null || moves == m) return;
        moves = m;
        pq = new float[m.bone.length * 4];
        pp = new float[m.bone.length * 3];
        n = 0;
        build();
    }

    static boolean ready() {
        return moves != null;
    }

    // ---- laying the blocks out round the rest pose ----

    private static int bone;

    private static void on(String name) {
        bone = moves.bone(name);
    }

    /** A block at a place of the rest pose, turned about z then about x (radians), on the bone set by on(). */
    private static void part(float x, float y, float z, float hx, float hy, float hz, float az, float ax, int col, int mat) {
        if (bone < 0 || n >= MAX) return;
        float cz = (float) Math.cos(az * 0.5f), sz = (float) Math.sin(az * 0.5f);
        float cx = (float) Math.cos(ax * 0.5f), sx = (float) Math.sin(ax * 0.5f);
        // the part's own turn: about x after about z
        float[] pq0 = {sx * cz, -sx * sz, cx * sz, cx * cz};
        float[] rq = {moves.restQ[bone * 4], moves.restQ[bone * 4 + 1], moves.restQ[bone * 4 + 2], moves.restQ[bone * 4 + 3]};
        float[] inv = {-rq[0], -rq[1], -rq[2], rq[3]};
        float[] d = {x - moves.restP[bone * 3], y - moves.restP[bone * 3 + 1], z - moves.restP[bone * 3 + 2]};
        float[] l = rotate(inv, d);
        float[] q = mul(inv, pq0);
        BONE[n] = bone;
        System.arraycopy(l, 0, LP, n * 3, 3);
        System.arraycopy(q, 0, LQ, n * 4, 4);
        HALF[n * 3] = hx;
        HALF[n * 3 + 1] = hy;
        HALF[n * 3 + 2] = hz;
        COL[n] = col;
        MAT[n] = mat;
        n++;
    }

    private static void part(float x, float y, float z, float hx, float hy, float hz, int col, int mat) {
        part(x, y, z, hx, hy, hz, 0f, 0f, col, mat);
    }

    /**
     * A block of brick: courses laid along one of its axes (0 across, 1 up), each a shade of
     * its own, a hair apart, the mortar showing between them.
     */
    private static void bricks(float x, float y, float z, float hx, float hy, float hz, int axis, int col, int courses) {
        float g = 0.004f;
        part(x, y, z, hx - (axis == 0 ? 0f : g), hy - (axis == 1 ? 0f : g), hz - g, MORTAR, DrawList.PLAIN);
        float len = axis == 0 ? hx : hy, h = len / courses;
        for (int k = 0; k < courses; k++) {
            float o = -len + h * (2 * k + 1);
            float shade = 1f + 0.07f * (((k * 7 + Math.round(Math.abs(x) * 61f + y * 13f)) % 3) - 1);
            int c = Street.scale(col, shade);
            if (axis == 0) part(x + o, y, z, h - g, hy, hz, c, DrawList.GRAIN);
            else part(x, y + o, z, hx, h - g, hz, c, DrawList.GRAIN);
        }
    }

    private static void build() {
        int stone = DrawList.STONE, wood = DrawList.WOOD, plain = DrawList.PLAIN, rail = DrawList.RAIL, glow = DrawList.GLOW;
        float deg = (float) Math.PI / 180f;
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "_l" : "_r";
            // feet, the iron-strapped stone at the ankles, the shins and thighs
            on("foot" + side);
            bricks(s * 0.1f, 0.065f, -0.035f, 0.11f, 0.065f, 0.15f, 1, BRICK, 1);
            on("calf" + side);
            part(s * 0.095f, 0.16f, 0f, 0.105f, 0.035f, 0.105f, STONE, stone);
            for (int k = -1; k <= 1; k += 2) part(s * 0.095f + k * 0.065f, 0.16f, -0.106f, 0.012f, 0.038f, 0.003f, IRON, plain);
            bricks(s * 0.095f, 0.36f, 0f, 0.1f, 0.165f, 0.1f, 1, BRICK_DARK, 3);
            on("thigh" + side);
            bricks(s * 0.1f, 0.72f, 0f, 0.115f, 0.19f, 0.115f, 1, BRICK_DARK, 3);
        }
        // the skirt, on the hips: white ruffles under three black tiers; the apron, its lace and pocket
        on("pelvis");
        part(0f, 0.69f, 0f, 0.33f, 0.02f, 0.245f, LACE, rail);
        part(0f, 0.725f, 0f, 0.315f, 0.02f, 0.235f, WHITE, rail);
        part(0f, 0.785f, 0f, 0.3f, 0.045f, 0.22f, DRESS, plain);
        part(0f, 0.87f, 0f, 0.27f, 0.045f, 0.2f, DRESS, plain);
        part(0f, 0.955f, 0f, 0.24f, 0.045f, 0.18f, DRESS, plain);
        part(0f, 0.875f, -0.24f, 0.17f, 0.115f, 0.005f, WHITE, plain);
        part(0f, 0.755f, -0.243f, 0.18f, 0.015f, 0.006f, LACE, rail);
        part(0.09f, 0.89f, -0.247f, 0.04f, 0.04f, 0.002f, POCKET, plain);
        // the waist and the sash; at the back its bow
        on("spine_01");
        bricks(0f, 1.07f, 0.005f, 0.19f, 0.06f, 0.14f, 1, BRICK, 2);
        part(0f, 1.02f, 0.005f, 0.215f, 0.016f, 0.16f, WHITE, plain);
        part(0f, 1.03f, 0.17f, 0.025f, 0.022f, 0.015f, WHITE, plain);
        for (int s = -1; s <= 1; s += 2) {
            part(s * 0.075f, 1.045f, 0.168f, 0.065f, 0.04f, 0.012f, s * 15f * deg, 0f, WHITE, plain);
            part(s * 0.04f, 0.96f, 0.17f, 0.018f, 0.07f, 0.008f, s * 18f * deg, 0f, WHITE, plain);
        }
        // the body: brick in courses, broad at the chest
        on("spine_02");
        bricks(0f, 1.2f, 0.005f, 0.21f, 0.075f, 0.15f, 1, BRICK, 2);
        on("spine_03");
        bricks(0f, 1.37f, 0.005f, 0.25f, 0.1f, 0.165f, 1, BRICK, 3);
        // the yoke of planks across it, front and back, iron rivets at their ends
        for (int k = 0; k < 3; k++) {
            float y = 1.37f + k * 0.045f;
            int c = k == 1 ? YOKE_DARK : YOKE;
            for (int f = -1; f <= 1; f += 2) {
                part(0f, y, f * 0.172f, 0.255f, 0.021f, 0.009f, c, wood);
                for (int e = -1; e <= 1; e += 2) part(e * 0.23f, y, f * 0.182f, 0.008f, 0.008f, 0.002f, IRON, plain);
            }
        }
        // the bib over it, its buttons, lace at its corners, the bow at the collar
        part(0f, 1.3f, -0.185f, 0.11f, 0.1f, 0.005f, WHITE, plain);
        for (int b = 0; b < 3; b++) part(0f, 1.33f - b * 0.04f, -0.191f, 0.011f, 0.011f, 0.002f, DRESS, plain);
        for (int s = -1; s <= 1; s += 2) part(s * 0.125f, 1.4f, -0.186f, 0.03f, 0.038f, 0.009f, LACE, rail);
        part(0f, 1.43f, -0.195f, 0.016f, 0.016f, 0.01f, DRESS, plain);
        for (int s = -1; s <= 1; s += 2) part(s * 0.045f, 1.43f, -0.193f, 0.036f, 0.022f, 0.007f, s * 8f * deg, 0f, DRESS, plain);
        // at the back the straps cross
        for (int s = -1; s <= 1; s += 2) part(0f, 1.27f, 0.175f + s * 0.002f, 0.018f, 0.19f, 0.004f, s * 42f * deg, 0f, WHITE, plain);
        // two stakes up behind the head, leaning out
        for (int s = -1; s <= 1; s += 2) part(s * 0.11f, 1.6f, 0.06f, 0.009f, 0.085f, 0.009f, -s * 15f * deg, 0f, STAKE, wood);
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "_l" : "_r";
            // a stone shoulder, the iron band under it, a brick laid on it
            on("clavicle" + side);
            part(s * 0.3f, 1.5f, 0.005f, 0.125f, 0.11f, 0.13f, STONE, stone);
            part(s * 0.3f, 1.385f, 0.005f, 0.128f, 0.011f, 0.133f, IRON, plain);
            bricks(s * 0.26f, 1.64f, 0.035f, 0.085f, 0.03f, 0.08f, 1, BRICK, 1);
            // the upper arm, stone, planks along it like sinews
            on("upperarm" + side);
            part(s * 0.33f, 1.44f, 0.065f, 0.13f, 0.07f, 0.07f, STONE_DARK, stone);
            float[] oy = {0.075f, -0.075f, 0f, 0f}, oz = {0f, 0f, 0.075f, -0.075f};
            for (int k = 0; k < 4; k++)
                part(s * 0.34f, 1.44f + oy[k], 0.065f + oz[k], 0.14f, oy[k] != 0f ? 0.009f : 0.016f, oz[k] != 0f ? 0.009f : 0.016f,
                        k % 2 == 0 ? PLANK : YOKE, wood);
            // the forearm, brick, bigger than the upper arm; the iron cuff at the wrist
            on("lowerarm" + side);
            bricks(s * 0.6f, 1.44f, 0.068f, 0.13f, 0.11f, 0.11f, 0, BRICK, 3);
            part(s * 0.735f, 1.44f, 0.066f, 0.014f, 0.116f, 0.116f, IRON, plain);
            // the fist, stone, the thumb over it
            on("hand" + side);
            part(s * 0.83f, 1.44f, 0.065f, 0.09f, 0.095f, 0.095f, STONE_DARK, stone);
            for (int k = 0; k < 3; k++) part(s * 0.9f, 1.405f + k * 0.035f, 0.0f, 0.03f, 0.016f, 0.03f, STONE, stone);
            part(s * 0.81f, 1.44f, -0.04f, 0.04f, 0.03f, 0.025f, STONE, stone);
        }
        // the head: small, a brick jaw, a stone face, the eyes, a cap of plank with a slab on it
        on("Head");
        bricks(0f, 1.6f, -0.045f, 0.085f, 0.035f, 0.08f, 1, BRICK, 2);
        part(0f, 1.67f, -0.035f, 0.08f, 0.04f, 0.075f, FACE, stone);
        for (int s = -1; s <= 1; s += 2) part(s * 0.037f, 1.68f, -0.111f, 0.019f, 0.009f, 0.003f, EYES, glow);
        part(0f, 1.725f, -0.035f, 0.1f, 0.02f, 0.095f, CAP, wood);
        part(0f, 1.75f, -0.035f, 0.085f, 0.008f, 0.08f, SLAB, stone);
    }

    // ---- turns as quaternions (x y z w) ----

    private static float[] mul(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static float[] rotate(float[] q, float[] v) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        // v + 2 u x (u x v + w v)
        float cx = y * v[2] - z * v[1] + w * v[0], cy = z * v[0] - x * v[2] + w * v[1], cz = x * v[1] - y * v[0] + w * v[2];
        return new float[]{v[0] + 2f * (y * cz - z * cy), v[1] + 2f * (z * cx - x * cz), v[2] + 2f * (x * cy - y * cx)};
    }

    private static final float[] rot = new float[9];

    /** Where one of her bones is in the world, in a motion at a time, standing and turned as draw() puts her. */
    static void place(String boneName, float x, float y, float z, float yaw, float size, String motion, float t, float[] out) {
        if (moves == null) return;
        int c = moves.motion(motion), b = moves.bone(boneName);
        if (c < 0 || b < 0) return;
        moves.pose(c, t, pq, pp);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float px = pp[b * 3] * size, py = pp[b * 3 + 1] * size, pz = pp[b * 3 + 2] * size;
        out[0] = x + cy * px + sy * pz;
        out[1] = y + py;
        out[2] = z - sy * px + cy * pz;
    }

    /** Draws her standing as at rest, the first motion she knows still (the opening). */
    static void draw(DrawList d, float x, float y, float z, float yaw, float size) {
        draw(d, x, y, z, yaw, size, "idle", 0f);
    }

    /** Draws her at (x, y, z), turned about the upright by yaw (0: facing -z), size times the skeleton, in a motion at a time. */
    static void draw(DrawList d, float x, float y, float z, float yaw, float size, String motion, float t) {
        if (moves == null) return;
        int c = moves.motion(motion);
        if (c < 0) c = 0;
        moves.pose(c, t, pq, pp);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float[] bq = new float[4], lq = new float[4], lp = new float[3];
        for (int i = 0; i < n; i++) {
            int b = BONE[i];
            System.arraycopy(pq, b * 4, bq, 0, 4);
            System.arraycopy(LQ, i * 4, lq, 0, 4);
            System.arraycopy(LP, i * 3, lp, 0, 3);
            float[] off = rotate(bq, lp);
            float px = (pp[b * 3] + off[0]) * size, py = (pp[b * 3 + 1] + off[1]) * size, pz = (pp[b * 3 + 2] + off[2]) * size;
            float[] q = mul(bq, lq);
            // the part's turn as rows, then the turn about the upright before it
            float qx = q[0], qy = q[1], qz = q[2], qw = q[3];
            float r00 = 1 - 2 * (qy * qy + qz * qz), r01 = 2 * (qx * qy - qz * qw), r02 = 2 * (qx * qz + qy * qw);
            float r10 = 2 * (qx * qy + qz * qw), r11 = 1 - 2 * (qx * qx + qz * qz), r12 = 2 * (qy * qz - qx * qw);
            float r20 = 2 * (qx * qz - qy * qw), r21 = 2 * (qy * qz + qx * qw), r22 = 1 - 2 * (qx * qx + qy * qy);
            rot[0] = cy * r00 + sy * r20; rot[1] = cy * r01 + sy * r21; rot[2] = cy * r02 + sy * r22;
            rot[3] = r10; rot[4] = r11; rot[5] = r12;
            rot[6] = -sy * r00 + cy * r20; rot[7] = -sy * r01 + cy * r21; rot[8] = -sy * r02 + cy * r22;
            float wx = x + cy * px + sy * pz, wz = z - sy * px + cy * pz;
            int col = COL[i] == EYES ? Street.mix(0x2A1C14, EYES, eyes) : COL[i];
            d.box(wx, y + py, wz, HALF[i * 3] * size, HALF[i * 3 + 1] * size, HALF[i * 3 + 2] * size, rot, col, MAT[i]);
        }
    }
}
