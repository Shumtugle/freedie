package com.shumtugle.freedie;

/**
 * The game, apart from the phone: what is where, what moves, what the frame should hold.
 * It never draws: each frame it fills a DrawList, and a renderer draws that. The shell
 * calls, always from one thread: touch() for each queued touch, update(dt), then draw().
 * Coordinates of touches are frame pixels, W across.
 *
 * For now the street under a low evening sun: houses, palms, the sea beyond the promenade,
 * cars parked and cars coming; on the plinth a stack of blocks with a golden cube turning on
 * top. A finger dragged across turns the eye; the red button on the lamp post lights up when
 * pressed.
 */
final class World {
    static final int W = 274;                // frame pixels across; the height follows the screen
    static final String VERSION = "0.2";     // shown on screen; keep in step with versionName
    static final int DOWN = 0, MOVE = 1, UP = 2;

    // the eye, where it stands in the game it comes from: left of the plinth, eyes at a person's height
    static final float CAM_X = -2.3f, CAM_Y = 1.8f, CAM_Z = 0f;
    static final float PLINTH_Z = 11.5f, PLINTH_HX = 2.13f, PLINTH_HZ = 1.17f, PLINTH_TOP = 1.0f;

    int h = 640;
    float time;
    float frameMs;                           // how long the last frame took, set by the shell
    private float look, lookVel;              // the eye turned aside by a finger, radians
    private float downX, downY, lastX, travel;
    private boolean down;

    private final DrawList still = new DrawList();   // the street, built once
    private boolean built;
    private final Traffic traffic = new Traffic(PLINTH_Z);
    private float pressedAt = -9f;            // when the red button was last pressed
    private final float[] button = {-99f, -99f};      // where it is in the frame, as last drawn

    void resize(int screenW, int screenH) {
        h = Math.max(1, Math.round(W * (float) screenH / Math.max(1, screenW)));
    }

    void touch(int type, float x, float y) {
        if (type == DOWN) {
            down = true;
            downX = lastX = x;
            downY = y;
            travel = 0f;
            lookVel = 0f;
        } else if (type == MOVE && down) {
            travel += Math.abs(x - lastX);
            look = Math.max(-0.7f, Math.min(0.7f, look - (x - lastX) * 0.006f));
            lastX = x;
        } else if (type == UP) {
            // a tap, not a drag, on the button
            if (down && travel < 6f && Math.hypot(downX - button[0], downY - button[1]) < 11f) pressedAt = time;
            down = false;
        }
    }

    void update(float dt) {
        time += dt;
        traffic.update(dt);
        if (!down) {
            // let go, the eye drifts back to rest
            lookVel += -look * 6f * dt;
            lookVel *= (float) Math.exp(-4f * dt);
            look += lookVel * dt;
        }
    }

    private final float[] turn = new float[9];

    void draw(DrawList d) {
        if (!built) {
            Street.build(still);
            plinth(still);
            built = true;
        }
        d.begin(W, h);
        d.time = time;
        float yaw = (float) Math.atan2(-CAM_X, PLINTH_Z) + look;
        d.camera(CAM_X, CAM_Y, CAM_Z, yaw, 0.0f, W * 1.9f, 0.1f, 500f);
        // a low sun from the left and a little behind; its shadows worked out over the near street
        d.sun(-0.80f, 0.30f, -0.10f, -1f, 4f, 42f, 100f, 36f, 140f);
        DrawList.rgb(d.skyTop, 0x3C73C0);
        DrawList.rgb(d.skyHorizon, 0xF1DCC4);
        DrawList.rgb(d.skyGlow, 0xF6C08A);
        d.sunColor[0] = 1.6f; d.sunColor[1] = 1.45f; d.sunColor[2] = 1.22f;
        d.ambientSky[0] = 0.35f; d.ambientSky[1] = 0.39f; d.ambientSky[2] = 0.49f;
        d.ambientGround[0] = 0.26f; d.ambientGround[1] = 0.22f; d.ambientGround[2] = 0.19f;
        DrawList.rgb(d.fog, 0xE9D6BE);
        d.fogStart = 8f;
        d.fogScale = 220f;

        d.append(still);
        traffic.draw(d);
        // the golden cube, turning slowly
        float top = PLINTH_TOP;
        float a = time * 0.8f, c = (float) Math.cos(a), s = (float) Math.sin(a);
        turn[0] = c; turn[1] = 0; turn[2] = s;
        turn[3] = 0; turn[4] = 1; turn[5] = 0;
        turn[6] = -s; turn[7] = 0; turn[8] = c;
        d.box(0f, top + 1.98f, PLINTH_Z, 0.22f, 0.22f, 0.22f, turn, 0xF2C230, DrawList.GOLD);
        // the red button on the call box: a slow glow, bright for a moment when pressed
        float since = time - pressedAt;
        float glow = since < 0.35f ? 1f : 0.45f + 0.25f * (float) Math.sin(time * 3.1f);
        int red = Street.mix(0x6A0C08, 0xFF2A18, glow);
        float in = since < 0.15f ? 0.015f : 0f;
        d.box(Street.CALL_X + in, Street.CALL_Y + 0.06f, Street.CALL_Z, 0.025f, 0.075f, 0.075f, null, red, DrawList.GLOW);
        if (!d.project(Street.CALL_X, Street.CALL_Y + 0.06f, Street.CALL_Z, button)) button[0] = button[1] = -99f;

        // the flat layer: the version in the corner, and the cost of a frame while the engine is young
        java.util.Arrays.fill(d.flat, 0);
        Px f = flat(d);
        PixelFont.draw(f, VERSION, W - PixelFont.width(VERSION, 1) - 3, h - 10, 1, 0x88FFFFFF);
        if (frameMs > 0f) {
            String ms = String.valueOf(Math.round(frameMs));
            PixelFont.draw(f, ms, 4, 4, 1, 0xCCFFFFFF);
        }
    }

    /** The plinth in the middle of the road, and a little stack on it. */
    private static void plinth(DrawList d) {
        d.box(0f, PLINTH_TOP * 0.5f, PLINTH_Z, PLINTH_HX, PLINTH_TOP * 0.5f, PLINTH_HZ, null, 0xE6D8BC, DrawList.STONE);
        float top = PLINTH_TOP;
        d.box(-1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(0f, top + 0.98f, PLINTH_Z, 1.6f, 0.08f, 0.42f, null, 0x4F8F86, DrawList.WOOD);
        d.box(-0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xBDB3A2, DrawList.STONE);
        d.box(0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xD9A83A, DrawList.GRAIN);
    }

    private Px flatPx;

    private Px flat(DrawList d) {
        if (flatPx == null || flatPx.a != d.flat) flatPx = new Px(d.w, d.h, d.flat);
        return flatPx;
    }
}
