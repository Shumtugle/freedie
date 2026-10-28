package com.shumtugle.freedie;

import java.util.Random;

/**
 * The fight with Freedie, as the fight with the golem went in the game this one grows from.
 * It comes when the golden hammer has been given once and a building will not fall ten
 * times again; or from the bridge's console. The street is seen red and black, the plinth
 * is gone, and she comes up the road from far off, flowing (see Giant), tearing parked cars
 * from the curb and throwing them. The watchers have given a diamond hammer: it flies hard and
 * flat, and comes back at once.
 *
 * A thrown car knocked out of the air goes up and over to the curb; one that gets through
 * breaks a shield, and with no shield left the next one ends the fight. Every blow that lands
 * on her, anywhere, knocks a block out of her; twelve, and she falls apart. If she reaches the player, a
 * sweep of her arm ends it too. Won, the world goes gold and up into the sky, where the watchers
 * cheer; in the street her heart comes out of the heap as a golden cube first. Lost, it goes
 * dark, and the player wakes on the street before the same building.
 */
final class Fight {
    static final int ARMOR = 3, FLY_MAX = 4;
    static final float LIFT_T = 0.9f, SPEED = 26f;
    /** How far above the finger the ring sits, frame pixels; and how near it a car in the air is taken. */
    static final float FINGER = 30f, SNAP = 30f;

    private final Game g;
    private final World.Host host;
    final Giant giant = new Giant(0.4f, Giant.START_Z);
    int armor = ARMOR;
    // the cars in her hand or in the air: 1 lifted, 2 thrown, 3 knocked away or dropped
    private final Body[] fly = new Body[FLY_MAX];
    private final int[] flyCar = new int[FLY_MAX], flyState = new int[FLY_MAX];
    private final float[] flyT = new float[FLY_MAX];
    private float throwIn = 2.2f, beatIn, rumble;
    private int landingsSeen;
    /** The street's jolt as she comes down, and the red veil when something strikes home, 0 to 1. */
    float shake, flash;
    /** Seconds since the fight ended; below 0 it goes on. */
    float end = -1f;
    boolean won;
    private final Random fx = new Random(0xF16L);
    private final float[] tmp = new float[3], scr = new float[2], dir = new float[3];

    Fight(Game g) {
        this.g = g;
        host = g.host;
        host.sound(Synth.ROAR, 1f, 1f);
    }

    boolean live() {
        return end < 0f;
    }

    /** How far she has come, 0 at her start, 1 at the player. */
    private float progress() {
        return Math.max(0f, Math.min(1f, (Giant.START_Z - giant.z) / (Giant.START_Z - Giant.REACH_Z)));
    }

    /** A thrown car about to arrive: time runs slow, so the player can still answer it. */
    boolean closeCall() {
        if (end >= 0f) return false;
        for (int i = 0; i < FLY_MAX; i++) if (flyState[i] == 2 && fly[i].pz < 13f && fly[i].vz < 0f) return true;
        return false;
    }

