package com.shumtugle.freedie;

/**
 * One frame, as the world wants it drawn: the eye, the sun, the sky, every box in the scene,
 * and the flat layer laid over it all. The world fills it; a renderer draws it. Nothing
 * here knows how drawing is done, so the phone and the desktop stand draw the same list
 * with the same shaders.
 *
 * Space: x to the right, y up, z away from the eye at rest; meters.
 */
final class DrawList {
    // what a box's faces are made of; the shaders turn these into patterns
    static final int PLAIN = 0, GRAIN = 1, STONE = 2, ASPHALT = 3, WOOD = 4, PAVING = 5, PAINT = 6, GOLD = 7;
    static final int FACADE = 8, FROND = 9, TRUNK = 10, ROAD = 11, GLASS = 12, GLOW = 13, SEA = 14, RAIL = 15,
            PROMENADE = 16, AWNING = 17, SQUARE = 18, LANE = 19;
    // out at sea: a block of old ice; the grey winter sea
    static final int ICE = 22, WINTER_SEA = 23;
    // a number a material that reads none of its own may carry: it burns in the golden fire
    // when the world is seen red (see the frame's grade)
    static final int FIRE = 255;
    // and another: it keeps its own colours however the world is seen (the diamond hammer)
    static final int KEEP = 254;
    // and a third: it goes into the dark with the world, the pool of light left there never
    // lights it (the people: seen close on the black, they are too plainly a few boxes)
    static final int CROWD = 253;
    // and a fourth: only the mirror up in the sky shows it (whoever stands where the eye is)
    static final int MIRRORED = 251;
    /** While true, every box added is marked MIRRORED, unless it carries a number of its own. */
    boolean mirrored;
    // what a ball is: a polished one with a marbled swirl and finger holes, or a rubber one;
    // or the mirror, a ball of steel that shows the world round it
    static final int POLISHED = 20, RUBBER = 21, MIRROR = 24;
    // gold that burns from inside: the golden cube, the golden hammer's head
    static final int FIRE_GOLD = 25;
    // per box: middle (3), half sizes (3), the turn as three columns (9), color (3), material (1)
    static final int STRIDE = 19;

    int w, h;                                  // the frame, in game pixels
    final float[] viewProj = new float[16];    // world to clip, column after column
    final float[] lightProj = new float[16];   // world to the sun's depth map
    final float[] eye = new float[3], right = new float[3], up = new float[3], ahead = new float[3];
    float focal;                               // pixels from the eye to the frame
    final float[] sun = new float[3];          // toward the sun, unit length
    final float[] sunColor = {1f, 1f, 1f};
    final float[] skyTop = new float[3], skyHorizon = new float[3], skyGlow = new float[3];
    final float[] ambientSky = new float[3], ambientGround = new float[3];
    final float[] fog = new float[3];
    float fogStart = 8f, fogScale = 150f;      // the haze begins so far off, and thickens over so many meters
    float time;                                // seconds of game time, for what drifts in the sky
    // the dark: how far the world has gone into it, and the one pool of light left in it
    float dark;
    final float[] spot = new float[3];
    float spotRadius = 4f;
    float goldGlow = 1f;                       // gold keeps this much of its own light in the dark
    float flash;                               // the frame drawn in to its middle and gone white, 0 to 1
    float tube = -1f;                          // the picture coming on like a set, 0 to 1; below 0 it is on
    // the fight's world: how far it is seen red and black (what burns, in golden fire), and
    // how far gold as it ends
    float red, gold;
    float blur;                                // the frame out of focus by so many of its pixels, waking
    float overcast;                            // out at sea: the low grey winter sky, 0 to 1
    float night;                               // on the street: lamps and windows lit, stars out, 0 to 1
    final float[] grade = {1f, 1f, 0f};        // the hour's grade: contrast, saturation, warmth
    // the mirror up in the sky: its middle and radius (radius 0: there is none). The world
    // round it is drawn once more from its middle, all six ways, for it to show
    final float[] mirror = new float[4];
    // its wreath of storm cloud: how far out (in mirror widths, 0: none), how black, lit within
    final float[] halo = new float[3];
    float rippleAge, ripple;                   // a ring over the mirror: seconds since, how strong
    float bolt;                                // lightning just now, 0 to 1

