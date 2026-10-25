package com.shumtugle.freedie;

import java.util.Random;

/**
 * Cars on the street: sedans parked along both curbs, cones round the plinth, and now and
 * then a car coming down the road toward the plinth, stopping short of it, looking, turning
 * round and driving off. Each car is a few dozen boxes, drawn afresh every frame.
 */
final class Traffic {
    static final int[] PAINT = {0xE9B63A, 0xB5352C, 0xE9E1CB, 0x2D3A57, 0x6E9DB0, 0x9DBB8E, 0x3A3B40, 0xF4F2EC};
    static final float[] PARK_X = {-3.05f, -3.05f, -3.05f, -3.05f, -3.05f, -3.05f, 3.05f, 3.05f, 3.05f, 3.05f, 3.05f};
    static final float[] PARK_Z = {15.0f, 20.6f, 29.0f, 34.6f, 47.5f, 61.0f, 17.5f, 23.2f, 31.0f, 41.5f, 55.0f};
    static final float[] CONE_X = {-2.75f, -2.45f, 2.6f};
    static final float[] CONE_Z = {10.0f, 10.9f, 9.9f};

    private final Random rnd = new Random(17);
    private final int[] parkColor = new int[PARK_X.length];

    // cars on the move
    private static final int CARS = 3;
    private static final int OFF = 0, COME = 1, LOOK = 2, TURN = 3, GO = 4;
    private static final float LANE = 1.3f;
    private final float[] cx = new float[CARS], cz = new float[CARS], cYaw = new float[CARS], cV = new float[CARS];
    private final float[] cStop = new float[CARS], cTime = new float[CARS], cLane = new float[CARS], cTheta = new float[CARS];
    private final int[] cState = new int[CARS], cColor = new int[CARS];
    private float nextCar = 3f;
    private final float stopAt;

    Traffic(float plinthZ) {
        stopAt = plinthZ;
        for (int i = 0; i < PARK_X.length; i++) parkColor[i] = PAINT[rnd.nextInt(PAINT.length)];
    }

    void update(float dt) {
        nextCar -= dt;
        if (nextCar <= 0f) {
            nextCar = 7f + rnd.nextFloat() * 8f;
            for (int c = 0; c < CARS; c++)
                if (cState[c] == OFF) {
                    spawn(c);
                    break;
                }
        }
        for (int c = 0; c < CARS; c++) {
            switch (cState[c]) {
                case COME: {
                    float d = cz[c] - cStop[c];
                    float want = Math.min(10f, (float) Math.sqrt(Math.max(0f, 2f * 4.5f * d)));
                    cV[c] = approach(cV[c], want, 6f, dt);
                    for (int o = 0; o < CARS; o++)
                        if (o != c && cState[o] != OFF && cz[o] < cz[c] && cz[c] - cz[o] < 12f)
                            cV[c] = Math.min(cV[c], Math.max(0f, cz[c] - cz[o] - 8f));
                    cz[c] -= cV[c] * dt;
                    if (d < 0.3f && cV[c] < 0.3f) {
                        cState[c] = LOOK;
                        cTime[c] = 0.8f + rnd.nextFloat() * 0.8f;
                        cV[c] = 0f;
                    }
                    break;
                }
                case LOOK:
                    cTime[c] -= dt;
                    if (cTime[c] <= 0f) {
                        boolean clear = true;
                        for (int o = 0; o < CARS; o++)
                            if (o != c && cState[o] != OFF && Math.abs(cz[o] - cz[c]) < 12f) clear = false;
                        if (clear) {
                            cState[c] = TURN;
                            cTheta[c] = 0f;
                        } else {
                            cTime[c] = 0.5f;
                        }
                    }
                    break;
                case TURN: {
                    // a half circle across the road, from its lane to the other
                    float r = Math.abs(cLane[c]);
                    cTheta[c] += 2.2f / r * dt;
                    float t = Math.min((float) Math.PI, cTheta[c]);
                    cx[c] = cLane[c] * (float) Math.cos(t);
                    cz[c] = cStop[c] - r * (float) Math.sin(t);
                    float dx = -cLane[c] * (float) Math.sin(t), dz = -r * (float) Math.cos(t);
                    cYaw[c] = (float) Math.atan2(dx, dz);
                    if (cTheta[c] >= Math.PI) {
                        cState[c] = GO;
                        cV[c] = 2.2f;
                        cYaw[c] = 0f;
                    }
                    break;
                }
                case GO:
                    cV[c] = approach(cV[c], 12f, 3f, dt);
                    cz[c] += cV[c] * dt;
                    if (cz[c] > 170f) cState[c] = OFF;
                    break;
                default:
                    break;
            }
        }
    }

    private void spawn(int c) {
        cLane[c] = rnd.nextBoolean() ? LANE : -LANE;
        cx[c] = cLane[c];
        cz[c] = 150f + rnd.nextFloat() * 20f;
        cYaw[c] = (float) Math.PI;
        cV[c] = 10f;
        cStop[c] = stopAt + 10f + rnd.nextFloat() * 8f;
        cColor[c] = PAINT[rnd.nextInt(PAINT.length)];
        cState[c] = COME;
    }

