package com.shumtugle.freedie;

import java.util.Random;

/**
 * Cars on the street: sedans parked along both curbs, cones round the plinth, and now and
 * then a car coming down the road toward the plinth, stopping short of it, looking, turning
 * round and driving off. Each car is a few dozen boxes, drawn afresh every frame.
 *
 * The parked cars take what falls on them: each blow is one step worse, crazed glass and a
 * dent in the hood, then the roof pressed lower, a door dented, a window gone and a headlamp
 * out, then a wreck; and every blow sets its alarm off, the indicators blinking. On the
 * right a stretch of the curb is kept clear: the way into a yard is to be there.
 *
 * In the fight a parked car can be torn from its place and thrown: while it is in the air
 * the giant's side draws it; where it comes down it lies on as a wreck.
 */
final class Traffic {
    static final int[] PAINT = {0xE9B63A, 0xB5352C, 0xE9E1CB, 0x2D3A57, 0x6E9DB0, 0x9DBB8E, 0x3A3B40, 0xF4F2EC};
    static final float[] PARK_X = {-3.05f, -3.05f, -3.05f, -3.05f, -3.05f, -3.05f, 3.05f, 3.05f, 3.05f, 3.05f};
    static final float[] PARK_Z = {15.0f, 20.6f, 29.0f, 34.6f, 47.5f, 61.0f, 17.5f, 23.2f, 41.5f, 55.0f};
    /** The stretch of the right curb kept clear for the way into a yard, from here to there along the street. */
    static final float YARD_Z0 = 27f, YARD_Z1 = 37f;
    static final float[] CONE_X = {-2.75f, -2.45f, 2.6f};
    static final float[] CONE_Z = {10.0f, 10.9f, 9.9f};

