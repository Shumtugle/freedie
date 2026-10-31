package com.shumtugle.freedie;

/**
 * Her mallet: a big head of pale planks, two laid along the top and bottom and their seams
 * showing, bound round near either end with a band of iron; a square handle of darker wood
 * through it, and on the handle's end a cap of red-brown wood, a little thicker. Its own frame:
 * the handle along x, the grip at the origin, the head out at +x; the head's length along z.
 */
final class Mallet {
    // the parts: middle along x, y, z; half sizes; color (meters, at size 1)
    private static final float[][] PARTS = {
            {-0.07f, 0f, 0f, 0.075f, 0.05f, 0.05f},          // the cap on the handle's end
            {0.28f, 0f, 0f, 0.28f, 0.035f, 0.035f},          // the handle
            {0.69f, 0.05f, 0f, 0.11f, 0.05f, 0.21f},         // the head: the top plank
            {0.69f, -0.05f, 0f, 0.11f, 0.05f, 0.21f},        // the bottom plank
            {0.69f, 0f, 0.125f, 0.116f, 0.106f, 0.022f},     // the iron bands
            {0.69f, 0f, -0.125f, 0.116f, 0.106f, 0.022f},
            {0.582f, 0f, 0f, 0.004f, 0.036f, 0.036f},        // the handle's end showing through the head
    };
    private static final int[] COL = {0x8C4A2C, 0xC6894A, 0xE2B270, 0xD9A563, 0x55585F, 0x55585F, 0xB0773E};
    private static final float[] T = new float[9];

    private Mallet() {
    }

    /**
     * Draws it turned by rot (rows; columns are its own x, y, z in the world), size times, the
     * point at pivot along its handle put at (x, y, z).
     */
    static void draw(DrawList d, float[] rot, float x, float y, float z, float size, float pivot) {
        System.arraycopy(rot, 0, T, 0, 9);
        for (int i = 0; i < PARTS.length; i++) {
            float[] p = PARTS[i];
            float ox = (p[0] - pivot) * size, oy = p[1] * size, oz = p[2] * size;
            float cx = x + T[0] * ox + T[1] * oy + T[2] * oz;
            float cy = y + T[3] * ox + T[4] * oy + T[5] * oz;
            float cz = z + T[6] * ox + T[7] * oy + T[8] * oz;
            int material = i == 4 || i == 5 ? DrawList.PLAIN : DrawList.WOOD;
            d.box(cx, cy, cz, p[3] * size, p[4] * size, p[5] * size, T, COL[i], material);
        }
    }
}
