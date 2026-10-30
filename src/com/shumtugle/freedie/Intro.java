package com.shumtugle.freedie;

import java.util.Random;

/**
 * The opening, seven steps in about six and a half seconds, no word but the name. The eye
 * flies in an arc over the seaside town down onto a square, cars going by, people walking,
 * and stops before Freedie, a giant there, who stands at rest and whose eyes kindle; the
 * town goes dark, light left on her alone; she swings her golden hammer up over her head and
 * down straight at the eye, the glass cracks, and she herself breaks: every block of her a
 * body of the game's own physics, falling into the black. On the black the name burns in
 * gold; a flash, and the game. Played at every start; a touch at any moment goes straight
 * to the flash.
 *
 * She moves as her skeleton's motions go (see Frida): at rest, then the throw, slowed in its
 * wind-up and caught at the instant her hand comes forward, where the blow lands.
 */
final class Intro {
    static final float FLY_END = 2.5f, LOOK_END = 3.0f, DARK_END = 3.6f, RAISE_END = 4.05f, HIT = 4.3f;
    static final float NAME = 5.2f, FLASH = 6.2f, PEAK = 6.45f;
    static final String TITLE = "FREEDIE";

    // where she stands, and how big: so many times her skeleton
    static final float FX = 0f, FZ = Square.Z, SIZE = 2.4f;
    // the golden hammer in her hand, so many times the player's
    static final float HAMMER = SIZE * 1.25f;
    // the throw: the hand drawn back over the head, and the instant it comes forward
    static final float BACK = 0.267f, STRIKE = 0.372f;
    // down from high over the town in an arc that swings out over the promenade, to before her
    private static final float[] P0 = {-30f, 38f, FZ - 30f}, P1 = {-24f, 15f, FZ - 24f}, P2 = {0.3f, 2.2f, FZ - 7.6f};
    private static final float[] T0 = {FX, 0.4f, FZ}, T1 = {FX, 1.8f, FZ}, T2 = {FX, 2.9f, FZ};
    // the name, a block for every dot of it, far enough off to fit the frame
    private static final float NAME_Z = FZ + 9.4f, NAME_Y = 5.0f, DOT = 0.22f;

    private final World.Host host;
    private final Square square = new Square();
    private final Random rnd = new Random(23);
    float t;
    private boolean peaked;                   // the flash is at its whitest: the game takes over

    // the cracks in the glass where the hammer struck, as runs of frame points
    private final int[] crack = new int[2 * 600];
    private int crackN;
    private final float[] scr = new float[2];
    private float lastKnock = -1f;

    // the name's dots, and when each lights up
    private final float[] dotX = new float[300], dotY = new float[300], dotAt = new float[300];
    private int dots;

    // what is left of her once she breaks: a body for each block, and how each is drawn
    private final Physics physics = new Physics();
    private Body[] bodies;
    private int[] bodyCol, bodyMat;
    private boolean broken;
    private float brokenFor;

    // the golden hammer as drawn now, and where its head will be at the blow
    private final float[] hammer = new float[12];
    private final float[] strikeHead = new float[3];
    private boolean strikeKnown;

