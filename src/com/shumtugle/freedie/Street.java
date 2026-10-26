package com.shumtugle.freedie;

import java.util.Random;

/**
 * The street the game is played on, on a summer evening by the sea, built once from boxes.
 * To the left a promenade with two rows of palms, a stone parapet and the sea beyond it; to
 * the right a row of old seaside houses, pastel, with shutters, iron balconies and awnings
 * over the shops; lamp posts and bollards along the curbs, one post with a call box. The
 * road is 7.5 m wide, painted with a zebra, its yellow bars and a dashed middle line.
 *
 * Space as in DrawList: x to the right, y up, z away from the eye at rest; the road at y = 0.
 */
final class Street {
    static final float ROAD = 3.75f, CURB_IN = 4.05f, CURB_OUT = 4.3f, CURB_TOP = 0.15f, WALK_TOP = 0.14f;
    static final float HOUSES_X = 12.5f, PARAPET_X = -14.6f;
    static final float LAMP_X = 4.55f;
    // the call box on the lamp post to the right, and its button
    static final float CALL_X = LAMP_X - 0.2f, CALL_Y = 1.45f + WALK_TOP, CALL_Z = 16f;

    static final int[] WALLS = {0xE9A684, 0xEAC27C, 0xF0E0C2, 0xDB8D68, 0xF1DAA2, 0xE6B9A2, 0xD8C9A8};

    private static final float[] T = new float[9];
    private static final float[] X = new float[3], Y = new float[3], Z = new float[3];

    private Street() {
    }

    /** Everything that stands still, into the list. */
    static void build(DrawList d) {
        build(d, null);
    }

    /** The street, and its own things that cannot be walked through, laid down in the solids. */
    static void build(DrawList d, Solid solid) {
        ground(d);
        houses(d, new Random(5));
        town(d, new Random(13));
        props(d, new Random(11), solid);
        palms(d, new Random(7), solid);
    }

    // ---- the ground ----

    private static void ground(DrawList d) {
        float z0 = -40f, z1 = 244f, zc = (z0 + z1) * 0.5f, zh = (z1 - z0) * 0.5f;
        // the road, its paint worked out by the shader from where each pixel lies
        d.box(0f, -0.25f, zc, CURB_IN, 0.25f, zh, null, 0x6E6862, DrawList.ROAD);
        for (int s = -1; s <= 1; s += 2)
            d.box(s * (CURB_IN + CURB_OUT) * 0.5f, (CURB_TOP - 0.25f) * 0.5f, zc,
                    (CURB_OUT - CURB_IN) * 0.5f, (CURB_TOP + 0.25f) * 0.5f, zh, null, 0xC9BFAE, DrawList.STONE);
        // the pavement in front of the houses, square slabs
        float wx0 = CURB_OUT, wx1 = HOUSES_X + 0.2f;
        d.box((wx0 + wx1) * 0.5f, (WALK_TOP - 0.25f) * 0.5f, zc, (wx1 - wx0) * 0.5f, (WALK_TOP + 0.25f) * 0.5f, zh,
                null, 0xCDBFA6, DrawList.PAVING);
        // the promenade, big pale stone; it ends at the parapet above the sea
        float px0 = PARAPET_X + 0.3f, px1 = -CURB_OUT;
        d.box((px0 + px1) * 0.5f, (WALK_TOP - 1.5f) * 0.5f, zc, (px1 - px0) * 0.5f, (WALK_TOP + 1.5f) * 0.5f, zh,
                null, 0xDCCDB2, DrawList.PROMENADE);
        d.box(PARAPET_X, -0.3f, zc, 0.3f, 1.2f, zh, null, 0xE2D4BA, DrawList.STONE);
        d.box(PARAPET_X, 0.95f, zc, 0.38f, 0.06f, zh, null, 0xEDE2CC, DrawList.PLAIN);
        // the sea, out to the haze
        d.box(-215f, -0.85f, 150f, 200f, 0.25f, 300f, null, 0x2D6990, DrawList.SEA);
    }

    // ---- houses ----

