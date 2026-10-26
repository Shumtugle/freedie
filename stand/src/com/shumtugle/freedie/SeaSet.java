package com.shumtugle.freedie;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

/**
 * Lays out, once, on the desktop, the fifty buildings played out at sea, and writes them to
 * the asset the game reads (see Builder, the sea set).
 *
 * They are flat, one block deep, since they are seen from the ice side on and far off; of
 * wood, stone and old blue ice; big square blocks with a hole among them become life rings
 * and ship's wheels; the tops carry caps of snow. Each is grown from a seed the way the
 * street's are: storey by storey, posts under a beam, a ring between two posts, a wall of
 * planks, a stack, steps, or two towers side by side; whatever stands on something is put
 * over its support. Then each is tested: it must stand a second on its own, as it is and
 * mirrored. Then a player is let at it: a hammer thrown at what is left on the plinth, again
 * and again, until three quarters of its weight is off; the number of throws, over a few
 * tries, is how stubborn it is. The fifty are picked from easy to stubborn across all that
 * stood, the ladder going up in steps with a breather now and then; the first is an easy one
 * that is a sight, and the 25th and the 50th are the grandest of their part.
 *
 *   java -cp out/stand-classes com.shumtugle.freedie.SeaSet assets/sea.bin [candidates]
 */
public final class SeaSet {
    static final int WOOD = 0, STONE = 1, ICE = 2, SNOW = 3;
    static final float T = 0.22f;                    // a plank's thickness, meters
    static final float SQ = 0.45f;                   // a small square block
    static final float RING_W = 0.85f, RING_H = 0.9f;   // a big square block with a hole
    static final float HALF = Builder.PHX - 0.14f;   // how far across from the middle it may reach

    static final class Plan {
        final ArrayList<float[]> b = new ArrayList<float[]>();   // material, middle across, bottom, width, height
        long seed;
        float score, top;
        int rings;
    }

    private Random r;
    private Plan p;
    private float maxH;
    private int budget;
    private float wWood, wStone, wIce;

    // ---- growing ----

    Plan grow(long seed, float c) {
        r = new Random(seed);
        p = new Plan();
        p.seed = seed;
        // the palette: mostly wood, mostly ice, much stone, or a mix
        int pal = r.nextInt(4);
        wWood = pal == 0 ? 0.7f : pal == 1 ? 0.2f : pal == 2 ? 0.35f : 0.4f;
        wStone = pal == 0 ? 0.12f : pal == 1 ? 0.12f : pal == 2 ? 0.45f : 0.25f;
        wIce = 1f - wWood - wStone;
        // broad at the foot, so it reads at a distance across the water
        float hw = Math.min(HALF, 0.9f + (0.4f + 0.6f * c) * r.nextFloat() * 1.2f);
        maxH = 1.6f + c * 3.0f + r.nextFloat() * 0.6f;
        budget = 10 + (int) (c * 34f);
        grow(0f, hw, 0f, 0);
        snow();
        for (float[] k : p.b) p.top = Math.max(p.top, k[2] + k[4]);
        return p;
    }

    private int mat() {
        float u = r.nextFloat();
        return u < wWood ? WOOD : u < wWood + wStone ? STONE : ICE;
    }

    private void put(int m, float x, float y, float w, float h) {
        p.b.add(new float[]{m, x, y, w, h});
    }

    private void grow(float cx, float hw, float y, int storey) {
        if (y > maxH || p.b.size() >= budget || hw < 0.24f) {
            cap(cx, hw, y);
            return;
        }
        float room = maxH - y;
        // each way by its own chance, among those that fit here
        float wPosts = hw >= 0.34f ? 1.6f : 0f;
        float wRing = hw >= 0.7f && p.rings < 2 && room > 1.1f ? 0.9f : 0f;
        float wWall = hw >= 0.45f ? 0.8f : 0f;
        float wStack = storey == 0 ? 0.1f : 0.4f;
        float wSteps = storey == 0 && hw > 0.8f ? 0.5f : 0f;
        float wSplit = hw >= 1.0f && storey < 3 ? 0.9f : 0f;
        float sum = wPosts + wRing + wWall + wStack + wSteps + wSplit, u = r.nextFloat() * sum;
        if ((u -= wPosts) < 0f) posts(cx, hw, y, storey);
        else if ((u -= wRing) < 0f) ring(cx, hw, y, storey);
        else if ((u -= wWall) < 0f) wall(cx, hw, y, storey);
        else if ((u -= wStack) < 0f) stack(cx, hw, y);
        else if ((u -= wSteps) < 0f) steps(cx, hw, y);
        else split(cx, hw, y, storey);
    }