    void step(float dt) {
        giant.update(dt, progress());
        if (giant.down) giant.body.fall(dt);
        flash = Math.max(0f, flash - dt * 2.2f);
        for (int i = 0; i < POPS; i++) if (popT[i] >= 0f && (popT[i] += dt) > 0.45f) popT[i] = -1f;
        if (hitT >= 0f && (hitT += dt) > 1.2f) hitT = -1f;
        shake = Math.max(0f, shake - dt * 2.5f);
        // a step: a dull thud of stone, the street trembling a little, more as she nears
        if (giant.landings != landingsSeen) {
            landingsSeen = giant.landings;
            float near = progress();
            shake = Math.max(shake, 0.1f + near * 0.2f);
            host.sound(Synth.GROUND, 0.55f + near * 0.35f, 0.5f + fx.nextFloat() * 0.1f);
            host.buzz((int) (12 + near * 25));
            for (int k = 0; k < 8; k++) {
                float a = fx.nextFloat() * 6.283f;
                g.spawn(giant.x + (float) Math.cos(a) * 1.6f, 0.12f, giant.z + (float) Math.sin(a) * 1.6f, true, 0xD8CFC0, 2.5f);
            }
            if (fx.nextFloat() < 0.12f) host.sound(Synth.ROAR, 0.7f, 0.9f + fx.nextFloat() * 0.2f);
        }
        // on the last shield the heart is heard
        if (armor == 0 && end < 0f) {
            beatIn -= dt;
            if (beatIn <= 0f) {
                beatIn = 0.75f;
                host.sound(Synth.BEAT, 1.1f, 1f);
                host.buzz(20);
            }
        }
        boolean holding = false;
        for (int i = 0; i < FLY_MAX; i++) if (flyState[i] == 1) holding = true;
        giant.holding = holding;
        if (!holding) giant.raise = Math.max(0f, giant.raise - dt * 2f);
        if (end >= 0f) {
            end += dt;
            flights(dt);
            if (won) {
                // the pieces jump and the ground trembles, a while after she falls
                rumble -= dt;
                if (rumble <= 0f && end < 2.4f) {
                    rumble = 0.32f + fx.nextFloat() * 0.15f;
                    shake = Math.max(shake, 0.55f);
                    host.sound(Synth.GROUND, 1.2f, 0.5f + fx.nextFloat() * 0.2f);
                    host.buzz(70);
                    giant.body.jolt(fx);
                }
            }
            return;
        }
        // throwing: a car at a time, more often the nearer she comes
        if (!holding && giant.stagger <= 0f) {
            throwIn -= dt;
            if (throwIn <= 0f) pickCar();
        }
        flights(dt);
        if (g.hs == Game.FLY) hits();
        if (giant.arrived()) {
            // she has reached the player: a sweep of her arm and the fight is lost
            flash = 1f;
            host.sound(Synth.CRASH, 1f, 0.6f);
            host.buzz(300);
            over(false);
        }
    }

