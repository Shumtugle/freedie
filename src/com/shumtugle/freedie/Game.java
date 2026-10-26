package com.shumtugle.freedie;

import java.util.ArrayList;
import java.util.Random;

/**
 * A level on the street, as in the game this one grows from: a building on the plinth and
 * the wooden hammer in hand. A finger put down anywhere brings up a ring on the building;
 * moved, the ring moves with it, never under it; held still, the hammer draws back and the
 * throw gathers strength, and held too long the arm tires and the ring shakes. Let go, the
 * hammer flies: a quick release lobs, a full one flies flat. It knocks into the building with
 * the game's own physics, then comes back to the hand.
 *
 * Every throw burns one of five stars in the corner. Knock three quarters of a building's
 * weight off the plinth before the stars run out and, once things are still, the view tips up
 * into the sky, where the gods gather and judge the fall (see Sky); then the next building
 * falls out of the sky onto the plinth as the view comes back down. Run out of stars, and the
 * gods frown, and the same building comes down again for another try.
 *
 * The first building is the gods' welcome: it stands close, flies to pieces at the first touch
 * of the hammer, and they all laugh and give the heavy hammer. From the second on the plinth
 * stands a little further off, and the game is played fair. A building felled at one throw
 * always brings the heavy hammer: a few heavy throws, marked under the stars; a tap on the
 * hammer in hand, or the one waiting low on the left, swaps one for the other.
 *
 * Lose the same building ten times running and the gods give the golden hammer: one throw
 * that scatters the building whatever it hits, as the very first one did. Once that has been
 * given, the next ten lost in a row bring Freedie herself, and the fight (see Fight).
 *
 * From the 21st on the plinth stands farther down the street; then, while a throw is aimed
 * and in flight, the view closes in on the building, the farther the more, and opens out
 * again once the hammer is back.
 *
 * When the 101st is down, no new building comes: a ring of glowing cloud gathers over the
 * plinth, winter showing through it, and a hammer thrown through it takes the player out
 * onto the winter ocean (see Ocean), where the buildings stand on an iceberg and come from
 * a set laid out in advance (see Builder). For now the levels go as far as the 109th.
 *
 * Plain Java: the world calls touch(), update() and draw() from its one thread, and says
 * where the eye is with frame() before any of them needs it.
 */
final class Game implements Physics.Listener {
    static final float CLEAR = 0.75f;          // share of the weight to knock off the plinth
    static final int BUILD = 0, PLAY = 1, CLEARED = 2, VANISH = 3, SKY_UP = 4, SKY_HOLD = 5, LOST = 6, FIGHT = 7, INSERT = 8,
            PORTAL = 9;
    static final int STARS = 5;
    /** The last level there is yet: the 109th, out at sea. */
    static final int LAST = 108;
    /** The first level out at sea, beyond the portal: the 102nd. */
    static final int SEA_FROM = 101;
    /** Whether the level being played is out at sea. */
    boolean atSea;
    static final float TILT = 1.0f;            // seconds to tip up into the sky, and back
    static final int HAND = 0, FLY = 1, BACK = 2;
    static final float CHARGE = 0.9f;          // seconds of stillness to full strength
    static final int DOWN = World.DOWN, MOVE = World.MOVE, UP = World.UP;

    final World.Host host;
    final Physics phys = new Physics();
    int stars = STARS;
    /** The golden cubes in the rack, shown by the stars; the world keeps them. */
    int cubes;
    private int nextLevel;

    // the heavy hammer: the gods give a few heavy throws for a building felled at one throw
    static final int HEAVY_GIFT = 3, HEAVY_MAX = 9;
    static final float HEAVY_MASS = 2.5f;
    int heavyLeft;
    boolean heavyOn;
    private boolean heavyThrow, tapOnHammer;
    private float downAt, tapX, tapY, tapFar;
    // the marks in the corner lag behind the gift: they drop into place once the view is back down
    private int heavyShown, marksLanded;
    // a heavy hammer given and still on its way down: not in the hand yet
    private boolean heavyDue;
    private float markT = -1f;
    private static final float MARK_FALL = Crest.MARK_FALL, MARK_GAP = Crest.MARK_GAP;
    private boolean giftShown;
    // the golden hammer: lose the same building ten times running and the gods give one throw
    // that scatters it whatever it hits
    static final int GOLD_AFTER = 10;
    int fails;
    boolean goldOn, goldSeen;
    private boolean goldThrow, goldGift;
    final Crest crest = new Crest();
    // the fight with Freedie: on now, or due after the sky; the diamond hammer the gods give
    // for it; the world going gold after a fight won, the eyes opening after one lost
    Fight fight;
    private boolean fightNext, diamondGift, afterFight, throwDue;
    private float dueX, dueY;
    private float winSky = -1f, wake = -1f;
    // between episodes: a light that rises over the sky and clears onto the next one
    static final float CHAPTER_RISE = 0.45f, CHAPTER_FADE = 0.6f;
    private float chapterT = -1f;
    private boolean chapterDue;
    // a film between two episodes, while it plays (see Crane); the light rising at its end; the
    // cube it gave, for the world to put in the rack
    Crane crane;
    private boolean craneOut;
    boolean cubeGift;
    // the gods' sky, made once the frame's size is known
    private Sky sky;
    private int throwsMade, boltsHeard;
    private float holdFor, backT;
    private boolean fromSky, buildFromSky;
    private final float[] thunderIn = {-1f, -1f, -1f, -1f}, thunderGain = new float[4], thunderRate = new float[4];
    private final int[] thunderId = new int[4];
    private Body plinth;
    /** The parked cars: their bodies here, their dents and alarms in the street's traffic. */
    Traffic traffic;
    /** The street's solids: what lies about is laid down in them every step, for people and cars to keep out of. */
    Solid solid;
    private final Body[] cars = new Body[Traffic.PARK_X.length];
    private final float[] carHitAt = new float[Traffic.PARK_X.length];
    private float carAlarmAt = -9f;
    final ArrayList<Body> pieces = new ArrayList<Body>();
    private float totalMass;
    final Body hammer;
    int level;
    int state = BUILD;
    float stateTime, clock;
    private float acc;

    // the hammer: in the hand, flying, or on its way back
    int hs = HAND;
    private float hsTime, flightTime, retX, retY, retZ, spinSign = 1f;

    // the aim: the ring starts on the building, or where the last throw went, and moves with
    // the finger without sitting under it
    boolean aiming;
    float aimX, aimY;
    private float lastX, lastY;
    private boolean aimedBefore;
    // the wind-up: strength while the finger rests; past full, a tiring arm shakes the ring
    float power;
    private float fatigue, tremX, tremY, anchorX, anchorY, movedAt;
    private boolean fullFelt;

    // the first building's welcome: one blow and it scatters
    private boolean blasted, blastNow;
    private float blastX, blastY, blastZ;
    private final ArrayList<Body> toBurst = new ArrayList<Body>();

    private final Random fx = new Random(99);
    private final float[] tmp = new float[3], dir = new float[3], scr = new float[2];

    // the eye, as the world set it on the last frame
    final float[] eye = new float[3];
    private final float[] right = new float[3], up = new float[3], ahead = new float[3];
    private float focal = 1f;
    int fw = World.W, fh = 640;
    private boolean seen;

    Game(World.Host host, int level) {
        this.host = host;
        this.level = Math.max(0, Math.min(LAST, level));
        phys.listener = this;
        hammer = new Body(0.42f, 0.08f, 0.17f, Body.HAMMER, 0xD9A066);
        heavyLeft = Math.max(0, Math.min(HEAVY_MAX, host.loadInt("gifts")));
        heavyShown = heavyLeft;
        fails = Math.max(0, host.loadInt("fails"));
        goldSeen = host.loadInt("goldSeen") == 1;
        crest.settle(fails);
        if (!Builder.hasWinter()) Builder.readWinter(host.asset("sea.bin"));
        readPortal(host.asset("portal.bin"));
        start(this.level, false);
    }

    // ---- the level ----

    static long seedOf(int level) {
        return level * 2654435761L ^ 0x2545F4914F6CDD1DL;
    }

    /**
     * Raises the building for a level: tested for standing still, asleep, ready to fall. From
     * the sky, its pieces are thrown down by the gods; else they come down just above the plinth.
     */
    void start(int lv, boolean thrown) {
        if (lv != level) {
            // a new building: the count of lost tries starts again
            fails = 0;
            host.saveInt("fails", 0);
        }
        goldOn = fails >= GOLD_AFTER;
        if (fight != null || traffic != null && traffic.calm) {
            // out of the fight: a new street, every car back at the curb, whole
            fight = null;
            if (traffic != null) {
                traffic.calm = false;
                traffic.repark();
            }
        }
        crest.fresh();
        episodeRun(lv);
        level = lv;
        buildFromSky = thrown;
        host.saveInt("level", lv);
        if (host.loadInt("best") < lv + 1) host.saveInt("best", lv + 1);
        // the first stands close; the rest a little further off; at sea, on the iceberg
        atSea = lv >= SEA_FROM;
        Builder.CZ = Builder.BASE_CZ + (atSea ? seaFar(lv - SEA_FROM) : Builder.farther(lv));
        portalIn = -1f;
        Builder.CX = 0f;
        Builder.PHZ = Builder.PHZ1;
        phys.clear();
        pieces.clear();
        plinth = new Body(Builder.PHX, Builder.TOP * 0.5f, Builder.PHZ, Body.PLINTH, 0xE6D8BC);
        plinth.setPose(Builder.CX, Builder.TOP * 0.5f, Builder.CZ, 1, 0, 0, 0);
        phys.add(plinth);
        // the parked cars and the cones, as solid as the plinth: what falls on them stays off the road
        for (int i = 0; i < Traffic.PARK_X.length; i++) {
            cars[i] = new Body(0.95f, 0.65f, 2.3f, Body.CAR, 0);
            // out at sea there are no cars
            carGone(i, atSea);
            phys.add(cars[i]);
        }
        ArrayList<Body> made = buildingFor(lv);
        // grown at the first plinth spot: carried along to where the plinth stands now
        for (Body b : made) b.homeZ += Builder.CZ - Builder.BASE_CZ;
        totalMass = 0f;
        for (Body b : made) {
            b.setPose(b.homeX, b.homeY, b.homeZ, 1, 0, 0, 0);
            b.asleep = true;
            phys.add(b);
            pieces.add(b);
            totalMass += b.mass;
        }
        hammer.ghost = true;
        hammer.asleep = false;
        phys.add(hammer);
        hs = HAND;
        aiming = false;
        aimedBefore = false;
        throwsMade = 0;
        stars = STARS;
        heavyThrow = false;
        if (heavyLeft == 0) heavyOn = false;
        blasted = blastNow = false;
        toBurst.clear();
        pN = 0;
        dropN = 0;
        lN = 0;
        toPop.clear();
        rollAt = 0f;
        lastLanded = -1;
        lastBuildTime = 0f;
        setState(BUILD);
    }

    /** A building for a level, tested and ready: the first seeds that stand still on their own. */
    static ArrayList<Body> buildingFor(int lv) {
        Builder builder = new Builder();
        long sd = seedOf(lv);
        if (lv >= SEA_FROM && Builder.hasWinter()) {
            // out at sea: the set laid out in advance, each already known to stand
            ArrayList<Body> made = builder.winter(lv - SEA_FROM, sd, false);
            standsStill(made);
            return made;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            ArrayList<Body> made = builder.build(lv, sd + attempt * 7919L);
            if (standsStill(made)) return made;
        }
        // a recipe that will not stand however it is seeded: a building of the level before,
        // grown afresh, as tall and as varied as this one should be
        for (int back = 1; back <= 3 && lv - back > 0; back++)
            for (int attempt = 0; attempt < 6; attempt++) {
                ArrayList<Body> made = builder.build(lv - back, sd + back * 104729L + attempt * 7919L);
                if (standsStill(made)) return made;
            }
        return builder.build(0, sd);
    }

    /** Whether a building stands a second on its own; if so it starts as it settled. */
    static boolean standsStill(ArrayList<Body> made) {
        Physics p = new Physics();
        p.sleeping = false;
        Body pl = new Body(Builder.PHX, Builder.TOP * 0.5f, Builder.PHZ1, Body.PLINTH, 0);
        pl.setPose(0f, Builder.TOP * 0.5f, Builder.BASE_CZ, 1, 0, 0, 0);
        p.add(pl);
        for (Body b : made) {
            b.setPose(b.homeX, b.homeY, b.homeZ, 1, 0, 0, 0);
            b.asleep = false;
            p.add(b);
        }
        for (int i = 0; i < 60; i++) p.step(1f / 60f);
        boolean ok = true;
        for (Body b : made) {
            float dx = b.px - b.homeX, dy = b.py - b.homeY, dz = b.pz - b.homeZ;
            // settling by a few centimeters is not falling: nothing may tip, nothing may wander far
            if (dx * dx + dy * dy + dz * dz > 0.09f * 0.09f || (b.radius == 0f && Math.abs(b.qw) < 0.995f)) ok = false;
        }
        if (ok)
            for (Body b : made) {
                b.homeX = b.px;
                b.homeY = b.py;
                b.homeZ = b.pz;
            }
        for (Body b : made) b.id = 0;
        return ok;
    }

