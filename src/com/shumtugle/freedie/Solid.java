package com.shumtugle.freedie;

/**
 * What stands on the ground and cannot be walked or driven through, seen from above as
 * circles: the street's own things (palm trunks, lamp posts, bollards, café tables), laid
 * down once, and what lies about for a while (pieces of a building knocked off, balls),
 * laid down afresh every step. Everything that only goes about the street, people and the
 * cars on the move, keeps out of them; only weather passes through things.
 */
final class Solid {
    private float[] x = new float[64], z = new float[64], r = new float[64];
    private int still, n;

    /** One of the street's own things, for good. */
    void fixed(float cx, float cz, float cr) {
        if (n != still) n = still;
        add(cx, cz, cr);
        still = n;
    }

    /** Forget what lay about last step: the street's own things stay. */
    void clearLoose() {
        n = still;
    }

    /** Something lying about, for this step. */
    void loose(float cx, float cz, float cr) {
        add(cx, cz, cr);
    }

    private void add(float cx, float cz, float cr) {
        if (n == x.length) {
            x = java.util.Arrays.copyOf(x, n * 2);
            z = java.util.Arrays.copyOf(z, n * 2);
            r = java.util.Arrays.copyOf(r, n * 2);
        }
        x[n] = cx;
        z[n] = cz;
        r[n] = cr;
        n++;
    }

    /**
     * Moves a point that is so wide out of everything it would stand in, to the nearest
     * spot just clear of it: someone walking into a palm steps round it.
     */
    void push(float[] p, float wide) {
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < n; i++) {
                float dx = p[0] - x[i], dz = p[1] - z[i], need = r[i] + wide;
                float d2 = dx * dx + dz * dz;
                if (d2 >= need * need) continue;
                float d = (float) Math.sqrt(d2);
                if (d < 1e-4f) {
                    dx = 1f;
                    dz = 0f;
                    d = 1f;
                }
                p[0] = x[i] + dx / d * need;
                p[1] = z[i] + dz / d * need;
            }
    }

    /** The farthest edge down the street of anything lying between these two lines across it, or -1e9. */
    float farthest(float x0, float x1) {
        float far = -1e9f;
        for (int i = 0; i < n; i++)
            if (x[i] + r[i] > x0 && x[i] - r[i] < x1) far = Math.max(far, z[i] + r[i]);
        return far;
    }
}