    private static void houses(DrawList d, Random rnd) {
        float z = -12f;
        int n = 0;
        while (z < 230f) {
            int bays = 3 + rnd.nextInt(3);
            float len = bays * 2.9f;
            int storeys = 4 + rnd.nextInt(2);
            float height = 3.3f * (storeys + 1) + 0.5f;
            int tint = WALLS[rnd.nextInt(WALLS.length)];
            int seed = n++;
            d.box(HOUSES_X + 7f, height * 0.5f, z + len * 0.5f, 7f, height * 0.5f, len * 0.5f, null, tint,
                    DrawList.material(DrawList.FACADE, seed));
            // the cornice that crowns each house
            d.box(HOUSES_X - 0.15f, height - 0.2f, z + len * 0.5f, 0.25f, 0.2f, len * 0.5f, null, scale(tint, 1.05f),
                    DrawList.PLAIN);
            if (z < 150f) fronts(d, z, bays, storeys, seed);
            roof(d, HOUSES_X + 7f, height, z + len * 0.5f, 7f, len * 0.5f, seed);
            z += len;
        }
        // the far end of the street
        d.box(-2f, 10f, 250f, 30f, 10f, 6f, null, 0xEBD2AC, DrawList.material(DrawList.FACADE, 77));
    }

    /** The town behind the street, seen from above as the eye comes down: blocks of houses, their roofs. */
    private static void town(DrawList d, Random rnd) {
        d.box(70f, -0.25f, 100f, 44f, 0.25f, 160f, null, 0x8A847C, DrawList.ASPHALT);
        int k = 0;
        for (int row = 0; row < 4; row++) {
            float x0 = 33f + row * 21f;
            float z = -40f + rnd.nextFloat() * 6f;
            while (z < 240f) {
                int bays = 3 + rnd.nextInt(3);
                float len = bays * 2.9f;
                int storeys = 3 + rnd.nextInt(4);
                float height = 3.3f * (storeys + 1) + 0.5f;
                int tint = WALLS[rnd.nextInt(WALLS.length)];
                int seed = 100 + k++;
                d.box(x0 + 7f, height * 0.5f, z + len * 0.5f, 7f, height * 0.5f, len * 0.5f, null, tint,
                        DrawList.material(DrawList.FACADE, seed));
                roof(d, x0 + 7f, height, z + len * 0.5f, 7f, len * 0.5f, seed);
                // now and then a lane between the houses
                z += len + (rnd.nextInt(6) == 0 ? 7f : 0f);
            }
        }
    }

    /** A roof of red tiles in two steps, a chimney or two. */
    static void roof(DrawList d, float x, float top, float z, float hx, float hz, int seed) {
        d.box(x, top + 0.25f, z, hx - 0.3f, 0.25f, hz - 0.3f, null, 0xC4673E, DrawList.GRAIN);
        d.box(x, top + 0.6f, z, hx - 1.3f, 0.15f, Math.max(0.4f, hz - 1.3f), null, 0xA8553A, DrawList.GRAIN);
        int h = nz(seed, 5, 21);
        for (int c = 0; c <= (h & 1); c++) {
            float cx = x + ((nz(seed, c, 22) / 255f) - 0.5f) * hx;
            float cz = z + ((nz(seed, c, 23) / 255f) - 0.5f) * hz;
            d.box(cx, top + 1.1f, cz, 0.3f, 0.6f, 0.3f, null, 0xBE9C84, DrawList.STONE);
        }
    }

    /** What stands out of a house front: balconies with railings and pots, awnings over the shops. */
    private static void fronts(DrawList d, float z0, int bays, int storeys, int seed) {
        fronts(d, false, HOUSES_X, -1, z0, bays, storeys, seed);
    }

