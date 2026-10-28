package com.shumtugle.freedie;

import java.util.Random;

/**
 * The yard behind the gate in the right-hand houses, and the air hockey table in it, seen
 * from over its near end at a slant: a light smooth top with dark wooden rails rounded inside
 * the corners, a line and a circle across the middle, a goal in the middle of each short end,
 * the player's at the bottom. Two round mallets, the player's of the hammer's wood bound in
 * iron, the other's black banded in brick red, as she is dressed; a dark puck that glides
 * long and slows hardly at all. The score is a crest at the top, as the level's is.
 *
 * The finger drives the player's mallet from anywhere on the screen: at the first touch the
 * mallet stays where it is and then follows the finger's moves, keeping the distance it
 * started at, so the finger can stay below it and the stroke is seen. It never leaves its own
 * half. The other mallet plays by the same laws, a little slow and a little late: it guards
 * its goal, cuts the puck off and now and then attacks, straight or off a rail.
 *
 * First to seven; after a goal a short sound, the score lit for a moment, a pause of a second,
 * then the puck on the half of whoever let it in and both mallets back at their places. The
 * back key, or the key in the corner, leads out onto the street, before the same building.
 *
 * Table space: x across (to the right), z along the table toward the other goal, the playing
 * surface at Y; the middle of the table at the origin.
 */
final class Yard {
    // the table: half its width and length, the surface, the rounded inner corners, the rails
    static final float B = 0.5f, L = 0.85f, Y = 0.8f, RC = 0.09f, RW = 0.045f, RH = 0.025f;
    /** Half the width of a goal mouth: a third of the table. */
    static final float GOAL = B / 3f;
    // the mallets and the puck: 13 and 6 hundredths of the table's width across
    static final float MR = 0.13f * B, PR = 0.06f * B;
    // the rails give back this much of the puck's way, a mallet standing still this much; the
    // fastest puck crosses the table in about half a second; the air hardly slows it
    static final float RAIL_E = 0.9f, HIT_E = 0.8f, MAX_V = 2f * L / 0.5f, DRAG = 0.06f;
    /** The way a mallet adds to the puck is counted up to this. */
    static final float MALLET_V = 4.5f;
    // the other mallet: how fast it runs, how hard it starts and stops, how late it sees
    static final float RIVAL_SPEED = 2.1f, RIVAL_ACC = 14f, RIVAL_LATE = 0.14f;
    /** Goals to win a game. */
    static final int WIN = 7;
    /** After a goal, so long before the next puck; after a game, so long before the next game. */
    static final float GOAL_T = 1.0f, OVER_T = 2.6f;
    /** Where the mallets stand at the start, as a share of the half length from the middle. */
    static final float HOME_Z = 0.72f;
    // the eye: so high over the table's surface, so far back from its middle, looking at a
    // point just past the middle; the slant keeps the far end nearly as wide as the near
    static final float EYE_UP = 2.4f, EYE_Z = -1.55f, LOOK_Z = 0.15f;
    // the key out to the street, in the band under the table, frame pixels
    static final int KEY_W = 66, KEY_H = 26;

    private final World w;
    private final World.Host host;
    private final Random rnd = new Random(0xA1L);
    private final DrawList still = new DrawList();
    private boolean built;

    // the puck: where, how fast; whether it is on the table
    float px, pz, vx, vz;
    private boolean onTable = true;
    // the player's mallet; the finger holding it, and how far from the finger it is held
    float mx, mz = -HOME_Z * L;
    private float mvx, mvz;
    private boolean held;
    private float grabX, grabZ, lastFx, lastFz;
    // the other mallet, and the one the stand plays for the player
    float rx, rz = HOME_Z * L;
    private float rvx, rvz;
    private final Brain rival = new Brain(1), stand = new Brain(-1);
    // the score; the last goal (1 the player's, 2 the other's) and how long ago; a game over
    int mine, theirs;
    private int scored;
    private float goalT = -1f, overT = -1f, lit;
    private float time, railAt = -1f, hitAt = -1f;
    /** For the desktop stand: the player's mallet played by a hand like the other one's. */
    boolean auto;

    // the eye as last set, to turn a finger into a point on the table
    private final float[] eye = {0f, Y + 2.5f, -1.45f}, right = {1f, 0f, 0f}, up = {0f, 1f, 0f}, ahead = {0f, 0f, 1f};
    private float focal = 500f;
    private int fw = World.W, fh = 640, keyX0, keyY0;
    private final float[] at = new float[2];

    Yard(World w, World.Host host) {
        this.w = w;
        this.host = host;
        serve(-1);
    }

    // ---- the finger ----

    void touch(int type, float x, float y) {
        if (type == World.DOWN && x >= keyX0 - 4 && x <= keyX0 + KEY_W + 4 && y >= keyY0 - 4 && y <= keyY0 + KEY_H + 4) {
            host.sound(Synth.TOCK, 0.8f, 1f);
            host.buzz(15);
            w.leaveYard();
            return;
        }
        if (type == World.UP) {
            held = false;
            return;
        }
        onTable(x, y, at);
        lastFx = at[0];
        lastFz = at[1];
        if (type == World.DOWN || !held) {
            // the mallet does not jump to the finger: it keeps its distance from it
            held = true;
            grabX = mx - at[0];
            grabZ = mz - at[1];
        }
    }

