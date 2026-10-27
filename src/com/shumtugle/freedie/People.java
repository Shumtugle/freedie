package com.shumtugle.freedie;

import java.util.Random;

/**
 * People going about: each walks from one point to another and back, at a pace of their own,
 * in clothes of their own. They are drawn round, of plain shaded balls drawn out along the
 * limbs: legs in shorts, trousers or a skirt, a body, arms swinging against the legs, a neck,
 * a head with hair, now and then long hair, a hat or a bag; a child here and there. Being
 * round, they may come close, and they cast their long evening shadows on the street like
 * everything else. Given the street's solids, nobody walks through a palm, a lamp post or a
 * café table, or anything lying about: they step round it.
 */
final class People {
    private static final int[] SKINS = {0xE9BC98, 0xD69F7A, 0xB98058, 0x7E5236, 0xF0CDAE};
    private static final int[] TOPS = {0xF4F1EA, 0xD9735A, 0xE8C25A, 0x5E9E92, 0x6F92C6, 0xD58FAE, 0x2E2E34, 0xB8CF8A};
    private static final int[] LEGS = {0x3F5679, 0xCDBB98, 0x54704A, 0x2F2F36, 0xE3DDCF, 0xB44A3E};
    private static final int[] HAIR = {0x2E2118, 0x5A3C24, 0xC9A25E, 0x161311, 0x8A4A26};
    private static final int SHOE = 0x3A302A, STRAW = 0xE3C784, BAG = 0x6A4A32;
    // what they wear on their legs
    private static final int SHORTS = 0, TROUSERS = 1, SKIRT = 2;

    private final int most;
    private int n;
    private final float[] ax, az, bx, bz, y, v, ph, size;
    private final int[] top, legs, skin, hair, kind;
    private final boolean[] hat, longHair, bag;
    private final Random rnd;
    private final float nearest;
    /** What cannot be walked through; none, and people go straight. */
    Solid solid;
    private final float[] at = new float[2];

    People(int most, long seed, float nearest) {
        this.most = most;
        this.nearest = nearest;
        rnd = new Random(seed);
        ax = new float[most]; az = new float[most]; bx = new float[most]; bz = new float[most];
        y = new float[most]; v = new float[most]; ph = new float[most]; size = new float[most];
        top = new int[most]; legs = new int[most]; skin = new int[most]; hair = new int[most]; kind = new int[most];
        hat = new boolean[most]; longHair = new boolean[most]; bag = new boolean[most];
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
        v[i] = 1.0f + rnd.nextFloat() * 0.5f;
        ph[i] = rnd.nextFloat() * 60f;
        size[i] = rnd.nextInt(7) == 0 ? 0.7f : 0.92f + rnd.nextFloat() * 0.14f;
        top[i] = TOPS[rnd.nextInt(TOPS.length)];
        legs[i] = LEGS[rnd.nextInt(LEGS.length)];
        skin[i] = SKINS[rnd.nextInt(SKINS.length)];
        hair[i] = HAIR[rnd.nextInt(HAIR.length)];
        kind[i] = rnd.nextInt(3);
        longHair[i] = kind[i] == SKIRT || rnd.nextInt(5) == 0;
        hat[i] = rnd.nextInt(4) == 0;
        bag[i] = rnd.nextInt(3) == 0;
    }

    /** Everyone where they are at this moment, as seen from the eye at (ex, ey, ez). */
    void draw(DrawList d, float t, float ex, float ey, float ez) {
        for (int i = 0; i < n; i++) {
            float dx = bx[i] - ax[i], dz = bz[i] - az[i], len = (float) Math.sqrt(dx * dx + dz * dz) + 0.01f;
            float w = (ph[i] + t * v[i]) % (2f * len);
            float k = w < len ? w / len : 2f - w / len;
            float yaw = (float) Math.atan2(dx, dz) + (w < len ? 0f : (float) Math.PI);
            float px = ax[i] + dx * k, pz = az[i] + dz * k;
            if (solid != null) {
                at[0] = px;
                at[1] = pz;
                solid.push(at, 0.3f);
                px = at[0];
                pz = at[1];
            }
            float qx = px - ex, qy = y[i] + 1f - ey, qz = pz - ez;
            if (qx * qx + qy * qy + qz * qz < nearest * nearest) continue;
            person(d, px, y[i], pz, yaw, t * v[i] * 3.2f + i, i);
        }
    }

    // the person being drawn: where, turned how (forward along local +z), how big
    private float px, py, pz, c, sn, s;
    private final float[] rot = new float[9], a = new float[3], b = new float[3];

