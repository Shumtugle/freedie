package com.shumtugle.freedie;

/**
 * The game, apart from the phone: what is where, what moves, what the frame should hold.
 * It never draws: each frame it fills a DrawList, and a renderer draws that. The shell
 * calls, always from one thread: touch() for each queued touch, update(dt), then draw().
 * Coordinates of touches are frame pixels, W across.
 *
 * The street under a low evening sun: houses, palms, the sea beyond the promenade, cars
 * parked and cars coming, and on the plinth in the middle of the road a building, with the
 * hammer in hand to knock it down (see Game). The red button on the lamp post rings the
 * dispatcher, and three presses open the bridge, where any level played so far can be
 * played again (see Bridge).
 */
final class World {
    static final int W = 274;                // frame pixels across; the height follows the screen
    static final String VERSION = "0.55";    // shown on screen; keep in step with versionName
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
        /** A round written down (see Journal): kept as a plain text by this name, in a folder of its own. */
        void journal(String name, String text);
    }

    // the eye, where it stands in the game it comes from: left of the plinth, eyes at a person's height
    static final float CAM_X = -2.3f, CAM_Y = 1.8f, CAM_Z = 0f;
    static final float PLINTH_Z = 11.5f, PLINTH_HX = 2.13f, PLINTH_HZ = 1.17f, PLINTH_TOP = 1.0f;

    private final Host host;
    int h = 640;
    float time;
    // the time the street keeps: it stands still while the room between rounds is closed over it
    float sceneTime;
    float frameMs;                           // how long the last frame took, set by the shell
    private final Game game;                  // the building on the plinth, and the hammer
    private Ink hudInk, roomInk;
    private final People people = new People(20, 0x5EE7L, 1.2f);   // up and down the street, and right by the eye
    private final Fliers fliers = new Fliers();                     // far off in the sky, seldom

    private final DrawList still = new DrawList();   // the street, built once
    private final DrawList sea = new DrawList();     // the winter ocean beyond the portal, built once
    private boolean built;
    private final Traffic traffic = new Traffic(PLINTH_Z);
    // what cannot be walked or driven through: the street's own things, and what lies about
    private final Solid solid = new Solid();
    private float pressedAt = -9f;            // when the red button was last pressed
    private final float[] button = {-99f, -99f};      // where it is in the frame, as last drawn
    private final float[] jolt = new float[2], view = new float[3], callAt3 = new float[3];
    private float lightPlate = -1f, frameDt;

    // the game's progress: the level being played (from 0), and whether it is out at sea
    int level;
    boolean atSea;

    // the dispatcher's bridge, while it is open; the presses that call it; the picture coming back
    Bridge bridge;
    private int calls;
    private float callAt = -9f;
    private float tvOn = -1f;
    private boolean swallow;                  // the touch that pressed the red button, until it lifts

    World(Host host) {
        this.host = host;
        game = new Game(host, host.loadInt("level"));
        game.traffic = traffic;
        game.solid = solid;
        people.solid = solid;
        level = game.level;
        // along the pavement by the curb and the promenade's near edge, up and down the whole
        // street and right past the eye: half out of the frame close by, their long shadows
        // reach across the road in front of it; nobody on the road
        java.util.Random r = people.random();
        for (int i = 0; i < 20; i++) {
            float side = (i & 1) == 0 ? 1f : -1f;
            float x = side * (4.8f + r.nextFloat() * (side > 0 ? 4.6f : 2.0f));
            float z0 = -4f + r.nextFloat() * 40f, z1 = Math.min(82f, z0 + 30f + r.nextFloat() * 30f);
            people.walk(x, z0, x + (r.nextFloat() - 0.5f) * 0.8f, z1, Street.WALK_TOP);
        }
    }

    void resize(int screenW, int screenH) {
        h = Math.max(1, Math.round(W * (float) screenH / Math.max(1, screenW)));
    }

    void touch(int type, float x, float y) {
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
        if (swallow) {
            if (type == UP) swallow = false;
            return;
        }
        if (type == BACK) return;
        // the red button, pressed with the hammer in hand, before it can be taken for an aim
        if (type == DOWN && game.handFree() && Math.hypot(x - button[0], y - button[1]) < 11f) {
            pressedAt = time;
            host.sound(Synth.TOCK, 0.8f, 1f);
            call();
            swallow = true;
            return;
        }
        game.touch(type, x, y);
    }

    void update(float dt) {
        time += dt;
        frameDt = dt;
        // the street on the set is a still: while the room is all closed in, nothing on it moves
        boolean still = game.tilt() >= 1f;
        if (!still) {
            sceneTime += dt;
            traffic.update(dt);
        }
        // birds and planes keep their own time, counted only while the street is in sight
        fliers.update(dt, bridge == null && !game.atSea && game.tilt() == 0f);
        if (bridge != null) bridge.update(dt);
        if (tvOn >= 0f && (tvOn += dt) > Bridge.TV_ON_T) tvOn = -1f;
        if (bridge == null) {
            game.update(dt);
            level = game.level;
            atSea = game.atSea;
        }
    }

    /** Whether the back key means something here, rather than leaving the game. */
    boolean wantsBack() {
        return bridge != null;
    }

    /** For the desktop stand: a flock over the street now (1), or a plane that has flown so many seconds. */
    void standFliers(int what, float seconds) {
        if (what == 1) fliers.standFlock();
        else fliers.standPlane(seconds);
    }

    /** For the desktop stand: up among the watchers at once, with a verdict. */
    void standDeck(int verdict) {
        game.standDeck(verdict);
    }

    /** For the desktop stand: every mattress torn, every suitcase burst open. */
    void standTear() {
        game.standTear();
    }

    void draw(DrawList d) {
        if (!built) {
            Street.build(still, solid);
            Ocean.build(sea);
            built = true;
        }
        d.begin(W, h);
        d.time = sceneTime;
        light(d);
        if (game.atSea) Ocean.light(d);
        // on the street, the hour of the evening the level is played at
        else Daylight.light(d, plate());
        java.util.Arrays.fill(d.flat, 0);
        Px f = flat(d);
        // the fine layer over the top, for the crest: kept from frame to frame (the crest redraws
        // it only when something on it changes), handed over only if the crest is shown
        if (hudInk == null || hudInk.w != W * Crest.K) hudInk = new Ink(W * Crest.K, Crest.ROWS * Crest.K);
        hudInk.used = false;
        d.hud = hudInk.a;
        d.hudW = hudInk.w;
        d.hudH = hudInk.h;
        d.hudRows = 0;
        game.hudInk = hudInk;
        if (roomInk == null || roomInk.w != W * Console.Q || roomInk.h != h * Console.Q) roomInk = new Ink(W * Console.Q, h * Console.Q);
        roomInk.used = false;
        game.roomInk = roomInk;
        float yaw = (float) Math.atan2(-CAM_X, PLINTH_Z);
        // as the room closes in, the eye drops a little to the plinth, so it sits on the set
        float pitch = -0.2f * game.tilt();
        // far plinths: under an aim the view closes in on the building
        game.view(yaw, pitch, view);
        yaw = view[0];
        pitch = view[1];
        // the ground shaking on the 39th
        game.jolt(jolt);
        yaw += jolt[0] / (W * 1.9f);
        pitch += jolt[1] / (W * 1.9f);
        // as the room closes in over the street, the street draws back a little into the set
        float wide = 1f - 0.15f * game.tilt();
        d.camera(CAM_X, CAM_Y, CAM_Z, yaw, pitch, W * 1.9f * view[2] * wide, 0.1f, 500f);
        // closed in, the haze thins as if the building had come nearer: it stays readable far off
        d.fogStart *= view[2];
        d.fogScale *= view[2];
        game.frame(d);
        if (!game.atSea) fliers.strokes(d);
        // the light through the portal
        d.flash = game.light();
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
        if (game.atSea) {
            d.append(sea);
            Ocean.draw(d, Builder.CZ, sceneTime);
            Ocean.button(time, callAt3);
        } else {
            d.append(still);
            // the gate in the houses on the right, shut
            Street.gate(d, 0f, false);
            if (d.night > 0f) lamps(d);
            traffic.draw(d);
            people.draw(d, sceneTime, CAM_X, CAM_Y, CAM_Z);
            callAt3[0] = Street.CALL_X;
            callAt3[1] = Street.CALL_Y + 0.06f;
            callAt3[2] = Street.CALL_Z;
        }
        game.draw(d);
        // the red button on the call box: a slow glow, bright for a moment when pressed
        float since = time - pressedAt;
        float glow = since < 0.35f ? 1f : 0.45f + 0.25f * (float) Math.sin(time * 3.1f);
        int red = Street.mix(0x6A0C08, 0xFF2A18, glow);
        float in = since < 0.15f ? 0.015f : 0f;
        d.box(callAt3[0] + in, callAt3[1], callAt3[2], 0.025f, 0.075f, 0.075f, null, red, DrawList.GLOW);
        if (!d.project(callAt3[0], callAt3[1], callAt3[2], button)) button[0] = button[1] = -99f;

        // the flat layer: snow at sea, the aim, the version in the corner, and the cost of a frame while the engine is young
        if (game.atSea) Ocean.snow(f, time);
        game.flat(f);
        // the aim, drawn smooth over everything by the renderer
        game.aim(d);
        // the room between rounds, at the world's own grain, over all of the frame
        if (roomInk.used) Console.over(f, roomInk);
        if (hudInk.used) {
            d.hudRows = Crest.ROWS;
            d.hudFresh = hudInk.fresh;
            hudInk.fresh = false;
        }
        PixelFont.draw(f, VERSION, W - PixelFont.width(VERSION, 1) - 3, h - 10, 1, 0x88FFFFFF);
        if (frameMs > 0f) {
            String ms = String.valueOf(Math.round(frameMs));
            PixelFont.draw(f, ms, 4, h - 10, 1, 0xCCFFFFFF);
        }
    }

    /** The number on the plate of the level being played, eased while one turns into the next, for the light. */
    private float plate() {
        float target = game.level + 1;
        if (lightPlate < 0f || Math.abs(target - lightPlate) > 3f) lightPlate = target;
        // one level to the next, the light moves on in a couple of seconds rather than at once
        lightPlate += (target - lightPlate) * Math.min(1f, frameDt * 1.2f);
        return lightPlate;
    }

    /** The street lamps lit, as night comes: their glass heads glowing warm. */
    private void lamps(DrawList d) {
        int c = Street.mix(0xF3E2B4, 0xFFC878, Math.min(1f, d.night * 1.4f));
        for (float z = -40f; z < 120f; z += 14f)
            for (int side = -1; side <= 1; side += 2)
                d.box(side * Street.LAMP_X, Street.WALK_TOP + 4.4f, z, 0.135f, 0.185f, 0.135f, null, c, DrawList.GLOW);
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
        d.overcast = 0f;
        d.night = 0f;
        d.grade[0] = 1f;
        d.grade[1] = 1f;
        d.grade[2] = 0f;
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
        game.cancel();
        bridge = new Bridge(this, host);
        host.sound(Synth.DOOR, 1f, 0.8f);
    }

    int best() {
        return Math.max(level + 1, host.loadInt("best"));
    }

    /** The key back, answered, once the room has gone out: the game comes on like a set, as it was. */
    void bridgeHome() {
        bridge = null;
        tvOn = 0f;
    }

    /** The green button: onto the street at that level, counted from one, the street coming on like a set. */
    void bridgeEnter(int plate) {
        bridge = null;
        game.enter(plate - 1);
        level = game.level;
        tvOn = 0f;
    }

    private Px flatPx;

    private Px flat(DrawList d) {
        if (flatPx == null || flatPx.a != d.flat) flatPx = new Px(d.w, d.h, d.flat);
        return flatPx;
    }
}