    /** The point of the table's plane under a frame point. */
    private void onTable(float sx, float sy, float[] out) {
        float u = (sx - fw * 0.5f) / focal, v = (fh * 0.5f - sy) / focal;
        float dx = ahead[0] + right[0] * u + up[0] * v, dy = ahead[1] + right[1] * u + up[1] * v, dz = ahead[2] + right[2] * u + up[2] * v;
        if (dy > -1e-3f) dy = -1e-3f;
        float t = (Y - eye[1]) / dy;
        out[0] = eye[0] + dx * t;
        out[1] = eye[2] + dz * t;
    }

    // ---- the play ----

    void update(float dt) {
        if (dt <= 0f) return;
        time += dt;
        lit = Math.max(0f, lit - dt);
        crestTime(dt);
        rival.see(this, time);
        stand.see(this, time);
        if (overT >= 0f && (overT += dt) >= OVER_T) {
            // the next game, at once
            overT = -1f;
            mine = 0;
            theirs = 0;
            goalT = -1f;
            restart(-1);
        }
        if (goalT >= 0f && overT < 0f && (goalT += dt) >= GOAL_T) {
            goalT = -1f;
            restart(scored == 1 ? 1 : -1);
        }
        // where the mallets mean to be this frame
        float tx, tz;
        if (auto) {
            stand.plan(this, dt);
            float[] t = run(mx, mz, stand.tx, stand.tz, RIVAL_SPEED * 1.15f, dt);
            tx = t[0];
            tz = t[1];
        } else if (held) {
            tx = lastFx + grabX;
            tz = lastFz + grabZ;
        } else {
            tx = mx;
            tz = mz;
        }
        clampMallet(tx, tz, -1, at);
        tx = at[0];
        tz = at[1];
        rival.plan(this, dt);
        // the other mallet runs toward its mark, gathering and losing speed
        float ax = rival.tx - rx, az = rival.tz - rz, ad = (float) Math.sqrt(ax * ax + az * az);
        float want = Math.min(rival.speed, ad * 10f);
        float wvx = ad > 1e-5f ? ax / ad * want : 0f, wvz = ad > 1e-5f ? az / ad * want : 0f;
        float cx = wvx - rvx, cz = wvz - rvz, cd = (float) Math.sqrt(cx * cx + cz * cz), acc = RIVAL_ACC * dt;
        if (cd > acc) {
            cx *= acc / cd;
            cz *= acc / cd;
        }
        rvx += cx;
        rvz += cz;
        clampMallet(rx + rvx * dt, rz + rvz * dt, 1, at);
        float rtx = at[0], rtz = at[1];
        // in steps small enough that nothing passes through anything
        float myVx = (tx - mx) / dt, myVz = (tz - mz) / dt;
        float far = Math.max((float) Math.hypot(tx - mx, tz - mz), Math.max((float) Math.hypot(rtx - rx, rtz - rz),
                (float) Math.hypot(vx, vz) * dt));
        int n = Math.max(4, Math.min(48, (int) Math.ceil(far / (PR * 0.5f))));
        float h = dt / n, sx = (tx - mx) / n, sz = (tz - mz) / n, ox = (rtx - rx) / n, oz = (rtz - rz) / n;
        float hmvx = cap(myVx, myVz, 0), hmvz = cap(myVx, myVz, 1), hrvx = cap(rvx, rvz, 0), hrvz = cap(rvx, rvz, 1);
        float rx0 = rx, rz0 = rz;
        for (int i = 0; i < n; i++) {
            mx += sx;
            mz += sz;
            rx += ox;
            rz += oz;
            if (onTable && goalT < 0f && overT < 0f) puckStep(h, hmvx, hmvz, hrvx, hrvz);
        }
        mvx = myVx;
        mvz = myVz;
        rvx = (rx - rx0) / dt;
        rvz = (rz - rz0) / dt;
    }

    private final float[] runOut = new float[2];

    /** One step toward a mark, no faster than so. */
    private float[] run(float x, float z, float tx, float tz, float speed, float dt) {
        float dx = tx - x, dz = tz - z, d = (float) Math.sqrt(dx * dx + dz * dz), most = speed * dt;
        if (d > most) {
            dx *= most / d;
            dz *= most / d;
        }
        runOut[0] = x + dx;
        runOut[1] = z + dz;
        return runOut;
    }

    /** A mallet's way, as the puck feels it: no more than MALLET_V. */
    private static float cap(float x, float z, int which) {
        float s = (float) Math.sqrt(x * x + z * z), k = s > MALLET_V ? MALLET_V / s : 1f;
        return (which == 0 ? x : z) * k;
    }

