package com.shumtugle.freedie;

/**
 * The opening, about four and a half seconds, no word but the name. Freedie stands on the
 * road before the empty plinth, facing the eye. The eye starts low at her feet and rises,
 * drawing back, until it stands where the player stands; her eyes kindle; over her the name
 * comes up in gold, a block for every dot of it, left to right. Then the eye goes into hers,
 * the light comes up white, and the game is seen from there: she is the player. Played at
 * every start; a touch at any moment goes straight to the light.
 */
final class Intro {
    static final String TITLE = "FREEDIE";
    // the timeline, seconds: rising, the eyes, the name, into her eyes, the light, the game
    static final float RISE = 2.4f, EYES0 = 0.9f, EYES1 = 1.6f, NAME = 1.5f, INTO = 3.4f, LIGHT = 4.1f, END = 4.45f;
    // where she stands, and how big: meters to her unit
    static final float FX = -0.6f, FZ = 8.6f, SIZE = 0.55f;
    // the name: a gold block for every dot of it, over her and a little behind
    private static final float NAME_Z = FZ + 4f, NAME_Y = 4.4f, DOT = 0.13f;
    private static final float NAME_X = FX + (NAME_Z - FZ) * 0.2f;

    private final World.Host host;
    float t;
    private boolean done;
    private final float[] dotX = new float[300], dotY = new float[300], dotAt = new float[300];
    private int dots;

    Intro(World.Host host) {
        this.host = host;
        int width = PixelFont.width(TITLE, 1), col = 0;
        for (int i = 0; i < TITLE.length(); i++) {
            char c = TITLE.charAt(i);
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++)
                    if (PixelFont.dot(c, gx, gy) && dots < dotX.length) {
                        float cx = col + gx;
                        dotX[dots] = NAME_X + (cx - (width - 1) * 0.5f) * DOT;
                        dotY[dots] = NAME_Y + (3 - gy) * DOT;
                        dotAt[dots] = NAME + cx * 0.016f;
                        dots++;
                    }
            col += 6;
        }
    }

    boolean done() {
        return done;
    }

    /** A touch: straight to the light. */
    void skip() {
        if (t < LIGHT) {
            t = LIGHT;
            host.sound(Synth.WHOOSH, 0.5f, 1.5f);
        }
    }

    void update(float dt) {
        float was = t;
        t += dt;
        if (was <= 0f && t > 0f) host.sound(Synth.WHOOSH, 0.7f, 0.55f);
        if (was < EYES0 && t >= EYES0) host.sound(Synth.CLANG, 0.5f, 0.45f);
        if (was < NAME && t >= NAME) host.sound(Synth.thunder(3), 0.6f, 1.1f);
        if (was < INTO && t >= INTO) host.sound(Synth.WHOOSH, 0.8f, 0.8f);
        if (t >= END) done = true;
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private final float[] eye = new float[3], look = new float[3];

    /**
     * The eye and where it looks: up from her feet and back to where the player stands, held
     * there, then into her eyes.
     */
    private void camera() {
        // her eyes, in the world
        float ex = FX, ey = 4.27f * SIZE, ez = FZ - 0.785f * SIZE;
        float[] e0 = {FX - 0.3f, 0.45f, FZ - 3.4f}, l0 = {FX, 2.2f, FZ};
        float[] e1 = {World.CAM_X, World.CAM_Y, World.CAM_Z}, l1 = {FX + 0.25f, 2.45f, FZ + 1.5f};
        float[] e2 = {ex, ey, ez - 0.25f}, l2 = {ex, ey, FZ};
        float a = ease(t / RISE), b = ease((t - INTO) / (LIGHT + 0.25f - INTO));
        for (int k = 0; k < 3; k++) {
            float ek = e0[k] + (e1[k] - e0[k]) * a, lk = l0[k] + (l1[k] - l0[k]) * a;
            // into her eyes faster at the end than at the start
            float bb = b * b;
            eye[k] = ek + (e2[k] - ek) * bb;
            look[k] = lk + (l2[k] - lk) * b;
        }
    }

    /** Sets the frame's eye. */
    void frame(DrawList d, int w) {
        camera();
        float dx = look[0] - eye[0], dy = look[1] - eye[1], dz = look[2] - eye[2];
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, (float) Math.sqrt(dx * dx + dz * dz));
        d.camera(eye[0], eye[1], eye[2], yaw, pitch, w * 1.9f, 0.05f, 500f);
        // the light rising into the game
        if (t >= LIGHT) d.flash = Math.min(1f, (t - LIGHT) / (END - LIGHT));
    }

    /** Herself, her eyes as far as they are lit, and the name. */
    void draw(DrawList d) {
        Frida.eyes = ease((t - EYES0) / (EYES1 - EYES0));
        Frida.draw(d, FX, 0f, FZ, 0f, SIZE);
        Frida.eyes = 1f;
        for (int i = 0; i < dots; i++) {
            float age = t - dotAt[i];
            if (age < 0f) continue;
            float s = Math.min(1f, age / 0.12f) * DOT * 0.45f;
            if (age < 0.3f)
                d.box(dotX[i], dotY[i], NAME_Z, s, s, s, null, Street.mix(0xFFF4C8, 0xF2C230, age / 0.3f), DrawList.GLOW);
            else d.box(dotX[i], dotY[i], NAME_Z, s, s, s * 1.6f, null, 0xF2C230, DrawList.GOLD);
        }
    }
}
