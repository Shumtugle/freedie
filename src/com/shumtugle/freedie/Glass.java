package com.shumtugle.freedie;

import java.util.Random;

/**
 * The glass of the screen breaking where the hammer strikes it, drawn on a fine layer over the
 * whole frame. From the point of the blow the cracks run out in long jagged rays, and rings of
 * cracks cross them further and further out, so the pane is cut into shards: small ones near
 * the blow, great ones at the edges. Where the head came through, the glass is gone, ground to
 * glitter that flies at the eye; the shards round the hole come away too, some at once, some a
 * moment later, growing as they come nearer, turning, catching the light along their edges and
 * blurring as they pass; the rest stay in the pane, each lit a little differently, its edges
 * bright, a glint here and there where cracks meet. Then the pane fades.
 *
 * Measures are in frame pixels; the layer is so many times finer.
 */
final class Glass {
    static final int RAYS = 15;
    // the rings of cracks, as shares of the frame's width out from the blow
    private static final float[] RING = {0.16f, 0.34f, 0.56f, 0.86f, 1.25f, 1.8f};
    static final float FADE0 = 0.35f, FADE1 = 0.95f;   // the pane fades between these, seconds after the blow
    static final float FLY = 0.75f;                     // how long a shard flies before it is gone

    final Ink ink, pane;
    private final float k;                 // layer pixels to a frame pixel
    private final float px, py;            // the blow, frame pixels
    private final int rings = RING.length;
    // where the rays cross the rings, and the points between rays along each ring
    private final float[] vx, vy, mx, my;
    // each shard between ray i and the next, ring r and the next: whether it comes away, when
    private final boolean[] flies;
    private final float[] delay, spinRate, speed, lit;
    // glitter out of the hole
    private static final int BITS = 70;
    private final float[] bx = new float[BITS], by = new float[BITS], bvx = new float[BITS], bvy = new float[BITS],
            bs = new float[BITS], bspin = new float[BITS];
    private boolean paneDrawn;

    /** A pane for a frame w by h frame pixels, the layer k times finer, struck at (x, y). */
    Glass(int w, int h, int k, float x, float y) {
        this.k = k;
        px = x;
        py = y;
        ink = new Ink(w * k, h * k);
        pane = new Ink(w * k, h * k);
        Random r = new Random(5);
        vx = new float[RAYS * rings];
        vy = new float[RAYS * rings];
        mx = new float[RAYS * rings];
        my = new float[RAYS * rings];
        float[] ang = new float[RAYS];
        float step = (float) (Math.PI * 2 / RAYS);
        for (int i = 0; i < RAYS; i++) ang[i] = i * step + (r.nextFloat() - 0.5f) * step * 0.55f;
        for (int i = 0; i < RAYS; i++)
            for (int j = 0; j < rings; j++) {
                // the ray wanders a little at each ring, and each ring sits nearer or further on each ray
                float a = ang[i] + (r.nextFloat() - 0.5f) * step * 0.18f;
                float rad = RING[j] * w * (0.88f + r.nextFloat() * 0.24f);
                vx[i * rings + j] = x + (float) Math.cos(a) * rad;
                vy[i * rings + j] = y + (float) Math.sin(a) * rad;
            }
        for (int i = 0; i < RAYS; i++)
            for (int j = 0; j < rings; j++) {
                // between this ray and the next along the ring: off the straight line, in or out
                int a = i * rings + j, b = ((i + 1) % RAYS) * rings + j;
                float cx = (vx[a] + vx[b]) * 0.5f, cy = (vy[a] + vy[b]) * 0.5f;
                float ox = cx - x, oy = cy - y, ol = (float) Math.sqrt(ox * ox + oy * oy) + 1e-3f;
                float push = (r.nextFloat() - 0.35f) * RING[j] * w * 0.12f;
                mx[a] = cx + ox / ol * push;
                my[a] = cy + oy / ol * push;
            }
        int cells = RAYS * (rings - 1);
        flies = new boolean[cells];
        delay = new float[cells];
        spinRate = new float[cells];
        speed = new float[cells];
        lit = new float[cells];
        for (int c = 0; c < cells; c++) {
            int ring = c % (rings - 1);
            flies[c] = ring <= 1 || ring == 2 && r.nextFloat() < 0.3f;
            delay[c] = ring <= 1 ? r.nextFloat() * 0.03f : 0.12f + r.nextFloat() * 0.3f;
            spinRate[c] = (r.nextFloat() - 0.5f) * 7f;
            speed[c] = 0.6f + r.nextFloat() * 0.9f;
            lit[c] = r.nextFloat();
        }
        for (int i = 0; i < BITS; i++) {
            float a = r.nextFloat() * 6.2832f, d = r.nextFloat() * RING[0] * w;
            bx[i] = x + (float) Math.cos(a) * d;
            by[i] = y + (float) Math.sin(a) * d;
            float v = 120f + r.nextFloat() * 260f;
            bvx[i] = (float) Math.cos(a) * v;
            bvy[i] = (float) Math.sin(a) * v - 40f;
            bs[i] = 0.8f + r.nextFloat() * 2.2f;
            bspin[i] = r.nextFloat() * 6f;
        }
    }

