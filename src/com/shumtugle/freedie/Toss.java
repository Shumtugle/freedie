package com.shumtugle.freedie;

/**
 * Her mallet (see Mallet): carried in her right hand, hanging from the fist and trailing behind
 * her, its head low. When she has run a golem down and it is held within her reach, she stops,
 * turns to it and throws: the mallet flies at its head end over end, knocks the plate off its
 * brow, and comes back to her hand.
 */
final class Toss {
    static final float LOCK = 1.1f, RELEASE = 0.33f;       // how long she stands to throw; when it leaves her hand
    static final float OUT_SPEED = 16f, BACK_TIME = 0.45f, SCALE = 1.6f, MIDDLE = 0.5f;
    static final int HELD = 0, OUT = 1, BACK = 2, WINDUP = 3;

    int state = HELD;
    int thrown;
    private float clock, at;
    private Chase aim;
    private final float[] from = new float[3], pos = new float[3], to = new float[3];
    private float flight, spin;
    private final float[] hand = new float[3], elbow = new float[3];
    private final float[] axis = new float[3], base = new float[9], turn = new float[9];

    void update(float dt, Runner r, Mission m, City city) {
        clock += dt;
        if (state == HELD) {
            for (Chase g : m.golems)
                if (g.seized()) {
                    aim = g;
                    r.lock(LOCK, g.x, g.z);
                    state = WINDUP;
                    break;
                }
            return;
        }
        if (state == WINDUP) {
            if (r.throwing() < RELEASE) return;
            // out of her hand at the height of the swing
            grip(r);
            System.arraycopy(hand, 0, from, 0, 3);
            aim.head(to);
            float dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
            flight = Math.max(0.15f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / OUT_SPEED);
            at = clock;
            spin = 0f;
            thrown++;
            state = OUT;
            return;
        }
        spin += dt * 17f;
        float u = (clock - at) / (state == OUT ? flight : BACK_TIME);
        if (state == OUT) {
            // the head it was thrown at
            if (!aim.done()) aim.head(to);
            flown(Math.min(1f, u), 0.08f);
            if (u >= 1f) {
                aim.take(r, city, to[0] - from[0], to[2] - from[2]);
                System.arraycopy(pos, 0, from, 0, 3);
                at = clock;
                state = BACK;
            }
        } else {
            grip(r);
            System.arraycopy(hand, 0, to, 0, 3);
            flown(Math.min(1f, u), 0.06f);
            if (u >= 1f) state = HELD;
        }
    }

    /** Along the way from where it set off to where it is going, a little arc over it. */
    private void flown(float u, float arc) {
        float dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (int k = 0; k < 3; k++) pos[k] = from[k] + (to[k] - from[k]) * u;
        pos[1] += (float) Math.sin(Math.PI * u) * len * arc;
    }

    /** Her fist and the way her forearm points, as she is now. */
    private void grip(Runner r) {
        r.bone("hand_r", hand);
        r.bone("lowerarm_r", elbow);
    }

    void draw(DrawList d, Runner r) {
        float hx, hy, hz;
        boolean inHand = state == HELD || state == WINDUP;
        if (inHand && state == WINDUP) {
            // swung: the handle runs on from her forearm
            grip(r);
            hx = hand[0] - elbow[0];
            hy = hand[1] - elbow[1];
            hz = hand[2] - elbow[2];
        } else if (inHand) {
            // hanging from the fist, trailing behind her, swung a little with her forearm
            grip(r);
            float fx = hand[0] - elbow[0], fy = hand[1] - elbow[1], fz = hand[2] - elbow[2];
            float fl = Math.max(1e-3f, (float) Math.sqrt(fx * fx + fy * fy + fz * fz));
            hx = -0.45f * (float) Math.sin(r.heading) + 0.35f * fx / fl;
            hy = -1f + 0.35f * fy / fl;
            hz = -0.45f * (float) Math.cos(r.heading) + 0.35f * fz / fl;
        } else {
            // flying: the handle along the way it goes, turning end over end
            hx = to[0] - from[0];
            hy = 0f;
            hz = to[2] - from[2];
        }
        float l = Math.max(1e-3f, (float) Math.sqrt(hx * hx + hy * hy + hz * hz));
        hx /= l;
        hy /= l;
        hz /= l;
        // the barrel across: level, square to the handle
        float bx = -hz, bz = hx, bl = Math.max(1e-3f, (float) Math.sqrt(bx * bx + bz * bz));
        bx /= bl;
        bz /= bl;
        // the third way: barrel by handle
        float ux = 0f * hz - bz * hy, uy = bz * hx - bx * hz, uz = bx * hy - 0f * hx;
        base[0] = hx; base[1] = ux; base[2] = bx;
        base[3] = hy; base[4] = uy; base[5] = 0f;
        base[6] = hz; base[7] = uz; base[8] = bz;
        float cx, cy, cz;
        if (inHand) {
            System.arraycopy(base, 0, turn, 0, 9);
            cx = hand[0];
            cy = hand[1];
            cz = hand[2];
        } else {
            axis[0] = bx;
            axis[1] = 0f;
            axis[2] = bz;
            Chase.spun(axis, 0, spin, base, 0, turn);
            cx = pos[0];
            cy = pos[1];
            cz = pos[2];
        }
        // held, by the grip; flying, turning about its middle
        Mallet.draw(d, turn, cx, cy, cz, SCALE, inHand ? 0f : MIDDLE);
    }
}
