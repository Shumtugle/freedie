package com.shumtugle.freedie;

import java.util.Random;

/**
 * The yard behind the gate in the right-hand houses: a square of old paving shut in by four
 * houses, washing on a line across its far end, and in the middle an air hockey table. The
 * player's hammer, laid flat with its head across the table, is the mallet, and the finger
 * slides it about its own half; across the table another hammer, dark stone bound in brick red, plays against it on
 * its own. A puck on the air, a goal in each end, first to five; then the next game at once,
 * for as long as the player likes. The back key, or the key in the corner, leads out onto the
 * street, before the same building.
 *
 * Table space is the yard's own: x across (to the right), z along the table away from the
 * player, the playing surface at Y; the middle of the table at the origin.
 */
final class Yard {
    // the table: half its width and length, the height of its surface, the goal mouths' half width
    static final float B = 0.6f, L = 1.12f, Y = 0.8f, GOAL = 0.2f;
    // the hammers are drawn so big; their heads, lying across the table, meet the puck as a bar
    // with round ends: half the bar's straight length, and its half depth; the puck's radius
    static final float SCALE = 0.75f, SEG = 0.07f * SCALE, MR = 0.1f * SCALE, PR = 0.045f;
    // how the puck bounces off the rails and off a hammer; how fast it may go; how the air slows it
    static final float RAIL_E = 0.86f, HIT_E = 0.92f, MAX_V = 6.5f, DRAG = 0.22f;
    // the player's hammer follows the finger this fast, at most; the other one runs about this fast
    static final float MY_SPEED = 9f, RIVAL_SPEED = 2.9f, RIVAL_ACC = 16f;
    /** Goals to win a game. */
    static final int WIN = 5;
    /** A goal: the puck gone down the slot, so long before the next is put on the table. */
    static final float GOAL_T = 1.1f, OVER_T = 2.8f;
    /** How far above the finger the hammer is held, frame pixels, so the finger does not hide it. */
    static final float FINGER = 26f;
    // the eye: where it stands over the near end of the table, and how it looks down onto it
    static final float EYE_Y = 2.35f, EYE_Z = -1.85f, LOOK_Z = 0.05f, FOCAL = 1.75f;
    // the key out to the street, in the lower left corner, frame pixels
    static final int KEY_X0 = 8, KEY_W = 66, KEY_H = 26, KEY_UP = 12;

    private final World w;
    private final World.Host host;
    private final Random rnd = new Random(0xA1L);
    private final DrawList still = new DrawList();
    private boolean built;

    // the puck: where, how fast; whether it is on the table
    float px, pz, vx, vz;
    private boolean onTable = true;
    // the player's hammer and where the finger wants it; the other hammer and where it means to go
    float mx, mz = -L * 0.62f, mvx, mvz;
    private float tx, tz = -L * 0.62f;
    private boolean held;
    float rx, rz = L * 0.72f, rvx, rvz;
    // which way each head is turned about the upright, radians: 0 square across the table; the
    // player's turns with the way it is moved, the other's to where it aims
    private float ma, ra, raWant, aimX;
    private float rtx, rtz = L * 0.72f, think, rivalSpeed = RIVAL_SPEED;
    private float rivalAim, myAim, maAuto;
    // the score; a goal just gone in (1 the player's, 2 the other's) and how long ago; a game over
    int mine, theirs;
    private int scored;
    private float goalT = -1f, overT = -1f, glow;
    private float time, tockAt = -1f;
    /** For the desktop stand: the player's hammer played by the same hand as the other one. */
    boolean auto;

    // the eye as last drawn, to turn a finger into a point on the table
    private final float[] eye = new float[3], right = new float[3], up = new float[3], ahead = new float[3];
    private float focal = 1f;
    private int fw = World.W, fh = 640;
    private final float[] pose = new float[12];
    private final float[] hit = new float[2];

    Yard(World w, World.Host host) {
        this.w = w;
        this.host = host;
        px = 0f;
        pz = -0.35f;
    }

    // ---- the finger ----

