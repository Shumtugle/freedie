package com.shumtugle.freedie;

import java.util.HashMap;
import java.util.Random;

/**
 * The town, without end: a grid of streets, and between them blocks of old seaside houses, now
 * and then a square with palms. It is grown from a seed one cell at a time, only where the
 * runner is: a cell is built when it comes near and forgotten when it is left far behind, and
 * built again the same when it is come back to.
 *
 * A cell is CELL meters square. Its streets run along its west edge (x = i * CELL, north and
 * south) and its south edge (z = j * CELL, east and west), crossing at its south-west corner;
 * the rest of it is the block those streets go round. Space as in DrawList: x to the right,
 * y up, the road at y = 0.
 */
final class City {
    static final float PARKED = 3.3f;            // a parked car's middle, from the road's middle
    static final float CELL = 64f;
    // across a street from its middle: the asphalt, the curb, the pavement up to the house fronts
    static final float ROAD = 4.05f, CURB = 4.3f, FRONT = 7f;
    static final float WALK_TOP = 0.14f, CURB_TOP = 0.15f;
    // how deep the houses are, from the front back into the block
    static final float DEPTH = 12f;
    // the cells drawn round the eye, each way
    static final int REACH = 3;

    private final long seed;
    private final HashMap<Long, Cell> cells = new HashMap<Long, Cell>();

    City(long seed) {
        this.seed = seed;
    }

    /** One cell, as built: its boxes, and what cannot be walked through, as boxes on the ground. */
    private static final class Cell {
        final DrawList boxes = new DrawList();
        // the small things (palms, lamp posts, chimneys): drawn only near
        final DrawList detail = new DrawList();
        // the parked cars: nearer still
        final DrawList parked = new DrawList();
        // x0, z0, x1, z1 for each
        float[] walls = new float[64];
        int wallN;
        int used;

        void wall(float x0, float z0, float x1, float z1) {
            if ((wallN + 1) * 4 > walls.length) walls = java.util.Arrays.copyOf(walls, walls.length * 2);
            walls[wallN * 4] = Math.min(x0, x1);
            walls[wallN * 4 + 1] = Math.min(z0, z1);
            walls[wallN * 4 + 2] = Math.max(x0, x1);
            walls[wallN * 4 + 3] = Math.max(z0, z1);
            wallN++;
        }
    }

    private int frame;

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private Cell cell(int i, int j) {
        long k = key(i, j);
        Cell c = cells.get(k);
        if (c == null) {
            c = build(i, j);
            cells.put(k, c);
        }
        c.used = frame;
        return c;
    }

    static int index(float v) {
        return (int) Math.floor(v / CELL);
    }

