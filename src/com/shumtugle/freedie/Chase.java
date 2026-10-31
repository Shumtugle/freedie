package com.shumtugle.freedie;

import java.util.Random;

/**
 * A chase: Freedie on fire runs off through the town and the player runs after her. She keeps
 * to the streets, on the right of the road, and at every crossing goes on, or turns left or
 * right, as it falls out; she never turns back. Near, she bolts; far behind her, she slows and
 * lets the chase come up; left very far behind, she stops, folds her arms and waits.
 *
 * Caught (the runner within reach of her), she is jolted, counted, and breaks loose: for a few
 * seconds she runs faster than anyone can and cannot be caught, and the chase goes on.
 *
 * The cars do not stop for her: she runs in their lane. Struck by one, she is thrown the way it
 * was going, falls on her back and lies a while; then she gets up and runs on. Caught lying
 * there, she is counted all the same, gets up and breaks loose.
 *
 * One of a hunt (see Mission) is caught for good: she goes out on the spot, falls apart, and
 * her blocks lie cooling to stone on the road a while and sink into it.
 */
final class Chase {
    static final float SIZE = 1.3f;
    static final float LANE = 1.6f;            // right of the road's middle
    static final float RUN = 8.6f, BOLT = 9.6f, LAG = 6.5f, BREAK = 12.5f;
    static final float NEAR = 10f, FAR = 70f, WAIT = 160f, RESUME = 50f, REACH = 2.2f;
    static final float BREAK_TIME = 3f, JOLT = 0.8f;
    // struck: the fall begun past its first stagger and played faster; lying; getting up again
    static final float FALL_FROM = 0.5f, FALL_PACE = 1.5f, FALL_END = 1.25f, LIE = 1.6f, RISE_PACE = 1.3f;

    static final int FLEE = 0, JOLTED = 1, LOOSE = 2, WAITING = 3, DOWN = 4, RISING = 5, STONE = 6, SEIZED = 7;
    static final float SEIZED_MOST = 4f, HELD_REACH = 3.4f;   // a hunt's golem is held a little further off: she stops to throw       // held so long at most, waiting for the blow
    static final float SIDE = 5.4f;            // where one of a hunt waits: on the pavement
    static final float COOL = 1.4f, LIES = 7f, SINKS = 2f;

    float x = LANE, z = 34f, y, heading, speed;
    int state = FLEE;
    float stateAt, clock;
    int caught, struck;
    // thrown by a car: sliding along the ground, off it a little
    private float vx, vz, lift, vy;
    private boolean caughtDown;
    // the crossing she runs to, and the way she is going (one of the four, as cell steps)
    private int ti = 0, tj = 1, di = 0, dj = 1;
    private final Random rnd;
    private float stride;
    // caught for good: she falls apart
    private final boolean forGood;
    private float[] bp, bv, bt, bh, axis, spin, turned;
    private int[] bc;
    private int bn;
    private float floor;
    // the plate off her brow: where, how fast, its turn when it came off and as it spins since
    private final float[] plate = new float[3], plateV = new float[3], plateTurn = new float[9], plateAxis = new float[3];
    private float plateSpin, plateRate;
    /** Which of the five slots over the frame her plate goes to, once it is off; -1 until then. */
    int slot = -1;
    private static final float[] turnNow = new float[9];

    /** The chase as it was first tried: she starts down the street ahead, already running. */
    Chase() {
        rnd = new Random(0xF1AEL);
        forGood = false;
    }

    /**
     * One of a hunt: waiting on the pavement half way along the block past crossing (ci, cj) the
     * way dir goes (0 +x, 1 -x, 2 +z, 3 -z), arms folded, until the runner comes near.
     */
    Chase(long seed, int ci, int cj, int dir) {
        rnd = new Random(seed);
        forGood = true;
        di = dir == 0 ? 1 : dir == 1 ? -1 : 0;
        dj = dir == 2 ? 1 : dir == 3 ? -1 : 0;
        float half = City.CELL * 0.5f;
        x = ci * City.CELL + di * half + dj * SIDE;
        z = cj * City.CELL + dj * half - di * SIDE;
        ti = ci + di;
        tj = cj + dj;
        heading = (float) Math.atan2(di, dj);
        state = WAITING;
    }

    private boolean seizedLying;

    /** Reached by the runner: she is held where she is, startled, or lying if she was down. */
    private void seize(boolean lying) {
        seizedLying = lying;
        speed = 0f;
        set(SEIZED);
    }

