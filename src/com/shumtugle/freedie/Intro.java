package com.shumtugle.freedie;

import java.util.Random;

/**
 * The opening, seven steps in about six and a half seconds, no words but the name:
 * the eye flies in an arc over the seaside town down onto the street and stops before
 * Freedie, who looks up; the town goes dark, light left on her alone; she swings her golden
 * hammer straight at the eye, the glass cracks, and she herself breaks and falls apart;
 * on the black the name burns in gold; a flash, and the game. A touch at any moment goes
 * straight to the flash.
 */
final class Intro {
    static final float FLY_END = 2.5f, LOOK_END = 3.0f, DARK_END = 3.6f, RAISE_END = 3.95f, HIT = 4.25f;
    static final float NAME = 5.2f, FLASH = 6.2f, PEAK = 6.45f;
    static final String TITLE = "FREEDIE";

    // where she stands, and the eye before her at the end of its flight
    static final float FX = 0f, FZ = 8.6f;
    private static final float[] P0 = {-55f, 38f, -45f}, P1 = {-10f, 22f, -20f}, P2 = {0f, 2.1f, 0.4f};
    private static final float[] T0 = {25f, 0f, 70f}, T1 = {5f, 4f, 30f}, T2 = {FX, 3.0f, FZ};
    // the name, a block for every dot of it, far enough off to fit the frame
    private static final float NAME_Z = 18f, NAME_Y = 5.0f, DOT = 0.18f;

    private final World.Host host;
    private final Frida frida = new Frida();
    private final Random rnd = new Random(23);
    float t;
    boolean peaked;                           // the flash is at its whitest: the game takes over

    // the cracks in the glass where the hammer struck, as runs of frame points
    private final int[] crack = new int[2 * 600];
    private int crackN;
    private final float[] head = new float[3], scr = new float[2];
    private float lastKnock = -1f;

    // the name's dots, and when each lights up
    private final float[] dotX = new float[300], dotY = new float[300], dotAt = new float[300];
    private int dots;