    /** Tears the parked car nearest her from its place and lifts it to her hand. */
    private void pickCar() {
        Traffic t = g.traffic;
        int slot = -1;
        for (int i = 0; i < FLY_MAX; i++) if (flyState[i] == 0) slot = i;
        int best = -1;
        float bd = 1e9f;
        for (int i = 0; i < Traffic.PARK_X.length; i++) {
            if (t.away[i]) continue;
            float cz = t.atZ[i];
            if (cz < 9f || cz > giant.z + 7f || cz < giant.z - 20f) continue;
            float d = Math.abs(cz - giant.z) + Math.abs(t.atX[i] - giant.x) * 0.5f;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (slot < 0 || best < 0) {
            throwIn = 1.5f;
            return;
        }
        t.lift(best);
        g.carGone(best, true);
        Body c = new Body(0.95f, 0.65f, 2.3f, Body.CAR, t.paint(best));
        float yaw = t.atYaw[best];
        c.setPose(t.atX[best], 0.65f, t.atZ[best], (float) Math.cos(yaw / 2), 0f, (float) Math.sin(yaw / 2), 0f);
        c.homeX = c.px;
        c.homeY = c.py;
        c.homeZ = c.pz;
        c.id = t.damage[best];
        fly[slot] = c;
        flyCar[slot] = best;
        flyState[slot] = 1;
        flyT[slot] = 0f;
        host.sound(Synth.CRASH, 0.5f, 0.7f);
        host.sound(t.alarm(best), 0.35f, 0.9f);
        throwIn = 4.0f - 2.6f * progress();
    }

    private void flights(float dt) {
        Traffic tr = g.traffic;
        for (int i = 0; i < FLY_MAX; i++) {
            Body c = fly[i];
            int st = flyState[i];
            if (st == 0) continue;
            flyT[i] += dt;
            if (st == 1) {
                // up into her raised hand
                float k = Math.min(1f, flyT[i] / LIFT_T);
                float e = k * k * (3f - 2f * k);
                giant.raise = e;
                giant.pose();
                giant.hand(tmp);
                c.px = c.homeX + (tmp[0] - c.homeX) * e;
                c.py = c.homeY + (tmp[1] + 0.3f - c.homeY) * e + (float) Math.sin(Math.PI * k) * 1.5f;
                c.pz = c.homeZ + (tmp[2] - c.homeZ) * e;
                c.rotateBy(0.8f * dt, 0.3f * dt, 0f);
                c.updateR();
                if (k >= 1f && end < 0f) {
                    // and away at the player, high and hard
                    float t = 2.4f - 0.8f * progress();
                    float tx = World.CAM_X + 0.3f, ty = 1.4f, tz = 1.0f;
                    c.vx = (tx - c.px) / t;
                    c.vz = (tz - c.pz) / t;
                    c.vy = (ty - c.py) / t - 0.5f * Physics.G * t;
                    c.wx = (fx.nextFloat() - 0.5f) * 3f;
                    c.wy = (fx.nextFloat() - 0.5f) * 3f;
                    c.wz = (fx.nextFloat() - 0.5f) * 2f;
                    flyState[i] = 2;
                    flyT[i] = 0f;
                    host.sound(Synth.WHOOSH, 1f, 0.55f);
                } else if (k >= 1f) {
                    flyState[i] = 3;
                }
                continue;
            }
            c.vy += Physics.G * dt;
            c.px += c.vx * dt;
            c.py += c.vy * dt;
            c.pz += c.vz * dt;
            c.rotateBy(c.wx * dt, c.wy * dt, c.wz * dt);
            c.updateR();
            if (st == 2 && c.pz < 2.6f) {
                // it reaches the player
                flyState[i] = 0;
                tr.putDown(flyCar[i], World.CAM_X - 3.2f, -6f, 0f);
                flash = 1f;
                host.sound(Synth.CRASH, 1.2f, 0.75f);
                host.sound(Synth.GLASS, 0.8f, 0.9f);
                host.buzz(250);
                if (armor == 0 && end < 0f) over(false);
                else armor = Math.max(0, armor - 1);
                continue;
            }
            if (c.py < 0.65f && c.vy < 0f) {
                // down on the street, a wreck where it fell
                flyState[i] = 0;
                float x = Math.max(-5f, Math.min(5f, c.px));
                tr.putDown(flyCar[i], x, c.pz, (float) Math.atan2(c.r02, c.r22));
                g.carGone(flyCar[i], false);
                host.sound(Synth.CRASH, 0.9f, 0.8f + fx.nextFloat() * 0.2f);
                host.sound(Synth.GROUND, 1f, 0.6f);
                host.buzz(60);
                for (int k = 0; k < 16; k++) g.spawn(c.px, 0.2f, c.pz, true, 0xD8CFC0, 3f);
            }
        }
    }

    /** What the diamond hammer has met: a car in the air, a weak point, or her bulk. */
    private void hits() {
        Body h = g.hammer;
        float hx = h.px + h.r00 * 0.31f, hy = h.py + h.r10 * 0.31f, hz = h.pz + h.r20 * 0.31f;
        for (int i = 0; i < FLY_MAX; i++) {
            if (flyState[i] != 1 && flyState[i] != 2) continue;
            Body c = fly[i];
            float dx = c.px - hx, dy = c.py - hy, dz = c.pz - hz;
            if (dx * dx + dy * dy + dz * dz > 1.6f * 1.6f) continue;
            // knocked out of the air: up, back and toward the curb
            c.vx = (c.px < 0f ? -1f : 1f) * (2.5f + fx.nextFloat() * 2f);
            c.vy = 5.5f + fx.nextFloat() * 2f;
            c.vz = 3f + fx.nextFloat() * 3f;
            c.wx = (fx.nextFloat() - 0.5f) * 9f;
            c.wz = (fx.nextFloat() - 0.5f) * 9f;
            flyState[i] = 3;
            pop(hx, hy, hz, CAR_POP);
            host.sound(Synth.CRASH, 1f, 1.1f);
            host.sound(Synth.GLASS, 0.6f, 1.2f);
            host.buzz(70);
            for (int k = 0; k < 14; k++) g.spawn(hx, hy, hz, false, (k & 1) == 0 ? 0xCFE2EC : 0xFFF2C0, 4f);
            g.beginReturn();
            return;
        }
        Giant gi = giant;
        if (gi.down) return;
        if (gi.bodyDist(hx, hy, hz) < Giant.BODY_HIT + 0.35f) {
            // a blow that lands, anywhere: the block nearest it is knocked out of her for good,
            // up and out to her side, to come down on the street before her
            int part = gi.body.nearest(hx, hy, hz);
            if (part >= 0) {
                Body b = gi.body.knockOut(part);
                float ox = b.px - gi.x;
                b.vx = (ox >= 0f ? 1f : -1f) * (2.5f + fx.nextFloat() * 2f);
                b.vy = 4.5f + fx.nextFloat() * 2.5f;
                b.vz = -2.5f - fx.nextFloat() * 2.5f;
                b.wx = (fx.nextFloat() - 0.5f) * 9f;
                b.wz = (fx.nextFloat() - 0.5f) * 9f;
                b.homeX = b.px;
                b.homeY = b.py;
                b.homeZ = b.pz;
                b.asleep = false;
                g.phys.add(b);
                g.pieces.add(b);
            }
            gi.hits++;
            hitT = 0f;
            pop(hx, hy, hz, WEAK_POP);
            gi.stagger = Math.max(gi.stagger, 0.35f);
            gi.hurt = 1f;
            host.sound(Synth.STONE, 1.2f, 0.55f + fx.nextFloat() * 0.15f);
            host.sound(Synth.CRASH, 0.5f, 1.2f);
            host.buzz(70);
            for (int k = 0; k < 16; k++) g.spawn(hx, hy, hz, (k & 1) == 0, (k & 1) == 0 ? 0xE6DCC8 : 0xFFB13A, 4f);
            if (gi.hits >= Giant.HITS) topple(hx, hy, hz);
            else g.beginReturn();
        }
    }

    private static boolean near(float[] p, float x, float y, float z, float r) {
        float dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    /** She falls apart into what she was made of, out from the last blow. */
    private void topple(float x, float y, float z) {
        giant.down = true;
        giant.body.fire = false;
        giant.body.shatter(x, y, z, fx);
        host.sound(Synth.THUNDER, 1.2f, 0.8f);
        host.sound(Synth.CRASH, 1f, 0.5f);
        host.buzz(200);
        over(true);
    }

    private void over(boolean win) {
        if (end >= 0f) return;
        won = win;
        end = 0f;
        g.cancel();
        if (!win) giant.stagger = 99f;
        else
            // her last throw loses its force as she falls: it drops short, onto the road
            for (int i = 0; i < FLY_MAX; i++)
                if (flyState[i] == 2) {
                    flyState[i] = 3;
                    fly[i].vz *= 0.25f;
                }
    }

    // ---- the throw ----

    /**
     * The diamond hammer flies hard and flat. A car in the air near the ring is taken as the
     * target and led by its flight; otherwise the ring's point on her, or on the plane she
     * walks in, led by her pace.
     */
    void throwAt(float ax, float ay) {
        Body hammer = g.hammer;
        g.handPoint(tmp);
        float sx = tmp[0], sy = tmp[1], sz = tmp[2];
        int car = -1;
        float bd = SNAP;
        for (int i = 0; i < FLY_MAX; i++) {
            if (flyState[i] != 1 && flyState[i] != 2) continue;
            if (!g.project(fly[i].px, fly[i].py, fly[i].pz, scr)) continue;
            float d = (float) Math.hypot(scr[0] - ax, scr[1] - ay);
            if (d < bd) {
                bd = d;
                car = i;
            }
        }
        float tx, ty, tz;
        if (car >= 0) {
            Body c = fly[car];
            tx = c.px;
            ty = c.py;
            tz = c.pz;
            if (flyState[car] == 2)
                for (int it = 0; it < 4; it++) {
                    float d = (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - sz) * (tz - sz));
                    float t = d / SPEED;
                    tx = c.px + c.vx * t;
                    ty = c.py + c.vy * t + 0.5f * Physics.G * t * t;
                    tz = c.pz + c.vz * t;
                }
        } else {
            target(ax, ay, tmp);
            tx = tmp[0];
            ty = tmp[1];
            tz = tmp[2];
            // she keeps coming while the hammer flies: meet her where she will be
            if (giant.stagger <= 0f) {
                float d = (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - sz) * (tz - sz));
                tz -= giant.pace() * d / SPEED;
            }
        }
        float dx = tx - sx, dy = ty - sy, dz = tz - sz;
        float t = Math.max(0.12f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / SPEED);
        g.launch(sx, sy, sz, dx / t, dy / t - 0.5f * Physics.G * t, dz / t, t, ax);
        host.sound(Synth.WHOOSH, 1f, 1.3f);
        host.buzz(12);
    }

