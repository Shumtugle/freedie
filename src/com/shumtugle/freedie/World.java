package com.shumtugle.freedie;

/**
 * The game, apart from the phone: what is where, what moves, what the frame should hold.
 * It never draws: each frame it fills a DrawList, and a renderer draws that. The shell
 * calls, always from one thread: touch() for each queued touch, update(dt), then draw().
 * Coordinates of touches are frame pixels, W across.
 *
 * The first game opens with Freedie herself and her gift (see Intro); every game after with
 * the name on the square (see Opening). Then, for now, the street under a low evening sun:
 * houses, palms, the sea beyond the promenade, cars parked and cars coming; on the plinth a
 * stack of blocks with a golden cube turning on top. A finger dragged across turns the eye;
 * the red button on the lamp post rings the dispatcher, and three presses open the bridge.
 */
final class World {
    static final int W = 274;                // frame pixels across; the height follows the screen
    static final String VERSION = "0.6";     // shown on screen; keep in step with versionName
    static final int DOWN = 0, MOVE = 1, UP = 2, BACK = 3;

    /** What the world may ask of the phone. */
    interface Host {
        /** Plays one of the synthesizer's sounds, so loud, at this share of its own pitch. */
        void sound(int id, float gain, float rate);
        /** A short buzz of the phone, so many milliseconds. */
        void buzz(int ms);
        /** A small number kept between games, by name; 0 when never kept. */
        void saveInt(String key, int value);
        int loadInt(String key);
        /** A file shipped with the game, or null. */
        byte[] asset(String name);
    }

    // the eye, where it stands in the game it comes from: left of the plinth, eyes at a person's height
    static final float CAM_X = -2.3f, CAM_Y = 1.8f, CAM_Z = 0f;
    static final float PLINTH_Z = 11.5f, PLINTH_HX = 2.13f, PLINTH_HZ = 1.17f, PLINTH_TOP = 1.0f;
    static final float FLASH_OUT = 0.35f;

    private final Host host;
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

    // the game's progress: the level being played (from 0), the golden cubes in hand, where
    int level;
    int cubes;
    boolean atSea;

    // the dispatcher's bridge, while it is open; the presses that call it; the picture coming back
    Bridge bridge;
    private int calls;
    private float callAt = -9f;
    private float tvOn = -1f;

    private Prelude intro;
    private boolean swallow;                  // the touch that skipped the opening, until it lifts
    private float flashOut;                   // the flash fading off the game, seconds left

    World(Host host) {
        this.host = host;
        // the first meeting once, and the gift that comes with it; the usual opening after
        intro = host.loadInt("met") == 1 ? new Opening(host) : new Intro(host);
        cubes = host.loadInt("cubes");
    }

    void resize(int screenW, int screenH) {
        h = Math.max(1, Math.round(W * (float) screenH / Math.max(1, screenW)));
    }