    /** A parked car's body torn from its place, or set down again where the street says it lies. */
    void carGone(int i, boolean gone) {
        Body c = cars[i];
        if (c == null) return;
        c.ghost = gone;
        float x = traffic == null ? Traffic.PARK_X[i] : traffic.atX[i], z = traffic == null ? Traffic.PARK_Z[i] : traffic.atZ[i];
        float yaw = traffic == null ? 0f : traffic.atYaw[i];
        c.setPose(x, 0.65f, z, (float) Math.cos(yaw / 2), 0f, (float) Math.sin(yaw / 2), 0f);
    }

    // ---- the fight ----

    /**
     * Freedie comes for the player: the plinth and the building gone, the street held for
     * her, the diamond hammer in hand. From the sky the view comes down into it; else it
     * reddens where it stands.
     */
    void startFight(boolean fromAbove) {
        // down from the sky she has come in the game itself: her screen on the bridge lights up
        if (fromAbove) host.saveInt("fought", 1);
        fightNext = false;
        throwDue = false;
        phys.clear();
        pieces.clear();
        hammer.ghost = true;
        hammer.asleep = false;
        phys.add(hammer);
        for (Body c : cars) if (c != null) c.ghost = true;
        hs = HAND;
        aiming = false;
        aimedBefore = false;
        heavyThrow = false;
        goldThrow = false;
        goldOn = false;
        power = 0f;
        pN = 0;
        lN = 0;
        dropN = 0;
        toPop.clear();
        toBurst.clear();
        traffic.calm = true;
        fight = new Fight(this);
        fromSky = fromAbove;
        backT = 0f;
        wake = -1f;
        setState(FIGHT);
    }

    /** The fight at once, from the bridge's console. */
    void fightNow() {
        cancel();
        chapterT = -1f;
        startFight(false);
    }

    /** The fight is won: the world goes gold and up into the sky, where the gods cheer. */
    private void fightWon() {
        afterFight = true;
        winSky = 0f;
        lookUp(Sky.OVATION, level + 1);
    }

    /** The fight is lost: the player wakes on the street, before the same building, as from a dream. */
    private void fightLost() {
        fails = 0;
        host.saveInt("fails", 0);
        start(level, false);
        wake = 0f;
        host.sound(Synth.BEAT, 0.8f, 0.9f);
    }

    /** How far the world is seen red now, 0 to 1. */
    float red() {
        if (winSky >= 0f) return Math.max(0f, 1f - winSky / 0.9f);
        if (fight == null || state != FIGHT) return 0f;
        return fromSky ? Math.min(1f, backT / TILT) : Math.min(1f, stateTime / 0.8f);
    }

    /** How far the world is seen gold, as a fight won turns to day. */
    float gold() {
        if (winSky < 0f) return 0f;
        return winSky < 0.9f ? winSky / 0.9f : Math.max(0f, 1f - (winSky - 0.9f) / 1.3f);
    }

    /** Waking after a fight lost: the street blurred at first, then clear, in frame pixels. */
    float blur() {
        if (wake < 0f) return 0f;
        return Math.max(0f, 1f - wake / 1.6f) * 7f;
    }

    /** The street's jolt this frame, frame pixels across and up. */
    void jolt(float[] out) {
        if (fight != null && state == FIGHT) fight.jolt(out);
        else out[0] = out[1] = 0f;
    }

    /** The hammer away, as the fight throws it: from here, so fast, so long in the air; spun by the side aimed at. */
    void launch(float sx, float sy, float sz, float vx, float vy, float vz, float t, float aimedX) {
        hammer.ghost = false;
        hammer.asleep = false;
        float yaw = (float) Math.atan2(ahead[0], ahead[2]);
        hammer.setPose(sx, sy, sz, (float) Math.cos(yaw / 2), 0f, (float) Math.sin(yaw / 2), 0f);
        hammer.vx = vx;
        hammer.vy = vy;
        hammer.vz = vz;
        spinSign = aimedX < fw * 0.5f ? 1f : -1f;
        hammer.wy = spinSign * 18f;
        hammer.wx = 0f;
        hammer.savePrev();
        flightTime = t;
        hs = FLY;
        hsTime = 0f;
        power = 0f;
    }

    private void setState(int s) {
        state = s;
        stateTime = 0f;
    }

    /** Weight still on the plinth, as a share of the building. */
    float onPlinth() {
        if (totalMass <= 0f) return 0f;
        float m = 0f;
        for (Body b : pieces)
            if (!b.gone && Math.abs(b.px - Builder.CX) < Builder.PHX + 0.05f && Math.abs(b.pz - Builder.CZ) < Builder.PHZ + 0.05f
                    && b.py > Builder.TOP - 0.05f && b.py < Builder.TOP + 8f) m += b.mass;
        return m / totalMass;
    }

    // ---- the finger ----

    /** How far off the iceberg is for the n-th level at sea: nearer than the last plinths in town. */
    static float seaFar(int n) {
        return Math.min(30f, 20f + 2f * (n / 10));
    }

    /** Whether the hammer is in hand and a finger may aim. */
    boolean ready() {
        if (hs != HAND) return false;
        if (state == FIGHT) return fight != null && fight.live();
        if (state == PORTAL) return portalIn < 0f && stateTime > PORTAL_RISE;
        return state == PLAY && stars > 0;
    }

    /** Whether the hand is free for something else: the hammer in it, no aim begun. */
    boolean handFree() {
        return hs == HAND && !aiming && crane == null;
    }

    void touch(int type, float x, float y) {
        if (crane != null) {
            if (type == DOWN) crane.tap();
            return;
        }
        if (type == DOWN) {
            tapX = x;
            tapY = y;
            tapFar = 0f;
        } else tapFar = Math.max(tapFar, (float) Math.hypot(x - tapX, y - tapY));
        if (type == DOWN) {
            // in the fight a finger may aim while the hammer is still on its way back
            boolean can = fight != null ? state == FIGHT && fight.live() : ready();
            if (!can || !seen) return;
            aiming = true;
            downAt = clock;
            tapOnHammer = fight == null && heavyShown > 0 && onHammer(x, y);
            lastX = x;
            lastY = y;
            if (fight != null) {
                // the diamond hammer is quick: the ring comes up right over the finger
                aimX = Math.max(2f, Math.min(fw - 3f, x));
                aimY = Math.max(2f, Math.min(fh - 3f, y - Fight.FINGER));
            } else if (!aimedBefore) buildingCenter();
            anchorX = x;
            anchorY = y;
            movedAt = clock;
            power = 0f;
            fatigue = 0f;
            fullFelt = false;
            return;
        }
        if (!aiming) return;
        if (fight != null) {
            aimX = Math.max(2f, Math.min(fw - 3f, x));
            aimY = Math.max(2f, Math.min(fh - 3f, y - Fight.FINGER));
        } else {
            aimX = Math.max(2f, Math.min(fw - 3f, aimX + x - lastX));
            aimY = Math.max(2f, Math.min(fh - 3f, aimY + y - lastY));
        }
        lastX = x;
        lastY = y;
        // a finger never lies quite still: only a real move breaks the wind-up
        float mx = x - anchorX, my = y - anchorY;
        if (mx * mx + my * my > 2.5f * 2.5f) {
            anchorX = x;
            anchorY = y;
            movedAt = clock;
        }
        if (type == UP) {
            aiming = false;
            // a short tap on the hammer, without a move, swaps hammers instead of throwing
            if (tapOnHammer && clock - downAt < 0.6f && tapFar < 6f) {
                heavyOn = !heavyOn;
                power = 0f;
                host.sound(Synth.CATCH, 0.6f, heavyOn ? 0.7f : 1.2f);
                host.buzz(heavyOn ? 30 : 10);
                return;
            }
            aimedBefore = true;
            if (fight != null) {
                if (hs == HAND) fight.throwAt(aimX, aimY);
                else {
                    // the hammer is not back yet: it goes the moment it is
                    throwDue = true;
                    dueX = aimX;
                    dueY = aimY;
                }
            } else throwHammer();
        }
    }

    /** Where the hammer in hand is on screen, the other one at the lower left, and the marks. */
    private boolean onHammer(float x, float y) {
        return (x > fw * 0.58f && y > fh * 0.7f) || (x < fw * 0.34f && y > fh * 0.8f) || (x < 130f && y < 32f);
    }

    /** Lets a finger go without a throw: the room took it, or the game was put away. */
    void cancel() {
        aiming = false;
        power = 0f;
    }

    /** Where the ring starts on a level's first throw: the middle of what stands on the plinth. */
    private void buildingCenter() {
        if (state == PORTAL) {
            if (project(portalX(), portalY(), Builder.CZ, scr)) {
                aimX = scr[0];
                aimY = scr[1];
            }
            return;
        }
        if (fight != null) {
            fight.middle(scr);
            aimX = scr[0];
            aimY = scr[1];
            return;
        }
        float sx = 0f, sy = 0f, m = 0f;
        for (Body b : pieces) {
            if (b.gone || !project(b.px, b.py, b.pz, scr)) continue;
            sx += scr[0] * b.mass;
            sy += scr[1] * b.mass;
            m += b.mass;
        }
        if (m > 0f) {
            aimX = sx / m;
            aimY = sy / m;
        } else if (project(Builder.CX, Builder.TOP + 1f, Builder.CZ, scr)) {
            aimX = scr[0];
            aimY = scr[1];
        }
    }

    /** Throw speed in m/s from the wind-up: a quick release lobs, a full one flies flat. */
    private float throwSpeed() {
        // the heavy hammer leaves the hand slower and tops out lower, but carries far more weight
        return heavyOn ? 7.5f + power * 4.5f : 8f + power * 5.5f;
    }

    /** Seconds in the air over a distance; far off, the arm puts more into it. */
    static float flight(float dist, float speed) {
        float reach = dist > 42f ? (float) Math.sqrt(dist / 42f) : 1f;
        return Math.max(0.18f, dist / (speed * reach));
    }

    private void windUp(float dt) {
        if (!aiming || fight != null) {
            // the diamond hammer needs no wind-up: it always flies hard and flat
            if (fight != null) power = 0f;
            tremX = tremY = 0f;
            return;
        }
        boolean still = clock - movedAt > 0.1f;
        if (still) power = Math.min(1f, power + dt / CHARGE);
        else power = Math.max(0f, power - dt * 2f);
        if (power >= 1f) {
            fatigue += dt;
            if (!fullFelt) {
                fullFelt = true;
                host.buzz(12);
            }
        } else {
            fatigue = Math.max(0f, fatigue - dt * 2f);
            if (power < 0.9f) fullFelt = false;
        }
        float amp = Math.max(0f, Math.min(8f, (fatigue - 0.35f) * 5f));
        tremX = amp * ((float) Math.sin(clock * 13f) * 0.6f + (float) Math.sin(clock * 7.3f + 1f) * 0.4f);
        tremY = amp * ((float) Math.sin(clock * 11f + 2f) * 0.6f + (float) Math.sin(clock * 5.9f) * 0.4f);
    }

    private void throwHammer() {
        // the ring draws a hammer thrown at it: it gets there in half the time
        float speed = throwSpeed() * (state == PORTAL ? 2.2f : 1f);
        target(aimX + tremX, aimY + tremY, tmp);
        float tx = tmp[0], ty = tmp[1], tz = tmp[2];
        handPoint(tmp);
        float sx = tmp[0], sy = tmp[1], sz = tmp[2];
        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        float t = flight((float) Math.sqrt(dx * dx + dy * dy + dz * dz), speed);
        hammer.ghost = false;
        hammer.asleep = false;
        // the handle across the view, as it leaves the hand; it turns flat about the upright
        float yaw = (float) Math.atan2(ahead[0], ahead[2]) + (fx.nextFloat() - 0.5f) * 0.6f;
        hammer.setPose(sx, sy, sz, (float) Math.cos(yaw / 2), 0f, (float) Math.sin(yaw / 2), 0f);
        hammer.vx = dx / t;
        hammer.vy = dy / t - 0.5f * Physics.G * t;
        hammer.vz = dz / t;
        spinSign = aimX < fw * 0.5f ? 1f : -1f;
        goldThrow = goldOn;
        // a throw at the portal costs nothing, not even a heavy one
        boolean free = state == PORTAL;
        heavyThrow = !goldThrow && heavyOn && heavyLeft > 0 && !free;
        if (heavyThrow) {
            hammer.scaleMass(HEAVY_MASS);
            heavyLeft--;
            host.saveInt("gifts", heavyLeft);
        }
        hammer.wy = spinSign * (heavyThrow ? 8f : 15f);
        hammer.wx = 0.6f;
        hammer.savePrev();
        flightTime = t;
        if (!free) {
            throwsMade++;
            stars--;
            crest.burn(stars);
        }
        hs = FLY;
        hsTime = 0f;
        power = 0f;
        host.sound(Synth.WHOOSH, 0.9f, 0.9f + speed / 60f);
        host.buzz(12);
    }

    // ---- the eye ----

    /** The eye as the world has just set it: kept, so a finger can be turned into a ray. */
    void frame(DrawList d) {
        // the view closing in or opening out under an aim: the ring stays on the same spot of
        // the building, wherever that spot now is in the frame
        boolean keep = seen && aiming && fight == null && zoomK > 0f && zoomK < 1f || seen && aiming && zoomMoved;
        if (keep) target(aimX, aimY, held);
        System.arraycopy(d.eye, 0, eye, 0, 3);
        System.arraycopy(d.right, 0, right, 0, 3);
        System.arraycopy(d.up, 0, up, 0, 3);
        System.arraycopy(d.ahead, 0, ahead, 0, 3);
        focal = d.focal;
        fw = d.w;
        fh = d.h;
        seen = true;
        zoomMoved = false;
        if (keep && project(held[0], held[1], held[2], scr)) {
            aimX = Math.max(2f, Math.min(fw - 3f, scr[0]));
            aimY = Math.max(2f, Math.min(fh - 3f, scr[1]));
        }
        if (sky == null) {
            sky = new Sky();
            sky.build(fw, fh, host.asset("gods.bin"), host.asset("clouds.bin"));
        }
    }