    /** The point under the ring: her head, her chest or her hips, or the plane she walks in. */
    void target(float sx, float sy, float[] out) {
        g.ray(sx, sy, dir);
        float dx = dir[0], dy = dir[1], dz = dir[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l;
        dy /= l;
        dz /= l;
        float ex = g.eye[0], ey = g.eye[1], ez = g.eye[2];
        float best = 1e9f;
        float[] c = scr3;
        if (!giant.down)
            for (int k = 0; k < 3; k++) {
                float r;
                if (k == 0) {
                    giant.headCenter(c);
                    r = Giant.HEAD_HIT;
                } else if (k == 1) {
                    giant.chest(c);
                    r = Giant.BODY_HIT + 0.2f;
                } else {
                    giant.hips(c);
                    r = Giant.BODY_HIT;
                }
                float ox = ex - c[0], oy = ey - c[1], oz = ez - c[2];
                float b = ox * dx + oy * dy + oz * dz, q = ox * ox + oy * oy + oz * oz - r * r;
                float disc = b * b - q;
                if (disc < 0f) continue;
                float t = -b - (float) Math.sqrt(disc);
                if (t > 0f && t < best) best = t;
            }
        if (best == 1e9f) best = Math.max(1f, (giant.z - ez) / Math.max(0.05f, dz));
        out[0] = ex + dx * best;
        out[1] = ey + dy * best;
        out[2] = ez + dz * best;
    }

    private final float[] scr3 = new float[3];

    /** Where the ring starts: on her chest. */
    void middle(float[] out) {
        giant.chest(tmp);
        if (!g.project(tmp[0], tmp[1], tmp[2], out)) {
            out[0] = g.fw * 0.5f;
            out[1] = g.fh * 0.45f;
        }
    }

    // ---- drawing ----

    private final float[] turn = new float[9];

    void draw(DrawList d) {
        Frida f = giant.body;
        f.fire = !giant.down && g.red() > 0f;
        if (giant.down) f.drawBroken(d);
        else f.draw(d);
        for (int i = 0; i < FLY_MAX; i++) {
            if (flyState[i] == 0) continue;
            Body c = fly[i];
            turn[0] = c.r00; turn[1] = c.r01; turn[2] = c.r02;
            turn[3] = c.r10; turn[4] = c.r11; turn[5] = c.r12;
            turn[6] = c.r20; turn[7] = c.r21; turn[8] = c.r22;
            g.traffic.car(d, c.px, c.py, c.pz, turn, c.tint, c.id);
        }
    }

    /** The fight's corner: the shields left on the left; on the right the blows still wanted, a block each. */
    void hud(Px f) {
        int x = 6, y = 8;
        for (int i = 0; i < ARMOR; i++) shield(f, x + i * 15, y, i < armor);
        int per = Giant.HITS / 2, bx = g.fw - 7 - per * 10;
        for (int i = 0; i < Giant.HITS; i++) {
            int cx = bx + (i % per) * 10, cy = y + (i / per) * 10;
            boolean whole = i >= giant.hits;
            // the block just knocked out flares white and shrinks to dark
            boolean last = i == giant.hits - 1 && hitT >= 0f;
            int in = whole ? 0xFFE2734E : last ? Px.mix(0xFFFFFFFF, 0xFF3A3236, hitT / 1.2f) : 0xFF3A3236;
            int grow = last ? Math.round(2f * (1f - hitT / 1.2f)) : 0;
            f.rect(cx - grow, cy - grow, cx + 8 + grow, cy + 8 + grow, 0xFF5A3A0E);
            f.rect(cx + 1 - grow, cy + 1 - grow, cx + 7 + grow, cy + 7 + grow, in);
        }
    }

    private static void shield(Px f, int x, int y, boolean on) {
        for (int r = 0; r < 13; r++) {
            int half = r < 8 ? 6 : 6 - (r - 7);
            for (int c = -half; c <= half; c++) {
                boolean edge = Math.abs(c) == half || r == 0 || r == 12;
                int col = edge ? 0xFF5A4A3A : on ? (c < 0 ? 0xFFB8D8E8 : 0xFF8FB4C8) : 0;
                if (col != 0) f.set(x + 6 + c, y + r, col);
            }
        }
    }

    /** A red veil at the edges of the frame when a car or her arm strikes home. */
    void veil(Px f) {
        if (flash <= 0f) return;
        int w = f.w, h = f.h;
        for (int yy = 0; yy < h; yy++)
            for (int xx = 0; xx < w; xx++) {
                float ex = Math.abs(xx - w * 0.5f) / (w * 0.5f), ey = Math.abs(yy - h * 0.5f) / (h * 0.5f);
                float a = flash * 0.6f * Math.max(0f, (Math.max(ex, ey) - 0.45f) / 0.55f);
                if (a <= 0f) continue;
                f.a[yy * w + xx] = ((int) (a * 255f) << 24) | 0xC02020;
            }
    }

    /** The street's jolt this frame, in frame pixels across and up. */
    void jolt(float[] out) {
        int a = Math.round(shake * 5f);
        out[0] = a == 0 ? 0f : (int) ((fx.nextFloat() - 0.5f) * 2f * a);
        out[1] = a == 0 ? 0f : (int) ((fx.nextFloat() - 0.5f) * 2f * a);
    }

    // ---- the marks over the frame ----
    // Through the red and the fire the eye needs help: brackets round each car in her hand or
    // in the air (red when the ring has it), the ring itself in the diamond's colours, and a
    // burst where a blow lands.

    private static final int POPS = 6, CAR_POP = 0, WEAK_POP = 1, BODY_POP = 2;
    private final float[] popX = new float[POPS], popY = new float[POPS], popZ = new float[POPS];
    private final float[] popT = {-1f, -1f, -1f, -1f, -1f, -1f};
    private final int[] popKind = new int[POPS];
    // seconds since the last block was knocked out of her
    private float hitT = -1f;
    private final float[] p0 = new float[2], p1 = new float[2], w3 = new float[3];

    private void pop(float x, float y, float z, int kind) {
        int k = 0;
        for (int i = 1; i < POPS; i++) if (popT[i] < 0f || popT[k] >= 0f && popT[i] > popT[k]) k = i;
        popX[k] = x;
        popY[k] = y;
        popZ[k] = z;
        popT[k] = 0f;
        popKind[k] = kind;
    }

    /** The car the ring has now, if any: the one in the air nearest it on screen, within reach. */
    private int locked(float ax, float ay) {
        int car = -1;
        float bd = SNAP;
        for (int i = 0; i < FLY_MAX; i++) {
            if (flyState[i] != 1 && flyState[i] != 2) continue;
            if (!g.project(fly[i].px, fly[i].py, fly[i].pz, scr)) continue;
            float d = (float) Math.hypot(scr[0] - ax, scr[1] - ay);
            if (d < bd) {
                bd = d;
                car = i;
            }
        }
        return car;
    }

    void marks(Px f, boolean aiming, float ax, float ay) {
        if (end >= 0f) {
            pops(f);
            return;
        }
        // the cars: white, red when the ring has one
        int lock = aiming ? locked(ax, ay) : -1;
        for (int i = 0; i < FLY_MAX; i++) {
            if (flyState[i] != 1 && flyState[i] != 2) continue;
            w3[0] = fly[i].px;
            w3[1] = fly[i].py;
            w3[2] = fly[i].pz;
            bracket(f, w3, 1.6f, i == lock ? 0xFFFF4A2A : 0xFFFFFFFF, i == lock ? 2 : 1);
        }
        pops(f);
        if (!aiming) return;
        // the ring, in the diamond's colours, red when it has a car
        int cx = Math.round(ax), cy = Math.round(ay);
        int c = lock >= 0 ? 0xFFFF4A2A : 0xFFBFF4FF;
        circle(f, cx, cy, 10, 0xFF1A1A22);
        circle(f, cx, cy, 9, c);
        circle(f, cx, cy, 8, 0xFFFFFFFF);
        circle(f, cx, cy, 7, 0xFF1A1A22);
        f.rect(cx - 1, cy - 1, cx + 1, cy + 1, c);
        // a thin line down to the finger, so the eye ties the ring to the hand
        for (int y = cy + 12; y < cy + (int) FINGER - 4; y += 3) f.set(cx, y, 0xAAFFFFFF);
    }

    /** Corner brackets round a point of the world, so many meters across. */
    private void bracket(Px f, float[] p, float r, int c, int thick) {
        if (!g.project(p[0], p[1], p[2], p0) || !g.project(p[0], p[1] + r, p[2], p1)) return;
        int s = Math.max(4, Math.min(34, Math.round(Math.abs(p0[1] - p1[1]))));
        int x = Math.round(p0[0]), y = Math.round(p0[1]), arm = Math.max(2, s / 2);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sy = -1; sy <= 1; sy += 2) {
                int ox = x + sx * s, oy = y + sy * s;
                // a dark edge outside, then the colour, one line or two
                for (int t = -1; t < thick; t++) {
                    int col = t < 0 ? 0xFF1A1A22 : c;
                    int ex = ox + sx * -t, ey = oy + sy * -t;
                    if (t < 0) {
                        ex = ox + sx;
                        ey = oy + sy;
                    }
                    for (int k = 0; k <= arm; k++) {
                        f.set(ex - sx * k, ey, col);
                        f.set(ex, ey - sy * k, col);
                    }
                }
            }
    }

