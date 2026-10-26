package com.shumtugle.freedie;

/**
 * The winter ocean beyond the portal, where the levels after the 101st are played. A low
 * heavy sky, neither night nor day; fog that swallows the horizon; a grey sea running in long
 * swells, whitecaps breaking on them. The player stands on an ice floe; the building stands
 * on a block of ice on an iceberg, its spikes of old ice standing round it. Sailing boats ride
 * at anchor to either side, rolling; a buoy bobs nearer, and on its cage the call box with
 * the red button, as on the lamp post in town. Snow drives across slantwise.
 *
 * What does not move is laid down once; the iceberg, the boats and the buoy every frame.
 */
final class Ocean {
    static final float WATER_Y = -0.22f;
    // the player's floe, round the feet
    static final float FLOE_X = World.CAM_X + 0.4f, FLOE_Z = 1.6f;
    // the buoy, and the call box on its cage, facing the ice
    static final float BUOY_X = 3.4f, BUOY_Z = 14.5f;
    static final float CALL_DX = -0.34f, CALL_Y = 1.62f;

    static final int ICE = 0xC9E2EE, ICE_DARK = 0xA9CADB, SNOW = 0xF0F4F7;

    private Ocean() {
    }

    /** The grey winter light: a sun lost in the cloud, the sky lighting everything, thick fog. */
    static void light(DrawList d) {
        d.sun(-0.45f, 0.78f, 0.25f, -1f, 4f, 42f, 100f, 36f, 140f);
        DrawList.rgb(d.skyTop, 0x56636F);
        DrawList.rgb(d.skyHorizon, 0xA9B4BD);
        DrawList.rgb(d.skyGlow, 0xB9C3CA);
        d.sunColor[0] = 0.62f; d.sunColor[1] = 0.66f; d.sunColor[2] = 0.72f;
        d.ambientSky[0] = 0.62f; d.ambientSky[1] = 0.67f; d.ambientSky[2] = 0.74f;
        d.ambientGround[0] = 0.5f; d.ambientGround[1] = 0.53f; d.ambientGround[2] = 0.57f;
        DrawList.rgb(d.fog, 0xB4BEC6);
        d.fogStart = 4f;
        d.fogScale = 85f;
        d.overcast = 1f;
    }

    // ---- laid down once ----

    static void build(DrawList d) {
        // the sea, out to the fog
        d.box(0f, WATER_Y - 0.25f, 200f, 420f, 0.25f, 260f, null, 0x34495A, DrawList.WINTER_SEA);
        floe(d, FLOE_X, FLOE_Z, 4.6f, 5.0f, 0.7f, 5);
        // drift ice far off: low white slabs fading into the fog
        java.util.Random r = new java.util.Random(31);
        for (int i = 0; i < 12; i++) {
            float x = -60f + r.nextFloat() * 120f, z = 48f + r.nextFloat() * 150f;
            if (Math.abs(x - 4f) < 12f && z < 70f) continue;
            float s = 2f + r.nextFloat() * 6f;
            floe(d, x, z, s, s * (0.5f + r.nextFloat() * 0.6f), r.nextFloat() * 3f, 2 + r.nextInt(3));
        }
    }

    /** A ragged slab of ice: a few overlapping plates turned this way and that, a little snow on top. */
    static void floe(DrawList d, float x, float z, float rx, float rz, float turn, int plates) {
        for (int k = 0; k < plates; k++) {
            float a = turn + k * 1.9f, s = 1f - 0.13f * k;
            float ox = (float) Math.cos(a * 1.7f) * rx * 0.18f * k, oz = (float) Math.sin(a * 1.3f) * rz * 0.16f * k;
            float c = (float) Math.cos(a * 0.4f), sn = (float) Math.sin(a * 0.4f);
            float[] rot = {c, 0f, sn, 0f, 1f, 0f, -sn, 0f, c};
            d.box(x + ox, WATER_Y + 0.06f + k * 0.012f, z + oz, rx * 0.72f * s, 0.3f, rz * 0.72f * s, rot,
                    k == 0 ? ICE_DARK : SNOW, k == 0 ? DrawList.ICE : DrawList.GRAIN);
        }
    }

