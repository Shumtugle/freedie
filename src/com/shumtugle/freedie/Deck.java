package com.shumtugle.freedie;

import java.util.Random;

/**
 * Who watches. Between buildings the view tips up, almost straight up, and over the street,
 * among the clouds, hangs a great iron hoop: the floor of the dispatcher's round room, the
 * bridge, all of glass. Through it the room's curved walls show, its old green screens, its
 * amber lamps; and on the glass, bent over toward the middle, looking straight down at the
 * player, stand his other selves: Freedie of stone, as in town; Freedie of fire, embers in
 * her brick; Freedie of old ice. The fourth place on the ring stays empty: it is his.
 *
 * Only those he has met stand there: at first the stone one alone; the fiery one comes the
 * first time she gives him her golden hammer; the icy one the first time she arms him
 * against her sister, or once he is out on the ice.
 *
 * They judge the fall as the old gods used to, each in her own way: laughter, with lightning
 * from the fiery one, for a building felled at one throw; nods and a wave for a few throws;
 * arms crossed for many; and when the stars ran out, a fist, a head shaken, one of them
 * asleep. A gift comes down through a hatch in the middle of the glass, from the one whose
 * it is: the heavy hammer from the stone one, the golden from the fiery one, the diamond
 * from the icy one.
 *
 * The sky round the hoop is the hour's own, evening, night or the winter's grey; the room is
 * lit by its own lamps.
 */
final class Deck {
    static final int OVATION = 0, CHEER = 1, POLITE = 2, GLOOM = 3;
    static final int STONE = 0, FIRE = 1, ICE = 2;
    /** How far up the eye looks among them, radians from level. */
    static final float PITCH = 1.36f;
    static final float DIST = 21f, R = 9f, WALL = 8f;

    private final Frida[] her = new Frida[3];
    final boolean[] here = new boolean[3];
    private final float[] spot = {0.5f * (float) Math.PI, (float) Math.PI, 1.5f * (float) Math.PI};
    float cx, cy, cz;
    private int verdict = POLITE;
    private boolean jubilee;
    float time;
    int bolts;                    // how many strikes of lightning so far: the world thunders for each
    float boltNear = 1f;          // how near the last strike was
    private final float[] boltAt = new float[4];
    private int boltN;
    private final Random rnd = new Random(7);
    // the gift on its way down: which hammer, from whom, since when
    private int giftKind = -1, giver;
    private float giftT0;
    private final float[] pose = new float[12], tmp = new float[3];

    Deck() {
        float yaw = (float) Math.atan2(-World.CAM_X, World.PLINTH_Z);
        float c = (float) Math.cos(PITCH);
        cx = World.CAM_X + DIST * (float) Math.sin(yaw) * c;
        cy = World.CAM_Y + DIST * (float) Math.sin(PITCH);
        cz = World.CAM_Z + DIST * (float) Math.cos(yaw) * c;
        for (int k = 0; k < 3; k++) {
            Frida f = new Frida();
            f.kind = k;
            f.size = 2.05f;
            f.holding = false;
            f.y = cy;
            // round the ring, facing its middle; the turn about the upright that faces her there
            float a = spot[k] + yaw;
            f.x = cx + (float) Math.cos(a) * 6.8f;
            f.z = cz + (float) Math.sin(a) * 6.8f;
            her[k] = f;
        }
        here[STONE] = true;
    }

    /** A verdict: they turn to it; jubilee, and every one of them laughs aloud. A gift, if one comes: its hammer kind, or -1. */
    void begin(int verdict, boolean jubilee, int gift) {
        this.verdict = verdict;
        this.jubilee = jubilee;
        time = 0f;
        boltN = 0;
        giftKind = gift;
        giver = gift == Hammer.GOLDEN ? FIRE : gift == Hammer.DIAMOND ? ICE : STONE;
        if (gift >= 0) here[giver] = true;
        giftT0 = 2.0f;
        // the strikes of lightning from the fiery one, if she is there: for laughter, and for anger
        if (here[FIRE] && (verdict == OVATION || verdict == GLOOM)) {
            int n = verdict == OVATION ? 3 : 1;
            for (int i = 0; i < n; i++) boltAt[boltN++] = 1.6f + i * (0.45f + rnd.nextFloat() * 0.3f);
        }
    }

    void update(float dt) {
        float was = time;
        time += dt;
        for (int i = 0; i < boltN; i++)
            if (was < boltAt[i] && time >= boltAt[i]) {
                bolts++;
                boltNear = 0.6f + rnd.nextFloat() * 0.4f;
            }
        for (int k = 0; k < 3; k++) if (here[k]) pose(k);
    }

