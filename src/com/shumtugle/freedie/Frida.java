package com.shumtugle.freedie;

/**
 * Freedie, as the player is: a giant heaped together from what the town's buildings are
 * made of, and dressed as a maid. Brick feet, legs, body and forearms; big stone blocks for
 * shoulders and fists, a brick laid on each shoulder; iron bands at the ankles, under the
 * shoulders and at the wrists; planks hanging splayed round the upper arms like sinews, and a
 * yoke of planks across the chest and back. A small head pushed forward: a brick jaw, a stone
 * face, two eyes like coals, a cap of plank; two stakes up behind it. The dress: a black skirt
 * in three tiers over white ruffles, a white apron with a pocket and lace at its hem, a bib
 * with three buttons and lace at its corners, a black bow at the collar; at the back the
 * straps cross and the sash ties in a big bow.
 *
 * Only boxes, as everything in the game is. The model is in units of its own, standing on its
 * feet at the origin and facing -z; it is drawn so many meters to a unit, turned about the
 * upright.
 */
final class Frida {
    static final int BRICK = 0xC9502F, BRICK_DARK = 0xA8402A, STONE = 0xBDB0A6, STONE_DARK = 0x9A8F88,
            FACE = 0xD0C0B6, PLANK = 0x8F6241, YOKE = 0x8A6650, YOKE_DARK = 0x7A5944, CAP = 0xA55E21,
            STAKE = 0x7A4A2A, IRON = 0x5F514C, DRESS = 0x1E1C21, WHITE = 0xEFE9E6, LACE = 0xE8E2DE,
            POCKET = 0xE0D8D4, EYES = 0xFFF0A0;
    /** How tall the model is, in its own units. */
    static final float HEIGHT = 4.6f;

    // the parts: middle, half sizes, a tilt about z then about x (radians), color, material
    private static final int MAX = 128;
    private static int n;
    private static final float[] P = new float[MAX * 8];
    private static final int[] COL = new int[MAX], MAT = new int[MAX];

    private Frida() {
    }

    private static void add(float x, float y, float z, float hx, float hy, float hz, int col, int mat) {
        tilted(x, y, z, hx, hy, hz, 0f, 0f, col, mat);
    }

    /** A part tilted about z (its top toward -x for a positive angle), then about x. */
    private static void tilted(float x, float y, float z, float hx, float hy, float hz, float az, float ax, int col, int mat) {
        int o = n * 8;
        P[o] = x; P[o + 1] = y; P[o + 2] = z;
        P[o + 3] = hx; P[o + 4] = hy; P[o + 5] = hz;
        P[o + 6] = az; P[o + 7] = ax;
        COL[n] = col;
        MAT[n] = mat;
        n++;
    }

    static final int MORTAR = 0x6E5A50;

    /**
     * A block of brick: courses laid one over another along its own height, each a shade of its
     * own, a hair apart, the mortar showing between them; tilted about z like a plain part.
     */
    private static void bricks(float x, float y, float z, float hx, float hy, float hz, float az, int col, int courses) {
        tilted(x, y, z, hx - 0.012f, hy, hz - 0.012f, az, 0f, MORTAR, DrawList.PLAIN);
        float h = hy / courses, sz = (float) Math.sin(az), cz = (float) Math.cos(az);
        for (int k = 0; k < courses; k++) {
            float dy = -hy + h * (2 * k + 1);
            float shade = 1f + 0.07f * (((k * 7 + Math.round(x * 13f)) % 3) - 1);
            tilted(x - sz * dy, y + cz * dy, z, hx, h - 0.018f, hz, az, 0f, Street.scale(col, shade), DrawList.GRAIN);
        }
    }

