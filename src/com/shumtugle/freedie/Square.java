package com.shumtugle.freedie;

import java.util.Random;

/**
 * The square of the opening: another corner of the same seaside town. A paved square laid
 * in rings round its middle, where Freedie stands; behind it a road across, another along
 * its right side, with cars going both ways; old pastel houses round it with the street's
 * fronts, shutters, balconies and awnings; to the left a promenade with palms, a parapet and
 * the sea. People walk the far pavements and the promenade, never close to the eye.
 */
final class Square {
    static final float Z = World.PLINTH_Z;
    // the square, the roads round it, where the houses begin
    static final float SQ_X = 14f, SQ_Z0 = Z - 14f, SQ_Z1 = Z + 11f;
    static final float BACK_Z0 = SQ_Z1, BACK_Z1 = Z + 17f, BACK_HOUSES = Z + 20f;
    static final float RIGHT_X0 = SQ_X, RIGHT_X1 = 20f, RIGHT_HOUSES = 23f;
    static final float FRONT_HOUSES = Z - 18f;
    static final float PROM_X = -14f, PARAPET_X = -24f;
    static final float NEAREST = 18f;          // people come no nearer to the eye than this

    private static final float[][] PALMS = {{-17f, Z - 12f, 8.6f, 0.6f}, {-21f, Z - 6f, 9.4f, -0.5f}, {-17f, Z + 1f, 8.2f, 0.4f},
            {-21f, Z + 8f, 9.8f, 0.7f}, {-17f, Z + 15f, 8.8f, -0.4f}, {-21f, Z + 22f, 9f, 0.5f}, {11.5f, Z - 11f, 7.6f, 0.5f},
            {11.5f, Z + 8.5f, 8.2f, -0.6f}, {-17f, Z + 29f, 8.4f, 0.3f}, {-21f, Z + 36f, 9.2f, -0.6f}};

    // cars going round: lane (0, 1 across behind the square, 2, 3 along its right), where on it, speed, color
    private static final int CARS = 9;
    private final int[] carLane = new int[CARS], carCol = new int[CARS];
    private final float[] carAt = new float[CARS], carV = new float[CARS];
    private static final int[] CAR_COLORS = {0xE8C04A, 0xB8443A, 0x4F8C8A, 0xEDE4CE, 0x2F4A6E, 0x8A9A5A, 0xD87A3A, 0x6E3A5A};

    // people: a walk from a to b and back, a pace, colors
    private static final int PEOPLE = 16;
    private final float[] pAx = new float[PEOPLE], pAz = new float[PEOPLE], pBx = new float[PEOPLE], pBz = new float[PEOPLE];
    private final float[] pV = new float[PEOPLE], pPh = new float[PEOPLE];
    private final int[] pShirt = new int[PEOPLE], pLegs = new int[PEOPLE], pSkin = new int[PEOPLE], pHair = new int[PEOPLE];
    private static final int[] SHIRTS = {0xF2EEE2, 0x5A86B8, 0xD9654E, 0xE8C860, 0x6EA87A, 0xE59AB0, 0x3A3A40};
    private static final int[] LEGS = {0x2E3446, 0x4A4038, 0xD8CCB0, 0x5A6E8A};
    private static final int[] SKINS = {0xE8BC98, 0xC8946E, 0x8C5E40, 0xF0CDB0};
    private static final int[] HAIR = {0x2A211C, 0x5A3E28, 0xC9A060, 0x8A8680};

    private final Traffic cars = new Traffic(Z);

