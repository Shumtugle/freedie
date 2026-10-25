package com.shumtugle.freedie;

/**
 * Freedie in the fight: the same giant of stone and brick in her maid's dress, half again as
 * tall as in the opening, coming up the street in leaps. She crouches, springs, comes down
 * with a blow that shakes the street, and stands a moment to gather herself; between leaps
 * she tears parked cars from their places and throws them with her empty hand, the golden
 * hammer hanging in the other. Her weak points glow: two knees and the eyes. Break one knee
 * and her leaps grow short; break both and she can only drag herself along on her knees,
 * her head brought low; three blows to the head and she falls apart.
 *
 * Plain timing and angles: this moves her and says where her weak points are; the fight
 * decides what hits her, and she draws herself (see Frida).
 */
final class Giant {
    static final float G = 1.6f;                 // meters to her unit
    static final float KNEE_HIT = 0.55f * G, HEAD_HIT = 0.6f * G, BODY_HIT = 1.15f * G;
    static final float START_Z = 46f, REACH_Z = 8f;
    static final int STAND = 0, CROUCH = 1, AIR = 2, LAND = 3, CRAWL = 4;
    // her leg: the hip over the knee, the knee over the sole, at rest
    private static final float HIP = 2.0f, THIGH = 0.85f, SHIN = 1.15f;

    final Frida body = new Frida();
    float x, z;                 // where her feet are
    int kneeL = 1, kneeR = 1, head = 3;
    float stagger;              // seconds of stunned stillness left
    float hurt;                 // the flash when hit, fading
    boolean down;               // fallen apart
    float raise;                // the throwing arm: 0 down, 1 raised high
    boolean holding;
    int act = STAND;
    float actT, jumpIn = 2.0f;
    int landings;               // how many times she has come down: the street shakes for each
    float time;
    private float z0, z1, x0, x1, jumpH;
    // a leap the fight asks for, to a given spot, instead of one straight at the player
    private boolean hopping;
    private float hopX, hopZ;
    private float squat, air;   // how low she crouches, how high she is
    // a leap rocks her onto one side, the other side the next time; the arms swing after the body
    private float roll, armU = 0.35f, armF = 0.65f;
    // her frame on the street: toward the player
    float fx, fz;

    Giant(float x, float z) {
        this.x = x;
        this.z = z;
        body.size = G;
        body.eyes = 1f;
        face();
        pose();
    }

    int weakLeft() {
        return kneeL + kneeR + head;
    }

    private void face() {
        float dx = World.CAM_X * 0.6f - x, dz = -z;
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        fx = dx / l;
        fz = dz / l;
    }

    /** The speed she closes in at, on average, for throws that lead her. */
    float pace() {
        if (act == AIR) return z0 - z1;
        return act == CRAWL ? 0.55f : 0f;
    }

    /** How far she comes in one leap; shorter on a broken knee. */
    private float leap(float near) {
        float k = kneeL + kneeR == 2 ? 1f : 0.55f;
        return (6f + 2.5f * near) * k;
    }

    void update(float dt, float near) {
        time += dt;
        hurt = Math.max(0f, hurt - dt * 3f);
        if (down) return;
        // the arms are heavy: they follow the body a few frames late
        float wantU = act == AIR ? -0.9f : 0.35f + (float) Math.sin(time * 1.7f) * 0.06f;
        float wantF = act == AIR ? -0.4f : 0.65f;
        armU += (wantU - armU) * Math.min(1f, dt * 7f);
        armF += (wantF - armF) * Math.min(1f, dt * 7f);
        // the roll: out to one side through a leap, back upright as she lands
        float side = (landings & 1) == 0 ? 1f : -1f;
        float wantRoll = act == AIR ? side * 0.11f * (float) Math.sin(Math.PI * Math.min(1f, actT)) : 0f;
        roll += (wantRoll - roll) * Math.min(1f, dt * 9f);
        if (stagger > 0f && act != AIR) {
            stagger -= dt;
            squat += ((act == CRAWL ? 1f : 0.3f) - squat) * Math.min(1f, dt * 4f);
            pose();
            return;
        }
        actT += dt;
        if (kneeL + kneeR == 0 && act != AIR) act = CRAWL;
        switch (act) {
            case STAND:
                if (!holding) jumpIn -= dt;
                squat += (0f - squat) * Math.min(1f, dt * 6f);
                if (jumpIn <= 0f && !holding) {
                    act = CROUCH;
                    actT = 0f;
                }
                break;
            case CROUCH:
                squat = Math.min(1f, actT / 0.45f);
                if (actT >= 0.45f) {
                    act = AIR;
                    actT = 0f;
                    z0 = z;
                    x0 = x1 = x;
                    z1 = z - leap(near);
                    if (hopping) {
                        x1 = hopX;
                        z1 = hopZ;
                        hopping = false;
                    }
                    jumpH = (kneeL + kneeR == 2 ? 3.2f : 1.8f) / G;
                }
                break;
            case AIR: {
                float k = Math.min(1f, actT);
                z = z0 + (z1 - z0) * k;
                x = x0 + (x1 - x0) * k;
                air = jumpH * 4f * k * (1f - k);
                squat = Math.max(0f, 1f - k * 3f) * 0.6f;
                if (k >= 1f) {
                    air = 0f;
                    act = LAND;
                    actT = 0f;
                    landings++;
                }
                break;
            }
            case LAND:
                squat = 1f - Math.min(1f, actT / 0.6f) * 0.7f;
                if (actT >= 0.6f) {
                    act = STAND;
                    actT = 0f;
                    jumpIn = 0.9f + 1.0f * (1f - near);
                }
                break;
            case CRAWL:
                squat = 1f;
                z -= 0.55f * dt;
                break;
            default:
                break;
        }
        face();
        pose();
    }