    /** A mallet kept on its own half (side -1 the player's), inside the rails and the rounded corners. */
    static void clampMallet(float x, float z, int side, float[] out) {
        x = Math.max(-B + MR, Math.min(B - MR, x));
        if (side < 0) z = Math.max(-L + MR, Math.min(-MR, z));
        else z = Math.max(MR, Math.min(L - MR, z));
        float cx = B - RC, cz = L - RC;
        if (Math.abs(x) > cx && Math.abs(z) > cz) {
            float ox = Math.signum(x) * cx, oz = Math.signum(z) * cz;
            float dx = x - ox, dz = z - oz, d = (float) Math.sqrt(dx * dx + dz * dz), r = RC - MR;
            if (d > r) {
                x = ox + dx / d * r;
                z = oz + dz / d * r;
            }
        }
        out[0] = x;
        out[1] = z;
    }

    /** Both mallets back to their places, the puck down on one half (-1 the player's), still. */
    private void restart(int side) {
        mx = 0f;
        mz = -HOME_Z * L;
        rx = 0f;
        rz = HOME_Z * L;
        mvx = mvz = rvx = rvz = 0f;
        // a finger still down goes on holding the mallet where it now is
        grabX = mx - lastFx;
        grabZ = mz - lastFz;
        serve(side);
    }

    private void serve(int side) {
        px = 0f;
        pz = side * 0.4f * L;
        vx = 0f;
        vz = 0f;
        onTable = true;
        rival.forget();
        stand.forget();
    }

    private void puckStep(float h, float mvx, float mvz, float rvx, float rvz) {
        float k = (float) Math.exp(-DRAG * h);
        vx *= k;
        vz *= k;
        px += vx * h;
        pz += vz * h;
        walls();
        knock(mx, mz, mvx, mvz, true);
        knock(rx, rz, rvx, rvz, false);
        // a puck caught between a mallet and a rail: the rail holds, the mallet gives way
        walls();
        unpin(true);
        unpin(false);
        float s = (float) Math.sqrt(vx * vx + vz * vz);
        if (s > MAX_V) {
            vx *= MAX_V / s;
            vz *= MAX_V / s;
        }
        if (pz > L + PR) goal(1);
        else if (pz < -L - PR) goal(2);
    }

    /** The rails: straight along the sides and ends, round in the corners; the goal mouths open, their posts hard. */
    private void walls() {
        float cx = B - RC, cz = L - RC;
        float ax = Math.abs(px), az = Math.abs(pz);
        if (ax > cx && az > cz && az < L + PR) {
            // a rounded corner: it turns the puck by its own curve
            float ox = Math.signum(px) * cx, oz = Math.signum(pz) * cz;
            float dx = px - ox, dz = pz - oz, d = (float) Math.sqrt(dx * dx + dz * dz), r = RC - PR;
            if (d > r) {
                float nx = dx / d, nz = dz / d;
                px = ox + nx * r;
                pz = oz + nz * r;
                reflect(nx, nz);
            }
            return;
        }
        if (px > B - PR) {
            px = B - PR;
            reflect(1f, 0f);
        } else if (px < -B + PR) {
            px = -B + PR;
            reflect(-1f, 0f);
        }
        if (Math.abs(px) >= GOAL) {
            if (pz > L - PR) {
                pz = L - PR;
                reflect(0f, 1f);
            } else if (pz < -L + PR) {
                pz = -L + PR;
                reflect(0f, -1f);
            }
        } else if (az > L) {
            // in the mouth: its sides hold it on the way down
            if (px > GOAL - PR) {
                px = GOAL - PR;
                if (vx > 0f) vx = -vx * RAIL_E;
            } else if (px < -GOAL + PR) {
                px = -GOAL + PR;
                if (vx < 0f) vx = -vx * RAIL_E;
            }
        }
        // the posts at the corners of each mouth
        for (int e = -1; e <= 1; e += 2)
            for (int s = -1; s <= 1; s += 2) {
                float dx = px - e * GOAL, dz = pz - s * L, d2 = dx * dx + dz * dz;
                if (d2 < PR * PR && d2 > 1e-12f) {
                    float d = (float) Math.sqrt(d2), nx = dx / d, nz = dz / d;
                    px = e * GOAL + nx * PR;
                    pz = s * L + nz * PR;
                    reflect(-nx, -nz);
                }
            }
    }

    /** The puck meets a wall whose outward normal is (nx, nz): mirrored, a little slower. */
    private void reflect(float nx, float nz) {
        float vn = vx * nx + vz * nz;
        if (vn <= 0f) return;
        vx -= (1f + RAIL_E) * vn * nx;
        vz -= (1f + RAIL_E) * vn * nz;
        if (vn > 0.15f && time - railAt > 0.05f) {
            railAt = time;
            host.sound(Synth.PLASTIC, Math.min(0.85f, 0.25f + vn * 0.2f), 1.5f);
        }
    }

    /** A mallet meets the puck: put out of it, and off it, with the mallet's own way added; once. */
    private void knock(float hx, float hz, float hvx, float hvz, boolean player) {
        float dx = px - hx, dz = pz - hz, d2 = dx * dx + dz * dz, r = PR + MR;
        if (d2 >= r * r || d2 < 1e-12f) return;
        float d = (float) Math.sqrt(d2), nx = dx / d, nz = dz / d;
        px = hx + nx * r;
        pz = hz + nz * r;
        float rvn = (vx - hvx) * nx + (vz - hvz) * nz;
        if (rvn >= 0f) return;
        vx -= (1f + HIT_E) * rvn * nx;
        vz -= (1f + HIT_E) * rvn * nz;
        float s = -rvn;
        if (time - hitAt > 0.06f) {
            hitAt = time;
            host.sound(Synth.TOCK, Math.min(1f, 0.35f + s * 0.18f), player ? 1.35f : 1.1f);
            if (player) host.buzz(Math.min(25, 6 + (int) (s * 5f)));
        }
    }

