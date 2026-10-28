package com.shumtugle.freedie;

import java.util.Random;

/**
 * Freedie in the fight: the same giant of stone and brick in her maid's dress, half again as
 * tall as in the opening, coming up the street the way she goes (see Flow): flowing, swaying,
 * her blocks changing places as she comes, never leaving the ground. Between steps she tears
 * parked cars from their places and throws them with her empty hand, gathering herself for
 * the throw, the golden hammer hanging in the other.
 *
 * Every blow that lands knocks one of her blocks out of her for good: it flies off and lies
 * on the street, and she comes on a little smaller and a little slower. Twelve, and she falls
 * apart.
 *
 * Plain timing and angles: this moves her and says where she is; the fight decides what hits
 * her, and she draws herself (see Frida).
 */
final class Giant {
    static final float G = 1.6f;                 // meters to her unit
    static final float HEAD_HIT = 0.6f * G, BODY_HIT = 1.15f * G;
    static final float START_Z = 46f, REACH_Z = 8f;
    /** Blows that bring her down. */
    static final int HITS = 12;

    final Frida body = new Frida();
    final Flow flow = new Flow(body, new Random(0xF10L));
    float x, z;                 // where her feet are
    int hits;                   // blocks knocked out of her
    float stagger;              // seconds she stands, shaken, after a blow
    float hurt;                 // the flash when hit, fading
    boolean down;               // fallen apart
    float raise;                // the throwing arm: 0 down, 1 raised high
    boolean holding;
    int landings;               // her steps: the street feels each one
    float time;
    // her frame on the street: toward the player
    float fx, fz;
    private float speed;

    Giant(float x, float z) {
        this.x = x;
        this.z = z;
        body.size = G;
        body.eyes = 1f;
        face();
        pose();
    }

    private void face() {
        float dx = World.CAM_X * 0.6f - x, dz = -z;
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        fx = dx / l;
        fz = dz / l;
    }

    /** The speed she closes in at now, for throws that lead her. */
    float pace() {
        return speed;
    }

    void update(float dt, float near) {
        time += dt;
        hurt = Math.max(0f, hurt - dt * 3f);
        if (down) return;
        if (stagger > 0f) stagger -= dt;
        // steady on, quicker as she nears; a little slower for every block she has lost; all
        // but still while she lifts a car or stands shaken
        float want = holding ? 0.2f : stagger > 0f ? 0f : (1.5f + 0.9f * near) * (1f - 0.035f * hits);
        speed += (want - speed) * Math.min(1f, dt * 3f);
        face();
        flow.x = x;
        flow.z = z;
        flow.fx = fx;
        flow.fz = fz;
        flow.gathered = holding || stagger > 0f;
        flow.update(dt, 0f, -speed);
        x = flow.x;
        z = flow.z;
        landings = flow.steps;
        pose();
    }

    /** True when she has come close enough to strike the player. */
    boolean arrived() {
        return z < REACH_Z;
    }

    /** Her joints from how she moves now, her empty arm raised with a car if she has one. */
    void pose() {
        Frida f = body;
        // smaller for every block she has lost
        f.size = G * (1f - 0.012f * hits);
        flow.pose();
        if (raise > 0f) {
            f.ax[Frida.ARM_B] = f.ax[Frida.ARM_B] * (1f - raise) + raise * 2.75f;
            f.az[Frida.ARM_B] = -0.14f - raise * 0.1f;
            f.ax[Frida.FORE_B] = raise * 0.45f;
        }
        f.hurt = hurt;
        f.flare = 0.15f;
        f.pose();
    }

    // ---- where things are ----

    void headCenter(float[] out) {
        body.headCenter(out);
    }

    void chest(float[] out) {
        body.chest(out);
    }

    /** Her hips, low in the middle of her. */
    void hips(float[] out) {
        body.place(Frida.ROOT, 0f, 1.4f, 0f, out);
    }

    /** Over the empty fist: where a car she lifts sits. */
    void hand(float[] out) {
        body.place(Frida.FORE_B, -1.34f, 0.45f, -0.04f, out);
    }

    private final float[] t3 = new float[3], t4 = new float[3];

    /** Distance from a point to her bulk, for blows that land on her. */
    float bodyDist(float px, float py, float pz) {
        chest(t3);
        float dx = px - t3[0], dz = pz - t3[2];
        float hd = (float) Math.sqrt(dx * dx + dz * dz);
        body.place(Frida.ROOT, 0f, 0.2f, 0f, t4);
        headCenter(t3);
        float lo = t4[1], hi = t3[1] + 0.3f * body.size;
        float dy = py < lo ? lo - py : py > hi ? py - hi : 0f;
        return (float) Math.sqrt(hd * hd + dy * dy);
    }
}
