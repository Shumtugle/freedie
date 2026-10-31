package com.shumtugle.freedie;

import java.util.Random;

/**
 * The town's traffic: small sedans on the right of the road, going on or turning at the
 * crossings as it falls out, never turning back; slowing for a turn, for a car ahead and for
 * the runner in the road before them. Not for a golem on the run: they run into it, stop short
 * a while, and wait while it lies there or stands in the road. They come from out of sight round the runner and go out of
 * it behind her, and are put back somewhere else round her.
 *
 * Also the sedan itself, for any car anywhere: the parked ones along the curbs are built with
 * the town (see City).
 */
final class Cars {
    static final int[] PAINT = {0xE9B63A, 0xB5352C, 0xE9E1CB, 0x2D3A57, 0x6E9DB0, 0x9DBB8E, 0x3A3B40, 0xF4F2EC};
    static final float LANE = 1.5f;                 // right of the road's middle, clear of the parked
    static final float CRUISE = 9f, TURNING = 5f, GAP = 11f;
    static final float BORN_NEAR = 45f, BORN_FAR = 130f, GONE = 170f;
    static final int MOST = 12;

    private final Random rnd = new Random(0xCA25L);
    final float[] x = new float[MOST], z = new float[MOST], heading = new float[MOST], speed = new float[MOST];
    private final int[] ti = new int[MOST], tj = new int[MOST], di = new int[MOST], dj = new int[MOST];
    private final int[] ni = new int[MOST], nj = new int[MOST], paint = new int[MOST];
    private final boolean[] live = new boolean[MOST];
    private final float[] halt = new float[MOST];       // stopped short after striking her, seconds left

    void update(float dt, Runner r, Chase[] golems) {
        for (int c = 0; c < MOST; c++) {
            float rx = x[c] - r.x, rz = z[c] - r.z;
            if (!live[c] || rx * rx + rz * rz > GONE * GONE) {
                spawn(c, r);
                continue;
            }
            // toward the next crossing, on the right of the road
            float tx = ti[c] * City.CELL + dj[c] * LANE, tz = tj[c] * City.CELL - di[c] * LANE;
            float ex = tx - x[c], ez = tz - z[c], left = (float) Math.sqrt(ex * ex + ez * ez);
            if (left < 2.5f) {
                di[c] = ni[c];
                dj[c] = nj[c];
                ti[c] += di[c];
                tj[c] += dj[c];
                choose(c);
                tx = ti[c] * City.CELL + dj[c] * LANE;
                tz = tj[c] * City.CELL - di[c] * LANE;
                ex = tx - x[c];
                ez = tz - z[c];
                left = (float) Math.sqrt(ex * ex + ez * ez);
            }
            boolean turns = ni[c] != di[c] || nj[c] != dj[c];
            float want = turns && left < 14f ? TURNING : CRUISE;
            float fx = (float) Math.sin(heading[c]), fz = (float) Math.cos(heading[c]);
            // a car ahead in the same way: keep back from it
            for (int o = 0; o < MOST; o++) {
                if (o == c || !live[o]) continue;
                float ox = x[o] - x[c], oz = z[o] - z[c];
                float along = ox * fx + oz * fz, across = Math.abs(ox * fz - oz * fx);
                if (along > 0f && along < GAP && across < 2.2f) want = Math.min(want, Math.max(0f, (along - 6f) * 1.2f));
            }
            // someone in the road ahead: stop short of them
            want = Math.min(want, clear(c, fx, fz, r.x, r.z));
            for (Chase g : golems) if (g.down()) want = Math.min(want, clear(c, fx, fz, g.x, g.z));
            if (halt[c] > 0f) {
                halt[c] -= dt;
                want = 0f;
            }
            float rate = want < speed[c] ? 9f : 2.5f;
            speed[c] += Math.max(-rate * dt, Math.min(rate * dt, want - speed[c]));
            float aim = (float) Math.atan2(ex, ez), d = aim - heading[c];
            while (d > Math.PI) d -= 2f * (float) Math.PI;
            while (d < -Math.PI) d += 2f * (float) Math.PI;
            float most = 2.4f * dt * Math.max(0.3f, speed[c] / CRUISE);
            heading[c] += Math.max(-most, Math.min(most, d));
            x[c] += (float) Math.sin(heading[c]) * speed[c] * dt;
            z[c] += (float) Math.cos(heading[c]) * speed[c] * dt;
        }
    }

    /** How fast car c may go with someone at (px, pz): no faster than it can stop short of them. */
    private float clear(int c, float fx, float fz, float px, float pz) {
        float ox = px - x[c], oz = pz - z[c];
        float along = ox * fx + oz * fz, across = Math.abs(ox * fz - oz * fx);
        if (along < 0f || along > 16f || across > 2.4f) return CRUISE;
        return Math.max(0f, (along - 4f) * 1.1f);
    }

