package com.shumtugle.freedie;

import java.util.ArrayList;
import java.util.Random;

/**
 * The alley: bowling on the street behind the player, at the crossroads before the 21st. She
 * waits in the road there; a touch on her and she sinks into herself and bursts, her blocks
 * flying in arcs to stand up as a figure: a triangle of pillars, a wall, a gate, a bending row
 * of slabs, a stepped pyramid. The player has three heavy balls for it, rolled with a swipe up
 * the screen; knock most of it down and the blocks fly back together into her, and she flows
 * back down the street and bursts again, farther off. Five times, and she is driven out of
 * sight: over the shore road, across the beach and into the sea. A figure left standing after
 * the third ball: her blocks come back to her, and she flows back to where she waited. Now and
 * then a figure carries a golden cube on top; knocked off, it is the player's, given in the
 * room between rounds.
 *
 * The lane runs straight down the street behind, from the player's feet; a point of it is u
 * across (to the player's right as he faces down it) and v along. Every figure is grown fresh,
 * from her blocks, and tried: it must stand by itself, and one ball rolled at its middle must
 * bring some of it down but not all. It grows in the background, one figure ahead.
 */
final class Alley {
    // the phases, in the order they come
    static final int ENTER = 0, SWELL = 1, SCATTER = 2, AIM = 3, ROLL = 4, SETTLE = 5, FELLED = 6, GATHER = 7, BACK = 8,
            OUT = 9, DONE = 10;
    // the burst: she sinks lower than a crouch, holds a moment, bursts
    static final float SLUMP_T = 0.22f, SWELL_T = 0.14f, BURST_T = 0.42f;
    static final float FLY_T = 0.55f, BACK_T = 0.5f, FELLED_T = 0.8f;

    static final int ROUNDS = 5, BALLS = 3;
    static final float WIN = 0.7f;           // share of a figure to knock down to drive her back
    static final float LANE_X = World.WAIT_X;
    static final float K = 2f;               // figures are built at this size: they are her blocks
    static final float BALL_R = 0.34f;
    static final float BLOCK_MASS = 0.12f, BALL_MASS = 5f;
    static final float FLOW = 4.2f, FLOW_OUT = 6.5f;   // how fast she flows back, meters a second
    static final float CUBE_ODDS = 0.25f;    // a golden cube on one figure, about one game in four
    // where she goes when driven out: the shore road, the end of the beach, and down into the sea
    static final float SHORE_V = -Street.SHORE_Z, SEA_V = -Street.BEACH_Z + 20f, SUNK = 7f;

    /** How far down the lane the figure of a round stands, and she behind it. */
    static float figureV(int round) {
        return 12f + 5f * round;
    }

    static float fridaV(int round) {
        return figureV(round) + 5.5f;
    }

    int phase = ENTER;
    float phaseT;
    int round, ball;
    int won;                                // figures felled so far
    float standing = 1f;                    // share of the figure still standing
    float need = WIN;                       // share to fell; the stand may ask for less
    boolean lost;                           // the last figure stood: back to where she waited
    boolean gone;                           // driven into the sea: the alley is over for this crossroads
    float flash;                            // a figure felled with one ball: a white flash fading
    final Physics phys = new Physics();
    final ArrayList<Body> blocks = new ArrayList<Body>();
    final ArrayList<Body> balls = new ArrayList<Body>();

    private final World.Host host;
    private final Game game;
    final Frida f;
    final Flow flow;
    private final Random rnd;
    private final int[] order = new int[ROUNDS];
    // she is not there while her blocks are the figure
    private boolean shown = true;
    private float slump;
    private float target;                   // where along the lane she is flowing to
    private float faceA;                    // the way she faces, about the upright (0: toward the player)
    private int outLeg;

    // the flight of each block from her to its place, or back
    private float[] fromX = new float[0], fromY = new float[0], fromZ = new float[0], delay = new float[0], turnOut = new float[0];
    private int[] partOf = new int[0];
    private boolean[] landedAt = new boolean[0];
    private final float[] q = new float[4], p3 = new float[3], t9 = new float[9], h3 = new float[3];

    // the golden cube, on the figure of one round in some games
    int cubeRound;
    Body cube;
    private boolean cubeTaken;

    // the swipe
    private boolean touching;
    private float downX, downY, downT, fingerX, clock;
    float handX;                            // where the ball in hand is, -1 left to 1 right
    int handColor;                          // the ball in hand: its color, and how far it has turned
    float handSpin;