    /**
     * Lays down what lies about now: the pieces of the building wherever they are, low enough
     * to be in someone's way; and tells the cars on the move how far down the road it is clear.
     */
    private void solids() {
        if (solid == null) return;
        solid.clearLoose();
        for (Body b : pieces) {
            if (b.gone || b.py > Builder.TOP + 1.5f) continue;
            float r = b.radius > 0f ? b.radius : Math.max(b.hx, b.hz);
            solid.loose(b.px, b.pz, r);
        }
        if (traffic != null) {
            float far = fight != null ? -1e9f : Builder.CZ + Builder.PHZ;
            traffic.clearFrom = Math.max(far, solid.farthest(-2.3f, 2.3f));
        }
    }

    // ---- the close view ----

    static final float ZOOM_T = 0.3f;          // seconds to close in, and to open out again
    private float zoomK;                       // how far closed in, 0 to 1
    private boolean zoomMoved;
    private final float[] held = new float[3];

    /**
     * How much narrower the close view is for the plinth where it stands now: none for the
     * near ones; for a far one by the ratio of distances, a little less, from 1.25 to 4.6.
     */
    static float zoomFor(float cz) {
        if (cz - Builder.BASE_CZ < 2.5f) return 1f;
        return Math.max(1.25f, Math.min(4.6f, cz / Builder.BASE_CZ * 0.92f));
    }

    /** Closes in while a throw is aimed or in flight, opens out once the hammer is back. */
    private void zoomStep(float dt) {
        boolean want = zoomFor(Builder.CZ) > 1f && (state == PLAY || state == PORTAL) && fight == null && (aiming || hs != HAND || portalIn >= 0f);
        float was = zoomK;
        zoomK = Math.max(0f, Math.min(1f, zoomK + (want ? dt : -dt) / ZOOM_T));
        if (zoomK != was) zoomMoved = true;
    }

    private float zoomEase() {
        float k = zoomK;
        return k * k * (3f - 2f * k);
    }

    /** How much narrower the view is now, 1 when open. */
    float zoomMul() {
        return 1f + (zoomFor(Builder.CZ) - 1f) * zoomEase();
    }

    /**
     * The eye's turn and pitch, and how much narrower it sees, as the close view has them now:
     * from the street's own (given) toward one looking straight at the building, a little over
     * the plinth's top.
     */
    void view(float yaw, float pitch, float[] out) {
        float e = zoomEase();
        if (portalIn >= 0f) {
            // pulled in after the hammer, into the ring
            float k = Math.min(1f, portalIn / CHAPTER_RISE);
            float dx = portalX() - World.CAM_X, dz = Builder.CZ - World.CAM_Z;
            float zy = (float) Math.atan2(dx, dz);
            float zp = (float) Math.atan2(portalY() - World.CAM_Y, (float) Math.sqrt(dx * dx + dz * dz));
            float m = Math.max(1f, zoomFor(Builder.CZ)) * (1f + 5f * k * k);
            out[0] = yaw + (zy - yaw) * Math.max(e, k);
            out[1] = pitch + (zp - pitch) * Math.max(e, k);
            out[2] = m;
            return;
        }
        if (e <= 0f || fight != null) {
            out[0] = yaw;
            out[1] = pitch;
            out[2] = 1f;
            return;
        }
        float dx = Builder.CX - World.CAM_X, dz = Builder.CZ - World.CAM_Z;
        float zy = (float) Math.atan2(dx, dz);
        float zp = (float) Math.atan2(Builder.TOP + 1.4f - World.CAM_Y, (float) Math.sqrt(dx * dx + dz * dz));
        out[0] = yaw + (zy - yaw) * e;
        out[1] = pitch + (zp - pitch) * e;
        out[2] = zoomMul();
    }

    /** How far the view is tipped up into the sky, 0 on the street to 1 among the gods, eased. */
    float tilt() {
        float k;
        if (state == SKY_UP) k = Math.min(1f, stateTime / TILT);
        else if (state == SKY_HOLD) k = 1f;
        else if (fromSky) k = 1f - Math.min(1f, backT / TILT);
        else return 0f;
        return k * k * (3f - 2f * k);
    }

    boolean project(float x, float y, float z, float[] out) {
        float px = x - eye[0], py = y - eye[1], pz = z - eye[2];
        float vz = px * ahead[0] + py * ahead[1] + pz * ahead[2];
        if (vz < 0.05f) return false;
        float vx = px * right[0] + py * right[1] + pz * right[2];
        float vy = px * up[0] + py * up[1] + pz * up[2];
        out[0] = fw * 0.5f + focal * vx / vz;
        out[1] = fh * 0.5f - focal * vy / vz;
        return true;
    }

    /** The ray through a frame point, one meter ahead for every meter it goes. */
    void ray(float sx, float sy, float[] out) {
        float u = (sx - fw * 0.5f) / focal, v = (fh * 0.5f - sy) / focal;
        for (int k = 0; k < 3; k++) out[k] = ahead[k] + right[k] * u + up[k] * v;
    }

    /** The world point under a frame point: the nearest piece, the plinth, a parked car, or the plane through the plinth. */
    void target(float sx, float sy, float[] out) {
        ray(sx, sy, dir);
        float dx = dir[0], dy = dir[1], dz = dir[2];
        float best = 1e9f;
        for (Body b : pieces) {
            if (b.gone) continue;
            float t = rayBox(b, eye[0], eye[1], eye[2], dx, dy, dz);
            if (t > 0f && t < best) best = t;
        }
        if (best > 1e8f) {
            float t = rayBox(plinth, eye[0], eye[1], eye[2], dx, dy, dz);
            if (t > 0f) best = t;
        }
        if (best > 1e8f)
            // a parked car is a thing to aim at too
            for (Body c : cars) {
                if (c == null) continue;
                float t = rayBox(c, eye[0], eye[1], eye[2], dx, dy, dz);
                if (t > 0f && t < best) best = t;
            }
        if (best > 1e8f) {
            // the upright plane through the plinth's middle; under the ground, the ground
            best = (Builder.CX - eye[0]) * ahead[0] + (Builder.CZ - eye[2]) * ahead[2];
            if (dy * best + eye[1] < 0f && dy < 0f) best = -eye[1] / dy;
        }
        out[0] = eye[0] + dx * best;
        out[1] = eye[1] + dy * best;
        out[2] = eye[2] + dz * best;
    }

