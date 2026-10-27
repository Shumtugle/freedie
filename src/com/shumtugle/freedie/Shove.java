package com.shumtugle.freedie;

/**
 * The film between the second episode and the third, made of the street itself, a few
 * seconds long and without a word: Freedie stands at the empty plinth, looks up at the eye,
 * turns to the plinth, gets down low and sets her shoulder to it, and in three heaves shoves
 * it three meters down the road, stone grinding on the asphalt, dust coming out from under
 * it. Then she straightens and looks back over her shoulder at the eye.
 *
 * The first time it plays whole; after that a touch cuts it short.
 */
final class Shove implements Film {
    // the film, in seconds: she looks up; turns; gets down; three heaves; straightens and looks back
    static final float LOOK0 = 0.3f, LOOK1 = 1.0f, TURN0 = 1.1f, TURN1 = 1.9f, DOWN0 = 1.8f, DOWN1 = 2.6f,
            HEAVE0 = 2.7f, HEAVE = 0.85f, UP0 = 5.35f, UP1 = 6.0f, STREET = 7.2f;
    static final int HEAVES = 3;
    // where she stands across the road, and how low she gets: her hips sunk, her back bent.
    // She comes at her own size, smaller than the giant of the square: a woman at a block of
    // stone, her shoulder at its top edge
    static final float FX = -0.5f, SINK = 0.85f, BEND = 1.2f, SIZE = 0.6f;

    private final Game game;
    private final Frida frida = new Frida();
    private final float fromZ, toZ;
    private final boolean first;
    float t;
    private float was = -1f;
    private boolean cut;
    private final float[] pt = new float[3];
    private final java.util.Random rnd = new java.util.Random(0x5A0E);

    Shove(Game game, float fromZ, float toZ, boolean first) {
        this.game = game;
        this.fromZ = fromZ;
        this.toZ = toZ;
        this.first = first;
        frida.size = SIZE;
        frida.x = FX;
        frida.z = standZ();
        frida.eyes = 0.4f;
    }

    /** Where she waits at first: before the plinth's near face, facing the eye. */
    private float standZ() {
        return fromZ - Builder.PHZ1 - 1.0f;
    }

    // ---- time ----

