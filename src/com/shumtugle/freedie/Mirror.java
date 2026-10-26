package com.shumtugle.freedie;

import java.util.Random;

/**
 * Who watches. Between buildings the view tips up, almost straight up, and high over the
 * street hangs a great ball of polished steel in a wreath of black storm cloud. Seen from
 * below, it shows the street from above, bent round as in a drop of mercury: the plinth and
 * what is left on it, the cars, the roofs, the horizon in a ring at its rim. And in its
 * middle, small, whoever looks up at it: a dark Freedie with burning eyes. Not plain at the
 * first look; plain enough at the second.
 *
 * The verdict is the weather's. Lightning out of the wreath and thunder for a building
 * felled at one throw; a far flash for a few throws; nothing for many; and when the stars
 * ran out, the wreath goes blacker and one bolt falls. In each flash, for as long as the sky
 * is lit, a face shows through the steel, pale and see-through, and is gone with it:
 * smiling for one throw, calm for a few, stern when the stars ran out. A gift comes down out of the mirror
 * itself: a ring runs out over the steel as over a liquid, and the hammer falls through it.
 */
final class Mirror {
    static final int OVATION = 0, CHEER = 1, POLITE = 2, GLOOM = 3;
    /** How far up the eye looks at it, radians from level. */
    static final float PITCH = 1.36f;
    static final float DIST = 21f, R = 10.5f;
    /** How far out the wreath goes, in mirror widths from its middle. */
    static final float WREATH = 1.35f;

    float cx, cy, cz;
    private final float yaw;
    private int verdict = POLITE;
    float time;
    int bolts;                    // how many strikes of lightning so far: the world thunders for each
    float boltNear = 1f;          // how near the last strike was
    private final float[] boltAt = new float[4], boltNearAt = new float[4];
    private int boltN;
    private final Random rnd = new Random(7);
    // the gift on its way down: which hammer, since when
    private int giftKind = -1;
    private float giftT0;
    // the last ring over the steel: when, how strong
    private float ringT0 = -99f, ringK;
    private final float[] pose = new float[12], p0 = new float[2], p1 = new float[2];
    // whoever stands where the eye is, as the mirror shows her
    private final Frida self = new Frida();
    // the faces that show in the flashes: stern, calm, smiling; straight RGBA, each its size
    static final int STERN = 0, CALM = 1, SMILE = 2;
    private int[][] face;
    private int[] faceW, faceH;
    // where the mirror is on the frame just now, in frame pixels: its middle and radius
    private float onX, onY, onR;

    Mirror() {
        yaw = (float) Math.atan2(-World.CAM_X, World.PLINTH_Z);
        float c = (float) Math.cos(PITCH);
        cx = World.CAM_X + DIST * (float) Math.sin(yaw) * c;
        cy = World.CAM_Y + DIST * (float) Math.sin(PITCH);
        cz = World.CAM_Z + DIST * (float) Math.cos(yaw) * c;
        self.kind = Frida.SHADOW_KIND;
        self.size = SELF_SIZE;
        self.holding = true;
        self.x = World.CAM_X;
        self.z = World.CAM_Z;
        self.y = 0f;
        self.eyes = 1f;
    }

    // a giant, as the fiery one is in the fight: from up there anything his size would be a
    // speck, and the mirror shows him as what he is
    static float SELF_SIZE = 1.5f;

    /** A verdict, and a gift if one comes (its hammer kind, or -1). */
    void begin(int verdict, int gift) {
        this.verdict = verdict;
        time = 0f;
        boltN = 0;
        giftKind = gift;
        giftT0 = 2.0f;
        ringT0 = -99f;
        if (verdict == OVATION) {
            for (int i = 0; i < 3; i++) {
                boltAt[boltN] = 1.0f + i * (0.45f + rnd.nextFloat() * 0.3f);
                boltNearAt[boltN++] = 0.7f + rnd.nextFloat() * 0.3f;
            }
        } else if (verdict == CHEER) {
            boltAt[boltN] = 1.2f;
            boltNearAt[boltN++] = 0.35f;
        } else if (verdict == GLOOM) {
            boltAt[boltN] = 1.5f;
            boltNearAt[boltN++] = 1f;
        }
    }

    void update(float dt) {
        float was = time;
        time += dt;
        for (int i = 0; i < boltN; i++)
            if (was < boltAt[i] && time >= boltAt[i]) {
                bolts++;
                boltNear = boltNearAt[i];
                // the steel shivers with the near ones
                if (boltNear > 0.6f && time - ringT0 > 1f) ring(0.45f);
            }
        if (giftKind >= 0 && was < giftT0 - 0.15f && time >= giftT0 - 0.15f) ring(1f);
        pose();
    }