    static {
        int grain = DrawList.GRAIN, stone = DrawList.STONE, wood = DrawList.WOOD, plain = DrawList.PLAIN,
                rail = DrawList.RAIL, glow = DrawList.GLOW;
        float deg = (float) Math.PI / 180f;
        for (int s = -1; s <= 1; s += 2) {
            // feet, the iron-strapped stone at the ankles, the legs
            bricks(s * 0.66f, 0.17f, -0.12f, 0.47f, 0.17f, 0.58f, 0f, BRICK, 1);
            add(s * 0.66f, 0.43f, 0f, 0.45f, 0.1f, 0.45f, STONE, stone);
            for (int k = -1; k <= 1; k += 2) add(s * 0.66f + k * 0.3f, 0.43f, -0.46f, 0.06f, 0.11f, 0.012f, IRON, plain);
            bricks(s * 0.66f, 0.95f, 0f, 0.41f, 0.42f, 0.4f, 0f, BRICK_DARK, 3);
        }
        // the skirt: ruffles under it, three black tiers, the sash at the waist
        add(0f, 1.28f, 0f, 1.45f, 0.08f, 1.02f, LACE, rail);
        add(0f, 1.42f, 0f, 1.38f, 0.07f, 0.98f, WHITE, rail);
        add(0f, 1.62f, 0f, 1.32f, 0.16f, 0.92f, DRESS, plain);
        add(0f, 1.92f, 0f, 1.18f, 0.16f, 0.84f, DRESS, plain);
        add(0f, 2.22f, 0f, 1.02f, 0.16f, 0.76f, DRESS, plain);
        add(0f, 2.42f, 0f, 0.98f, 0.06f, 0.72f, WHITE, plain);
        // the apron, lace at its hem, a pocket on the right
        add(0f, 2.1f, -0.95f, 0.76f, 0.46f, 0.02f, WHITE, plain);
        add(0f, 1.6f, -0.96f, 0.8f, 0.06f, 0.025f, LACE, rail);
        add(0.42f, 2.2f, -0.975f, 0.17f, 0.17f, 0.006f, POCKET, plain);
        // the body, the yoke of planks across it front and back, iron rivets at their ends
        bricks(0f, 3.05f, 0.02f, 0.92f, 0.62f, 0.68f, 0f, BRICK, 4);
        for (int k = 0; k < 3; k++) {
            float y = 3.4f + k * 0.2f;
            int c = k == 1 ? YOKE_DARK : YOKE;
            for (int f = -1; f <= 1; f += 2) {
                add(0f, y, f * 0.74f, 1.0f, 0.092f, 0.04f, c, wood);
                for (int e = -1; e <= 1; e += 2) add(e * 0.9f, y, f * 0.785f, 0.035f, 0.035f, 0.008f, IRON, plain);
            }
        }
        // the bib, its buttons, lace at its corners, the bow at the collar
        add(0f, 3.0f, -0.8f, 0.48f, 0.45f, 0.02f, WHITE, plain);
        for (int b = 0; b < 3; b++) add(0f, 3.1f - b * 0.18f, -0.825f, 0.05f, 0.05f, 0.008f, DRESS, plain);
        for (int s = -1; s <= 1; s += 2) add(s * 0.55f, 3.45f, -0.82f, 0.13f, 0.17f, 0.04f, LACE, rail);
        add(0f, 3.4f, -0.86f, 0.07f, 0.07f, 0.04f, DRESS, plain);
        for (int s = -1; s <= 1; s += 2) tilted(s * 0.2f, 3.4f, -0.85f, 0.16f, 0.1f, 0.03f, s * 8f * deg, 0f, DRESS, plain);
        // at the back: the straps crossing, the sash tied in a big bow
        for (int s = -1; s <= 1; s += 2) tilted(0f, 3.1f, 0.8f + s * 0.01f, 0.08f, 0.85f, 0.02f, s * 42f * deg, 0f, WHITE, plain);
        add(0f, 2.45f, 0.8f, 0.1f, 0.09f, 0.06f, WHITE, plain);
        for (int s = -1; s <= 1; s += 2) {
            tilted(s * 0.32f, 2.5f, 0.78f, 0.28f, 0.17f, 0.05f, s * 15f * deg, 0f, WHITE, plain);
            tilted(s * 0.18f, 2.12f, 0.8f, 0.08f, 0.3f, 0.03f, s * 18f * deg, 0f, WHITE, plain);
        }
        for (int s = -1; s <= 1; s += 2) {
            // a stone shoulder, the iron band under it, a brick laid on it
            add(s * 1.3f, 3.72f, 0.02f, 0.54f, 0.48f, 0.56f, STONE, stone);
            add(s * 1.3f, 3.25f, 0.02f, 0.55f, 0.05f, 0.57f, IRON, plain);
            bricks(s * 1.15f, 4.33f, 0.15f, 0.38f, 0.13f, 0.36f, 0f, BRICK, 1);
            // the upper arm, stone, and the planks hanging splayed round it
            add(s * 1.32f, 2.95f, 0.02f, 0.28f, 0.32f, 0.28f, STONE_DARK, stone);
            float[] px = {1.0f, 1.18f, 1.36f, 1.12f, 1.42f}, pz = {-0.3f, -0.36f, -0.3f, 0.32f, 0.3f}, pa = {26f, 32f, 38f, 28f, 36f};
            for (int k = 0; k < px.length; k++) {
                float a = pa[k] * deg, l = 0.45f;
                tilted(s * (px[k] + (float) Math.sin(a) * l), 3.22f - (float) Math.cos(a) * l, pz[k], 0.055f, l, 0.075f,
                        s * a, 0f, k % 2 == 0 ? PLANK : YOKE, wood);
            }
            // the forearm, brick, leaning out; the iron cuff at the wrist; the stone fist
            float fa = 12f * deg;
            bricks(s * 1.7f, 2.2f, 0f, 0.45f, 0.52f, 0.45f, s * fa, BRICK, 3);
            tilted(s * 1.86f, 1.72f, 0f, 0.47f, 0.07f, 0.47f, s * fa, 0f, IRON, plain);
            add(s * 2.0f, 1.38f, 0f, 0.42f, 0.28f, 0.42f, STONE_DARK, stone);
            for (int k = 0; k < 3; k++) add(s * (1.75f + k * 0.26f), 1.3f, -0.45f, 0.12f, 0.15f, 0.07f, STONE, stone);
            add(s * 1.6f, 1.45f, -0.22f, 0.12f, 0.15f, 0.18f, STONE, stone);
        }
        // the head, pushed forward: a brick jaw, a stone face, the eyes, a cap of plank with a slab on it
        bricks(0f, 3.9f, -0.6f, 0.32f, 0.16f, 0.28f, 0f, BRICK, 2);
        add(0f, 4.22f, -0.5f, 0.3f, 0.17f, 0.28f, FACE, stone);
        for (int s = -1; s <= 1; s += 2) add(s * 0.14f, 4.27f, -0.785f, 0.07f, 0.035f, 0.01f, EYES, glow);
        add(0f, 4.45f, -0.5f, 0.38f, 0.08f, 0.36f, CAP, wood);
        add(0f, 4.56f, -0.5f, 0.33f, 0.03f, 0.31f, 0xC9C1B8, stone);
        // two stakes up behind it, leaning out
        for (int s = -1; s <= 1; s += 2) tilted(s * 0.48f, 4.15f, 0.1f, 0.04f, 0.38f, 0.04f, -s * 15f * deg, 0f, STAKE, wood);
    }