    /** Ray against an oriented box; distance along the ray or -1. */
    static float rayBox(Body b, float ox, float oy, float oz, float dx, float dy, float dz) {
        float px = ox - b.px, py = oy - b.py, pz = oz - b.pz;
        float[] o = {b.r00 * px + b.r10 * py + b.r20 * pz, b.r01 * px + b.r11 * py + b.r21 * pz, b.r02 * px + b.r12 * py + b.r22 * pz};
        float[] d = {b.r00 * dx + b.r10 * dy + b.r20 * dz, b.r01 * dx + b.r11 * dy + b.r21 * dz, b.r02 * dx + b.r12 * dy + b.r22 * dz};
        float[] hh = {b.hx, b.hy, b.hz};
        float t0 = -1e9f, t1 = 1e9f;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9f) {
                if (Math.abs(o[i]) > hh[i]) return -1f;
                continue;
            }
            float a = (-hh[i] - o[i]) / d[i], c = (hh[i] - o[i]) / d[i];
            if (a > c) {
                float s = a;
                a = c;
                c = s;
            }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, c);
            if (t0 > t1) return -1f;
        }
        return t0 > 0f ? t0 : (t1 > 0f ? 0.001f : -1f);
    }

    /** Where the hand holds the hammer, in the world. */
    void handPoint(float[] out) {
        // closed in, the hand is as far again as the view is narrower: it stays where it is in the frame
        float vx = 0.3f, vy = -0.7f + (float) Math.sin(clock * 1.7f) * 0.008f, vz = 1.35f * zoomMul();
        for (int k = 0; k < 3; k++) out[k] = eye[k] + right[k] * vx + up[k] * vy + ahead[k] * vz;
    }

    // ---- the clock ----

    void update(float dt) {
        // a car about to arrive: time runs slow, so the player can still answer it
        if (fight != null && state == FIGHT && fight.closeCall()) dt *= 0.4f;
        if (winSky >= 0f && (winSky += dt) > 2.4f) winSky = -1f;
        if (wake >= 0f && (wake += dt) > 1.8f) wake = -1f;
        clock += dt;
        stateTime += dt;
        acc += dt;
        int steps = 0;
        while (acc >= 1f / 60f && steps < 2) {
            acc -= 1f / 60f;
            steps++;
            if (state != BUILD && state != VANISH) phys.step(1f / 60f);
            bursts();
            pops();
            blast();
            if (state == PLAY || state == CLEARED) flyingCases();
        }
        if (acc > 1f / 30f) acc = 0f;
        particles(dt);
        lights(dt);
        drops(dt);
        rolling();
        windUp(dt);
        zoomStep(dt);
        solids();
        hammerMoves(dt);
        thunderStep(dt);
        marksStep(dt);
        crest.update(dt);
        // a lost try shows on the ring once the street is back in sight
        if (tilt() * fh < fh / 8f && state != SKY_UP && state != SKY_HOLD) crest.attempts(fails);
        if (sky != null && (state == SKY_UP || state == SKY_HOLD || fromSky)) {
            sky.update(dt);
            if (sky.bolts != boltsHeard) {
                boltsHeard = sky.bolts;
                strikeHeard(sky.boltNear);
            }
        }
        if (chapterT >= 0f) {
            chapterT += dt;
            if (chapterT >= CHAPTER_RISE + CHAPTER_FADE) chapterT = -1f;
        }
        if (fromSky) {
            backT += dt;
            if (backT >= TILT + 0.2f) fromSky = false;
        }
        switch (state) {
            case BUILD:
                buildSounds();
                if (stateTime > buildDuration() + 0.15f) setState(PLAY);
                break;
            case PLAY:
                if (1f - onPlinth() >= CLEAR) {
                    if (!blasted && throwsMade <= 1) host.sound(Synth.THUNDER, 1.1f, 1f);
                    episodeWin();
                    setState(CLEARED);
                } else if (stars == 0 && hs == HAND && !aiming && (phys.settled() || stateTime > 3f)) {
                    setState(LOST);
                }
                break;
            case CLEARED:
                if (stateTime > 2.2f && hs == HAND && (phys.settled() || stateTime > 4.5f)) {
                    if (sky != null) lookUp(throwsMade <= 1 ? Sky.OVATION : throwsMade <= 3 ? Sky.CHEER : Sky.POLITE, level + 1);
                    else vanish();
                }
                break;
            case LOST:
                if (stateTime > 0.7f) {
                    if (sky != null) lookUp(Sky.GLOOM, level);
                    else vanish();
                }
                break;
            case VANISH:
                if (stateTime > 0.5f) start(level, false);
                break;
            case INSERT:
                crane.update(dt);
                if (crane.over() && !craneOut) {
                    // the film ends as it began, in the light
                    craneOut = true;
                    chapterT = 0f;
                    host.sound(Synth.WHOOSH, 0.7f, 0.7f);
                }
                if (craneOut && chapterT >= CHAPTER_RISE) {
                    crane = null;
                    craneOut = false;
                    start(Math.min(LAST, nextLevel), false);
                }
                break;
            case PORTAL:
                portalStep(dt);
                break;
            case FIGHT:
                fight.step(dt);
                if (fight.end > 2.8f && fight.won) fightWon();
                else if (fight.end > 1.3f && !fight.won) fightLost();
                break;
            case SKY_UP:
                if (stateTime >= TILT) setState(SKY_HOLD);
                break;
            case SKY_HOLD:
                // an episode won: the light rises first
                if (stateTime >= holdFor && chapterDue && chapterT < 0f) {
                    chapterT = 0f;
                    host.sound(Synth.WHOOSH, 0.7f, 0.7f);
                }
                if (stateTime >= holdFor && (chapterT < 0f || chapterT >= CHAPTER_RISE)) {
                    boolean chapter = chapterDue;
                    chapterDue = false;
                    if (fightNext) {
                        // down out of the sky, into the red street, where she is coming
                        startFight(true);
                        break;
                    }
                    if (chapter && craneAfter(level, nextLevel)) {
                        // out of the light onto the street, where the crane carries the plinth on
                        startCrane();
                        break;
                    }
                    if (nextLevel == SEA_FROM && level == SEA_FROM - 1) {
                        // the last building in town is down: no new one comes, the portal does
                        openPortal();
                        fromSky = true;
                        backT = 0f;
                        break;
                    }
                    // the gods have thrown the blocks: the next building falls into place as the eye comes down
                    start(Math.min(LAST, nextLevel), true);
                    fromSky = true;
                    backT = 0f;
                }
                break;
            default:
        }
    }

    private void hammerMoves(float dt) {
        hsTime += dt;
        if (hs == FLY && fight != null) {
            // the diamond hammer comes back at once from whatever it met, or missed
            if (hsTime > flightTime + 0.35f || hammer.py < 0.25f || hammer.pz > fight.giant.z + 4f) beginReturn();
        } else if (hs == FLY) {
            if (hsTime > flightTime + 0.6f && (hammer.speed2() < 4f || hammer.py < 0.4f || hammer.pz > Builder.CZ + 4f)
                    || hsTime > flightTime + 2.2f || hammer.pz > Builder.CZ + 16f) beginReturn();
        } else if (hs == BACK) {
            float k = Math.min(1f, hsTime / (fight != null ? 0.4f : 0.6f));
            handPoint(tmp);
            float e = k * k * (3f - 2f * k);
            hammer.px = retX + (tmp[0] - retX) * e;
            hammer.py = retY + (tmp[1] - retY) * e + (float) Math.sin(Math.PI * k) * 1.2f;
            hammer.pz = retZ + (tmp[2] - retZ) * e;
            hammer.rotateBy(0f, spinSign * (heavyThrow ? 8f : 14f) * dt, 0f);
            hammer.updateR();
            if (k >= 1f) {
                host.sound(Synth.CATCH, 0.8f, heavyThrow ? 0.75f : 1f);
                host.buzz(heavyThrow ? 45 : 25);
                goldThrow = false;
                if (heavyThrow) {
                    hammer.scaleMass(1f / HEAVY_MASS);
                    heavyThrow = false;
                    if (heavyLeft == 0) heavyOn = false;
                }
                hs = HAND;
                hsTime = 0f;
                // the last star spent waits for the hammer back, and then for things to settle
                if (state == PLAY) stateTime = 0f;
                if (throwDue) {
                    throwDue = false;
                    if (fight != null && state == FIGHT && fight.live()) fight.throwAt(dueX, dueY);
                }
            }
        }
    }

    void beginReturn() {
        hammer.ghost = true;
        hammer.vx = hammer.vy = hammer.vz = hammer.wx = hammer.wy = hammer.wz = 0f;
        retX = hammer.px;
        retY = hammer.py;
        retZ = hammer.pz;
        hs = BACK;
        hsTime = 0f;
        host.sound(Synth.WHOOSH, 0.45f, 1.25f);
    }

    /**
     * Up into the sky, where the gods judge the fall: laughter and lightning for a building
     * felled at one throw, and the heavy hammer; smiles for a few; a sceptic look for many;
     * anger, sleep and scepticism when the stars ran out. The first building is their
     * welcome: they all laugh aloud. Then the building for the level next.
     */
    private void lookUp(int verdict, int next) {
        nextLevel = next;
        boolean fought = afterFight;
        afterFight = false;
        // after a fight won they all laugh aloud, as at the first building
        sky.begin(verdict, (level == 0 || fought) && verdict == Sky.OVATION);
        boltsHeard = sky.bolts;
        holdFor = verdict == Sky.GLOOM ? 2.2f : verdict == Sky.OVATION ? 3.2f : 2.4f;
        giftShown = false;
        goldGift = false;
        diamondGift = false;
        if (fought) {
            // the fight is over: nothing counted, and no fight again at once
        } else if (verdict == Sky.GLOOM) {
            fails++;
            host.saveInt("fails", fails);
            if (fails == GOLD_AFTER) {
                // out at sea she does not come yet: the golden hammer again instead
                if (!goldSeen || atSea) {
                    // the tenth time the same building would not fall: the gods give the golden hammer
                    goldGift = true;
                    goldSeen = true;
                    host.saveInt("goldSeen", 1);
                } else {
                    // ever after, she comes instead, and the gods give the diamond hammer for her
                    fightNext = true;
                    diamondGift = true;
                    fails = 0;
                    host.saveInt("fails", 0);
                }
                giftShown = true;
                giftT0 = -1f;
            }
        } else if (fails > 0) {
            fails = 0;
            host.saveInt("fails", 0);
        }
        if (verdict == Sky.OVATION) {
            // the gods give the heavy hammer: it comes down to the player from the sky, and is in
            // the hand only once it has come, when its marks have dropped into the corner
            heavyLeft = Math.min(HEAVY_MAX, heavyLeft + HEAVY_GIFT);
            heavyDue = true;
            host.saveInt("gifts", heavyLeft);
            giftShown = true;
            giftT0 = -1f;
        }
        aiming = false;
        host.sound(Synth.WHOOSH, 0.5f, 0.6f);
        setState(SKY_UP);
    }

    /** A strike of lightning: its thunder comes the later, the farther off it struck. */
    private void strikeHeard(float near) {
        int id;
        if (near > 0.8f) id = Synth.thunder(fx.nextBoolean() ? 1 : 4);
        else if (near > 0.55f) id = Synth.thunder(fx.nextInt(3) == 0 ? 0 : fx.nextBoolean() ? 2 : 5);
        else id = Synth.thunder(3);
        host.buzz((int) (40 + 60 * near));
        for (int i = 0; i < thunderIn.length; i++)
            if (thunderIn[i] < 0f) {
                thunderIn[i] = (1f - near) * 0.9f;
                thunderId[i] = id;
                thunderGain[i] = 0.6f + 0.5f * near;
                thunderRate[i] = 0.88f + 0.14f * near + fx.nextFloat() * 0.08f;
                break;
            }
    }

    private void thunderStep(float dt) {
        for (int i = 0; i < thunderIn.length; i++) {
            if (thunderIn[i] < 0f) continue;
            thunderIn[i] -= dt;
            if (thunderIn[i] < 0f) host.sound(thunderId[i], thunderGain[i], thunderRate[i]);
        }
    }

    // ---- episodes ----

    /**
     * A run through an episode: its stars count when the whole episode is played from its first
     * level on, one level after another. Kept: epRun, the stars left so far; epRunOk, whether
     * the run is whole; for each episode ep<i>done and ep<i>stars, its best whole run.
     */
    private void episodeRun(int lv) {
        int plate = lv + 1;
        if (Episode.first(plate)) {
            host.saveInt("epRun", 0);
            host.saveInt("epRunOk", 1);
        } else if (lv != level && lv != level + 1) host.saveInt("epRunOk", 0);
    }

    /** A building down: its stars go to the run; the last of an episode closes it, and the light comes. */
    private void episodeWin() {
        int plate = level + 1, e = Episode.of(plate);
        int run = host.loadInt("epRun") + Math.max(0, stars);
        host.saveInt("epRun", run);
        if (!Episode.last(plate)) return;
        host.saveInt("ep" + e + "done", 1);
        if (host.loadInt("epRunOk") == 1 && run > host.loadInt("ep" + e + "stars")) host.saveInt("ep" + e + "stars", run);
        // an episode that ends in the alley goes on through the light too, until the alley is there
        if (Episode.ENDS[e] == Episode.NEW || Episode.ENDS[e] == Episode.ALLEY) chapterDue = true;
    }

    /** Whether the crane's film comes between these two levels: from the third episode into the fourth. */
    static boolean craneAfter(int lv, int next) {
        return lv + 1 == Episode.LAST[2] && next == lv + 1;
    }

    /**
     * The film between the third episode and the fourth: what was left of the building has
     * gone in the light, the plinth stands empty, and the crane comes for it. The first time
     * it brings a golden cube.
     */
    private void startCrane() {
        float from = Builder.CZ, to = Builder.BASE_CZ + Builder.farther(nextLevel);
        phys.clear();
        pieces.clear();
        toBurst.clear();
        toPop.clear();
        pN = 0;
        lN = 0;
        dropN = 0;
        hammer.ghost = true;
        hs = HAND;
        aiming = false;
        power = 0f;
        fromSky = false;
        crane = new Crane(this, from, to, host.loadInt("ins2") != 1, cubes, nextLevel);
        // the cars coming down the road keep clear of where it is going
        Builder.CZ = to;
        craneOut = false;
        crest.fresh();
        setState(INSERT);
    }

    /** For the desktop stand: the crane's film at once, as if the third episode had just been won. */
    void standCrane() {
        level = Episode.LAST[2] - 1;
        nextLevel = level + 1;
        Builder.CZ = Builder.BASE_CZ + Builder.farther(level);
        startCrane();
    }

    /** Into an episode from the bridge: its first building, through the light. */
    void enter(int lv) {
        fromSky = false;
        start(Math.max(0, Math.min(LAST, lv)), false);
        chapterT = CHAPTER_RISE;
    }

    /** How white the light between episodes makes the frame, 0 to 1. */
    float light() {
        if (chapterT < 0f) return 0f;
        if (chapterT < CHAPTER_RISE) {
            float u = chapterT / CHAPTER_RISE;
            return u * u;
        }
        float u = Math.min(1f, (chapterT - CHAPTER_RISE) / CHAPTER_FADE);
        return (1f - u) * (1f - u);
    }

    /** What is left goes in a puff, and the building rises again. */
    private void vanish() {
        for (Body b : pieces) {
            if (b.gone) continue;
            for (int i = 0; i < 3; i++) spawn(b.px, b.py, b.pz, true, 0xF4EFE4, 0.6f);
        }
        host.sound(Synth.WHOOSH, 0.6f, 0.7f);
        setState(VANISH);
    }

    // ---- the building coming down into place ----

    private float stagger() {
        return Math.min(0.05f, 1.7f / Math.max(1, pieces.size()));
    }

    private float buildDuration() {
        if (buildFromSky) {
            float end = 0f;
            for (int i = 0; i < pieces.size(); i++) end = Math.max(end, skyAppear(i) + skyFall(i));
            return end;
        }
        return stagger() * pieces.size() + 0.4f;
    }

    // pieces thrown down by the gods: they appear 15, 10 or 5 meters up and fall under a brisk pull
    private static final float SKY_G = 26f;
    private static final float[] SKY_H = {15f, 10f, 5f};

    private float skyHeight(int i) {
        return SKY_H[i % 3];
    }

    private float skyFall(int i) {
        return (float) Math.sqrt(2f * skyHeight(i) / SKY_G);
    }

    private float skyAppear(int i) {
        return 0.25f + i * Math.min(0.07f, 1.6f / Math.max(1, pieces.size()));
    }

    private int lastLanded = -1;
    private float lastBuildTime;

    private void buildSounds() {
        if (buildFromSky) {
            for (int i = 0; i < pieces.size(); i++) {
                float land = skyAppear(i) + skyFall(i);
                if (land > lastBuildTime && land <= stateTime)
                    host.sound(Synth.TOCK, 0.18f + skyHeight(i) * 0.02f, 0.75f + fx.nextFloat() * 0.5f);
            }
            lastBuildTime = stateTime;
            return;
        }
        int landed = stateTime < 0.4f ? -1 : Math.min(pieces.size() - 1, (int) ((stateTime - 0.4f) / stagger()));
        for (int i = lastLanded + 1; i <= landed; i++)
            if (i >= 0 && (i & 1) == 0) host.sound(Synth.TOCK, 0.22f, 0.8f + fx.nextFloat() * 0.5f);
        lastLanded = Math.max(lastLanded, landed);
    }

    // ---- knocks ----

    @Override
    public void knock(Body a, Body b, float speed, float x, float y, float z) {
        boolean hammerHit = a == hammer || b == hammer;
        Body other = a == hammer ? b : a;
        if (a.material == Body.CAR || (b != null && b.material == Body.CAR)) {
            carKnock(a.material == Body.CAR ? a : b, speed, x, y, z);
            return;
        }
        // the first building flies to pieces at the first touch of the hammer; so does any, at the golden one
        if ((level == 0 || goldThrow) && hammerHit && other != null && other != plinth && !blasted) {
            if (goldThrow) {
                goldOn = false;
                fails = 0;
                host.saveInt("fails", 0);
            }
            blasted = true;
            blastX = x;
            blastY = y;
            blastZ = z;
            blastNow = true;
        }
        // water bottles burst at a hard blow, or a hard landing; balloons at any touch of the hammer
        if (a.material == Body.WATER && (speed > 4.2f || hammerHit && speed > 2.4f)) burst(a);
        if (b != null && b.material == Body.WATER && (speed > 4.2f || hammerHit && speed > 2.4f)) burst(b);
        if (a.material == Body.BALLOON && (hammerHit || speed > 3f)) pop(a);
        if (b != null && b.material == Body.BALLOON && (hammerHit || speed > 3f)) pop(b);
        // a suitcase that lands hard bursts its latches (one sent flying opens in the air, see
        // flyingCases); a mattress tears under the hammer or a heavy fall
        if (a.material == Body.SUITCASE && !hammerHit && speed > 4.5f) openCase(a);
        if (b != null && b.material == Body.SUITCASE && !hammerHit && speed > 4.5f) openCase(b);
        if (a.material == Body.MATTRESS && (hammerHit && speed > 4.5f || speed > 7f)) tearMattress(a, x, y, z);
        if (b != null && b.material == Body.MATTRESS && (hammerHit && speed > 4.5f || speed > 7f)) tearMattress(b, x, y, z);
        boolean plastic = a.material == Body.WATER || (b != null && b.material == Body.WATER);
        float gain = Math.min(1f, (speed - 1f) / 5f);
        // a heavy ball knocks about a great deal, and each knock was the loudest of all: kept down
        if (a.material == Body.BALL || b != null && b.material == Body.BALL) gain *= 0.5f;
        if (hammerHit) gain = Math.min(1.3f, gain * 1.4f + 0.2f);
        int voice = voiceOf(a);
        if (voice < 0 && b != null) voice = voiceOf(b);
        if (voice >= 0) {
            if (speed > 1.2f || hammerHit) {
                float rate = voice == Synth.PLASTIC ? 1.35f : voice == Synth.CLANG ? 0.9f : 1f;
                // a light knock on a mattress is only the whump: the creak needs a real blow
                if (voice == Synth.MATTRESS && speed < 3f && !hammerHit) voice = Synth.SAND;
                host.sound(voice, gain * (voice == Synth.CLANG ? 0.85f : 1f), rate * (0.92f + fx.nextFloat() * 0.16f));
            }
        } else if (b == null || other == null) {
            if (speed > 1.6f) host.sound(Synth.GROUND, gain * 0.9f, 0.9f + fx.nextFloat() * 0.2f);
        } else if (speed > 1.2f || hammerHit) {
            boolean stone = a.material == Body.STONE || b.material == Body.STONE || other.material == Body.PLINTH
                    || a.material == Body.BALL || b.material == Body.BALL;
            float size = Math.max(other.hx, Math.max(other.hy, other.hz));
            float rate = Math.max(0.6f, Math.min(1.5f, 0.32f / size)) * (0.92f + fx.nextFloat() * 0.16f);
            host.sound(plastic ? Synth.PLASTIC : stone ? Synth.STONE : Synth.WOOD, gain, plastic ? 0.9f + fx.nextFloat() * 0.2f : rate);
        }
        if (hammerHit) host.buzz((int) Math.min(60f, 10f + speed * 3f));
        boolean soft = other != null && (other.material == Body.SUITCASE || other.material == Body.MATTRESS
                || other.material == Body.TYRE || other.material == Body.BALLOON);
        if (soft && speed > 2.2f) {
            // no chips off cloth, leather or rubber: a little grey fluff from a mattress
            if (other.material == Body.MATTRESS)
                for (int i = 0; i < 3; i++) spawn(x, y, z, true, 0xC9C5BC, 0.8f);
        } else if (speed > 2.2f && other != null && other.material != Body.WATER) {
            int n = (int) Math.min(14f, speed * 1.2f);
            boolean bag = other.material == Body.SANDBAG;
            boolean dusty = other.material == Body.STONE || other.material == Body.PLINTH || bag;
            int dust = bag ? 0xDCC79C : 0xE6DCC8;
            // a sandbag puffs more dust than it sheds chips: sand out of its weave
            for (int i = 0; i < n; i++)
                spawn(x, y, z, dusty && (bag || (i & 1) == 0), dusty ? dust : Street.scale(other.tint, 0.8f), speed * 0.35f);
        } else if (speed > 3f && b == null) {
            for (int i = 0; i < 4; i++) spawn(x, 0.02f, z, true, 0xD8CFC0, 1f);
        }
    }

    /**
     * Something hard against a parked car: a light knock only clatters; a real blow crumples
     * metal, sprays glass, makes the car one step worse and sets its alarm off.
     */
    private void carKnock(Body car, float speed, float x, float y, float z) {
        int i = 0;
        while (i < cars.length && cars[i] != car) i++;
        if (i == cars.length || traffic == null) return;
        if (speed < 2.2f) {
            host.sound(Synth.CRASH, 0.25f, 1.3f);
            return;
        }
        if (clock - carHitAt[i] < 0.5f) return;
        carHitAt[i] = clock;
        boolean wasQuiet = traffic.damage[i] == 0 || clock - carAlarmAt > 1.8f;
        traffic.hit(i);
        host.sound(Synth.CRASH, Math.min(1f, 0.4f + speed * 0.08f), 0.85f + fx.nextFloat() * 0.3f);
        host.sound(Synth.GLASS, 0.5f, 1.1f + fx.nextFloat() * 0.3f);
        if (wasQuiet) {
            int al = traffic.alarm(i);
            host.sound(al, al == Synth.CHIRP ? 0.5f : 0.4f, 0.96f + fx.nextFloat() * 0.08f);
            carAlarmAt = clock;
        }
        host.buzz(50);
        for (int k = 0; k < 12; k++) spawn(x, y, z, false, (k & 1) == 0 ? 0xCFE2EC : 0x9FB6C4, speed * 0.4f);
    }

    /** The sound of a street thing knocked about, or -1 for a plain block. */
    private static int voiceOf(Body b) {
        switch (b.material) {
            case Body.BARREL: return Synth.CLANG;
            case Body.TYRE: return Synth.RUBBER;
            case Body.SANDBAG: return Synth.SAND;
            case Body.CONE: return Synth.PLASTIC;
            case Body.SUITCASE: return Synth.LEATHER;
            case Body.MATTRESS: return Synth.MATTRESS;
            default: return -1;
        }
    }

    /** A suitcase thrown through the air faster than a brisk walk gapes open in flight. */
    private void flyingCases() {
        for (Body b : pieces)
            if (b.material == Body.SUITCASE && b.broke < 0f && !b.gone && !b.asleep && b.speed2() > 3.2f * 3.2f) openCase(b);
    }

    /** A suitcase bursts open: the latches snap, the lid gapes, shirts and postcards fly. */
    private void openCase(Body b) {
        if (b.broke >= 0f) return;
        b.broke = clock;
        int shirts = 4 + fx.nextInt(3), cards = 4 + fx.nextInt(4);
        for (int i = 0; i < shirts; i++) spawnLight(b.px, b.py + b.hy, b.pz, b.vx, b.vy, b.vz, SHIRT);
        for (int i = 0; i < cards; i++) spawnLight(b.px, b.py + b.hy, b.pz, b.vx, b.vy, b.vz, CARD);
        host.sound(Synth.LATCH, 0.9f, 0.94f + fx.nextFloat() * 0.12f);
    }

    /** A mattress torn where it was struck: springs jump out of the ticking, grey wadding puffs. */
    private void tearMattress(Body b, float x, float y, float z) {
        if (b.broke >= 0f) return;
        b.broke = clock;
        float dx = x - b.px, dy = y - b.py, dz = z - b.pz;
        float lx = b.r00 * dx + b.r10 * dy + b.r20 * dz;
        float ly = b.r01 * dx + b.r11 * dy + b.r21 * dz;
        float lz = b.r02 * dx + b.r12 * dy + b.r22 * dz;
        // onto the face it is nearest to; the thin sides read best, so the front wins ties
        float ax = Math.abs(lx) / b.hx, ay = Math.abs(ly) / b.hy * 0.7f, az = Math.abs(lz) / b.hz;
        int axis = az >= ax && az >= ay ? 2 : ax >= ay ? 0 : 1;
        float[] l = {lx, ly, lz}, hh = {b.hx, b.hy, b.hz};
        boolean neg = l[axis] < 0f;
        l[axis] = neg ? -hh[axis] : hh[axis];
        for (int k = 0; k < 3; k++) if (k != axis) l[k] = Math.max(-hh[k] * 0.6f, Math.min(hh[k] * 0.6f, l[k]));
        b.markX = l[0];
        b.markY = l[1];
        b.markZ = l[2];
        b.markAxis = axis + (neg ? 3 : 0);
        for (int i = 0; i < 4; i++) spawnLight(x, y, z, b.vx, b.vy, b.vz, SPRING);
        for (int i = 0; i < 10; i++) spawn(x, y, z, true, (i & 1) == 0 ? 0xB9B5AD : 0xD6D2C8, 1.4f);
        host.sound(Synth.TEAR, 1f, 0.95f + fx.nextFloat() * 0.1f);
    }

    /** For the desktop stand: every mattress torn at the middle of its front, every suitcase burst. */
    void standTear() {
        for (Body b : pieces) {
            if (b.material == Body.MATTRESS) tearMattress(b, b.px + b.hx * 0.3f, b.py, b.pz - b.hz);
            if (b.material == Body.SUITCASE) openCase(b);
        }
    }

    private final ArrayList<Body> toPop = new ArrayList<Body>();

    private void pop(Body b) {
        if (!b.gone && !toPop.contains(b)) toPop.add(b);
    }

    /** Balloons burst this step: gone with a crack and a spray of rubber shreds; a freed one drifts off. */
    private void pops() {
        for (Body b : pieces)
            if (b.material == Body.BALLOON && !b.gone && b.py > Builder.TOP + 25f) {
                b.gone = true;
                b.ghost = true;
            }
        if (toPop.isEmpty()) return;
        for (Body b : toPop) {
            b.gone = true;
            b.ghost = true;
            float e = 0.08f;
            for (Body o : pieces)
                if (o.asleep && !(o.maxX + e < b.minX || b.maxX + e < o.minX || o.maxY + e < b.minY
                        || b.maxY + e < o.minY || o.maxZ + e < b.minZ || b.maxZ + e < o.minZ)) phys.wake(o);
            for (int i = 0; i < 14; i++) spawn(b.px, b.py, b.pz, false, Street.scale(b.tint, 0.8f + fx.nextFloat() * 0.4f), 3.5f);
            host.sound(Synth.POP, 0.55f, 0.9f + fx.nextFloat() * 0.25f);
            host.buzz(20);
        }
        toPop.clear();
    }

    private float rollAt;

    /** A heavy ball rolling along the plinth or the street rumbles as it goes. */
    private void rolling() {
        // one rumble at a time: each runs on for a couple of seconds, and laid over one another they roar
        if (clock - rollAt < 1.7f) return;
        for (Body b : pieces) {
            if (b.material != Body.BALL || b.gone || b.asleep) continue;
            float onTop = Math.abs(b.px - Builder.CX) < Builder.PHX && Math.abs(b.pz - Builder.CZ) < Builder.PHZ ? Builder.TOP : 0f;
            if (b.py - b.radius > onTop + 0.04f || Math.abs(b.vy) > 0.6f) continue;
            float v = (float) Math.sqrt(b.vx * b.vx + b.vz * b.vz);
            if (v < 1f) continue;
            host.sound(Synth.ROLL, Math.min(0.32f, v * 0.06f), 0.8f + Math.min(0.5f, v * 0.06f));
            rollAt = clock;
            return;
        }
    }

    /** Throws every piece of the building away from the blow, after the step, never inside it. */
    private void blast() {
        if (!blastNow) return;
        blastNow = false;
        for (Body b : pieces) {
            if (b.gone) continue;
            float dx = b.px - blastX, dy = b.py - blastY + 0.6f, dz = b.pz - blastZ + 0.5f;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.2f;
            float v = 6f + fx.nextFloat() * 3.5f;
            b.vx = dx / l * v + (fx.nextFloat() - 0.5f) * 1.5f;
            b.vy = Math.abs(dy / l) * v * 0.8f + 2.5f + fx.nextFloat() * 2f;
            b.vz = dz / l * v + 1.5f;
            b.wx = (fx.nextFloat() - 0.5f) * 14f;
            b.wy = (fx.nextFloat() - 0.5f) * 14f;
            b.wz = (fx.nextFloat() - 0.5f) * 14f;
            b.asleep = false;
            b.savePrev();
            if (b.material == Body.WATER) burst(b);
            if (b.material == Body.BALLOON) pop(b);
        }
        for (int i = 0; i < 40; i++)
            spawn(blastX, blastY, blastZ, (i & 1) == 0, (i & 1) == 0 ? 0xE6DCC8 : 0xD39E62, 5f);
        host.sound(Synth.THUNDER, 0.8f, 1.3f);
        host.buzz(80);
    }

    private void burst(Body b) {
        if (!b.gone && !toBurst.contains(b)) toBurst.add(b);
    }

    /** Bottles burst this step: gone, in a spray of water that leaves a puddle where it lands. */
    private void bursts() {
        if (toBurst.isEmpty()) return;
        for (Body b : toBurst) {
            b.gone = true;
            b.ghost = true;
            // whatever leaned on it must notice it is gone
            float e = 0.05f;
            for (Body o : pieces)
                if (o.asleep && !(o.maxX + e < b.minX || b.maxX + e < o.minX || o.maxY + e < b.minY
                        || b.maxY + e < o.minY || o.maxZ + e < b.minZ || b.maxZ + e < o.minZ)) phys.wake(o);
            for (int i = 0; i < 70; i++) spawnWater(b.px, b.py, b.pz, b.vx, b.vy, b.vz);
            boolean onTop = Math.abs(b.px - Builder.CX) < Builder.PHX && Math.abs(b.pz - Builder.CZ) < Builder.PHZ
                    && b.py > Builder.TOP - 0.2f;
            puddle(b.px, onTop ? Builder.TOP : 0.002f, b.pz, 0.5f + fx.nextFloat() * 0.2f);
            if (onTop) puddle(b.px + (fx.nextFloat() - 0.5f) * 0.6f, 0.002f, Builder.CZ - Builder.PHZ - 0.35f, 0.45f);
            host.sound(Synth.SPLASH, 1.1f, 0.9f + fx.nextFloat() * 0.2f);
            host.buzz(40);
        }
        toBurst.clear();
    }

    // ---- chips, dust and water ----

    private static final int MAXP = 400;
    private final float[] pX = new float[MAXP], pY = new float[MAXP], pZ = new float[MAXP];
    private final float[] pVx = new float[MAXP], pVy = new float[MAXP], pVz = new float[MAXP];
    private final float[] pLife = new float[MAXP], pAge = new float[MAXP], pSize = new float[MAXP];
    private final int[] pCol = new int[MAXP];
    private final boolean[] pDust = new boolean[MAXP], pWet = new boolean[MAXP];
    private int pN;

    /** A chip off a block, or a puff of dust that drifts and grows thin. */
    void spawn(float x, float y, float z, boolean dust, int col, float speed) {
        if (pN >= MAXP) return;
        int i = pN++;
        pX[i] = x;
        pY[i] = y;
        pZ[i] = z;
        float a = fx.nextFloat() * 6.283f, s = speed * (0.4f + fx.nextFloat() * 0.8f);
        pVx[i] = (float) Math.cos(a) * s;
        pVz[i] = (float) Math.sin(a) * s;
        pVy[i] = s * (0.5f + fx.nextFloat()) * (dust ? 0.4f : 1f);
        pLife[i] = dust ? 0.8f + fx.nextFloat() * 0.6f : 1.2f + fx.nextFloat() * 1.4f;
        pAge[i] = 0f;
        pCol[i] = col;
        pDust[i] = dust;
        pWet[i] = false;
        pSize[i] = dust ? 0.05f + fx.nextFloat() * 0.04f : 0.018f + fx.nextFloat() * 0.022f;
    }

    private void spawnWater(float x, float y, float z, float vx, float vy, float vz) {
        if (pN >= MAXP) return;
        int i = pN++;
        pX[i] = x + (fx.nextFloat() - 0.5f) * 0.2f;
        pY[i] = y + (fx.nextFloat() - 0.5f) * 0.3f;
        pZ[i] = z + (fx.nextFloat() - 0.5f) * 0.2f;
        float a = fx.nextFloat() * 6.283f, s = 0.8f + fx.nextFloat() * 3f;
        pVx[i] = vx * 0.4f + (float) Math.cos(a) * s;
        pVz[i] = vz * 0.4f + (float) Math.sin(a) * s;
        pVy[i] = vy * 0.3f + 1f + fx.nextFloat() * 3f;
        pLife[i] = 1.5f;
        pAge[i] = 0f;
        pCol[i] = fx.nextBoolean() ? 0xBFE2F6 : 0x8CC6EE;
        pDust[i] = false;
        pWet[i] = true;
        pSize[i] = 0.015f + fx.nextFloat() * 0.02f;
    }

    private void particles(float dt) {
        for (int i = 0; i < pN; i++) {
            pAge[i] += dt;
            if (pAge[i] >= pLife[i] || (pWet[i] && pY[i] < 0f)) {
                pN--;
                pX[i] = pX[pN]; pY[i] = pY[pN]; pZ[i] = pZ[pN];
                pVx[i] = pVx[pN]; pVy[i] = pVy[pN]; pVz[i] = pVz[pN];
                pLife[i] = pLife[pN]; pAge[i] = pAge[pN]; pCol[i] = pCol[pN];
                pDust[i] = pDust[pN]; pWet[i] = pWet[pN]; pSize[i] = pSize[pN];
                i--;
                continue;
            }
            if (pDust[i]) {
                pVx[i] *= (float) Math.exp(-3f * dt);
                pVz[i] *= (float) Math.exp(-3f * dt);
                pVy[i] = pVy[i] * (float) Math.exp(-3f * dt) + 0.15f * dt;
            } else {
                pVy[i] += Physics.G * dt;
            }
            pX[i] += pVx[i] * dt;
            pY[i] += pVy[i] * dt;
            pZ[i] += pVz[i] * dt;
            if (!pDust[i] && pY[i] < pSize[i] && !pWet[i]) {
                // chips skitter to rest on the ground
                pY[i] = pSize[i];
                pVy[i] = -pVy[i] * 0.3f;
                pVx[i] *= 0.6f;
                pVz[i] *= 0.6f;
            }
        }
    }

    // ---- light things out of suitcases and mattresses ----
    // shirts and postcards thrown up, swinging down slowly and lying where they fell; springs

    static final int SHIRT = 0, CARD = 1, SPRING = 2;
    private static final int MAXL = 90;
    // the shirts packed in old suitcases: loud prints on bright grounds
    static final int[] SHIRTS = {0xE8483C, 0x2FB0A6, 0xF2C230, 0xF27A3A, 0xE87AB0, 0x3F8FD8, 0x5DBE6A};
    private final float[] lX = new float[MAXL], lY = new float[MAXL], lZ = new float[MAXL];
    private final float[] lVx = new float[MAXL], lVy = new float[MAXL], lVz = new float[MAXL];
    private final float[] lAge = new float[MAXL], lLife = new float[MAXL], lYaw = new float[MAXL], lSpin = new float[MAXL];
    private final float[] lPitch = new float[MAXL], lRoll = new float[MAXL];
    private final int[] lKind = new int[MAXL], lCol = new int[MAXL];
    private final boolean[] lDown = new boolean[MAXL];
    private int lN;

    /** Something light thrown up into the air: a shirt, a postcard, or a spring. */
    private void spawnLight(float x, float y, float z, float vx, float vy, float vz, int kind) {
        if (lN >= MAXL) return;
        int i = lN++;
        lX[i] = x;
        lY[i] = y;
        lZ[i] = z;
        float a = fx.nextFloat() * 6.283f;
        float sp = kind == SPRING ? 1.5f + fx.nextFloat() * 2.5f : 0.8f + fx.nextFloat() * 2.2f;
        lVx[i] = (float) Math.cos(a) * sp + vx * 0.4f;
        lVz[i] = (float) Math.sin(a) * sp + vz * 0.4f;
        lVy[i] = (kind == SPRING ? 2.5f : 4f) + fx.nextFloat() * 4f + Math.max(0f, vy) * 0.3f;
        lLife[i] = kind == SPRING ? 2.5f + fx.nextFloat() : 40f;
        lAge[i] = 0f;
        lYaw[i] = fx.nextFloat() * 6.283f;
        lSpin[i] = (fx.nextFloat() - 0.5f) * 3f;
        lPitch[i] = lRoll[i] = 0f;
        lKind[i] = kind;
        lCol[i] = kind == SHIRT ? SHIRTS[fx.nextInt(SHIRTS.length)] : fx.nextInt(1 << 16);
        lDown[i] = false;
    }

    private static float floorAt(float x, float z) {
        return Math.abs(x - Builder.CX) < Builder.PHX && Math.abs(z - Builder.CZ) < Builder.PHZ ? Builder.TOP : 0f;
    }

    private void lights(float dt) {
        for (int i = 0; i < lN; i++) {
            lAge[i] += dt;
            if (lAge[i] >= lLife[i]) {
                lN--;
                lX[i] = lX[lN]; lY[i] = lY[lN]; lZ[i] = lZ[lN];
                lVx[i] = lVx[lN]; lVy[i] = lVy[lN]; lVz[i] = lVz[lN];
                lAge[i] = lAge[lN]; lLife[i] = lLife[lN]; lYaw[i] = lYaw[lN]; lSpin[i] = lSpin[lN];
                lPitch[i] = lPitch[lN]; lRoll[i] = lRoll[lN];
                lKind[i] = lKind[lN]; lCol[i] = lCol[lN]; lDown[i] = lDown[lN];
                i--;
                continue;
            }
            if (lDown[i]) continue;
            float floor = floorAt(lX[i], lZ[i]);
            if (lKind[i] == SPRING) {
                // wire: falls like a stone, bounces once or twice, tumbling
                lVy[i] += Physics.G * dt;
                lX[i] += lVx[i] * dt;
                lY[i] += lVy[i] * dt;
                lZ[i] += lVz[i] * dt;
                lPitch[i] += 9f * dt;
                lYaw[i] += lSpin[i] * 2f * dt;
                if (lY[i] < floor + 0.04f) {
                    lY[i] = floor + 0.04f;
                    lVy[i] = -lVy[i] * 0.4f;
                    lVx[i] *= 0.6f;
                    lVz[i] *= 0.6f;
                }
                continue;
            }
            // cloth and paper: caught by the air at once, then sinking slowly, swinging and
            // turning over; once down they lie flat where they fell
            float thin = lKind[i] == SHIRT ? 0.016f : 0.008f;
            if (lY[i] <= floor + thin && lVy[i] <= 0f) {
                lY[i] = floor + thin;
                lDown[i] = true;
                lPitch[i] = 0f;
                lRoll[i] = 0f;
                continue;
            }
            boolean shirt = lKind[i] == SHIRT;
            float drag = shirt ? 2.8f : 2.2f;
            lVy[i] += Physics.G * (shirt ? 0.32f : 0.4f) * dt;
            float k = Math.max(0f, 1f - drag * dt);
            lVx[i] *= k;
            lVy[i] *= k;
            lVz[i] *= k;
            // the swing: a card side-slips like a leaf, a shirt billows more lazily
            float ph = lAge[i] * (shirt ? 1.9f : 4.2f) + i * 1.7f;
            float sw = shirt ? 0.6f : 1.1f;
            lX[i] += (lVx[i] + (float) Math.cos(ph) * sw) * dt;
            lY[i] += lVy[i] * dt;
            lZ[i] += (lVz[i] + (float) Math.sin(ph * 0.7f) * sw * 0.5f) * dt;
            // tipping with the swing, slowly turning over
            lPitch[i] = (float) Math.sin(ph) * (shirt ? 0.9f : 1.2f) + lAge[i] * lSpin[i] * 0.4f;
            lRoll[i] = (float) Math.cos(ph * 0.7f) * 0.5f;
            lYaw[i] += lSpin[i] * dt;
            if (lY[i] < floor + thin) lY[i] = floor + thin;
        }
    }

    private final float[] lTurn = new float[9];

    /** A turn from a heading, a tip forward and a tip sideways, row by row. */
    private static void euler(float yaw, float pitch, float roll, float[] m) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // Ry * Rx * Rz
        m[0] = cy * cr + sy * sp * sr;  m[1] = -cy * sr + sy * sp * cr;  m[2] = sy * cp;
        m[3] = cp * sr;                 m[4] = cp * cr;                  m[5] = -sp;
        m[6] = -sy * cr + cy * sp * sr; m[7] = sy * sr + cy * sp * cr;   m[8] = cy * cp;
    }

    private void drawLights(DrawList d) {
        for (int i = 0; i < lN; i++) {
            euler(lYaw[i], lPitch[i], lRoll[i], lTurn);
            if (lKind[i] == SHIRT) Things.shirt(d, lX[i], lY[i], lZ[i], lTurn, lCol[i], i);
            else if (lKind[i] == CARD) Things.card(d, lX[i], lY[i], lZ[i], lTurn, lCol[i]);
            else {
                float fade = Math.min(1f, (lLife[i] - lAge[i]) / 0.4f);
                if (fade > 0.3f) Things.looseSpring(d, lX[i], lY[i], lZ[i], lTurn);
            }
        }
    }

    // puddles where water came down: they spread, lie a while, and dry up
    private static final int DROPS = 12;
    private final float[] dX = new float[DROPS], dY = new float[DROPS], dZ = new float[DROPS], dR = new float[DROPS], dAge = new float[DROPS];
    private int dropN;
    static final float DRY = 30f;

    private void puddle(float x, float y, float z, float r) {
        int i = dropN < DROPS ? dropN++ : fx.nextInt(DROPS);
        dX[i] = x;
        dY[i] = y;
        dZ[i] = z;
        dR[i] = r;
        dAge[i] = 0f;
    }

    private void drops(float dt) {
        for (int i = 0; i < dropN; i++) {
            dAge[i] += dt;
            if (dAge[i] > DRY) {
                dropN--;
                dX[i] = dX[dropN]; dY[i] = dY[dropN]; dZ[i] = dZ[dropN]; dR[i] = dR[dropN]; dAge[i] = dAge[dropN];
                i--;
            }
        }
    }

    // ---- drawing ----

    private final float[] turn = new float[9], pose = new float[12];

    void draw(DrawList d) {
        if (crane != null) crane.draw(d);
        else if (atSea) d.box(plinth.px, plinth.py, plinth.pz, plinth.hx, plinth.hy, plinth.hz, null, Ocean.ICE, DrawList.ICE);
        else if (fight == null) d.box(plinth.px, plinth.py, plinth.pz, plinth.hx, plinth.hy, plinth.hz, null, 0xE6D8BC, DrawList.STONE);
        else fight.draw(d);
        float st = stagger();
        for (int i = 0; i < pieces.size(); i++) {
            Body b = pieces.get(i);
            if (b.gone) continue;
            float scale = 1f, drop = 0f;
            if (state == BUILD && buildFromSky) {
                // out of nothing, high above, and down into place
                float t = stateTime - skyAppear(i);
                if (t < 0f) continue;
                float fall = skyFall(i);
                if (t < fall) {
                    drop = skyHeight(i) - 0.5f * SKY_G * t * t;
                    scale = Math.min(1f, t / 0.12f);
                }
            } else if (state == BUILD) {
                // block after block, down into place out of the air just above
                float t = stateTime - 0.4f - i * st;
                if (t < -0.35f) continue;
                if (t < 0f) {
                    float k = (t + 0.35f) / 0.35f;
                    drop = (1f - k * k) * 4f;
                }
            } else if (state == VANISH) {
                float k = Math.min(1f, stateTime / 0.35f);
                scale = (1f - k) * (1f - k);
                if (scale < 0.02f) continue;
            }
            body(d, b, scale, drop);
        }
        for (int i = 0; i < dropN; i++) {
            float grow = Math.min(1f, dAge[i] / 0.5f), dry = 1f - Math.max(0f, (dAge[i] - DRY + 6f) / 6f);
            float r = dR[i] * grow * (0.4f + 0.6f * dry);
            if (r > 0.02f) Round.disc(d, dX[i], dY[i] + 0.004f, dZ[i], r, 0.003f, 0x6E8FA6, DrawList.GLASS);
        }
        for (int i = 0; i < pN; i++) {
            float s = pSize[i];
            if (pDust[i]) s *= 1f + pAge[i] / pLife[i];
            if (pDust[i] && pAge[i] > pLife[i] * 0.6f) s *= 1f - (pAge[i] / pLife[i] - 0.6f) / 0.4f;
            d.box(pX[i], pY[i], pZ[i], s, s, s, null, pCol[i], pWet[i] ? DrawList.GLASS : DrawList.PLAIN);
        }
        drawLights(d);
        if (crane == null) hammer(d);
    }

    /**
     * How far a mattress gives under what lies on it, from nothing to one: the weight of
     * whatever rests on its top face, against what it takes to press it flat.
     */
    private float sag(Body m) {
        if (m.r11 < 0.9f) return 0f;
        float load = 0f, top = m.maxY;
        for (Body o : pieces) {
            if (o == m || o.gone || o.minY < top - 0.08f || o.minY > top + 0.06f) continue;
            if (o.maxX < m.minX || o.minX > m.maxX || o.maxZ < m.minZ || o.minZ > m.maxZ) continue;
            load += o.mass;
        }
        return Math.min(1f, load / 120f);
    }

    /** One piece of the building, so big and so far above its place. */
    private void body(DrawList d, Body b, float scale, float drop) {
        turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
        turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
        turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
        if (b.material == Body.WATER) {
            Round.bottle(d, b.px, b.py + drop, b.pz, turn, b.hx * scale, b.hy * scale);
            return;
        }
        float x = b.px, y = b.py + drop, z = b.pz, hx = b.hx * scale, hy = b.hy * scale, hz = b.hz * scale;
        switch (b.material) {
            case Body.CONE:
                Round.cone(d, x, y, z, turn, hx, hy);
                return;
            case Body.BARREL:
                Things.drum(d, x, y, z, turn, hx, hy, b.tint);
                return;
            case Body.TYRE:
                Things.tyre(d, x, y, z, turn, hx, hy, b.tint);
                return;
            case Body.SANDBAG:
                Things.bag(d, x, y, z, turn, hx, hy, hz, b.tint);
                return;
            case Body.SUITCASE:
                Things.suitcase(d, x, y, z, turn, hx, hy, hz, b.tint, b.broke >= 0f ? clock - b.broke : -1f,
                        SHIRTS[(b.id * 3 + 1) % SHIRTS.length]);
                return;
            case Body.MATTRESS:
                Things.mattress(d, x, y, z, turn, hx, hy, hz, sag(b), b.broke >= 0f && scale >= 1f, b.markAxis,
                        b.markX, b.markY, b.markZ, clock - b.broke);
                return;
            case Body.BALL:
                d.ball(x, y, z, b.radius * scale, turn, b.tint, DrawList.POLISHED);
                return;
            case Body.BALLOON: {
                float r = b.radius * scale;
                d.ball(x, y, z, r, turn, b.tint, DrawList.RUBBER);
                // the knot under it, a darker twist of the same rubber
                d.box(x - turn[1] * r * 1.02f, y - turn[4] * r * 1.02f, z - turn[7] * r * 1.02f, r * 0.09f, r * 0.08f,
                        r * 0.09f, turn, Street.scale(b.tint, 0.6f), DrawList.PAINT);
                return;
            }
            default:
                break;
        }
        // out at sea: a life ring, a ship's wheel, a cap of snow, a block of old ice
        if (b.look == 1) {
            Things.lifeRing(d, x, y, z, turn, Math.min(hx, hy), hz);
            return;
        }
        if (b.look == 2) {
            Things.wheel(d, x, y, z, turn, Math.min(hx, hy), hz, b.tint);
            return;
        }
        int mat = b.look == 3 ? DrawList.GRAIN : b.icy ? DrawList.ICE : b.material == Body.STONE ? DrawList.STONE : DrawList.WOOD;
        d.box(b.px, b.py + drop, b.pz, b.hx * scale, b.hy * scale, b.hz * scale, turn, b.tint, mat);
    }

    /** The hammer: held low on the right, drawn back by the wind-up; or wherever it is now. */
    private void hammer(DrawList d) {
        if (hs == HAND) {
            float bob = (float) Math.sin(clock * 1.7f) * 0.008f;
            // held upright, head up and to the left, tilted toward the street
            // looking up into the sky, the hand drops out of sight
            // closed in, it is held as far again as the view is narrower, and so looks the same
            float m = zoomMul();
            Hammer.held(d, 0.3f, -0.7f + bob - 0.9f * tilt(), 1.35f * m, -0.42f, 0.86f, 0.28f, 0.94f, 0.2f, -0.28f, pose);
            if (aiming) pose[1] += 0.06f * m;
            // the handle runs down off the screen: move the hammer along its own axis
            pose[0] -= pose[3] * 0.05f * m;
            pose[1] -= pose[6] * 0.05f * m;
            pose[2] -= pose[9] * 0.05f * m;
            if (aiming) {
                // the wind-up draws it back and down toward the shoulder; a tired arm shakes it
                float k = power * power * (3f - 2f * power) * m;
                float vx = 0.06f * k + tremX * 0.002f * m, vy = -0.06f * k + tremY * 0.002f * m, vz = -0.07f * k;
                for (int j = 0; j < 3; j++) pose[j] += d.right[j] * vx + d.up[j] * vy + d.ahead[j] * vz;
            }
        } else {
            Hammer.of(hammer, pose);
        }
        boolean heavy = hs == HAND ? heavyOn && !goldOn : heavyThrow;
        boolean gold = hs == HAND ? goldOn : goldThrow;
        if (fight != null) {
            Hammer.draw(d, pose, Hammer.DIAMOND, 1f);
            return;
        }
        Hammer.draw(d, pose, gold ? Hammer.GOLDEN : heavy ? Hammer.HEAVY_KIND : Hammer.PLAIN, 1f);
        // the other hammer waits at the lower left, in sight and in reach of a tap
        if (heavyShown > 0 || heavy) {
            Hammer.held(d, -0.38f, -0.88f - 0.9f * tilt(), 1.65f * zoomMul(), 0.5f, 0.8f, 0.2f, 0.94f, -0.2f, 0.28f, other);
            Hammer.draw(d, other, !heavy, 0.62f);
        }
    }

    private final float[] other = new float[12];

    /** The flat layer: the flight the throw would take, in dots, and the ring with its wind-up. */
    void flat(Px f) {
        if (crane != null) {
            crane.flat(f);
            return;
        }
        float k = tilt();
        if (k > 0f && sky != null) {
            // the gods' strip comes down from above as the eye goes up
            float shift = k * fh - fh;
            sky.drawFar(f, shift);
            sky.drawClouds(f, shift);
            if (giftShown && state == SKY_HOLD && stateTime > 0.9f) drawGift(f, stateTime - 0.9f);
        }
        // the corner, while the street is in sight
        if (fight != null && state == FIGHT) {
            // the corner once the street is in sight, as the view comes down into it
            boolean seen = k * fh < fh / 8f;
            fight.veil(f);
            if (seen) {
                fight.marks(f, aiming, aimX, aimY);
                fight.hud(f);
            }
            if (!fight.won && fight.end >= 0f) shade(f, Math.min(1f, fight.end / 0.9f));
            return;
        }
        if (state == PORTAL) drawPortal(f);
        if (k * fh < fh / 8f) hud(f);
        if (wake >= 0f) lids(f);
        if (!aiming) return;
        float speed = throwSpeed() * (state == PORTAL ? 2.2f : 1f);
        target(aimX + tremX, aimY + tremY, tmp);
        float tx = tmp[0], ty = tmp[1], tz = tmp[2];
        handPoint(tmp);
        float sx = tmp[0], sy = tmp[1], sz = tmp[2];
        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        float t = flight((float) Math.sqrt(dx * dx + dy * dy + dz * dz), speed);
        float vx = dx / t, vy = dy / t - 0.5f * Physics.G * t, vz = dz / t;
        for (int i = 3; i < 22; i++) {
            float q = t * i / 22f;
            if (!project(sx + vx * q, sy + vy * q + 0.5f * Physics.G * q * q, sz + vz * q, scr)) continue;
            int c = (i & 1) == 0 ? 0xFFFFF6D8 : 0xFFF2C14E;
            int x = Math.round(scr[0]), y = Math.round(scr[1]);
            f.set(x - 1, y - 1, c);
            f.set(x, y - 1, c);
            f.set(x - 1, y, c);
            f.set(x, y, c);
        }
        ringAt(f);
    }

    /** The ring, and round it the wind-up: a band filling clockwise from the top, flickering when full. */
    private void ringAt(Px f) {
        int cx = Math.round(aimX + tremX), cy = Math.round(aimY + tremY);
        ring(f, cx, cy, 6, 0xFFFFF6D8);
        ring(f, cx, cy, 5, 0xFFF2B33A);
        // the wind-up: a band round the ring fills clockwise from the top, and flickers when full
        boolean full = power >= 1f;
        int band = full ? (((int) (clock * 8f) & 1) == 0 ? 0xFFFFFFFF : 0xFFFFE27A) : 0xFFFFC93A;
        int n = Math.round(power * 64f);
        for (int a = 0; a < n; a++) {
            double ang = -Math.PI / 2 + a * Math.PI * 2 / 64;
            for (int r = 8; r <= 9; r++)
                f.set(cx + (int) Math.round(Math.cos(ang) * r), cy + (int) Math.round(Math.sin(ang) * r), band);
        }
    }

    /** The whole frame gone so far into the dark. */
    private static void shade(Px f, float k) {
        int c = ((int) (Math.min(1f, k) * 255f) << 24);
        for (int i = 0; i < f.a.length; i++) f.a[i] = c;
    }

    /**
     * Waking on the street after a fight lost: dark at first, then the lids part, from above
     * and below, soft at their edges.
     */
    private void lids(Px f) {
        float k = Math.min(1f, wake / 1.6f);
        float open = k * k * (3f - 2f * k);
        float dark = Math.max(0f, 1f - wake / 0.5f) * 0.8f;
        int lid = Math.round(fh * 0.5f * (1f - open));
        for (int y = 0; y < fh; y++) {
            float e = y < lid || y >= fh - lid ? 1f : 0f;
            int edge = Math.min(Math.abs(y - lid), Math.abs(fh - lid - y));
            if (e == 0f && edge < 30) e = (30 - edge) / 30f * (1f - open);
            e = Math.max(e, dark);
            if (e <= 0f) continue;
            int c = (int) (Math.min(1f, e) * 255f) << 24;
            for (int x = 0; x < fw; x++) f.a[y * fw + x] = c;
        }
    }

    private static void ring(Px f, int cx, int cy, int r, int c) {
        for (int a = 0; a < 48; a++) {
            double t = a * Math.PI * 2 / 48;
            f.set(cx + (int) Math.round(Math.cos(t) * r), cy + (int) Math.round(Math.sin(t) * r), c);
        }
    }

    // ---- the corner ----

    /** The new marks drop in once the view is back down from the sky where the gift was seen. */
    private void marksStep(float dt) {
        if (heavyShown > heavyLeft) heavyShown = heavyLeft;
        if (heavyShown == heavyLeft) {
            markT = -1f;
            // the gift has come down and its marks are in the corner: now it is in the hand
            if (heavyDue && state != SKY_UP && state != SKY_HOLD && tilt() * fh < fh / 8f) {
                heavyDue = false;
                if (heavyLeft > 0 && !heavyOn) {
                    heavyOn = true;
                    host.sound(Synth.CATCH, 0.8f, 0.75f);
                    host.buzz(45);
                }
            }
            return;
        }
        if (markT < 0f) {
            if (state == SKY_UP || state == SKY_HOLD || tilt() * fh >= fh / 8f) return;
            markT = 0f;
            marksLanded = 0;
        }
        markT += dt;
        int n = heavyLeft - heavyShown;
        while (marksLanded < n && markT >= marksLanded * MARK_GAP + MARK_FALL) {
            host.sound(Synth.CATCH, 0.45f, 1.05f + marksLanded * 0.14f);
            host.buzz(12);
            marksLanded++;
        }
        if (markT >= (n - 1) * MARK_GAP + MARK_FALL + 0.45f) {
            heavyShown = heavyLeft;
            markT = -1f;
        }
    }

    /** The crest as a level about to open shows it: whole stars, nothing down yet, so many cubes in the rack. */
    void crestFor(Px f, int lv, int cubesShown) {
        Crest c = crest;
        c.level = lv;
        c.stars = STARS;
        c.cubes = cubesShown;
        c.heavyShown = heavyShown;
        c.heavyLeft = heavyLeft;
        c.heavyOn = heavyOn;
        c.goldOn = false;
        c.won = false;
        c.knocked = 0f;
        c.markT = -1f;
        c.draw(f);
    }

    /** The crest at the top, with what it has to show this frame. */
    private void hud(Px f) {
        Crest c = crest;
        c.level = level;
        c.stars = stars;
        c.cubes = cubes;
        c.heavyShown = heavyShown;
        c.heavyLeft = heavyLeft;
        c.heavyOn = heavyOn && !goldOn;
        c.goldOn = goldOn && hs == HAND || goldThrow;
        c.won = state == CLEARED;
        c.knocked = 1f - onPlinth();
        c.markT = markT;
        c.draw(f);
    }

    // ---- the gift in the sky ----
    // The heavy hammer drops from the thunderer's cloud and tumbles down past the gods toward
    // the street, a glint on its head. Its fall is new each time.

    private float giftT0 = -1f, giftX, giftY, giftVx, giftVy, giftG, giftS, giftAng, giftSpin, giftSway, giftPhase;
    private final Random giftRnd = new Random(0x61F7L);
    private final float[] godAt = new float[2];

    private void planGift() {
        Random r = giftRnd;
        giftT0 = 0f;
        sky.godAt(Sky.THUNDERER, 0f, godAt);
        int gw = Math.max(40, sky.godW(Sky.THUNDERER)), gh = Math.max(30, sky.godH(Sky.THUNDERER));
        giftX = godAt[0] + (r.nextFloat() - 0.5f) * gw * 0.6f;
        giftY = godAt[1] + gh * (0.1f + r.nextFloat() * 0.25f);
        // a near one is bigger and falls faster across the screen
        float near = r.nextFloat();
        giftS = 17f + near * 15f;
        giftG = 300f + near * 260f + r.nextFloat() * 80f;
        // tossed toward the middle of the sky more often than away from it, sometimes up first
        float toMid = Math.signum(fw * 0.5f - giftX);
        giftVx = (r.nextFloat() < 0.75f ? toMid : -toMid) * (15f + r.nextFloat() * 70f);
        giftVy = -60f + r.nextFloat() * 110f;
        giftAng = r.nextFloat() * 6.28f;
        giftSpin = (r.nextBoolean() ? 1f : -1f) * (3f + r.nextFloat() * 5f);
        giftSway = r.nextFloat() * 9f;
        giftPhase = r.nextFloat() * 6.28f;
    }

    private void drawGift(Px f, float t) {
        if (giftT0 < 0f) planGift();
        float cx = giftX + giftVx * t + giftSway * (float) Math.sin(t * 2.3f + giftPhase);
        float cy = giftY + giftVy * t + 0.5f * giftG * t * t;
        if (cy > fh + 40 || cx < -40 || cx > fw + 40) return;
        float ang = giftAng + t * giftSpin, s = giftS;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        int r = (int) (s * 1.1f) + 2;
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++) {
                // into the hammer's own frame: u along the handle, v across
                float u = (x * ca + y * sa) / s, v = (-x * sa + y * ca) / s;
                int c = 0;
                if (u > 0.2f && u < 0.62f && Math.abs(v) < 0.34f) {
                    c = (u > 0.27f && u < 0.32f) || (u > 0.5f && u < 0.55f)
                            ? (diamondGift ? 0xFFFFFFFF : goldGift ? 0xFFFFF0B0 : 0xFFC08A3C)
                            : diamondGift ? 0xFFBFEFFF : goldGift ? 0xFFE8B83A : 0xFF4A484E;
                    if (v < -0.2f) c = Px.scale(c, 1.35f);
                } else if (u > -0.75f && u <= 0.2f && Math.abs(v) < 0.08f) {
                    c = v < 0f ? 0xFF8A5A32 : 0xFF6A4226;
                }
                if (c != 0) f.set(Math.round(cx) + x, Math.round(cy) + y, c);
            }
        if (((int) (t * 10f) & 3) == 0) {
            int gx = Math.round(cx + ca * s * 0.45f), gy = Math.round(cy + sa * s * 0.45f);
            f.set(gx, gy, 0xFFFFFFFF);
            f.set(gx - 1, gy, 0xFFFFF0C0);
            f.set(gx + 1, gy, 0xFFFFF0C0);
            f.set(gx, gy - 1, 0xFFFFF0C0);
            f.set(gx, gy + 1, 0xFFFFF0C0);
        }
    }

    // ---- the portal ----
    // When the 101st building is down and the gods have looked, no new building comes: a ring
    // of glowing cloud gathers over the plinth, and through it shows raw winter, grey sky and
    // a dark sea in a blizzard, snow streaming out of it. Throws cost nothing now. A hammer
    // through the ring pulls the view in after it, the light rises, and the next level is out
    // on the ocean.

    static final float PORTAL_H = 3.9f, PORTAL_RISE = 1.6f;
    private float portalIn = -1f;            // seconds since the hammer went through, or -1
    private float prevHX, prevHY, prevHZ, snowOut;
    // the ring as painted: premultiplied colors, and how much of each pixel is the way through
    private int[] ringCol;
    private byte[] ringIn;
    private int ringW, ringH;
    private float holeU = 0.5f, holeV = 0.42f, holeRU = 0.36f, holeRV = 0.29f;   // as shares of the picture

    private void readPortal(byte[] b) {
        if (b == null || b.length < 4) return;
        int ww = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), hh = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if (b.length < 4 + ww * hh * 5) return;
        ringCol = new int[ww * hh];
        ringIn = new byte[ww * hh];
        int x0 = ww, x1 = 0, y0 = hh, y1 = 0;
        for (int k = 0, p = 4; k < ww * hh; k++, p += 5) {
            ringCol[k] = ((b[p + 3] & 0xFF) << 24) | ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
            ringIn[k] = b[p + 4];
            if ((b[p + 4] & 0xFF) > 128) {
                int x = k % ww, y = k / ww;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
            }
        }
        ringW = ww;
        ringH = hh;
        if (x1 > x0 && y1 > y0) {
            holeU = (x0 + x1 + 1) * 0.5f / ww;
            holeV = (y0 + y1 + 1) * 0.5f / hh;
            holeRU = (x1 - x0 + 1) * 0.5f / ww;
            holeRV = (y1 - y0 + 1) * 0.5f / hh;
        }
    }

    private float portalW() {
        return ringH > 0 ? PORTAL_H * ringW / ringH : PORTAL_H * 0.67f;
    }

    /** The middle of the picture, in the air over the plinth. */
    private float portalMid() {
        return Builder.TOP + 0.05f + PORTAL_H * 0.5f;
    }

    /** The middle of the way through. */
    private float portalY() {
        return portalMid() + (0.5f - holeV) * PORTAL_H;
    }

    private float portalX() {
        return Builder.CX + (holeU - 0.5f) * portalW();
    }

    private void openPortal() {
        for (Body b : pieces)
            if (!b.gone) {
                b.gone = true;
                b.ghost = true;
            }
        stars = STARS;
        throwsMade = 0;
        portalIn = -1f;
        pN = 0;
        setState(PORTAL);
        aimedBefore = false;
        host.sound(Synth.ROLL, 0.8f, 0.7f);
    }

    private void portalStep(float dt) {
        if (portalIn >= 0f) {
            portalIn += dt;
            // the light has risen: out on the ice
            if (chapterT >= CHAPTER_RISE) start(SEA_FROM, false);
            return;
        }
        // snow blows out of it toward the player
        if (stateTime > PORTAL_RISE && (snowOut -= dt) <= 0f) {
            snowOut = 0.04f;
            float a = fx.nextFloat() * 6.283f, r = fx.nextFloat();
            spawn(portalX() + (float) Math.cos(a) * r * holeRU * portalW(), portalY() + (float) Math.sin(a) * r * holeRV * PORTAL_H,
                    Builder.CZ - 0.2f, true, 0xF4F8FA, 2.2f);
        }
        if (hs == FLY) {
            // through the ring: the hammer crossed its plane inside the way through this frame
            float z0 = prevHZ - Builder.CZ, z1 = hammer.pz - Builder.CZ;
            if (hsTime > 0.05f && z0 < 0f && z1 >= 0f) {
                float k = z0 / (z0 - z1);
                float x = (prevHX + (hammer.px - prevHX) * k - portalX()) / (holeRU * portalW());
                float y = (prevHY + (hammer.py - prevHY) * k - portalY()) / (holeRV * PORTAL_H);
                if (x * x + y * y < 0.85f) {
                    portalIn = 0f;
                    chapterT = 0f;
                    hammer.ghost = true;
                    hammer.vx = hammer.vy = hammer.vz = 0f;
                    hammer.px = 0f;
                    hammer.py = -50f;
                    host.sound(Synth.WHOOSH, 1.1f, 0.6f);
                    host.sound(Synth.THUNDER, 0.7f, 1.6f);
                    host.buzz(120);
                }
            }
        }
        prevHX = hammer.px;
        prevHY = hammer.py;
        prevHZ = hammer.pz;
    }

    /** The cloud ring gathering over the plinth, and the winter seen through it, on the flat layer. */
    private void drawPortal(Px f) {
        if (ringCol == null) return;
        float t = stateTime - 0.5f;
        float open = Math.max(0f, Math.min(1f, t / 1.1f));
        if (open <= 0f) return;
        float e = 1f - (1f - open) * (1f - open) * (1f - open);
        float wx = Builder.CX, wy = portalMid(), wz = Builder.CZ;
        float vz = (wx - eye[0]) * ahead[0] + (wy - eye[1]) * ahead[1] + (wz - eye[2]) * ahead[2];
        if (vz < 0.5f || !project(wx, wy, wz, scr)) return;
        float cx = scr[0], cy = scr[1];
        // grows out of a puff, breathes, sways a little
        float size = (0.55f + 0.45f * e) * (1f + 0.018f * (float) Math.sin(clock * 1.3f));
        float px = PORTAL_H / ringH * focal / vz * size;            // frame pixels per picture pixel
        float rot = 0.035f * (float) Math.sin(clock * 0.55f), c = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float half = 0.5f * (float) Math.hypot(ringW, ringH) * px;
        int x0 = Math.max(0, (int) (cx - half)), x1 = Math.min(fw - 1, (int) (cx + half));
        int y0 = Math.max(0, (int) (cy - half)), y1 = Math.min(fh - 1, (int) (cy + half));
        float inv = 1f / px;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float u = (c * dx + sn * dy) * inv + ringW * 0.5f, v = (-sn * dx + c * dy) * inv + ringH * 0.5f;
                int iu = (int) u, iv = (int) v;
                if (u < 0f || v < 0f || iu >= ringW || iv >= ringH) continue;
                int q = iv * ringW + iu;
                int col = ringCol[q], hole = ringIn[q] & 0xFF;
                float ca = (col >>> 24) / 255f * e;
                // the way through: winter, as much as the picture says
                float ha = hole / 255f * e;
                int base = 0;
                if (ha > 0f) {
                    float hu = (u / ringW - holeU) / holeRU, hv = (v / ringH - holeV) / holeRV;
                    base = winterAt(hu, hv, x, y);
                }
                // the cloud over the winter: premultiplied, so its own color is already weighed by its cover
                float a = ca + ha * (1f - ca);
                if (a <= 0.004f) continue;
                float r = (((col >> 16) & 0xFF) * e + ((base >> 16) & 0xFF) * ha * (1f - ca)) / a;
                float g = (((col >> 8) & 0xFF) * e + ((base >> 8) & 0xFF) * ha * (1f - ca)) / a;
                float b = ((col & 0xFF) * e + (base & 0xFF) * ha * (1f - ca)) / a;
                f.a[y * fw + x] = (Math.min(255, Math.round(a * 255f)) << 24) | (Math.min(255, Math.round(r)) << 16)
                        | (Math.min(255, Math.round(g)) << 8) | Math.min(255, Math.round(b));
            }
    }

    /**
     * Winter through the ring, at a point of it (-1..1 across the way through): a raw grey
     * sky, a dark sea under it, fog blowing across, and snow streaming out at the viewer.
     */
    private int winterAt(float u, float v, int sx, int sy) {
        float horizon = 0.28f;
        int c = v < horizon ? Px.mix(0xFF56636F, 0xFFA9B4BD, Math.max(0f, (v + 1f) / (horizon + 1f)))
                : Px.mix(0xFF4A5E6E, 0xFF2A3A48, Math.min(1f, (v - horizon) * 2.2f));
        if (v > horizon && v < horizon + 0.05f) c = Px.mix(c, 0xFFB8C4CC, 0.6f);
        float t = clock;
        float fog = noise(u * 2.2f - t * 1.4f, v * 3f + t * 0.2f);
        c = Px.mix(c, 0xFFC4CDD4, Math.max(0f, fog - 0.4f) * 0.8f);
        // snow coming out of it: flakes on rays from the middle, rushing outward
        float r = (float) Math.sqrt(u * u + v * v) + 0.02f;
        float a = (float) Math.atan2(v, u);
        int ring = (int) Math.floor((float) Math.log(r) * 7f - t * 5f);
        int spoke = (int) Math.floor(a * 9f + ring * 0.37f);
        if (Street.nz(spoke, ring, 5) > 236) c = Px.mix(c, 0xFFFFFFFF, Math.min(1f, r * 1.3f));
        // and streaks blown sideways
        if (Street.nz((int) Math.floor(sx * 0.25f - t * 24f), sy, 9) > 247) c = Px.mix(c, 0xFFF4F8FA, 0.7f);
        return c;
    }

    /** Smooth value noise, 0 to 1. */
    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = Street.nz(ix, iy, 3) / 255f, b = Street.nz(ix + 1, iy, 3) / 255f;
        float c = Street.nz(ix, iy + 1, 3) / 255f, d = Street.nz(ix + 1, iy + 1, 3) / 255f;
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    // ---- for the desktop stand ----

    /** For the stand: a throw by the fight's rules at a frame point, if the hand is free. */
    void standFightThrow(float x, float y) {
        if (fight == null || !ready()) return;
        fight.throwAt(x, y);
    }

    /** Where on the frame the middle of the building is now, or null. */
    float[] standMiddle() {
        buildingCenter();
        return new float[]{aimX, aimY};
    }
}