    // ---- their poses ----

    /**
     * Lying on the glass on her front, her head toward the middle, her face to the glass,
     * looking down at him, her shins kicked up behind her; then as the verdict moves her.
     */
    private void pose(int k) {
        Frida f = her[k];
        float yaw = (float) Math.atan2(-World.CAM_X, World.PLINTH_Z);
        float a = spot[k] + yaw;
        java.util.Arrays.fill(f.ax, 0f);
        java.util.Arrays.fill(f.az, 0f);
        java.util.Arrays.fill(f.ay, 0f);
        f.ay[Frida.ROOT] = TURN_SIGN * (0.5f * (float) Math.PI - a) + TURN_ADD;
        f.ax[Frida.ROOT] = PRONE;
        f.lift = -1.25f;
        f.ax[Frida.HEAD] = -0.55f;
        f.ax[Frida.LEG_A] = 0.1f;
        f.ax[Frida.LEG_B] = 0.1f;
        float t = time;
        // the shins kicked up behind her, swinging idly
        f.ax[Frida.SHIN_A] = -1.5f + 0.25f * (float) Math.sin(t * 1.7f + k);
        f.ax[Frida.SHIN_B] = -1.5f + 0.25f * (float) Math.sin(t * 1.7f + k + 2f);
        // propped on her elbows out to either side, her face clear between them
        f.az[Frida.ARM_A] = 1.25f;
        f.az[Frida.ARM_B] = -1.25f;
        f.ax[Frida.ARM_A] = 0.5f;
        f.ax[Frida.ARM_B] = 0.5f;
        f.ax[Frida.FORE_A] = 1.2f;
        f.ax[Frida.FORE_B] = 1.2f;
        f.ax[Frida.HAMMER] = 1.2f;
        f.eyes = 0.75f;
        f.head = 3;
        float s = (float) Math.sin(t * 9f + k);
        switch (mood(k)) {
            case 0: // laughter: fists drumming on the glass, the head thrown back and forth
                f.ax[Frida.ARM_A] = 1.45f + 0.35f * Math.abs(s);
                f.ax[Frida.ARM_B] = 1.45f + 0.35f * Math.abs((float) Math.sin(t * 9f + k + 1.6f));
                f.ax[Frida.HEAD] = -0.4f + 0.2f * (float) Math.sin(t * 11f + k);
                f.ax[Frida.SHIN_A] = -1.5f + 0.6f * (float) Math.sin(t * 8f);
                f.ax[Frida.SHIN_B] = -1.5f - 0.6f * (float) Math.sin(t * 8f);
                f.eyes = 1f;
                break;
            case 1: // a nod and a wave
                f.ax[Frida.HEAD] = -0.55f + 0.2f * (float) Math.sin(t * 5f + k);
                f.az[Frida.ARM_A] = 0.5f + 0.4f * (float) Math.sin(t * 6f + k);
                f.ax[Frida.ARM_A] = 2.2f;
                f.eyes = 0.9f;
                break;
            case 2: // chin on her crossed arms, unimpressed
                f.ax[Frida.ARM_A] = 2.3f;
                f.az[Frida.FORE_A] = -1.3f;
                f.ax[Frida.ARM_B] = 2.3f;
                f.az[Frida.FORE_B] = 1.3f;
                f.ax[Frida.HEAD] = -0.2f;
                f.az[Frida.HEAD] = 0.2f;
                f.eyes = 0.55f;
                break;
            case 3: // anger: a fist pounding the glass, eyes gone red
                f.ax[Frida.ARM_A] = 1.0f + 0.9f * Math.abs(s);
                f.ax[Frida.FORE_A] = 0.3f;
                f.eyes = 1f;
                f.head = 1;
                break;
            case 4: // asleep: the head down on one side, the eyes all but out, the shins still
                f.az[Frida.HEAD] = 0.6f;
                f.ax[Frida.HEAD] = -0.9f + 0.03f * (float) Math.sin(t * 1.5f);
                f.ax[Frida.SHIN_A] = -1.2f;
                f.ax[Frida.SHIN_B] = -1.3f;
                f.eyes = 0.06f;
                break;
            case 5: // the head shaken, slowly
                f.ay[Frida.HEAD] = 0.4f * (float) Math.sin(t * 4f);
                f.eyes = 0.7f;
                break;
            default: // looking on
                f.ax[Frida.HEAD] = -0.55f + 0.04f * (float) Math.sin(t * 1.3f + k);
                break;
        }
        // the one giving: her arm reaching over the hatch, letting go
        if (giftKind >= 0 && k == giver && t > giftT0 - 0.6f && t < giftT0 + 0.8f) {
            f.ax[Frida.ARM_A] = 2.6f;
            f.az[Frida.ARM_A] = -0.2f;
            f.ax[Frida.FORE_A] = 0f;
        }
        f.pose();
    }