    void touch(int type, float x, float y) {
        if (type == World.DOWN && x >= KEY_X0 && x <= KEY_X0 + KEY_W && y >= fh - KEY_UP - KEY_H - 4 && y <= fh - KEY_UP + 4) {
            host.sound(Synth.TOCK, 0.8f, 1f);
            host.buzz(15);
            w.leaveYard();
            return;
        }
        if (type == World.UP) {
            held = false;
            return;
        }
        if (onTablePoint(x, y - FINGER, hit)) {
            held = true;
            tx = hit[0];
            tz = hit[1];
        }
    }

    /** The point of the table's plane under a frame point, or false if the eye looks above it. */
    private boolean onTablePoint(float sx, float sy, float[] out) {
        float u = (sx - fw * 0.5f) / focal, v = (fh * 0.5f - sy) / focal;
        float dx = ahead[0] + right[0] * u + up[0] * v, dy = ahead[1] + right[1] * u + up[1] * v, dz = ahead[2] + right[2] * u + up[2] * v;
        if (dy > -1e-4f) return false;
        float t = (Y - eye[1]) / dy;
        out[0] = eye[0] + dx * t;
        out[1] = eye[2] + dz * t;
        return true;
    }

    // ---- the play ----

    void update(float dt) {
        time += dt;
        glow = Math.max(0f, glow - dt);
        if (overT >= 0f) {
            overT += dt;
            if (overT >= OVER_T) {
                // the next game, at once: the score back to nothing, the puck to the player
                overT = -1f;
                mine = 0;
                theirs = 0;
                serve(-1);
                rivalSpeed = RIVAL_SPEED * (0.92f + rnd.nextFloat() * 0.16f);
            }
        }
        if (goalT >= 0f) {
            goalT += dt;
            if (goalT >= GOAL_T && overT < 0f) {
                goalT = -1f;
                // the puck to whoever let the goal in
                serve(scored == 1 ? 1 : -1);
            }
        }
        // where the player's hammer goes: after the finger, or the stand's own hand
        if (auto) {
            plan(-1, dt);
        }
        float ox = mx, oz = mz;
        float nx = clampX(tx), nz = Math.max(-L + MR, Math.min(-MR - 0.01f, tz));
        float ddx = nx - mx, ddz = nz - mz, dd = (float) Math.sqrt(ddx * ddx + ddz * ddz), most = MY_SPEED * dt;
        if (dd > most) {
            ddx *= most / dd;
            ddz *= most / dd;
        }
        if (!held && !auto) {
            ddx = 0f;
            ddz = 0f;
        }
        // the other hammer: thinks a few times a second, runs to where it means to be
        plan(1, dt);
        float rox = rx, roz = rz;
        float ax = (rtx - rx), az = (rtz - rz), ad = (float) Math.sqrt(ax * ax + az * az);
        float want = Math.min(rivalSpeed, ad * 9f);
        float wvx = ad > 1e-4f ? ax / ad * want : 0f, wvz = ad > 1e-4f ? az / ad * want : 0f;
        float cvx = wvx - rvx, cvz = wvz - rvz, cd = (float) Math.sqrt(cvx * cvx + cvz * cvz), acc = RIVAL_ACC * dt;
        if (cd > acc) {
            cvx *= acc / cd;
            cvz *= acc / cd;
        }
        float rvx1 = rvx + cvx, rvz1 = rvz + cvz;
        // the heads turn: the player's with its sideways way, the other toward its aim
        float maWant = auto ? maAuto : Math.max(-0.5f, Math.min(0.5f, (dt > 0f ? ddx / dt : 0f) * 0.11f));
        ma += (maWant - ma) * Math.min(1f, dt * 10f);
        ra += (raWant - ra) * Math.min(1f, dt * 8f);
        // in small steps, so that nothing passes through anything
        int n = 8;
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            mx += ddx / n;
            mz += ddz / n;
            rx = clampX(rx + rvx1 * h);
            rz = Math.max(MR + 0.01f, Math.min(L - MR, rz + rvz1 * h));
            if (onTable && goalT < 0f) puckStep(h, dt > 0f ? ddx / dt : 0f, dt > 0f ? ddz / dt : 0f, rvx1, rvz1);
        }
        mvx = dt > 0f ? (mx - ox) / dt : 0f;
        mvz = dt > 0f ? (mz - oz) / dt : 0f;
        rvx = dt > 0f ? (rx - rox) / dt : 0f;
        rvz = dt > 0f ? (rz - roz) / dt : 0f;
    }

    private static float clampX(float x) {
        return Math.max(-B + MR + SEG, Math.min(B - MR - SEG, x));
    }

    /** The puck put down in the middle of one half, still: -1 the player's, 1 the other's. */
    private void serve(int side) {
        rivalAim = newAim();
        myAim = newAim();
        px = 0f;
        pz = side * 0.38f;
        vx = 0f;
        vz = 0f;
        onTable = true;
    }

    private void puckStep(float h, float mvxNow, float mvzNow, float rvxNow, float rvzNow) {
        float k = (float) Math.exp(-DRAG * h);
        vx *= k;
        vz *= k;
        px += vx * h;
        pz += vz * h;
        // the rails along the sides
        if (px > B - PR) {
            px = B - PR;
            if (vx > 0f) bounce(vx = -vx * RAIL_E);
        } else if (px < -B + PR) {
            px = -B + PR;
            if (vx < 0f) bounce(vx = -vx * RAIL_E);
        }
        // the ends: the rail, or the mouth of a goal
        boolean mouth = Math.abs(px) < GOAL - PR * 0.4f;
        if (pz > L - PR && !mouth) {
            pz = L - PR;
            if (vz > 0f) bounce(vz = -vz * RAIL_E);
        } else if (pz < -L + PR && !mouth) {
            pz = -L + PR;
            if (vz < 0f) bounce(vz = -vz * RAIL_E);
        }
        if ((pz > 0f) != (pz - vz * h > 0f)) {
            // over the middle: the side it comes to picks a new aim
            if (pz > 0f) rivalAim = newAim();
            else myAim = newAim();
        }
        if (pz > L + PR) goal(1);
        else if (pz < -L - PR) goal(2);
        knock(mx, mz, mvxNow, mvzNow, true);
        knock(rx, rz, rvxNow, rvzNow, false);
        float s = (float) Math.sqrt(vx * vx + vz * vz);
        if (s > MAX_V) {
            vx *= MAX_V / s;
            vz *= MAX_V / s;
        }
    }

    private void bounce(float v) {
        float s = Math.abs(v);
        if (s > 0.25f && time - tockAt > 0.05f) {
            tockAt = time;
            host.sound(Synth.PLASTIC, Math.min(0.8f, 0.2f + s * 0.12f), 1.3f);
        }
    }

    /** A hammer's head meets the puck: it is put out of the head and goes off it, with the head's own way added. */
    private void knock(float hx, float hz, float hvx, float hvz, boolean player) {
        // the nearest point of the bar along the head, turned as the head is
        float a = player ? ma : ra, ex = (float) Math.cos(a), ez = -(float) Math.sin(a);
        float along = Math.max(-SEG, Math.min(SEG, (px - hx) * ex + (pz - hz) * ez));
        float cx = hx + ex * along, cz = hz + ez * along;
        float dx = px - cx, dz = pz - cz, d2 = dx * dx + dz * dz, r = PR + MR;
        if (d2 >= r * r || d2 < 1e-10f) return;
        float d = (float) Math.sqrt(d2), nx = dx / d, nz = dz / d;
        px = cx + nx * r;
        pz = cz + nz * r;
        float rvn = (vx - hvx) * nx + (vz - hvz) * nz;
        if (rvn >= 0f) return;
        vx -= (1f + HIT_E) * rvn * nx;
        vz -= (1f + HIT_E) * rvn * nz;
        float s = -rvn;
        if (time - tockAt > 0.06f) {
            tockAt = time;
            host.sound(Synth.TOCK, Math.min(1f, 0.3f + s * 0.15f), player ? 1.2f : 1.0f);
            if (player) host.buzz(Math.min(30, 8 + (int) (s * 4f)));
        }
    }

    /** A goal: 1 the player's, in the far end; 2 the other's, in the near one. */
    private void goal(int who) {
        onTable = false;
        scored = who;
        goalT = 0f;
        if (who == 1) {
            mine++;
            glow = 0.9f;
            host.sound(Synth.CHIME, 0.9f, 1.3f);
            host.buzz(40);
        } else {
            theirs++;
            host.sound(Synth.TOCK, 0.6f, 0.6f);
        }
        if (mine >= WIN || theirs >= WIN) {
            overT = 0f;
            if (mine >= WIN) {
                // a game won: the score glows; kept, for what the yard may pay one day
                glow = 2.4f;
                host.sound(Synth.CHIME, 1f, 1f);
                host.sound(Synth.BEAT, 0.8f, 1.2f);
                host.saveInt("hockeyWins", host.loadInt("hockeyWins") + 1);
            }
        }
    }

    /**
     * Where a hammer means to go: side 1 the other's, -1 the player's (the stand's own hand).
     * The puck on its own half: get behind it, on the line from the far goal, and strike
     * through it; on the other half: stand in front of its own goal, following the puck across.
     */
    private void plan(int side, float dt) {
        if (side > 0) {
            think -= dt;
            if (think > 0f) return;
            think = 0.09f + rnd.nextFloat() * 0.06f;
        }
        float hx = side > 0 ? rx : mx, hz = side > 0 ? rz : mz;
        // in its own space: z toward its own goal
        float qz = pz * side, qx = px, hzz = hz * side;
        float gx, gz;
        boolean own = qz > 0.02f && onTable && goalT < 0f;
        if (own) {
            if (side > 0) aimX = rivalAim;
            else aimX = myAim;
            // from the far goal through the puck, and on past it
            float ux = qx - aimX, uz = qz + L, ul = (float) Math.sqrt(ux * ux + uz * uz);
            ux /= ul;
            uz /= ul;
            float bx = qx + ux * (PR + MR + 0.04f), bz = qz + uz * (PR + MR + 0.04f);
            float behind = (hx - qx) * ux + (hzz - qz) * uz;
            float off = Math.abs((hx - qx) * uz - (hzz - qz) * ux);
            if (behind > (PR + MR) * 0.4f && off < SEG + MR * 0.5f) {
                // behind it and in line: through it
                gx = qx - ux * 0.22f;
                gz = qz - uz * 0.22f;
            } else if (behind < 0f && off < PR + MR + SEG + 0.02f) {
                // the wrong side of it: round it, not through it toward its own goal
                gx = qx + (hx > qx ? 1f : -1f) * (PR + MR + SEG + 0.06f);
                gz = qz + 0.05f;
            } else {
                gx = bx;
                gz = bz;
            }
        } else {
            gx = Math.max(-0.3f, Math.min(0.3f, px * 0.5f));
            gz = L - 0.24f;
        }
        gx += (rnd.nextFloat() - 0.5f) * 0.03f;
        // the head turned so its face looks from the puck to where it aims
        float fa = 0f;
        if (own) {
            float fx = aimX - qx, fz = -L - qz;
            fa = Math.max(-0.6f, Math.min(0.6f, (float) Math.atan2(fx, -fz)));
        }
        if (side > 0) {
            rtx = clampX(gx);
            rtz = Math.max(MR + 0.01f, Math.min(L - MR, gz));
            raWant = -fa;
        } else {
            tx = gx;
            tz = -gz;
            maAuto = fa;
        }
    }

    /** A new aim, now and then: straight into the goal, or off a side rail into it. */
    private float newAim() {
        float x = (rnd.nextFloat() - 0.5f) * GOAL * 1.5f;
        if (rnd.nextFloat() < 0.3f) x = (rnd.nextBoolean() ? 2f : -2f) * B - x;
        return x;
    }

    // ---- drawing ----

    void draw(DrawList d, Px f) {
        if (!built) {
            build(still);
            built = true;
        }
        fw = d.w;
        fh = d.h;
        light(d);
        float dy = EYE_Y - Y, dz = LOOK_Z - EYE_Z;
        d.camera(0f, EYE_Y, EYE_Z, 0f, -(float) Math.atan2(dy, dz), World.W * FOCAL, 0.1f, 200f);
        for (int k = 0; k < 3; k++) {
            eye[k] = d.eye[k];
            right[k] = d.right[k];
            up[k] = d.up[k];
            ahead[k] = d.ahead[k];
        }
        focal = d.focal;
        d.append(still);
        // the puck, or the puck going down the slot
        if (onTable) puck(d, px, Y, pz);
        else if (goalT >= 0f && goalT < 0.35f) {
            float k = goalT / 0.35f;
            puck(d, px, Y - 0.12f * k, scored == 1 ? L + PR + 0.03f : -L - PR - 0.03f);
        }
        hammer(d, mx, mz, ma, 1f, Hammer.PLAIN);
        hammer(d, rx, rz, ra, -1f, Hammer.RIVAL);
        score(f);
        key(f);
    }

    private static void puck(DrawList d, float x, float y, float z) {
        Round.cylinder(d, x, y, z, null, 0f, 0.022f, PR, 0x1C1C22, DrawList.PLAIN);
        Round.cylinder(d, x, y, z, null, 0.022f, 0.024f, PR * 0.6f, 0x3A3A44, DrawList.PLAIN);
    }

    /** A hammer laid flat on the table: its head across it, its handle back toward the end it plays from. */
    private void hammer(DrawList d, float x, float z, float a, float way, int kind) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        pose[0] = x - way * sa * 0.31f * SCALE;
        pose[1] = Y + 0.078f * SCALE + 0.003f;
        pose[2] = z - way * ca * 0.31f * SCALE;
        // the handle (its own x) along the table, the head's long side (its own z) across it,
        // all of it turned about the upright
        pose[3] = way * sa; pose[4] = 0f; pose[5] = way * ca;
        pose[6] = 0f; pose[7] = -1f; pose[8] = 0f;
        pose[9] = way * ca; pose[10] = 0f; pose[11] = -way * sa;
        Hammer.draw(d, pose, kind, SCALE);
    }

    /** The score at the top: the player's on the left, warm; the other's on the right, brick red. */
    private void score(Px f) {
        int s = 5, cw = 5 * s;
        int y = 14, gap = 22;
        int cx = f.w / 2;
        String a = String.valueOf(mine), b = String.valueOf(theirs);
        int wa = PixelFont.width(a, s), wb = PixelFont.width(b, s);
        float g = Math.min(1f, glow);
        int warm = Px.scale(0xFFF6E2C0, 1f + 0.3f * g);
        int ax = cx - gap / 2 - wa, bx = cx + gap / 2;
        PixelFont.draw(f, a, ax + 2, y + 2, s, 0x99000000);
        PixelFont.draw(f, b, bx + 2, y + 2, s, 0x99000000);
        PixelFont.draw(f, a, ax, y, s, glow > 0f ? Px.scale(0xFFFFB428, 1f + 0.25f * g) : warm);
        PixelFont.draw(f, b, bx, y, s, 0xFFC8604A);
        f.disc(cx, y + cw * 7 / 10, 2.5f, 0xCCFFFFFF);
    }

    /** The key out to the street, in the lower left corner. */
    private void key(Px f) {
        int x0 = KEY_X0, y1 = fh - KEY_UP, y0 = y1 - KEY_H, x1 = x0 + KEY_W;
        f.rect(x0, y0, x1, y1, 0xE01B1E24);
        f.rect(x0, y0, x1, y0 + 2, 0xE03A404A);
        f.rect(x0, y1 - 2, x1, y1, 0xE00E1014);
        String s = "HOME";
        int tw = PixelFont.width(s, 2);
        PixelFont.draw(f, s, x0 + (KEY_W - tw) / 2, y0 + (KEY_H - 14) / 2, 2, 0xFFE8EEF4);
    }

    /** The same hour as out on the street: only the sun's shadows worked out over the yard. */
    private static void light(DrawList d) {
        d.sun(d.sun[0], d.sun[1], d.sun[2], 0f, 3f, 0f, 30f, 30f, 60f);
    }

    // ---- what stands still ----

    /** The yard and the table, once. */
    private static void build(DrawList d) {
        Random r = new Random(23);
        float half = 7.5f;
        // the paving, and the four houses round it, their walls toward the yard
        d.box(0f, -0.25f, 0f, half + 1f, 0.25f, half + 1f, null, 0xC9BBA2, DrawList.PAVING);
        for (int side = 0; side < 4; side++) {
            int storeys = 4 + r.nextInt(2);
            float height = 3.3f * (storeys + 1) + 0.5f;
            int tint = Street.WALLS[r.nextInt(Street.WALLS.length)];
            float mat = DrawList.material(DrawList.FACADE, 40 + side);
            boolean alongX = side < 2;
            int out = side == 0 || side == 2 ? -1 : 1;
            float wall = side == 0 ? half : side == 1 ? -half : side == 2 ? half : -half;
            if (alongX) d.box(0f, height * 0.5f, wall - out * 3f, half + 6f, height * 0.5f, 3f, null, tint, mat);
            else d.box(wall - out * 3f, height * 0.5f, 0f, 3f, height * 0.5f, half, null, tint, mat);
            Street.fronts(d, alongX, wall, out, -7.25f, 5, storeys, 40 + side);
        }
        // a washing line across the far end, low enough to be seen over the table
        float lz = 6.4f, ly = 2.25f;
        d.box(0f, ly, lz, half, 0.008f, 0.008f, null, 0x3A3836, DrawList.RAIL);
        int[] cloth = {0xF4F2EC, 0x6E9DB0, 0xF4F2EC, 0xD98A6A, 0xE9E1CB, 0x4F7A5E, 0xF4F2EC, 0xB5523E};
        float x = -6.4f;
        for (int i = 0; x < 6.2f; i++) {
            float cw = 0.25f + r.nextFloat() * 0.3f, ch = 0.25f + r.nextFloat() * 0.35f;
            d.box(x + cw, ly - ch - 0.01f, lz, cw, ch, 0.012f, null, cloth[i % cloth.length], DrawList.GRAIN);
            x += cw * 2f + 0.15f + r.nextFloat() * 0.5f;
        }
        // pots along the far wall, a bench on the left
        for (int i = 0; i < 6; i++) {
            float bx = -6f + i * 2.4f + r.nextFloat() * 0.6f;
            d.box(bx, 0.25f, half - 0.45f, 0.25f, 0.25f, 0.25f, null, 0xB8693E, DrawList.GRAIN);
            d.box(bx, 0.72f, half - 0.45f, 0.32f, 0.25f, 0.32f, null, i % 2 == 0 ? 0x4F6F28 : 0x5E7E2E, DrawList.GRAIN);
        }
        d.box(-half + 0.5f, 0.42f, 1.5f, 0.22f, 0.04f, 1.1f, null, 0x8A5A32, DrawList.WOOD);
        for (int e = -1; e <= 1; e += 2) d.box(-half + 0.5f, 0.2f, 1.5f + e * 0.9f, 0.2f, 0.2f, 0.05f, null, 0x2B2A2C, DrawList.PLAIN);
        table(d);
    }

    /** The table: a white top on a dark blue cabinet, blue rails round it, a slot at each end. */
    private static void table(DrawList d) {
        int rail = 0x2E5E9E, body = 0x22304A, red = 0xD0413A;
        float rw = 0.05f, ex = B + rw;
        d.box(0f, (Y - 0.05f) * 0.5f, 0f, B + 0.04f, (Y - 0.05f) * 0.5f, L + 0.04f, null, body, DrawList.PLAIN);
        d.box(0f, Y - 0.03f, 0f, B, 0.03f, L, null, 0xEEF2F4, DrawList.PLAIN);
        // the side rails, and the end rails either side of the goal mouths
        for (int e = -1; e <= 1; e += 2) {
            d.box(e * (B + rw * 0.5f), Y + 0.015f, 0f, rw * 0.5f, 0.045f, L + rw, null, rail, DrawList.PAINT);
            for (int s = -1; s <= 1; s += 2) {
                float c = s * (GOAL + ex) * 0.5f, hw = (ex - GOAL) * 0.5f;
                d.box(c, Y + 0.015f, e * (L + rw * 0.5f), hw, 0.045f, rw * 0.5f, null, rail, DrawList.PAINT);
            }
            // the slot the puck drops down, dark
            d.box(0f, Y - 0.035f, e * (L + rw * 0.5f), GOAL, 0.03f, rw * 0.5f, null, 0x0C0C10, DrawList.PLAIN);
        }
        // the lines: across the middle, the circle round it, a line before each goal
        d.box(0f, Y + 0.001f, 0f, B, 0.0015f, 0.008f, null, red, DrawList.PLAIN);
        Round.ring(d, 0f, Y, 0f, null, 0f, 0.003f, 0.16f, 0.176f, red, DrawList.PLAIN);
        for (int e = -1; e <= 1; e += 2)
            d.box(0f, Y + 0.001f, e * (L - 0.34f), B, 0.0015f, 0.006f, null, 0x5A86C0, DrawList.PLAIN);
    }
}