    /** A car put on a street round the runner, out of her sight, going one way or the other. */
    private void spawn(int c, Runner r) {
        for (int tries = 0; tries < 20; tries++) {
            int ci = City.index(r.x) + rnd.nextInt(5) - 2, cj = City.index(r.z) + rnd.nextInt(5) - 2;
            int dir = rnd.nextInt(4);
            int a = dir == 0 ? 1 : dir == 1 ? -1 : 0, b = dir == 2 ? 1 : dir == 3 ? -1 : 0;
            float half = City.CELL * 0.5f;
            float px = ci * City.CELL + a * half + b * LANE, pz = cj * City.CELL + b * half - a * LANE;
            float dx = px - r.x, dz = pz - r.z, dd = (float) Math.sqrt(dx * dx + dz * dz);
            if (dd < BORN_NEAR || dd > BORN_FAR) continue;
            // not right before her eyes
            float fx = (float) Math.sin(r.eyeYaw), fz = (float) Math.cos(r.eyeYaw);
            if ((dx * fx + dz * fz) / dd > 0.6f && dd < 90f) continue;
            x[c] = px;
            z[c] = pz;
            di[c] = a;
            dj[c] = b;
            ti[c] = ci + a;
            tj[c] = cj + b;
            heading[c] = (float) Math.atan2(a, b);
            speed[c] = CRUISE;
            halt[c] = 0f;
            paint[c] = PAINT[rnd.nextInt(PAINT.length)];
            choose(c);
            live[c] = true;
            return;
        }
        live[c] = false;
    }

    /** For the desktop stand: car 0 put behind her in her lane, so far back, coming on at speed. */
    void standBehind(Chase ch, float back) {
        int a = Math.round((float) Math.sin(ch.heading)), b = Math.round((float) Math.cos(ch.heading));
        if (a != 0) b = 0;
        float px = ch.x - a * back, pz = ch.z - b * back;
        int ri = Math.round(ch.x / City.CELL), rj = Math.round(ch.z / City.CELL);
        // on the street she is on, in its right lane, to the next crossing that way
        if (a != 0) {
            pz = rj * City.CELL - a * LANE;
            ti[0] = City.index(px) + (a > 0 ? 1 : 0);
            tj[0] = rj;
        } else {
            px = ri * City.CELL + b * LANE;
            ti[0] = ri;
            tj[0] = City.index(pz) + (b > 0 ? 1 : 0);
        }
        x[0] = px;
        z[0] = pz;
        di[0] = a;
        dj[0] = b;
        ni[0] = a;
        nj[0] = b;
        heading[0] = (float) Math.atan2(a, b);
        speed[0] = CRUISE;
        halt[0] = 0f;
        paint[0] = PAINT[1];
        live[0] = true;
    }

    /** What car c does at the crossing after the one it is going to: on, mostly; left or right now and then. */
    private void choose(int c) {
        int roll = rnd.nextInt(5);
        ni[c] = di[c];
        nj[c] = dj[c];
        if (roll == 3) {
            ni[c] = -dj[c];
            nj[c] = di[c];
        } else if (roll == 4) {
            ni[c] = dj[c];
            nj[c] = -di[c];
        }
    }

    /**
     * The car, if any, running into a round body of radius rr at (px, pz): touching it at its
     * nose or its side, and coming at it fast enough to knock it down; -1 if none.
     */
    int striking(float px, float pz, float rr) {
        for (int c = 0; c < MOST; c++) {
            if (!live[c] || speed[c] < 2.5f) continue;
            float fx = (float) Math.sin(heading[c]), fz = (float) Math.cos(heading[c]);
            for (int e = -1; e <= 1; e += 2) {
                float cx = x[c] + fx * e * 1.2f, cz = z[c] + fz * e * 1.2f;
                float dx = px - cx, dz = pz - cz, m = rr + 1.15f;
                if (dx * dx + dz * dz >= m * m) continue;
                // coming at her: she is ahead of the car's middle, not behind it
                if ((px - x[c]) * fx + (pz - z[c]) * fz > 0.6f) return c;
            }
        }
        return -1;
    }

    /** Car c has struck someone: it jolts to a stop and stands a moment. */
    void struck(int c) {
        speed[c] *= 0.25f;
        halt[c] = 1.4f;
    }

    /**
     * Pushes a round body of radius rr at (p[0], p[1]) out of any car it went into: each car two
     * round ends along its length. True if it touched one.
     */
    boolean push(float[] p, float rr) {
        boolean hit = false;
        for (int c = 0; c < MOST; c++) {
            if (!live[c]) continue;
            float fx = (float) Math.sin(heading[c]), fz = (float) Math.cos(heading[c]);
            for (int e = -1; e <= 1; e += 2) {
                float cx = x[c] + fx * e * 1.2f, cz = z[c] + fz * e * 1.2f;
                float dx = p[0] - cx, dz = p[1] - cz, dd = dx * dx + dz * dz, m = rr + 1.05f;
                if (dd >= m * m || dd < 1e-8f) continue;
                float l = (float) Math.sqrt(dd);
                p[0] = cx + dx / l * m;
                p[1] = cz + dz / l * m;
                hit = true;
            }
        }
        return hit;
    }