    // ---- drawn every frame ----

    /** The iceberg the plinth stands on, its spikes, the boats at anchor and the buoy. */
    static void draw(DrawList d, float cz, float time) {
        iceberg(d, cz);
        // at the edges of the view, one to either side
        boat(d, -5.2f, 27f, 0.5f, time, 0xB8322A, 0);
        boat(d, 10.2f, 27f, -0.7f, time + 2.3f, 0x2F4A66, 1);
        buoy(d, time);
    }

    private static final float[] SPIKES = {
            // across, along from the plinth, height, half width at the foot: to either side of
            // the plinth and behind its corners, never in front of what stands on it
            -4.4f, 1.6f, 3.2f, 0.6f,
            -3.3f, 2.9f, 4.4f, 0.65f,
            3.5f, 2.7f, 3.8f, 0.6f,
            4.7f, 1.1f, 2.4f, 0.5f,
            5.3f, -1.3f, 1.3f, 0.4f,
            -5.0f, -0.9f, 1.6f, 0.45f};

    private static void iceberg(DrawList d, float cz) {
        floe(d, 0.2f, cz + 0.4f, 6.2f, 4.4f, 0.2f, 3);
        for (int i = 0; i < SPIKES.length; i += 4) {
            float x = SPIKES[i], z = cz + SPIKES[i + 1], h = SPIKES[i + 2], w = SPIKES[i + 3];
            spike(d, x, z, h, w, i);
        }
    }

    /**
     * A spike of old ice: blocks one on another, each narrower than the last, coming to a
     * point; turned corner-on to the eye, so it reads as a pointed shard, a little lean to it.
     */
    private static void spike(DrawList d, float x, float z, float h, float w, int seed) {
        int n = 7;
        float y = WATER_Y + 0.15f, lean = ((seed * 37) % 7 - 3) * 0.03f;
        float a = 0.785f + ((seed * 13) % 5 - 2) * 0.08f;
        float c = (float) Math.cos(a), sn = (float) Math.sin(a);
        float[] rot = {c, 0f, sn, 0f, 1f, 0f, -sn, 0f, c};
        for (int k = 0; k < n; k++) {
            float s = 1f - k / (float) n * 0.88f, hh = h / n * 0.5f;
            d.box(x + lean * (y + hh), y + hh, z, w * s, hh, w * s, rot, (k & 1) == 0 ? ICE : ICE_DARK, DrawList.ICE);
            y += hh * 2f;
        }
    }