    // how she is turned to lie toward the middle, and how far over on her front: found on the stand
    static float TURN_SIGN = 1f, TURN_ADD = 0f, PRONE = -1.5f;

    /** What each of them feels about the verdict: 0 laughter, 1 nod, 2 arms crossed, 3 anger, 4 asleep, 5 head shaken, 6 looking on. */
    private int mood(int k) {
        if (time < 1f) return 6;
        switch (verdict) {
            case OVATION:
                return jubilee || k != ICE ? 0 : 1;
            case CHEER:
                return k == FIRE ? 6 : 1;
            case POLITE:
                return k == STONE ? 2 : k == ICE ? 5 : 6;
            default:
                // the stars ran out: the fiery one is angry, the stone one shakes her head, the icy one sleeps;
                // alone, the stone one is angry herself
                if (!here[FIRE] && k == STONE) return 3;
                return k == FIRE ? 3 : k == STONE ? 5 : 4;
        }
    }

    // ---- drawing ----

    static final int IRON = 0x1E2125, IRON_DARK = 0x15171A, AMBER = 0xFFB347, FROST = 0xD6E4EE, SCREEN = 0x3EDC6A;

    void draw(DrawList d) {
        d.aloft = true;
        drawn(d);
        d.aloft = false;
    }

    private void drawn(DrawList d) {
        // the lamps burn warm over the evening street and cold over the winter sea
        int lamp = Street.mix(AMBER, FROST, d.overcast);
        // the hoop, and its lamps all round underneath
        Round.ring(d, cx, cy, cz, null, -0.55f, 0.55f, R, R + 1.1f, IRON, DrawList.PAINT);
        Round.ring(d, cx, cy - 0.5f, cz, null, -0.08f, 0.08f, R + 0.2f, R + 0.9f, IRON_DARK, DrawList.PAINT);
        for (int i = 0; i < 16; i++) {
            float a = i * (float) Math.PI / 8f;
            float[] r = Ocean.turn(-a, 0f, 0f);
            d.box(cx + (float) Math.cos(a) * (R + 0.55f), cy - 0.6f, cz + (float) Math.sin(a) * (R + 0.55f), 0.32f, 0.06f, 0.12f, r,
                    lamp, DrawList.GLOW);
        }
        // the glass: nothing but its frame, iron ribs out from the hatch and a ring between, a glint or two
        for (int i = 0; i < 8; i++) {
            float a = i * (float) Math.PI / 4f + 0.2f;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            Crane.beam(d, cx + c * 1.4f, cy, cz + s * 1.4f, cx + c * R, cy, cz + s * R, 0.11f, IRON, DrawList.PAINT);
        }
        Round.ring(d, cx, cy, cz, null, -0.1f, 0.1f, R * 0.55f - 0.12f, R * 0.55f + 0.12f, IRON, DrawList.PAINT);
        hatch(d);
        for (int i = 0; i < 3; i++) {
            float a = 0.9f + i * 1.7f, l = 2.5f + i;
            float c = (float) Math.cos(a), s = (float) Math.sin(a), r0 = 2.5f + i * 2.2f;
            Crane.beam(d, cx + c * r0, cy - 0.02f, cz + s * r0, cx + c * r0 - s * l, cy - 0.02f, cz + s * r0 + c * l, 0.04f,
                    0xDDE8F0, DrawList.GLOW);
        }
        // the room's wall going up from the glass, its screens and lamps facing in
        Round.ring(d, cx, cy, cz, null, 0.1f, WALL, R - 0.05f, R + 0.45f, IRON_DARK, DrawList.PAINT);
        for (int i = 0; i < 12; i++) {
            float a = i * (float) Math.PI / 6f + 0.26f;
            float[] r = Ocean.turn((float) Math.PI * 0.5f - a, 0f, 0f);
            float px = cx + (float) Math.cos(a) * (R - 0.35f), pz = cz + (float) Math.sin(a) * (R - 0.35f);
            d.box(px, cy + 2.4f, pz, 1.0f, 0.85f, 0.28f, r, 0x3C4A44, DrawList.PAINT);
            float flick = 0.85f + 0.15f * (float) Math.sin(time * 9f + i * 2.3f);
            int scr = Street.scale(SCREEN, flick);
            float qx = cx + (float) Math.cos(a) * (R - 0.65f), qz = cz + (float) Math.sin(a) * (R - 0.65f);
            d.box(qx, cy + 2.45f, qz, 0.72f, 0.6f, 0.03f, r, scr, DrawList.GLOW);
            float lx = cx + (float) Math.cos(a + 0.26f) * (R - 0.4f), lz = cz + (float) Math.sin(a + 0.26f) * (R - 0.4f);
            d.box(lx, cy + 4.6f, lz, 0.18f, 0.12f, 0.18f, null, lamp, DrawList.GLOW);
        }
        // the ceiling, dark, a lamp hanging from its middle: seen through the glass, the room is a room
        Round.cylinder(d, cx, cy + WALL, cz, null, -0.2f, 0.2f, R + 0.4f, 0x101215, DrawList.PAINT);
        d.box(cx, cy + WALL - 1.4f, cz, 0.04f, 1.2f, 0.04f, null, IRON, DrawList.PLAIN);
        d.box(cx, cy + WALL - 2.7f, cz, 0.55f, 0.18f, 0.55f, null, lamp, DrawList.GLOW);
        // the consoles round the foot of the wall
        Round.ring(d, cx, cy, cz, null, 0.1f, 1.1f, R - 1.4f, R - 0.1f, 0x2C3036, DrawList.PAINT);
        for (int k = 0; k < 3; k++) if (here[k]) her[k].draw(d);
        bolts(d);
        gift(d);
    }