    Square() {
        Random r = new Random(0x70A11L);
        for (int i = 0; i < CARS; i++) {
            carLane[i] = i % 4;
            carAt[i] = r.nextFloat() * 120f;
            carV[i] = 7f + r.nextFloat() * 5f;
            carCol[i] = CAR_COLORS[r.nextInt(CAR_COLORS.length)];
        }
        for (int i = 0; i < PEOPLE; i++) {
            // the far pavements: past the road behind the square, past the road on its right,
            // and along the promenade's far end
            if (i < 7) {
                pAx[i] = -12f + r.nextFloat() * 24f;
                pAz[i] = BACK_Z1 + 0.8f + r.nextFloat() * 1.6f;
                pBx[i] = pAx[i] + (r.nextBoolean() ? 1f : -1f) * (6f + r.nextFloat() * 10f);
                pBz[i] = pAz[i] + (r.nextFloat() - 0.5f) * 0.8f;
            } else if (i < 11) {
                pAx[i] = RIGHT_X1 + 0.8f + r.nextFloat() * 1.4f;
                pAz[i] = Z - 20f + r.nextFloat() * 20f;
                pBx[i] = pAx[i] + (r.nextFloat() - 0.5f) * 0.6f;
                pBz[i] = pAz[i] + 8f + r.nextFloat() * 14f;
            } else {
                pAx[i] = -19f + (r.nextFloat() - 0.5f) * 2f;
                pAz[i] = Z + 5f + r.nextFloat() * 8f;
                pBx[i] = pAx[i];
                pBz[i] = pAz[i] + 12f + r.nextFloat() * 8f;
            }
            pV[i] = 1.1f + r.nextFloat() * 0.5f;
            pPh[i] = r.nextFloat() * 60f;
            pShirt[i] = SHIRTS[r.nextInt(SHIRTS.length)];
            pLegs[i] = LEGS[r.nextInt(LEGS.length)];
            pSkin[i] = SKINS[r.nextInt(SKINS.length)];
            pHair[i] = HAIR[r.nextInt(HAIR.length)];
        }
    }