    Intro(World.Host host) {
        this.host = host;
        frida.x = FX;
        frida.z = FZ;
        int col = 0;
        int width = PixelFont.width(TITLE, 1);
        for (int i = 0; i < TITLE.length(); i++) {
            char c = TITLE.charAt(i);
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++)
                    if (PixelFont.dot(c, gx, gy) && dots < dotX.length) {
                        float cx = col + gx;
                        dotX[dots] = (cx - (width - 1) * 0.5f) * DOT;
                        dotY[dots] = NAME_Y + (3 - gy) * DOT;
                        dotAt[dots] = NAME + cx * 0.012f;
                        dots++;
                    }
            col += 6;
        }
        frida.physics.listener = new Physics.Listener() {
            @Override
            public void knock(Body a, Body b, float speed, float x, float y, float z) {
                if (speed < 1.2f || t - lastKnock < 0.035f) return;
                lastKnock = t;
                int id = a.material == Body.WOOD ? Synth.WOOD : a.material == Body.GOLD ? Synth.CLANG : Synth.STONE;
                sound(id, Math.min(1f, speed / 6f) * 0.7f, 0.75f + rnd.nextFloat() * 0.4f);
            }
        };
    }

    private void sound(int id, float gain, float rate) {
        if (host != null) host.sound(id, gain, rate);
    }

    /** A touch: straight to the flash. */
    void skip() {
        if (t < FLASH) {
            t = FLASH + 0.08f;
            sound(Synth.WHOOSH, 0.5f, 1.5f);
        }
    }

    void update(float dt) {
        float was = t;
        t += dt;
        if (crossed(was, 0f)) sound(Synth.WIND, 0.8f, 1f);
        if (crossed(was, RAISE_END)) sound(Synth.WHOOSH, 1.1f, 0.62f);
        if (crossed(was, HIT)) {
            sound(Synth.thunder(1), 1.2f, 1f);
            sound(Synth.GROUND, 1f, 0.7f);
            sound(Synth.GLASS, 0.9f, 0.8f);
        }
        if (crossed(was, NAME)) sound(Synth.CHIME, 0.9f, 1f);
        if (crossed(was, FLASH)) sound(Synth.WHOOSH, 0.5f, 1.5f);
        if (t >= PEAK) peaked = true;
        pose();
        if (!frida.broken && t >= HIT) {
            frida.pose();
            frida.place(Frida.HAMMER, 1.34f, 1.08f - 1.95f, -0.04f, head);
            frida.shatter(head[0], head[1], head[2], rnd);
        }
        frida.fall(dt);
    }

    /** Whether this step of time passed the moment at. */
    private boolean crossed(float was, float at) {
        return was <= at && t > at;
    }

    // ---- her movements ----

    private void pose() {
        if (frida.broken) return;
        frida.rest(t);
        float[] ax = frida.ax, az = frida.az;
        if (t > 2.6f) {
            float u = ease(span(t, 2.6f, LOOK_END));
            ax[Frida.HEAD] = mix(-0.3f, 0.1f, u);
            frida.eyes = mix(0.4f, 1f, u);
        }
        if (t > DARK_END && t <= RAISE_END) {
            // up and back, over her head
            float u = ease(span(t, DARK_END, RAISE_END));
            ax[Frida.ARM_A] = mix(0f, 3.4f, u);
            ax[Frida.FORE_A] = 0.35f * u;
            ax[Frida.HAMMER] = mix(1.2f, 1.0f, u);
            az[Frida.ARM_A] = mix(0.12f, 0.05f, u);
            ax[Frida.CHEST] = 0.12f * u;
        } else if (t > RAISE_END) {
            // and down at the eye, faster and faster, leaning into it
            float u = (float) Math.pow(span(t, RAISE_END, HIT - 0.06f), 1.3);
            ax[Frida.ARM_A] = mix(3.4f, 1.45f, u);
            ax[Frida.FORE_A] = mix(0.35f, 0f, u);
            ax[Frida.HAMMER] = mix(1.0f, 0f, u);
            az[Frida.ARM_A] = 0.05f;
            // across her body, so it comes at the eye and not past it
            frida.ay[Frida.ARM_A] = 0.3f * u;
            ax[Frida.CHEST] = mix(0.12f, -0.45f, u);
        }
    }

    // ---- the frame ----

    private final float[] eye = new float[3], aim = new float[3];

    /** The eye, the light and the dark for this moment; the world has set the rest. */
    void frame(DrawList d, int w) {
        float u = 1f - (float) Math.pow(1f - span(t, 0f, FLY_END), 2.5f);
        bezier(P0, P1, P2, u, eye);
        bezier(T0, T1, T2, u, aim);
        // a step in toward her as she swings
        if (t > RAISE_END && t < HIT) eye[2] += 1.6f * ease(span(t, RAISE_END, HIT));
        else if (t >= HIT) eye[2] += 1.6f;
        if (t > HIT) {
            float k = 0.14f * (float) Math.exp(-(t - HIT) * 7f);
            eye[0] += k * (float) Math.sin(t * 91f);
            eye[1] += k * (float) Math.sin(t * 73f + 1f);
        }
        float dx = aim[0] - eye[0], dy = aim[1] - eye[1], dz = aim[2] - eye[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        d.camera(eye[0], eye[1], eye[2], yaw, pitch, w * 1.9f, 0.1f, 500f);

        // the dark comes in; the light left turns to face her, then the name
        float dark = span(t, LOOK_END, DARK_END);
        d.dark = dark;
        float sx = mix(-0.80f, -0.35f, dark), sy = mix(0.30f, 0.55f, dark), sz = mix(-0.10f, -0.76f, dark);
        d.sun(sx, sy, sz, -1f, 4f, 42f, 100f, 36f, 140f);
        float toName = ease(span(t, NAME - 0.3f, NAME + 0.2f));
        d.spot[0] = FX;
        d.spot[1] = mix(2.6f, NAME_Y, toName);
        d.spot[2] = mix(FZ, NAME_Z, toName);
        d.spotRadius = mix(4.2f, 3.8f, toName);
        d.goldGlow = 1f - toName;
        // a white blink at the blow; then the flash into the game
        if (t >= HIT && t < HIT + 0.12f) d.flash = 0.45f * (1f - (t - HIT) / 0.12f);
        if (t >= FLASH) d.flash = Math.min(1f, (t - FLASH) / (PEAK - FLASH));
    }

    /** Everything of hers: herself, or what is left of her; the name. */
    void draw(DrawList d) {
        if (frida.broken) frida.drawBroken(d);
        else {
            frida.pose();
            frida.draw(d);
        }
        for (int i = 0; i < dots; i++) {
            float age = t - dotAt[i];
            if (age < 0f) continue;
            float s = Math.min(1f, age / 0.12f) * DOT * 0.45f;
            if (age < 0.3f)
                d.box(dotX[i], dotY[i], NAME_Z, s, s, s, null, Street.mix(0xFFF4C8, 0xF2C230, age / 0.3f), DrawList.GLOW);
            else d.box(dotX[i], dotY[i], NAME_Z, s, s, s * 1.6f, null, 0xF2C230, DrawList.GOLD);
        }
    }

    /** The flat layer: the cracked glass, from the blow until the name. */
    void flat(Px f, DrawList d) {
        if (t < HIT || t > NAME) return;
        if (crackN == 0) cracks(d);
        float a = t < HIT + 0.3f ? 1f : Math.max(0f, 1f - (t - HIT - 0.3f) / (NAME - HIT - 0.3f));
        int c = ((int) (a * 0xD0) << 24) | 0xFFFFFF;
        for (int i = 0; i + 1 < crackN; i++) {
            int x0 = crack[2 * i], y0 = crack[2 * i + 1], x1 = crack[2 * i + 2], y1 = crack[2 * i + 3];
            if (x1 == Integer.MIN_VALUE || x0 == Integer.MIN_VALUE) continue;
            line(f, x0, y0, x1, y1, c);
        }
    }

    /** Cracks running out from where the hammer met the glass, splitting as they go. */
    private void cracks(DrawList d) {
        float cx = d.w * 0.5f, cy = d.h * 0.42f;
        if (d.project(head[0], head[1], head[2], scr)) {
            cx = Math.max(30f, Math.min(d.w - 30f, scr[0]));
            cy = Math.max(60f, Math.min(d.h - 60f, scr[1]));
        }
        Random r = new Random(5);
        int arms = 8;
        for (int a = 0; a < arms; a++) {
            float ang = (float) (a * Math.PI * 2 / arms + r.nextFloat() * 0.5f);
            run(r, cx, cy, ang, 7 + r.nextInt(6), 2);
        }
    }

    private void run(Random r, float x, float y, float ang, int steps, int depth) {
        if (crackN + steps + 2 > crack.length / 2) return;
        add((int) x, (int) y);
        for (int s = 0; s < steps; s++) {
            ang += (r.nextFloat() - 0.5f) * 0.7f;
            float len = 5f + r.nextFloat() * 11f;
            x += (float) Math.cos(ang) * len;
            y += (float) Math.sin(ang) * len;
            add((int) x, (int) y);
            if (depth > 0 && r.nextInt(4) == 0) {
                add(Integer.MIN_VALUE, Integer.MIN_VALUE);
                run(r, x, y, ang + (r.nextBoolean() ? 0.8f : -0.8f), steps / 2, depth - 1);
                add(Integer.MIN_VALUE, Integer.MIN_VALUE);
                add((int) x, (int) y);
            }
        }
        add(Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private void add(int x, int y) {
        if (crackN >= crack.length / 2) return;
        crack[2 * crackN] = x;
        crack[2 * crackN + 1] = y;
        crackN++;
    }

    private static void line(Px f, int x0, int y0, int x1, int y1, int c) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
            f.set(x, y, c);
        }
    }

    // ---- helpers ----

    private static void bezier(float[] a, float[] b, float[] c, float u, float[] out) {
        float v = 1f - u;
        for (int i = 0; i < 3; i++) out[i] = v * v * a[i] + 2f * u * v * b[i] + u * u * c[i];
    }

    static float span(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    static float ease(float u) {
        return u * u * (3f - 2f * u);
    }

    static float mix(float a, float b, float u) {
        return a + (b - a) * u;
    }
}