    /** Whether she is held, reached by the runner and waiting for the blow. */
    boolean seized() {
        return state == SEIZED;
    }

    /** Caught for good and fallen apart. */
    boolean done() {
        return state == STONE;
    }

    /** For the desktop stand: as the chase first was, running down the street ahead of the start. */
    void standAhead() {
        x = LANE;
        z = 34f;
        ti = 0;
        tj = 1;
        di = 0;
        dj = 1;
        heading = 0f;
        speed = RUN;
        state = FLEE;
    }

    /** The distance to the runner, as last worked out. */
    float apart;

    void update(float dt, Runner r, City city, Cars cars) {
        clock += dt;
        float dx = r.x - x, dz = r.z - z;
        apart = (float) Math.sqrt(dx * dx + dz * dz);
        if (state == STONE) {
            rubble(dt);
            return;
        }
        if (state == SEIZED) {
            // held where she was reached, waiting for the blow; if it never comes, she falls apart all the same
            speed = 0f;
            y += (city.ground(x, z) + 0f - y) * Math.min(1f, dt * 18f);
            lift = Math.max(0f, lift - dt * 4f);
            if (clock - stateAt > SEIZED_MOST) crumble(r, city);
            return;
        }
        float want;
        switch (state) {
            case JOLTED:
                want = 0f;
                if (clock - stateAt > JOLT) set(LOOSE);
                break;
            case LOOSE:
                want = BREAK;
                if (clock - stateAt > BREAK_TIME) set(FLEE);
                break;
            case WAITING:
                want = 0f;
                if (apart < RESUME) set(FLEE);
                break;
            case DOWN:
                want = 0f;
                if (apart < (forGood ? HELD_REACH : REACH) && !caughtDown) {
                    caught++;
                    caughtDown = true;
                    if (forGood) {
                        seize(true);
                        return;
                    }
                }
                if (caughtDown || clock - stateAt > (FALL_END - FALL_FROM) / FALL_PACE + LIE) set(RISING);
                break;
            case RISING:
                want = 0f;
                if (clock - stateAt > FALL_END / RISE_PACE) {
                    set(caughtDown ? LOOSE : FLEE);
                    onWay();
                }
                break;
            default:
                if (apart < (forGood ? HELD_REACH : REACH)) {
                    caught++;
                    if (forGood) {
                        seize(false);
                        return;
                    }
                    set(JOLTED);
                    want = 0f;
                } else if (apart > WAIT) {
                    set(WAITING);
                    want = 0f;
                } else {
                    // near, she bolts; far ahead, she lets the chase come up
                    float k = Math.max(0f, Math.min(1f, (apart - NEAR) / (FAR - NEAR)));
                    want = apart < NEAR ? BOLT : RUN + (LAG - RUN) * k;
                }
        }
        if (state != DOWN && state != RISING && state != JOLTED) {
            int c = cars.striking(x, z, 0.55f);
            if (c >= 0) strike(cars, c);
        }
        if (state == DOWN || state == RISING) {
            lying(dt, city, cars);
            return;
        }
        speed += (want - speed) * Math.min(1f, dt * 3f);
        // toward the next crossing, kept on her side of the road
        float rx = (float) dj, rz = (float) -di;
        float tx = ti * City.CELL + rx * LANE, tz = tj * City.CELL + rz * LANE;
        float ex = tx - x, ez = tz - z;
        if (ex * ex + ez * ez < 2.5f * 2.5f) {
            turn();
            rx = dj;
            rz = -di;
            tx = ti * City.CELL + rx * LANE;
            tz = tj * City.CELL + rz * LANE;
            ex = tx - x;
            ez = tz - z;
        }
        float aim = (float) Math.atan2(ex, ez);
        float d = aim - heading;
        while (d > Math.PI) d -= 2f * (float) Math.PI;
        while (d < -Math.PI) d += 2f * (float) Math.PI;
        float most = 5f * dt;
        heading += Math.max(-most, Math.min(most, d));
        x += (float) Math.sin(heading) * speed * dt;
        z += (float) Math.cos(heading) * speed * dt;
        float[] p = {x, z};
        city.push(p, 0.55f);
        cars.push(p, 0.55f);
        x = p[0];
        z = p[1];
        y += (city.ground(x, z) - y) * Math.min(1f, dt * 18f);
        stride += dt * Math.max(0.3f, speed) / (speed > 6f ? 7f : 3.3f);
    }

