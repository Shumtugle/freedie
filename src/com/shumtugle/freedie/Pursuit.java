package com.shumtugle.freedie;

/**
 * The chase, the game the golden cube from the broken plinth opens: a seaside town grown from
 * a seed as it is come to (see City), cars going about it and people out on the pavements, and
 * Freedie on foot in it (see Runner). Somewhere down the streets her double on fire waits; come
 * near and she runs (see Chase). Run her down to within reach, and Freedie stops and throws her
 * mallet (see Toss): it knocks the plate off the burning brow, and the double goes out and falls
 * apart. Then the game is over and the room comes back.
 *
 * A finger on the glass runs; moved sideways, it turns her; flicked up, a jump; flicked down, a
 * slide. At the top, how many meters off the double still is; at the edge of the frame, while
 * she is out of sight, a burning mark the way she is.
 *
 * For the room between rounds it can also show itself still, as a picture on the set: before
 * the game, the double running off down the first street; after it, the place where she fell.
 */
final class Pursuit {
    static final float NEAR = 70f;              // how far off she waits at the start, meters
    static final float OVER_AFTER = 3.2f;       // seconds from the plate knocked off to the end

    private final World.Host host;
    final City city = new City(0x5EE7L);
    final Runner runner = new Runner();
    final Mission mission = new Mission(0x60E3L, 1, NEAR);
    final Toss toss = new Toss();
    final Cars cars = new Cars();
    final Walkers walkers = new Walkers();
    Chase chase;
    float time;
    private boolean caughtSeen;
    private int struckSeen;
    private final float[] at = new float[2];

    Pursuit(World.Host host) {
        this.host = host;
        mission.start(0f, 0f);
        chase = mission.target(null);
    }

    void touch(int type, float x, float y) {
        runner.touch(type, x, y);
    }

    void update(float dt) {
        time += dt;
        runner.update(dt, city, cars, walkers);
        mission.update(dt, runner, city, cars);
        int thrown = toss.thrown;
        toss.update(dt, runner, mission, city);
        if (toss.thrown != thrown) sound(Synth.WHOOSH, 0.9f, 0.8f);
        chase = mission.target(chase);
        cars.update(dt, runner, mission.golems);
        walkers.update(dt, runner, chase);
        if (!caughtSeen && mission.caught() > 0) {
            caughtSeen = true;
            host.buzz(160);
            sound(Synth.thunder(1), 1f, 1f);
            sound(Synth.STONE, 1f, 0.6f);
            sound(Synth.CRASH, 0.6f, 0.8f);
        }
        int struck = 0;
        for (Chase g : mission.golems) struck += g.struck;
        if (struck != struckSeen) {
            struckSeen = struck;
            host.buzz(30);
            sound(Synth.CRASH, 0.7f, 1f);
        }
    }

    private void sound(int id, float gain, float rate) {
        if (host != null) host.sound(id, gain, rate);
    }

    /** Whether it is over: the double caught, and a while after for her to fall and lie. */
    boolean over() {
        return mission.over() && mission.clock - mission.doneAt >= 0f && chase.sinceTaken() > OVER_AFTER;
    }

    /** The eye, the light and the town round the runner, the double, the mallet: the whole scene. */
    void draw(DrawList d, int w) {
        Runner r = runner;
        float fx = (float) Math.sin(r.heading), fz = (float) Math.cos(r.heading);
        Daylight.light(d, 1f);
        // the town is open on every side: the sun higher than over the old street, from the left
        // and behind, so a street running away from it is lit down its length
        double e = Math.toRadians(40), t = Math.toRadians(-140);
        d.sun((float) (Math.cos(e) * Math.sin(t)), (float) Math.sin(e), (float) (Math.cos(e) * Math.cos(t)),
                r.x + fx * 30f, 6f, r.z + fz * 30f, 110f, 70f, 170f);
        d.camera(r.eyeX, r.eyeY, r.eyeZ, r.eyeYaw, r.eyePitch, w * 1.6f, 0.1f, 500f);
        float ex = (float) Math.sin(r.eyeYaw), ez = (float) Math.cos(r.eyeYaw);
        city.draw(d, r.eyeX, r.eyeZ, ex, ez);
        cars.draw(d, r.eyeX, r.eyeZ, ex, ez);
        walkers.draw(d, r.eyeX, r.eyeZ, ex, ez);
        r.draw(d);
        toss.draw(d, r);
        for (Chase g : mission.golems) {
            float gx = g.x - r.eyeX, gz = g.z - r.eyeZ;
            if (gx * ex + gz * ez < -6f || gx * gx + gz * gz > 200f * 200f) continue;
            g.draw(d);
        }
    }