    /** A person of round parts, walking: the stride swings the legs, the arms against them. */
    private void person(DrawList d, float x, float y, float z, float yaw, float phase, int i) {
        px = x;
        py = y;
        pz = z;
        c = (float) Math.cos(yaw);
        sn = (float) Math.sin(yaw);
        s = size[i];
        float sw = (float) Math.sin(phase);
        float step = sw * 0.28f, bob = Math.abs(sw) * 0.025f;
        float hip = 0.9f + bob, sh = 1.4f + bob;
        int sk = skin[i], tp = top[i], lg = legs[i];
        for (int side = -1; side <= 1; side += 2) {
            float fz = step * side, lx = side * 0.085f;
            if (kind[i] == SHORTS) {
                limb(d, lx, hip, 0f, lx, hip * 0.55f, fz * 0.45f, 0.08f, lg);
                limb(d, lx, hip * 0.55f, fz * 0.45f, lx, 0.08f, fz, 0.055f, sk);
            } else if (kind[i] == TROUSERS) {
                limb(d, lx, hip, 0f, lx, hip * 0.5f, fz * 0.45f, 0.082f, lg);
                limb(d, lx, hip * 0.5f, fz * 0.45f, lx, 0.08f, fz, 0.07f, lg);
            } else {
                limb(d, lx, hip * 0.6f, fz * 0.4f, lx, 0.08f, fz, 0.05f, sk);
            }
            ball(d, lx, 0.055f, fz + 0.05f, 0.065f, 0.05f, 0.1f, SHOE);
        }
        // the skirt: wide at the hem
        if (kind[i] == SKIRT) ball(d, 0f, hip * 0.8f, 0f, 0.24f, hip * 0.24f, 0.2f, lg);
        // the body, flatter front to back
        ball(d, 0f, (hip + sh) * 0.5f, 0f, 0.19f, (sh - hip) * 0.5f + 0.05f, 0.14f, tp);
        if (bag[i]) ball(d, side(i) * 0.2f, hip + 0.05f, 0.02f, 0.06f, 0.12f, 0.13f, BAG);
        // the arms swing against the legs
        for (int side = -1; side <= 1; side += 2) {
            float arm = side * 0.22f, hz = -step * side * 0.8f;
            limb(d, arm, sh - 0.06f, 0f, arm * 1.1f, hip + 0.14f, hz * 0.5f, 0.06f, tp);
            limb(d, arm * 1.1f, hip + 0.14f, hz * 0.5f, arm * 1.12f, hip - 0.1f, hz, 0.048f, sk);
        }
        // the neck, the head, the hair over its back
        limb(d, 0f, sh - 0.04f, 0f, 0f, sh + 0.08f, 0f, 0.055f, sk);
        float hy = sh + 0.21f;
        ball(d, 0f, hy, 0.01f, 0.11f, 0.125f, 0.115f, sk);
        ball(d, 0f, hy + 0.045f, -0.035f, 0.118f, 0.11f, 0.118f, hair[i]);
        if (longHair[i]) limb(d, 0f, hy, -0.07f, 0f, hy - 0.26f, -0.09f, 0.1f, hair[i]);
        if (hat[i]) {
            ball(d, 0f, hy + 0.1f, 0f, 0.22f, 0.018f, 0.22f, STRAW);
            ball(d, 0f, hy + 0.15f, 0f, 0.11f, 0.07f, 0.11f, STRAW);
        }
    }

    /** Which side a bag hangs on, the same for one person always. */
    private static float side(int i) {
        return (i & 1) == 0 ? 1f : -1f;
    }

    /** A round part in the person's own frame, upright: its middle and half sizes. */
    private void ball(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, int rgb) {
        rot[0] = c; rot[1] = 0f; rot[2] = sn;
        rot[3] = 0f; rot[4] = 1f; rot[5] = 0f;
        rot[6] = -sn; rot[7] = 0f; rot[8] = c;
        lx *= s;
        ly *= s;
        lz *= s;
        d.ellipsoid(px + c * lx + sn * lz, py + ly, pz - sn * lx + c * lz, hx * s, hy * s, hz * s, rot, rgb, DrawList.MATTE);
    }

    /**
     * A limb from one point to another of the person's own frame, so thick: a ball drawn out
     * along it, a little past both ends so the joints close.
     */
    private void limb(DrawList d, float x0, float y0, float z0, float x1, float y1, float z1, float r, int rgb) {
        world(x0, y0, z0, a);
        world(x1, y1, z1, b);
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float len = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (len < 1e-4f) return;
        ux /= len;
        uy /= len;
        uz /= len;
        // across the stride: the person's own right, made square to the limb
        float rx = c, ry = 0f, rz = -sn;
        float dot = rx * ux + ry * uy + rz * uz;
        rx -= dot * ux;
        ry -= dot * uy;
        rz -= dot * uz;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl;
        ry /= rl;
        rz /= rl;
        // and the third: right times along
        float fx = ry * uz - rz * uy, fy = rz * ux - rx * uz, fz = rx * uy - ry * ux;
        rot[0] = rx; rot[1] = ux; rot[2] = fx;
        rot[3] = ry; rot[4] = uy; rot[5] = fy;
        rot[6] = rz; rot[7] = uz; rot[8] = fz;
        float rr = r * s;
        d.ellipsoid((a[0] + b[0]) * 0.5f, (a[1] + b[1]) * 0.5f, (a[2] + b[2]) * 0.5f, rr, len * 0.5f + rr * 0.6f, rr, rot, rgb,
                DrawList.MATTE);
    }

    private void world(float lx, float ly, float lz, float[] out) {
        lx *= s;
        ly *= s;
        lz *= s;
        out[0] = px + c * lx + sn * lz;
        out[1] = py + ly;
        out[2] = pz - sn * lx + c * lz;
    }
}