    /**
     * Struck by car c: thrown off her feet the way it goes and off to the side she was on (the
     * curb side, if she was dead ahead), turned so that she falls on her back.
     */
    private void strike(Cars cars, int c) {
        float h = cars.heading[c], v = cars.speed[c];
        float fx = (float) Math.sin(h), fz = (float) Math.cos(h), rx = fz, rz = -fx;
        float off = (x - cars.x[c]) * rx + (z - cars.z[c]) * rz;
        float side = off < -0.3f ? -1f : 1f;
        float tx = fx * 0.8f + rx * side * 0.6f, tz = fz * 0.8f + rz * side * 0.6f;
        heading = (float) Math.atan2(tx, tz) + (float) Math.PI;
        float flung = v * 0.8f + 1.5f;
        vx = tx * flung;
        vz = tz * flung;
        vy = 2.5f + v * 0.18f;
        speed = 0f;
        caughtDown = false;
        struck++;
        cars.struck(c);
        set(DOWN);
    }

    /** Thrown and down: she slides to a stop on the ground, kept out of the houses and the cars. */
    private void lying(float dt, City city, Cars cars) {
        float drag = Math.max(0f, 1f - dt * (lift > 0f ? 0.6f : 5f));
        vx *= drag;
        vz *= drag;
        x += vx * dt;
        z += vz * dt;
        vy -= 22f * dt;
        lift = Math.max(0f, lift + vy * dt);
        if (lift == 0f) vy = 0f;
        float[] p = {x, z};
        city.push(p, 0.55f);
        cars.push(p, 0.55f);
        x = p[0];
        z = p[1];
        y += (city.ground(x, z) - y) * Math.min(1f, dt * 18f);
    }

    /** Up again: if she was thrown past the crossing she was running to, she runs on from it. */
    private void onWay() {
        float tx = ti * City.CELL, tz = tj * City.CELL;
        if ((tx - x) * di + (tz - z) * dj < 0f) turn();
    }

    /** Whether she is on the ground or getting up, or stands still: the cars stop for her then. */
    boolean down() {
        return state == DOWN || state == RISING || state == JOLTED || (state != STONE && speed < 1.5f);
    }

    /**
     * Caught for good: she goes out where she is, every block flying off her a little, the
     * runner's push in it, and turning as it goes.
     */
    private void crumble(Runner r, City city) {
        crumble(r, city, x - r.x, z - r.z);
    }

    /**
     * Struck on the head (by the runner's hammer) coming from the way (bx, bz): the plate is
     * knocked off her brow and she is caught for good. False if she already was.
     */
    boolean take(Runner r, City city, float bx, float bz) {
        if (state == STONE) return false;
        caught++;
        crumble(r, city, bx, bz);
        return true;
    }

    /** Where her head is now, into out (x, y, z). */
    void head(float[] out) {
        Frida.place("Head", x, y + lift, z, heading + (float) Math.PI, SIZE, motion(), motionTime(), out);
        out[1] += 0.1f * SIZE;
    }

    private void crumble(Runner r, City city, float bx, float bz) {
        int most = Frida.blocks();
        if (bp == null || bp.length < most * 3) {
            bp = new float[most * 3];
            bv = new float[most * 3];
            bh = new float[most * 3];
            bt = new float[most * 9];
            turned = new float[most * 9];
            axis = new float[most * 3];
            spin = new float[most * 2];
            bc = new int[most];
        }
        bn = Frida.capture(x, y + lift, z, heading + (float) Math.PI, SIZE, motion(), motionTime(), bp, bt, bh, bc);
        float px = bx, pz = bz, pl = Math.max(0.1f, (float) Math.sqrt(px * px + pz * pz));
        px /= pl;
        pz /= pl;
        for (int i = 0; i < bn; i++) {
            float ox = bp[i * 3] - x, oz = bp[i * 3 + 2] - z, ol = Math.max(0.1f, (float) Math.sqrt(ox * ox + oz * oz));
            float out = 0.8f + rnd.nextFloat() * 1.6f;
            bv[i * 3] = ox / ol * out + px * 1.6f + (rnd.nextFloat() - 0.5f);
            bv[i * 3 + 1] = 0.8f + rnd.nextFloat() * 2.6f;
            bv[i * 3 + 2] = oz / ol * out + pz * 1.6f + (rnd.nextFloat() - 0.5f);
            float ax = rnd.nextFloat() - 0.5f, ay = rnd.nextFloat() - 0.5f, az = rnd.nextFloat() - 0.5f;
            float al = Math.max(1e-3f, (float) Math.sqrt(ax * ax + ay * ay + az * az));
            axis[i * 3] = ax / al;
            axis[i * 3 + 1] = ay / al;
            axis[i * 3 + 2] = az / al;
            spin[i * 2] = 0f;
            spin[i * 2 + 1] = 2f + rnd.nextFloat() * 6f;
        }
        // the plate flies high off her brow, the way the blow went, spinning
        System.arraycopy(Frida.shemAt, 0, plate, 0, 3);
        System.arraycopy(Frida.shemTurn, 0, plateTurn, 0, 9);
        plateV[0] = px * 2.2f + (rnd.nextFloat() - 0.5f);
        plateV[1] = 7.5f;
        plateV[2] = pz * 2.2f + (rnd.nextFloat() - 0.5f);
        plateAxis[0] = pz;
        plateAxis[1] = 0.3f;
        plateAxis[2] = -px;
        float al = (float) Math.sqrt(pz * pz + 0.09f + px * px);
        for (int k = 0; k < 3; k++) plateAxis[k] /= al;
        plateSpin = 0f;
        plateRate = 14f;
        floor = city.ground(x, z);
        speed = 0f;
        set(STONE);
    }

