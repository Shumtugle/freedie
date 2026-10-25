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
 * The first building flies to pieces at the first touch of the hammer. Knock three quarters
 * of a building's weight off the plinth and, once things are still, what is left goes in a
 * puff and the building rises again, block by block.
 *
 * Plain Java: the world calls touch(), update() and draw() from its one thread, and says
 * where the eye is with frame() before any of them needs it.
 */
final class Game implements Physics.Listener {
    static final float CLEAR = 0.75f;          // share of the weight to knock off the plinth
    static final int BUILD = 0, PLAY = 1, CLEARED = 2, VANISH = 3;
    static final int HAND = 0, FLY = 1, BACK = 2;
    static final float CHARGE = 0.9f;          // seconds of stillness to full strength
    static final int DOWN = World.DOWN, MOVE = World.MOVE, UP = World.UP;

    private final World.Host host;
    final Physics phys = new Physics();
    private Body plinth;
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
    private final float[] eye = new float[3], right = new float[3], up = new float[3], ahead = new float[3];
    private float focal = 1f;
    private int fw = World.W, fh = 640;
    private boolean seen;

    Game(World.Host host, int level) {
        this.host = host;
        this.level = level;
        phys.listener = this;
        hammer = new Body(0.42f, 0.08f, 0.17f, Body.HAMMER, 0xD9A066);
        start(level);
    }

    // ---- the level ----

    static long seedOf(int level) {
        return level * 2654435761L ^ 0x2545F4914F6CDD1DL;
    }

    /** Raises the building for a level: tested for standing still, asleep, ready to fall. */
    void start(int lv) {
        level = lv;
        Builder.CZ = Builder.BASE_CZ;
        Builder.CX = 0f;
        Builder.PHZ = Builder.PHZ1;
        phys.clear();
        pieces.clear();
        plinth = new Body(Builder.PHX, Builder.TOP * 0.5f, Builder.PHZ, Body.PLINTH, 0xE6D8BC);
        plinth.setPose(Builder.CX, Builder.TOP * 0.5f, Builder.CZ, 1, 0, 0, 0);
        phys.add(plinth);
        // the parked cars and the cones, as solid as the plinth: what falls on them stays off the road
        for (int i = 0; i < Traffic.PARK_X.length; i++) {
            Body car = new Body(0.95f, 0.65f, 2.3f, Body.CAR, 0);
            car.setPose(Traffic.PARK_X[i], 0.65f, Traffic.PARK_Z[i], 1, 0, 0, 0);
            phys.add(car);
        }
        ArrayList<Body> made = buildingFor(lv);
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
        blasted = blastNow = false;
        toBurst.clear();
        pN = 0;
        dropN = 0;
        lastLanded = -1;
        setState(BUILD);
    }

