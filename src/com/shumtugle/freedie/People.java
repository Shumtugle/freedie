package com.shumtugle.freedie;

import java.util.Random;

/**
 * People going about: each walks from one point to another and back, at a pace of their own,
 * in clothes of their own. They are blocks: legs, a shirt with arms, a head with hair, the
 * legs and arms swinging. Nobody comes nearer to the eye than a set distance: close up a
 * person of blocks reads as a toy.
 */
final class People {
    private static final int[] SHIRTS = {0xF2EEE2, 0x5A86B8, 0xD9654E, 0xE8C860, 0x6EA87A, 0xE59AB0, 0x3A3A40, 0xC8A27A, 0x8A5A9A};
    private static final int[] LEGS = {0x2E3446, 0x4A4038, 0xD8CCB0, 0x5A6E8A, 0x6E4A3A};
    private static final int[] SKINS = {0xE8BC98, 0xC8946E, 0x8C5E40, 0xF0CDB0};
    private static final int[] HAIR = {0x2A211C, 0x5A3E28, 0xC9A060, 0x8A8680, 0x1C1A18};

    private final int most;
    private int n;
    private final float[] ax, az, bx, bz, y, v, ph, size;
    private final int[] shirt, legs, skin, hair;
    private final Random rnd;
    private final float nearest;

    People(int most, long seed, float nearest) {
        this.most = most;
        this.nearest = nearest;
        rnd = new Random(seed);
        ax = new float[most]; az = new float[most]; bx = new float[most]; bz = new float[most];
        y = new float[most]; v = new float[most]; ph = new float[most]; size = new float[most];
        shirt = new int[most]; legs = new int[most]; skin = new int[most]; hair = new int[most];
    }

    Random random() {
        return rnd;
    }

    /** One more, walking between (x0, z0) and (x1, z1) on ground so high. */
    void walk(float x0, float z0, float x1, float z1, float ground) {
        if (n >= most) return;
        int i = n++;
        ax[i] = x0;
        az[i] = z0;
        bx[i] = x1;
        bz[i] = z1;
        y[i] = ground;
        v[i] = 1.1f + rnd.nextFloat() * 0.5f;
        ph[i] = rnd.nextFloat() * 60f;
        size[i] = 0.92f + rnd.nextFloat() * 0.14f;
        shirt[i] = SHIRTS[rnd.nextInt(SHIRTS.length)];
        legs[i] = LEGS[rnd.nextInt(LEGS.length)];
        skin[i] = SKINS[rnd.nextInt(SKINS.length)];
        hair[i] = HAIR[rnd.nextInt(HAIR.length)];
    }

    /** Everyone where they are at this moment, as seen from the eye at (ex, ey, ez). */
    void draw(DrawList d, float t, float ex, float ey, float ez) {
        for (int i = 0; i < n; i++) {
            float dx = bx[i] - ax[i], dz = bz[i] - az[i], len = (float) Math.sqrt(dx * dx + dz * dz) + 0.01f;
            float w = (ph[i] + t * v[i]) % (2f * len);
            float k = w < len ? w / len : 2f - w / len;
            float yaw = (float) Math.atan2(dx, dz) + (w < len ? 0f : (float) Math.PI);
            float px = ax[i] + dx * k, pz = az[i] + dz * k;
            float qx = px - ex, qy = y[i] + 1f - ey, qz = pz - ez;
            if (qx * qx + qy * qy + qz * qz < nearest * nearest) continue;
            person(d, px, y[i], pz, yaw, t * v[i] * 3.2f + i, i);
        }
    }

    private final float[] turn = new float[9];
    private float px, py, pz, c, sn, s;

    /** A person of blocks: legs, a shirt with arms, a head with hair, walking. */
    private void person(DrawList d, float x, float y, float z, float yaw, float phase, int i) {
        px = x;
        py = y;
        pz = z;
        c = (float) Math.cos(yaw);
        sn = (float) Math.sin(yaw);
        s = size[i];
        float swing = (float) Math.sin(phase) * 0.16f;
        part(d, -0.1f, 0.44f, swing, 0.075f, 0.44f, 0.08f, legs[i]);
        part(d, 0.1f, 0.44f, -swing, 0.075f, 0.44f, 0.08f, legs[i]);
        part(d, 0f, 1.17f, 0f, 0.2f, 0.3f, 0.12f, shirt[i]);
        part(d, -0.26f, 1.15f, -swing * 0.8f, 0.055f, 0.27f, 0.06f, shirt[i]);
        part(d, 0.26f, 1.15f, swing * 0.8f, 0.055f, 0.27f, 0.06f, shirt[i]);
        part(d, 0f, 1.6f, 0f, 0.1f, 0.12f, 0.11f, skin[i]);
        part(d, 0f, 1.73f, 0.01f, 0.11f, 0.03f, 0.12f, hair[i]);
    }

    private void part(DrawList d, float ox, float oy, float oz, float hx, float hy, float hz, int rgb) {
        turn[0] = c; turn[1] = 0f; turn[2] = sn;
        turn[3] = 0f; turn[4] = 1f; turn[5] = 0f;
        turn[6] = -sn; turn[7] = 0f; turn[8] = c;
        ox *= s;
        oy *= s;
        oz *= s;
        d.box(px + c * ox + sn * oz, py + oy, pz - sn * ox + c * oz, hx * s, hy * s, hz * s, turn, rgb, DrawList.PLAIN);
    }
}