    /** Her blocks falling, bouncing a little and coming to rest on the ground; the plate too. */
    private void rubble(float dt) {
        plateV[1] -= 14f * dt;
        for (int k = 0; k < 3; k++) plate[k] += plateV[k] * dt;
        plateSpin += plateRate * dt;
        if (plate[1] < floor + 0.03f) {
            plate[1] = floor + 0.03f;
            if (plateV[1] < 0f) plateV[1] *= -0.3f;
            plateV[0] *= 0.5f;
            plateV[2] *= 0.5f;
            plateRate *= 0.4f;
        }
        for (int i = 0; i < bn; i++) {
            float h = Math.min(bh[i * 3], Math.min(bh[i * 3 + 1], bh[i * 3 + 2]));
            bv[i * 3 + 1] -= 20f * dt;
            for (int k = 0; k < 3; k++) bp[i * 3 + k] += bv[i * 3 + k] * dt;
            spin[i * 2] += spin[i * 2 + 1] * dt;
            if (bp[i * 3 + 1] - h < floor) {
                bp[i * 3 + 1] = floor + h;
                if (bv[i * 3 + 1] < 0f) bv[i * 3 + 1] *= -0.25f;
                bv[i * 3] *= 0.55f;
                bv[i * 3 + 2] *= 0.55f;
                spin[i * 2 + 1] *= 0.45f;
            }
        }
    }

    /** How long since she was caught for good, seconds; negative while she is loose. */
    float sinceTaken() {
        return state == STONE ? clock - stateAt : -1f;
    }

    /** Where the plate knocked off her brow is now, into out (x, y, z). */
    void plateAt(float[] out) {
        System.arraycopy(plate, 0, out, 0, 3);
    }

    /** How far the plate has spun since it came off, radians. */
    float plateSpin() {
        return plateSpin;
    }

    /** Whether she still burns: loose, or caught a moment ago and not yet gone out. */
    boolean burning() {
        return state != STONE || clock - stateAt < COOL;
    }

    /** Whether her stones are still to be seen. */
    private boolean lying() {
        return clock - stateAt < LIES + SINKS;
    }

    private void set(int s) {
        state = s;
        stateAt = clock;
    }

    /** At a crossing: on, left or right, as it falls out; then on to the next one that way. */
    private void turn() {
        int roll = rnd.nextInt(4);
        int ni = di, nj = dj;
        if (roll == 2) {
            ni = -dj;
            nj = di;
        } else if (roll == 3) {
            ni = dj;
            nj = -di;
        }
        di = ni;
        dj = nj;
        ti += di;
        tj += dj;
    }

    private String motion() {
        if (state == SEIZED) return seizedLying ? "fall" : clock - stateAt < 0.37f ? "hit" : "idle";
        if (state == DOWN || state == RISING) return "fall";
        if (state == JOLTED) return "knockback";
        if (state == WAITING) return speed < 0.4f ? "arms_folded" : "walk";
        if (speed > 6f) return "sprint";
        if (speed > 2.4f) return "jog";
        if (speed > 0.4f) return "walk";
        return "idle";
    }

