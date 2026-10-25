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

    private static final float[] T = new float[9];

    private Hammer() {
    }

    static void draw(DrawList d, float[] pose) {
        T[0] = pose[3]; T[1] = pose[4]; T[2] = pose[5];
        T[3] = pose[6]; T[4] = pose[7]; T[5] = pose[8];
        T[6] = pose[9]; T[7] = pose[10]; T[8] = pose[11];
        for (int i = 0; i < PARTS.length; i++) {
            float[] p = PARTS[i];
            float cx = pose[0] + T[0] * p[0] + T[1] * p[1] + T[2] * p[2];
            float cy = pose[1] + T[3] * p[0] + T[4] * p[1] + T[5] * p[2];
            float cz = pose[2] + T[6] * p[0] + T[7] * p[1] + T[8] * p[2];
            d.box(cx, cy, cz, p[3], p[4], p[5], T, COL[i], MAT[i]);
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
