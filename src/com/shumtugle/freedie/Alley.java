package com.shumtugle.freedie;

import java.util.ArrayList;
import java.util.Random;

/**
 * The alley: bowling on the street behind the player, at the crossroads before the 21st, with
 * her at the far end of the street on a stage, at a console like a disc jockey's. She does not
 * come near. Turned round to her, the game begins: her hammer comes down on the console, the
 * lights go, and a figure falls out of the sky onto the street, block after block, as the
 * watchers throw the buildings down; another blow, and a ball is shot up off the stage in a
 * high arc and comes down at the player's feet, rolls into his hand. Three balls to a figure,
 * rolled with a swipe up the screen. Most of it down: lasers, strobes, a roll of beats; the
 * rubble goes up in smoke and the next figure falls, farther off. A figure left standing after
 * the third ball: she throws her golden hammer at it from the stage and it bursts, and the
 * game goes on. Five figures, the near one first; then fireworks over the sea, and the eye
 * turns back to the street. A game played very well, all five down by the player's own balls
 * and few of them, now and then (not always) ends with a golden cube shot from the stage to
 * the player's feet: his, given in the room between rounds.
 *
 * The lane runs down the street behind from the player's feet, turned a little toward the
 * middle of the road; a point of it is u across (to the player's right as he faces down it)
 * and v along. The eye looks straight down it. Every figure is grown fresh and tried: it must
 * stand by itself, and one ball rolled at its middle must bring some of it down but not all.
 * It grows in the background, one figure ahead.
 */
final class Alley {
    // the phases, in the order they come
    static final int OPEN = 0, DROP = 1, FEED = 2, AIM = 3, ROLL = 4, SETTLE = 5, FELLED = 6, SMASH = 7, CLEAR = 8,
            FINALE = 9, DONE = 10;
    static final float FELLED_T = 1.0f, CLEAR_T = 0.9f, FINALE_T = 6.5f;

    static final int ROUNDS = 5, BALLS = 3;
    static final float WIN = 0.7f;           // share of a figure to knock down to fell it
    // the lane: from the player's feet, down the street behind, turned so much toward the road's middle
    static final float LANE_TURN = 0.11f, YAW = (float) Math.PI - LANE_TURN;
    static final float FX = (float) Math.sin(YAW), FZ = (float) Math.cos(YAW), RX = (float) Math.cos(YAW), RZ = -(float) Math.sin(YAW);
    static final float K = 2f;               // figures are built at this size
    static final float BALL_R = 0.34f;
    static final float BLOCK_MASS = 0.12f, BALL_MASS = 5f;
    // the stage at the end of the street: where along the lane, its floor, and she behind the console
    static final float STAGE_V = 42.5f, STAGE_Y = 1.2f, HER_V = STAGE_V + 1.6f;
    // a ball shot off the stage: how long it flies, where it comes down, how long it then rolls
    static final float LOB_T = 1.5f, LOB_V = 5.5f, LOB_TOP = 13f, IN_T = 0.55f;
    // her golden hammer thrown at a figure left standing
    static final float THROW_T = 1.0f;
    // a game played very well: all five down by the player's balls, no more than so many; and then a chance
    static final int FEW_BALLS = 8;
    static final float CUBE_ODDS = 0.5f;

    /** How far down the lane the figure of a round stands. */
    static float figureV(int round) {
        return 12f + 5f * round;
    }

    int phase = OPEN;
    float phaseT;
    int round, ball;
    int won;                                // figures felled by the player
    int used;                               // balls rolled in all
    float standing = 1f;                    // share of the figure still standing
    float need = WIN;                       // share to fell; the stand may ask for less
    float flash;                            // the strobes: a cold white light over everything, fading
    final Physics phys = new Physics();
    final ArrayList<Body> blocks = new ArrayList<Body>();
    final ArrayList<Body> balls = new ArrayList<Body>();
    private boolean cubeDue;                // the cube is to come in the finale
    private boolean fedEarly, ballIn;       // the first ball of a figure sent as it falls, and come
    boolean cubeSure;                       // the stand may ask for it, however the game went

    private final World.Host host;
    private final Game game;
    final Frida f;
    final Flow flow;
    private final Random rnd;
    private final int[] order = new int[ROUNDS];
    private final float[] q = new float[4], t9 = new float[9];

    // her blows on the console, and the lights they set off
    private float blowT = -1f;              // seconds into a blow
    private boolean blowLanded;
    private int blowFor;                    // what the blow is for
    private static final int FOR_FIGURE = 0, FOR_BALL = 1, FOR_CLEAR = 2, FOR_CUBE = 3;
    private float lights;                   // how hard the lights are going, 0 to 1, fading
    private float strobeT = -1f;            // strobes: seconds since they began
    private float clock;
    // smoke puffs: where, how old
    private final float[] smX = new float[40], smY = new float[40], smZ = new float[40], smAge = new float[40], smR = new float[40];
    private int smN;
    // sparks: fireworks, and the golden hammer's trail
    private static final int SPARKS = 600;
    private final float[] spX = new float[SPARKS], spY = new float[SPARKS], spZ = new float[SPARKS];
    private final float[] spVx = new float[SPARKS], spVy = new float[SPARKS], spVz = new float[SPARKS], spAge = new float[SPARKS],
            spLife = new float[SPARKS];
    private final int[] spCol = new int[SPARKS];
    private final boolean[] spBig = new boolean[SPARKS];   // far off in the sky: bigger, to be seen
    private int spN;
    private float nextBurst;

