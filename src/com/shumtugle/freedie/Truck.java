package com.shumtugle.freedie;

import java.util.ArrayList;

/**
 * The film between the first episode and the second, made of the street itself, a few
 * seconds long and without a word: a tipper truck backs up the road to the plinth, beeping,
 * stops with a hiss of its brakes, and tips its load out onto the road behind the plinth:
 * steel drums, old tyres, road cones, the things the second episode builds with. Then it
 * lets its body down and drives off the way it came.
 *
 * The load is real: while it rides in the body it moves with it, and as the body rises it
 * slides to the tailgate row by row and goes over the edge into the physics, to tumble,
 * bounce off the plinth and lie where it falls. The film ends in the light, and the second
 * episode opens.
 *
 * The first time it plays whole; after that a touch cuts it short.
 */
final class Truck implements Film {
    // the truck in its own frame: x across, y up, z along its nose; its origin on the ground
    // under its middle. The body's hinge, at its tail, low; the body so long, so wide, so deep
    static final float HZ = -3.15f, HY = 1.2f, LEN = 4.3f, WIDE = 1.0f, SIDE = 0.8f, WALL = 0.05f;
    // how far behind the plinth the tail stops, and how far up the road it starts backing from
    static final float GAP = 2.2f, RUN = 24f;
    // the film, in seconds
    static final float REV1 = 2.3f, TIP0 = 2.7f, TIP1 = 3.7f, DOWN0 = 4.9f, DOWN1 = 5.55f, OFF0 = 5.5f, STREET = 6.8f;
    // the body's top angle; the angle at which the load starts to slide; how it slides
    static final float AMAX = 0.9f, SLIDE = 0.4f, ACC = 7f, VMAX = 4f, ROW_LAG = 0.1f;

    private final Game game;
    private final float plinthZ, stopZ;
    /** Whether this is the first time: then it plays whole. */
    private final boolean first;
    float t;
    private float was = -1f;
    private boolean cut, released;
    private float gate, gateV;

    /** What it brings, for the game to hand to the physics. */
    final ArrayList<Body> load = new ArrayList<Body>();
    // each piece of the load: where it rides in the body (along it, across it, up from the
    // floor to its middle), its row from the tail, how far it has slid, how fast, when it began
    private float[] lu, lx, lv, slid, speed, began;
    private int[] row;
    private boolean[] free;
    private final java.util.Random rnd = new java.util.Random(0x7A1C);

    Truck(Game game, float plinthZ, boolean first) {
        this.game = game;
        this.plinthZ = plinthZ;
        this.first = first;
        stopZ = plinthZ + Builder.PHZ1 + GAP - HZ;
        loadUp();
    }

    // ---- the load ----

    private static final float DRUM_R = Builder.DRUM_D * 0.5f * Builder.S, DRUM_HH = Builder.DRUM_H * 0.5f * Builder.S;
    private static final float TYRE_R = Builder.TYRE_D * 0.5f * Builder.S, TYRE_HH = Builder.TYRE_H * 0.5f * Builder.S;
    private static final float CONE_R = Builder.CONE_W * 0.5f * Builder.S, CONE_HH = Builder.CONE_H * 0.5f * Builder.S;

    // five rows from the tail, three across: d a drum, c a cone, a digit a stack of so many tyres
    private static final String[] ROWS = {"2c2", "ddd", "c3c", "d2d", "dcd"};
    private static final float[] ROW_U = {0.45f, 1.2f, 1.95f, 2.7f, 3.45f};
    private static final float[] COL_X = {-0.62f, 0f, 0.62f};

    private void loadUp() {
        ArrayList<float[]> spots = new ArrayList<float[]>();
        for (int r = 0; r < ROWS.length; r++)
            for (int c = 0; c < 3; c++) {
                char k = ROWS[r].charAt(c);
                float u = ROW_U[r] + (rnd.nextFloat() - 0.5f) * 0.08f, x = COL_X[c] + (rnd.nextFloat() - 0.5f) * 0.08f;
                if (k == 'd') {
                    add(new Body(DRUM_R, DRUM_HH, DRUM_R, Body.BARREL, Builder.DRUM_COLORS[rnd.nextInt(Builder.DRUM_COLORS.length)]));
                    spots.add(new float[]{u, x, DRUM_HH, r});
                } else if (k == 'c') {
                    add(new Body(CONE_R, CONE_HH, CONE_R, Body.CONE, 0xFFF0641E));
                    spots.add(new float[]{u, x, CONE_HH, r});
                } else {
                    int n = k - '0';
                    for (int i = 0; i < n; i++) {
                        add(new Body(TYRE_R, TYRE_HH, TYRE_R, Body.TYRE, Builder.TYRE_COLOR));
                        spots.add(new float[]{u, x, TYRE_HH * (2 * i + 1) + 0.004f * i, r});
                    }
                }
            }
        int n = load.size();
        lu = new float[n];
        lx = new float[n];
        lv = new float[n];
        slid = new float[n];
        speed = new float[n];
        began = new float[n];
        row = new int[n];
        free = new boolean[n];
        for (int i = 0; i < n; i++) {
            float[] s = spots.get(i);
            lu[i] = s[0];
            lx[i] = s[1];
            lv[i] = s[2];
            row[i] = (int) s[3];
            began[i] = -1f;
        }
        carry();
    }

