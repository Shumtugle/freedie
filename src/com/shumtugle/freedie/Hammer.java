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
    // the gift of the gods: a dark wrought head, broader and deeper, bound in bronze, on a stout handle
    static final float[][] HEAVY = {
            {-0.1f, 0f, 0f, 0.31f, 0.038f, 0.038f},
            {-0.36f, 0f, 0f, 0.07f, 0.044f, 0.044f},
            {0.31f, 0f, 0f, 0.13f, 0.1f, 0.2f},
            {0.31f, 0f, 0.15f, 0.134f, 0.104f, 0.02f},
            {0.31f, 0f, -0.15f, 0.134f, 0.104f, 0.02f},
    };
    private static final int[] HEAVY_COL = {0x6E4A2C, 0x3E2618, 0x3F3D44, 0xA8783A, 0xA8783A};
    private static final int[] HEAVY_MAT = {DrawList.WOOD, DrawList.PLAIN, DrawList.STONE, DrawList.PAINT, DrawList.PAINT};

    private static final float[] T = new float[9];

    private Hammer() {
    }

    static void draw(DrawList d, float[] pose) {
        draw(d, pose, false, 1f);
    }

    /** The plain hammer or the heavy one, so big. */
    static void draw(DrawList d, float[] pose, boolean heavy, float scale) {
        float[][] parts = heavy ? HEAVY : PARTS;
        int[] col = heavy ? HEAVY_COL : COL, mat = heavy ? HEAVY_MAT : MAT;
        T[0] = pose[3]; T[1] = pose[4]; T[2] = pose[5];
        T[3] = pose[6]; T[4] = pose[7]; T[5] = pose[8];
        T[6] = pose[9]; T[7] = pose[10]; T[8] = pose[11];
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            float ox = p[0] * scale, oy = p[1] * scale, oz = p[2] * scale;
            float cx = pose[0] + T[0] * ox + T[1] * oy + T[2] * oz;
            float cy = pose[1] + T[3] * ox + T[4] * oy + T[5] * oz;
            float cz = pose[2] + T[6] * ox + T[7] * oy + T[8] * oz;
            d.box(cx, cy, cz, p[3] * scale, p[4] * scale, p[5] * scale, T, col[i], mat[i]);
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
