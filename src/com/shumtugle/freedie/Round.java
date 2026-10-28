package com.shumtugle.freedie;

/**
 * Round things out of boxes. Six thin plates turned about one axis, each a twelfth of a turn
 * from the last, make together exactly a twelve-sided prism: a plate as wide as the prism and
 * as thick as one of its sides covers what lies within a side either way of its own facing.
 * Cut to the frame's coarse pixels, twelve sides read as round.
 */
final class Round {
    static final int SIDES = 12;
    private static final int PLATES = SIDES / 2;
    private static final float THIN = (float) Math.tan(Math.PI / SIDES);
    private static final float[] COS = new float[PLATES], SIN = new float[PLATES];
    private static final float[] M = new float[9];
    private static final float[] UPRIGHT = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    static {
        for (int k = 0; k < PLATES; k++) {
            COS[k] = (float) Math.cos(k * Math.PI * 2 / SIDES);
            SIN[k] = (float) Math.sin(k * Math.PI * 2 / SIDES);
        }
    }

    private Round() {
    }

    /**
     * A prism about the y axis of a frame: rot is the frame's turn row by row (null: upright),
     * (x, y, z) its middle; the prism runs from y0 to y1 along that axis, r to the middle of a side.
     */
    static void cylinder(DrawList d, float x, float y, float z, float[] rot, float y0, float y1, float r,
                         int rgb, int material) {
        float[] R = rot == null ? UPRIGHT : rot;
        float ym = (y0 + y1) * 0.5f, hh = (y1 - y0) * 0.5f;
        float cx = x + R[1] * ym, cy = y + R[4] * ym, cz = z + R[7] * ym;
        for (int k = 0; k < PLATES; k++) {
            float c = COS[k], s = SIN[k];
            // the plate's own axes in the frame: across (c, 0, s), up, and its thin way (-s, 0, c)
            for (int i = 0; i < 3; i++) {
                float a = R[i * 3], b = R[i * 3 + 1], e = R[i * 3 + 2];
                M[i * 3] = a * c + e * s;
                M[i * 3 + 1] = b;
                M[i * 3 + 2] = -a * s + e * c;
            }
            d.box(cx, cy, cz, r, hh, r * THIN, M, rgb, material);
        }
    }

    private static final float[] M2 = new float[9];

    /**
     * A ring about the y axis of a frame, from radius rIn to rOut, from y0 to y1: twelve
     * blocks round it, each turned to face out, a little overlapping its neighbours.
     */
    static void ring(DrawList d, float x, float y, float z, float[] rot, float y0, float y1, float rIn, float rOut,
                     int rgb, int material) {
        float[] R = rot == null ? UPRIGHT : rot;
        float ym = (y0 + y1) * 0.5f, hh = (y1 - y0) * 0.5f, rm = (rIn + rOut) * 0.5f;
        float along = rOut * THIN * 1.08f;
        for (int k = 0; k < SIDES; k++) {
            double a = k * Math.PI * 2 / SIDES;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            // in the frame: out (c, 0, s), up, along the ring (-s, 0, c)
            for (int i = 0; i < 3; i++) {
                float p = R[i * 3], q = R[i * 3 + 1], e = R[i * 3 + 2];
                M2[i * 3] = p * c + e * s;
                M2[i * 3 + 1] = q;
                M2[i * 3 + 2] = -p * s + e * c;
            }
            float lx = c * rm, lz = s * rm;
            d.box(x + R[0] * lx + R[1] * ym + R[2] * lz, y + R[3] * lx + R[4] * ym + R[5] * lz,
                    z + R[6] * lx + R[7] * ym + R[8] * lz, (rOut - rIn) * 0.5f, hh, along, M2, rgb, material);
        }
    }

    /** A flat round lying on the ground or a top: a puddle. */
    static void disc(DrawList d, float x, float y, float z, float r, float half, int rgb, int material) {
        cylinder(d, x, y, z, null, -half, half, r, rgb, material);
    }

    // a road cone's bands, up its half height from -1 to 1: where each ends, its colour
    private static final float[] CONE_Y = {-0.8f, -0.12f, 0.12f, 0.36f, 0.56f, 1f};
    private static final int[] CONE_COL = {0xF0641E, 0xF4F2EC, 0xF0641E, 0xF4F2EC, 0xF0641E};

    /** A road cone on its square dark foot, banded orange and white, narrowing in steps. */
    static void cone(DrawList d, float x, float y, float z, float[] rot, float r, float hh) {
        float[] R = rot == null ? UPRIGHT : rot;
        float fy = -hh * 0.9f;
        d.box(x + R[1] * fy, y + R[4] * fy, z + R[7] * fy, r, hh * 0.1f, r, rot, 0x34302E, DrawList.PLAIN);
        for (int k = 0; k + 1 < CONE_Y.length; k++) {
            float mid = (CONE_Y[k] + CONE_Y[k + 1]) * 0.5f;
            // the radius at the middle of the band: full at the foot, a quarter at the tip
            float rr = r * (0.82f - 0.6f * (mid + 0.8f) / 1.8f);
            cylinder(d, x, y, z, rot, CONE_Y[k] * hh, CONE_Y[k + 1] * hh, rr, CONE_COL[k], DrawList.PAINT);
        }
    }

    // a water bottle, up its half height from -1 to 1: the water in it up to here, the cap from here
    private static final float WATER_TOP = 0.38f, CAP0 = 0.8f;

    /**
     * A plastic water bottle standing on its base: clear pale blue plastic (drawn last, over
     * what is behind it), the water in it up to the shoulder, a blue cap. The water is solid:
     * it is what casts the bottle's shadow.
     */
    static void bottle(DrawList d, float x, float y, float z, float[] rot, float r, float hh) {
        float[] R = rot == null ? UPRIGHT : rot;
        cylinder(d, x, y, z, rot, -0.95f * hh, WATER_TOP * hh, r * 0.93f, 0x4F8FC4, DrawList.GLASS);
        cylinder(d, x, y, z, rot, CAP0 * hh, hh, r * 0.42f, 0x2F7FD0, DrawList.PAINT);
        d.ellipsoid(x, y, z, r, hh, r, R, 0xA8D6F2, DrawList.CLEAR);
    }
}