    // the figure falling from the sky: each block's delay and height
    private float[] dropAt = new float[0], dropH = new float[0];
    private boolean[] landed = new boolean[0];
    private static final float SKY_G = 26f;
    private static final float[] SKY_H = {16f, 11f, 6f};

    // a ball on its way to the player, or the cube
    private float lobT = -1f;
    private boolean lobCube;
    // her golden hammer in flight
    private float throwT = -1f;
    private final float[] pose = new float[12];

    // the swipe
    private boolean touching;
    private float downX, downY, downT, fingerX;
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
        if (host != null) prepare(0);
        go(OPEN);
        blow(FOR_FIGURE);
        lights = 1f;
        host.sound(Synth.BEAT, 0.9f, 0.8f);
    }

    /** A grower alone, for the background: no world, no her. */
    private Alley(long seed) {
        host = null;
        game = null;
        f = null;
        flow = null;
        rnd = new Random(seed);
    }

    /** A point of the lane, across u and along v from the player's feet, into the world. */
    static float wx(float u, float v) {
        return World.CAM_X + RX * u + FX * v;
    }

    static float wz(float u, float v) {
        return World.CAM_Z + RZ * u + FZ * v;
    }

    /** Where a world point is along the lane. */
    static float vOf(float x, float z) {
        return (x - World.CAM_X) * FX + (z - World.CAM_Z) * FZ;
    }

    private void go(int p) {
        phase = p;
        phaseT = 0f;
    }

    boolean done() {
        return phase == DONE;
    }

    /** Whether a ball can be rolled now. */
    boolean aiming() {
        return phase == AIM;
    }

    // the view closes in on far figures, as it does on far plinths: so much narrower, eased
    private float zoomNow = 1f;

    /** How much narrower the view is now: by the figure's distance, none for the first. */
    float zoom() {
        return zoomNow;
    }

    private void zoomStep(float dt) {
        float v = phase == FINALE || phase == DONE ? 12f : figureV(round);
        // not so far that the stage behind comes near: she stays far off at the end of the street
        float want = Math.max(1f, Math.min(1.5f, v / 12f * 0.85f));
        zoomNow += (want - zoomNow) * Math.min(1f, dt * 2.5f);
    }

    // ---- time ----

    void update(float dt) {
        phaseT += dt;
        clock += dt;
        flash = Math.max(0f, flash - dt * 9f);
        lights = Math.max(0f, lights - dt * 0.6f);
        handSpin += dt * 0.6f;
        float want = touching ? Math.max(-1f, Math.min(1f, (fingerX - World.W * 0.5f) / (World.W * 0.4f))) : 0f;
        handX += (want - handX) * Math.min(1f, dt * 10f);
        flow.update(dt, 0f, 0f);
        zoomStep(dt);
        blowStep(dt);
        strobes(dt);
        smoke(dt);
        sparks(dt);
        lobStep(dt);
        throwStep(dt);
        switch (phase) {
            case OPEN:
                break;
            case DROP:
                dropStep();
                // the first ball is sent while the last blocks are still coming down
                if (!fedEarly && phaseT > dropEnd() - 0.6f) {
                    fedEarly = true;
                    blow(FOR_BALL);
                }
                if (phaseT > dropEnd() + 0.35f) {
                    for (Body b : blocks) {
                        b.setPose(b.homeX, b.homeY, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
                        b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0f;
                        b.shown = 1f;
                        b.asleep = true;
                        b.savePrev();
                        phys.add(b);
                    }
                    ball = 0;
                    standing = 1f;
                    go(ballIn ? AIM : FEED);
                    ballIn = false;
                }
                break;
            case FEED:
                break;
            case AIM:
                break;
            case ROLL: {
                Body b = balls.get(balls.size() - 1);
                phys.step(dt);
                standing = 1f - knockedShare();
                boolean stopped = b.speed2() < 0.09f && phaseT > 0.8f;
                // at the figure, what it does now is settling
                boolean past = vOf(b.px, b.pz) > figureV(round) - 0.5f * K;
                if (vOf(b.px, b.pz) > figureV(round) + 6f * K) {
                    b.gone = true;
                    b.ghost = true;
                }
                if (stopped || past || b.gone || phaseT > 3f) go(SETTLE);
                break;
            }
            case SETTLE:
                phys.step(dt);
                standing = 1f - knockedShare();
                if (phaseT > 1.1f && (still() || phaseT > 2.6f)) count();
                break;
            case FELLED:
            case SMASH:
                phys.step(dt);
                if (phase == FELLED && phaseT > FELLED_T || phase == SMASH && throwT < 0f && phaseT > THROW_T + 1.0f) {
                    if (round >= ROUNDS - 1) finale();
                    else {
                        blow(FOR_CLEAR);
                        go(CLEAR);
                    }
                }
                break;
            case CLEAR:
                phys.step(dt);
                // the rubble shrinks away in the smoke
                for (Body b : blocks) b.shown = Math.max(0f, 1f - Math.max(0f, phaseT - 0.35f) / 0.5f);
                for (Body b : balls) if (phaseT > 0.85f) b.gone = true;
                if (phaseT > CLEAR_T) {
                    blocks.clear();
                    balls.clear();
                    phys.clear();
                    round++;
                    blow(FOR_FIGURE);
                    go(OPEN);
                }
                break;
            case FINALE:
                phys.step(dt);
                if ((nextBurst -= dt) <= 0f && phaseT < FINALE_T - 2f) {
                    burst();
                    nextBurst = 0.35f + rnd.nextFloat() * 0.5f;
                }
                if (cubeDue && phaseT > 1.6f && lobT < 0f) {
                    cubeDue = false;
                    blow(FOR_CUBE);
                }
                if (phaseT > FINALE_T && lobT < 0f && blowT < 0f) go(DONE);
                break;
            default:
                break;
        }
    }

    private boolean still() {
        for (Body b : blocks) if (!b.asleep && b.speed2() > 0.02f) return false;
        return true;
    }

    /** A pin: a tall stone pillar, pale, with red bands round its neck. */
    static boolean isPin(Body b) {
        return b.hy > 2.5f * b.hx && b.hy > 2.5f * b.hz;
    }

    // ---- her blows on the console ----

    /** Her hammer comes down on the console; when it lands, what it is for happens. */
    private void blow(int what) {
        blowT = 0f;
        blowLanded = false;
        blowFor = what;
    }

    private static final float WIND = 0.26f, SLAM = 0.1f, BLOW_T = 0.75f;

    private void blowStep(float dt) {
        if (blowT < 0f) return;
        blowT += dt;
        if (!blowLanded && blowT >= WIND + SLAM) {
            blowLanded = true;
            lights = 1f;
            host.sound(Synth.BEAT, 1f, blowFor == FOR_BALL ? 1.1f : 0.85f);
            host.sound(Synth.WOOD, 0.6f, 0.5f);
            host.buzz(30);
            puff(wx(-3.6f, STAGE_V), STAGE_Y, wz(-3.6f, STAGE_V), 1.2f);
            puff(wx(3.6f, STAGE_V), STAGE_Y, wz(3.6f, STAGE_V), 1.2f);
            switch (blowFor) {
                case FOR_FIGURE:
                    drop();
                    break;
                case FOR_BALL:
                case FOR_CUBE:
                    lob(blowFor == FOR_CUBE);
                    break;
                case FOR_CLEAR:
                    // the rubble goes up in smoke
                    for (int i = 0; i < 9; i++)
                        puff(wx((rnd.nextFloat() - 0.5f) * 3f * K, figureV(round) + (rnd.nextFloat() - 0.3f) * 2f * K), 0.5f,
                                wz((rnd.nextFloat() - 0.5f) * 3f * K, figureV(round)), 1.4f + rnd.nextFloat());
                    host.sound(Synth.WHOOSH, 0.9f, 0.6f);
                    break;
                default:
                    break;
            }
        }
        if (blowT >= BLOW_T) blowT = -1f;
    }

    /** Her arms for the blow, over what the flow gave them: up over her head, and down onto the console. */
    private void blowPose() {
        if (blowT < 0f) return;
        float t = blowT, up;
        if (t < WIND) {
            float k = t / WIND;
            up = k * k * (3f - 2f * k);
            f.ax[Frida.ARM_A] = mix(f.ax[Frida.ARM_A], 3.2f, up);
            f.ax[Frida.FORE_A] = mix(f.ax[Frida.FORE_A], 0.35f, up);
            f.ax[Frida.CHEST] = mix(f.ax[Frida.CHEST], 0.1f, up);
        } else if (t < WIND + SLAM) {
            float k = (t - WIND) / SLAM;
            f.ax[Frida.ARM_A] = mix(3.2f, 1.35f, k * k);
            f.ax[Frida.FORE_A] = mix(0.35f, 0.1f, k);
            f.ax[Frida.HAMMER] = mix(1.0f, 0.15f, k);
            f.ax[Frida.CHEST] = mix(0.1f, -0.4f, k);
        } else {
            float k = Math.min(1f, (t - WIND - SLAM) / (BLOW_T - WIND - SLAM));
            float e = k * k * (3f - 2f * k);
            f.ax[Frida.ARM_A] = mix(1.35f, f.ax[Frida.ARM_A], e);
            f.ax[Frida.FORE_A] = mix(0.1f, f.ax[Frida.FORE_A], e);
            f.ax[Frida.HAMMER] = mix(0.15f, f.ax[Frida.HAMMER], e);
            f.ax[Frida.CHEST] = mix(-0.4f, f.ax[Frida.CHEST], e);
        }
    }

    private static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    // ---- the figure out of the sky ----

    /** The figure of the round is taken from the grower and thrown down, block after block, the lowest first. */
    private void drop() {
        fedEarly = false;
        ballIn = false;
        blocks.clear();
        phys.clear();
        ArrayList<Body> grown = takeGrown(round);
        if (grown != null) blocks.addAll(grown);
        else {
            v0 = figureV(round);
            build(order[round]);
        }
        prepare(round + 1);
        int n = blocks.size();
        dropAt = new float[n];
        dropH = new float[n];
        landed = new boolean[n];
        int[] rank = ranks();
        for (int i = 0; i < n; i++) {
            dropAt[i] = 0.1f + rank[i] * Math.min(0.06f, 1.5f / Math.max(1, n));
            dropH[i] = SKY_H[rank[i] % 3];
        }
        go(DROP);
        dropStep();
    }

    private float dropEnd() {
        float m = 0f;
        for (int i = 0; i < dropAt.length; i++) m = Math.max(m, dropAt[i] + (float) Math.sqrt(2f * dropH[i] / SKY_G));
        return m;
    }

    /** Each block down out of the sky into its place, under a brisk pull, a puff of dust where it lands. */
    private void dropStep() {
        int n = blocks.size();
        for (int i = 0; i < n; i++) {
            Body b = blocks.get(i);
            float t = phaseT - dropAt[i];
            if (t < 0f) {
                b.shown = 0f;
                continue;
            }
            float fall = (float) Math.sqrt(2f * dropH[i] / SKY_G);
            float drop = t < fall ? dropH[i] - 0.5f * SKY_G * t * t : 0f;
            b.setPose(b.homeX, b.homeY + drop, b.homeZ, b.homeQw, b.homeQx, b.homeQy, b.homeQz);
            b.shown = Math.min(1f, t / 0.12f);
            if (t >= fall && !landed[i]) {
                landed[i] = true;
                boolean last = true;
                for (boolean l : landed) last &= l;
                if (last) {
                    host.sound(Synth.GROUND, 1.0f, 0.65f);
                    host.buzz(40);
                } else if ((i & 1) == 0) host.sound(Synth.TOCK, 0.3f, 0.7f + rnd.nextFloat() * 0.4f);
                game.spawn(b.homeX, 0.05f, b.homeZ, true, 0xC9BFAE, 0.8f);
            }
        }
    }

    /** The order the figure's blocks come in: the lowest first, and of those the nearest the player. */
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

    // ---- a ball from the stage ----

    /** She sends the next ball. */
    private void feed() {
        go(FEED);
        blow(FOR_BALL);
    }

    private void lob(boolean cube) {
        lobT = 0f;
        lobCube = cube;
        host.sound(Synth.WHOOSH, 0.9f, cube ? 1.3f : 0.8f);
    }

    /**
     * The ball shot up off the stage in a high arc over the figure, down at the player's feet
     * with a thud, and rolling the last of the way into his hand; the cube the same way, and
     * into the rack.
     */
    private void lobStep(float dt) {
        if (lobT < 0f) return;
        float was = lobT;
        lobT += dt;
        if (was < LOB_T && lobT >= LOB_T) {
            host.sound(lobCube ? Synth.CATCH : Synth.GROUND, lobCube ? 0.9f : 0.8f, lobCube ? 1.3f : 0.9f);
            host.buzz(25);
            game.spawn(wx(0f, LOB_V), 0.05f, wz(0f, LOB_V), true, 0xC9BFAE, 1f);
        }
        if (lobT >= LOB_T + IN_T) {
            lobT = -1f;
            if (lobCube) {
                game.alleyCube();
                host.sound(Synth.CATCH, 0.8f, 1.6f);
            } else if (phase == FEED) go(AIM);
            else ballIn = true;
        }
    }

    /** Where the ball (or the cube) on its way is now, into out. */
    private void lobAt(float[] out) {
        float r = lobCube ? 0.26f : BALL_R;
        if (lobT < LOB_T) {
            float k = lobT / LOB_T;
            float v = mix(STAGE_V - 1.2f, LOB_V, k);
            float y0 = STAGE_Y + 1.6f, y = mix(y0, r, k) + LOB_TOP * 4f * k * (1f - k);
            out[0] = wx(0f, v);
            out[1] = y;
            out[2] = wz(0f, v);
        } else {
            float k = Math.min(1f, (lobT - LOB_T) / IN_T);
            float e = 1f - (1f - k) * (1f - k);
            float v = mix(LOB_V, 1.6f, e);
            // a little hop off the ground as it lands
            float hop = 0.25f * (float) Math.abs(Math.sin(k * Math.PI)) * (1f - k);
            out[0] = wx(0f, v);
            out[1] = r + hop;
            out[2] = wz(0f, v);
        }
    }

    // ---- counting ----

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

    /** After each ball: felled, and the lights go; else the next ball; else her golden hammer. */
    private void count() {
        float s = knockedShare();
        standing = 1f - s;
        if (s >= need) {
            won++;
            felledAt[round] = true;
            host.sound(Synth.thunder(rnd.nextInt(Synth.THUNDERS)), ball == 0 ? 1f : 0.7f, ball == 0 ? 1f : 1.2f);
            host.buzz(ball == 0 ? 90 : 50);
            lights = 1f;
            strobeT = 0f;
            go(FELLED);
            return;
        }
        if (ball < BALLS - 1) {
            ball++;
            feed();
            return;
        }
        // it stands: she throws her golden hammer at it from the stage
        throwT = 0f;
        f.holding = false;
        host.sound(Synth.WHOOSH, 1f, 0.6f);
        go(SMASH);
    }

    // ---- her golden hammer ----

    private void throwStep(float dt) {
        if (throwT < 0f) return;
        float was = throwT;
        throwT += dt;
        if (throwT < THROW_T) {
            hammerAt(throwT / THROW_T);
            // a trail of gold behind it
            for (int i = 0; i < 3; i++) spark(pose[0], pose[1], pose[2], (rnd.nextFloat() - 0.5f) * 1.5f, rnd.nextFloat() * 1.5f,
                    (rnd.nextFloat() - 0.5f) * 1.5f, 0xFFC03A, 0.5f);
        }
        if (was < THROW_T && throwT >= THROW_T) {
            // it bursts the figure apart
            float cx = wx(0f, figureV(round)), cz = wz(0f, figureV(round));
            for (Body b : blocks) {
                float dx = b.px - cx, dz = b.pz - cz, l = (float) Math.sqrt(dx * dx + dz * dz) + 0.3f;
                b.asleep = false;
                b.vx += dx / l * 6f + (rnd.nextFloat() - 0.5f) * 3f;
                b.vz += dz / l * 6f + FX * 3f;
                b.vy += 5f + rnd.nextFloat() * 4f;
                b.wx += (rnd.nextFloat() - 0.5f) * 8f;
                b.wz += (rnd.nextFloat() - 0.5f) * 8f;
            }
            for (int i = 0; i < 120; i++) spark(cx, 1.5f, cz, (rnd.nextFloat() - 0.5f) * 14f, rnd.nextFloat() * 10f,
                    (rnd.nextFloat() - 0.5f) * 14f, 0xFFB428, 1.2f);
            host.sound(Synth.CRASH, 1.1f, 0.6f);
            host.sound(Synth.THUNDER, 1f, 0.9f);
            host.buzz(90);
            flash = Math.max(flash, 1f);
            lights = 1f;
        }
        if (throwT >= THROW_T + 0.6f) {
            throwT = -1f;
            f.holding = true;
        }
    }

    /** The golden hammer along its throw, k from 0 (her fist) to 1 (the figure), turning over and over. */
    private void hammerAt(float k) {
        float x0 = wx(1.2f, HER_V - 0.8f), y0 = STAGE_Y + 5f, z0 = wz(1.2f, HER_V - 0.8f);
        float x1 = wx(0f, figureV(round)), y1 = 1.4f * K, z1 = wz(0f, figureV(round));
        float y = mix(y0, y1, k) + 6f * 4f * k * (1f - k);
        float a = k * 14f;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        pose[0] = mix(x0, x1, k);
        pose[1] = y;
        pose[2] = mix(z0, z1, k);
        // turning end over end about the lane's across
        pose[3] = c; pose[4] = -s; pose[5] = 0f;
        pose[6] = s; pose[7] = c; pose[8] = 0f;
        pose[9] = 0f; pose[10] = 0f; pose[11] = 1f;
    }

    // ---- the finale ----

    private void finale() {
        // a game played very well, and luck with it: the cube
        cubeDue = cubeSure || won >= ROUNDS && used <= FEW_BALLS && rnd.nextFloat() < CUBE_ODDS;
        game.journalLine(String.format(java.util.Locale.ROOT, "the alley over: %d of %d felled, %d balls%s", won, ROUNDS, used,
                cubeDue ? "; a golden cube to come" : ""));
        nextBurst = 0.3f;
        strobeT = 0f;
        lights = 1f;
        host.sound(Synth.ROAR, 0.6f, 1.2f);
        go(FINALE);
    }

    /** A rocket bursting high over the end of the street: a ball of sparks of one colour, falling. */
    private void burst() {
        int[] cols = {0xFF4A3A, 0x4AE0FF, 0xFFE04A, 0x8AFF6A, 0xFF7AE0, 0xFFFFFF};
        int c = cols[rnd.nextInt(cols.length)];
        float u = (rnd.nextFloat() - 0.5f) * 30f, v = STAGE_V + 15f + rnd.nextFloat() * 25f, y = 22f + rnd.nextFloat() * 14f;
        float x = wx(u, v), z = wz(u, v);
        for (int i = 0; i < 70; i++) {
            float a = rnd.nextFloat() * 6.283f, b = (float) Math.acos(2f * rnd.nextFloat() - 1f);
            float sp = 14f + rnd.nextFloat() * 3f;
            spark(x, y, z, sp * (float) (Math.sin(b) * Math.cos(a)), sp * (float) Math.cos(b), sp * (float) (Math.sin(b) * Math.sin(a)), c,
                    1.4f + rnd.nextFloat() * 0.6f);
        }
        host.sound(Synth.POP, 0.7f, 0.6f + rnd.nextFloat() * 0.5f);
        host.sound(Synth.THUNDER, 0.35f, 1.6f + rnd.nextFloat() * 0.4f);
    }

    private void spark(float x, float y, float z, float vx, float vy, float vz, int col, float life) {
        if (spN >= SPARKS) return;
        int i = spN++;
        spX[i] = x;
        spY[i] = y;
        spZ[i] = z;
        spVx[i] = vx;
        spVy[i] = vy;
        spVz[i] = vz;
        spAge[i] = 0f;
        spLife[i] = life;
        spCol[i] = col;
        spBig[i] = y > 15f;
    }

    private void sparks(float dt) {
        for (int i = 0; i < spN; i++) {
            spAge[i] += dt;
            if (spAge[i] >= spLife[i]) {
                spN--;
                spX[i] = spX[spN]; spY[i] = spY[spN]; spZ[i] = spZ[spN];
                spVx[i] = spVx[spN]; spVy[i] = spVy[spN]; spVz[i] = spVz[spN];
                spAge[i] = spAge[spN]; spLife[i] = spLife[spN]; spCol[i] = spCol[spN]; spBig[i] = spBig[spN];
                i--;
                continue;
            }
            float drag = 1f - dt * 1.6f;
            spVx[i] *= drag;
            spVy[i] = spVy[i] * drag - 4f * dt;
            spVz[i] *= drag;
            spX[i] += spVx[i] * dt;
            spY[i] += spVy[i] * dt;
            spZ[i] += spVz[i] * dt;
        }
    }

    // ---- smoke ----

    private void puff(float x, float y, float z, float r) {
        if (smN >= smX.length) return;
        int i = smN++;
        smX[i] = x;
        smY[i] = y;
        smZ[i] = z;
        smR[i] = r;
        smAge[i] = 0f;
    }

    private void smoke(float dt) {
        for (int i = 0; i < smN; i++) {
            smAge[i] += dt;
            smY[i] += dt * 0.6f;
            if (smAge[i] > 1.6f) {
                smN--;
                smX[i] = smX[smN]; smY[i] = smY[smN]; smZ[i] = smZ[smN]; smR[i] = smR[smN]; smAge[i] = smAge[smN];
                i--;
            }
        }
    }

    /** Strobes: four white flashes in quick time when a figure goes down, more in the finale. */
    private void strobes(float dt) {
        if (strobeT < 0f) return;
        float was = strobeT;
        strobeT += dt;
        float beat = 0.12f;
        int n = phase == FINALE ? 10 : 4;
        if ((int) (was / beat) != (int) (strobeT / beat) && ((int) (strobeT / beat) & 1) == 0) flash = Math.max(flash, 1f);
        if (strobeT > n * 2 * beat) strobeT = -1f;
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
        // and the next one: another of the set, never the same color twice running
        int c;
        do c = Builder.BALL_COLORS[rnd.nextInt(Builder.BALL_COLORS.length)];
        while (c == handColor);
        handColor = c;
        // along the lane, turned a little across it
        float across = (float) Math.sin(ang), along = (float) Math.cos(ang);
        b.vx = (RX * across + FX * along) * speed;
        b.vz = (RZ * across + FZ * along) * speed;
        b.vy = 0f;
        b.wx = b.vz / BALL_R;
        b.wz = -b.vx / BALL_R;
        b.asleep = false;
        b.savePrev();
        phys.add(b);
        balls.add(b);
        used++;
        host.sound(Synth.ROLL, Math.min(1.2f, 0.5f + speed / 12f), 0.8f + speed / 45f);
        host.buzz(15);
        handX = 0f;
        go(ROLL);
    }

    // ---- drawing ----

    private final float[] turn = new float[9], at = new float[3];

    /** The stage and her, the figure, the balls, what flies, the smoke, the sparks, the lights, and the ball in hand. */
    void draw(DrawList d) {
        stage(d, clock, lights);
        flow.pose();
        blowPose();
        f.pose();
        f.draw(d);
        for (Body b : blocks) {
            if (b.shown <= 0f) continue;
            rot(b);
            float s = b.shown;
            int mat = b.tint == BRICK || b.tint == BRICK2 ? DrawList.GRAIN : DrawList.STONE;
            d.box(b.px, b.py, b.pz, b.hx * s, b.hy * s, b.hz * s, turn, b.tint & 0xFFFFFF, mat);
            if (isPin(b)) {
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
        // the ball (or the cube) on its way from the stage
        if (lobT >= 0f) {
            lobAt(at);
            float a = lobT * 6f, c = (float) Math.cos(a), s = (float) Math.sin(a);
            turn[0] = 1f; turn[1] = 0f; turn[2] = 0f;
            turn[3] = 0f; turn[4] = c; turn[5] = -s;
            turn[6] = 0f; turn[7] = s; turn[8] = c;
            if (lobCube) d.box(at[0], at[1], at[2], 0.26f, 0.26f, 0.26f, turn, 0xFFB428, DrawList.FIRE_GOLD);
            else d.ball(at[0], at[1], at[2], BALL_R, turn, handColor & 0xFFFFFF, DrawList.POLISHED);
        }
        // her golden hammer, thrown: the golden hammer, the same everywhere
        if (throwT >= 0f && throwT < THROW_T) Hammer.draw(d, pose, Hammer.GOLDEN, 1f);
        // smoke: soft grey balls swelling and rising
        for (int i = 0; i < smN; i++) {
            float k = smAge[i] / 1.6f, r = smR[i] * (0.5f + 0.9f * k) * (k > 0.7f ? (1f - k) / 0.3f : 1f);
            turn[0] = 1f; turn[1] = 0f; turn[2] = 0f;
            turn[3] = 0f; turn[4] = 1f; turn[5] = 0f;
            turn[6] = 0f; turn[7] = 0f; turn[8] = 1f;
            if (r > 0.02f) d.ball(smX[i], smY[i], smZ[i], r, turn, 0xC8C6CC, DrawList.RUBBER);
        }
        // sparks: little bright cubes
        for (int i = 0; i < spN; i++) {
            float k = spAge[i] / spLife[i], s = (spBig[i] ? 0.4f : 0.13f) * (1f - k * 0.6f);
            d.box(spX[i], spY[i], spZ[i], s, s, s, null, spCol[i], DrawList.GLOW);
        }
        lasers(d);
        // the next ball, held low in front, following the finger: a little one close to the eye,
        // so it is all there at the bottom of the frame; it stays the same however the view closes in
        if (phase == AIM) {
            float z = zoomNow;
            float vx = handX * 0.22f * z, vy = -0.5f * z, vz = 1.05f * z;
            float x = d.eye[0] + d.right[0] * vx + d.up[0] * vy + d.ahead[0] * vz;
            float y = d.eye[1] + d.right[1] * vx + d.up[1] * vy + d.ahead[1] * vz;
            float zz = d.eye[2] + d.right[2] * vx + d.up[2] * vy + d.ahead[2] * vz;
            float c = (float) Math.cos(handSpin), sn = (float) Math.sin(handSpin);
            turn[0] = c; turn[1] = 0f; turn[2] = sn;
            turn[3] = 0f; turn[4] = 1f; turn[5] = 0f;
            turn[6] = -sn; turn[7] = 0f; turn[8] = c;
            d.ball(x, y, zz, HAND_R * z, turn, handColor & 0xFFFFFF, DrawList.POLISHED);
        }
    }

    // the ball in hand, as drawn: small and near, standing for the big one
    static final float HAND_R = 0.13f;

    /** Lasers off the stage while the lights go: thin bright beams sweeping out over the street and up. */
    private void lasers(DrawList d) {
        if (lights < 0.05f && phase != FINALE) return;
        int[] cols = {0x3AFF6A, 0xFF3ADC, 0x3AD8FF};
        float strength = phase == FINALE ? 1f : lights;
        int n = 2 + Math.round(strength * 4f);
        for (int i = 0; i < n; i++) {
            float sx = wx((i - (n - 1) * 0.5f) * 1.4f, STAGE_V + 0.5f), sz = wz((i - (n - 1) * 0.5f) * 1.4f, STAGE_V + 0.5f), sy = STAGE_Y + 6.6f;
            // sweeping: across the street and up and down, each its own way
            float a = (float) Math.sin(clock * (1.3f + i * 0.37f) + i) * 0.7f, b = 0.12f + 0.25f * (0.5f + 0.5f * (float) Math.sin(clock * 0.9f + i * 1.7f));
            // toward the player down the street, turned by a across and lifted by b
            float dx = -FX, dz = -FZ;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float hx = dx * ca - dz * sa, hz = dx * sa + dz * ca;
            float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            float ux = hx * cb, uy = sb, uz = hz * cb;
            beam(d, sx, sy, sz, ux, uy, uz, 34f, 0.03f, cols[i % cols.length]);
        }
    }

    /** A thin bright beam from a point along a unit direction, so long and so thick. */
    private void beam(DrawList d, float x, float y, float z, float ux, float uy, float uz, float len, float th, int col) {
        // a frame for the box: its y along the beam
        float ax = 0f, ay = 1f, az = 0f;
        if (Math.abs(uy) > 0.9f) {
            ax = 1f;
            ay = 0f;
        }
        float rx = ay * uz - az * uy, ry = az * ux - ax * uz, rz = ax * uy - ay * ux;
        float l = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= l; ry /= l; rz /= l;
        float fx = uy * rz - uz * ry, fy = uz * rx - ux * rz, fz = ux * ry - uy * rx;
        turn[0] = rx; turn[1] = ux; turn[2] = fx;
        turn[3] = ry; turn[4] = uy; turn[5] = fy;
        turn[6] = rz; turn[7] = uz; turn[8] = fz;
        d.box(x + ux * len * 0.5f, y + uy * len * 0.5f, z + uz * len * 0.5f, th, len * 0.5f, th, turn, col, DrawList.GLOW);
    }

    private static final float[] SR = new float[9];

    /**
     * The stage at the end of the street, there whether a game is on or not: a low dark
     * platform with a strip of light along its edge, speaker stacks either side, a truss over
     * it with lamps, and the console in front of her, its pads lit; all of it brighter as the
     * lights go.
     */
    static void stage(DrawList d, float time, float lit) {
        float a = YAW;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        // turned square to the lane: x across, z along
        SR[0] = c; SR[1] = 0f; SR[2] = s;
        SR[3] = 0f; SR[4] = 1f; SR[5] = 0f;
        SR[6] = -s; SR[7] = 0f; SR[8] = c;
        sbox(d, 0f, STAGE_V + 1.8f, STAGE_Y * 0.5f, 5.2f, STAGE_Y * 0.5f, 2.6f, 0x24232A, DrawList.PLAIN);
        // the edge of light along its front, chasing
        for (int i = 0; i < 13; i++) {
            float k = 0.5f + 0.5f * (float) Math.sin(time * 6f - i * 0.8f);
            int col = Street.mix(0x3A2A50, i % 2 == 0 ? 0xFF4AD0 : 0x4AD0FF, Math.min(1f, 0.25f + k * (0.4f + 0.6f * lit)));
            sbox(d, -4.8f + i * 0.8f, STAGE_V - 0.82f, STAGE_Y - 0.08f, 0.32f, 0.06f, 0.04f, col, DrawList.GLOW);
        }
        // the speaker stacks
        for (int side = -1; side <= 1; side += 2) {
            sbox(d, side * 4.3f, STAGE_V + 1.2f, STAGE_Y + 1.6f, 0.75f, 1.6f, 0.65f, 0x18181C, DrawList.PLAIN);
            for (int k = 0; k < 2; k++)
                sbox(d, side * 4.3f, STAGE_V + 0.53f, STAGE_Y + 0.75f + k * 1.6f, 0.45f, 0.45f, 0.03f, 0x3A3A44, DrawList.PAINT);
        }
        // the truss over it, and its lamps
        for (int side = -1; side <= 1; side += 2) sbox(d, side * 5.0f, STAGE_V + 2.6f, STAGE_Y + 3.6f, 0.12f, 3.6f, 0.12f, 0x8A8E96, DrawList.PLAIN);
        sbox(d, 0f, STAGE_V + 2.6f, STAGE_Y + 7.2f, 5.1f, 0.12f, 0.12f, 0x8A8E96, DrawList.PLAIN);
        for (int i = 0; i < 5; i++) {
            float k = 0.5f + 0.5f * (float) Math.sin(time * 3f + i * 1.3f);
            int col = Street.mix(0x5A5040, 0xFFF0C0, Math.min(1f, 0.3f + lit * k));
            sbox(d, -4f + i * 2f, STAGE_V + 2.6f, STAGE_Y + 6.85f, 0.22f, 0.22f, 0.22f, col, DrawList.GLOW);
        }
        // the console: a dark desk, its pads in two rows lit by turns
        sbox(d, 0f, STAGE_V + 0.4f, STAGE_Y + 0.55f, 2.3f, 0.55f, 0.55f, 0x2E2C34, DrawList.PLAIN);
        for (int i = 0; i < 8; i++)
            for (int r = 0; r < 2; r++) {
                float k = 0.5f + 0.5f * (float) Math.sin(time * 5f + i * 0.9f + r * 2f);
                int[] pads = {0x3AFF6A, 0xFF3ADC, 0x3AD8FF, 0xFFD23A};
                int col = Street.mix(0x2A2A30, pads[(i + r) % 4], Math.min(1f, 0.2f + k * (0.3f + 0.7f * lit)));
                sbox(d, -1.75f + i * 0.5f, STAGE_V + 0.15f + r * 0.4f, STAGE_Y + 1.12f, 0.18f, 0.03f, 0.15f, col, DrawList.GLOW);
            }
    }

    /** A box of the stage, across u and along v, its middle so high, square to the lane. */
    private static void sbox(DrawList d, float u, float v, float y, float hu, float hy, float hv, int col, int mat) {
        d.box(wx(u, v), y, wz(u, v), hu, hy, hv, SR, col, mat);
    }

    private void rot(Body b) {
        turn[0] = b.r00; turn[1] = b.r01; turn[2] = b.r02;
        turn[3] = b.r10; turn[4] = b.r11; turn[5] = b.r12;
        turn[6] = b.r20; turn[7] = b.r21; turn[8] = b.r22;
    }

    /**
     * The alley's corner, without a word, in the world's grain: five squares for the five
     * figures, green for those felled by the player and the one bowled at now blinking; under
     * them the balls left; to the right the bar of what still stands, with the line it must
     * fall below.
     */
    void flat(Px p) {
        int x = 6, y = 8;
        for (int i = 0; i < ROUNDS; i++) {
            int bx = x + i * 17;
            boolean now = i == round && phase < FINALE && i >= doneRounds();
            boolean done = i < doneRounds() && felledAt[i];
            p.rect(bx, y, bx + 14, y + 14, now && ((int) (clock * 3f) & 1) == 0 ? 0xFFFFC93A : 0xFF5A3A0E);
            p.rect(bx + 1, y + 1, bx + 13, y + 13, done ? 0xFF6FBF5A : now ? 0xFFF3E9D2 : i < doneRounds() ? 0xFFC9A04A : 0xFF3A3236);
        }
        if (phase >= FEED && phase <= SETTLE) {
            int left = BALLS - ball - (phase == AIM || phase == FEED ? 0 : 1);
            for (int i = 0; i < left; i++) {
                int cx = x + 5 + i * 13, cy = y + 23;
                for (int dy = -4; dy <= 4; dy++)
                    for (int dx = -4; dx <= 4; dx++)
                        if (dx * dx + dy * dy <= 17) p.set(cx + dx, cy + dy, dx + dy < -3 ? 0xFF5A70B8 : 0xFF22357E);
            }
        }
        // what stands of the figure, with the line to bring it below
        if (phase < FEED || phase > SMASH) return;
        int bw = 58, bx = World.W - bw - 7, by = 9;
        float on = standing;
        p.rect(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF8A5A12);
        p.rect(bx, by, bx + bw, by + 6, 0xFFF3E9D2);
        p.rect(bx, by, bx + Math.round(bw * on), by + 6, on <= 1f - need ? 0xFF6FBF5A : 0xFFE8963C);
        int mark = bx + Math.round(bw * (1f - need));
        p.rect(mark, by - 3, mark + 1, by + 9, 0xFF5A3A0E);
    }

    // which rounds are over, and whether the player felled each
    private final boolean[] felledAt = new boolean[ROUNDS];

    private int doneRounds() {
        return phase == FELLED || phase == SMASH || phase == CLEAR ? round + 1 : phase >= FINALE ? ROUNDS : round;
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
        ball.vx = FX * speed;
        ball.vz = FZ * speed;
        ball.wx = ball.vz / BALL_R;
        ball.wz = -ball.vx / BALL_R;
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
        float a = YAW + turn;
        q[0] = (float) Math.cos(a * 0.5f);
        q[2] = (float) Math.sin(a * 0.5f);
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
