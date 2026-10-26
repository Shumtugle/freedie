package com.shumtugle.freedie;

/**
 * The film between the third episode and the fourth, made of the street itself, a few
 * seconds long and without a word: a tower crane on the promenade carries the plinth
 * farther down the road, where the fourth episode stands it.
 *
 * Out of the light the eye is low by the curb, looking up at the jib across the sky. The
 * hook comes down on its two cables, the slings take the plinth's corners, the winch whines
 * and the plinth leaves the asphalt; the jib slews, the plinth swinging under it as a weight
 * on a rope does, while the eye goes up and round in an arc; it is set down three meters on
 * with a thud and a puff of dust, the slings drop, the hook goes up, and the eye comes back
 * down to where the player stands.
 *
 * The first time only, the hook comes down once more, close, with a golden cube hanging
 * from it: the game's gift. The cube leaves the hook and flies up into the rack in the crest.
 * The film then ends in the light, as it began, and the fourth episode opens.
 *
 * The first time it plays whole; after that a touch cuts it short.
 */
final class Crane {
    // the crane: its mast on the promenade, its jib so high and so long, the counter-jib behind
    static final float MX = -8f, MZ = 22f, MAST = 1.6f, RING_Y = 22.4f, JIB_Y = 24f, JIB = 20f, BACK = 7f;
    static final float TROLLEY_Y = JIB_Y - 0.9f;
    // where the hook waits, high up and out of sight
    static final float HIGH = 18f;
    // the slings: from the hook to the plinth's top, so far; and how high the plinth is lifted
    static final float SLINGS = 2.6f, LIFT = 3f;
    // the film, in seconds
    static final float DOWN0 = 0.2f, DOWN1 = 1.5f, GRIP0 = 1.45f, GRIP1 = 1.75f, LIFT0 = 1.9f, LIFT1 = 2.75f,
            SLEW0 = 2.5f, SLEW1 = 3.9f, LOWER0 = 3.65f, LAND = 4.4f, LET0 = 4.55f, LET1 = 4.85f, UP0 = 4.7f, UP1 = 5.5f,
            STREET = 5.6f;
    // the gift: the jib round to the front, the hook down with the cube, the cube up into the rack
    static final float GSLEW0 = 5.2f, GSLEW1 = 6.0f, GDOWN0 = 5.85f, GDOWN1 = 6.6f, GOFF = 7.05f, GIN = 7.6f, GEND = 7.9f;
    // where the cube is brought, in front of the player
    static final float GX = -1.3f, GZ = 7.2f, GHOOK = 3.35f, CHAIN = 0.5f, CUBE = 0.24f;
    // the swing: a load on a short rope for the eye, so it sways while it is carried
    static final float ROPE = 1.4f, DAMP = 0.7f, PULL = 0.25f;

    private final Game game;
    private final float fromZ, toZ;
    /** Whether the cube comes: the first time the film is seen. */
    final boolean gift;
    private final int cubesBefore, next;
    float t;
    private float was = -1f;
    private boolean cut;

    // the slew and the trolley: where the hook hangs from, now and the step before
    private float ang, rad, tx, tz, ptx, ptz, pvx, pvz;
    private boolean moved;
    // the load swinging under the trolley: its offset and speed across the ground
    private float ox, oz, ovx, ovz;
    private float hookY = HIGH + 2f, lift;
    private float grip;
    private final float a0, a1, aG, r0, rG;

    // the cube's flight into the rack, from where it was seen on the hook
    private float flyX = -1f, flyY, flyS;
    private Px top;
    private final float[] scr = new float[2], scr2 = new float[2], rot = new float[9];
    private final java.util.Random rnd = new java.util.Random(0xC4A3EL);