    /** If the rail has pushed the puck back into a mallet, the mallet steps back from it. */
    private void unpin(boolean player) {
        float hx = player ? mx : rx, hz = player ? mz : rz;
        float dx = hx - px, dz = hz - pz, d2 = dx * dx + dz * dz, r = PR + MR;
        if (d2 >= r * r || d2 < 1e-12f) return;
        float d = (float) Math.sqrt(d2);
        if (player) {
            mx = px + dx / d * r;
            mz = pz + dz / d * r;
        } else {
            rx = px + dx / d * r;
            rz = pz + dz / d * r;
        }
    }

    /** A goal: 1 the player's, through the far end; 2 the other's, through the near one. */
    private void goal(int who) {
        onTable = false;
        scored = who;
        goalT = 0f;
        lit = 1.2f;
        if (who == 1) {
            mine++;
            host.sound(Synth.CHIME, 0.8f, 1.25f);
            host.buzz(30);
        } else {
            theirs++;
            host.sound(Synth.CHIME, 0.45f, 0.8f);
        }
        if (mine >= WIN || theirs >= WIN) {
            overT = 0f;
            lit = OVER_T;
            if (mine >= WIN) {
                host.sound(Synth.BEAT, 0.8f, 1.2f);
                host.saveInt("hockeyWins", host.loadInt("hockeyWins") + 1);
            }
        }
    }

    // ---- a hand at the table ----

    /**
     * A mallet's mind: it sees the puck a little late, picks a mark a few times a second and
     * makes small mistakes. Its goal is the end its side points to (1 the far end).
     */
    private final class Brain {
        final int side;
        float tx, tz, speed = RIVAL_SPEED;
        private float think, err, aimX;
        private boolean bank;
        // what it has seen of the puck, one entry a frame
        private static final int N = 64;
        private final float[] hx = new float[N], hz = new float[N], hvx = new float[N], hvz = new float[N], ht = new float[N];
        private int head;

        Brain(int side) {
            this.side = side;
            tz = side * HOME_Z * L;
            forget();
        }

        void forget() {
            for (int i = 0; i < N; i++) ht[i] = -99f;
            think = 0f;
            newAim();
        }

        void newAim() {
            aimX = (rnd.nextFloat() - 0.5f) * GOAL * 1.2f;
            bank = rnd.nextFloat() < 0.3f;
            err = (rnd.nextFloat() - 0.5f) * 0.08f;
        }

        void see(Yard y, float now) {
            head = (head + 1) % N;
            hx[head] = y.px;
            hz[head] = y.pz;
            hvx[head] = y.vx;
            hvz[head] = y.vz;
            ht[head] = y.onTable && y.goalT < 0f ? now : -99f;
        }

        void plan(Yard y, float dt) {
            think -= dt;
            if (think > 0f) return;
            think = 0.07f + rnd.nextFloat() * 0.05f;
            // the puck as it was a moment ago
            int k = head;
            for (int i = 0; i < N; i++) {
                int j = (head - i + N) % N;
                if (ht[j] < 0f) break;
                k = j;
                if (y.time - ht[j] >= RIVAL_LATE) break;
            }
            if (ht[k] < 0f) {
                home(y);
                return;
            }
            // its own space: z toward its own goal
            float qx = hx[k], qz = hz[k] * side, qvx = hvx[k], qvz = hvz[k] * side;
            float me = side > 0 ? y.rx : y.mx, mez = (side > 0 ? y.rz : y.mz) * side;
            float sp = (float) Math.sqrt(qvx * qvx + qvz * qvz);
            float gx, gz;
            if (qz > 0f && (sp < 0.9f || qvz < -0.2f)) {
                // on its own half and slow, or going away: get behind it and strike at the other goal
                float ax = bank ? (qx > 0f ? -1f : 1f) * 2f * (B - PR) - aimX : aimX;
                float ux = qx - ax, uz = qz + L, ul = (float) Math.sqrt(ux * ux + uz * uz);
                ux /= ul;
                uz /= ul;
                float behind = (me - qx) * ux + (mez - qz) * uz, off = Math.abs((me - qx) * uz - (mez - qz) * ux);
                if (behind > 0f && off < MR * 0.7f) {
                    gx = qx - ux * 0.14f;
                    gz = qz - uz * 0.14f;
                } else if (behind < 0f && off < PR + MR + 0.01f) {
                    gx = qx + (me > qx ? 1f : -1f) * (PR + MR + 0.04f);
                    gz = qz + 0.04f;
                } else {
                    gx = qx + ux * (PR + MR + 0.03f);
                    gz = qz + uz * (PR + MR + 0.03f);
                }
            } else if (qvz > 0.05f) {
                // coming at it: where it will cross the line before its goal, off the side rails
                float zd = L - 0.2f, t = (zd - qz) / qvz;
                gx = fold(qx + qvx * Math.max(0f, t)) + err;
                gz = zd;
                if (qz > 0f && t < 0.25f) {
                    // close already: meet it
                    gx = qx + err;
                    gz = Math.max(qz + 0.02f, MR);
                }
            } else {
                home(y);
                if (qvz < -0.05f && qz < 0f) newAim();
                return;
            }
            set(gx, gz);
        }

