package com.shumtugle.freedie;

import java.util.Random;

/**
 * What crosses the sky far away, seldom: now and then a small flock of birds, and more
 * seldom still an airliner high up, drawing a white trail that spreads and fades behind it.
 *
 * They are not boxes: each frame they are laid out as a few short strokes in frame pixels,
 * and the sky draws them as it draws itself. Everything nearer is drawn over the sky, so
 * they show over open sky only, behind everything on the street.
 */
final class Fliers {
    private final Random rnd = new Random(5);
    private final float[] scr = new float[2], scr2 = new float[2];

    // seconds of street time until the next flock and the next plane
    private float birdsIn, planeIn;

    // the flock: where its middle is, how fast it goes, each bird's place in it and wing beat
    private static final int MAX_BIRDS = 7;
    private boolean flock;
    private float fx, fy, fz, fv, ft;
    private int birds;
    private final float[] bx = new float[MAX_BIRDS], by = new float[MAX_BIRDS], bz = new float[MAX_BIRDS], beat = new float[MAX_BIRDS];

    // the plane and its trail: points it has passed, with their age
    private static final float DROP = 1.5f, TRAIL_LIFE = 30f;
    private static final int TRAIL = (int) (TRAIL_LIFE / DROP) + 2;
    private boolean plane;
    private float px, py, pz, pv, pt;
    private final float[] tx = new float[TRAIL], ty = new float[TRAIL], tz = new float[TRAIL], ta = new float[TRAIL];
    private int tn;
    private float dropAt;

    Fliers() {
        birdsIn = 60f + rnd.nextFloat() * 90f;
        planeIn = 170f + rnd.nextFloat() * 200f;
    }

    /** Time goes on; street is true while the street is what the player sees. */
    void update(float dt, boolean street) {
        if (street) {
            if (!flock && (birdsIn -= dt) <= 0f) startFlock();
            if (!plane && tn == 0 && (planeIn -= dt) <= 0f) startPlane();
        }
        if (flock) {
            ft += dt;
            fx += fv * dt;
            // a flock rises and sinks a little as it goes, each bird drifting in its place
            fy += (float) Math.sin(ft * 0.5f) * 1.2f * dt;
            for (int i = 0; i < birds; i++) {
                bx[i] += (float) Math.sin(ft * 0.9f + i * 1.3f) * 0.6f * dt;
                by[i] += (float) Math.cos(ft * 1.1f + i * 0.8f) * 0.5f * dt;
            }
            if (Math.abs(fx) > 90f && fx * fv > 0f) {
                flock = false;
                birdsIn = 100f + rnd.nextFloat() * 110f;
            }
        }
        if (plane) {
            pt += dt;
            px += pv * dt;
            dropAt += dt;
            if (dropAt > DROP && tn < TRAIL) {
                dropAt = 0f;
                drop();
            }
            if (Math.abs(px) > 1300f && px * pv > 0f) {
                plane = false;
                planeIn = 220f + rnd.nextFloat() * 220f;
            }
        }
        for (int i = 0; i < tn; i++) ta[i] += dt;
        // the oldest end of the trail is gone once it has faded out
        int gone = 0;
        while (gone < tn && ta[gone] > TRAIL_LIFE) gone++;
        if (gone > 0) {
            System.arraycopy(tx, gone, tx, 0, tn - gone);
            System.arraycopy(ty, gone, ty, 0, tn - gone);
            System.arraycopy(tz, gone, tz, 0, tn - gone);
            System.arraycopy(ta, gone, ta, 0, tn - gone);
            tn -= gone;
        }
    }

    /** A point of the trail, just behind the plane. */
    private void drop() {
        tx[tn] = px - Math.signum(pv) * 14f;
        ty[tn] = py;
        tz[tn] = pz;
        ta[tn] = 0f;
        tn++;
    }