    private float motionTime() {
        if (state == SEIZED) return seizedLying ? FALL_END : clock - stateAt;
        if (state == DOWN) return FALL_FROM + (clock - stateAt) * FALL_PACE;
        // getting up: the fall played back
        if (state == RISING) return Math.max(0f, FALL_END - (clock - stateAt) * RISE_PACE);
        if (state == JOLTED) return clock - stateAt;
        if (state == WAITING || speed <= 0.4f) return clock - stateAt;
        return stride * (speed > 6f ? 0.6667f : speed > 2.4f ? 0.9333f : 1.3333f);
    }

    private final float[] at = new float[3];

    /** She burns: every block of her alight. */
    void draw(DrawList d) {
        draw(d, true);
    }

    /** The same; lit, the nearest of a hunt (the stones of one caught still glow a moment). */
    void draw(DrawList d, boolean lit) {
        if (state == STONE) {
            if (lying()) stones(d, lit);
            return;
        }
        Frida.draw(d, x, y + lift, z, heading + (float) Math.PI, SIZE, motion(), motionTime(), true);
    }

    /**
     * Her blocks where they fell: still burning for a moment, going out to a dark crust, then
     * cold stone; at the last sinking into the ground.
     */
    private void stones(DrawList d, boolean lit) {
        float k = clock - stateAt, cool = Math.max(0f, 1f - k / COOL);
        float sink = Math.max(0f, k - LIES) / SINKS;
        for (int i = 0; i < bn; i++) {
            // the block's turn as it fell: about its own axis, so far
            spun(axis, i * 3, spin[i * 2], bt, i * 9, turnNow);
            float hy = bh[i * 3 + 1];
            float py = bp[i * 3 + 1] - sink * (hy * 2f + 0.3f);
            int own = bc[i];
            if (cool > 0f) {
                float hot = Frida.hot(own) * cool - (1f - cool) * 0.35f;
                d.box(bp[i * 3], py, bp[i * 3 + 2], bh[i * 3], hy, bh[i * 3 + 2], turnNow,
                        hot > 0.1f ? Frida.burning(hot) : Frida.cold(own), hot > 0.1f ? DrawList.GLOW : DrawList.STONE);
            } else {
                d.box(bp[i * 3], py, bp[i * 3 + 2], bh[i * 3], hy, bh[i * 3 + 2], turnNow, Frida.cold(own), DrawList.STONE);
            }
        }
        // the plate, still alight, the sign cut in it, as long as her stones lie
        spun(plateAxis, plateSpin, plateTurn, turnNow);
        float py = plate[1] - sink * 0.2f;
        float sz = SIZE;
        d.box(plate[0], py, plate[2], 0.075f * sz, 0.045f * sz, 0.008f * sz, turnNow, Frida.SHEM, DrawList.GLOW);
        float nx = turnNow[2], ny = turnNow[5], nz = turnNow[8];
        for (int side = -1; side <= 1; side += 2)
            d.box(plate[0] + nx * side * 0.01f * sz, py + ny * side * 0.01f * sz, plate[2] + nz * side * 0.01f * sz,
                    0.015f * sz, 0.03f * sz, 0.003f * sz, turnNow, Frida.SIGN, DrawList.PLAIN);
    }

    private static void spun(float[] axis, float angle, float[] turn, float[] out) {
        spun(axis, 0, angle, turn, 0, out);
    }

    /** A turn (rows, at to) turned further about a unit axis (at ao) by an angle, into out. */
    static void spun(float[] axis, int ao, float angle, float[] turn, int to, float[] out) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), t = 1f - c;
        float ux = axis[ao], uy = axis[ao + 1], uz = axis[ao + 2];
        float r00 = t * ux * ux + c, r01 = t * ux * uy - s * uz, r02 = t * ux * uz + s * uy;
        float r10 = t * ux * uy + s * uz, r11 = t * uy * uy + c, r12 = t * uy * uz - s * ux;
        float r20 = t * ux * uz - s * uy, r21 = t * uy * uz + s * ux, r22 = t * uz * uz + c;
        for (int col = 0; col < 3; col++) {
            float m0 = turn[to + col], m1 = turn[to + 3 + col], m2 = turn[to + 6 + col];
            out[col] = r00 * m0 + r01 * m1 + r02 * m2;
            out[3 + col] = r10 * m0 + r11 * m1 + r12 * m2;
            out[6 + col] = r20 * m0 + r21 * m1 + r22 * m2;
        }
    }
}