        private void home(Yard y) {
            set(y.px * 0.35f, L - 0.2f);
        }

        private void set(float gx, float gz) {
            tx = gx;
            tz = gz * side;
        }
    }

    /** A point across the table, folded back off the side rails as the puck would be. */
    private static float fold(float x) {
        float lim = B - PR, span = 4f * lim;
        float u = ((x + lim) % span + span) % span;
        return u < 2f * lim ? u - lim : 3f * lim - u;
    }

    // ---- drawing ----

    /** The fine layer the score is drawn on, at the top, as the crest is on the street; set by the world. */
    Ink ink;

    void draw(DrawList d, Px f) {
        if (!built) {
            build(still);
            built = true;
        }
        fw = d.w;
        fh = d.h;
        // the yard keeps the street's hour: only its shadows are worked out over the table
        d.sun(d.sun[0], d.sun[1], d.sun[2], 0f, 2f, 0f, 16f, 16f, 40f);
        // the eye over the near end, looking down at the table at a slant: the near rails
        // across nearly all the frame, the far end a little narrower
        float ey = Y + EYE_UP, pitch = -(float) Math.atan2(EYE_UP, LOOK_Z - EYE_Z);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float near = (-L - RW - EYE_Z) * cp + (Y - ey) * sp;
        focal = 0.95f * fw * near / (2f * (B + RW + 0.03f));
        d.camera(0f, ey, EYE_Z, 0f, pitch, focal, 0.1f, 120f);
        for (int k = 0; k < 3; k++) {
            eye[k] = d.eye[k];
            right[k] = d.right[k];
            up[k] = d.up[k];
            ahead[k] = d.ahead[k];
        }
        d.append(still);
        if (onTable) puck(d, px, pz, 1f);
        else if (goalT >= 0f && goalT < 0.3f) puck(d, px, pz + (scored == 1 ? 1f : -1f) * 0.05f * goalT / 0.3f, 1f - goalT / 0.3f);
        // the player's mallet is the hammer's: grained wood bound in iron; the other one is
        // hers: black, banded in brick red, a cap of white lace
        mallet(d, mx, mz, 0x55524F, 0xDFA868, DrawList.WOOD, 0x55524F, 0xE8B878);
        mallet(d, rx, rz, 0xB5553A, 0x1E1C22, DrawList.PLAIN, 0xB5553A, 0xF2F0EA);
        crest();
        float[] p = at;
        d.project(-B - RW, Y, -L - RW, p);
        int bottom = Math.round(p[1]);
        keyX0 = 8;
        keyY0 = Math.max(bottom + 6, Math.min(fh - KEY_H - 8, bottom + (fh - bottom - KEY_H) / 2));
        key(f);
    }

    private static void puck(DrawList d, float x, float z, float k) {
        if (k <= 0.02f) return;
        Round.cylinder(d, x, Y, z, null, 0f, 0.009f, PR * k, 0x232226, DrawList.PLAIN);
        Round.cylinder(d, x, Y, z, null, 0.009f, 0.0096f, PR * 0.7f * k, 0x4A4850, DrawList.PLAIN);
    }

    /** A round mallet: a banded rim, a broad face, a knob on it with a band and a cap. */
    private static void mallet(DrawList d, float x, float z, int rim, int face, int faceMat, int band, int cap) {
        Round.cylinder(d, x, Y, z, null, 0f, 0.018f, MR, rim, DrawList.PAINT);
        Round.cylinder(d, x, Y, z, null, 0.018f, 0.024f, MR * 0.86f, face, faceMat);
        Round.cylinder(d, x, Y, z, null, 0.024f, 0.05f, MR * 0.42f, face, faceMat);
        Round.cylinder(d, x, Y, z, null, 0.05f, 0.058f, MR * 0.45f, band, DrawList.PAINT);
        Round.cylinder(d, x, Y, z, null, 0.058f, 0.064f, MR * 0.36f, cap, DrawList.PLAIN);
    }

    // ---- the crest: the score at the top, the way the level's is on the street ----

    private float flipT = -1f, jolt = -1f;
    private int shownMine, shownTheirs, inkKey = Integer.MIN_VALUE;
    private static final int K = Crest.K, CX = World.W / 2, CY = Crest.CY, R = Crest.R;
    private static final int INK = 0xFF3A2410, GREEN = 0xFF8FCB6C, GREEN_LIT = 0xFFC6EE9C, GOLD = 0xFFFFC840;

