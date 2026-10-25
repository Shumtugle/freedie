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
    float fogNear = 60f, fogFar = 220f;
    float time;                                // seconds of game time, for what drifts in the sky

    float[] boxes = new float[STRIDE * 256];
    int count;

    int[] flat;                                // the flat layer, ARGB, w * h; clear where the scene shows

    void begin(int w, int h) {
        if (flat == null || flat.length != w * h) flat = new int[w * h];
        this.w = w;
        this.h = h;
        count = 0;
    }

    /** A box: its middle, its half sizes, its turn (a 3x3, row by row), color as 0xRRGGBB. */
    void box(float x, float y, float z, float hx, float hy, float hz, float[] rot, int rgb, int material) {
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
        b[o + 18] = material;
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
     * The sun: its direction (toward it), and the square of ground its shadows are worked
     * out over, around a middle point, so many meters across and deep.
     */
    void sun(float dx, float dy, float dz, float cx, float cy, float cz, float size, float depth) {
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
        float s = 2f / size, k = 2f / depth;
        float[] m = lightProj;
        m[0] = s * rx; m[4] = s * ry; m[8] = s * rz; m[12] = -s * (rx * ex + ry * ey + rz * ez);
        m[1] = s * ux; m[5] = s * uy; m[9] = s * uz; m[13] = -s * (ux * ex + uy * ey + uz * ez);
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
        s.append(",\"fogNear\":").append(fogNear).append(",\"fogFar\":").append(fogFar).append(",\"time\":").append(time);
        s.append(",\"count\":").append(count).append(",\"boxes\":[");
        for (int i = 0; i < count * STRIDE; i++) {
            if (i > 0) s.append(',');
            s.append(boxes[i]);
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