    /**
     * The cells round (x, z) that the eye looking along (fx, fz) can see, into the list, their
     * small things only near; the ones long out of reach forgotten.
     */
    void draw(DrawList d, float x, float z, float fx, float fz) {
        frame++;
        int ci = index(x), cj = index(z);
        for (int j = cj - REACH; j <= cj + REACH; j++)
            for (int i = ci - REACH; i <= ci + REACH; i++) {
                // the cell's middle from the eye, and how far it reaches round it
                float dx = (i + 0.5f) * CELL - x, dz = (j + 0.5f) * CELL - z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz), reach = CELL * 0.75f;
                if (dist > reach && (dx * fx + dz * fz) < -reach) continue;
                if (dist > (REACH + 0.5f) * CELL) continue;
                Cell c = cell(i, j);
                d.append(c.boxes);
                if (dist < CELL * 1.6f) d.append(c.detail);
                if (dist < CELL * 1.2f) d.append(c.parked);
            }
        if (cells.size() > (2 * REACH + 3) * (2 * REACH + 3)) {
            java.util.Iterator<Cell> it = cells.values().iterator();
            while (it.hasNext()) if (frame - it.next().used > 120) it.remove();
        }
    }

    /** How high the ground is here: the road, or the pavement a step up. */
    float ground(float x, float z) {
        float ox = x - Math.round(x / CELL) * CELL, oz = z - Math.round(z / CELL) * CELL;
        return Math.abs(ox) < CURB || Math.abs(oz) < CURB ? 0f : WALK_TOP;
    }

    /**
     * Pushes a round body of radius r standing at (p[0], p[1]) out of whatever it went into;
     * true if it touched something.
     */
    boolean push(float[] p, float r) {
        boolean hit = false;
        int ci = index(p[0]), cj = index(p[1]);
        for (int j = cj - 1; j <= cj + 1; j++)
            for (int i = ci - 1; i <= ci + 1; i++) {
                Cell c = cell(i, j);
                for (int k = 0; k < c.wallN; k++) {
                    float x0 = c.walls[k * 4], z0 = c.walls[k * 4 + 1], x1 = c.walls[k * 4 + 2], z1 = c.walls[k * 4 + 3];
                    float nx = Math.max(x0, Math.min(p[0], x1)), nz = Math.max(z0, Math.min(p[1], z1));
                    float dx = p[0] - nx, dz = p[1] - nz, dd = dx * dx + dz * dz;
                    if (dd >= r * r) continue;
                    hit = true;
                    if (dd > 1e-8f) {
                        float l = (float) Math.sqrt(dd);
                        p[0] = nx + dx / l * r;
                        p[1] = nz + dz / l * r;
                    } else {
                        // inside: out by the nearest side
                        float l = p[0] - x0, rr = x1 - p[0], b = p[1] - z0, t = z1 - p[1];
                        float m = Math.min(Math.min(l, rr), Math.min(b, t));
                        if (m == l) p[0] = x0 - r;
                        else if (m == rr) p[0] = x1 + r;
                        else if (m == b) p[1] = z0 - r;
                        else p[1] = z1 + r;
                    }
                }
            }
        return hit;
    }

    // ---- growing a cell ----

    private long hash(int i, int j, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL + j * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    /** What a cell's block is: houses, mostly; now and then a square. The one the runner starts by is houses. */
    boolean square(int i, int j) {
        if (i == 0 && j == 0 || i == -1 && j == 0) return false;
        return (hash(i, j, 1) & 0xFF) < 30;
    }

    /** Whether the street along the cell's west edge has palms down its pavements. */
    private boolean palmy(int i, int j) {
        return (hash(i, j, 2) & 3) == 0;
    }

    private Cell build(int i, int j) {
        Cell c = new Cell();
        DrawList d = c.boxes;
        Random rnd = new Random(hash(i, j, 0));
        float x0 = i * CELL, z0 = j * CELL, half = CELL * 0.5f;
        // the crossing, then the two streets out of it, north and east
        d.box(x0, -0.25f, z0, ROAD, 0.25f, ROAD, null, 0x5E4844, DrawList.ASPHALT);
        d.box(x0, -0.25f, z0 + half, ROAD, 0.25f, half - ROAD, null, 0x5E4844, DrawList.ROAD);
        d.box(x0 + half, -0.25f, z0, half - ROAD, 0.25f, ROAD, null, 0x5E4844, DrawList.ROAD);
        // the block: curbs round it, the pavement within
        float b0x = x0 + ROAD, b1x = x0 + CELL - ROAD, b0z = z0 + ROAD, b1z = z0 + CELL - ROAD;
        float cw = CURB - ROAD, cy = (CURB_TOP - 0.25f) * 0.5f, ch = (CURB_TOP + 0.25f) * 0.5f;
        int curb = 0xC9BFAE;
        d.box((b0x + b1x) * 0.5f, cy, b0z + cw * 0.5f, (b1x - b0x) * 0.5f, ch, cw * 0.5f, null, curb, DrawList.STONE);
        d.box((b0x + b1x) * 0.5f, cy, b1z - cw * 0.5f, (b1x - b0x) * 0.5f, ch, cw * 0.5f, null, curb, DrawList.STONE);
        d.box(b0x + cw * 0.5f, cy, (b0z + b1z) * 0.5f, cw * 0.5f, ch, (b1z - b0z) * 0.5f - cw, null, curb, DrawList.STONE);
        d.box(b1x - cw * 0.5f, cy, (b0z + b1z) * 0.5f, cw * 0.5f, ch, (b1z - b0z) * 0.5f - cw, null, curb, DrawList.STONE);
        boolean sq = square(i, j);
        float p0x = b0x + cw, p1x = b1x - cw, p0z = b0z + cw, p1z = b1z - cw;
        d.box((p0x + p1x) * 0.5f, (WALK_TOP - 0.25f) * 0.5f, (p0z + p1z) * 0.5f, (p1x - p0x) * 0.5f, (WALK_TOP + 0.25f) * 0.5f,
                (p1z - p0z) * 0.5f, null, sq ? 0xDCCDB2 : 0xCDBFA6, sq ? DrawList.PROMENADE : DrawList.PAVING);
        if (sq) plaza(c, rnd, x0, z0);
        else block(c, rnd, x0, z0, i, j);
        lamps(c, x0, z0);
        parked(c, rnd, x0, z0);
        if (palmy(i, j)) {
            // down the street along the west edge, on both its pavements
            for (float z = z0 + 10f; z < z0 + CELL - 6f; z += 12f)
                for (int s = -1; s <= 1; s += 2) {
                    float px = x0 + s * (CURB + 1.1f);
                    Street.palm(c.detail, rnd, px, WALK_TOP, z, 6.5f + rnd.nextFloat() * 2f, (rnd.nextFloat() - 0.5f) * 0.3f, false);
                    c.wall(px - 0.25f, z - 0.25f, px + 0.25f, z + 0.25f);
                }
        }
        return c;
    }

    /** A block of houses round its four sides, their fronts on the streets, a yard hidden in the middle. */
    private void block(Cell c, Random rnd, float x0, float z0, int i, int j) {
        float a = FRONT, b = CELL - FRONT;
        // the south and north rows run the whole way; the west and east rows fit between them
        row(c, rnd, x0 + a, x0 + b, z0 + a, z0 + a + DEPTH, true, -1);
        row(c, rnd, x0 + a, x0 + b, z0 + b - DEPTH, z0 + b, true, 1);
        row(c, rnd, z0 + a + DEPTH, z0 + b - DEPTH, x0 + a, x0 + a + DEPTH, false, -1);
        row(c, rnd, z0 + a + DEPTH, z0 + b - DEPTH, x0 + b - DEPTH, x0 + b, false, 1);
    }

    /**
     * A row of houses from u0 to u1 along x (alongX) or z, from v0 to v1 the other way; its
     * fronts face toward smaller v (side -1) or larger (side 1).
     */
    private void row(Cell c, Random rnd, float u0, float u1, float v0, float v1, boolean alongX, int side) {
        DrawList d = c.boxes;
        float u = u0;
        while (u < u1 - 0.5f) {
            int bays = 3 + rnd.nextInt(3);
            float len = bays * 2.9f;
            if (u1 - (u + len) < 6f) len = u1 - u;
            int storeys = 3 + rnd.nextInt(4);
            float height = 3.3f * (storeys + 1) + 0.5f;
            int tint = Street.WALLS[rnd.nextInt(Street.WALLS.length)];
            int seed = rnd.nextInt(1 << 16);
            float cu = u + len * 0.5f, cv = (v0 + v1) * 0.5f, hu = len * 0.5f, hv = (v1 - v0) * 0.5f;
            float cx = alongX ? cu : cv, cz = alongX ? cv : cu, hx = alongX ? hu : hv, hz = alongX ? hv : hu;
            d.box(cx, height * 0.5f, cz, hx, height * 0.5f, hz, null, tint, DrawList.material(DrawList.FACADE, seed));
            // the cornice along the front
            float fv = side < 0 ? v0 : v1;
            float kx = alongX ? cu : fv + side * 0.15f, kz = alongX ? fv + side * 0.15f : cu;
            d.box(kx, height - 0.2f, kz, alongX ? hu : 0.25f, 0.2f, alongX ? 0.25f : hu, null, Street.scale(tint, 1.05f), DrawList.PLAIN);
            roof(d, c.detail, cx, height, cz, hx, hz, seed);
            c.wall(cx - hx, cz - hz, cx + hx, cz + hz);
            u += len;
        }
    }

    /** A roof of red tiles in two steps; its chimneys among the small things. */
    private static void roof(DrawList d, DrawList small, float x, float top, float z, float hx, float hz, int seed) {
        d.box(x, top + 0.25f, z, hx - 0.3f, 0.25f, hz - 0.3f, null, 0xC4673E, DrawList.GRAIN);
        d.box(x, top + 0.6f, z, hx - 1.3f, 0.15f, Math.max(0.4f, hz - 1.3f), null, 0xA8553A, DrawList.GRAIN);
        int h = seed * 0x2545F491;
        for (int c = 0; c <= ((h >>> 7) & 1); c++) {
            float cx = x + ((((h >>> (9 + c * 5)) & 31) / 31f) - 0.5f) * hx;
            float cz = z + ((((h >>> (19 + c * 5)) & 31) / 31f) - 0.5f) * hz;
            small.box(cx, top + 1.1f, cz, 0.3f, 0.6f, 0.3f, null, 0xBE9C84, DrawList.STONE);
        }
    }

    /** A square: big pale paving, palms in rows, a stone basin in the middle. */
    private void plaza(Cell c, Random rnd, float x0, float z0) {
        DrawList d = c.boxes;
        float mx = x0 + CELL * 0.5f, mz = z0 + CELL * 0.5f;
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) < 2 && Math.abs(b) < 2) continue;
                float px = mx + a * 10f, pz = mz + b * 10f;
                Street.palm(c.detail, rnd, px, WALK_TOP, pz, 6f + rnd.nextFloat() * 2.5f, (rnd.nextFloat() - 0.5f) * 0.35f, false);
                c.wall(px - 0.25f, pz - 0.25f, px + 0.25f, pz + 0.25f);
            }
        // the basin and its water
        d.box(mx, WALK_TOP + 0.3f, mz, 3.2f, 0.3f, 3.2f, null, 0xE2D4BA, DrawList.STONE);
        d.box(mx, WALK_TOP + 0.55f, mz, 2.8f, 0.06f, 2.8f, null, 0x2D6990, DrawList.SEA);
        d.box(mx, WALK_TOP + 1.1f, mz, 0.35f, 0.6f, 0.35f, null, 0xE2D4BA, DrawList.STONE);
        c.wall(mx - 3.2f, mz - 3.2f, mx + 3.2f, mz + 3.2f);
    }

    /**
     * Cars left along the curbs of the two streets the cell owns, each the way its side of the
     * road goes, now and then a gap; none near the crossings.
     */
    private void parked(Cell c, Random rnd, float x0, float z0) {
        float off = PARKED;
        for (float t = 13f; t < CELL - 13f; t += 5.6f)
            for (int s = -1; s <= 1; s += 2) {
                if (rnd.nextInt(10) < 7) continue;
                int paint = Cars.PAINT[rnd.nextInt(Cars.PAINT.length)];
                // the street running north: on its east side they face north, on its west side south
                if (rnd.nextBoolean()) {
                    float x = x0 + s * off, z = z0 + t;
                    Cars.car(c.parked, x, z, s > 0 ? 0f : (float) Math.PI, paint, true);
                    c.wall(x - 0.95f, z - 2.3f, x + 0.95f, z + 2.3f);
                } else {
                    // the street running east: on its south side they face east, on its north side west
                    float x = x0 + t, z = z0 + s * off;
                    Cars.car(c.parked, x, z, s < 0 ? (float) (Math.PI / 2) : (float) (-Math.PI / 2), paint, true);
                    c.wall(x - 2.3f, z - 0.95f, x + 2.3f, z + 0.95f);
                }
            }
    }

    /** Lamp posts along the curbs of the block, every so often. */
    private void lamps(Cell c, float x0, float z0) {
        DrawList d = c.detail;
        float in = CURB + 0.5f;
        for (float t = 14f; t < CELL - 8f; t += 18f) {
            lamp(c, d, x0 + in, z0 + t);
            lamp(c, d, x0 + t, z0 + in);
        }
    }

    private static void lamp(Cell c, DrawList d, float x, float z) {
        int iron = 0x2B2F3A;
        d.box(x, WALK_TOP + 2.2f, z, 0.07f, 2.2f, 0.07f, null, iron, DrawList.PLAIN);
        d.box(x, WALK_TOP + 0.25f, z, 0.14f, 0.25f, 0.14f, null, iron, DrawList.PLAIN);
        d.box(x, WALK_TOP + 4.4f, z, 0.135f, 0.185f, 0.135f, null, 0xF3E2B4, DrawList.GLOW);
        d.box(x, WALK_TOP + 4.62f, z, 0.2f, 0.04f, 0.2f, null, iron, DrawList.PLAIN);
        c.wall(x - 0.12f, z - 0.12f, x + 0.12f, z + 0.12f);
    }
}