    Intro(World.Host host) {
        this.host = host;
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
        physics.listener = new Physics.Listener() {
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

    boolean done() {
        return peaked;
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
        // the wind high over the town; the eyes; the swing; the blow
        if (crossed(was, 0f)) sound(Synth.WHOOSH, 0.7f, 0.45f);
        if (crossed(was, LOOK_END - 0.3f)) sound(Synth.CLANG, 0.45f, 0.45f);
        if (crossed(was, RAISE_END)) sound(Synth.WHOOSH, 1.1f, 0.62f);
        if (crossed(was, HIT)) {
            sound(Synth.thunder(1), 1.2f, 1f);
            sound(Synth.GROUND, 1f, 0.7f);
            sound(Synth.GLASS, 0.9f, 0.8f);
        }
        if (crossed(was, NAME)) sound(Synth.CLANG, 0.8f, 1.6f);
        if (crossed(was, FLASH)) sound(Synth.WHOOSH, 0.5f, 1.5f);
        if (t >= PEAK) peaked = true;
        if (!broken && t >= HIT && Frida.ready()) shatter();
        if (broken) {
            brokenFor += dt;
            // the blocks were packed into one another: at first each meets only the ground
            if (brokenFor > 0.45f) physics.pairs = true;
            physics.step(Math.min(dt, 1f / 30f));
        }
    }

    /** Whether this step of time passed the moment at. */
    private boolean crossed(float was, float at) {
        return was <= at && t > at;
    }

    // ---- her movements ----

    /** Her motion now, and how far into it: at rest, then the throw, slowed to the swing. */
    private String motion() {
        return t < DARK_END ? "idle" : "throw";
    }

    private float motionTime() {
        if (t < DARK_END) return t;
        // up and back over her head, easing in
        if (t < RAISE_END) return BACK * ease(span(t, DARK_END, RAISE_END));
        // and down at the eye, faster and faster
        return BACK + (STRIKE - BACK) * (float) Math.pow(span(t, RAISE_END, HIT), 1.4);
    }

    private final float[] hand = new float[3], elbow = new float[3];

    /**
     * The golden hammer in her right fist, the handle along the forearm out past the fist,
     * the head's long side across, in the plane of the swing.
     */
    private void hammerAt(String motion, float mt, float[] pose) {
        Frida.place("hand_r", FX, 0f, FZ, 0f, SIZE, motion, mt, hand);
        Frida.place("lowerarm_r", FX, 0f, FZ, 0f, SIZE, motion, mt, elbow);
        float ax = hand[0] - elbow[0], ay = hand[1] - elbow[1], az = hand[2] - elbow[2];
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-6f;
        ax /= l;
        ay /= l;
        az /= l;
        // the head across: square to the handle and to her side to side
        float zx = 0f, zy = az, zz = -ay;
        float zl = (float) Math.sqrt(zy * zy + zz * zz) + 1e-6f;
        zy /= zl;
        zz /= zl;
        // the grip in the fist, a little past the wrist
        float grip = 0.09f * SIZE, mid = 0.36f * HAMMER;
        pose[0] = hand[0] + ax * (grip + mid);
        pose[1] = hand[1] + ay * (grip + mid);
        pose[2] = hand[2] + az * (grip + mid);
        pose[3] = ax;
        pose[6] = ay;
        pose[9] = az;
        pose[5] = zx;
        pose[8] = zy;
        pose[11] = zz;
        pose[4] = zy * az - zz * ay;
        pose[7] = zz * ax - zx * az;
        pose[10] = zx * ay - zy * ax;
    }

    /** Where the hammer's head is in a pose. */
    private static void headOf(float[] pose, float[] out) {
        float o = 0.31f * HAMMER;
        out[0] = pose[0] + pose[3] * o;
        out[1] = pose[1] + pose[6] * o;
        out[2] = pose[2] + pose[9] * o;
    }

    /**
     * She breaks where she stands: each block a body of its own, thrown out from the middle
     * of her and away from the blow; the hammer drops from her hand.
     */
    private void shatter() {
        broken = true;
        brokenFor = 0f;
        physics.clear();
        physics.pairs = false;
        Frida.pose(FX, 0f, FZ, 0f, SIZE, motion(), motionTime());
        hammerAt(motion(), motionTime(), hammer);
        int n = Frida.parts();
        bodies = new Body[n + 2];
        bodyCol = new int[n + 2];
        bodyMat = new int[n + 2];
        float[] q = new float[4];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float hx = Frida.HALF[i * 3] * SIZE, hy = Frida.HALF[i * 3 + 1] * SIZE, hz = Frida.HALF[i * 3 + 2] * SIZE;
            // the mortar hidden in the bricks, and the buttons and rivets too small to fall as anything
            if (Frida.mortar(i) || Math.min(hx, Math.min(hy, hz)) < 0.008f) continue;
            int m = Frida.MAT[i];
            int kind = m == DrawList.STONE || m == DrawList.GRAIN ? Body.STONE : Body.WOOD;
            Body b = new Body(hx, hy, hz, kind, Frida.colour(i));
            quat(Frida.TURN, i * 9, q);
            float x = Frida.AT[i * 3], y = Frida.AT[i * 3 + 1], z = Frida.AT[i * 3 + 2];
            b.setPose(x, y, z, q[0], q[1], q[2], q[3]);
            float ox = x - FX, oy = y - 1.1f * SIZE, oz = z - FZ;
            float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-3f;
            float kick = 1.2f + rnd.nextFloat() * 1.8f;
            b.vx = ox / ol * kick + (rnd.nextFloat() - 0.5f) * 1.5f;
            b.vy = oy / ol * kick + 0.8f + rnd.nextFloat() * 1.4f;
            b.vz = oz / ol * kick + (rnd.nextFloat() - 0.5f) * 1.2f + 0.5f;
            b.wx = (rnd.nextFloat() - 0.5f) * 7f;
            b.wy = (rnd.nextFloat() - 0.5f) * 7f;
            b.wz = (rnd.nextFloat() - 0.5f) * 7f;
            bodyCol[k] = Frida.colour(i);
            bodyMat[k] = m;
            bodies[k++] = b;
            physics.add(b);
        }
        // the hammer: the handle and the head, falling apart from her
        float[][] parts = Hammer.parts(Hammer.GOLDEN);
        int[] cols = Hammer.colours(Hammer.GOLDEN);
        float[] rows = new float[9];
        for (int r = 0; r < 9; r++) rows[r] = hammer[3 + r];
        quat(rows, 0, q);
        for (int p = 0; p <= 2; p += 2) {
            float[] part = parts[p];
            float ox = part[0] * HAMMER;
            Body b = new Body(part[3] * HAMMER, part[4] * HAMMER, part[5] * HAMMER, p == 2 ? Body.GOLD : Body.WOOD, cols[p]);
            b.setPose(hammer[0] + hammer[3] * ox, hammer[1] + hammer[6] * ox, hammer[2] + hammer[9] * ox, q[0], q[1], q[2], q[3]);
            b.vx = (rnd.nextFloat() - 0.5f) * 0.6f;
            b.vy = 0.5f;
            b.vz = 2.2f;
            b.wx = (rnd.nextFloat() - 0.5f) * 3f;
            bodyCol[k] = cols[p];
            bodyMat[k] = p == 2 ? DrawList.FIRE_GOLD : DrawList.WOOD;
            bodies[k++] = b;
            physics.add(b);
        }
        bodies = java.util.Arrays.copyOf(bodies, k);
    }

    // ---- the frame ----

    private final float[] eye = new float[3], aim = new float[3];

    void frame(DrawList d, int w) {
        if (!strikeKnown && Frida.ready()) {
            // where the head will be at the blow: the eye comes to meet it
            hammerAt("throw", STRIKE, hammer);
            headOf(hammer, strikeHead);
            strikeKnown = true;
        }
        float u = ease(span(t, 0f, FLY_END));
        bezier(P0, P1, P2, u, eye);
        bezier(T0, T1, T2, u, aim);
        if (strikeKnown && t > RAISE_END - 0.25f) {
            // a step in and up toward her as she swings, to just short of where the head comes
            // down; after the blow, back again and down, to watch her fall
            float s = ease(span(t, RAISE_END - 0.25f, HIT)) * (1f - ease(span(t, HIT + 0.05f, HIT + 0.55f)));
            float ex = strikeHead[0] * 0.6f, ey = strikeHead[1] - 0.1f, ez = strikeHead[2] - 2.1f;
            eye[0] = mix(eye[0], ex, s);
            eye[1] = mix(eye[1], ey, s);
            eye[2] = mix(eye[2], ez, s);
            aim[1] = mix(aim[1], strikeHead[1], s);
            float down = ease(span(t, HIT + 0.05f, HIT + 0.7f));
            aim[1] = mix(aim[1], 1.0f, down);
            eye[2] = mix(eye[2], P2[2] - 0.6f, down);
            eye[1] = mix(eye[1], 2.6f, down);
        }
        if (t > HIT) {
            float k = 0.14f * (float) Math.exp(-(t - HIT) * 7f);
            eye[0] += k * (float) Math.sin(t * 91f);
            eye[1] += k * (float) Math.sin(t * 73f + 1f);
        }
        float dx = aim[0] - eye[0], dy = aim[1] - eye[1], dz = aim[2] - eye[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        // a little wider than the game's eye, to take in the square round her
        d.camera(eye[0], eye[1], eye[2], yaw, pitch, w * 1.5f, 0.1f, 500f);

        // the dark comes in; the light left turns to face her, then the name
        float dark = span(t, LOOK_END, DARK_END);
        d.dark = dark;
        float sx = mix(-0.80f, -0.35f, dark), sy = mix(0.30f, 0.55f, dark), sz = mix(-0.10f, -0.76f, dark);
        d.sun(sx, sy, sz, 0f, 4f, FZ + 6f, 100f, 36f, 140f);
        float toName = ease(span(t, NAME - 0.3f, NAME + 0.2f));
        d.spot[0] = FX;
        d.spot[1] = mix(0.55f * SIZE * 1.75f, NAME_Y, toName);
        d.spot[2] = mix(FZ, NAME_Z, toName);
        d.spotRadius = mix(4.8f, 3.8f, toName);
        d.goldGlow = 1f - toName;
        // a white blink at the blow; then the flash into the game
        if (t >= HIT && t < HIT + 0.12f) d.flash = 0.45f * (1f - (t - HIT) / 0.12f);
        if (t >= FLASH) d.flash = Math.min(1f, (t - FLASH) / (PEAK - FLASH));
    }

    /** The square, herself and her hammer or what is left of them, the name. */
    void draw(DrawList d) {
        d.append(Square.scene());
        square.life(d, t, eye[0], eye[1], eye[2], d.dark > 0.35f);
        if (broken) {
            float[] turn = new float[9];
            for (int k = 0; k < bodies.length; k++) {
                Body b = bodies[k];
                turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
                turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
                turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
                d.box(b.px, b.py, b.pz, b.hx, b.hy, b.hz, turn, bodyCol[k], bodyMat[k]);
            }
        } else if (Frida.ready()) {
            // the eyes kindle as the flight comes to rest
            Frida.eyes = 0.15f + 0.85f * ease(span(t, LOOK_END - 0.5f, LOOK_END));
            Frida.draw(d, FX, 0f, FZ, 0f, SIZE, motion(), motionTime());
            Frida.eyes = 1f;
            hammerAt(motion(), motionTime(), hammer);
            Hammer.draw(d, hammer, Hammer.GOLDEN, HAMMER);
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
        if (d.project(strikeHead[0], strikeHead[1], strikeHead[2], scr)) {
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

    /** The turn at m[o..o+8], row by row, as a quaternion w, x, y, z. */
    static void quat(float[] mm, int o, float[] q) {
        float m00 = mm[o], m01 = mm[o + 1], m02 = mm[o + 2];
        float m10 = mm[o + 3], m11 = mm[o + 4], m12 = mm[o + 5];
        float m20 = mm[o + 6], m21 = mm[o + 7], m22 = mm[o + 8];
        float tr = m00 + m11 + m22;
        if (tr > 0f) {
            float s = (float) Math.sqrt(tr + 1f) * 2f;
            q[0] = 0.25f * s;
            q[1] = (m21 - m12) / s;
            q[2] = (m02 - m20) / s;
            q[3] = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
            q[0] = (m21 - m12) / s;
            q[1] = 0.25f * s;
            q[2] = (m01 + m10) / s;
            q[3] = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
            q[0] = (m02 - m20) / s;
            q[1] = (m01 + m10) / s;
            q[2] = 0.25f * s;
            q[3] = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
            q[0] = (m10 - m01) / s;
            q[1] = (m02 + m20) / s;
            q[2] = (m12 + m21) / s;
            q[3] = 0.25f * s;
        }
    }

    private static void bezier(float[] a, float[] b, float[] c, float u, float[] out) {
        float v = 1f - u;
        for (int i = 0; i < 3; i++) out[i] = v * v * a[i] + 2f * u * v * b[i] + u * u * c[i];
    }

    static float span(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    static float ease(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    static float mix(float a, float b, float u) {
        return a + (b - a) * u;
    }
}
