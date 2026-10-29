package com.shumtugle.freedie;

import java.util.Random;

/**
 * How Freedie gets about: not in steps and not in leaps, but flowing, the way a drop of water
 * would walk if it were made of blocks. She never leaves the ground; she sways from side to
 * side, dips and rises like a spring at every step, her feet sliding forward in turn, her arms
 * swinging the other way. And at every step two of her blocks of a kind change places, one
 * rolling up over the top of her and the other tumbling down her front, so that what was a
 * shin may come down as a forearm, and her head may go round to a shoulder. Standing still she
 * gathers into herself: no block sets out, only her breath.
 *
 * Whoever moves her (the fight, a game in the yard, the bowling alley) says where she is, the
 * way she faces and the way she goes, so fast; this sets her joints for the frame and sends
 * her blocks on their way. It knows nothing of where she is going or why.
 */
final class Flow {
    // her leg: the hip over the knee, the knee over the sole, at rest
    private static final float HIP = 2.0f, THIGH = 0.85f, SHIN = 1.15f;
    /** Meters of ground to a step, at her size of one; a step's blocks take this share of a step's time to arrive. */
    static final float STEP = 0.75f, TRAVEL = 1.3f;

    final Frida f;
    private final Random rnd;
    /** Where she is, the way she faces (a unit along the ground), and the floor under her. */
    float x, z, fx = 0f, fz = -1f, y;
    /** How fast she goes now, meters a second, along the ground; and how many steps she has taken. */
    float speed;
    int steps;
    /** Gathered into herself: no block sets out, the sway dies down. */
    boolean gathered;
    /**
     * How much she lives standing still, 0 to 1: at 0 she only breathes; above it she keeps
     * swaying and springing on the spot, treading, and her blocks keep changing places.
     */
    float alive;
    private float phase, move, time;

    Flow(Frida f, Random rnd) {
        this.f = f;
        this.rnd = rnd;
    }

    /**
     * A frame: she goes by (vx, vz) meters a second, faces (fx, fz), and her blocks travel. A
     * new step sends two of a kind on their way to each other's places.
     */
    void update(float dt, float vx, float vz) {
        time += dt;
        float v = (float) Math.sqrt(vx * vx + vz * vz);
        speed = v;
        x += vx * dt;
        z += vz * dt;
        // the sway grows as she gets going and dies down when she stops
        float want = gathered ? 0f : Math.max(alive, Math.min(1f, v / 0.8f));
        move += (want - move) * Math.min(1f, dt * 4f);
        float stepLen = STEP * f.size;
        float before = phase;
        phase += Math.max(v, gathered ? 0f : Math.max(0.15f * move, 0.7f * alive)) * dt / stepLen;
        if ((int) phase != (int) before) {
            steps++;
            if (!gathered && move > 0.3f) swaps(stepLen / Math.max(0.5f, Math.max(v, 0.7f * alive)) * TRAVEL);
        }
        f.travel(dt);
    }

    /** Two blocks of a kind set out; now and then two pairs at once. */
    private void swaps(float time) {
        int pairs = rnd.nextFloat() < 0.15f ? 2 : 1;
        for (int p = 0; p < pairs; p++) {
            int kind = 1 + rnd.nextInt(3);
            int a = pick(kind, -1), b = pick(kind, a);
            if (a >= 0 && b >= 0) f.swap(a, b, Math.max(0.35f, time), rnd);
        }
    }

    /** A free block of a kind, at random, not the one given. */
    private int pick(int kind, int not) {
        int n = f.parts(), start = rnd.nextInt(n);
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            if (i != not && f.kind(i) == kind && f.free(i)) return i;
        }
        return -1;
    }

    /**
     * Her joints for this frame: the legs sliding in turn, the hips dipping at each step and
     * rising between, the body swaying side to side, the arms swinging against the legs, the
     * head up. Whoever moves her may set an arm afterward (a raised hand, a carried thing).
     */
    void pose() {
        f.rest(time);
        float s = phase * (float) Math.PI;          // half a turn a step
        float sw = (float) Math.sin(s), m = move;
        // the legs: one slides forward as the other goes back, the forward one bending a little more
        float bendA = 0.22f + 0.18f * Math.max(0f, sw) * m, bendB = 0.22f + 0.18f * Math.max(0f, -sw) * m;
        f.ax[Frida.LEG_A] = bendA + 0.32f * sw * m;
        f.ax[Frida.SHIN_A] = -bendA * 1.8f;
        f.ax[Frida.LEG_B] = bendB - 0.32f * sw * m;
        f.ax[Frida.SHIN_B] = -bendB * 1.8f;
        float b = Math.max(bendA, bendB);
        float hip = THIGH * (float) Math.cos(b) + SHIN * (float) Math.cos(0.8f * b);
        // a spring: down as a foot comes down, up between
        float bob = 0.09f * m * (float) Math.cos(2f * s);
        float breathe = (float) Math.sin(time * 2.4f) * 0.03f;
        f.lift = hip - HIP - 0.06f * m + bob + breathe;
        f.ax[Frida.CHEST] = -0.24f - 0.06f * m * (1f - (float) Math.cos(2f * s)) * 0.5f;
        f.ax[Frida.HEAD] = 0.12f - 0.05f * m * (float) Math.cos(2f * s);
        f.az[Frida.ROOT] = 0.08f * m * sw;
        f.ay[Frida.ROOT] = (float) Math.atan2(-fx, -fz);
        // the arms against the legs
        f.ax[Frida.ARM_A] = 0.1f - 0.35f * sw * m;
        f.az[Frida.ARM_A] = 0.14f;
        f.ax[Frida.FORE_A] = 0.25f;
        f.ax[Frida.HAMMER] = 1.0f;
        f.ax[Frida.ARM_B] = 0.05f + 0.35f * sw * m;
        f.az[Frida.ARM_B] = -0.14f;
        f.ax[Frida.FORE_B] = 0.25f;
        f.x = x;
        f.z = z;
        f.y = y;
    }
}