    /**
     * The picture on the set before the game: the first street, Freedie standing with her mallet,
     * and down the street ahead her double on fire running off, so far into it.
     */
    void preview(DrawList d, int w, float t) {
        runner.update(0f, city, cars, walkers);
        draw(d, w);
        float z = runner.z + 12f + Chase.RUN * (t % 3f);
        Frida.draw(d, Chase.LANE, 0f, z, (float) Math.PI, Chase.SIZE, "sprint", t, true);
    }

    /**
     * The picture on the set after the game: where she fell, and before the eye the golden cube
     * that opened it, turning and burning.
     */
    void bonus(DrawList d, int w, float t) {
        draw(d, w);
        float[] eye = d.eye, ahead = d.ahead, up = d.up;
        float k = 6f;
        float cx = eye[0] + ahead[0] * k + up[0] * 0.9f, cy = eye[1] + ahead[1] * k + up[1] * 0.9f, cz = eye[2] + ahead[2] * k + up[2] * 0.9f;
        cube(d, cx, cy, cz, 0.32f, t);
    }

    private static final float[] turn = new float[9];

    /** A golden cube burning from inside, turning about the upright and leaning toward the eye. */
    static void cube(DrawList d, float x, float y, float z, float half, float t) {
        float a = t * 1.3f, c = (float) Math.cos(a), s = (float) Math.sin(a), tilt = 0.35f;
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        turn[0] = c; turn[1] = s * st; turn[2] = s * ct;
        turn[3] = 0f; turn[4] = ct; turn[5] = -st;
        turn[6] = -s; turn[7] = c * st; turn[8] = c * ct;
        d.box(x, y, z, half, half, half, turn, 0xFFB428, DrawList.FIRE_GOLD);
    }

    /**
     * Over the frame: how far off she still is, in meters, at the top; while she is out of sight,
     * a burning mark at the edge the way she is, smaller the further.
     */
    void flat(DrawList d, Px f, int w, int h) {
        int fire = 0xFFF0581C, ink = 0xFF232A4A, cream = 0xFFF4E8D2;
        if (mission.over()) return;
        String m = String.valueOf(Math.round(chase.apart));
        int tw = PixelFont.width(m, 3);
        PixelFont.draw(f, m, (w - tw) / 2 + 1, 15, 3, ink);
        PixelFont.draw(f, m, (w - tw) / 2, 14, 3, cream);
        Chase g = chase;
        boolean seen = d.project(g.x, g.y + 1.5f, g.z, at) && at[0] > 6 && at[0] < w - 6 && at[1] > 30 && at[1] < h - 6;
        if (seen) return;
        // the way to her, from the middle of the frame, cut at its edge
        float ax = (float) Math.sin(runner.eyeYaw), az = (float) Math.cos(runner.eyeYaw);
        float vx = g.x - runner.x, vz = g.z - runner.z;
        float across = vx * az - vz * ax, along = vx * ax + vz * az;
        float ang = (float) Math.atan2(across, along);
        float ex = (float) Math.sin(ang), ey = -(float) Math.cos(ang);
        float cx = w * 0.5f, cy = h * 0.5f, k = Math.min((w * 0.5f - 12f) / Math.max(1e-3f, Math.abs(ex)),
                (h * 0.5f - 34f) / Math.max(1e-3f, Math.abs(ey)));
        float px = cx + ex * k, py = cy + ey * k;
        float rr = 3.5f + 2.5f * Math.max(0f, Math.min(1f, 1f - (g.apart - 40f) / 360f));
        f.disc(px, py, rr + 1.5f, ink);
        f.disc(px, py, rr, fire);
        f.disc(px + ex * rr * 0.33f, py + ey * rr * 0.33f, rr * 0.42f, 0xFFFFD45A);
    }
}