    /** Bursts where blows landed: a wide white-blue one on her, orange off a car. */
    private void pops(Px f) {
        for (int i = 0; i < POPS; i++) {
            if (popT[i] < 0f) continue;
            w3[0] = popX[i];
            w3[1] = popY[i];
            w3[2] = popZ[i];
            if (!g.project(w3[0], w3[1], w3[2], p0)) continue;
            float k = popT[i] / 0.45f;
            int kind = popKind[i];
            int r = Math.round((kind == WEAK_POP ? 6f : 4f) + k * (kind == WEAK_POP ? 26f : kind == CAR_POP ? 18f : 10f));
            int c = kind == WEAK_POP ? 0xFFE8FAFF : kind == CAR_POP ? 0xFFFFB13A : 0xFFB8B0A8;
            int a = (int) (255f * (1f - k));
            c = (a << 24) | (c & 0xFFFFFF);
            int x = Math.round(p0[0]), y = Math.round(p0[1]);
            circle(f, x, y, r, c);
            if (kind == WEAK_POP) {
                circle(f, x, y, r - 3, c);
                for (int ray = 0; ray < 8; ray++) {
                    double ang = ray * Math.PI / 4 + 0.39;
                    for (int q = r + 2; q < r + 7; q++)
                        f.set(x + (int) Math.round(Math.cos(ang) * q), y + (int) Math.round(Math.sin(ang) * q), c);
                }
            }
        }
    }

