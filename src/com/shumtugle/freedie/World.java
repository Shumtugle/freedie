package com.shumtugle.freedie;

/**
 * The game, apart from the phone: what is where, what moves, what the frame should hold.
 * It never draws: each frame it fills a DrawList, and a renderer draws that. The shell
 * calls, always from one thread: touch() for each queued touch, update(dt), then draw().
 * Coordinates of touches are frame pixels, W across.
 *
 * For now a street with a plinth and a stack of blocks under a low sun, a golden cube
 * turning on top; a finger dragged across turns the eye a little, to see the shadows move
 * over the faces.
 */
final class World {
    static final int W = 274;                // frame pixels across; the height follows the screen
    static final String VERSION = "0.1";     // shown on screen; keep in step with versionName
    static final int DOWN = 0, MOVE = 1, UP = 2;

    // the eye, where it stands in the game it comes from: left of the plinth, eyes at a person's height
    static final float CAM_X = -2.3f, CAM_Y = 1.8f, CAM_Z = 0f;
    static final float PLINTH_Z = 11.5f, PLINTH_HX = 2.13f, PLINTH_HZ = 1.17f, PLINTH_TOP = 1.0f;

    int h = 640;
    float time;
    float frameMs;                           // how long the last frame took, set by the shell
    private float look, lookVel;              // the eye turned aside by a finger, radians
    private float downX, lastX;
    private boolean down;

    void resize(int screenW, int screenH) {
        h = Math.max(1, Math.round(W * (float) screenH / Math.max(1, screenW)));
    }

    void touch(int type, float x, float y) {
        if (type == DOWN) {
            down = true;
            downX = lastX = x;
            lookVel = 0f;
        } else if (type == MOVE && down) {
            look = Math.max(-0.7f, Math.min(0.7f, look - (x - lastX) * 0.006f));
            lastX = x;
        } else if (type == UP) {
            down = false;
        }
    }

    void update(float dt) {
        time += dt;
        if (!down) {
            // let go, the eye drifts back to rest
            lookVel += -look * 6f * dt;
            lookVel *= (float) Math.exp(-4f * dt);
            look += lookVel * dt;
        }
    }

    private final float[] turn = new float[9];

    void draw(DrawList d) {
        d.begin(W, h);
        d.time = time;
        float yaw = (float) Math.atan2(-CAM_X, PLINTH_Z) + look;
        d.camera(CAM_X, CAM_Y, CAM_Z, yaw, 0.0f, W * 1.9f, 0.1f, 400f);
        d.sun(-0.66f, 0.6f, -0.22f, 0f, 0f, 16f, 44f, 80f);
        DrawList.rgb(d.skyTop, 0x1E4DB3);
        DrawList.rgb(d.skyHorizon, 0xC7D7EE);
        DrawList.rgb(d.skyGlow, 0xFAAE9E);
        d.sunColor[0] = 1.6f; d.sunColor[1] = 1.48f; d.sunColor[2] = 1.28f;
        d.ambientSky[0] = 0.36f; d.ambientSky[1] = 0.42f; d.ambientSky[2] = 0.56f;
        d.ambientGround[0] = 0.26f; d.ambientGround[1] = 0.22f; d.ambientGround[2] = 0.19f;
        DrawList.rgb(d.fog, 0xC4D3EA);
        d.fogNear = 60f;
        d.fogFar = 230f;

        // the street: asphalt, two raised pavements, kerbs
        d.box(0f, -0.5f, 80f, 40f, 0.5f, 110f, null, 0x55575D, DrawList.ASPHALT);
        for (int s = -1; s <= 1; s += 2) {
            d.box(s * 9.2f, 0.08f, 80f, 3.1f, 0.08f, 110f, null, 0xC9C0B0, DrawList.PAVING);
            d.box(s * 6.1f, 0.1f, 80f, 0.12f, 0.1f, 110f, null, 0xD8D2C6, DrawList.STONE);
        }
        // houses along the right, lit by the low sun; a row on the left, in their shade
        int[] walls = {0xF1D9A8, 0xF2C4B0, 0xF4E3B4, 0xE9CFA0};
        for (int i = 0; i < 9; i++) {
            float z = 9f + i * 14f, hh = 8f + (i % 3) * 1.5f;
            d.box(15.3f, hh, z, 3f, hh, 6.8f, null, walls[i % 4], DrawList.PAINT);
            d.box(-16.3f, hh - 1f, z + 7f, 3f, hh - 1f, 6.8f, null, walls[(i + 2) % 4], DrawList.PAINT);
        }
        d.box(0f, 11f, 150f, 24f, 11f, 2f, null, 0xEFD8B0, DrawList.PAINT);

        // the plinth, and a little stack on it
        d.box(0f, PLINTH_TOP * 0.5f, PLINTH_Z, PLINTH_HX, PLINTH_TOP * 0.5f, PLINTH_HZ, null, 0xE6D8BC, DrawList.STONE);
        float top = PLINTH_TOP;
        d.box(-1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(0f, top + 0.98f, PLINTH_Z, 1.6f, 0.08f, 0.42f, null, 0x4F8F86, DrawList.WOOD);
        d.box(-0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xBDB3A2, DrawList.STONE);
        d.box(0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xD9A83A, DrawList.GRAIN);
        // the golden cube, turning slowly
        float a = time * 0.8f, c = (float) Math.cos(a), s = (float) Math.sin(a);
        turn[0] = c; turn[1] = 0; turn[2] = s;
        turn[3] = 0; turn[4] = 1; turn[5] = 0;
        turn[6] = -s; turn[7] = 0; turn[8] = c;
        d.box(0f, top + 1.98f, PLINTH_Z, 0.22f, 0.22f, 0.22f, turn, 0xF2C230, DrawList.GOLD);

        // the flat layer: the version in the corner, and the cost of a frame while the engine is young
        java.util.Arrays.fill(d.flat, 0);
        Px f = flat(d);
        PixelFont.draw(f, VERSION, W - PixelFont.width(VERSION, 1) - 3, h - 10, 1, 0x88FFFFFF);
        if (frameMs > 0f) {
            String ms = String.valueOf(Math.round(frameMs));
            PixelFont.draw(f, ms, 4, 4, 1, 0xCCFFFFFF);
        }
    }

    private Px flatPx;

    private Px flat(DrawList d) {
        if (flatPx == null || flatPx.a != d.flat) flatPx = new Px(d.w, d.h, d.flat);
        return flatPx;
    }
}