    /**
     * In the middle the medallion, green, with the player's goals, and round it a ring of seven
     * that fills with gold goal by goal; a goal turns it over like a coin. On the right a small
     * black one with hers, banded in brick red, that jolts when she scores. A game won burns gold.
     */
    private void crest() {
        if (ink == null) return;
        if (mine != shownMine) {
            if (mine > shownMine && flipT < 0f) flipT = 0f;
            else if (mine < shownMine) shownMine = mine;
        }
        if (theirs != shownTheirs) {
            if (theirs > shownTheirs) jolt = 0f;
            shownTheirs = theirs;
        }
        boolean gold = overT >= 0f && mine >= WIN;
        boolean moving = flipT >= 0f || jolt >= 0f || gold;
        int key = shownMine * 64 + shownTheirs * 4 + (gold ? 1 : 0);
        if (!moving && key == inkKey) {
            ink.used = true;
            return;
        }
        inkKey = key;
        ink.clear();
        Ink g = ink;
        float mx = CX * K, my = CY * K, r = R * K;
        float across = 1f;
        int shown = shownMine;
        if (flipT >= 0f) {
            float an = (float) Math.PI * Math.min(1f, flipT / 0.5f);
            across = Math.max(0.04f, Math.abs((float) Math.cos(an)));
            if (an > Math.PI / 2) shown = mine;
        }
        int lit = Math.min(WIN, shown);
        g.disc(mx + 3, my + 7, r + 4 * K, 0xFF000000, 9f, 0.28f);
        g.squash(mx, my, across, 1f);
        for (int s = 0; s < WIN; s++) {
            float t0 = s / (float) WIN + 0.014f, t1 = (s + 1) / (float) WIN - 0.014f;
            int c = gold ? 0xFFFFB23A : s < lit ? GOLD : INK;
            g.ring(mx, my, r + 1.2f * K, r + 4f * K, t0, t1, c, gold || s < lit ? 1f : 0.62f);
        }
        int face = gold ? 0xFFF08A1E : GREEN, faceLit = gold ? 0xFFFFE6A0 : GREEN_LIT;
        g.disc(mx, my, r, face, 1f, 1f);
        g.disc(mx - r * 0.18f, my - r * 0.22f, r * 0.72f, faceLit, r * 0.55f, 0.7f);
        g.ring(mx, my, r - 1.2f * K, r + 0.4f * K, 0f, 1f, INK, 1f);
        String a = String.valueOf(shown);
        float th = 15f * K, tw = Ink.digitsWidth(a, th);
        g.digits(a, mx - tw * 0.5f, my - th * 0.5f, th, gold ? 0xFFFFF6D0 : 0xFFFFFFFF, INK, 1.1f * K, 1f);
        if (gold) {
            float breathe = 0.6f + 0.4f * (float) Math.sin(time * 5f);
            g.disc(mx, my, r * 0.45f, 0xFFFFFFF0, r * 0.5f, 0.45f * breathe);
        }
        g.unsquash();
        // hers, smaller, off to the right
        float ox = (CX + R + 24) * K, oy = (CY + 2) * K, orr = 9f * K;
        float jx = 1f, jy = 1f;
        if (jolt >= 0f) {
            float k = 0.25f * (float) Math.exp(-jolt * 8f) * (float) Math.cos(jolt * 28f);
            jx = 1f + k;
            jy = 1f - k;
        }
        g.disc(ox + 2, oy + 5, orr + 2 * K, 0xFF000000, 7f, 0.25f);
        g.squash(ox, oy, jx, jy);
        g.disc(ox, oy, orr + 1.6f * K, 0xFFB5553A, 1f, 1f);
        g.disc(ox, oy, orr, 0xFF1E1C22, 1f, 1f);
        g.disc(ox - orr * 0.2f, oy - orr * 0.25f, orr * 0.6f, 0xFF3A3842, orr * 0.5f, 0.6f);
        String b = String.valueOf(shownTheirs);
        float bh = 10f * K, bw = Ink.digitsWidth(b, bh);
        g.digits(b, ox - bw * 0.5f, oy - bh * 0.5f, bh, 0xFFF2F0EA, 0xFF000000, 0.8f * K, 1f);
        g.unsquash();
        ink.used = true;
    }

    private void crestTime(float dt) {
        if (flipT >= 0f && (flipT += dt) > 0.5f) {
            flipT = -1f;
            shownMine = mine;
        }
        if (jolt >= 0f && (jolt += dt) > 0.6f) jolt = -1f;
    }

    /** The key out to the street, under the table, in the colours of the room's keys. */
    private void key(Px f) {
        int x0 = keyX0, y0 = keyY0, x1 = x0 + KEY_W, y1 = y0 + KEY_H;
        f.rect(x0 + 1, y0 + 2, x1 + 1, y1 + 2, 0x66000000);
        f.rect(x0, y0, x1, y1, 0xF02E3226);
        f.rect(x0, y0, x1, y0 + 2, 0xF04A5040);
        f.rect(x0, y1 - 3, x1, y1, 0xF01A1D16);
        String s = "HOME";
        int tw = PixelFont.width(s, 2);
        PixelFont.draw(f, s, x0 + (KEY_W - tw) / 2, y0 + (KEY_H - 14) / 2, 2, 0xFFE8E2C8);
    }