    /** Posts under a beam: two, or three under a wide one. */
    private void posts(float cx, float hw, float y, int storey) {
        float ph = maxH - y > 1.6f && r.nextBoolean() ? 1.35f : 0.9f;
        int n = hw > 1.1f ? 3 : 2;
        int m = mat();
        for (int i = 0; i < n; i++) {
            float x = cx - hw + T * 0.5f + i * (2f * hw - T) / (n - 1);
            put(r.nextFloat() < 0.8f ? m : mat(), x, y, T, ph);
        }
        put(mat(), cx, y + ph, 2f * hw, T);
        float nhw = hw * (0.6f + 0.4f * r.nextFloat());
        grow(cx, nhw, y + ph + T, storey + 1);
    }

    /** A big ring block between two posts, a beam over all three. */
    private void ring(float cx, float hw, float y, int storey) {
        p.rings++;
        put(WOOD, cx, y, RING_W, RING_H);
        float px = RING_W * 0.5f + 0.04f + T * 0.5f;
        int m = mat();
        put(m, cx - px, y, T, RING_H);
        put(m, cx + px, y, T, RING_H);
        float bw = (px + T * 0.5f) * 2f;
        put(mat(), cx, y + RING_H, bw, T);
        grow(cx, Math.min(hw, bw * 0.5f) * (0.6f + 0.4f * r.nextFloat()), y + RING_H + T, storey + 1);
    }

    /** Planks laid flat, two or three courses, each joint over the middle of a plank below. */
    private void wall(float cx, float hw, float y, int storey) {
        int rows = 2 + r.nextInt(2);
        float yy = y;
        for (int k = 0; k < rows; k++) {
            float x0 = cx - hw, x1 = cx + hw;
            float len = 0.9f, x = x0 + ((k & 1) == 1 ? len * 0.5f : 0f);
            int m = mat();
            if ((k & 1) == 1) put(m, x0 + len * 0.25f, yy, len * 0.5f - 0.01f, T);
            while (x + 0.2f < x1) {
                float w = Math.min(len, x1 - x);
                put(r.nextFloat() < 0.7f ? m : mat(), x + w * 0.5f, yy, w - 0.01f, T);
                x += len;
            }
            yy += T;
        }
        grow(cx, hw * (0.65f + 0.35f * r.nextFloat()), yy, storey + 1);
    }

    /** A narrow column of blocks, standing or lying. */
    private void stack(float cx, float hw, float y) {
        int n = 1 + r.nextInt(3);
        float yy = y;
        for (int k = 0; k < n && yy < maxH; k++) {
            boolean upright = r.nextFloat() < 0.4f;
            float w = upright ? T : Math.min(2f * hw, SQ), h = upright ? 0.9f : SQ;
            put(mat(), cx, yy, w, h);
            yy += h;
        }
        cap(cx, 0.2f, yy);
    }

    /** Steps of small squares, a row shorter each time up. */
    private void steps(float cx, float hw, float y) {
        int n = Math.min(6, (int) (2f * hw / SQ));
        float yy = y;
        int m = mat();
        for (int row = 0; n > 0 && yy < maxH; row++, n--) {
            float w0 = n * SQ;
            for (int i = 0; i < n; i++) put(r.nextFloat() < 0.75f ? m : mat(), cx - w0 * 0.5f + SQ * (i + 0.5f), yy, SQ - 0.01f, SQ);
            yy += SQ;
        }
    }

    /** Two towers side by side, often each the other's mirror. */
    private void split(float cx, float hw, float y, int storey) {
        float h2 = hw * 0.5f - 0.04f;
        int before = p.b.size();
        grow(cx - hw * 0.5f, h2, y, storey + 1);
        if (r.nextFloat() < 0.6f) {
            int after = p.b.size();
            for (int i = before; i < after; i++) {
                float[] k = p.b.get(i);
                put((int) k[0], 2f * cx - k[1], k[2], k[3], k[4]);
            }
        } else grow(cx + hw * 0.5f, h2, y, storey + 1);
    }

    /** A little something on top. */
    private void cap(float cx, float hw, float y) {
        if (y > maxH + 0.6f) return;
        float u = r.nextFloat();
        if (u < 0.35f && hw >= SQ * 0.5f) put(mat(), cx, y, SQ, SQ);
        else if (u < 0.6f && hw >= 0.45f) put(mat(), cx, y, Math.min(0.9f, 2f * hw), T);
        else if (u < 0.75f) put(mat(), cx, y, T, 0.9f);
    }

