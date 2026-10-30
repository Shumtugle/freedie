package com.shumtugle.freedie;

import java.util.Random;

/**
 * A game on one of the bridge's screens: Freedie pitches, the player bats with the hammer.
 * She stands a dozen meters off facing the player, and over and over throws a ball overhand
 * at him, a little faster or slower, higher or lower, this side or that. A touch swings the
 * hammer through the air before the eye, at the place touched; met at the right moment and
 * near enough, the ball flies off the other way, early pulled to one side, late pushed to the
 * other, high or low as the hammer met it. A ball sent back at her knocks her back. Only what
 * is won is counted. The back key leaves.
 *
 * For now with nothing round it but the ground and the sky: a test of the pitch and the swing.
 */
final class Baseball {
    // where she stands, how big; where the eye is
    static final float HER_Z = 12f, SIZE = 1.3f, EYE_Y = 1.7f;
    // the moment of her throw the ball leaves her hand, seconds into it; how long she waits between throws
    static final float RELEASE = 0.333f, WAIT = 1.1f;
    // the swing: how long; the stretch of it the hammer sweeps the space before the eye, from and to
    static final float SWING = 0.3f, SWEEP0 = 0.06f, SWEEP1 = 0.2f;
    // how far the ball may be from where the hammer goes and still be met: frame pixels, and meters ahead of the eye
    static final float REACH_PX = 32f, NEAR_Z = 0.4f, FAR_Z = 2.6f, SWEET_Z = 1.3f;
    static final float BALL_R = 0.075f, G = -9.8f;

    private final World.Host host;
    private final Random rnd = new Random(7);
    float t;
    // her: which motion, since when
    private String motion = "idle";
    private float motionAt;
    private boolean thrown;
    // the ball: where, how fast, whether in the air, hit, and when it was hit
    private final float[] p = new float[3], v = new float[3];
    private boolean flying, hit, bounced;
    private float flyT;
    // the swing: when it began (below 0, none), where the finger aimed
    private float swingAt = -9f, aimX, aimY;
    private boolean tested;
    // the count of balls hit, and of those that went back at her
    int hits, struck;
    private float hitFlash = -1f;
    private DrawList last;
    private final float[] scr = new float[2], hand = new float[3], pose = new float[12];

    Baseball(World.Host host) {
        this.host = host;
        motionAt = 0f;
    }

    /** A touch: the hammer swings at the place touched, unless a swing is still going. */
    void touch(int type, float x, float y) {
        if (type != World.DOWN || t - swingAt < SWING) return;
        swingAt = t;
        aimX = x;
        aimY = y;
        tested = false;
        host.sound(Synth.WHOOSH, 0.8f, 1.3f);
    }

    void update(float dt) {
        t += dt;
        if (hitFlash >= 0f && (hitFlash += dt) > 0.4f) hitFlash = -1f;
        float since = t - motionAt;
        // her round: wait, throw, and the ball away at the moment it leaves her hand
        if (motion.equals("idle") && since > WAIT && !flying) start("throw");
        if (motion.equals("throw")) {
            if (!thrown && since >= RELEASE) pitch();
            if (since > 1.37f) start("idle");
        }
        if ((motion.equals("knockback") || motion.equals("hit")) && since > 1.0f) start("idle");
        if (flying) ball(dt);
        // while the hammer sweeps the space before the eye, does it meet the ball?
        float in = t - swingAt;
        if (!tested && swingAt >= 0f && in >= SWEEP0 && in <= SWEEP1 && meet()) tested = true;
    }

    private void start(String m) {
        motion = m;
        motionAt = t;
        thrown = false;
    }