    // ---- what stands still ----

    /**
     * The yard: old paving, tall houses on three sides with their balconies and flowers, a low
     * garden wall on the fourth, toward the evening sun, which comes in over it; two palms in
     * beds by the wall laying their shadows across; washing on a line; pots and a bench. The
     * table stands in the middle on a block of the plinth's pale stone, its rails dark wood
     * like a hammer's handle, bound with iron at the corners.
     */
    private static void build(DrawList d) {
        Random r = new Random(23);
        // a narrow yard: the far house close behind the table, so its door and its flowers stand
        // right over the far end; the right-hand house; behind the eye another; on the left the
        // low wall toward the sun
        float far = 3.0f, back = -5f, east = 4.2f, west = -4.2f;
        d.box(0f, -0.25f, 0f, 12f, 0.25f, 12f, null, 0xC9BBA2, DrawList.PAVING);
        float[][] walls = {{far, -1}, {back, 1}};
        for (int i = 0; i < 2; i++) {
            float wz = walls[i][0];
            int out = (int) walls[i][1];
            int storeys = 4 + r.nextInt(2);
            float height = 3.3f * (storeys + 1) + 0.5f;
            int tint = Street.WALLS[r.nextInt(Street.WALLS.length)];
            d.box(0f, height * 0.5f, wz - out * 3f, 9f, height * 0.5f, 3f, null, tint, DrawList.material(DrawList.FACADE, 40 + i));
            Street.fronts(d, true, wz, out, -5.8f, 4, storeys, 40 + i);
        }
        {
            int storeys = 4;
            float height = 3.3f * (storeys + 1) + 0.5f;
            int tint = Street.WALLS[r.nextInt(Street.WALLS.length)];
            d.box(east + 3f, height * 0.5f, (far + back) * 0.5f, 3f, height * 0.5f, (far - back) * 0.5f, null, tint,
                    DrawList.material(DrawList.FACADE, 42));
            Street.fronts(d, false, east, -1, back, 2, storeys, 42);
        }
        // the low wall toward the sun, a coping of paler stone
        d.box(west - 0.25f, 1.0f, (far + back) * 0.5f, 0.25f, 1.0f, (far - back) * 0.5f, null, 0xE6D8BC, DrawList.STONE);
        d.box(west - 0.25f, 2.05f, (far + back) * 0.5f, 0.32f, 0.06f, (far - back) * 0.5f + 0.05f, null, 0xF0E6D0, DrawList.STONE);
        // two palms in their beds by the low wall
        Random pr = new Random(7);
        float[][] palms = {{west + 0.9f, -0.5f, 7.4f, 0.4f}, {west + 1.0f, 2.0f, 8.2f, -0.3f}};
        for (float[] pm : palms) {
            d.box(pm[0], 0.03f, pm[1], 0.6f, 0.03f, 0.6f, null, 0x8C6E4C, DrawList.GRAIN);
            Street.palm(d, pr, pm[0], 0f, pm[1], pm[2], pm[3], false);
        }
        // the washing across the far end, over the far goal
        float lz = far - 0.25f, ly = 2.15f;
        d.box(0f, ly, lz, east, 0.008f, 0.008f, null, 0x3A3836, DrawList.RAIL);
        int[] cloth = {0xF4F2EC, 0x6E9DB0, 0xF4F2EC, 0xD98A6A, 0xE9E1CB, 0x4F7A5E, 0xF4F2EC, 0xB5523E};
        float x = west + 0.4f;
        for (int i = 0; x < east - 0.6f; i++) {
            float cw = 0.14f + r.nextFloat() * 0.14f, ch = 0.16f + r.nextFloat() * 0.18f;
            d.box(x + cw, ly - ch - 0.01f, lz, cw, ch, 0.012f, null, cloth[i % cloth.length], DrawList.GRAIN);
            x += cw * 2f + 0.1f + r.nextFloat() * 0.3f;
        }
        // pots along the far wall, a bench by the right one
        for (int i = 0; i < 5; i++) pot(d, -2.9f + i * 1.45f + r.nextFloat() * 0.3f, far - 0.3f, i);
        pot(d, east - 0.4f, -1.6f, 1);
        d.box(east - 0.45f, 0.42f, 1.0f, 0.22f, 0.04f, 0.8f, null, 0x8A5A32, DrawList.WOOD);
        for (int e = -1; e <= 1; e += 2) d.box(east - 0.45f, 0.2f, 1.0f + e * 0.65f, 0.2f, 0.2f, 0.05f, null, 0x2B2A2C, DrawList.PLAIN);
        table(d);
    }

    private static void pot(DrawList d, float x, float z, int i) {
        d.box(x, 0.17f, z, 0.16f, 0.17f, 0.16f, null, 0xB8693E, DrawList.GRAIN);
        d.box(x, 0.33f, z, 0.18f, 0.025f, 0.18f, null, 0xC87A4A, DrawList.GRAIN);
        d.box(x, 0.52f, z, 0.21f, 0.17f, 0.21f, null, i % 2 == 0 ? 0x4F6F28 : 0x5E7E2E, DrawList.GRAIN);
        if (i % 3 == 1) d.box(x + 0.05f, 0.71f, z - 0.05f, 0.06f, 0.04f, 0.06f, null, 0xD9505A, DrawList.GRAIN);
    }