    /** Everything that stands still, into the list. */
    static void build(DrawList d) {
        // the ground: pavement everywhere, the square on it, the roads with their curbs
        d.box(30f, -0.25f, Z + 10f, 60f, 0.25f, 80f, null, 0xCDBFA6, DrawList.PAVING);
        // the square: its rings are laid round the middle of its box, where she stands
        d.box(0f, -0.24f, Z, SQ_X, 0.25f, SQ_Z1 - Z, null, 0xD9C3A0, DrawList.SQUARE);
        lane(d, (PROM_X + 90f) * 0.5f, (BACK_Z0 + BACK_Z1) * 0.5f, (90f - PROM_X) * 0.5f, (BACK_Z1 - BACK_Z0) * 0.5f);
        lane(d, (RIGHT_X0 + RIGHT_X1) * 0.5f, (Z - 70f + BACK_Z0) * 0.5f, (RIGHT_X1 - RIGHT_X0) * 0.5f, (BACK_Z0 - (Z - 70f)) * 0.5f);
        curb(d, (PROM_X + 90f) * 0.5f, BACK_Z0, (90f - PROM_X) * 0.5f, 0.125f);
        curb(d, (PROM_X + 90f) * 0.5f, BACK_Z1, (90f - PROM_X) * 0.5f, 0.125f);
        curb(d, RIGHT_X0, (Z - 70f + BACK_Z0) * 0.5f, 0.125f, (BACK_Z0 - (Z - 70f)) * 0.5f);
        curb(d, RIGHT_X1, (Z - 70f + BACK_Z0) * 0.5f, 0.125f, (BACK_Z0 - (Z - 70f)) * 0.5f);
        // the promenade, the parapet, the sea
        float px0 = PARAPET_X + 0.3f, px1 = PROM_X;
        d.box((px0 + px1) * 0.5f, (Street.WALK_TOP - 1.5f) * 0.5f, Z, (px1 - px0) * 0.5f, (Street.WALK_TOP + 1.5f) * 0.5f, 90f,
                null, 0xDCCDB2, DrawList.PROMENADE);
        d.box(PARAPET_X, -0.3f, Z, 0.3f, 1.2f, 90f, null, 0xE2D4BA, DrawList.STONE);
        d.box(PARAPET_X, 0.95f, Z, 0.38f, 0.06f, 90f, null, 0xEDE2CC, DrawList.PLAIN);
        d.box(-224.6f, -0.85f, Z, 200f, 0.25f, 300f, null, 0x2D6990, DrawList.SEA);

        Random r = new Random(0x70A11L);
        int seed = 200;
        // behind the road across the square, a row facing it
        for (float px = PROM_X - 4f; px < 70f; ) {
            int bays = 3 + r.nextInt(3);
            float len = bays * 2.9f;
            int storeys = 4 + r.nextInt(2);
            house(d, px + len * 0.5f, BACK_HOUSES + 7f, len * 0.5f, 7f, storeys, Street.WALLS[r.nextInt(Street.WALLS.length)], seed);
            Street.fronts(d, true, BACK_HOUSES, -1, px, bays, storeys, seed++);
            px += len;
        }
        // along the right of the square, beyond its road
        for (float pz = FRONT_HOUSES - 30f; pz < BACK_Z1; ) {
            int bays = 3 + r.nextInt(3);
            float len = bays * 2.9f;
            int storeys = 4 + r.nextInt(2);
            house(d, RIGHT_HOUSES + 7f, pz + len * 0.5f, 7f, len * 0.5f, storeys, Street.WALLS[r.nextInt(Street.WALLS.length)], seed);
            Street.fronts(d, false, RIGHT_HOUSES, -1, pz, bays, storeys, seed++);
            pz += len;
        }
        // before the square, the row the eye comes past: open at the promenade's end
        for (float px = -4f; px < RIGHT_X0; ) {
            int bays = Math.max(1, Math.min((int) ((RIGHT_X0 - px) / 2.9f), 3 + r.nextInt(3)));
            float len = bays * 2.9f;
            int storeys = 3 + r.nextInt(2);
            house(d, px + len * 0.5f, FRONT_HOUSES - 7f, len * 0.5f, 7f, storeys, Street.WALLS[r.nextInt(Street.WALLS.length)], seed);
            Street.fronts(d, true, FRONT_HOUSES, 1, px, bays, storeys, seed++);
            px += len;
        }
        // the town beyond, for the flight over it
        for (int row = 0; row < 3; row++) {
            float z0 = BACK_HOUSES + 21f + row * 21f;
            for (float px = PROM_X - 4f; px < 90f; ) {
                int bays = 3 + r.nextInt(3);
                float len = bays * 2.9f;
                house(d, px + len * 0.5f, z0 + 7f, len * 0.5f, 7f, 3 + r.nextInt(4), Street.WALLS[r.nextInt(Street.WALLS.length)], seed++);
                px += len + (r.nextInt(6) == 0 ? 7f : 0f);
            }
        }
        for (int row = 0; row < 3; row++) {
            float x0 = RIGHT_HOUSES + 21f + row * 21f;
            for (float pz = FRONT_HOUSES - 50f; pz < BACK_HOUSES; ) {
                int bays = 3 + r.nextInt(3);
                float len = bays * 2.9f;
                house(d, x0 + 7f, pz + len * 0.5f, 7f, len * 0.5f, 3 + r.nextInt(4), Street.WALLS[r.nextInt(Street.WALLS.length)], seed++);
                pz += len + (r.nextInt(6) == 0 ? 7f : 0f);
            }
        }
        for (int i = 0; i < PALMS.length; i++) {
            float[] p = PALMS[i];
            d.box(p[0], Street.WALK_TOP + 0.01f, p[1], 0.7f, 0.01f, 0.6f, null, 0x8C6E4C, DrawList.GRAIN);
            Street.palm(d, new Random(900L + i), p[0], p[0] < PROM_X ? Street.WALK_TOP : 0f, p[1], p[2], p[3], false);
        }
        // lamp posts round the square
        for (int i = 0; i < 4; i++) {
            float lx = i < 2 ? -SQ_X + 0.6f : SQ_X - 0.6f, lz = (i & 1) == 0 ? SQ_Z0 + 3f : SQ_Z1 - 1f;
            d.box(lx, 2.1f, lz, 0.06f, 2.1f, 0.06f, null, 0x2E2D2F, DrawList.PLAIN);
            d.box(lx, 4.18f, lz, 0.1f, 0.04f, 0.1f, null, 0x3A3836, DrawList.PLAIN);
            d.box(lx, 4.4f, lz, 0.13f, 0.18f, 0.13f, null, 0xF3E2B4, DrawList.PLAIN);
            d.box(lx, 4.62f, lz, 0.2f, 0.05f, 0.2f, null, 0x3A3836, DrawList.PLAIN);
        }
    }

    private static void house(DrawList d, float x, float z, float hx, float hz, int storeys, int tint, int seed) {
        float height = 3.3f * (storeys + 1) + 0.5f;
        d.box(x, height * 0.5f, z, hx, height * 0.5f, hz, null, tint, DrawList.material(DrawList.FACADE, seed));
        d.box(x, height - 0.2f, z, hx + 0.15f, 0.2f, hz + 0.15f, null, Street.scale(tint, 1.05f), DrawList.PLAIN);
        Street.roof(d, x, height, z, hx, hz, seed);
    }