    private static final float[] rot = new float[9];

    /** Draws her standing at (x, y, z), turned about the upright by yaw (0: facing -z), so many meters to her unit. */
    static void draw(DrawList d, float x, float y, float z, float yaw, float size) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        for (int i = 0; i < n; i++) {
            int o = i * 8;
            float px = P[o] * size, py = P[o + 1] * size, pz = P[o + 2] * size;
            float az = P[o + 6], ax = P[o + 7];
            // the part's own tilt: about z, then about x; then the turn about the upright
            float cz = (float) Math.cos(az), sz = (float) Math.sin(az), cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
            // R = Ryaw * Rx * Rz, row by row
            float r00 = cz, r01 = -sz, r02 = 0f;
            float r10 = cx * sz, r11 = cx * cz, r12 = -sx;
            float r20 = sx * sz, r21 = sx * cz, r22 = cx;
            rot[0] = cy * r00 + sy * r20; rot[1] = cy * r01 + sy * r21; rot[2] = cy * r02 + sy * r22;
            rot[3] = r10; rot[4] = r11; rot[5] = r12;
            rot[6] = -sy * r00 + cy * r20; rot[7] = -sy * r01 + cy * r21; rot[8] = -sy * r02 + cy * r22;
            float wx = x + cy * px + sy * pz, wz = z - sy * px + cy * pz;
            d.box(wx, y + py, wz, P[o + 3] * size, P[o + 4] * size, P[o + 5] * size, rot, COL[i], MAT[i]);
        }
    }
}