    /** A flock now, across the far end of the street. */
    void startFlock() {
        flock = true;
        ft = 0f;
        boolean right = rnd.nextBoolean();
        fv = (right ? 1f : -1f) * (8f + rnd.nextFloat() * 4f);
        fx = right ? -80f : 80f;
        fz = 170f + rnd.nextFloat() * 90f;
        fy = 34f + rnd.nextFloat() * 26f;
        birds = 3 + rnd.nextInt(MAX_BIRDS - 2);
        for (int i = 0; i < birds; i++) {
            // loosely in a wedge behind the first
            float back = i == 0 ? 0f : 4f + rnd.nextFloat() * 9f;
            bx[i] = -Math.signum(fv) * back;
            by[i] = (rnd.nextFloat() - 0.5f) * 5f - back * 0.2f;
            bz[i] = (rnd.nextFloat() - 0.5f) * 10f;
            beat[i] = rnd.nextFloat() * 6.28f;
        }
    }

    /** A plane now, high over the town. */
    void startPlane() {
        plane = true;
        pt = 0f;
        tn = 0;
        dropAt = 0f;
        boolean right = rnd.nextBoolean();
        pv = (right ? 1f : -1f) * (55f + rnd.nextFloat() * 20f);
        px = right ? -1250f : 1250f;
        pz = 2200f + rnd.nextFloat() * 900f;
        py = 1100f + rnd.nextFloat() * 700f;
    }

    /** For the desktop stand: the plane as if it had been flying so many seconds already. */
    void standPlane(float seconds) {
        startPlane();
        // well into the sky: it comes in from the side and has crossed some of it
        px = -Math.signum(pv) * 300f;
        for (float t = 0f; t < seconds; t += 0.1f) update(0.1f, false);
    }

    /** For the desktop stand: a flock, already over the street. */
    void standFlock() {
        startFlock();
        fx = 38f - fv * 1.5f;
    }

    // ---- the strokes ----

    /** The trail's look by its age: bright and thin when fresh, then wider and fading out. */
    private static float alpha(float age) {
        return 0.75f * Math.max(0f, 1f - age / TRAIL_LIFE) * Math.min(1f, age * 3f + 0.4f);
    }

    private static float width(float age) {
        return 1f + Math.min(2.2f, age / 6f);
    }

    /** Lays the fliers out as strokes for the sky to draw, in the frame as the eye now sees it. */
    void strokes(DrawList d) {
        d.flyN = 0;
        // the trail: from the oldest point still there to the newest, and on to the plane
        for (int i = 1; i < tn; i++) stroke(d, tx[i - 1], ty[i - 1], tz[i - 1], tx[i], ty[i], tz[i], ta[i - 1], ta[i]);
        if (plane) {
            float hx = px - Math.signum(pv) * 14f;
            if (tn > 0) stroke(d, tx[tn - 1], ty[tn - 1], tz[tn - 1], hx, py, pz, ta[tn - 1], 0f);
            // the plane itself, a bright dot, and now and then the sun catching it
            if (d.project(px, py, pz, scr)) {
                float dir = pv > 0 ? -1f : 1f;
                d.fly(scr[0] + dir * 0.6f, scr[1], scr[0] - dir * 0.6f, scr[1], 1.2f, 1.2f, 1f, 1f, false);
                if (((int) (pt * 2.5f) & 3) == 0) d.fly(scr[0], scr[1] - 1f, scr[0], scr[1] - 1f, 1f, 1f, 0.8f, 0.8f, false);
            }
        }
        if (flock) {
            for (int i = 0; i < birds; i++) {
                if (!d.project(fx + bx[i], fy + by[i], fz + bz[i], scr)) continue;
                float sx = scr[0], sy = scr[1];
                // the wing beat: up, level, down; now and then a glide with wings held out
                float b = ft * 7.5f + beat[i];
                boolean glide = Math.sin(ft * 0.6f + i) > 0.55f;
                float wy = glide ? 0f : (float) Math.sin(b) * 1.4f;
                d.fly(sx - 2.2f, sy + wy, sx, sy, 1f, 1.1f, 0.6f, 0.85f, true);
                d.fly(sx, sy, sx + 2.2f, sy + wy, 1.1f, 1f, 0.85f, 0.6f, true);
            }
        }
    }

    private void stroke(DrawList d, float x0, float y0, float z0, float x1, float y1, float z1, float age0, float age1) {
        if (!d.project(x0, y0, z0, scr) || !d.project(x1, y1, z1, scr2)) return;
        float a0 = alpha(age0), a1 = alpha(age1);
        if (a0 < 0.02f && a1 < 0.02f) return;
        d.fly(scr[0], scr[1], scr2[0], scr2[1], width(age0), width(age1), a0, a1, false);
    }
}