    void touch(int type, float x, float y) {
        if (intro != null) {
            if (type == DOWN) {
                intro.skip();
                swallow = true;
            }
            if (type == UP) swallow = false;
            return;
        }
        if (swallow) {
            if (type == UP) swallow = false;
            return;
        }
        if (bridge != null) {
            if (type == BACK) {
                // the back key leaves the room as the lever does, without the show
                bridge = null;
                tvOn = 0f;
                host.sound(Synth.TV_OFF, 0.9f, 1f);
                return;
            }
            bridge.touch(type, x, y);
            return;
        }
        if (type == BACK) return;
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
            if (down && travel < 6f && Math.hypot(downX - button[0], downY - button[1]) < 11f) {
                pressedAt = time;
                host.sound(Synth.TOCK, 0.8f, 1f);
                call();
            }
            down = false;
        }
    }

    void update(float dt) {
        time += dt;
        traffic.update(dt);
        if (intro != null) {
            intro.update(dt);
            if (intro.done()) {
                if (intro instanceof Intro) {
                    // her heart is the first cube, and it is in the first slot already
                    host.saveInt("met", 1);
                    host.saveInt("slot0", 1);
                }
                intro = null;
                flashOut = FLASH_OUT;
            }
            return;
        }
        flashOut = Math.max(0f, flashOut - dt);
        if (bridge != null) bridge.update(dt);
        if (tvOn >= 0f && (tvOn += dt) > Bridge.TV_ON_T) tvOn = -1f;
        if (!down) {
            // let go, the eye drifts back to rest
            lookVel += -look * 6f * dt;
            lookVel *= (float) Math.exp(-4f * dt);
            look += lookVel * dt;
        }
    }

    /** Whether the back key means something here, rather than leaving the game. */
    boolean wantsBack() {
        return bridge != null;
    }

    /** Whether the opening is still playing. */
    boolean opening() {
        return intro != null;
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
        light(d);
        java.util.Arrays.fill(d.flat, 0);
        Px f = flat(d);
        if (intro != null) {
            intro.frame(d, W);
            intro.draw(d);
            intro.flat(f, d);
            return;
        }
        float yaw = (float) Math.atan2(-CAM_X, PLINTH_Z) + look;
        d.camera(CAM_X, CAM_Y, CAM_Z, yaw, 0.0f, W * 1.9f, 0.1f, 500f);
        d.flash = flashOut / FLASH_OUT;
        if (tvOn >= 0f) d.tube = tvOn / Bridge.TV_ON_T;
        if (bridge != null) {
            // the world fades as the room comes up, and is not drawn once it is up
            float up = Math.min(1f, bridge.t / Bridge.OPEN_T);
            bridge.draw(f, 1f);
            if (up >= 1f) {
                d.dark = 1f;
                return;
            }
            int[] a = d.flat;
            int k = (int) (up * 255f);
            for (int i = 0; i < a.length; i++) a[i] = (a[i] & 0xFFFFFF) | (k << 24);
        }
        d.append(still);
        traffic.draw(d);
        stack(d);
        // the red button on the call box: a slow glow, bright for a moment when pressed
        float since = time - pressedAt;
        float glow = since < 0.35f ? 1f : 0.45f + 0.25f * (float) Math.sin(time * 3.1f);
        int red = Street.mix(0x6A0C08, 0xFF2A18, glow);
        float in = since < 0.15f ? 0.015f : 0f;
        d.box(Street.CALL_X + in, Street.CALL_Y + 0.06f, Street.CALL_Z, 0.025f, 0.075f, 0.075f, null, red, DrawList.GLOW);
        if (!d.project(Street.CALL_X, Street.CALL_Y + 0.06f, Street.CALL_Z, button)) button[0] = button[1] = -99f;

        // the flat layer: the version in the corner, and the cost of a frame while the engine is young
        PixelFont.draw(f, VERSION, W - PixelFont.width(VERSION, 1) - 3, h - 10, 1, 0x88FFFFFF);
        if (frameMs > 0f) {
            String ms = String.valueOf(Math.round(frameMs));
            PixelFont.draw(f, ms, 4, 4, 1, 0xCCFFFFFF);
        }
    }

    /** The evening: a low sun from the left and a little behind, the sky, the haze. */
    private static void light(DrawList d) {
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
        d.dark = 0f;
        d.flash = 0f;
        d.goldGlow = 1f;
        d.tube = -1f;
    }

    // ---- the call box and the bridge ----
    // Each press of the red button rings the dispatcher; three in a row and the bridge opens.

    private void call() {
        if (time - callAt > 6f) calls = 0;
        calls++;
        callAt = time;
        host.sound(Synth.RING, 0.9f, 1f);
        host.buzz(20);
        if (calls >= 3) {
            calls = 0;
            openBridge();
        }
    }

    void openBridge() {
        down = false;
        bridge = new Bridge(this, host);
        host.sound(Synth.DOOR, 1f, 0.8f);
    }

    int best() {
        return Math.max(level + 1, host.loadInt("best"));
    }

    /** Whether a console monitor has a game behind it yet: the alley, the golem and the stones are still to come. */
    boolean offers(int which) {
        return which == Bridge.HOME;
    }

    /** The lever, answered, once the room has gone out: the game comes on like a set. */
    void bridgeAct(int which) {
        bridge = null;
        tvOn = 0f;
    }

    /** A screen with its cube, thrown at: into its episode, through the light. No levels yet: the street. */
    void bridgeEnter(int which) {
        bridge = null;
        flashOut = FLASH_OUT;
    }

    /** A golden cube from the row, gone into a slot for good. */
    void spendCube() {
        if (cubes <= 0) return;
        cubes--;
        host.saveInt("cubes", cubes);
    }

    // the stones on the water, for its monitor on the console
    static final String[] SKIP = {
            ".XX..........",
            "XXXX...X...X.",
            ".XX...X.X.X.X",
            ".....X...X...",
            ".............",
            "X.X.X.X.X.X.X",
            ".X.X.X.X.X.X."};

    /** A little picture of X and dots, drawn at so many pixels a dot, centred. */
    static void glyph(Px f, String[] rows, int cx, int cy, int s, int c) {
        int x0 = cx - rows[0].length() * s / 2, y0 = cy - rows.length * s / 2;
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++)
                if (rows[j].charAt(i) == 'X') f.rect(x0 + i * s, y0 + j * s, x0 + (i + 1) * s, y0 + (j + 1) * s, c);
    }


    /** The plinth in the middle of the road. */
    private static void plinth(DrawList d) {
        d.box(0f, PLINTH_TOP * 0.5f, PLINTH_Z, PLINTH_HX, PLINTH_TOP * 0.5f, PLINTH_HZ, null, 0xE6D8BC, DrawList.STONE);
    }

    /** A little stack on the plinth, the golden cube turning slowly on top. */
    private void stack(DrawList d) {
        float top = PLINTH_TOP;
        d.box(-1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(1.2f, top + 0.45f, PLINTH_Z, 0.3f, 0.45f, 0.3f, null, 0xC0603E, DrawList.GRAIN);
        d.box(0f, top + 0.98f, PLINTH_Z, 1.6f, 0.08f, 0.42f, null, 0x4F8F86, DrawList.WOOD);
        d.box(-0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xBDB3A2, DrawList.STONE);
        d.box(0.6f, top + 1.36f, PLINTH_Z, 0.3f, 0.3f, 0.3f, null, 0xD9A83A, DrawList.GRAIN);
        float a = time * 0.8f, c = (float) Math.cos(a), s = (float) Math.sin(a);
        turn[0] = c; turn[1] = 0; turn[2] = s;
        turn[3] = 0; turn[4] = 1; turn[5] = 0;
        turn[6] = -s; turn[7] = 0; turn[8] = c;
        d.box(0f, top + 1.98f, PLINTH_Z, 0.22f, 0.22f, 0.22f, turn, 0xF2C230, DrawList.GOLD);
    }

    private Px flatPx;

    private Px flat(DrawList d) {
        if (flatPx == null || flatPx.a != d.flat) flatPx = new Px(d.w, d.h, d.flat);
        return flatPx;
    }
}