    void draw(DrawList d, float ex, float ez, float fx, float fz) {
        for (int c = 0; c < MOST; c++) {
            if (!live[c]) continue;
            float dx = x[c] - ex, dz = z[c] - ez;
            if (dx * fx + dz * fz < -6f) continue;
            car(d, x[c], z[c], heading[c], paint[c], false);
        }
    }

    // ---- the sedan ----

    private static float px, py, pz;
    private static final float[] frame = new float[9], t = new float[9];

    /**
     * A small sedan standing on the road, its nose along yaw (0 along +z); lean, without its
     * small things (a parked one: there are many of them).
     */
    static void car(DrawList d, float x, float z, float yaw, int paint, boolean lean) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        px = x;
        py = 0f;
        pz = z;
        frame[0] = cy; frame[1] = 0f; frame[2] = sy;
        frame[3] = 0f; frame[4] = 1f; frame[5] = 0f;
        frame[6] = -sy; frame[7] = 0f; frame[8] = cy;
        float plain = DrawList.PLAIN, glass = DrawList.GLASS;
        int clear = 0x33414E;
        // body, with a darker sill
        part(d, 0f, 0.6f, 0f, 0.9f, 0.25f, 2.2f, 0f, paint, plain);
        part(d, 0f, 0.36f, 0f, 0.86f, 0.05f, 1.75f, 0f, 0x2A2829, plain);
        // cabin: glass all round, the screens sloping, a roof, the pillars between the doors
        float top = 1.3f, mid = (0.85f + top) * 0.5f;
        part(d, 0f, mid, -0.275f, 0.79f, 0.225f, 0.675f, 0f, clear, glass);
        part(d, 0f, mid, 0.7f, 0.79f, 0.375f, 0.01f, -0.927f, clear, glass);
        part(d, 0f, mid, -1.1f, 0.79f, 0.27f, 0.01f, 0.588f, clear, glass);
        part(d, 0f, top + 0.01f, -0.275f, 0.77f, 0.025f, 0.7f, 0f, paint, plain);
        for (int side = -1; side <= 1; side += 2) {
            if (!lean) part(d, side * 0.795f, mid, -0.25f, 0.01f, 0.22f, 0.07f, 0f, paint, plain);
            for (int end = -1; end <= 1; end += 2) {
                part(d, side * 0.85f, 0.32f, end * 1.38f, 0.12f, 0.32f, 0.32f, 0f, 0x1C1B1D, plain);
                if (!lean) part(d, side * 0.975f, 0.32f, end * 1.38f, 0.01f, 0.15f, 0.15f, 0f, 0xB8B4AC, plain);
            }
            part(d, side * 0.6f, 0.68f, 2.205f, 0.17f, 0.07f, 0.01f, 0f, 0xFFF0C2, plain);
            part(d, side * 0.64f, 0.7f, -2.205f, 0.17f, 0.07f, 0.01f, 0f, 0xD43A2E, plain);
        }
        // bumpers, grille, plates
        part(d, 0f, 0.42f, 2.24f, 0.93f, 0.07f, 0.05f, 0f, 0xD2CEC6, plain);
        part(d, 0f, 0.42f, -2.24f, 0.93f, 0.07f, 0.05f, 0f, 0xD2CEC6, plain);
        if (lean) return;
        part(d, 0f, 0.64f, 2.205f, 0.3f, 0.08f, 0.01f, 0f, 0x2A2A2C, plain);
        part(d, 0f, 0.43f, 2.3f, 0.24f, 0.06f, 0.01f, 0f, 0xF2F0E6, plain);
        part(d, 0f, 0.43f, -2.3f, 0.24f, 0.06f, 0.01f, 0f, 0xF2F0E6, plain);
    }

    /** A box of the car in its own frame: tilted about its own x first (sloping glass), then turned with it. */
    private static void part(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, float tilt,
                             int color, float material) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float[] f = frame;
        for (int r = 0; r < 3; r++) {
            t[r * 3] = f[r * 3];
            t[r * 3 + 1] = f[r * 3 + 1] * ct + f[r * 3 + 2] * st;
            t[r * 3 + 2] = -f[r * 3 + 1] * st + f[r * 3 + 2] * ct;
        }
        d.box(px + f[0] * lx + f[1] * ly + f[2] * lz, py + f[3] * lx + f[4] * ly + f[5] * lz,
                pz + f[6] * lx + f[7] * ly + f[8] * lz, hx, hy, hz, t, color, material);
    }
}