    /** The hatch in the middle of the glass: an iris of iron leaves, open while a gift comes down. */
    private void hatch(DrawList d) {
        float open = 0f;
        if (giftKind >= 0) {
            float t = time - giftT0;
            open = t < -0.4f ? 0f : t < 0f ? (t + 0.4f) / 0.4f : t < 1.2f ? 1f : Math.max(0f, 1f - (t - 1.2f) / 0.4f);
        }
        Round.ring(d, cx, cy, cz, null, -0.14f, 0.14f, 1.3f, 1.6f, IRON, DrawList.PAINT);
        for (int i = 0; i < 6; i++) {
            float a = i * (float) Math.PI / 3f + open * 0.9f;
            float rr = 0.65f + open * 0.75f;
            float[] r = Ocean.turn(-a, 0f, 0f);
            d.box(cx + (float) Math.cos(a) * rr, cy - 0.05f, cz + (float) Math.sin(a) * rr, 0.62f, 0.05f, 0.36f, r, IRON_DARK, DrawList.PAINT);
        }
    }

    /** The gift: a hammer let fall through the hatch, tumbling down past the eye. */
    private void gift(DrawList d) {
        if (giftKind < 0) return;
        float t = time - giftT0;
        if (t < 0f || t > 2.2f) return;
        // from the hatch down toward a point a little in front of and below the eye
        float ex = World.CAM_X + 0.6f, ey = World.CAM_Y + 4f, ez = World.CAM_Z + 3f;
        float k = Math.min(1f, t * t / 1.6f);
        pose[0] = cx + (ex - cx) * k;
        pose[1] = cy - 0.4f + (ey - cy + 0.4f) * k;
        pose[2] = cz + (ez - cz) * k;
        float a = t * 6.5f, c = (float) Math.cos(a), s = (float) Math.sin(a);
        // tumbling end over end
        pose[3] = c; pose[4] = -s; pose[5] = 0f;
        pose[6] = s; pose[7] = c; pose[8] = 0f;
        pose[9] = 0f; pose[10] = 0f; pose[11] = 1f;
        Hammer.draw(d, pose, giftKind, 3.2f);
    }

    /** Lightning from the fiery one's raised fist, up and out into the sky. */
    private void bolts(DrawList d) {
        if (!here[FIRE]) return;
        for (int i = 0; i < boltN; i++) {
            float age = time - boltAt[i];
            if (age < 0f || age > 0.22f) continue;
            her[FIRE].fist(tmp);
            Random r = new Random(i * 7919L + 13);
            float x = tmp[0], y = tmp[1], z = tmp[2];
            // away from the middle, up into the sky beyond the room
            float ox = x - cx, oz = z - cz, l = (float) Math.sqrt(ox * ox + oz * oz) + 1e-3f;
            ox /= l;
            oz /= l;
            int c = age < 0.08f ? 0xFFFFFF : 0xCFE6FF;
            for (int s = 0; s < 9; s++) {
                float nx = x + ox * 2.2f + (r.nextFloat() - 0.5f) * 2.4f, ny = y + 3.2f + r.nextFloat() * 1.5f,
                        nz = z + oz * 2.2f + (r.nextFloat() - 0.5f) * 2.4f;
                Crane.beam(d, x, y, z, nx, ny, nz, 0.09f, c, DrawList.GLOW);
                x = nx;
                y = ny;
                z = nz;
            }
        }
    }
}