    /** Caps of snow on what nothing rests on. */
    private void snow() {
        int n = p.b.size();
        for (int i = 0; i < n; i++) {
            float[] a = p.b.get(i);
            if (a[3] < 0.2f || r.nextFloat() > 0.65f) continue;
            float top = a[2] + a[4];
            boolean covered = false;
            for (int j = 0; j < n && !covered; j++) {
                float[] o = p.b.get(j);
                if (j == i || Math.abs(o[2] - top) > 0.02f) continue;
                if (o[1] + o[3] * 0.5f > a[1] - a[3] * 0.5f && o[1] - o[3] * 0.5f < a[1] + a[3] * 0.5f) covered = true;
            }
            if (!covered) put(SNOW, a[1], top, a[3] - 0.04f, 0.07f);
        }
    }

    // ---- testing ----

    /** The plan as bodies at the first plinth spot, as the game would make them, mirrored or not. */
    static ArrayList<Body> bodies(Plan pl, boolean mirror) {
        ArrayList<Body> out = new ArrayList<Body>();
        for (float[] k : pl.b) {
            int m = (int) k[0];
            float x = (mirror ? -1f : 1f) * k[1], y = k[2], w = k[3], h = k[4];
            Body b = new Body(w * 0.5f, h * 0.5f, Builder.WINTER_DEPTH, m == STONE ? Body.STONE : Body.WOOD, 0);
            float py = Builder.TOP + y + h * 0.5f;
            b.setPose(x, py, Builder.BASE_CZ, 1, 0, 0, 0);
            b.homeX = x;
            b.homeY = py;
            b.homeZ = Builder.BASE_CZ;
            out.add(b);
        }
        return out;
    }

    /** How many throws it takes a fair player to get three quarters of its weight off, over a few tries. */
    static float stubborn(Plan pl, long seed) {
        float sum = 0f;
        int tries = 3;
        for (int t = 0; t < tries; t++) sum += throwsToClear(bodies(pl, (t & 1) == 1), new Random(seed + t * 31L));
        return sum / tries;
    }

    static int throwsToClear(ArrayList<Body> made, Random rnd) {
        Physics ph = new Physics();
        Body pl = new Body(Builder.PHX, Builder.TOP * 0.5f, Builder.PHZ1, Body.PLINTH, 0);
        pl.setPose(0f, Builder.TOP * 0.5f, Builder.BASE_CZ, 1, 0, 0, 0);
        ph.add(pl);
        float total = 0f;
        for (Body b : made) {
            b.asleep = true;
            ph.add(b);
            total += b.mass;
        }
        Body hammer = new Body(0.42f, 0.08f, 0.17f, Body.HAMMER, 0);
        hammer.ghost = true;
        hammer.setPose(0f, -50f, 0f, 1, 0, 0, 0);
        ph.add(hammer);
        for (int n = 1; n <= 12; n++) {
            // at the middle of what is still on the plinth, by weight, a hand's breadth off either way
            float sx = 0f, sy = 0f, m = 0f;
            for (Body b : made)
                if (on(b)) {
                    sx += b.px * b.mass;
                    sy += b.py * b.mass;
                    m += b.mass;
                }
            if (m <= 0f) return n - 1;
            float tx = sx / m + (rnd.nextFloat() - 0.5f) * 0.6f, ty = sy / m + (rnd.nextFloat() - 0.5f) * 0.5f;
            // from a little way off, as fast as a good throw arrives, falling a little
            float back = 2.4f, speed = 11.5f + rnd.nextFloat() * 2f, t = back / speed;
            hammer.ghost = false;
            hammer.asleep = false;
            hammer.setPose(tx * 0.5f, ty + 0.15f, Builder.BASE_CZ - back, 1, 0, 0, 0);
            hammer.vx = tx * 0.5f / t;
            hammer.vy = (ty - hammer.py) / t - 0.5f * Physics.G * t;
            hammer.vz = speed;
            hammer.wx = 0.6f;
            hammer.wy = 15f;
            hammer.wz = 0f;
            hammer.savePrev();
            for (int s = 0; s < 150; s++) {
                ph.step(1f / 60f);
                if (s == 40) {
                    hammer.ghost = true;
                    hammer.vx = hammer.vy = hammer.vz = hammer.wx = hammer.wy = hammer.wz = 0f;
                    hammer.setPose(0f, -50f, 0f, 1, 0, 0, 0);
                }
            }
            float left = 0f;
            for (Body b : made) if (on(b)) left += b.mass;
            if (1f - left / total >= Game.CLEAR) return n;
        }
        return 13;
    }