    /** The ball leaves her hand for a spot before the player, a little different each time. */
    private void pitch() {
        thrown = true;
        Frida.place("hand_r", 0f, 0f, HER_Z, 0f, SIZE, "throw", RELEASE, hand);
        p[0] = hand[0];
        p[1] = hand[1];
        p[2] = hand[2];
        float tx = -0.3f + rnd.nextFloat() * 0.6f, ty = 0.9f + rnd.nextFloat() * 0.6f, tz = 0.2f;
        float time = 0.62f + rnd.nextFloat() * 0.3f;
        v[0] = (tx - p[0]) / time;
        v[2] = (tz - p[2]) / time;
        v[1] = (ty - p[1]) / time - 0.5f * G * time;
        flying = true;
        hit = false;
        bounced = false;
        flyT = 0f;
        host.sound(Synth.WHOOSH, 0.6f, 0.8f);
    }

    private void ball(float dt) {
        flyT += dt;
        v[1] += G * dt;
        for (int k = 0; k < 3; k++) p[k] += v[k] * dt;
        // a ball sent back at her: does it reach her?
        if (hit && motion.equals("idle") || hit && motion.equals("throw")) {
            float dx = p[0], dz = p[2] - HER_Z;
            if (Math.abs(dz) < 0.5f && Math.abs(dx) < 0.6f * SIZE && p[1] > 0.2f && p[1] < 2.4f * SIZE) {
                start("knockback");
                struck++;
                host.sound(Synth.STONE, 1f, 0.6f);
                host.sound(Synth.GROUND, 0.8f, 0.8f);
                host.buzz(40);
                v[2] = -Math.abs(v[2]) * 0.3f;
                v[0] *= 0.3f;
            }
        }
        if (p[1] < BALL_R && v[1] < 0f) {
            // on the ground: a bounce, losing most of its way
            p[1] = BALL_R;
            v[1] = -v[1] * 0.45f;
            v[0] *= 0.7f;
            v[2] *= 0.7f;
            if (!bounced) host.sound(Synth.TOCK, 0.5f, 0.6f);
            bounced = true;
        }
        // gone past the player, far off, or come to rest: the next one
        if (p[2] < -3f || p[2] > 80f || Math.abs(p[0]) > 40f || flyT > 5f || bounced && Math.abs(v[1]) < 0.4f && flyT > 1.5f)
            flying = false;
    }

    /** The hammer sweeping: met if the ball is near enough ahead and near where the finger aimed. */
    private boolean meet() {
        if (!flying || hit || last == null) return false;
        if (p[2] < NEAR_Z || p[2] > FAR_Z) return false;
        if (!last.project(p[0], p[1], p[2], scr)) return false;
        float dx = scr[0] - aimX, dy = scr[1] - aimY;
        if (dx * dx + dy * dy > REACH_PX * REACH_PX) return false;
        hit = true;
        hits++;
        hitFlash = 0f;
        // early (the ball still far) pulls it to one side, late pushes it to the other; the hammer
        // under the ball lifts it, over it beats it down
        float early = (p[2] - SWEET_Z) / (FAR_Z - NEAR_Z);
        float lift = Math.max(-0.25f, Math.min(0.75f, 0.25f + dy / REACH_PX * 0.5f));
        float speed = 22f - Math.abs(early) * 10f;
        float side = -early * 1.2f + (rnd.nextFloat() - 0.5f) * 0.15f;
        v[0] = (float) Math.sin(side) * speed * (float) Math.cos(lift);
        v[2] = (float) Math.cos(side) * speed * (float) Math.cos(lift);
        v[1] = (float) Math.sin(lift) * speed;
        host.sound(Synth.WOOD, 1.2f, 1.3f);
        host.sound(Synth.CLANG, 0.4f, 1.6f);
        host.buzz(35);
        return true;
    }

    // ---- drawing ----

    /** The eye: where the player stands, looking down the way at her. */
    void frame(DrawList d, int w) {
        d.camera(0f, EYE_Y, 0f, 0f, -0.06f, w * 1.9f, 0.05f, 500f);
        last = d;
    }