    // a shard's corners: along ring r from ray i to the next, then back along ring r + 1
    private final float[] sx = new float[6], sy = new float[6];

    private void corners(int c) {
        int i = c / (rings - 1), j = c % (rings - 1), n = (i + 1) % RAYS;
        int a0 = i * rings + j, a1 = i * rings + j + 1, b0 = n * rings + j, b1 = n * rings + j + 1;
        sx[0] = vx[a0]; sy[0] = vy[a0];
        sx[1] = mx[a0]; sy[1] = my[a0];
        sx[2] = vx[b0]; sy[2] = vy[b0];
        sx[3] = vx[b1]; sy[3] = vy[b1];
        sx[4] = mx[a1]; sy[4] = my[a1];
        sx[5] = vx[a1]; sy[5] = vy[a1];
    }

    /** Whether a shard that stays borders the hole: the one nearer the blow on its ray is gone. */
    private boolean rimmed(int c) {
        int ring = c % (rings - 1);
        return ring > 0 && flies[c - 1];
    }

    private final float[] shape = new float[12];

    /**
     * One shard: its corners moved by (dx, dy), grown by s and turned by a about its middle,
     * filled with a faint sheen of its own, its edges lit; blurred by so much.
     */
    private void shard(Ink g, float dx, float dy, float s, float a, float sheen, float edge, float blur, float cover) {
        shard(g, dx, dy, s, a, sheen, edge, 1.1f, blur, cover);
    }

    private void shard(Ink g, float dx, float dy, float s, float a, float sheen, float edge, float width, float blur, float cover) {
        float cx = 0f, cy = 0f;
        for (int q = 0; q < 6; q++) {
            cx += sx[q];
            cy += sy[q];
        }
        cx /= 6f;
        cy /= 6f;
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        for (int q = 0; q < 6; q++) {
            float ox = (sx[q] - cx) * s, oy = (sy[q] - cy) * s;
            shape[q * 2] = (cx + dx + ox * ca - oy * sa) * k;
            shape[q * 2 + 1] = (cy + dy + ox * sa + oy * ca) * k;
        }
        g.shape(shape);
        int tint = ((int) (sheen * 255f) << 24) | 0xE8F6FF;
        g.polyFill(tint, tint, blur * k, cover);
        if (edge <= 0f) return;
        int bright = ((int) (edge * 255f) << 24) | 0xFFFFFF;
        float wdt = (width + 0.6f * (s - 1f)) * k * 0.5f;
        for (int q = 0; q < 6; q++) {
            int p = (q + 1) % 6;
            g.line(shape[q * 2], shape[q * 2 + 1], shape[p * 2], shape[p * 2 + 1], wdt + blur * k * 0.5f, bright, cover);
        }
    }