    float[] boxes = new float[STRIDE * 256];
    int count;
    // true spheres, laid out as boxes (the cube round each), drawn apart: their shader works
    // out where the eye's ray meets the sphere and how deep that is, and the boxes' does not
    float[] balls = new float[STRIDE * 16];
    int ballCount;

    int[] flat;                                // the flat layer, ARGB, w * h; clear where the scene shows

    // what flies far off in the sky, as strokes the sky draws with itself: per stroke its ends
    // in frame pixels (x0, y0, x1, y1), then its width and cover at each end; a dark one (a bird)
    // has its first width negative, the rest are white (a plane, its trail)
    static final int FLY_MAX = 32;
    final float[] fly = new float[FLY_MAX * 8];
    int flyN;

    void fly(float x0, float y0, float x1, float y1, float w0, float w1, float a0, float a1, boolean dark) {
        if (flyN >= FLY_MAX) return;
        int o = flyN++ * 8;
        fly[o] = x0; fly[o + 1] = y0; fly[o + 2] = x1; fly[o + 3] = y1;
        fly[o + 4] = dark ? -w0 : w0; fly[o + 5] = w1; fly[o + 6] = a0; fly[o + 7] = a1;
    }

    void begin(int w, int h) {
        if (flat == null || flat.length != w * h) flat = new int[w * h];
        this.w = w;
        this.h = h;
        count = 0;
        ballCount = 0;
        flyN = 0;
        mirror[3] = 0f;
        halo[0] = halo[1] = halo[2] = 0f;
        ripple = rippleAge = bolt = 0f;
    }

    /**
     * A ball: its middle, its radius, its turn (a 3x3, row by row; the swirl and the holes
     * turn with it), color as 0xRRGGBB, and what it is: POLISHED or RUBBER.
     */
    void ball(float x, float y, float z, float r, float[] rot, int rgb, int kind) {
        float[] keep = boxes;
        int kept = count;
        boxes = balls;
        count = ballCount;
        box(x, y, z, r, r, r, rot, rgb, kind);
        balls = boxes;
        ballCount = count;
        boxes = keep;
        count = kept;
    }

    /** A material with a number of its own, 0 to 255, that the shader reads as a seed or a flag. */
    static float material(int material, int seed) {
        return material + (seed & 0xFF) / 256f;
    }

    /** Every box of another list, added to this one: the still part of a scene, built once. */
    void append(DrawList o) {
        int n = o.count * STRIDE;
        while ((count * STRIDE) + n > boxes.length) {
            float[] more = new float[boxes.length * 2];
            System.arraycopy(boxes, 0, more, 0, boxes.length);
            boxes = more;
        }
        System.arraycopy(o.boxes, 0, boxes, count * STRIDE, n);
        count += o.count;
    }

    /** Where a point lands in the frame, in frame pixels; false behind the eye. */
    boolean project(float x, float y, float z, float[] out) {
        float[] m = viewProj;
        float cx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float cy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float cw = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (cw < 0.05f) return false;
        out[0] = (cx / cw * 0.5f + 0.5f) * w;
        out[1] = (0.5f - cy / cw * 0.5f) * h;
        return true;
    }

    /** A box: its middle, its half sizes, its turn (a 3x3, row by row), color as 0xRRGGBB. */
    void box(float x, float y, float z, float hx, float hy, float hz, float[] rot, int rgb, int material) {
        box(x, y, z, hx, hy, hz, rot, rgb, (float) material);
    }

