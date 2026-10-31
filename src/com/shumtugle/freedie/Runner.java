package com.shumtugle.freedie;

/**
 * Freedie on foot, and the eye that follows her: she runs while a finger is on the glass and
 * slows to a stop when it lifts; the finger leads her, as it led the aim: moved sideways, she
 * turns by as much as it moved, wherever it lies on the glass; flicked up, she jumps; flicked
 * down, she slides. Houses, lamp posts, palms, cars and people stop her; the curb is a step she takes in her
 * stride.
 *
 * Her heading is the way she runs, in the eye's own terms (0 along +z, growing toward +x); the
 * eye sits behind her and a little above, turning after her a moment late.
 */
final class Runner {
    static final float SIZE = 1.3f, RADIUS = 0.55f;
    static final float TOP_SPEED = 10f, PICK_UP = 1.1f, BRAKE = 12f;
    static final float GRAVITY = 22f, JUMP = 8f;
    // the finger: frame pixels moved sideways for a quarter turn; how far and how fast a flick goes
    static final float QUARTER = 110f, FLICK_PX = 26f, FLICK_S = 0.16f;
    // the eye: so far behind her, so high, looking so far ahead of her
    static final float BACK = 6f, HIGH = 3.4f, AHEAD = 16f, LOOK_UP = 2.4f;

    float x = -1.9f, z = 6f, y, vy;
    float heading;                  // radians: 0 runs along +z
    float speed;
    boolean holding;
    // what she is doing, and since when (seconds of her own clock)
    static final int RUN = 0, AIR = 1, SLIDE = 2;
    int state = RUN;
    float stateAt, clock, stride;
    // the eye
    float eyeX, eyeY, eyeZ, eyeYaw, eyePitch;
    private boolean placed;
    // throwing: she stops, turns to (faceX, faceZ) and throws, the finger not heard till it is over
    private float lockAt = -1f, lockFor, faceX, faceZ;

    // the finger: where it was last, and where it was a moment ago, for a flick
    private float lastX, lastY, flickY, flickAt;

    void touch(int type, float fx, float fy) {
        if (type == World.DOWN) {
            holding = true;
            lastX = fx;
            lastY = fy;
            flickY = fy;
            flickAt = clock;
        } else if (type == World.MOVE && holding) {
            heading += (fx - lastX) / QUARTER * (float) (Math.PI / 2);
            lastX = fx;
            lastY = fy;
            // a flick: far enough, quickly enough, up or down
            if (clock - flickAt > FLICK_S) {
                flickY = fy;
                flickAt = clock;
            } else if (flickY - fy > FLICK_PX) {
                jump();
                flickY = fy;
                flickAt = clock;
            } else if (fy - flickY > FLICK_PX) {
                slide();
                flickY = fy;
                flickAt = clock;
            }
        } else if (type == World.UP) {
            holding = false;
        }
    }

    private void jump() {
        if (state == AIR) return;
        state = AIR;
        stateAt = clock;
        vy = JUMP;
    }

    private void slide() {
        if (state != RUN || speed < 3f) return;
        state = SLIDE;
        stateAt = clock;
    }

    /** She stops where she is, turns to (fx, fz) and throws, for so many seconds. */
    void lock(float seconds, float fx, float fz) {
        lockAt = clock;
        lockFor = seconds;
        faceX = fx;
        faceZ = fz;
        if (state == SLIDE) {
            state = RUN;
            stateAt = clock;
        }
    }

    /** How long she has been throwing, seconds; negative when she is not. */
    float throwing() {
        return lockAt >= 0f && clock - lockAt < lockFor ? clock - lockAt : -1f;
    }