    /** A leap to a spot of the fight's choosing: crouch, spring, come down there. */
    void hop(float tx, float tz) {
        hopping = true;
        hopX = tx;
        hopZ = tz;
        act = CROUCH;
        actT = 0f;
    }

    /** True when she has come close enough to strike the player. */
    boolean arrived() {
        return z < REACH_Z;
    }

    /** True while she is in the air: blows glance off something moving that fast. */
    boolean flying() {
        return act == AIR;
    }

    // ---- the pose ----

    private boolean kneeling() {
        return kneeL + kneeR == 0;
    }

    /** The bend of a leg: thigh forward, shin back; deep in a crouch, more on a broken knee. */
    private float bend(boolean right) {
        float lame = (right ? kneeR : kneeL) == 0 ? 0.25f : 0f;
        return 0.15f + squat * 0.85f + lame;
    }

    /** Her joints set from how she moves now, and her weak points found again. */
    void pose() {
        Frida f = body;
        f.rest(time);
        float hip;
        if (kneeling()) {
            // down on both knees, the shins flat behind her
            for (int s = 0; s < 2; s++) {
                f.ax[s == 0 ? Frida.LEG_A : Frida.LEG_B] = 0.25f;
                f.ax[s == 0 ? Frida.SHIN_A : Frida.SHIN_B] = -1.75f;
            }
            hip = THIGH * (float) Math.cos(0.25f) + 0.3f;
        } else {
            hip = 0f;
            for (int s = 0; s < 2; s++) {
                // the hammer side is her right
                float b = bend(s == 0);
                f.ax[s == 0 ? Frida.LEG_A : Frida.LEG_B] = b;
                f.ax[s == 0 ? Frida.SHIN_A : Frida.SHIN_B] = -b * 1.8f;
                hip = Math.max(hip, THIGH * (float) Math.cos(b) + SHIN * (float) Math.cos(0.8f * b));
            }
        }
        float breathe = (float) Math.sin(time * 2.4f) * 0.03f;
        f.lift = hip - HIP + air + breathe;
        // stooping, more so in a crouch; the head up, looking at the player
        f.ax[Frida.CHEST] = -(0.22f + squat * 0.3f) - (kneeling() ? 0.25f : 0f);
        f.ax[Frida.HEAD] = 0.12f;
        f.az[Frida.ROOT] = roll;
        f.ay[Frida.ROOT] = (float) Math.atan2(-fx, -fz);
        // the hammer arm hangs and swings with the body
        f.ax[Frida.ARM_A] = armU * 0.9f - 0.2f;
        f.az[Frida.ARM_A] = 0.14f;
        f.ax[Frida.FORE_A] = armF * 0.35f;
        f.ax[Frida.HAMMER] = 1.2f - armF * 0.3f;
        // the empty one swings too, or goes up over her head with a car
        if (raise > 0f) {
            f.ax[Frida.ARM_B] = armU * 0.9f * (1f - raise) + raise * 2.75f;
            f.az[Frida.ARM_B] = -0.14f - raise * 0.1f;
            f.ax[Frida.FORE_B] = raise * 0.45f;
        } else {
            f.ax[Frida.ARM_B] = armU * 0.9f - 0.1f;
            f.az[Frida.ARM_B] = -0.14f;
            f.ax[Frida.FORE_B] = armF * 0.35f;
        }
        f.x = x;
        f.z = z;
        f.kneeOutA = kneeR == 0;
        f.kneeOutB = kneeL == 0;
        f.head = head;
        f.hurt = hurt;
        // the weak points throb, so the eye finds them through the fire
        f.flare = 0.3f + 0.25f * (float) Math.sin(time * 7f);
        f.pose();
    }

    // ---- where things are ----

    void knee(boolean right, float[] out) {
        body.knee(right, out);
    }

    void headCenter(float[] out) {
        body.headCenter(out);
    }

    void chest(float[] out) {
        body.chest(out);
    }

    /** Over the empty fist: where a car she lifts sits. */
    void hand(float[] out) {
        body.place(Frida.FORE_B, -1.34f, 0.45f, -0.04f, out);
    }

    private final float[] t3 = new float[3], t4 = new float[3];

    /** Distance from a point to her bulk, for blows that land on the body. */
    float bodyDist(float px, float py, float pz) {
        chest(t3);
        float dx = px - t3[0], dz = pz - t3[2];
        float hd = (float) Math.sqrt(dx * dx + dz * dz);
        body.place(Frida.ROOT, 0f, 1.6f, 0f, t4);
        headCenter(t3);
        float lo = t4[1], hi = t3[1] + 0.3f * G;
        float dy = py < lo ? lo - py : py > hi ? py - hi : 0f;
        return (float) Math.sqrt(hd * hd + dy * dy);
    }
}
