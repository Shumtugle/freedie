package com.shumtugle.freedie;

/**
 * A layer drawn finer than the frame, at the screen's own grain, with soft edges: shapes are
 * given as distance fields and filled with an edge one pixel wide, so a circle is round and a
 * star has points. Colours are kept premultiplied, laid one over another as glass over glass.
 * A squash or a turn can be set about a point; everything drawn while it is set is drawn
 * through it. Digits are drawn with strokes of a round pen, so a number needs no font.
 */
final class Ink {
    final int w, h;
    final int[] a;
    /** Whether anything was drawn since the last clear. */
    boolean used;

    // the squash in force: about (ox, oy), so much across and down
    private float ox, oy, sx = 1f, sy = 1f;

    Ink(int w, int h) {
        this.w = w;
        this.h = h;
        a = new int[w * h];
    }

    void clear() {
        java.util.Arrays.fill(a, 0);
        used = false;
        fresh = true;
        unsquash();
    }

    /** Whether it was drawn afresh since it was last handed on. */
    boolean fresh;

    /** Everything drawn from now squashed about (x, y): so much across, so much down (negative mirrors). */
    void squash(float x, float y, float across, float down) {
        ox = x;
        oy = y;
        sx = across;
        sy = down;
    }

    void unsquash() {
        sx = sy = 1f;
    }

    // ---- shapes as distance fields: below zero inside, in pixels of the shape's own frame ----

    abstract static class Field {
        /** Distance to the edge from (x, y), negative inside. */
        abstract float d(float x, float y);

        /** The colour at (x, y), as 0xAARRGGBB, not premultiplied. */
        int color(float x, float y) {
            return col;
        }

        int col;
    }