    /**
     * The same on any wall standing square to x or z: the wall runs along x (alongX) or z,
     * stands at the coordinate wall, faces out to the plus or minus side; its bays begin at
     * start, the lower end along it, as the shader counts them.
     */
    static void fronts(DrawList d, boolean alongX, float wall, int out, float start, int bays, int storeys, int seed) {
        for (int b = 0; b < bays; b++) {
            float c = start + b * 2.9f + 1.45f;
            int h0 = nz(b, 0, seed);
            if ((h0 & 3) != 0) {
                boolean stripes = (h0 & 4) != 0;
                int cloth = (h0 & 8) != 0 ? 0xB5523E : 0x4F7A5E;
                awning(d, alongX, wall, out, 2.75f, c, 1.15f, cloth, stripes);
            }
            for (int s = 1; s <= storeys; s++) {
                int h = nz(b, s, seed);
                if ((h & 32) == 0 && s != 1) continue;
                float y = s * 3.3f;
                // the slab, and the railing round it
                put(d, alongX, c, y + 0.28f, wall + out * 0.25f, 0.75f, 0.06f, 0.25f, 0x5C5450, DrawList.PLAIN);
                put(d, alongX, c, y + 0.8f, wall + out * 0.49f, 0.75f, 0.46f, 0.015f, 0x2B2A2C, DrawList.RAIL);
                for (int e = -1; e <= 1; e += 2)
                    put(d, alongX, c + e * 0.735f, y + 0.8f, wall + out * 0.25f, 0.015f, 0.46f, 0.24f, 0x2B2A2C, DrawList.RAIL);
                if ((h & 64) != 0) {
                    // flower pots behind the bars
                    for (int p = -1; p <= 1; p++) {
                        int f = nz(p + 7, s, seed + 11);
                        int col = f < 90 ? 0x4D7A3A : f < 150 ? 0xD9505A : 0x6E9440;
                        put(d, alongX, c + p * 0.45f, y + 0.46f, wall + out * 0.25f, 0.1f, 0.12f, 0.1f, col, DrawList.GRAIN);
                    }
                }
            }
        }
    }

    /** A box square to a wall: so far along it, so high, so far out; its half sizes the same way. */
    private static void put(DrawList d, boolean alongX, float along, float y, float out, float hAlong, float hy, float hOut,
                            int rgb, int material) {
        if (alongX) d.box(along, y, out, hAlong, hy, hOut, null, rgb, material);
        else d.box(out, y, along, hOut, hy, hAlong, null, rgb, material);
    }

    /** An awning out from the wall over a shop window, sloping down, with a flap along its edge. */
    private static void awning(DrawList d, boolean alongX, float wall, int out, float top, float c, float half,
                               int cloth, boolean stripes) {
        float a = (float) Math.atan2(0.4, 1.1), ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float len = 0.6f;
        // the way out from the wall, and the way along it that makes the turn right-handed
        float ox = alongX ? 0f : out, oz = alongX ? out : 0f;
        if (alongX) set(X, out, 0f, 0f);
        else set(X, 0f, 0f, -out);
        set(Z, ox * ca, -sa, oz * ca);
        cross(Z, X, Y);
        axes();
        float mat = DrawList.material(DrawList.AWNING, stripes ? 1 : 0);
        float wx = alongX ? c : wall, wz = alongX ? wall : c;
        d.box(wx + ox * len * ca, top - len * sa, wz + oz * len * ca, half, 0.02f, len, T, cloth, mat);
        if (alongX) set(X, out, 0f, 0f);
        else set(X, 0f, 0f, -out);
        set(Y, 0f, 1f, 0f);
        set(Z, ox, 0f, oz);
        axes();
        d.box(wx + ox * 2f * len * ca, top - 2f * len * sa - 0.12f, wz + oz * 2f * len * ca, half, 0.12f, 0.01f, T, cloth, mat);
    }

    // ---- lamps, bollards, parasols ----