    /** Reads the faces: a count, then for each its width, height and straight RGBA rows. */
    void readFaces(byte[] b) {
        if (b == null || b.length < 2) return;
        int n = ((b[0] & 0xFF) << 8) | (b[1] & 0xFF), p = 2;
        face = new int[n][];
        faceW = new int[n];
        faceH = new int[n];
        for (int i = 0; i < n && p + 4 <= b.length; i++) {
            int w = ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF), h = ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
            p += 4;
            if (p + w * h * 4 > b.length) return;
            int[] img = new int[w * h];
            for (int k = 0; k < img.length; k++, p += 4)
                img[k] = ((b[p + 3] & 0xFF) << 24) | ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
            face[i] = img;
            faceW[i] = w;
            faceH[i] = h;
        }
    }

    private void ring(float k) {
        ringT0 = time;
        ringK = k;
    }

    /** Standing on the street where the eye is, her face turned up to the mirror. */
    private void pose() {
        Frida f = self;
        java.util.Arrays.fill(f.ax, 0f);
        java.util.Arrays.fill(f.az, 0f);
        java.util.Arrays.fill(f.ay, 0f);
        // facing down the street toward the plinth (she faces -z unturned)
        float a = (float) Math.atan2(-Math.cos(yaw), -Math.sin(yaw));
        f.ay[Frida.ROOT] = 0.5f * (float) Math.PI - a;
        f.ax[Frida.HEAD] = HEAD_UP;
        f.ax[Frida.CHEST] = LEAN;
        // arms a little out from her sides, the hammer hanging from one hand
        f.az[Frida.ARM_A] = ARMS;
        f.az[Frida.ARM_B] = -ARMS;
        f.ax[Frida.FORE_A] = 0.3f + 0.04f * (float) Math.sin(time * 1.3f);
        f.ax[Frida.FORE_B] = 0.3f;
        f.lift = 0f;
        f.pose();
    }

    // how her head tips back to look up: found on the stand
    static float HEAD_UP = 1.3f, ARMS = 0.45f, LEAN = 0.35f;

    // ---- drawing ----

    void draw(DrawList d) {
        d.mirror[0] = cx;
        d.mirror[1] = cy;
        d.mirror[2] = cz;
        d.mirror[3] = R;
        d.ball(cx, cy, cz, R, null, 0xC8CED6, DrawList.MIRROR);
        d.mirrored = true;
        self.draw(d);
        d.mirrored = false;
        // the wreath, blacker when the stars ran out
        float gloom = verdict == GLOOM ? Math.min(1f, Math.max(0f, time - 0.6f) / 0.8f) : 0f;
        d.halo[0] = WREATH + 0.12f * gloom;
        d.halo[1] = gloom;
        float flash = flash();
        d.halo[2] = flash;
        d.bolt = flash * boltNear;
        if (time - ringT0 < 3.5f) {
            d.rippleAge = time - ringT0;
            d.ripple = ringK;
        }
        bolts(d);
        gift(d);
    }

    /** How bright the lightning is just now, flickering: on, off a moment, on, and gone. */
    private float flash() {
        float f = 0f;
        for (int i = 0; i < boltN; i++) {
            float age = time - boltAt[i];
            if (age < 0f || age > 0.32f) continue;
            float k = age < 0.06f ? 1f : age < 0.1f ? 0.15f : age < 0.2f ? 0.85f : 0.85f * (0.32f - age) / 0.12f;
            f = Math.max(f, k);
        }
        return f;
    }

    /**
     * The bolts, as strokes the sky draws: out of the wreath, jagged, away from the mirror and
     * down, with a fork; a wide faint glow under each stroke. The near ones are thick.
     */
    private void bolts(DrawList d) {
        onR = 0f;
        if (!d.project(cx, cy, cz, p0)) return;
        float ox = p0[0], oy = p0[1];
        // the mirror's radius on the frame, from a point on its rim
        if (!d.project(cx + d.right[0] * R, cy + d.right[1] * R, cz + d.right[2] * R, p1)) return;
        float rpx = (float) Math.hypot(p1[0] - ox, p1[1] - oy);
        onX = ox;
        onY = oy;
        onR = rpx;
        for (int i = 0; i < boltN; i++) {
            float age = time - boltAt[i];
            if (age < 0f || age > 0.2f || (age > 0.06f && age < 0.1f)) continue;
            Random r = new Random(i * 7919L + bolts * 31L + 13);
            float near = boltNearAt[i];
            float w = 2.2f + 2.6f * near;
            float ang = r.nextFloat() * 6.2832f;
            float x = ox + (float) Math.cos(ang) * rpx * 1.15f, y = oy + (float) Math.sin(ang) * rpx * 1.15f;
            // away from the middle, bending down toward the street
            float dir = ang + (r.nextFloat() - 0.5f) * 0.6f;
            int steps = 7 + (int) (near * 3f);
            float len = rpx * (0.2f + 0.1f * near);
            for (int s = 0; s < steps; s++) {
                dir += (r.nextFloat() - 0.5f) * 1.1f;
                // pulled toward down the frame
                float dx = (float) Math.cos(dir), dy = (float) Math.sin(dir) + 0.35f;
                float l = (float) Math.hypot(dx, dy);
                float nx = x + dx / l * len, ny = y + dy / l * len;
                float w0 = w * (1f - 0.08f * s), w1 = w * (1f - 0.08f * (s + 1));
                d.fly(x, y, nx, ny, w0 * 3f, w1 * 3f, 0.3f, 0.25f, false);
                d.fly(x, y, nx, ny, w0, w1, 1f, 1f, false);
                if (s == 2 || (s == 4 && near > 0.8f)) {
                    // a fork, thinner, its own way
                    float bx = x, by = y, bd = dir + (r.nextBoolean() ? 0.9f : -0.9f);
                    for (int k = 0; k < 3; k++) {
                        bd += (r.nextFloat() - 0.5f) * 1.0f;
                        float ex = bx + (float) Math.cos(bd) * len * 0.7f, ey = by + (float) Math.sin(bd) * len * 0.7f;
                        d.fly(bx, by, ex, ey, w0 * 0.5f, w0 * 0.35f, 0.9f, 0.6f, false);
                        bx = ex;
                        by = ey;
                    }
                }
                x = nx;
                y = ny;
            }
        }
    }

    /**
     * The face in the flash, laid over the mirror on the flat layer: as bright as the flash,
     * see-through, a little cold, kept inside the steel's rim.
     */
    void flat(Px f) {
        // the face lingers a breath longer than the light, an afterimage
        float k = 0f;
        for (int i = 0; i < boltN; i++) {
            float age = time - boltAt[i];
            if (age < 0f || age > 0.5f) continue;
            k = Math.max(k, age < 0.06f ? 1f : age < 0.1f ? 0.55f : age < 0.2f ? 0.9f : 0.9f * (0.5f - age) / 0.3f);
        }
        if (face == null || k < 0.03f || onR <= 0f) return;
        int which = verdict == OVATION ? SMILE : verdict == GLOOM ? STERN : CALM;
        if (which >= face.length || face[which] == null) return;
        int[] img = face[which];
        int iw = faceW[which], ih = faceH[which];
        float scale = onR * FACE / iw;                     // frame pixels per picture pixel
        int w = Math.round(iw * scale), h = Math.round(ih * scale);
        int x0 = Math.round(onX - w * 0.5f), y0 = Math.round(onY - h * 0.5f);
        float rim2 = onR * onR * 0.9f;
        for (int y = 0; y < h; y++) {
            int fy = y0 + y;
            if (fy < 0 || fy >= f.h) continue;
            int sy = Math.min(ih - 1, (int) (y / scale));
            for (int x = 0; x < w; x++) {
                int fx = x0 + x;
                if (fx < 0 || fx >= f.w) continue;
                float dx = fx - onX, dy = fy - onY;
                if (dx * dx + dy * dy > rim2) continue;
                int c = img[sy * iw + Math.min(iw - 1, (int) (x / scale))];
                float a = ((c >>> 24) / 255f) * k * SHOW;
                if (a < 0.02f) continue;
                // a little cold, as the lightning lights it
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                r = (r * 3 + 200) / 4;
                g = (g * 3 + 214) / 4;
                b = (b * 3 + 255) / 4;
                int under = f.a[fy * f.w + fx];
                float ua = (under >>> 24) / 255f;
                float oa = a + ua * (1f - a);
                if (oa <= 0f) continue;
                int or = Math.round((r * a + ((under >> 16) & 0xFF) * ua * (1f - a)) / oa);
                int og = Math.round((g * a + ((under >> 8) & 0xFF) * ua * (1f - a)) / oa);
                int ob = Math.round((b * a + (under & 0xFF) * ua * (1f - a)) / oa);
                f.a[fy * f.w + fx] = (Math.round(oa * 255f) << 24) | (or << 16) | (og << 8) | ob;
            }
        }
    }

    // how wide the face is against the mirror's radius, and how much of it shows at the flash's height
    static float FACE = 1.15f, SHOW = 0.6f;

    /** The gift: a hammer let fall out of the mirror, tumbling down past the eye. */
    private void gift(DrawList d) {
        if (giftKind < 0) return;
        float t = time - giftT0;
        if (t < 0f || t > 2.2f) return;
        // out of the steel where it faces the eye, down toward a point in front of and below the eye
        float ex = World.CAM_X + 0.6f, ey = World.CAM_Y + 4f, ez = World.CAM_Z + 3f;
        float ux = World.CAM_X - cx, uy = World.CAM_Y - cy, uz = World.CAM_Z - cz;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        float sx = cx + ux / ul * R * 0.8f, sy = cy + uy / ul * R * 0.8f, sz = cz + uz / ul * R * 0.8f;
        float k = Math.min(1f, t * t / 1.6f);
        pose[0] = sx + (ex - sx) * k;
        pose[1] = sy + (ey - sy) * k;
        pose[2] = sz + (ez - sz) * k;
        // inside the steel it is not seen, and must not fill what the mirror sees
        float qx = pose[0] - cx, qy = pose[1] - cy, qz = pose[2] - cz;
        if (qx * qx + qy * qy + qz * qz < R * R * 1.1f) return;
        float a = t * 6.5f, c = (float) Math.cos(a), s = (float) Math.sin(a);
        // tumbling end over end
        pose[3] = c; pose[4] = -s; pose[5] = 0f;
        pose[6] = s; pose[7] = c; pose[8] = 0f;
        pose[9] = 0f; pose[10] = 0f; pose[11] = 1f;
        Hammer.draw(d, pose, giftKind, 3.2f);
    }
}