    private static float ease(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    private static float span(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    private static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private boolean at(float moment) {
        return was < moment && t >= moment;
    }

    public boolean over() {
        return cut || t >= STREET;
    }

    public void tap() {
        if (!first && t > 1f) cut = true;
    }

    /** How far the plinth has gone, from nothing to one: each heave a lurch that slows to a stop. */
    private float moved() {
        float k = 0f;
        for (int h = 0; h < HEAVES; h++) {
            float u = span(t, HEAVE0 + h * HEAVE + 0.12f, HEAVE0 + h * HEAVE + HEAVE * 0.8f);
            k += 1f - (1f - u) * (1f - u);
        }
        return k / HEAVES;
    }

    /** The plinth's place along the street. */
    float plinthZ() {
        return fromZ + (toZ - fromZ) * moved();
    }

    public void update(float dt) {
        was = t;
        t += dt;
        game.plinthAt(plinthZ());
        pose();
        sounds();
        if (first && at(STREET - 0.05f)) game.host.saveInt("ins1", 1);
    }

    // ---- her movements ----

    private void pose() {
        frida.rest(t);
        float[] ax = frida.ax, az = frida.az, ay = frida.ay;
        // she looks up at the eye, her eyes kindling
        float look = ease(span(t, LOOK0, LOOK1));
        ax[Frida.HEAD] = mix(-0.3f, 0.12f, look);
        frida.eyes = mix(0.4f, 0.9f, look);
        // she turns her back on the eye, to the plinth, with a step either side
        float turn = ease(span(t, TURN0, TURN1));
        ay[Frida.ROOT] = (float) Math.PI * turn;
        float step = (float) Math.sin(Math.PI * span(t, TURN0, TURN1) * 2f);
        if (t > TURN0 && t < TURN1) {
            ax[Frida.LEG_A] = 0.35f * Math.max(0f, step);
            ax[Frida.SHIN_A] = -0.5f * Math.max(0f, step);
            ax[Frida.LEG_B] = 0.35f * Math.max(0f, -step);
            ax[Frida.SHIN_B] = -0.5f * Math.max(0f, -step);
        }
        // down low, her back bent, the shoulder forward, the empty arm along the plinth's top
        float low = ease(span(t, DOWN0, DOWN1)) * (1f - ease(span(t, UP0, UP1)));
        // each heave: she drives into it, her back straining, then a short step after it
        float drive = 0f, stride = 0f;
        int side = 0;
        for (int h = 0; h < HEAVES; h++) {
            float h0 = HEAVE0 + h * HEAVE;
            if (t >= h0 && t < h0 + HEAVE) {
                float u = (t - h0) / HEAVE;
                drive = (float) Math.sin(Math.PI * Math.min(1f, u / 0.7f));
                stride = u > 0.45f ? (float) Math.sin(Math.PI * (u - 0.45f) / 0.55f) : 0f;
                side = h & 1;
            }
        }
        frida.lift = -SINK * low - 0.08f * drive;
        ax[Frida.CHEST] += -BEND * low - 0.12f * drive;
        // the head up, looking where she shoves
        ax[Frida.HEAD] = mix(ax[Frida.HEAD], 0.75f, low);
        // a crouch: thighs forward, shins back so the feet stay under her
        float thigh = 1.0f * low, shin = -1.85f * low;
        ax[Frida.LEG_A] += thigh + (side == 0 ? 0.3f * stride : -0.12f * drive);
        ax[Frida.SHIN_A] += shin - (side == 0 ? 0.35f * stride : 0f);
        ax[Frida.LEG_B] += thigh * 0.8f + (side == 1 ? 0.3f * stride : -0.2f * drive);
        ax[Frida.SHIN_B] += shin * 0.8f - (side == 1 ? 0.35f * stride : 0f);
        // the empty arm reaches forward along the stone, the elbow bent over its edge
        ax[Frida.ARM_B] = mix(ax[Frida.ARM_B], 2.55f, low);
        ax[Frida.FORE_B] = mix(0f, -0.9f, low);
        az[Frida.ARM_B] = mix(az[Frida.ARM_B], -0.25f, low);
        // the hammer arm back for balance, the hammer trailing
        ax[Frida.ARM_A] = mix(ax[Frida.ARM_A], 1.0f, low);
        az[Frida.ARM_A] = mix(az[Frida.ARM_A], 0.45f, low);
        ax[Frida.HAMMER] = mix(1.2f, 0.4f, low);
        // straightening up, she looks back over her shoulder at the eye
        float back = ease(span(t, UP0 + 0.2f, UP1 + 0.3f));
        ay[Frida.HEAD] = 1.6f * back;
        ax[Frida.HEAD] = mix(ax[Frida.HEAD], 0.05f, back);
        ay[Frida.CHEST] = 0.35f * back;
        // where she stands: at first where she waited; down low, her shoulder against the stone
        frida.z = standZ();
        frida.pose();
        if (low > 0f) {
            frida.place(Frida.CHEST, -1.2f, 3.22f, 0f, pt);
            float face = plinthZ() - Builder.PHZ1 - 0.5f * frida.size;
            frida.z = mix(standZ(), standZ() + (face - pt[2]), ease(span(t, DOWN0, DOWN1)));
            frida.pose();
        }
        if (t >= UP0) {
            // she stays where the last heave left her as she gets up
            frida.z = upZ;
            frida.pose();
        } else upZ = frida.z;
    }

    private float upZ;

    private void sounds() {
        World.Host h = game.host;
        // her steps as she turns, heavy
        if (at(TURN0 + 0.2f) || at(TURN0 + 0.6f)) {
            h.sound(Synth.GROUND, 0.8f, 0.55f);
            h.buzz(20);
        }
        if (at(DOWN1 - 0.1f)) h.sound(Synth.STONE, 0.6f, 0.5f);
        for (int k = 0; k < HEAVES; k++) {
            float h0 = HEAVE0 + k * HEAVE;
            if (at(h0 + 0.12f)) {
                // stone dragged on the asphalt; the last heave she puts her voice into
                h.sound(Synth.SCRAPE, 0.9f, 0.85f + rnd.nextFloat() * 0.15f);
                h.sound(Synth.STONE, 0.5f, 0.45f);
                h.buzz(40);
                if (k == HEAVES - 1) h.sound(Synth.ROAR, 0.45f, 1.15f);
            }
            if (at(h0 + HEAVE * 0.7f)) h.sound(Synth.GROUND, 0.6f, 0.6f);
        }
        // dust out from under it while it moves
        float z = plinthZ();
        if (t > HEAVE0 && t < HEAVE0 + HEAVES * HEAVE && Math.abs(z - lastZ) > 0.01f)
            for (int i = 0; i < 2; i++) {
                float side = rnd.nextBoolean() ? 1f : -1f;
                game.spawn(side * Builder.PHX * (0.9f + rnd.nextFloat() * 0.15f), 0.06f,
                        z - Builder.PHZ1 + rnd.nextFloat() * 2f * Builder.PHZ1, true, 0xD9CDB8, 0.5f);
            }
        lastZ = z;
    }

    private float lastZ;

    // ---- the eye ----

    // low before her, she filling the frame as she looks up; round to the right side of
    // the road to see her and the plinth side by side as it goes; back a little as she
    // straightens and looks back at the eye, and the light
    private static final float[] KT = {0f, 1.6f, 3.2f, 5.3f, STREET};
    private final float[] p = new float[3], q = new float[3];

    public void camera(DrawList d, int w) {
        float sz = standZ();
        float[][] kp = {{-1.6f, 1.3f, sz - 3.8f}, {1.6f, 2.4f, sz - 4.4f}, {4.6f, 2.0f, fromZ - 2.4f}, {4.6f, 2.0f, toZ - 2.8f},
                {3.4f, 1.7f, toZ - 5.4f}};
        float[][] kl = {{FX, 1.7f, sz}, {0f, 1.0f, fromZ - 0.5f}, {-0.4f, 0.8f, fromZ - 1.1f}, {-0.4f, 0.8f, toZ - 1.3f},
                {FX, 1.5f, upZ}};
        path(kp, p);
        path(kl, q);
        float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        // each heave shakes the eye a little
        for (int h = 0; h < HEAVES; h++) {
            float s = t - (HEAVE0 + h * HEAVE + 0.12f);
            if (s > 0f && s < 0.6f) pitch += 0.006f * (float) Math.exp(-s * 9f) * (float) Math.sin(s * 55f);
        }
        float k = ease(span(t, 0f, STREET));
        d.camera(p[0], p[1], p[2], yaw, pitch, w * (1.5f + 0.1f * k), 0.1f, 500f);
    }

    /** A point along keyed positions at this moment: a smooth curve through them, coming to rest at the last. */
    private void path(float[][] k, float[] out) {
        int n = KT.length;
        float tt = Math.min(t, KT[n - 1]);
        int i = 0;
        while (i < n - 2 && tt >= KT[i + 1]) i++;
        float u = (tt - KT[i]) / (KT[i + 1] - KT[i]);
        if (i == n - 2) u = 1f - (1f - u) * (1f - u);
        float[] a = k[Math.max(0, i - 1)], b = k[i], c = k[i + 1], e = k[Math.min(n - 1, i + 2)];
        float u2 = u * u, u3 = u2 * u;
        for (int j = 0; j < 3; j++)
            out[j] = 0.5f * (2f * b[j] + (-a[j] + c[j]) * u + (2f * a[j] - 5f * b[j] + 4f * c[j] - e[j]) * u2
                    + (-a[j] + 3f * b[j] - 3f * c[j] + e[j]) * u3);
    }

    // ---- drawing ----

    public void draw(DrawList d) {
        frida.draw(d);
    }

    public void flat(Px f) {
    }
}