    private static void circle(Px f, int cx, int cy, int r, int c) {
        int n = Math.max(24, r * 7);
        for (int a = 0; a < n; a++) {
            double t = a * Math.PI * 2 / n;
            f.set(cx + (int) Math.round(Math.cos(t) * r), cy + (int) Math.round(Math.sin(t) * r), c);
        }
    }

    // ---- for the desktop stand ----

    /** The frame point the stand's player should throw at now: a car on its way, else her chest; or null. */
    float[] standPick() {
        if (end >= 0f) return null;
        int best = -1;
        float bz = 1e9f;
        for (int i = 0; i < FLY_MAX; i++)
            if (flyState[i] == 2 && fly[i].pz < bz && flyT[i] > 0.35f) {
                bz = fly[i].pz;
                best = i;
            }
        float[] o = new float[2];
        if (best >= 0) {
            if (!g.project(fly[best].px, fly[best].py, fly[best].pz, o)) return null;
            return o;
        }
        giant.chest(tmp);
        if (!g.project(tmp[0], tmp[1], tmp[2], o)) return null;
        return o;
    }

    String standState() {
        StringBuilder cars = new StringBuilder();
        for (int i = 0; i < FLY_MAX; i++)
            if (flyState[i] != 0) cars.append(String.format(" car%d:%d@%.1f,%.1f,%.1f v%.1f,%.1f,%.1f t%.2f", i, flyState[i], fly[i].px, fly[i].py, fly[i].pz, fly[i].vx, fly[i].vy, fly[i].vz, flyT[i]));
        return String.format("z %.1f hits %d armor %d end %.1f %s%s", giant.z, giant.hits, armor, end, won ? "won" : "", cars);
    }
}