    private static float approach(float v, float target, float rate, float dt) {
        if (v < target) return Math.min(target, v + rate * dt);
        return Math.max(target, v - rate * 1.6f * dt);
    }

    // ---- drawing ----

    void draw(DrawList d) {
        for (int i = 0; i < PARK_X.length; i++)
            car(d, PARK_X[i], PARK_Z[i], PARK_X[i] < 0f ? 0f : (float) Math.PI, parkColor[i]);
        for (int c = 0; c < CARS; c++) if (cState[c] != OFF) car(d, cx[c], cz[c], cYaw[c], cColor[c]);
        for (int i = 0; i < CONE_X.length; i++) cone(d, CONE_X[i], CONE_Z[i]);
    }

    private final float[] t = new float[9];
    private float px, pz, cy, sy;

    /** A small sedan, its nose along yaw (0 faces away from the eye). */
    void car(DrawList d, float x, float z, float yaw, int paint) {
        px = x;
        pz = z;
        cy = (float) Math.cos(yaw);
        sy = (float) Math.sin(yaw);
        float plain = DrawList.PLAIN, glass = DrawList.GLASS;
        // body, with a darker sill
        part(d, 0f, 0.6f, 0f, 0.9f, 0.25f, 2.2f, 0f, paint, plain);
        part(d, 0f, 0.36f, 0f, 0.86f, 0.05f, 1.75f, 0f, 0x2A2829, plain);
        // cabin: glass all round, the screens sloping, a roof, the pillars between the doors
        part(d, 0f, 1.075f, -0.275f, 0.79f, 0.225f, 0.675f, 0f, 0x33414E, glass);
        part(d, 0f, 1.075f, 0.7f, 0.79f, 0.375f, 0.01f, -0.927f, 0x33414E, glass);
        part(d, 0f, 1.075f, -1.1f, 0.79f, 0.27f, 0.01f, 0.588f, 0x33414E, glass);
        part(d, 0f, 1.31f, -0.275f, 0.77f, 0.025f, 0.7f, 0f, paint, plain);
        for (int side = -1; side <= 1; side += 2) {
            part(d, side * 0.795f, 1.075f, -0.25f, 0.01f, 0.22f, 0.07f, 0f, paint, plain);
            // wheels and hubcaps
            for (int end = -1; end <= 1; end += 2) {
                part(d, side * 0.85f, 0.32f, end * 1.38f, 0.12f, 0.32f, 0.32f, 0f, 0x1C1B1D, plain);
                part(d, side * 0.975f, 0.32f, end * 1.38f, 0.01f, 0.15f, 0.15f, 0f, 0xB8B4AC, plain);
            }
            // lamps
            part(d, side * 0.6f, 0.68f, 2.205f, 0.17f, 0.07f, 0.01f, 0f, 0xFFF0C2, plain);
            part(d, side * 0.64f, 0.7f, -2.205f, 0.17f, 0.07f, 0.01f, 0f, 0xD43A2E, plain);
        }
        // bumpers, grille, plates
        part(d, 0f, 0.42f, 2.24f, 0.93f, 0.07f, 0.05f, 0f, 0xD2CEC6, plain);
        part(d, 0f, 0.42f, -2.24f, 0.93f, 0.07f, 0.05f, 0f, 0xD2CEC6, plain);
        part(d, 0f, 0.64f, 2.205f, 0.3f, 0.08f, 0.01f, 0f, 0x2A2A2C, plain);
        part(d, 0f, 0.43f, 2.3f, 0.24f, 0.06f, 0.01f, 0f, 0xF2F0E6, plain);
        part(d, 0f, 0.43f, -2.3f, 0.24f, 0.06f, 0.01f, 0f, 0xF2F0E6, plain);
    }

    /** A road cone: a dark foot and three bands, orange, white, orange. */
    private void cone(DrawList d, float x, float z) {
        px = x;
        pz = z;
        cy = 1f;
        sy = 0f;
        float plain = DrawList.PLAIN;
        part(d, 0f, 0.025f, 0f, 0.2f, 0.025f, 0.2f, 0f, 0x2E2B2A, plain);
        part(d, 0f, 0.16f, 0f, 0.13f, 0.11f, 0.13f, 0f, 0xE8682E, plain);
        part(d, 0f, 0.34f, 0f, 0.095f, 0.07f, 0.095f, 0f, 0xF2EEE6, plain);
        part(d, 0f, 0.54f, 0f, 0.06f, 0.13f, 0.06f, 0f, 0xE8682E, plain);
    }

    /**
     * A box of the thing being drawn, given in its own frame: tilted about its own x first
     * (for sloping glass), then turned round the vertical with the whole thing.
     */
    private void part(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, float tilt,
                      int color, float material) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        // the columns, tilted: (1,0,0), (0,ct,st), (0,-st,ct); then turned by the yaw
        t[0] = cy; t[1] = st * sy; t[2] = ct * sy;
        t[3] = 0f; t[4] = ct; t[5] = -st;
        t[6] = -sy; t[7] = st * cy; t[8] = ct * cy;
        d.box(px + lx * cy + lz * sy, ly, pz - lx * sy + lz * cy, hx, hy, hz, t, color, material);
    }
}
