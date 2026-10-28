package com.shumtugle.freedie;

import java.util.Random;

/**
 * The yard behind the gate in the right-hand houses, and the air hockey table in it, seen
 * straight from above: a light smooth top with firm rails and rounded corners inside, a line
 * and a circle across the middle, a goal in the middle of each short end. The player's goal
 * is at the bottom. Two round mallets, the player's warm red, the other's cool violet, and a
 * dark puck that glides long and slows hardly at all.
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
    // the eye: so high over the table that it sees it as good as flat
    static final float EYE_H = 8f;
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
    private float eyeX, eyeZ, scale = 200f;
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

    /** The point of the table under a frame point: the eye looks straight down. */
    private void onTable(float sx, float sy, float[] out) {
        out[0] = eyeX + (sx - fw * 0.5f) / scale;
        out[1] = eyeZ - (sy - fh * 0.5f) / scale;
    }

    // ---- the play ----

    void update(float dt) {
        if (dt <= 0f) return;
        time += dt;
        lit = Math.max(0f, lit - dt);
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

    void draw(DrawList d, Px f) {
        if (!built) {
            build(still);
            built = true;
        }
        fw = d.w;
        fh = d.h;
        // the table across most of the width, its length within the frame, bands left above and below
        int band = 56;
        scale = Math.min(0.88f * fw / (2f * B), (fh - 2f * band) / (2f * (L + RW)));
        eyeX = 0f;
        eyeZ = 0f;
        d.sun(-0.38f, 0.88f, 0.28f, 0f, Y, 0f, 4f, 4f, 10f);
        d.fogStart = 60f;
        d.camera(eyeX, Y + EYE_H, eyeZ, 0f, -(float) (Math.PI / 2 - 1e-4), scale * EYE_H, 1f, 40f);
        d.append(still);
        if (onTable) puck(d, px, pz, 1f);
        else if (goalT >= 0f && goalT < 0.3f) puck(d, px, pz + (scored == 1 ? 1f : -1f) * 0.05f * goalT / 0.3f, 1f - goalT / 0.3f);
        mallet(d, mx, mz, 0xD8352B, 0xFF8E7A, 0x7A120E);
        mallet(d, rx, rz, 0x4A3A9A, 0x9C8CF0, 0x1A1240);
        int top = Math.round(fh * 0.5f - scale * (L + RW)), bottom = Math.round(fh * 0.5f + scale * (L + RW));
        score(f, top);
        keyX0 = 8;
        keyY0 = Math.min(fh - KEY_H - 8, bottom + (fh - bottom - KEY_H) / 2);
        key(f);
    }

    private static void puck(DrawList d, float x, float z, float k) {
        if (k <= 0.02f) return;
        Round.cylinder(d, x, Y, z, null, 0f, 0.008f, PR * k, 0x1C1C22, DrawList.PLAIN);
        Round.cylinder(d, x, Y, z, null, 0.008f, 0.0085f, PR * 0.62f * k, 0x44444E, DrawList.PLAIN);
    }

    /** A round mallet: a broad base, a knob on top with a paler cap. */
    private static void mallet(DrawList d, float x, float z, int face, int light, int dark) {
        Round.cylinder(d, x, Y, z, null, 0f, 0.016f, MR, dark, DrawList.PAINT);
        Round.cylinder(d, x, Y, z, null, 0.016f, 0.02f, MR * 0.94f, face, DrawList.PAINT);
        Round.cylinder(d, x, Y, z, null, 0.02f, 0.05f, MR * 0.42f, face, DrawList.PAINT);
        Round.cylinder(d, x, Y, z, null, 0.05f, 0.055f, MR * 0.36f, light, DrawList.PAINT);
    }

    /** The score in the band over the table: the player's on the left in red, the other's in violet; lit after a goal. */
    private void score(Px f, int top) {
        int s = 5, ch = 7 * s;
        int y = Math.max(6, (top - ch) / 2);
        int cx = f.w / 2, gap = 26;
        String a = String.valueOf(mine), b = String.valueOf(theirs);
        int wa = PixelFont.width(a, s);
        float g = Math.min(1f, lit);
        int ca = 0xFFE8574A, cb = 0xFF9C8CF0;
        if (g > 0f && (scored == 1 || mine >= WIN)) ca = Px.scale(ca, 1f + 0.35f * g);
        if (g > 0f && (scored == 2 || theirs >= WIN)) cb = Px.scale(cb, 1f + 0.25f * g);
        int ax = cx - gap / 2 - wa, bx = cx + gap / 2;
        PixelFont.draw(f, a, ax + 2, y + 2, s, 0x99000000);
        PixelFont.draw(f, b, bx + 2, y + 2, s, 0x99000000);
        PixelFont.draw(f, a, ax, y, s, ca);
        PixelFont.draw(f, b, bx, y, s, cb);
        f.disc(cx, y + ch / 2, 2.5f, 0xCCFFFFFF);
    }

    /** The key out to the street, in the band under the table. */
    private void key(Px f) {
        int x0 = keyX0, y0 = keyY0, x1 = x0 + KEY_W, y1 = y0 + KEY_H;
        f.rect(x0, y0, x1, y1, 0xE01B1E24);
        f.rect(x0, y0, x1, y0 + 2, 0xE03A404A);
        f.rect(x0, y1 - 2, x1, y1, 0xE00E1014);
        String s = "HOME";
        int tw = PixelFont.width(s, 2);
        PixelFont.draw(f, s, x0 + (KEY_W - tw) / 2, y0 + (KEY_H - 14) / 2, 2, 0xFFE8EEF4);
    }

    // ---- what stands still ----

    /** The yard's paving, and the table on it, once. */
    private static void build(DrawList d) {
        d.box(0f, -0.25f, 0f, 6f, 0.25f, 6f, null, 0xC9BBA2, DrawList.PAVING);
        int rail = 0x2E5E9E, red = 0xD0413A, line = 0x5A86C0;
        float ex = B + RW, ez = L + RW;
        // the cabinet under the top, a shade wider than the rails
        d.box(0f, (Y - 0.02f) * 0.5f, 0f, ex + 0.015f, (Y - 0.02f) * 0.5f, ez + 0.015f, null, 0x1E2638, DrawList.PLAIN);
        d.box(0f, Y - 0.02f, 0f, B, 0.02f, L, null, 0xF2F5F7, DrawList.PLAIN);
        // the straight rails: the sides between the corners, the ends between the corners and the mouths
        for (int e = -1; e <= 1; e += 2) {
            d.box(e * (B + RW * 0.5f), Y + RH * 0.5f - 0.01f, 0f, RW * 0.5f, RH * 0.5f + 0.01f, L - RC, null, rail, DrawList.PAINT);
            for (int s = -1; s <= 1; s += 2) {
                float x0 = GOAL, x1 = B - RC, c = s * (x0 + x1) * 0.5f;
                d.box(c, Y + RH * 0.5f - 0.01f, e * (L + RW * 0.5f), (x1 - x0) * 0.5f, RH * 0.5f + 0.01f, RW * 0.5f, null, rail,
                        DrawList.PAINT);
            }
            // the mouth: a dark slot through the end
            d.box(0f, Y - 0.03f, e * (L + RW * 0.5f), GOAL, 0.012f, RW * 0.5f + 0.004f, null, 0x08080C, DrawList.PLAIN);
        }
        // the corners: the rail follows a quarter round inside; outside, it is square
        for (int e = -1; e <= 1; e += 2)
            for (int s = -1; s <= 1; s += 2) {
                float ox = e * (B - RC), oz = s * (L - RC), hy = RH * 0.5f + 0.01f, y = Y + RH * 0.5f - 0.01f;
                // the square outer edge of the rails round the corner
                d.box(e * (B + RW * 0.5f), y, s * (L + (RW - RC) * 0.5f), RW * 0.5f, hy, (RC + RW) * 0.5f, null, rail, DrawList.PAINT);
                d.box(e * (B + (RW - RC) * 0.5f), y, s * (L + RW * 0.5f), (RC + RW) * 0.5f, hy, RW * 0.5f, null, rail, DrawList.PAINT);
                // and the round inside: blocks out from the corner's middle, from the curve to the table's edge
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
                    d.box(ox + dx * mid, y, oz + dz * mid, half, hy, across, T, rail, DrawList.PAINT);
                }
            }
        // the lines on the top: across the middle, the circle round it, a half round before each goal
        float lh = 0.0006f;
        d.box(0f, Y + lh, 0f, B, lh, 0.004f, null, red, DrawList.PLAIN);
        arc(d, 0f, 0f, 0.15f, 0.006f, 0f, (float) (Math.PI * 2), 40, red);
        for (int s = -1; s <= 1; s += 2) {
            float a0 = s > 0 ? (float) Math.PI : 0f;
            arc(d, 0f, s * L, GOAL + 0.04f, 0.005f, a0, a0 + (float) Math.PI, 22, line);
        }
    }

    private static final float[] T = new float[9];

    /** A thin painted line round part of a circle on the table top, from angle a0 to a1 (x = cos, z = sin). */
    private static void arc(DrawList d, float cx, float cz, float r, float w, float a0, float a1, int segs, int rgb) {
        float step = (a1 - a0) / segs, along = r * Math.abs(step) * 0.5f + 0.002f;
        for (int i = 0; i < segs; i++) {
            float a = a0 + (i + 0.5f) * step;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            // across the line: out from the middle; along it: round
            T[0] = c; T[1] = 0f; T[2] = -s;
            T[3] = 0f; T[4] = 1f; T[5] = 0f;
            T[6] = s; T[7] = 0f; T[8] = c;
            d.box(cx + c * r, Y + 0.0007f, cz + s * r, w * 0.5f, 0.0007f, along, T, rgb, DrawList.PLAIN);
        }
    }
}