    /** A sailing boat at anchor, its sail furled on the boom, rolling and pitching on the swell. */
    private static void boat(DrawList d, float x, float z, float yaw, float t, int hull, int kind) {
        float roll = 0.07f * (float) Math.sin(t * 0.9f), pitch = 0.035f * (float) Math.sin(t * 0.7f + 1f);
        float heave = 0.12f * (float) Math.sin(t * 0.8f + 0.5f);
        float[] r = turn(yaw, pitch, roll);
        float y = WATER_Y + heave;
        // the hull: deeper in the middle, the bow narrowing in steps
        part(d, r, x, y, z, 0f, 0.15f, 0f, 1.0f, 0.5f, 2.2f, hull, DrawList.PAINT);
        part(d, r, x, y, z, 0f, 0.25f, 2.55f, 0.7f, 0.4f, 0.4f, hull, DrawList.PAINT);
        part(d, r, x, y, z, 0f, 0.35f, 3.05f, 0.35f, 0.3f, 0.2f, hull, DrawList.PAINT);
        part(d, r, x, y, z, 0f, 0.68f, 0.2f, 1.02f, 0.04f, 2.24f, 0xEDEAE2, DrawList.PAINT);
        // the deck, the cabin
        part(d, r, x, y, z, 0f, 0.7f, 0.3f, 0.92f, 0.03f, 2.6f, 0xB89A70, DrawList.WOOD);
        part(d, r, x, y, z, 0f, 1.0f, -0.6f, 0.62f, 0.3f, 0.9f, 0xEDEAE2, DrawList.PAINT);
        part(d, r, x, y, z, 0.63f, 1.05f, -0.6f, 0.02f, 0.1f, 0.6f, 0x3A4A58, DrawList.GLASS);
        part(d, r, x, y, z, -0.63f, 1.05f, -0.6f, 0.02f, 0.1f, 0.6f, 0x3A4A58, DrawList.GLASS);
        // the mast, the boom, the sail furled on it, snow on the cabin roof
        float mh = kind == 0 ? 5.6f : 6.4f;
        part(d, r, x, y, z, 0f, 0.7f + mh * 0.5f, 0.6f, 0.06f, mh * 0.5f, 0.06f, 0x6A4E36, DrawList.WOOD);
        part(d, r, x, y, z, 0f, 1.9f, -0.5f, 0.05f, 0.05f, 1.15f, 0x6A4E36, DrawList.WOOD);
        part(d, r, x, y, z, 0f, 2.0f, -0.45f, 0.11f, 0.09f, 1.0f, 0xE6E2D6, DrawList.PLAIN);
        part(d, r, x, y, z, 0f, 1.33f, -0.6f, 0.58f, 0.04f, 0.85f, SNOW, DrawList.GRAIN);
        // the stays from the masthead to the bow and the stern
        float tx = x + r[1] * (0.7f + mh) + r[2] * 0.6f, ty = y + r[4] * (0.7f + mh) + r[5] * 0.6f, tz = z + r[7] * (0.7f + mh) + r[8] * 0.6f;
        Crane.beam(d, tx, ty, tz, x + r[1] * 0.6f + r[2] * 3.2f, y + r[4] * 0.6f + r[5] * 3.2f, z + r[7] * 0.6f + r[8] * 3.2f,
                0.012f, 0x3A3A3E, DrawList.PLAIN);
        Crane.beam(d, tx, ty, tz, x + r[1] * 0.7f - r[2] * 2.1f, y + r[4] * 0.7f - r[5] * 2.1f, z + r[7] * 0.7f - r[8] * 2.1f,
                0.012f, 0x3A3A3E, DrawList.PLAIN);
        // the anchor chain going down into the sea from the bow
        float bx = x + r[1] * 0.5f + r[2] * 3.1f, by = y + r[4] * 0.5f + r[5] * 3.1f, bz = z + r[7] * 0.5f + r[8] * 3.1f;
        Crane.beam(d, bx, by, bz, bx + r[2] * 1.4f, WATER_Y - 0.1f, bz + r[8] * 1.4f, 0.02f, 0x4A4642, DrawList.PLAIN);
    }

    private static void part(DrawList d, float[] r, float x, float y, float z, float lx, float ly, float lz,
                             float hx, float hy, float hz, int rgb, int mat) {
        d.box(x + r[0] * lx + r[1] * ly + r[2] * lz, y + r[3] * lx + r[4] * ly + r[5] * lz, z + r[6] * lx + r[7] * ly + r[8] * lz,
                hx, hy, hz, r, rgb, mat);
    }

    /** A turn: yaw about the upright, then pitch about across, then roll about along; row by row. */
    static float[] turn(float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // R = Ry(yaw) * Rx(pitch) * Rz(roll)
        float[] rz = {cr, -sr, 0f, sr, cr, 0f, 0f, 0f, 1f};
        float[] rx = {1f, 0f, 0f, 0f, cp, -sp, 0f, sp, cp};
        float[] ry = {cy, 0f, sy, 0f, 1f, 0f, -sy, 0f, cy};
        return mul(ry, mul(rx, rz));
    }

