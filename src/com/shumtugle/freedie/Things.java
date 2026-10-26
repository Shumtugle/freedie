package com.shumtugle.freedie;

/**
 * The street things the buildings are made of from the second episode on, out of boxes:
 * steel drums, car tyres, sandbags, old suitcases, spring mattresses, heavy balls; and the
 * light things that fly out of them: shirts, postcards, springs.
 *
 * Each is drawn at a body's pose: a middle, a turn row by row, and the body's half sizes.
 */
final class Things {
    private Things() {
    }

    // the pose being drawn: its middle and its turn
    private static float ox, oy, oz;
    private static float[] R;
    private static final float[] M = new float[9];

    private static void at(float x, float y, float z, float[] rot) {
        ox = x;
        oy = y;
        oz = z;
        R = rot;
    }

    /** A box in the thing's own frame. */
    private static void part(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, int rgb, int material) {
        d.box(ox + R[0] * lx + R[1] * ly + R[2] * lz, oy + R[3] * lx + R[4] * ly + R[5] * lz,
                oz + R[6] * lx + R[7] * ly + R[8] * lz, hx, hy, hz, R, rgb, material);
    }

    /** A box in the thing's own frame, turned about the thing's x axis by a, round its own middle. */
    private static void tilted(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, float a, int rgb,
                               int material) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int i = 0; i < 3; i++) {
            float p = R[i * 3], q = R[i * 3 + 1], e = R[i * 3 + 2];
            M[i * 3] = p;
            M[i * 3 + 1] = q * c + e * s;
            M[i * 3 + 2] = -q * s + e * c;
        }
        d.box(ox + R[0] * lx + R[1] * ly + R[2] * lz, oy + R[3] * lx + R[4] * ly + R[5] * lz,
                oz + R[6] * lx + R[7] * ly + R[8] * lz, hx, hy, hz, M, rgb, material);
    }

    /** A box in the thing's own frame, turned about the thing's z axis by a, round its own middle. */
    private static void turnedZ(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, float a, int rgb,
                                int material) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int i = 0; i < 3; i++) {
            float p = R[i * 3], q = R[i * 3 + 1], e = R[i * 3 + 2];
            M[i * 3] = p * c + q * s;
            M[i * 3 + 1] = -p * s + q * c;
            M[i * 3 + 2] = e;
        }
        d.box(ox + R[0] * lx + R[1] * ly + R[2] * lz, oy + R[3] * lx + R[4] * ly + R[5] * lz,
                oz + R[6] * lx + R[7] * ly + R[8] * lz, hx, hy, hz, M, rgb, material);
    }

    // ---- out at sea: a life ring and a ship's wheel, standing on edge, facing the ice ----

    private static final float SEG = (float) Math.tan(Math.PI / 12) * 1.12f;

    /** A life ring as big as its block: red and white quarters round it, a rope looped along its outside. */
    static void lifeRing(DrawList d, float x, float y, float z, float[] rot, float r, float hz) {
        at(x, y, z, rot);
        float rIn = r * 0.52f, rOut = r * 0.96f, rm = (rIn + rOut) * 0.5f, half = (rOut - rIn) * 0.5f;
        for (int k = 0; k < 12; k++) {
            float a = (float) (k * Math.PI / 6), c = (float) Math.cos(a), sn = (float) Math.sin(a);
            int col = (k / 3) % 2 == 0 ? 0xD8322A : 0xF2EEE6;
            turnedZ(d, c * rm, sn * rm, 0f, half, rOut * SEG, hz * 0.8f, a, col, DrawList.PAINT);
        }
        // the rope: a dark line round the outside, caught to it at four points
        for (int k = 0; k < 12; k++) {
            float a = (float) ((k + 0.5) * Math.PI / 6), c = (float) Math.cos(a), sn = (float) Math.sin(a);
            turnedZ(d, c * r * 0.985f, sn * r * 0.985f, 0f, r * 0.03f, r * SEG * 0.9f, hz * 0.3f, a, 0x4A4038, DrawList.PLAIN);
        }
    }

    /** A ship's wheel as big as its block: a rim of dark wood, a hub, eight spokes running out past the rim into handles. */
    static void wheel(DrawList d, float x, float y, float z, float[] rot, float r, float hz, int wood) {
        at(x, y, z, rot);
        int dark = Street.scale(wood, 0.8f);
        float rIn = r * 0.6f, rOut = r * 0.76f, rm = (rIn + rOut) * 0.5f, half = (rOut - rIn) * 0.5f;
        for (int k = 0; k < 12; k++) {
            float a = (float) (k * Math.PI / 6);
            turnedZ(d, (float) Math.cos(a) * rm, (float) Math.sin(a) * rm, 0f, half, rOut * SEG, hz * 0.45f, a, wood, DrawList.WOOD);
        }
        for (int k = 0; k < 8; k++) {
            float a = (float) (k * Math.PI / 4 + Math.PI / 8), c = (float) Math.cos(a), sn = (float) Math.sin(a);
            // a spoke from the hub out through the rim, and a turned handle at its end
            turnedZ(d, c * r * 0.47f, sn * r * 0.47f, 0f, r * 0.42f, r * 0.045f, hz * 0.3f, a, dark, DrawList.WOOD);
            turnedZ(d, c * r * 0.93f, sn * r * 0.93f, 0f, r * 0.07f, r * 0.065f, hz * 0.3f, a, wood, DrawList.WOOD);
        }
        part(d, 0f, 0f, 0f, r * 0.16f, r * 0.16f, hz * 0.6f, dark, DrawList.WOOD);
        part(d, 0f, 0f, 0f, r * 0.07f, r * 0.07f, hz * 0.75f, 0xB08A3A, DrawList.GOLD);
    }

    // ---- a steel drum standing on end ----

    /** Painted steel, darker chimes at both ends, two rolling hoops, a lid a little down inside, a bung. */
    static void drum(DrawList d, float x, float y, float z, float[] rot, float r, float hh, int paint) {
        int rim = Street.scale(paint, 0.72f), hoop = Street.mix(paint, 0xFFFFFF, 0.12f), lid = Street.scale(paint, 0.86f);
        Round.cylinder(d, x, y, z, rot, -hh * 0.93f, hh * 0.93f, r, paint, DrawList.PAINT);
        Round.cylinder(d, x, y, z, rot, -hh, -hh * 0.93f, r * 0.98f, rim, DrawList.PAINT);
        Round.cylinder(d, x, y, z, rot, hh * 0.93f, hh, r * 0.98f, rim, DrawList.PAINT);
        for (int s = -1; s <= 1; s += 2)
            Round.cylinder(d, x, y, z, rot, s * hh * 0.4f - hh * 0.05f, s * hh * 0.4f + hh * 0.05f, r * 1.05f, hoop, DrawList.PAINT);
        Round.cylinder(d, x, y, z, rot, hh * 0.93f, hh * 0.96f, r * 0.86f, lid, DrawList.PAINT);
        at(x, y, z, rot);
        part(d, r * 0.5f, hh * 0.975f, r * 0.2f, r * 0.1f, hh * 0.03f, r * 0.1f, 0x3A3A3C, DrawList.PLAIN);
    }

    // ---- a car tyre lying flat ----

    /** The tread, a dark groove round its middle, the sidewalls round the hole. */
    static void tyre(DrawList d, float x, float y, float z, float[] rot, float r, float hh, int rubber) {
        int side = Street.scale(rubber, 1.25f), tread = Street.scale(rubber, 0.95f);
        Round.ring(d, x, y, z, rot, -hh, -hh * 0.15f, r * 0.52f, r, tread, DrawList.PLAIN);
        Round.ring(d, x, y, z, rot, hh * 0.15f, hh, r * 0.52f, r, tread, DrawList.PLAIN);
        Round.ring(d, x, y, z, rot, -hh * 0.15f, hh * 0.15f, r * 0.52f, r * 0.96f, 0x121213, DrawList.PLAIN);
        // the sidewalls, a shade lighter, a little in from the tread
        Round.ring(d, x, y, z, rot, hh * 0.98f, hh * 1.02f, r * 0.55f, r * 0.9f, side, DrawList.PLAIN);
        Round.ring(d, x, y, z, rot, -hh * 1.02f, -hh * 0.98f, r * 0.55f, r * 0.9f, side, DrawList.PLAIN);
    }

    // ---- a sandbag ----

    /** A box of sacking, rounded off in layers, tied at one end. */
    static void bag(DrawList d, float x, float y, float z, float[] rot, float hx, float hy, float hz, int c) {
        at(x, y, z, rot);
        part(d, 0f, 0f, 0f, hx, hy * 0.62f, hz, c, DrawList.GRAIN);
        part(d, 0f, 0f, 0f, hx * 0.92f, hy, hz * 0.84f, c, DrawList.GRAIN);
        part(d, 0f, 0f, 0f, hx * 0.97f, hy * 0.84f, hz * 0.95f, Street.scale(c, 1.04f), DrawList.GRAIN);
        // the end gathered and tied
        part(d, hx * 0.96f, 0f, 0f, hx * 0.05f, hy * 0.5f, hz * 0.5f, Street.scale(c, 0.8f), DrawList.GRAIN);
        part(d, hx * 1.02f, 0f, 0f, hx * 0.03f, hy * 0.25f, hz * 0.22f, 0x6A5638, DrawList.PLAIN);
    }

    // ---- an old suitcase lying flat ----

    /**
     * Leather, two straps across, brass corners and latches, a handle on the front, the seam
     * of the lid. Burst open (since: seconds ago, or below zero), its lid swings up on the hinge
     * along the back, the lining shows, and a shirt still lies in it.
     */
    static void suitcase(DrawList d, float x, float y, float z, float[] rot, float hx, float hy, float hz, int leather,
                         float since, int shirt) {
        at(x, y, z, rot);
        int strap = Street.scale(leather, 0.45f), brass = 0xC9A23A, lining = 0xE9D9B0, seam = Street.scale(leather, 0.7f);
        boolean open = since >= 0f;
        // the lower half reaches up to the seam; the lid sits on it
        float cut = hy * 0.1f, low = (cut + hy) * 0.5f, lowY = (cut - hy) * 0.5f, half = (hy - cut) * 0.5f;
        if (!open) {
            part(d, 0f, 0f, 0f, hx, hy, hz, leather, DrawList.PAINT);
            part(d, 0f, cut, 0f, hx * 1.006f, hy * 0.04f, hz * 1.006f, seam, DrawList.PLAIN);
        } else {
            part(d, 0f, lowY, 0f, hx, low, hz, leather, DrawList.PAINT);
            part(d, 0f, cut - 0.006f, 0f, hx * 0.94f, 0.006f, hz * 0.9f, lining, DrawList.PLAIN);
            part(d, -hx * 0.15f, cut + 0.004f, hz * 0.05f, hx * 0.55f, 0.008f, hz * 0.62f, shirt, DrawList.PLAIN);
            part(d, -hx * 0.15f, cut + 0.009f, -hz * 0.5f, hx * 0.12f, 0.004f, hz * 0.1f, 0xF4F2EC, DrawList.PLAIN);
            // the lid, turned about the hinge along the back edge, from shut to leaning back
            float a = Math.min(1.95f, since * 10f), c = (float) Math.cos(a), s = (float) Math.sin(a);
            // its middle, from the hinge at (cut, hz): (0, half, -hz) in the shut lid, turned
            float my = cut + half * c + hz * s, mz = hz + half * s - hz * c;
            tilted(d, 0f, my, mz, hx, half, hz, a, leather, DrawList.PAINT);
            // the lining on its inside face
            float iy = cut - 0.004f * c + hz * s, iz = hz - 0.004f * s - hz * c;
            tilted(d, 0f, iy, iz, hx * 0.94f, 0.005f, hz * 0.9f, a, lining, DrawList.PLAIN);
            for (int k = -1; k <= 1; k += 2) {
                float sy = my, sz = mz;
                tilted(d, k * hx * 0.5f, sy, sz, hx * 0.06f, half * 1.04f, hz * 1.03f, a, strap, DrawList.PLAIN);
            }
        }
        // straps across, over the top and down the sides, and the latches on the front
        float sy = open ? lowY : 0f, sh = open ? low : hy;
        for (int k = -1; k <= 1; k += 2) {
            float sx = k * hx * 0.5f;
            if (!open) part(d, sx, sy, 0f, hx * 0.06f, sh * 1.03f, hz * 1.03f, strap, DrawList.PLAIN);
            else part(d, sx, sy, 0f, hx * 0.06f, sh * 1.03f, hz * 1.03f, strap, DrawList.PLAIN);
            part(d, k * hx * 0.72f, cut, -hz * 1.03f, hx * 0.05f, hy * 0.12f, hz * 0.03f, brass, DrawList.GOLD);
        }
        // brass corners at the bottom
        for (int i = -1; i <= 1; i += 2)
            for (int j = -1; j <= 1; j += 2)
                part(d, i * hx * 0.96f, -hy * 0.82f, j * hz * 0.96f, hx * 0.05f, hy * 0.19f, hz * 0.05f, brass, DrawList.GOLD);
        // the handle on the front: two lugs and the grip between them
        part(d, -hx * 0.18f, -hy * 0.2f, -hz * 1.03f, hx * 0.03f, hy * 0.2f, hz * 0.04f, strap, DrawList.PLAIN);
        part(d, hx * 0.18f, -hy * 0.2f, -hz * 1.03f, hx * 0.03f, hy * 0.2f, hz * 0.04f, strap, DrawList.PLAIN);
        part(d, 0f, -hy * 0.2f, -hz * 1.1f, hx * 0.21f, hy * 0.1f, hz * 0.05f, Street.scale(leather, 0.45f), DrawList.PLAIN);
    }

    // ---- a spring mattress ----

    /**
     * Thick ticking in broad stripes, blue and cream, running front to back; a dark blue piping
     * round its edges; buttons in rows on top. Pressed by a load, its middle sinks. Torn, it
     * gapes dark on the face it was struck, springs stand out of it and grey wadding.
     */
    static void mattress(DrawList d, float x, float y, float z, float[] rot, float hx, float hy, float hz,
                         float sag, boolean torn, int markAxis, float mx, float my, float mz, float since) {
        at(x, y, z, rot);
        int cream = 0xE9E6DC, blue = 0x3F6FB0, pipe = 0x2A3E6A;
        int n = Math.max(3, Math.round(hx / 0.07f));
        float w = hx / n;
        for (int i = 0; i < n; i++) {
            float cx = -hx + w * (2 * i + 1);
            // sinking toward the middle under a load
            float u = cx / hx, dip = sag * hy * 0.4f * (1f - u * u);
            float top = hy - dip, h = (top + hy) * 0.5f;
            part(d, cx, top - h, 0f, w, h, hz, (i & 1) == 0 ? cream : blue, DrawList.PAINT);
        }
        // the piping round the top and the bottom
        for (int s = -1; s <= 1; s += 2) {
            float py = s * hy;
            part(d, 0f, py, -hz, hx * 1.01f, hy * 0.08f, hy * 0.08f, pipe, DrawList.PLAIN);
            part(d, 0f, py, hz, hx * 1.01f, hy * 0.08f, hy * 0.08f, pipe, DrawList.PLAIN);
            part(d, -hx, py, 0f, hy * 0.08f, hy * 0.08f, hz, pipe, DrawList.PLAIN);
            part(d, hx, py, 0f, hy * 0.08f, hy * 0.08f, hz, pipe, DrawList.PLAIN);
        }
        // buttons in rows on top
        for (float bx = -hx + 0.22f; bx < hx - 0.1f; bx += 0.3f)
            for (float bz = -hz + 0.18f; bz < hz - 0.08f; bz += 0.28f) {
                float u = bx / hx, dip = sag * hy * 0.4f * (1f - u * u);
                part(d, bx, hy - dip + 0.006f, bz, 0.018f, 0.008f, 0.018f, pipe, DrawList.PLAIN);
            }
        if (!torn) return;
        // the tear: a dark gap on the face it was struck, grey wadding round it, springs out of it
        int axis = markAxis % 3, a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
        float sgn = markAxis >= 3 ? -1f : 1f;
        float[] l = {mx, my, mz}, h = {hx, hy, hz};
        float hole = Math.min(0.15f, Math.min(h[a1], h[a2]) * 0.9f);
        float[] p = l.clone(), g = new float[3];
        p[axis] += sgn * 0.004f;
        g[axis] = 0.006f;
        g[a1] = hole;
        g[a2] = hole * 0.8f;
        part(d, p[0], p[1], p[2], g[0], g[1], g[2], 0x26221F, DrawList.PLAIN);
        for (int k = 0; k < 6; k++) {
            float ang = k * 1.047f;
            float rr = hole * (1.05f + 0.15f * (k % 3));
            float[] q = l.clone();
            q[axis] += sgn * 0.018f;
            q[a1] += (float) Math.cos(ang) * rr;
            q[a2] += (float) Math.sin(ang) * rr * 0.8f;
            float wd = 0.03f + 0.008f * (k % 2);
            part(d, q[0], q[1], q[2], wd, wd, wd, (k & 1) == 0 ? 0xBDB9B1 : 0xD9D5CD, DrawList.PLAIN);
        }
        float grow = Math.min(1f, since / 0.18f);
        for (int sp = 0; sp < 3; sp++) {
            float[] q = l.clone();
            float ang = sp * 2.1f;
            q[a1] += (float) Math.cos(ang) * hole * 0.45f;
            q[a2] += (float) Math.sin(ang) * hole * 0.35f;
            spring(d, q[0], q[1], q[2], axis, sgn, (sp - 1) * 0.5f, 0.06f, (0.26f + 0.06f * sp) * grow);
        }
    }

    /**
     * A coil of wire jumping out along one axis of the thing's frame, leaning off it a little:
     * a helix of small steel cubes, bright on its near half, dark behind.
     */
    private static void spring(DrawList d, float x, float y, float z, int axis, float sgn, float lean, float r, float len) {
        if (len <= 0.01f) return;
        int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
        float[] q = new float[3];
        int n = 16;
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n, th = t * 2.5f * 6.2832f;
            float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
            q[0] = x;
            q[1] = y;
            q[2] = z;
            q[axis] += sgn * len * t;
            q[a1] += lean * len * t + cs * r;
            q[a2] += (axis == 1 ? 0f : 0.6f) * len * t + sn * r;
            float w = 0.017f;
            part(d, q[0], q[1], q[2], w, w, w, sn > 0f ? 0xE8EAEE : 0x6E6C70, DrawList.GOLD);
        }
    }

    // ---- the light things ----

    /**
     * A shirt, flat, as big as a real one: the body, two sleeves out at the shoulders, a white
     * collar, and a big flower print over it.
     */
    static void shirt(DrawList d, float x, float y, float z, float[] rot, int c, int seed) {
        at(x, y, z, rot);
        float t = 0.012f;
        int print = (seed & 1) == 0 ? 0xF8F1E2 : 0xFFE07A, heart = Street.scale(c, 0.6f);
        part(d, 0f, 0f, 0f, 0.17f, t, 0.24f, c, DrawList.PLAIN);
        // the sleeves, out and down from the shoulders
        sleeve(d, 1f, c);
        sleeve(d, -1f, c);
        // the collar: two white points at the neck
        part(d, -0.05f, t * 1.5f, -0.21f, 0.05f, t * 0.6f, 0.035f, 0xF4F2EC, DrawList.PLAIN);
        part(d, 0.05f, t * 1.5f, -0.21f, 0.05f, t * 0.6f, 0.035f, 0xF4F2EC, DrawList.PLAIN);
        // the print: big flowers, a dark heart in each
        for (int k = 0; k < 5; k++) {
            float fx = ((seed * 7 + k * 53) % 26 - 13) / 100f, fz = ((seed * 11 + k * 31) % 36 - 16) / 100f;
            for (int sd = -1; sd <= 1; sd += 2) {
                part(d, fx, sd * t * 1.3f, fz, 0.035f, t * 0.4f, 0.035f, print, DrawList.PLAIN);
                part(d, fx, sd * t * 1.6f, fz, 0.012f, t * 0.4f, 0.012f, heart, DrawList.PLAIN);
            }
        }
    }

    private static void sleeve(DrawList d, float side, int c) {
        // a short sleeve turned out about the thing's up axis by forty degrees
        float a = side * 0.7f, cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        for (int i = 0; i < 3; i++) {
            float p = R[i * 3], q = R[i * 3 + 1], e = R[i * 3 + 2];
            M[i * 3] = p * cs - e * sn;
            M[i * 3 + 1] = q;
            M[i * 3 + 2] = p * sn + e * cs;
        }
        float lx = side * 0.2f, lz = -0.14f;
        d.box(ox + R[0] * lx + R[2] * lz, oy + R[3] * lx + R[5] * lz, oz + R[6] * lx + R[8] * lz, 0.09f, 0.011f, 0.06f, M, c, DrawList.PLAIN);
    }

    /** A postcard: white card, a picture on one side, a red stamp in its corner. */
    static void card(DrawList d, float x, float y, float z, float[] rot, int seed) {
        at(x, y, z, rot);
        int[] views = {0x3F8FD8, 0x5DBE6A, 0xF29A3A, 0xE87AB0};
        part(d, 0f, 0f, 0f, 0.075f, 0.004f, 0.05f, 0xF6F3EA, DrawList.PLAIN);
        part(d, -0.01f, 0.005f, 0f, 0.055f, 0.002f, 0.038f, views[seed & 3], DrawList.PLAIN);
        part(d, 0.05f, -0.005f, -0.03f, 0.012f, 0.002f, 0.012f, 0xD9505A, DrawList.PLAIN);
    }

    /** A loose spring out of a torn mattress. */
    static void looseSpring(DrawList d, float x, float y, float z, float[] rot) {
        at(x, y, z, rot);
        spring(d, 0f, -0.1f, 0f, 1, 1f, 0f, 0.04f, 0.2f);
    }
}