    void update(float dt, City city, Cars cars, Walkers walkers) {
        clock += dt;
        if (throwing() >= 0f) {
            // stopped short, turned to what she throws at
            speed = Math.max(0f, speed - 40f * dt);
            float want = (float) Math.atan2(faceX - x, faceZ - z), d = want - heading;
            while (d > Math.PI) d -= 2f * (float) Math.PI;
            while (d < -Math.PI) d += 2f * (float) Math.PI;
            heading += Math.max(-10f * dt, Math.min(10f * dt, d));
        } else if (holding) speed += (TOP_SPEED - speed) * Math.min(1f, dt * PICK_UP);
        else speed = Math.max(0f, speed - BRAKE * dt);
        if (state == SLIDE) {
            // a slide carries her on, a little slower, then she is up and running again
            speed = Math.max(speed - 3f * dt, 2f);
            if (clock - stateAt > 0.9f) {
                state = RUN;
                stateAt = clock;
            }
        }
        float fx = (float) Math.sin(heading), fz = (float) Math.cos(heading);
        x += fx * speed * dt;
        z += fz * speed * dt;
        float[] p = {x, z};
        if (city.push(p, RADIUS) | cars.push(p, RADIUS) | walkers.push(p, RADIUS)) {
            // ran into something: what was across her way is lost
            float mx = (p[0] - x), mz = (p[1] - z), m = (float) Math.sqrt(mx * mx + mz * mz);
            if (m > 1e-4f) {
                float into = -(fx * mx + fz * mz) / m;
                if (into > 0f) speed *= 1f - 0.8f * into;
            }
        }
        x = p[0];
        z = p[1];
        float ground = city.ground(x, z);
        if (state == AIR) {
            vy -= GRAVITY * dt;
            y += vy * dt;
            if (y <= ground && vy < 0f) {
                y = ground;
                vy = 0f;
                state = RUN;
                stateAt = clock;
            }
        } else {
            // the step up onto the pavement taken in a moment, the step down as quick
            y += (ground - y) * Math.min(1f, dt * 18f);
        }
        // the stride: run on in step with the ground going by
        stride += dt * Math.max(0.3f, speed) / strideSpeed();
        eye(dt, city);
    }

    /** How fast the motion now shown carries her when played as it was taken, meters a second. */
    private float strideSpeed() {
        if (speed > 6f) return 7f;
        if (speed > 2.4f) return 3.3f;
        return 1.4f;
    }

    private String motion() {
        if (throwing() >= 0f && state != AIR) return "throw";
        if (state == AIR) return "leap_air";
        if (state == SLIDE) return clock - stateAt < 0.25f ? "slide_start" : clock - stateAt < 0.7f ? "slide" : "slide_end";
        if (speed > 6f) return "sprint";
        if (speed > 2.4f) return "jog";
        if (speed > 0.4f) return "walk";
        return "idle";
    }

    private float motionTime() {
        if (throwing() >= 0f && state != AIR) return throwing();
        if (state == AIR) return (clock - stateAt) * 1.4f + 0.3f;
        if (state == SLIDE) {
            float t = clock - stateAt;
            return t < 0.25f ? t * 2.4f : t < 0.7f ? t - 0.25f : (t - 0.7f) * 2.2f;
        }
        if (speed <= 0.4f) return clock;
        return stride * strideLength();
    }

    private float strideLength() {
        if (speed > 6f) return 0.6667f;
        if (speed > 2.4f) return 0.9333f;
        return 1.3333f;
    }

    /** The eye: behind her, a little above, turning after her a moment late, kept out of walls. */
    private void eye(float dt, City city) {
        if (!placed) {
            eyeYaw = heading;
            placed = true;
        }
        float d = heading - eyeYaw;
        while (d > Math.PI) d -= 2f * (float) Math.PI;
        while (d < -Math.PI) d += 2f * (float) Math.PI;
        eyeYaw += d * Math.min(1f, dt * 4.5f);
        float fx = (float) Math.sin(eyeYaw), fz = (float) Math.cos(eyeYaw);
        float[] p = {x - fx * BACK, z - fz * BACK};
        city.push(p, 0.4f);
        eyeX = p[0];
        eyeZ = p[1];
        eyeY = y + HIGH;
        float tx = x + fx * AHEAD, tz = z + fz * AHEAD, ty = y + LOOK_UP;
        float dx = tx - eyeX, dz = tz - eyeZ;
        eyePitch = (float) Math.atan2(ty - eyeY, Math.sqrt(dx * dx + dz * dz));
    }

    /** Where one of her bones is now, into out (x, y, z). */
    void bone(String name, float[] out) {
        Frida.place(name, x, y, z, heading + (float) Math.PI, SIZE, motion(), motionTime(), out);
    }

    void draw(DrawList d) {
        // the figure faces -z at no turn: turned half round, she runs the way she is headed
        Frida.draw(d, x, y, z, heading + (float) Math.PI, SIZE, motion(), motionTime());
    }
}