    private final Random rnd = new Random(17);
    private final int[] parkColor = new int[PARK_X.length];
    /** How badly each parked car is damaged, 0 to 3, and how long its alarm has still to blink. */
    final int[] damage = new int[PARK_X.length];
    private final float[] alarm = new float[PARK_X.length];
    /** Where each parked car is now, which way it faces, and whether it is up in the air. */
    final float[] atX = PARK_X.clone(), atZ = PARK_Z.clone(), atYaw = new float[PARK_X.length];
    final boolean[] away = new boolean[PARK_X.length];
    /** What each parked car's alarm sounds like: the two tones, a chirp, the horn, a wail. */
    private final int[] alarmSound = new int[PARK_X.length];
    private static final int[] ALARMS = {Synth.ALARM, Synth.CHIRP, Synth.CHIRP, Synth.HORN, Synth.WAIL};
    /** No cars come down the road: the street is held for the fight. */
    boolean calm;

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
        for (int i = 0; i < PARK_X.length; i++) {
            parkColor[i] = PAINT[rnd.nextInt(PAINT.length)];
            atYaw[i] = PARK_X[i] < 0f ? 0f : (float) Math.PI;
            alarmSound[i] = ALARMS[rnd.nextInt(ALARMS.length)];
        }
    }

    /** The sound a parked car's alarm makes, its own. */
    int alarm(int i) {
        return alarmSound[i];
    }

    int paint(int i) {
        return parkColor[i];
    }

    /** A parked car torn from its place: gone from the curb until it comes down. */
    void lift(int i) {
        away[i] = true;
    }

    /** A thrown car down on the street: a wreck where it fell, facing as it came to rest. */
    void putDown(int i, float x, float z, float yaw) {
        away[i] = false;
        atX[i] = x;
        atZ[i] = z;
        atYaw[i] = yaw;
        damage[i] = 3;
        alarm[i] = 2.4f;
    }

    /** Every parked car back where it was parked, whole: a new street after the fight. */
    void repark() {
        for (int i = 0; i < PARK_X.length; i++) {
            away[i] = false;
            atX[i] = PARK_X[i];
            atZ[i] = PARK_Z[i];
            atYaw[i] = PARK_X[i] < 0f ? 0f : (float) Math.PI;
            damage[i] = 0;
            alarm[i] = 0f;
        }
    }

    /** A blow to a parked car: one step worse, and its alarm goes off. */
    void hit(int i) {
        if (damage[i] < 3) damage[i]++;
        alarm[i] = 1.8f;
    }

    void update(float dt) {
        for (int i = 0; i < alarm.length; i++) alarm[i] = Math.max(0f, alarm[i] - dt);
        if (calm) {
            // the cars on the move are gone round the corner, and no more come
            for (int c = 0; c < CARS; c++) cState[c] = OFF;
            nextCar = 4f;
        }
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
            if (!away[i])
                car(d, atX[i], atZ[i], atYaw[i], parkColor[i], damage[i], alarm[i] > 0f && ((int) (alarm[i] * 6f) & 1) == 0);
        for (int c = 0; c < CARS; c++) if (cState[c] != OFF) car(d, cx[c], cz[c], cYaw[c], cColor[c]);
        for (int i = 0; i < CONE_X.length; i++) cone(d, CONE_X[i], CONE_Z[i]);
    }

    private final float[] t = new float[9], frame = new float[9];
    private float px, py, pz;

    /** A small sedan, its nose along yaw (0 faces away from the eye). */
    void car(DrawList d, float x, float z, float yaw, int paint) {
        car(d, x, z, yaw, paint, 0, false);
    }

    /** A sedan; a damaged one has crazed glass, dents and a roof pressed lower, step by step. */
    void car(DrawList d, float x, float z, float yaw, int paint, int dmg, boolean blink) {
        upright(x, 0f, z, yaw);
        drawCar(d, paint, dmg, blink);
    }

    /**
     * A car turned any way at all, tumbling through the air: its middle (the body's, 0.65 m
     * over the ground when it stands) and its turn, row by row.
     */
    void car(DrawList d, float x, float y, float z, float[] rot, int paint, int dmg) {
        System.arraycopy(rot, 0, frame, 0, 9);
        px = x - rot[1] * 0.65f;
        py = y - rot[4] * 0.65f;
        pz = z - rot[7] * 0.65f;
        drawCar(d, paint, dmg, false);
    }

    /** The frame of something standing on the ground, turned about the upright. */
    private void upright(float x, float y, float z, float yaw) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        px = x;
        py = y;
        pz = z;
        frame[0] = cy; frame[1] = 0f; frame[2] = sy;
        frame[3] = 0f; frame[4] = 1f; frame[5] = 0f;
        frame[6] = -sy; frame[7] = 0f; frame[8] = cy;
    }

    private void drawCar(DrawList d, int paint, int dmg, boolean blink) {
        float plain = DrawList.PLAIN, glass = DrawList.GLASS;
        int clear = 0x33414E, crazed = 0xA3B0B9, gone = 0x15181C;
        int dent = Street.scale(paint, 0.62f);
        // body, with a darker sill
        part(d, 0f, 0.6f, 0f, 0.9f, 0.25f, 2.2f, 0f, paint, plain);
        part(d, 0f, 0.36f, 0f, 0.86f, 0.05f, 1.75f, 0f, 0x2A2829, plain);
        // cabin: glass all round, the screens sloping, a roof, the pillars between the doors;
        // pressed lower the worse it is hit
        float top = dmg >= 3 ? 1.06f : dmg >= 2 ? 1.18f : 1.3f, k = (top - 0.85f) / 0.45f, mid = (0.85f + top) * 0.5f;
        part(d, 0f, mid, -0.275f, 0.79f, 0.225f * k, 0.675f, 0f, clear, glass);
        // a side window smashed: dark where the glass was
        if (dmg >= 2) part(d, 0.795f, mid, -0.275f, 0.006f, 0.2f * k, 0.6f, 0f, gone, glass);
        part(d, 0f, mid, 0.7f, 0.79f, 0.375f * k, 0.01f, -0.927f, dmg >= 1 ? crazed : clear, glass);
        part(d, 0f, mid, -1.1f, 0.79f, 0.27f * k, 0.01f, 0.588f, dmg >= 3 ? gone : dmg >= 1 ? crazed : clear, glass);
        part(d, 0f, top + 0.01f, -0.275f, 0.77f, 0.025f, 0.7f, 0f, dmg >= 2 ? dent : paint, plain);
        // dents in the hood, the boot and the doors, so a blow shows from either end
        if (dmg >= 1) part(d, 0.22f, 0.86f, 1.45f, 0.32f, 0.012f, 0.38f, 0f, dent, plain);
        if (dmg >= 1) part(d, -0.25f, 0.86f, -1.7f, 0.34f, 0.012f, 0.3f, 0f, dent, plain);
        if (dmg >= 2) part(d, 0.3f, 0.62f, -2.205f, 0.3f, 0.12f, 0.012f, 0f, dent, plain);
        if (dmg >= 2) part(d, 0.905f, 0.62f, -0.5f, 0.012f, 0.13f, 0.5f, 0f, dent, plain);
        if (dmg >= 3) part(d, -0.905f, 0.6f, 0.4f, 0.012f, 0.15f, 0.6f, 0f, dent, plain);
        for (int side = -1; side <= 1; side += 2) {
            part(d, side * 0.795f, mid, -0.25f, 0.01f, 0.22f * k, 0.07f, 0f, paint, plain);
            // wheels and hubcaps
            for (int end = -1; end <= 1; end += 2) {
                part(d, side * 0.85f, 0.32f, end * 1.38f, 0.12f, 0.32f, 0.32f, 0f, 0x1C1B1D, plain);
                part(d, side * 0.975f, 0.32f, end * 1.38f, 0.01f, 0.15f, 0.15f, 0f, 0xB8B4AC, plain);
            }
            // lamps: the alarm blinks the indicators; a wrecked car has lost a headlamp
            int head = blink ? 0xFFB13A : dmg >= 2 && side > 0 ? 0x2A2829 : 0xFFF0C2;
            part(d, side * 0.6f, 0.68f, 2.205f, 0.17f, 0.07f, 0.01f, 0f, head, blink ? DrawList.GLOW : plain);
            part(d, side * 0.64f, 0.7f, -2.205f, 0.17f, 0.07f, 0.01f, 0f, blink ? 0xFFB13A : 0xD43A2E, blink ? DrawList.GLOW : plain);
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
        upright(x, 0f, z, 0f);
        float plain = DrawList.PLAIN;
        part(d, 0f, 0.025f, 0f, 0.2f, 0.025f, 0.2f, 0f, 0x2E2B2A, plain);
        part(d, 0f, 0.16f, 0f, 0.13f, 0.11f, 0.13f, 0f, 0xE8682E, plain);
        part(d, 0f, 0.34f, 0f, 0.095f, 0.07f, 0.095f, 0f, 0xF2EEE6, plain);
        part(d, 0f, 0.54f, 0f, 0.06f, 0.13f, 0.06f, 0f, 0xE8682E, plain);
    }

    /**
     * A box of the thing being drawn, given in its own frame: tilted about its own x first
     * (for sloping glass), then turned with the whole thing.
     */
    private void part(DrawList d, float lx, float ly, float lz, float hx, float hy, float hz, float tilt,
                      int color, float material) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float[] f = frame;
        // the frame times the tilt, whose columns are (1,0,0), (0,ct,st), (0,-st,ct)
        for (int r = 0; r < 3; r++) {
            t[r * 3] = f[r * 3];
            t[r * 3 + 1] = f[r * 3 + 1] * ct + f[r * 3 + 2] * st;
            t[r * 3 + 2] = -f[r * 3 + 1] * st + f[r * 3 + 2] * ct;
        }
        d.box(px + f[0] * lx + f[1] * ly + f[2] * lz, py + f[3] * lx + f[4] * ly + f[5] * lz,
                pz + f[6] * lx + f[7] * ly + f[8] * lz, hx, hy, hz, t, color, material);
    }
}
