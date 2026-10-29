package com.shumtugle.freedie;

/**
 * A humanoid skeleton and the motions it knows, read from an asset (see the format below).
 * Every motion is kept as it was sampled: for each frame, for each bone, its turn and its
 * place in the figure's own space, standing at the origin and facing -z. The game only
 * blends between two frames; nothing is worked down the bones here.
 *
 * Format, big-endian, deflated: 'MOVE', u8 version; u8 bones, each a u8 length and its name
 * and an i8 parent; the rest pose, per bone 4 f32 (a turn as x y z w) and 3 f32 (a place, m);
 * u8 motions, each a u8 length and its name, u16 frames, u8 frames a second, u8 whether it
 * loops; then for each frame, each bone, 4 i16 (the turn, times 32767) and 3 i16 (the place,
 * in mm).
 */
final class Moves {
    final String[] bone;
    final int[] parent;
    final float[] restQ, restP;
    final String[] motion;
    private final int[] frames, fps;
    private final boolean[] loops;
    private final short[][] data;

    private Moves(String[] bone, int[] parent, float[] restQ, float[] restP, String[] motion, int[] frames, int[] fps,
                  boolean[] loops, short[][] data) {
        this.bone = bone;
        this.parent = parent;
        this.restQ = restQ;
        this.restP = restP;
        this.motion = motion;
        this.frames = frames;
        this.fps = fps;
        this.loops = loops;
        this.data = data;
    }

    /** Reads the asset; null if it is missing or will not read. */
    static Moves read(byte[] z) {
        if (z == null) return null;
        byte[] b = Console.inflate(z);
        if (b == null || b.length < 6 || b[0] != 'M' || b[1] != 'O' || b[2] != 'V' || b[3] != 'E') return null;
        java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(b);
        in.position(5);
        int n = in.get() & 0xFF;
        String[] bone = new String[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            bone[i] = name(in);
            parent[i] = in.get();
        }
        float[] rq = new float[n * 4], rp = new float[n * 3];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) rq[i * 4 + k] = in.getFloat();
            for (int k = 0; k < 3; k++) rp[i * 3 + k] = in.getFloat();
        }
        int m = in.get() & 0xFF;
        String[] motion = new String[m];
        int[] frames = new int[m], fps = new int[m];
        boolean[] loops = new boolean[m];
        short[][] data = new short[m][];
        for (int c = 0; c < m; c++) {
            motion[c] = name(in);
            frames[c] = in.getShort() & 0xFFFF;
            fps[c] = in.get() & 0xFF;
            loops[c] = in.get() != 0;
            short[] d = new short[frames[c] * n * 7];
            for (int i = 0; i < d.length; i++) d[i] = in.getShort();
            data[c] = d;
        }
        return new Moves(bone, parent, rq, rp, motion, frames, fps, loops, data);
    }

    private static String name(java.nio.ByteBuffer in) {
        int l = in.get() & 0xFF;
        byte[] s = new byte[l];
        in.get(s);
        return new String(s, java.nio.charset.StandardCharsets.US_ASCII);
    }

    int bone(String name) {
        for (int i = 0; i < bone.length; i++) if (bone[i].equals(name)) return i;
        return -1;
    }

    int motion(String name) {
        for (int i = 0; i < motion.length; i++) if (motion[i].equals(name)) return i;
        return -1;
    }

    /** How long a motion lasts, seconds. */
    float length(int c) {
        return loops[c] ? frames[c] / (float) fps[c] : (frames[c] - 1) / (float) fps[c];
    }

    boolean loops(int c) {
        return loops[c];
    }

    /**
     * The pose at a time into a motion (a looping one goes round, another holds its last
     * frame): for every bone its turn into q (x y z w) and its place into p, blended between
     * the two frames either side.
     */
    void pose(int c, float t, float[] q, float[] p) {
        int n = bone.length, f = frames[c];
        float x = t * fps[c];
        int a, b;
        float u;
        if (loops[c]) {
            x = x % f;
            if (x < 0f) x += f;
            a = (int) x;
            b = (a + 1) % f;
            u = x - a;
        } else {
            x = Math.max(0f, Math.min(f - 1, x));
            a = (int) x;
            b = Math.min(f - 1, a + 1);
            u = x - a;
        }
        short[] d = data[c];
        for (int i = 0; i < n; i++) {
            int oa = (a * n + i) * 7, ob = (b * n + i) * 7;
            // the shorter way between the two turns, then brought back to unit length
            float dot = 0f;
            for (int k = 0; k < 4; k++) dot += d[oa + k] * (float) d[ob + k];
            float sb = dot < 0f ? -1f : 1f, len = 0f;
            for (int k = 0; k < 4; k++) {
                float v = d[oa + k] + (sb * d[ob + k] - d[oa + k]) * u;
                q[i * 4 + k] = v;
                len += v * v;
            }
            len = (float) Math.sqrt(len);
            for (int k = 0; k < 4; k++) q[i * 4 + k] /= len;
            for (int k = 0; k < 3; k++) p[i * 3 + k] = (d[oa + 4 + k] + (d[ob + 4 + k] - d[oa + 4 + k]) * u) * 0.001f;
        }
    }
}