    Crane(Game game, float fromZ, float toZ, boolean gift, int cubes, int next) {
        this.game = game;
        this.fromZ = fromZ;
        this.toZ = toZ;
        this.gift = gift;
        this.cubesBefore = cubes;
        this.next = next;
        a0 = (float) Math.atan2(fromZ - MZ, -MX);
        a1 = (float) Math.atan2(toZ - MZ, -MX);
        aG = (float) Math.atan2(GZ - MZ, GX - MX);
        r0 = (float) Math.hypot(-MX, fromZ - MZ);
        rG = (float) Math.hypot(GX - MX, GZ - MZ);
        ang = a0;
        rad = r0;
        trolley();
        ptx = tx;
        ptz = tz;
    }

    /** Whether the film has run its course, or was cut short: the light may rise. */
    boolean over() {
        return cut || t >= (gift ? GEND : STREET);
    }

    /** A touch: after the first time, it cuts the film short. */
    void tap() {
        if (!gift && t > 1f) cut = true;
    }

    // ---- time ----

    private static float ease(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    private static float span(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    /** Whether this step has just passed the given moment. */
    private boolean at(float moment) {
        return was < moment && t >= moment;
    }

    private void trolley() {
        tx = MX + rad * (float) Math.cos(ang);
        tz = MZ + rad * (float) Math.sin(ang);
    }

    void update(float dt) {
        was = t;
        t += dt;
        // the slew, and the trolley along the jib
        if (gift && t >= GSLEW0) {
            float k = ease(span(t, GSLEW0, GSLEW1));
            ang = a1 + (aG - a1) * k;
            rad = r0 + (rG - r0) * k;
        } else ang = a0 + (a1 - a0) * ease(span(t, SLEW0, SLEW1));
        trolley();
        // the hook: down to the plinth, up with it and down again, up out of sight, down with the cube
        lift = 0f;
        if (t < LIFT0) {
            float k = span(t, DOWN0, DOWN1);
            float out = 1f - (1f - k) * (1f - k) * (1f - k);
            hookY = HIGH + 2f + (Builder.TOP + SLINGS - HIGH - 2f) * out;
        } else if (t < UP0) {
            lift = t < LOWER0 ? LIFT * ease(span(t, LIFT0, LIFT1)) : LIFT * (1f - ease(span(t, LOWER0, LAND)));
            hookY = Builder.TOP + SLINGS + lift;
        } else if (!gift || t < GDOWN0) {
            float k = span(t, UP0, UP1);
            hookY = Builder.TOP + SLINGS + (HIGH - Builder.TOP - SLINGS) * k * k;
        } else {
            float k = span(t, GDOWN0, GDOWN1);
            float out = 1f - (1f - k) * (1f - k);
            // it comes to rest with a little give in the cables
            hookY = HIGH + (GHOOK - HIGH) * out + 0.12f * (float) Math.sin(Math.min(1f, Math.max(0f, (t - GDOWN1) / 0.5f)) * Math.PI) * (t > GDOWN1 ? 1f : 0f);
        }
        grip = t < LET0 ? ease(span(t, GRIP0, GRIP1)) : 1f - ease(span(t, LET0, LET1));
        swing(dt);
        sounds();
    }

    /**
     * The load sways under the trolley: whatever the trolley does, the load lags and swings
     * after it, its rope pulling it back, the air slowly calming it. On the ground it is still.
     */
    private void swing(float dt) {
        int n = 4;
        float h = dt / n;
        float vx = (tx - ptx) / Math.max(1e-4f, dt), vz = (tz - ptz) / Math.max(1e-4f, dt);
        float ax = moved ? (vx - pvx) / Math.max(1e-4f, dt) : 0f, az = moved ? (vz - pvz) / Math.max(1e-4f, dt) : 0f;
        moved = true;
        pvx = vx;
        pvz = vz;
        ptx = tx;
        ptz = tz;
        boolean free = t >= LIFT0 && lift > 0.05f || gift && t >= GSLEW0;
        if (!free) {
            ox = oz = ovx = ovz = 0f;
            return;
        }
        float w2 = -Physics.G / ROPE;
        // the long slew round to the front is gentler on what hangs from it
        float pull = gift && t >= GSLEW0 ? PULL * 0.4f : PULL;
        for (int i = 0; i < n; i++) {
            ovx += (-w2 * ox - pull * ax - DAMP * ovx) * h;
            ovz += (-w2 * oz - pull * az - DAMP * ovz) * h;
            ox += ovx * h;
            oz += ovz * h;
        }
        float m = 0.6f;
        ox = Math.max(-m, Math.min(m, ox));
        oz = Math.max(-m, Math.min(m, oz));
    }

    /** The swing as it shows: on the way down the plinth is steadied, so it lands square where it is meant to. */
    private float steady() {
        return t > LOWER0 && t < UP0 ? ease(lift / 1.6f) : 1f;
    }

    private void sounds() {
        World.Host h = game.host;
        if (at(DOWN0)) {
            h.sound(Synth.WINCH, 0.55f, 1.15f);
            h.sound(Synth.CHAIN, 0.4f, 0.9f);
        }
        if (at(GRIP0 + 0.1f)) h.sound(Synth.CLANG, 0.5f, 1.6f);
        if (at(GRIP1)) {
            h.sound(Synth.CLANG, 0.45f, 1.9f);
            h.buzz(15);
        }
        if (at(LIFT0)) {
            h.sound(Synth.WINCH, 0.85f, 0.9f);
            h.sound(Synth.STONE, 0.4f, 0.55f);
        }
        if (at(SLEW0 + 0.3f)) h.sound(Synth.WINCH, 0.6f, 0.75f);
        if (at(LOWER0)) h.sound(Synth.WINCH, 0.7f, 0.82f);
        if (at(LAND)) {
            h.sound(Synth.GROUND, 1f, 0.7f);
            h.sound(Synth.STONE, 0.9f, 0.6f);
            h.buzz(70);
            float hx = Builder.PHX, hz = Builder.PHZ1;
            for (int i = 0; i < 34; i++) {
                // a puff of dust all round its foot
                float u = rnd.nextFloat() * 2f - 1f;
                boolean side = rnd.nextBoolean();
                float x = side ? u * hx : (rnd.nextBoolean() ? hx : -hx);
                float z = toZ + (side ? (rnd.nextBoolean() ? hz : -hz) : u * hz);
                game.spawn(x, 0.08f, z, true, 0xD9CDB8, 1.6f);
            }
            for (int i = 0; i < 8; i++) game.spawn((rnd.nextFloat() - 0.5f) * 2f * hx, 0.1f, toZ + (rnd.nextFloat() - 0.5f) * 2f * hz, false, 0x6E6862, 2.2f);
        }
        if (at(LET0)) {
            h.sound(Synth.CLANG, 0.35f, 2.1f);
            h.sound(Synth.CHAIN, 0.5f, 1.1f);
        }
        if (at(UP0)) h.sound(Synth.WINCH, 0.5f, 1.2f);
        if (!gift) return;
        if (at(GDOWN0)) {
            h.sound(Synth.WINCH, 0.6f, 1.1f);
            h.sound(Synth.CHAIN, 0.45f, 1.25f);
        }
        if (at(GDOWN1)) h.sound(Synth.CLANG, 0.25f, 2.6f);
        if (at(GOFF)) h.sound(Synth.WHOOSH, 0.6f, 1.4f);
        if (at(GIN)) {
            // the cube is in the rack: the game's, for good
            h.sound(Synth.CHIME, 0.9f, 1f);
            h.buzz(30);
            game.cubeGift = true;
            h.saveInt("ins2", 1);
        }
    }

    // ---- the eye ----

    // the eye's path, over the road and clear of the palms: low looking up at the crane, close to
    // the hook as it takes the plinth, up with the load and over it as it is set down, then back
    // down to where the player stands
    private static final float[] KT = {0f, 1.5f, 3.0f, 4.2f, STREET};
    private static final float[][] KP = {{-1.6f, 0.9f, 7.0f}, {-1.9f, 2.6f, 13.5f}, {-1.2f, 8.5f, 13.0f}, {0.6f, 12.0f, 15.5f},
            {World.CAM_X, World.CAM_Y, World.CAM_Z}};
    private final float[] p = new float[3], q = new float[3];

    void camera(DrawList d, int w) {
        // where it looks: up at the crane, then the plinth wherever it is, then down the street
        float yaw0 = (float) Math.atan2(-World.CAM_X, World.PLINTH_Z);
        float[][] kl = {{-3f, 15f, 21f}, {0f, 2.2f, fromZ}, {0f, 3.4f, fromZ + 1.2f}, {0f, 0.8f, toZ},
                {World.CAM_X + 20f * (float) Math.sin(yaw0), World.CAM_Y, World.CAM_Z + 20f * (float) Math.cos(yaw0)}};
        path(KP, p);
        path(kl, q);
        float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        // the plinth set down shakes the eye a little
        if (t > LAND) pitch += 0.012f * (float) Math.exp(-(t - LAND) * 9f) * (float) Math.sin((t - LAND) * 60f);
        // wider at first, to take the crane in; the street's own lens at the end
        float k = ease(span(t, 0f, STREET));
        float focal = w * (1.45f + 0.45f * k);
        d.camera(p[0], p[1], p[2], yaw, pitch, focal, 0.1f, 500f);
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

    static final int YELLOW = 0xF0B428, STEEL = 0x2C2C30, CONCRETE = 0xB9B3A8, PLINTH = 0xE6D8BC;

    void draw(DrawList d) {
        mast(d);
        top(d);
        float k = steady(), hx = tx + ox * k, hz = tz + oz * k;
        hook(d, hx, hz);
        plinth(d, hx, hz);
        if (gift && t >= GSLEW0 && t < GOFF) cube(d, hx, hz);
    }

    /** The mast: a square lattice tower on its ballast, standing still while the top slews. */
    private void mast(DrawList d) {
        float s = MAST * 0.5f;
        d.box(MX, 0.35f, MZ, 1.6f, 0.35f, 1.6f, null, CONCRETE, DrawList.STONE);
        d.box(MX, 0.95f, MZ, 1.1f, 0.25f, 1.1f, null, Street.scale(CONCRETE, 0.92f), DrawList.STONE);
        float y0 = 1.2f, y1 = RING_Y;
        for (int c = 0; c < 4; c++) {
            float cx = MX + ((c & 1) == 0 ? -s : s), cz = MZ + (c < 2 ? -s : s);
            d.box(cx, (y0 + y1) * 0.5f, cz, 0.08f, (y1 - y0) * 0.5f, 0.08f, null, YELLOW, DrawList.PAINT);
        }
        int sections = 10;
        float step = (y1 - y0) / sections;
        for (int k = 0; k <= sections; k++) {
            float y = y0 + k * step;
            for (int f = 0; f < 4; f++) {
                // each face: a rung, and a brace up to the next rung, the other way on the next
                float ax, az, bx, bz;
                if (f == 0) { ax = -s; az = -s; bx = s; bz = -s; }
                else if (f == 1) { ax = s; az = -s; bx = s; bz = s; }
                else if (f == 2) { ax = s; az = s; bx = -s; bz = s; }
                else { ax = -s; az = s; bx = -s; bz = -s; }
                beam(d, MX + ax, y, MZ + az, MX + bx, y, MZ + bz, 0.04f, YELLOW, DrawList.PAINT);
                if (k == sections) continue;
                if ((k & 1) == 0) beam(d, MX + ax, y, MZ + az, MX + bx, y + step, MZ + bz, 0.035f, YELLOW, DrawList.PAINT);
                else beam(d, MX + bx, y, MZ + bz, MX + ax, y + step, MZ + az, 0.035f, YELLOW, DrawList.PAINT);
            }
        }
    }

    // the slewing top, in its own frame: u along the jib, w across it
    private float ca, sa;

    private float wx(float u, float w) {
        return MX + u * ca - w * sa;
    }

    private float wz(float u, float w) {
        return MZ + u * sa + w * ca;
    }

    private void part(DrawList d, float u, float y, float w, float hu, float hy, float hw, int rgb, int mat) {
        rot[0] = ca; rot[1] = 0f; rot[2] = -sa;
        rot[3] = 0f; rot[4] = 1f; rot[5] = 0f;
        rot[6] = sa; rot[7] = 0f; rot[8] = ca;
        d.box(wx(u, w), y, wz(u, w), hu, hy, hw, rot, rgb, mat);
    }

    private void bar(DrawList d, float u0, float y0, float w0, float u1, float y1, float w1, float half) {
        beam(d, wx(u0, w0), y0, wz(u0, w0), wx(u1, w1), y1, wz(u1, w1), half, YELLOW, DrawList.PAINT);
    }

    /** The slewing top: the ring and the cab, the jib and its trolley, the counter-jib and its weights, the peak and its ties. */
    private void top(DrawList d) {
        ca = (float) Math.cos(ang);
        sa = (float) Math.sin(ang);
        part(d, 0f, RING_Y + 0.25f, 0f, 1.0f, 0.25f, 1.0f, YELLOW, DrawList.PAINT);
        part(d, 0.6f, RING_Y + 1.0f, 1.35f, 0.65f, 0.6f, 0.55f, 0xEDE8DC, DrawList.PAINT);
        part(d, 1.27f, RING_Y + 1.15f, 1.35f, 0.02f, 0.32f, 0.45f, 0x4D6A80, DrawList.GLASS);
        // the jib: two chords below, one above, braced in a zigzag
        float lo = JIB_Y - 0.6f, hi = JIB_Y + 0.6f, wb = 0.6f;
        bar(d, 0.4f, lo, -wb, JIB, lo, -wb, 0.06f);
        bar(d, 0.4f, lo, wb, JIB, lo, wb, 0.06f);
        bar(d, 0.4f, hi, 0f, JIB - 0.8f, hi, 0f, 0.06f);
        bar(d, JIB - 0.8f, hi, 0f, JIB, lo, 0f, 0.05f);
        int n = 10;
        for (int k = 0; k <= n; k++) {
            float u = 0.4f + (JIB - 0.4f) * k / n;
            bar(d, u, lo, -wb, u, lo, wb, 0.035f);
            if (k == n) continue;
            float um = 0.4f + (JIB - 0.4f) * (k + 0.5f) / n;
            if (um > JIB - 0.8f) continue;
            bar(d, u, lo, -wb, um, hi, 0f, 0.03f);
            bar(d, u, lo, wb, um, hi, 0f, 0.03f);
            float u1 = 0.4f + (JIB - 0.4f) * (k + 1) / n;
            if (u1 <= JIB - 0.8f) {
                bar(d, um, hi, 0f, u1, lo, -wb, 0.03f);
                bar(d, um, hi, 0f, u1, lo, wb, 0.03f);
            }
        }
        // the counter-jib, and its concrete weights at the end
        float cy = JIB_Y - 0.3f;
        bar(d, -0.4f, cy, -wb, -BACK, cy, -wb, 0.06f);
        bar(d, -0.4f, cy, wb, -BACK, cy, wb, 0.06f);
        for (int k = 1; k <= 4; k++) bar(d, -BACK * k / 4f, cy, -wb, -BACK * k / 4f, cy, wb, 0.04f);
        for (int k = 0; k < 3; k++) part(d, -BACK + 0.45f + k * 0.92f, cy - 0.85f, 0f, 0.42f, 0.8f, 0.9f, CONCRETE, DrawList.STONE);
        part(d, -2.5f, cy + 0.3f, 0f, 0.7f, 0.3f, 0.5f, 0x8A8E92, DrawList.PAINT);
        // the peak, and its ties out to the jib and back to the counter-jib
        float py = JIB_Y + 4.5f;
        for (int c = 0; c < 4; c++) {
            float cu = (c & 1) == 0 ? -0.6f : 0.6f, cw = c < 2 ? -0.6f : 0.6f;
            bar(d, cu, RING_Y + 0.5f, cw, 0f, py, 0f, 0.06f);
        }
        beam(d, wx(0f, 0f), py, wz(0f, 0f), wx(JIB * 0.62f, 0f), hi, wz(JIB * 0.62f, 0f), 0.025f, STEEL, DrawList.PLAIN);
        beam(d, wx(0f, 0f), py, wz(0f, 0f), wx(-BACK, -wb), cy, wz(-BACK, -wb), 0.025f, STEEL, DrawList.PLAIN);
        beam(d, wx(0f, 0f), py, wz(0f, 0f), wx(-BACK, wb), cy, wz(-BACK, wb), 0.025f, STEEL, DrawList.PLAIN);
        // the trolley under the jib
        part(d, rad, lo - 0.18f, 0f, 0.45f, 0.14f, 0.55f, 0x55585E, DrawList.PAINT);
    }

    /** The hook: two cables from the trolley to its block, the block in yellow and black, the hook under it. */
    private void hook(DrawList d, float hx, float hz) {
        float by = hookY + 0.55f;
        for (int s = -1; s <= 1; s += 2) {
            float du = 0.22f * s;
            beam(d, tx + ca * du, TROLLEY_Y - 0.25f, tz + sa * du, hx + ca * du, by + 0.3f, hz + sa * du, 0.018f, STEEL, DrawList.PLAIN);
        }
        rot[0] = ca; rot[1] = 0f; rot[2] = -sa;
        rot[3] = 0f; rot[4] = 1f; rot[5] = 0f;
        rot[6] = sa; rot[7] = 0f; rot[8] = ca;
        d.box(hx, by, hz, 0.3f, 0.3f, 0.17f, rot, YELLOW, DrawList.PAINT);
        for (int k = 0; k < 3; k++)
            d.box(hx, by - 0.2f + k * 0.2f, hz, 0.305f, 0.045f, 0.175f, rot, 0x1E1E20, DrawList.PAINT);
        d.box(hx, hookY + 0.16f, hz, 0.05f, 0.1f, 0.05f, rot, STEEL, DrawList.PLAIN);
        d.box(hx, hookY + 0.03f, hz, 0.06f, 0.05f, 0.14f, rot, STEEL, DrawList.PLAIN);
        d.box(hx, hookY + 0.1f, hz - ca * 0.12f, 0.04f, 0.09f, 0.04f, rot, STEEL, DrawList.PLAIN);
    }

    /**
     * The plinth: on the ground where it stood, in the air under the hook, or on the ground
     * where it is set down; and the four slings from the hook to its corners, hanging loose
     * when they hold nothing.
     */
    private void plinth(DrawList d, float hx, float hz) {
        float px, py, pz;
        float ax = 0f, az = 0f;
        if (t < LIFT0 || t < LOWER0 && lift <= 0f) {
            px = 0f;
            py = Builder.TOP * 0.5f;
            pz = fromZ;
        } else if (t < LAND) {
            px = hx;
            pz = hz;
            py = Builder.TOP * 0.5f + lift;
            // it leans with the swing
            az = -ox * steady() / ROPE * 0.35f;
            ax = oz * steady() / ROPE * 0.35f;
        } else {
            // where it ends up, it ends up exactly: the swing has died out on the way down
            px = 0f;
            py = Builder.TOP * 0.5f;
            pz = toZ;
        }
        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax), cz = (float) Math.cos(az), sz = (float) Math.sin(az);
        // a lean about the street's axis, then across it
        float[] r = {cz, -sz, 0f, cx * sz, cx * cz, -sx, sx * sz, sx * cz, cx};
        float hxp = Builder.PHX, hzp = Builder.PHZ1, hy = Builder.TOP * 0.5f;
        d.box(px, py, pz, hxp, hy, hzp, r, PLINTH, DrawList.STONE);
        // the slings: from under the hook to the top corners, or hanging loose from it
        for (int c = 0; c < 4; c++) {
            float lx = ((c & 1) == 0 ? -1f : 1f) * hxp * 0.88f, lz = (c < 2 ? -1f : 1f) * hzp * 0.85f, ly = hy;
            float gx = px + r[0] * lx + r[1] * ly + r[2] * lz;
            float gy = py + r[3] * lx + r[4] * ly + r[5] * lz;
            float gz = pz + r[6] * lx + r[7] * ly + r[8] * lz;
            if (t >= LET0) {
                // let go: off the hook, they fall and lie across the top toward its middle
                float k = ease(span(t, LET0, LET1));
                float sx0 = hx + (px - hx) * k, sy0 = hookY + (py + hy + 0.025f - hookY) * k, sz0 = hz + (pz - hz) * k;
                beam(d, sx0, sy0, sz0, gx, gy + 0.025f, gz, 0.022f, 0x3A3A3E, DrawList.PLAIN);
                continue;
            }
            // loose: hanging down from the hook, a little apart
            float fx = hx + lx * 0.12f, fy = hookY - SLINGS * 0.95f, fz = hz + lz * 0.12f;
            float k = grip;
            float ex = fx + (gx - fx) * k, ey = fy + (gy - fy) * k, ez = fz + (gz - fz) * k;
            if (ey < 0.03f) ey = 0.03f;
            beam(d, hx, hookY, hz, ex, ey, ez, 0.022f, 0x3A3A3E, DrawList.PLAIN);
        }
    }

    /** The cube on its short chain under the hook, turning slowly. */
    private void cube(DrawList d, float hx, float hz) {
        float cy = hookY - CHAIN - CUBE;
        for (int k = 0; k < 3; k++) {
            float y = hookY - 0.08f - k * (CHAIN - 0.05f) / 3f;
            d.box(hx, y - 0.06f, hz, 0.02f, 0.07f, k % 2 == 0 ? 0.03f : 0.012f, null, STEEL, DrawList.PLAIN);
        }
        float a = t * 1.7f;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] r = {c, 0f, s, 0f, 1f, 0f, -s, 0f, c};
        d.box(hx, cy, hz, CUBE, CUBE, CUBE, r, 0xF2B020, DrawList.GOLD);
        // its edges darker, as the cubes in the rack are drawn
        float e = CUBE + 0.006f, th = 0.028f;
        for (int i = 0; i < 4; i++) {
            float u = (i & 1) == 0 ? -1f : 1f, v = i < 2 ? -1f : 1f;
            edge(d, r, hx, cy, hz, u * e, 0f, v * e, th, CUBE, th);
            edge(d, r, hx, cy, hz, u * e, v * e, 0f, th, th, CUBE);
            edge(d, r, hx, cy, hz, 0f, u * e, v * e, CUBE, th, th);
        }
    }

    private static void edge(DrawList d, float[] r, float x, float y, float z, float lx, float ly, float lz, float hx, float hy, float hz) {
        d.box(x + r[0] * lx + r[1] * ly + r[2] * lz, y + r[3] * lx + r[4] * ly + r[5] * lz, z + r[6] * lx + r[7] * ly + r[8] * lz,
                hx, hy, hz, r, 0x9A6418, DrawList.GOLD);
    }

    /** A beam between two points, square in section. */
    static void beam(DrawList d, float x0, float y0, float z0, float x1, float y1, float z1, float half, int rgb, int mat) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        dx /= len;
        dy /= len;
        dz /= len;
        float ax, ay, az;
        if (Math.abs(dy) < 0.9f) {
            ax = -dz;
            ay = 0f;
            az = dx;
        } else {
            ax = 0f;
            ay = dz;
            az = -dy;
        }
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al;
        ay /= al;
        az /= al;
        float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
        float[] r = {ax, bx, dx, ay, by, dy, az, bz, dz};
        d.box((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f, half, half, len * 0.5f, r, rgb, mat);
    }

    // ---- the flat layer ----

    /** The glint on the cube, and its flight up into the rack, the crest coming down to meet it. */
    void flat(Px f) {
        if (!gift || t < GDOWN0) return;
        float hx = tx + ox, hz = tz + oz, cy = hookY - CHAIN - CUBE;
        if (t < GOFF) {
            if (game.project(hx, cy, hz, scr) && ((int) (t * 3.2f) & 1) == 1 && t > GDOWN1 - 0.2f) {
                float k = (t * 3.2f) % 1f;
                star(f, Math.round(scr[0] + 4f), Math.round(scr[1] - 5f), Math.round(2f + 5f * (float) Math.sin(k * Math.PI)));
            }
            if (game.project(hx, cy, hz, scr) && game.project(hx, cy + 2f * CUBE, hz, scr2)) {
                flyX = scr[0];
                flyY = scr[1];
                flyS = Math.abs(scr[1] - scr2[1]) * 1.25f;
            }
        }
        // the crest comes down from above the frame
        if (t >= GOFF - 0.25f) {
            float k = ease(span(t, GOFF - 0.25f, GOFF + 0.2f));
            int shown = t >= GIN ? cubesBefore + 1 : cubesBefore;
            crest(f, Math.round(-56f * (1f - k)), shown);
        }
        if (t >= GOFF && t < GIN && flyX >= 0f) {
            float u = span(t, GOFF, GIN);
            float e = u * u * (3f - 2f * u);
            int slot = cubesBefore + 1 <= 7 ? cubesBefore : 0;
            float rx = Crest.CX + Crest.R + 7 + slot * 11 + 4.5f, ry = Crest.CY + 6 + 4.5f;
            // up in an arc: first rising, then falling into its place
            float mx = (flyX + rx) * 0.5f, my = Math.min(flyY, ry) - 70f;
            float x = (1 - e) * (1 - e) * flyX + 2 * (1 - e) * e * mx + e * e * rx;
            float y = (1 - e) * (1 - e) * flyY + 2 * (1 - e) * e * my + e * e * ry;
            float s = flyS + (9f - flyS) * e;
            Crest.cubeAt(f, x - s * 0.5f, y - s * 0.5f, s);
        }
        if (t >= GIN && t < GIN + 0.35f) {
            int slot = cubesBefore + 1 <= 7 ? cubesBefore : 0;
            int rx = Crest.CX + Crest.R + 7 + slot * 11 + 4, ry = Crest.CY + 6 + 4;
            star(f, rx, ry, Math.round(3f + 9f * (float) Math.sin(span(t, GIN, GIN + 0.35f) * Math.PI)));
        }
    }

    /** The crest as the next level will show it, shifted down by so many pixels (up, when negative). */
    private void crest(Px f, int dy, int cubes) {
        if (top == null || top.w != f.w) top = new Px(f.w, 64, new int[f.w * 64]);
        java.util.Arrays.fill(top.a, 0);
        game.crestFor(top, next, cubes);
        for (int y = 0; y < top.h; y++) {
            int ty = y + dy;
            if (ty < 0 || ty >= f.h) continue;
            for (int x = 0; x < f.w; x++) {
                int c = top.a[y * top.w + x];
                if ((c >>> 24) != 0) f.a[ty * f.w + x] = c;
            }
        }
    }

    /** A four-pointed glint. */
    private static void star(Px f, int x, int y, int r) {
        for (int i = -r; i <= r; i++) {
            int c = Math.abs(i) <= 1 ? 0xFFFFFFFF : 0xFFFFF0B0;
            f.set(x + i, y, c);
            f.set(x, y + i, c);
        }
        if (r > 3) {
            f.set(x - 1, y - 1, 0xFFFFF0B0);
            f.set(x + 1, y - 1, 0xFFFFF0B0);
            f.set(x - 1, y + 1, 0xFFFFF0B0);
            f.set(x + 1, y + 1, 0xFFFFF0B0);
        }
    }
}