    private static void lane(DrawList d, float x, float z, float hx, float hz) {
        d.box(x, -0.23f, z, hx, 0.25f, hz, null, 0x6E6862, DrawList.LANE);
    }

    private static void curb(DrawList d, float x, float z, float hx, float hz) {
        d.box(x, -0.05f, z, hx, 0.2f, hz, null, 0xC9BFAE, DrawList.STONE);
    }

    // ---- what moves ----

    /** Cars going round and people walking, at this moment, seen from where the eye is. */
    void life(DrawList d, float t, float ex, float ey, float ez) {
        for (int i = 0; i < CARS; i++) {
            int lane = carLane[i];
            float run = ((carAt[i] + carV[i] * t) % 130f + 130f) % 130f - 65f;
            float x, z, yaw;
            if (lane == 0) {
                x = run;
                z = BACK_Z0 + 1.6f;
                yaw = (float) Math.PI * 0.5f;
            } else if (lane == 1) {
                x = -run;
                z = BACK_Z1 - 1.6f;
                yaw = -(float) Math.PI * 0.5f;
            } else if (lane == 2) {
                x = RIGHT_X0 + 1.6f;
                z = Z - run;
                yaw = (float) Math.PI;
            } else {
                x = RIGHT_X1 - 1.6f;
                z = Z + run;
                yaw = 0f;
            }
            // the cars of the road across go round the promenade's end, out of sight
            if ((lane < 2 && x < PROM_X + 2f) || (lane >= 2 && z > BACK_Z1)) continue;
            cars.car(d, x, z, yaw, carCol[i]);
        }
        for (int i = 0; i < PEOPLE; i++) {
            float dx = pBx[i] - pAx[i], dz = pBz[i] - pAz[i], len = (float) Math.sqrt(dx * dx + dz * dz) + 0.01f;
            float w = (pPh[i] + t * pV[i]) % (2f * len);
            float k = w < len ? w / len : 2f - w / len;
            float yaw = (float) Math.atan2(dx, dz) + (w < len ? 0f : (float) Math.PI);
            float px = pAx[i] + dx * k, pz = pAz[i] + dz * k;
            float qx = px - ex, qy = 1f - ey, qz = pz - ez;
            if (qx * qx + qy * qy + qz * qz < NEAREST * NEAREST) continue;
            float y = pAx[i] < PROM_X ? Street.WALK_TOP : 0f;
            person(d, px, y, pz, yaw, t * pV[i] * 3.2f + i, i);
        }
    }

    private final float[] turn = new float[9];
    private float bx, by, bz, c, sn;

    /** A person of blocks: legs, a shirt with arms, a head with hair, walking. */
    private void person(DrawList d, float x, float y, float z, float yaw, float phase, int i) {
        bx = x;
        by = y;
        bz = z;
        c = (float) Math.cos(yaw);
        sn = (float) Math.sin(yaw);
        float swing = (float) Math.sin(phase) * 0.16f;
        part(d, -0.1f, 0.44f, swing, 0.075f, 0.44f, 0.08f, pLegs[i]);
        part(d, 0.1f, 0.44f, -swing, 0.075f, 0.44f, 0.08f, pLegs[i]);
        part(d, 0f, 1.17f, 0f, 0.2f, 0.3f, 0.12f, pShirt[i]);
        part(d, -0.26f, 1.15f, -swing * 0.8f, 0.055f, 0.27f, 0.06f, pShirt[i]);
        part(d, 0.26f, 1.15f, swing * 0.8f, 0.055f, 0.27f, 0.06f, pShirt[i]);
        part(d, 0f, 1.6f, 0f, 0.1f, 0.12f, 0.11f, pSkin[i]);
        part(d, 0f, 1.73f, 0.01f, 0.11f, 0.03f, 0.12f, pHair[i]);
    }

    private void part(DrawList d, float ox, float oy, float oz, float hx, float hy, float hz, int rgb) {
        turn[0] = c; turn[1] = 0f; turn[2] = sn;
        turn[3] = 0f; turn[4] = 1f; turn[5] = 0f;
        turn[6] = -sn; turn[7] = 0f; turn[8] = c;
        d.box(bx + c * ox + sn * oz, by + oy, bz - sn * ox + c * oz, hx, hy, hz, turn, rgb, DrawList.PLAIN);
    }
}
