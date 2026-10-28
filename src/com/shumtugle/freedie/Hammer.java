package com.shumtugle.freedie;

/**
 * The player's hammer, as in the game it comes from: a handle, a darker grip, a grained
 * wooden head bound with two iron hoops near its ends. Its own frame: the handle along x,
 * the head's long side along z. A pose is twelve numbers: the middle, then the turn row by row.
 */
final class Hammer {
    // the parts: middle, half sizes, in meters
    static final float[][] PARTS = {
            {-0.1f, 0f, 0f, 0.31f, 0.03f, 0.03f},       // handle
            {-0.36f, 0f, 0f, 0.07f, 0.036f, 0.036f},    // grip
            {0.31f, 0f, 0f, 0.1f, 0.078f, 0.17f},       // head
            {0.31f, 0f, 0.12f, 0.104f, 0.082f, 0.016f}, // hoops
            {0.31f, 0f, -0.12f, 0.104f, 0.082f, 0.016f},
    };
    private static final int[] COL = {0x9A6638, 0x6E3F27, 0xDFA868, 0x55524F, 0x55524F};
    private static final int[] MAT = {DrawList.WOOD, DrawList.PLAIN, DrawList.WOOD, DrawList.PLAIN, DrawList.PLAIN};
    // the gift from above: a dark wrought head, broader and deeper, bound in bronze, on a stout handle
    static final float[][] HEAVY = {
            {-0.1f, 0f, 0f, 0.31f, 0.038f, 0.038f},
            {-0.36f, 0f, 0f, 0.07f, 0.044f, 0.044f},
            {0.31f, 0f, 0f, 0.13f, 0.1f, 0.2f},
            {0.31f, 0f, 0.15f, 0.134f, 0.104f, 0.02f},
            {0.31f, 0f, -0.15f, 0.134f, 0.104f, 0.02f},
    };
    private static final int[] HEAVY_COL = {0x6E4A2C, 0x3E2618, 0x3F3D44, 0xA8783A, 0xA8783A};
    private static final int[] HEAVY_MAT = {DrawList.WOOD, DrawList.PLAIN, DrawList.STONE, DrawList.PAINT, DrawList.PAINT};
    // the golden hammer: the plain one's shape, its head of gold burning from inside, its hoops
    // of polished gold
    private static final int[] GOLD_COL = {0x8A5A32, 0x5A3418, 0xFFB428, 0xC88A2E, 0xC88A2E};
    private static final int[] GOLD_MAT = {DrawList.WOOD, DrawList.PLAIN, DrawList.FIRE_GOLD, DrawList.GOLD, DrawList.GOLD};
    // the watchers' hammer for the fight: the heavy one's shape, its head of pale blue diamond
    private static final int[] DIAMOND_COL = {0x7A5A3A, 0x3E2618, 0x6CC8EC, 0xE8FAFF, 0xE8FAFF};
    private static final int[] DIAMOND_MAT = {DrawList.WOOD, DrawList.PLAIN, DrawList.GLASS, DrawList.GLASS, DrawList.GLASS};
    static final int PLAIN = 0, HEAVY_KIND = 1, GOLDEN = 2, DIAMOND = 3;

    private static final float[] T = new float[9];

    private Hammer() {
    }

    static void draw(DrawList d, float[] pose) {
        draw(d, pose, false, 1f);
    }

    /** The plain hammer or the heavy one, so big. */
    static void draw(DrawList d, float[] pose, boolean heavy, float scale) {
        draw(d, pose, heavy ? HEAVY_KIND : PLAIN, scale);
    }