    /** The pane at so many seconds after the blow, onto the layer. */
    void draw(float t) {
        int cells = RAYS * (rings - 1);
        if (!paneDrawn) {
            // what stays in the pane is drawn once: each shard lit its own way, the cracks bright,
            // brighter near the blow; a fine line of light inside some edges; glints where cracks meet
            pane.clear();
            Random r = new Random(11);
            for (int c = 0; c < cells; c++) {
                if (flies[c]) continue;
                corners(c);
                int ring = c % (rings - 1);
                // each shard a faint sheen of its own; the cracks are drawn after, along rays and rings
                shard(pane, 0f, 0f, 1f, 0f, 0.025f + 0.07f * lit[c], 0f, 1f, 1f, 1f);
                if (rimmed(c)) {
                    // the rim of the hole: the broken edge of the pane, thick, catching the light
                    pane.line(sx[0] * k, sy[0] * k, sx[1] * k, sy[1] * k, 3.4f * k, 0x5590D8FF, 1f);
                    pane.line(sx[1] * k, sy[1] * k, sx[2] * k, sy[2] * k, 3.4f * k, 0x5590D8FF, 1f);
                    pane.line(sx[0] * k, sy[0] * k, sx[1] * k, sy[1] * k, 1.6f * k, 0xF0FFFFFF, 1f);
                    pane.line(sx[1] * k, sy[1] * k, sx[2] * k, sy[2] * k, 1.6f * k, 0xF0FFFFFF, 1f);
                }
                // a second, thinner line just inside one or two edges: the light down a cut face
                for (int q = 0; q < 6; q++) {
                    if (r.nextFloat() > 0.3f) continue;
                    int p = (q + 1) % 6;
                    float cx = (sx[0] + sx[2] + sx[3] + sx[5]) * 0.25f, cy = (sy[0] + sy[2] + sy[3] + sy[5]) * 0.25f;
                    float ax = sx[q] + (cx - sx[q]) * 0.05f, ay = sy[q] + (cy - sy[q]) * 0.05f;
                    float bx2 = sx[p] + (cx - sx[p]) * 0.05f, by2 = sy[p] + (cy - sy[p]) * 0.05f;
                    pane.line(ax * k, ay * k, bx2 * k, by2 * k, 0.6f * k, 0x99CFF0FF, 1f);
                }
            }
            // the rays: long cracks out to the edges, bright and thick near the hole, finer further out
            for (int i = 0; i < RAYS; i++)
                for (int j = 1; j < rings - 1; j++) {
                    int left = ((i + RAYS - 1) % RAYS) * (rings - 1) + j, right = i * (rings - 1) + j;
                    if (flies[left] && flies[right]) continue;
                    int a = i * rings + j;
                    float al = j <= 2 ? 0.85f : j == 3 ? 0.55f : 0.4f, wd = j <= 2 ? 1.4f : j == 3 ? 1f : 0.8f;
                    pane.line(vx[a] * k, vy[a] * k, vx[a + 1] * k, vy[a + 1] * k, wd * k, ((int) (al * 255f) << 24) | 0xFFFFFF, 1f);
                }
            // the rings: whole round the hole, further out only here and there
            for (int j = 1; j < rings - 1; j++)
                for (int i = 0; i < RAYS; i++) {
                    int inner = i * (rings - 1) + j - 1, outer = i * (rings - 1) + j;
                    if (flies[inner] && flies[outer]) continue;
                    if (j == 3 && r.nextFloat() > 0.4f || j > 3 && r.nextFloat() > 0.14f) continue;
                    int a = i * rings + j, b = ((i + 1) % RAYS) * rings + j;
                    float al = j <= 2 ? 0.8f : 0.4f, wd = j <= 2 ? 1.2f : 0.8f;
                    int col = ((int) (al * 255f) << 24) | 0xFFFFFF;
                    pane.line(vx[a] * k, vy[a] * k, mx[a] * k, my[a] * k, wd * k, col, 1f);
                    pane.line(mx[a] * k, my[a] * k, vx[b] * k, vy[b] * k, wd * k, col, 1f);
                }
            // short side cracks branching off the rays
            for (int i = 0; i < RAYS * 4; i++) {
                int ray = r.nextInt(RAYS), j = 1 + r.nextInt(rings - 2);
                int a = ray * rings + j;
                float ang = (float) Math.atan2(vy[a] - py, vx[a] - px) + (r.nextBoolean() ? 1f : -1f) * (0.6f + r.nextFloat() * 0.6f);
                float len = RING[j] * ink.w / k * (0.08f + r.nextFloat() * 0.12f);
                float ex = vx[a] + (float) Math.cos(ang) * len, ey = vy[a] + (float) Math.sin(ang) * len;
                pane.line(vx[a] * k, vy[a] * k, ex * k, ey * k, 0.7f * k, 0x88FFFFFF, 1f);
            }
            // glints where cracks meet
            for (int i = 0; i < 22; i++) {
                int a = r.nextInt(RAYS) * rings + 2 + r.nextInt(2);
                float g = (2f + r.nextFloat() * 4f) * k, cx = vx[a] * k, cy = vy[a] * k;
                pane.line(cx - g, cy, cx + g, cy, 0.7f * k, 0xEEFFFFFF, 1f);
                pane.line(cx, cy - g, cx, cy + g, 0.7f * k, 0xEEFFFFFF, 1f);
                pane.disc(cx, cy, 1.1f * k, 0xFFFFFFFF, 1f, 1f);
            }
            paneDrawn = true;
        }
        ink.clear();
        float fade = 1f - Math.max(0f, Math.min(1f, (t - FADE0) / (FADE1 - FADE0)));
        if (fade > 0f) ink.blit(pane, 0f, 0f, fade);
        // the shards that come away: in the pane till they go, then flying at the eye
        for (int c = 0; c < cells; c++) {
            // the glass right where the head came through is ground to glitter: gone at once
            if (!flies[c] || c % (rings - 1) == 0) continue;
            corners(c);
            float u = t - delay[c];
            if (u < 0f) {
                shard(ink, 0f, 0f, 1f, 0f, 0.03f + 0.07f * lit[c], 0.9f, 1.5f, 1f, fade);
                continue;
            }
            if (u > FLY) continue;
            float cx = (sx[0] + sx[2] + sx[3] + sx[5]) * 0.25f, cy = (sy[0] + sy[2] + sy[3] + sy[5]) * 0.25f;
            float ox = cx - px, oy = cy - py, ol = (float) Math.sqrt(ox * ox + oy * oy) + 1e-3f;
            // out from the blow and growing as they come nearer, falling a little, turning
            float s = 1f + 1.6f * u + 2f * u * u;
            float out = speed[c] * (420f * u + 500f * u * u);
            float dx = ox / ol * out * s * 0.6f, dy = oy / ol * out * s * 0.6f + 140f * u * u;
            float cover = 1f - u / FLY;
            cover *= cover;
            shard(ink, dx, dy, s, spinRate[c] * u, (0.05f + 0.1f * lit[c]) * cover, 0.8f * cover, 1.6f, 1f + 4f * u, 1f);
        }
        // glitter from the hole
        for (int i = 0; i < BITS; i++) {
            if (t > FLY) break;
            float s = bs[i] * (1f + 3f * t), x = bx[i] + bvx[i] * t, y = by[i] + bvy[i] * t + 200f * t * t;
            float a = bspin[i] + t * 9f, c = (float) Math.cos(a) * s, sn = (float) Math.sin(a) * s;
            ink.shape((x + c) * k, (y + sn) * k, (x - sn * 0.6f) * k, (y + c * 0.6f) * k, (x - c) * k, (y - sn) * k);
            int al = (int) (230f * (1f - t / FLY));
            ink.polyFill((al << 24) | 0xFFFFFF, (al << 24) | 0xFFFFFF, 1f, 1f);
        }
        ink.fresh = true;
    }
}