    /** The same, with a material that carries its own number (see material()). */
    void box(float x, float y, float z, float hx, float hy, float hz, float[] rot, int rgb, float material) {
        if ((count + 1) * STRIDE > boxes.length) {
            float[] more = new float[boxes.length * 2];
            System.arraycopy(boxes, 0, more, 0, boxes.length);
            boxes = more;
        }
        int o = count * STRIDE;
        float[] b = boxes;
        b[o] = x;
        b[o + 1] = y;
        b[o + 2] = z;
        b[o + 3] = hx;
        b[o + 4] = hy;
        b[o + 5] = hz;
        if (rot == null) {
            b[o + 6] = 1; b[o + 7] = 0; b[o + 8] = 0;
            b[o + 9] = 0; b[o + 10] = 1; b[o + 11] = 0;
            b[o + 12] = 0; b[o + 13] = 0; b[o + 14] = 1;
        } else {
            // columns of the turn
            b[o + 6] = rot[0]; b[o + 7] = rot[3]; b[o + 8] = rot[6];
            b[o + 9] = rot[1]; b[o + 10] = rot[4]; b[o + 11] = rot[7];
            b[o + 12] = rot[2]; b[o + 13] = rot[5]; b[o + 14] = rot[8];
        }
        b[o + 15] = ((rgb >> 16) & 0xFF) / 255f;
        b[o + 16] = ((rgb >> 8) & 0xFF) / 255f;
        b[o + 17] = (rgb & 0xFF) / 255f;
        b[o + 18] = mirrored && material == (float) Math.floor(material) ? material + MIRRORED / 256f : material;
        count++;
    }

    /**
     * The eye: where it stands, which way it looks (yaw to the right of straight ahead,
     * pitch up), the focal length in frame pixels; near and far planes in meters.
     */
    void camera(float x, float y, float z, float yaw, float pitch, float focal, float near, float far) {
        float cp = (float) Math.cos(pitch);
        float fx = (float) Math.sin(yaw) * cp, fy = (float) Math.sin(pitch), fz = (float) Math.cos(yaw) * cp;
        float rl = (float) Math.sqrt(fz * fz + fx * fx);
        float rx = fz / rl, ry = 0f, rz = -fx / rl;
        // up = ahead x right
        float ux = fy * rz - fz * ry, uy = fz * rx - fx * rz, uz = fx * ry - fy * rx;
        eye[0] = x; eye[1] = y; eye[2] = z;
        right[0] = rx; right[1] = ry; right[2] = rz;
        up[0] = ux; up[1] = uy; up[2] = uz;
        ahead[0] = fx; ahead[1] = fy; ahead[2] = fz;
        this.focal = focal;
        float sx = 2f * focal / w, sy = 2f * focal / h;
        float a = (far + near) / (far - near), b = -2f * far * near / (far - near);
        float tx = -(rx * x + ry * y + rz * z), ty = -(ux * x + uy * y + uz * z), tz = -(fx * x + fy * y + fz * z);
        // clip = P * V, written out: x' = sx * (r.p + tx), y' = sy * (u.p + ty), w' = f.p + tz
        float[] m = viewProj;
        m[0] = sx * rx; m[4] = sx * ry; m[8] = sx * rz; m[12] = sx * tx;
        m[1] = sy * ux; m[5] = sy * uy; m[9] = sy * uz; m[13] = sy * ty;
        m[2] = a * fx; m[6] = a * fy; m[10] = a * fz; m[14] = a * tz + b;
        m[3] = fx; m[7] = fy; m[11] = fz; m[15] = tz;
    }