    void draw(DrawList d) {
        // the ground, plain
        d.box(0f, -0.25f, 30f, 60f, 0.25f, 60f, null, 0x8E8A84, DrawList.PLAIN);
        // where she throws from, and where the player stands
        d.box(0f, 0.01f, HER_Z, 0.9f, 0.01f, 0.9f, null, 0xB9AE9C, DrawList.PLAIN);
        d.box(0f, 0.01f, 0.6f, 0.25f, 0.01f, 0.25f, null, 0xEFE9E6, DrawList.PLAIN);
        Frida.draw(d, 0f, 0f, HER_Z, 0f, SIZE, motion, t - motionAt);
        if (flying || motion.equals("throw") && !thrown) {
            if (!thrown) {
                // in her hand till she lets go
                Frida.place("hand_r", 0f, 0f, HER_Z, 0f, SIZE, "throw", t - motionAt, hand);
                d.ball(hand[0], hand[1], hand[2], BALL_R * SIZE, null, 0xF4F1EA, DrawList.RUBBER);
            } else d.ball(p[0], p[1], p[2], BALL_R, null, 0xF4F1EA, DrawList.RUBBER);
        }
        hammer(d);
    }

    /** The hammer: held ready over the right shoulder, swung through the place touched and round to the left. */
    private void hammer(DrawList d) {
        float s = swingAt < 0f ? 1f : Math.min(1f, (t - swingAt) / SWING);
        boolean swinging = s < 1f;
        // ready, at the middle of the swing, and after it
        float[] v0 = {0.32f, -0.62f, 1.3f}, u0 = {-0.2f, 0.9f, 0.2f}, w0 = {0.94f, 0.2f, -0.28f};
        float[] v1 = {0f, -0.32f, 1.2f}, u1 = {-0.55f, 0.1f, 0.85f}, w1 = {0f, 1f, 0f};
        float[] v2 = {-0.45f, -0.55f, 0.95f}, u2 = {-0.9f, 0.05f, -0.35f}, w2 = {0f, 1f, 0f};
        float[] vv = new float[3], uu = new float[3], ww = new float[3];
        if (!swinging) {
            vv = v0;
            uu = u0;
            ww = w0;
        } else {
            float a = s < 0.5f ? s / 0.5f : (s - 0.5f) / 0.5f;
            float[] va = s < 0.5f ? v0 : v1, vb = s < 0.5f ? v1 : v2, ua = s < 0.5f ? u0 : u1, ub = s < 0.5f ? u1 : u2;
            float[] wa = s < 0.5f ? w0 : w1, wb = s < 0.5f ? w1 : w2;
            for (int k = 0; k < 3; k++) {
                vv[k] = va[k] + (vb[k] - va[k]) * a;
                uu[k] = ua[k] + (ub[k] - ua[k]) * a;
                ww[k] = wa[k] + (wb[k] - wa[k]) * a;
            }
            // toward the place touched, at the middle of the swing
            float k = 1f - Math.abs(s - 0.5f) * 2f;
            vv[0] += (aimX / d.w - 0.5f) * 0.6f * k;
            vv[1] -= (aimY / d.h - 0.5f) * 0.6f * k;
        }
        Hammer.held(d, vv[0], vv[1], vv[2], uu[0], uu[1], uu[2], ww[0], ww[1], ww[2], pose);
        Hammer.draw(d, pose, Hammer.PLAIN, 1f);
    }

    /** The flat layer: the count of balls hit, big, at the top. */
    void flat(Px f) {
        String s = String.valueOf(hits);
        int k = 4, w = PixelFont.width(s, k);
        int c = hitFlash >= 0f ? 0xFFFFE27A : 0xFFFFF6D8;
        PixelFont.draw(f, s, (f.w - w) / 2, 18, k, c);
    }

    /** For the desktop stand: the ball now, if it is in the air coming at the player, as a frame point; else null. */
    float[] standBall() {
        if (!flying || hit || last == null || p[2] > 2.9f || p[2] < 1f) return null;
        if (!last.project(p[0], p[1], p[2], scr)) return null;
        return new float[]{scr[0], scr[1]};
    }
}