    private void add(Body b) {
        // it rides in the body, touching nothing, until it goes over the tail
        b.ghost = true;
        b.asleep = false;
        load.add(b);
    }

    // ---- time ----

    private static float ease(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    private static float span(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
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

    /** Where the truck's origin is along the street: backing up to its stop, then pulling away. */
    private float truckZ() {
        if (t < OFF0) {
            float k = span(t, 0f, REV1);
            float out = 1f - (1f - k) * (1f - k) * (1f - k);
            return stopZ + RUN * (1f - out);
        }
        float u = t - OFF0;
        return stopZ + 0.5f * 3.2f * u * u;
    }

    /** The body's angle above the chassis. */
    private float angle() {
        if (t < DOWN0) return AMAX * ease(span(t, TIP0, TIP1));
        return AMAX * (1f - ease(span(t, DOWN0, DOWN1)));
    }

    public void update(float dt) {
        was = t;
        t += dt;
        slideLoad(dt);
        carry();
        // the tailgate: it hangs from its top, and each piece going under it kicks it out
        gateV += (-gate * 38f - gateV * 5.5f) * dt;
        gate += gateV * dt;
        if (gate < 0f) {
            gate = 0f;
            gateV = 0f;
        }
        sounds();
        if (first && at(STREET - 0.05f)) game.host.saveInt("ins0", 1);
    }

    /** The load slides once the body is steep enough, the tail rows first; over the edge, it is let go. */
    private void slideLoad(float dt) {
        float a = angle();
        float sa = (float) Math.sin(a), ca = (float) Math.cos(a);
        float tz = truckZ();
        for (int i = 0; i < load.size(); i++) {
            if (free[i]) continue;
            Body b = load.get(i);
            if (began[i] < 0f && a >= SLIDE) began[i] = t + row[i] * ROW_LAG + rnd.nextFloat() * 0.06f;
            if (began[i] >= 0f && t >= began[i]) {
                speed[i] = Math.min(VMAX, speed[i] + ACC * dt);
                slid[i] += speed[i] * dt;
            }
            float u = lu[i] - slid[i];
            // over the edge, or the body coming down with it still aboard: let go
            if (u < -0.05f || t >= DOWN0) {
                free[i] = true;
                b.ghost = false;
                b.asleep = false;
                float v = Math.max(1.2f, speed[i]);
                b.vx = (rnd.nextFloat() - 0.5f) * 0.9f;
                b.vy = -v * sa;
                b.vz = -v * ca;
                // it tips over the edge, nose first
                b.wx = -(2f + rnd.nextFloat() * 3f);
                b.wy = (rnd.nextFloat() - 0.5f) * 2f;
                b.wz = (rnd.nextFloat() - 0.5f) * 2f;
                if (!released) {
                    released = true;
                    game.host.sound(Synth.CLANG, 0.55f, 1.25f);
                }
                gateV += 3.5f + rnd.nextFloat() * 1.5f;
                // a little grit and dust off the floor of the body as it goes
                for (int k = 0; k < 3; k++) game.spawn(b.px, HY + 0.05f, tz + HZ - 0.1f, true, 0xBDB2A0, 0.6f);
            }
        }
    }

    /** What still rides in the body, set where the body has it. */
    private void carry() {
        float a = angle();
        float sa = (float) Math.sin(a), ca = (float) Math.cos(a);
        float tz = truckZ();
        // a turn about the street's x by -a, as a quaternion
        float qw = (float) Math.cos(a * 0.5f), qx = -(float) Math.sin(a * 0.5f);
        for (int i = 0; i < load.size(); i++) {
            if (free[i]) continue;
            float u = lu[i] - slid[i], v = lv[i];
            float y = HY + u * sa + v * ca, z = tz + HZ + u * ca - v * sa;
            load.get(i).setPose(lx[i], y, z, qw, qx, 0f, 0f);
        }
    }

    private void sounds() {
        World.Host h = game.host;
        if (at(0.02f)) h.sound(Synth.DIESEL, 0.7f, 0.82f);
        // the beeper, every half second while it backs up
        for (float b = 0.1f; b < REV1; b += 0.5f) if (at(b)) h.sound(Synth.BEEP, 0.55f, 1f);
        if (at(REV1 + 0.05f)) {
            h.sound(Synth.HISS, 0.7f, 1f);
            h.buzz(20);
        }
        if (at(TIP0)) h.sound(Synth.WINCH, 0.75f, 0.6f);
        if (at(DOWN0)) h.sound(Synth.WINCH, 0.55f, 0.52f);
        if (at(DOWN1 - 0.05f)) {
            h.sound(Synth.CLANG, 0.6f, 0.7f);
            h.buzz(25);
        }
        if (at(OFF0)) h.sound(Synth.DIESEL, 0.95f, 1.1f);
        if (t > OFF0 && t < OFF0 + 0.9f && ((int) (t * 14f) != (int) (was * 14f))) {
            // the exhaust, black, as it pulls away
            float z = truckZ() + 1.36f;
            game.spawn(1.17f, 3.05f, z, true, 0x3E3C3A, 0.5f);
        }
    }

    // ---- the eye ----

    // low by the plinth looking up the road as it comes; up over the plinth to look down on the
    // load going out; back down to where the player stands
    private static final float[] KT = {0f, REV1, 3.6f, 5.2f, STREET};
    private final float[] p = new float[3], q = new float[3];

    public void camera(DrawList d, int w) {
        float cz = plinthZ, hinge = stopZ + HZ, heap = plinthZ + Builder.PHZ1 + 1.2f;
        float yaw0 = (float) Math.atan2(-World.CAM_X, World.PLINTH_Z);
        float[][] kp = {{-1.9f, 1.15f, cz - 6.5f}, {-2.6f, 2.8f, cz - 5.2f}, {-3.4f, 6.6f, cz - 4.8f}, {-3.0f, 4.2f, cz - 6.2f},
                {World.CAM_X, World.CAM_Y, World.CAM_Z}};
        float[][] kl = {{0f, 1.9f, hinge + 14f}, {0f, 1.3f, hinge - 0.3f}, {0f, 0.3f, heap - 0.1f}, {0f, 0.6f, heap + 0.4f},
                {World.CAM_X + 20f * (float) Math.sin(yaw0), World.CAM_Y, World.CAM_Z + 20f * (float) Math.cos(yaw0)}};
        path(kp, p);
        path(kl, q);
        float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        // the brakes biting shake the eye a little
        if (t > REV1) pitch += 0.008f * (float) Math.exp(-(t - REV1) * 8f) * (float) Math.sin((t - REV1) * 50f);
        float k = ease(span(t, 0f, STREET));
        d.camera(p[0], p[1], p[2], yaw, pitch, w * (1.55f + 0.35f * k), 0.1f, 500f);
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

    static final int CAB = 0xE07A2C, STEEL = 0x7A7872, RIB = 0x5E5C58, DARK = 0x2A2829, TYRE = 0x1C1B1D;
    private final float[] rot = new float[9];
    private float oz;

    public void draw(DrawList d) {
        oz = truckZ();
        cab(d);
        chassis(d);
        body(d);
    }

    /** A box in the truck's own frame, square to the street. */
    private void box(DrawList d, float x, float y, float z, float hx, float hy, float hz, int rgb, int mat) {
        d.box(x, y, oz + z, hx, hy, hz, null, rgb, mat);
    }

    /** The cab over the front axle: flat-fronted, glass all round its top, a beacon on its roof. */
    private void cab(DrawList d) {
        int glass = DrawList.GLASS, paint = DrawList.PAINT;
        int clear = 0x33414E;
        box(d, 0f, 1.62f, 2.3f, 1.12f, 0.78f, 0.85f, CAB, paint);
        // the glass: the screen in front, a window each side, a small one at the back
        box(d, 0f, 2.18f, 3.152f, 1.0f, 0.4f, 0.006f, clear, glass);
        for (int s = -1; s <= 1; s += 2) {
            box(d, s * 1.122f, 2.18f, 2.5f, 0.006f, 0.36f, 0.55f, clear, glass);
            // the door's seam and handle
            box(d, s * 1.123f, 1.45f, 1.92f, 0.004f, 0.42f, 0.012f, Street.scale(CAB, 0.7f), paint);
            box(d, s * 1.125f, 1.6f, 2.0f, 0.006f, 0.025f, 0.08f, DARK, DrawList.PLAIN);
            // the mirror on its arm
            box(d, s * 1.24f, 2.3f, 3.0f, 0.12f, 0.02f, 0.02f, DARK, DrawList.PLAIN);
            box(d, s * 1.36f, 2.2f, 3.0f, 0.03f, 0.2f, 0.09f, DARK, DrawList.PLAIN);
            // the lamps
            box(d, s * 0.82f, 1.08f, 3.155f, 0.17f, 0.08f, 0.006f, 0xFFF0C2, DrawList.PLAIN);
            box(d, s * 1.02f, 1.08f, 3.155f, 0.06f, 0.08f, 0.006f, 0xFFB13A, DrawList.PLAIN);
            // the step under the door
            box(d, s * 1.0f, 0.62f, 2.0f, 0.14f, 0.03f, 0.3f, DARK, DrawList.PLAIN);
        }
        // the roof, a shade darker, and the grille
        box(d, 0f, 2.41f, 2.3f, 1.1f, 0.012f, 0.83f, Street.scale(CAB, 0.85f), paint);
        box(d, 0f, 1.2f, 3.154f, 0.58f, 0.22f, 0.006f, DARK, DrawList.PLAIN);
        for (int k = 0; k < 4; k++) box(d, 0f, 1.05f + k * 0.1f, 3.158f, 0.56f, 0.012f, 0.004f, 0x55585E, DrawList.PLAIN);
        // the bumper
        box(d, 0f, 0.62f, 3.2f, 1.16f, 0.12f, 0.06f, 0x4A4846, DrawList.PLAIN);
        // the beacon: amber, turning, so it flashes
        boolean on = ((int) (t * 5f) & 1) == 0;
        box(d, 0f, 2.52f, 2.2f, 0.16f, 0.1f, 0.12f, on ? 0xFFB13A : 0x8A5A20, on ? DrawList.GLOW : DrawList.PLAIN);
        box(d, 0f, 2.435f, 2.2f, 0.2f, 0.02f, 0.16f, DARK, DrawList.PLAIN);
        // the exhaust stack behind the cab
        box(d, 1.17f, 2.2f, 1.36f, 0.05f, 0.85f, 0.05f, 0x3A3A3E, DrawList.PLAIN);
    }

    /** The chassis: two rails, the wheels, the mudguards, the lamps at the tail. */
    private void chassis(DrawList d) {
        for (int s = -1; s <= 1; s += 2) {
            box(d, s * 0.55f, 0.82f, -0.9f, 0.08f, 0.12f, 2.32f, DARK, DrawList.PLAIN);
            // front wheel; two at the back, doubled
            wheel(d, s * 1.0f, 2.25f, 0.13f);
            wheel(d, s * 0.92f, -0.85f, 0.25f);
            wheel(d, s * 0.92f, -1.95f, 0.25f);
            // the mudguard over the back pair, and a flap behind it
            box(d, s * 0.95f, 1.06f, -1.4f, 0.32f, 0.03f, 1.2f, 0x3A3A3E, DrawList.PLAIN);
            box(d, s * 0.95f, 0.62f, -2.62f, 0.28f, 0.28f, 0.01f, DARK, DrawList.PLAIN);
            // the tail lamps; the reversing lamp lit while it backs
            box(d, s * 0.85f, 0.86f, -3.2f, 0.16f, 0.07f, 0.02f, 0xD43A2E, DrawList.PLAIN);
            boolean back = t < REV1;
            box(d, s * 0.62f, 0.86f, -3.2f, 0.06f, 0.07f, 0.02f, back ? 0xFFF6E0 : 0xB8B4AC, back ? DrawList.GLOW : DrawList.PLAIN);
        }
        // the rear bar across under the tail, striped
        box(d, 0f, 0.62f, -3.18f, 1.0f, 0.06f, 0.04f, 0xE9E1CB, DrawList.PAINT);
        for (int k = -2; k <= 2; k++) box(d, k * 0.4f, 0.62f, -3.225f, 0.1f, 0.055f, 0.006f, 0xB5352C, DrawList.PAINT);
    }

    private void wheel(DrawList d, float x, float z, float hx) {
        box(d, x, 0.52f, z, hx, 0.52f, 0.52f, TYRE, DrawList.PLAIN);
        float side = x > 0f ? 1f : -1f;
        box(d, x + side * (hx + 0.005f), 0.52f, z, 0.006f, 0.24f, 0.24f, 0xB8B4AC, DrawList.PLAIN);
    }

    // the body's turn: about x by -a, so its floor rises toward the nose
    private float sa, ca;

    /** A box in the body's frame: along it from the hinge, up from its floor, across it. */
    private void bed(DrawList d, float u, float v, float x, float hu, float hv, float hx, int rgb, int mat) {
        rot[0] = 1f; rot[1] = 0f; rot[2] = 0f;
        rot[3] = 0f; rot[4] = ca; rot[5] = sa;
        rot[6] = 0f; rot[7] = -sa; rot[8] = ca;
        d.box(x, HY + u * sa + v * ca, oz + HZ + u * ca - v * sa, hx, hv, hu, rot, rgb, mat);
    }

    /** The tipping body, its ram, and the tailgate hanging from its top edge. */
    private void body(DrawList d) {
        float a = angle();
        sa = (float) Math.sin(a);
        ca = (float) Math.cos(a);
        float m = LEN * 0.5f;
        // the floor, the sides with their ribs and top rail, the headboard
        bed(d, m, -0.05f, 0f, m, 0.05f, WIDE + WALL, STEEL, DrawList.PAINT);
        bed(d, m, -0.16f, 0f, m - 0.1f, 0.06f, 0.5f, RIB, DrawList.PAINT);
        for (int s = -1; s <= 1; s += 2) {
            bed(d, m, SIDE * 0.5f, s * (WIDE + WALL * 0.5f), m, SIDE * 0.5f, WALL * 0.5f, STEEL, DrawList.PAINT);
            bed(d, m, SIDE + 0.03f, s * (WIDE + WALL), m, 0.04f, 0.04f, RIB, DrawList.PAINT);
            for (int k = 0; k < 5; k++) bed(d, 0.3f + k * 0.92f, SIDE * 0.5f, s * (WIDE + WALL + 0.02f), 0.04f, SIDE * 0.48f, 0.02f, RIB, DrawList.PAINT);
        }
        bed(d, LEN + 0.03f, 0.58f, 0f, 0.03f, 0.62f, WIDE + WALL, STEEL, DrawList.PAINT);
        // its lip over the cab's roof
        bed(d, LEN + 0.35f, 1.25f, 0f, 0.35f, 0.03f, WIDE * 0.8f, RIB, DrawList.PAINT);
        // the ram: from the chassis in front of the body up to the headboard, telescoped out
        float bx = 0f, by = 0.95f, bz = oz + 1.3f;
        float u = LEN + 0.1f, v = 0.32f;
        float ty = HY + u * sa + v * ca, tzz = oz + HZ + u * ca - v * sa;
        float dx = 0f, dy = ty - by, dz = tzz - bz, len = (float) Math.sqrt(dy * dy + dz * dz);
        float k1 = Math.min(1f, 0.75f / len), k2 = Math.min(1f, 1.5f / len);
        Crane.beam(d, bx, by, bz, bx + dx * k1, by + dy * k1, bz + dz * k1, 0.1f, 0x3A3A3E, DrawList.PLAIN);
        Crane.beam(d, bx, by + dy * k1, bz + dz * k1, bx, by + dy * k2, bz + dz * k2, 0.078f, 0x8A8E92, DrawList.POLISHED);
        Crane.beam(d, bx, by + dy * k2, bz + dz * k2, bx, ty, tzz, 0.058f, 0xB8BCC0, DrawList.POLISHED);
        // the tailgate: hung from the top of the tail, straight down, kicked out by what goes under it
        float hy = HY + SIDE * ca, hz = oz + HZ - SIDE * sa - 0.03f;
        float phi = Math.min(1.3f, gate + 0.55f * a);
        float c = (float) Math.cos(phi), s = (float) Math.sin(phi);
        rot[0] = 1f; rot[1] = 0f; rot[2] = 0f;
        rot[3] = 0f; rot[4] = c; rot[5] = -s;
        rot[6] = 0f; rot[7] = s; rot[8] = c;
        float h = SIDE * 0.5f;
        d.box(0f, hy - h * c, hz - h * s, WIDE + WALL, h, 0.03f, rot, STEEL, DrawList.PAINT);
        d.box(0f, hy - 0.04f, hz - 0.01f, WIDE + WALL + 0.02f, 0.04f, 0.05f, rot, RIB, DrawList.PAINT);
    }

    public void flat(Px f) {
    }
}