    Alley(World.Host host, Game game, Frida f, Flow flow, long seed) {
        this.host = host;
        this.game = game;
        this.f = f;
        this.flow = flow;
        rnd = new Random(seed);
        phys.sleeping = true;
        handColor = Builder.BALL_COLORS[rnd.nextInt(Builder.BALL_COLORS.length)];
        // the first figure is always the triangle, the rest drawn from the others
        order[0] = 0;
        int[] rest = {1, 2, 3, 4, 5, 6, 7};
        for (int i = rest.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1), t = rest[i];
            rest[i] = rest[j];
            rest[j] = t;
        }
        for (int i = 1; i < ROUNDS; i++) order[i] = rest[i - 1];
        cubeRound = rnd.nextFloat() < CUBE_ODDS ? rnd.nextInt(ROUNDS) : -1;
        faceA = 0f;
        if (host != null) prepare(0);
        target = fridaV(0);
        go(ENTER);
    }

    /** A grower alone, for the background: no world, no her. */
    private Alley(long seed) {
        host = null;
        game = null;
        f = null;
        flow = null;
        rnd = new Random(seed);
        cubeRound = -1;
    }

    /** A point of the lane, across u and along v from the player's feet, into the world. */
    static float wx(float u, float v) {
        return LANE_X - u;
    }

    static float wz(float u, float v) {
        return World.CAM_Z - v;
    }

    /** Where a world point is along the lane. */
    static float vOf(float x, float z) {
        return World.CAM_Z - z;
    }

    private void go(int p) {
        phase = p;
        phaseT = 0f;
    }

    boolean done() {
        return phase == DONE;
    }

    // the view closes in on far figures, as it does on far plinths: so much narrower, eased
    private float zoomNow = 1f;

    /** How much narrower the view is now: by the figure's distance, none for the first. */
    float zoom() {
        return zoomNow;
    }

    private void zoomStep(float dt) {
        float v = phase == OUT || phase == BACK || phase == DONE ? 12f : figureV(round);
        float want = Math.max(1f, v / 12f * 0.85f);
        zoomNow += (want - zoomNow) * Math.min(1f, dt * 2.5f);
    }

    /** Whether a ball can be rolled now. */
    boolean aiming() {
        return phase == AIM;
    }

    // ---- time ----

    void update(float dt) {
        phaseT += dt;
        clock += dt;
        flash = Math.max(0f, flash - dt * 2.5f);
        handSpin += dt * 0.6f;
        float want = touching ? Math.max(-1f, Math.min(1f, (fingerX - World.W * 0.5f) / (World.W * 0.4f))) : 0f;
        handX += (want - handX) * Math.min(1f, dt * 10f);
        flowStep(dt);
        zoomStep(dt);
        cubeFlight(dt);
        switch (phase) {
            case ENTER:
                if (arrived() && phaseT > 0.25f) {
                    go(SWELL);
                    host.sound(Synth.STONE, 0.7f, 0.45f);
                }
                break;
            case SWELL: {
                float s = Math.min(1f, phaseT / SLUMP_T);
                slump = s * s * (3f - 2f * s);
                if (phaseT >= BURST_T) {
                    for (int i = 0; i < 14; i++)
                        game.spawn(flow.x + (rnd.nextFloat() - 0.5f) * 3f, 0.1f, flow.z + (rnd.nextFloat() - 0.5f) * 3f, true, 0xC9BFAE, 1.2f);
                    scatter();
                    slump = 0f;
                }
                break;
            }
            case SCATTER:
                flyBlocks(false);
                if (phaseT > lastDelay() + FLY_T + 0.35f) {
                    for (Body b : blocks) {
                        b.setPose(b.homeX, b.homeY, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
                        b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0f;
                        b.shown = 1f;
                        b.asleep = true;
                        b.savePrev();
                        phys.add(b);
                    }
                    if (cube != null) {
                        cube.asleep = true;
                        cube.savePrev();
                        phys.add(cube);
                    }
                    ball = 0;
                    standing = 1f;
                    go(AIM);
                }
                break;
            case AIM:
                break;
            case ROLL: {
                Body b = balls.get(balls.size() - 1);
                phys.step(dt);
                cubeWatch();
                boolean stopped = b.speed2() < 0.09f && phaseT > 0.8f;
                // at the figure, what it does now is settling
                boolean past = vOf(b.px, b.pz) > figureV(round) - 0.5f * K;
                if (vOf(b.px, b.pz) > fridaV(round) + 1f) {
                    // gone on down the street, out of the game
                    b.gone = true;
                    b.ghost = true;
                }
                if (stopped || past || b.gone || phaseT > 3f) go(SETTLE);
                break;
            }
            case SETTLE:
                phys.step(dt);
                cubeWatch();
                standing = 1f - knockedShare();
                if (phaseT > 1.1f && (still() || phaseT > 2.6f)) count();
                break;
            case FELLED:
                phys.step(dt);
                cubeWatch();
                if (phaseT > FELLED_T) gather();
                break;
            case GATHER:
                flyBlocks(true);
                if (phaseT > lastDelay() + BACK_T) {
                    blocks.clear();
                    shown = true;
                    host.sound(Synth.STONE, 0.8f, 0.5f);
                    if (lost) {
                        // whole again: she flows back to where she waited, and waits
                        target = fridaV(0);
                        go(BACK);
                    } else if (won >= ROUNDS) {
                        // driven out: over the shore road, across the beach, into the sea
                        host.sound(Synth.ROAR, 0.8f, 1.3f);
                        outLeg = 0;
                        target = SHORE_V;
                        go(OUT);
                    } else {
                        round++;
                        target = fridaV(round);
                        go(ENTER);
                    }
                }
                break;
            case BACK:
                if (arrived() && phaseT > 0.3f) go(DONE);
                break;
            case OUT:
                if (arrived()) {
                    if (outLeg == 0) {
                        outLeg = 1;
                        target = SEA_V;
                    } else if (flow.y > -SUNK) {
                        // down into the water, a little at a time, as if wading in
                        flow.y = Math.max(-SUNK, flow.y - dt * 2.5f);
                        if (((int) (phaseT * 8f) & 1) == 0)
                            game.spawn(flow.x + (rnd.nextFloat() - 0.5f) * 2f, 0.1f, flow.z + (rnd.nextFloat() - 0.5f) * 2f, false, 0xE8F4FA, 2.5f);
                    } else {
                        gone = true;
                        host.sound(Synth.SPLASH, 1.2f, 0.55f);
                        go(DONE);
                    }
                }
                break;
            default:
                break;
        }
    }

    /** She flows toward her mark along the lane, turning the way she goes; arrived, she faces the player. */
    private void flowStep(float dt) {
        float v = vOf(flow.x, flow.z), dv = target - v;
        float speed = phase == OUT ? FLOW_OUT : FLOW;
        float go = Math.abs(dv) < 0.05f || !shown ? 0f : Math.signum(dv) * Math.min(speed, Math.abs(dv) * 3f + 0.4f);
        // facing the player as she backs away down the lane; only driven out does she turn her back
        float want = phase == OUT && go > 0.05f ? (float) Math.PI : 0f;
        float d = want - faceA;
        faceA += Math.signum(d) * Math.min(Math.abs(d), dt * 4f);
        flow.fx = (float) Math.sin(faceA);
        flow.fz = (float) Math.cos(faceA);
        // along the lane: v grows as z falls
        flow.update(dt, 0f, -go);
        flow.x = LANE_X;
    }

    private boolean arrived() {
        return Math.abs(target - vOf(flow.x, flow.z)) < 0.06f && Math.abs(faceA) < 0.05f;
    }

    private boolean still() {
        for (Body b : blocks) if (!b.asleep && b.speed2() > 0.02f) return false;
        return true;
    }

    /** A pin: a tall stone pillar, pale, with red bands round its neck. */
    static boolean isPin(Body b) {
        return b.hy > 2.5f * b.hx && b.hy > 2.5f * b.hz;
    }

    private float lastDelay() {
        float m = 0f;
        for (float x : delay) m = Math.max(m, x);
        return m;
    }

    // ---- her blocks and the figure ----

    /** Her blocks in order, lowest first: the one each block of the figure flies from. */
    private int[] partsLowFirst() {
        f.pose();
        int n = f.parts();
        final float[] y = new float[n];
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            f.where(i, p3, t9, h3);
            y[i] = p3[1];
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Float.compare(y[a], y[b]);
            }
        });
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = idx[i];
        return out;
    }

    /** The order the figure's blocks land in: the lowest first, and of those the nearest the player. */
    private int[] ranks() {
        int n = blocks.size();
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                Body p = blocks.get(a), q = blocks.get(b);
                float ya = Math.round(p.homeY * 4f), yb = Math.round(q.homeY * 4f);
                if (ya != yb) return ya < yb ? -1 : 1;
                float va = vOf(p.homeX, p.homeZ), vb = vOf(q.homeX, q.homeZ);
                return va < vb ? -1 : va > vb ? 1 : 0;
            }
        });
        int[] rank = new int[n];
        for (int r = 0; r < n; r++) rank[idx[r]] = r;
        return rank;
    }

    /**
     * She bursts, and her blocks fly to their places in the figure of the round: each from a
     * block of hers, the lowest and nearest first, so they come down in a wave from the point
     * of the figure backward.
     */
    private void scatter() {
        blocks.clear();
        phys.clear();
        ArrayList<Body> grown = takeGrown(round);
        if (grown != null) blocks.addAll(grown);
        else {
            v0 = figureV(round);
            build(order[round]);
        }
        prepare(round + 1);
        cube = round == cubeRound ? crown() : null;
        cubeTaken = false;
        int n = blocks.size();
        fromX = new float[n];
        fromY = new float[n];
        fromZ = new float[n];
        delay = new float[n];
        turnOut = new float[n];
        partOf = new int[n];
        landedAt = new boolean[n];
        int[] rank = ranks(), low = partsLowFirst();
        for (int i = 0; i < n; i++) {
            partOf[i] = low[Math.min(low.length - 1, rank[i] * low.length / Math.max(1, n))];
            f.where(partOf[i], p3, t9, h3);
            fromX[i] = p3[0];
            fromY[i] = p3[1];
            fromZ[i] = p3[2];
            turnOut[i] = 0.4f + (rank[i] % 5) * 0.5f;
            delay[i] = rank[i] * Math.min(0.037f, 0.9f / Math.max(1, n));
        }
        shown = false;
        host.sound(Synth.THUNDER, 0.5f, 1.5f);
        host.sound(Synth.CRASH, 0.7f, 0.5f);
        host.buzz(60);
        go(SCATTER);
    }

    /** Each block along its arc: out of her to its place, or (back) the other way. */
    private void flyBlocks(boolean back) {
        int n = blocks.size();
        for (int i = 0; i < n; i++) {
            Body b = blocks.get(i);
            float t = (phaseT - delay[i]) / (back ? BACK_T : FLY_T);
            float k = Math.max(0f, Math.min(1f, t));
            if (back) k = 1f - k;
            // k = 0 in her, 1 at the place: a ballistic arc, quick out of the body
            float x = fromX[i] + (b.homeX - fromX[i]) * k;
            float z = fromZ[i] + (b.homeZ - fromZ[i]) * k;
            float rise = Math.max(1.2f, (fromY[i] - b.homeY) * 0.3f + 1.4f);
            float y = fromY[i] + (b.homeY - fromY[i]) * k + rise * 4f * k * (1f - k);
            // down, it kicks up once and settles
            if (!back && t > 1f && t < 1.6f) {
                float u = (t - 1f) * FLY_T;
                y += 0.07f * K * (float) Math.exp(-u * 16f) * Math.abs((float) Math.sin(u * 26f));
            }
            // turning over on its way, upright as it lands
            float spin = (1f - k) * turnOut[i];
            float c = (float) Math.cos(spin * 0.5f), s = (float) Math.sin(spin * 0.5f);
            float hw = b.homeQw, hx = b.homeQx, hy = b.homeQy, hz = b.homeQz;
            b.setPose(x, y, z, hw * c - hx * s, hx * c + hw * s, hy * c + hz * s, hz * c - hy * s);
            b.shown = back ? 0.7f + 0.3f * k : 1f;
            if (!back && t >= 1f && !landedAt[i]) {
                landedAt[i] = true;
                boolean last = true;
                for (boolean l : landedAt) last &= l;
                if (last) {
                    host.sound(Synth.GROUND, 1.1f, 0.65f);
                    host.sound(Synth.STONE, 0.9f, 0.5f);
                    host.buzz(45);
                } else host.sound(Synth.STONE, 0.5f, 0.55f + rnd.nextFloat() * 0.35f);
                for (int d = 0; d < 3; d++)
                    game.spawn(b.homeX + (rnd.nextFloat() - 0.5f) * b.hx * 2f, 0.05f, b.homeZ, true, 0xC9BFAE, 0.8f);
            }
        }
        // the cube comes last, onto the top
        if (cube != null && !back) {
            float t = Math.max(0f, Math.min(1f, (phaseT - lastDelay() - 0.1f) / FLY_T));
            float y = cube.homeY + (1f - t) * 6f;
            cube.setPose(cube.homeX, y, cube.homeZ, 1f, 0f, 0f, 0f);
            cube.shown = t > 0f ? 1f : 0f;
        }
    }

    private float knockedShare() {
        int down = 0;
        for (Body b : blocks) if (knocked(b)) down++;
        return blocks.isEmpty() ? 0f : down / (float) blocks.size();
    }

    static boolean knocked(Body b) {
        float dx = b.px - b.homeX, dy = b.py - b.homeY, dz = b.pz - b.homeZ;
        float far = 0.25f * K;
        if (dx * dx + dy * dy + dz * dz > far * far) return true;
        // the up of the block against the up it was set with
        return b.r11 < 0.8f;
    }

    /** After each ball: felled enough, and she is driven back; else the next ball, or she gathers. */
    private void count() {
        float s = knockedShare();
        standing = 1f - s;
        if (s >= need) {
            won++;
            if (ball == 0) {
                flash = 1f;
                host.sound(Synth.thunder(1 + rnd.nextInt(Synth.THUNDERS - 1)), 1f, 1f);
                host.buzz(90);
            } else {
                host.sound(Synth.thunder(rnd.nextInt(Synth.THUNDERS)), 0.7f, 1.2f);
                host.buzz(50);
            }
            // a figure felled carries its cube with it
            if (cube != null) takeCube();
            go(FELLED);
            return;
        }
        if (ball < BALLS - 1) {
            ball++;
            go(AIM);
            return;
        }
        lost = true;
        gather();
    }

    /** Her blocks fly back together, and she stands up from them. */
    private void gather() {
        int n = blocks.size();
        fromX = new float[n];
        fromY = new float[n];
        fromZ = new float[n];
        delay = new float[n];
        if (partOf.length != n) {
            partOf = new int[n];
            for (int i = 0; i < n; i++) partOf[i] = i % Math.max(1, f.parts());
        }
        turnOut = new float[n];
        f.pose();
        for (int i = 0; i < n; i++) {
            Body b = blocks.get(i);
            // the flight runs from her block (k = 0) to where the block lies now (k = 1)
            f.where(partOf[i], p3, t9, h3);
            fromX[i] = p3[0];
            fromY[i] = p3[1];
            fromZ[i] = p3[2];
            turnOut[i] = 1f + rnd.nextFloat();
            b.homeX = b.px;
            b.homeY = b.py;
            b.homeZ = b.pz;
            b.homeQw = b.qw;
            b.homeQx = b.qx;
            b.homeQy = b.qy;
            b.homeQz = b.qz;
            delay[i] = rnd.nextFloat() * 0.25f;
        }
        for (Body b : balls) b.gone = true;
        balls.clear();
        phys.clear();
        // a cube left on the figure goes with the figure; one taken goes on to the player
        if (cube != null && !cubeTaken) cube = null;
        host.sound(Synth.WHOOSH, 0.8f, 0.7f);
        go(GATHER);
    }

    // ---- the golden cube ----

    /**
     * A golden cube on the figure: on the top of the highest block near the middle that is
     * broad enough to carry it; failing that, on the ground before the figure.
     */
    private Body crown() {
        Body top = null;
        for (Body b : blocks)
            if (Math.abs(b.homeX - wx(0f, 0f)) < 0.6f * K && b.hx >= 0.18f && b.hz >= 0.18f && (top == null || b.homeY + b.hy > top.homeY + top.hy))
                top = b;
        float hs = 0.26f;
        Body c = new Body(hs, hs, hs, Body.GOLD, 0xFFC81E);
        c.scaleMass(0.4f);
        float x = top != null ? top.homeX : wx(0f, figureV(round) - 1.2f);
        float z = top != null ? top.homeZ : wz(0f, figureV(round) - 1.2f);
        float y = top != null ? top.homeY + top.hy + hs + 0.005f : hs;
        c.setPose(x, y, z, 1f, 0f, 0f, 0f);
        c.homeX = x;
        c.homeY = y;
        c.homeZ = z;
        c.shown = 0f;
        return c;
    }

    /** Knocked off the figure, the cube is the player's. */
    private void cubeWatch() {
        if (cube == null || cubeTaken) return;
        float dx = cube.px - cube.homeX, dy = cube.py - cube.homeY, dz = cube.pz - cube.homeZ;
        if (dx * dx + dy * dy + dz * dz > 0.4f * 0.4f) takeCube();
    }

    private void takeCube() {
        cubeTaken = true;
        // it comes to the player: up off the figure and down at his feet, and is gone into the rack
        cubeT = 0f;
        cubeX0 = cube.px;
        cubeY0 = cube.py;
        cubeZ0 = cube.pz;
        cube.ghost = true;
        cube.asleep = true;
        host.sound(Synth.CATCH, 0.9f, 1.3f);
        host.buzz(40);
        game.alleyCube();
    }

    static final float CUBE_FLY = 0.9f;
    private float cubeT = -1f, cubeX0, cubeY0, cubeZ0;

    private void cubeFlight(float dt) {
        if (cube == null || cubeT < 0f) return;
        cubeT += dt;
        float k = Math.min(1f, cubeT / CUBE_FLY), e = k * k * (3f - 2f * k);
        float tx = World.CAM_X + 0.6f, ty = 0.6f, tz = World.CAM_Z - 2.2f;
        float x = cubeX0 + (tx - cubeX0) * e, z = cubeZ0 + (tz - cubeZ0) * e;
        float y = cubeY0 + (ty - cubeY0) * e + 3.5f * 4f * k * (1f - k);
        float a = cubeT * 7f;
        cube.setPose(x, y, z, (float) Math.cos(a * 0.5f), (float) Math.sin(a * 0.5f) * 0.6f, (float) Math.sin(a * 0.5f) * 0.8f, 0f);
        cube.shown = k < 0.85f ? 1f : (1f - k) / 0.15f;
        if (k >= 1f) {
            cube = null;
            cubeT = -1f;
            host.sound(Synth.CATCH, 0.8f, 1.6f);
        }
    }

    // ---- the swipe ----

    void touch(int type, float x, float y, float now) {
        if (phase != AIM) return;
        if (type == World.DOWN) {
            touching = true;
            downX = x;
            downY = y;
            downT = now;
            fingerX = x;
        } else if (type == World.MOVE && touching) {
            fingerX = x;
        } else if (type == World.UP && touching) {
            touching = false;
            float dy = downY - y, dx = x - downX, secs = Math.max(0.05f, now - downT);
            if (dy < 20f || secs > 1.5f) return;
            float pxps = (float) Math.sqrt(dx * dx + dy * dy) / secs;
            float speed = Math.max(5f, Math.min(13f, 4f + pxps / 140f));
            float ang = Math.max(-0.12f, Math.min(0.12f, (float) Math.atan2(dx, dy) * 0.3f));
            roll((downX - World.W * 0.5f) / World.W * 2.4f, speed, ang);
        }
    }

    /** A ball rolled from u (meters right of the lane's middle as the player faces down it) at a speed, at an angle. */
    void roll(float u, float speed, float ang) {
        if (phase != AIM) return;
        u = Math.max(-1.2f, Math.min(1.2f, u));
        // the very ball that was in hand, turned as it was held
        Body b = Body.ball(BALL_R, handColor);
        b.scaleMass(BALL_MASS);
        b.setPose(wx(u, 1.6f), BALL_R + 0.01f, wz(u, 1.6f), (float) Math.cos(handSpin * 0.5f), 0f, (float) Math.sin(handSpin * 0.5f), 0f);
        // and the next one taken up: another of the set, never the same color twice running
        int c;
        do c = Builder.BALL_COLORS[rnd.nextInt(Builder.BALL_COLORS.length)];
        while (c == handColor);
        handColor = c;
        // along the lane (toward -z), turned a little across it
        float across = (float) Math.sin(ang), along = (float) Math.cos(ang);
        b.vx = -across * speed;
        b.vz = -along * speed;
        b.vy = 0f;
        b.wx = b.vz / BALL_R;
        b.wz = -b.vx / BALL_R;
        b.asleep = false;
        b.savePrev();
        phys.add(b);
        balls.add(b);
        host.sound(Synth.ROLL, Math.min(1.2f, 0.5f + speed / 12f), 0.8f + speed / 45f);
        host.buzz(15);
        handX = 0f;
        go(ROLL);
    }

    // ---- drawing ----

    private final float[] turn = new float[9];

    /** Her, the figure, the balls rolled, the golden cube, and the ball in hand. */
    void draw(DrawList d) {
        if (shown) {
            flow.pose();
            // sinking into herself before she bursts
            f.lift -= slump * 0.9f;
            f.pose();
            f.draw(d);
        }
        for (Body b : blocks) {
            if (b.shown <= 0f) continue;
            rot(b);
            float s = b.shown;
            boolean pin = isPin(b);
            int mat = b.tint == BRICK || b.tint == BRICK2 ? DrawList.GRAIN : DrawList.STONE;
            d.box(b.px, b.py, b.pz, b.hx * s, b.hy * s, b.hz * s, turn, b.tint & 0xFFFFFF, mat);
            if (pin) {
                // two red bands round its neck, standing a hair proud of it
                for (int k = 0; k < 2; k++) {
                    float off = (0.55f - k * 0.18f) * b.hy * s;
                    d.box(b.px + turn[1] * off, b.py + turn[4] * off, b.pz + turn[7] * off, b.hx * s * 1.06f, b.hy * s * 0.05f,
                            b.hz * s * 1.06f, turn, 0xC23A2C, DrawList.PAINT);
                }
            }
        }
        for (Body b : balls) {
            if (b.gone) continue;
            rot(b);
            d.ball(b.px, b.py, b.pz, b.radius, turn, b.tint & 0xFFFFFF, DrawList.POLISHED);
        }
        if (cube != null && cube.shown > 0f) {
            rot(cube);
            float s = cube.shown;
            d.box(cube.px, cube.py, cube.pz, cube.hx * s, cube.hy * s, cube.hz * s, turn, 0xFFB428, DrawList.FIRE_GOLD);
        }
        // the ball in hand, low on the right, following the finger
        if (phase == AIM || phase == SCATTER || phase == ROLL && phaseT < 0.15f) {
            float up = phase == SCATTER ? Math.min(1f, phaseT / 0.6f) : 1f;
            float vx = 0.62f + handX * 0.45f, vy = -1.3f - (1f - up) * 0.8f, vz = 3.3f;
            float x = d.eye[0] + d.right[0] * vx + d.up[0] * vy + d.ahead[0] * vz;
            float y = d.eye[1] + d.right[1] * vx + d.up[1] * vy + d.ahead[1] * vz;
            float z = d.eye[2] + d.right[2] * vx + d.up[2] * vy + d.ahead[2] * vz;
            float c = (float) Math.cos(handSpin), s = (float) Math.sin(handSpin);
            turn[0] = c; turn[1] = 0f; turn[2] = s;
            turn[3] = 0f; turn[4] = 1f; turn[5] = 0f;
            turn[6] = -s; turn[7] = 0f; turn[8] = c;
            d.ball(x, y, z, BALL_R, turn, handColor & 0xFFFFFF, DrawList.POLISHED);
        }
    }

    private void rot(Body b) {
        turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
        turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
        turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
    }

    /** The balls left, small, low in the corner, while the figure stands. */
    void flat(Px p) {
        if (phase != AIM && phase != ROLL && phase != SETTLE) return;
        int left = BALLS - ball - (phase == AIM ? 0 : 1);
        for (int i = 0; i < left; i++) {
            float cx = 12 + i * 13, cy = p.h - 22;
            p.disc(cx + 1, cy + 1.5f, 5f, 0x66000000);
            p.disc(cx, cy, 5f, 0xFF000000 | Street.scale(handColor, 1.4f));
            p.set(Math.round(cx) - 2, Math.round(cy) - 2, 0xCCFFFFFF);
        }
    }

    // ---- the grower ----
    // Every figure is grown fresh, from her blocks, storey by storey: ranks of pillars, courses
    // of brick, portals with a lintel, towers of blocks, a bending row of slabs, steps, a totem.
    // A flat top may carry another storey, narrower; some are capped with a block or a pillar.
    // Each candidate must stand by itself, and one ball rolled at its middle must bring down
    // some of it but not all: of six candidates the one nearest the share wanted is kept.

    private Thread growing;
    private volatile ArrayList<Body> grownFig;
    private int grownFor = -1;

    private void prepare(final int r) {
        if (r >= ROUNDS || grownFor == r) return;
        grownFor = r;
        grownFig = null;
        final long sd = rnd.nextLong();
        growing = new Thread(new Runnable() {
            @Override
            public void run() {
                grownFig = new Alley(sd).growFigure(r, new Random(sd));
            }
        });
        growing.setDaemon(true);
        growing.start();
    }

    private ArrayList<Body> takeGrown(int r) {
        if (grownFor != r || growing == null) return null;
        try {
            growing.join();
        } catch (InterruptedException e) {
            return null;
        }
        growing = null;
        grownFor = -1;
        return grownFig;
    }

    /** Six candidates, the one that stands and falls nearest the share wanted; null if none stood. */
    ArrayList<Body> growFigure(int r, Random g) {
        float want = 0.55f - 0.05f * r, bestGap = 9f;
        ArrayList<Body> best = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            blocks.clear();
            grow(g, r, figureV(r));
            if (blocks.size() < 6 || !standsAlone()) continue;
            float s = probe();
            if (s < 0.12f || s > 0.9f) continue;
            float gap = Math.abs(s - want);
            if (gap < bestGap) {
                bestGap = gap;
                best = new ArrayList<Body>(blocks);
            }
            if (gap < 0.1f) break;
        }
        return best;
    }

    /** Let it stand a second: nothing may slide or lean. It starts the game as it settled. */
    boolean standsAlone() {
        Physics p = new Physics();
        p.sleeping = false;
        for (Body b : blocks) {
            b.setPose(b.homeX, b.homeY, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
            b.asleep = false;
            b.savePrev();
            p.add(b);
        }
        for (int i = 0; i < 60; i++) p.step(1f / 60f);
        boolean ok = true;
        for (Body b : blocks) {
            float dx = b.px - b.homeX, dy = b.py - b.homeY, dz = b.pz - b.homeZ;
            if (dx * dx + dy * dy + dz * dz > 0.06f * K * 0.06f * K || b.r11 < 0.98f) ok = false;
        }
        for (Body b : blocks) {
            if (ok) {
                b.homeX = b.px;
                b.homeY = b.py;
                b.homeZ = b.pz;
            }
            b.setPose(b.homeX, b.homeY, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
            b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0f;
            b.id = 0;
        }
        return ok;
    }

    /** One ball, fair and square at the middle: the share it brings down. Everything put back after. */
    float probe() {
        Physics p = new Physics();
        p.sleeping = true;
        for (Body b : blocks) {
            b.asleep = true;
            b.savePrev();
            p.add(b);
        }
        Body ball = Body.ball(BALL_R, 0);
        ball.scaleMass(BALL_MASS);
        ball.setPose(wx(0f, 1.6f), BALL_R + 0.01f, wz(0f, 1.6f), 1, 0, 0, 0);
        float speed = 10f;
        ball.vz = -speed;
        ball.wx = ball.vz / BALL_R;
        ball.asleep = false;
        ball.savePrev();
        p.add(ball);
        for (int i = 0; i < 60 * 4; i++) p.step(1f / 60f);
        float s = knockedShare();
        for (Body b : blocks) {
            b.setPose(b.homeX, b.homeY, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
            b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0f;
            b.asleep = true;
            b.id = 0;
        }
        return s;
    }

    private static final int PINS = 0, WALL = 1, PORTAL = 2, TOWERS = 3, SLABS = 4, STEPS = 5;

    /** A figure grown storey by storey, in lane units, at a distance v. */
    void grow(Random g, int r, float v) {
        v0 = v;
        float hw = 0.7f + g.nextFloat() * 0.4f + r * 0.04f;
        float y = 0f;
        int storeys = 2 + g.nextInt(2) + (r >= 2 && g.nextBoolean() ? 1 : 0);
        boolean flat = false;
        for (int s = 0; s < storeys; s++) {
            boolean ground = s == 0;
            // what may stand here: everything on the ground, on a flat top only what is narrow-footed;
            // flat tops come more often low down, so the figure has storeys to grow
            boolean last = s == storeys - 1;
            float[] w = {last ? 1.4f : 0.5f, 1.4f, 1.8f, last ? 1.0f : 0.3f, ground ? 0.5f : 0f, ground ? 0.5f : 0f, last ? 0.6f : 0.2f};
            float sum = 0f;
            for (float x : w) sum += x;
            float pick = g.nextFloat() * sum;
            int m = 0;
            while (m < w.length - 1 && (pick -= w[m]) > 0f) m++;
            int c = (s & 1) == 0 ? STONE : STONE2;
            switch (m) {
                case PINS: {
                    int n = Math.max(2, Math.min(5, Math.round(2f * hw / 0.46f)));
                    float gap = Math.min(0.56f, 2f * hw / n);
                    int ranks = ground ? 1 + g.nextInt(3) : 1;
                    for (int k = 0; k < ranks; k++) {
                        int nk = n - (k & 1);
                        for (int i = 0; i < nk; i++)
                            block((i - (nk - 1) * 0.5f) * gap, k * 0.5f, y, 0.13f, 0.45f, 0.13f, PIN, 0f);
                    }
                    flat = false;
                    s = storeys;
                    break;
                }
                case WALL: {
                    int n = Math.max(2, Math.round(2f * hw / 0.5f));
                    float bw = 2f * hw / n, bh = 0.24f;
                    int courses = 2 + g.nextInt(2);
                    for (int k = 0; k < courses; k++) {
                        if ((k & 1) == 0)
                            for (int i = 0; i < n; i++)
                                block(-hw + bw * (i + 0.5f), 0f, y, bw * 0.5f - 0.005f, bh * 0.5f, 0.15f, (i + k) % 3 == 0 ? BRICK2 : BRICK, 0f);
                        else {
                            block(-hw + bw * 0.25f, 0f, y, bw * 0.25f - 0.005f, bh * 0.5f, 0.15f, BRICK, 0f);
                            for (int i = 0; i < n - 1; i++)
                                block(-hw + bw * (i + 1f), 0f, y, bw * 0.5f - 0.005f, bh * 0.5f, 0.15f, (i + k) % 3 == 0 ? BRICK2 : BRICK, 0f);
                            block(hw - bw * 0.25f, 0f, y, bw * 0.25f - 0.005f, bh * 0.5f, 0.15f, BRICK, 0f);
                        }
                        y += bh;
                    }
                    flat = true;
                    break;
                }
                case PORTAL: {
                    float cs = 0.17f;
                    int high = 2 + g.nextInt(2);
                    boolean three = hw > 0.75f;
                    for (int k = 0; k < high; k++) {
                        cube(-hw + cs, 0f, y + k * 2f * cs, cs, (k & 1) == 0 ? c : BRICK);
                        cube(hw - cs, 0f, y + k * 2f * cs, cs, (k & 1) == 0 ? BRICK : c);
                        if (three) cube(0f, 0f, y + k * 2f * cs, cs, c);
                    }
                    y += high * 2f * cs;
                    block(0f, 0f, y, hw + 0.04f, 0.06f, 0.2f, STONE2, 0f);
                    y += 0.12f;
                    flat = true;
                    break;
                }
                case TOWERS: {
                    int n = 2 + (hw > 0.7f ? g.nextInt(2) : 0), high = 2 + g.nextInt(3);
                    float cs = 0.15f + g.nextFloat() * 0.04f;
                    for (int t = 0; t < n; t++) {
                        float u = n == 1 ? 0f : -hw + cs + t * (2f * hw - 2f * cs) / (n - 1);
                        for (int k = 0; k < high; k++) cube(u, 0f, y + k * 2f * cs, cs, ((k + t) & 1) == 0 ? c : BRICK);
                    }
                    flat = false;
                    s = storeys;
                    break;
                }
                case SLABS: {
                    int n = 6 + g.nextInt(5);
                    float bend = (g.nextFloat() - 0.5f) * 1.2f;
                    for (int i = 0; i < n; i++) {
                        float a = i * 0.55f;
                        float u = bend * (float) Math.sin(a), du = bend * 0.55f / 0.5f * (float) Math.cos(a);
                        block(u, -1.8f + i * 0.5f, 0f, 0.26f, 0.45f, 0.06f, (i & 1) == 0 ? STONE : BRICK, (float) Math.atan(du));
                    }
                    flat = false;
                    s = storeys;
                    break;
                }
                case STEPS: {
                    float cs = 0.2f;
                    int base = Math.max(2, Math.min(4, Math.round(hw / cs)));
                    for (int k = 0; k < base; k++) {
                        int n = base - k;
                        for (int i = 0; i < n; i++) cube((i - (n - 1) * 0.5f) * 2f * cs, 0f, y + k * 2f * cs, cs - 0.003f, (k & 1) == 0 ? STONE : STONE2);
                    }
                    flat = false;
                    s = storeys;
                    break;
                }
                default: { // a totem in the middle, stone and brick by turns
                    int n = 3 + g.nextInt(3);
                    for (int i = 0; i < n; i++) {
                        boolean wide = (i & 1) == 0;
                        float hu = wide ? 0.17f : 0.12f, hy = wide ? 0.11f : 0.16f;
                        block(0f, 0f, y, hu, hy, 0.14f, wide ? c : BRICK, 0f);
                        y += 2f * hy;
                    }
                    flat = false;
                    s = storeys;
                    break;
                }
            }
            hw *= 0.65f + g.nextFloat() * 0.25f;
            if (hw < 0.28f) break;
        }
        // a flat top may wear a cap
        if (flat && g.nextFloat() < 0.7f) {
            if (g.nextBoolean()) block(0f, 0f, y, 0.13f, 0.45f, 0.13f, PIN, 0f);
            else for (int i = -1; i <= 1; i += 2) cube(i * Math.min(0.3f, hw), 0f, y, 0.15f, BRICK2);
        }
    }

    // ---- the figures ----
    // Written small, in lane units: u across, dv along from the figure's place, y up, and
    // built K times bigger, every block set square to the lane. Her own blocks: pale stone,
    // a darker stone, brick; the pillars of the triangle banded red like pins.

    static final int STONE = 0xE4DACA, STONE2 = 0xCFC3AE, BRICK = 0xCC644C, BRICK2 = 0xB4553E, PIN = 0xF0EAE0;
    private float v0;

    private void block(float u, float dv, float y, float hu, float hy, float hv, int color, float turn) {
        float x = wx(u * K, v0 + dv * K), z = wz(u * K, v0 + dv * K);
        hu *= K;
        hy *= K;
        hv *= K;
        // square to the lane, and the block's own turn about the upright on top of that
        q[0] = (float) Math.cos(turn * 0.5f);
        q[2] = (float) Math.sin(turn * 0.5f);
        Body b = new Body(hu, hy, hv, Body.STONE, color);
        // stone to look at, light enough to knock over, and free to topple
        b.scaleMass(BLOCK_MASS);
        b.trueInertia();
        b.setPose(x, y * K + hy, z, q[0], 0f, q[2], 0f);
        b.homeX = b.px;
        b.homeY = b.py;
        b.homeZ = b.pz;
        b.homeQw = q[0];
        b.homeQx = 0f;
        b.homeQy = q[2];
        b.homeQz = 0f;
        blocks.add(b);
    }

    private void pin(float u, float dv) {
        block(u, dv, 0f, 0.13f, 0.45f, 0.13f, PIN, 0f);
    }

    private void cube(float u, float dv, float y, float s, int color) {
        block(u, dv, y, s, s, s, color, 0f);
    }

    void build(int kind) {
        switch (kind) {
            case 0: // ten pillars in a triangle, its point toward the player
                for (int r = 0; r < 4; r++)
                    for (int i = 0; i <= r; i++) pin((i - r * 0.5f) * 0.56f, r * 0.5f);
                break;
            case 1: { // a brick wall in running bond
                float bw = 0.5f, bh = 0.24f;
                for (int r = 0; r < 4; r++) {
                    int n = (r & 1) == 0 ? 5 : 4;
                    for (int i = 0; i < n; i++)
                        block((i - (n - 1) * 0.5f) * bw, 0f, r * bh, bw * 0.5f - 0.005f, bh * 0.5f, 0.15f,
                                (i + r) % 3 == 0 ? BRICK2 : BRICK, 0f);
                }
                break;
            }
            case 2: { // a gate: two columns, a lintel, three stones on it
                float c = 0.2f;
                for (int i = 0; i < 3; i++) {
                    cube(-0.4f, 0f, i * 2f * c, c, STONE);
                    cube(0.4f, 0f, i * 2f * c, c, STONE2);
                }
                block(0f, 0f, 6f * c, 0.7f, 0.07f, 0.22f, BRICK, 0f);
                for (int i = -1; i <= 1; i++) cube(i * 0.42f, 0f, 6f * c + 0.14f, 0.18f, i == 0 ? STONE2 : STONE);
                break;
            }
            case 3: { // slabs on end in a gentle bend, each one near enough to topple the next
                for (int i = 0; i < 10; i++) {
                    float dv = -1.8f + i * 0.5f;
                    float u = 0.5f * (float) Math.sin(i * 0.55f);
                    float du = 0.55f * (float) Math.cos(i * 0.55f);
                    block(u, dv, 0f, 0.26f, 0.45f, 0.06f, (i & 1) == 0 ? STONE : BRICK, (float) Math.atan(du));
                }
                break;
            }
            case 4: { // a stepped pyramid, one stone deep
                float c = 0.2f;
                for (int r = 0; r < 4; r++) {
                    int n = 4 - r;
                    for (int i = 0; i < n; i++)
                        cube((i - (n - 1) * 0.5f) * 2f * c, 0f, r * 2f * c, c - 0.003f, (r & 1) == 0 ? STONE : STONE2);
                }
                break;
            }
            case 5: // a phalanx: three ranks of pillars, staggered
                for (int r = 0; r < 3; r++) {
                    int n = (r & 1) == 0 ? 4 : 3;
                    for (int i = 0; i < n; i++) pin((i - (n - 1) * 0.5f) * 0.42f, r * 0.5f);
                }
                break;
            case 6: { // two towers bridged by a plank, a little one on top
                float c = 0.17f;
                for (int i = 0; i < 4; i++) {
                    cube(-0.33f, 0f, i * 2f * c, c, (i & 1) == 0 ? STONE : BRICK);
                    cube(0.33f, 0f, i * 2f * c, c, (i & 1) == 0 ? BRICK : STONE);
                }
                block(0f, 0f, 8f * c, 0.6f, 0.06f, 0.2f, STONE2, 0f);
                cube(0f, 0f, 8f * c + 0.12f, c, BRICK2);
                cube(0f, 0f, 10f * c + 0.12f, c * 0.8f, STONE);
                break;
            }
            default: { // three totems in a row, stone and brick by turns
                for (int t = -1; t <= 1; t++) {
                    float y = 0f;
                    for (int i = 0; i < 5; i++) {
                        boolean flat = (i & 1) == 0;
                        float hu = flat ? 0.17f : 0.12f, hy = flat ? 0.11f : 0.16f;
                        block(t * 0.5f, Math.abs(t) * 0.35f, y, hu, hy, 0.14f, flat ? STONE : BRICK, 0f);
                        y += 2f * hy;
                    }
                }
                break;
            }
        }
    }
}
