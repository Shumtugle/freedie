package com.shumtugle.freedie;

/**
 * The game, apart from the phone: what is where, what moves, what the frame should hold.
 * It never draws: each frame it fills a DrawList, and a renderer draws that. The shell
 * calls, always from one thread: touch() for each queued touch, update(dt), then draw().
 * Coordinates of touches are frame pixels, W across.
 *
 * Every game opens with Freedie herself (see Intro); the first time, with her gift. Then the
 * street under a low evening sun: houses, palms, the sea beyond the promenade, cars parked
 * and cars coming, and on the plinth in the middle of the road a building, with the hammer
 * in hand to knock it down (see Game). The red button on the lamp post rings the
 * dispatcher, and three presses open the bridge.
 */
final class World {
    static final int W = 274;                // frame pixels across; the height follows the screen
    static final String VERSION = "0.43";    // shown on screen; keep in step with versionName
    static final int DOWN = 0, MOVE = 1, UP = 2, BACK = 3, RETURN = 4;

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
        /** One of the games on the bridge's screens, played until it hands back: 0 the first. */
        void play(int game);
        /** A round written down (see Journal): kept as a plain text by this name, in a folder of its own. */
        void journal(String name, String text);
    }

    // the eye, where it stands in the game it comes from: left of the plinth, eyes at a person's height
    static final float CAM_X = -2.3f, CAM_Y = 1.8f, CAM_Z = 0f;
    static final float PLINTH_Z = 11.5f, PLINTH_HX = 2.13f, PLINTH_HZ = 1.17f, PLINTH_TOP = 1.0f;
    static final float FLASH_OUT = 0.35f;

    private final Host host;
    int h = 640;
    float time;
    // the time the street keeps: it stands still while the room between rounds is closed over it
    float sceneTime;
    float frameMs;                           // how long the last frame took, set by the shell
    private final Game game;                  // the building on the plinth, and the hammer
    private Ink hudInk;
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
    // the gate into the yard in the frame, as last drawn: its middle, half its width and height
    private final float[] gateAt = {-99f, -99f, 0f, 0f};
    private final float[] jolt = new float[2], view = new float[3], callAt3 = new float[3];
    private float lightPlate = -1f, frameDt;

    // the game's progress: the level being played (from 0), the golden cubes in hand, where
    int level;
    int cubes;
    boolean atSea;

    // the dispatcher's bridge, while it is open; the presses that call it; the picture coming back
    Bridge bridge;
    // the yard behind the gate, while the player is in it
    Yard yard;
    private int calls;
    private float callAt = -9f;
    private float tvOn = -1f;

    private Prelude intro;
    private boolean swallow;                  // the touch that skipped the opening, until it lifts
    private float flashOut;                   // the flash fading off the game, seconds left

    World(Host host) {
        this.host = host;
        // she opens every game; her heart is given at the first meeting only
        intro = new Intro(host, host.loadInt("met") != 1);
        cubes = host.loadInt("cubes");
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
        if (type == RETURN) {
            // not a touch: back from a game on a screen
            back(0);
            return;
        }
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
        if (yard != null) {
            if (type == BACK) leaveYard();
            else yard.touch(type, x, y);
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
        // a touch on the gate, ajar: the hammer goes to knock it open
        if (type == DOWN && game.gateReady() && Math.abs(x - gateAt[0]) < gateAt[2] + 6f && Math.abs(y - gateAt[1]) < gateAt[3] + 6f) {
            game.throwAtGate();
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
        if (intro != null) {
            intro.update(dt);
            if (intro.done()) {
                if (((Intro) intro).gift) {
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
        // birds and planes keep their own time, counted only while the street is in sight
        fliers.update(dt, bridge == null && yard == null && game.fight == null && !game.atSea && game.tilt() == 0f);
        if (bridge != null) bridge.update(dt);
        if (tvOn >= 0f && (tvOn += dt) > Bridge.TV_ON_T) tvOn = -1f;
        if (yard != null) {
            yard.update(dt);
            return;
        }
        if (bridge == null) {
            game.cubes = cubes;
            game.update(dt);
            level = game.level;
            atSea = game.atSea;
            if (game.cubeGift) {
                // the cube knocked out on the console, in the rack
                game.cubeGift = false;
                cubes++;
                host.saveInt("cubes", cubes);
            }
            if (game.yardDue) openYard();
        }
    }

    /** Through the gate, once it has swung wide: into the yard, out of a flash of light. */
    void openYard() {
        game.cancel();
        game.fromYard();
        yard = new Yard(this, host);
        flashOut = FLASH_OUT;
        host.sound(Synth.WHOOSH, 0.6f, 0.7f);
    }

    /** Out of the yard: back on the street, before the same building, the hammer in hand. */
    void leaveYard() {
        yard = null;
        game.crest.dirty();
        game.fromYard();
        flashOut = FLASH_OUT;
        host.sound(Synth.DOOR, 0.8f, 1f);
    }

    /** Whether the back key means something here, rather than leaving the game. */
    boolean wantsBack() {
        return bridge != null || yard != null;
    }

    /** For the desktop stand: where the gate is in the frame: its middle, half its width and height. */
    float[] standGate() {
        return gateAt;
    }

    /** For the desktop stand: into the yard at once. */
    void standYard() {
        openYard();
    }

    /** For the desktop stand: the cars on the move, the people's solids. */
    Traffic standTraffic() {
        return traffic;
    }

    /** For the desktop stand: the fight at once, as from its screen on the bridge. */
    void standFight() {
        game.fightNow();
    }

    /** For the desktop stand: the point a player would throw at in the fight now, or null. */
    float[] standFightPick() {
        return game.fight == null ? null : game.fight.standPick();
    }

    /** For the desktop stand: a throw in the fight at a frame point. */
    void standFightThrow(float x, float y) {
        game.standFightThrow(x, y);
    }

    /** For the desktop stand: whether the hammer is in hand. */
    boolean standHandFree() {
        return game.hs == Game.HAND;
    }

        /** For the desktop stand: whether a fight has come to its end, won or lost. */
    boolean standFightOver() {
        return game.fight != null && !game.fight.live();
    }

    /** For the desktop stand: how the fight stands, in words. */
    String standFightState() {
        return game.fight == null ? "no fight, level " + (level + 1) : game.fight.standState() + (game.aiming ? " aiming" : "") + " hs " + game.hs;
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

    /** Whether the opening is still playing. */
    boolean opening() {
        return intro != null;
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
        if (game.atSea && intro == null) Ocean.light(d);
        // on the street, the hour of the evening the level is played at
        else if (intro == null) Daylight.light(d, plate());
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
        if (intro != null) {
            intro.frame(d, W);
            intro.draw(d);
            intro.flat(f, d);
            return;
        }
        if (yard != null) {
            yard.ink = hudInk;
            yard.draw(d, f);
            if (hudInk.used) {
                d.hudRows = Crest.ROWS;
                d.hudFresh = hudInk.fresh;
                hudInk.fresh = false;
            }
            d.flash = flashOut / FLASH_OUT;
            PixelFont.draw(f, VERSION, W - PixelFont.width(VERSION, 1) - 3, h - 10, 1, 0x88FFFFFF);
            return;
        }
        float yaw = (float) Math.atan2(-CAM_X, PLINTH_Z);
        // as the room closes in, the eye drops a little to the plinth, so it sits on the set
        float pitch = -0.2f * game.tilt();
        // far plinths: under an aim the view closes in on the building
        game.view(yaw, pitch, view);
        yaw = view[0];
        pitch = view[1];
        // in the fight the street jolts as she comes down
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
        if (game.fight == null && !game.atSea) fliers.strokes(d);
        d.red = game.red();
        d.gold = game.gold();
        d.blur = game.blur();
        // the flash off the opening, or the light between two episodes
        d.flash = Math.max(flashOut / FLASH_OUT, game.light());
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
            Street.gate(d, game.gateOpen(), game.yardOpen());
            gateSeen(d);
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
        if (game.gateReady() && gateAt[0] > -50f) gateGlow(f);
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

    private final float[] g0 = new float[2], g1 = new float[2];

    /** The gate ajar calls: a warm light breathing round it, over the open street only. */
    private void gateGlow(Px f) {
        float cx = gateAt[0], cy = gateAt[1], ax = gateAt[2] + 9f, ay = gateAt[3] + 9f;
        float breath = 0.6f + 0.4f * (float) Math.sin(time * 2.4f);
        for (int y = (int) (cy - ay * 1.6f); y <= cy + ay * 1.6f; y++)
            for (int x = (int) (cx - ax * 1.6f); x <= cx + ax * 1.6f; x++) {
                if (x < 0 || y < 0 || x >= f.w || y >= f.h || f.a[y * f.w + x] != 0) continue;
                float ex = (x + 0.5f - cx) / ax, ey = (y + 0.5f - cy) / ay, r = (float) Math.sqrt(ex * ex + ey * ey);
                if (r >= 1.6f || r < 0.55f) continue;
                float k = r < 1f ? (r - 0.55f) / 0.45f : 1f - (r - 1f) / 0.6f;
                int al = Math.round(120f * k * k * breath);
                if (al > 0) f.a[y * f.w + x] = (al << 24) | 0xFFB858;
            }
    }

    /** Where the gate is in the frame now, for a touch on it. */
    private void gateSeen(DrawList d) {
        gateAt[0] = gateAt[1] = -99f;
        if (game.tilt() > 0f) return;
        float x = Street.HOUSES_X - 0.1f;
        if (!d.project(x, 0f, Street.GATE_Z - Street.GATE_HALF, g0) || !d.project(x, Street.GATE_H, Street.GATE_Z + Street.GATE_HALF, g1)) return;
        gateAt[0] = (g0[0] + g1[0]) * 0.5f;
        gateAt[1] = (g0[1] + g1[1]) * 0.5f;
        gateAt[2] = Math.abs(g1[0] - g0[0]) * 0.5f;
        gateAt[3] = Math.abs(g1[1] - g0[1]) * 0.5f;
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
        for (float z = -12f; z < 120f; z += 14f)
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
        d.red = 0f;
        d.gold = 0f;
        d.blur = 0f;
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

    /** Whether a console monitor has a game behind it yet: the alley and the stones are still to come. */
    boolean offers(int which) {
        return which == Bridge.HOME;
    }

    /** The lever, answered, once the room has gone out: the game comes on like a set. */
    void bridgeAct(int which) {
        bridge = null;
        tvOn = 0f;
    }

    /** The fight's screen, with its cube, poked: out of the room and onto the red street, where she comes. */
    void bridgeFight() {
        bridge = null;
        game.fightNow();
    }

    /** A screen with its cube, thrown at: into its episode, through the light; the crossroads not yet. */
    void bridgeEnter(int which) {
        bridge = null;
        if (which < Episode.COUNT) {
            game.enter(Episode.FIRST[which] - 1);
            level = game.level;
        } else flashOut = FLASH_OUT;
    }

    /** A game's screen, poked: the game is played over the bridge, which waits behind it. */
    void bridgeGame(int which) {
        host.play(which == Bridge.GAME_A ? 0 : 1);
    }

    /** Back from a game on a screen: the cubes found there, and the room coming on again like a set. */
    void back(int found) {
        cubes = host.loadInt("cubes");
        tvOn = 0f;
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


    private Px flatPx;

    private Px flat(DrawList d) {
        if (flatPx == null || flatPx.a != d.flat) flatPx = new Px(d.w, d.h, d.flat);
        return flatPx;
    }
}