    /** A building for a level, tested and ready: the first seeds that stand still on their own. */
    static ArrayList<Body> buildingFor(int lv) {
        Builder builder = new Builder();
        long sd = seedOf(lv);
        for (int attempt = 0; attempt < 6; attempt++) {
            ArrayList<Body> made = builder.build(lv, sd + attempt * 7919L);
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

    /** Whether the hammer is in hand and a finger may aim. */
    boolean ready() {
        return state == PLAY && hs == HAND;
    }

    /** Whether the hand is free for something else: the hammer in it, no aim begun. */
    boolean handFree() {
        return hs == HAND && !aiming;
    }

    void touch(int type, float x, float y) {
        if (type == DOWN) {
            if (!ready() || !seen) return;
            aiming = true;
            lastX = x;
            lastY = y;
            if (!aimedBefore) buildingCenter();
            anchorX = x;
            anchorY = y;
            movedAt = clock;
            power = 0f;
            fatigue = 0f;
            fullFelt = false;
            return;
        }
        if (!aiming) return;
        aimX = Math.max(2f, Math.min(fw - 3f, aimX + x - lastX));
        aimY = Math.max(2f, Math.min(fh - 3f, aimY + y - lastY));
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
            aimedBefore = true;
            throwHammer();
        }
    }

    /** Lets a finger go without a throw: the room took it, or the game was put away. */
    void cancel() {
        aiming = false;
        power = 0f;
    }

    /** Where the ring starts on a level's first throw: the middle of what stands on the plinth. */
    private void buildingCenter() {
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
        return 8f + power * 5.5f;
    }

    /** Seconds in the air over a distance; far off, the arm puts more into it. */
    static float flight(float dist, float speed) {
        float reach = dist > 42f ? (float) Math.sqrt(dist / 42f) : 1f;
        return Math.max(0.18f, dist / (speed * reach));
    }

    private void windUp(float dt) {
        if (!aiming) {
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
        float speed = throwSpeed();
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
        hammer.wy = spinSign * 15f;
        hammer.wx = 0.6f;
        hammer.savePrev();
        flightTime = t;
        hs = FLY;
        hsTime = 0f;
        power = 0f;
        host.sound(Synth.WHOOSH, 0.9f, 0.9f + speed / 60f);
        host.buzz(12);
    }

    // ---- the eye ----

    /** The eye as the world has just set it: kept, so a finger can be turned into a ray. */
    void frame(DrawList d) {
        System.arraycopy(d.eye, 0, eye, 0, 3);
        System.arraycopy(d.right, 0, right, 0, 3);
        System.arraycopy(d.up, 0, up, 0, 3);
        System.arraycopy(d.ahead, 0, ahead, 0, 3);
        focal = d.focal;
        fw = d.w;
        fh = d.h;
        seen = true;
    }

    private boolean project(float x, float y, float z, float[] out) {
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
    private void ray(float sx, float sy, float[] out) {
        float u = (sx - fw * 0.5f) / focal, v = (fh * 0.5f - sy) / focal;
        for (int k = 0; k < 3; k++) out[k] = ahead[k] + right[k] * u + up[k] * v;
    }

    /** The world point under a frame point: the nearest piece, the plinth, or the plane through it. */
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
    private void handPoint(float[] out) {
        float vx = 0.3f, vy = -0.7f + (float) Math.sin(clock * 1.7f) * 0.008f, vz = 1.35f;
        for (int k = 0; k < 3; k++) out[k] = eye[k] + right[k] * vx + up[k] * vy + ahead[k] * vz;
    }

    // ---- the clock ----

    void update(float dt) {
        clock += dt;
        stateTime += dt;
        acc += dt;
        int steps = 0;
        while (acc >= 1f / 60f && steps < 2) {
            acc -= 1f / 60f;
            steps++;
            if (state != BUILD && state != VANISH) phys.step(1f / 60f);
            bursts();
            blast();
        }
        if (acc > 1f / 30f) acc = 0f;
        particles(dt);
        drops(dt);
        windUp(dt);
        hammerMoves(dt);
        switch (state) {
            case BUILD:
                buildSounds();
                if (stateTime > buildDuration() + 0.15f) setState(PLAY);
                break;
            case PLAY:
                if (1f - onPlinth() >= CLEAR) {
                    if (!blasted) host.sound(Synth.THUNDER, 1.1f, 1f);
                    setState(CLEARED);
                }
                break;
            case CLEARED:
                if (stateTime > 2.2f && hs == HAND && (phys.settled() || stateTime > 4.5f)) vanish();
                break;
            case VANISH:
                if (stateTime > 0.5f) start(level);
                break;
            default:
        }
    }

    private void hammerMoves(float dt) {
        hsTime += dt;
        if (hs == FLY) {
            if (hsTime > flightTime + 0.6f && (hammer.speed2() < 4f || hammer.py < 0.4f || hammer.pz > Builder.CZ + 4f)
                    || hsTime > flightTime + 2.2f || hammer.pz > Builder.CZ + 16f) beginReturn();
        } else if (hs == BACK) {
            float k = Math.min(1f, hsTime / 0.6f);
            handPoint(tmp);
            float e = k * k * (3f - 2f * k);
            hammer.px = retX + (tmp[0] - retX) * e;
            hammer.py = retY + (tmp[1] - retY) * e + (float) Math.sin(Math.PI * k) * 1.2f;
            hammer.pz = retZ + (tmp[2] - retZ) * e;
            hammer.rotateBy(0f, spinSign * 14f * dt, 0f);
            hammer.updateR();
            if (k >= 1f) {
                host.sound(Synth.CATCH, 0.8f, 1f);
                host.buzz(25);
                hs = HAND;
                hsTime = 0f;
            }
        }
    }

    private void beginReturn() {
        hammer.ghost = true;
        hammer.vx = hammer.vy = hammer.vz = hammer.wx = hammer.wy = hammer.wz = 0f;
        retX = hammer.px;
        retY = hammer.py;
        retZ = hammer.pz;
        hs = BACK;
        hsTime = 0f;
        host.sound(Synth.WHOOSH, 0.45f, 1.25f);
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
        return stagger() * pieces.size() + 0.4f;
    }

    private int lastLanded = -1;

    private void buildSounds() {
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
            if (speed > 2.2f) host.sound(Synth.CRASH, Math.min(0.6f, speed / 12f), 1.2f);
            return;
        }
        // the first building flies to pieces at the first touch of the hammer
        if (level == 0 && hammerHit && other != null && other != plinth && !blasted) {
            blasted = true;
            blastX = x;
            blastY = y;
            blastZ = z;
            blastNow = true;
        }
        // water bottles burst at a hard blow, or a hard landing
        if (a.material == Body.WATER && (speed > 4.2f || hammerHit && speed > 2.4f)) burst(a);
        if (b != null && b.material == Body.WATER && (speed > 4.2f || hammerHit && speed > 2.4f)) burst(b);
        boolean plastic = a.material == Body.WATER || (b != null && b.material == Body.WATER);
        float gain = Math.min(1f, (speed - 1f) / 5f);
        if (hammerHit) gain = Math.min(1.3f, gain * 1.4f + 0.2f);
        if (b == null || other == null) {
            if (speed > 1.6f) host.sound(Synth.GROUND, gain * 0.9f, 0.9f + fx.nextFloat() * 0.2f);
        } else if (speed > 1.2f || hammerHit) {
            boolean stone = a.material == Body.STONE || b.material == Body.STONE || other.material == Body.PLINTH;
            float size = Math.max(other.hx, Math.max(other.hy, other.hz));
            float rate = Math.max(0.6f, Math.min(1.5f, 0.32f / size)) * (0.92f + fx.nextFloat() * 0.16f);
            host.sound(plastic ? Synth.PLASTIC : stone ? Synth.STONE : Synth.WOOD, gain, plastic ? 0.9f + fx.nextFloat() * 0.2f : rate);
        }
        if (hammerHit) host.buzz((int) Math.min(60f, 10f + speed * 3f));
        if (speed > 2.2f && other != null && other.material != Body.WATER) {
            int n = (int) Math.min(14f, speed * 1.2f);
            boolean dusty = other.material == Body.STONE || other.material == Body.PLINTH;
            for (int i = 0; i < n; i++)
                spawn(x, y, z, dusty && (i & 1) == 0, dusty ? 0xE6DCC8 : Street.scale(other.tint, 0.8f), speed * 0.35f);
        } else if (speed > 3f && b == null) {
            for (int i = 0; i < 4; i++) spawn(x, 0.02f, z, true, 0xD8CFC0, 1f);
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
    private void spawn(float x, float y, float z, boolean dust, int col, float speed) {
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
        d.box(plinth.px, plinth.py, plinth.pz, plinth.hx, plinth.hy, plinth.hz, null, 0xE6D8BC, DrawList.STONE);
        float st = stagger();
        for (int i = 0; i < pieces.size(); i++) {
            Body b = pieces.get(i);
            if (b.gone) continue;
            float scale = 1f, drop = 0f;
            if (state == BUILD) {
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
        hammer(d);
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
        int mat = b.material == Body.STONE ? DrawList.STONE : DrawList.WOOD;
        d.box(b.px, b.py + drop, b.pz, b.hx * scale, b.hy * scale, b.hz * scale, turn, b.tint, mat);
    }

    /** The hammer: held low on the right, drawn back by the wind-up; or wherever it is now. */
    private void hammer(DrawList d) {
        if (hs == HAND) {
            float bob = (float) Math.sin(clock * 1.7f) * 0.008f;
            // held upright, head up and to the left, tilted toward the street
            Hammer.held(d, 0.3f, -0.7f + bob, 1.35f, -0.42f, 0.86f, 0.28f, 0.94f, 0.2f, -0.28f, pose);
            if (aiming) pose[1] += 0.06f;
            // the handle runs down off the screen: move the hammer along its own axis
            pose[0] -= pose[3] * 0.05f;
            pose[1] -= pose[6] * 0.05f;
            pose[2] -= pose[9] * 0.05f;
            if (aiming) {
                // the wind-up draws it back and down toward the shoulder; a tired arm shakes it
                float k = power * power * (3f - 2f * power);
                float vx = 0.06f * k + tremX * 0.002f, vy = -0.06f * k + tremY * 0.002f, vz = -0.07f * k;
                for (int j = 0; j < 3; j++) pose[j] += d.right[j] * vx + d.up[j] * vy + d.ahead[j] * vz;
            }
        } else {
            Hammer.of(hammer, pose);
        }
        Hammer.draw(d, pose);
    }

    /** The flat layer: the flight the throw would take, in dots, and the ring with its wind-up. */
    void flat(Px f) {
        if (!aiming) return;
        float speed = throwSpeed();
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

    private static void ring(Px f, int cx, int cy, int r, int c) {
        for (int a = 0; a < 48; a++) {
            double t = a * Math.PI * 2 / 48;
            f.set(cx + (int) Math.round(Math.cos(t) * r), cy + (int) Math.round(Math.sin(t) * r), c);
        }
    }

    // ---- for the desktop stand ----

    /** Where on the frame the middle of the building is now, or null. */
    float[] standMiddle() {
        buildingCenter();
        return new float[]{aimX, aimY};
    }
}