    /** The table: on a block of pale stone, a light top, dark wooden rails rounded inside the corners. */
    private static void table(DrawList d) {
        int rail = 0x6E4A2C, iron = 0x55524F, red = 0xB5553A;
        float ex = B + RW, ez = L + RW, hy = RH * 0.5f + 0.01f, y = Y + RH * 0.5f - 0.01f;
        d.box(0f, (Y - 0.03f) * 0.5f, 0f, ex + 0.04f, (Y - 0.03f) * 0.5f, ez + 0.04f, null, 0xE6D8BC, DrawList.STONE);
        d.box(0f, Y - 0.035f, 0f, ex + 0.05f, 0.012f, ez + 0.05f, null, 0xF0E6D0, DrawList.STONE);
        d.box(0f, Y - 0.02f, 0f, B, 0.02f, L, null, 0xF4F0E6, DrawList.PLAIN);
        for (int e = -1; e <= 1; e += 2) {
            d.box(e * (B + RW * 0.5f), y, 0f, RW * 0.5f, hy, L - RC, null, rail, DrawList.WOOD);
            for (int s = -1; s <= 1; s += 2) {
                float x0 = GOAL, x1 = B - RC, c = s * (x0 + x1) * 0.5f;
                d.box(c, y, e * (L + RW * 0.5f), (x1 - x0) * 0.5f, hy, RW * 0.5f, null, rail, DrawList.WOOD);
                // iron at the mouth's posts
                d.box(s * (GOAL + 0.012f), y + 0.002f, e * (L + RW * 0.5f), 0.012f, hy + 0.002f, RW * 0.5f + 0.003f, null, iron, DrawList.PAINT);
            }
            d.box(0f, Y - 0.03f, e * (L + RW * 0.5f), GOAL, 0.012f, RW * 0.5f + 0.004f, null, 0x0C0A08, DrawList.PLAIN);
        }
        for (int e = -1; e <= 1; e += 2)
            for (int s = -1; s <= 1; s += 2) {
                float ox = e * (B - RC), oz = s * (L - RC);
                d.box(e * (B + RW * 0.5f), y, s * (L + (RW - RC) * 0.5f), RW * 0.5f, hy, (RC + RW) * 0.5f, null, rail, DrawList.WOOD);
                d.box(e * (B + (RW - RC) * 0.5f), y, s * (L + RW * 0.5f), (RC + RW) * 0.5f, hy, RW * 0.5f, null, rail, DrawList.WOOD);
                int segs = 12;
                for (int i = 0; i < segs; i++) {
                    float a = (float) ((i + 0.5f) / segs * Math.PI / 2);
                    float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                    float len = RC / Math.max(c, sn) + 0.006f, mid = (RC + len) * 0.5f;
                    float half = (len - RC) * 0.5f + 0.001f, across = RC * (float) (Math.PI / 4 / segs) * 1.3f;
                    float dx = e * c, dz = s * sn;
                    T[0] = dx; T[1] = 0f; T[2] = -dz;
                    T[3] = 0f; T[4] = 1f; T[5] = 0f;
                    T[6] = dz; T[7] = 0f; T[8] = dx;
                    d.box(ox + dx * mid, y, oz + dz * mid, half, hy, across, T, rail, DrawList.WOOD);
                }
                // an iron cap over the outer corner
                d.box(e * (B + RW * 0.5f), y + 0.003f, s * (L + RW * 0.5f), RW * 0.5f + 0.004f, hy + 0.003f, RW * 0.5f + 0.004f, null, iron,
                        DrawList.PAINT);
            }
        float lh = 0.0006f;
        d.box(0f, Y + lh, 0f, B, lh, 0.004f, null, red, DrawList.PLAIN);
        arc(d, 0f, 0f, 0.15f, 0.006f, 0f, (float) (Math.PI * 2), 40, red);
        for (int s = -1; s <= 1; s += 2) {
            float a0 = s > 0 ? (float) Math.PI : 0f;
            arc(d, 0f, s * L, GOAL + 0.04f, 0.005f, a0, a0 + (float) Math.PI, 22, red);
        }
    }

    private static final float[] T = new float[9];

    /** A thin painted line round part of a circle on the table top, from angle a0 to a1 (x = cos, z = sin). */
    private static void arc(DrawList d, float cx, float cz, float r, float w, float a0, float a1, int segs, int rgb) {
        float step = (a1 - a0) / segs, along = r * Math.abs(step) * 0.5f + 0.002f;
        for (int i = 0; i < segs; i++) {
            float a = a0 + (i + 0.5f) * step;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            T[0] = c; T[1] = 0f; T[2] = -s;
            T[3] = 0f; T[4] = 1f; T[5] = 0f;
            T[6] = s; T[7] = 0f; T[8] = c;
            d.box(cx + c * r, Y + 0.0007f, cz + s * r, w * 0.5f, 0.0007f, along, T, rgb, DrawList.PLAIN);
        }
    }
}
