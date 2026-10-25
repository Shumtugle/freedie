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

    /** A flat round lying on the ground or a top: a puddle. */
    static void disc(DrawList d, float x, float y, float z, float r, float half, int rgb, int material) {
        cylinder(d, x, y, z, null, -half, half, r, rgb, material);
    }

    // a water bottle's profile, up its half height from -1 to 1: where each part ends, and its radius
    private static final float[] BOTTLE_Y = {-1f, 0.42f, 0.58f, 0.74f, 0.8f, 1f};
    private static final float[] BOTTLE_R = {1f, 0.8f, 0.56f, 0.36f, 0.42f};

    /** A plastic water bottle standing on its base: pale blue, a blue cap. */
    static void bottle(DrawList d, float x, float y, float z, float[] rot, float r, float hh) {
        for (int k = 0; k + 1 < BOTTLE_Y.length; k++) {
            boolean cap = k == BOTTLE_R.length - 1;
            cylinder(d, x, y, z, rot, BOTTLE_Y[k] * hh, BOTTLE_Y[k + 1] * hh, BOTTLE_R[k] * r,
                    cap ? 0x2F7FD0 : 0x9FD0F0, cap ? DrawList.PAINT : DrawList.GLASS);
        }
    }
}