    /**
     * The sun: its direction (toward it), and the stretch its shadows are worked out over,
     * around a middle point: so many meters across the sun's beams level with the ground,
     * so many up and down them, so many deep along them.
     */
    void sun(float dx, float dy, float dz, float cx, float cy, float cz, float across, float upDown, float depth) {
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l; dy /= l; dz /= l;
        sun[0] = dx; sun[1] = dy; sun[2] = dz;
        // the sun's own frame: it looks along -d
        float fx = -dx, fy = -dy, fz = -dz;
        float rx = fz, ry = 0f, rz = -fx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) { rx = 1f; rz = 0f; rl = 1f; }
        rx /= rl; rz /= rl;
        float ux = fy * rz - fz * ry, uy = fz * rx - fx * rz, uz = fx * ry - fy * rx;
        float ex = cx - fx * depth * 0.5f, ey = cy - fy * depth * 0.5f, ez = cz - fz * depth * 0.5f;
        float s = 2f / across, t = 2f / upDown, k = 2f / depth;
        float[] m = lightProj;
        m[0] = s * rx; m[4] = s * ry; m[8] = s * rz; m[12] = -s * (rx * ex + ry * ey + rz * ez);
        m[1] = t * ux; m[5] = t * uy; m[9] = t * uz; m[13] = -t * (ux * ex + uy * ey + uz * ez);
        m[2] = k * fx; m[6] = k * fy; m[10] = k * fz; m[14] = -k * (fx * ex + fy * ey + fz * ez) - 1f;
        m[3] = 0; m[7] = 0; m[11] = 0; m[15] = 1;
    }

    static void rgb(float[] out, int c) {
        out[0] = ((c >> 16) & 0xFF) / 255f;
        out[1] = ((c >> 8) & 0xFF) / 255f;
        out[2] = (c & 0xFF) / 255f;
    }

    /** The whole list as text, for the desktop stand's renderer. The flat layer is written apart. */
    String json() {
        StringBuilder s = new StringBuilder(64 + count * STRIDE * 10);
        s.append("{\"w\":").append(w).append(",\"h\":").append(h).append(",\"focal\":").append(focal);
        arr(s, "viewProj", viewProj);
        arr(s, "lightProj", lightProj);
        arr(s, "eye", eye);
        arr(s, "right", right);
        arr(s, "up", up);
        arr(s, "ahead", ahead);
        arr(s, "sun", sun);
        arr(s, "sunColor", sunColor);
        arr(s, "skyTop", skyTop);
        arr(s, "skyHorizon", skyHorizon);
        arr(s, "skyGlow", skyGlow);
        arr(s, "ambientSky", ambientSky);
        arr(s, "ambientGround", ambientGround);
        arr(s, "fog", fog);
        s.append(",\"fogStart\":").append(fogStart).append(",\"fogScale\":").append(fogScale).append(",\"time\":").append(time);
        s.append(",\"overcast\":").append(overcast).append(",\"night\":").append(night);
        arr(s, "grade", grade);
        s.append(",\"red\":").append(red).append(",\"gold\":").append(gold).append(",\"blur\":").append(blur);
        s.append(",\"dark\":").append(dark).append(",\"spotRadius\":").append(spotRadius).append(",\"flash\":").append(flash).append(",\"goldGlow\":").append(goldGlow).append(",\"tube\":").append(tube);
        arr(s, "spot", spot);
        arr(s, "mirror", mirror);
        arr(s, "halo", halo);
        s.append(",\"rippleAge\":").append(rippleAge).append(",\"ripple\":").append(ripple).append(",\"bolt\":").append(bolt);
        s.append(",\"flyN\":").append(flyN).append(",\"fly\":[");
        for (int i = 0; i < flyN * 8; i++) {
            if (i > 0) s.append(',');
            s.append(fly[i]);
        }
        s.append(']');
        s.append(",\"count\":").append(count).append(",\"boxes\":[");
        for (int i = 0; i < count * STRIDE; i++) {
            if (i > 0) s.append(',');
            s.append(boxes[i]);
        }
        s.append("],\"ballCount\":").append(ballCount).append(",\"balls\":[");
        for (int i = 0; i < ballCount * STRIDE; i++) {
            if (i > 0) s.append(',');
            s.append(balls[i]);
        }
        s.append("]}");
        return s.toString();
    }

    private static void arr(StringBuilder s, String name, float[] a) {
        s.append(",\"").append(name).append("\":[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) s.append(',');
            s.append(a[i]);
        }
        s.append(']');
    }
}