    private static boolean on(Body b) {
        return Math.abs(b.px) < Builder.PHX + 0.05f && Math.abs(b.pz - Builder.BASE_CZ) < Builder.PHZ1 + 0.05f
                && b.py > Builder.TOP - 0.05f && b.py < Builder.TOP + 8f;
    }

    // ---- choosing ----

    public static void main(String[] a) throws IOException {
        String out = a.length > 0 ? a[0] : "assets/sea.bin";
        int candidates = a.length > 1 ? Integer.parseInt(a[1]) : 900;
        SeaSet g = new SeaSet();
        ArrayList<Plan> ok = new ArrayList<Plan>();
        Random pick = new Random(0x5EA5E7L);
        for (int i = 0; i < candidates; i++) {
            long seed = 0x5EA00000L + i * 7919L;
            Plan pl = g.grow(seed, (i % 100) / 99f);
            // a lone post is not a building: it must have some body and some breadth
            if (pl.b.size() < 6 || span(pl) < 1.4f) continue;
            if (!Game.standsStill(bodies(pl, false)) || !Game.standsStill(bodies(pl, true))) continue;
            pl.score = stubborn(pl, seed) + pl.b.size() * 0.01f;
            // one that a fair player cannot get down in eight throws is not stubborn, it is unfair
            if (pl.score > 8f) continue;
            ok.add(pl);
        }
        Collections.sort(ok, new Comparator<Plan>() {
            public int compare(Plan x, Plan y) {
                return Float.compare(x.score, y.score);
            }
        });
        System.out.println(ok.size() + " of " + candidates + " stood; stubbornness " + ok.get(0).score + " to " + ok.get(ok.size() - 1).score);
        // fifty across the range, easy to stubborn
        ArrayList<Plan> set = new ArrayList<Plan>();
        for (int i = 0; i < 50; i++) set.add(ok.get(Math.round(i * (ok.size() - 1) / 49f)));
        // the first is the ice's welcome: easy, but a sight, as the first in town was
        Plan first = null;
        for (Plan pl : ok)
            if (pl.score <= 1.3f && !set.contains(pl) && (first == null || pl.rings * 1.5f + pl.top > first.rings * 1.5f + first.top)) first = pl;
        if (first != null) set.set(0, first);
        // the 25th and the 50th: the grandest of their stretch, ring blocks and height first
        set.set(24, grandest(ok, set, ok.size() * 40 / 100, ok.size() * 60 / 100));
        set.set(49, grandest(ok, set, ok.size() * 85 / 100, ok.size()));
        // a breather every fifth: an easier one from a little earlier
        for (int i = 4; i < 49; i += 5) {
            if (i == 24) continue;
            Plan x = set.get(i);
            set.set(i, set.get(i - 3));
            set.set(i - 3, x);
        }
        DataOutputStream o = new DataOutputStream(new FileOutputStream(out));
        o.writeShort(set.size());
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < set.size(); i++) {
            Plan pl = set.get(i);
            o.writeShort(i);
            o.writeByte(pl.b.size());
            for (float[] k : pl.b) {
                o.writeByte((int) k[0]);
                o.writeShort(Math.round(k[1] * 1000f));
                o.writeShort(Math.round(k[2] * 1000f));
                o.writeShort(Math.round(k[3] * 1000f));
                o.writeShort(Math.round(k[4] * 1000f));
            }
            log.append(String.format("%d:%.1f/%d/%.1fm%s ", i + 1, pl.score, pl.b.size(), pl.top, pl.rings > 0 ? "o" + pl.rings : ""));
        }
        o.close();
        System.out.println(log);
    }

    private static float span(Plan pl) {
        float x0 = 1e9f, x1 = -1e9f;
        for (float[] k : pl.b) {
            x0 = Math.min(x0, k[1] - k[3] * 0.5f);
            x1 = Math.max(x1, k[1] + k[3] * 0.5f);
        }
        return x1 - x0;
    }

    private static Plan grandest(ArrayList<Plan> ok, ArrayList<Plan> taken, int from, int to) {
        Plan best = null;
        float bestV = -1f;
        for (int i = from; i < to; i++) {
            Plan pl = ok.get(i);
            if (taken.contains(pl)) continue;
            float v = pl.rings * 1.5f + pl.top + pl.b.size() * 0.05f;
            if (v > bestV) {
                bestV = v;
                best = pl;
            }
        }
        return best != null ? best : ok.get(Math.max(0, to - 1));
    }
}
