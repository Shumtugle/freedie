package com.shumtugle.freedie;

import java.util.Random;

/**
 * A hunt: golems that should not be are loose in the town, each waiting somewhere on a
 * pavement of its own, far apart, round where the runner starts. Come near one and it runs;
 * caught, it is done with for good. The clock runs until the last of them is caught.
 */
final class Mission {
    static final float STEP = 55f, SPREAD = 30f;

    final Chase[] golems;
    final int count;
    private final float nearest;
    float clock, doneAt = -1f;
    int round;
    private final Random rnd;

    /** So many golems, the first so far off, the rest further and further. */
    Mission(long seed, int count, float nearest) {
        rnd = new Random(seed);
        this.count = count;
        this.nearest = nearest;
        golems = new Chase[count];
    }

    /** Let loose round (x, z): one a way round the compass each, further and further off. */
    void start(float x, float z) {
        round++;
        clock = 0f;
        doneAt = -1f;
        float turn = rnd.nextFloat() * 6.2832f;
        for (int k = 0; k < count; k++) {
            float a = turn + (k + 0.3f + rnd.nextFloat() * 0.4f) * 6.2832f / count;
            float d = nearest + k * STEP + rnd.nextFloat() * SPREAD;
            int ci = Math.round((x + (float) Math.sin(a) * d) / City.CELL);
            int cj = Math.round((z + (float) Math.cos(a) * d) / City.CELL);
            golems[k] = new Chase(rnd.nextLong(), ci, cj, rnd.nextInt(4));
        }
    }

    void update(float dt, Runner r, City city, Cars cars) {
        if (doneAt < 0f) clock += dt;
        for (Chase g : golems) g.update(dt, r, city, cars);
        if (doneAt < 0f && caught() == count) doneAt = clock;
    }

    int caught() {
        int n = 0;
        for (Chase g : golems) if (g.done()) n++;
        return n;
    }

    boolean over() {
        return doneAt >= 0f;
    }

    /** The nearest of them still loose; the last caught when none is. */
    Chase target(Chase last) {
        Chase best = null;
        for (Chase g : golems) if (!g.done() && (best == null || g.apart < best.apart)) best = g;
        return best != null ? best : last != null ? last : golems[0];
    }
}