    private static void props(DrawList d, Random rnd, Solid solid) {
        for (float z = -12f; z < 120f; z += 14f) {
            for (int side = -1; side <= 1; side += 2) {
                float x = side * LAMP_X, y = WALK_TOP;
                if (solid != null) solid.fixed(x, z, 0.12f);
                d.box(x, y + 2.1f, z, 0.06f, 2.1f, 0.06f, null, 0x2E2D2F, DrawList.PLAIN);
                d.box(x, y + 4.18f, z, 0.1f, 0.04f, 0.1f, null, 0x3A3836, DrawList.PLAIN);
                d.box(x, y + 4.4f, z, 0.13f, 0.18f, 0.13f, null, 0xF3E2B4, DrawList.PLAIN);
                d.box(x, y + 4.62f, z, 0.2f, 0.05f, 0.2f, null, 0x3A3836, DrawList.PLAIN);
                // the call box on one post: a grey case facing the street; its button is the world's
                if (side == 1 && z == CALL_Z)
                    d.box(x - 0.12f, CALL_Y, z, 0.07f, 0.26f, 0.17f, null, 0x8E959C, DrawList.PLAIN);
            }
            if (z < 0f) continue;
            for (int b = 1; b < 4; b++) {
                if (solid != null) solid.fixed(4.45f, z + b * 3.2f, 0.12f);
                d.box(4.45f, WALK_TOP + 0.4f, z + b * 3.2f, 0.08f, 0.4f, 0.08f, null, 0x2E2D2F, DrawList.PLAIN);
            }
        }
        for (float z = 6f; z < 90f; z += 9f + rnd.nextFloat() * 8f) {
            if (rnd.nextInt(3) == 0) continue;
            float x = 10.3f + rnd.nextFloat() * 0.8f, y = WALK_TOP;
            // a parasol over a table: walked round, not under
            if (solid != null) solid.fixed(x, z, 0.55f);
            int cloth = rnd.nextBoolean() ? 0xF3EDE0 : 0xE4D5B6;
            d.box(x, y + 1.2f, z, 0.04f, 1.2f, 0.04f, null, 0x4A4542, DrawList.PLAIN);
            d.box(x, y + 2.35f, z, 1.2f, 0.12f, 1.2f, null, cloth, DrawList.PLAIN);
            d.box(x, y + 2.5f, z, 0.8f, 0.06f, 0.8f, null, cloth, DrawList.PLAIN);
            // a table under it
            d.box(x, y + 0.38f, z, 0.35f, 0.38f, 0.35f, null, 0x6A5A4C, DrawList.WOOD);
        }
    }

    // ---- palms ----

    static final float[] PALM_ROWS = {5.6f, -5.6f, -10.1f};
    private static final int[] GREENS = {0x3F5F22, 0x4F6F28, 0x5E7E2E, 0x6C8434};

    private static void palms(DrawList d, Random rnd, Solid solid) {
        for (int i = 13; i >= 0; i--)
            for (float rx : PALM_ROWS) {
                float x = rx + (rnd.nextFloat() - 0.5f) * 0.3f;
                float z = 7f + i * 7.5f + (rx < -8f ? 3.7f : 0f) + (rnd.nextFloat() - 0.5f) * 0.6f;
                float height = 7.2f + rnd.nextFloat() * 2.6f;
                float lean = (rnd.nextFloat() - 0.5f) * 0.6f;
                // a bed of earth round its foot, which nobody walks across
                if (solid != null) solid.fixed(x, z, 0.65f);
                d.box(x, WALK_TOP + 0.01f, z, 0.7f, 0.01f, 0.6f, null, 0x8C6E4C, DrawList.GRAIN);
                palm(d, rnd, x, WALK_TOP, z, height, lean, z > 60f);
            }
    }

    /** One palm: a trunk of blocks bending with its lean, a crown of fronds, a few dry ones hanging. */
    static void palm(DrawList d, Random rnd, float x, float y0, float z, float height, float lean, boolean far) {
        int segs = 7;
        float spin = rnd.nextFloat() * 6.283f;
        for (int j = 0; j < segs; j++) {
            float t0 = j / (float) segs, t1 = (j + 1) / (float) segs, tm = (t0 + t1) * 0.5f;
            float ax = x + lean * t0 * t0, ay = y0 + height * t0;
            float bx = x + lean * t1 * t1, by = y0 + height * t1;
            float len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
            set(Y, (bx - ax) / len, (by - ay) / len, 0f);
            set(X, (float) Math.cos(spin), 0f, (float) Math.sin(spin));
            float k = dot(X, Y);
            set(X, X[0] - Y[0] * k, X[1] - Y[1] * k, X[2] - Y[2] * k);
            norm(X);
            cross(X, Y, Z);
            axes();
            float r = (0.28f - 0.08f * tm) * (j == 0 ? 1.2f : 1f);
            d.box((ax + bx) * 0.5f, (ay + by) * 0.5f, z, r, len * 0.5f + 0.03f, r, T, 0x5E4A38,
                    DrawList.material(DrawList.TRUNK, j * 37));
        }
        float tx = x + lean, ty = y0 + height;
        int parts = far ? 2 : 3;
        int dry = 2 + rnd.nextInt(3);
        for (int i = 0; i < dry; i++) {
            float a = rnd.nextFloat() * 6.283f;
            frond(d, tx, ty - 0.15f, z, a, 1.5f + rnd.nextFloat() * 0.5f, -1.1f, 0x5A4630, 0x7A6242, parts);
        }
        int n = (far ? 12 : 15) + rnd.nextInt(5);
        for (int i = 0; i < n; i++) {
            float a = (float) (i * Math.PI * 2 / n + rnd.nextFloat() * 0.3f);
            float lift = 0.2f + rnd.nextFloat() * 0.5f;
            int g = GREENS[rnd.nextInt(GREENS.length)];
            int tip = rnd.nextInt(5) == 0 ? 0x9C8A48 : g;
            frond(d, tx, ty, z, a, 2.4f + rnd.nextFloat() * 0.9f, lift, g, tip, parts);
        }
    }