    private static float[] mul(float[] a, float[] b) {
        float[] m = new float[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) m[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        return m;
    }

    /** How high the buoy rides now. */
    static float buoyLift(float t) {
        return 0.12f * (float) Math.sin(t * 1.25f) + 0.05f * (float) Math.sin(t * 2.7f + 1f);
    }

    /** The buoy: a red float banded black, a cage on it with a lantern, and the call box on the cage. */
    private static void buoy(DrawList d, float t) {
        float y = WATER_Y + buoyLift(t);
        float lean = 0.06f * (float) Math.sin(t * 0.95f);
        float[] r = turn(0f, 0f, lean);
        Round.cylinder(d, BUOY_X, y, BUOY_Z, r, -0.35f, 0.45f, 0.55f, 0xC8302A, DrawList.PAINT);
        Round.cylinder(d, BUOY_X, y, BUOY_Z, r, 0.05f, 0.2f, 0.56f, 0x1E1E22, DrawList.PAINT);
        for (int k = 0; k < 4; k++) {
            float a = (float) (k * Math.PI / 2 + Math.PI / 4), lx = (float) Math.cos(a) * 0.3f, lz = (float) Math.sin(a) * 0.3f;
            part(d, r, BUOY_X, y, BUOY_Z, lx, 1.15f, lz, 0.03f, 0.7f, 0.03f, 0x2E2D2F, DrawList.PLAIN);
        }
        part(d, r, BUOY_X, y, BUOY_Z, 0f, 1.88f, 0f, 0.36f, 0.04f, 0.36f, 0x2E2D2F, DrawList.PLAIN);
        part(d, r, BUOY_X, y, BUOY_Z, 0f, 2.05f, 0f, 0.12f, 0.14f, 0.12f, 0xF3E2B4, DrawList.GLOW);
        part(d, r, BUOY_X, y, BUOY_Z, 0f, 2.24f, 0f, 0.17f, 0.04f, 0.17f, 0x2E2D2F, DrawList.PLAIN);
        // the call box, facing the ice, as on the lamp post in town
        part(d, r, BUOY_X, y, BUOY_Z, CALL_DX + 0.05f, CALL_Y - 0.06f, 0f, 0.07f, 0.26f, 0.17f, 0x8E959C, DrawList.PLAIN);
    }

    /** Where the red button is now, in the world. */
    static void button(float t, float[] out) {
        float y = WATER_Y + buoyLift(t), lean = 0.06f * (float) Math.sin(t * 0.95f);
        float c = (float) Math.cos(lean), s = (float) Math.sin(lean);
        out[0] = BUOY_X + c * CALL_DX - s * CALL_Y;
        out[1] = y + s * CALL_DX + c * CALL_Y;
        out[2] = BUOY_Z;
    }

    // ---- the snow, over everything ----

    /**
     * Snow driving across slantwise: flakes at many depths, the near ones bigger and faster,
     * each falling along the same slant and wrapping round the frame.
     */
    static void snow(Px f, float t) {
        int w = f.w, h = f.h;
        for (int i = 0; i < 170; i++) {
            float depth = 0.25f + 0.75f * frac(i * 0.618034f);
            float speed = 30f + 70f * depth, wind = 0.55f + 0.25f * frac(i * 0.381966f);
            float x0 = frac(i * 0.7548777f) * (w + 40), y0 = frac(i * 0.5698403f) * (h + 40);
            float wob = (float) Math.sin(t * (1.3f + depth) + i) * 2f * depth;
            int x = (int) (((x0 + t * speed * wind + wob) % (w + 40) + (w + 40)) % (w + 40)) - 20;
            int y = (int) ((y0 + t * speed) % (h + 40)) - 20;
            int a = (int) (110 + 140 * depth);
            int c = (a << 24) | 0xF4F7FA;
            f.set(x, y, c);
            if (depth > 0.75f) {
                f.set(x + 1, y, c);
                f.set(x, y + 1, c);
                f.set(x + 1, y + 1, c);
            }
        }
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