    /**
     * Fills a field over its box (x0, y0)-(x1, y1) in its own frame: inside full, the edge
     * fading over so many pixels (one for a crisp edge, more for a shadow), at so much cover.
     */
    void fill(Field f, float x0, float y0, float x1, float y1, float soft, float cover) {
        if (cover <= 0.003f) return;
        float m = soft + 1f;
        // the box on the layer, through the squash
        float ax = place(x0 - m, ox, sx), bx = place(x1 + m, ox, sx), ay = place(y0 - m, oy, sy), by = place(y1 + m, oy, sy);
        int px0 = Math.max(0, (int) Math.floor(Math.min(ax, bx))), px1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, bx)));
        int py0 = Math.max(0, (int) Math.floor(Math.min(ay, by))), py1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, by)));
        float scale = Math.max(1e-3f, Math.min(Math.abs(sx), Math.abs(sy)));
        for (int py = py0; py <= py1; py++) {
            float ly = oy + (py + 0.5f - oy) / sy;
            for (int px = px0; px <= px1; px++) {
                float lx = ox + (px + 0.5f - ox) / sx;
                float d = f.d(lx, ly) * scale;
                float k = 0.5f - d / soft;
                if (k <= 0f) continue;
                if (k > 1f) k = 1f;
                over(px, py, f.color(lx, ly), k * cover);
            }
        }
        used = true;
    }

    private static float place(float v, float o, float s) {
        return o + (v - o) * s;
    }

    /** One colour laid over a pixel at so much cover. */
    private void over(int x, int y, int c, float k) {
        int al = (int) ((c >>> 24) * k + 0.5f);
        if (al <= 0) return;
        if (al > 255) al = 255;
        int i = y * w + x;
        int d = a[i], keep = 255 - al;
        int ra = al + ((d >>> 24) * keep + 127) / 255;
        int rr = (((c >> 16) & 0xFF) * al + ((d >> 16) & 0xFF) * keep + 127) / 255;
        int rg = (((c >> 8) & 0xFF) * al + ((d >> 8) & 0xFF) * keep + 127) / 255;
        int rb = ((c & 0xFF) * al + (d & 0xFF) * keep + 127) / 255;
        a[i] = (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /**
     * Another layer laid over this one, its top left at (x, y), at so much cover: pixel for
     * pixel when it is not squashed; through the squash in force, pixel by nearest pixel, when it
     * is (a squash only lasts a moment, while it moves).
     */
    void blit(Ink src, float x, float y, float cover) {
        if (cover <= 0.003f) return;
        int k = Math.min(256, Math.round(cover * 256f));
        if (sx == 1f && sy == 1f) {
            int ix = Math.round(x), iy = Math.round(y);
            int x0 = Math.max(0, ix), y0 = Math.max(0, iy), x1 = Math.min(w, ix + src.w), y1 = Math.min(h, iy + src.h);
            for (int py = y0; py < y1; py++) {
                int si = (py - iy) * src.w - ix, di = py * w;
                if (k == 256)
                    for (int px = x0; px < x1; px++) {
                        int c = src.a[si + px];
                        // whole over whatever is there: laid straight down
                        if ((c >>> 24) == 255) a[di + px] = c;
                        else if (c != 0) put(di + px, c, k);
                    }
                else
                    for (int px = x0; px < x1; px++) {
                        int c = src.a[si + px];
                        if (c != 0) put(di + px, c, k);
                    }
            }
        } else {
            float ax = place(x, ox, sx), bx = place(x + src.w, ox, sx), ay = place(y, oy, sy), by = place(y + src.h, oy, sy);
            int px0 = Math.max(0, (int) Math.floor(Math.min(ax, bx))), px1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, bx)));
            int py0 = Math.max(0, (int) Math.floor(Math.min(ay, by))), py1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, by)));
            for (int py = py0; py <= py1; py++) {
                float ly = oy + (py + 0.5f - oy) / sy - y - 0.5f;
                for (int px = px0; px <= px1; px++) {
                    float lx = ox + (px + 0.5f - ox) / sx - x - 0.5f;
                    int c = src.at((int) Math.floor(lx + 0.5f), (int) Math.floor(ly + 0.5f));
                    if (c != 0) put(py * w + px, c, k);
                }
            }
        }
        used = true;
    }

    /** A premultiplied colour laid over a pixel, scaled by k out of 256. */
    private void put(int i, int c, int k) {
        int sa = (c >>> 24) * k >> 8;
        if (sa <= 0) return;
        int keep = 255 - sa, d = a[i];
        int ra = sa + ((d >>> 24) * keep + 127) / 255;
        int rr = (((c >> 16) & 0xFF) * k >> 8) + (((d >> 16) & 0xFF) * keep + 127) / 255;
        int rg = (((c >> 8) & 0xFF) * k >> 8) + (((d >> 8) & 0xFF) * keep + 127) / 255;
        int rb = ((c & 0xFF) * k >> 8) + ((d & 0xFF) * keep + 127) / 255;
        a[i] = (Math.min(255, ra) << 24) | (Math.min(255, rr) << 16) | (Math.min(255, rg) << 8) | Math.min(255, rb);
    }

    private int at(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h ? 0 : a[y * w + x];
    }

    // ---- the shapes ----

    private final Disc disc = new Disc();

    static final class Disc extends Field {
        float cx, cy, r;

        @Override
        float d(float x, float y) {
            return (float) Math.hypot(x - cx, y - cy) - r;
        }
    }

    void disc(float cx, float cy, float r, int col, float soft, float cover) {
        disc.cx = cx;
        disc.cy = cy;
        disc.r = r;
        disc.col = col;
        fill(disc, cx - r, cy - r, cx + r, cy + r, soft, cover);
    }

    private final Ring ring = new Ring();

    /** A band round a middle, from one share of a turn to another, a turn starting at the top going right. */
    static final class Ring extends Field {
        float cx, cy, r0, r1, t0, t1;

        @Override
        float d(float x, float y) {
            float dx = x - cx, dy = y - cy;
            float rr = (float) Math.hypot(dx, dy);
            float band = Math.max(r0 - rr, rr - r1);
            // all the way round, or well off the band: the angle does not matter
            if (t1 - t0 >= 0.999f || band > 2f) return band;
            float t = (float) ((Math.atan2(dx, -dy) / (Math.PI * 2) + 1.0) % 1.0);
            // how far outside the sector, along the circle at this radius
            float out = 0f;
            if (t < t0 || t > t1) {
                float to0 = Math.min(Math.abs(t - t0), 1f - Math.abs(t - t0)), to1 = Math.min(Math.abs(t - t1), 1f - Math.abs(t - t1));
                out = Math.min(to0, to1) * (float) Math.PI * 2f * rr;
            }
            return Math.max(band, out);
        }
    }

    void ring(float cx, float cy, float r0, float r1, float t0, float t1, int col, float cover) {
        ring.cx = cx;
        ring.cy = cy;
        ring.r0 = r0;
        ring.r1 = r1;
        ring.t0 = t0;
        ring.t1 = t1;
        ring.col = col;
        // only the box round the sector: its two ends, and every quarter of a turn it passes
        float bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float t = k == 0 ? t0 : k == 1 ? t1 : (k - 2) * 0.25f;
            if (k >= 2 && (t < t0 || t > t1)) continue;
            double an = t * Math.PI * 2;
            for (int e = 0; e < 2; e++) {
                float r = e == 0 ? r0 : r1;
                float x = cx + r * (float) Math.sin(an), y = cy - r * (float) Math.cos(an);
                bx0 = Math.min(bx0, x);
                by0 = Math.min(by0, y);
                bx1 = Math.max(bx1, x);
                by1 = Math.max(by1, y);
            }
        }
        if (t1 - t0 >= 0.999f) {
            bx0 = cx - r1;
            by0 = cy - r1;
            bx1 = cx + r1;
            by1 = cy + r1;
        }
        fill(ring, bx0, by0, bx1, by1, 1f, cover);
    }

    private final Box box = new Box();

    /** A box with round corners, its colour going from top to bottom. */
    static final class Box extends Field {
        float x0, y0, x1, y1, r;
        int top, bottom;

        @Override
        float d(float x, float y) {
            float cx = (x0 + x1) * 0.5f, cy = (y0 + y1) * 0.5f;
            float hx = (x1 - x0) * 0.5f - r, hy = (y1 - y0) * 0.5f - r;
            float qx = Math.abs(x - cx) - hx, qy = Math.abs(y - cy) - hy;
            float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
            return (float) Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0f) - r;
        }

        @Override
        int color(float x, float y) {
            if (top == bottom) return top;
            return Px.mix(top, bottom, Math.max(0f, Math.min(1f, (y - y0) / Math.max(1f, y1 - y0))));
        }
    }

    void box(float x0, float y0, float x1, float y1, float r, int top, int bottom, float soft, float cover) {
        box.x0 = x0;
        box.y0 = y0;
        box.x1 = x1;
        box.y1 = y1;
        box.r = Math.min(r, Math.min(x1 - x0, y1 - y0) * 0.5f);
        box.top = top;
        box.bottom = bottom;
        fill(box, x0, y0, x1, y1, soft, cover);
    }

    private final Poly poly = new Poly();

    /** A shape of straight sides, any shape, its colour going from top to bottom over its height. */
    static final class Poly extends Field {
        final float[] x = new float[32], y = new float[32];
        int n;
        float ytop, ybot;
        int top, bottom;

        @Override
        float d(float px, float py) {
            float best = Float.MAX_VALUE;
            boolean in = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float ax = x[j], ay = y[j], bx = x[i], by = y[i];
                if ((ay > py) != (by > py) && px < (bx - ax) * (py - ay) / (by - ay) + ax) in = !in;
                float ex = bx - ax, ey = by - ay, wx = px - ax, wy = py - ay;
                float t = Math.max(0f, Math.min(1f, (wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-6f)));
                float dx = wx - ex * t, dy = wy - ey * t;
                best = Math.min(best, dx * dx + dy * dy);
            }
            float d = (float) Math.sqrt(best);
            return in ? -d : d;
        }

        @Override
        int color(float px, float py) {
            if (top == bottom) return top;
            return Px.mix(top, bottom, Math.max(0f, Math.min(1f, (py - ytop) / Math.max(1f, ybot - ytop))));
        }

        void bounds(float[] out) {
            out[0] = out[1] = Float.MAX_VALUE;
            out[2] = out[3] = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                out[0] = Math.min(out[0], x[i]);
                out[1] = Math.min(out[1], y[i]);
                out[2] = Math.max(out[2], x[i]);
                out[3] = Math.max(out[3], y[i]);
            }
        }
    }

    private final float[] bb = new float[4];

    /** The shape set up in poly(): filled. */
    void polyFill(int top, int bottom, float soft, float cover) {
        poly.bounds(bb);
        poly.ytop = bb[1];
        poly.ybot = bb[3];
        poly.top = top;
        poly.bottom = bottom;
        fill(poly, bb[0], bb[1], bb[2], bb[3], soft, cover);
    }

    /** A star of five points round (cx, cy), the outer and inner radius, turned so much (0: a point up). */
    void starShape(float cx, float cy, float ro, float ri, float turn) {
        poly.n = 10;
        for (int k = 0; k < 10; k++) {
            float r = (k & 1) == 0 ? ro : ri;
            double an = -Math.PI / 2 + turn + k * Math.PI / 5;
            poly.x[k] = cx + r * (float) Math.cos(an);
            poly.y[k] = cy + r * (float) Math.sin(an);
        }
    }

    /** A shape by its corners, x and y in turn. */
    void shape(float... xy) {
        poly.n = xy.length / 2;
        for (int i = 0; i < poly.n; i++) {
            poly.x[i] = xy[i * 2];
            poly.y[i] = xy[i * 2 + 1];
        }
    }

    // ---- strokes of a round pen ----

    private final Pen pen = new Pen();

    /** Strokes: segments with round ends, so thick, all one shape. */
    static final class Pen extends Field {
        final float[] s = new float[4 * 96];
        int n;
        float half;

        void clear() {
            n = 0;
        }

        void seg(float ax, float ay, float bx, float by) {
            if (n >= 96) return;
            s[n * 4] = ax;
            s[n * 4 + 1] = ay;
            s[n * 4 + 2] = bx;
            s[n * 4 + 3] = by;
            n++;
        }

        @Override
        float d(float px, float py) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float ax = s[i * 4], ay = s[i * 4 + 1], ex = s[i * 4 + 2] - ax, ey = s[i * 4 + 3] - ay;
                float wx = px - ax, wy = py - ay;
                float t = Math.max(0f, Math.min(1f, (wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-6f)));
                float dx = wx - ex * t, dy = wy - ey * t;
                best = Math.min(best, dx * dx + dy * dy);
            }
            return (float) Math.sqrt(best) - half;
        }
    }

    /** A straight stroke. */
    void line(float ax, float ay, float bx, float by, float width, int col, float cover) {
        pen.clear();
        pen.seg(ax, ay, bx, by);
        pen.half = width * 0.5f;
        pen.col = col;
        float m = width;
        fill(pen, Math.min(ax, bx) - m, Math.min(ay, by) - m, Math.max(ax, bx) + m, Math.max(ay, by) + m, 1f, cover);
    }

    // the digits, drawn in a box 6 wide and 10 high: each a list of strokes, an arc given as
    // (a, cx, cy, rx, ry, from, to) in degrees clockwise from three o'clock, a line as (l, x0, y0, x1, y1)
    private static final float A = 0f, L = 1f;
    private static final float[][] GLYPH = {
            {A, 3, 5, 2.3f, 4, 0, 360},
            {L, 1.4f, 2.6f, 3.4f, 1, L, 3.4f, 1, 3.4f, 9},
            {A, 3, 3.3f, 2.2f, 2.3f, 190, 380, L, 4.95f, 4.4f, 1, 9, L, 1, 9, 5.3f, 9},
            {A, 3, 3, 2.1f, 2, 200, 450, A, 3, 6.8f, 2.3f, 2.2f, 270, 520},
            {L, 4.2f, 9, 4.2f, 1, L, 4.2f, 1, 0.8f, 6.6f, L, 0.8f, 6.6f, 5.6f, 6.6f},
            {L, 5, 1, 1.5f, 1, L, 1.5f, 1, 1.2f, 4.8f, A, 3, 6.5f, 2.3f, 2.4f, 210, 505},
            {A, 3, 6.6f, 2.2f, 2.4f, 0, 360, A, 4.6f, 6.4f, 3.8f, 5.4f, 182, 262},
            {L, 0.9f, 1, 5.3f, 1, L, 5.3f, 1, 2.2f, 9},
            {A, 3, 3, 1.9f, 2, 0, 360, A, 3, 6.9f, 2.3f, 2.1f, 0, 360},
            {A, 3, 3.4f, 2.2f, 2.4f, 0, 360, A, 1.4f, 3.6f, 3.8f, 5.4f, 2, 82},
    };
    // the times sign
    private static final float[] TIMES = {L, 1.2f, 3.6f, 4.8f, 8.4f, L, 4.8f, 3.6f, 1.2f, 8.4f};

    /** How wide a run of digits is, at this height. */
    static float digitsWidth(String s, float height) {
        float u = height / 10f;
        return s.length() * 6.6f * u - 0.6f * u;
    }

    /**
     * Digits (and 'x' for the times sign) with their left edge at x, their top at y, so high:
     * a dark edge all round first, then the ink.
     */
    void digits(String s, float x, float y, float height, int ink, int edge, float edgeWidth, float cover) {
        float u = height / 10f;
        for (int pass = 0; pass < 2; pass++) {
            pen.clear();
            for (int k = 0; k < s.length(); k++) {
                char ch = s.charAt(k);
                float[] g = ch == 'x' ? TIMES : ch >= '0' && ch <= '9' ? GLYPH[ch - '0'] : null;
                if (g == null) continue;
                strokes(g, x + k * 6.6f * u, y, u);
            }
            pen.half = (pass == 0 ? 0.95f * u + edgeWidth : 0.95f * u);
            pen.col = pass == 0 ? edge : ink;
            float m = pen.half + 2f;
            fill(pen, x - m, y - m, x + digitsWidth(s, height) + m, y + height + m, 1f, cover);
        }
    }

    private void strokes(float[] g, float x, float y, float u) {
        int i = 0;
        while (i < g.length) {
            if (g[i] == L) {
                pen.seg(x + g[i + 1] * u, y + g[i + 2] * u, x + g[i + 3] * u, y + g[i + 4] * u);
                i += 5;
            } else {
                float cx = g[i + 1], cy = g[i + 2], rx = g[i + 3], ry = g[i + 4], from = g[i + 5], to = g[i + 6];
                int steps = Math.max(4, (int) ((to - from) / 18f));
                float px = 0f, py = 0f;
                for (int k = 0; k <= steps; k++) {
                    double an = Math.toRadians(from + (to - from) * k / steps);
                    float qx = x + (cx + rx * (float) Math.cos(an)) * u, qy = y + (cy + ry * (float) Math.sin(an)) * u;
                    if (k > 0) pen.seg(px, py, qx, qy);
                    px = qx;
                    py = qy;
                }
                i += 7;
            }
        }
    }
}