    /**
     * One frond: a spine drooping as it goes, leaflets either side, falling away from it like
     * a roof; each part of it is two boxes, one for each side, their spine along one edge.
     */
    private static void frond(DrawList d, float x, float y, float z, float angle, float len, float lift,
                              int c0, int c1, int parts) {
        float dx = (float) Math.cos(angle), dz = (float) Math.sin(angle);
        float droop = 0.5f, cd = (float) Math.cos(droop), sd = (float) Math.sin(droop);
        for (int i = 0; i < parts; i++) {
            float t0 = i / (float) parts, t1 = (i + 1) / (float) parts, tm = (t0 + t1) * 0.5f;
            float ax = x + dx * len * t0, ay = y + len * (lift * t0 - 0.9f * t0 * t0), az = z + dz * len * t0;
            float bx = x + dx * len * t1, by = y + len * (lift * t1 - 0.9f * t1 * t1), bz = z + dz * len * t1;
            float sx = bx - ax, sy = by - ay, sz = bz - az;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            float ux = sx / sl, uy = sy / sl, uz = sz / sl;
            // the leaflets' reach either side, widest in the middle of the frond
            float w = 0.09f + 0.34f * (float) Math.sin(Math.PI * Math.min(1f, tm * 1.15f));
            int color = mix(c0, c1, tm);
            float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
            // across, level, and up, square to the spine
            float px = -uz, pz = ux;
            float pl = (float) Math.sqrt(px * px + pz * pz);
            px /= pl;
            pz /= pl;
            // q = p x u, the frond's own up
            float qx = -pz * uy, qy = pz * ux - px * uz, qz = px * uy;
            if (qy < 0f) {
                qx = -qx;
                qy = -qy;
                qz = -qz;
            }
            for (int side = -1; side <= 1; side += 2) {
                // out from the spine and down a little
                float lx = px * side * cd - qx * sd, ly = -qy * sd, lz = pz * side * cd - qz * sd;
                set(X, ux, uy, uz);
                set(Z, lx, ly, lz);
                norm(Z);
                cross(Z, X, Y);
                axes();
                float h = w * 0.5f;
                d.box(mx + Z[0] * h, my + Z[1] * h, mz + Z[2] * h, sl * 0.5f + 0.02f, 0.012f, h, T, color,
                        DrawList.FROND);
            }
        }
    }

    // ---- helpers ----

    /** The integer hash the shader uses too: both agree on every window, balcony and awning. */
    static int nz(int a, int b, int seed) {
        int h = a * 374761393 + b * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0xFF;
    }

    private static void set(float[] v, float x, float y, float z) {
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void norm(float[] v) {
        float l = (float) Math.sqrt(dot(v, v));
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    private static void cross(float[] a, float[] b, float[] out) {
        float x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /** The turn whose columns are X, Y and Z, row by row, as DrawList.box takes it. */
    private static void axes() {
        T[0] = X[0]; T[1] = Y[0]; T[2] = Z[0];
        T[3] = X[1]; T[4] = Y[1]; T[5] = Z[1];
        T[6] = X[2]; T[7] = Y[2]; T[8] = Z[2];
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int scale(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((c & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }
}