    /** The plain hammer, the heavy one, the golden one or the diamond one, so big. */
    static void draw(DrawList d, float[] pose, int kind, float scale) {
        float[][] parts = kind == HEAVY_KIND || kind == DIAMOND ? HEAVY : PARTS;
        int[] col = kind == HEAVY_KIND ? HEAVY_COL : kind == GOLDEN ? GOLD_COL : kind == DIAMOND ? DIAMOND_COL : COL;
        int[] mat = kind == HEAVY_KIND ? HEAVY_MAT : kind == GOLDEN ? GOLD_MAT : kind == DIAMOND ? DIAMOND_MAT : MAT;
        T[0] = pose[3]; T[1] = pose[4]; T[2] = pose[5];
        T[3] = pose[6]; T[4] = pose[7]; T[5] = pose[8];
        T[6] = pose[9]; T[7] = pose[10]; T[8] = pose[11];
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            float ox = p[0] * scale, oy = p[1] * scale, oz = p[2] * scale;
            float cx = pose[0] + T[0] * ox + T[1] * oy + T[2] * oz;
            float cy = pose[1] + T[3] * ox + T[4] * oy + T[5] * oz;
            float cz = pose[2] + T[6] * ox + T[7] * oy + T[8] * oz;
            // the diamond keeps its own colours, however the world is seen
            float m = kind == DIAMOND ? DrawList.material(mat[i], DrawList.KEEP) : mat[i];
            d.box(cx, cy, cz, p[3] * scale, p[4] * scale, p[5] * scale, T, col[i], m);
        }
    }

    /** The parts of a kind of hammer, in its own frame: middle, half sizes, in meters. */
    static float[][] parts(int kind) {
        return kind == HEAVY_KIND || kind == DIAMOND ? HEAVY : PARTS;
    }

    /** The colours of a kind's parts, as 0xRRGGBB: handle, grip, head, the hoops. */
    static int[] colours(int kind) {
        return kind == HEAVY_KIND ? HEAVY_COL : kind == GOLDEN ? GOLD_COL : kind == DIAMOND ? DIAMOND_COL : COL;
    }

    /** The colour of a kind's head, the one the eye takes it by. */
    static int head(int kind) {
        return colours(kind)[2];
    }

    private static final float[] C3 = new float[3], P2 = new float[2], ALL = new float[16];

    /**
     * Where a hammer lies in the frame: the outline of each of its parts (a box seen from
     * anywhere is a convex shape, the hull of its eight corners), as x and y in turn, so many
     * corners each. A part with a corner behind the eye is left out. Returns how many parts.
     */
    static int outline(DrawList d, float[] pose, int kind, float scale, float[] pts, int[] counts) {
        float[][] parts = parts(kind);
        int n = 0;
        for (float[] p : parts) {
            boolean seen = true;
            for (int k = 0; k < 8; k++) {
                float ox = (p[0] + ((k & 1) == 0 ? -p[3] : p[3])) * scale;
                float oy = (p[1] + ((k & 2) == 0 ? -p[4] : p[4])) * scale;
                float oz = (p[2] + ((k & 4) == 0 ? -p[5] : p[5])) * scale;
                C3[0] = pose[0] + pose[3] * ox + pose[4] * oy + pose[5] * oz;
                C3[1] = pose[1] + pose[6] * ox + pose[7] * oy + pose[8] * oz;
                C3[2] = pose[2] + pose[9] * ox + pose[10] * oy + pose[11] * oz;
                if (!d.project(C3[0], C3[1], C3[2], P2)) {
                    seen = false;
                    break;
                }
                ALL[k * 2] = P2[0];
                ALL[k * 2 + 1] = P2[1];
            }
            if (!seen) continue;
            counts[n] = hull(ALL, 8, pts, n * 16);
            n++;
        }
        return n;
    }

    /** The convex hull of so many points, its corners in turn written from an offset; how many corners. */
    private static int hull(float[] p, int m, float[] out, int at) {
        // the lowest-leftmost point, then a wrap round the rest
        int start = 0;
        for (int i = 1; i < m; i++)
            if (p[i * 2] < p[start * 2] || p[i * 2] == p[start * 2] && p[i * 2 + 1] < p[start * 2 + 1]) start = i;
        int cur = start, n = 0;
        do {
            out[at + n * 2] = p[cur * 2];
            out[at + n * 2 + 1] = p[cur * 2 + 1];
            n++;
            int next = (cur + 1) % m;
            for (int i = 0; i < m; i++) {
                float cross = (p[next * 2] - p[cur * 2]) * (p[i * 2 + 1] - p[cur * 2 + 1])
                        - (p[next * 2 + 1] - p[cur * 2 + 1]) * (p[i * 2] - p[cur * 2]);
                if (cross < 0f) next = i;
            }
            cur = next;
        } while (cur != start && n < 8);
        return n;
    }

    /**
     * A hammer as a sign, drawn flat on a fine layer: the same parts seen from above, the head
     * across the handle, turned so the head is up and to the right, so long overall; in its own
     * colours (one below 0), or all in one colour given (a sign on a key, a shadow).
     */
    static void icon(Ink g, float cx, float cy, float length, int kind, int one, float cover) {
        float[][] parts = parts(kind);
        int[] col = colours(kind);
        float s = length / 0.88f;
        float a = (float) Math.toRadians(52);
        float dx = (float) Math.cos(a), dy = -(float) Math.sin(a);   // along the handle, toward the head
        float zx = -dy, zy = dx;                                       // across it, along the head
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            float mx = cx + (p[0] * dx + p[2] * zx) * s, my = cy + (p[0] * dy + p[2] * zy) * s;
            float hx = p[3] * s, hz = p[5] * s;
            g.shape(mx - dx * hx - zx * hz, my - dy * hx - zy * hz, mx + dx * hx - zx * hz, my + dy * hx - zy * hz,
                    mx + dx * hx + zx * hz, my + dy * hx + zy * hz, mx - dx * hx + zx * hz, my - dy * hx + zy * hz);
            int c = 0xFF000000 | (one >= 0 ? one : col[i]);
            int low = one >= 0 ? c : 0xFF000000 | Street.scale(col[i], 0.72f);
            g.polyFill(c, low, 1f, cover);
        }
    }

    /** The pose of a body, as a hammer pose. */
    static void of(Body b, float[] pose) {
        pose[0] = b.px; pose[1] = b.py; pose[2] = b.pz;
        pose[3] = b.r00; pose[4] = b.r01; pose[5] = b.r02;
        pose[6] = b.r10; pose[7] = b.r11; pose[8] = b.r12;
        pose[9] = b.r20; pose[10] = b.r21; pose[11] = b.r22;
    }

    /**
     * Held near the eye: at a point given in the eye's own space (right, up, ahead), its
     * handle along one such direction, the faces of its head across another.
     */
    static void held(DrawList d, float vx, float vy, float vz, float ux, float uy, float uz, float wx, float wy, float wz,
                     float[] pose) {
        float[] r = d.right, up = d.up, a = d.ahead;
        for (int k = 0; k < 3; k++) pose[k] = d.eye[k] + r[k] * vx + up[k] * vy + a[k] * vz;
        float ax = r[0] * ux + up[0] * uy + a[0] * uz, ay = r[1] * ux + up[1] * uy + a[1] * uz, az = r[2] * ux + up[2] * uy + a[2] * uz;
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= l;
        ay /= l;
        az /= l;
        float zx = r[0] * wx + up[0] * wy + a[0] * wz, zy = r[1] * wx + up[1] * wy + a[1] * wz, zz = r[2] * wx + up[2] * wy + a[2] * wz;
        float dd = zx * ax + zy * ay + zz * az;
        zx -= dd * ax;
        zy -= dd * ay;
        zz -= dd * az;
        l = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        zx /= l;
        zy /= l;
        zz /= l;
        pose[3] = ax;
        pose[6] = ay;
        pose[9] = az;
        pose[4] = zy * az - zz * ay;
        pose[7] = zz * ax - zx * az;
        pose[10] = zx * ay - zy * ax;
        pose[5] = zx;
        pose[8] = zy;
        pose[11] = zz;
    }
}
